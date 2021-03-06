package com.bluesky.osprey.pttapp;

import com.bluesky.JitterBuffer;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;
/**
 * AudioRxPath polls received audio packet from JitterBuffer, and play it to speaker;
 * It's data driven, being started to play by first audio data, and being stopped playing
 * by EOF(an empty(zero-byte) audio data).
 *
 * In addition, user can stop/release AudioRxPath by calling its stop() method. A stopped
 * AudioRxPath should be treated as zombie.
 *
 * It has following internal states:
 * - uninitialized
 * - initialized, awaiting first data
 * - playing,
 * - ending, after received EOF, or 6 consecutive loss packets
 * - ended (equivalent to initialized)
 * - stopping(?)
 * - stopped, after stopped()
 *
 * It can inform listener following events:
 * - playing (from initialized to playing)
 * - ended (from playing to ended)
 * - stopped ( after released all resources )
 *
 * Created by liangc on 17/01/15.
 */
public class AudioRxPath {
    public class AudioTrackConfiguration{
        static final int AUDIO_SAMPLE_RATE = 8000; // 8KHz
        static final int BUFFER_SIZE_MULTIPLIER = 4;
        static final int AUDIO_PCM20MS_SAMPLES =
                (AUDIO_SAMPLE_RATE * GlobalConstants.CALL_PACKET_INTERVAL /1000);
        static final int AUDIO_PCM20MS_SIZE = AUDIO_PCM20MS_SAMPLES * 2;
        static final int AUDIO_TONE_SIZE = AUDIO_PCM20MS_SIZE * 2; // 40ms tone
    }

    public class AudioDecoderConfiguration {
        static final int AUDIO_SAMPLE_RATE = 8000; // 8KHz
        static final int AUDIO_AMR_BITRATE = 7400; // 7.4Kbps
    }

    public interface Listener{
        public void audioEnd();
    }

    public AudioRxPath(){

        Log.i(TAG, "creating AudioRxPath...");

        mState = State.UNINITIALIZED;
        mAudioThread = new Thread( new Runnable(){
            @Override
            public void run(){

                Log.i(TAG, "Audio Rx thread started");
                createAudioRxComponents();

                while ( !Thread.currentThread().isInterrupted()) {
                    startDecoder();
                    waitFirstAudio();
                    playing();
                    ending();
                    reset();

                }

                cleanup();
                Log.i(TAG, "Audio Rx thread stopped");
            }// end of run()
        });

        mAudioThread.start();
    }

    /** stop audio rx path abruptly, discard whatever bufferred audio
     *   caller shall not use the instance after called stop()
     */
    public void stop(){
        if( mAudioThread != null ){
            mAudioThread.interrupt();
            mAudioThread = null;
        }
    }

    /** offer compressed audio data to Rx path
     *  for first audio data, the method triggers AudioRxPath to play
     *
     */
    public boolean offerAudioData(ByteBuffer audio, short sequence){
        if( mAudioThread == null ){
            return false;
        }

        boolean res = false;
        mLock.lock();
        try {
            switch (mState) {
                case INITIALIZED:
                    int sz = audio.limit();
                    res = mJitterBuffer.offer(audio, sequence);
                    Log.d(TAG, "offer, seq=" + sequence + ", sz=" + sz + ", res= " + res);
                    mAwaitFirst.signal();
                    break;
                case PLAYING:
                    sz = audio.limit();
                    res = mJitterBuffer.offer(audio, sequence);
                    Log.d(TAG, "offer, seq=" + sequence + ", sz=" + sz + ", res= " + res);
                    break;
                default:
                    // discard
                    break;
            }
        } finally {
            mLock.unlock();
        }
        return res;
    }

    public void registerListener(Listener listener){
        mListener = listener;
    }

    /** private methods  and members */

    private boolean createAudioRxComponents(){
        try {
            mDecoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AMR_NB);

            configureDecoder();

            int minBufferSize = AudioTrack.getMinBufferSize(
                    AudioTrackConfiguration.AUDIO_SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            Log.i(TAG, "min buffer size = " + minBufferSize);

            mAudioTrack = new AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    AudioTrackConfiguration.AUDIO_SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBufferSize * AudioTrackConfiguration.BUFFER_SIZE_MULTIPLIER,
                    AudioTrack.MODE_STREAM
            );

        } catch (Exception e){
            Log.e(TAG, "exception in config: " + e);
            return false;
        }

        return true;
    }

    private void startDecoder(){
        mDecoder.start();
        mDecoderInputBuffers = mDecoder.getInputBuffers();
        mDecoderOutputBuffers = mDecoder.getOutputBuffers();
    }

    private void configureDecoder(){
        MediaFormat format = new MediaFormat();
        format.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AMR_NB);
        format.setInteger(MediaFormat.KEY_SAMPLE_RATE,
                AudioDecoderConfiguration.AUDIO_SAMPLE_RATE);
        format.setInteger(MediaFormat.KEY_BIT_RATE,
                AudioDecoderConfiguration.AUDIO_AMR_BITRATE);
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);

        mDecoder.configure(format, null /* surface */, null /*crypto */, 0 /*flag */);
    }

    private void cleanup(){
        mAudioTrack.stop();
        mAudioTrack.release();
        mAudioTrack = null;

        mDecoder.stop();
        mDecoder.release();
        mDecoder = null;

        mInsideBuffer.clear();
        mJitterBuffer = null;
    }

    private void reset(){
        mPlayCount = 0;

        mAudioTrack.stop();
        mAudioTrack.flush();

        mInsideBuffer.clear();
        mJitterBuffer.reset();

        mDecoder.stop();
        configureDecoder();
    }

    private void waitFirstAudio(){
        mLock.lock();
        try {
            mState = State.INITIALIZED;
            mAwaitFirst.await();
        } catch (InterruptedException e){
            Log.w(TAG, "interrupted when awaiting first audio");
            Thread.currentThread().interrupt(); // re-assert interrupt
        } finally {
            mLock.unlock();
        }
    }

    /** keep polling jitter buffer, and playing audio,
     * until EOF (zero-len buffer), or interrupt
     *
     */
    private void playing() {
        loadTone(); // load 40ms start tone

        while(true){
            ByteBuffer receivedAudio = pollJitterBuffer();
            assert(receivedAudio != null);
            mInsideBuffer.offer(receivedAudio);

            if( receivedAudio.limit() == 0){
                break; // EOF
            }

            decodeAudio(0);
            playDecodedAudio();
        }
    }

    private void ending(){
        mState = State.ENDING;
        while( decodeAudio(DRAIN_WAIT)){
            playDecodedAudio();
        }
        drainDecodedAudio();
        drainAudioTrack();

        if(mListener!=null){
            mListener.audioEnd();
        }
    }

    private void loadTone(){
        mAudioTrack.write(Tones.TONE_A, 0, Tones.TONE_A.length);
    }

    /** poll jitter buffer,
     * if no packet was polled out, and if less than 6 consecutive lost packet, then synthesize
     * a NO_DATA packet
     *
     * if we have seen 6 consecutive lost packets, then we synthesize a zero-length packet
     *
     * @return received or synthesized audio
     *
     *   sythesized audio:
     *      NO_DATA if packet loss less than 6
     *      EOF: zero-len buffer, for 6 consecutive loss
     */
    private ByteBuffer pollJitterBuffer() {
        ByteBuffer compressedAudio = mJitterBuffer.poll();
        long deqSeq = mJitterBuffer.getDequeueSequence() -1;
        int sz = 0;
        boolean res = false;
        if( compressedAudio != null ) {
            sz = compressedAudio.limit();
            res = true;
        }
        Log.d(TAG, "poll, seq=" + deqSeq
                + ", sz=" + sz
                + ", res= " + res);

        if( compressedAudio == null ){
            // fake a noise packet/audio lost
            Log.i(TAG, "no data from jitter buffer");
            ++mConsecutiveLostCount;
            if( mConsecutiveLostCount == GlobalConstants.JITTER_DEPTH ){
                Log.w(TAG, "jitter empty");
                compressedAudio = ByteBuffer.allocate(0);
            } else {
                compressedAudio = ByteBuffer.wrap(AMB_NO_DATA_FRAME);
            }
        } else {
            mConsecutiveLostCount = 0;
        }
        return compressedAudio;
    }

    /** keep decode audio if we can get a decoder input buffer, otherwise we park it aside
     *  @param  wait wait time
     *  @return true if there's still audio to be enqueued to decoder
     */
    private boolean decodeAudio(int wait) {

        boolean res = true;

        while( mInsideBuffer.size() != 0 ) {

            int index = mDecoder.dequeueInputBuffer(wait);

            if( index < 0 ){
                Log.i(TAG, "empty decoder input buffer: " + index);
                break;
            }

            ByteBuffer buf = mInsideBuffer.poll();
            assert(buf!=null);

            int flags = 0;
            if(buf.limit() == 0){
                Log.i(TAG, "decode input got EOF");
                flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                res = false;
            }

            mDecoderInputBuffers[index].clear();
            mDecoderInputBuffers[index].put(buf);

            mDecoder.queueInputBuffer(
                    index,
                    0, // offset
                    mDecoderInputBuffers[index].position(), // meaningful size
                    0, // presenttion timeUS
                    flags
            );

            if( !res){
                break; // break if we've enqueued EOF to decoder
            }

        }
        return res;
    }

    /** keep writing decoded audio to Audio track, until no more decoded audio
     *  called before EOF
     */
    private void playDecodedAudio() {
        int index;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

        while((index = mDecoder.dequeueOutputBuffer(info, 0)) != MediaCodec.INFO_TRY_AGAIN_LATER ) {

            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                continue;
            }

            if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                mDecoderOutputBuffers = mDecoder.getOutputBuffers();
                continue;
            }

            mDecoderOutputBuffers[index].position(info.offset);
            mDecoderOutputBuffers[index].limit(info.offset + info.size);

            //for API3, I have to use write(byte[]...), and thus have to do a copy.
            // if I use API21, then I can write decoder output buffer directly to audioTrack.
            mDecoderOutputBuffers[index].get(mRawAudioData, 0, info.size);
            mDecoder.releaseOutputBuffer(index, false /* render */);

            mAudioTrack.write(mRawAudioData, 0, info.size);

            ++mPlayCount;
            if (mPlayCount == 2) {
                mAudioTrack.play();
            }
        }
    }

    /** drain decoder, called after EOF
     *
      */
    private void drainDecodedAudio(){
        int index;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

        while(true){
            index = mDecoder.dequeueOutputBuffer(info, -1); // wait infinite TODO: check deadloop
            if (index == MediaCodec.INFO_TRY_AGAIN_LATER ) {
                continue; //TODO: timeout handling
            }

            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                continue;
            }

            if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                mDecoderOutputBuffers = mDecoder.getOutputBuffers();
                continue;
            }

            mDecoderOutputBuffers[index].position(info.offset);
            mDecoderOutputBuffers[index].limit(info.offset + info.size);
            //for API3, I have to use write(byte[]...), and thus have to do a copy.
            // if I use API21, then I can write decoder output buffer directly to audioTrack.
            mDecoderOutputBuffers[index].get(mRawAudioData, 0, info.size);
            mDecoder.releaseOutputBuffer(index, false /* render */);

            mAudioTrack.write(mRawAudioData, 0, info.size);

            if( info.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM ){
                loadTone();
                Log.w(TAG, "decoder egress EOF");
                break;
            }

            ++mPlayCount;
            if (mPlayCount == 2) {
                mAudioTrack.play();
            }
        }
    }

    /** drain audio track according to # of pending samples
     * TODO: prevent awaiting forever
     */
    private void drainAudioTrack(){
        while(true) {
            int currentPos = mAudioTrack.getPlaybackHeadPosition();
            int totalSamples = mPlayCount * AudioTrackConfiguration.AUDIO_PCM20MS_SAMPLES
                    + Tones.TONE_A_SAMPLES * 2;
            if( currentPos == 0 || currentPos >= totalSamples ){
                Log.w(TAG, "draining audio track done! current pos =" + currentPos);
                break;
            }
            int wait = (totalSamples - currentPos) * 1000 / AudioTrackConfiguration.AUDIO_SAMPLE_RATE;
            Log.w(TAG, "draining audio track... to wait " + wait
                    + "ms, rent pos=" + currentPos
                    + ", total samples = " + totalSamples);
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private JitterBuffer<ByteBuffer> mJitterBuffer =
            new JitterBuffer<ByteBuffer>(GlobalConstants.JITTER_DEPTH,
                    GlobalConstants.CALL_PACKET_INTERVAL,
                    GlobalConstants.TIME_UNIT);

    private final LinkedList<ByteBuffer> mInsideBuffer = new LinkedList<ByteBuffer>();

    private Thread mAudioThread;
    private MediaCodec mDecoder;
    private ByteBuffer[] mDecoderInputBuffers, mDecoderOutputBuffers;
    private AudioTrack mAudioTrack;

    //should be enough for AMR-NB one frame, for API3
    private byte[] mRawAudioData = new byte[AudioTrackConfiguration.AUDIO_PCM20MS_SIZE];

    private int mPlayCount = 0;
    private int mLostCount = 0;

    private int mConsecutiveLostCount = 0;


    private static enum State {
        UNINITIALIZED,
        INITIALIZED,
        PLAYING,
        ENDING,
    }

    private State mState;

    private Listener mListener;

    private final Lock mLock = new ReentrantLock();
    private final Condition mAwaitFirst = mLock.newCondition();

    private static final byte[] AMB_NO_DATA_FRAME = {
            ((1<<2) | (15 << 3)),
    };
    private static final int DRAIN_WAIT = 10; // 10ms

    private static final String TAG = "AudioRxPath";
}

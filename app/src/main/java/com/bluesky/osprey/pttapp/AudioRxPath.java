package com.bluesky.osprey.pttapp;

import com.bluesky.JitterBuffer;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.concurrent.Semaphore;
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
        static final int BUFFER_SIZE_MULTIPLIER = 10;
        static final int AUDIO_PCM20MS_SAMPLES =
                (AUDIO_SAMPLE_RATE * GlobalConstants.CALL_PACKET_INTERVAL /1000);
        static final int AUDIO_PCM20MS_SIZE = AUDIO_PCM20MS_SAMPLES * 2;
        static final int AUDIO_TONE_SIZE = AUDIO_PCM20MS_SIZE * 2; // 40ms tone
    }

    public class AudioDecoderConfiguration {
        static final int AUDIO_SAMPLE_RATE = 8000; // 8KHz
        static final int AUDIO_AMR_BITRATE = 7400; // 7.4Kbps
    }

    public AudioRxPath(){

        Log.i(TAG, "creating AudioRxPath...");

        mState = State.UNINITIALIZED;
        mAudioThread = new Thread( new Runnable(){
            @Override
            public void run(){

                Log.i(TAG, "Audio Rx thread started");
                configureAudioRxPath();

                while ( !Thread.currentThread().isInterrupted()) {
                    waitFirstAudio();
                    startDecoder();
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
                    res = mJitterBuffer.offer(audio, sequence);
                    mAwaitFirst.signal();
                    break;
                case PLAYING:
                    res = mJitterBuffer.offer(audio, sequence);
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

    /** private methods  and members */

    private boolean configureAudioRxPath(){
        try {
            mDecoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AMR_NB);

            MediaFormat format = new MediaFormat();
            format.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AMR_NB);
            format.setInteger(MediaFormat.KEY_SAMPLE_RATE,
                    AudioDecoderConfiguration.AUDIO_SAMPLE_RATE);
            format.setInteger(MediaFormat.KEY_BIT_RATE,
                    AudioDecoderConfiguration.AUDIO_AMR_BITRATE);
            format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);

            mDecoder.configure(format, null /* surface */, null /*crypto */, 0 /*flag */);

            int minBufferSize = AudioTrack.getMinBufferSize(
                    AudioTrackConfiguration.AUDIO_SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);

            mAudioTrack = new AudioTrack(
                    AudioManager.STREAM_VOICE_CALL,
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
        mAudioTrack.stop();
        mAudioTrack.flush();

        mDecoder.stop();

        mInsideBuffer.clear();
        mJitterBuffer.reset();

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

    private void playing() {
        preloadTone(); // load 40ms start tone

        while(true){
            ByteBuffer receivedAudio = pollJitterBuffer();

            if( receivedAudio.position() == 0){
                // end of stream
                break;
            }

            decodeAudio(receivedAudio);

            playDecodedAudio();
        }
    }

    private void ending(){
        mState = State.ENDING;
        ByteBuffer eofBuf = ByteBuffer.allocate(0);
        decodeAudio(eofBuf);
        while( decodeAudio(null)){
            playDecodedAudio();
        }
        drainDecodedAudio();
        drainAudioTrack();
    }

    private void preloadTone(){
        ByteBuffer toneBuffer = generateTone();
//        mAudioTrack.write(toneBuffer, toneBuffer.remaining(), AudioTrack.WRITE_NON_BLOCKING);
        mAudioTrack.write(toneBuffer.array(), toneBuffer.arrayOffset(), toneBuffer.position());
    }

    private ByteBuffer generateTone(){
        ByteBuffer tone = ByteBuffer.allocate(AudioTrackConfiguration.AUDIO_TONE_SIZE);
        int toneSamples = AudioTrackConfiguration.AUDIO_TONE_SIZE / 2;
        final double frequency = 950.0;
        for( int i = 0; i< toneSamples; ++i){
            double time = (double)i / AudioTrackConfiguration.AUDIO_SAMPLE_RATE;
            double sinValue =
                    Math.sin(2*Math.PI * frequency * time);
            short s = (short)(sinValue * (short)20000);
            tone.putShort(s);
        }
        return tone;
    }

    /** poll jitter buffer,
     * if no packet was polled out, and if less than 6 consecutive lost packet, then synthesize
     * a NO_DATA packet
     *
     * if we have seen 6 consecutive lost packets, then we synthesize a zero-length packet
     *
     * @return received or synthesized audio,
     */
    private ByteBuffer pollJitterBuffer() {
        ByteBuffer compressedAudio = mJitterBuffer.poll();
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
     *  @param  compressedAudio compressed audio. If it's null, then we just need to drain
     *                          internal buffer
     *  @return true if there's still audio to be enqueued to decoder
     */
    private boolean decodeAudio(ByteBuffer compressedAudio) {

        ByteBuffer buf;
        boolean res = true;
        int wait = DRAIN_WAIT; // 10ms

        if( compressedAudio != null ) {
            mInsideBuffer.offer(compressedAudio);
            wait = 0;
        }

        while( (buf = mInsideBuffer.poll())!= null ) {

            int index = mDecoder.dequeueInputBuffer(wait);
            if( index >= 0) {
                mDecoderInputBuffers[index].clear();
                mDecoderInputBuffers[index].put(buf);
                int flags = 0;

                if(buf.position() == 0){
                    Log.i(TAG, "decode input got EOF");
                    flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                    res = false;
                }

                mDecoder.queueInputBuffer(
                        index,
                        0, // offset
                        mDecoderInputBuffers[index].position(), // meaningful size
                        0, // presenttion timeUS
                        flags
                );

            } else {
                Log.i(TAG, "empty decoder input buffer: " + index);
                mInsideBuffer.addFirst(buf);
                res = false;
            }
        }
        return res;
    }

    /** keep writing decoded audio to Audio track, until no more decoded audio
     *
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

    /** drain decoder
     *
      */
    private void drainDecodedAudio(){
        int index;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

        while(true){
            index = mDecoder.dequeueOutputBuffer(info, -1); // wait infinite
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

            if( info.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM ){
                Log.w(TAG, "decoder egress EOF");
                break;
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

    /** drain audio track according to # of pending samples
     *
     */
    private void drainAudioTrack(){
        while(true) {
            int currentPos = mAudioTrack.getPlaybackHeadPosition();
            int totalSamples = mPlayCount * AudioTrackConfiguration.AUDIO_PCM20MS_SAMPLES;
            if( currentPos >= totalSamples ){
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

    private byte[] mRawAudioData = new byte[1000]; //should be enough for AMR-NB one frame, for API3

    private int mPlayCount = 0;
    private int mLostCount = 0;

    private int mConsecutiveLostCount = 0;
    boolean mbAwaitFirst;


    private static enum State {
        UNINITIALIZED,
        INITIALIZED,
        PLAYING,
        ENDING,
    }

    private State mState;

    private final Lock mLock = new ReentrantLock();
    private final Condition mAwaitFirst = mLock.newCondition();

    private static final byte[] AMB_NO_DATA_FRAME = {
            ((1<<2) | (15 << 3)),
    };
    private static final int DRAIN_WAIT = 10; // 10ms

    private static final String TAG = "AudioRxPath";
}

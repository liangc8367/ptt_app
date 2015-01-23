package com.bluesky.osprey.pttapp;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;
import android.content.Context;

import com.bluesky.JitterBuffer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedList;

/**
 * Created by liangc on 21/01/15.
 */
public class AudioDecoderTest {

    public class AudioTrackConfiguration{
        static final int AUDIO_SAMPLE_RATE = 8000; // 8KHz
        static final int BUFFER_SIZE_MULTIPLIER = 10;
        static final int AUDIO_PCM20MS_SIZE =
                (AUDIO_SAMPLE_RATE *2 * GlobalConstants.CALL_PACKET_INTERVAL /1000);
        static final int AUDIO_TONE_SIZE = AUDIO_PCM20MS_SIZE * 5; // 100ms tone
    }

    public class AudioDecoderConfiguration {
        static final int AUDIO_SAMPLE_RATE = 8000; // 8KHz
        static final int AUDIO_AMR_BITRATE = 7400; // 7.4Kbps
    }

    public AudioDecoderTest(Context context, boolean stolen){
        mContext = context;
        mStolen = stolen;

        Log.i(TAG, "creating AudioRxPath...");
        configureAudioRxPath();
//        preloadTone();
        Log.i(TAG, "completed creation.");

        mbAwaitFirst = true;
        mAudioThread = new Thread( new Runnable(){
            @Override
            public void run(){

                Log.i(TAG, "Audio Rx thread started");
                openIOStreams();

//                writeTone(generateTone());

                startDecoder();
                mPlayCount = 0;
                while(mRunning) {
                    ByteBuffer receivedAudio = loadInput();// = pollJitterBuffer();
                    if (receivedAudio == null) {
                        // end of input
                        markInputEOF();
                        while(playDecodedAudio()){
                            try {
                                Thread.sleep(20);
                            } catch (InterruptedException e){

                            }
                        }
                        break;
                    }

                    decodeAudio(receivedAudio);
                    if(!playDecodedAudio()){
                        break;
                    };
                }
                cleanup();
                Log.i(TAG, "Audio Rx thread stopped");
            }// end of run()
        });
    }

    /** stop audio rx path abruptly, discard whatever bufferred audio
     *
     */
    public void stop(){
        if( mRunning ) {
            mRunning = false;
            mAudioThread.interrupt();
        }
    }
//
//    /** offer compressed audio data to Rx path
//     *
//     */
//    public boolean offerAudioData(ByteBuffer audio, short sequence){
//        boolean res = mJitterBuffer.offer(audio, sequence);
//        if(!mRunning){
//            start();
//        }
//        return res;
//    }

    /** private methods  and members */

    /* audio rx path is data driven, i.e. start when the 1st data comes */
    public void start(){
        if(!mRunning) {
            mRunning = true;
            mAudioThread.start();
        }
    }

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

        //TODO: clean jitterBuffer;
        mInsideBuffer.clear();

        try {
            mIs.close();
//            mOs.close();
        } catch (Exception e){
            Log.w(TAG, "exception in closing:" +e);
        }

        mRunning = false;
    }

    private void preloadTone(){
//        ByteBuffer toneBuffer = generateTone();
////        mAudioTrack.write(toneBuffer, toneBuffer.remaining(), AudioTrack.WRITE_NON_BLOCKING);
//        mAudioTrack.write(toneBuffer.array(), toneBuffer.arrayOffset(), toneBuffer.position());
    }

    private void writeTone(ByteBuffer tone){

        writeOutput(tone.array(), tone.arrayOffset(), tone.position());
    }

    private ByteBuffer generateTone(){
        ByteBuffer tone = ByteBuffer.allocate(AudioTrackConfiguration.AUDIO_TONE_SIZE);
        tone.order(ByteOrder.LITTLE_ENDIAN); //<== little endian
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

//    /** poll jitter buffer, and sync noise packet if needed
//     *
//     * @return received audio, or faked noise packet, otherwise null if more than 6 lost
//     */
//    private ByteBuffer pollJitterBuffer() {
//        ByteBuffer compressedAudio = mJitterBuffer.poll();
//        if( compressedAudio == null ){
//            // fake a noise packet/audio lost
//            Log.i(TAG, "no data from jitter buffer");
//            ++mConsecutiveLostCount;
//            if( mConsecutiveLostCount == GlobalConstants.JITTER_DEPTH ){
//                Log.i(TAG, "jitter empty");
//                return null;
//            }
//            // compressedAudio = fakeLostPacket();
//            compressedAudio = null;
//        } else {
//            mConsecutiveLostCount = 0;
//        }
//        return compressedAudio;
//    }

    /** keep decode audio if we can get a decoder input buffer, otherwise we park it aside
     *
     */
    private void decodeAudio(ByteBuffer compressedAudio) {
        mInsideBuffer.offer(compressedAudio);
        ByteBuffer buf;
        while( (buf = mInsideBuffer.poll())!= null ) {

            int index = mDecoder.dequeueInputBuffer(0);
            if( index >= 0) {
                mDecoderInputBuffers[index].clear();
                mDecoderInputBuffers[index].put(buf);

                mDecoder.queueInputBuffer(
                        index,
                        0, // offset
                        mDecoderInputBuffers[index].position(), // meaningful size
                        0, // presenttion timeUS
                        0  // flags);
                );
            } else {
                Log.i(TAG, "empty decoder input buffer: " + index);
                mInsideBuffer.addFirst(buf);
                return;
            }
        }
    }

    private void markInputEOF(){
        while(true){
            int index = mDecoder.dequeueInputBuffer(0);
            if( index >= 0){
                mDecoderInputBuffers[index].clear();
                mDecoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                break;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e){

            }
        }
    }

    /** keep writing decoded audio to Audio track, until no more decoded audio
     *
     * @return true if not eof
     */
    private boolean playDecodedAudio() {
        int index;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean eof = false;

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

            if(info.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM ){
                Log.w(TAG, "end of decoded stream: playback head ="
                        + mAudioTrack.getPlaybackHeadPosition()
                        + ", native sample rate=" + mAudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_VOICE_CALL)
                        + ", current playcount=" + (mPlayCount - 1));

            }

            if (mPlayCount == 2) {
                mAudioTrack.play();
            }
//            writeOutput(mRawAudioData, 0, info.size);

            if( info.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM){
                Log.i(TAG, "end of decoded stream");
                eof = true;

                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e){

                }

                Log.w(TAG, "after 2s: playback head ="
                        + mAudioTrack.getPlaybackHeadPosition()
                        + ", native sample rate=" + mAudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_VOICE_CALL)
                        + ", current playcount=" + (mPlayCount - 1));

                break;
            }
        }

        return !eof;
    }

    private void openIOStreams(){
        File ifile = new File(mContext.getExternalFilesDir(null), "audio.amr-nb");

//        String ofileName;
//        if( mStolen ){
//            ofileName = "audio-stolen8-tone.raw";
//        } else {
//            ofileName = "audio.raw";
//        }
//        File ofile = new File(mContext.getExternalFilesDir(null), ofileName);
        try {
            mIs = new FileInputStream(ifile);
//            mOs = new FileOutputStream(ofile);

            mIs.skip(AMR_FILE_HEADER_SINGLE_CHANNEL.length());

        } catch (IOException e){
            Log.w(TAG, "Error open " + e);
        }

    }

    private ByteBuffer loadInput(){
        byte[] buffer = new byte[COMPRESSED_20MS_AUDIO_SIZE];
        int sz;
        try{
            sz = mIs.read(buffer, 0, COMPRESSED_20MS_AUDIO_SIZE);
            ++mReadCount;
        } catch (Exception e){
            Log.w(TAG, "error in reading " + e);
            return null;
        }

        if(sz == -1){
            Log.i(TAG, "end of input");
            //TODO: to fake a EOF
            return null;
        }

        ByteBuffer buf;
//        if( (mReadCount % 8) == 0 ){
        if(false){ // no stolen frame
            // generate NO_DATA frame in every eight frames
            buffer[0] = (1<<2) | (15 << 3);
            buf = ByteBuffer.wrap(buffer, 0, 1);
        } else {
            buf = ByteBuffer.wrap(buffer);
        }
        return buf;
    }

    private void writeOutput(byte[] data, int offset, int size ){
        try{
            mOs.write(data, offset, size);
        } catch (Exception e){
            Log.w(TAG, "exception in writing: " + e);
        }
    }


    private final JitterBuffer<ByteBuffer> mJitterBuffer =
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

    private int mReadCount = 0;
    private boolean mStolen = false;

    private int mConsecutiveLostCount = 0;
    boolean mbAwaitFirst;
    private boolean mRunning = false;

    InputStream mIs;
    OutputStream mOs;

    Context mContext;

    static final String AMR_FILE_HEADER_SINGLE_CHANNEL = "#!AMR\n";
    static final int COMPRESSED_20MS_AUDIO_SIZE  = 20;
    private static final String TAG = "AudioRxPath";
}

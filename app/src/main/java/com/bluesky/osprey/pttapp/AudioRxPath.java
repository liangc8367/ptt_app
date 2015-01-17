package com.bluesky.osprey.pttapp;

import com.bluesky.JitterBuffer;

import java.nio.ByteBuffer;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;
/**
 * AudioRxPath polls received audio packet from JitterBuffer, and play it to speaker;
 *
 * it provides:
 * start()
 * stop()
 * cleanup()
 *
 * Created by liangc on 17/01/15.
 */
public class AudioRxPath {
    public class AudioTrackConfiguration{
        static final int AUDIO_SAMPLE_RATE = 8000; // 8KHz
        static final int BUFFER_SIZE_MULTIPLIER = 10;
        static final int AUDIO_PCM20MS_SIZE =
                (AUDIO_SAMPLE_RATE *2 * GlobalConstants.CALL_PACKET_INTERVAL /1000);
    }

    public class AudioDecoderConfiguration {
        static final int AUDIO_SAMPLE_RATE = 8000; // 8KHz
        static final int AUDIO_AMR_BITRATE = 7400; // 7.4Kbps
    }

    public AudioRxPath(){

        configureAudioRxPath();

        mAudioThread = new Thread( new Runnable(){
            @Override
            public void run(){

                preloadTone();

                Log.i(TAG, "Audio Rx thread started");
                boolean bAwaitFirst = true;
                while(mRunning){
                    int index;
                    try {
                        index = mDecoder.dequeueInputBuffer(0); // no wait
                    } catch (Exception e){
                        Log.e(TAG, "exception in dequeue: " + e);
                        break;
                    }

                    Log.i(TAG, "decoder input index = " + index);
                    if(index != MediaCodec.INFO_TRY_AGAIN_LATER){
                        ByteBuffer compressedAudio = mJitterBuffer.poll();
                        if( compressedAudio == null ){
                            if( mRunning == false ){
                                Log.i(TAG, "stopped" );
                                break;
                            } else {
                                // fake a noise packet/audio lost
                                Log.i(TAG, "no data from jitter buffer");
                                ++mConsecutiveLostCount;
                                if( mConsecutiveLostCount == GlobalConstants.JITTER_DEPTH ){
                                    Log.i(TAG, "jitter empty");
                                    break;
                                }

//                                compressedAudio = fakeLostPacket();
                                continue; //TODO:
                            }
                        } else {
                            mConsecutiveLostCount = 0;
                        }

                        mDecoderInputBuffers[index].clear();
                        mDecoderInputBuffers[index].put(compressedAudio);

                        mDecoder.queueInputBuffer(
                                index,
                                0, // offset
                                mDecoderInputBuffers[index].remaining(),
                                0, // presenttion timeUS
                                0  // flags);
                        );
                    }

                    // get decompressed audio
                    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                    index = mDecoder.dequeueOutputBuffer(info, 0); // immediate
                    if( index == MediaCodec.INFO_TRY_AGAIN_LATER ){
                        ;
                    } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED ){
                        ;
                    } else if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED ){
                        mDecoderOutputBuffers = mDecoder.getOutputBuffers();
                    } else {
                        Log.i(TAG, "got decompressed audio, index = "
                                + index + ", sz =" + info.size);

                        mDecoderOutputBuffers[index].position(info.offset);
                        mDecoderOutputBuffers[index].limit(info.offset + info.size);

//                        mAudioTrack.write(mDecoderOutputBuffers[index],
//                                info.size,
//                                AudioTrack.WRITE_NON_BLOCKING); //TODO: which write mode is proper?
                        mAudioTrack.write(mDecoderOutputBuffers[index].array(),
                                  mDecoderOutputBuffers[index].arrayOffset(),
                                info.size);
                        if(bAwaitFirst == true ){
                            bAwaitFirst = false;
                            mAudioTrack.play();
                        }

                        mDecoder.releaseOutputBuffer(index, false /* render */);
                    }

                } // end of while(mRunning)

                cleanup();
            }// end of run()
        });
    }


    public void start(){
        mRunning = true;
        mAudioThread.start();

    }

    public void stop(){
        mRunning = false;
        mAudioThread.interrupt();
    }

    /** offer compressed audio data to Rx path
     *
     */
    public boolean offerAudioData(ByteBuffer audio, short sequence){
        return mJitterBuffer.offer(audio, sequence);
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

        mDecoder.start();
        mDecoderInputBuffers = mDecoder.getInputBuffers();
        mDecoderOutputBuffers = mDecoder.getOutputBuffers();
        return true;
    }

    private void cleanup(){
        mAudioTrack.stop();
        mAudioTrack.release();
        mAudioTrack = null;

        mDecoder.stop();
        mDecoder.release();
        mDecoder = null;
    }

    private void preloadTone(){
        ByteBuffer toneBuffer = generateTone();
//        mAudioTrack.write(toneBuffer, toneBuffer.remaining(), AudioTrack.WRITE_NON_BLOCKING);
        mAudioTrack.write(toneBuffer.array(), toneBuffer.arrayOffset(), toneBuffer.remaining());
    }

    private ByteBuffer generateTone(){
        ByteBuffer tone = ByteBuffer.allocate(AudioTrackConfiguration.AUDIO_PCM20MS_SIZE);
        int samples20ms = AudioTrackConfiguration.AUDIO_PCM20MS_SIZE / 2;
        final double frequency = 950.0;
        for( int i = 0; i< samples20ms; ++i){
            double time = i / AudioTrackConfiguration.AUDIO_SAMPLE_RATE;
            double sinValue =
                    Math.sin(2*Math.PI * frequency * time);
            short s = (short)sinValue;
            tone.putShort((short)sinValue);
        }
        return tone;
    }

    private final JitterBuffer<ByteBuffer> mJitterBuffer =
            new JitterBuffer<ByteBuffer>(GlobalConstants.JITTER_DEPTH,
                    GlobalConstants.CALL_PACKET_INTERVAL,
                    GlobalConstants.TIME_UNIT);

    private Thread mAudioThread;
    private MediaCodec mDecoder;
    private ByteBuffer[] mDecoderInputBuffers, mDecoderOutputBuffers;
    private AudioTrack mAudioTrack;
    private int mConsecutiveLostCount = 0;
    private boolean mRunning = false;
    private static final String TAG = "AudioRxPath";
}

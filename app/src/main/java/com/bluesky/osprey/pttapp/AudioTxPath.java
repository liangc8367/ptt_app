package com.bluesky.osprey.pttapp;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.util.Log;

import java.nio.ByteBuffer;

/**
 * Thread driven audio transmission path, which is composed of
 * audio record, encoder, and rtp module.
 *
 *
 *
 * Created by liangc on 10/01/15.
 */
public class AudioTxPath {
    public class AudioRecordConfiguration{
        static final int AUDIO_SAMPLE_RATE = 8000; // 8KHz
        static final int BUFFER_SIZE_MULTIPLIER = 10;
        static final int AUDIO_PCM20MS_SIZE = (AUDIO_SAMPLE_RATE *2 * 20 /1000);
    }

    public class AudioEncoderConfiguration{
        static final int AUDIO_SAMPLE_RATE = 8000; // 8KHz
        static final int AUDIO_AMR_BITRATE = 7400; // 7.4Kbps
    }

    /** ctor */
    public AudioTxPath(){

    }

    public boolean start(){
        boolean res;
        switch (mState) {
            case RUNNING:
                res = true;
            case INITIAL:
                startAudioThread();
                res = true;
            case ZOMBIE:
            default:
                res = false;
        }
        return res;
    }

    public boolean stop(){
        boolean res;
        switch (mState){
            case RUNNING:
            case ZOMBIE:
                mState = State.ZOMBIE;
                res = true;
            case INITIAL:
            default:
                res = false;
        }
        return res;
    }

    /** release all resources hold by AudioTxPath. User shall not use AudioTxPath afterwards */
    public void release(){

    }

    /** private classes, methods, and members */
    private enum State{
        INITIAL,
        RUNNING,
        ZOMBIE
    }

    private void createAudioRecord(){
        int szMinBuffer = AudioRecord.getMinBufferSize(
                AudioRecordConfiguration.AUDIO_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
        );
        Log.i(TAG, "create audioRecord with buffer size as "
                + szMinBuffer * AudioRecordConfiguration.BUFFER_SIZE_MULTIPLIER);

        mAudioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                AudioRecordConfiguration.AUDIO_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                szMinBuffer * AudioRecordConfiguration.BUFFER_SIZE_MULTIPLIER
        );
        mAudioRecord.startRecording();
    }

    private boolean createEncoder(){
        MediaFormat format = new MediaFormat();
        format.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AMR_NB);
        format.setInteger(MediaFormat.KEY_SAMPLE_RATE, AudioEncoderConfiguration.AUDIO_SAMPLE_RATE);
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
        format.setInteger(MediaFormat.KEY_BIT_RATE, AudioEncoderConfiguration.AUDIO_AMR_BITRATE);

        try {
            mMediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AMR_NB);
        } catch (Exception e){
            Log.e(TAG, "failed to create encoder " + e);
            return false;
        }
        mMediaCodec.configure(
                format,
                null /* surface */,
                null /* crypto */,
                MediaCodec.CONFIGURE_FLAG_ENCODE
        );

        mMediaCodec.start();
        mCodecInputBuffers = mMediaCodec.getInputBuffers();
        mCodecOutputBuffers = mMediaCodec.getOutputBuffers();
        return true;
    }

    private void releaseAudioRecord(){
        mAudioRecord.stop();
        mAudioRecord.release();
        mAudioRecord = null;
    }

    private void releaseEncoder(){
        mMediaCodec.stop();
        mMediaCodec.release();
        mMediaCodec = null;
    }

    private void runTxPath(){
        // get encoder input buffer
        int index;
        index = mMediaCodec.dequeueInputBuffer(0); // immediately
        if( index != MediaCodec.INFO_TRY_AGAIN_LATER ){
            // lets get recorded audio
            mCodecInputBuffers[index].clear();

            int szRead = mAudioRecord.read(mCodecInputBuffers[index],
                    AudioRecordConfiguration.AUDIO_PCM20MS_SIZE);
            Log.d(TAG, "got raw audio sample, size = " + szRead + ", index = " + index);

            // encode it
            mMediaCodec.queueInputBuffer(
                    index,
                    0, //offset
                    szRead,
                    0, // presentation time Us
                    0  // flags
            );
        }

        // try to get encoded audio
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        index = mMediaCodec.dequeueOutputBuffer(info, 0);
        if( index == MediaCodec.INFO_TRY_AGAIN_LATER ){
            return;
        }
        if( index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED ){
            return;
        }
        if( index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED){
            mCodecOutputBuffers = mMediaCodec.getOutputBuffers();
            return;
        }
        Log.d(TAG, "got compressed audio, size = " + info.size + ", index = " + index);
        mMediaCodec.releaseOutputBuffer(index, false /* render */);

    }

    private void startAudioThread(){
        Log.i(TAG, "start audio thread");
        mAudioThread    = new Thread (new Runnable(){
            @Override
            public void run(){

                android.os.Process.setThreadPriority(
                        android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

                createAudioRecord();
                if( createEncoder() == false ){
                    releaseAudioRecord();
                    mState = State.ZOMBIE;
                    return;
                }

                while(mState == State.RUNNING){
                    try {
                        runTxPath();
                    } catch (Exception e){
                        Log.e(TAG, "exception in Tx path: " + e);
                        break;
                    }
                }

                releaseEncoder();
                releaseAudioRecord();
                mState = State.ZOMBIE;

                Log.i(TAG, "stopped");
            }
        }, TAG);

        mAudioThread.start();
        mState = State.RUNNING;
    }

    static final String TAG = "AudioTxPath";
    State               mState = State.INITIAL;
    Thread              mAudioThread;
    AudioRecord         mAudioRecord;
    MediaCodec          mMediaCodec;
    ByteBuffer[]        mCodecInputBuffers;
    ByteBuffer[]        mCodecOutputBuffers;
}

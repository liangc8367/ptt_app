package com.bluesky.osprey.pttapp;


import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

/**
 * attempt to play tone by using AudioTrack
 * Created by liangc on 01/02/15.
 */
public class ToneTest {

    private AudioTrack mAudioTrack;

    public ToneTest(){
        configAudioTrack();
    }

    public void playTone(){
        mAudioTrack.write(Tones.TONE_A, 0, Tones.TONE_A.length);
        mAudioTrack.write(Tones.TONE_A, 0, Tones.TONE_A.length);
        mAudioTrack.write(Tones.TONE_A, 0, Tones.TONE_A.length);
        mAudioTrack.write(Tones.TONE_A, 0, Tones.TONE_A.length);
        mAudioTrack.play();
    }

    public void release(){
        mAudioTrack.release();
        mAudioTrack = null;
    }

    private void configAudioTrack(){
        int minBufferSize = AudioTrack.getMinBufferSize(
                8000,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        Log.i(TAG, "min buffer size = " + minBufferSize);

        mAudioTrack = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                8000,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize * 2,
                AudioTrack.MODE_STREAM
        );

    }

    private static final String TAG = "ToneTest";
}

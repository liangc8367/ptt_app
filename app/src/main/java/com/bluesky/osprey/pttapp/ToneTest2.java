package com.bluesky.osprey.pttapp;

import android.media.AudioManager;
import android.media.ToneGenerator;

/**
 * Created by liangc on 01/02/15.
 */
public class ToneTest2 {
    public ToneTest2(){
        mToneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 80);
    }

    public void release(){
        mToneGen.release();
        mToneGen = null;
    }

    public void playTone(){
        mToneGen.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT);
    }
    private ToneGenerator mToneGen;

}

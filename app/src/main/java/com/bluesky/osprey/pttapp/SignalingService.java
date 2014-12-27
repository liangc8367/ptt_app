package com.bluesky.osprey.pttapp;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Looper;
import android.os.HandlerThread;


import android.widget.Toast;
import android.util.Log;

/** Signaling Service is the core of entire PTT application. It's responsible for:
 *
 */
public class SignalingService extends Service {
    private final static String TAG=GlobalConstants.TAG + ":Signaling";
    private Looper mServiceLooper;
//    private ServiceHandler mServiceHandler;

    public SignalingService() {
    }

    @Override
    public void onCreate() {
        Log.i(TAG, "Signaling service creating ...");
//        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Toast.makeText(this, "Signaling service starting", Toast.LENGTH_SHORT).show();
        Log.i(TAG, "Signaling service starting...");
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "Signaling service destory ...");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
}

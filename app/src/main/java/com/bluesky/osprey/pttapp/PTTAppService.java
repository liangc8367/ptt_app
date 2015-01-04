package com.bluesky.osprey.pttapp;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.HandlerThread;

import android.widget.Toast;
import android.util.Log;

import java.net.InetSocketAddress;


/** PTT Application Service is the core of entire PTT application. It's responsible for
 * initialization of all background service threads, such as udp service and signaling.
 */
public class PTTAppService extends Service {

    @Override
    public void onCreate() {
        Log.i(TAG, "Signaling service creating ...");

        // create udp service
        UDPService.Configuration udpSvcConfig = new UDPService.Configuration();
        //TODO: read configuration from database

        udpSvcConfig.addrServer = new InetSocketAddress(
                                    GlobalConstants.TRUNK_CENTER_ADDR,
                                    GlobalConstants.TRUNK_CENTER_PORT);
        udpSvcConfig.addrLocal = new InetSocketAddress(GlobalConstants.LOCAL_PORT);
        mUdpService = new UDPService(udpSvcConfig);

        // create signaling processor
        mSignalingThread = new HandlerThread("Signaling",
                                android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
        mSignalingThread.start();
        mSignaling = new PTTSignaling(mSignalingThread.getLooper(), mUdpService);
        mSignaling.start();

     }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Toast.makeText(this, "Signaling service starting...", Toast.LENGTH_SHORT).show();
        Log.i(TAG, "Signaling service starting...");
        // we want the service to continue running until it is explicitly stopped.
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Toast.makeText(this, "Signaling service stopping...", Toast.LENGTH_SHORT).show();
        Log.i(TAG, "Signaling service destory ...");
        mUdpService.stopService();
        mSignalingThread.quit();

        //TODO: to clean up timer thread, and udp thread
        // better to send a message to Signaling service before quit its looper thread
        // , and then query its state and set null to its reference.
        super.onDestroy();
    }

    public class SignalingBinder extends Binder {
        public PTTSignaling getSignaling(){
            return mSignaling;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
//        throw new UnsupportedOperationException("Not yet implemented");
        return mBinder;
    }

    /** private members */
    HandlerThread   mSignalingThread = null;
    UDPService  mUdpService = null;
    PTTSignaling    mSignaling = null;

    final IBinder   mBinder = new SignalingBinder();

    final static String TAG=GlobalConstants.TAG + ":App";
}

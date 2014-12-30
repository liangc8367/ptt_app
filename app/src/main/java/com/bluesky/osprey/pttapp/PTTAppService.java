package com.bluesky.osprey.pttapp;

import android.app.Service;
import android.content.Intent;
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
        int port    = 32000;
        udpSvcConfig.addrServer = new InetSocketAddress("192.168.0.105", port);
        udpSvcConfig.addrLocal = new InetSocketAddress(port+1);
        mUdpService = new UDPService(udpSvcConfig);

        // create signaling processor
        mSignalingThread = new HandlerThread("Signaling",
                                android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
        mSignalingThread.start();
        mSignaling = new PTTSignaling(mSignalingThread.getLooper(), mUdpService);


        mUdpService.startService();
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

    /** private members */
    HandlerThread   mSignalingThread = null;
    UDPService  mUdpService = null;
    PTTSignaling    mSignaling = null;
    private final static String TAG=GlobalConstants.TAG + ":App";
}

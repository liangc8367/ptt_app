package com.bluesky.osprey.pttapp;

import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;


/**
 * UDP Service is responsible for send/receive packets to/from
 * connected PTTApp server.
 * Since Andriod doesn't support asynchronous UDP socket, I have
 * to implement such asynchronous behaviour in this class, so Signaling
 * service won't be blocked on receiving.
 *
 * UDP Service is a threaded class for receiving, while sedning is done
 * in caller's context.
 *
 * Created by liangc on 28/12/14.
 */
public class UDPService extends Thread{
    /** Configuration for UDP Service */
    static public class Configuration {
        /* local addr & port */
        public InetSocketAddress addrLocal;
        /* remote addr & port */
        public InetSocketAddress addrServer;

        /* thread name, priority */
    }

    /** Completion handler */
    static public interface CompletionHandler {
        /** invoked when receive is done */
        public void completed(DatagramPacket packet);
    }

    /** public methods of UDP Service */

    public UDPService(Configuration config){
        super("UdpSvc");
        mConfig = config;
    }

    public void setCompletionHandler(CompletionHandler handler){
        mRegisteredHandler = handler;
    }

    public boolean startService(){
        if(!bind()){
            return false; //TODO: to rethink this part
        }
        if(!connect()){
            return false;                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               
        }

        try{
            start();
        } catch (IllegalThreadStateException e){
            Log.e(TAG, "UDP Service has already started");
            return false;
        }
        return true;
    }

    public boolean stopService(){
        mRunning = false;
        //TODO: send interrupt
        return true;
    }

    public boolean send(DatagramPacket packet){
        if(mSocket != null){
            try {
                mSocket.send(packet);
            } catch (IOException e){
                Log.e(TAG, "send failed:" + e);
                return false;
            }
            return true;
        }
        return false;
    }

    public boolean send(ByteBuffer payload){
        DatagramPacket pkt  = new DatagramPacket(payload.array(), payload.capacity());
        return send(pkt);
    }


    public void run(){
        Log.e(TAG, "local address=" + mSocket.getLocalAddress() + ":" + mSocket.getLocalPort());
        Log.e(TAG, "remote address =" + mSocket.getInetAddress() + ":" + mSocket.getPort());
        while(mRunning){
            byte[]          rxedBuffer = new byte[MAX_UDP_PACKET_LENGTH];
            DatagramPacket  rxedPacket = new DatagramPacket(rxedBuffer, MAX_UDP_PACKET_LENGTH);
            try {
                mSocket.receive(rxedPacket);
            }catch (IOException e){
                Log.e(TAG, "rxed failed:" + e);
                continue;
            }
            if (mRegisteredHandler != null) {
                mRegisteredHandler.completed(rxedPacket);
            } else {
                rxedPacket = null;
            }

        }
        //TODO: disconnect the socket
    }

    /** synchronous receive,
     *  @NOTE: not implemented yet, better to throw exception
     */
    public void receive(){
        ;
    }

    /** private methods */
    /** bind and connect udp socket per configuration
     *
     * @return true if success, else false
     */
    private boolean connect(){
        try {
//            mSocket = new DatagramSocket(mConfig.addrLocal);
            mSocket.connect(mConfig.addrServer);
        }catch ( Exception e ){
            Log.e(TAG, "failed to connect:" + e);
            mSocket = null;
            return false;
        }
        return true;
    }


    /** private methods */
    /** bind and bind udp socket per configuration
     *
     * @return true if success, else false
     */
    private boolean bind(){
        try {
            mSocket = new DatagramSocket(mConfig.addrLocal);
        }catch ( Exception e ){
            Log.e(TAG, "failed to bind:" + e);
            mSocket = null;
            return false;
        }
        mBound = true;
        return true;
    }


    /** private members */
    Configuration   mConfig = null;
    boolean         mRunning = false;
    boolean         mBound   = false;
    DatagramSocket  mSocket = null;
    CompletionHandler   mRegisteredHandler = null;

    private final static String TAG = GlobalConstants.TAG + ":UDPSvc";
    private final static int MAX_UDP_PACKET_LENGTH = 1000; //TODO: to make it even smaller
}

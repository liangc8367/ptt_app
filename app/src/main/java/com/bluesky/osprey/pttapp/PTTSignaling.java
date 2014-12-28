package com.bluesky.osprey.pttapp;

import java.net.DatagramPacket;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

/**
 * Signaling of PTT App, responsible for:
 * - signaling processing
 * - driving speaker path
 * - driving mic path
 * - launching GUI
 * - interaction with GUI
 *
 * This class has a state-machine
 *
 * Created by liangc on 28/12/14.
 */
public class PTTSignaling extends Handler{

    /** public methods */
    public PTTSignaling(Looper svcLooper, UDPService udpService){
        super(svcLooper);
        mUdpService = udpService;
        mUdpService.setCompletionHandler(mUdpRxHandler);
        mStateNode  =
    }


    /** private methods */
    @Override
    public void handleMessage(Message message){

    }

    /** private members */
    State       mState  = State.UNREGISTERED;
    StateNode   mStateNode = null;
    UDPService  mUdpService = null;
    UdpRxHandler    mUdpRxHandler   = null;

    private class UdpRxHandler implements UDPService.CompletionHandler{
        @Override
        public void completed(DatagramPacket packet) {
            Message msg = Message.obtain(PTTSignaling.this, MSG_RXED_PACKET, packet);
            msg.sendToTarget();
        }
    }



    private final static String TAG=GlobalConstants.TAG + ":Signaling";
    private final static int MSG_RXED_PACKET = 1;

    private enum State {
        UNREGISTERED,
        REGISTERED,
        CALL_RECEIVING,
        CALL_HANG,
        CALL_INITIATIATED,
        CALL_TRANSMITTING
    };

    /** encapsulation of a state */
    private interface StateNode{
        public State handleMessage(Message message);
        public void  entry();
        public void  exit();
    }

    private class StateUnregistered implements StateNode{

        @Override
        public State handleMessage(Message message) {
            return null;
        }

        @Override
        public void entry() {

        }

        @Override
        public void exit() {

        }
    }


}

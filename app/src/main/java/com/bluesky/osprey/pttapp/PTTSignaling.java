package com.bluesky.osprey.pttapp;

import java.util.EnumMap;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.net.DatagramPacket;
import java.nio.ByteBuffer;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.bluesky.protocol.*;


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
    /** state machine for PTT Signaling
     *  @TODO: Right now, I just quickly implemented the state machine directly in java language.
     *         If I have enough time, I would replace the implementation using SMC(State Machine
     *         Compiler).
     */
    public enum State {
        NOT_STARTED,
        STOPPED,
        UNREGISTERED,
        REGISTERED,
        CALL_RECEIVING,
        CALL_HANG,
        CALL_INITIATIATING,
        CALL_TRANSMITTING

    }

    public static final int PTT_PRESSED = 1;
    public static final int PTT_RELEASED    = 0;

    /** public methods */
    public PTTSignaling(Looper svcLooper, UDPService udpService){
        super(svcLooper);
        mSeqNumber  = GlobalConstants.INIT_SEQ_NUMBER;
        mUdpService = udpService;

        mUdpRxHandler = new UdpRxHandler();
        mUdpService.setCompletionHandler(mUdpRxHandler);

        mTimer  = new Timer("SignalingTimer");

        mState = State.NOT_STARTED;
    }

    /** get current state */
    public State getState(){
        return mState;
    }

    /** kick off Signaling */
    public void start(){
        Message msg = Message.obtain(this, MSG_START);
        msg.sendToTarget();
    }

    /** graceful shutdown signaling */
    public void stop(){
        Message msg = Message.obtain(this, MSG_STOP);
        msg.sendToTarget();
    }

     /** try to make a call per current configuration
     *
     * @param pressed non-zero, pressed
     */
    public void pttKey(int pressed){
        Message msg = Message.obtain(this, MSG_PTT, pressed, 0);
        msg.sendToTarget();
    }


    /** handling all messages, may change state afterwards.
     *
     * @param message
     */
    @Override
    public void handleMessage(Message message){
        if( mState == State.STOPPED ){
            return;
        }

        if( message.what == MSG_STOP ){
            cleanup();
            return;
        }

        if( mState == State.NOT_STARTED &&
                message.what == MSG_START)
        {
            mUdpService.startService();
            initializeStateMachine();
            return;
        }

        State oldState = mState;
        State newState = mStateNode.handleMessage(message);
        if( oldState != newState ){
            mStateNode.exit();
            mState = newState;
            mStateNode = mStateMap.get(mState);
            mStateNode.entry();
        }
    }

    /** private methods */
    private void initializeStateMachine(){
        mStateMap = new EnumMap<State, StateNode>(State.class);
        StateNode aState;
        aState = new StateUnregistered();
        mStateMap.put(State.UNREGISTERED, aState);
        aState = new StateRegistered();
        mStateMap.put(State.REGISTERED, aState);
        aState = new StateCallReceiving();
        mStateMap.put(State.CALL_RECEIVING, aState);
        aState = new StateCallHang();
        mStateMap.put(State.CALL_HANG, aState);
        aState = new StateCallInitiatiating();
        mStateMap.put(State.CALL_INITIATIATING, aState);
        aState = new StateCallTransmitting();
        mStateMap.put(State.CALL_TRANSMITTING, aState);

//        mState      = State.UNREGISTERED;
        mState = State.REGISTERED; // TODO: for debug only

        mStateNode  = mStateMap.get(mState);
        mStateNode.entry();
    }

    /** house clean */
    private void cleanup(){
        getLooper().quit();

        mUdpService.stopService();
        mUdpService = null;
        mUdpRxHandler = null;

        mTimer.cancel();
        mTimer  = null;

        mStateNode.exit();
        mStateNode = null;
        mState = State.STOPPED;
    }

    /** send registration */
    private void sendRegistration(){
        Registration reg = new Registration();
        reg.setSequence(++mSeqNumber);
        reg.setSUID(GlobalConstants.SUB_ID); //TODO: read from cnfig
        ByteBuffer payload  = ByteBuffer.allocate(reg.getSize());
        reg.serialize(payload);
        mUdpService.send(payload);
    }

    /** start calling procedure */
    private void sendPreamble(){
        CallInit preamble = new CallInit(GlobalConstants.TGT_ID, GlobalConstants.SUB_ID);
        preamble.setSequence(++mSeqNumber);
        ByteBuffer payload = ByteBuffer.allocate(preamble.getSize());
        preamble.serialize(payload);
        mUdpService.send(payload);
    }

    /** send compressed audio data */
    private void sendCompressedAudioData(ByteBuffer compressedAudio, short seq){
        CallData callData = new CallData(
                GlobalConstants.TGT_ID,
                GlobalConstants.SUB_ID,
                seq,
                compressedAudio);
        callData.setSequence(++mSeqNumber);
        ByteBuffer payload = ByteBuffer.allocate(callData.getSize());
        callData.serialize(payload);
        mUdpService.send(payload);
    }

    /** create timer task */
    private TimerTask creatTimerTask(){
        return new TimerTask(){
            @Override
            public void run() {
                Message msg = Message.obtain(PTTSignaling.this, MSG_TIME_EXPIRED);
                msg.sendToTarget();
            }
        };
    }

    /** private members */
    private final static String TAG=GlobalConstants.TAG + ":Signaling";
    private final static int MSG_START          = 0;
    private final static int MSG_RXED_PACKET    = 1;
    private final static int MSG_TIME_EXPIRED   = 2;
    private final static int MSG_PTT = 3;
    private final static int MSG_TXPATH_STOPPED = 4; // tx path stopped itself
    private final static int MSG_STOP           = 99;



    State       mState  = State.NOT_STARTED;
    StateNode   mStateNode = null;
    EnumMap<State, StateNode>   mStateMap;

    UDPService  mUdpService = null;
    UdpRxHandler    mUdpRxHandler   = null;

    Timer       mTimer;
    short       mSeqNumber;

    private class UdpRxHandler implements UDPService.CompletionHandler{
        @Override
        public void completed(DatagramPacket packet) {
            Message msg = Message.obtain(PTTSignaling.this, MSG_RXED_PACKET, packet);
            msg.sendToTarget();
        }
    }



    /** state abstraction */
    private abstract class StateNode{
        abstract State handleMessage(Message message);
        abstract void  entry();
        abstract void  exit();

    }

    /** concrete states */
    private class StateUnregistered extends StateNode{

        @Override
        public State handleMessage(Message message) {
            switch(message.what){
                case MSG_TIME_EXPIRED:
                    ++mRetryCount;
                    Log.d(TAG, "registrion timer expired, tried " + mRetryCount);
                    if((GlobalConstants.REGISTRATION_MAX_RETRY==0) ||
                            (mRetryCount < GlobalConstants.REGISTRATION_MAX_RETRY)) {
                        sendRegistration();
                        mTimerTask = creatTimerTask();
                        mTimer.schedule(mTimerTask, GlobalConstants.REGISTRATION_RETRY_TIME);
                    }
                    break;
                case MSG_RXED_PACKET:
                    DatagramPacket packet = (DatagramPacket)message.obj;
                    handleRxedPacket(packet);
                    break;
            }
            return getState();
        }

        @Override
        public void entry() {
            sendRegistration();

            mTimerTask = creatTimerTask();
            mTimer.schedule(mTimerTask,GlobalConstants.REGISTRATION_RETRY_TIME);
        }

        @Override
        public void exit() {
            mTimerTask.cancel();
            mTimerTask  = null;
        }


        private void handleRxedPacket(DatagramPacket packet){
            short protoType = ProtocolBase.peepType(ByteBuffer.wrap(packet.getData()));
            if( protoType == ProtocolBase.PTYPE_ACK){
                Ack proto = (Ack)ProtocolFactory.getProtocol(ByteBuffer.wrap(packet.getData()));
                if(proto.getAckType() == Ack.ACKTYPE_POSITIVE){
                    //TODO: validate ack...
                    mState = State.REGISTERED;
                    Log.i(TAG, "rxed registeration ack");
                }
            }
        }

        private TimerTask   mTimerTask  = null;
        private int         mRetryCount = 0;
    }

    /** registered state,
     *  ptt ==> call initiated
     *  callinit ==> call receiving
     *  call term ==> call hang
     */
    private class StateRegistered extends StateNode {

        @Override
        public State handleMessage(Message message) {
            switch(message.what){
                case MSG_PTT:
                    if( message.arg1 == PTT_PRESSED) {
                        mState = State.CALL_INITIATIATING;
                    }
                    break;
                default:
                    break;
            }
            return getState();
        }

        @Override
        public void entry() {
            Log.i(TAG, "Registered");
        }

        @Override
        public void exit() {

        }
    }

    private class StateCallReceiving extends StateNode {

        @Override
        public State handleMessage(Message message) {
            return getState();
        }

        @Override
        public void entry() {
            Log.i(TAG, "Call receiving");
        }

        @Override
        public void exit() {

        }
    }

    private class StateCallHang extends StateNode {

        @Override
        public State handleMessage(Message message) {
            return getState();
        }

        @Override
        public void entry() {
            Log.i(TAG, "Call hang");
        }

        @Override
        public void exit() {

        }
    }

    /** call initiatiating state, to send out 3 preambles, and jump to call transmitting
     *  if no negative ack, or call term, or others call is received from Trunk manager.
     */
    private class StateCallInitiatiating extends StateNode {

        @Override
        public State handleMessage(Message message) {
            switch(message.what) {
                case MSG_TIME_EXPIRED:
                    ++mCallPreambleCount;
                    Log.d(TAG, "Call initiatiating, preamble = " + mCallPreambleCount);
                    if(mCallPreambleCount < GlobalConstants.CALL_PREAMBLE_NUMBER){
                        sendPreamble();
                        mTimerTask = creatTimerTask();
                        mTimer.schedule(mTimerTask, GlobalConstants.CALL_PACKET_INTERVAL);
                    } else {
                        mState = State.CALL_TRANSMITTING;
                    }
                    break;
                case MSG_RXED_PACKET:
                    DatagramPacket packet = (DatagramPacket) message.obj;
                    // TODO: handleRxedPacket(packet);
                    break;
            }
            return getState();
        }

        @Override
        public void entry() {
            ++mCallPreambleCount;
            Log.d(TAG, "Call initiatiating, preamble = " + mCallPreambleCount);

            // send first callInit packet
            sendPreamble();

            // start timer
            mTimerTask = creatTimerTask();
            mTimer.schedule(mTimerTask, GlobalConstants.CALL_PACKET_INTERVAL);

        }

        @Override
        public void exit() {
            // cancel timer
            mTimerTask.cancel();
            mTimerTask  = null;

            // reset preamble count(?)

        }

        /** private methods & members */
        TimerTask   mTimerTask;
        int         mCallPreambleCount = 0;
        short       mCallInitSeq;
    }

    /** call transmitting state, keep transmitting until user dekeys, or receives any signaling
     *  from trunk manager, indicating to stop transmitting.
     */
    private class StateCallTransmitting extends StateNode {

        @Override
        public State handleMessage(Message message) {
            switch(message.what) {
                case MSG_TXPATH_STOPPED:
                    Log.d(TAG, "tx path stopped");
                    mState = State.CALL_HANG;
                    break;

                case MSG_PTT:
                    if( message.arg1 == PTT_RELEASED) {
                        Log.d(TAG, "PTT released");
                        mState = State.CALL_HANG;
                    }
                    break;

                case MSG_RXED_PACKET:
                    DatagramPacket packet = (DatagramPacket) message.obj;
                    // TODO: handleRxedPacket(packet);
                    break;
            }
            return getState();
        }

        @Override
        public void entry() {
            Log.i(TAG, "Call transmitting...");

            mAudioTxSequence = (short)(new Random()).nextInt();
            // create encoder
            mAudioTxPath    = new AudioTxPath();
            mAudioTxPath.setCompletionHandler(new CompressedAudioHandler());
            mAudioTxPath.start();
        }

        @Override
        public void exit() {
            // disable microphone lineup
            mAudioTxPath.stop();
        }


        /** private methods and members */
        private class CompressedAudioHandler implements DataSource.CompletionHandler {

            @Override
            public void dataAvailable(ByteBuffer byteBuffer) {
                sendCompressedAudioData(byteBuffer, ++mAudioTxSequence);
            }

            @Override
            public void onEndOfLife() {
                //TODO: send message about self dead
            }
        }


        AudioTxPath     mAudioTxPath;
        short           mAudioTxSequence;
    }

}

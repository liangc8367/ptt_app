package com.bluesky.osprey.pttapp;

import android.media.AudioManager;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.content.Intent;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;
import android.content.Context;

import com.bluesky.protocol.CallData;
import com.bluesky.protocol.CallTerm;
import com.bluesky.protocol.ProtocolBase;
import com.bluesky.protocol.ProtocolFactory;

import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;


public class MainActivity extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

//        mAudioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
    }

    @Override
    protected void onStop() {
//        stopAudioRxPathTest();
        super.onStop();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void onStartPTTService(View view) {
        Intent intent = new Intent(this, PTTAppService.class);
        startService(intent);

        // start Call Activity
        intent = new Intent(this, CallActivity.class);
        startActivity(intent);


    }

    public void onStopService(View view){
        Intent intent = new Intent(this, PTTAppService.class);
        stopService(intent);


//        if (mUdpService != null) {
//
//            mUdpService.stopService();
//            mAudioRxPath.stop();
//            mUdpService = null;
//            Toast.makeText(this, "stopped receiving compressed audio...", Toast.LENGTH_SHORT).show();
//        }

//        mAudioManager.setSpeakerphoneOn(false);

//        if( mDecoderTest != null ){
//            mDecoderTest.stop();
//            mDecoderTest = null;
//            Toast.makeText(this, "stopped receiving compressed audio...", Toast.LENGTH_SHORT).show();
//        }
    }

    public void onStartTest(View view){
//        startAudioRxPathTest();
    }

    public void onStopTest(View view){
//        stopAudioRxPathTest();
    }

//    private void startAudioRxPathTest(){
//        mAudioRxPath = new AudioRxPath();
//
//        // create udp service
//        UDPService.Configuration udpSvcConfig = new UDPService.Configuration();
//        //TODO: read configuration from database
//
//        udpSvcConfig.addrServer = new InetSocketAddress(
//                GlobalConstants.TRUNK_CENTER_ADDR,
//                GlobalConstants.TRUNK_CENTER_PORT);
//        udpSvcConfig.addrLocal = new InetSocketAddress(GlobalConstants.LOCAL_PORT);
//        mUdpService = new UDPService(udpSvcConfig);
//        mUdpHandler = new UdpHandler();
//        mUdpService.setCompletionHandler(mUdpHandler);
//        mUdpService.startService();
//    }
//
//    private void stopAudioRxPathTest(){
//        if (mUdpService != null) {
//            mUdpService.stopService();
//            mUdpService = null;
//        }
//        if (mAudioRxPath != null){
//            mAudioRxPath.stop();
//            mAudioRxPath = null;
//            Toast.makeText(this, "stopped receiving compressed audio...", Toast.LENGTH_SHORT).show();
//        }
//
//    }
//
//
//    private class UdpHandler implements UDPService.CompletionHandler {
//        @Override
//        public void completed(DatagramPacket packet) {
//
//            short protoType = ProtocolBase.peepType(ByteBuffer.wrap(packet.getData()));
//            switch (protoType) {
//                case ProtocolBase.PTYPE_CALL_INIT:
//                    break;
//                case ProtocolBase.PTYPE_CALL_TERM:
//                    if (mAudioRxPath != null) {
//                        CallTerm callTerm = (CallTerm) ProtocolFactory.getProtocol(packet);
//                        ByteBuffer eof = ByteBuffer.allocate(0);
//                        mAudioRxPath.offerAudioData(eof, callTerm.getAudioSeq());
//                    }
//                    break;
//                case ProtocolBase.PTYPE_CALL_DATA:
//                    if (mAudioRxPath != null) {
//                        CallData callData = (CallData) ProtocolFactory.getProtocol(packet);
//                        ByteBuffer audioPayload = callData.getAudioData();
//                        short seq = callData.getAudioSeq();
//                        mAudioRxPath.offerAudioData(audioPayload, seq);
//                    }
//                    break;
//                default:
//                    break;
//            }
//        }
//    }
//
//    /** private members */
//    UDPService  mUdpService;
//    AudioRxPath mAudioRxPath;
//    UdpHandler  mUdpHandler;

}

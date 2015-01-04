package com.bluesky.osprey.pttapp;

import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.content.Intent;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;

import java.net.InetSocketAddress;


public class MainActivity extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
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

// for debugging purpose only
//        // create udp service
//        UDPService.Configuration udpSvcConfig = new UDPService.Configuration();
//        //TODO: read configuration from database
//
//        udpSvcConfig.addrServer = new InetSocketAddress(
//                GlobalConstants.TRUNK_CENTER_ADDR,
//                GlobalConstants.TRUNK_CENTER_PORT);
//        udpSvcConfig.addrLocal = new InetSocketAddress(GlobalConstants.LOCAL_PORT);
//        mUdpService = new UDPService(udpSvcConfig);
//        mUdpService.startService();

        // start Call Activity
        intent = new Intent(this, CallActivity.class);
        startActivity(intent);
    }

    public void onStopService(View view){
        Intent intent = new Intent(this, PTTAppService.class);
        stopService(intent);
    }

    /** private members */
    UDPService  mUdpService;


}

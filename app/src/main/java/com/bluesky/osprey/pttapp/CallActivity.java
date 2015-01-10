package com.bluesky.osprey.pttapp;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View.OnTouchListener;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;


public class CallActivity extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);

        // setup button callback to handle press/release
        Button btnCall = (Button)findViewById(R.id.btnCall);
        btnCall.setOnTouchListener(new CallButtonListener(this));

        txtViewCallInfo = (TextView)findViewById(R.id.txCallInfo);
        txtViewCallStatus = (TextView)findViewById(R.id.txCallStatus);
        txtViewCallType = (TextView)findViewById(R.id.txCallType);
        txtViewMiscInfo = (TextView)findViewById(R.id.txMiscInfo);

    }

     @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_call, menu);
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

    @Override
    protected void onResume(){
        super.onResume();
        // the activity has become visible, let's update info on GUI
        txtViewMiscInfo.setText("");
        txtViewCallType.setText("Group Call");
        txtViewCallStatus.setText("Idle");
        txtViewCallInfo.setText("");

        signalingStateChanged();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, PTTAppService.class);
        boolean res = bindService(intent, mSignalingConnection, Context.BIND_AUTO_CREATE);
        Log.i(TAG, "bind service result =" + res);

    }

    @Override
    protected void onStop() {
        super.onStop();
        if( mBound ){
            unbindService(mSignalingConnection);
            mBound = false;
            Log.i(TAG, "unbound service");
        }
    }

    public void signalingStateChanged() {
        if (!mBound) {
            txtViewMiscInfo.setText("Not bind");
            txtViewCallStatus.setText("");
            return;
        }

        txtViewMiscInfo.setText("Bound");
        txtViewCallStatus.setText(mSignaling.getState().name());
    }

    /** private inner classes and members */
    private class CallButtonListener implements OnTouchListener {
        /** ctor */
        public CallButtonListener(CallActivity parent){
            mCallActivity = parent;
        }
        @Override
        public boolean onTouch(View v, MotionEvent event){
            if(event.getAction() == MotionEvent.ACTION_DOWN ) {
//                Toast.makeText(mCallActivity, "PTT Pressed...", Toast.LENGTH_SHORT).show();
                Log.i(TAG, "PTT pressed");
                if( mBound ){
                    mSignaling.pttKey(PTTSignaling.PTT_PRESSED);
                }

            } else if (event.getAction() == MotionEvent.ACTION_UP) {
//                Toast.makeText(mCallActivity, "PTT Released...", Toast.LENGTH_SHORT).show();
                Log.i(TAG, "PTT released");
                if( mBound ){
                    mSignaling.pttKey(PTTSignaling.PTT_RELEASED);
                }
            }
            // pretend we didn't consume the event, so the button has it
            // default behaviour unchanged.
            return false;
        }
        private CallActivity mCallActivity = null;
    }

    private ServiceConnection mSignalingConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            PTTAppService.SignalingBinder binder = (PTTAppService.SignalingBinder) service;
            mSignaling = binder.getSignaling();
            mBound = true;
            signalingStateChanged();
        }


        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBound = false;
            mSignaling = null;
            signalingStateChanged();
         }
    };


    TextView txtViewCallInfo;
    TextView txtViewCallStatus;
    TextView txtViewCallType;
    TextView txtViewMiscInfo;

    PTTSignaling    mSignaling = null; // reference to signaling
    boolean         mBound  = false;
    final String TAG = GlobalConstants.TAG + ":CallActivity";
}

package com.bluesky.osprey.pttapp;

import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View.OnTouchListener;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;


public class CallActivity extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);

        // setup button callback to handle press/release
        Button btnCall = (Button)findViewById(R.id.btnCall);
        btnCall.setOnTouchListener(new CallButtonListener(this));

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

        TextView txtViewCallInfo = (TextView)findViewById(R.id.txCallInfo);
        TextView txtViewCallStatus = (TextView)findViewById(R.id.txCallStatus);
        TextView txtViewCallType = (TextView)findViewById(R.id.txCallType);
        TextView txtViewMiscInfo = (TextView)findViewById(R.id.txMiscInfo);

        txtViewMiscInfo.setText("");
        txtViewCallType.setText("Group Call");
        txtViewCallStatus.setText("Idle");
        txtViewCallInfo.setText("");
    }

    /**
     * try to make call per current configuration
     * @param view
     */
    public void onMakeCall(View view){
        Toast.makeText(this, "PTT Pressed...", Toast.LENGTH_SHORT).show();
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
                Toast.makeText(mCallActivity, "PTT Pressed...", Toast.LENGTH_SHORT).show();
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                Toast.makeText(mCallActivity, "PTT Released...", Toast.LENGTH_SHORT).show();
            }
            // pretend we didn't consume the event, so the button has it
            // default behaviour unchanged.
            return false;
        }
        private CallActivity mCallActivity = null;
    }
}

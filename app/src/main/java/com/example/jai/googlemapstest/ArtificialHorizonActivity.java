package com.example.jai.googlemapstest;

import android.content.pm.ActivityInfo;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

public class ArtificialHorizonActivity extends AppCompatActivity {

    private AttitudeIndicator mAttitudeIndicator;
    private Telemetry telemetry;

    //might have to change to float
    private float pitch,roll;

    Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_artificial_horizon);
        mAttitudeIndicator = (AttitudeIndicator) findViewById(R.id.attitude_indicator);

        //sets orientation to landscape
        super.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);


        if(telemetry == null){
            telemetry = new Telemetry(this.getApplicationContext());
        }


        //set up USB and have it start recording values within that class
        telemetry.setUpUsbIfNeeded();

        pitch = 0;
        roll = 0;
        thread.start();

    }

    @Override
    protected void onResume(){
        super.onResume();
    }

    Thread thread = new Thread() {
        @Override
        public void run() {
            while (true) {
                try {
                    sleep(160);
                    roll = (float) (telemetry.planeRoll*(-1.0));
                    pitch = (float) telemetry.planePitch;

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                handler.post(new Runnable() {
                    @Override
                    public void run() {

                        //send those values to the attitude indicator class to display it in the new activity
                        mAttitudeIndicator.setAttitude(pitch, roll);
                    }
                });
            }
        }
    };
    @Override
    public void onDestroy() {

        telemetry.closeDevice();
        super.onDestroy();
    }

}
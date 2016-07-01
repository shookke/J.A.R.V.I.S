package com.example.shookke.jarvis.app;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import com.example.shookke.jarvis.R;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void startListeners(View view){

        // Start the BackgroundService to receive and handle Myo events.
        startService(new Intent(this, MultiMyoBackgroundService.class));

        // Close this activity since BackgroundService will run in the background.
        //finish();
    }

    public void stopListners(View view) {
        stopService(new Intent(this, MultiMyoBackgroundService.class));
    }



}

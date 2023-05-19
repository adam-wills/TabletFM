package com.ece420.lab5;

import android.os.Bundle;
import android.app.Activity;
import android.view.View;

public class PianoActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.virtual_keyboard);
    }


    public void onParamClick(View view) {
        setContentView(R.layout.activity_main);
    }
}

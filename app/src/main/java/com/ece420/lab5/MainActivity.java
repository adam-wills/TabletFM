/*
 * Copyright 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ece420.lab5;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.Manifest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;


public class MainActivity extends Activity
        implements ActivityCompat.OnRequestPermissionsResultCallback {

    // UI Variables
    Button   controlButton;
    TextView statusView;
    EditText modIdxMin, modIdxMax, modFreqFactor;
    Spinner ampSpinner, modSpinner;
    String  nativeSampleRate;
    String  nativeSampleBufSize;
    boolean supportRecording;
    Boolean isPlaying = false;

    // User Input Variables
    int AA = 0, AD = 0, AS = 0, AR = 0, MA = 0, MD = 0, MS = 0, MR = 0;

    // Static Values
    private static final int AUDIO_ECHO_REQUEST = 0;
    private static final int FRAME_SIZE = 1024;
    private static final int MIN_VAL = 0;
    private static final int F_S = 48000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        super.setRequestedOrientation (ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        // Google NDK Stuff
        controlButton = (Button)findViewById((R.id.capture_control_button));
        statusView = (TextView)findViewById(R.id.statusView);
        modIdxMin = (EditText)findViewById(R.id.ModIdxMin);
        modIdxMax = (EditText)findViewById(R.id.ModIdxMax);
        modFreqFactor = (EditText)findViewById(R.id.ModFreqFactor);
        queryNativeAudioParameters();
        // initialize native audio system
        updateNativeAudioUI();
        if (supportRecording) {
            // Native Setting: 48k Hz Sampling Frequency and 1024 Frame Size
            createSLEngine(Integer.parseInt(nativeSampleRate), FRAME_SIZE);
        }

        // Setup UI
        // Setup the two envelope graphs
        final GraphView AmpGraph = (GraphView) findViewById(R.id.AmpGraph);
        final GraphView ModGraph = (GraphView) findViewById(R.id.ModGraph);

        // Initialize the graphs and their data sets
        AmpGraph.setVisibility(View.VISIBLE);
        ModGraph.setVisibility(View.VISIBLE);
        final LineGraphSeries<DataPoint> AmpSeries = new LineGraphSeries<>(new DataPoint[]{
                new DataPoint(0, 0),
                new DataPoint(0, 0),
                new DataPoint(0, 0),
                new DataPoint(0, 0),
                new DataPoint(0, 0)
        });
        final LineGraphSeries<DataPoint> ModSeries = new LineGraphSeries<>(new DataPoint[]{
                new DataPoint(0, 0),
                new DataPoint(0, 0),
                new DataPoint(0, 0),
                new DataPoint(0, 0),
                new DataPoint(0, 0)
        });
        AmpGraph.addSeries(AmpSeries);
        ModGraph.addSeries(ModSeries);
        AmpGraph.getViewport().setYAxisBoundsManual(true);
        ModGraph.getViewport().setYAxisBoundsManual(true);
        AmpGraph.getViewport().setXAxisBoundsManual(true);
        ModGraph.getViewport().setXAxisBoundsManual(true);
        AmpGraph.getViewport().setMinY(0.0);
        AmpGraph.getViewport().setMaxY(1.33);
        ModGraph.getViewport().setMinY(0.0);
        ModGraph.getViewport().setMaxY(1.33);
        AmpGraph.getViewport().setMinX(0.0);
        ModGraph.getViewport().setMinX(0.0);

        // Setup Seekbars
        SeekBar AmpAttack = (SeekBar) findViewById(R.id.AmpAttack);
        SeekBar AmpDecay = (SeekBar) findViewById(R.id.AmpDecay);
        SeekBar AmpSustain = (SeekBar) findViewById(R.id.AmpSustain);
        SeekBar AmpRelease = (SeekBar) findViewById(R.id.AmpRelease);
        SeekBar ModAttack = (SeekBar) findViewById(R.id.ModAttack);
        SeekBar ModDecay = (SeekBar) findViewById(R.id.ModDecay);
        SeekBar ModSustain = (SeekBar) findViewById(R.id.ModSustain);
        SeekBar ModRelease = (SeekBar) findViewById(R.id.ModRelease);

        // Initialize the seekbars
        AmpAttack.setProgress(0);
        AmpDecay.setProgress(0);
        AmpSustain.setProgress(0);
        AmpRelease.setProgress(0);
        ModAttack.setProgress(0);
        ModDecay.setProgress(0);
        ModSustain.setProgress(0);
        ModRelease.setProgress(0);

        // Set the seekbar listeners
        AmpAttack.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
        {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
            {
                int newVal = progress + MIN_VAL;
                AA = newVal;
                AmpSeries.resetData(new DataPoint[]{
                        new DataPoint(0, 0),
                        new DataPoint((double)AA/F_S, 1),
                        new DataPoint(((double)AA/F_S)+((double)AD/F_S), (double)AS/100),
                        new DataPoint(2*(((double)AA/F_S)+((double)AD/F_S)), (double)AS/100),
                        new DataPoint((2*(((double)AA/F_S)+((double)AD/F_S)))+((double)AR/F_S), 0)
                });
                AmpGraph.getViewport().setMaxX((2*(((double)AA/F_S)+((double)AD/F_S)))+((double)AR/F_S));
                setCarrierADSR((float)AA/F_S, (float)AD/F_S, (float)AS/100, (float)AR/F_S);
            }

            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        AmpDecay.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
        {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
            {
                int newVal = progress + MIN_VAL;
                AD = newVal;
                AmpSeries.resetData(new DataPoint[]{
                        new DataPoint(0, 0),
                        new DataPoint((double)AA/F_S, 1),
                        new DataPoint(((double)AA/F_S)+((double)AD/F_S), (double)AS/100),
                        new DataPoint(2*(((double)AA/F_S)+((double)AD/F_S)), (double)AS/100),
                        new DataPoint((2*(((double)AA/F_S)+((double)AD/F_S)))+((double)AR/F_S), 0)
                });
                AmpGraph.getViewport().setMaxX((2*(((double)AA/F_S)+((double)AD/F_S)))+((double)AR/F_S));
                setCarrierADSR((float)AA/F_S, (float)AD/F_S, (float)AS/100, (float)AR/F_S);
            }

            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        AmpSustain.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
        {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
            {
                int newVal = progress + MIN_VAL;
                AS = newVal;
                AmpSeries.resetData(new DataPoint[]{
                        new DataPoint(0, 0),
                        new DataPoint((double)AA/F_S, 1),
                        new DataPoint(((double)AA/F_S)+((double)AD/F_S), (double)AS/100),
                        new DataPoint(2*(((double)AA/F_S)+((double)AD/F_S)), (double)AS/100),
                        new DataPoint((2*(((double)AA/F_S)+((double)AD/F_S)))+((double)AR/F_S), 0)
                });
                AmpGraph.getViewport().setMaxX((2*(((double)AA/F_S)+((double)AD/F_S)))+((double)AR/F_S));
                setCarrierADSR((float)AA/F_S, (float)AD/F_S, (float)AS/100, (float)AR/F_S);
            }

            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        AmpRelease.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
        {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
            {
                int newVal = progress + MIN_VAL;
                AR = newVal;
                AmpSeries.resetData(new DataPoint[]{
                        new DataPoint(0, 0),
                        new DataPoint((double)AA/F_S, 1),
                        new DataPoint(((double)AA/F_S)+((double)AD/F_S), (double)AS/100),
                        new DataPoint(2*(((double)AA/F_S)+((double)AD/F_S)), (double)AS/100),
                        new DataPoint((2*(((double)AA/F_S)+((double)AD/F_S)))+((double)AR/F_S), 0)
                });
                AmpGraph.getViewport().setMaxX((2*(((double)AA/F_S)+((double)AD/F_S)))+((double)AR/F_S));
                setCarrierADSR((float)AA/F_S, (float)AD/F_S, (float)AS/100, (float)AR/F_S);
            }

            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        ModAttack.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
        {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
            {
                int newVal = progress + MIN_VAL;
                MA = newVal;
                ModSeries.resetData(new DataPoint[]{
                        new DataPoint(0, 0),
                        new DataPoint((double)MA/F_S, 1),
                        new DataPoint(((double)MA/F_S)+((double)MD/F_S), (double)MS/100),
                        new DataPoint(2*(((double)MA/F_S)+((double)MD/F_S)), (double)MS/100),
                        new DataPoint((2*(((double)MA/F_S)+((double)MD/F_S)))+((double)MR/F_S), 0)
                });
                ModGraph.getViewport().setMaxX((2*(((double)MA/F_S)+((double)MD/F_S)))+((double)MR/F_S));
                setModADSR((float)MA/F_S, (float)MD/F_S, (float)MS/100, (float)MR/F_S);
            }

            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        ModDecay.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
        {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
            {
                int newVal = progress + MIN_VAL;
                MD = newVal;
                ModSeries.resetData(new DataPoint[]{
                        new DataPoint(0, 0),
                        new DataPoint((double)MA/F_S, 1),
                        new DataPoint(((double)MA/F_S)+((double)MD/F_S), (double)MS/100),
                        new DataPoint(2*(((double)MA/F_S)+((double)MD/F_S)), (double)MS/100),
                        new DataPoint((2*(((double)MA/F_S)+((double)MD/F_S)))+((double)MR/F_S), 0)
                });
                ModGraph.getViewport().setMaxX((2*(((double)MA/F_S)+((double)MD/F_S)))+((double)MR/F_S));
                setModADSR((float)MA/F_S, (float)MD/F_S, (float)MS/100, (float)MR/F_S);
            }

            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        ModSustain.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
        {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
            {
                int newVal = progress + MIN_VAL;
                MS = newVal;
                ModSeries.resetData(new DataPoint[]{
                        new DataPoint(0, 0),
                        new DataPoint((double)MA/F_S, 1),
                        new DataPoint(((double)MA/F_S)+((double)MD/F_S), (double)MS/100),
                        new DataPoint(2*(((double)MA/F_S)+((double)MD/F_S)), (double)MS/100),
                        new DataPoint((2*(((double)MA/F_S)+((double)MD/F_S)))+((double)MR/F_S), 0)
                });
                ModGraph.getViewport().setMaxX((2*(((double)MA/F_S)+((double)MD/F_S)))+((double)MR/F_S));
                setModADSR((float)MA/F_S, (float)MD/F_S, (float)MS/100, (float)MR/F_S);
            }

            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        ModRelease.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
        {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
            {
                int newVal = progress + MIN_VAL;
                MR = newVal;
                ModSeries.resetData(new DataPoint[]{
                        new DataPoint(0, 0),
                        new DataPoint((double)MA/F_S, 1),
                        new DataPoint(((double)MA/F_S)+((double)MD/F_S), (double)MS/100),
                        new DataPoint(2*(((double)MA/F_S)+((double)MD/F_S)), (double)MS/100),
                        new DataPoint((2*(((double)MA/F_S)+((double)MD/F_S)))+((double)MR/F_S), 0)
                });
                ModGraph.getViewport().setMaxX((2*(((double)MA/F_S)+((double)MD/F_S)))+((double)MR/F_S));
                setModADSR((float)MA/F_S, (float)MD/F_S, (float)MS/100, (float)MR/F_S);
            }

            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Initialize the Spinners for the Wave Types
        ampSpinner = (Spinner)findViewById(R.id.AmpWaveSpinner);
        ArrayAdapter<CharSequence> ampAdapter = ArrayAdapter.createFromResource(this, R.array.wave_types, android.R.layout.simple_spinner_item);
        ampAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        ampSpinner.setAdapter(ampAdapter);
        modSpinner = (Spinner)findViewById(R.id.ModWaveSpinner);
        ArrayAdapter<CharSequence> modAdapter = ArrayAdapter.createFromResource(this, R.array.wave_types, android.R.layout.simple_spinner_item);
        modAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modSpinner.setAdapter(modAdapter);

        // Set the selection commands for the spinners
        ampSpinner.setOnItemSelectedListener(new Spinner.OnItemSelectedListener()
        {
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id){
                String selected = parent.getItemAtPosition(pos).toString();
                if(selected == "Sine"){
                    setCarrierWaveform(0);
                }
                else if(selected == "Square"){
                    setCarrierWaveform(1);
                }
                else if(selected == "Saw"){
                    setCarrierWaveform(2);
                }
                else{
                    setCarrierWaveform(3);
                }
            }
            public void onNothingSelected(AdapterView<?> parent){
                // not sure if we have to do anything here
            }
        });
        modSpinner.setOnItemSelectedListener(new Spinner.OnItemSelectedListener()
        {
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id){
                String selected = parent.getItemAtPosition(pos).toString();
                if(selected == "Sine"){
                    setModWaveform(0);
                }
                else if(selected == "Square"){
                    setModWaveform(1);
                }
                else if(selected == "Saw"){
                    setModWaveform(2);
                }
                else{
                    setModWaveform(3);
                }
            }
            public void onNothingSelected(AdapterView<?> parent){
                // not sure if we have to do anything here
            }
        });
    }
    @Override
    protected void onDestroy() {
        if (supportRecording) {
            if (isPlaying) {
                stopPlay();
            }
            deleteSLEngine();
            isPlaying = false;
        }
        super.onDestroy();
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

    private void startEcho() {
        if(!supportRecording){
            return;
        }
        if(Float.valueOf(modIdxMax.getText().toString()) < 0 || Float.valueOf(modIdxMin.getText().toString()) < 0 || Float.valueOf(modFreqFactor.getText().toString()) < 0){
            statusView.setText(getString(R.string.error_invalid_input));
            return;
        }
        if(Float.valueOf(modIdxMax.getText().toString()) <= Float.valueOf(modIdxMin.getText().toString())){
            statusView.setText(getString(R.string.error_invalid_input));
            return;
        }
        if (!isPlaying) {
            if(!createSLBufferQueueAudioPlayer()) {
                statusView.setText(getString(R.string.error_player));
                return;
            }
            if(!createAudioRecorder()) {
                deleteSLBufferQueueAudioPlayer();
                statusView.setText(getString(R.string.error_recorder));
                return;
            }
            startPlay();   // this must include startRecording()
            statusView.setText(getString(R.string.status_echoing));
        } else {
            stopPlay();  //this must include stopRecording()
            updateNativeAudioUI();
            deleteAudioRecorder();
            deleteSLBufferQueueAudioPlayer();
        }
        isPlaying = !isPlaying;
        controlButton.setText(getString((isPlaying == true) ? R.string.StopEcho: R.string.StartEcho));

        if(isPlaying) {
            // set the typed input parameter values and switch context
            setModIdx(Float.valueOf(modIdxMax.getText().toString()), Float.valueOf(modIdxMin.getText().toString()));
            setModToCarRatio(Float.valueOf(modFreqFactor.getText().toString()));
            Intent myIntent = new Intent(MainActivity.this, PianoActivity.class);
            startActivityForResult(myIntent, 0);
        }
    }

    public void onEchoClick(View view) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
                                               PackageManager.PERMISSION_GRANTED) {
            statusView.setText(getString(R.string.status_record_perm));
            ActivityCompat.requestPermissions(
                    this,
                    new String[] { Manifest.permission.RECORD_AUDIO },
                    AUDIO_ECHO_REQUEST);
            return;
        }
        startEcho();
    }

    public void getLowLatencyParameters(View view) {
        updateNativeAudioUI();
        return;
    }

    private void queryNativeAudioParameters() {
        AudioManager myAudioMgr = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        nativeSampleRate  =  myAudioMgr.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
        nativeSampleBufSize =myAudioMgr.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
        int recBufSize = AudioRecord.getMinBufferSize(
                Integer.parseInt(nativeSampleRate),
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        supportRecording = true;
        if (recBufSize == AudioRecord.ERROR ||
            recBufSize == AudioRecord.ERROR_BAD_VALUE) {
            supportRecording = false;
        }
    }

    private void updateNativeAudioUI() {
        if (!supportRecording) {
            statusView.setText(getString(R.string.error_no_mic));
            controlButton.setEnabled(false);
            return;
        }

        //statusView.setText("nativeSampleRate    = " + nativeSampleRate + "\n" +
                //"nativeSampleBufSize = " + nativeSampleBufSize + "\n");

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        /*
         * if any permission failed, the sample could not play
         */
        if (AUDIO_ECHO_REQUEST != requestCode) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }

        if (grantResults.length != 1  ||
            grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            /*
             * When user denied permission, throw a Toast to prompt that RECORD_AUDIO
             * is necessary; also display the status on UI
             * Then application goes back to the original state: it behaves as if the button
             * was not clicked. The assumption is that user will re-click the "start" button
             * (to retry), or shutdown the app in normal way.
             */
            statusView.setText(getString(R.string.error_no_permission));
            Toast.makeText(getApplicationContext(),
                    getString(R.string.prompt_permission),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        /*
         * When permissions are granted, we prompt the user the status. User would
         * re-try the "start" button to perform the normal operation. This saves us the extra
         * logic in code for async processing of the button listener.
         */
        statusView.setText("RECORD_AUDIO permission granted, touch " +
                           getString(R.string.StartEcho) + " to begin");

        // The callback runs on app's thread, so we are safe to resume the action
        startEcho();
    }

    /*
     * Loading our Libs
     */
    static {
        System.loadLibrary("echo");
    }

    /*
     * jni function implementations...
     */
    public static native void createSLEngine(int rate, int framesPerBuf);
    public static native void deleteSLEngine();

    public static native boolean createSLBufferQueueAudioPlayer();
    public static native void deleteSLBufferQueueAudioPlayer();

    public static native boolean createAudioRecorder();
    public static native void deleteAudioRecorder();
    public static native void startPlay();
    public static native void stopPlay();

    public static native void setModADSR(float a, float d, float s, float r);
    public static native void setCarrierADSR(float a, float d, float s, float r);
    public static native void setModIdx(float max, float min);
    public static native void setModToCarRatio(double ratio);
    public static native void setCarrierWaveform(int wave);
    public static native void setModWaveform(int wave);
}

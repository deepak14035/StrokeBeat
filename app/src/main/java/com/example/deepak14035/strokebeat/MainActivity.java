package com.example.deepak14035.strokebeat;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;

import android.Manifest;
import android.content.pm.PackageManager;

import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.os.Build;
import android.os.Handler;

import android.os.Parcelable;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

import com.google.android.gms.awareness.Awareness;
import com.google.android.gms.awareness.snapshot.WeatherResponse;
import com.google.android.gms.awareness.snapshot.WeatherResult;
import com.google.android.gms.awareness.state.Weather;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.util.ArrayList;
import java.util.Random;

public class MainActivity extends AppCompatActivity  implements
        GoogleApiClient.OnConnectionFailedListener,
        ConnectionCallbacks {

    /**
     * The {@link android.support.v4.view.PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link FragmentPagerAdapter} derivative, which will keep every
     * loaded fragment in memory. If this becomes too memory intensive, it
     * may be best to switch to a
     * {@link android.support.v4.app.FragmentStatePagerAdapter}.
     */
    private SectionsPagerAdapter mSectionsPagerAdapter;

    /**
     * The {@link ViewPager} that will host the section contents.
     */
    private ViewPager mViewPager;
    BluetoothDevice bluetoothDevice;
    BluetoothSocket msocket,fallbackSocket;
    BluetoothAdapter mBluetoothAdapter;
    InputStream mmInputStream;
    Thread workerThread;
    byte[] readBuffer;
    int readBufferPosition;
    volatile boolean stopWorker;
    public static String sensordata;
    OutputStream mmOutputStream;
    //TextView showbluetoothdata;
    public static ArrayList<Float> humidityValues = new ArrayList<>();
    public static ArrayList<Float> heartRateValues = new ArrayList<>();
    public static ArrayList<Float> bodyTempValues = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());
        //showbluetoothdata=(TextView) findViewById(R.id.section_label1);
        // Set up the ViewPager with the sections adapter.
        mViewPager = (ViewPager) findViewById(R.id.container);
        mViewPager.setAdapter(mSectionsPagerAdapter);
        //TelephonyManager tManager = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
        bluetoothDevice = getIntent().getExtras().getParcelable("btdevice");

        findBT();
        //openBT();
        if (bluetoothDevice == null) {
            try {
                msocket = (BluetoothSocket) bluetoothDevice.getClass().getMethod("createRfcommSocket", new Class[]{int.class}).invoke(bluetoothDevice, 1);
                msocket.connect();

            } catch (Exception e) {

                Log.d("asd", "fallback failed");
            }
        }

        beginListenForData();
//        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
//        fab.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
//                        .setAction("Action", null).show();
//            }
//        });

    }


    void openBT() throws IOException
    {
        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //Standard SerialPortService ID
        msocket = bluetoothDevice.createRfcommSocketToServiceRecord(uuid);
        msocket.connect();
        mmOutputStream = msocket.getOutputStream();
        mmInputStream = msocket.getInputStream();

        beginListenForData();

    }

    void findBT()
    {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(mBluetoothAdapter == null)
        {
            Log.d("asd","no adapter");
        }

        if(!mBluetoothAdapter.isEnabled())
        {
            Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetooth, 0);
        }

        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if(pairedDevices.size() > 0)
        {
            for(BluetoothDevice device : pairedDevices)
            {
                Log.d("asd",device.getName());
                if(device.getName().equals("HC-05"))
                {
                    bluetoothDevice = device;
                    Log.d("asd","finally");
                    break;
                }
            }
        }
    }


    void beginListenForData()
    {
        final Handler handler = new Handler();
        final byte delimiter = 10; //This is the ASCII code for a newline character

        stopWorker = false;
        readBufferPosition = 0;
        readBuffer = new byte[1024];
        try {
            openBT();
        }catch (Exception e){
            e.printStackTrace();
        }
        workerThread = new Thread(new Runnable()
        {
            public void run()
            {

                while(!Thread.currentThread().isInterrupted() && !stopWorker)
                {
                    try
                    {
                        if(mmInputStream==null)
                            break;
                        int bytesAvailable = mmInputStream.available();
                        if(bytesAvailable > 0)
                        {
                            Log.d("asd","working-"+bytesAvailable);
                            byte[] packetBytes = new byte[bytesAvailable];
                            mmInputStream.read(packetBytes);
                            //asdasd
                            for(int i=0;i<bytesAvailable;i++)
                            {
                                byte b = packetBytes[i];
                                if(b == delimiter)
                                {
                                    byte[] encodedBytes = new byte[readBufferPosition];
                                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                    final String data = new String(encodedBytes, "US-ASCII");
                                    readBufferPosition = 0;
                                    //showbluetoothdata.append(data+"\n");
                                    Log.d("asd",data);
                                    String[] values = data.split(",");
                                    while(humidityValues.size()>=100){
                                        humidityValues.remove(0);
                                    }
                                    while(bodyTempValues.size()>=100){
                                        bodyTempValues.remove(0);
                                    }
                                    while(heartRateValues.size()>=100){
                                        heartRateValues.remove(0);
                                    }
                                    try {
                                        humidityValues.add(Float.parseFloat(values[0]));
                                        bodyTempValues.add(Float.parseFloat(values[1]));
                                        heartRateValues.add(Float.parseFloat(values[2]));
                                    }catch(NumberFormatException e){
                                        e.printStackTrace();
                                    }catch(ArrayIndexOutOfBoundsException e){
                                        e.printStackTrace();
                                    }

                                }
                                else
                                {
                                    if((b>47 && b<58) ||(b>96&&b<123) ||(b>64 &&b<91) || b==44 || b==46)
                                    readBuffer[readBufferPosition++] = b;
                                }
                            }
                        }
                    }
                    catch (IOException ex)
                    {
                        stopWorker = true;
                    }
                }
            }
        });

        workerThread.start();
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

    @Override
    public void onConnected(@Nullable Bundle bundle) {

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment implements Orientation.Listener{
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private final static int REQUEST_PERMISSION_RESULT_CODE = 42;
        private static final String ARG_SECTION_NUMBER = "section_number";
        private Orientation mOrientation;
        private ArrayList<Float> accelerometerValues;
        private ArrayList<Float> tempValues;
        private TextView displayvalues;
        public PlaceholderFragment() {
        }

        private GoogleApiClient mGoogleApiClient;
        private final Handler mHandler = new Handler();
        private Runnable mTimer;
        private double graphLastX1Value = 5d;
        private double graphLastX2Value = 5d;
        private double graphLastX3Value = 5d;
        private double graphLastX4Value = 5d;
        private double graphLastX5Value = 5d;
        private LineGraphSeries<DataPoint> accelSeries= new LineGraphSeries<>();
        private LineGraphSeries<DataPoint> tempSeries= new LineGraphSeries<>();
        private LineGraphSeries<DataPoint> humiditySeries= new LineGraphSeries<>();
        private LineGraphSeries<DataPoint> bodyTempSeries= new LineGraphSeries<>();
        private LineGraphSeries<DataPoint> heartRateSeries= new LineGraphSeries<>();


        public void initGraph(GraphView graph, LineGraphSeries mSeries ) {
            graph.getViewport().setXAxisBoundsManual(true);
            graph.getViewport().setMinX(0);
            graph.getViewport().setMaxX(4);

            graph.getGridLabelRenderer().setLabelVerticalWidth(100);

            // first mSeries is a line
            //mSeries = new LineGraphSeries<>();
            mSeries.setDrawDataPoints(true);
            mSeries.setDrawBackground(true);
            graph.addSeries(mSeries);
        }

        private void calculateStroke(){



        }

        @Override
        public void onResume() {
            super.onResume();
            mTimer = new Runnable() {
                @Override
                public void run() {
                    graphLastX1Value += 0.25d;
                    if(accelerometerValues.size()>0) {
                        accelSeries.appendData(new DataPoint(graphLastX1Value, accelerometerValues.get(accelerometerValues.size() - 1)), true, 22);
                    }
                    mHandler.postDelayed(this, 330);
                }
            };
            mHandler.postDelayed(mTimer, 1500);
            mTimer = new Runnable() {
                @Override
                public void run() {
                    graphLastX2Value += 0.25d;
                    if(tempValues.size()>0) {
                        tempSeries.appendData(new DataPoint(graphLastX2Value, tempValues.get(tempValues.size() - 1)), true, 22);
                    }
                    mHandler.postDelayed(this, 330);
                }
            };
            mHandler.postDelayed(mTimer, 1500);
            mTimer = new Runnable() {
                @Override
                public void run() {
                    graphLastX3Value += 0.25d;
                    Log.d("asd- humidity",humidityValues.size()+"\n");

                    if(humidityValues.size()>0) {
                        humiditySeries.appendData(new DataPoint(graphLastX3Value, humidityValues.get(humidityValues.size() - 1)), true, 22);
                    }
                    mHandler.postDelayed(this, 330);
                }
            };
            mHandler.postDelayed(mTimer, 1500);
            mTimer = new Runnable() {
                @Override
                public void run() {
                    graphLastX4Value += 0.25d;
                    Log.d("asd- temp",tempValues.size()+"\n");
                    if(bodyTempValues.size()>0) {
                        //float randTemp = 37+(float)getRandom();
                        bodyTempSeries.appendData(new DataPoint(graphLastX4Value, bodyTempValues.get(bodyTempValues.size()-1)), true, 22);
                    }
                    mHandler.postDelayed(this, 330);
                }
            };
            mHandler.postDelayed(mTimer, 1500);
            mTimer = new Runnable() {
                @Override
                public void run() {
                    graphLastX5Value += 0.25d;
                    Log.d("asd- heartrate",heartRateValues.size()+"\n");

                    //float randheart = (float) mRand.nextInt();
                    if(heartRateValues.size()>0) {
                        heartRateSeries.appendData(new DataPoint(graphLastX5Value, heartRateValues.get(heartRateValues.size()-1)), true, 22);
                    }
                    mHandler.postDelayed(this, 330);
                }
            };
            mHandler.postDelayed(mTimer, 1500);

//            mTimer = new Runnable() {
//                @Override
//                public void run() {
//                    //detectWeather();
//                    mHandler.postDelayed(this, 330);
//                }
//            };
//            mHandler.postDelayed(mTimer, 1500);
        }

        @Override
        public void onPause() {
            mHandler.removeCallbacks(mTimer);
            super.onPause();
        }

        double mLastRandom = 2;
        Random mRand = new Random();
        private double getRandom() {
            return mLastRandom += mRand.nextDouble()*0.5 - 0.25;
        }

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static PlaceholderFragment newInstance(int sectionNumber) {
            PlaceholderFragment fragment = new PlaceholderFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_SECTION_NUMBER, sectionNumber);
            fragment.setArguments(args);
            return fragment;
        }

        @Override//TODO: what is sensordata
        public void onOrientationChanged(String sensor, float readings) {

            if(getArguments().getInt(ARG_SECTION_NUMBER)==2 &&sensordata!=null) {
                    //accelerometerValues.add(pitch+roll+yaw);
                    //displayvalues.append(sensordata);//check what is sensordata
            }
            if(getArguments().getInt(ARG_SECTION_NUMBER)==2) {
                if(sensor.equals("accel")){
                    while(accelerometerValues.size()>=100){
                        accelerometerValues.remove(0);
                    }
                    accelerometerValues.add(readings);
                } else if(sensor.equals("temp")){
                    while(tempValues.size()>=100){
                        tempValues.remove(0);
                    }
                    tempValues.add(readings);
                }


                //displayvalues.append(pitch + roll + yaw + "\n");

            }
        }

        private boolean checkLocationPermission() {
            Log.e("error+", "permission check");
            if( !hasLocationPermission() ) {
                Log.e("error+", "Does not have location permission granted");
                requestLocationPermission();
                return false;
            }

            return true;
        }

        private void requestLocationPermission() {
            ActivityCompat.requestPermissions(
                    this.getActivity(),
                    new String[]{ Manifest.permission.ACCESS_FINE_LOCATION },
                    REQUEST_PERMISSION_RESULT_CODE );
        }

        private boolean hasLocationPermission() {
            return ContextCompat.checkSelfPermission( this.getActivity(), Manifest.permission.ACCESS_FINE_LOCATION )
                    == PackageManager.PERMISSION_GRANTED;
        }

        @Override
        public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[],
                                               @NonNull int[] grantResults) {
            switch (requestCode) {
                case REQUEST_PERMISSION_RESULT_CODE: {
                    // If request is cancelled, the result arrays are empty.
                    if (grantResults.length > 0
                            && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        detectWeather();
                    } else {
                        Log.e("error+", "Location permission denied.");
                    }
                }
            }
        }

        private void detectWeather() {
            Log.e("error+", "WEATHER WEAHER EWEATHER EWEAETHER.");
            if( !checkLocationPermission() ) {

                return;
            }

            final Task<WeatherResponse> weatherResponseTask = Awareness.getSnapshotClient(this.getActivity()).getWeather().addOnCompleteListener(this.getActivity(), new OnCompleteListener<WeatherResponse>() {
                @Override
                public void onComplete(@NonNull Task<WeatherResponse> task) {

                    Weather weather = task.getResult().getWeather();
                    //displayvalues.append("Temp: " + weather.getTemperature(Weather.FAHRENHEIT) + " " + weather.getFeelsLikeTemperature(Weather.FAHRENHEIT) + "\n");
                    Log.e("Tuts+", "Temp: " + weather.getTemperature(Weather.FAHRENHEIT));
                    Log.e("Tuts+", "Feels like: " + weather.getFeelsLikeTemperature(Weather.FAHRENHEIT));
                }
            });

        }

        @Override
        public void onStart() {
            super.onStart();
            mOrientation.startListening(this);
        }

        @Override
        public void onStop() {
            super.onStop();
            mOrientation.stopListening();
        }



        @Override
        public void onCreate (Bundle savedInstanceState){
            super.onCreate(savedInstanceState);


        }


        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView;
            mOrientation = new Orientation(this.getActivity(),Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_LIGHT);

            accelerometerValues=new ArrayList<>();
            tempValues = new ArrayList<>();
            if(getArguments().getInt(ARG_SECTION_NUMBER)==1){
                rootView = inflater.inflate(R.layout.fragment_main, container, false);
                displayvalues=(TextView) rootView.findViewById(R.id.section_label);
                displayvalues.setText("temp values\n");
                detectWeather();
                Log.e("weather+", "DETECT START DEteCT start");
                //detectWeather();
                Log.e("weather+", "DETECT OVER CDETECT OVEr");
            }else if(getArguments().getInt(ARG_SECTION_NUMBER)==2){
                mOrientation.startListening(this);
                rootView = inflater.inflate(R.layout.show_readings, container, false);
                GraphView accelGraph = (GraphView) rootView.findViewById(R.id.accelerometer_graph);
                GraphView tempGraph = (GraphView) rootView.findViewById(R.id.ambient_temp_graph);
                GraphView humidityGraph = (GraphView) rootView.findViewById(R.id.humidity_graph);
                GraphView bodyTempGraph = (GraphView) rootView.findViewById(R.id.body_temp_graph);
                GraphView heartRateGraph = (GraphView) rootView.findViewById(R.id.heart_rate_graph);
                accelSeries = new LineGraphSeries<>();
                tempSeries = new LineGraphSeries<>();
                humiditySeries = new LineGraphSeries<>();
                bodyTempSeries = new LineGraphSeries<>();
                heartRateSeries = new LineGraphSeries<>();
                initGraph(accelGraph, accelSeries);
                initGraph(tempGraph, tempSeries);
                initGraph(humidityGraph, humiditySeries);
                initGraph(bodyTempGraph, bodyTempSeries);
                initGraph(heartRateGraph, heartRateSeries);
                //displayvalues=(TextView) rootView.findViewById(R.id.section_label1);
                //return rootView;
            }
            else {
                rootView = inflater.inflate(R.layout.show_tips, container, false);
                TextView heatstrokeTextView = (TextView)rootView.findViewById(R.id.heatstroke_tips);
                TextView coldstrokeTextView = (TextView)rootView.findViewById(R.id.coldstroke_tips);
                for (String tips:getResources().getStringArray(R.array.heatstroke_tips)) {
                    heatstrokeTextView.append(tips+"\n\n");
                }
                for (String tips:getResources().getStringArray(R.array.coldstroke_tips)) {
                    coldstrokeTextView.append(tips+"\n\n");
                }
//                TextView textView = (TextView) rootView.findViewById(R.id.section_label);

                //return rootView;
            }
            //if(R.id.section_label1>0){

            //}





            return rootView;
        }
    }

    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            // getItem is called to instantiate the fragment for the given page.
            // Return a PlaceholderFragment (defined as a static inner class below).
            return PlaceholderFragment.newInstance(position + 1);
        }

        @Override
        public int getCount() {
            // Show 3 total pages.
            return 3;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return "SECTION 1";
                case 1:
                    return "SECTION 2";
                case 2:
                    return "SECTION 3";
            }
            return null;
        }
    }
}

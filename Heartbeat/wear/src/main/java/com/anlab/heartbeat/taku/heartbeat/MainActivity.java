package com.anlab.heartbeat.taku.heartbeat;

import android.app.Activity;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;


public class MainActivity extends Activity implements SensorEventListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = MainActivity.class.getSimpleName();
    private final MainActivity self = this;

    private static final String sendHearRateDataPath = "/send/heartrate/data/path";
    private static final String HEART_RATE_DATA_KEY = "heart_rate_key";
    private static final String NOW_UNIXTIME_KEY = "unixtime_key";

    private TextView rate;
    private TextView accuracy;
    private TextView sensorInformation;
    private TextView mTextView;
    private Sensor mHeartRateSensor;
    private static final int SENSOR_TYPE_HEARTRATE = 65538;

    //センサ
    private SensorManager sensorManager;
    private Sensor heartrateSensor;

    private GoogleApiClient googleApiClient;

    private CountDownLatch latch;


    static Boolean pressedFinishButton;

    ArrayList<Integer> wellnessArray;
    ArrayList<Long> timeArray;

    //DataAPI用
    private PutDataMapRequest dataMapRequest;
    private DataMap dataMap;
    private PutDataRequest putDataRequest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        latch = new CountDownLatch(1);
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                rate = (TextView) stub.findViewById(R.id.rate);
                rate.setText("Reading...");

                accuracy = (TextView) stub.findViewById(R.id.accuracy);
                sensorInformation = (TextView) stub.findViewById(R.id.sensor);
                latch.countDown();
//                sensorBtn = (Button) stub.findViewById(R.id.button);
//                sensorBtn.setOnClickListener(sensorBtnClick);
            }
        });
        googleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        pressedFinishButton = false;
        googleApiClient.connect();
        sensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        heartrateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
        if (heartrateSensor != null) {
            sensorManager.registerListener(this,heartrateSensor,(int)1e6);
        } else {
            Log.d(TAG, "heartrateSensor is null");
        }
        wellnessArray = new ArrayList<Integer>();
        timeArray = new ArrayList<Long>();
        new MainActivity.sendHeartRateDataThread();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");
        if (googleApiClient != null && googleApiClient.isConnected()) {
            googleApiClient.disconnect();
        }
        sensorManager.unregisterListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
        sensorManager.unregisterListener(this);
    }

    public void finishActivity() {

        finish();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        try {
            latch.await();
            sensorInformation.setText(Arrays.toString(event.values));
            if (pressedFinishButton == true) {
                finishActivity();
            }
            if (event.values[0] != 0) {
                wellnessArray.add((int) event.values[0]);
                long nowUnixTime = getTime();
                dataThread((int)event.values[0], nowUnixTime);
                accuracy.setText(""+wellnessArray.size());
//                if (wellnessArray.size() > 10) {
//                    finishActivity();
//                }
            }
            Log.d(TAG, Arrays.toString(event.values));
        } catch (InterruptedException e) {
            Log.e(TAG, e.getMessage(), e);
            e.printStackTrace();
        }
    }

    private void dataThread(int heartRateValue, long nowUnixTime) {
        // DataMapインスタンスを生成
        dataMapRequest = PutDataMapRequest.create(sendHearRateDataPath);
        dataMap = dataMapRequest.getDataMap();
        // Dataをセット
        dataMap.putInt(HEART_RATE_DATA_KEY, heartRateValue);
        dataMap.putLong(NOW_UNIXTIME_KEY, nowUnixTime);
        // Dataの更新
        putDataRequest = dataMapRequest.asPutDataRequest();
        PendingResult<DataApi.DataItemResult> pendingResult = Wearable.DataApi.putDataItem(googleApiClient, putDataRequest);
        pendingResult.setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
            @Override
            public void onResult(DataApi.DataItemResult dataItemResult) {
                Log.d(TAG, "onResult : " + dataItemResult.getStatus());
            }
        });
    }

    private long getTime() {
        long utc = System.currentTimeMillis();
        timeArray.add(utc);
        Log.d(TAG, "unixtime : " + utc);
        return utc;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
//        Log.d(TAG, "accuracy changed: " + i);
//        accuracy.setText("Accuracy: " + Integer.toString(i));
    }

    @Override
    public void onConnected(Bundle bundle) {

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }

    private class sendHeartRateDataThread extends Thread{


        public void run() {
            NodeApi.GetConnectedNodesResult nodesResult = Wearable.NodeApi.getConnectedNodes(googleApiClient).await();
            for (Node node : nodesResult.getNodes()) {
                MessageApi.SendMessageResult result = Wearable.MessageApi.sendMessage(googleApiClient, node.getId(), sendHearRateDataPath, null).await();
                if (result.getStatus().isSuccess()) {
                    Log.d(TAG, "message is ok");
                    Log.d(TAG, "To : " + node.getDisplayName());
                } else {
                    Log.d(TAG, "send error");
                }
            }
        }
    }

}

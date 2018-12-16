/*
 * Copyright 2017, The Android Open Source Project
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

package com.example.androidthings.gattserver;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class GattServerActivity extends Activity {
    private static final String TAG = GattServerActivity.class.getSimpleName();

    private static final int READ_PERIOD = 60000;

    Handler mReadHandler;

    /* Local UI */
    private TextView mLocalTimeView;
    private TextView mAdapterAddress;
    /* Bluetooth API */
    private BluetoothManager mBluetoothManager;

    /**
     * Google IoT core connection
     */
    private GoogleIoT googleIoT;

    /**
     * AWS IoT
     */
    private AwsIot awsIot;

    /**
     * Is scanning of LE device in progress
     */
    private boolean mIsScanning;

    /**
     * Current sensor
     */
    private int mCurrentSensorIdx;

    /**
     * Service responsible for BLE communication with remote BLE devices
     */
    private BluetoothLeService mBluetoothLeService;

    /**
     * List of LE sensors
     */
    private List<LeSensor> sensors = new ArrayList<LeSensor>() {
        {
//            add(new LeSensor("ST-1", "24:71:89:C1:44:02"));
            add(new LeSensor("ST-2", "24:71:89:08:BD:82"));
        }
    };

    /**
     * Circular cycle through sensor list.
     *
     * @return next sensor in the list
     */
    private LeSensor nextSensor() {
        if (mCurrentSensorIdx >= (sensors.size() - 1)) {
            mCurrentSensorIdx = 0;
        } else {
            mCurrentSensorIdx++;
        }

        return sensors.get(mCurrentSensorIdx);
    }

    /**
     * Currently queried sensor.
     *
     * @return currently selected sensor
     */
    private LeSensor currentSensor() {

        return sensors.get(mCurrentSensorIdx);
    }

    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    /**
     * ReOccurring event of scanning and reading sensors.
     * <p>
     * It reoccurs each READ_PERIOD.
     */
    private Runnable reader = new Runnable() {
        @Override
        public void run() {
            Log.i(TAG, "Read handler started.");
            if ((mBluetoothLeService != null) && (!mIsScanning)) {
                mIsScanning = true;
                LeSensor currentSensor = nextSensor();
                mBluetoothLeService.scanLeDevice(true, currentSensor.getMac());
                mAdapterAddress.setText("Scanning for " + currentSensor.getName());
            }
            mReadHandler.postDelayed(this, READ_PERIOD);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server);

        googleIoT = new GoogleIoT(getResources());
        awsIot = new AwsIot();

        mLocalTimeView = (TextView) findViewById(R.id.text_time);
        mAdapterAddress = (TextView) findViewById(R.id.text_address);
        mAdapterAddress.setText("Startup...");

        // Devices with a display should not go to sleep
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mBluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();
        // We can't continue without proper Bluetooth support
        if (!checkBluetoothSupport(bluetoothAdapter)) {
            finish();
        }

        // Register for system Bluetooth events
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mBluetoothReceiver, filter);

        if (!bluetoothAdapter.isEnabled()) {
            Log.d(TAG, "Bluetooth is currently disabled...enabling");
            bluetoothAdapter.enable();
        } else {
            Log.d(TAG, "Bluetooth enabled");
//            scanLeDevice(true);
        }

        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        // Register for custom BLE service events
        registerReceiver(mBluetoothLeReceiver, makeBluetoothLeUpdateIntentFilter());

        mReadHandler = new Handler();
        mReadHandler.postDelayed(reader, READ_PERIOD);
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        googleIoT.disconnect();
        awsIot.disconnect();
        unregisterReceiver(mBluetoothReceiver);
    }

    /**
     * Verify the level of Bluetooth support provided by the hardware.
     *
     * @param bluetoothAdapter System {@link BluetoothAdapter}.
     * @return true if Bluetooth is properly supported, false otherwise.
     */
    private boolean checkBluetoothSupport(BluetoothAdapter bluetoothAdapter) {

        if (bluetoothAdapter == null) {
            Log.w(TAG, "Bluetooth is not supported");
            return false;
        }

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.w(TAG, "Bluetooth LE is not supported");
            return false;
        }

        return true;
    }

    /**
     * Listens for Bluetooth adapter events to enable/disable
     * advertising and server functionality.
     */
    private BroadcastReceiver mBluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF);

            switch (state) {
                case BluetoothAdapter.STATE_ON:
//                    startServer();
                    break;
                case BluetoothAdapter.STATE_OFF:
//                    stopServer();
                    break;
                default:
                    // Do nothing
            }

        }
    };

    /**
     * Update graphical UI on devices that support it.
     */
    private void updateLocalUi() {

        mLocalTimeView.setText("foo");
    }

    /*
     * Handling events form BluetoothLeService
     */


    /**
     * Handles events by BluetoothLeService
     */
    private final BroadcastReceiver mBluetoothLeReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                Log.i(TAG, "Device discovered and connected.");
                mAdapterAddress.setText("Connected " + currentSensor().getName());
                mIsScanning = false;
            } else if (BluetoothLeService.ACTION_SCAN_FINISHED.equals(action)) {
                Log.i(TAG, "Device scan finished.");
                mIsScanning = false;
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                Log.i(TAG, "Device disconnected.");
                mIsScanning = false;
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                Log.i(TAG, "Services discovered.");
                mBluetoothLeService.turnOnCharacteristics(HTSensor.HT_CONF);
//                mBluetoothLeService.turnOnCharacteristics(OpticalSensor.OPTICAL_CONF);
                // Wait for sensor to stabilize
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
//                mBluetoothLeService.readCharacteristic(OpticalSensor.OPTICAL_DATA);
                mBluetoothLeService.readCharacteristic(HTSensor.HT_DATA);
                mAdapterAddress.setText("Reading " + currentSensor().getName());
                mIsScanning = false;
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                Log.i(TAG, "Action GATT Data Available");
                String dataType = intent.getStringExtra(BluetoothLeService.DATA_TYPE);
                String strVal = "NA";
                switch (dataType) {
                    case BluetoothLeService.DATA_TYPE_LUX:
                        double lux = intent.getDoubleExtra(BluetoothLeService.EXTRA_DATA, 0.0d);
                        strVal = String.valueOf(lux) + " [lux]";
                        break;
                    case BluetoothLeService.DATA_TYPE_HMDT:
                        double humidity = intent.getDoubleExtra(BluetoothLeService.EXTRA_DATA, 0.0d);
                        strVal = String.valueOf(humidity) + " [%RH]";
                        break;
                    case BluetoothLeService.DATA_TYPE_TEMP:
                        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
                        double temp = intent.getDoubleExtra(BluetoothLeService.EXTRA_DATA, 0.0d);
                        strVal = "{\"sensor_name\":\"" + currentSensor().getName() + "\"" +
                                 ",\"sensor_type\":\"temperature\"" +
                                 ",\"timestamp\": \"" + timestamp + "\"" +
                                 ",\"value\":\"" + String.valueOf(temp) + "\"}";
                        Log.i(TAG, strVal);
//                        googleIoT.publishTelemetry(strVal);
                        break;
                }
                mAdapterAddress.setText("Read " + currentSensor().getName());
                mLocalTimeView.setText(strVal);
                mBluetoothLeService.close();
            }
        }
    };

    /**
     * Creates filter that listens to selected set of BluetoothLeService events.
     *
     * @return
     */
    private static IntentFilter makeBluetoothLeUpdateIntentFilter() {

        final IntentFilter intentFilter = new IntentFilter();

        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_SCAN_FINISHED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);

        return intentFilter;
    }

}

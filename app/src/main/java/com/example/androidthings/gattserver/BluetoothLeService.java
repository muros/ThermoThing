package com.example.androidthings.gattserver;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.util.SparseArray;

import java.util.List;
import java.util.UUID;

public class BluetoothLeService extends Service {

    private static final String TAG = BluetoothLeService.class.getSimpleName();

    /** Scan BLE devices for 10 seconds */
    private static final int SCAN_PERIOD = 10000;

    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_SCAN_FINISHED =
            "com.example.bluetooth.le.ACTION_SCAN_FINISHED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String ACTION_BROADCAST_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_BROADCAST_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.example.bluetooth.le.EXTRA_DATA";
    public final static String DATA_TYPE =
            "com.example.bluetooth.le.DATA_TYPE";
    public final static String DATA_TYPE_TEMP =
            "com.example.bluetooth.le.DATA_TYPE_TEMP";
    public final static String DATA_TYPE_HMDT =
            "com.example.bluetooth.le.DATA_TYPE_HMDT";
    public final static String DATA_TYPE_LUX =
            "com.example.bluetooth.le.DATA_TYPE_LUX";
    public final static String DATA_TYPE_DHT =
            "com.example.bluetooth.le.DATA_TYPE_DHT";

    /** LE scan handler */
    private Handler mLeScanHandler;

    /** Main thread handler - for UI updates */
    Handler handler;

    /** BLE Disconnected */
    private static final int STATE_DISCONNECTED = 0;
    /** BLE Connecting */
    private static final int STATE_CONNECTING = 1;
    /** BLE Connected */
    private static final int STATE_CONNECTED = 2;

    /** Current connection state */
    private int mConnectionState = STATE_DISCONNECTED;

    /** Bluetooth API */
    private BluetoothManager mBluetoothManager;

    /** Is scanning in progress */
    private boolean mScanning;

    /** GATT client for communication with GATT server */
    private BluetoothGatt mBluetoothGatt;

    /** Address of remote BLE device */
    private String mBluetoothDeviceAddress;

    /** List of services on BLE device that is connected */
    private List<BluetoothGattService> mServices;

    /** Example of LUX value form TI SensorTag */
    private short mLux;

    /** MAC address of device currently scanned for */
    private String mScannedMac;

    public class LocalBinder extends Binder {
        BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    private final IBinder mBinder = new LocalBinder();

    @Override
    public void onCreate() {
        handler = new Handler();
        super.onCreate();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        close();

        return super.onUnbind(intent);
    }

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        return true;
    }

    /**
     * Scan for BLE device with specified MAC address.
     *
     * @param enable true to enable scanning
     * @param scannedMac MAC address to scan for
     */
    public void scanLeDevice(final boolean enable, final String scannedMac) {

        BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();
        final BluetoothLeScanner bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();

        if (enable) {
            mLeScanHandler = new Handler();
            mLeScanHandler.postDelayed(new Runnable() {

                @Override
                public void run() {
                    if (mScanning) {
                        mScanning = false;
                        mScannedMac = null;
                        bluetoothLeScanner.stopScan(mLeScanCallback);
                        broadcastUpdate(ACTION_SCAN_FINISHED);
                    }
                }
            }, SCAN_PERIOD);

            mScanning = true;
            mScannedMac = scannedMac;
            bluetoothLeScanner.startScan(mLeScanCallback);
        } else {
            mScanning = false;
            mScannedMac = null;
            bluetoothLeScanner.stopScan(mLeScanCallback);
            broadcastUpdate(ACTION_SCAN_FINISHED);
        }
    }

    /**
     * Callback for BLE scanning.
     *
     * When scanned device if found it is connected.
     */
    private ScanCallback mLeScanCallback = new ScanCallback() {

        @Override
        public void onScanResult(int callbackType, final ScanResult result) {
            super.onScanResult(callbackType, result);


                final BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();
                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        BluetoothDevice device = result.getDevice();
                        String deviceName = device.getName();
                        String deviceMac = device.getAddress();
                        ScanRecord scanRecord = result.getScanRecord();
                        BluetoothLeScanner bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
                        Log.i(TAG, "Device: " + deviceName + " (" + deviceMac + ")");
                        if (mScannedMac.equalsIgnoreCase(deviceMac)) {
                            Log.i(TAG, "Device FOUND.");
                            byte[] manuData = scanRecord.getManufacturerSpecificData(0x0059);
                            Log.d(TAG, "Device manufacturer data: " + manuData);
                            bluetoothLeScanner.stopScan(mLeScanCallback);
                            mScanning = false;
                            if (Dht22.chechSum(manuData)) {
                                broadcastUpdate(ACTION_BROADCAST_DATA_AVAILABLE, manuData);
                            } else {
                                Log.e(TAG, "Wrong checksum on DHT data.");
                                broadcastUpdate(ACTION_SCAN_FINISHED);
                            }
                        }
                    }
                });
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
        }
    };

    /**
     * Callback methods defined by the BLE API.
     */
    private final BluetoothGattCallback mGattCallback =

            new BluetoothGattCallback() {
                @Override
                public void onConnectionStateChange(BluetoothGatt gatt, int status,
                                                    int newState) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        mConnectionState = STATE_CONNECTED;
                        broadcastUpdate(ACTION_GATT_CONNECTED);
                        Log.i(TAG, "Connected to GATT server.");
                        Log.i(TAG, "Attempting to start service discovery:" +
                                mBluetoothGatt.discoverServices());
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        mConnectionState = STATE_DISCONNECTED;
                        broadcastUpdate(ACTION_GATT_DISCONNECTED);
                        Log.i(TAG, "Disconnected from GATT server.");
                    }
                }

                @Override
                public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        Log.i(TAG, "Services discovered " + status);
                        mServices = mBluetoothGatt.getServices();
                        broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
                    } else {
                        Log.w(TAG, "onServicesDiscovered received: " + status);
                    }
                }

                @Override
                public void onCharacteristicRead(BluetoothGatt gatt,
                                                 BluetoothGattCharacteristic characteristic,
                                                 int status) {
                    Log.i(TAG, "Characteristic read.");
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
                    }
                }

                @Override
                public void onCharacteristicChanged(BluetoothGatt gatt,
                                                    BluetoothGattCharacteristic characteristic) {
                    Log.i(TAG, "Characteristic changed.");
//                    broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
                }
            };

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     *
     * @return Return true if the connection is initiated successfully. The connection result
     *         is reported asynchronously through the
     *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     *         callback.
     */
    private boolean connect(final String address) {

        final BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();

        if (bluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found. Unable to connect.");
            return false;
        }
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;

        return true;
    }

    /**
     * Ensure resources are released properly after BLE usage.
     */
    public void close() {
        mBluetoothDeviceAddress = null;
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.disconnect();
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    /**
     * Turn on specified characteristic, this method is specific to TI SensorTag, that
     * has to have byte 0x01 written to turn on certain sensor.
     *
     * @param charToTurnoOnUuid characteristic UUID
     */
    public void turnOnCharacteristics(UUID charToTurnoOnUuid) {

        byte[] on = {0x01};
        writeCharacteristic(charToTurnoOnUuid, on);
    }

    /**
     * Get certain characteristic based on its UUID form list of characteristics that was retreived
     * when GATT was connected. Before calling this method GATT has to be connected otherwise service
     * list is not available.
     *
     * @param charUuid UUID of desired characteristic
     * @return characteristic
     */
    private BluetoothGattCharacteristic getGattCharacteristic(UUID charUuid) {

        BluetoothGattCharacteristic characteristic = null;

        if (mServices == null)
            return null;

        String uuid = null;

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : mServices) {
            uuid = gattService.getUuid().toString();
//            Log.d(TAG, "Service: " + uuid);
            List<BluetoothGattCharacteristic> gattCharacteristics = gattService.getCharacteristics();
            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                uuid = gattCharacteristic.getUuid().toString();
                if (gattCharacteristic.getUuid().equals(charUuid)) {
                    characteristic = gattCharacteristic;
                }
//                Log.d(TAG, "Char: " + uuid);
            }
        }

        return characteristic;
    }

    /**
     * Issue write of characteristic.
     *
     * @param charUuid characteristic UUID
     * @param value byte array to write to characteristic
     */
    private void writeCharacteristic(UUID charUuid, byte[] value) {
        if (mBluetoothGatt == null || mServices == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        BluetoothGattCharacteristic characteristic = getGattCharacteristic(charUuid);
        characteristic.setValue(value);
        mBluetoothGatt.writeCharacteristic(characteristic);
    }

    /**
     * Initiate read of characteristic.
     * Response is handled by mGattCallback
     *
     * @param charUuid characteristic UUID
     */
    public void readCharacteristic(UUID charUuid) {
        if (mBluetoothGatt == null || mServices == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        BluetoothGattCharacteristic characteristic = getGattCharacteristic(charUuid);
        mBluetoothGatt.readCharacteristic(characteristic);
        // response is handled by mGattCallback
    }

    private void runOnUiThread(Runnable runnable) {
        handler.post(runnable);
    }

    /*
     * Broadcasting as a service, back to receiver.
     */

    /**
     * Broadcast simple event, back to receiver.
     *
     * @param action event / action
     */
    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);

        sendBroadcast(intent);
    }

    /**
     * Broadcast action / event and additional data. Used for returning data
     * read from characteristic of BLE sensor.
     *
     * @param action event / data read usually
     * @param characteristic characteristic data
     */
    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {

        final Intent intent = new Intent(action);

        if (OpticalSensor.OPTICAL_DATA.equals(characteristic.getUuid())) {
            intent.putExtra(DATA_TYPE, DATA_TYPE_LUX);
            intent.putExtra(EXTRA_DATA, Double.valueOf((double)OpticalSensor.lux(characteristic.getValue())));
        } else if (HTSensor.HT_DATA.equals(characteristic.getUuid())) {
//            intent.putExtra(DATA_TYPE, DATA_TYPE_HMDT);
            intent.putExtra(DATA_TYPE, DATA_TYPE_TEMP);
//            intent.putExtra(EXTRA_DATA, Double.valueOf((double)HTSensor.humidity(characteristic.getValue())));
            intent.putExtra(EXTRA_DATA, Double.valueOf((double)HTSensor.temp(characteristic.getValue())));
        } else {
            // For all other profiles, writes the data formatted in HEX.
            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                for(byte byteChar : data)
                    stringBuilder.append(String.format("%02X ", byteChar));
                intent.putExtra(EXTRA_DATA, new String(data) + "\n" +
                        stringBuilder.toString());
            }
        }
        sendBroadcast(intent);
    }

    /**
     * Broadcast action / event and additional data. Used for returning data
     * read from manufacturer broadcast data of BLE sensor.
     *
     * @param action event / data read usually
     * @param data broadcast data
     */
    private void broadcastUpdate(final String action,
                                 final byte[] data) {

        final Intent intent = new Intent(action);

        intent.putExtra(DATA_TYPE, DATA_TYPE_DHT);
        intent.putExtra(DATA_TYPE_TEMP, Double.valueOf(Dht22.temp(data)));
        intent.putExtra(DATA_TYPE_HMDT, Double.valueOf(Dht22.humidity(data)));
        sendBroadcast(intent);
    }
}

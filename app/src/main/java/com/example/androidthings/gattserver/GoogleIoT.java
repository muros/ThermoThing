package com.example.androidthings.gattserver;

import android.content.res.Resources;
import android.util.Log;

import com.google.android.things.iotcore.ConnectionCallback;
import com.google.android.things.iotcore.ConnectionParams;
import com.google.android.things.iotcore.IotCoreClient;
import com.google.android.things.iotcore.OnConfigurationListener;
import com.google.android.things.iotcore.TelemetryEvent;

import java.util.concurrent.atomic.AtomicBoolean;

public class GoogleIoT implements OnConfigurationListener {

    private static final String TAG = GoogleIoT.class.getSimpleName();

    private final String DEVICE_ID;

    private final String CLOUD_REGION;

    private String REGISTRY_ID;

    private String PROJECT_ID;

    private AtomicBoolean ready;

    private Resources resources;

    private IotCoreClient iotCoreClient;

    public GoogleIoT(Resources resources) {
        this.resources = resources;
        this.ready =  new AtomicBoolean(false);

        PROJECT_ID = resources.getString(R.string.projectId);
        REGISTRY_ID = resources.getString(R.string.registryId);
        CLOUD_REGION = resources.getString(R.string.cloudRegion);
        DEVICE_ID = resources.getString(R.string.deviceId);

        initializeIfNeeded();
    }

    public void disconnect() {
        if ((iotCoreClient != null) && (iotCoreClient.isConnected())) {
            iotCoreClient.disconnect();
            iotCoreClient = null;
        }
        Log.i(TAG, "Connection to IoT core closed.");
    }

    public void publishTelemetry(String payload) {
        Log.d(TAG, "Publishing telemetry: " + payload);
        if (iotCoreClient == null) {
            Log.w(TAG, "Ignoring sensor readings because IotCoreClient is not yet active.");
            return;
        }
        TelemetryEvent event = new TelemetryEvent(payload.getBytes(),
                null, TelemetryEvent.QOS_AT_LEAST_ONCE);
        iotCoreClient.publishTelemetry(event);
    }

    private void initializeIfNeeded() {
        ready.set(false);

        AuthKeyProvider akp = new AuthKeyProvider(resources);

        iotCoreClient = new IotCoreClient.Builder()
                .setConnectionParams(getConnectionParams())
                .setKeyPair(akp.getKeyPair())
                .setConnectionCallback(new ConnectionCallback() {
                    @Override
                    public void onConnected() {
                        Log.d(TAG, "Connected to IoT Core");
                        ready.set(true);
                    }

                    @Override
                    public void onDisconnected(int i) {
                        Log.d(TAG, "Disconnected from IoT Core");
                    }
                })
                .setOnConfigurationListener(this)
                .build();
        connectIfNeeded();
    }

    @Override
    public void onConfigurationReceived(byte[] bytes) {
        Log.w(TAG, "Device config event received.");

    }

    private void connectIfNeeded() {
        if (iotCoreClient != null && !iotCoreClient.isConnected()) {
            iotCoreClient.connect();
        }
    }

    private ConnectionParams getConnectionParams() {
        return new ConnectionParams.Builder()
                .setProjectId(PROJECT_ID)
                .setRegistry(REGISTRY_ID, CLOUD_REGION)
                .setDeviceId(DEVICE_ID)
                .build();
    }
}

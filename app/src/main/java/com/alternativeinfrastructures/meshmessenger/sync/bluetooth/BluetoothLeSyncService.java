package com.alternativeinfrastructures.meshmessenger.sync.bluetooth;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import com.alternativeinfrastructures.meshmessenger.R;

public class BluetoothLeSyncService extends Service {
    public static final String TAG = "BluetoothLeSyncService";

    private BluetoothAdapter bluetoothAdapter;
    private boolean started = false;

    public BluetoothLeSyncService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        if (started) {
            Log.d(TAG, "Started again");
            return START_STICKY;
        }

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            Log.w(TAG, "BTLE not supported, killing BTLE sync service");

            // TODO: Killing the service like this feels weird. Maybe whoever started the service should be responsible for this check?
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null)
            Log.wtf(TAG, "bluetoothManager.getAdapter() returned null even though FEATURE_BLUETOOTH_LE is available");

        if (!bluetoothAdapter.isEnabled()) {
            // TODO: Kill and restart the service if the user turns off Bluetooth
            // TODO: Provide some UI for disabling this service (and therefore Bluetooth) so we're not violating Google's recommendations
            Toast.makeText(this, R.string.bluetooth_enabling, Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Enabling Bluetooth");
            bluetoothAdapter.enable();
        }

        // TODO: Do the following in a separate function that we call right now if Bluetooth is already on or in a callback triggered once Bluetooth is enabled:
        // TODO: Initialize BTLE
        // TODO: Start BTLE server
        // TODO: Start scanning for other servers and never stop, blatantly ignoring Google's advice not to do this
        // TODO: Implement a callback when scanning finds a device. If the device also runs this app, trigger a sync with it.
        Log.d(TAG, "Started");
        started = true;
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        started = false;
        Log.d(TAG, "Stopped");
    }
}

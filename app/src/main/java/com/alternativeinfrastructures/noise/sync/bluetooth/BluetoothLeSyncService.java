package com.alternativeinfrastructures.noise.sync.bluetooth;

import android.app.Activity;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import com.alternativeinfrastructures.noise.R;

public class BluetoothLeSyncService extends Service {
    public static final String TAG = "BluetoothLeSyncService";

    private boolean started = false;

    public BluetoothLeSyncService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public static boolean isSupported(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }

    public static boolean isStartable(Context context) {
        if (!isSupported(context))
            return false;

        final BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    public static void startOrPromptBluetooth(Context context) {
        if (!BluetoothLeSyncService.isSupported(context)) {
            Log.d(TAG, "BLE not supported, not starting BLE sync service");
            Toast.makeText(context, R.string.bluetooth_not_supported, Toast.LENGTH_LONG).show();
            return;
        }

        if (BluetoothLeSyncService.isStartable(context)) {
            Log.d(TAG, "BLE supported and Bluetooth is on; starting BLE sync service");
            Intent startSyncServiceIntent = new Intent(context, BluetoothLeSyncService.class);
            context.startService(startSyncServiceIntent);
        } else {
            Log.d(TAG, "BLE supported but Bluetooth is off; will prompt for Bluetooth and start once it's on");
            Toast.makeText(context, R.string.bluetooth_ask, Toast.LENGTH_LONG).show();
            Intent enableBluetoothIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            context.startActivity(enableBluetoothIntent);
            // BluetoothLeSyncServiceManager will turn on this service once Bluetooth is on.
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        if (!isStartable(this)) {
            Log.e(TAG, "Trying to start the service even though Bluetooth is off or BLE is unsupported");
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        if (started) {
            Log.d(TAG, "Started again");
            return START_STICKY;
        }

        // TODO: Start BLE service
        // TODO: Start scanning for other services and never stop, blatantly ignoring Google's advice not to do this
        // TODO: Implement a callback when scanning finds a device that implements syncing

        Log.d(TAG, "Started");
        Toast.makeText(this, R.string.bluetooth_sync_started, Toast.LENGTH_LONG).show();
        started = true;
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Stopped");
        Toast.makeText(this, R.string.bluetooth_sync_stopped, Toast.LENGTH_LONG).show();
        started = false;
        super.onDestroy();
    }
}

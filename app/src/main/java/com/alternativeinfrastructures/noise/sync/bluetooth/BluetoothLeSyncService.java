package com.alternativeinfrastructures.noise.sync.bluetooth;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.util.Log;
import android.widget.Toast;

import com.alternativeinfrastructures.noise.R;

import java.util.UUID;

public class BluetoothLeSyncService extends Service {
    public static final String TAG = "BluetoothLeSyncService";
    public static final UUID SERVICE_UUID_HALF = UUID.fromString("5ac825f4-6084-42a6-0000-000000000000");

    private boolean started = false;
    private ParcelUuid serviceAndInstanceUuid;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeAdvertiser bluetoothLeAdvertiser;

    public BluetoothLeSyncService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public static boolean isSupported(Context context) {
        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE))
            return false;

        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        return bluetoothAdapter != null && bluetoothAdapter.isMultipleAdvertisementSupported();
    }

    public static boolean isStartable(Context context) {
        if (!isSupported(context))
            return false;

        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
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
            context.startService(new Intent(context, BluetoothLeSyncService.class));
        } else {
            Log.d(TAG, "BLE supported but Bluetooth is off; will prompt for Bluetooth and start once it's on");
            Toast.makeText(context, R.string.bluetooth_ask, Toast.LENGTH_LONG).show();
            context.startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
            // BluetoothLeSyncServiceManager will turn on this service once Bluetooth is on.
        }
    }

    private AdvertiseData buildAdvertiseData() {
        AdvertiseData.Builder builder = new AdvertiseData.Builder();
        builder.addServiceUuid(serviceAndInstanceUuid);
        // TODO: Include the (15 byte hash of the (?)) sync bit string/Bloom filter from the database
        builder.setIncludeDeviceName(false);
        return builder.build();
    }

    private AdvertiseSettings buildAdvertiseSettings() {
        AdvertiseSettings.Builder builder = new AdvertiseSettings.Builder();
        builder.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER);
        builder.setTimeout(0); // Advertise as long as Bluetooth is on, blatantly ignoring Google's advice.
        builder.setConnectable(false);
        return builder.build();
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

        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        bluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();

        // First half identifies that the advertisement is for Noise.
        // Second half is a unique identifier per session needed to make a Classic Bluetooth connection.
        // These are not separate UUIDs in the advertisement because a UUID is 16 bytes and ads are limited to 31 bytes.
        UUID instanceUuidHalf = UUID.randomUUID();
        serviceAndInstanceUuid = new ParcelUuid(new UUID(SERVICE_UUID_HALF.getMostSignificantBits(), instanceUuidHalf.getLeastSignificantBits()));

        bluetoothLeAdvertiser.startAdvertising(buildAdvertiseSettings(), buildAdvertiseData(),
                new AdvertiseCallback() {
                    @Override
                    public void onStartFailure(int errorCode) {
                        super.onStartFailure(errorCode);
                        Log.e(TAG, "BLE advertise failed to start: error " + errorCode);
                        stopSelf();
                        // TODO: Handle the failure outside of this callback too (is it safe to restart the advertisement?)
                    }
                });

        // TODO: Start scanning for other services and never stop, blatantly ignoring Google's advice not to do this
        // TODO: Implement a callback when scanning finds a device that implements syncing
        // TODO: BLE is very key-value oriented, so it's not the best interface for actually syncing.
        // Use Classic Bluetooth with service records (using instanceUuid as the record) instead:
        // https://developer.android.com/reference/android/bluetooth/BluetoothDevice.html#createInsecureRfcommSocketToServiceRecord(java.util.UUID)

        Log.d(TAG, "Started");
        Toast.makeText(this, R.string.bluetooth_sync_started, Toast.LENGTH_LONG).show();
        started = true;
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (bluetoothLeAdvertiser != null) {
            bluetoothLeAdvertiser.stopAdvertising(new AdvertiseCallback() {
                @Override
                public void onStartFailure(int errorCode) {
                    super.onStartFailure(errorCode);
                    Log.e(TAG, "BLE advertise failed to stop: error " + errorCode);
                }
            });
        }

        Toast.makeText(this, R.string.bluetooth_sync_stopped, Toast.LENGTH_LONG).show();
        started = false;
        Log.d(TAG, "Stopped");
        super.onDestroy();
    }
}

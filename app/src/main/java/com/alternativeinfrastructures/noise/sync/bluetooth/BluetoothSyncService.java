package com.alternativeinfrastructures.noise.sync.bluetooth;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.util.Log;
import android.widget.Toast;

import com.alternativeinfrastructures.noise.R;

import java.util.UUID;

public class BluetoothSyncService extends Service {
    public static final String TAG = "BluetoothSyncService";
    public static final UUID SERVICE_UUID_HALF = UUID.fromString("5ac825f4-6084-42a6-0000-000000000000");

    private boolean started = false;
    private ParcelUuid serviceAndInstanceUuid;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeAdvertiser bluetoothLeAdvertiser;
    private BluetoothLeScanner bluetoothLeScanner;

    public BluetoothSyncService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    // TODO: On some phones, this incorrectly returns false when the Bluetooth radio is off even though BLE advertise is supported
    public static boolean isSupported(Context context) {
        PackageManager packageManager = context.getPackageManager();
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) || !packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH))
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
        if (!BluetoothSyncService.isSupported(context)) {
            Log.d(TAG, "BLE not supported, not starting BLE sync service");
            Toast.makeText(context, R.string.bluetooth_not_supported, Toast.LENGTH_LONG).show();
            return;
        }

        if (BluetoothSyncService.isStartable(context)) {
            Log.d(TAG, "BLE supported and Bluetooth is on; starting BLE sync service");
            context.startService(new Intent(context, BluetoothSyncService.class));
        } else {
            Log.d(TAG, "BLE supported but Bluetooth is off; will prompt for Bluetooth and start once it's on");
            Toast.makeText(context, R.string.bluetooth_ask, Toast.LENGTH_LONG).show();
            context.startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
            // BluetoothSyncServiceManager will start this service once Bluetooth is on.
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

    private ScanSettings buildScanSettings() {
        ScanSettings.Builder builder = new ScanSettings.Builder();
        builder.setScanMode(ScanSettings.SCAN_MODE_LOW_POWER);

        if (Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            builder.setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE);
            builder.setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT);
            builder.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES);
        }

        return builder.build();
    }

    private boolean matchesServiceUuid(UUID uuid) {
        return SERVICE_UUID_HALF.getMostSignificantBits() == uuid.getMostSignificantBits();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, final int startId) {
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
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();

        // First half identifies that the advertisement is for Noise.
        // Second half is a session-unique identifier needed to make a Classic Bluetooth connection.
        // These are not separate UUIDs in the advertisement because a UUID is 16 bytes and ads are limited to 31 bytes.
        UUID instanceUuidHalf = UUID.randomUUID();
        serviceAndInstanceUuid = new ParcelUuid(new UUID(SERVICE_UUID_HALF.getMostSignificantBits(), instanceUuidHalf.getLeastSignificantBits()));

        bluetoothLeAdvertiser.startAdvertising(buildAdvertiseSettings(), buildAdvertiseData(),
                new AdvertiseCallback() {
                    @Override
                    public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                        super.onStartSuccess(settingsInEffect);
                        Log.d(TAG, "BLE advertise started with UUID " + serviceAndInstanceUuid.toString());
                    }

                    @Override
                    public void onStartFailure(int errorCode) {
                        super.onStartFailure(errorCode);
                        Log.e(TAG, "BLE advertise failed to start: error " + errorCode);
                        stopSelf(startId);
                        // TODO: Is it safe to restart the advertisement?
                    }
                });

        // Scan filters on service UUIDs were completely broken on the devices I tested (fully updated Google Pixel and Moto G4 Play as of March 2017)
        // https://stackoverflow.com/questions/29664316/bluetooth-le-scan-filter-not-working
        bluetoothLeScanner.startScan(null /*filters*/, buildScanSettings(),
                new ScanCallback() {
                    @Override
                    public void onScanFailed(int errorCode) {
                        super.onScanFailed(errorCode);
                        Log.e(TAG, "BLE scan failed to start: error " + errorCode);
                        stopSelf(startId);
                        // TODO: Is it safe to restart the scan?
                    }

                    @Override
                    public void onScanResult(int callbackType, ScanResult result) {
                        super.onScanResult(callbackType, result);

                        if (result.getScanRecord() == null || result.getScanRecord().getServiceUuids() == null)
                            return;

                        for (ParcelUuid uuid : result.getScanRecord().getServiceUuids()) {
                            if (!matchesServiceUuid(uuid.getUuid()))
                                continue;

                            Log.d(TAG, "Found supported device with session UUID " + uuid.toString());

                            // TODO: Connect over Classic Bluetooth pair-free insecure sockets and sync
                            // https://developer.android.com/reference/android/bluetooth/BluetoothDevice.html#createRfcommSocketToServiceRecord(java.util.UUID)
                        }
                    }
                });

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

        bluetoothLeScanner.stopScan(new ScanCallback() {
            @Override
            public void onScanFailed(int errorCode) {
                super.onScanFailed(errorCode);
                Log.e(TAG, "BLE scan failed to stop: error " + errorCode);
            }
        });

        Toast.makeText(this, R.string.bluetooth_sync_stopped, Toast.LENGTH_LONG).show();
        started = false;
        Log.d(TAG, "Stopped");
        super.onDestroy();
    }
}

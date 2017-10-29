package com.alternativeinfrastructures.noise.sync.bluetooth;

import android.annotation.SuppressLint;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
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

import net.vidageek.mirror.dsl.Mirror;

import com.alternativeinfrastructures.noise.R;
import com.alternativeinfrastructures.noise.sync.StreamSync;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BluetoothSyncService extends Service {
    public static final String TAG = "BluetoothSyncService";
    public static final UUID SERVICE_UUID_HALF = UUID.fromString("5ac825f4-6084-42a6-0000-000000000000");

    private static final String FAKE_MAC_ADDRESS = "02:00:00:00:00:00";

    private boolean started = false;
    private UUID serviceUuidAndAddress;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeAdvertiser bluetoothLeAdvertiser;
    private BluetoothLeScanner bluetoothLeScanner;
    private Thread bluetoothClassicServer;
    private ConcurrentHashMap<String, Boolean> openConnections;

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
        return bluetoothAdapter != null && getBluetoothAdapterAddress(bluetoothAdapter) != null && bluetoothAdapter.isMultipleAdvertisementSupported();
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

        // We are including this device's physical MAC address in the advertisement to enable higher bandwidth pair-free communication over Bluetooth Classic sockets.
        // While our communications will always be anonymous by design, this still has privacy implications:
        // If an attacker manages to associate an address with a person, they will be able to determine if that person is nearby as long as the app is installed on that phone.
        builder.addServiceUuid(new ParcelUuid(serviceUuidAndAddress));
        // TODO: Include some portion of the sync bit string/Bloom filter from the database
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

    private static String getBluetoothAdapterAddress(BluetoothAdapter bluetoothAdapter) {
        @SuppressLint("HardwareIds") // Pair-free peer-to-peer communication should qualify as an "advanced telephony use case".
        String address = bluetoothAdapter.getAddress();
        if (address.equals(FAKE_MAC_ADDRESS)) {
            Log.w(TAG, "bluetoothAdapter.getAddress() did not return the physical address");

            // HACK HACK HACK: getAddress is intentionally broken (but not deprecated?!) on Marshmallow and up:
            //   * https://developer.android.com/about/versions/marshmallow/android-6.0-changes.html#behavior-notifications
            //   * https://code.google.com/p/android/issues/detail?id=197718
            // However, we need it to establish pair-free Bluetooth Classic connections:
            //   * All BLE advertisements include a MAC address, but Android broadcasts a temporary, randomly-generated address.
            //   * Currently, it is only possible to listen for connections using the device's physical address.
            // So we use reflection to get it anyway: http://stackoverflow.com/a/35984808
            // This hack won't be necessary if getAddress is ever fixed (unlikely) or (preferably) we can listen using an arbitrary address.

            Object bluetoothManagerService = new Mirror().on(bluetoothAdapter).get().field("mService");
            if (bluetoothManagerService == null) {
                Log.w(TAG, "Couldn't retrieve bluetoothAdapter.mService using reflection");
                return null;
            }

            Object internalAddress = new Mirror().on(bluetoothManagerService).invoke().method("getAddress").withoutArgs();
            if (internalAddress == null || !(internalAddress instanceof String)) {
                Log.w(TAG, "Couldn't call bluetoothAdapter.mService.getAddress() using reflection");
                return null;
            }

            address = (String) internalAddress;
        }

        return address;
    }

    private static boolean matchesServiceUuid(UUID uuid) {
        return SERVICE_UUID_HALF.getMostSignificantBits() == uuid.getMostSignificantBits();
    }

    private static long longFromMacAddress(String macAddress) {
        return Long.parseLong(macAddress.replaceAll(":", ""), 16);
    }

    private static String macAddressFromLong(long macAddressLong) {
        return String.format("%02x:%02x:%02x:%02x:%02x:%02x",
                (byte) (macAddressLong >> 40),
                (byte) (macAddressLong >> 32),
                (byte) (macAddressLong >> 24),
                (byte) (macAddressLong >> 16),
                (byte) (macAddressLong >> 8),
                (byte) (macAddressLong)).toUpperCase();
    }

    private void startBluetoothLeDiscovery(final int startId) {
        bluetoothLeAdvertiser.startAdvertising(buildAdvertiseSettings(), buildAdvertiseData(),
                new AdvertiseCallback() {
                    @Override
                    public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                        super.onStartSuccess(settingsInEffect);
                        Log.d(TAG, "BLE advertise started with UUID " + serviceUuidAndAddress.toString());
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

                            Log.d(TAG, "Found supported device with service UUID " + uuid.toString());

                            // Android uses randomly-generated MAC addresses in its broadcasts, and result.getDevice() uses that broadcast address.
                            // Unfortunately, the device that sent the broadcast can't listen using that MAC address.
                            // As a result, we can't use result.getDevice() to establish a Bluetooth Classic connection.
                            // Instead, we use the MAC address that was included in the service UUID.
                            String remoteDeviceMacAddress = macAddressFromLong(uuid.getUuid().getLeastSignificantBits());
                            BluetoothDevice remoteDevice = bluetoothAdapter.getRemoteDevice(remoteDeviceMacAddress);

                            // TODO: Interrupt this thread when the service is stopping
                            new BluetoothClassicClient(remoteDevice, uuid.getUuid()).start();
                        }
                    }
                });
    }

    private void stopBluetoothLeDiscovery() {
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
    }

    private class BluetoothClassicServer extends Thread {
        private BluetoothServerSocket serverSocket;

        public BluetoothClassicServer(UUID uuid) {
            try {
                serverSocket = bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(TAG, uuid);
            } catch (IOException e) {
                Log.e(TAG, "Failed to set up Bluetooth Classic connection as a server", e);
            }
        }

        @Override
        public void run() {
            BluetoothSocket socket = null;

            while (started) {
                String macAddress = null;
                try {
                    // This will block until there is a connection
                    Log.d(TAG, "Bluetooth Classic server is listening for a client");
                    socket = serverSocket.accept();
                    macAddress = socket.getRemoteDevice().getAddress();
                    if (!openConnections.containsKey(macAddress)) {
                        openConnections.put(macAddress, true);
                        StreamSync.bidirectionalSync(socket.getInputStream(), socket.getOutputStream());
                    }
                    socket.close();
                } catch (IOException connectException) {
                    Log.e(TAG, "Failed to start a Bluetooth Classic connection as a server", connectException);

                    try {
                        if (socket != null)
                            socket.close();
                    } catch (IOException closeException) {
                        Log.e(TAG, "Failed to close a Bluetooth Classic connection as a server", closeException);
                    }
                }
                if (macAddress != null)
                    openConnections.remove(macAddress);
            }
        }
    }

    private class BluetoothClassicClient extends Thread {
        BluetoothSocket socket = null;
        String macAddress = null;

        public BluetoothClassicClient(BluetoothDevice device, UUID uuid) {
            macAddress = device.getAddress();
            try {
                socket = device.createInsecureRfcommSocketToServiceRecord(uuid);
            } catch (IOException connectException) {
                Log.e(TAG, "Failed to set up a Bluetooth Classic connection as a client", connectException);
            }
        }

        @Override
        public void run() {
            // TODO: This should be done with a counter instead (with AtomicInteger)
            if (openConnections.containsKey(macAddress))
                return;

            openConnections.put(macAddress, true);
            try {
                // This will block until there is a connection
                Log.d(TAG, "Bluetooth Classic client is attempting to connect to a server");
                socket.connect();

                StreamSync.bidirectionalSync(socket.getInputStream(), socket.getOutputStream());
                socket.close();
            } catch (IOException connectException) {
                Log.e(TAG, "Failed to start a Bluetooth Classic connection as a client", connectException);

                try {
                    socket.close();
                } catch (IOException closeException) {
                    Log.e(TAG, "Failed to close a Bluetooth Classic connection as a client", closeException);
                }
            }
            openConnections.remove(macAddress);
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

        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        // First half identifies that the advertisement is for Noise.
        // Second half is the MAC address of this device's Bluetooth adapter so that clients know how to connect to it.
        // These are not listed separately in the advertisement because a UUID is 16 bytes and ads are limited to 31 bytes.
        String macAddress = getBluetoothAdapterAddress(bluetoothAdapter);
        if (macAddress == null) {
            Log.e(TAG, "Unable to get this device's Bluetooth MAC address");
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        serviceUuidAndAddress = new UUID(SERVICE_UUID_HALF.getMostSignificantBits(), longFromMacAddress(macAddress));

        bluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        startBluetoothLeDiscovery(startId);

        bluetoothClassicServer = new BluetoothClassicServer(serviceUuidAndAddress);
        bluetoothClassicServer.start();

        openConnections = new ConcurrentHashMap<String, Boolean>();

        Log.d(TAG, "Started");
        Toast.makeText(this, R.string.bluetooth_sync_started, Toast.LENGTH_LONG).show();
        started = true;
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopBluetoothLeDiscovery();

        // TODO: Verify that this actually stops the thread
        bluetoothClassicServer.interrupt();

        // TODO: Stop all BluetoothClassicClient threads

        Toast.makeText(this, R.string.bluetooth_sync_stopped, Toast.LENGTH_LONG).show();
        started = false;
        Log.d(TAG, "Stopped");
        super.onDestroy();
    }
}

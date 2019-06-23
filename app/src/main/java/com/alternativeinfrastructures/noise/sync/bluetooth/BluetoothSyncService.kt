package com.alternativeinfrastructures.noise.sync.bluetooth

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.ParcelUuid
import android.preference.PreferenceManager
import android.util.Log
import android.widget.Toast

import net.vidageek.mirror.dsl.Mirror

import com.alternativeinfrastructures.noise.R
import com.alternativeinfrastructures.noise.sync.StreamSync
import com.alternativeinfrastructures.noise.views.SettingsActivity

import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

class BluetoothSyncService : Service() {

    private var started = false
    private var serviceUuidAndAddress: UUID? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var bluetoothClassicServer: Thread? = null
    private var openConnections: ConcurrentHashMap<String, Boolean>? = null

    override fun onBind(intent: Intent): IBinder? {
        // TODO: Return the communication channel to the service.
        throw UnsupportedOperationException("Not yet implemented")
    }

    enum class CanStartResult {
        CAN_START,
        BLUETOOTH_OR_BLE_UNSUPPORTED,
        BLUETOOTH_OFF,
        BLUETOOTH_ADDRESS_UNAVAILABLE
    }

    private fun buildAdvertiseData(): AdvertiseData {
        val builder = AdvertiseData.Builder()

        // We are including this device's physical MAC address in the advertisement to enable higher bandwidth pair-free communication over Bluetooth Classic sockets.
        // While our communications will always be anonymous by design, this still has privacy implications:
        // If an attacker manages to associate an address with a person, they will be able to determine if that person is nearby as long as the app is installed on that phone.
        builder.addServiceUuid(ParcelUuid(serviceUuidAndAddress))
        // TODO: Include some portion of the sync bit string/Bloom filter from the database
        builder.setIncludeDeviceName(false)
        return builder.build()
    }

    private fun buildAdvertiseSettings(): AdvertiseSettings {
        val builder = AdvertiseSettings.Builder()
        builder.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
        builder.setTimeout(0) // Advertise as long as Bluetooth is on, blatantly ignoring Google's advice.
        builder.setConnectable(false)
        return builder.build()
    }

    private fun buildScanSettings(): ScanSettings {
        val builder = ScanSettings.Builder()
        builder.setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)

        if (Build.VERSION.SDK_INT >= 23 /* Marshmallow */) {
            builder.setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            builder.setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
            builder.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
        }

        return builder.build()
    }

    private fun startBluetoothLeDiscovery(startId: Int) {
        bluetoothLeAdvertiser!!.startAdvertising(buildAdvertiseSettings(), buildAdvertiseData(),
                object : AdvertiseCallback() {
                    override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                        super.onStartSuccess(settingsInEffect)
                        Log.d(TAG, "BLE advertise started")
                    }

                    override fun onStartFailure(errorCode: Int) {
                        super.onStartFailure(errorCode)
                        Log.e(TAG, "BLE advertise failed to start: error $errorCode")
                        stopSelf(startId)
                        // TODO: Is it safe to restart the advertisement?
                    }
                })

        // Scan filters on service UUIDs were completely broken on the devices I tested (fully updated Google Pixel and Moto G4 Play as of March 2017)
        // https://stackoverflow.com/questions/29664316/bluetooth-le-scan-filter-not-working
        // TODO: Check if that's supported using bluetoothAdapter.isOffloadedFilteringSupported/isOffloadedScanBatchingSupported
        // https://stackoverflow.com/questions/26482611/chipsets-devices-supporting-android-5-ble-peripheral-mode
        bluetoothLeScanner!!.startScan(null, buildScanSettings(),
                object : ScanCallback() {
                    override fun onScanFailed(errorCode: Int) {
                        super.onScanFailed(errorCode)
                        Log.e(TAG, "BLE scan failed to start: error $errorCode")
                        stopSelf(startId)
                        // TODO: Is it safe to restart the scan?
                    }

                    override fun onScanResult(callbackType: Int, result: ScanResult) {
                        super.onScanResult(callbackType, result)

                        if (result.scanRecord == null || result.scanRecord!!.serviceUuids == null)
                            return

                        for (uuid in result.scanRecord!!.serviceUuids) {
                            if (!matchesServiceUuid(uuid.uuid))
                                continue

                            Log.d(TAG, "BLE scanner found supported device")

                            // Android uses randomly-generated MAC addresses in its broadcasts, and result.getDevice() uses that broadcast address.
                            // Unfortunately, the device that sent the broadcast can't listen using that MAC address.
                            // As a result, we can't use result.getDevice() to establish a Bluetooth Classic connection.
                            // Instead, we use the MAC address that was included in the service UUID.
                            val remoteDeviceMacAddress = macAddressFromLong(uuid.uuid.leastSignificantBits)
                            val remoteDevice = bluetoothAdapter!!.getRemoteDevice(remoteDeviceMacAddress)

                            // TODO: Interrupt this thread when the service is stopping
                            BluetoothClassicClient(remoteDevice, uuid.uuid).start()
                        }
                    }
                })/*filters*/
    }

    private fun stopBluetoothLeDiscovery() {
        if (!bluetoothAdapter!!.isEnabled)
            return

        if (bluetoothLeAdvertiser != null) {
            bluetoothLeAdvertiser!!.stopAdvertising(object : AdvertiseCallback() {
                override fun onStartFailure(errorCode: Int) {
                    super.onStartFailure(errorCode)
                    Log.e(TAG, "BLE advertise failed to stop: error $errorCode")
                }
            })
        }

        if (bluetoothLeScanner != null) {
            bluetoothLeScanner!!.stopScan(object : ScanCallback() {
                override fun onScanFailed(errorCode: Int) {
                    super.onScanFailed(errorCode)
                    Log.e(TAG, "BLE scan failed to stop: error $errorCode")
                }
            })
        }
    }

    private inner class BluetoothClassicServer(uuid: UUID) : Thread() {
        private var serverSocket: BluetoothServerSocket? = null

        init {
            try {
                serverSocket = bluetoothAdapter!!.listenUsingInsecureRfcommWithServiceRecord(TAG, uuid)
            } catch (e: IOException) {
                Log.e(TAG, "Failed to set up Bluetooth Classic connection as a server", e)
            }

        }

        override fun run() {
            var socket: BluetoothSocket? = null

            while (bluetoothAdapter!!.isEnabled && started) {
                var macAddress: String? = null
                try {
                    // This will block until there is a connection
                    Log.d(TAG, "Bluetooth Classic server is listening for a client")
                    socket = serverSocket!!.accept()
                    macAddress = socket!!.remoteDevice.address
                    if (!openConnections!!.containsKey(macAddress)) {
                        openConnections!![macAddress!!] = true
                        StreamSync.bidirectionalSync(socket.inputStream, socket.outputStream)
                    }
                    socket.close()
                } catch (connectException: IOException) {
                    Log.e(TAG, "Failed to start a Bluetooth Classic connection as a server", connectException)

                    try {
                        socket?.close()
                    } catch (closeException: IOException) {
                        Log.e(TAG, "Failed to close a Bluetooth Classic connection as a server", closeException)
                    }

                }

                if (macAddress != null)
                    openConnections!!.remove(macAddress)
            }
        }
    }

    private inner class BluetoothClassicClient(device: BluetoothDevice, uuid: UUID) : Thread() {
        internal var socket: BluetoothSocket? = null
        internal var macAddress: String? = null

        init {
            macAddress = device.address
            try {
                socket = device.createInsecureRfcommSocketToServiceRecord(uuid)
            } catch (connectException: IOException) {
                Log.e(TAG, "Failed to set up a Bluetooth Classic connection as a client", connectException)
            }

        }

        override fun run() {
            // TODO: This should be done with a counter instead (with AtomicInteger)
            if (openConnections!!.containsKey(macAddress))
                return

            openConnections!![macAddress!!] = true
            try {
                // This will block until there is a connection
                Log.d(TAG, "Bluetooth Classic client is attempting to connect to a server")
                socket!!.connect()

                StreamSync.bidirectionalSync(socket!!.inputStream, socket!!.outputStream)
                socket!!.close()
            } catch (connectException: IOException) {
                Log.e(TAG, "Failed to start a Bluetooth Classic connection as a client", connectException)

                try {
                    socket!!.close()
                } catch (closeException: IOException) {
                    Log.e(TAG, "Failed to close a Bluetooth Classic connection as a client", closeException)
                }

            }

            openConnections!!.remove(macAddress!!)
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (canStart(this) != CanStartResult.CAN_START) {
            Log.e(TAG, "Trying to start the service even though Bluetooth is off or BLE is unsupported")
            stopSelf(startId)
            return Service.START_NOT_STICKY
        }

        if (started) {
            Log.d(TAG, "Started again")
            return Service.START_STICKY
        }

        bluetoothAdapter = getBluetoothAdapter(this)

        // First half identifies that the advertisement is for Noise.
        // Second half is the MAC address of this device's Bluetooth adapter so that clients know how to connect to it.
        // These are not listed separately in the advertisement because a UUID is 16 bytes and ads are limited to 31 bytes.
        val macAddress = getBluetoothAdapterAddress(bluetoothAdapter!!, this)
        if (macAddress == null) {
            Log.e(TAG, "Unable to get this device's Bluetooth MAC address")
            stopSelf(startId)
            return Service.START_NOT_STICKY
        }
        val uuid = UUID(SERVICE_UUID_HALF.mostSignificantBits, longFromMacAddress(macAddress))
        serviceUuidAndAddress = uuid

        bluetoothLeAdvertiser = bluetoothAdapter!!.bluetoothLeAdvertiser
        bluetoothLeScanner = bluetoothAdapter!!.bluetoothLeScanner
        startBluetoothLeDiscovery(startId)

        started = true
        bluetoothClassicServer = BluetoothClassicServer(uuid)
        bluetoothClassicServer!!.start()

        openConnections = ConcurrentHashMap()

        Log.d(TAG, "Started")
        Toast.makeText(this, R.string.bluetooth_sync_started, Toast.LENGTH_LONG).show()
        return Service.START_STICKY
    }

    override fun onDestroy() {
        started = false

        stopBluetoothLeDiscovery()

        // TODO: Verify that this actually stops the thread
        bluetoothClassicServer!!.interrupt()

        // TODO: Stop all BluetoothClassicClient threads

        Toast.makeText(this, R.string.bluetooth_sync_stopped, Toast.LENGTH_LONG).show()
        Log.d(TAG, "Stopped")
        super.onDestroy()
    }

    companion object {
        val TAG = "BluetoothSyncService"
        val SERVICE_UUID_HALF = UUID.fromString("5ac825f4-6084-42a6-0000-000000000000")

        private val FAKE_MAC_ADDRESS = "02:00:00:00:00:00"
        private val MAC_PATTERN = Pattern.compile("\\w\\w:\\w\\w:\\w\\w:\\w\\w:\\w\\w:\\w\\w")

        fun canStart(context: Context): CanStartResult {
            val packageManager = context.packageManager
            val bluetoothAdapter = getBluetoothAdapter(context)
            if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH) || !packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
                return CanStartResult.BLUETOOTH_OR_BLE_UNSUPPORTED
            } else if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
                return CanStartResult.BLUETOOTH_OFF
            } else if (!bluetoothAdapter.isMultipleAdvertisementSupported) {
                return CanStartResult.BLUETOOTH_OR_BLE_UNSUPPORTED
            } else if (getBluetoothAdapterAddress(bluetoothAdapter, context) == null) {
                return CanStartResult.BLUETOOTH_ADDRESS_UNAVAILABLE
            }

            return CanStartResult.CAN_START
        }

        fun startOrPromptBluetooth(context: Context) {
            when (canStart(context)) {
                BluetoothSyncService.CanStartResult.CAN_START -> {
                    Log.d(TAG, "Starting BLE sync service")
                    context.startService(Intent(context, BluetoothSyncService::class.java))
                }
                BluetoothSyncService.CanStartResult.BLUETOOTH_OR_BLE_UNSUPPORTED -> {
                    Log.d(TAG, "BLE not supported, not starting BLE sync service")
                    Toast.makeText(context, R.string.bluetooth_not_supported, Toast.LENGTH_LONG).show()
                }
                BluetoothSyncService.CanStartResult.BLUETOOTH_OFF -> {
                    Log.d(TAG, "BLE supported but Bluetooth is off; will prompt for Bluetooth and start once it's on")
                    Toast.makeText(context, R.string.bluetooth_ask_enable, Toast.LENGTH_LONG).show()
                    context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                }
                BluetoothSyncService.CanStartResult.BLUETOOTH_ADDRESS_UNAVAILABLE -> {
                    Log.d(TAG, "BLE supported but MAC address is unavailable; will prompt for address and start once it's available")
                    Toast.makeText(context, R.string.bluetooth_ask_address, Toast.LENGTH_LONG).show()
                }
            }// BluetoothSyncServiceManager will start this service once Bluetooth is on.
            // TODO: Open the app's settings? Maybe getting the address should be part of onboarding UI
            // BluetoothSyncServiceManager will start this (re)start this service when the address changes.
        }

        private fun getBluetoothAdapter(context: Context): BluetoothAdapter? {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                    ?: return null

            return bluetoothManager.adapter
        }

        private fun getBluetoothAdapterAddress(bluetoothAdapter: BluetoothAdapter, context: Context): String? {
            @SuppressLint("HardwareIds") // Pair-free peer-to-peer communication should qualify as an "advanced telephony use case".
            var address = bluetoothAdapter.address

            if (address == FAKE_MAC_ADDRESS && Build.VERSION.SDK_INT < 26 /* Oreo */) {
                Log.w(TAG, "bluetoothAdapter.getAddress() did not return the physical address")

                // HACK HACK HACK: getAddress is intentionally broken (but not deprecated?!) on Marshmallow and up:
                //   * https://developer.android.com/about/versions/marshmallow/android-6.0-changes.html#behavior-notifications
                //   * https://code.google.com/p/android/issues/detail?id=197718
                // However, we need it to establish pair-free Bluetooth Classic connections:
                //   * All BLE advertisements include a MAC address, but Android broadcasts a temporary, randomly-generated address.
                //   * Currently, it is only possible to listen for connections using the device's physical address.
                // So we use reflection to get it anyway: http://stackoverflow.com/a/35984808
                // This hack won't be necessary if getAddress is ever fixed (unlikely) or (preferably) we can listen using an arbitrary address.

                val bluetoothManagerService = Mirror().on(bluetoothAdapter).get().field("mService")
                if (bluetoothManagerService == null) {
                    Log.w(TAG, "Couldn't retrieve bluetoothAdapter.mService using reflection")
                    return null
                }

                val internalAddress = Mirror().on(bluetoothManagerService).invoke().method("getAddress").withoutArgs()
                if (internalAddress == null || internalAddress !is String) {
                    Log.w(TAG, "Couldn't call bluetoothAdapter.mService.getAddress() using reflection")
                    return null
                }

                address = internalAddress
            }

            // On Oreo and above, Android will throw a SecurityException if we try to get the MAC address with reflection
            // https://android-developers.googleblog.com/2017/04/changes-to-device-identifiers-in.html
            // https://stackoverflow.com/a/35984808/702467
            if (address == FAKE_MAC_ADDRESS) {
                Log.w(TAG, "Android is actively blocking requests to get the MAC address")

                val preferences = PreferenceManager.getDefaultSharedPreferences(context)
                address = preferences.getString(SettingsActivity.KEY_BLUETOOTH_MAC, "")!!.toUpperCase()
                if (!MAC_PATTERN.matcher(address).matches())
                    return null
            }

            return address
        }

        private fun matchesServiceUuid(uuid: UUID): Boolean {
            return SERVICE_UUID_HALF.mostSignificantBits == uuid.mostSignificantBits
        }

        private fun longFromMacAddress(macAddress: String): Long {
            return java.lang.Long.parseLong(macAddress.replace(":".toRegex(), ""), 16)
        }

        private fun macAddressFromLong(macAddressLong: Long): String {
            return String.format("%02x:%02x:%02x:%02x:%02x:%02x",
                    (macAddressLong shr 40).toByte(),
                    (macAddressLong shr 32).toByte(),
                    (macAddressLong shr 24).toByte(),
                    (macAddressLong shr 16).toByte(),
                    (macAddressLong shr 8).toByte(),
                    macAddressLong.toByte()).toUpperCase()
        }
    }
}

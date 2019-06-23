package com.alternativeinfrastructures.noise.sync.bluetooth

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BluetoothSyncServiceManager : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action!!.equals(BluetoothAdapter.ACTION_STATE_CHANGED, ignoreCase = true)) {
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_ON -> BluetoothSyncService.startOrPromptBluetooth(context)
                BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_OFF -> {
                    val stopSyncServiceIntent = Intent(context, BluetoothSyncService::class.java)
                    context.stopService(stopSyncServiceIntent)
                }
            }
        }
    }
}

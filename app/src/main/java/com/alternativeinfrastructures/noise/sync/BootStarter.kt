package com.alternativeinfrastructures.noise.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

import com.alternativeinfrastructures.noise.sync.bluetooth.BluetoothSyncService

class BootStarter : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action!!.equals(Intent.ACTION_BOOT_COMPLETED, ignoreCase = true))
            BluetoothSyncService.startOrPromptBluetooth(context)
    }
}

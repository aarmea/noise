package com.alternativeinfrastructures.noise

import android.app.Application

import com.raizlabs.android.dbflow.config.FlowConfig
import com.raizlabs.android.dbflow.config.FlowManager

import com.alternativeinfrastructures.noise.sync.bluetooth.BluetoothSyncService

class NoiseApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        FlowManager.init(FlowConfig.Builder(this).build())

        BluetoothSyncService.startOrPromptBluetooth(this)
    }
}

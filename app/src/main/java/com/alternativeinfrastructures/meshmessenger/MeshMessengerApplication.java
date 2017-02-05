package com.alternativeinfrastructures.meshmessenger;

import android.app.Application;
import android.content.Intent;

import com.raizlabs.android.dbflow.config.FlowConfig;
import com.raizlabs.android.dbflow.config.FlowManager;

import com.alternativeinfrastructures.meshmessenger.sync.bluetooth.BluetoothLeSyncService;

public class MeshMessengerApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        FlowManager.init(new FlowConfig.Builder(this).build());

        startService(new Intent(this, BluetoothLeSyncService.class));
    }
}

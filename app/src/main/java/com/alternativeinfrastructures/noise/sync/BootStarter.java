package com.alternativeinfrastructures.noise.sync;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.alternativeinfrastructures.noise.sync.bluetooth.BluetoothLeSyncService;

public class BootStarter extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equalsIgnoreCase(Intent.ACTION_BOOT_COMPLETED))
            context.startService(new Intent(context, BluetoothLeSyncService.class));
    }
}

package com.alternativeinfrastructures.noise.sync.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BluetoothSyncServiceManager extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equalsIgnoreCase(BluetoothAdapter.ACTION_STATE_CHANGED)) {
            switch (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                case BluetoothAdapter.STATE_ON:
                    BluetoothSyncService.startOrPromptBluetooth(context);
                    break;
                case BluetoothAdapter.STATE_TURNING_OFF:
                case BluetoothAdapter.STATE_OFF:
                    Intent stopSyncServiceIntent = new Intent(context, BluetoothSyncService.class);
                    context.stopService(stopSyncServiceIntent);
                    break;
            }
        }
    }
}

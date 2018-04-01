package com.alternativeinfrastructures.noise.views;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;

import com.alternativeinfrastructures.noise.R;
import com.alternativeinfrastructures.noise.sync.bluetooth.BluetoothSyncService;

public class SettingsActivity extends AppCompatActivity
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    public static final String TAG = "SettingsActivity";
    public static final String KEY_BLUETOOTH_MAC = "pref_key_bluetooth_mac";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_settings);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        setTitle(R.string.settings_title);

        FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        prefsFragment = new NoisePrefsFragment();
        fragmentTransaction.replace(R.id.content_settings_placeholder, prefsFragment);
        fragmentTransaction.commit();
    }

    @Override
    protected void onResume() {
        super.onResume();
        prefsFragment.getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        prefsFragment.getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
        if (key.equals(KEY_BLUETOOTH_MAC)) {
            Log.d(TAG, "Bluetooth MAC changed, kicking service");
            Intent stopSyncServiceIntent = new Intent(this, BluetoothSyncService.class);
            stopService(stopSyncServiceIntent);
            BluetoothSyncService.startOrPromptBluetooth(this);
        }
    }

    NoisePrefsFragment prefsFragment;

    public static class NoisePrefsFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            addPreferencesFromResource(R.xml.preferences);
        }
    }
}

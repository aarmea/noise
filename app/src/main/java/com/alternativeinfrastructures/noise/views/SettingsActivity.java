package com.alternativeinfrastructures.noise.views;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;

import com.alternativeinfrastructures.noise.R;

public class SettingsActivity extends AppCompatActivity {
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
        NoisePrefsFragment prefsFragment = new NoisePrefsFragment();
        fragmentTransaction.replace(R.id.content_settings_placeholder, prefsFragment);
        fragmentTransaction.commit();
    }

    public static class NoisePrefsFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            addPreferencesFromResource(R.xml.preferences);
        }
    }
}

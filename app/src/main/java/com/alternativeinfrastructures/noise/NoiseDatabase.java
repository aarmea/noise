package com.alternativeinfrastructures.noise;

import com.raizlabs.android.dbflow.annotation.Database;

@Database(name = NoiseDatabase.NAME, version = NoiseDatabase.VERSION, foreignKeyConstraintsEnforced = true)
public class NoiseDatabase {
    public static final String NAME = "NoiseDatabase";
    public static final int VERSION = 1;
}

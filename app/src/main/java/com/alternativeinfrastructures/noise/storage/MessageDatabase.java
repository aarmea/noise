package com.alternativeinfrastructures.noise.storage;

import com.raizlabs.android.dbflow.annotation.Database;

@Database(name = MessageDatabase.NAME, version = MessageDatabase.VERSION)
public class MessageDatabase {
    public static final String NAME = "MessageDatabase";
    public static final int VERSION = 1;
}

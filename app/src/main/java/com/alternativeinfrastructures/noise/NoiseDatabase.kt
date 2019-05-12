package com.alternativeinfrastructures.noise

import com.raizlabs.android.dbflow.annotation.Database

@Database(name = NoiseDatabase.NAME, version = NoiseDatabase.VERSION, foreignKeyConstraintsEnforced = true)
object NoiseDatabase {
    const val NAME = "NoiseDatabase"
    const val VERSION = 2
}

package com.alternativeinfrastructures.noise.models

import com.alternativeinfrastructures.noise.NoiseDatabase
import com.alternativeinfrastructures.noise.models.signal.TypeConverters
import com.raizlabs.android.dbflow.annotation.Column
import com.raizlabs.android.dbflow.annotation.Index
import com.raizlabs.android.dbflow.annotation.PrimaryKey
import com.raizlabs.android.dbflow.annotation.Table
import com.raizlabs.android.dbflow.rx2.structure.BaseRXModel

import org.whispersystems.libsignal.IdentityKey

@Table(database = NoiseDatabase::class)
class RemoteIdentity : BaseRXModel {

    @Column
    @Index
    var username = ""

    @Column
    var deviceId: Int = 0

    @PrimaryKey
    @Column(typeConverter = TypeConverters.IdentityKeyConverter::class)
    lateinit var identityKey: IdentityKey

    constructor(username: String, deviceId: Int, identityKey: IdentityKey) {
        this.username = username
        this.deviceId = deviceId
        this.identityKey = identityKey
    }

    constructor() {}

    override fun equals(`object`: Any?): Boolean {
        if (`object` !is RemoteIdentity)
            return false

        return username == `object`.username &&
                deviceId == `object`.deviceId &&
                identityKey == `object`.identityKey
    }
}

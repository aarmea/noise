package com.alternativeinfrastructures.noise.models;

import com.alternativeinfrastructures.noise.NoiseDatabase;
import com.alternativeinfrastructures.noise.models.signal.TypeConverters;
import com.dbflow5.annotation.Column;
import com.dbflow5.annotation.Index;
import com.dbflow5.annotation.PrimaryKey;
import com.dbflow5.annotation.Table;
import com.dbflow5.reactivestreams.structure.BaseRXModel;

import org.whispersystems.libsignal.IdentityKey;

@Table(database = NoiseDatabase.class)
public class RemoteIdentity extends BaseRXModel {
    public RemoteIdentity(String username, int deviceId, IdentityKey identityKey) {
        this.username = username;
        this.deviceId = deviceId;
        this.identityKey = identityKey;
    }

    public RemoteIdentity() {}

    @Override
    public boolean equals(Object object) {
        RemoteIdentity other;
        if (!(object instanceof RemoteIdentity))
            return false;

        other = (RemoteIdentity) object;
        return (username.equals(other.username) &&
                deviceId == other.deviceId &&
                identityKey.equals(other.identityKey));
    }

    @Column
    @Index
    protected String username;
    public String getUsername() { return username; }

    @Column
    protected int deviceId;
    public int getDeviceId() { return deviceId; }

    @PrimaryKey
    @Column(typeConverter = TypeConverters.IdentityKeyConverter.class)
    protected IdentityKey identityKey;
    public IdentityKey getIdentityKey() { return identityKey; }
}

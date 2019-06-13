package com.alternativeinfrastructures.noise.models;

import com.alternativeinfrastructures.noise.NoiseDatabase;
import com.alternativeinfrastructures.noise.models.signal.TypeConverters;
import com.alternativeinfrastructures.noise.storage.IdentityAnnouncementMessage;
import com.alternativeinfrastructures.noise.storage.UnknownMessage;
import com.raizlabs.android.dbflow.annotation.Column;
import com.raizlabs.android.dbflow.annotation.ForeignKey;
import com.raizlabs.android.dbflow.annotation.Index;
import com.raizlabs.android.dbflow.annotation.PrimaryKey;
import com.raizlabs.android.dbflow.annotation.Table;
import com.raizlabs.android.dbflow.rx2.structure.BaseRXModel;

import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.util.KeyHelper;

import java.io.IOException;

import io.reactivex.Single;

@Table(database = NoiseDatabase.class)
public class LocalIdentity extends BaseRXModel {
    public static boolean validUsername(String username) {
        // TODO: Other constraints?
        return username != null && username.length() >= 5 && username.length() <= 31;
    }

    public static Single<Boolean> createNew(String username) throws IOException, InvalidKeyException, UnknownMessage.PayloadTooLargeException {
        LocalIdentity identity = new LocalIdentity();
        identity.username = username;
        identity.registrationId = KeyHelper.generateRegistrationId(true /*extendedRange*/);
        identity.identityKeyPair = KeyHelper.generateIdentityKeyPair();
        // TODO: Once there's support for multiple identities, increment the signedPreKeyId
        identity.signedPreKey = KeyHelper.generateSignedPreKey(
                identity.identityKeyPair, 0 /*signedPreKeyId*/);
        identity.remoteIdentity = identity.createRemoteIdentity();

        return IdentityAnnouncementMessage.Companion.createAndSignAsync(identity.remoteIdentity, IdentityAnnouncementMessage.Companion.getDEFAULT_ZERO_BITS())
                .flatMap((UnknownMessage) -> identity.save());
    }

    private RemoteIdentity createRemoteIdentity() {
        // TODO: Is using the registrationId as the deviceId okay?
        return new RemoteIdentity(username, registrationId, identityKeyPair.getPublicKey());
    }

    @Index
    protected String username;
    public String getUsername() { return username; }

    protected int registrationId;
    public int getRegistrationId() { return registrationId; }

    @PrimaryKey
    @Column(typeConverter = TypeConverters.IdentityKeyPairConverter.class)
    protected IdentityKeyPair identityKeyPair;
    public IdentityKeyPair getIdentityKeyPair() { return identityKeyPair; }

    @Column(typeConverter = TypeConverters.SignedPreKeyRecordConverter.class)
    protected SignedPreKeyRecord signedPreKey;

    @ForeignKey
    protected RemoteIdentity remoteIdentity;
    public RemoteIdentity getRemoteIdentity() { return remoteIdentity; }
}

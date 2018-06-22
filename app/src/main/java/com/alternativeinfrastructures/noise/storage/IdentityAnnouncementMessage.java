package com.alternativeinfrastructures.noise.storage;

import com.alternativeinfrastructures.noise.NoiseDatabase;
import com.alternativeinfrastructures.noise.models.RemoteIdentity;

import com.raizlabs.android.dbflow.annotation.ForeignKey;
import com.raizlabs.android.dbflow.annotation.ForeignKeyAction;
import com.raizlabs.android.dbflow.annotation.Index;
import com.raizlabs.android.dbflow.annotation.Table;

import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.ecc.ECPublicKey;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import io.reactivex.Single;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;
import okio.Utf8;

@Table(database = NoiseDatabase.class)
public class IdentityAnnouncementMessage extends UnknownMessage {
    protected IdentityAnnouncementMessage() {}

    protected IdentityAnnouncementMessage(UnknownMessage message) throws InvalidKeyException, IOException {
        super(message);

        if (MessageTypes.get(publicType) != IdentityAnnouncementMessage.class)
            throw new ClassCastException();

        BufferedSource payloadSource = Okio.buffer(Okio.source(new ByteArrayInputStream(payload.getBlob())));

        byte usernameLength = payloadSource.readByte();
        String username = payloadSource.readString(usernameLength, UnknownMessage.PAYLOAD_CHARSET);
        int deviceId = payloadSource.readInt();
        byte[] identityKeyBytes = payloadSource.readByteArray(IDENTITY_KEY_SIZE);

        IdentityKey identityKey = new IdentityKey(identityKeyBytes, 0 /*offset*/);
        RemoteIdentity identity = new RemoteIdentity(username, deviceId, identityKey);
        // TODO: Add a mechanism to wait for this to finish saving?
        identity.save().subscribe();
    }

    public static Single<UnknownMessage> createAndSignAsync(RemoteIdentity identity, byte zeroBits) throws PayloadTooLargeException, IOException {
        ByteArrayOutputStream payloadStream = new ByteArrayOutputStream();
        BufferedSink payloadSink = Okio.buffer(Okio.sink(payloadStream));

        payloadSink.writeByte((byte) Utf8.size(identity.getUsername()));
        payloadSink.writeString(identity.getUsername(), PAYLOAD_CHARSET);
        payloadSink.writeInt(identity.getDeviceId());
        payloadSink.write(identity.getIdentityKey().serialize(), 0 /*offset*/, IDENTITY_KEY_SIZE);
        payloadSink.flush();

        return IdentityAnnouncementMessage.createAndSignAsync(
                payloadStream.toByteArray(), zeroBits, MessageTypes.get(IdentityAnnouncementMessage.class));
    }

    @Override
    public Single<Boolean> save() {
        return identity.save().flatMap((Boolean) -> super.save());
    }

    @ForeignKey(stubbedRelationship = true)
    @Index
    protected RemoteIdentity identity;
    public RemoteIdentity getIdentity() { return identity; }

    // TODO: Tune this value
    public final static byte DEFAULT_ZERO_BITS = 22;
    private final static int IDENTITY_KEY_SIZE = ECPublicKey.KEY_SIZE;
}

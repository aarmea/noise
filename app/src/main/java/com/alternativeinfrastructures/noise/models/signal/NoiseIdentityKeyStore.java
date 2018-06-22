package com.alternativeinfrastructures.noise.models.signal;

import com.alternativeinfrastructures.noise.models.LocalIdentity;
import com.alternativeinfrastructures.noise.models.RemoteIdentity;
import com.alternativeinfrastructures.noise.models.RemoteIdentity_Table;
import com.raizlabs.android.dbflow.sql.language.SQLite;

import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.state.IdentityKeyStore;

public class NoiseIdentityKeyStore implements IdentityKeyStore {
    // TODO: Cache the LocalIdentity
    // Constructor grabs a copy of the Identity from the database
    // Register for changes to the Identity
    // This way we can avoid querying the database every time

    // TODO: Support multiple local identities?
    // Constructor can accept arguments to pick one

    // TODO: Use Rx

    // TODO: Use SQLCipher or similar to avoid putting private keys in the database in cleartext

    @Override
    public IdentityKeyPair getIdentityKeyPair() {
        LocalIdentity identity = SQLite.select().from(LocalIdentity.class).querySingle();
        return identity == null ? null : identity.getIdentityKeyPair();
    }

    @Override
    public int getLocalRegistrationId() {
        LocalIdentity identity = SQLite.select().from(LocalIdentity.class).querySingle();
        return identity == null ? null : identity.getRegistrationId();
    }

    @Override
    public void saveIdentity(SignalProtocolAddress address, IdentityKey identityKey) {
        // RemoteIdentity identity = new RemoteIdentity(address.getName(), address.getDeviceId(),
        //         identityKey);
        // identity.save().subscribe();
        // TODO: Do we actually need this? Identity discovery should happen automatically via IdentityAnnouncementMessage
        // Once we have something running, see who calls this
        // TODO: Identity lifetime management
        // At what point is it okay to update an identity?
    }

    @Override
    public boolean isTrustedIdentity(SignalProtocolAddress address, IdentityKey identityKey) {
        RemoteIdentity identity = SQLite.select().from(RemoteIdentity.class).where(
                RemoteIdentity_Table.username.eq(address.getName()),
                RemoteIdentity_Table.deviceId.eq(address.getDeviceId())).querySingle();
        return identity != null && identityKey.equals(identity.getIdentityKey());
    }
}

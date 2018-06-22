package com.alternativeinfrastructures.noise.models.signal;

import org.whispersystems.libsignal.InvalidKeyIdException;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyStore;

import java.util.List;

public class NoiseSignedPreKeyStore implements SignedPreKeyStore {
    @Override
    public SignedPreKeyRecord loadSignedPreKey(int signedPreKeyId) throws InvalidKeyIdException {
        // TODO
        return null;
    }

    @Override
    public List<SignedPreKeyRecord> loadSignedPreKeys() {
        // TODO
        return null;
    }

    @Override
    public void storeSignedPreKey(int signedPreKeyId, SignedPreKeyRecord record) {
        // TODO
    }

    @Override
    public boolean containsSignedPreKey(int signedPreKeyId) {
        // TODO
        return false;
    }

    @Override
    public void removeSignedPreKey(int signedPreKeyId) {
        // TODO
    }
}

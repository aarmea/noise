package com.alternativeinfrastructures.noise.models.signal;

import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.PreKeyStore;

// Ensuring prekeys are used exactly once is nontrivial without a central service. Fortunately, they're optional.
public class DummyPreKeyStore implements PreKeyStore {
    @Override
    public PreKeyRecord loadPreKey(int preKeyId) {
        return null;
    }

    @Override
    public void storePreKey(int preKeyId, PreKeyRecord record) {
    }

    @Override
    public boolean containsPreKey(int preKeyId) {
        return false;
    }

    @Override
    public void removePreKey(int preKeyId) {
    }
}

package com.alternativeinfrastructures.noise.models.signal

import org.whispersystems.libsignal.state.PreKeyRecord
import org.whispersystems.libsignal.state.PreKeyStore

// Ensuring prekeys are used exactly once is nontrivial without a central service. Fortunately, they're optional.
class DummyPreKeyStore : PreKeyStore {
    override fun loadPreKey(preKeyId: Int): PreKeyRecord? {
        return null
    }

    override fun storePreKey(preKeyId: Int, record: PreKeyRecord) {}

    override fun containsPreKey(preKeyId: Int): Boolean {
        return false
    }

    override fun removePreKey(preKeyId: Int) {}
}

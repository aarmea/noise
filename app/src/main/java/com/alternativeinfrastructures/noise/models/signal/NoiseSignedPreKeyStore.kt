package com.alternativeinfrastructures.noise.models.signal

import org.whispersystems.libsignal.InvalidKeyIdException
import org.whispersystems.libsignal.state.SignedPreKeyRecord
import org.whispersystems.libsignal.state.SignedPreKeyStore

class NoiseSignedPreKeyStore : SignedPreKeyStore {
    @Throws(InvalidKeyIdException::class)
    override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord? {
        // TODO
        return null
    }

    override fun loadSignedPreKeys(): List<SignedPreKeyRecord>? {
        // TODO
        return null
    }

    override fun storeSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord) {
        // TODO
    }

    override fun containsSignedPreKey(signedPreKeyId: Int): Boolean {
        // TODO
        return false
    }

    override fun removeSignedPreKey(signedPreKeyId: Int) {
        // TODO
    }
}

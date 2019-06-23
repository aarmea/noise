package com.alternativeinfrastructures.noise.models.signal

import com.raizlabs.android.dbflow.converter.TypeConverter

import org.whispersystems.libsignal.IdentityKey
import org.whispersystems.libsignal.IdentityKeyPair
import org.whispersystems.libsignal.state.SignedPreKeyRecord

class TypeConverters {
    @com.raizlabs.android.dbflow.annotation.TypeConverter
    class IdentityKeyPairConverter : TypeConverter<ByteArray, IdentityKeyPair>() {
        override fun getDBValue(model: IdentityKeyPair): ByteArray {
            return model.serialize()
        }

        override fun getModelValue(data: ByteArray): IdentityKeyPair? {
            try {
                return IdentityKeyPair(data)
            } catch (e: Exception) {
                return null
            }

        }
    }

    @com.raizlabs.android.dbflow.annotation.TypeConverter
    class IdentityKeyConverter : TypeConverter<ByteArray, IdentityKey>() {
        override fun getDBValue(model: IdentityKey): ByteArray {
            return model.serialize()
        }

        override fun getModelValue(data: ByteArray): IdentityKey? {
            try {
                return IdentityKey(data, 0 /*offset*/)
            } catch (e: Exception) {
                return null
            }

        }
    }

    @com.raizlabs.android.dbflow.annotation.TypeConverter
    class SignedPreKeyRecordConverter : TypeConverter<ByteArray, SignedPreKeyRecord>() {
        override fun getDBValue(model: SignedPreKeyRecord): ByteArray {
            return model.serialize()
        }

        override fun getModelValue(data: ByteArray): SignedPreKeyRecord? {
            try {
                return SignedPreKeyRecord(data)
            } catch (e: Exception) {
                return null
            }

        }
    }
}

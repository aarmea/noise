package com.alternativeinfrastructures.noise.models.signal;

import com.raizlabs.android.dbflow.converter.TypeConverter;

import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;

public class TypeConverters {
    @com.raizlabs.android.dbflow.annotation.TypeConverter
    public static class IdentityKeyPairConverter extends TypeConverter<byte[], IdentityKeyPair> {
        @Override
        public byte[] getDBValue(IdentityKeyPair model) {
            return model.serialize();
        }

        @Override
        public IdentityKeyPair getModelValue(byte[] data) {
            try {
                return new IdentityKeyPair(data);
            } catch (Exception e) {
                return null;
            }
        }
    }

    @com.raizlabs.android.dbflow.annotation.TypeConverter
    public static class IdentityKeyConverter extends TypeConverter<byte[], IdentityKey> {
        @Override
        public byte[] getDBValue(IdentityKey model) {
            return model.serialize();
        }

        @Override
        public IdentityKey getModelValue(byte[] data) {
            try {
                return new IdentityKey(data, 0 /*offset*/);
            } catch (Exception e) {
                return null;
            }
        }
    }

    @com.raizlabs.android.dbflow.annotation.TypeConverter
    public static class SignedPreKeyRecordConverter extends TypeConverter<byte[], SignedPreKeyRecord> {
        @Override
        public byte[] getDBValue(SignedPreKeyRecord model) {
            return model.serialize();
        }

        @Override
        public SignedPreKeyRecord getModelValue(byte[] data) {
            try {
                return new SignedPreKeyRecord(data);
            } catch (Exception e) {
                return null;
            }
        }
    }
}

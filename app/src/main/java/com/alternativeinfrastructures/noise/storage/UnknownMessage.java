package com.alternativeinfrastructures.noise.storage;

import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import com.raizlabs.android.dbflow.annotation.Column;
import com.raizlabs.android.dbflow.annotation.PrimaryKey;
import com.raizlabs.android.dbflow.annotation.Table;
import com.raizlabs.android.dbflow.config.FlowManager;
import com.raizlabs.android.dbflow.data.Blob;
import com.raizlabs.android.dbflow.structure.BaseModel;
import com.raizlabs.android.dbflow.structure.database.DatabaseWrapper;
import com.raizlabs.android.dbflow.structure.database.transaction.ITransaction;
import com.raizlabs.android.dbflow.structure.database.transaction.Transaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Date;

import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;

@Table(database = MessageDatabase.class)
public class UnknownMessage extends BaseModel {
    public static final String TAG = "UnknownMessage";
    // TODO: Design and implement database syncing across devices

    private static final String HASH_ALGORITHM = "SHA-256";

    // TODO: Tune the size to something more appropriate
    protected static final int PAYLOAD_SIZE = 240;

    @Column
    protected byte version;

    @Column
    protected byte zeroBits;

    @Column
    protected Date date;

    @PrimaryKey
    protected Blob payload;

    @Column
    protected int counter;

    // TODO: Implement a way to get a (Bloom filter?) bit string that describes the entire contents of this table (ideally directly in SQLite/DBFlow)

    protected UnknownMessage() {}

    public static class PayloadTooLargeException extends Exception {}

    public static Transaction createAndSignAsync(byte[] payload, byte zeroBits) throws PayloadTooLargeException {
        if (payload.length < PAYLOAD_SIZE) {
            byte[] paddedPayload = new byte[PAYLOAD_SIZE];
            new SecureRandom().nextBytes(paddedPayload);
            System.arraycopy(payload, 0, paddedPayload, 0, payload.length);
            payload = paddedPayload;
        } else if (payload.length > PAYLOAD_SIZE) {
            throw new PayloadTooLargeException();
        }

        UnknownMessage message = new UnknownMessage();
        message.version = 1;
        message.zeroBits = zeroBits;
        // TODO: Derive an expiration from the number of zero bits
        // Expiration should be derived from number of zero bits - the more declared zero digits in the generated hash, the further out the expiration
        message.date = new Date();
        message.payload = new Blob(payload);

        return message.saveAsync(true /*shouldSign*/);
    }

    public static Transaction createFromSourceAsync(BufferedSource source) throws IOException {
        UnknownMessage message = new UnknownMessage();
        message.version = source.readByte();
        message.zeroBits = source.readByte();
        message.date = new Date(source.readLong());
        message.payload = new Blob(source.readByteArray(PAYLOAD_SIZE));
        message.counter = source.readInt();

        if (!message.isValid())
            throw new IOException("Received an invalid message");

        return message.saveAsync(false /*shouldSign*/);
    }

    public void writeToSink(BufferedSink sink) throws IOException {
        if (payload.getBlob().length != PAYLOAD_SIZE)
            throw new IOException("Payload is " + payload.getBlob().length + " bytes when it should always be " + PAYLOAD_SIZE);

        sink.writeByte(version);
        sink.writeByte(zeroBits);
        sink.writeLong(date.getTime());
        sink.write(payload.getBlob());
        sink.writeInt(counter);
    }

    public byte[] writeToByteArray() throws IOException {
        // TODO: Add a way to calculate what the actual expected size is instead of using this guess
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream(PAYLOAD_SIZE * 2);
        BufferedSink byteSink = Okio.buffer(Okio.sink(byteStream));
        writeToSink(byteSink);
        byteSink.flush();

        return byteStream.toByteArray();
    }

    public boolean isValid() {
        // TODO: Validate the other fields first before calculating and checking the hash
        // (i.e. if the message is expired or we don't support the message version, return false now)
        // TODO: Say *why* the message is invalid (probably by returning an enum instead of a boolean)
        MessageDigest digest;

        try {
            digest = MessageDigest.getInstance(HASH_ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            Log.wtf(TAG, "Couldn't use " + HASH_ALGORITHM, e);
            return false;
        }

        byte[] message;
        try {
            message = writeToByteArray();
        } catch (IOException e) {
            Log.e(TAG, "Couldn't serialize while validating", e);
            return false;
        }

        byte[] hash = digest.digest(message);

        int zeroBytes = zeroBits / 8;
        if (hash.length <= zeroBytes) {
            Log.e(TAG, "Message requires " + zeroBits + " zero bits but the hash is only " + hash.length * 8 + " bits long");
            return false;
        }

        // TODO: Do we want to do a double hash (like Bitcoin) to avoid potential birthday collision attacks?

        // Do we have enough zero bytes? First check the fully-zero bytes...
        for (int hashIndex = 0; hashIndex < zeroBytes; ++hashIndex) {
            if (hash[hashIndex] != 0) {
                return false;
            }
        }

        // ... then check the remaining zero bits in the last byte.
        int zeroBitsRemaining = zeroBits % 8;
        if (zeroBitsRemaining != 0) {
            byte lastZeroByte = hash[zeroBytes];
            byte mask = (byte) (0xFF << (8 - zeroBitsRemaining));
            if ((lastZeroByte & mask) != 0) {
                return false;
            }
        }

        return true;
    }

    private Transaction saveAsync(final boolean shouldSign) {
        final UnknownMessage messageToSave = this;
        Transaction transaction = FlowManager.getDatabase(MessageDatabase.class).beginTransactionAsync(new ITransaction() {
            @Override
            public void execute(DatabaseWrapper databaseWrapper) {
                // TODO: Sign outside of a transaction so we don't block sync
                // Signing happens here as a hack so we don't sign on the UI thread
                if (shouldSign)
                    messageToSave.sign();

                messageToSave.save(databaseWrapper);
            }
        }).success(new Transaction.Success() {
            @Override
            public void onSuccess(Transaction transaction) {
                Log.d(TAG, "Saved a message");
            }
        }).error(new Transaction.Error() {
            @Override
            public void onError(Transaction transaction, Throwable error) {
                Log.e(TAG, "Error saving a message", error);
            }
        }).build();
        transaction.execute();
        return transaction;
    }

    private void sign() {
        // Signing will use 100% of one core for a few seconds. Don't do it on the UI thread.
        if (Looper.getMainLooper().getThread() == Thread.currentThread())
            Log.e(TAG, "Attempting to sign on the UI thread");

        Log.d(TAG, "Signing started");

        long started = System.nanoTime();
        for (counter = 0; counter < Integer.MAX_VALUE; ++counter) {
            if (isValid())
                break;
        }
        long finished = System.nanoTime();

        Log.d(TAG, "Signing took " + (finished - started) / 1000 + " ms");
    }

    // Raw encrypted data, used only for debugging purposes
    public String toString() {
        if (this.payload != null)
            return Base64.encodeToString(this.payload.getBlob(), Base64.NO_WRAP);
        else
            return "";
    }
}

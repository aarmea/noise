package com.alternativeinfrastructures.noise.storage;

import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import com.alternativeinfrastructures.noise.NoiseDatabase;
import com.raizlabs.android.dbflow.annotation.Column;
import com.raizlabs.android.dbflow.annotation.Index;
import com.raizlabs.android.dbflow.annotation.PrimaryKey;
import com.raizlabs.android.dbflow.annotation.Table;
import com.raizlabs.android.dbflow.config.FlowManager;
import com.raizlabs.android.dbflow.data.Blob;
import com.raizlabs.android.dbflow.rx2.structure.BaseRXModel;
import com.raizlabs.android.dbflow.sql.language.SQLite;
import com.raizlabs.android.dbflow.structure.database.DatabaseWrapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;

import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;

@Table(database = NoiseDatabase.class)
public class UnknownMessage extends BaseRXModel {
    public static final String TAG = "UnknownMessage";
    // TODO: Design and implement database syncing across devices

    public static final String HASH_ALGORITHM = "SHA-256";
    public static final Charset PAYLOAD_CHARSET = Charset.forName("UTF-8");

    // TODO: Tune the size to something more appropriate
    public static final int PAYLOAD_SIZE = 240;

    // TODO: If we're going to subclass this the autoincrement id has to go
    // Maybe use the last few bytes of the generated hash?
    @PrimaryKey
    protected long id;

    @Column
    protected byte version;

    @Column
    protected byte zeroBits;

    @Column
    protected Date date;

    @Column
    @Index
    protected Blob payload;

    @Column
    protected int counter;

    @Column
    protected UUID publicType;

    protected UnknownMessage() {}

    public static class PayloadTooLargeException extends Exception {}
    public static class InvalidMessageException extends Exception {}
    public static class NotHashableException extends Exception {}

    public static Single<UnknownMessage> createAndSignAsync(byte[] payload, byte zeroBits, UUID publicType) throws PayloadTooLargeException {
        if (payload.length < PAYLOAD_SIZE) {
            byte[] paddedPayload = new byte[PAYLOAD_SIZE];
            new SecureRandom().nextBytes(paddedPayload);
            System.arraycopy(payload, 0, paddedPayload, 0, payload.length);
            payload = paddedPayload;
        } else if (payload.length > PAYLOAD_SIZE) {
            throw new PayloadTooLargeException();
        } else if (publicType == null) {
            throw new NullPointerException();
        }

        UnknownMessage message = new UnknownMessage();
        message.version = 2;
        message.zeroBits = zeroBits;
        // TODO: Derive an expiration from the number of zero bits
        // Expiration should be derived from number of zero bits - the more declared zero digits in the generated hash, the further out the expiration
        message.date = new Date();
        message.payload = new Blob(payload);
        message.publicType = publicType;

        return Single.fromCallable(message::sign).flatMap(UnknownMessage::saveAsync).subscribeOn(Schedulers.computation());
    }

    public static UnknownMessage fromSource(BufferedSource source) throws IOException {
        // TODO: This I/O shouldn't happen directly on this thread!
        // But it's probably okay as long as it's called from a networking thread
        if (Looper.getMainLooper().getThread() == Thread.currentThread())
            Log.e(TAG, "Attempting to read from a network on the UI thread");

        UnknownMessage message = new UnknownMessage();
        message.version = source.readByte();
        message.zeroBits = source.readByte();
        message.date = new Date(source.readLong());
        message.payload = new Blob(source.readByteArray(PAYLOAD_SIZE));
        message.counter = source.readInt();
        long typeMsb = source.readLong();
        long typeLsb = source.readLong();
        message.publicType = new UUID(typeMsb, typeLsb);

        return message;
    }

    protected UnknownMessage(UnknownMessage other) {
        // Copy constructor provided to populate subclasses
        id = other.id;
        version = other.version;
        zeroBits = other.zeroBits;
        date = other.date;
        payload = other.payload;
        counter = other.counter;
        publicType = other.publicType;
    }

    public final void writeToSink(BufferedSink sink) throws IOException {
        if (payload.getBlob().length != PAYLOAD_SIZE)
            throw new IOException("Payload is " + payload.getBlob().length + " bytes when it should always be " + PAYLOAD_SIZE);

        sink.writeByte(version);
        sink.writeByte(zeroBits);
        sink.writeLong(date.getTime());
        sink.write(payload.getBlob());
        sink.writeInt(counter);
        sink.writeLong(publicType.getMostSignificantBits());
        sink.writeLong(publicType.getLeastSignificantBits());
    }

    public final byte[] writeToByteArray() throws IOException {
        // TODO: Add a way to calculate what the actual expected size is instead of using this guess
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream(PAYLOAD_SIZE * 2);
        BufferedSink byteSink = Okio.buffer(Okio.sink(byteStream));
        writeToSink(byteSink);
        byteSink.flush();

        return byteStream.toByteArray();
    }

    @Override
    public boolean equals(Object object) {
        UnknownMessage other;
        if (!(object instanceof UnknownMessage))
            return false;

        other = (UnknownMessage) object;
        return (version == other.version &&
                zeroBits == other.zeroBits &&
                date.equals(other.date) &&
                Arrays.equals(payload.getBlob(), other.payload.getBlob()) &&
                counter == other.counter &&
                publicType.equals(other.publicType));
    }

    public final byte[] calculateHash() throws NotHashableException {
        MessageDigest digest;

        // TODO: This can be cleaner with okio.HashingSink
        try {
            digest = MessageDigest.getInstance(HASH_ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            Log.wtf(TAG, "Couldn't use " + HASH_ALGORITHM, e);
            NotHashableException hashException = new NotHashableException();
            hashException.initCause(e);
            throw hashException;
        }

        byte[] message;
        try {
            message = writeToByteArray();
        } catch (IOException e) {
            Log.e(TAG, "Couldn't serialize while validating", e);
            NotHashableException hashException = new NotHashableException();
            hashException.initCause(e);
            throw hashException;
        }

        return digest.digest(message);
    }

    public final long calculateId() throws NotHashableException {
        byte[] hash = calculateHash();
        long id = 0;
        for (int i = 0; i < 8 /*bytes in a long*/; ++i)
            id += ((long) hash[hash.length - i - 1] & 0xffL) << (i*8);
        return id;
    }

    public final boolean isValid() {
        // TODO: Validate the other fields first before calculating and checking the hash
        // (i.e. if the message is expired or we don't support the message version, return false now)
        // TODO: Say *why* the message is invalid (probably by returning an enum instead of a boolean)
        byte[] hash;
        try {
            hash = calculateHash();
        } catch (NotHashableException e) {
            return false;
        }

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

    public Single<UnknownMessage> saveAsync() {
        final UnknownMessage messageToSave = this;
        return Single.fromCallable(() -> {
            // TODO: Include the reason *why* the message is invalid in the exception
            if (!messageToSave.isValid())
                throw new InvalidMessageException();

            // TODO: This is duplicating work from the last call to isValid
            messageToSave.id = messageToSave.calculateId();

            UnknownMessage typedMessage = MessageTypes.downcastIfKnown(messageToSave);

            FlowManager.getDatabase(NoiseDatabase.class).beginTransactionAsync((DatabaseWrapper databaseWrapper) -> {
                long equalMessages = SQLite.selectCountOf().from(UnknownMessage.class)
                        .where(UnknownMessage_Table.payload.eq(messageToSave.payload)).count();
                if (equalMessages > 0) {
                    // TODO: In this case, we should keep the message that expires later - someone intentionally signed it again
                    Log.d(TAG, "Skipped saving an existing message");
                    return;
                }

                // blockingGet is okay here because this is ultimately wrapped in a Callable
                messageToSave.save(databaseWrapper).blockingGet();

                // DBFlow doesn't automatically add base classes as their own row
                // TODO: UnknownMessage and its typed counterpart need to have the same lifetime
                if (typedMessage != null)
                    typedMessage.save(databaseWrapper).blockingGet();

                // TODO: Do this using a listener and then we won't need saveAsync anymore (message.insert() will implicitly manage the filter)
                // https://agrosner.gitbooks.io/dbflow/content/Observability.html
                BloomFilter.addMessage(messageToSave, databaseWrapper);
                Log.d(TAG, "Saved a message with id " + messageToSave.id);
            }).build().executeSync();
            return typedMessage != null ? typedMessage : messageToSave;
        }).subscribeOn(Schedulers.computation());
    }

    public Single<Boolean> deleteAsync() {
        // Ensures that we are deleting from the UnknownMessage table first
        UnknownMessage message = new UnknownMessage(this);
        UnknownMessage typedMessage = MessageTypes.downcastIfKnown(message);
        if (typedMessage != null)
            return typedMessage.delete().flatMap((Boolean) -> message.delete());
        return message.delete();
    }

    private UnknownMessage sign() {
        // Signing will use 100% of one core for a few seconds. Don't do it on the UI thread.
        // TODO: Sign on multiple threads
        // TODO: Use a memory-intensive proof-of-work function to minimize the impact of bogus messages signed by ASICs (like Ethereum)
        // http://www.ethdocs.org/en/latest/introduction/what-is-ethereum.html#how-does-ethereum-work
        if (Looper.getMainLooper() == Looper.myLooper())
            Log.e(TAG, "Attempting to sign on the UI thread");

        Log.d(TAG, "Signing started");

        long started = System.nanoTime();
        for (counter = 0; counter < Integer.MAX_VALUE; ++counter) {
            if (isValid())
                break;
        }
        long finished = System.nanoTime();

        Log.d(TAG, "Signing took " + (finished - started) / 1000000 + " ms");

        return this;
    }

    // Raw data, used only for debugging purposes
    public String toString() {
        if (this.payload != null)
            return Base64.encodeToString(this.payload.getBlob(), Base64.NO_WRAP);
        else
            return "";
    }
}

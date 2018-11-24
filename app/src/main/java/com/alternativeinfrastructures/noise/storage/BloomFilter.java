package com.alternativeinfrastructures.noise.storage;

import android.os.Looper;
import android.util.Log;

import com.alternativeinfrastructures.noise.NoiseDatabase;
import com.raizlabs.android.dbflow.annotation.ForeignKey;
import com.raizlabs.android.dbflow.annotation.ForeignKeyAction;
import com.raizlabs.android.dbflow.annotation.PrimaryKey;
import com.raizlabs.android.dbflow.annotation.Table;
import com.raizlabs.android.dbflow.rx2.language.RXSQLite;
import com.raizlabs.android.dbflow.rx2.structure.BaseRXModel;
import com.raizlabs.android.dbflow.sql.language.CursorResult;
import com.raizlabs.android.dbflow.sql.language.Method;
import com.raizlabs.android.dbflow.sql.language.SQLite;
import com.raizlabs.android.dbflow.structure.database.DatabaseWrapper;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Vector;

import io.reactivex.Flowable;
import io.reactivex.Single;
import util.hash.MurmurHash3;

// Actual bloom filter implementation based heavily on this guide:
// http://blog.michaelschmatz.com/2016/04/11/how-to-write-a-bloom-filter-cpp/
@Table(database = NoiseDatabase.class)
public class BloomFilter extends BaseRXModel {
    public static final String TAG = "BloomFilter";

    // TODO: Tune these
    // They need to be large enough to describe billions of messages
    // but also small enough to transmit in a few seconds over Bluetooth
    static final int SIZE = 1 << 20; // in bits
    static final int USABLE_SIZE = SIZE - 1;
    static final int NUM_HASHES = 5;

    public static final int SIZE_IN_BYTES = SIZE / 8;

    @PrimaryKey
    @ForeignKey(onDelete = ForeignKeyAction.CASCADE, stubbedRelationship = true)
    UnknownMessage message;

    @PrimaryKey
    int hash;

    BloomFilter() {}

    static List<Integer> hashMessage(UnknownMessage message) {
        Vector<Integer> hashList = new Vector<Integer>(NUM_HASHES);

        // TODO: Is using a non-cryptographic hash like murmurhash okay? An attacker can generate messages that match the hashes to try to block it
        MurmurHash3.LongPair primaryHash = new MurmurHash3.LongPair();
        MurmurHash3.murmurhash3_x64_128(message.payload.getBlob(), 0 /*offset*/, UnknownMessage.PAYLOAD_SIZE, 0 /*seed*/, primaryHash);
        for (int hashFunction = 0; hashFunction < NUM_HASHES; ++hashFunction)
            hashList.add((int) nthHash(primaryHash.val1, primaryHash.val2, hashFunction));

        return hashList;
    }

    static void addMessage(UnknownMessage message, DatabaseWrapper databaseWrapper) {
        if (Looper.getMainLooper() == Looper.myLooper())
            Log.e(TAG, "Attempting to save on the UI thread");

        for (int hash : hashMessage(message)) {
            BloomFilter row = new BloomFilter();
            row.message = message;
            row.hash = hash;
            // blockingGet is okay here because this is always called from within a Transaction
            row.save(databaseWrapper).blockingGet();
        }
    }

    public static BitSet makeEmptyMessageVector() {
        BitSet messageVector = new BitSet(SIZE);
        messageVector.set(USABLE_SIZE); // Hack to keep the generated byte array the same size
        return messageVector;
    }

    public static Single<BitSet> getMessageVectorAsync() {
        return RXSQLite.rx(SQLite.select(BloomFilter_Table.hash.distinct()).from(BloomFilter.class))
                .queryResults().map((CursorResult<BloomFilter> bloomCursor) -> {
            BitSet messageVector = makeEmptyMessageVector();
            for (int bloomIndex = 0; bloomIndex < bloomCursor.getCount(); ++bloomIndex) {
                BloomFilter filterElement = bloomCursor.getItem(bloomIndex);
                if (filterElement != null)
                    messageVector.set(filterElement.hash);
            }
            bloomCursor.close();
            return messageVector;
        });
    }

    public static Flowable<UnknownMessage> getMatchingMessages(BitSet messageVector) {
        ArrayList<Integer> hashes = new ArrayList<Integer>(messageVector.cardinality());
        for (int hash = messageVector.nextSetBit(0); hash != -1; hash = messageVector.nextSetBit(hash+1))
            hashes.add(hash);

        // TODO: Implement Noise message priority - order by date and zero bits
        return RXSQLite.rx(SQLite.select(UnknownMessage_Table.ALL_COLUMN_PROPERTIES)
                .from(UnknownMessage.class).leftOuterJoin(BloomFilter.class)
                .on(UnknownMessage_Table.id.eq(BloomFilter_Table.message_id))
                .where(BloomFilter_Table.hash.in(hashes))
                .groupBy(BloomFilter_Table.message_id)
                .having(Method.count().eq(NUM_HASHES)))
        .queryStreamResults();
    }

    private static long nthHash(long hashA, long hashB, int hashFunction) {
        // Double modulus ensures that the result is positive when any of the hashes are negative
        return ((hashA + hashFunction * hashB) % USABLE_SIZE + USABLE_SIZE) % USABLE_SIZE;
    }
}

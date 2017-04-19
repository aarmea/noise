package com.alternativeinfrastructures.noise.storage;

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

import java.util.Date;

@Table(database = MessageDatabase.class)
public class UnknownMessage extends BaseModel {
    public static final String TAG = "UnknownMessage";
    // TODO: Design and implement database syncing across devices

    @Column
    protected byte version;

    @Column
    protected byte zeroBits;

    @Column
    protected Date date;

    @PrimaryKey
    protected Blob data;

    @Column
    protected int counter;

    // TODO: Implement a way to get a (Bloom filter?) bit string that describes the entire contents of this table (ideally directly in SQLite/DBFlow)

    protected UnknownMessage() {}

    public static void signAndSaveAsync(final Blob data) {
        // TODO: Enforce size requirements (we want each transmitted message to be exactly 256 bytes)
        FlowManager.getDatabase(MessageDatabase.class).beginTransactionAsync(new ITransaction() {
            @Override
            public void execute(DatabaseWrapper databaseWrapper) {
                UnknownMessage message = new UnknownMessage();
                message.version = 1;
                message.zeroBits = 20;
                // TODO: This is hashcash's default. How many bits do we actually want?
                // Expiration should be derived from number of zero bits - the more declared zero digits in the generated hash, the further out the expiration
                message.date = new Date();
                message.data = data;
                message.counter = 0;

                message.sign();
                message.save(databaseWrapper);
            }
        }).success(new Transaction.Success() {
            @Override
            public void onSuccess(Transaction transaction) {
                Log.d(TAG, "Saved an message");
            }
        }).error(new Transaction.Error() {
            @Override
            public void onError(Transaction transaction, Throwable error) {
                Log.e(TAG, "Error saving a message", error);
            }
        }).build().execute();
    }

    public boolean isValid() {
        // TODO: Hash the serialized representation of the data and verify it has the correct number of zero bits
        return true;
    }

    private void sign() {
        // TODO: Calculate correct counter value to sign the message
        // Since DBFlow transactions happen on a separate thread, we could theoretically sign here
        // Make sure to configure it to allow multiple threads with a custom TransactionManager so signing doesn't block sync:
        // https://github.com/Raizlabs/DBFlow/blob/master/usage2/StoringData.md#transactions
    }

    // Raw encrypted data, used only for debugging purposes
    public String toString() {
        return Base64.encodeToString(this.data.getBlob(), Base64.NO_WRAP);
    }
}

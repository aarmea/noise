package com.alternativeinfrastructures.noise.storage;

import com.alternativeinfrastructures.noise.BuildConfig;
import com.raizlabs.android.dbflow.structure.database.transaction.Transaction;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class)
public class UnknownMessageTest {
    public static final int TRANSACTION_MAX_TIME_MS = 10000;

    @Test
    public void createMessage() {
        byte[] payload = "This is a test message".getBytes();
        try {
            Transaction transaction = UnknownMessage.createAndSignAsync(payload);
            synchronized(transaction) {
                transaction.wait(TRANSACTION_MAX_TIME_MS);
            }
        } catch (UnknownMessage.PayloadTooLargeException | InterruptedException e) {
            fail(e.toString());
        }

        // TODO: Make sure message exists
        // TODO: Make sure message is signed properly
        // TODO: Validate contents of the message
    }
}

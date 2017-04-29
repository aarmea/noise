package com.alternativeinfrastructures.noise.storage;

import com.alternativeinfrastructures.noise.BuildConfig;
import com.raizlabs.android.dbflow.sql.language.SQLite;
import com.raizlabs.android.dbflow.structure.database.transaction.Transaction;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

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

        List<UnknownMessage> messages = SQLite.select().from(UnknownMessage.class).queryList();
        assertEquals(messages.size(), 1);

        UnknownMessage message = messages.get(0);
        assertTrue(message.isValid());
        assertNotNull(message.payload);

        byte[] messagePayload = message.payload.getBlob();
        assertEquals(messagePayload.length, UnknownMessage.PAYLOAD_SIZE);

        for (int i = 0; i < payload.length; ++i)
            assertEquals(payload[i], messagePayload[i]);
    }
}

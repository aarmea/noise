package com.alternativeinfrastructures.noise.storage;

import com.alternativeinfrastructures.noise.BuildConfig;
import com.raizlabs.android.dbflow.config.FlowManager;
import com.raizlabs.android.dbflow.sql.language.SQLite;
import com.raizlabs.android.dbflow.structure.database.transaction.Transaction;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.util.List;

import okio.Okio;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class)
public class UnknownMessageTest {
    public static final int TRANSACTION_MAX_TIME_MS = 10000;

    @After
    public void teardown() {
        // DBFlow doesn't automatically close its database handle when a test ends.
        // https://github.com/robolectric/robolectric/issues/1890#issuecomment-218880541
        FlowManager.destroy();
    }

    @Test
    public void createNewMessage() throws Exception {
        byte[] payload = "This is a test message".getBytes();
        UnknownMessage message = createAndSignOneMessage(payload);
        assertTrue(message.isValid());
        assertPayloadContents(message, payload);
    }

    @Test
    public void saveAndReloadMessage() throws Exception {
        byte[] payload = "This is another test message".getBytes();
        UnknownMessage message = createAndSignOneMessage(payload);
        byte[] savedMessage = message.writeToByteArray();

        message.delete();
        assertEquals(SQLite.select().from(UnknownMessage.class).count(), 0);

        ByteArrayInputStream messageStream = new ByteArrayInputStream(savedMessage);
        Transaction transaction = UnknownMessage.createFromSourceAsync(Okio.buffer(Okio.source(messageStream)));
        synchronized(transaction) {
            transaction.wait(TRANSACTION_MAX_TIME_MS);
        }

        List<UnknownMessage> messages = SQLite.select().from(UnknownMessage.class).queryList();
        assertEquals(messages.size(), 1);
        UnknownMessage reloadedMessage = messages.get(0);
        assertTrue(reloadedMessage.isValid());

        assertPayloadContents(reloadedMessage, payload);
    }

    private UnknownMessage createAndSignOneMessage(byte[] payload) throws Exception {
        Transaction transaction = UnknownMessage.createAndSignAsync(payload);
        synchronized(transaction) {
            transaction.wait(TRANSACTION_MAX_TIME_MS);
        }

        List<UnknownMessage> messages = SQLite.select().from(UnknownMessage.class).queryList();
        assertEquals(messages.size(), 1);

        return messages.get(0);
    }

    private void assertPayloadContents(UnknownMessage message, byte[] payload) {
        assertNotNull(message.payload);

        byte[] messagePayload = message.payload.getBlob();
        assertEquals(messagePayload.length, UnknownMessage.PAYLOAD_SIZE);

        for (int i = 0; i < payload.length; ++i)
            assertEquals(payload[i], messagePayload[i]);
    }
}

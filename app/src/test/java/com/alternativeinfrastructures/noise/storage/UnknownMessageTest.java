package com.alternativeinfrastructures.noise.storage;

import com.alternativeinfrastructures.noise.BuildConfig;
import com.alternativeinfrastructures.noise.TestBase;
import com.raizlabs.android.dbflow.config.FlowManager;
import com.raizlabs.android.dbflow.sql.language.SQLite;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

import java.io.ByteArrayInputStream;
import java.util.UUID;

import okio.Okio;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class)
public class UnknownMessageTest extends TestBase {
    public static UnknownMessage createTestMessage(byte[] payload) throws Exception {
        final byte zeroBits = 10;
        final UUID publicType = new UUID(0, 0);

        return UnknownMessage.createAndSignAsync(payload, zeroBits, publicType).blockingGet();
    }

    @Test
    public void createNewMessage() throws Exception {
        byte[] payload = "This is a test message".getBytes();
        UnknownMessage message = createTestMessage(payload);

        assertEquals(1, SQLite.selectCountOf().from(UnknownMessage.class).count());
        assertTrue(message.isValid());
        assertPayloadContents(message, payload);
    }

    @Test
    public void saveAndReloadMessage() throws Exception {
        byte[] payload = "This is another test message".getBytes();
        UnknownMessage message = createTestMessage(payload);
        byte[] savedMessage = message.writeToByteArray();

        assertTrue(message.deleteAsync().blockingGet());

        assertEquals(0, SQLite.selectCountOf().from(UnknownMessage.class).count());
        assertEquals(0, SQLite.selectCountOf().from(BloomFilter.class).count());

        ByteArrayInputStream messageStream = new ByteArrayInputStream(savedMessage);
        UnknownMessage reloadedMessage = UnknownMessage.fromSource(Okio.buffer(Okio.source(messageStream))).saveAsync().blockingGet();

        assertEquals(1, SQLite.selectCountOf().from(UnknownMessage.class).count());
        assertTrue(reloadedMessage.isValid());
        assertPayloadContents(reloadedMessage, payload);
        assertEquals(message, reloadedMessage);
    }

    @Test
    public void newMessagesAreDistinct() throws Exception {
        byte[] payload = "Different calls to createAndSignAsync should produce different, unequal messages".getBytes();
        UnknownMessage message1 = createTestMessage(payload);
        UnknownMessage message2 = createTestMessage(payload);

        assertEquals(2, SQLite.selectCountOf().from(UnknownMessage.class).count());
        assertNotEquals(message1, message2);
    }

    @Test
    public void invalidMessage() throws Exception {
        byte[] payload = "This will become an invalid message".getBytes();
        UnknownMessage message = createTestMessage(payload);

        // Incorrect proof-of-work
        ++message.counter;
        assertFalse(message.isValid());
        try {
            message.saveAsync().blockingGet();
            fail("Expected an InvalidMessageException");
        } catch (Exception e) {
            assertEquals(UnknownMessage.InvalidMessageException.class, e.getCause().getClass());
        }
    }

    @Test
    public void tooLargeMessage() throws Exception {
        String payloadString = "This message will become too large to save. ";
        for (int i = 0; i < 5; ++i)
            payloadString = payloadString.concat(payloadString);
        assertTrue(payloadString.length() > UnknownMessage.PAYLOAD_SIZE);

        try {
            createTestMessage(payloadString.getBytes());
            fail("Expected a PayloadTooLargeException");
        } catch (UnknownMessage.PayloadTooLargeException e) {
            // Expected exception
        }
    }

    private void assertPayloadContents(UnknownMessage message, byte[] payload) {
        assertNotNull(message.payload);

        byte[] messagePayload = message.payload.getBlob();
        assertEquals(messagePayload.length, UnknownMessage.PAYLOAD_SIZE);

        for (int i = 0; i < payload.length; ++i)
            assertEquals(payload[i], messagePayload[i]);
    }
}

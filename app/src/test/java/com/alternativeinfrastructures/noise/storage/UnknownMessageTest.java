package com.alternativeinfrastructures.noise.storage;

import com.alternativeinfrastructures.noise.BuildConfig;
import com.raizlabs.android.dbflow.config.FlowManager;
import com.raizlabs.android.dbflow.sql.language.SQLite;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;

import okio.Okio;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class)
public class UnknownMessageTest {
    public static final byte ZERO_BITS = 10;

    @After
    public void teardown() {
        // DBFlow doesn't automatically close its database handle when a test ends.
        // https://github.com/robolectric/robolectric/issues/1890#issuecomment-218880541
        FlowManager.destroy();
    }

    // Tests in here failed with "No such manifest file"?
    // Roboelectric needs the Android JUnit working directory set to $MODULE_DIR$.
    // https://github.com/robolectric/robolectric/issues/2949

    @Test
    public void createNewMessage() throws Exception {
        byte[] payload = "This is a test message".getBytes();
        UnknownMessage message = UnknownMessage.createAndSignAsync(payload, ZERO_BITS).blockingGet();

        assertTrue(message.isValid());
        assertPayloadContents(message, payload);
    }

    @Test
    public void saveAndReloadMessage() throws Exception {
        byte[] payload = "This is another test message".getBytes();
        UnknownMessage message = UnknownMessage.createAndSignAsync(payload, ZERO_BITS).blockingGet();
        byte[] savedMessage = message.writeToByteArray();

        assertTrue(message.delete().blockingGet());

        assertEquals(0, SQLite.select().from(UnknownMessage.class).count());
        assertEquals(0, SQLite.select().from(BloomFilter.class).count());

        ByteArrayInputStream messageStream = new ByteArrayInputStream(savedMessage);
        UnknownMessage reloadedMessage = UnknownMessage.fromSource(Okio.buffer(Okio.source(messageStream))).saveAsync().blockingGet();

        assertTrue(reloadedMessage.isValid());
        assertPayloadContents(reloadedMessage, payload);
        assertTrue(message.equivalent(reloadedMessage));
    }

    @Test
    public void newMessagesAreDistinct() throws Exception {
        byte[] payload = "Different calls to createAndSignAsync should produce different, unequal messages".getBytes();
        UnknownMessage message1 = UnknownMessage.createAndSignAsync(payload, ZERO_BITS).blockingGet();
        UnknownMessage message2 = UnknownMessage.createAndSignAsync(payload, ZERO_BITS).blockingGet();

        assertFalse(message1.equivalent(message2));
        assertNotEquals(message1, message2);
    }

    @Test
    public void invalidMessage() throws Exception {
        byte[] payload = "This will become an invalid message".getBytes();
        UnknownMessage message = UnknownMessage.createAndSignAsync(payload, ZERO_BITS).blockingGet();

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
            UnknownMessage.createAndSignAsync(payloadString.getBytes(), ZERO_BITS).blockingGet();
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

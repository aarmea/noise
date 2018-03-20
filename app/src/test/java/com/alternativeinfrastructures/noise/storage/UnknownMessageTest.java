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
import java.util.BitSet;

import okio.Okio;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class)
public class UnknownMessageTest {
    static final byte TEST_ZERO_BITS = 10;

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
        UnknownMessage message = UnknownMessage.createAndSignAsync(payload, TEST_ZERO_BITS).blockingGet();
        BitSet messageVector = BloomFilter.getMessageVectorAsync().blockingGet();

        assertTrue(message.isValid());
        assertPayloadContents(message, payload);
        assertVectorContainsMessage(message, messageVector);
    }

    @Test
    public void saveAndReloadMessage() throws Exception {
        byte[] payload = "This is another test message".getBytes();
        UnknownMessage message = UnknownMessage.createAndSignAsync(payload, TEST_ZERO_BITS).blockingGet();
        byte[] savedMessage = message.writeToByteArray();

        assertTrue(message.delete().blockingGet());

        assertEquals(0, SQLite.select().from(UnknownMessage.class).count());
        assertEquals(0, SQLite.select().from(BloomFilter.class).count());

        ByteArrayInputStream messageStream = new ByteArrayInputStream(savedMessage);
        UnknownMessage reloadedMessage = UnknownMessage.fromSource(Okio.buffer(Okio.source(messageStream))).saveAsync().blockingGet();
        BitSet messageVector = BloomFilter.getMessageVectorAsync().blockingGet();

        assertTrue(reloadedMessage.isValid());
        assertPayloadContents(reloadedMessage, payload);
        assertVectorContainsMessage(reloadedMessage, messageVector);
    }

    // TODO: Test invalid messages

    private void assertPayloadContents(UnknownMessage message, byte[] payload) {
        assertNotNull(message.payload);

        byte[] messagePayload = message.payload.getBlob();
        assertEquals(messagePayload.length, UnknownMessage.PAYLOAD_SIZE);

        for (int i = 0; i < payload.length; ++i)
            assertEquals(payload[i], messagePayload[i]);
    }

    private void assertVectorContainsMessage(UnknownMessage message, BitSet messageVector) {
        assertEquals(messageVector.toByteArray().length, BloomFilter.SIZE_IN_BYTES);
        for (int hash : BloomFilter.hashMessage(message))
            assertTrue(messageVector.get(hash));
    }
}

package com.alternativeinfrastructures.noise.storage;

import com.alternativeinfrastructures.noise.TestBase;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.BitSet;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class BloomFilterTest extends TestBase {
    @Test
    public void newMessageMembership() throws Exception {
        byte[] payload = "This is a test message".getBytes();
        UnknownMessage message = UnknownMessageTest.createTestMessage(payload);

        BitSet messageVector = BloomFilter.getMessageVectorAsync().blockingGet();

        // `-1` needed to remove the placeholder ending bit
        assertEquals(BloomFilter.NUM_HASHES, messageVector.cardinality()-1);
        assertVectorContainsMessage(message, messageVector);
    }

    @Test
    public void messageVectorQuery() throws Exception {
        byte[] payload = "This is a test message".getBytes();
        UnknownMessage message = UnknownMessageTest.createTestMessage(payload);

        // Query with a fully set message vector
        BitSet allVector = BloomFilter.makeEmptyMessageVector();
        allVector.flip(0, BloomFilter.USABLE_SIZE);
        UnknownMessage messageFromAllVector =
                BloomFilter.getMatchingMessages(allVector).toList().blockingGet().get(0);
        assertEquals(message, messageFromAllVector);

        // Query with just this message's hashes
        BitSet filterVector = BloomFilter.getMessageVectorAsync().blockingGet();
        UnknownMessage messageFromFilterVector =
                BloomFilter.getMatchingMessages(filterVector).toList().blockingGet().get(0);
        assertEquals(message, messageFromFilterVector);

        // Query with all but one of this message's hashes
        BitSet incompleteVector = (BitSet) filterVector.clone();
        incompleteVector.flip(incompleteVector.nextSetBit(0));
        long messageCount = BloomFilter.getMatchingMessages(incompleteVector).count().blockingGet();
        assertEquals(0, messageCount);
    }

    private void assertVectorContainsMessage(UnknownMessage message, BitSet messageVector) {
        assertEquals(messageVector.toByteArray().length, BloomFilter.SIZE_IN_BYTES);
        for (int hash : BloomFilter.hashMessage(message))
            assertTrue(messageVector.get(hash));
    }
}

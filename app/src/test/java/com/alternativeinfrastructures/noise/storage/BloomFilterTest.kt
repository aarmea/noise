package com.alternativeinfrastructures.noise.storage

import com.alternativeinfrastructures.noise.TestBase

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

import java.util.BitSet

import org.junit.Assert.*

@RunWith(RobolectricTestRunner::class)
class BloomFilterTest : TestBase() {
    @Test
    @Throws(Exception::class)
    fun newMessageMembership() {
        val payload = "This is a test message".toByteArray()
        val message = UnknownMessageTest.createTestMessage(payload)

        val messageVector = BloomFilter.messageVectorAsync.blockingGet()

        // `-1` needed to remove the placeholder ending bit
        assertEquals(BloomFilter.NUM_HASHES.toLong(), (messageVector.cardinality() - 1).toLong())
        assertVectorContainsMessage(message, messageVector)
    }

    @Test
    @Throws(Exception::class)
    fun messageVectorQuery() {
        val payload = "This is a test message".toByteArray()
        val message = UnknownMessageTest.createTestMessage(payload)

        // Query with a fully set message vector
        val allVector = BloomFilter.makeEmptyMessageVector()
        allVector.flip(0, BloomFilter.USABLE_SIZE)
        val messageFromAllVector = BloomFilter.getMatchingMessages(allVector).toList().blockingGet()[0]
        assertEquals(message, messageFromAllVector)

        // Query with just this message's hashes
        val filterVector = BloomFilter.messageVectorAsync.blockingGet()
        val messageFromFilterVector = BloomFilter.getMatchingMessages(filterVector).toList().blockingGet()[0]
        assertEquals(message, messageFromFilterVector)

        // Query with all but one of this message's hashes
        val incompleteVector = filterVector.clone() as BitSet
        incompleteVector.flip(incompleteVector.nextSetBit(0))
        val messageCount = BloomFilter.getMatchingMessages(incompleteVector).count().blockingGet()
        assertEquals(0, messageCount)
    }

    private fun assertVectorContainsMessage(message: UnknownMessage, messageVector: BitSet) {
        assertEquals(messageVector.toByteArray().size.toLong(), BloomFilter.SIZE_IN_BYTES.toLong())
        for (hash in BloomFilter.hashMessage(message))
            assertTrue(messageVector.get(hash))
    }
}

package com.alternativeinfrastructures.noise.storage

import com.alternativeinfrastructures.noise.TestBase
import com.raizlabs.android.dbflow.sql.language.SQLite

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

import java.io.ByteArrayInputStream
import java.util.UUID

import okio.Okio

import org.junit.Assert.*
import org.junit.Rule
import org.junit.rules.ExpectedException

@RunWith(RobolectricTestRunner::class)
class UnknownMessageTest : TestBase() {

    @Rule
    @JvmField
    public val thrown = ExpectedException.none()

    @Test
    @Throws(Exception::class)
    fun createNewMessage() {
        val payload = "This is a test message".toByteArray()
        val message = createTestMessage(payload)

        assertEquals(1, SQLite.selectCountOf().from<UnknownMessage>(UnknownMessage::class.java).longValue())
        assertTrue(message.isValid)
        assertPayloadContents(message, payload)
    }

    @Test
    @Throws(Exception::class)
    fun saveAndReloadMessage() {
        val payload = "This is another test message".toByteArray()
        val message = createTestMessage(payload)
        val savedMessage = message.writeToByteArray()

        assertTrue(message.deleteAsync().blockingGet())

        assertEquals(0, SQLite.selectCountOf().from<UnknownMessage>(UnknownMessage::class.java).longValue())
        assertEquals(0, SQLite.selectCountOf().from<BloomFilter>(BloomFilter::class.java).longValue())

        val messageStream = ByteArrayInputStream(savedMessage)
        val reloadedMessage = UnknownMessage.fromSource(Okio.buffer(Okio.source(messageStream))).saveAsync().blockingGet()

        assertEquals(1, SQLite.selectCountOf().from<UnknownMessage>(UnknownMessage::class.java).longValue())
        assertTrue(reloadedMessage.isValid)
        assertPayloadContents(reloadedMessage, payload)
        assertEquals(message, reloadedMessage)
    }

    @Test
    @Throws(Exception::class)
    fun newMessagesAreDistinct() {
        val payload = "Different calls to createAndSignAsync should produce different, unequal messages".toByteArray()
        val message1 = createTestMessage(payload)
        val message2 = createTestMessage(payload)

        assertEquals(2, SQLite.selectCountOf().from<UnknownMessage>(UnknownMessage::class.java).longValue())
        assertNotEquals(message1, message2)
    }

    @Throws(Exception::class)
    fun invalidMessage() {
        val payload = "This will become an invalid message".toByteArray()
        val message = createTestMessage(payload)

        // Incorrect proof-of-work
        ++message.counter
        assertFalse(message.isValid)

        thrown.expect(UnknownMessage.InvalidMessageException::class.java)
        message.saveAsync().blockingGet()
    }

    @Test
    @Throws(Exception::class)
    fun tooLargeMessage() {
        var payloadString = "This message will become too large to save. "
        for (i in 0..4)
            payloadString = payloadString + payloadString
        assertTrue(payloadString.length > UnknownMessage.PAYLOAD_SIZE)

        thrown.expect(UnknownMessage.PayloadTooLargeException::class.java)
        createTestMessage(payloadString.toByteArray())
    }

    private fun assertPayloadContents(message: UnknownMessage, payload: ByteArray) {
        assertNotNull(message.payload)

        val messagePayload = message.payload.blob
        assertEquals(messagePayload.size.toLong(), UnknownMessage.PAYLOAD_SIZE.toLong())

        for (i in payload.indices)
            assertEquals(payload[i].toLong(), messagePayload[i].toLong())
    }

    companion object {
        @Throws(Exception::class)
        fun createTestMessage(payload: ByteArray): UnknownMessage {
            val zeroBits: Byte = 10
            val publicType = UUID(0, 0)

            return UnknownMessage.rawCreateAndSignAsync(payload, zeroBits, publicType).blockingGet()
        }
    }
}

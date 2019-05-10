package com.alternativeinfrastructures.noise.storage

import com.alternativeinfrastructures.noise.TestBase
import com.alternativeinfrastructures.noise.models.RemoteIdentity
import com.raizlabs.android.dbflow.sql.language.SQLite

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.whispersystems.libsignal.IdentityKey
import org.whispersystems.libsignal.ecc.Curve

import java.io.ByteArrayInputStream

import okio.Okio

import org.junit.Assert.*

@RunWith(RobolectricTestRunner::class)
class IdentityAnnouncementMessageTest : TestBase() {
    @Test
    @Throws(Exception::class)
    fun castThroughUnknownMessage() {
        val zeroBits: Byte = 10
        // Full UTF-8 should be supported here, so there's a poop emoji at the end of this username
        val username = "testUsername\uD83D\uDCA9"
        val deviceId = 42
        val identityKeyData = byteArrayOf(Curve.DJB_TYPE.toByte(), 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32)
        val identityKey = IdentityKey(identityKeyData, 0 /*offset*/)

        val identity = RemoteIdentity(username, deviceId, identityKey)
        identity.save().blockingGet()
        assertEquals(1, SQLite.selectCountOf().from<RemoteIdentity>(RemoteIdentity::class.java).longValue())

        val message = IdentityAnnouncementMessage.createAndSignAsync(identity, zeroBits).blockingGet()
        assertTrue(message.isValid)
        assertEquals(1, SQLite.selectCountOf().from<UnknownMessage>(UnknownMessage::class.java).longValue())
        assertEquals(1, SQLite.selectCountOf().from<IdentityAnnouncementMessage>(IdentityAnnouncementMessage::class.java).longValue())

        val savedMessage = message.writeToByteArray()

        message.deleteAsync().blockingGet()
        assertEquals(0, SQLite.selectCountOf().from<UnknownMessage>(UnknownMessage::class.java).longValue())
        assertEquals(0, SQLite.selectCountOf().from<IdentityAnnouncementMessage>(IdentityAnnouncementMessage::class.java).longValue())

        identity.delete().blockingGet()
        assertEquals(0, SQLite.selectCountOf().from<RemoteIdentity>(RemoteIdentity::class.java).longValue())

        val messageStream = ByteArrayInputStream(savedMessage)
        val reloadedMessage = UnknownMessage.fromSource(Okio.buffer(Okio.source(messageStream))).saveAsync().blockingGet()

        assertTrue(reloadedMessage is IdentityAnnouncementMessage)
        assertTrue(reloadedMessage.isValid)
        assertEquals(1, SQLite.selectCountOf().from<IdentityAnnouncementMessage>(IdentityAnnouncementMessage::class.java).longValue())
        assertEquals(1, SQLite.selectCountOf().from<RemoteIdentity>(RemoteIdentity::class.java).longValue())

        val reloadedIdentity = SQLite.select().from<RemoteIdentity>(RemoteIdentity::class.java).queryList()[0]
        assertEquals(identity, reloadedIdentity)
    }
}

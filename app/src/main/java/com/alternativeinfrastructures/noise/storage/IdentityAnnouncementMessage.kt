package com.alternativeinfrastructures.noise.storage

import com.alternativeinfrastructures.noise.NoiseDatabase
import com.alternativeinfrastructures.noise.models.RemoteIdentity

import com.raizlabs.android.dbflow.annotation.ForeignKey
import com.raizlabs.android.dbflow.annotation.Index
import com.raizlabs.android.dbflow.annotation.Table

import org.whispersystems.libsignal.IdentityKey
import org.whispersystems.libsignal.InvalidKeyException
import org.whispersystems.libsignal.ecc.ECPublicKey

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

import io.reactivex.Single
import okio.Okio
import okio.Utf8
import java.util.*

@Table(database = NoiseDatabase::class)
class IdentityAnnouncementMessage : UnknownMessage {

    @ForeignKey(stubbedRelationship = true)
    @Index
    var identity: RemoteIdentity? = null

    constructor() {}

    @Throws(InvalidKeyException::class, IOException::class)
    protected constructor(message: UnknownMessage) : super(message) {

        if (MessageTypes[publicType] != IdentityAnnouncementMessage::class.java)
            throw ClassCastException()

        val payloadSource = Okio.buffer(Okio.source(ByteArrayInputStream(payload.blob)))

        val usernameLength = payloadSource.readByte()
        val username = payloadSource.readString(usernameLength.toLong(), UnknownMessage.PAYLOAD_CHARSET)
        val deviceId = payloadSource.readInt()
        val identityKeyBytes = payloadSource.readByteArray(IDENTITY_KEY_SIZE.toLong())

        val identityKey = IdentityKey(identityKeyBytes, 0 /*offset*/)
        val identity = RemoteIdentity(username, deviceId, identityKey)
        // TODO: Add a mechanism to wait for this to finish saving?
        identity.save().subscribe()
    }

    override fun save(): Single<Boolean> {
        return identity!!.save().flatMap { Boolean -> super.save() }
    }

    companion object {
        @Throws(PayloadTooLargeException::class)
        fun rawCreateAndSignAsync(payload: ByteArray, zeroBits: Byte, publicType: UUID): Single<UnknownMessage> =
                UnknownMessage.rawCreateAndSignAsync(payload, zeroBits, publicType)

        @Throws(UnknownMessage.PayloadTooLargeException::class, IOException::class)
        fun createAndSignAsync(identity: RemoteIdentity, zeroBits: Byte): Single<UnknownMessage> {
            val payloadStream = ByteArrayOutputStream()
            val payloadSink = Okio.buffer(Okio.sink(payloadStream))

            payloadSink.writeByte(Utf8.size(identity.username).toByte().toInt())
            payloadSink.writeString(identity.username, UnknownMessage.Companion.PAYLOAD_CHARSET)
            payloadSink.writeInt(identity.deviceId)
            payloadSink.write(identity.identityKey.serialize(), 0 /*offset*/, IDENTITY_KEY_SIZE)
            payloadSink.flush()

            return IdentityAnnouncementMessage.rawCreateAndSignAsync(
                    payloadStream.toByteArray(), zeroBits, MessageTypes[IdentityAnnouncementMessage::class.java]!!)
        }

        // TODO: Tune this value
        val DEFAULT_ZERO_BITS: Byte = 22
        private val IDENTITY_KEY_SIZE = ECPublicKey.KEY_SIZE
    }
}

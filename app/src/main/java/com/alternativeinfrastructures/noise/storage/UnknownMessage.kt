package com.alternativeinfrastructures.noise.storage

import android.os.Looper
import android.util.Base64
import android.util.Log

import com.alternativeinfrastructures.noise.NoiseDatabase
import com.raizlabs.android.dbflow.annotation.Column
import com.raizlabs.android.dbflow.annotation.Index
import com.raizlabs.android.dbflow.annotation.PrimaryKey
import com.raizlabs.android.dbflow.annotation.Table
import com.raizlabs.android.dbflow.config.FlowManager
import com.raizlabs.android.dbflow.data.Blob
import com.raizlabs.android.dbflow.rx2.structure.BaseRXModel
import com.raizlabs.android.dbflow.sql.language.SQLite
import com.raizlabs.android.dbflow.structure.database.DatabaseWrapper

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.Charset
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.util.Arrays
import java.util.Date
import java.util.UUID

import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import okio.BufferedSink
import okio.BufferedSource
import okio.Okio

@Table(database = NoiseDatabase::class)
open class UnknownMessage : BaseRXModel {

    // TODO: If we're going to subclass this the autoincrement id has to go
    // Maybe use the last few bytes of the generated hash?
    @PrimaryKey
    var id: Long = 0

    @Column
    var version: Byte = 0

    @Column
    var zeroBits: Byte = 0

    @Column
    var date = Date(0)

    @Column
    @Index
    var payload = Blob()

    @Column
    var counter: Int = 0

    @Column
    var publicType = UUID(0, 0)

    // TODO: Validate the other fields first before calculating and checking the hash
    // (i.e. if the message is expired or we don't support the message version, return false now)
    // TODO: Say *why* the message is invalid (probably by returning an enum instead of a boolean)
    // TODO: Do we want to do a double hash (like Bitcoin) to avoid potential birthday collision attacks?
    // Do we have enough zero bytes? First check the fully-zero bytes...
    // ... then check the remaining zero bits in the last byte.
    val isValid: Boolean
        get() {
            val hash: ByteArray
            try {
                hash = calculateHash()
            } catch (e: NotHashableException) {
                return false
            }

            val zeroBytes = zeroBits / 8
            if (hash.size <= zeroBytes) {
                Log.e(TAG, "Message requires " + zeroBits + " zero bits but the hash is only " + hash.size * 8 + " bits long")
                return false
            }
            for (hashIndex in 0 until zeroBytes) {
                if (hash[hashIndex].toInt() != 0) {
                    return false
                }
            }
            val zeroBitsRemaining = zeroBits % 8
            if (zeroBitsRemaining != 0) {
                // Kotlin doesn't support bitwise and of bytes.
                val lastZeroByte = hash[zeroBytes].toInt()
                val mask = 0xFF shl 8 - zeroBitsRemaining
                if (lastZeroByte and mask != 0) {
                    return false
                }
            }

            return true
        }

    constructor() {}

    class PayloadTooLargeException : Exception()
    class InvalidMessageException : Exception()
    class NotHashableException : Exception()

    protected constructor(other: UnknownMessage) {
        // Copy constructor provided to populate subclasses
        id = other.id
        version = other.version
        zeroBits = other.zeroBits
        date = other.date
        payload = other.payload
        counter = other.counter
        publicType = other.publicType
    }

    @Throws(IOException::class)
    fun writeToSink(sink: BufferedSink) {
        if (payload.blob.size != PAYLOAD_SIZE)
            throw IOException("Payload is " + payload.blob.size + " bytes when it should always be " + PAYLOAD_SIZE)

        sink.writeByte(version.toInt())
        sink.writeByte(zeroBits.toInt())
        sink.writeLong(date.time)
        sink.write(payload.blob)
        sink.writeInt(counter)
        sink.writeLong(publicType.mostSignificantBits)
        sink.writeLong(publicType.leastSignificantBits)
    }

    @Throws(IOException::class)
    fun writeToByteArray(): ByteArray {
        // TODO: Add a way to calculate what the actual expected size is instead of using this guess
        val byteStream = ByteArrayOutputStream(PAYLOAD_SIZE * 2)
        val byteSink = Okio.buffer(Okio.sink(byteStream))
        writeToSink(byteSink)
        byteSink.flush()

        return byteStream.toByteArray()
    }

    override fun equals(`object`: Any?): Boolean {
        val other: UnknownMessage
        if (`object` !is UnknownMessage)
            return false

        other = `object`
        return version == other.version &&
                zeroBits == other.zeroBits &&
                date == other.date &&
                Arrays.equals(payload.blob, other.payload.blob) &&
                counter == other.counter &&
                publicType == other.publicType
    }

    @Throws(NotHashableException::class)
    fun calculateHash(): ByteArray {
        val digest: MessageDigest

        // TODO: This can be cleaner with okio.HashingSink
        try {
            digest = MessageDigest.getInstance(HASH_ALGORITHM)
        } catch (e: NoSuchAlgorithmException) {
            Log.wtf(TAG, "Couldn't use $HASH_ALGORITHM", e)
            val hashException = NotHashableException()
            hashException.initCause(e)
            throw hashException
        }

        val message: ByteArray
        try {
            message = writeToByteArray()
        } catch (e: IOException) {
            Log.e(TAG, "Couldn't serialize while validating", e)
            val hashException = NotHashableException()
            hashException.initCause(e)
            throw hashException
        }

        return digest.digest(message)
    }

    @Throws(NotHashableException::class)
    fun calculateId(): Long {
        val hash = calculateHash()
        var id: Long = 0
        for (i in 0..7 /*bytes in a long*/)
            id += hash[hash.size - i - 1].toLong() and 0xffL shl i * 8
        return id
    }

    fun saveAsync(): Single<UnknownMessage> {
        val messageToSave = this
        return Single.fromCallable {
            // TODO: Include the reason *why* the message is invalid in the exception
            if (!messageToSave.isValid)
                throw InvalidMessageException()

            // TODO: This is duplicating work from the last call to isValid
            messageToSave.id = messageToSave.calculateId()

            val typedMessage = MessageTypes.downcastIfKnown(messageToSave)

            FlowManager.getDatabase(NoiseDatabase::class.java).beginTransactionAsync { databaseWrapper: DatabaseWrapper ->
                val equalMessages = SQLite.selectCountOf().from(UnknownMessage::class.java)
                        .where(UnknownMessage_Table.payload.eq(messageToSave.payload)).longValue()
                if (equalMessages > 0) {
                    // TODO: In this case, we should keep the message that expires later - someone intentionally signed it again
                    Log.d(TAG, "Skipped saving an existing message")
                } else {
                    // blockingGet is okay here because this is ultimately wrapped in a Callable
                    messageToSave.save(databaseWrapper).blockingGet()

                    // DBFlow doesn't automatically add base classes as their own row
                    // TODO: UnknownMessage and its typed counterpart need to have the same lifetime
                    typedMessage?.save(databaseWrapper)?.blockingGet()

                    // TODO: Do this using a listener and then we won't need saveAsync anymore (message.insert() will implicitly manage the filter)
                    // https://agrosner.gitbooks.io/dbflow/content/Observability.html
                    BloomFilter.addMessage(messageToSave, databaseWrapper)
                    Log.d(TAG, "Saved a message with id " + messageToSave.id)
                }
            }.build().executeSync()
            typedMessage ?: messageToSave
        }.subscribeOn(Schedulers.computation())
    }

    fun deleteAsync(): Single<Boolean> {
        // Ensures that we are deleting from the UnknownMessage table first
        val message = UnknownMessage(this)
        val typedMessage = MessageTypes.downcastIfKnown(message)
        return if (typedMessage != null) typedMessage.delete().flatMap { Boolean -> message.delete() } else message.delete()
    }

    private fun sign(): UnknownMessage {
        // Signing will use 100% of one core for a few seconds. Don't do it on the UI thread.
        // TODO: Sign on multiple threads
        // TODO: Use a memory-intensive proof-of-work function to minimize the impact of bogus messages signed by ASICs (like Ethereum)
        // http://www.ethdocs.org/en/latest/introduction/what-is-ethereum.html#how-does-ethereum-work
        if (Looper.getMainLooper() == Looper.myLooper())
            Log.e(TAG, "Attempting to sign on the UI thread")

        Log.d(TAG, "Signing started")

        val started = System.nanoTime()
        counter = 0
        while (counter < Integer.MAX_VALUE) {
            if (isValid)
                break
            ++counter
        }
        val finished = System.nanoTime()

        Log.d(TAG, "Signing took " + (finished - started) / 1000000 + " ms")

        return this
    }

    // Raw data, used only for debugging purposes
    override fun toString(): String {
        return Base64.encodeToString(this.payload.blob, Base64.NO_WRAP)
    }

    companion object {
        val TAG = "UnknownMessage"
        // TODO: Design and implement database syncing across devices

        val HASH_ALGORITHM = "SHA-256"
        val PAYLOAD_CHARSET = Charset.forName("UTF-8")

        // TODO: Tune the size to something more appropriate
        val PAYLOAD_SIZE = 240

        @Throws(PayloadTooLargeException::class)
        fun rawCreateAndSignAsync(payload: ByteArray, zeroBits: Byte, publicType: UUID): Single<UnknownMessage> {
            var payload = payload
            if (payload.size < PAYLOAD_SIZE) {
                val paddedPayload = ByteArray(PAYLOAD_SIZE)
                SecureRandom().nextBytes(paddedPayload)
                System.arraycopy(payload, 0, paddedPayload, 0, payload.size)
                payload = paddedPayload
            } else if (payload.size > PAYLOAD_SIZE) {
                throw PayloadTooLargeException()
            }

            val message = UnknownMessage()
            message.version = 2
            message.zeroBits = zeroBits
            // TODO: Derive an expiration from the number of zero bits
            // Expiration should be derived from number of zero bits - the more declared zero digits in the generated hash, the further out the expiration
            message.date = Date()
            message.payload = Blob(payload)
            message.publicType = publicType

            return Single.fromCallable<UnknownMessage>(message::sign).flatMap<UnknownMessage>(UnknownMessage::saveAsync).subscribeOn(Schedulers.computation())
        }

        @Throws(IOException::class)
        fun fromSource(source: BufferedSource): UnknownMessage {
            // TODO: This I/O shouldn't happen directly on this thread!
            // But it's probably okay as long as it's called from a networking thread
            if (Looper.getMainLooper().thread === Thread.currentThread())
                Log.e(TAG, "Attempting to read from a network on the UI thread")

            val message = UnknownMessage()
            message.version = source.readByte()
            message.zeroBits = source.readByte()
            message.date = Date(source.readLong())
            message.payload = Blob(source.readByteArray(PAYLOAD_SIZE.toLong()))
            message.counter = source.readInt()
            val typeMsb = source.readLong()
            val typeLsb = source.readLong()
            message.publicType = UUID(typeMsb, typeLsb)

            return message
        }
    }
}

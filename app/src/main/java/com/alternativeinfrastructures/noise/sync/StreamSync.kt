package com.alternativeinfrastructures.noise.sync

import android.util.Log

import com.alternativeinfrastructures.noise.storage.BloomFilter
import com.alternativeinfrastructures.noise.storage.UnknownMessage

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.BitSet
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.FlowableEmitter
import io.reactivex.schedulers.Schedulers
import okio.BufferedSink
import okio.BufferedSource
import okio.Okio

object StreamSync {
    val TAG = "StreamSync"

    private val PROTOCOL_NAME = "Noise0"
    private val DEFAULT_CHARSET = Charset.forName("US-ASCII")

    fun bidirectionalSync(inputStream: InputStream, outputStream: OutputStream) {
        Log.d(TAG, "Starting sync")

        // TODO: Set timeouts

        val source = Okio.buffer(Okio.source(inputStream))
        val sink = Okio.buffer(Okio.sink(outputStream))
        val ioExecutors = Executors.newFixedThreadPool(2) // Separate threads for send and receive

        val handshakeFutures = handshakeAsync(source, sink, ioExecutors)

        try {
            handshakeFutures.get()
        } catch (e: Exception) {
            Log.e(TAG, "Handshake failed", e)
            return
        }

        Log.d(TAG, "Connected to a peer")

        val myMessageVector = BloomFilter.messageVectorAsync.blockingGet()
        val messageVectorFutures = exchangeMessageVectorsAsync(myMessageVector, source, sink, ioExecutors)

        val theirMessageVector: BitSet?
        try {
            theirMessageVector = messageVectorFutures.get()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to exchange message vectors", e)
            return
        }

        // TODO: Include a subset of the message vector in the broadcast and verify that theirMessageVector matches

        Log.d(TAG, "Exchanged message vectors")
        ioExecutors.shutdown()

        val vectorDifference = myMessageVector.clone() as BitSet
        vectorDifference.andNot(theirMessageVector)

        // TODO: Is this I/O as parallel as you think it is? Look into explicitly using separate threads for these
        val myMessages = BloomFilter.getMatchingMessages(vectorDifference)
        sendMessagesAsync(myMessages, sink)

        val theirMessages = receiveMessagesAsync(source)
        theirMessages.subscribe(
                { message: UnknownMessage -> message.saveAsync().subscribe() },
                { e: Throwable -> Log.e(TAG, "Error receiving messages", e) })

        // Wait until both complete so that we don't prematurely close the connection
        Log.d(TAG, "Sync completed")
    }

    private enum class Messages private constructor(internal val value: Byte) {
        MESSAGE_VECTOR(1.toByte()),
        MESSAGE(2.toByte()),
        END(3.toByte())
    }

    internal class IOFutures<T> {
        var sender: Future<Void>? = null
        var receiver: Future<T>? = null

        @Throws(InterruptedException::class, ExecutionException::class)
        fun get(): T? {
            if (sender != null)
                sender!!.get()

            return if (receiver != null)
                receiver!!.get()
            else
                null
        }
    }

    internal fun handshakeAsync(source: BufferedSource, sink: BufferedSink, ioExecutors: ExecutorService): IOFutures<String> {
        if (PROTOCOL_NAME.length > java.lang.Byte.MAX_VALUE)
            Log.wtf(TAG, "Protocol name is too long")

        val futures = IOFutures<String>()

        futures.sender = ioExecutors.submit<Void> {
            sink.writeByte(PROTOCOL_NAME.length)
            sink.writeString(PROTOCOL_NAME, DEFAULT_CHARSET)
            sink.flush()
            null
        }

        futures.receiver = ioExecutors.submit<String> {
            val protocolNameLength = source.readByte()
            val protocolName = source.readString(protocolNameLength.toLong(), DEFAULT_CHARSET)
            if (protocolName != PROTOCOL_NAME)
                throw IOException("Protocol \"$protocolName\" not supported")
            protocolName
        }

        return futures
    }

    internal fun exchangeMessageVectorsAsync(
            myMessageVector: BitSet, source: BufferedSource, sink: BufferedSink, ioExecutors: ExecutorService): IOFutures<BitSet> {
        val futures = IOFutures<BitSet>()

        futures.sender = ioExecutors.submit<Void> {
            sink.writeByte(Messages.MESSAGE_VECTOR.value.toInt())
            sink.write(myMessageVector.toByteArray())
            sink.flush()
            null
        }

        futures.receiver = ioExecutors.submit<BitSet> {
            val messageType = source.readByte()
            // TODO: Make an exception type for protocol errors
            if (messageType != Messages.MESSAGE_VECTOR.value)
                throw IOException("Expected a message vector but got $messageType")

            val theirMessageVectorByteArray = source.readByteArray(BloomFilter.SIZE_IN_BYTES.toLong())
            BitSet.valueOf(theirMessageVectorByteArray)
        }

        return futures
    }

    internal fun sendMessagesAsync(myMessages: Flowable<UnknownMessage>, sink: BufferedSink) {
        Log.d(TAG, "Sending messages")
        myMessages.subscribe({ message: UnknownMessage ->
            sink.writeByte(Messages.MESSAGE.value.toInt())
            message.writeToSink(sink)
            sink.flush()
        }, { e: Throwable -> Log.e(TAG, "Error sending messages", e) }, {
            Log.d(TAG, "Sent messages")
            sink.writeByte(Messages.END.value.toInt())
            sink.flush()
        })
    }

    internal fun receiveMessagesAsync(source: BufferedSource): Flowable<UnknownMessage> {
        Log.d(TAG, "Receiving messages")
        return Flowable.create({ messageEmitter: FlowableEmitter<UnknownMessage> ->
            var messageCount = 0
            while (true) {
                val messageType = source.readByte()
                if (messageType == Messages.END.value)
                    break
                else if (messageType != Messages.MESSAGE.value)
                    messageEmitter.onError(IOException("Expected a message but got $messageType"))

                messageEmitter.onNext(UnknownMessage.fromSource(source))
                ++messageCount
            }

            messageEmitter.onComplete()
            Log.d(TAG, "Received $messageCount messages")
        }, BackpressureStrategy.BUFFER)
    }
}

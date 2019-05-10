package com.alternativeinfrastructures.noise.sync

import com.alternativeinfrastructures.noise.TestBase
import com.alternativeinfrastructures.noise.storage.BloomFilter
import com.alternativeinfrastructures.noise.storage.UnknownMessage
import com.alternativeinfrastructures.noise.storage.UnknownMessageTest

import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

import java.util.ArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

import io.reactivex.Flowable
import okio.Okio
import okio.Pipe

import org.junit.Assert.*

@RunWith(RobolectricTestRunner::class)
class StreamSyncTest : TestBase() {

    private var executors: ExecutorService? = null
    private var firstToSecond = Pipe(PIPE_SIZE)
    private var secondToFirst = Pipe(PIPE_SIZE)
    private var firstSource = Okio.buffer(secondToFirst.source())
    private var secondSource = Okio.buffer(firstToSecond.source())
    private var firstSink = Okio.buffer(firstToSecond.sink())
    private var secondSink = Okio.buffer(secondToFirst.sink())

    @Before
    override fun setup() {
        super.setup()

        executors = Executors.newFixedThreadPool(4)
    }

    @After
    override fun teardown() {
        executors!!.shutdown()

        super.teardown()
    }

    @Test
    @Throws(Exception::class)
    fun handshake() {
        val firstFutures = StreamSync.handshakeAsync(
                firstSource, firstSink, executors)
        val secondFutures = StreamSync.handshakeAsync(
                secondSource, secondSink, executors)

        firstFutures.get()
        secondFutures.get()

        // TODO: Test failure conditions
    }

    @Test
    @Throws(Exception::class)
    fun exchangeMessageVectors() {
        val firstMessageVector = BloomFilter.makeEmptyMessageVector()
        val secondMessageVector = BloomFilter.makeEmptyMessageVector()

        // Arbitrary bits within BloomFilter.SIZE that we'll check for later
        firstMessageVector.set(193)
        firstMessageVector.set(719418)
        firstMessageVector.set(1048574)
        secondMessageVector.set(378)
        secondMessageVector.set(87130)
        secondMessageVector.set(183619)

        assertEquals(firstMessageVector.length().toLong(), BloomFilter.makeEmptyMessageVector().length().toLong())
        assertEquals(firstMessageVector.length().toLong(), secondMessageVector.length().toLong())

        val firstFutures = StreamSync.exchangeMessageVectorsAsync(
                firstMessageVector, firstSource, firstSink, executors!!)
        val secondFutures = StreamSync.exchangeMessageVectorsAsync(
                secondMessageVector, secondSource, secondSink, executors!!)

        val firstMessageVectorAfterExchange = secondFutures.get()
        val secondMessageVectorAfterExchange = firstFutures.get()

        assertEquals(firstMessageVector.length().toLong(), firstMessageVectorAfterExchange!!.length().toLong())
        assertEquals(firstMessageVector, firstMessageVectorAfterExchange)

        assertEquals(secondMessageVector.length().toLong(), secondMessageVectorAfterExchange!!.length().toLong())
        assertEquals(secondMessageVector, secondMessageVectorAfterExchange)

        // TODO: Test failure conditions
    }

    @Test
    @Throws(Exception::class)
    fun sendReceiveMessages() {
        val payload = "Test message".toByteArray()
        val numTestMessages = 10

        val testMessages = ArrayList<UnknownMessage>()
        for (i in 0 until numTestMessages)
            testMessages.add(UnknownMessageTest.createTestMessage(payload))

        StreamSync.sendMessagesAsync(Flowable.fromIterable(testMessages), firstSink)
        val receivedMessages = StreamSync.receiveMessagesAsync(secondSource).toList().blockingGet()

        for (i in 0 until numTestMessages)
            assertEquals(testMessages, receivedMessages)
    }

    companion object {
        const val PIPE_SIZE: Long = 16384
        const val TIMEOUT_VALUE = 10
        internal val TIMEOUT_UNIT = TimeUnit.SECONDS
    }
}

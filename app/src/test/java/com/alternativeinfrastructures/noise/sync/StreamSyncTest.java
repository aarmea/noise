package com.alternativeinfrastructures.noise.sync;

import com.alternativeinfrastructures.noise.BuildConfig;
import com.alternativeinfrastructures.noise.TestBase;
import com.alternativeinfrastructures.noise.storage.BloomFilter;
import com.alternativeinfrastructures.noise.storage.UnknownMessage;
import com.alternativeinfrastructures.noise.storage.UnknownMessageTest;
import com.raizlabs.android.dbflow.config.FlowManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.reactivex.Flowable;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;
import okio.Pipe;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class)
public class StreamSyncTest extends TestBase {
    static final long PIPE_SIZE = 16384;
    static final int TIMEOUT_VALUE = 10;
    static final TimeUnit TIMEOUT_UNIT = TimeUnit.SECONDS;

    private ExecutorService executors;
    private Pipe firstToSecond, secondToFirst;
    private BufferedSource firstSource, secondSource;
    private BufferedSink firstSink, secondSink;

    @Before
    public void setup() {
        super.setup();

        executors = Executors.newFixedThreadPool(4);

        firstToSecond = new Pipe(PIPE_SIZE);
        secondToFirst = new Pipe(PIPE_SIZE);

        firstSource = Okio.buffer(secondToFirst.source());
        firstSource.timeout().timeout(TIMEOUT_VALUE, TIMEOUT_UNIT);

        firstSink = Okio.buffer(firstToSecond.sink());
        firstSink.timeout().timeout(TIMEOUT_VALUE, TIMEOUT_UNIT);

        secondSource = Okio.buffer(firstToSecond.source());
        secondSource.timeout().timeout(TIMEOUT_VALUE, TIMEOUT_UNIT);

        secondSink = Okio.buffer(secondToFirst.sink());
        secondSink.timeout().timeout(TIMEOUT_VALUE, TIMEOUT_UNIT);
    }

    @After
    public void teardown() {
        executors.shutdown();

        super.teardown();
    }

    @Test
    public void handshake() throws Exception {
        StreamSync.IOFutures<String> firstFutures = StreamSync.handshakeAsync(
                firstSource, firstSink, executors);
        StreamSync.IOFutures<String> secondFutures = StreamSync.handshakeAsync(
                secondSource, secondSink, executors);

        firstFutures.get();
        secondFutures.get();

        // TODO: Test failure conditions
    }

    @Test
    public void exchangeMessageVectors() throws Exception {
        BitSet firstMessageVector = BloomFilter.makeEmptyMessageVector();
        BitSet secondMessageVector = BloomFilter.makeEmptyMessageVector();

        // Arbitrary bits within BloomFilter.SIZE that we'll check for later
        firstMessageVector.set(193);
        firstMessageVector.set(719418);
        firstMessageVector.set(1048574);
        secondMessageVector.set(378);
        secondMessageVector.set(87130);
        secondMessageVector.set(183619);

        assertEquals(firstMessageVector.length(), BloomFilter.makeEmptyMessageVector().length());
        assertEquals(firstMessageVector.length(), secondMessageVector.length());

        StreamSync.IOFutures<BitSet> firstFutures = StreamSync.exchangeMessageVectorsAsync(
                firstMessageVector, firstSource, firstSink, executors);
        StreamSync.IOFutures<BitSet> secondFutures = StreamSync.exchangeMessageVectorsAsync(
                secondMessageVector, secondSource, secondSink, executors);

        BitSet firstMessageVectorAfterExchange = secondFutures.get();
        BitSet secondMessageVectorAfterExchange = firstFutures.get();

        assertEquals(firstMessageVector.length(), firstMessageVectorAfterExchange.length());
        assertEquals(firstMessageVector, firstMessageVectorAfterExchange);

        assertEquals(secondMessageVector.length(), secondMessageVectorAfterExchange.length());
        assertEquals(secondMessageVector, secondMessageVectorAfterExchange);

        // TODO: Test failure conditions
    }

    @Test
    public void sendReceiveMessages() throws Exception {
        byte[] payload = "Test message".getBytes();
        int numTestMessages = 10;

        ArrayList<UnknownMessage> testMessages = new ArrayList<UnknownMessage>();
        for (int i = 0; i < numTestMessages; ++i)
            testMessages.add(UnknownMessageTest.createTestMessage(payload));

        StreamSync.sendMessagesAsync(Flowable.fromIterable(testMessages), firstSink);
        List<UnknownMessage> receivedMessages =
                StreamSync.receiveMessagesAsync(secondSource).toList().blockingGet();

        for (int i = 0; i < numTestMessages; ++i)
            assertEquals(testMessages, receivedMessages);
    }
}

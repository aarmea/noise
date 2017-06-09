package com.alternativeinfrastructures.noise.sync;

import com.alternativeinfrastructures.noise.BuildConfig;
import com.raizlabs.android.dbflow.config.FlowManager;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;
import okio.Pipe;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class)
public class StreamSyncTest {
    static final long PIPE_SIZE = 1024;

    @After
    public void teardown() {
        // DBFlow doesn't automatically close its database handle when a test ends.
        // https://github.com/robolectric/robolectric/issues/1890#issuecomment-218880541
        FlowManager.destroy();
    }

    @Test
    public void handshake() throws Exception {
        ExecutorService executors = Executors.newFixedThreadPool(4);

        Pipe firstToSecond = new Pipe(PIPE_SIZE);
        Pipe secondToFirst = new Pipe(PIPE_SIZE);

        BufferedSource firstSource = Okio.buffer(secondToFirst.source());
        BufferedSink firstSink = Okio.buffer(firstToSecond.sink());
        BufferedSource secondSource = Okio.buffer(firstToSecond.source());
        BufferedSink secondSink = Okio.buffer(secondToFirst.sink());

        StreamSync.IOFutures<String> firstFutures = StreamSync.handshakeAsync(firstSource, firstSink, executors);
        StreamSync.IOFutures<String> secondFutures = StreamSync.handshakeAsync(secondSource, secondSink, executors);

        firstFutures.get();
        secondFutures.get();

        executors.shutdown();

        // TODO: Test failure conditions
    }

    // TODO: Message vector exchange test
    // TODO: Message send test
    // TODO: Message receive test
}

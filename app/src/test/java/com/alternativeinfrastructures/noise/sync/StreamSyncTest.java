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

import okio.Buffer;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class)
public class StreamSyncTest {
    @After
    public void teardown() {
        // DBFlow doesn't automatically close its database handle when a test ends.
        // https://github.com/robolectric/robolectric/issues/1890#issuecomment-218880541
        FlowManager.destroy();
    }

    @Test
    public void handshake() throws Exception {
        ExecutorService firstInstanceThreads = Executors.newFixedThreadPool(2);
        ExecutorService secondInstanceThreads = Executors.newFixedThreadPool(2);

        Buffer firstToSecondBuffer = new Buffer();
        Buffer secondToFirstBuffer = new Buffer();

        StreamSync.handshakeAsync(firstToSecondBuffer, secondToFirstBuffer, firstInstanceThreads);
        StreamSync.handshakeAsync(secondToFirstBuffer, firstToSecondBuffer, secondInstanceThreads);

        firstInstanceThreads.shutdown();
        secondInstanceThreads.shutdown();

        // TODO: Test failure conditions
    }

    // TODO: Message vector exchange test
    // TODO: Message send test
    // TODO: Message receive test
}

package com.alternativeinfrastructures.noise.storage;


import com.alternativeinfrastructures.noise.BuildConfig;
import com.alternativeinfrastructures.noise.TestBase;
import com.raizlabs.android.dbflow.config.FlowManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

import java.util.UUID;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
// @Config(constants = BuildConfig.class)
public class MessageTypesTest extends TestBase {
    @Test
    public void downcastUnknownType() throws Exception {
        final byte[] payload = "This message should not downcast".getBytes();
        final byte zeroBits = 10;
        final UUID type = new UUID(0, 0);
        UnknownMessage message = UnknownMessage.createAndSignAsync(payload, zeroBits, type).blockingGet();

        assertEquals(UnknownMessage.class, message.getClass());
    }

    // TODO: Test other ways downcasting can fail
}

package com.alternativeinfrastructures.noise.storage;

import com.alternativeinfrastructures.noise.BuildConfig;
import com.alternativeinfrastructures.noise.TestBase;
import com.alternativeinfrastructures.noise.models.RemoteIdentity;
import com.raizlabs.android.dbflow.config.FlowManager;
import com.raizlabs.android.dbflow.sql.language.SQLite;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.ecc.Curve;

import java.io.ByteArrayInputStream;

import okio.Okio;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class)
public class IdentityAnnouncementMessageTest extends TestBase {
    @Test
    public void castThroughUnknownMessage() throws Exception {
        final byte zeroBits = 10;
        // Full UTF-8 should be supported here, so there's a poop emoji at the end of this username
        final String username = "testUsername\uD83D\uDCA9";
        final int deviceId = 42;
        final byte[] identityKeyData = {Curve.DJB_TYPE,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32};
        IdentityKey identityKey = new IdentityKey(identityKeyData, 0 /*offset*/);

        RemoteIdentity identity = new RemoteIdentity(username, deviceId, identityKey);
        identity.save().blockingGet();
        assertEquals(1, SQLite.selectCountOf().from(RemoteIdentity.class).count());

        UnknownMessage message = IdentityAnnouncementMessage.createAndSignAsync(identity, zeroBits).blockingGet();
        assertTrue(message.isValid());
        assertEquals(1, SQLite.selectCountOf().from(UnknownMessage.class).count());
        assertEquals(1, SQLite.selectCountOf().from(IdentityAnnouncementMessage.class).count());

        byte[] savedMessage = message.writeToByteArray();

        message.deleteAsync().blockingGet();
        assertEquals(0, SQLite.selectCountOf().from(UnknownMessage.class).count());
        assertEquals(0, SQLite.selectCountOf().from(IdentityAnnouncementMessage.class).count());

        identity.delete().blockingGet();
        assertEquals(0, SQLite.selectCountOf().from(RemoteIdentity.class).count());

        ByteArrayInputStream messageStream = new ByteArrayInputStream(savedMessage);
        UnknownMessage reloadedMessage = UnknownMessage.fromSource(Okio.buffer(Okio.source(messageStream))).saveAsync().blockingGet();

        assertTrue(reloadedMessage instanceof IdentityAnnouncementMessage);
        assertTrue(reloadedMessage.isValid());
        assertEquals(1, SQLite.selectCountOf().from(IdentityAnnouncementMessage.class).count());
        assertEquals(1, SQLite.selectCountOf().from(RemoteIdentity.class).count());

        RemoteIdentity reloadedIdentity = SQLite.select().from(RemoteIdentity.class).queryList().get(0);
        assertEquals(identity, reloadedIdentity);
    }
}

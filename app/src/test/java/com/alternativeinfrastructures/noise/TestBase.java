package com.alternativeinfrastructures.noise;

import com.raizlabs.android.dbflow.config.FlowManager;

import org.junit.After;
import org.junit.Before;
import org.robolectric.shadows.ShadowLog;

public class TestBase {
    // Tests in here failed with "No such manifest file"?
    // Roboelectric needs the Android JUnit working directory set to $MODULE_DIR$.
    // https://github.com/robolectric/robolectric/issues/2949

    @Before
    public void setup() {
        // Redirect logcat to standard output for debugging
        // https://stackoverflow.com/questions/10219915/where-is-log-output-written-to-when-using-robolectric-roboguice
        ShadowLog.stream = System.out;
    }

    @After
    public void teardown() {
        // DBFlow doesn't automatically close its database handle when a test ends.
        // https://github.com/robolectric/robolectric/issues/1890#issuecomment-218880541
        FlowManager.destroy();
    }
}

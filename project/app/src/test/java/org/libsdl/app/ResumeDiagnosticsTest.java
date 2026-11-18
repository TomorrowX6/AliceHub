package org.libsdl.app;

import android.util.Log;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;
import static org.junit.Assert.*;

/** Snapshot hooks must not enter JNI or change the lifecycle state they observe. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ResumeDiagnosticsTest {
    private static final String TAG = "AliceHubResume";

    @Before public void reset() {
        SDLActivity.initialize();
        ShadowLog.clear();
    }

    @Test public void traceIsOffByDefaultPriority() {
        ShadowLog.setLoggable(TAG, Log.INFO);
        SDLActivity.traceResumeState("disabled");
        assertTrue(ShadowLog.getLogsForTag(TAG).isEmpty());
        assertEquals(SDLActivity.NativeState.INIT, SDLActivity.mCurrentNativeState);
    }

    @Test public void pendingResumeIsObservedWithoutExecutingIt() {
        ShadowLog.setLoggable(TAG, Log.DEBUG);
        SDLActivity.mCurrentNativeState = SDLActivity.NativeState.PAUSED;
        SDLActivity.mNextNativeState = SDLActivity.NativeState.RESUMED;
        SDLActivity.mIsResumedCalled = true;
        SDLActivity.mHasFocus = false;
        // No SDL libraries or native thread are loaded/started in this test.
        SDLActivity.traceResumeState("test.pending");
        assertEquals(1, ShadowLog.getLogsForTag(TAG).size());
        String text = ShadowLog.getLogsForTag(TAG).get(0).msg;
        assertTrue(text.contains("current=PAUSED next=RESUMED"));
        assertTrue(text.contains("resumed=true focus=false ready=false surfaceValid=false thread=none"));
        assertEquals(SDLActivity.NativeState.PAUSED, SDLActivity.mCurrentNativeState);
        assertEquals(SDLActivity.NativeState.RESUMED, SDLActivity.mNextNativeState);
        assertNull(SDLActivity.mSDLThread);
    }
}

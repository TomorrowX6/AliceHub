package io.github.kichikuou.xsystem4;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class UpdateCheckTest {
    @Test
    public void updateCheckDefaultsOnAndCanBeDisabled() {
        Context context = RuntimeEnvironment.getApplication();
        assertTrue(AppSettings.INSTANCE.isUpdateCheckEnabled(context));
        AppSettings.INSTANCE.setUpdateCheckEnabled(context, false);
        assertFalse(AppSettings.INSTANCE.isUpdateCheckEnabled(context));
        AppSettings.INSTANCE.setUpdateCheckEnabled(context, true);
        assertTrue(AppSettings.INSTANCE.isUpdateCheckEnabled(context));
    }

    @Test
    public void comparesReleaseTagWithBuildCommit() {
        String commit = "abcdef0123456789abcdef0123456789abcdef01";
        assertNull(UpdateCheck.INSTANCE.newReleaseTag(commit, "ABCDEF0"));
        assertEquals("1234567", UpdateCheck.INSTANCE.newReleaseTag(commit, "1234567"));
        assertNull(UpdateCheck.INSTANCE.newReleaseTag(commit, "v0.1"));
        assertNull(UpdateCheck.INSTANCE.newReleaseTag("unknown", "1234567"));
    }
}

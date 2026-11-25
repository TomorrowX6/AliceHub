package io.github.kichikuou.xsystem4;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
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
    public void updateBannerSurvivesLauncherRecreation() {
        Bundle state = new Bundle();
        state.putString("STATE_AVAILABLE_RELEASE", "1234567");
        try (ActivityController<LauncherActivity> controller =
                     Robolectric.buildActivity(LauncherActivity.class).setup(state)) {
            assertUpdateBanner(controller.get());
            controller.recreate();
            assertUpdateBanner(controller.get());
        }
    }

    private void assertUpdateBanner(LauncherActivity activity) {
        TextView banner = activity.findViewById(R.id.update_banner);
        assertEquals(View.VISIBLE, banner.getVisibility());
        assertEquals(activity.getString(R.string.update_available_message, "1234567"),
                banner.getText().toString());
    }

    @Test
    public void comparesReleaseTagWithBuildCommit() throws Exception {
        String commit = "abcdef0123456789abcdef0123456789abcdef01";
        assertEquals("https://api.github.com/repos/TomorrowX6/AliceHub/compare/1234567..."
                        + commit + "?per_page=1",
                UpdateCheck.INSTANCE.compareUrl(commit, "1234567"));
        assertNull(UpdateCheck.INSTANCE.compareUrl(commit, "v0.1"));
        assertNull(UpdateCheck.INSTANCE.compareUrl("unknown", "1234567"));
        assertTrue(UpdateCheck.INSTANCE.isBehind(new JSONObject().put("status", "behind")));
        for (String status : new String[]{"identical", "ahead", "diverged", ""}) {
            assertFalse(UpdateCheck.INSTANCE.isBehind(new JSONObject().put("status", status)));
        }
        assertFalse(UpdateCheck.INSTANCE.isBehind(new JSONObject()));
    }
}

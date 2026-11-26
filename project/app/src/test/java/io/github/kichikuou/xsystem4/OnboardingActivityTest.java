package io.github.kichikuou.xsystem4;

import android.app.AlertDialog;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class OnboardingActivityTest {
    @Test
    public void skipStorageAndAcceptOnlineInstallPersistsAfterFinishing() {
        try (ActivityController<OnboardingActivity> controller =
                     Robolectric.buildActivity(OnboardingActivity.class).setup()) {
            OnboardingActivity activity = controller.get();
            AppSettings settings = AppSettings.INSTANCE;
            assertFalse(settings.isOnboardingComplete(activity));
            assertStep(activity, 1);
            activity.findViewById(R.id.onboarding_next).performClick();
            assertStep(activity, 2);
            activity.findViewById(R.id.storage_option_private).performClick();

            controller.recreate();
            activity = controller.get();
            assertStep(activity, 2);
            activity.findViewById(R.id.onboarding_next).performClick();
            assertStep(activity, 3);
            assertFalse(settings.prefersPublicStorage(activity));
            assertFalse(settings.isOnlineInstallEnabled(activity));
            Switch toggle = findSwitch(activity.findViewById(R.id.onboarding_page));
            toggle.performClick();
            AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertFalse(toggle.isChecked());
            toggle.performClick();
            ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertTrue(toggle.isChecked());
            assertFalse(settings.isOnboardingComplete(activity));
            activity.findViewById(R.id.onboarding_next).performClick();

            assertTrue(settings.isOnboardingComplete(activity));
            assertTrue(settings.isOnlineInstallEnabled(activity));
            assertFalse(settings.prefersPublicStorage(activity));
        }
    }

    private static void assertStep(OnboardingActivity activity, int number) {
        TextView step = activity.findViewById(R.id.onboarding_step);
        assertEquals(activity.getString(R.string.onboarding_step, number, 3), step.getText().toString());
    }

    private static Switch findSwitch(View view) {
        if (view instanceof Switch) return (Switch) view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            Switch result = findSwitch(group.getChildAt(i));
            if (result != null) return result;
        }
        return null;
    }
}

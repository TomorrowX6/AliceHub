package io.github.kichikuou.xsystem4;

import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
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
import org.robolectric.util.ReflectionHelpers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class OnboardingActivityTest {
    @Test
    public void welcomeHasNoHaloAndShowsTextWithAnimationsDisabled() {
        float durationScale = ValueAnimator.getDurationScale();
        setAnimatorDurationScale(0f);
        try (ActivityController<OnboardingActivity> controller =
                     Robolectric.buildActivity(OnboardingActivity.class).setup()) {
            OnboardingActivity activity = controller.get();
            ViewGroup page = activity.findViewById(R.id.onboarding_page);
            ViewGroup content = (ViewGroup) page.getChildAt(0);
            View icon = content.getChildAt(0);
            View title = content.getChildAt(1);
            View subtitle = content.getChildAt(2);
            assertNull(icon.getBackground());
            assertFalse(ValueAnimator.areAnimatorsEnabled());
            assertNull(ReflectionHelpers.getField(activity, "welcomeAnimation"));
            assertEquals(1f, title.getAlpha(), 0f);
            assertEquals(1f, subtitle.getAlpha(), 0f);
            assertEquals(0f, subtitle.getTranslationY(), 0f);
        } finally {
            setAnimatorDurationScale(durationScale);
        }
    }

    private static void setAnimatorDurationScale(float value) {
        ReflectionHelpers.callStaticMethod(ValueAnimator.class, "setDurationScale",
                ReflectionHelpers.ClassParameter.from(float.class, value));
    }

    @Test
    public void upgradeCanKeepExistingFilesPrivateWithoutRequestingPermission() throws IOException {
        try (ActivityController<OnboardingActivity> controller =
                     Robolectric.buildActivity(OnboardingActivity.class).setup()) {
            OnboardingActivity activity = controller.get();
            File save = seedLegacySave(activity);
            activity.findViewById(R.id.onboarding_next).performClick();
            activity.findViewById(R.id.storage_option_private).performClick();
            activity.findViewById(R.id.onboarding_next).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();

            assertStep(activity, 3);
            assertNull(Shadows.shadowOf(activity).getNextStartedActivityForResult());
            activity.findViewById(R.id.onboarding_next).performClick();
            assertFalse(AppSettings.INSTANCE.prefersPublicStorage(activity));
            assertTrue(save.isFile());
        }
    }

    @Test
    public void confirmingSharedStorageOnUpgradeDirectlyRequestsPermission() throws IOException {
        try (ActivityController<OnboardingActivity> controller =
                     Robolectric.buildActivity(OnboardingActivity.class).setup()) {
            OnboardingActivity activity = controller.get();
            seedLegacySave(activity);
            Shadows.shadowOf(activity.getSystemService(AppOpsManager.class)).setMode(
                    "android:manage_external_storage", activity.getApplicationInfo().uid,
                    activity.getPackageName(), AppOpsManager.MODE_IGNORED);
            assertFalse(AppStorage.INSTANCE.hasPermission(activity));
            Intent permission = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + activity.getPackageName()));
            ResolveInfo resolver = new ResolveInfo();
            resolver.activityInfo = new ActivityInfo();
            resolver.activityInfo.packageName = "com.android.settings";
            resolver.activityInfo.name = "AllFilesAccess";
            Shadows.shadowOf(activity.getPackageManager()).addResolveInfoForIntent(permission, resolver);
            activity.findViewById(R.id.onboarding_next).performClick();
            assertNull(Shadows.shadowOf(activity).getNextStartedActivityForResult());

            activity.findViewById(R.id.onboarding_next).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertNull(ShadowAlertDialog.getLatestAlertDialog());
            assertTrue(AppSettings.INSTANCE.prefersPublicStorage(activity));
            assertEquals(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Shadows.shadowOf(activity).getNextStartedActivityForResult().intent.getAction());
        }
    }

    @Test
    @Config(qualifiers = "en-rUS")
    public void storageDetailsFollowBothChoicesAndSurviveRecreation() {
        try (ActivityController<OnboardingActivity> controller =
                     Robolectric.buildActivity(OnboardingActivity.class).setup()) {
            OnboardingActivity activity = controller.get();
            activity.findViewById(R.id.onboarding_next).performClick();
            assertPageIcon(activity, R.drawable.ic_folder_open);
            ViewGroup storageCard = activity.findViewById(R.id.storage_options);
            assertEquals(1, ((ViewGroup) storageCard.getParent()).getChildCount());
            assertStorageDetails(activity, true);
            activity.findViewById(R.id.storage_option_private).performClick();
            assertStorageDetails(activity, false);
            activity.findViewById(R.id.storage_option_public).performClick();
            assertStorageDetails(activity, true);
            activity.findViewById(R.id.storage_option_private).performClick();

            controller.recreate();
            activity = controller.get();
            assertStorageDetails(activity, false);
            activity.findViewById(R.id.onboarding_next).performClick();
            assertPageIcon(activity, R.drawable.ic_cloud_download);
        }
    }

    @Test
    @Config(qualifiers = "en-rUS")
    public void settingsStorageDialogUsesDirectoryLabelsWithoutOnboardingCopy() {
        try (ActivityController<SettingsActivity> controller =
                     Robolectric.buildActivity(SettingsActivity.class).setup()) {
            controller.get().findViewById(R.id.row_settings_storage).performClick();
            AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
            assertTrue(dialog.isShowing());
            TextView shared = dialog.findViewById(R.id.storage_option_public);
            TextView appSpecific = dialog.findViewById(R.id.storage_option_private);
            assertEquals("Shared storage directory", shared.getText().toString());
            assertEquals("App-specific directory", appSpecific.getText().toString());
        }
    }

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

    private static File seedLegacySave(OnboardingActivity activity) throws IOException {
        File file = new File(activity.getFilesDir(), "saves/LegacyGame/slot.asd");
        Files.createDirectories(file.getParentFile().toPath());
        Files.write(file.toPath(), new byte[] { 1 });
        return file;
    }

    private static void assertStorageDetails(OnboardingActivity activity, boolean usePublic) {
        TextView shared = activity.findViewById(R.id.storage_option_public);
        TextView skip = activity.findViewById(R.id.storage_option_private);
        TextView benefits = activity.findViewById(R.id.storage_public_benefits);
        assertTrue(shared.isShown());
        assertTrue(skip.isShown());
        assertEquals("Use shared storage\nRequires additional storage permission", shared.getText().toString());
        assertTrue(skip.getText().toString().endsWith("\nNo additional storage permission required"));
        assertTrue(benefits.getText().toString().contains("Automatically migrate saves from older versions"));
        assertEquals(usePublic ? View.VISIBLE : View.GONE,
                benefits.getVisibility());
        assertEquals(usePublic ? View.GONE : View.VISIBLE,
                activity.findViewById(R.id.storage_private_drawbacks).getVisibility());
    }

    private static void assertPageIcon(OnboardingActivity activity, int drawable) {
        ViewGroup page = activity.findViewById(R.id.onboarding_page);
        ViewGroup content = (ViewGroup) page.getChildAt(0);
        ImageView icon = (ImageView) content.getChildAt(0);
        assertEquals(drawable, Shadows.shadowOf(icon.getDrawable()).getCreatedFromResId());
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

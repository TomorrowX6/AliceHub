package io.github.kichikuou.xsystem4;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
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

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class MonetThemeAndSettingsTest {

    @Test
    public void monetIsDefaultOnAndroid12PlusAndAllSevenSwatchesRender() {
        try (ActivityController<SettingsActivity> controller =
                     Robolectric.buildActivity(SettingsActivity.class).setup()) {
            SettingsActivity activity = controller.get();
            assertTrue(AppSettings.INSTANCE.isMonetSupported());
            assertEquals(AppSettings.PALETTE_MONET, AppSettings.INSTANCE.getColorPalette(activity));

            // Reorganized 4 section cards exist and clip to rounded card outline
            assertTrue(activity.findViewById(R.id.settings_appearance_card).getClipToOutline());
            assertTrue(activity.findViewById(R.id.settings_storage_card).getClipToOutline());
            assertTrue(activity.findViewById(R.id.settings_controls_card).getClipToOutline());
            assertTrue(activity.findViewById(R.id.settings_about_card).getClipToOutline());

            LinearLayout strip = activity.findViewById(R.id.settings_palette_strip);
            List<String> palettes = AppSettings.INSTANCE.availableColorPalettes();
            assertEquals(7, palettes.size());
            assertEquals(7, strip.getChildCount());
            assertEquals(AppSettings.PALETTE_MONET, strip.getChildAt(0).getTag());
            assertTrue(strip.getChildAt(0).isSelected());

            // Switches are themed with custom tint lists
            Switch onlineSwitch = activity.findViewById(R.id.switch_online_install);
            Switch updatesSwitch = activity.findViewById(R.id.switch_updates);
            assertNotNull(onlineSwitch.getThumbTintList());
            assertNotNull(updatesSwitch.getThumbTintList());

            // Version footer shows AliceHub version
            TextView version = activity.findViewById(R.id.text_settings_version);
            assertTrue(version.getText().toString().startsWith("AliceHub"));
        }
    }

    @Test
    @Config(sdk = 28)
    public void preAndroid12FallsBackToClassicIndigoWithoutMonetOption() {
        try (ActivityController<SettingsActivity> controller =
                     Robolectric.buildActivity(SettingsActivity.class).setup()) {
            SettingsActivity activity = controller.get();
            assertFalse(AppSettings.INSTANCE.isMonetSupported());
            assertEquals(AppSettings.PALETTE_INDIGO, AppSettings.INSTANCE.getColorPalette(activity));

            List<String> palettes = AppSettings.INSTANCE.availableColorPalettes();
            assertEquals(6, palettes.size());
            assertFalse(palettes.contains(AppSettings.PALETTE_MONET));

            LinearLayout strip = activity.findViewById(R.id.settings_palette_strip);
            assertEquals(6, strip.getChildCount());
            assertEquals(AppSettings.PALETTE_INDIGO, strip.getChildAt(0).getTag());
            assertTrue(strip.getChildAt(0).isSelected());
        }
    }

    @Test
    public void tappingPaletteSwatchPersistsAndUpdatesResolvedThemeColors() {
        try (ActivityController<SettingsActivity> controller =
                     Robolectric.buildActivity(SettingsActivity.class).setup()) {
            SettingsActivity activity = controller.get();
            LinearLayout strip = activity.findViewById(R.id.settings_palette_strip);

            View sakuraTile = strip.findViewWithTag(AppSettings.PALETTE_SAKURA);
            assertNotNull(sakuraTile);
            sakuraTile.performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();

            SettingsActivity recreatedSakura = controller.get();
            assertEquals(AppSettings.PALETTE_SAKURA, AppSettings.INSTANCE.getColorPalette(recreatedSakura));
            assertEquals(R.style.Theme_Launcher_Palette_Sakura,
                    AppSettings.INSTANCE.resolveThemeRes(recreatedSakura, AppSettings.PALETTE_SAKURA));
            assertEquals(recreatedSakura.getColor(R.color.sakura_primary), UiKit.INSTANCE.primaryColor(recreatedSakura));
            assertEquals(recreatedSakura.getColor(R.color.sakura_bg), UiKit.INSTANCE.bgColor(recreatedSakura));
            assertNotEquals(recreatedSakura.getColor(R.color.ui_primary), UiKit.INSTANCE.primaryColor(recreatedSakura));

            // Also verify switching via the palette dialog works cleanly and recreates into Forest
            recreatedSakura.findViewById(R.id.row_settings_palette).performClick();
            AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
            assertTrue(dialog.isShowing());
            int forestIdx = AppSettings.INSTANCE.availableColorPalettes().indexOf(AppSettings.PALETTE_FOREST);
            dialog.getListView().performItemClick(
                    dialog.getListView().getChildAt(forestIdx), forestIdx, forestIdx);
            Shadows.shadowOf(Looper.getMainLooper()).idle();

            SettingsActivity recreatedForest = controller.get();
            assertEquals(AppSettings.PALETTE_FOREST, AppSettings.INSTANCE.getColorPalette(recreatedForest));
            assertEquals(recreatedForest.getColor(R.color.forest_primary), UiKit.INSTANCE.primaryColor(recreatedForest));
            assertEquals(recreatedForest.getColor(R.color.forest_bg), UiKit.INSTANCE.bgColor(recreatedForest));
        }
    }

    @Test
    @Config(qualifiers = "night")
    public void nightModePalettesResolveDarkThemeAttributesCleanly() {
        try (ActivityController<SettingsActivity> controller =
                     Robolectric.buildActivity(SettingsActivity.class).setup()) {
            SettingsActivity activity = controller.get();
            AppSettings.INSTANCE.setColorPalette(activity, AppSettings.PALETTE_OCEAN);
        }
        try (ActivityController<SettingsActivity> controller =
                     Robolectric.buildActivity(SettingsActivity.class).setup()) {
            SettingsActivity activity = controller.get();
            assertEquals(activity.getColor(R.color.ocean_primary), UiKit.INSTANCE.primaryColor(activity));
            assertEquals(activity.getColor(R.color.ocean_bg), UiKit.INSTANCE.bgColor(activity));
            assertEquals(activity.getColor(R.color.ocean_surface), UiKit.INSTANCE.surfaceColor(activity));

            // FAB icon tint and onboarding step label resolve palette theme attributes cleanly in dark mode
            View launcherView = LayoutInflater.from(activity).inflate(R.layout.launcher, null);
            ImageView fab = launcherView.findViewById(R.id.fab_import_game);
            assertNotNull(fab.getImageTintList());
            assertEquals(activity.getColor(R.color.ocean_on_primary), fab.getImageTintList().getDefaultColor());

            View onboardingView = LayoutInflater.from(activity).inflate(R.layout.activity_onboarding, null);
            TextView step = onboardingView.findViewById(R.id.onboarding_step);
            assertEquals(activity.getColor(R.color.ocean_primary), step.getCurrentTextColor());
        }
    }

    @Test
    public void gameModifierDialogsFollowPerGameAccentColor() {
        Intent intent = new Intent();
        intent.putExtra(GameModifierActivity.EXTRA_GAME_ID, GameCatalog.ID_RANCE02);
        try (ActivityController<GameModifierActivity> controller =
                     Robolectric.buildActivity(GameModifierActivity.class, intent).setup()) {
            GameModifierActivity activity = controller.get();
            SaveScreenLoadingTest.await(() -> activity.findViewById(R.id.btn_mod_font_size).isEnabled());
            int gameAccent = GameCatalog.INSTANCE.descriptorFor(activity, GameCatalog.ID_RANCE02, null).getAccentColor();
            int expectedAccent = UiKit.INSTANCE.accentOnSurface(activity, gameAccent);

            // Header action buttons follow Rance02 accent
            assertEquals(expectedAccent, ((Button) activity.findViewById(R.id.btn_mod_floating_cfg)).getCurrentTextColor());
            assertEquals(expectedAccent, ((Button) activity.findViewById(R.id.btn_mod_switch_dir)).getCurrentTextColor());

            // 1. Font size dialog: SeekBar, preset/step buttons, and dialog action buttons follow Rance02 accent
            activity.findViewById(R.id.btn_mod_font_size).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            AlertDialog fontDialog = ShadowAlertDialog.getLatestAlertDialog();
            assertTrue(fontDialog.isShowing());
            assertEquals(expectedAccent, fontDialog.getButton(AlertDialog.BUTTON_POSITIVE).getCurrentTextColor());
            assertEquals(expectedAccent, fontDialog.getButton(AlertDialog.BUTTON_NEGATIVE).getCurrentTextColor());
            assertEquals(expectedAccent, fontDialog.getButton(AlertDialog.BUTTON_NEUTRAL).getCurrentTextColor());
            SeekBar fontSeek = findFirstView(fontDialog.getWindow().getDecorView(), SeekBar.class);
            assertNotNull(fontSeek);
            assertEquals(expectedAccent, fontSeek.getThumbTintList().getDefaultColor());
            Button stepMinus = findButtonWithText(fontDialog.getWindow().getDecorView(), "-5%");
            assertNotNull(stepMinus);
            assertEquals(expectedAccent, stepMinus.getCurrentTextColor());
            Button preset100 = findButtonWithText(fontDialog.getWindow().getDecorView(), "100%");
            assertNotNull(preset100);
            assertEquals(expectedAccent, preset100.getCurrentTextColor());
            fontDialog.dismiss();

            // 2. Scrollbar dialog follows Rance02 accent
            activity.findViewById(R.id.btn_mod_scrollbar).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            AlertDialog sbDialog = ShadowAlertDialog.getLatestAlertDialog();
            assertTrue(sbDialog.isShowing());
            assertEquals(expectedAccent, sbDialog.getButton(AlertDialog.BUTTON_POSITIVE).getCurrentTextColor());
            SeekBar sbSeek = findFirstView(sbDialog.getWindow().getDecorView(), SeekBar.class);
            assertNotNull(sbSeek);
            assertEquals(expectedAccent, sbSeek.getProgressTintList().getDefaultColor());
            Button preset200 = findButtonWithText(sbDialog.getWindow().getDecorView(), "200%");
            assertNotNull(preset200);
            assertEquals(expectedAccent, preset200.getCurrentTextColor());
            sbDialog.dismiss();

            // 3. Live locks dialog: action buttons and multi-choice checkmarks follow Rance02 accent
            activity.findViewById(R.id.btn_mod_floating_cfg).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            AlertDialog locksDialog = ShadowAlertDialog.getLatestAlertDialog();
            assertTrue(locksDialog.isShowing());
            assertEquals(expectedAccent, locksDialog.getButton(AlertDialog.BUTTON_POSITIVE).getCurrentTextColor());
            assertEquals(expectedAccent, locksDialog.getButton(AlertDialog.BUTTON_NEGATIVE).getCurrentTextColor());
            assertEquals(expectedAccent, locksDialog.getButton(AlertDialog.BUTTON_NEUTRAL).getCurrentTextColor());
            ListView lv = locksDialog.getListView();
            assertNotNull(lv);
            lv.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.AT_MOST));
            lv.layout(0, 0, 800, 800);
            CheckedTextView firstRow = findFirstView(lv, CheckedTextView.class);
            assertNotNull(firstRow);
            assertNotNull(firstRow.getCompoundDrawableTintList());
            assertEquals(expectedAccent,
                    firstRow.getCompoundDrawableTintList().getColorForState(
                            new int[]{android.R.attr.state_checked}, 0));
            locksDialog.dismiss();

            // 4. Switch directory & create directory dialogs follow Rance02 accent
            activity.findViewById(R.id.btn_mod_switch_dir).performClick();
            SaveScreenLoadingTest.await(() -> {
                AlertDialog latest = ShadowAlertDialog.getLatestAlertDialog();
                return latest != null && latest != locksDialog && latest.isShowing();
            });
            AlertDialog switchDialog = ShadowAlertDialog.getLatestAlertDialog();
            assertTrue(switchDialog.isShowing());
            assertEquals(expectedAccent, switchDialog.getButton(AlertDialog.BUTTON_NEGATIVE).getCurrentTextColor());
            ListView switchList = switchDialog.getListView();
            assertNotNull(switchList);
            int createIdx = switchList.getAdapter().getCount() - 1;
            switchList.performItemClick(switchList.getChildAt(createIdx), createIdx, createIdx);
            Shadows.shadowOf(Looper.getMainLooper()).idle();

            AlertDialog createDialog = ShadowAlertDialog.getLatestAlertDialog();
            assertTrue(createDialog.isShowing());
            assertNotEquals(switchDialog, createDialog);
            assertEquals(expectedAccent, createDialog.getButton(AlertDialog.BUTTON_POSITIVE).getCurrentTextColor());
            assertEquals(expectedAccent, createDialog.getButton(AlertDialog.BUTTON_NEGATIVE).getCurrentTextColor());
            EditText dirInput = findFirstView(createDialog.getWindow().getDecorView(), EditText.class);
            assertNotNull(dirInput);
            assertNotNull(dirInput.getBackgroundTintList());
            assertEquals(expectedAccent,
                    dirInput.getBackgroundTintList().getColorForState(
                            new int[]{android.R.attr.state_focused}, 0));
            createDialog.dismiss();
        }
    }

    private static <T extends View> T findFirstView(View root, Class<T> clazz) {
        if (clazz.isInstance(root)) return clazz.cast(root);
        if (root instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) root;
            for (int i = 0; i < vg.getChildCount(); i++) {
                T found = findFirstView(vg.getChildAt(i), clazz);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static Button findButtonWithText(View root, String text) {
        if (root instanceof Button && text.equals(((Button) root).getText().toString())) {
            return (Button) root;
        }
        if (root instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) root;
            for (int i = 0; i < vg.getChildCount(); i++) {
                Button found = findButtonWithText(vg.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }
}

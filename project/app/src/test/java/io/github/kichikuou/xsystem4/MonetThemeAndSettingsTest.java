package io.github.kichikuou.xsystem4;

import android.app.AlertDialog;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
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
        }
    }
}

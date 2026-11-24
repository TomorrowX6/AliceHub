package io.github.kichikuou.xsystem4;

import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.ArrayList;
import java.util.List;

import kotlin.Unit;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class GamepadSensitivityTest {

    @Test
    public void physicalPadConfigRoundTripsAndClampsSensitivityAndDeadzone() throws Exception {
        PhysicalPadConfig low = PhysicalPadConfig.Companion.fromJson(
                new JSONObject().put("cursorSpeedPct", 5).put("deadzonePct", 1));
        assertEquals(GamepadBridge.CURSOR_SPEED_PCT_MIN, low.getCursorSpeedPct());
        assertEquals(GamepadBridge.DEADZONE_PCT_MIN, low.getDeadzonePct());

        PhysicalPadConfig high = PhysicalPadConfig.Companion.fromJson(
                new JSONObject().put("cursorSpeedPct", 999).put("deadzonePct", 90));
        assertEquals(GamepadBridge.CURSOR_SPEED_PCT_MAX, high.getCursorSpeedPct());
        assertEquals(GamepadBridge.DEADZONE_PCT_MAX, high.getDeadzonePct());

        PhysicalPadConfig mid = new PhysicalPadConfig(
                false, false,
                GamepadBridge.STICK_MOUSE, GamepadBridge.STICK_WHEEL, GamepadBridge.STICK_ARROWS,
                24, 280, false, GamepadBridge.INSTANCE.defaultPhysicalBindings());
        PhysicalPadConfig parsed = PhysicalPadConfig.Companion.fromJson(mid.toJson());
        assertEquals(280, parsed.getCursorSpeedPct());
        assertEquals(24, parsed.getDeadzonePct());

        // Legacy profiles containing the removed autoHideVirtualPad flag must still deserialize
        // cleanly and omit the removed field when re-serialized.
        PhysicalPadConfig legacy = PhysicalPadConfig.Companion.fromJson(
                new JSONObject()
                        .put("enabled", true)
                        .put("autoHideVirtualPad", true)
                        .put("swapAbXy", true)
                        .put("cursorSpeedPct", 160));
        assertTrue(legacy.getEnabled());
        assertTrue(legacy.getSwapAbXy());
        assertEquals(160, legacy.getCursorSpeedPct());
        assertFalse(legacy.toJson().has("autoHideVirtualPad"));
    }

    @Test
    public void settingsOverviewExposesSensitivityAndDeadzoneSlidersAndPersists() {
        GamepadSettingsActivity host = Robolectric.buildActivity(GamepadSettingsActivity.class).setup().get();
        try {
            AppSettings.INSTANCE.setLanguageMode(host, "zh");
            GamepadConfigController controller = new GamepadConfigController(
                    host, "sens-test", null, "SensitivityTest", null, true,
                    null, dirty -> Unit.INSTANCE, saved -> Unit.INSTANCE);
            View root = controller.buildRootView();

            assertNull("Removed auto-hide switch must not appear in overview",
                    findText(root, "接入隐藏虚拟键", "接続時仮想非表示", "Auto-hide Virtual"));

            SeekBar sensSeek = findSliderByLabel(root, "手柄灵敏度");
            SeekBar dzSeek = findSliderByLabel(root, "摇杆死区");
            assertNotNull("GamepadSettings must expose 手柄灵敏度 slider", sensSeek);
            assertNotNull("GamepadSettings must expose 摇杆死区 slider", dzSeek);
            assertEquals(GamepadBridge.CURSOR_SPEED_PCT_MAX - GamepadBridge.CURSOR_SPEED_PCT_MIN, sensSeek.getMax());
            assertEquals(GamepadBridge.DEADZONE_PCT_MAX - GamepadBridge.DEADZONE_PCT_MIN, dzSeek.getMax());

            // Verify balanced row heights between switch rows above and slider rows below.
            int dp44 = UiKit.INSTANCE.dpInt(controller.getUiCtx(), 44);
            int dp48 = UiKit.INSTANCE.dpInt(controller.getUiCtx(), 48);
            View sensRow = (View) sensSeek.getParent();
            View dzRow = (View) dzSeek.getParent();
            assertTrue("Slider row must have comfortable minimum height", sensRow.getMinimumHeight() >= dp44);
            assertTrue("Slider row must have comfortable minimum height", dzRow.getMinimumHeight() >= dp44);
            TextView hapticLabel = findText(root, "按键触感");
            assertNotNull(hapticLabel);
            View hapticRow = (View) hapticLabel.getParent().getParent();
            assertEquals("Switch row must stay compact at 48dp minHeight", dp48, hapticRow.getMinimumHeight());

            // Simulate user dragging sensitivity to 260% and deadzone to 30%.
            setSeekFromUser(sensSeek, 260 - GamepadBridge.CURSOR_SPEED_PCT_MIN);
            setSeekFromUser(dzSeek, 30 - GamepadBridge.DEADZONE_PCT_MIN);
            assertTrue(controller.isDirty());

            GamepadProfile saved = controller.saveAndApply();
            assertEquals(260, saved.getPhysical().getCursorSpeedPct());
            assertEquals(30, saved.getPhysical().getDeadzonePct());

            GamepadProfile reloaded = GamepadBridge.INSTANCE.loadProfile(host, "sens-test");
            assertEquals(260, reloaded.getPhysical().getCursorSpeedPct());
            assertEquals(30, reloaded.getPhysical().getDeadzonePct());

            // Reset restores defaults (100% sensitivity, 18% deadzone).
            controller.resetToDefaults();
            GamepadProfile resetSaved = controller.saveAndApply();
            assertEquals(100, resetSaved.getPhysical().getCursorSpeedPct());
            assertEquals(18, resetSaved.getPhysical().getDeadzonePct());
        } finally {
            GamepadBridge.INSTANCE.clearPerGameOverride(RuntimeEnvironment.getApplication(), "sens-test");
            host.finish();
        }
    }

    @Test
    public void editorExposesSensitivityInBothVirtualAndPhysicalTabs() {
        GamepadSettingsActivity host = Robolectric.buildActivity(GamepadSettingsActivity.class).setup().get();
        Intent intent = GamepadEditorActivity.Companion.createIntent(
                host, "editor-sens", "EditorSens", GamepadEditorActivity.TAB_VIRTUAL, null);
        GamepadEditorActivity editor = Robolectric.buildActivity(GamepadEditorActivity.class, intent).setup().get();
        try {
            View decor = editor.getWindow().getDecorView();
            assertNull("Removed auto-hide switch must not appear in editor",
                    findText(decor, "接入隐藏虚拟键", "接続時仮想非表示", "Auto-hide Virtual"));
            // Physical tab slider has widened 20..300 range.
            SeekBar physSens = findSliderByLabel(decor, "灵敏度", "感度", "Sensitivity");
            assertNotNull("Physical tab must expose Sensitivity slider", physSens);
            assertEquals(GamepadBridge.CURSOR_SPEED_PCT_MAX - GamepadBridge.CURSOR_SPEED_PCT_MIN, physSens.getMax());

            // Open Virtual tab Options popover -> must also expose Sensitivity & Deadzone sliders.
            TextView optionsBtn = findText(decor, "设置", "全体設定", "Options");
            assertNotNull(optionsBtn);
            optionsBtn.performClick();
            SeekBar virtSens = findSliderByLabel(decor, "手柄灵敏度", "感度", "Sensitivity");
            SeekBar virtDz = findSliderByLabel(decor, "摇杆死区", "デッドゾーン", "Deadzone");
            assertNotNull("Virtual popover must expose Sensitivity slider", virtSens);
            assertNotNull("Virtual popover must expose Deadzone slider", virtDz);
            assertEquals(GamepadBridge.CURSOR_SPEED_PCT_MAX - GamepadBridge.CURSOR_SPEED_PCT_MIN, virtSens.getMax());
            assertEquals(GamepadBridge.DEADZONE_PCT_MAX - GamepadBridge.DEADZONE_PCT_MIN, virtDz.getMax());
        } finally {
            editor.finish();
            host.finish();
        }
    }

    @Test
    public void clearingLocalizedOverrideDoesNotReloadLegacyProfileAndPreservesOtherCopies() throws Exception {
        android.content.Context ctx = RuntimeEnvironment.getApplication();
        java.io.File profileDir = new java.io.File(ctx.getFilesDir(), "gamepad_profiles");
        profileDir.mkdirs();
        // Seed a legacy localized override file ("Rance10-__") with custom cursorSpeedPct = 240.
        org.json.JSONObject legacyJson = new GamepadProfile().toJson();
        legacyJson.put("perGameOverride", true);
        legacyJson.getJSONObject("physical").put("cursorSpeedPct", 240);
        java.io.File legacyFile = new java.io.File(profileDir, "game_Rance10-__.json");
        java.nio.file.Files.write(legacyFile.toPath(),
                legacyJson.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        try {
            // Before clearing, both localized keys fall back to the legacy override.
            assertTrue(GamepadBridge.INSTANCE.hasPerGameOverride(ctx, "Rance10-原版"));
            assertEquals(240, GamepadBridge.INSTANCE.loadProfile(ctx, "Rance10-原版").getPhysical().getCursorSpeedPct());
            assertEquals(240, GamepadBridge.INSTANCE.loadProfile(ctx, "Rance10-日版").getPhysical().getCursorSpeedPct());

            // Clear override for Rance10-原版 -> must switch to global without deleting legacy file for Rance10-日版.
            GamepadBridge.INSTANCE.clearPerGameOverride(ctx, "Rance10-原版");
            assertFalse(GamepadBridge.INSTANCE.hasPerGameOverride(ctx, "Rance10-原版"));
            assertEquals(100, GamepadBridge.INSTANCE.loadProfile(ctx, "Rance10-原版").getPhysical().getCursorSpeedPct());
            assertTrue("Legacy file must stay intact for other copies", legacyFile.isFile());
            assertTrue(GamepadBridge.INSTANCE.hasPerGameOverride(ctx, "Rance10-日版"));
            assertEquals(240, GamepadBridge.INSTANCE.loadProfile(ctx, "Rance10-日版").getPhysical().getCursorSpeedPct());

            // Save global profile (saveAsPerGame=false) from Rance10-日版 -> must also disable its legacy override.
            GamepadProfile global = GamepadBridge.INSTANCE.loadGlobalProfile(ctx);
            GamepadBridge.INSTANCE.saveProfile(ctx, global, "Rance10-日版", false);
            assertFalse(GamepadBridge.INSTANCE.hasPerGameOverride(ctx, "Rance10-日版"));
            assertEquals(100, GamepadBridge.INSTANCE.loadProfile(ctx, "Rance10-日版").getPhysical().getCursorSpeedPct());
        } finally {
            legacyFile.delete();
            String k1 = GamepadBridge.INSTANCE.normalizeGameProfileKey("Rance10-原版");
            String k2 = GamepadBridge.INSTANCE.normalizeGameProfileKey("Rance10-日版");
            if (k1 != null) new java.io.File(profileDir, "game_" + k1 + ".json").delete();
            if (k2 != null) new java.io.File(profileDir, "game_" + k2 + ".json").delete();
        }
    }

    private static void setSeekFromUser(SeekBar seek, int progress) {
        try {
            java.lang.reflect.Method m = android.widget.AbsSeekBar.class.getSuperclass()
                    .getDeclaredMethod("setProgressInternal", int.class, boolean.class, boolean.class);
            m.setAccessible(true);
            m.invoke(seek, progress, true, false);
        } catch (Exception e) {
            throw new AssertionError("Failed to dispatch user SeekBar change", e);
        }
    }

    private static SeekBar findSliderByLabel(View root, String... labels) {
        List<ViewGroup> groups = new ArrayList<>();
        collectGroups(root, groups);
        for (ViewGroup g : groups) {
            boolean labelFound = false;
            SeekBar seek = null;
            for (int i = 0; i < g.getChildCount(); i++) {
                View child = g.getChildAt(i);
                if (child instanceof TextView) {
                    CharSequence t = ((TextView) child).getText();
                    if (t != null) {
                        String s = t.toString();
                        for (String l : labels) {
                            if (l.equals(s)) {
                                labelFound = true;
                                break;
                            }
                        }
                    }
                } else if (child instanceof SeekBar) {
                    seek = (SeekBar) child;
                }
            }
            if (labelFound && seek != null) {
                return seek;
            }
        }
        return null;
    }

    private static void collectGroups(View view, List<ViewGroup> out) {
        if (view instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) view;
            out.add(g);
            for (int i = 0; i < g.getChildCount(); i++) {
                collectGroups(g.getChildAt(i), out);
            }
        }
    }

    private static TextView findText(View root, String... labels) {
        if (root instanceof TextView) {
            CharSequence t = ((TextView) root).getText();
            if (t != null) {
                String s = t.toString();
                for (String l : labels) {
                    if (l.equals(s)) return (TextView) root;
                }
            }
        }
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) {
                TextView found = findText(g.getChildAt(i), labels);
                if (found != null) return found;
            }
        }
        return null;
    }
}

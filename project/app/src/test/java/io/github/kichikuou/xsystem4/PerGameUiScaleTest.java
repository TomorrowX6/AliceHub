package io.github.kichikuou.xsystem4;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class PerGameUiScaleTest {
    private Context context;
    private File configFile;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        configFile = new File(context.getFilesDir(), "modifier_config.json");
        if (configFile.exists()) {
            assertTrue(configFile.delete());
        }
        context.getSharedPreferences("game_modifier_prefs", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
        GameModifierBridge.INSTANCE.clearActiveUiScaleGameKey();
    }

    @After
    public void tearDown() {
        GameModifierBridge.INSTANCE.clearActiveUiScaleGameKey();
        if (configFile != null && configFile.exists()) {
            configFile.delete();
        }
    }

    @Test
    public void normalizeUiScaleGameKeyExtractsBasenameAndPreservesUnicode() {
        GameModifierBridge bridge = GameModifierBridge.INSTANCE;
        assertEquals("Rance10", bridge.normalizeUiScaleGameKey("Rance10"));
        assertEquals("Rance10", bridge.normalizeUiScaleGameKey("/storage/emulated/0/Games/Rance10/"));
        assertEquals("DohnaDohna", bridge.normalizeUiScaleGameKey("C:\\Games\\DohnaDohna\\"));
        assertEquals("兰斯10", bridge.normalizeUiScaleGameKey("/data/user/0/app/files/兰斯10"));
        assertEquals("ドーナドーナ", bridge.normalizeUiScaleGameKey("ドーナドーナ"));
        assertNull(bridge.normalizeUiScaleGameKey(null));
        assertNull(bridge.normalizeUiScaleGameKey("   "));
        assertNull(bridge.normalizeUiScaleGameKey("/"));
        assertNull(bridge.normalizeUiScaleGameKey("."));
        assertNull(bridge.normalizeUiScaleGameKey(".."));
    }

    @Test
    public void fontAndScrollbarScalesAreIsolatedPerGameIncludingUnicodeNames() throws Exception {
        GameModifierBridge bridge = GameModifierBridge.INSTANCE;

        assertEquals(100, bridge.getFontScalePercent(context, "Rance10"));
        assertEquals(100, bridge.getScrollbarScalePercent(context, "Rance10"));
        assertEquals(100, bridge.getFontScalePercent(context, "DohnaDohna"));
        assertEquals(100, bridge.getScrollbarScalePercent(context, "DohnaDohna"));

        bridge.setFontScalePercent(context, "Rance10", 135);
        bridge.setScrollbarScalePercent(context, "Rance10", 220);

        // Unconfigured game stays at default 100%.
        assertEquals(135, bridge.getFontScalePercent(context, "Rance10"));
        assertEquals(220, bridge.getScrollbarScalePercent(context, "Rance10"));
        assertEquals(100, bridge.getFontScalePercent(context, "DohnaDohna"));
        assertEquals(100, bridge.getScrollbarScalePercent(context, "DohnaDohna"));

        // Configure a second game and two CJK-named games of equal character length.
        bridge.setFontScalePercent(context, "/data/games/DohnaDohna/", 85);
        bridge.setScrollbarScalePercent(context, "DohnaDohna", 150);
        bridge.setFontScalePercent(context, "ドーナドーナ", 120);
        bridge.setFontScalePercent(context, "イブニクル1", 90);
        bridge.setScrollbarScalePercent(context, "兰斯10", 180);
        bridge.setScrollbarScalePercent(context, "兰斯01", 260);

        assertEquals(135, bridge.getFontScalePercent(context, "Rance10"));
        assertEquals(220, bridge.getScrollbarScalePercent(context, "Rance10"));
        assertEquals(85, bridge.getFontScalePercent(context, "DohnaDohna"));
        assertEquals(150, bridge.getScrollbarScalePercent(context, "DohnaDohna"));
        assertEquals(120, bridge.getFontScalePercent(context, "ドーナドーナ"));
        assertEquals(90, bridge.getFontScalePercent(context, "イブニクル1"));
        assertEquals(180, bridge.getScrollbarScalePercent(context, "兰斯10"));
        assertEquals(260, bridge.getScrollbarScalePercent(context, "兰斯01"));

        // Resetting one game back to 100% removes only its entry and preserves others.
        int seqBeforeReset = bridge.getConfigSeq(context);
        bridge.setFontScalePercent(context, "Rance10", 100);
        assertTrue(bridge.getConfigSeq(context) > seqBeforeReset);
        assertEquals(100, bridge.getFontScalePercent(context, "Rance10"));
        assertEquals(220, bridge.getScrollbarScalePercent(context, "Rance10"));
        assertEquals(85, bridge.getFontScalePercent(context, "DohnaDohna"));

        // Verify JSON structure on disk has per-game maps and no legacy global keys.
        JSONObject diskJson = new JSONObject(new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8));
        assertFalse(diskJson.has("font_scale_pct"));
        assertFalse(diskJson.has("scrollbar_scale_pct"));
        JSONObject fontMap = diskJson.getJSONObject("game_font_scale_pct");
        JSONObject sbMap = diskJson.getJSONObject("game_scrollbar_scale_pct");
        assertFalse(fontMap.has("Rance10"));
        assertEquals(85, fontMap.getInt("DohnaDohna"));
        assertEquals(220, sbMap.getInt("Rance10"));
        assertEquals(150, sbMap.getInt("DohnaDohna"));

        // Deleting modifier_config.json restores per-game maps from SharedPreferences.
        assertTrue(configFile.delete());
        assertEquals(100, bridge.getFontScalePercent(context, "Rance10"));
        assertEquals(220, bridge.getScrollbarScalePercent(context, "Rance10"));
        assertEquals(85, bridge.getFontScalePercent(context, "DohnaDohna"));
        assertEquals(150, bridge.getScrollbarScalePercent(context, "DohnaDohna"));
        assertEquals(120, bridge.getFontScalePercent(context, "ドーナドーナ"));
        assertEquals(90, bridge.getFontScalePercent(context, "イブニクル1"));
    }

    @Test
    public void legacyGlobalUiScaleKeysDoNotLeakAcrossGames() throws Exception {
        JSONObject legacy = new JSONObject();
        legacy.put("floating_enabled", true);
        legacy.put("floating_default_on_v1", true);
        legacy.put("ctrl_overlay_enabled", false);
        legacy.put("ctrl_default_on_v1", true);
        legacy.put("ctrl_default_off_v2", true);
        legacy.put("font_scale_pct", 145);
        legacy.put("scrollbar_scale_pct", 240);
        Files.write(configFile.toPath(), legacy.toString().getBytes(StandardCharsets.UTF_8));

        GameModifierBridge bridge = GameModifierBridge.INSTANCE;
        assertEquals(100, bridge.getFontScalePercent(context, "Rance10"));
        assertEquals(100, bridge.getScrollbarScalePercent(context, "Rance10"));
        assertEquals(100, bridge.getFontScalePercent(context, "Evenicle"));
        assertEquals(100, bridge.getScrollbarScalePercent(context, "Evenicle"));

        JSONObject migrated = new JSONObject(new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8));
        assertFalse(migrated.has("font_scale_pct"));
        assertFalse(migrated.has("scrollbar_scale_pct"));

        // Also verify legacy SharedPreferences global keys do not leak.
        assertTrue(configFile.delete());
        SharedPreferences prefs = context.getSharedPreferences("game_modifier_prefs", Context.MODE_PRIVATE);
        prefs.edit()
                .clear()
                .putBoolean("floating_default_on_v1", true)
                .putBoolean("ctrl_default_off_v2", true)
                .putInt("font_scale_pct", 150)
                .putInt("scrollbar_scale_pct", 280)
                .commit();
        assertEquals(100, bridge.getFontScalePercent(context, "Rance10"));
        assertEquals(100, bridge.getScrollbarScalePercent(context, "Rance10"));
        assertFalse(prefs.contains("font_scale_pct"));
        assertFalse(prefs.contains("scrollbar_scale_pct"));
    }

    @Test
    public void nullOrInvalidGameKeyDefaultsTo100AndDoesNotPersist() {
        GameModifierBridge bridge = GameModifierBridge.INSTANCE;
        bridge.setFontScalePercent(context, null, 150);
        bridge.setScrollbarScalePercent(context, "   ", 250);
        assertEquals(100, bridge.getFontScalePercent(context, null));
        assertEquals(100, bridge.getScrollbarScalePercent(context, ""));
        assertEquals(100, bridge.getFontScalePercent(context, "Rance10"));
        assertEquals(100, bridge.getScrollbarScalePercent(context, "Rance10"));
    }

    @Test
    public void gameModifierActivityDisplaysPerGameScaleButtons() {
        GameModifierBridge bridge = GameModifierBridge.INSTANCE;
        String dohnaDefaultDir = GameCatalog.INSTANCE
                .descriptorFor(context, GameCatalog.ID_DOHNA_DOHNA, null).getDefaultDirName();
        bridge.setFontScalePercent(context, "Rance10", 125);
        bridge.setScrollbarScalePercent(context, "Rance10", 180);
        bridge.setFontScalePercent(context, dohnaDefaultDir, 90);
        bridge.setScrollbarScalePercent(context, dohnaDefaultDir, 140);

        Intent r10Intent = new Intent(context, GameModifierActivity.class)
                .putExtra(GameModifierActivity.EXTRA_DIR_NAME, "Rance10")
                .putExtra(GameModifierActivity.EXTRA_GAME_ID, GameCatalog.ID_RANCE10);
        GameModifierActivity r10Act = Robolectric.buildActivity(GameModifierActivity.class, r10Intent)
                .create().get();
        SaveScreenLoadingTest.await(() -> r10Act.findViewById(R.id.btn_mod_font_size).isEnabled());
        Button r10FontBtn = r10Act.findViewById(R.id.btn_mod_font_size);
        Button r10SbBtn = r10Act.findViewById(R.id.btn_mod_scrollbar);
        assertTrue("Expected 125% in " + r10FontBtn.getText(), r10FontBtn.getText().toString().contains("125%"));
        assertTrue("Expected 180% in " + r10SbBtn.getText(), r10SbBtn.getText().toString().contains("180%"));

        Intent ddIntent = new Intent(context, GameModifierActivity.class)
                .putExtra(GameModifierActivity.EXTRA_DIR_NAME, dohnaDefaultDir)
                .putExtra(GameModifierActivity.EXTRA_GAME_ID, GameCatalog.ID_DOHNA_DOHNA);
        GameModifierActivity ddAct = Robolectric.buildActivity(GameModifierActivity.class, ddIntent)
                .create().get();
        SaveScreenLoadingTest.await(() -> ddAct.findViewById(R.id.btn_mod_font_size).isEnabled());
        Button ddFontBtn = ddAct.findViewById(R.id.btn_mod_font_size);
        Button ddSbBtn = ddAct.findViewById(R.id.btn_mod_scrollbar);
        assertTrue("Expected 90% in " + ddFontBtn.getText(), ddFontBtn.getText().toString().contains("90%"));
        assertTrue("Expected 140% in " + ddSbBtn.getText(), ddSbBtn.getText().toString().contains("140%"));

        Intent evIntent = new Intent(context, GameModifierActivity.class)
                .putExtra(GameModifierActivity.EXTRA_DIR_NAME, "Evenicle")
                .putExtra(GameModifierActivity.EXTRA_GAME_ID, GameCatalog.ID_EVENICLE);
        GameModifierActivity evAct = Robolectric.buildActivity(GameModifierActivity.class, evIntent)
                .create().get();
        SaveScreenLoadingTest.await(() -> evAct.findViewById(R.id.btn_mod_font_size).isEnabled());
        Button evFontBtn = evAct.findViewById(R.id.btn_mod_font_size);
        Button evSbBtn = evAct.findViewById(R.id.btn_mod_scrollbar);
        assertTrue("Expected 100% in " + evFontBtn.getText(), evFontBtn.getText().toString().contains("100%"));
        assertTrue("Expected 100% in " + evSbBtn.getText(), evSbBtn.getText().toString().contains("100%"));
    }

    @Test
    public void gameSpeedIsIsolatedPerGameClampedFormattedAndPersisted() throws Exception {
        GameModifierBridge bridge = GameModifierBridge.INSTANCE;
        assertEquals(100, bridge.getGameSpeedPercent(context, "Rance10"));
        assertEquals("1.0x", bridge.formatGameSpeed(100));
        assertEquals("1x", bridge.formatGameSpeed(100, true));
        assertEquals("1.5x", bridge.formatGameSpeed(150));
        assertEquals("1.25x", bridge.formatGameSpeed(125));
        assertEquals("2.0x", bridge.formatGameSpeed(200));
        assertEquals("2x", bridge.formatGameSpeed(200, true));
        assertEquals("0.5x", bridge.formatGameSpeed(10));
        assertEquals("5.0x", bridge.formatGameSpeed(900));

        bridge.setGameSpeedPercent(context, "Rance10", 250);
        bridge.setGameSpeedPercent(context, "/games/DohnaDohna/", 999);
        bridge.setGameSpeedPercent(context, "Evenicle", 20);

        assertEquals(250, bridge.getGameSpeedPercent(context, "Rance10"));
        assertEquals(500, bridge.getGameSpeedPercent(context, "DohnaDohna"));
        assertEquals(50, bridge.getGameSpeedPercent(context, "Evenicle"));
        assertEquals(100, bridge.getGameSpeedPercent(context, "Rance02"));

        // Resetting back to 100% removes entry from persisted map
        bridge.setGameSpeedPercent(context, "Evenicle", 100);
        JSONObject diskJson = new JSONObject(new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8));
        JSONObject speedMap = diskJson.getJSONObject("game_speed_pct");
        assertEquals(250, speedMap.getInt("Rance10"));
        assertEquals(500, speedMap.getInt("DohnaDohna"));
        assertFalse(speedMap.has("Evenicle"));

        // Deleting modifier_config.json restores from SharedPreferences fallback
        assertTrue(configFile.delete());
        assertEquals(250, bridge.getGameSpeedPercent(context, "Rance10"));
        assertEquals(500, bridge.getGameSpeedPercent(context, "DohnaDohna"));
        assertEquals(100, bridge.getGameSpeedPercent(context, "Evenicle"));
    }

    @Test
    public void floatingOverlaySpeedMenuBadgePreviewAndRevertWork() {
        try (ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class).setup()) {
            Activity host = controller.get();
            FloatingModifierOverlay overlay = new FloatingModifierOverlay(host);
            overlay.attach();

            TextView badge = overlay.getSpeedBadgeForTest();
            assertNotNull(badge);
            assertEquals(View.GONE, badge.getVisibility());

            // 1. Floating main menu lists Game Speed as the first entry
            overlay.openMainMenu();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            AlertDialog menuDialog = ShadowAlertDialog.getLatestAlertDialog();
            assertTrue(menuDialog.isShowing());
            ListView menuList = menuDialog.getListView();
            assertNotNull(menuList);
            String firstItem = menuList.getAdapter().getItem(0).toString();
            assertTrue("Expected speed item first, got: " + firstItem,
                    firstItem.contains("1.0x"));

            // Click first menu entry -> opens Game Speed dialog
            menuList.performItemClick(menuList.getChildAt(0), 0, 0);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            AlertDialog speedDialog = ShadowAlertDialog.getLatestAlertDialog();
            assertTrue(speedDialog.isShowing());
            assertNotNull(findFirstView(speedDialog.getWindow().getDecorView(), SeekBar.class));
            assertNotNull(findButtonWithText(speedDialog.getWindow().getDecorView(), "-0.25x"));
            assertNotNull(findButtonWithText(speedDialog.getWindow().getDecorView(), "+0.25x"));
            Button btn2x = findButtonWithText(speedDialog.getWindow().getDecorView(), "2.0x");
            assertNotNull(btn2x);
            assertNotNull(findButtonWithText(speedDialog.getWindow().getDecorView(), "5.0x"));

            // Tapping 2.0x previews badge immediately; tapping Cancel reverts badge back to GONE
            btn2x.performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals(View.VISIBLE, badge.getVisibility());
            assertEquals("2x", badge.getText().toString());
            speedDialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals(View.GONE, badge.getVisibility());

            // Opening again, picking 3.0x and pressing OK keeps badge visible at 3x
            overlay.showGameSpeedDialog();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            AlertDialog speedDialog2 = ShadowAlertDialog.getLatestAlertDialog();
            Button btn3x = findButtonWithText(speedDialog2.getWindow().getDecorView(), "3.0x");
            assertNotNull(btn3x);
            btn3x.performClick();
            speedDialog2.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals(View.VISIBLE, badge.getVisibility());
            assertEquals("3x", badge.getText().toString());

            // Neutral Reset button restores 1.0x and hides badge
            overlay.showGameSpeedDialog();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            AlertDialog speedDialog3 = ShadowAlertDialog.getLatestAlertDialog();
            speedDialog3.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals(View.GONE, badge.getVisibility());
            overlay.detach();
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

package io.github.kichikuou.xsystem4;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.Button;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
        bridge.setFontScalePercent(context, "Rance10", 125);
        bridge.setScrollbarScalePercent(context, "Rance10", 180);
        bridge.setFontScalePercent(context, "DohnaDohna", 90);
        bridge.setScrollbarScalePercent(context, "DohnaDohna", 140);

        Intent r10Intent = new Intent(context, GameModifierActivity.class)
                .putExtra(GameModifierActivity.EXTRA_DIR_NAME, "Rance10")
                .putExtra(GameModifierActivity.EXTRA_GAME_ID, GameCatalog.ID_RANCE10);
        GameModifierActivity r10Act = Robolectric.buildActivity(GameModifierActivity.class, r10Intent)
                .create().get();
        Button r10FontBtn = r10Act.findViewById(R.id.btn_mod_font_size);
        Button r10SbBtn = r10Act.findViewById(R.id.btn_mod_scrollbar);
        assertTrue("Expected 125% in " + r10FontBtn.getText(), r10FontBtn.getText().toString().contains("125%"));
        assertTrue("Expected 180% in " + r10SbBtn.getText(), r10SbBtn.getText().toString().contains("180%"));

        Intent ddIntent = new Intent(context, GameModifierActivity.class)
                .putExtra(GameModifierActivity.EXTRA_DIR_NAME, "DohnaDohna")
                .putExtra(GameModifierActivity.EXTRA_GAME_ID, GameCatalog.ID_DOHNA_DOHNA);
        GameModifierActivity ddAct = Robolectric.buildActivity(GameModifierActivity.class, ddIntent)
                .create().get();
        Button ddFontBtn = ddAct.findViewById(R.id.btn_mod_font_size);
        Button ddSbBtn = ddAct.findViewById(R.id.btn_mod_scrollbar);
        assertTrue("Expected 90% in " + ddFontBtn.getText(), ddFontBtn.getText().toString().contains("90%"));
        assertTrue("Expected 140% in " + ddSbBtn.getText(), ddSbBtn.getText().toString().contains("140%"));

        Intent evIntent = new Intent(context, GameModifierActivity.class)
                .putExtra(GameModifierActivity.EXTRA_DIR_NAME, "Evenicle")
                .putExtra(GameModifierActivity.EXTRA_GAME_ID, GameCatalog.ID_EVENICLE);
        GameModifierActivity evAct = Robolectric.buildActivity(GameModifierActivity.class, evIntent)
                .create().get();
        Button evFontBtn = evAct.findViewById(R.id.btn_mod_font_size);
        Button evSbBtn = evAct.findViewById(R.id.btn_mod_scrollbar);
        assertTrue("Expected 100% in " + evFontBtn.getText(), evFontBtn.getText().toString().contains("100%"));
        assertTrue("Expected 100% in " + evSbBtn.getText(), evSbBtn.getText().toString().contains("100%"));
    }
}

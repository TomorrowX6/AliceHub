package io.github.kichikuou.xsystem4;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class PerGameUiScaleKeyTest {
    @Test
    public void normalizeUiScaleGameKeyHandlesPathsUnicodeAndInvalidValues() {
        GameModifierBridge bridge = GameModifierBridge.INSTANCE;
        assertEquals("Rance10", bridge.normalizeUiScaleGameKey("Rance10"));
        assertEquals("Rance10", bridge.normalizeUiScaleGameKey("  Rance10  "));
        assertEquals("Rance10", bridge.normalizeUiScaleGameKey("/storage/emulated/0/Games/Rance10/"));
        assertEquals("DohnaDohna", bridge.normalizeUiScaleGameKey("C:\\Games\\DohnaDohna\\"));
        assertEquals("兰斯10", bridge.normalizeUiScaleGameKey("/data/user/0/app/files/兰斯10"));
        assertEquals("兰斯01", bridge.normalizeUiScaleGameKey("/data/user/0/app/files/兰斯01"));
        assertEquals("ドーナドーナ", bridge.normalizeUiScaleGameKey("ドーナドーナ"));
        assertEquals("イブニクル1", bridge.normalizeUiScaleGameKey("イブニクル1"));
        assertNull(bridge.normalizeUiScaleGameKey(null));
        assertNull(bridge.normalizeUiScaleGameKey(""));
        assertNull(bridge.normalizeUiScaleGameKey("   "));
        assertNull(bridge.normalizeUiScaleGameKey("/"));
        assertNull(bridge.normalizeUiScaleGameKey("///"));
        assertNull(bridge.normalizeUiScaleGameKey("."));
        assertNull(bridge.normalizeUiScaleGameKey(".."));
    }

    @Test
    public void scaleClampingRespectsBounds() {
        GameModifierBridge bridge = GameModifierBridge.INSTANCE;
        assertEquals(GameModifierBridge.MIN_FONT_SCALE_PCT, bridge.clampFontScale(10));
        assertEquals(125, bridge.clampFontScale(125));
        assertEquals(GameModifierBridge.MAX_FONT_SCALE_PCT, bridge.clampFontScale(500));

        assertEquals(GameModifierBridge.MIN_SCROLLBAR_SCALE_PCT, bridge.clampScrollbarScale(50));
        assertEquals(180, bridge.clampScrollbarScale(180));
        assertEquals(GameModifierBridge.MAX_SCROLLBAR_SCALE_PCT, bridge.clampScrollbarScale(999));
    }
}

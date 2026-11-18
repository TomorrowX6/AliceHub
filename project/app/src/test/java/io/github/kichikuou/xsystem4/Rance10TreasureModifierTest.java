package io.github.kichikuou.xsystem4;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;

public class Rance10TreasureModifierTest {
    @Test
    public void treasure100ActionOmitsCombatPowerStats() {
        Map<String, Integer> params = GameModifierBridge.INSTANCE.treasure100ActionParams();

        assertEquals(3, params.size());
        assertEquals(Integer.valueOf(999), params.get("perFindTreasure"));
        assertEquals(Integer.valueOf(99), params.get("treasureNotGetCount"));
        assertEquals(Integer.valueOf(999), params.get("rareLuck"));

        // Must never implicitly boost itemStar / party stats / combat power.
        assertFalse(params.containsKey("itemStar"));
        assertFalse(params.containsKey("partyIsFixStatus"));
        assertFalse(params.containsKey("partyAtk"));
        assertFalse(params.containsKey("cardAtkBonus"));
        assertFalse(params.containsKey("allStatusPer"));
        assertFalse(params.containsKey("totalExp"));
        assertFalse(params.containsKey("partyRank"));
    }

    @Test
    public void computeEnabledLiveLockMaskPreservesExistingAndStreamerFlag() {
        GameModifierBridge bridge = GameModifierBridge.INSTANCE;
        assertNotEquals(0, GameModifierBridge.LOCK_TREASURE_100);
        assertNotEquals(GameModifierBridge.LOCK_DISABLE_STREAMER_MODE, GameModifierBridge.LOCK_TREASURE_100);

        int existing = GameModifierBridge.LOCK_INFINITE_AP | GameModifierBridge.LOCK_MAX_RESOURCES;

        int withStreamerFromFlag = bridge.computeEnabledLiveLockMask(
                existing, true, GameModifierBridge.LOCK_TREASURE_100);
        assertEquals(
                existing | GameModifierBridge.LOCK_TREASURE_100 | GameModifierBridge.LOCK_DISABLE_STREAMER_MODE,
                withStreamerFromFlag);

        int withStreamerFromMask = bridge.computeEnabledLiveLockMask(
                existing | GameModifierBridge.LOCK_DISABLE_STREAMER_MODE,
                false,
                GameModifierBridge.LOCK_TREASURE_100);
        assertEquals(
                existing | GameModifierBridge.LOCK_TREASURE_100 | GameModifierBridge.LOCK_DISABLE_STREAMER_MODE,
                withStreamerFromMask);

        int withoutStreamer = bridge.computeEnabledLiveLockMask(
                existing, false, GameModifierBridge.LOCK_TREASURE_100);
        assertEquals(existing | GameModifierBridge.LOCK_TREASURE_100, withoutStreamer);
        assertEquals(0, withoutStreamer & GameModifierBridge.LOCK_DISABLE_STREAMER_MODE);
        assertEquals(3, Integer.bitCount(withoutStreamer & ~GameModifierBridge.LOCK_DISABLE_STREAMER_MODE));

        // Guard: enabling via computeEnabledLiveLockMask must never grant LOCK_DISABLE_STREAMER_MODE.
        int maskedStreamer = bridge.computeEnabledLiveLockMask(
                existing, false, GameModifierBridge.LOCK_DISABLE_STREAMER_MODE);
        assertEquals(existing, maskedStreamer);

        // Idempotent when LOCK_TREASURE_100 is already active.
        assertEquals(
                withoutStreamer,
                bridge.computeEnabledLiveLockMask(withoutStreamer, false, GameModifierBridge.LOCK_TREASURE_100));
    }
}

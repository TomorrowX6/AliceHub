package io.github.kichikuou.xsystem4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

public class GlobalSaveModFieldsTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void preservesUnchangedLegacyFlagsAndValidatesEdits() {
        assertNull(GameModifierBridge.INSTANCE.parseGlobalSaveFlagEdit("2", "2"));
        assertEquals(Integer.valueOf(0), GameModifierBridge.INSTANCE.parseGlobalSaveFlagEdit("0", "2"));
        assertEquals(Integer.valueOf(1), GameModifierBridge.INSTANCE.parseGlobalSaveFlagEdit("1", "1"));
        assertThrows(IllegalArgumentException.class,
                () -> GameModifierBridge.INSTANCE.parseGlobalSaveFlagEdit("2", "1"));
        assertThrows(IllegalArgumentException.class,
                () -> GameModifierBridge.INSTANCE.parseGlobalSaveFlagEdit("-1", "2"));
    }

    private void assertFields(File dir, boolean global, boolean collection, boolean csd) {
        for (String key : new String[]{"gamePlayCount", "itemStarRank", "setAllCharRank", "setTakeOverCardCount"}) {
            assertEquals(key, global, GameModifierBridge.INSTANCE.isGlobalSaveModFieldAvailable(dir, key));
        }
        for (String key : new String[]{"leaderAtkRatio", "trophyExpBonus", "trophyStatusMaxPer"}) {
            assertEquals(key, collection, GameModifierBridge.INSTANCE.isGlobalSaveModFieldAvailable(dir, key));
        }
        for (String key : new String[]{"TotalClearCount", "totalClearPoint", "AutoBattleMedal", "CardComplete"}) {
            assertEquals(key, csd, GameModifierBridge.INSTANCE.isGlobalSaveModFieldAvailable(dir, key));
        }
    }

    @Test
    public void csdOnlyOffersNoGlobalOrCollectionFields() throws Exception {
        for (boolean nested : new boolean[]{false, true}) {
            File root = folder.newFolder();
            File dir = nested ? new File(root, "SaveData") : root;
            if (nested) org.junit.Assert.assertTrue(dir.mkdir());
            org.junit.Assert.assertTrue(new File(dir, "CommonSystemData.sav").createNewFile());
            assertFields(root, false, false, true);
        }
    }

    @Test
    public void existingSaveFilesAllowCsdCreationAndOnlyTheirOwnFields() throws Exception {
        File root = folder.newFolder();
        assertFields(root, false, false, false);
        File global = new File(root, "GlobalSave.asd");
        org.junit.Assert.assertTrue(global.mkdir());
        assertFields(root, false, false, false);
        org.junit.Assert.assertTrue(global.delete());
        org.junit.Assert.assertTrue(global.createNewFile());
        assertFields(root, true, false, true);
        File sub = new File(root, "SaveData");
        org.junit.Assert.assertTrue(sub.mkdir());
        org.junit.Assert.assertTrue(new File(sub, "Collection.asd").createNewFile());
        assertFields(root, true, true, true);
        org.junit.Assert.assertTrue(global.delete());
        assertFields(root, false, true, true);
    }
}

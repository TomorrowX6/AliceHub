package io.github.kichikuou.xsystem4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LegacySystem3ImportTest {
    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void picksMainGameInsteadOfTheHintDiskInAnExtractedZip() throws Exception {
        File game = tmp.newFolder("兰斯 4.1");
        File hint = tmp.newFolder("提示盘");
        assertTrue(new File(game, "ADISK.dat").createNewFile());
        assertTrue(new File(game, "Acg.DAT").createNewFile());
        assertTrue(new File(game, "system3.ini").createNewFile());
        assertTrue(new File(hint, "ADISK.dat").createNewFile());
        assertTrue(new File(hint, "system3.ini").createNewFile());

        assertEquals(game, GameListKt.findGameRootDirInternal(tmp.getRoot(), null, null));
        assertTrue(GameCatalog.INSTANCE.isSystem3xGameDir(game));
        assertTrue(GameCatalog.INSTANCE.isLegacySystem3GameDir(game));
        assertFalse(GameCatalog.INSTANCE.isLegacySystem3GameDir(hint));
    }

    private static NativeGameImport.TreeEntry<String, String> entry(String name, boolean folder) {
        return new NativeGameImport.TreeEntry<>(name, name, folder, name);
    }

    @Test
    public void picksOnlyTheLegacyGameTreeWhenImportingAFolder() {
        var main = List.of(entry("ADISK.dat", false), entry("ACG.dat", false),
                entry("system3.ini", false), entry("bgm", true));
        var hint = List.of(entry("ADISK.dat", false), entry("system3.ini", false));
        var result = NativeGameImport.INSTANCE.discoverImportSources(
                "root", List.of(entry("提示盘", true), entry("game", true)),
                id -> id.equals("game") ? main : hint);

        assertEquals(main, result.getPrimaryEntries());
        assertEquals(1, result.getSourceTrees().size());
        assertEquals("game", result.getSourceTrees().get(0).getFirst());
    }

    @Test
    public void doesNotOverrideNewerEngineMarkers() {
        for (String marker : List.of("Rance41SA.ALD", "ADISK.ALD", "System39.ini", "System39.ain")) {
            var files = List.of("ADISK.DAT", "ACG.DAT", "system3.ini", marker);
            assertFalse(GameCatalog.INSTANCE.isLegacySystem3GameFiles(files));
            assertTrue(GameCatalog.INSTANCE.isSystem3xGameFiles(files));
        }
        for (String marker : List.of("System40.ini", "AliceStart.ini", "Game.AIN")) {
            var files = List.of("ADISK.DAT", "ACG.DAT", "system3.ini", marker);
            assertFalse(GameCatalog.INSTANCE.isLegacySystem3GameFiles(files));
            assertFalse(GameCatalog.INSTANCE.isSystem3xGameFiles(files));
        }
    }
}

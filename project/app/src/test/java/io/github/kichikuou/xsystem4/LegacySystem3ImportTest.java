package io.github.kichikuou.xsystem4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Arrays;
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
    public void recognizesRance42ChinesePackageWithoutSelectingItsNestedHintDisk() throws Exception {
        File game = tmp.newFolder("【卡拉保护协会】兰斯 4.2～天使组～");
        for (String name : List.of("ADISK.dat", "ACG.dat", "BCG.dat", "CCG.dat", "AMUS.DAT",
                "GAIJI.DAT", "system3.ini", "simhei.ttf", "system3.exe", "SDL2.dll",
                "SDL2_ttf.dll", "libfreetype-6.dll", "zlib1.dll", "Version.txt", "兰斯4.2 汉化说明.pdf")) {
            assertTrue(new File(game, name).createNewFile());
        }
        File hint = new File(game, "提示盘");
        assertTrue(hint.mkdir());
        assertTrue(new File(hint, "ADISK.dat").createNewFile());
        assertTrue(new File(hint, "system3.ini").createNewFile());
        File bgm = new File(game, "bgm");
        assertTrue(bgm.mkdir());
        assertTrue(new File(bgm, "playlist.txt").createNewFile());
        for (int track = 2; track <= 20; track++) {
            assertTrue(new File(bgm, String.format(java.util.Locale.ROOT, "%02d.mp3", track)).createNewFile());
        }
        File bonus = new File(game, "广播剧BONUS");
        assertTrue(bonus.mkdir());
        assertTrue(new File(bonus, "兰斯～真实的兰斯～ 02.魔将军巴布兹.mp4").createNewFile());

        assertEquals(game, GameListKt.findGameRootDirInternal(tmp.getRoot(), null, null));
        assertTrue(GameCatalog.INSTANCE.isLegacySystem3GameDir(game));
        assertFalse(GameCatalog.INSTANCE.isLegacySystem3GameDir(hint));
        assertEquals(GameCatalog.PREFIX_CUSTOM + game.getName(),
                GameCatalog.INSTANCE.classifyDirectory(game, game.getName(), null, null));
        for (String path : List.of("system3.ini", "simhei.ttf", "bgm/playlist.txt", "bgm/02.mp3", "ADISK.dat")) {
            assertFalse(NativeGameImport.INSTANCE.skip(path));
        }
        assertTrue(NativeGameImport.INSTANCE.skip("SDL2.dll"));
        assertTrue(NativeGameImport.INSTANCE.skip("提示盘/运行提示盘.bat"));
    }

    @Test
    public void picksOnlyTheLegacyGameTreeWhenImportingAFolder() {
        List<NativeGameImport.TreeEntry<String, String>> main = Arrays.asList(entry("ADISK.dat", false), entry("ACG.dat", false),
                entry("system3.ini", false), entry("bgm", true));
        List<NativeGameImport.TreeEntry<String, String>> hint = Arrays.asList(entry("ADISK.dat", false), entry("system3.ini", false));
        NativeGameImport.DiscoveredImportSources<String, String> result = NativeGameImport.INSTANCE.discoverImportSources(
                "root", Arrays.asList(entry("提示盘", true), entry("game", true)),
                id -> id.equals("game") ? main : hint);

        assertEquals(main, result.getPrimaryEntries());
        assertEquals(1, result.getSourceTrees().size());
        assertEquals("game", result.getSourceTrees().get(0).getFirst());
    }

    @Test
    public void doesNotOverrideNewerEngineMarkers() {
        for (String marker : Arrays.asList("Rance41SA.ALD", "ADISK.ALD", "System39.ini", "System39.ain")) {
            List<String> files = Arrays.asList("ADISK.DAT", "ACG.DAT", "system3.ini", marker);
            assertFalse(GameCatalog.INSTANCE.isLegacySystem3GameFiles(files));
            assertTrue(GameCatalog.INSTANCE.isSystem3xGameFiles(files));
        }
        for (String marker : Arrays.asList("System40.ini", "AliceStart.ini", "Game.AIN")) {
            List<String> files = Arrays.asList("ADISK.DAT", "ACG.DAT", "system3.ini", marker);
            assertFalse(GameCatalog.INSTANCE.isLegacySystem3GameFiles(files));
            assertFalse(GameCatalog.INSTANCE.isSystem3xGameFiles(files));
        }
    }
}

package io.github.kichikuou.xsystem4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class Tsumamigui3CatalogTest {
    private static final Charset GBK = Charset.forName("GBK");

    private static final String[] RUNTIME_PATHS = {
            "Version.txt", "AliceStart.ini", "Tsumamigui3.ex", "Data/Shader.slk", "Tsumamigui3.ain",
            "Tsumamigui3Pact.afa", "Tsumamigui3Flat.afa", "Tsumamigui3Font.fnl", "Tsumamigui3Sound.afa",
            "Movie/ED.apeg", "Tsumamigui3CG.afa", "Tsumamigui3Voice.afa"
    };

    private static final String[] BLOAT_PATHS = {
            "Tsumamigui3.exe", "DecodeCG.dll", "OpenSaveFolder.exe", "ResetConfig.exe", "Uninstaller.exe",
            "InstallInfo/SaveFolderSetting.ini", "License/libogg/COPYING", "Manual/index.html",
            "v0.8汉化原版/AliceStart.ini", "v0.8汉化原版/Tsumamigui3.ain", "v0.8汉化原版/Tsumamigui3.ex",
            "补丁/DLL.rar", "补丁/QZM3_v0.801校对测试版.zip", "Inner.zip.rar去掉", "攻略/QZM3_攻略图表（中文）.rar去掉",
            "全CG存档/汉化版全CG存档.rar去掉", "读我_v0.801汉化测试版.txt"
    };

    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    private static void writeFile(File file, byte[] bytes) throws Exception {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            assertTrue(parent.mkdirs());
        }
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(bytes);
        }
    }

    private static byte[] cnIni() {
        return ("GameName\t= \"妻中蜜３ v0.801汉化 个人校对测试版\"\n"
                + "BootName\t= \"Tsumamigui3\"\n"
                + "CodeName\t= \"Tsumamigui3.ain\"\n"
                + "SaveFolder\t= \"SaveData\"\n").getBytes(GBK);
    }

    @Test
    public void classifiesTsumamigui3AndKeepsOtherTsumaGamesApart() throws Exception {
        File cnDir = tmp.newFolder("SomeFolder");
        writeFile(new File(cnDir, "AliceStart.ini"), cnIni());
        writeFile(new File(cnDir, "Tsumamigui3.ain"), new byte[]{0});
        writeFile(new File(cnDir, "Tsumamigui3CG.afa"), new byte[]{0});

        File iniOnlyDir = tmp.newFolder("IniOnly");
        writeFile(new File(iniOnlyDir, "AliceStart.ini"), cnIni());
        writeFile(new File(iniOnlyDir, "Renamed.ain"), new byte[]{0});

        File tsumashibori = tmp.newFolder("Other");
        writeFile(new File(tsumashibori, "System40.ini"), "GameName = \"妻しぼり\"\n".getBytes(GBK));
        writeFile(new File(tsumashibori, "妻しぼり.ain"), new byte[]{0});

        assertTrue(GameCatalog.INSTANCE.isTsumamigui3GameDir(cnDir));
        assertTrue(GameCatalog.INSTANCE.isTsumamigui3GameDir(iniOnlyDir));
        assertFalse(GameCatalog.INSTANCE.isTsumamigui3GameDir(tsumashibori));
        assertTrue(GameCatalog.INSTANCE.isTsumamigui3GameFiles(Arrays.asList("AliceStart.ini", "Tsumamigui3Voice.afa")));
        assertFalse(GameCatalog.INSTANCE.isTsumamigui3GameFiles(Arrays.asList("AliceStart.ini", "Other.ain")));
        assertEquals(GameCatalog.ID_TSUMAMIGUI3, GameCatalog.INSTANCE.classifyDirectory(cnDir, "UnknownFolder", null, null));
        assertEquals(GameCatalog.ID_TSUMAMIGUI3, GameCatalog.INSTANCE.classifyGameRootDir(cnDir));
        assertEquals(GameCatalog.ID_TSUMASHIBORI, GameCatalog.INSTANCE.classifyDirectory(tsumashibori, "Other", null, null));
        assertTrue(GameModifierBridge.INSTANCE.isTsumamigui3GameDir(cnDir));
        assertFalse(GameModifierBridge.INSTANCE.isTsumashiboriGameDir(cnDir));
        assertFalse(GameModifierBridge.INSTANCE.isRance10GameDir(cnDir, null, null));
        assertFalse(GameModifierBridge.INSTANCE.isEvenicleGameDir(cnDir, null, null));

        assertTrue(GameCatalog.INSTANCE.validateImportedGameDir(cnDir, null, GameCatalog.ID_TSUMAMIGUI3));
        assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(cnDir, null, GameCatalog.ID_TSUMASHIBORI));
        assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(tsumashibori, null, GameCatalog.ID_TSUMAMIGUI3));

        String[] aliases = {
                "Tsumamigui3", "Tsumamigui 3", "tsuma_migui_3", "TsumamiguiIII", "妻みぐい3", "妻みぐい３",
                "ツマミグイ3", "妻中蜜３", "妻中蜜3 汉化版",
                "[ALICESOFT] 妻みぐい3 汉化硬盘版 [汉化v0.801]"
        };
        for (String alias : aliases) {
            assertTrue(alias, GameCatalog.INSTANCE.isTsumamigui3Alias(alias));
            assertEquals(alias, GameCatalog.ID_TSUMAMIGUI3, GameCatalog.INSTANCE.classifyDirectory(null, alias, null, null));
        }
        String[] nonAliases = {"Tsumamigui", "妻みぐい", "妻みぐい２", "Tsumamigui2", "Tsumashibori", "妻しぼり", "Rance03", ""};
        for (String other : nonAliases) {
            assertFalse(other, GameCatalog.INSTANCE.isTsumamigui3Alias(other));
            assertNotEquals(other, GameCatalog.ID_TSUMAMIGUI3, GameCatalog.INSTANCE.classifyDirectory(null, other, null, null));
        }
    }

    @Test
    public void exposesDescriptorCatalogOrderMetadataAndCdn() {
        GameDescriptor desc = GameCatalog.INSTANCE.descriptorForWithResolver(
                GameCatalog.ID_TSUMAMIGUI3, null, resId -> "resolved:" + resId);
        assertEquals("resolved:" + R.string.game_tsumamigui3_title, desc.getTitle());
        assertEquals("Tsumamigui3", desc.getDefaultDirName());
        assertEquals(R.drawable.ic_game_tsumamigui3, desc.getIconRes());
        assertTrue(desc.getSupportsOnlineBaseInstall());
        assertEquals("https://alicehub.000.moe/tsumamigui3", desc.getOnlineBaseCdnUrl());
        assertFalse(desc.getSupportsOnlineMod());
        assertFalse(desc.getSupportsLocalMod());

        List<String> order = GameCatalog.builtInSelectOrder();
        assertTrue(order.indexOf(GameCatalog.ID_TSUMAMIGUI3) > order.indexOf(GameCatalog.ID_TSUMASHIBORI));

        GameSortInfo info = GameSortMetadata.INSTANCE.forGame(GameCatalog.ID_TSUMAMIGUI3);
        assertNotNull(info);
        assertEquals("2016-01-29", info.getReleaseDate());
        assertEquals(79.5, info.getRating(), 0.001);
        assertEquals("https://vndb.org/v18486", info.getScoreSourceUrl());

        assertEquals(2403975825L, AppSettings.defaultBaseGameSizeForUrl(OnlineInstaller.TSUMAMIGUI3_CDN_URL + "/"));
    }

    @Test
    public void prunesReleaseBloatAndKeepsRuntimeFiles() throws Exception {
        for (String rel : RUNTIME_PATHS) {
            assertFalse(rel, GameListKt.isTsumamigui3BloatRelPath(rel));
            assertFalse(rel, NativeGameImport.INSTANCE.skip(rel));
        }
        for (String rel : BLOAT_PATHS) {
            assertTrue(rel, GameListKt.isTsumamigui3BloatRelPath(rel));
        }
        assertFalse(GameListKt.isTsumamigui3BloatRelPath("Tsumamigui3.zip"));
        assertFalse(GameListKt.isTsumamigui3BloatRelPath("game/Tsumamigui3.rar"));

        File staging = tmp.newFolder("staging");
        File root = new File(staging, "[ALICESOFT] 妻みぐい3 汉化硬盘版 [汉化v0.801]");
        for (String rel : RUNTIME_PATHS) {
            writeFile(new File(root, rel), rel.equals("AliceStart.ini") ? cnIni() : new byte[]{1});
        }
        for (String rel : BLOAT_PATHS) {
            writeFile(new File(root, rel), rel.endsWith("AliceStart.ini") ? cnIni() : new byte[]{2});
        }
        assertEquals(root, GameListKt.findGameRootDirInternal(staging, null, GameCatalog.ID_TSUMAMIGUI3));

        GameListKt.pruneRedundantBaseBloat(root);
        for (String rel : RUNTIME_PATHS) {
            assertTrue(rel, new File(root, rel).isFile());
        }
        for (String rel : BLOAT_PATHS) {
            assertFalse(rel, new File(root, rel).exists());
        }
        assertEquals("Tsumamigui3", OnlineInstaller.INSTANCE.resolveInstallDirBaseName(root, "Tsumamigui3"));
        assertEquals("Tsumamigui3", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("AliceStart.ini", "Tsumamigui3.ain"), false));
    }

    @Test
    public void evictsOnlyTsumamigui3Staging() throws Exception {
        File storage = tmp.newFolder("storage");
        File own = new File(storage, ".online_base_staging_Tsumamigui3");
        File other = new File(storage, ".online_base_staging_Tsumashibori");
        assertTrue(own.mkdirs());
        assertTrue(other.mkdirs());
        List<File> evicted = OnlineInstaller.INSTANCE.evictBaseStagingToTrash(storage, GameCatalog.ID_TSUMAMIGUI3, null);
        assertEquals(1, evicted.size());
        assertFalse(own.exists());
        assertTrue(other.exists());
        assertEquals(StandardCharsets.UTF_8.name(), "UTF-8");
    }
}

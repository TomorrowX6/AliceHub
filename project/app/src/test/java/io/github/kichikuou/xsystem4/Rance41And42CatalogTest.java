package io.github.kichikuou.xsystem4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class Rance41And42CatalogTest {
    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    private static void write(File root, String name, String content) throws Exception {
        File file = new File(root, name);
        if (!file.getParentFile().isDirectory()) {
            assertTrue(file.getParentFile().mkdirs());
        }
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void classifiesRance41AndRance42BySystem3IniAndAliases() throws Exception {
        File r41 = tmp.newFolder("SomeLegacyFolderA");
        write(r41, "ADISK.dat", "d");
        write(r41, "ACG.dat", "c");
        write(r41, "system3.ini", "[config]\ngame = rance41\ntitle = 兰斯 4.1 ～拯救制药厂！～ V1.0\n");

        File r42 = tmp.newFolder("SomeLegacyFolderB");
        write(r42, "ADISK.dat", "d");
        write(r42, "ACG.dat", "c");
        write(r42, "system3.ini", "[config]\ngame = rance42\ntitle = 兰斯 4.2 ～天使组～\n");

        assertTrue(GameCatalog.INSTANCE.isLegacySystem3GameDir(r41));
        assertTrue(GameCatalog.INSTANCE.isLegacySystem3GameDir(r42));
        assertTrue(GameCatalog.INSTANCE.isRance41GameDir(r41));
        assertFalse(GameCatalog.INSTANCE.isRance42GameDir(r41));
        assertTrue(GameCatalog.INSTANCE.isRance42GameDir(r42));
        assertFalse(GameCatalog.INSTANCE.isRance41GameDir(r42));

        assertEquals(GameCatalog.ID_RANCE41, GameCatalog.INSTANCE.classifyGameRootDir(r41));
        assertEquals(GameCatalog.ID_RANCE41, GameCatalog.INSTANCE.classifyDirectory(r41, r41.getName(), null, null));
        assertTrue(GameCatalog.INSTANCE.validateImportedGameDir(r41, null, GameCatalog.ID_RANCE41));
        assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(r41, null, GameCatalog.ID_RANCE42));
        assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(r41, null, GameCatalog.ID_RANCE04));

        assertEquals(GameCatalog.ID_RANCE42, GameCatalog.INSTANCE.classifyGameRootDir(r42));
        assertEquals(GameCatalog.ID_RANCE42, GameCatalog.INSTANCE.classifyDirectory(r42, r42.getName(), null, null));
        assertTrue(GameCatalog.INSTANCE.validateImportedGameDir(r42, null, GameCatalog.ID_RANCE42));
        assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(r42, null, GameCatalog.ID_RANCE41));
        assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(r42, null, GameCatalog.ID_RANCE04));

        assertEquals("Rance41", OnlineInstaller.INSTANCE.resolveInstallDirBaseName(r41, "Rance41"));
        assertEquals("Rance42", OnlineInstaller.INSTANCE.resolveInstallDirBaseName(r42, "Rance42"));

        for (String alias : Arrays.asList("Rance41", "Rance 4.1", "兰斯4.1", "ランス４．１", "拯救制药厂", "お薬工場")) {
            assertTrue(alias, GameCatalog.INSTANCE.isRance41Alias(alias));
            assertFalse(alias, GameCatalog.INSTANCE.isRance42Alias(alias));
            assertEquals(alias, GameCatalog.ID_RANCE41, GameCatalog.INSTANCE.classifyDirectory(null, alias, null, null));
        }
        for (String alias : Arrays.asList("Rance42", "Rance 4.2", "兰斯4.2", "ランス４．２", "天使组", "エンジェル組", "Angel Army")) {
            assertTrue(alias, GameCatalog.INSTANCE.isRance42Alias(alias));
            assertFalse(alias, GameCatalog.INSTANCE.isRance41Alias(alias));
            assertEquals(alias, GameCatalog.ID_RANCE42, GameCatalog.INSTANCE.classifyDirectory(null, alias, null, null));
        }
        for (String other : Arrays.asList("", "Rance04", "Rance IV", "Rance5D", "Rance06", "Rance410")) {
            assertFalse(other, GameCatalog.INSTANCE.isRance41Alias(other));
            assertFalse(other, GameCatalog.INSTANCE.isRance42Alias(other));
            assertNotEquals(other, GameCatalog.ID_RANCE41, GameCatalog.INSTANCE.classifyDirectory(null, other, null, null));
            assertNotEquals(other, GameCatalog.ID_RANCE42, GameCatalog.INSTANCE.classifyDirectory(null, other, null, null));
        }
    }

    @Test
    public void registersOnlineDescriptorsOrderAndSortingMetadata() {
        GameDescriptor d41 = GameCatalog.INSTANCE.descriptorForWithResolver(GameCatalog.ID_RANCE41, null, id -> "title");
        assertEquals("rance41", d41.getId());
        assertEquals("Rance41", d41.getDefaultDirName());
        assertNull(d41.getExpectedAinName());
        assertTrue(d41.getSupportsOnlineBaseInstall());
        assertEquals(OnlineInstaller.RANCE41_CDN_URL, d41.getOnlineBaseCdnUrl());
        assertEquals(AppSettings.DEFAULT_RANCE41_BYTES, AppSettings.defaultBaseGameSizeForUrl(OnlineInstaller.RANCE41_CDN_URL));
        assertFalse(d41.getSupportsOnlineMod());
        assertFalse(d41.getSupportsLocalMod());

        GameDescriptor d42 = GameCatalog.INSTANCE.descriptorForWithResolver(GameCatalog.ID_RANCE42, null, id -> "title");
        assertEquals("rance42", d42.getId());
        assertEquals("Rance42", d42.getDefaultDirName());
        assertNull(d42.getExpectedAinName());
        assertTrue(d42.getSupportsOnlineBaseInstall());
        assertEquals(OnlineInstaller.RANCE42_CDN_URL, d42.getOnlineBaseCdnUrl());
        assertEquals(AppSettings.DEFAULT_RANCE42_BYTES, AppSettings.defaultBaseGameSizeForUrl(OnlineInstaller.RANCE42_CDN_URL));
        assertFalse(d42.getSupportsOnlineMod());
        assertFalse(d42.getSupportsLocalMod());

        List<String> order = GameCatalog.builtInSelectOrder();
        assertEquals(24, order.size());
        assertEquals(order.indexOf(GameCatalog.ID_RANCE04) + 1, order.indexOf(GameCatalog.ID_RANCE41));
        assertEquals(order.indexOf(GameCatalog.ID_RANCE41) + 1, order.indexOf(GameCatalog.ID_RANCE42));
        assertEquals(order.indexOf(GameCatalog.ID_RANCE42) + 1, order.indexOf(GameCatalog.ID_RANCE5D));

        GameSortInfo info41 = GameSortMetadata.INSTANCE.forGame(GameCatalog.ID_RANCE41);
        assertNotNull(info41);
        assertEquals("1995-12-01", info41.getReleaseDate());
        assertEquals(Double.valueOf(53.3), info41.getRating());
        assertEquals("https://vndb.org/v2043", info41.getScoreSourceUrl());

        GameSortInfo info42 = GameSortMetadata.INSTANCE.forGame(GameCatalog.ID_RANCE42);
        assertNotNull(info42);
        assertEquals("1995-12-08", info42.getReleaseDate());
        assertEquals(Double.valueOf(53.7), info42.getRating());
        assertEquals("https://vndb.org/v2044", info42.getScoreSourceUrl());
    }
}

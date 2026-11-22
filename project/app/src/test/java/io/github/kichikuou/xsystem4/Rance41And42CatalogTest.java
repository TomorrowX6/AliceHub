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
        assertEquals(39, order.size());
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

    private static byte[] buildAdiskWithCrc32(long targetCrc, int totalSize) {
        byte[] data = new byte[Math.max(totalSize, 256)];
        for (int i = 0; i < 252; i++) {
            data[i] = (byte) (i & 0xFF);
        }
        // Compute IEEE CRC32 of the first 252 bytes, then solve the final 4 bytes so CRC32(data[0..255]) == targetCrc.
        int[] table = new int[256];
        for (int i = 0; i < 256; i++) {
            int c = i;
            for (int k = 0; k < 8; k++) {
                c = ((c & 1) != 0) ? (0xEDB88320 ^ (c >>> 1)) : (c >>> 1);
            }
            table[i] = c;
        }
        int crc = ~0;
        for (int i = 0; i < 252; i++) {
            crc = table[(crc ^ (data[i] & 0xFF)) & 0xFF] ^ (crc >>> 8);
        }
        int desired = ~(int) targetCrc;
        int[] patch = new int[4];
        for (int i = 3; i >= 0; i--) {
            int topByte = (desired >>> 24) & 0xFF;
            int idx = -1;
            for (int j = 0; j < 256; j++) {
                if (((table[j] >>> 24) & 0xFF) == topByte) {
                    idx = j;
                    break;
                }
            }
            assertTrue("CRC32 inverse lookup must succeed", idx >= 0);
            patch[i] = idx;
            desired = (desired ^ table[idx]) << 8;
        }
        for (int i = 0; i < 4; i++) {
            data[252 + i] = (byte) ((crc ^ patch[i]) & 0xFF);
            crc = table[(crc ^ (data[252 + i] & 0xFF)) & 0xFF] ^ (crc >>> 8);
        }
        return data;
    }

    @Test
    public void recognizesV105AldReleasesAndCrossRejects() throws Exception {
        File r41Ald = tmp.newFolder("rance41_v105");
        write(r41Ald, "RAN41_SA.ald", "sa");
        write(r41Ald, "RAN41_GA.ald", "ga");

        File r42Ald = tmp.newFolder("rance42_v105");
        write(r42Ald, "ran42_sa.ald", "sa");
        write(r42Ald, "ran42_ga.ald", "ga");

        assertTrue(GameCatalog.INSTANCE.isSystem3xGameDir(r41Ald));
        assertFalse(GameCatalog.INSTANCE.isLegacySystem3GameDir(r41Ald));
        assertTrue(GameCatalog.INSTANCE.isRance41GameDir(r41Ald));
        assertFalse(GameCatalog.INSTANCE.isRance42GameDir(r41Ald));
        assertEquals(GameCatalog.ID_RANCE41, GameCatalog.INSTANCE.classifyGameRootDir(r41Ald));
        assertTrue(GameCatalog.INSTANCE.validateImportedGameDir(r41Ald, null, GameCatalog.ID_RANCE41));
        assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(r41Ald, null, GameCatalog.ID_RANCE42));
        assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(r41Ald, null, GameCatalog.ID_RANCE04));
        assertEquals("RAN41_SA", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(Arrays.asList("RAN41_SA.ald", "RAN41_GA.ald"), false));

        assertTrue(GameCatalog.INSTANCE.isSystem3xGameDir(r42Ald));
        assertFalse(GameCatalog.INSTANCE.isLegacySystem3GameDir(r42Ald));
        assertTrue(GameCatalog.INSTANCE.isRance42GameDir(r42Ald));
        assertFalse(GameCatalog.INSTANCE.isRance41GameDir(r42Ald));
        assertEquals(GameCatalog.ID_RANCE42, GameCatalog.INSTANCE.classifyGameRootDir(r42Ald));
        assertTrue(GameCatalog.INSTANCE.validateImportedGameDir(r42Ald, null, GameCatalog.ID_RANCE42));
        assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(r42Ald, null, GameCatalog.ID_RANCE41));
        assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(r42Ald, null, GameCatalog.ID_RANCE04));
        assertEquals("ran42_sa", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(Arrays.asList("ran42_sa.ald", "ran42_ga.ald"), false));
    }

    @Test
    public void recognizesOriginalLegacyDatByAdiskCrc32EvenAbove256Kb() throws Exception {
        // Larger than 256 KiB to verify bounded 256-byte prefix reading works on real-sized ADISK.DAT files.
        int realisticSize = 300 * 1024;
        long[] r41Crcs = {0xa43fb4b6L, 0xc39ca9e6L};
        long[] r42Crcs = {0x04d24d1eL, 0x07e46c85L};

        for (long crc : r41Crcs) {
            File dir = tmp.newFolder("orig_r41_" + Long.toHexString(crc));
            Files.write(new File(dir, "ADISK.DAT").toPath(), buildAdiskWithCrc32(crc, realisticSize));
            write(dir, "ACG.DAT", "cg");
            assertEquals(Long.valueOf(crc), GameCatalog.INSTANCE.legacyAdiskCrc32(Files.readAllBytes(new File(dir, "ADISK.DAT").toPath())));
            assertTrue(GameCatalog.INSTANCE.isRance41GameDir(dir));
            assertFalse(GameCatalog.INSTANCE.isRance42GameDir(dir));
            assertEquals(GameCatalog.ID_RANCE41, GameCatalog.INSTANCE.classifyGameRootDir(dir));
            assertTrue(GameCatalog.INSTANCE.validateImportedGameDir(dir, null, GameCatalog.ID_RANCE41));
            assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(dir, null, GameCatalog.ID_RANCE42));
        }

        for (long crc : r42Crcs) {
            File dir = tmp.newFolder("orig_r42_" + Long.toHexString(crc));
            Files.write(new File(dir, "ADISK.DAT").toPath(), buildAdiskWithCrc32(crc, realisticSize));
            write(dir, "ACG.DAT", "cg");
            assertEquals(Long.valueOf(crc), GameCatalog.INSTANCE.legacyAdiskCrc32(Files.readAllBytes(new File(dir, "ADISK.DAT").toPath())));
            assertTrue(GameCatalog.INSTANCE.isRance42GameDir(dir));
            assertFalse(GameCatalog.INSTANCE.isRance41GameDir(dir));
            assertEquals(GameCatalog.ID_RANCE42, GameCatalog.INSTANCE.classifyGameRootDir(dir));
            assertTrue(GameCatalog.INSTANCE.validateImportedGameDir(dir, null, GameCatalog.ID_RANCE42));
            assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(dir, null, GameCatalog.ID_RANCE41));
        }

        // Another legacy System 3 game (e.g., Toushin Toshi II, 0xe27dd441) must not classify as Rance 4.1 or 4.2.
        File otherLegacy = tmp.newFolder("orig_toushin2");
        Files.write(new File(otherLegacy, "ADISK.DAT").toPath(), buildAdiskWithCrc32(0xe27dd441L, realisticSize));
        write(otherLegacy, "ACG.DAT", "cg");
        assertTrue(GameCatalog.INSTANCE.isLegacySystem3GameDir(otherLegacy));
        assertFalse(GameCatalog.INSTANCE.isRance41GameDir(otherLegacy));
        assertFalse(GameCatalog.INSTANCE.isRance42GameDir(otherLegacy));
        assertNull(GameCatalog.INSTANCE.classifyGameRootDir(otherLegacy));
        assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(otherLegacy, null, GameCatalog.ID_RANCE41));
        assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(otherLegacy, null, GameCatalog.ID_RANCE42));
    }
}

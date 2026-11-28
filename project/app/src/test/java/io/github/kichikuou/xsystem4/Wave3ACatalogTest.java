package io.github.kichikuou.xsystem4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.CRC32;

/**
 * Unit tests for Wave 3A classic AliceSoft catalog additions (79 -> 89 built-in titles):
 * mamatoto, onlyyourecross, mamorigami, ambivalenz, funnybee, toushin2sp,
 * mugenhouyou, otomesenki, gakuenhyouryuu, maidnosusume.
 */
public class Wave3ACatalogTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static byte[] make256BytesWithCrc32(long targetCrc) {
        byte[] buf = new byte[256];
        CRC32 crc = new CRC32();
        crc.update(buf, 0, 252);
        int forward = (int) (crc.getValue() ^ 0xffffffffL);
        int want = (int) (targetCrc ^ 0xffffffffL);
        int[] crcTable = new int[256];
        for (int i = 0; i < 256; i++) {
            int v = i;
            for (int k = 0; k < 8; k++) {
                v = ((v & 1) != 0) ? (0xedb88320 ^ (v >>> 1)) : (v >>> 1);
            }
            crcTable[i] = v;
        }
        int[] revIdx = new int[256];
        for (int i = 0; i < 256; i++) {
            revIdx[(crcTable[i] >>> 24) & 0xff] = i;
        }
        int r = want;
        int[] tableIdx = new int[4];
        for (int i = 3; i >= 0; i--) {
            tableIdx[i] = revIdx[(r >>> 24) & 0xff];
            r = (r ^ crcTable[tableIdx[i]]) << 8;
        }
        for (int i = 0; i < 4; i++) {
            int b = (forward ^ tableIdx[i]) & 0xff;
            buf[252 + i] = (byte) b;
            forward = (forward >>> 8) ^ crcTable[(forward ^ b) & 0xff];
        }
        return buf;
    }

    @Test
    public void wave3aCatalogOrderDescriptorsAndMetadataAreWired() {
        List<String> order = GameCatalog.builtInSelectOrder();
        assertEquals(171, order.size());

        Object[][] specs = new Object[][]{
            {GameCatalog.ID_MAMATOTO, "Mamatoto", OnlineInstaller.MAMATOTO_CDN_URL, 186917297L, "1999-07-01", 67.6, "https://vndb.org/v1860", "https://vndb.org/v1860"},
            {GameCatalog.ID_ONLYYOURECROSS, "OnlyYouReCross", OnlineInstaller.ONLYYOURECROSS_CDN_URL, 548617730L, "2001-07-26", 73.5, "https://vndb.org/v2000", "https://vndb.org/v2000"},
            {GameCatalog.ID_MAMORIGAMI, "Mamorigami", OnlineInstaller.MAMORIGAMI_CDN_URL, 147669840L, "1999-03-25", 66.1, "https://vndb.org/v4746", "https://vndb.org/v4746"},
            {GameCatalog.ID_AMBIVALENZ, "AmbivalenZ", OnlineInstaller.AMBIVALENZ_CDN_URL, 82305908L, "1994-04-28", 66.5, "https://vndb.org/v2048", "https://vndb.org/v2048"},
            {GameCatalog.ID_FUNNYBEE, "FunnyBee", OnlineInstaller.FUNNYBEE_CDN_URL, 68093371L, "1994-08-10", 70.5, "https://vndb.org/v1723", "https://vndb.org/v1723"},
            {GameCatalog.ID_TOUSHIN2SP, "Toushin2Sp", OnlineInstaller.TOUSHIN2SP_CDN_URL, 41383183L, "1995-12-08", 70.4, "https://vndb.org/v6916", "https://vndb.org/v6916"},
            {GameCatalog.ID_MUGENHOUYOU, "MugenHouyou", OnlineInstaller.MUGENHOUYOU_CDN_URL, 50736493L, "1995-07-07", 58.2, "https://vndb.org/v5933", "https://vndb.org/v5933"},
            {GameCatalog.ID_OTOMESENKI, "OtomeSenki", OnlineInstaller.OTOMESENKI_CDN_URL, 2849367L, "1997-01-01", null, null, "https://www.alicesoft.com/"},
            {GameCatalog.ID_GAKUENHYOURYUU, "GakuenHyouryuu", OnlineInstaller.GAKUENHYOURYUU_CDN_URL, 3931456L, "1999-07-01", 65.6, "https://vndb.org/v20393", "https://vndb.org/v20393"},
            {GameCatalog.ID_MAIDNOSUSUME, "MaidNoSusume", OnlineInstaller.MAIDNOSUSUME_CDN_URL, 21891136L, "1999-07-01", null, null, "https://vndb.org/v20392"}
        };

        for (Object[] spec : specs) {
            String id = (String) spec[0];
            String dirName = (String) spec[1];
            String cdnUrl = (String) spec[2];
            long sizeBytes = (Long) spec[3];
            String releaseDate = (String) spec[4];
            Double rating = (Double) spec[5];
            String scoreUrl = (String) spec[6];
            String releaseUrl = (String) spec[7];

            assertTrue(id, order.contains(id));
            GameDescriptor desc = GameCatalog.INSTANCE.descriptorForWithResolver(id, null, resId -> id);
            assertEquals(dirName, desc.getDefaultDirName());
            assertNull(id, desc.getExpectedAinName());
            assertEquals(cdnUrl, desc.getOnlineBaseCdnUrl());
            assertTrue(id, desc.getSupportsOnlineBaseInstall());
            assertFalse(id, desc.getSupportsOnlineMod());
            assertEquals(sizeBytes, AppSettings.defaultBaseGameSizeForUrl(cdnUrl));

            GameSortInfo meta = GameSortMetadata.INSTANCE.forGame(id);
            assertNotNull(id, meta);
            assertEquals(releaseDate, meta.getReleaseDate());
            assertEquals(rating, meta.getRating());
            assertEquals(scoreUrl, meta.getScoreSourceUrl());
            assertEquals(releaseUrl, meta.getReleaseSourceUrl());
        }
    }

    @Test
    public void wave3aFileAndAliasClassificationAvoidsCollisions() throws Exception {
        // System 3.x file classification
        assertEquals(GameCatalog.ID_MAMATOTO, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("ママトトSA.ALD", "ママトトGA.ALD", "playlist.txt")));
        assertEquals(GameCatalog.ID_ONLYYOURECROSS, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("System39.ain", "System39.ini", "オンリーSA.ALD", "オンリーGA.ALD")));
        assertEquals(GameCatalog.ID_MAMORIGAMI, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("守神SA.ALD", "守神GA.ALD", "守神MA.ALD")));
        assertEquals(GameCatalog.ID_GAKUENHYOURYUU, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("学園漂流SA.ALD", "学園漂流GA.ALD", "学園漂流MA.ALD")));
        assertEquals(GameCatalog.ID_MAIDNOSUSUME, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("メイドSA.ALD", "メイドGA.ALD", "メイドMA.ALD")));

        // Legacy System 3 classification via system3.ini AND ADISK.DAT CRC32 (CD + FD editions)
        Object[][] legacyCases = new Object[][]{
            {GameCatalog.ID_AMBIVALENZ, "ambivalenz_cd", "ACG.DAT", 0x4b10db69L, "AmbivalenZ"},
            {GameCatalog.ID_AMBIVALENZ, "ambivalenz_fd", "ACG.DAT", 0xa6b48dfeL, "AmbivalenZ"},
            {GameCatalog.ID_FUNNYBEE, "funnybee_cd", "ACG.DAT", 0xe14e3971L, "FunnyBee"},
            {GameCatalog.ID_FUNNYBEE, "funnybee_fd", "ACG.DAT", 0x731267faL, "FunnyBee"},
            {GameCatalog.ID_TOUSHIN2SP, "toushin2_sp", "ACG.DAT", 0x2172c7b2L, "Toushin2Sp"},
            {GameCatalog.ID_MUGENHOUYOU, "mugen", "ACG.DAT", 0xbb27d1baL, "MugenHouyou"},
            {GameCatalog.ID_OTOMESENKI, "otome", "ACG.DAT", 0x49a4db15L, "OtomeSenki"}
        };
        for (Object[] c : legacyCases) {
            String expectedId = (String) c[0];
            String gameIniVal = (String) c[1];
            String cgFile = (String) c[2];
            long crc = (Long) c[3];
            String expectedBase = (String) c[4];

            File iniDir = tmp.newFolder("legacy3a_ini_" + gameIniVal);
            assertTrue(new File(iniDir, "ADISK.DAT").createNewFile());
            assertTrue(new File(iniDir, cgFile).createNewFile());
            try (FileOutputStream out = new FileOutputStream(new File(iniDir, "system3.ini"))) {
                out.write(("game = " + gameIniVal + "\n").getBytes(StandardCharsets.UTF_8));
            }
            assertEquals(expectedId, GameCatalog.INSTANCE.classifyGameRootDir(iniDir));
            assertTrue(GameCatalog.INSTANCE.validateImportedGameDir(iniDir, null, expectedId));

            File crcDir = tmp.newFolder("legacy3a_crc_" + gameIniVal);
            assertTrue(new File(crcDir, cgFile).createNewFile());
            byte[] header = make256BytesWithCrc32(crc);
            assertEquals(Long.valueOf(crc), GameCatalog.INSTANCE.legacyAdiskCrc32(header));
            try (FileOutputStream out = new FileOutputStream(new File(crcDir, "ADISK.DAT"))) {
                out.write(header);
            }
            assertEquals(expectedId, GameCatalog.INSTANCE.classifyGameRootDir(crcDir));

            List<String> crcFiles = Arrays.asList("ADISK.DAT", cgFile);
            assertEquals(expectedBase, NativeGameImport.INSTANCE.resolveCopyTreeBaseName(
                    crcFiles,
                    GameCatalog.INSTANCE.classifyGameRootFiles(crcFiles, Collections.emptyList(),
                            name -> name.equalsIgnoreCase("ADISK.DAT") ? header : null),
                    null,
                    false,
                    name -> name.equalsIgnoreCase("ADISK.DAT") ? header : null));
        }

        // Sibling collision guards
        assertEquals(GameCatalog.ID_ONLYYOURECROSS, GameCatalog.INSTANCE.classifyDirectory(
                null, "OnlyYouReCross", null, "Only you -リ・クルス-"));
        assertEquals(GameCatalog.ID_ONLYYOURECROSS, GameCatalog.INSTANCE.classifyDirectory(
                null, "Only You -Re Cross-", null, null));
        assertEquals(GameCatalog.ID_ONLYYOU, GameCatalog.INSTANCE.classifyDirectory(
                null, "OnlyYou", null, "Only You 世纪末的朱丽叶们"));
        assertFalse(GameCatalog.INSTANCE.isOnlyYouAlias("Only You -Re Cross-"));
        assertFalse(GameCatalog.INSTANCE.isOnlyYouAlias("Only you -リ・クルス-"));

        assertEquals(GameCatalog.ID_TOUSHIN2SP, GameCatalog.INSTANCE.classifyDirectory(
                null, "Toushin2Sp", null, "斗神都市II 然后，从那以后…"));
        assertEquals(GameCatalog.ID_TOUSHIN2SP, GameCatalog.INSTANCE.classifyDirectory(
                null, "UnknownDir", null, "斗神都市II 然后，从那以后…"));
        assertEquals(GameCatalog.ID_TOUSHIN2SP, GameCatalog.INSTANCE.classifyDirectory(
                null, "闘神都市II そして、それから…", null, null));
        assertTrue(GameCatalog.INSTANCE.isToushin2SpAlias("斗神都市II 然后，从那以后…"));
        assertTrue(GameCatalog.INSTANCE.isToushin2SpAlias("鬥神都市II 然後，從那以後…"));
        assertEquals(GameCatalog.ID_TOUSHIN2, GameCatalog.INSTANCE.classifyDirectory(
                null, "Toushin2", null, "斗神都市II"));
        assertEquals(GameCatalog.ID_TOUSHIN1, GameCatalog.INSTANCE.classifyDirectory(
                null, "Toushin1", null, "斗神都市"));
        assertFalse(GameCatalog.INSTANCE.isToushin2Alias("闘神都市II そして、それから…"));
        assertFalse(GameCatalog.INSTANCE.isToushin2Alias("斗神都市II 然后，从那以后…"));
        assertFalse(GameCatalog.INSTANCE.isToushin1Alias("闘神都市II そして、それから…"));

        assertEquals(GameCatalog.ID_GAKUENHYOURYUU, GameCatalog.INSTANCE.classifyDirectory(
                null, "GakuenHyouryuu", null, "学园漂流战记"));
        assertEquals(GameCatalog.ID_GAKUENKING, GameCatalog.INSTANCE.classifyDirectory(
                null, "GakuenKING", null, "学园KING"));
        assertEquals(GameCatalog.ID_GAKUENKINGEX, GameCatalog.INSTANCE.classifyDirectory(
                null, "GakuenKINGEx", null, "学园KING 番外篇"));
    }

    @Test
    public void wave3aStagingCleanupImportNamesAndModifierGuardsWork() throws Exception {
        File storage = tmp.newFolder("storage3a");
        File sOnlyYou = new File(storage, ".online_base_staging_onlyyou");
        File sReCross = new File(storage, ".online_base_staging_onlyyourecross");
        File sT2 = new File(storage, ".online_base_staging_toushin2");
        File sT2Sp = new File(storage, ".online_base_staging_toushin2sp");
        File sGk = new File(storage, ".online_base_staging_gakuenking");
        File sGh = new File(storage, ".online_base_staging_gakuenhyouryuu");
        for (File f : Arrays.asList(sOnlyYou, sReCross, sT2, sT2Sp, sGk, sGh)) {
            assertTrue(f.mkdirs());
        }

        OnlineInstaller.INSTANCE.cleanupBaseStagingForGame(storage, GameCatalog.ID_ONLYYOU, null);
        assertFalse(sOnlyYou.exists());
        assertTrue(sReCross.exists());
        OnlineInstaller.INSTANCE.cleanupBaseStagingForGame(storage, GameCatalog.ID_ONLYYOURECROSS, null);
        assertFalse(sReCross.exists());

        OnlineInstaller.INSTANCE.cleanupBaseStagingForGame(storage, GameCatalog.ID_TOUSHIN2, null);
        assertFalse(sT2.exists());
        assertTrue(sT2Sp.exists());
        OnlineInstaller.INSTANCE.cleanupBaseStagingForGame(storage, GameCatalog.ID_TOUSHIN2SP, null);
        assertFalse(sT2Sp.exists());

        OnlineInstaller.INSTANCE.cleanupBaseStagingForGame(storage, GameCatalog.ID_GAKUENKING, null);
        assertFalse(sGk.exists());
        assertTrue(sGh.exists());
        OnlineInstaller.INSTANCE.cleanupBaseStagingForGame(storage, GameCatalog.ID_GAKUENHYOURYUU, null);
        assertFalse(sGh.exists());

        assertEquals("Mamatoto", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("ママトトSA.ALD", "ママトトGA.ALD")));
        assertEquals("OnlyYouReCross", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("System39.ain", "オンリーSA.ALD")));
        assertEquals("Mamorigami", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("守神SA.ALD", "守神GA.ALD")));
        assertEquals("GakuenHyouryuu", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("学園漂流SA.ALD", "学園漂流GA.ALD")));
        assertEquals("MaidNoSusume", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("メイドSA.ALD", "メイドGA.ALD")));

        File reCrossDir = tmp.newFolder("stage_onlyyourecross");
        assertTrue(new File(reCrossDir, "System39.ain").createNewFile());
        assertTrue(new File(reCrossDir, "オンリーSA.ALD").createNewFile());
        assertEquals("OnlyYouReCross", OnlineInstaller.INSTANCE.resolveInstallDirBaseName(reCrossDir, "OnlyYouReCross"));
        assertTrue(GameModifierBridge.INSTANCE.isOnlyYouReCrossGameDir(reCrossDir));
        assertTrue(GameModifierBridge.INSTANCE.isNonModifierBuiltInGameDir(reCrossDir));
    }
}

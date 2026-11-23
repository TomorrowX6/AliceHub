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
 * Unit tests for Wave 2 AliceSoft catalog additions (58 -> 79 built-in titles):
 * sanbiki, persiom, seeinao, hushaby, ayumi, cielclaire, nightdemon, katsumi,
 * drstop, dpsall, koredps, kaerualice, prostudentg, prostudentgood, pastelchime,
 * pastelchime2, yakata3, dalk, ningengari, paschacpp, pascha3.
 */
public class Wave2CatalogTest {
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
    public void wave2CatalogOrderDescriptorsAndMetadataAreWired() {
        List<String> order = GameCatalog.builtInSelectOrder();
        assertEquals(79, order.size());

        Object[][] specs = new Object[][]{
            {GameCatalog.ID_SANBIKI, "Sanbiki", OnlineInstaller.SANBIKI_CDN_URL, 160062187L, "2008-04-11", 58.1, "https://vndb.org/v31408", "https://vndb.org/v31408"},
            {GameCatalog.ID_PERSIOM, "PERSIOM", OnlineInstaller.PERSIOM_CDN_URL, 287307616L, "2000-04-13", 65.8, "https://vndb.org/v4477", "https://vndb.org/v4477"},
            {GameCatalog.ID_SEEINAO, "SeeInAO", OnlineInstaller.SEEINAO_CDN_URL, 197954141L, "2000-07-06", 64.6, "https://vndb.org/v4771", "https://vndb.org/v4771"},
            {GameCatalog.ID_HUSHABY, "HushabyBaby", OnlineInstaller.HUSHABY_CDN_URL, 32337216L, "1999-09-30", 51.9, "https://vndb.org/v3049", "https://vndb.org/v3049"},
            {GameCatalog.ID_AYUMI, "AyumiChan", OnlineInstaller.AYUMI_CDN_URL, 89331318L, "1992-12-15", 58.9, "https://vndb.org/v1929", "https://vndb.org/v1929"},
            {GameCatalog.ID_CIELCLAIRE, "CielClaire", OnlineInstaller.CIELCLAIRE_CDN_URL, 772056801L, "2003-06-27", 68.5, "https://vndb.org/v493", "https://vndb.org/v493"},
            {GameCatalog.ID_NIGHTDEMON, "NightDemon", OnlineInstaller.NIGHTDEMON_CDN_URL, 761609079L, "2003-08-29", 62.4, "https://vndb.org/v5896", "https://vndb.org/v5896"},
            {GameCatalog.ID_KATSUMI, "Katsumi", OnlineInstaller.KATSUMI_CDN_URL, 127134186L, "1997-04-25", null, null, "https://vndb.org/v4515"},
            {GameCatalog.ID_DRSTOP, "DrStop", OnlineInstaller.DRSTOP_CDN_URL, 2372912L, "1992-04-15", 60.0, "https://vndb.org/v1828", "https://vndb.org/v1828"},
            {GameCatalog.ID_DPSALL, "DpsAll", OnlineInstaller.DPSALL_CDN_URL, 48806657L, "1995-11-10", 58.7, "https://vndb.org/v2234", "https://vndb.org/v2234"},
            {GameCatalog.ID_KOREDPS, "KoreDps", OnlineInstaller.KOREDPS_CDN_URL, 34941173L, "2000-12-07", null, null, "https://vndb.org/v1825"},
            {GameCatalog.ID_KAERUALICE, "KaeruAlice", OnlineInstaller.KAERUALICE_CDN_URL, 114809179L, "2000-12-07", null, null, "https://vndb.org/r3541"},
            {GameCatalog.ID_PROSTUDENTG, "ProstudentG", OnlineInstaller.PROSTUDENTG_CDN_URL, 74947167L, "1993-05-15", 69.7, "https://vndb.org/v4701", "https://vndb.org/v4701"},
            {GameCatalog.ID_PROSTUDENTGOOD, "ProstudentGood", OnlineInstaller.PROSTUDENTGOOD_CDN_URL, 144813947L, "1999-01-14", 68.7, "https://vndb.org/v4702", "https://vndb.org/v4702"},
            {GameCatalog.ID_PASTELCHIME, "PastelChime", OnlineInstaller.PASTELCHIME_CDN_URL, 128679015L, "1998-11-26", 62.4, "https://vndb.org/v4693", "https://vndb.org/v4693"},
            {GameCatalog.ID_PASTELCHIME2, "PastelChime2", OnlineInstaller.PASTELCHIME2_CDN_URL, 2725818511L, "2005-06-17", 72.6, "https://vndb.org/v1080", "https://vndb.org/v1080"},
            {GameCatalog.ID_YAKATA3, "Yakata3", OnlineInstaller.YAKATA3_CDN_URL, 14582865L, "1995-04-01", 57.0, "https://vndb.org/v9426", "https://vndb.org/v9426"},
            {GameCatalog.ID_DALK, "DALK", OnlineInstaller.DALK_CDN_URL, 13360976L, "1992-12-15", 59.0, "https://vndb.org/v1798", "https://vndb.org/v1798"},
            {GameCatalog.ID_NINGENGARI, "NingenGari", OnlineInstaller.NINGENGARI_CDN_URL, 17006400L, "1997-12-18", 57.4, "https://vndb.org/v1827", "https://vndb.org/v1827"},
            {GameCatalog.ID_PASCHACPP, "PaschaCpp", OnlineInstaller.PASCHACPP_CDN_URL, 1391660611L, "2005-12-09", 71.7, "https://vndb.org/v4758", "https://vndb.org/v4758"},
            {GameCatalog.ID_PASCHA3, "Pascha3", OnlineInstaller.PASCHA3_CDN_URL, 3638072646L, "2013-02-15", 74.8, "https://vndb.org/v11292", "https://vndb.org/v11292"}
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
    public void wave2FileAndAliasClassificationAvoidsCollisions() throws Exception {
        // System 4 & System 3.x file classification
        assertEquals(GameCatalog.ID_SANBIKI, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("System40.ini", "GBK.ain", "三匹がGA.ald", "三匹がBA.ald")));
        assertEquals(GameCatalog.ID_SENGOKU_RANCE, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("System40.ini", "戦国ランス.ain", "戦国GA.ald")));
        assertEquals(GameCatalog.ID_PERSIOM, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("PERSIOM_SA.ALD", "PERSIOM_GA.ALD")));
        assertEquals(GameCatalog.ID_SEEINAO, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("イルカSA.ald", "イルカGA.ald", "playlist.txt")));
        assertEquals(GameCatalog.ID_HUSHABY, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("幽霊SA.ALD", "幽霊GA.ALD")));
        assertEquals(GameCatalog.ID_CIELCLAIRE, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("System39.ain", "System39.ini", "逃避行SA.ALD", "逃避行GA.ald")));
        assertEquals(GameCatalog.ID_NIGHTDEMON, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("System39.ain", "夢鬼SA.ald", "NightDemonDemo1.alk")));
        assertEquals(GameCatalog.ID_KATSUMI, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList(".xsys35rc", "かつみSA.ALD", "かつみGA.ALD")));
        assertEquals(GameCatalog.ID_DRSTOP, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("DS_SA.ALD", "DS_GA.ALD", "DS_MA.ALD")));
        assertEquals(GameCatalog.ID_KOREDPS, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("System39.ain", "これＤＰＳ？SA.ALD", "これＤＰＳ？GA.ALD")));
        assertEquals(GameCatalog.ID_KAERUALICE, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("System39.ain", "かえるにょ国にょアリスSA.ALD", "かえるにょ国にょアリスGA.ALD")));
        assertEquals(GameCatalog.ID_KAERUNYO, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("ぱにょSA.ALD", "ぱにょGA.ALD")));
        assertEquals(GameCatalog.ID_PROSTUDENTGOOD, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("ＳＲ猿SA.ALD", "ＳＲ猿GA.ALD", "playlist.txt")));
        assertEquals(GameCatalog.ID_PASTELCHIME, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("ぱすてるSA.ALD", "ぱすてるGA.ALD", "playlist.txt")));
        assertEquals(GameCatalog.ID_PASTELCHIME2, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("System40.ini", "ぱすてる２.ain", "ぱすてる２GA.ald")));
        assertEquals(GameCatalog.ID_PASCHACPP, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("System40.ini", "ぱすてる＋.ain", "ぱすてる＋GA.ald")));
        assertEquals(GameCatalog.ID_PASCHA3, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("AliceStart.ini", "パステルチャイム３.ain", "Pascha3_cn.afa")));
        assertEquals(GameCatalog.ID_DALK, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("DALK_SA.ALD", "DALK_GA.ALD")));
        assertEquals(GameCatalog.ID_NINGENGARI, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("人間狩りSA.ALD", "人間狩りGA.ALD")));

        // Legacy System 3 classification via system3.ini AND ADISK.DAT CRC32 (including ACG0.DAT for ayumi)
        Object[][] legacyCases = new Object[][]{
            {GameCatalog.ID_AYUMI, "ayumi_cd", "ACG0.DAT", 0xd2bed9eeL, "AyumiChan"},
            {GameCatalog.ID_AYUMI, "ayumi_fd", "ACG.DAT", 0x4e2fed2aL, "AyumiChan"},
            {GameCatalog.ID_DPSALL, "dps_all", "ACG.DAT", 0xd48b4ec6L, "DpsAll"},
            {GameCatalog.ID_PROSTUDENTG, "prog_cd", "ACG.DAT", 0xfb0e4a63L, "ProstudentG"},
            {GameCatalog.ID_PROSTUDENTG, "prog_fd", "ACG.DAT", 0x5ffbfee7L, "ProstudentG"},
            {GameCatalog.ID_YAKATA3, "yakata3_cd", "ACG.DAT", 0x7f8f5e2aL, "Yakata3"},
            {GameCatalog.ID_YAKATA3, "yakata3_fd", "ACG.DAT", 0x58ebcc99L, "Yakata3"}
        };
        for (Object[] c : legacyCases) {
            String expectedId = (String) c[0];
            String gameIniVal = (String) c[1];
            String cgFile = (String) c[2];
            long crc = (Long) c[3];
            String expectedBase = (String) c[4];

            File iniDir = tmp.newFolder("legacy_ini_" + gameIniVal);
            assertTrue(new File(iniDir, "ADISK.DAT").createNewFile());
            assertTrue(new File(iniDir, cgFile).createNewFile());
            try (FileOutputStream out = new FileOutputStream(new File(iniDir, "system3.ini"))) {
                out.write(("game = " + gameIniVal + "\n").getBytes(StandardCharsets.UTF_8));
            }
            assertEquals(expectedId, GameCatalog.INSTANCE.classifyGameRootDir(iniDir));
            assertTrue(GameCatalog.INSTANCE.validateImportedGameDir(iniDir, null, expectedId));

            File crcDir = tmp.newFolder("legacy_crc_" + gameIniVal);
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
        assertEquals(GameCatalog.ID_SANBIKI, GameCatalog.INSTANCE.classifyDirectory(
                null, "Sanbiki", null, "战国兰斯 三匹が斬ったり突いたり燃やしたり"));
        assertEquals(GameCatalog.ID_SANBIKI, GameCatalog.INSTANCE.classifyDirectory(
                null, "戦国ランス～三匹が斬ったり突いたり燃やしたり～", null, null));
        assertEquals(GameCatalog.ID_SENGOKU_RANCE, GameCatalog.INSTANCE.classifyDirectory(
                null, "SengokuRance", null, "战国兰斯"));

        assertEquals(GameCatalog.ID_KAERUALICE, GameCatalog.INSTANCE.classifyDirectory(
                null, "かえるにょ国にょアリス", null, "蛙国爱丽丝"));
        assertEquals(GameCatalog.ID_KAERUNYO, GameCatalog.INSTANCE.classifyDirectory(
                null, "かえるにょ・ぱにょ～ん", null, "回家扭·帕扭"));
        assertFalse(GameCatalog.INSTANCE.isKaerunyoAlias("かえるにょ国にょアリス"));

        assertEquals(GameCatalog.ID_KOREDPS, GameCatalog.INSTANCE.classifyDirectory(null, "KoreDps", null, "这是D.P.S.？"));
        assertEquals(GameCatalog.ID_DPSALL, GameCatalog.INSTANCE.classifyDirectory(null, "DpsAll", null, "D.P.S. 全部"));
        assertEquals(GameCatalog.ID_SDPSMARIA, GameCatalog.INSTANCE.classifyDirectory(null, "SuperDpsMaria", null, "Super D.P.S 玛利亚和坎潘"));
        assertFalse(GameCatalog.INSTANCE.isDpsAllAlias("これD.P.S.？"));

        assertEquals(GameCatalog.ID_PROSTUDENTGOOD, GameCatalog.INSTANCE.classifyDirectory(null, "ProstudentGood", null, "职业学生Good"));
        assertEquals(GameCatalog.ID_PROSTUDENTG, GameCatalog.INSTANCE.classifyDirectory(null, "ProstudentG", null, "职业学生G"));
        assertFalse(GameCatalog.INSTANCE.isProstudentGAlias("职业学生Good"));

        assertEquals(GameCatalog.ID_PASCHA3, GameCatalog.INSTANCE.classifyDirectory(null, "Pascha3", null, "粉彩铃音3 封神追寻者"));
        assertEquals(GameCatalog.ID_PASCHACPP, GameCatalog.INSTANCE.classifyDirectory(null, "PaschaCpp", null, "粉彩铃音 C++"));
        assertEquals(GameCatalog.ID_PASTELCHIME2, GameCatalog.INSTANCE.classifyDirectory(null, "PastelChime2", null, "粉彩铃音 Continue"));
        assertEquals(GameCatalog.ID_PASTELCHIME, GameCatalog.INSTANCE.classifyDirectory(null, "PastelChime", null, "粉彩铃音"));
        assertFalse(GameCatalog.INSTANCE.isPastelChimeAlias("粉彩铃音 Continue"));
        assertFalse(GameCatalog.INSTANCE.isPastelChimeAlias("粉彩铃音 C++"));
        assertFalse(GameCatalog.INSTANCE.isPastelChimeAlias("粉彩铃音3 封神追寻者"));
        assertFalse(GameCatalog.INSTANCE.isPastelChime2Alias("粉彩铃音3 封神追寻者"));

        assertEquals(GameCatalog.ID_YAKATA3, GameCatalog.INSTANCE.classifyDirectory(null, "Yakata3", null, "爱丽丝之馆3"));
        assertEquals(GameCatalog.ID_YAKATA2, GameCatalog.INSTANCE.classifyDirectory(null, "Yakata2", null, "爱丽丝之馆2"));

        assertTrue(GameCatalog.INSTANCE.isDalkAlias("DALK"));
        assertFalse(GameCatalog.INSTANCE.isDalkAlias("DALK外伝"));
        assertFalse(GameCatalog.INSTANCE.isDalkAlias("DALKDemo"));
    }

    @Test
    public void wave2StagingCleanupImportNamesAndModifierGuardsWork() throws Exception {
        File storage = tmp.newFolder("storage");
        File sRance = new File(storage, ".online_base_staging_sengokurance");
        File sSanbiki = new File(storage, ".online_base_staging_sengokurance_sanbiki");
        File sKaeru = new File(storage, ".online_base_staging_kaerunyo");
        File sAlice = new File(storage, ".online_base_staging_kaerualice");
        File sProG = new File(storage, ".online_base_staging_prostudentg");
        File sProGood = new File(storage, ".online_base_staging_prostudentgood");
        File sPc1 = new File(storage, ".online_base_staging_pastelchime");
        File sPc2 = new File(storage, ".online_base_staging_pastelchime2");
        File sCpp = new File(storage, ".online_base_staging_paschacpp");
        File sPc3 = new File(storage, ".online_base_staging_pascha3");
        for (File f : Arrays.asList(sRance, sSanbiki, sKaeru, sAlice, sProG, sProGood, sPc1, sPc2, sCpp, sPc3)) {
            assertTrue(f.mkdirs());
        }

        OnlineInstaller.INSTANCE.cleanupBaseStagingForGame(storage, GameCatalog.ID_SENGOKU_RANCE, null);
        assertFalse(sRance.exists());
        assertTrue(sSanbiki.exists());
        OnlineInstaller.INSTANCE.cleanupBaseStagingForGame(storage, GameCatalog.ID_SANBIKI, null);
        assertFalse(sSanbiki.exists());

        OnlineInstaller.INSTANCE.cleanupBaseStagingForGame(storage, GameCatalog.ID_KAERUNYO, null);
        assertFalse(sKaeru.exists());
        assertTrue(sAlice.exists());
        OnlineInstaller.INSTANCE.cleanupBaseStagingForGame(storage, GameCatalog.ID_KAERUALICE, null);
        assertFalse(sAlice.exists());

        OnlineInstaller.INSTANCE.cleanupBaseStagingForGame(storage, GameCatalog.ID_PROSTUDENTG, null);
        assertFalse(sProG.exists());
        assertTrue(sProGood.exists());
        OnlineInstaller.INSTANCE.cleanupBaseStagingForGame(storage, GameCatalog.ID_PROSTUDENTGOOD, null);
        assertFalse(sProGood.exists());

        OnlineInstaller.INSTANCE.cleanupBaseStagingForGame(storage, GameCatalog.ID_PASTELCHIME, null);
        assertFalse(sPc1.exists());
        assertTrue(sPc2.exists());
        assertTrue(sCpp.exists());
        assertTrue(sPc3.exists());
        OnlineInstaller.INSTANCE.cleanupBaseStagingForGame(storage, GameCatalog.ID_PASTELCHIME2, null);
        assertFalse(sPc2.exists());
        assertTrue(sCpp.exists());
        assertTrue(sPc3.exists());
        OnlineInstaller.INSTANCE.cleanupBaseStagingForGame(storage, GameCatalog.ID_PASCHACPP, null);
        assertFalse(sCpp.exists());
        assertTrue(sPc3.exists());
        OnlineInstaller.INSTANCE.cleanupBaseStagingForGame(storage, GameCatalog.ID_PASCHA3, null);
        assertFalse(sPc3.exists());

        // NativeGameImport canonical dir names
        assertEquals("Sanbiki", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("System40.ini", "GBK.ain", "三匹がGA.ald")));
        assertEquals("KaeruAlice", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("System39.ain", "かえるにょ国にょアリスSA.ALD")));
        assertEquals("PaschaCpp", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("System40.ini", "ぱすてる＋.ain")));
        assertEquals("Pascha3", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("AliceStart.ini", "パステルチャイム３.ain", "Pascha3_cn.afa")));

        // Modifier non-modifier built-in guard
        File p3Dir = tmp.newFolder("stage_pascha3");
        assertTrue(new File(p3Dir, "AliceStart.ini").createNewFile());
        assertTrue(new File(p3Dir, "パステルチャイム３.ain").createNewFile());
        assertEquals("Pascha3", OnlineInstaller.INSTANCE.resolveInstallDirBaseName(p3Dir, "Pascha3"));
        assertTrue(GameModifierBridge.INSTANCE.isPascha3GameDir(p3Dir));
        assertTrue(GameModifierBridge.INSTANCE.isNonModifierBuiltInGameDir(p3Dir));
    }
}

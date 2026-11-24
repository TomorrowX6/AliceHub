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
import java.util.List;

/**
 * Unit tests for Wave 3B + 4A2 System 4 AliceSoft catalog additions (89 -> 106 built-in titles):
 * dalkgaiden, mamanyonyo, majonoshokuzai, saboten, doublesensei, ojousama, edoranze, vanish,
 * hokenshitsu, kamunagara, erumeiflower, erumeiwakare, manana1..manana5.
 */
public class Wave3BCatalogTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void wave3bCatalogOrderDescriptorsAndMetadataAreWired() {
        List<String> order = GameCatalog.builtInSelectOrder();
        assertEquals(106, order.size());

        Object[][] specs = new Object[][]{
            {GameCatalog.ID_DALKGAIDEN, "DalkGaiden", OnlineInstaller.DALKGAIDEN_CDN_URL, 460876218L, "2002-09-27", 58.7, "https://vndb.org/v1807", "https://vndb.org/v1807"},
            {GameCatalog.ID_MAMANYONYO, "Mamanyonyo", OnlineInstaller.MAMANYONYO_CDN_URL, 335070846L, "2003-02-07", 66.1, "https://vndb.org/v1111", "https://vndb.org/v1111"},
            {GameCatalog.ID_MAJONOSHOKUZAI, "MajoNoShokuzai", OnlineInstaller.MAJONOSHOKUZAI_CDN_URL, 974991851L, "2004-04-16", 71.2, "https://vndb.org/v4823", "https://vndb.org/v4823"},
            {GameCatalog.ID_SABOTEN, "Saboten", OnlineInstaller.SABOTEN_CDN_URL, 1157904094L, "2006-04-21", 65.6, "https://vndb.org/v502", "https://vndb.org/v502"},
            {GameCatalog.ID_DOUBLESENSEI, "DoubleSensei", OnlineInstaller.DOUBLESENSEI_CDN_URL, 1403588942L, "2007-06-29", 68.7, "https://vndb.org/v303", "https://vndb.org/v303"},
            {GameCatalog.ID_OJOUSAMA, "Ojousama", OnlineInstaller.OJOUSAMA_CDN_URL, 553143699L, "2007-10-05", 56.8, "https://vndb.org/v411", "https://vndb.org/v411"},
            {GameCatalog.ID_EDORANZE, "Edoranze", OnlineInstaller.EDORANZE_CDN_URL, 327142146L, "2008-10-12", 58.9, "https://vndb.org/v2289", "https://vndb.org/v2289"},
            {GameCatalog.ID_VANISH, "Vanish", OnlineInstaller.VANISH_CDN_URL, 936198458L, "2009-02-27", 64.1, "https://vndb.org/v1364", "https://vndb.org/v1364"},
            {GameCatalog.ID_HOKENSHITSU, "Hokenshitsu", OnlineInstaller.HOKENSHITSU_CDN_URL, 1125275837L, "2009-07-24", 72.1, "https://vndb.org/v2029", "https://vndb.org/v2029"},
            {GameCatalog.ID_KAMUNAGARA, "Kamunagara", OnlineInstaller.KAMUNAGARA_CDN_URL, 68102129L, "2005-09-30", 57.7, "https://vndb.org/v3104", "https://vndb.org/v3104"},
            {GameCatalog.ID_ERUMEIFLOWER, "ErumeiFlower", OnlineInstaller.ERUMEIFLOWER_CDN_URL, 149238805L, "2004-12-17", null, null, "https://vndb.org/v21749"},
            {GameCatalog.ID_ERUMEIWAKARE, "ErumeiWakare", OnlineInstaller.ERUMEIWAKARE_CDN_URL, 36214242L, "2006-06-02", null, null, "https://www.alicesoft.com/"},
            {GameCatalog.ID_MANANA1, "Manana1", OnlineInstaller.MANANA1_CDN_URL, 322325810L, "2004-08-27", 61.1, "https://vndb.org/v2036", "https://vndb.org/v2036"},
            {GameCatalog.ID_MANANA2, "Manana2", OnlineInstaller.MANANA2_CDN_URL, 346673076L, "2004-10-11", 59.5, "https://vndb.org/v2037", "https://vndb.org/v2037"},
            {GameCatalog.ID_MANANA3, "Manana3", OnlineInstaller.MANANA3_CDN_URL, 360676229L, "2004-12-17", 59.6, "https://vndb.org/v2038", "https://vndb.org/v2038"},
            {GameCatalog.ID_MANANA4, "Manana4", OnlineInstaller.MANANA4_CDN_URL, 284601476L, "2005-05-04", 59.1, "https://vndb.org/v2039", "https://vndb.org/v2039"},
            {GameCatalog.ID_MANANA5, "Manana5", OnlineInstaller.MANANA5_CDN_URL, 319631754L, "2005-06-17", 61.8, "https://vndb.org/v2041", "https://vndb.org/v2041"}
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
    public void wave3bFileAndAliasClassificationAvoidsCollisions() throws Exception {
        // System 4 file classification
        assertEquals(GameCatalog.ID_DALKGAIDEN, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("System40.ini", "ＤＡＬＫ外伝.ain", "ＤＡＬＫ外伝GA.ald")));
        assertEquals(GameCatalog.ID_MAMANYONYO, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("System40.ini", "ままにょにょ.ain", "ままにょにょGA.ald")));
        assertEquals(GameCatalog.ID_MAJONOSHOKUZAI, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("System40.ini", "魔女の贖罪.ain", "魔女の贖罪GA.ald")));
        assertEquals(GameCatalog.ID_SABOTEN, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("System40.ini", "よくばりサボテン.ain", "よくばりサボテンGA.ald")));
        assertEquals(GameCatalog.ID_DOUBLESENSEI, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("System40.ini", "だぶる先生らいふ.ain", "だぶる先生らいふGA.ald")));
        assertEquals(GameCatalog.ID_OJOUSAMA, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("System40.ini", "お嬢様.ain", "お嬢様GA.ald")));
        assertEquals(GameCatalog.ID_EDORANZE, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("System40.ini", "エドランゼ.ain", "エドランゼGA.ald")));
        assertEquals(GameCatalog.ID_VANISH, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("System40.ini", "ばにしゅ！.ain", "ばにしゅ！GA.ald")));
        assertEquals(GameCatalog.ID_HOKENSHITSU, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("AliceStart.ini", "僕だけの保健室.ain", "Hoken.alm")));
        assertEquals(GameCatalog.ID_KAMUNAGARA, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("System40.ini", "神武.ain", "神武GA.ald")));
        assertEquals(GameCatalog.ID_MANANA1, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("System40.ini", "まななＶＯＬ１.ain", "まななＶＯＬ１GA.ald")));
        assertEquals(GameCatalog.ID_MANANA2, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("System40.ini", "まななＶＯＬ２.ain", "まななＶＯＬ２GA.ald")));
        assertEquals(GameCatalog.ID_MANANA3, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("System40.ini", "まななＶＯＬ３.ain", "まななＶＯＬ３GA.ald")));
        assertEquals(GameCatalog.ID_MANANA4, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("System40.ini", "まななＶＯＬ４.ain", "まななＶＯＬ４GA.ald")));
        assertEquals(GameCatalog.ID_MANANA5, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("System40.ini", "まななＶＯＬ５.ain", "まななＶＯＬ５GA.ald")));

        // El Mayflower main vs Wakare no Yoru disambiguation via WA.wai size
        File efDir = tmp.newFolder("ef_main");
        assertTrue(new File(efDir, "System40.ini").createNewFile());
        assertTrue(new File(efDir, "エルメイフラワー.ain").createNewFile());
        try (FileOutputStream out = new FileOutputStream(new File(efDir, "エルメイフラワーWA.wai"))) {
            out.write(new byte[1803]);
        }
        assertEquals(GameCatalog.ID_ERUMEIFLOWER, GameCatalog.INSTANCE.classifyGameRootDir(efDir));
        assertTrue(GameCatalog.INSTANCE.isErumeiFlowerGameDir(efDir));
        assertFalse(GameCatalog.INSTANCE.isErumeiWakareGameDir(efDir));

        File ewDir = tmp.newFolder("ef_wakare");
        assertTrue(new File(ewDir, "System40.ini").createNewFile());
        assertTrue(new File(ewDir, "エルメイフラワー.ain").createNewFile());
        try (FileOutputStream out = new FileOutputStream(new File(ewDir, "エルメイフラワーWA.wai"))) {
            out.write(new byte[99]);
        }
        assertEquals(GameCatalog.ID_ERUMEIWAKARE, GameCatalog.INSTANCE.classifyGameRootDir(ewDir));
        assertTrue(GameCatalog.INSTANCE.isErumeiWakareGameDir(ewDir));
        assertFalse(GameCatalog.INSTANCE.isErumeiFlowerGameDir(ewDir));

        // Sibling collision guards
        assertEquals(GameCatalog.ID_DALKGAIDEN, GameCatalog.INSTANCE.classifyDirectory(null, "UnknownDir", null, "DALK外伝"));
        assertEquals(GameCatalog.ID_DALKGAIDEN, GameCatalog.INSTANCE.classifyDirectory(null, "DalkGaiden", null, "DALK外传"));
        assertEquals(GameCatalog.ID_DALK, GameCatalog.INSTANCE.classifyDirectory(null, "DALK", null, "DALK"));
        assertFalse(GameCatalog.INSTANCE.isDalkAlias("DALK外伝"));
        assertFalse(GameCatalog.INSTANCE.isDalkAlias("DALK外傳"));

        assertEquals(GameCatalog.ID_MAMANYONYO, GameCatalog.INSTANCE.classifyDirectory(null, "Mamanyonyo", null, "ままにょにょ"));
        assertEquals(GameCatalog.ID_MAMAN, GameCatalog.INSTANCE.classifyDirectory(null, "Maman", null, "は～とふるママン"));
        assertFalse(GameCatalog.INSTANCE.isMamanAlias("Mamanyonyo"));

        assertEquals(GameCatalog.ID_ERUMEIWAKARE, GameCatalog.INSTANCE.classifyDirectory(null, "UnknownDir", null, "艾尔梅花 ～别离之夜～"));
        assertEquals(GameCatalog.ID_ERUMEIFLOWER, GameCatalog.INSTANCE.classifyDirectory(null, "UnknownDir", null, "艾尔梅花"));
        assertFalse(GameCatalog.INSTANCE.isErumeiFlowerAlias("エルメイフラワー ～別れの夜～"));

        assertTrue(GameCatalog.INSTANCE.isManana1Alias("ウルトラ魔法少女まなな　ＶＯＬ１"));
        assertFalse(GameCatalog.INSTANCE.isManana2Alias("ウルトラ魔法少女まなな　ＶＯＬ１"));
        assertTrue(GameCatalog.INSTANCE.isManana5Alias("超级魔法少女玛娜娜 Vol.5"));
    }

    @Test
    public void wave3bStagingCleanupImportNamesAndModifierGuardsWork() throws Exception {
        File storage = tmp.newFolder("storage3b");
        File sDalk = new File(storage, ".online_base_staging_dalk");
        File sDalkG = new File(storage, ".online_base_staging_dalkgaiden");
        File sMaman = new File(storage, ".online_base_staging_maman");
        File sMamanyonyo = new File(storage, ".online_base_staging_mamanyonyo");
        File sEf = new File(storage, ".online_base_staging_erumeiflower");
        File sEw = new File(storage, ".online_base_staging_erumeiwakare");
        for (File f : Arrays.asList(sDalk, sDalkG, sMaman, sMamanyonyo, sEf, sEw)) {
            assertTrue(f.mkdirs());
        }

        OnlineInstaller.INSTANCE.cleanupBaseStagingForGame(storage, GameCatalog.ID_DALK, null);
        assertFalse(sDalk.exists());
        assertTrue(sDalkG.exists());
        OnlineInstaller.INSTANCE.cleanupBaseStagingForGame(storage, GameCatalog.ID_DALKGAIDEN, null);
        assertFalse(sDalkG.exists());

        OnlineInstaller.INSTANCE.cleanupBaseStagingForGame(storage, GameCatalog.ID_MAMAN, null);
        assertFalse(sMaman.exists());
        assertTrue(sMamanyonyo.exists());
        OnlineInstaller.INSTANCE.cleanupBaseStagingForGame(storage, GameCatalog.ID_MAMANYONYO, null);
        assertFalse(sMamanyonyo.exists());

        OnlineInstaller.INSTANCE.cleanupBaseStagingForGame(storage, GameCatalog.ID_ERUMEIFLOWER, null);
        assertFalse(sEf.exists());
        assertTrue(sEw.exists());
        OnlineInstaller.INSTANCE.cleanupBaseStagingForGame(storage, GameCatalog.ID_ERUMEIWAKARE, null);
        assertFalse(sEw.exists());

        assertEquals("DalkGaiden", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("System40.ini", "ＤＡＬＫ外伝.ain")));
        assertEquals("Mamanyonyo", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("System40.ini", "ままにょにょ.ain")));
        assertEquals("MajoNoShokuzai", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("System40.ini", "魔女の贖罪.ain")));
        assertEquals("Saboten", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("System40.ini", "よくばりサボテン.ain")));
        assertEquals("DoubleSensei", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("System40.ini", "だぶる先生らいふ.ain")));
        assertEquals("Ojousama", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("System40.ini", "お嬢様.ain")));
        assertEquals("Edoranze", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("System40.ini", "エドランゼ.ain")));
        assertEquals("Vanish", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("System40.ini", "ばにしゅ！.ain")));
        assertEquals("Hokenshitsu", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("AliceStart.ini", "僕だけの保健室.ain")));
        assertEquals("Kamunagara", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("System40.ini", "神武.ain")));
        assertEquals("Manana3", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("System40.ini", "まななＶＯＬ３.ain")));

        File hokenDir = tmp.newFolder("stage_hokenshitsu");
        assertTrue(new File(hokenDir, "AliceStart.ini").createNewFile());
        assertTrue(new File(hokenDir, "僕だけの保健室.ain").createNewFile());
        assertEquals("Hokenshitsu", OnlineInstaller.INSTANCE.resolveInstallDirBaseName(hokenDir, "Hokenshitsu"));
        assertTrue(GameModifierBridge.INSTANCE.isHokenshitsuGameDir(hokenDir));
        assertTrue(GameModifierBridge.INSTANCE.isNonModifierBuiltInGameDir(hokenDir));

        File ewStage = tmp.newFolder("stage_erumeiwakare");
        assertTrue(new File(ewStage, "System40.ini").createNewFile());
        assertTrue(new File(ewStage, "エルメイフラワー.ain").createNewFile());
        try (FileOutputStream out = new FileOutputStream(new File(ewStage, "エルメイフラワーWA.wai"))) {
            out.write("x".repeat(99).getBytes(StandardCharsets.UTF_8));
        }
        assertEquals("ErumeiWakare", OnlineInstaller.INSTANCE.resolveInstallDirBaseName(ewStage, "ErumeiWakare"));
        assertTrue(GameModifierBridge.INSTANCE.isErumeiWakareGameDir(ewStage));
        assertTrue(GameModifierBridge.INSTANCE.isNonModifierBuiltInGameDir(ewStage));
    }
}

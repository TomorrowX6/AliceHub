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
import java.util.Arrays;
import java.util.List;

/**
 * Unit tests for Wave 4 System 4 AliceSoft catalog additions (106 -> 127 built-in titles):
 * Wave 4A1 (tsumasakura, shamanssanctuary, alivez),
 * Wave 4B ALICE no Yakata 7 (dungeonsdolls, suzumiya, shimaima, daibanchougaiden, cosplaylemon,
 *   whiphoney, onimusu, progrx, dpsvern, sukiirono, girls, debut),
 * Wave 4C Alice 2010 (harukavsesca, harukaninja, megane, rachelsakura, sabakunika, vanishpalm).
 */
public class Wave4CatalogTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void wave4CatalogOrderDescriptorsAndMetadataAreWired() {
        List<String> order = GameCatalog.builtInSelectOrder();
        assertEquals(158, order.size());

        Object[][] specs = new Object[][]{
            {GameCatalog.ID_TSUMASAKURA, "TsumaSakura", OnlineInstaller.TSUMASAKURA_CDN_URL, 33875467L, "2010-07-14", 57.9, "https://vndb.org/v3974", "https://vndb.org/r7680"},
            {GameCatalog.ID_SHAMANSSANCTUARY, "ShamansSanctuary", OnlineInstaller.SHAMANSSANCTUARY_CDN_URL, 1464069450L, "2010-11-26", 65.5, "https://vndb.org/v5121", "https://vndb.org/v5121"},
            {GameCatalog.ID_ALIVEZ, "AliveZ", OnlineInstaller.ALIVEZ_CDN_URL, 3724684828L, "2008-09-26", 57.8, "https://vndb.org/v958", "https://vndb.org/v958"},
            {GameCatalog.ID_DUNGEONSDOLLS, "DungeonsDolls", OnlineInstaller.DUNGEONSDOLLS_CDN_URL, 790324801L, "2004-12-17", null, null, "https://www.alicesoft.com/"},
            {GameCatalog.ID_SUZUMIYA, "Suzumiya", OnlineInstaller.SUZUMIYA_CDN_URL, 694041858L, "2004-12-17", 65.9, "https://vndb.org/v2033", "https://vndb.org/v2033"},
            {GameCatalog.ID_SHIMAIMA, "Shimaima", OnlineInstaller.SHIMAIMA_CDN_URL, 959573915L, "2004-12-17", 61.0, "https://vndb.org/v2034", "https://vndb.org/v2034"},
            {GameCatalog.ID_DAIBANCHOUGAIDEN, "DaibanchouGaiden", OnlineInstaller.DAIBANCHOUGAIDEN_CDN_URL, 179008967L, "2004-12-17", null, null, "https://vndb.org/v30469"},
            {GameCatalog.ID_COSPLAYLEMON, "CosplayLemon", OnlineInstaller.COSPLAYLEMON_CDN_URL, 59713023L, "2004-12-17", null, null, "https://www.alicesoft.com/"},
            {GameCatalog.ID_WHIPHONEY, "WhipHoney", OnlineInstaller.WHIPHONEY_CDN_URL, 10895556L, "2004-12-17", null, null, "https://www.alicesoft.com/"},
            {GameCatalog.ID_ONIMUSU, "Onimusu", OnlineInstaller.ONIMUSU_CDN_URL, 45327000L, "2004-12-17", null, null, "https://www.alicesoft.com/"},
            {GameCatalog.ID_PROGRX, "ProGrx", OnlineInstaller.PROGRX_CDN_URL, 25585442L, "2004-12-17", null, null, "https://www.alicesoft.com/"},
            {GameCatalog.ID_DPSVERN, "DpsVerN", OnlineInstaller.DPSVERN_CDN_URL, 95418388L, "2004-12-17", null, null, "https://www.alicesoft.com/"},
            {GameCatalog.ID_SUKIIRONO, "Sukiirono", OnlineInstaller.SUKIIRONO_CDN_URL, 113630600L, "2004-12-17", null, null, "https://www.alicesoft.com/"},
            {GameCatalog.ID_GIRLS, "Girls", OnlineInstaller.GIRLS_CDN_URL, 135973798L, "2004-12-17", null, null, "https://www.alicesoft.com/"},
            {GameCatalog.ID_DEBUT, "Debut", OnlineInstaller.DEBUT_CDN_URL, 27789639L, "2004-12-17", null, null, "https://www.alicesoft.com/"},
            {GameCatalog.ID_HARUKAVSESCA, "HarukaVsEsca", OnlineInstaller.HARUKAVSESCA_CDN_URL, 667745059L, "2009-12-18", 64.0, "https://vndb.org/v3100", "https://vndb.org/v3100"},
            {GameCatalog.ID_HARUKANINJA, "HarukaNinja", OnlineInstaller.HARUKANINJA_CDN_URL, 213542665L, "2009-12-18", 60.3, "https://vndb.org/v3105", "https://vndb.org/v3105"},
            {GameCatalog.ID_MEGANE, "Megane", OnlineInstaller.MEGANE_CDN_URL, 44386500L, "2009-12-18", 51.8, "https://vndb.org/v3101", "https://vndb.org/v3101"},
            {GameCatalog.ID_RACHELSAKURA, "RachelSakura", OnlineInstaller.RACHELSAKURA_CDN_URL, 192743017L, "2009-12-18", 54.5, "https://vndb.org/v3102", "https://vndb.org/v3102"},
            {GameCatalog.ID_SABAKUNIKA, "SabakuNika", OnlineInstaller.SABAKUNIKA_CDN_URL, 253327874L, "2009-12-18", 58.5, "https://vndb.org/v3103", "https://vndb.org/v3103"},
            {GameCatalog.ID_VANISHPALM, "VanishPalm", OnlineInstaller.VANISHPALM_CDN_URL, 336117791L, "2009-12-18", 60.9, "https://vndb.org/v3099", "https://vndb.org/v3099"}
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
    public void wave4FileAndAliasClassificationAvoidsCollisions() {
        // System 4 file classification (including shared HarukaDLL.inc guard)
        assertEquals(GameCatalog.ID_TSUMASAKURA, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("AliceStart.ini", "妻たちの桜貝.ain", "妻たちの桜貝GA.ald")));
        assertEquals(GameCatalog.ID_SHAMANSSANCTUARY, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("AliceStart.ini", "まんくちゅ.ain", "まんくちゅGA.ald")));
        assertEquals(GameCatalog.ID_ALIVEZ, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("System40.ini", "ＡｌｉｖｅＺ.ain", "ＡｌｉｖｅＺGA.ald")));
        assertEquals(GameCatalog.ID_DUNGEONSDOLLS, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("System40.ini", "ドールズ.ain", "ドールズGA.ald")));
        assertEquals(GameCatalog.ID_SUZUMIYA, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("System40.ini", "鈴宮刑務娼館.ain", "鈴宮刑務娼館GA.ald")));
        assertEquals(GameCatalog.ID_SHIMAIMA, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("System40.ini", "しまいま.ain", "しまいまGA.ald")));
        assertEquals(GameCatalog.ID_DAIBANCHOUGAIDEN, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("System40.ini", "大番長外伝.ain", "大番長外伝GA.ald")));
        assertEquals(GameCatalog.ID_COSPLAYLEMON, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("System40.ini", "こすぷれれもん.ain", "こすぷれれもんGA.ald")));
        assertEquals(GameCatalog.ID_WHIPHONEY, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("System40.ini", "ほいっぷハニー.ain", "ほいっぷハニーGA.ald")));
        assertEquals(GameCatalog.ID_ONIMUSU, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("System40.ini", "オニむス.ain", "オニむスGA.ald")));
        assertEquals(GameCatalog.ID_PROGRX, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("System40.ini", "ぷろＧＲＸ.ain", "ぷろＧＲＸGA.ald")));
        assertEquals(GameCatalog.ID_DPSVERN, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("System40.ini", "でーぴーえすVer_N.ain", "でーぴーえすVer_NGA.ald")));
        assertEquals(GameCatalog.ID_SUKIIRONO, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("System40.ini", "透色.ain", "透色GA.ald")));
        assertEquals(GameCatalog.ID_GIRLS, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("System40.ini", "Ｇｉｒｌｓ.ain", "ＧｉｒｌｓGA.ald")));
        assertEquals(GameCatalog.ID_DEBUT, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("System40.ini", "ＤＥＢＵＴ.ain", "ＤＥＢＵＴGA.ald")));
        // HarukaDLL.inc is present in BOTH harukavsesca and harukaninja — must NOT misclassify as ID_HARUKA!
        assertEquals(GameCatalog.ID_HARUKAVSESCA, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("AliceStart.ini", "HarukaDLL.inc", "ハルカVS.ain", "ハルカVSGA.ald")));
        assertFalse(GameCatalog.INSTANCE.isHarukaGameFiles(
                Arrays.asList("AliceStart.ini", "HarukaDLL.inc", "ハルカVS.ain")));
        assertEquals(GameCatalog.ID_HARUKANINJA, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("AliceStart.ini", "HarukaDLL.inc", "疾風！？忍者大作戦.ain", "疾風！？忍者大作戦GA.ald")));
        assertFalse(GameCatalog.INSTANCE.isHarukaGameFiles(
                Arrays.asList("AliceStart.ini", "HarukaDLL.inc", "疾風！？忍者大作戦.ain")));
        assertEquals(GameCatalog.ID_HARUKA, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("System40.ini", "HarukaDLL.inc", "超昂閃忍ハルカ.ain")));
        assertEquals(GameCatalog.ID_MEGANE, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("AliceStart.ini", "眼鏡のメイと夏休み.ain", "眼鏡のメイと夏休みGA.ald")));
        assertEquals(GameCatalog.ID_RACHELSAKURA, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("AliceStart.ini", "レイチェルの桜貝.ain", "レイチェルの桜貝GA.ald")));
        assertEquals(GameCatalog.ID_SABAKUNIKA, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("AliceStart.ini", "砂漠のニッカ.ain", "砂漠のニッカGA.ald")));
        assertEquals(GameCatalog.ID_VANISHPALM, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("AliceStart.ini", "ばにしゅ！番外.ain", "ばにしゅ！番外GA.ald")));

        // Sibling alias collision guards
        assertEquals(GameCatalog.ID_DAIBANCHOUGAIDEN, GameCatalog.INSTANCE.classifyDirectory(null, "UnknownDir", null, "大番長外伝"));
        assertEquals(GameCatalog.ID_DAIBANCHOUGAIDEN, GameCatalog.INSTANCE.classifyDirectory(null, "DaibanchouGaiden", null, "大番长外传"));
        assertEquals(GameCatalog.ID_DAIBANCHOU, GameCatalog.INSTANCE.classifyDirectory(null, "Daibanchou", null, "大番長 -Big Bang Age-"));
        assertFalse(GameCatalog.INSTANCE.isDaibanchouAlias("大番長外伝"));
        assertFalse(GameCatalog.INSTANCE.isDaibanchouAlias("Daibanchou Gaiden"));

        assertEquals(GameCatalog.ID_HARUKAVSESCA, GameCatalog.INSTANCE.classifyDirectory(null, "UnknownDir", null, "超昂閃忍ハルカ ハルカVSエスカレイヤー"));
        assertEquals(GameCatalog.ID_HARUKAVSESCA, GameCatalog.INSTANCE.classifyDirectory(null, "HarukaVsEsca", null, "Choukou Sennin Haruka: Haruka VS Escalayer"));
        assertEquals(GameCatalog.ID_HARUKANINJA, GameCatalog.INSTANCE.classifyDirectory(null, "UnknownDir", null, "超昂閃忍ハルカ ―疾風！？忍者大作戦―"));
        assertEquals(GameCatalog.ID_HARUKA, GameCatalog.INSTANCE.classifyDirectory(null, "Haruka", null, "超昂閃忍ハルカ"));
        assertEquals(GameCatalog.ID_ESCALAYER, GameCatalog.INSTANCE.classifyDirectory(null, "Escalayer", null, "超昂天使エスカレイヤー"));
        assertFalse(GameCatalog.INSTANCE.isHarukaAlias("超昂閃忍ハルカ ハルカVSエスカレイヤー"));
        assertFalse(GameCatalog.INSTANCE.isHarukaAlias("超昂閃忍ハルカ ―疾風！？忍者大作戦―"));

        assertEquals(GameCatalog.ID_VANISHPALM, GameCatalog.INSTANCE.classifyDirectory(null, "UnknownDir", null, "ばにしゅ！～この手のひらにおっぱいを～"));
        assertEquals(GameCatalog.ID_VANISHPALM, GameCatalog.INSTANCE.classifyDirectory(null, "VanishPalm", null, "ばにしゅ！番外"));
        assertEquals(GameCatalog.ID_VANISH, GameCatalog.INSTANCE.classifyDirectory(null, "Vanish", null, "ばにしゅ！～おっぱいの消えた王国～"));
        assertFalse(GameCatalog.INSTANCE.isVanishAlias("ばにしゅ！～この手のひらにおっぱいを～"));
        assertFalse(GameCatalog.INSTANCE.isVanishAlias("ばにしゅ！番外"));

        assertEquals(GameCatalog.ID_TSUMASAKURA, GameCatalog.INSTANCE.classifyDirectory(null, "UnknownDir", null, "妻たちの桜貝"));
        assertEquals(GameCatalog.ID_RACHELSAKURA, GameCatalog.INSTANCE.classifyDirectory(null, "UnknownDir", null, "レイチェルの桜貝"));
        assertFalse(GameCatalog.INSTANCE.isTsumaSakuraAlias("レイチェルの桜貝"));
        assertFalse(GameCatalog.INSTANCE.isRachelSakuraAlias("妻たちの桜貝"));

        assertEquals(GameCatalog.ID_DPSVERN, GameCatalog.INSTANCE.classifyDirectory(null, "DpsVerN", null, "でーぴーえすVer_N"));
        assertEquals(GameCatalog.ID_PROGRX, GameCatalog.INSTANCE.classifyDirectory(null, "ProGrx", null, "ぷろGRX"));
        assertFalse(GameCatalog.INSTANCE.isProstudentGAlias("ぷろGRX"));
        assertFalse(GameCatalog.INSTANCE.isGirlsAlias("Schoolgirls"));
        assertFalse(GameCatalog.INSTANCE.isDebutAlias("Predebut"));
        assertTrue(GameCatalog.INSTANCE.isGirlsAlias("Girls"));
        assertTrue(GameCatalog.INSTANCE.isDebutAlias("DEBUT"));
    }

    @Test
    public void wave4StagingCleanupImportNamesAndModifierGuardsWork() throws Exception {
        File storage = tmp.newFolder("storage4");
        File sHaruka = new File(storage, ".online_base_staging_haruka");
        File sHarukaVs = new File(storage, ".online_base_staging_harukavsesca");
        File sHarukaNinja = new File(storage, ".online_base_staging_harukaninja");
        File sDaibanchou = new File(storage, ".online_base_staging_daibanchou");
        File sDaibanchouG = new File(storage, ".online_base_staging_daibanchougaiden");
        File sVanish = new File(storage, ".online_base_staging_vanish");
        File sVanishPalm = new File(storage, ".online_base_staging_vanishpalm");
        for (File f : Arrays.asList(sHaruka, sHarukaVs, sHarukaNinja, sDaibanchou, sDaibanchouG, sVanish, sVanishPalm)) {
            assertTrue(f.mkdirs());
        }

        OnlineInstaller.INSTANCE.cleanupBaseStagingForGame(storage, GameCatalog.ID_HARUKA, null);
        assertFalse(sHaruka.exists());
        assertTrue(sHarukaVs.exists());
        assertTrue(sHarukaNinja.exists());
        OnlineInstaller.INSTANCE.cleanupBaseStagingForGame(storage, GameCatalog.ID_HARUKAVSESCA, null);
        assertFalse(sHarukaVs.exists());
        assertTrue(sHarukaNinja.exists());
        OnlineInstaller.INSTANCE.cleanupBaseStagingForGame(storage, GameCatalog.ID_HARUKANINJA, null);
        assertFalse(sHarukaNinja.exists());

        OnlineInstaller.INSTANCE.cleanupBaseStagingForGame(storage, GameCatalog.ID_DAIBANCHOU, null);
        assertFalse(sDaibanchou.exists());
        assertTrue(sDaibanchouG.exists());
        OnlineInstaller.INSTANCE.cleanupBaseStagingForGame(storage, GameCatalog.ID_DAIBANCHOUGAIDEN, null);
        assertFalse(sDaibanchouG.exists());

        OnlineInstaller.INSTANCE.cleanupBaseStagingForGame(storage, GameCatalog.ID_VANISH, null);
        assertFalse(sVanish.exists());
        assertTrue(sVanishPalm.exists());
        OnlineInstaller.INSTANCE.cleanupBaseStagingForGame(storage, GameCatalog.ID_VANISHPALM, null);
        assertFalse(sVanishPalm.exists());

        assertEquals("TsumaSakura", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("AliceStart.ini", "妻たちの桜貝.ain")));
        assertEquals("ShamansSanctuary", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("AliceStart.ini", "まんくちゅ.ain")));
        assertEquals("AliveZ", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("System40.ini", "ＡｌｉｖｅＺ.ain")));
        assertEquals("DaibanchouGaiden", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("System40.ini", "大番長外伝.ain")));
        assertEquals("HarukaVsEsca", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("AliceStart.ini", "HarukaDLL.inc", "ハルカVS.ain")));
        assertEquals("HarukaNinja", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("AliceStart.ini", "HarukaDLL.inc", "疾風！？忍者大作戦.ain")));
        assertEquals("VanishPalm", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("AliceStart.ini", "ばにしゅ！番外.ain")));
        assertEquals("Girls", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("System40.ini", "Ｇｉｒｌｓ.ain")));
        assertEquals("Debut", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("System40.ini", "ＤＥＢＵＴ.ain")));

        File ssDir = tmp.newFolder("stage_shamanssanctuary");
        assertTrue(new File(ssDir, "AliceStart.ini").createNewFile());
        assertTrue(new File(ssDir, "まんくちゅ.ain").createNewFile());
        assertEquals("ShamansSanctuary", OnlineInstaller.INSTANCE.resolveInstallDirBaseName(ssDir, "ShamansSanctuary"));
        assertTrue(GameModifierBridge.INSTANCE.isShamansSanctuaryGameDir(ssDir));
        assertTrue(GameModifierBridge.INSTANCE.isNonModifierBuiltInGameDir(ssDir));

        File hveDir = tmp.newFolder("stage_harukavsesca");
        assertTrue(new File(hveDir, "AliceStart.ini").createNewFile());
        assertTrue(new File(hveDir, "HarukaDLL.inc").createNewFile());
        assertTrue(new File(hveDir, "ハルカVS.ain").createNewFile());
        assertEquals("HarukaVsEsca", OnlineInstaller.INSTANCE.resolveInstallDirBaseName(hveDir, "HarukaVsEsca"));
        assertTrue(GameModifierBridge.INSTANCE.isHarukaVsEscaGameDir(hveDir));
        assertTrue(GameModifierBridge.INSTANCE.isNonModifierBuiltInGameDir(hveDir));
    }
}

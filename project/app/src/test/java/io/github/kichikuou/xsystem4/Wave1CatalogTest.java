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
 * Unit tests for Wave 1 Chinese-patched AliceSoft additions:
 * - Evenicle Rance (eveniclerance)
 * - Haha Ranman (haharanman)
 * - Tsumamigui 2 (tsumamigui2)
 * - Tsumamigui 3 Spin-off: Pure Love (tsumamigui3jun)
 * - Tsumamigui 3 Spin-off: NTR (tsumamigui3ntr)
 */
public class Wave1CatalogTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void wave1CatalogOrderDescriptorsAndMetadataAreWired() {
        List<String> order = GameCatalog.builtInSelectOrder();
        assertEquals(58, order.size());
        assertTrue(order.contains(GameCatalog.ID_EVENICLERANCE));
        assertTrue(order.contains(GameCatalog.ID_HAHARANMAN));
        assertTrue(order.contains(GameCatalog.ID_TSUMAMIGUI2));
        assertTrue(order.contains(GameCatalog.ID_TSUMAMIGUI3JUN));
        assertTrue(order.contains(GameCatalog.ID_TSUMAMIGUI3NTR));

        GameDescriptor er = GameCatalog.INSTANCE.descriptorForWithResolver(
                GameCatalog.ID_EVENICLERANCE, null, id -> "Evenicle Rance");
        assertEquals("EvenicleRance", er.getDefaultDirName());
        assertNull(er.getExpectedAinName());
        assertEquals(OnlineInstaller.EVENICLERANCE_CDN_URL, er.getOnlineBaseCdnUrl());
        assertTrue(er.getSupportsOnlineBaseInstall());
        assertFalse(er.getSupportsOnlineMod());

        GameDescriptor hh = GameCatalog.INSTANCE.descriptorForWithResolver(
                GameCatalog.ID_HAHARANMAN, null, id -> "Haha Ranman");
        assertEquals("Haharanman", hh.getDefaultDirName());
        assertNull(hh.getExpectedAinName());
        assertEquals(OnlineInstaller.HAHARANMAN_CDN_URL, hh.getOnlineBaseCdnUrl());

        GameDescriptor t2 = GameCatalog.INSTANCE.descriptorForWithResolver(
                GameCatalog.ID_TSUMAMIGUI2, null, id -> "Tsumamigui 2");
        assertEquals("Tsumamigui2", t2.getDefaultDirName());
        assertNull(t2.getExpectedAinName());
        assertEquals(OnlineInstaller.TSUMAMIGUI2_CDN_URL, t2.getOnlineBaseCdnUrl());

        GameDescriptor t3j = GameCatalog.INSTANCE.descriptorForWithResolver(
                GameCatalog.ID_TSUMAMIGUI3JUN, null, id -> "Tsumamigui 3 Spin-off: Pure Love");
        assertEquals("Tsumamigui3Jun", t3j.getDefaultDirName());
        assertNull(t3j.getExpectedAinName());
        assertEquals(OnlineInstaller.TSUMAMIGUI3JUN_CDN_URL, t3j.getOnlineBaseCdnUrl());

        GameDescriptor t3n = GameCatalog.INSTANCE.descriptorForWithResolver(
                GameCatalog.ID_TSUMAMIGUI3NTR, null, id -> "Tsumamigui 3 Spin-off: NTR");
        assertEquals("Tsumamigui3Ntr", t3n.getDefaultDirName());
        assertNull(t3n.getExpectedAinName());
        assertEquals(OnlineInstaller.TSUMAMIGUI3NTR_CDN_URL, t3n.getOnlineBaseCdnUrl());

        assertEquals(397398161L, AppSettings.defaultBaseGameSizeForUrl(OnlineInstaller.EVENICLERANCE_CDN_URL));
        assertEquals(1231870701L, AppSettings.defaultBaseGameSizeForUrl(OnlineInstaller.HAHARANMAN_CDN_URL));
        assertEquals(760017305L, AppSettings.defaultBaseGameSizeForUrl(OnlineInstaller.TSUMAMIGUI2_CDN_URL));
        assertEquals(215949267L, AppSettings.defaultBaseGameSizeForUrl(OnlineInstaller.TSUMAMIGUI3JUN_CDN_URL));
        assertEquals(244591288L, AppSettings.defaultBaseGameSizeForUrl(OnlineInstaller.TSUMAMIGUI3NTR_CDN_URL));

        GameSortInfo erMeta = GameSortMetadata.INSTANCE.forGame(GameCatalog.ID_EVENICLERANCE);
        GameSortInfo hhMeta = GameSortMetadata.INSTANCE.forGame(GameCatalog.ID_HAHARANMAN);
        GameSortInfo t2Meta = GameSortMetadata.INSTANCE.forGame(GameCatalog.ID_TSUMAMIGUI2);
        GameSortInfo t3jMeta = GameSortMetadata.INSTANCE.forGame(GameCatalog.ID_TSUMAMIGUI3JUN);
        GameSortInfo t3nMeta = GameSortMetadata.INSTANCE.forGame(GameCatalog.ID_TSUMAMIGUI3NTR);
        assertNotNull(erMeta);
        assertEquals("2015-04-24", erMeta.getReleaseDate());
        assertEquals(Double.valueOf(67.4), erMeta.getRating());
        assertNotNull(hhMeta);
        assertEquals("2019-04-26", hhMeta.getReleaseDate());
        assertEquals(Double.valueOf(76.7), hhMeta.getRating());
        assertNotNull(t2Meta);
        assertEquals("2003-03-28", t2Meta.getReleaseDate());
        assertEquals(Double.valueOf(67.5), t2Meta.getRating());
        assertNotNull(t3jMeta);
        assertEquals("2016-06-24", t3jMeta.getReleaseDate());
        assertEquals(Double.valueOf(69.9), t3jMeta.getRating());
        assertNotNull(t3nMeta);
        assertEquals("2016-06-24", t3nMeta.getReleaseDate());
        assertEquals(Double.valueOf(69.9), t3nMeta.getRating());
    }

    @Test
    public void wave1FileAndAliasClassificationAvoidsCollisions() {
        // Evenicle family: EvenicleRance vs Evenicle vs Evenicle2Rance
        assertEquals(GameCatalog.ID_EVENICLERANCE, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("AliceStart.ini", "EvenicleRance.ain", "EvenicleRanceCG.afa")));
        assertEquals(GameCatalog.ID_EVENICLE, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("AliceStart.ini", "Evenicle.ain", "EvenicleCG.afa")));
        assertEquals(GameCatalog.ID_EVENICLE2RANCE, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("AliceStart.ini", "Evenicle2Rance.ain")));
        assertTrue(GameCatalog.INSTANCE.isEvenicleRanceAlias("夏娃年代记 兰斯版"));
        assertFalse(GameCatalog.INSTANCE.isEvenicleRanceAlias("夏娃年代记2 兰斯版"));
        assertEquals(GameCatalog.ID_EVENICLERANCE, GameCatalog.INSTANCE.classifyDirectory(
                null, "EvenicleRance", null, "夏娃年代记 兰斯版"));

        // Haharanman
        assertEquals(GameCatalog.ID_HAHARANMAN, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("AliceStart.ini", "Haharanman.ain", "HaharanmanFont.fnl")));
        assertTrue(GameCatalog.INSTANCE.isHaharanmanAlias("母烂漫"));
        assertTrue(GameCatalog.INSTANCE.isHaharanmanAlias("母爛漫"));
        assertEquals(GameCatalog.ID_HAHARANMAN, GameCatalog.INSTANCE.classifyDirectory(
                null, "Haharanman", null, "母烂漫"));

        // Tsumamigui family: 1 vs 2 vs 3 vs 3Jun vs 3Ntr (and OkusamaJun/Ntr)
        assertEquals(GameCatalog.ID_TSUMAMIGUI2, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("System39.ini", "System39.ain", "妻みぐい２SA.ALD", "妻みぐい２GA.ald")));
        assertEquals(GameCatalog.ID_TSUMAMIGUI, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("System39.ini", "System39.ain", "妻みぐいSA.ALD", "妻みぐいGA.ALD")));
        assertEquals(GameCatalog.ID_TSUMAMIGUI3JUN, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("AliceStart.ini", "Tsuma3SO_Junai.ain", "Tsuma3SO_JunaiFont.fnl")));
        assertEquals(GameCatalog.ID_TSUMAMIGUI3NTR, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("AliceStart.ini", "Tsuma3SO_Netorare.ain", "Tsuma3SO_NetorareFont.fnl")));
        assertEquals(GameCatalog.ID_TSUMAMIGUI3, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("AliceStart.ini", "Tsumamigui3.ain", "Tsumamigui3CG.afa")));

        assertTrue(GameCatalog.INSTANCE.isTsumamigui2Alias("妻中蜜2"));
        assertFalse(GameCatalog.INSTANCE.isTsumamiguiAlias("妻中蜜2"));
        assertTrue(GameCatalog.INSTANCE.isTsumamigui3JunAlias("妻中蜜3 番外 纯爱篇"));
        assertFalse(GameCatalog.INSTANCE.isTsumamigui3Alias("妻中蜜3 番外 纯爱篇"));
        assertFalse(GameCatalog.INSTANCE.isOkusamaJunAlias("妻中蜜3 番外 纯爱篇"));
        assertTrue(GameCatalog.INSTANCE.isTsumamigui3NtrAlias("妻中蜜3 番外 寝取篇"));
        assertFalse(GameCatalog.INSTANCE.isTsumamigui3Alias("妻中蜜3 番外 寝取篇"));
        assertFalse(GameCatalog.INSTANCE.isOkusamaNtrAlias("妻中蜜3 番外 寝取篇"));

        assertEquals(GameCatalog.ID_TSUMAMIGUI3JUN, GameCatalog.INSTANCE.classifyDirectory(
                null, "Tsumamigui3Jun", null, "妻中蜜3 番外 纯爱篇"));
        assertEquals(GameCatalog.ID_TSUMAMIGUI3NTR, GameCatalog.INSTANCE.classifyDirectory(
                null, "Tsumamigui3Ntr", null, "妻中蜜3 番外 寝取篇"));
        assertEquals(GameCatalog.ID_TSUMAMIGUI2, GameCatalog.INSTANCE.classifyDirectory(
                null, "Tsumamigui2", null, "妻中蜜2"));
    }

    @Test
    public void wave1InstallDirNamesAndModifierGuardsWork() throws Exception {
        File erDir = tmp.newFolder("stage_er");
        assertTrue(new File(erDir, "EvenicleRance.ain").createNewFile());
        assertEquals("EvenicleRance", OnlineInstaller.INSTANCE.resolveInstallDirBaseName(erDir, "EvenicleRance"));
        assertTrue(GameModifierBridge.INSTANCE.isEvenicleRanceGameDir(erDir));
        assertTrue(GameModifierBridge.INSTANCE.isNonModifierBuiltInGameDir(erDir));

        File hhDir = tmp.newFolder("stage_hh");
        assertTrue(new File(hhDir, "Haharanman.ain").createNewFile());
        assertEquals("Haharanman", OnlineInstaller.INSTANCE.resolveInstallDirBaseName(hhDir, "Haharanman"));
        assertTrue(GameModifierBridge.INSTANCE.isHaharanmanGameDir(hhDir));
        assertTrue(GameModifierBridge.INSTANCE.isNonModifierBuiltInGameDir(hhDir));

        File t2Dir = tmp.newFolder("stage_t2");
        assertTrue(new File(t2Dir, "System39.ain").createNewFile());
        assertTrue(new File(t2Dir, "妻みぐい２SA.ALD").createNewFile());
        assertEquals("Tsumamigui2", OnlineInstaller.INSTANCE.resolveInstallDirBaseName(t2Dir, "Tsumamigui2"));
        assertTrue(GameModifierBridge.INSTANCE.isTsumamigui2GameDir(t2Dir));
        assertTrue(GameModifierBridge.INSTANCE.isNonModifierBuiltInGameDir(t2Dir));

        File t3jDir = tmp.newFolder("stage_t3j");
        assertTrue(new File(t3jDir, "Tsuma3SO_Junai.ain").createNewFile());
        assertEquals("Tsumamigui3Jun", OnlineInstaller.INSTANCE.resolveInstallDirBaseName(t3jDir, "Tsumamigui3Jun"));
        assertTrue(GameModifierBridge.INSTANCE.isTsumamigui3JunGameDir(t3jDir));
        assertTrue(GameModifierBridge.INSTANCE.isNonModifierBuiltInGameDir(t3jDir));

        File t3nDir = tmp.newFolder("stage_t3n");
        assertTrue(new File(t3nDir, "Tsuma3SO_Netorare.ain").createNewFile());
        assertEquals("Tsumamigui3Ntr", OnlineInstaller.INSTANCE.resolveInstallDirBaseName(t3nDir, "Tsumamigui3Ntr"));
        assertTrue(GameModifierBridge.INSTANCE.isTsumamigui3NtrGameDir(t3nDir));
        assertTrue(GameModifierBridge.INSTANCE.isNonModifierBuiltInGameDir(t3nDir));

        // Verify staging cleanup for Evenicle doesn't delete EvenicleRance staging, and Tsumamigui3 doesn't delete spin-offs
        File storage = tmp.newFolder("storage");
        File evStage = new File(storage, ".online_base_staging_evenicle");
        File erStage = new File(storage, ".online_base_staging_eveniclerance");
        File t1Stage = new File(storage, ".online_base_staging_tsumamigui");
        File t2Stage = new File(storage, ".online_base_staging_tsumamigui2");
        File t3Stage = new File(storage, ".online_base_staging_tsumamigui3");
        File t3jStage = new File(storage, ".online_base_staging_tsumamigui3jun");
        File t3nStage = new File(storage, ".online_base_staging_tsumamigui3ntr");
        for (File f : Arrays.asList(evStage, erStage, t1Stage, t2Stage, t3Stage, t3jStage, t3nStage)) {
            assertTrue(f.mkdirs());
        }
        OnlineInstaller.INSTANCE.cleanupBaseStagingForGame(storage, GameCatalog.ID_EVENICLE, null);
        assertFalse(evStage.exists());
        assertTrue(erStage.exists());

        OnlineInstaller.INSTANCE.cleanupBaseStagingForGame(storage, GameCatalog.ID_TSUMAMIGUI, null);
        assertFalse(t1Stage.exists());
        assertTrue(t2Stage.exists());
        assertTrue(t3Stage.exists());
        assertTrue(t3jStage.exists());
        assertTrue(t3nStage.exists());

        OnlineInstaller.INSTANCE.cleanupBaseStagingForGame(storage, GameCatalog.ID_TSUMAMIGUI3, null);
        assertFalse(t3Stage.exists());
        assertTrue(t3jStage.exists());
        assertTrue(t3nStage.exists());

        assertEquals("Tsumamigui2", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("System39.ain", "妻みぐい２SA.ALD")));
        assertEquals("Tsumamigui3Jun", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("AliceStart.ini", "Tsuma3SO_Junai.ain")));
        assertEquals("Tsumamigui3Ntr", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("AliceStart.ini", "Tsuma3SO_Netorare.ain")));
        assertEquals("EvenicleRance", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("AliceStart.ini", "EvenicleRance.ain")));
        assertEquals("Haharanman", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("AliceStart.ini", "Haharanman.ain")));

        // Alternate recognized AIN filenames and companion assets must pass validateImportedGameDir
        // while opposite spin-off editions are still rejected.
        File altJun = tmp.newFolder("alt_jun");
        assertTrue(new File(altJun, "Tsumamigui3Jun.ain").createNewFile());
        assertTrue(GameCatalog.INSTANCE.validateImportedGameDir(altJun, null, GameCatalog.ID_TSUMAMIGUI3JUN));
        assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(altJun, null, GameCatalog.ID_TSUMAMIGUI3NTR));
        assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(altJun, null, GameCatalog.ID_TSUMAMIGUI3));

        File altNtr = tmp.newFolder("alt_ntr");
        assertTrue(new File(altNtr, "Tsumamigui3Ntr.ain").createNewFile());
        assertTrue(GameCatalog.INSTANCE.validateImportedGameDir(altNtr, null, GameCatalog.ID_TSUMAMIGUI3NTR));
        assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(altNtr, null, GameCatalog.ID_TSUMAMIGUI3JUN));

        File altEr = tmp.newFolder("alt_er");
        assertTrue(new File(altEr, "GameCN.ain").createNewFile());
        assertTrue(new File(altEr, "EvenicleRanceCG.afa").createNewFile());
        assertTrue(GameCatalog.INSTANCE.validateImportedGameDir(altEr, null, GameCatalog.ID_EVENICLERANCE));
        assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(altEr, null, GameCatalog.ID_EVENICLE));

        File altHh = tmp.newFolder("alt_hh");
        assertTrue(new File(altHh, "GameCN.ain").createNewFile());
        assertTrue(new File(altHh, "HaharanmanFont.fnl").createNewFile());
        assertTrue(GameCatalog.INSTANCE.validateImportedGameDir(altHh, null, GameCatalog.ID_HAHARANMAN));
        assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(altHh, null, GameCatalog.ID_MAMAN));
    }
}

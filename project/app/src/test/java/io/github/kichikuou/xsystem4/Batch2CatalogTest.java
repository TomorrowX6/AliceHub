package io.github.kichikuou.xsystem4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class Batch2CatalogTest {
    private static final Charset GBK = Charset.forName("GBK");

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

    private File makeDir(String folderName, String... fileNames) throws Exception {
        File dir = tmp.newFolder(folderName);
        for (String name : fileNames) {
            writeFile(new File(dir, name), new byte[]{1});
        }
        return dir;
    }

    @Test
    public void classifiesAllEightBatch2GamesAndIsolatesModifiers() throws Exception {
        File okusamaJun = makeDir("OkJun", "AliceStart.ini", "okusama_jun_cn.ain", "okusama_jun_cnCG.afa");
        File okusamaNtr = makeDir("OkNtr", "AliceStart.ini", "okusama_ntr_cn.ain", "okusama_ntr_cnCG.afa");
        File ev2Rance = makeDir("Ev2R", "AliceStart.ini", "Evenicle2Rance.ain", "Evenicle2RanceCHS.afa", "Evenicle2RanceCHS2.afa");
        File evGaiden = makeDir("EvG", "AliceStart.ini", "EvenicleApc.ain", "EvenicleApcCG.afa");
        File waidonyo = makeDir("Wdn", "AliceStart.ini", "waidonyo.ain", "waidonyoDLL.inc", "waidonyoGZ.ald");
        File r6After = makeDir("R6A", "System40.ini", "rance6after.ain", "rance6afterDLL.inc", "rance6afterGA.ald");
        File yakata2 = makeDir("Yk2", "ADISK.dat", "ACG.DAT", "system3.ini");
        writeFile(new File(yakata2, "system3.ini"), "[config]\ngame = yakata2\ntitle = 爱丽丝之馆２ -揍揍卷卷塔-\n".getBytes(StandardCharsets.UTF_8));
        File sdpsMaria = makeDir("Sdps", "ADISK.dat", "ACG.dat", "CGP.DPS", "system3.ini");
        writeFile(new File(sdpsMaria, "system3.ini"), "[config]\ngame = sdps_maria\ntitle = Super D.P.S - 玛利亚和坎潘\n".getBytes(StandardCharsets.UTF_8));

        File[] dirs = {okusamaJun, okusamaNtr, ev2Rance, evGaiden, waidonyo, r6After, yakata2, sdpsMaria};
        String[] expectedIds = {
                GameCatalog.ID_OKUSAMAJUN,
                GameCatalog.ID_OKUSAMANTR,
                GameCatalog.ID_EVENICLE2RANCE,
                GameCatalog.ID_EVENICLEGAIDEN,
                GameCatalog.ID_WAIDONYO,
                GameCatalog.ID_RANCE6AFTER,
                GameCatalog.ID_YAKATA2,
                GameCatalog.ID_SDPSMARIA
        };
        String[] expectedBaseNames = {
                "OkusamaJun", "OkusamaNtr", "Evenicle2Rance", "EvenicleGaiden",
                "Waidonyo", "Rance6After", "Yakata2", "SuperDpsMaria"
        };

        for (int i = 0; i < dirs.length; i++) {
            File dir = dirs[i];
            String id = expectedIds[i];
            assertEquals(id, GameCatalog.INSTANCE.classifyGameRootDir(dir));
            assertEquals(id, GameCatalog.INSTANCE.classifyDirectory(dir, "UnknownFolder", null, null));
            assertTrue(id, GameCatalog.INSTANCE.validateImportedGameDir(dir, null, id));
            assertFalse(id + " must not validate as Rance10",
                    GameCatalog.INSTANCE.validateImportedGameDir(dir, null, GameCatalog.ID_RANCE10));
            assertFalse(id + " must not validate as Evenicle",
                    GameCatalog.INSTANCE.validateImportedGameDir(dir, null, GameCatalog.ID_EVENICLE));
            assertFalse(id + " must not validate as Evenicle2",
                    GameCatalog.INSTANCE.validateImportedGameDir(dir, null, GameCatalog.ID_EVENICLE2));
            assertFalse(id + " must not validate as Rance06",
                    GameCatalog.INSTANCE.validateImportedGameDir(dir, null, GameCatalog.ID_RANCE06));
            assertEquals(expectedBaseNames[i], OnlineInstaller.INSTANCE.resolveInstallDirBaseName(dir, expectedBaseNames[i]));

            assertTrue(id + " must be non-modifier built-in", GameModifierBridge.INSTANCE.isNonModifierBuiltInGameDir(dir));
            assertFalse(id + " must not match Rance10 modifier", GameModifierBridge.INSTANCE.isRance10GameDir(dir, null, null));
            assertFalse(id + " must not match DohnaDohna modifier", GameModifierBridge.INSTANCE.isDohnaDohnaGameDir(dir, null, null));
            assertFalse(id + " must not match Evenicle modifier", GameModifierBridge.INSTANCE.isEvenicleGameDir(dir, null, null));
            assertFalse(id + " must not match Evenicle2 modifier", GameModifierBridge.INSTANCE.isEvenicle2GameDir(dir, null, null));
            assertFalse(id + " must not match Rance06 modifier", GameModifierBridge.INSTANCE.isRance06GameDir(dir, null, null));
            assertFalse(id + " must not match SengokuRance modifier", GameModifierBridge.INSTANCE.isSengokuRanceGameDir(dir, null, null));
            assertFalse(id + " must not match Rance03 modifier", GameModifierBridge.INSTANCE.isRance03GameDir(dir, null, null));
            assertFalse(id + " must not match Rance08 modifier", GameModifierBridge.INSTANCE.isRance08GameDir(dir, null, null));
            assertFalse(id + " must not match Rance09 modifier", GameModifierBridge.INSTANCE.isRance09GameDir(dir, null, null));
            assertFalse(id + " must not match Escalayer modifier", GameModifierBridge.INSTANCE.isEscalayerGameDir(dir, null, null));
        }

        // Legacy System 3 vs System 4 classification
        assertTrue(GameCatalog.INSTANCE.isLegacySystem3GameDir(yakata2));
        assertTrue(GameCatalog.INSTANCE.isLegacySystem3GameDir(sdpsMaria));
        assertFalse(GameCatalog.INSTANCE.isLegacySystem3GameDir(okusamaJun));
        assertFalse(GameCatalog.INSTANCE.isSystem3xGameDir(ev2Rance));
        assertFalse(GameCatalog.INSTANCE.isSystem3xGameDir(waidonyo));
        assertFalse(GameCatalog.INSTANCE.isSystem3xGameDir(r6After));

        // Specific modifier bridge helpers
        assertTrue(GameModifierBridge.INSTANCE.isOkusamaJunGameDir(okusamaJun));
        assertTrue(GameModifierBridge.INSTANCE.isOkusamaNtrGameDir(okusamaNtr));
        assertTrue(GameModifierBridge.INSTANCE.isEvenicle2RanceGameDir(ev2Rance));
        assertTrue(GameModifierBridge.INSTANCE.isEvenicleGaidenGameDir(evGaiden));
        assertTrue(GameModifierBridge.INSTANCE.isWaidonyoGameDir(waidonyo));
        assertTrue(GameModifierBridge.INSTANCE.isRance6AfterGameDir(r6After));
        assertTrue(GameModifierBridge.INSTANCE.isYakata2GameDir(yakata2));
        assertTrue(GameModifierBridge.INSTANCE.isSdpsMariaGameDir(sdpsMaria));
    }

    @Test
    public void disambiguatesAliasesFilesStagingAndImportNames() throws Exception {
        // Evenicle2Rance / EvenicleGaiden vs Evenicle2 / Evenicle
        assertTrue(GameCatalog.INSTANCE.isEvenicle2RanceAlias("夏娃年代记2 兰斯版"));
        assertTrue(GameCatalog.INSTANCE.isEvenicle2RanceAlias("イブニクル２ランス版"));
        assertTrue(GameCatalog.INSTANCE.isEvenicle2RanceAlias("Evenicle2Rance"));
        assertFalse(GameCatalog.INSTANCE.isEvenicle2Alias("evenicle2rance"));
        assertFalse(GameCatalog.INSTANCE.isEvenicle2Alias("夏娃年代记2 兰斯版"));
        assertFalse(GameCatalog.INSTANCE.isEvenicle2Alias("イブニクル２ランス版"));
        assertTrue(GameCatalog.INSTANCE.isEvenicle2Alias("夏娃年代记2"));
        assertEquals(GameCatalog.ID_EVENICLE2RANCE, GameCatalog.INSTANCE.classifyDirectory(null, "Evenicle2Rance", null, null));
        assertEquals(GameCatalog.ID_EVENICLE2RANCE, GameCatalog.INSTANCE.classifyDirectory(null, "Unknown", null, "夏娃年代记2 兰斯版"));
        assertEquals(GameCatalog.ID_EVENICLEGAIDEN, GameCatalog.INSTANCE.classifyDirectory(null, "EvenicleGaiden", null, null));
        assertEquals(GameCatalog.ID_EVENICLEGAIDEN, GameCatalog.INSTANCE.classifyDirectory(null, "Unknown", null, "夏娃年代记外典"));
        assertEquals(GameCatalog.ID_EVENICLE2, GameCatalog.INSTANCE.classifyDirectory(null, "Evenicle2", null, null));
        assertEquals(GameCatalog.ID_EVENICLE, GameCatalog.INSTANCE.classifyDirectory(null, "Evenicle", null, null));

        // Rance6After vs Rance06 (including rance6afterGA.ald!)
        assertTrue(GameCatalog.INSTANCE.isRance6AfterAlias("兰斯06 后日谈"));
        assertTrue(GameCatalog.INSTANCE.isRance6AfterAlias("兰斯6后日谈"));
        assertTrue(GameCatalog.INSTANCE.isRance6AfterAlias("ランス６その後"));
        assertTrue(GameCatalog.INSTANCE.isRance6AfterAlias("Rance VI: Aftermath"));
        assertFalse(GameCatalog.INSTANCE.isRance06Alias("兰斯06 后日谈"));
        assertFalse(GameCatalog.INSTANCE.isRance06Alias("ランス６その後"));
        assertFalse(GameCatalog.INSTANCE.isRance06Alias("Rance6After"));
        assertTrue(GameCatalog.INSTANCE.isRance06Alias("兰斯06 赛斯崩坏"));
        assertFalse(GameCatalog.INSTANCE.isRance06GameFiles(Arrays.asList("System40.ini", "rance6after.ain", "rance6afterGA.ald")));
        assertFalse(GameCatalog.INSTANCE.isRance06GameFiles(Arrays.asList("rance6afterGA.ald")));
        assertEquals(GameCatalog.ID_RANCE6AFTER, GameCatalog.INSTANCE.classifyDirectory(null, "Rance6After", null, null));
        assertEquals(GameCatalog.ID_RANCE6AFTER, GameCatalog.INSTANCE.classifyDirectory(null, "Unknown", null, "【哈尼喵汉化组】兰斯6后日谈"));
        assertEquals(GameCatalog.ID_RANCE06, GameCatalog.INSTANCE.classifyDirectory(null, "Rance06", null, null));

        // OkusamaJun vs OkusamaNtr
        assertTrue(GameCatalog.INSTANCE.isOkusamaJunAlias("太太的恢复术_纯爱篇"));
        assertFalse(GameCatalog.INSTANCE.isOkusamaNtrAlias("太太的恢复术_纯爱篇"));
        assertTrue(GameCatalog.INSTANCE.isOkusamaNtrAlias("太太的恢复术_夺爱篇"));
        assertFalse(GameCatalog.INSTANCE.isOkusamaJunAlias("太太的恢复术_夺爱篇"));
        assertEquals(GameCatalog.ID_OKUSAMAJUN, GameCatalog.INSTANCE.classifyDirectory(null, "OkusamaJun", null, null));
        assertEquals(GameCatalog.ID_OKUSAMANTR, GameCatalog.INSTANCE.classifyDirectory(null, "OkusamaNtr", null, null));

        // Waidonyo / Yakata2 / Super D.P.S Maria
        assertEquals(GameCatalog.ID_WAIDONYO, GameCatalog.INSTANCE.classifyDirectory(null, "异空窟探险", null, null));
        assertEquals(GameCatalog.ID_YAKATA2, GameCatalog.INSTANCE.classifyDirectory(null, "爱丽丝之馆2", null, null));
        assertEquals(GameCatalog.ID_SDPSMARIA, GameCatalog.INSTANCE.classifyDirectory(null, "Super D.P.S 玛利亚和坎潘", null, null));

        // NativeGameImport canonical folder names
        assertEquals("OkusamaJun", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("AliceStart.ini", "okusama_jun_cn.ain"), false));
        assertEquals("OkusamaNtr", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("AliceStart.ini", "okusama_ntr_cn.ain"), false));
        assertEquals("Evenicle2Rance", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("AliceStart.ini", "Evenicle2Rance.ain"), false));
        assertEquals("EvenicleGaiden", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("AliceStart.ini", "EvenicleApc.ain"), false));
        assertEquals("Waidonyo", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("AliceStart.ini", "waidonyo.ain"), false));
        assertEquals("Rance6After", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("System40.ini", "rance6after.ain", "rance6afterGA.ald"), false));

        // INI-based fallback for renamed .ain
        File renamedWaidonyo = tmp.newFolder("RenamedWaidonyo");
        writeFile(new File(renamedWaidonyo, "AliceStart.ini"), "GameName = \"异空窟探险中文版v1.00\"\n".getBytes(GBK));
        writeFile(new File(renamedWaidonyo, "game.ain"), new byte[]{1});
        assertEquals(GameCatalog.ID_WAIDONYO, GameCatalog.INSTANCE.classifyGameRootDir(renamedWaidonyo));

        // Staging eviction isolation between Evenicle / Evenicle2 / Evenicle2Rance / EvenicleGaiden and Rance06 / Rance6After
        File storage = tmp.newFolder("storage");
        File evSt = new File(storage, ".online_base_staging_Evenicle");
        File ev2St = new File(storage, ".online_base_staging_Evenicle2");
        File ev2rSt = new File(storage, ".online_base_staging_Evenicle2Rance");
        File evgSt = new File(storage, ".online_base_staging_EvenicleGaiden");
        File r6St = new File(storage, ".online_base_staging_Rance06");
        File r6aSt = new File(storage, ".online_base_staging_Rance6After");
        for (File f : new File[]{evSt, ev2St, ev2rSt, evgSt, r6St, r6aSt}) {
            assertTrue(f.mkdirs());
        }
        List<File> evictedEv = OnlineInstaller.INSTANCE.evictBaseStagingToTrash(storage, GameCatalog.ID_EVENICLE, null);
        assertEquals(1, evictedEv.size());
        assertFalse(evSt.exists());
        assertTrue(ev2St.exists());
        assertTrue(ev2rSt.exists());
        assertTrue(evgSt.exists());

        List<File> evictedEv2 = OnlineInstaller.INSTANCE.evictBaseStagingToTrash(storage, GameCatalog.ID_EVENICLE2, null);
        assertEquals(1, evictedEv2.size());
        assertFalse(ev2St.exists());
        assertTrue(ev2rSt.exists());
        assertTrue(evgSt.exists());

        List<File> evictedR6 = OnlineInstaller.INSTANCE.evictBaseStagingToTrash(storage, GameCatalog.ID_RANCE06, null);
        assertEquals(1, evictedR6.size());
        assertFalse(r6St.exists());
        assertTrue(r6aSt.exists());
    }

    @Test
    public void exposesDescriptorsCdnUrlsDefaultSizesAndVndbSnapshotParity() throws Exception {
        assertEquals(171, GameCatalog.builtInSelectOrder().size());

        Object[][] spec = {
                {GameCatalog.ID_OKUSAMAJUN, R.string.game_okusamajun_title, "OkusamaJun", R.drawable.ic_game_okusamajun,
                        OnlineInstaller.OKUSAMAJUN_CDN_URL, 433754405L, "2022-05-27", 67.3, "https://vndb.org/v35407"},
                {GameCatalog.ID_OKUSAMANTR, R.string.game_okusamantr_title, "OkusamaNtr", R.drawable.ic_game_okusamantr,
                        OnlineInstaller.OKUSAMANTR_CDN_URL, 407324160L, "2022-05-27", 67.3, "https://vndb.org/v35407"},
                {GameCatalog.ID_EVENICLE2RANCE, R.string.game_evenicle2rance_title, "Evenicle2Rance", R.drawable.ic_game_evenicle2rance,
                        OnlineInstaller.EVENICLE2RANCE_CDN_URL, 675181447L, "2019-02-22", 71.1, "https://vndb.org/v25513"},
                {GameCatalog.ID_EVENICLEGAIDEN, R.string.game_eveniclegaiden_title, "EvenicleGaiden", R.drawable.ic_game_eveniclegaiden,
                        OnlineInstaller.EVENICLEGAIDEN_CDN_URL, 344150541L, "2015-06-11", 65.3, "https://vndb.org/v20540"},
                {GameCatalog.ID_WAIDONYO, R.string.game_waidonyo_title, "Waidonyo", R.drawable.ic_game_waidonyo,
                        OnlineInstaller.WAIDONYO_CDN_URL, 554965092L, "2009-12-18", null, null},
                {GameCatalog.ID_RANCE6AFTER, R.string.game_rance6after_title, "Rance6After", R.drawable.ic_game_rance6after,
                        OnlineInstaller.RANCE6AFTER_CDN_URL, 38024313L, "2004-12-17", 63.0, "https://vndb.org/v29842"},
                {GameCatalog.ID_YAKATA2, R.string.game_yakata2_title, "Yakata2", R.drawable.ic_game_yakata2,
                        OnlineInstaller.YAKATA2_CDN_URL, 14072286L, "1992-07-15", 58.2, "https://vndb.org/v9609"},
                {GameCatalog.ID_SDPSMARIA, R.string.game_sdpsmaria_title, "SuperDpsMaria", R.drawable.ic_game_sdpsmaria,
                        OnlineInstaller.SDPSMARIA_CDN_URL, 11185724L, "1992-09-01", 61.2, "https://vndb.org/v8670"},
        };

        File snapshotFile = new File("../docs/game-sort-vndb-snapshot.json");
        if (!snapshotFile.isFile()) snapshotFile = new File("../../docs/game-sort-vndb-snapshot.json");
        String snapshotText = new String(Files.readAllBytes(snapshotFile.toPath()), StandardCharsets.UTF_8);
        Map<String, Double> snapshotRatings = new HashMap<>();
        Matcher rowMatcher = Pattern.compile("\\{[^}]*\"id\"\\s*:\\s*\"(v\\d+)\"[^}]*\\}").matcher(snapshotText);
        Pattern ratingPattern = Pattern.compile("\"rating\"\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)");
        while (rowMatcher.find()) {
            String vndbId = rowMatcher.group(1);
            Matcher rm = ratingPattern.matcher(rowMatcher.group(0));
            if (rm.find()) {
                snapshotRatings.put("https://vndb.org/" + vndbId, Double.parseDouble(rm.group(1)));
            }
        }

        for (Object[] row : spec) {
            String id = (String) row[0];
            int titleRes = (Integer) row[1];
            String dirName = (String) row[2];
            int iconRes = (Integer) row[3];
            String cdnUrl = (String) row[4];
            long sizeBytes = (Long) row[5];
            String releaseDate = (String) row[6];
            Double rating = (Double) row[7];
            String vndbUrl = (String) row[8];

            GameDescriptor desc = GameCatalog.INSTANCE.descriptorForWithResolver(id, null, r -> "res:" + r);
            assertEquals(id, "res:" + titleRes, desc.getTitle());
            assertEquals(id, dirName, desc.getDefaultDirName());
            assertEquals(id, iconRes, desc.getIconRes());
            assertTrue(id, desc.getSupportsOnlineBaseInstall());
            assertEquals(id, cdnUrl, desc.getOnlineBaseCdnUrl());
            assertFalse(id, desc.getSupportsOnlineMod());
            assertFalse(id, desc.getSupportsLocalMod());
            assertEquals(id, sizeBytes, AppSettings.defaultBaseGameSizeForUrl(cdnUrl + "/"));

            GameSortInfo sortInfo = GameSortMetadata.INSTANCE.forGame(id);
            assertNotNull(id, sortInfo);
            assertEquals(id, releaseDate, sortInfo.getReleaseDate());
            if (rating == null) {
                assertNull(id, sortInfo.getRating());
                assertNull(id, sortInfo.getScoreSourceUrl());
            } else {
                assertEquals(id, rating, sortInfo.getRating(), 0.0001);
                assertEquals(id, vndbUrl, sortInfo.getScoreSourceUrl());
                assertEquals(id, snapshotRatings.get(vndbUrl), sortInfo.getRating());
            }
        }
    }
}

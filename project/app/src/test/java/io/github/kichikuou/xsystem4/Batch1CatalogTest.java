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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class Batch1CatalogTest {
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
    public void classifiesAllSevenBatch1GamesAndIsolatesModifiers() throws Exception {
        File daibanchou = makeDir("Dbc", "System40.ini", "大番長.ain", "大番長GA.ald");
        File daiteikoku = makeDir("Dtk", "AliceStart.ini", "大帝国.ain", "大帝国CG.afa");
        File tsumamigui = makeDir("Tsm", "System39.ini", "System39.ain", "妻みぐいSA.ald", "妻みぐいGA.ald");
        File kaerunyo = makeDir("Krn", "ぱにょSA.ALD", "ぱにょGA.ALD", "FLOOR_1.MAP", "FLOOR_33.MAP");
        File atlachnacha = makeDir("Atl", "System39.ini", "CHS.TBL", "蜘蛛SA_CHS.ALD", "蜘蛛GA_CHS.ALD");
        File reishiki = makeDir("Rei", "零式SA.ALD", "零式GA.ALD", "MAP_DATA.DAT", "UR_DATA.DAT");
        File haruurare = makeDir("Haru", "AliceStart.ini", "AliceStart2.ini", "haru_c.ain", "はるうられGA.ald");

        File[][] cases = {
                {daibanchou}, {daiteikoku}, {tsumamigui}, {kaerunyo}, {atlachnacha}, {reishiki}, {haruurare}
        };
        String[] expectedIds = {
                GameCatalog.ID_DAIBANCHOU,
                GameCatalog.ID_DAITEIKOKU,
                GameCatalog.ID_TSUMAMIGUI,
                GameCatalog.ID_KAERUNYO,
                GameCatalog.ID_ATLACHNACHA,
                GameCatalog.ID_REISHIKI,
                GameCatalog.ID_HARUURARE
        };
        String[] expectedBaseNames = {
                "Daibanchou", "Daiteikoku", "Tsumamigui", "Kaerunyo", "AtlachNacha", "Reishiki", "Haruurare"
        };

        for (int i = 0; i < cases.length; i++) {
            File dir = cases[i][0];
            String id = expectedIds[i];
            assertEquals(id, GameCatalog.INSTANCE.classifyGameRootDir(dir));
            assertEquals(id, GameCatalog.INSTANCE.classifyDirectory(dir, "UnknownFolder", null, null));
            assertTrue(id, GameCatalog.INSTANCE.validateImportedGameDir(dir, null, id));
            assertFalse(id + " should not validate as Rance10",
                    GameCatalog.INSTANCE.validateImportedGameDir(dir, null, GameCatalog.ID_RANCE10));
            assertEquals(expectedBaseNames[i], OnlineInstaller.INSTANCE.resolveInstallDirBaseName(dir, expectedBaseNames[i]));

            assertTrue(id + " must be non-modifier built-in", GameModifierBridge.INSTANCE.isNonModifierBuiltInGameDir(dir));
            assertFalse(id + " must not match Rance10 modifier", GameModifierBridge.INSTANCE.isRance10GameDir(dir, null, null));
            assertFalse(id + " must not match DohnaDohna modifier", GameModifierBridge.INSTANCE.isDohnaDohnaGameDir(dir, null, null));
            assertFalse(id + " must not match Evenicle modifier", GameModifierBridge.INSTANCE.isEvenicleGameDir(dir, null, null));
            assertFalse(id + " must not match SengokuRance modifier", GameModifierBridge.INSTANCE.isSengokuRanceGameDir(dir, null, null));
            assertFalse(id + " must not match Rance03 modifier", GameModifierBridge.INSTANCE.isRance03GameDir(dir, null, null));
            assertFalse(id + " must not match Rance08 modifier", GameModifierBridge.INSTANCE.isRance08GameDir(dir, null, null));
            assertFalse(id + " must not match Rance09 modifier", GameModifierBridge.INSTANCE.isRance09GameDir(dir, null, null));
            assertFalse(id + " must not match Escalayer modifier", GameModifierBridge.INSTANCE.isEscalayerGameDir(dir, null, null));
        }

        // System 3.x vs System 4.x distinction
        assertFalse(GameCatalog.INSTANCE.isSystem3xGameDir(daibanchou));
        assertFalse(GameCatalog.INSTANCE.isSystem3xGameDir(daiteikoku));
        assertFalse(GameCatalog.INSTANCE.isSystem3xGameDir(haruurare));
        assertTrue(GameCatalog.INSTANCE.isSystem3xGameDir(tsumamigui));
        assertTrue(GameCatalog.INSTANCE.isSystem3xGameDir(kaerunyo));
        assertTrue(GameCatalog.INSTANCE.isSystem3xGameDir(atlachnacha));
        assertTrue(GameCatalog.INSTANCE.isSystem3xGameDir(reishiki));
        assertTrue(GameCatalog.INSTANCE.isSystem3xGameFiles(Collections.singletonList("蜘蛛SA_CHS.ALD")));

        // Specific modifier bridge helpers
        assertTrue(GameModifierBridge.INSTANCE.isDaibanchouGameDir(daibanchou));
        assertTrue(GameModifierBridge.INSTANCE.isDaiteikokuGameDir(daiteikoku));
        assertTrue(GameModifierBridge.INSTANCE.isTsumamiguiGameDir(tsumamigui));
        assertTrue(GameModifierBridge.INSTANCE.isKaerunyoGameDir(kaerunyo));
        assertTrue(GameModifierBridge.INSTANCE.isAtlachnachaGameDir(atlachnacha));
        assertTrue(GameModifierBridge.INSTANCE.isReishikiGameDir(reishiki));
        assertTrue(GameModifierBridge.INSTANCE.isHaruurareGameDir(haruurare));
    }

    @Test
    public void handlesIniFallbacksMojibakeAndDisambiguation() throws Exception {
        // Haruurare with AliceStart2.ini-only layout (no AliceStart.ini / System40.ini) must pass
        // folder classification, extracted archive root detection, and folder discovery equally.
        File haruArchiveStaging = tmp.newFolder("HaruArchiveStaging");
        File haruIni2 = new File(haruArchiveStaging, "HaruurareCN");
        writeFile(new File(haruIni2, "AliceStart2.ini"), "GameName = \"校内赤线区\"\nCodeName = \"haru_c.ain\"\n".getBytes(GBK));
        writeFile(new File(haruIni2, "haru_c.ain"), new byte[]{1});
        assertEquals(GameCatalog.ID_HARUURARE, GameCatalog.INSTANCE.classifyGameRootDir(haruIni2));
        assertTrue(GameListKt.isSystem4GameRoot(haruIni2));
        assertEquals(haruIni2, GameListKt.findGameRootDirInternal(haruArchiveStaging, null, GameCatalog.ID_HARUURARE));

        // Atlach=Nacha archive with only *_CHS.ALD scenario must also pass archive root detection.
        File atlArchiveStaging = tmp.newFolder("AtlArchiveStaging");
        File atlRoot = new File(atlArchiveStaging, "AtlachCN");
        writeFile(new File(atlRoot, "蜘蛛SA_CHS.ALD"), new byte[]{1});
        writeFile(new File(atlRoot, "CHS.TBL"), new byte[]{1});
        assertTrue(GameListKt.isSystem3GameRoot(atlRoot));
        assertEquals(atlRoot, GameListKt.findGameRootDirInternal(atlArchiveStaging, null, GameCatalog.ID_ATLACHNACHA));

        // Case-insensitive alicestart2.ini must win over AliceStart.ini when haru_c.ain is present,
        // and exact-case AliceStart2.ini must win when both case variants coexist.
        File haruDualLower = tmp.newFolder("HaruDualLower");
        writeFile(new File(haruDualLower, "AliceStart.ini"), "GameName = \"はるうられ\"\n".getBytes(GBK));
        writeFile(new File(haruDualLower, "alicestart2.ini"), "GameName = \"校内赤线区\"\n".getBytes(GBK));
        writeFile(new File(haruDualLower, "haru_c.ain"), new byte[]{1});
        assertEquals("alicestart2.ini", GameListKt.resolveLaunchIniFile(haruDualLower).getName());
        writeFile(new File(haruDualLower, "AliceStart2.ini"), "GameName = \"校内赤线区\"\n".getBytes(GBK));
        assertEquals("AliceStart2.ini", GameListKt.resolveLaunchIniFile(haruDualLower).getName());

        // Outer AliceStart2.ini without an .ain must NOT override a complete InstallSet/InstallData root.
        File outerOnlyIni = tmp.newFolder("OuterOnlyIni");
        writeFile(new File(outerOnlyIni, "AliceStart2.ini"), "GameName = \"校内赤线区\"\n".getBytes(GBK));
        File innerInstallData = new File(outerOnlyIni, "InstallSet/InstallData");
        writeFile(new File(innerInstallData, "AliceStart.ini"), "GameName = \"はるうられ\"\n".getBytes(GBK));
        writeFile(new File(innerInstallData, "haru_c.ain"), new byte[]{1});
        assertEquals(innerInstallData, GameListKt.preferCnRootOverInstallData(innerInstallData));
        // Once outer root has an actual launchable .ain, preferCnRootOverInstallData switches to outer root.
        writeFile(new File(outerOnlyIni, "haru_c.ain"), new byte[]{1});
        assertEquals(outerOnlyIni, GameListKt.preferCnRootOverInstallData(innerInstallData));

        // Companion disc merge: SA_CHS.ALD + base SA.ALD share scenario identity and merge assets,
        // while a genuinely different game's scenario disc is still skipped.
        File multiDiscRoot = tmp.newFolder("MultiDiscChs");
        File disc1 = new File(multiDiscRoot, "Disc1/GAMEDATA");
        File disc2 = new File(multiDiscRoot, "Disc2/GAMEDATA");
        File discOther = new File(multiDiscRoot, "Disc3/GAMEDATA");
        writeFile(new File(disc1, "蜘蛛SA_CHS.ALD"), new byte[]{1});
        writeFile(new File(disc1, "CHS.TBL"), new byte[]{1});
        writeFile(new File(disc2, "蜘蛛SA.ALD"), new byte[]{2});
        writeFile(new File(disc2, "蜘蛛GA.ALD"), new byte[]{3});
        writeFile(new File(discOther, "otherSA.ALD"), new byte[]{4});
        writeFile(new File(discOther, "otherGA.ALD"), new byte[]{5});
        GameListKt.mergeSiblingMultiDiscDirsInternal(multiDiscRoot, disc1, () -> kotlin.Unit.INSTANCE);
        assertTrue(new File(disc1, "蜘蛛SA_CHS.ALD").isFile());
        assertTrue(new File(disc1, "蜘蛛SA.ALD").isFile());
        assertTrue(new File(disc1, "蜘蛛GA.ALD").isFile());
        assertFalse(new File(disc1, "otherGA.ALD").exists());

        // NativeGameImport canonical base names including *_CHS.ALD
        assertEquals("Daibanchou", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("System40.ini", "大番長.ain"), false));
        assertEquals("Daiteikoku", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("AliceStart.ini", "大帝国.ain"), false));
        assertEquals("Haruurare", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("AliceStart2.ini", "haru_c.ain"), false));
        assertEquals("Tsumamigui", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("System39.ini", "妻みぐいSA.ald"), false));
        assertEquals("Kaerunyo", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("ぱにょSA.ALD", "FLOOR_1.MAP", "FLOOR_33.MAP"), false));
        assertEquals("AtlachNacha", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("System39.ini", "蜘蛛SA_CHS.ALD"), false));
        assertEquals("Reishiki", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("零式SA.ALD", "MAP_DATA.DAT", "UR_DATA.DAT"), false));

        // Disambiguate Tsumamigui 1 vs 2 vs 3 vs Tsumashibori
        assertTrue(GameCatalog.INSTANCE.isTsumamiguiAlias("妻中蜜"));
        assertTrue(GameCatalog.INSTANCE.isTsumamiguiAlias("Tsumamigui"));
        assertFalse(GameCatalog.INSTANCE.isTsumamiguiAlias("妻中蜜3"));
        assertFalse(GameCatalog.INSTANCE.isTsumamiguiAlias("妻みぐい３"));
        assertFalse(GameCatalog.INSTANCE.isTsumamiguiAlias("妻みぐい2"));
        assertFalse(GameCatalog.INSTANCE.isTsumamiguiAlias("Tsumamigui2"));
        assertFalse(GameCatalog.INSTANCE.isTsumamiguiAlias("Tsumamigui III"));
        assertFalse(GameCatalog.INSTANCE.isTsumamiguiAlias("妻しぼり"));
        assertEquals(GameCatalog.ID_TSUMAMIGUI, GameCatalog.INSTANCE.classifyDirectory(null, "妻中蜜", null, null));
        assertEquals(GameCatalog.ID_TSUMAMIGUI3, GameCatalog.INSTANCE.classifyDirectory(null, "妻中蜜3", null, null));
        assertNotEquals(GameCatalog.ID_TSUMAMIGUI, GameCatalog.INSTANCE.classifyDirectory(null, "妻みぐい2", null, null));

        // Disambiguate Reishiki vs generic "zero"
        assertTrue(GameCatalog.INSTANCE.isReishikiAlias("零式"));
        assertTrue(GameCatalog.INSTANCE.isReishikiAlias("Reishiki"));
        assertTrue(GameCatalog.INSTANCE.isReishikiAlias("TYPE_ZERO"));
        assertFalse(GameCatalog.INSTANCE.isReishikiAlias("zero"));
        assertFalse(GameCatalog.INSTANCE.isReishikiAlias("Rance01"));

        // Disambiguate Haruurare vs Haruka
        assertTrue(GameCatalog.INSTANCE.isHaruurareAlias("校内赤线区"));
        assertTrue(GameCatalog.INSTANCE.isHaruurareAlias("はるうられ"));
        assertFalse(GameCatalog.INSTANCE.isHaruurareAlias("超昂闪忍遥"));
        assertFalse(GameCatalog.INSTANCE.isHarukaAlias("校内赤线区"));
    }

    @Test
    public void exposesDescriptorsCdnUrlsDefaultSizesAndVndbSnapshotParity() throws Exception {
        assertEquals(39, GameCatalog.builtInSelectOrder().size());

        Object[][] spec = {
                {GameCatalog.ID_DAIBANCHOU, R.string.game_daibanchou_title, "Daibanchou", R.drawable.ic_game_daibanchou,
                        OnlineInstaller.DAIBANCHOU_CDN_URL, 1895126876L, "2003-12-19", 75.0, "https://vndb.org/v299"},
                {GameCatalog.ID_DAITEIKOKU, R.string.game_daiteikoku_title, "Daiteikoku", R.drawable.ic_game_daiteikoku,
                        OnlineInstaller.DAITEIKOKU_CDN_URL, 2416202457L, "2011-04-28", 77.0, "https://vndb.org/v3066"},
                {GameCatalog.ID_TSUMAMIGUI, R.string.game_tsumamigui_title, "Tsumamigui", R.drawable.ic_game_tsumamigui,
                        OnlineInstaller.TSUMAMIGUI_CDN_URL, 1400592703L, "2002-03-15", 66.4, "https://vndb.org/v1125"},
                {GameCatalog.ID_KAERUNYO, R.string.game_kaerunyo_title, "Kaerunyo", R.drawable.ic_game_kaerunyo,
                        OnlineInstaller.KAERUNYO_CDN_URL, 105838567L, "1997-08-29", 56.4, "https://vndb.org/v1841"},
                {GameCatalog.ID_ATLACHNACHA, R.string.game_atlachnacha_title, "AtlachNacha", R.drawable.ic_game_atlachnacha,
                        OnlineInstaller.ATLACHNACHA_CDN_URL, 148510731L, "1997-12-18", 76.5, "https://vndb.org/v163"},
                {GameCatalog.ID_REISHIKI, R.string.game_reishiki_title, "Reishiki", R.drawable.ic_game_reishiki,
                        OnlineInstaller.REISHIKI_CDN_URL, 88875020L, "1997-12-18", 65.6, "https://vndb.org/v1826"},
                {GameCatalog.ID_HARUURARE, R.string.game_haruurare_title, "Haruurare", R.drawable.ic_game_haruurare,
                        OnlineInstaller.HARUURARE_CDN_URL, 264968018L, "2009-12-18", 65.3, "https://vndb.org/v3096"},
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
            double rating = (Double) row[7];
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
            assertEquals(id, rating, sortInfo.getRating(), 0.0001);
            assertEquals(id, vndbUrl, sortInfo.getScoreSourceUrl());
            assertEquals(id, snapshotRatings.get(vndbUrl), sortInfo.getRating());
        }
    }
}

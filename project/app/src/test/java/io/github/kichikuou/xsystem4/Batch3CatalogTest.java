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
import static org.junit.Assert.assertTrue;

public class Batch3CatalogTest {
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
    public void classifiesAllFourBatch3GamesAndIsolatesModifiers() throws Exception {
        File toushin3 = makeDir("T3", "System40.ini", "斗神都市3.ain", "闘神都市３DLL.inc", "闘神都市３GM.ald");
        File oyakorankan = makeDir("Oya", "AliceStart.CHS", "母娘乱館.CHS", "CHS.TBL", "CHS.afa", "母娘乱館CG.afa");
        File yorugakuru = makeDir("Ygk", "AliceStart.ini", "nightRemaster.ain", "NightRemasterCG.afa", "NightRemasterDict.dat");
        File rance1Digest = makeDir("R1D", "System40.ini", "Rance1.ain", "Rance1GA.ald", "RANCE1BA.ald");

        File[] dirs = {toushin3, oyakorankan, yorugakuru, rance1Digest};
        String[] expectedIds = {
                GameCatalog.ID_TOUSHIN3,
                GameCatalog.ID_OYAKORANKAN,
                GameCatalog.ID_YORUGAKURU,
                GameCatalog.ID_RANCE1DIGEST
        };
        String[] expectedBaseNames = {
                "Toushin3", "Oyakorankan", "Yorugakuru", "Rance1Digest"
        };

        for (int i = 0; i < dirs.length; i++) {
            File dir = dirs[i];
            String id = expectedIds[i];
            assertTrue(id + " must be recognized as System 4 root", GameListKt.isSystem4GameRoot(dir));
            assertFalse(id + " must not be classified as System 3.x", GameCatalog.INSTANCE.isSystem3xGameDir(dir));
            assertEquals(id, GameCatalog.INSTANCE.classifyGameRootDir(dir));
            assertEquals(id, GameCatalog.INSTANCE.classifyDirectory(dir, "UnknownFolder", null, null));
            assertTrue(id, GameCatalog.INSTANCE.validateImportedGameDir(dir, null, id));
            assertFalse(id + " must not validate as Rance10",
                    GameCatalog.INSTANCE.validateImportedGameDir(dir, null, GameCatalog.ID_RANCE10));
            assertFalse(id + " must not validate as Rance01",
                    GameCatalog.INSTANCE.validateImportedGameDir(dir, null, GameCatalog.ID_RANCE01));
            assertEquals(expectedBaseNames[i], OnlineInstaller.INSTANCE.resolveInstallDirBaseName(dir, expectedBaseNames[i]));

            assertTrue(id + " must be non-modifier built-in", GameModifierBridge.INSTANCE.isNonModifierBuiltInGameDir(dir));
            assertFalse(id + " must not match Rance10 modifier", GameModifierBridge.INSTANCE.isRance10GameDir(dir, null, null));
            assertFalse(id + " must not match Rance01 modifier", GameModifierBridge.INSTANCE.isRance01GameDir(dir, null, null));
            assertFalse(id + " must not match DohnaDohna modifier", GameModifierBridge.INSTANCE.isDohnaDohnaGameDir(dir, null, null));
            assertFalse(id + " must not match Evenicle modifier", GameModifierBridge.INSTANCE.isEvenicleGameDir(dir, null, null));
            assertFalse(id + " must not match Evenicle2 modifier", GameModifierBridge.INSTANCE.isEvenicle2GameDir(dir, null, null));
        }

        assertTrue(GameModifierBridge.INSTANCE.isToushin3GameDir(toushin3));
        assertTrue(GameModifierBridge.INSTANCE.isOyakorankanGameDir(oyakorankan));
        assertTrue(GameModifierBridge.INSTANCE.isYorugakuruGameDir(yorugakuru));
        assertTrue(GameModifierBridge.INSTANCE.isRance1DigestGameDir(rance1Digest));
    }

    @Test
    public void prefersAliceStartChsWhenChsTblPresentAndDisambiguatesRance1Digest() throws Exception {
        // Longinus Oyakorankan: when both AliceStart.ini and AliceStart.CHS + CHS.TBL exist, prefer AliceStart.CHS.
        File oyaDual = makeDir("OyaDual", "AliceStart.ini", "AliceStart.CHS", "CHS.TBL", "母娘乱館.ain", "母娘乱館.CHS");
        assertEquals("AliceStart.CHS", GameListKt.resolveLaunchIniFile(oyaDual).getName());

        // Clean online Oyakorankan package with only AliceStart.CHS + 母娘乱館.CHS + CHS.TBL.
        File oyaClean = makeDir("OyaClean", "AliceStart.CHS", "CHS.TBL", "母娘乱館.CHS");
        assertEquals("AliceStart.CHS", GameListKt.resolveLaunchIniFile(oyaClean).getName());
        assertTrue(GameListKt.isSystem4GameRoot(oyaClean));
        assertEquals(GameCatalog.ID_OYAKORANKAN, GameCatalog.INSTANCE.classifyGameRootDir(oyaClean));

        // Without CHS.TBL, AliceStart.ini remains preferred over AliceStart.CHS.
        File noTbl = makeDir("NoTbl", "AliceStart.ini", "AliceStart.CHS", "game.ain");
        assertEquals("AliceStart.ini", GameListKt.resolveLaunchIniFile(noTbl).getName());

        // Auxiliary .chs files without AliceStart.CHS/CHS.TBL or INI CodeName must not shadow .ain or be treated as code.
        File auxChsOnly = makeDir("AuxChsOnly", "System40.ini", "00_aux.chs");
        writeFile(new File(auxChsOnly, "System40.ini"), "CodeName = \"Other.ain\"\n".getBytes(StandardCharsets.UTF_8));
        assertFalse(GameListKt.isSystem4GameRoot(auxChsOnly));
        assertEquals("Game", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("00_aux.chs", "Game.ain"), false));

        // Standard INI explicitly configuring CodeName = "CustomGame.CHS" must be accepted by dir, staging, and SAF discovery.
        File configuredChs = makeDir("ConfiguredChs", "System40.ini", "CustomGame.CHS");
        byte[] configuredIniBytes = "CodeName = \"CustomGame.CHS\"\nGameName = \"CustomGame\"\n".getBytes(StandardCharsets.UTF_8);
        writeFile(new File(configuredChs, "System40.ini"), configuredIniBytes);
        assertTrue(GameListKt.isSystem4GameRoot(configuredChs));
        assertEquals("CustomGame", OnlineInstaller.INSTANCE.resolveInstallDirBaseName(configuredChs, ""));
        List<NativeGameImport.TreeEntry<String, byte[]>> safEntries = Arrays.asList(
                new NativeGameImport.TreeEntry<>("ini", "System40.ini", false, configuredIniBytes),
                new NativeGameImport.TreeEntry<>("chs", "CustomGame.CHS", false, new byte[]{1})
        );
        NativeGameImport.DiscoveredImportSources<String, byte[]> discoveredChs =
                NativeGameImport.INSTANCE.discoverImportSources("root", safEntries, id -> Arrays.asList(), e -> e.getPayload());
        assertNotNull(discoveredChs.getPrimaryEntries());
        List<NativeGameImport.TreeEntry<String, byte[]>> auxSafEntries = Arrays.asList(
                new NativeGameImport.TreeEntry<>("ini", "System40.ini", false, "CodeName = \"Other.ain\"\n".getBytes(StandardCharsets.UTF_8)),
                new NativeGameImport.TreeEntry<>("chs", "00_aux.chs", false, new byte[]{1})
        );
        NativeGameImport.DiscoveredImportSources<String, byte[]> rejectedAux =
                NativeGameImport.INSTANCE.discoverImportSources("root", auxSafEntries, id -> Arrays.asList(), e -> e.getPayload());
        assertEquals(null, rejectedAux.getPrimaryEntries());

        // UTF-8 CodeName with ASCII GameName (both with and without BOM), plus legacy GBK & Shift_JIS retention.
        File utf8Chs = makeDir("Utf8Chs", "System40.ini", "游戏.CHS");
        byte[] utf8IniBytes = "GameName = \"Game\"\nCodeName = \"游戏.CHS\"\n".getBytes(StandardCharsets.UTF_8);
        writeFile(new File(utf8Chs, "System40.ini"), utf8IniBytes);
        System40Ini parsedUtf8 = System40Ini.Companion.parse(utf8IniBytes);
        assertEquals("Game", parsedUtf8.getGameName());
        assertEquals("游戏.CHS", parsedUtf8.getCodeName());
        assertTrue(GameListKt.isSystem4GameRoot(utf8Chs));
        List<NativeGameImport.TreeEntry<String, byte[]>> utf8SafEntries = Arrays.asList(
                new NativeGameImport.TreeEntry<>("ini", "System40.ini", false, utf8IniBytes),
                new NativeGameImport.TreeEntry<>("chs", "游戏.CHS", false, new byte[]{1})
        );
        assertNotNull(NativeGameImport.INSTANCE.discoverImportSources(
                "root", utf8SafEntries, id -> Arrays.asList(), e -> e.getPayload()).getPrimaryEntries());

        byte[] bomPrefix = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] bomIniBytes = new byte[bomPrefix.length + utf8IniBytes.length];
        System.arraycopy(bomPrefix, 0, bomIniBytes, 0, bomPrefix.length);
        System.arraycopy(utf8IniBytes, 0, bomIniBytes, bomPrefix.length, utf8IniBytes.length);
        assertEquals("游戏.CHS", System40Ini.Companion.parse(bomIniBytes).getCodeName());

        byte[] gbkIniBytes = "GameName = \"魔\"\nCodeName = \"游戏.CHS\"\n".getBytes(GBK);
        System40Ini parsedGbk = System40Ini.Companion.parse(gbkIniBytes);
        assertEquals("魔", parsedGbk.getGameName());
        assertEquals("游戏.CHS", parsedGbk.getCodeName());

        Charset sjis = Charset.forName("Shift_JIS");
        byte[] sjisIniBytes = "GameName = \"Game\"\nCodeName = \"ランス.CHS\"\n".getBytes(sjis);
        System40Ini parsedSjis = System40Ini.Companion.parse(sjisIniBytes);
        assertEquals("Game", parsedSjis.getGameName());
        assertEquals("ランス.CHS", parsedSjis.getCodeName());

        // Inline ';' and '//' trailing comments (and comment markers inside quotes) must match native ini_lexer.l.
        byte[] semiCommentIni = (
                "; CodeName = \"Ignored.CHS\"\n" +
                "// CodeName = \"AlsoIgnored.CHS\"\n" +
                "CodeName = \"CustomGame.CHS\" ; Chinese \"build\"\n"
        ).getBytes(StandardCharsets.UTF_8);
        assertEquals("CustomGame.CHS", System40Ini.Companion.parse(semiCommentIni).getCodeName());
        byte[] slashCommentIni = "CodeName = \"a;b//c.CHS\" // Chinese build\n".getBytes(StandardCharsets.UTF_8);
        assertEquals("a;b//c.CHS", System40Ini.Companion.parse(slashCommentIni).getCodeName());

        File commentedChs = makeDir("CommentedChs", "System40.ini", "CustomGame.CHS");
        writeFile(new File(commentedChs, "System40.ini"), semiCommentIni);
        assertTrue(GameListKt.isSystem4GameRoot(commentedChs));
        List<NativeGameImport.TreeEntry<String, byte[]>> semiSafEntries = Arrays.asList(
                new NativeGameImport.TreeEntry<>("ini", "System40.ini", false, semiCommentIni),
                new NativeGameImport.TreeEntry<>("chs", "CustomGame.CHS", false, new byte[]{1})
        );
        assertNotNull(NativeGameImport.INSTANCE.discoverImportSources(
                "root", semiSafEntries, id -> Arrays.asList(), e -> e.getPayload()).getPrimaryEntries());

        // Untitled configured .CHS game falls back to the resolved code filename instead of failing library validation.
        assertEquals("CustomGame", Item.Companion.resolveFallbackSystem4Name(
                commentedChs, System40Ini.Companion.parse(semiCommentIni)));
        File untitledMissingCode = makeDir("UntitledMissingCode", "System40.ini", "00_aux.chs");
        byte[] missingCodeIni = "CodeName = \"Other.CHS\"\n".getBytes(StandardCharsets.UTF_8);
        writeFile(new File(untitledMissingCode, "System40.ini"), missingCodeIni);
        assertEquals(null, Item.Companion.resolveFallbackSystem4Name(
                untitledMissingCode, System40Ini.Companion.parse(missingCodeIni)));

        // Rance 1 Digest vs Rance 01 disambiguation
        assertTrue(GameCatalog.INSTANCE.isRance1DigestAlias("兰斯1 摘要版"));
        assertTrue(GameCatalog.INSTANCE.isRance1DigestAlias("兰斯１摘要版"));
        assertTrue(GameCatalog.INSTANCE.isRance1DigestAlias("Rance -光をもとめて- Digest"));
        assertEquals(GameCatalog.ID_RANCE1DIGEST, GameCatalog.INSTANCE.classifyDirectory(null, "Rance1Digest", null, null));
        assertEquals(GameCatalog.ID_RANCE1DIGEST, GameCatalog.INSTANCE.classifyDirectory(null, "兰斯１摘要版", null, null));
        assertEquals(GameCatalog.ID_RANCE1DIGEST, GameCatalog.INSTANCE.classifyDirectory(null, "Unknown", null, "兰斯1 摘要版"));
        assertEquals(GameCatalog.ID_RANCE01, GameCatalog.INSTANCE.classifyDirectory(null, "Rance01", null, null));
        assertEquals(GameCatalog.ID_RANCE01, GameCatalog.INSTANCE.classifyDirectory(null, "Unknown", null, "兰斯01 寻找小光"));

        // Aliases & NativeGameImport canonical folder names
        assertEquals(GameCatalog.ID_TOUSHIN3, GameCatalog.INSTANCE.classifyDirectory(null, "斗神都市3", null, null));
        assertEquals(GameCatalog.ID_OYAKORANKAN, GameCatalog.INSTANCE.classifyDirectory(null, "母娘乱馆", null, null));
        assertEquals(GameCatalog.ID_YORUGAKURU, GameCatalog.INSTANCE.classifyDirectory(null, "邪夜将至 Remastered", null, null));

        assertEquals("Toushin3", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("System40.ini", "斗神都市3.ain"), false));
        assertEquals("Oyakorankan", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("AliceStart.CHS", "CHS.TBL", "母娘乱館.CHS"), false));
        assertEquals("Yorugakuru", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("AliceStart.ini", "nightRemaster.ain"), false));
        assertEquals("Rance1Digest", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("System40.ini", "Rance1.ain"), false));

        // INI fallback for renamed .ain
        File renamedYgk = tmp.newFolder("RenamedYgk");
        writeFile(new File(renamedYgk, "AliceStart.ini"),
                "GameName = \"邪夜将至！-Square of the MOON- Remastered\"\n".getBytes(GBK));
        writeFile(new File(renamedYgk, "game.ain"), new byte[]{1});
        assertEquals(GameCatalog.ID_YORUGAKURU, GameCatalog.INSTANCE.classifyGameRootDir(renamedYgk));

        // Staging eviction isolation between Rance01 and Rance1Digest
        File storage = tmp.newFolder("storage");
        File r01St = new File(storage, ".online_base_staging_Rance01");
        File r1dSt = new File(storage, ".online_base_staging_Rance1Digest");
        assertTrue(r01St.mkdirs());
        assertTrue(r1dSt.mkdirs());
        List<File> evictedR01 = OnlineInstaller.INSTANCE.evictBaseStagingToTrash(storage, GameCatalog.ID_RANCE01, null);
        assertEquals(1, evictedR01.size());
        assertFalse(r01St.exists());
        assertTrue(r1dSt.exists());
    }

    @Test
    public void exposesDescriptorsCdnUrlsDefaultSizesAndVndbSnapshotParity() throws Exception {
        assertEquals(127, GameCatalog.builtInSelectOrder().size());

        Object[][] spec = {
                {GameCatalog.ID_TOUSHIN3, R.string.game_toushin3_title, "Toushin3", R.drawable.ic_game_toushin3,
                        OnlineInstaller.TOUSHIN3_CDN_URL, 3406621760L, "2008-11-28", 74.4, "https://vndb.org/v849"},
                {GameCatalog.ID_OYAKORANKAN, R.string.game_oyakorankan_title, "Oyakorankan", R.drawable.ic_game_oyakorankan,
                        OnlineInstaller.OYAKORANKAN_CDN_URL, 2961641642L, "2012-09-14", 78.1, "https://vndb.org/v10732"},
                {GameCatalog.ID_YORUGAKURU, R.string.game_yorugakuru_title, "Yorugakuru", R.drawable.ic_game_yorugakuru,
                        OnlineInstaller.YORUGAKURU_CDN_URL, 1565514386L, "2024-04-19", 66.9, "https://vndb.org/v499"},
                {GameCatalog.ID_RANCE1DIGEST, R.string.game_rance1digest_title, "Rance1Digest", R.drawable.ic_game_rance1digest,
                        OnlineInstaller.RANCE1DIGEST_CDN_URL, 11945316L, "2004-08-27", 54.4, "https://vndb.org/v1414"},
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
            assertEquals(id, rating, sortInfo.getRating(), 0.0001);
            assertEquals(id, vndbUrl, sortInfo.getScoreSourceUrl());
            assertEquals(id, snapshotRatings.get(vndbUrl), sortInfo.getRating());
        }
    }
}

package io.github.kichikuou.xsystem4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Pure-JVM regression tests covering English, Traditional Chinese, European (CP1252 / 2-byte UTF-8),
 * Russian (Cyrillic UTF-8), and partially translated game import, classification, mojibake repair,
 * INI parsing, boot entry discovery, ISO scanning, and Chinese badge detection.
 */
public class MultiLangImportAndBadgeTest {
    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    private static final Charset CP1252 = Charset.forName("windows-1252");
    private static final Charset SJIS = Charset.forName("Shift_JIS");
    private static final Charset GBK = Charset.forName("GBK");

    private static void writeFile(File file, byte[] bytes) throws Exception {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            assertTrue(parent.mkdirs());
        }
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(bytes);
        }
    }

    @Test
    public void decodeBestCjkTextHandlesCp1252EuropeanFilenamesWithoutMojibake() {
        String[] cp1252Names = new String[]{
                "Rance\u2019s Quest.txt",
                "Sc\u00e8ne.txt",
                "Caf\u00e9.txt",
                "Rance IX \u2013 Patch.txt",
                "Rance\u2122 Guide.txt",
                "Gro\u00dfe Stra\u00dfe.txt"
        };
        for (String name : cp1252Names) {
            byte[] raw = name.getBytes(CP1252);
            assertEquals("CP1252 decode mismatch for " + name,
                    name, ArchiveExtractor.INSTANCE.decodeBestCjkText(raw, true));
            assertEquals("decodeEntryPath mismatch for " + name,
                    name, ArchiveExtractor.INSTANCE.decodeEntryPath(raw));
        }
        // Genuine Shift-JIS and GBK still decode properly
        assertEquals("\u6226\u56fd\u30e9\u30f3\u30b9.ain",
                ArchiveExtractor.INSTANCE.decodeEntryPath("\u6226\u56fd\u30e9\u30f3\u30b9.ain".getBytes(SJIS)));
        assertEquals("\u5170\u65af10\u6c49\u5316.ain",
                ArchiveExtractor.INSTANCE.decodeEntryPath("\u5170\u65af10\u6c49\u5316.ain".getBytes(GBK)));
    }

    @Test
    public void repairSjisAsGbkMojibakePreservesTraditionalChineseAndRepairsRealMojibake() {
        // Real Traditional Chinese filenames MUST stay intact!
        String[] tcNames = new String[]{
                "\u5abd\u5abd.ain",       // 媽媽.ain
                "\u5c0d\u6230.ini",       // 對戰.ini
                "\u5be6\u6230.ini",       // 實戰.ini
                "\u6416\u687f.txt",       // 搖桿.txt
                "\u5bf6\u528d.ain",       // 寶劍.ain
                "\u5169\u5f35.ain",       // 兩張.ain
                "\u59b3\u5011.ain",       // 妳們.ain
                "\u7e41\u9ad4\u4e2d\u6587\u7248.ain" // 繁體中文版.ain
        };
        for (String tc : tcNames) {
            assertEquals("Traditional Chinese must NOT be corrupted: " + tc,
                    tc, ArchiveExtractor.INSTANCE.repairSjisAsGbkMojibake(tc));
        }

        // Genuine Shift-JIS-as-GBK mojibake MUST still be repaired!
        assertEquals("\u6226\u56fd\u30e9\u30f3\u30b9_Sys.ASD",
                ArchiveExtractor.INSTANCE.repairSjisAsGbkMojibake("\u6134\u5d19\u5114\u511e\u50d7_Sys.ASD"));
        assertEquals("\u30e9\u30f3\u30b9\u30fb\u30af\u30a8\u30b9\u30c8.ain",
                ArchiveExtractor.INSTANCE.repairSjisAsGbkMojibake("\u5114\u511e\u50d7\u4e12\u50cb\u50c4\u50d7\u50e9.ain"));
        assertEquals("\u59bb\u3057\u307c\u308a.ain",
                ArchiveExtractor.INSTANCE.repairSjisAsGbkMojibake("\u5d62\u505f\u508f\u509d.ain"));
        assertEquals("\u738b\u5b50_M1.asd",
                ArchiveExtractor.INSTANCE.repairSjisAsGbkMojibake("\u58b9\u5dd5_M1.asd"));
        assertEquals("\u540d\u524d.ASD",
                ArchiveExtractor.INSTANCE.repairSjisAsGbkMojibake("\u67e4\u615c.ASD"));
    }

    @Test
    public void system40IniParses2ByteUtf8Cp1252AndCaseInsensitiveUnquotedCodeName() {
        // BOM-less 2-byte UTF-8 European & Russian titles
        byte[] frUtf8 = "GameName = \"\u00c9dition Fran\u00e7aise Caf\u00e9\"\nCodeName = Rance10_FR.ain\n".getBytes(StandardCharsets.UTF_8);
        System40Ini parsedFr = System40Ini.Companion.parse(frUtf8);
        assertEquals("\u00c9dition Fran\u00e7aise Caf\u00e9", parsedFr.getGameName());
        assertEquals("Rance10_FR.ain", parsedFr.getCodeName());

        byte[] ruUtf8 = "GameName = \"\u0420\u0430\u043d\u0441 10\"\ncodename = Rance10_RU.ain\n".getBytes(StandardCharsets.UTF_8);
        System40Ini parsedRu = System40Ini.Companion.parse(ruUtf8);
        assertEquals("\u0420\u0430\u043d\u0441 10", parsedRu.getGameName());
        assertEquals("Rance10_RU.ain", parsedRu.getCodeName());

        // CP1252 GameName
        byte[] cp1252Ini = "GameName = \"Rance\u2019s Caf\u00e9\"\nCodeName = \"Rance6.ain\"\n".getBytes(CP1252);
        System40Ini parsedCp = System40Ini.Companion.parse(cp1252Ini);
        assertEquals("Rance\u2019s Caf\u00e9", parsedCp.getGameName());
        assertEquals("Rance6.ain", parsedCp.getCodeName());

        // System4BootEntry extracts lowercase and unquoted CodeName
        byte[] unquotedIni = "gamename = \"Rance X\"\ncodename = Rance10_EN.ain ; fan patch\n".getBytes(StandardCharsets.UTF_8);
        List<String> extracted = System4BootEntry.extractCodeNames(unquotedIni);
        assertTrue("Expected Rance10_EN.ain in " + extracted, extracted.contains("Rance10_EN.ain"));
    }

    @Test
    public void baseInstallFiltersPreserveXsys4RcAndPostPatchArchives() {
        assertFalse(".xsys4rc must NOT be skipped", NativeGameImport.INSTANCE.skip(".xsys4rc"));
        assertFalse(".xsys35rc must NOT be skipped", NativeGameImport.INSTANCE.skip(".xsys35rc"));
        assertFalse("PostPatch.zip must NOT be skipped by OST filter",
                GameListKt.shouldSkipBaseInstallEntryInternal("PostPatch.zip"));
        assertFalse("Lost_Edition.7z must NOT be skipped by OST filter",
                GameListKt.shouldSkipBaseInstallEntryInternal("Lost_Edition.7z"));
        assertFalse("Ghost_EN.zip must NOT be skipped by OST filter",
                GameListKt.shouldSkipBaseInstallEntryInternal("Ghost_EN.zip"));
        assertTrue("Rance10_OST.zip MUST be skipped",
                GameListKt.shouldSkipBaseInstallEntryInternal("Rance10_OST.zip"));
        assertTrue("Game (OST).7z MUST be skipped",
                GameListKt.shouldSkipBaseInstallEntryInternal("Game (OST).7z"));
    }

    @Test
    public void classifiesLocalizedEnglishJapaneseAndAliceCnGamesWithoutHijack() throws Exception {
        // 1. AliceCN.ain inside Tsumashibori / Daibanchou must NOT hijack to Sengoku Rance
        assertEquals(GameCatalog.ID_TSUMASHIBORI, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("System40.ini", "AliceCN.ain", "\u59bb\u3057\u307c\u308aGA.ald")));
        assertEquals(GameCatalog.ID_DAIBANCHOU, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("System40.ini", "AliceCN.ain", "\u5927\u756a\u9577GA.ald")));
        // Bare AliceCN.ain still falls back to Sengoku Rance
        assertEquals(GameCatalog.ID_SENGOKU_RANCE, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("System40.ini", "AliceCN.ain")));

        // 2. MangaGamer / English System 4 editions
        assertEquals(GameCatalog.ID_RANCE09, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("AliceStart.ini", "RanceIX.ain")));
        assertEquals(GameCatalog.ID_RANCE08, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("AliceStart.ini", "RanceQuestMagnum.ain")));
        assertEquals(GameCatalog.ID_RANCE04, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("System39.ini", "RANCE4SA_CHS.ALD")));

        // 3. Localized JP script names pass validateImportedGameDir even when expectedAinName differs!
        File jpReboot = tmp.newFolder("JP_EscalayerReboot");
        writeFile(new File(jpReboot, "AliceStart.ini"), "GameName = \"\u30a8\u30b9\u30ab\u30ec\u30a4\u30e4\u30fc\u30fb\u30ea\u30d6\u30fc\u30c8\"\n".getBytes(SJIS));
        writeFile(new File(jpReboot, "\u30a8\u30b9\u30ab\u30ec\u30a4\u30e4\u30fc\u30fb\u30ea\u30d6\u30fc\u30c8.ain"), new byte[]{1, 2});
        assertTrue(GameCatalog.INSTANCE.validateImportedGameDir(
                jpReboot, "EscalayerReboot.ain", GameCatalog.ID_ESCALAYER_REBOOT));

        File jpIxseal = tmp.newFolder("JP_Ixseal");
        writeFile(new File(jpIxseal, "AliceStart.ini"), "GameName = \"\u8d85\u6602\u795e\u9a0e\u30a8\u30af\u30b7\u30fc\u30eb\"\n".getBytes(SJIS));
        writeFile(new File(jpIxseal, "\u8d85\u6602\u795e\u9a0e\u30a8\u30af\u30b7\u30fc\u30eb.ain"), new byte[]{1, 2});
        assertTrue(GameCatalog.INSTANCE.validateImportedGameDir(
                jpIxseal, "Ixseal.ain", GameCatalog.ID_IXSEAL));

        File enR10 = tmp.newFolder("EN_Rance10");
        writeFile(new File(enR10, "AliceStart.ini"), "GameName = \"Rance 10 English Edition\"\nCodeName = \"Rance10_EN.ain\"\n".getBytes(StandardCharsets.UTF_8));
        writeFile(new File(enR10, "Rance10_EN.ain"), new byte[]{1, 2});
        assertTrue(GameCatalog.INSTANCE.validateImportedGameDir(
                enR10, "Rance10.ain", GameCatalog.ID_RANCE10));
    }

    @Test
    public void legacySystem3EnglishIdsBomCommentsAndMissingFilesClassifyProperly() throws Exception {
        // UTF-8 BOM + inline comment + _eng suffix across all System 1-3 titles
        byte[] vampEngIni = "\uFEFF[config]\ngame = little_vampire_eng ; English v2.0\n".getBytes(StandardCharsets.UTF_8);
        assertEquals("little_vampire_eng", GameCatalog.INSTANCE.rawLegacySystem3GameId$app_debug(vampEngIni));
        assertEquals("little_vampire", GameCatalog.INSTANCE.legacySystem3GameId$app_debug(vampEngIni));

        File vampDir = tmp.newFolder("LittleVampireEN");
        writeFile(new File(vampDir, "ADISK.DAT"), new byte[256]);
        writeFile(new File(vampDir, "ACG.DAT"), new byte[]{1});
        writeFile(new File(vampDir, "SYSTEM3.INI"), vampEngIni);
        assertEquals(GameCatalog.ID_LITTLEVAMPIRE, GameCatalog.INSTANCE.classifyGameRootDir(vampDir));
        assertTrue(GameCatalog.hasExplicitNonChineseEdition(vampDir));

        // Rance 1 Classic English
        byte[] ranceEngIni = "[config]\ngame = rance_eng\n".getBytes(StandardCharsets.UTF_8);
        assertEquals(GameCatalog.ID_RANCE1CLASSIC, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("ADISK.DAT", "ACG.DAT", "system3.ini"),
                Collections.emptyList(),
                name -> name.equalsIgnoreCase("system3.ini") ? ranceEngIni : null));

        // Dr. STOP! System 2 legacy edition
        byte[] drstopIni = "[config]\ngame = drstop\n".getBytes(StandardCharsets.UTF_8);
        assertEquals(GameCatalog.ID_DRSTOP, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("ADISK.DAT", "ACG.DAT", "system3.ini"),
                Collections.emptyList(),
                name -> name.equalsIgnoreCase("system3.ini") ? drstopIni : null));

        // Prostudent G Omake with AGAME.DAT (no ADISK.DAT)
        byte[] progOmakeIni = "[config]\ngame = prog_omake\n".getBytes(StandardCharsets.UTF_8);
        assertTrue(GameCatalog.INSTANCE.isLegacySystem3GameFiles(Arrays.asList("AGAME.DAT", "ACG.DAT")));
        assertEquals(GameCatalog.ID_PROGOMAKE, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("AGAME.DAT", "ACG.DAT", "system3.ini"),
                Collections.emptyList(),
                name -> name.equalsIgnoreCase("system3.ini") ? progOmakeIni : null));
    }

    @Test
    public void badgesDistinguishEnglishEditionsAndTraditionalChineseMarkers() throws Exception {
        // MangaGamer English Rance VI must NOT be badged Chinese
        File mgR6 = tmp.newFolder("MG_Rance6");
        writeFile(new File(mgR6, "System40.ini"),
                "GameName = \"Rance VI - The Collapse of Zeth\"\nCodeName = \"Rance6.ain\"\n".getBytes(StandardCharsets.UTF_8));
        writeFile(new File(mgR6, "Rance6.ain"), new byte[]{1, 2});
        GameDescriptor r6Desc = GameCatalog.INSTANCE.descriptorForWithResolver(GameCatalog.ID_RANCE06, null, r -> "rance06");
        Item mgR6Item = new Item("Rance VI", mgR6, mgR6, null, null, null);
        assertTrue(GameCatalog.hasExplicitNonChineseEdition(mgR6));
        assertFalse(GameCatalog.hasChineseTranslation(r6Desc, mgR6Item, null));

        // Traditional Chinese markers and NSPic/ folder flip custom game to Chinese
        assertTrue(GameCatalog.hasLocalChineseMarkers(Arrays.asList("AliceStart.ini", "Game_cht.ain")));
        assertTrue(GameCatalog.hasLocalChineseMarkers(Arrays.asList("AliceStart.ini", "Rance\u6f22\u5316.ain")));
        File nsPicDir = tmp.newFolder("NSPicGame");
        writeFile(new File(nsPicDir, "AliceStart.ini"), "GameName = \"Test\"\n".getBytes(StandardCharsets.UTF_8));
        writeFile(new File(nsPicDir, "Test.ain"), new byte[]{1});
        assertTrue(new File(nsPicDir, "NSPic").mkdir());
        assertTrue(GameCatalog.hasLocalChineseMarkers(nsPicDir));
    }

    @Test
    public void shiftJisKanjiWithAsciiTrailByteIsNotCorruptedAsCp1252() {
        // "地.ain" = 92 6E 2E 61 69 6E (0x92 is CP1252 right apostrophe, 0x6E is 'n')
        assertEquals("\u5730.ain", ArchiveExtractor.INSTANCE.decodeBestCjkText(new byte[]{
                (byte) 0x92, 0x6E, 0x2E, 0x61, 0x69, 0x6E
        }, false));
        // "A地.ain" and "Map地.ain" must also remain Shift-JIS
        assertEquals("A\u5730.ain", ArchiveExtractor.INSTANCE.decodeBestCjkText(new byte[]{
                0x41, (byte) 0x92, 0x6E, 0x2E, 0x61, 0x69, 0x6E
        }, false));
        assertEquals("Map\u5730.ain", ArchiveExtractor.INSTANCE.decodeBestCjkText(new byte[]{
                0x4D, 0x61, 0x70, (byte) 0x92, 0x6E, 0x2E, 0x61, 0x69, 0x6E
        }, false));
        // "Map顴.ain" (E9 41 = 顴, uppercase trail byte 'A') must NOT become "MapéA.ain"
        assertEquals("Map\u9874.ain", ArchiveExtractor.INSTANCE.decodeBestCjkText(new byte[]{
                0x4D, 0x61, 0x70, (byte) 0xE9, 0x41, 0x2E, 0x61, 0x69, 0x6E
        }, false));
        // Standalone "I’m Ready.txt" (49 92 6D) MUST decode as CP1252 English contraction
        assertEquals("I\u2019m Ready.txt", ArchiveExtractor.INSTANCE.decodeBestCjkText(new byte[]{
                0x49, (byte) 0x92, 0x6D, 0x20, 0x52, 0x65, 0x61, 0x64, 0x79, 0x2E, 0x74, 0x78, 0x74
        }, false));
    }
}

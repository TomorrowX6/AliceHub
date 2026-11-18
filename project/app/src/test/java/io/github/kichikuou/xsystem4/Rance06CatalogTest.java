package io.github.kichikuou.xsystem4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class Rance06CatalogTest {
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

    private static void putLe32(byte[] buf, int off, int val) {
        buf[off] = (byte) (val & 0xFF);
        buf[off + 1] = (byte) ((val >>> 8) & 0xFF);
        buf[off + 2] = (byte) ((val >>> 16) & 0xFF);
        buf[off + 3] = (byte) ((val >>> 24) & 0xFF);
    }

    private static void writeDirRecord(
            ByteArrayOutputStream out,
            String name,
            boolean isDir,
            int lba,
            int size,
            boolean joliet
    ) throws Exception {
        byte[] idBytes = joliet
                ? name.getBytes(StandardCharsets.UTF_16BE)
                : name.getBytes(StandardCharsets.US_ASCII);
        int recLen = 33 + idBytes.length;
        if ((recLen & 1) != 0) recLen++;
        byte[] rec = new byte[recLen];
        rec[0] = (byte) recLen;
        putLe32(rec, 2, lba);
        putLe32(rec, 10, size);
        rec[25] = (byte) (isDir ? 0x02 : 0x00);
        rec[32] = (byte) idBytes.length;
        System.arraycopy(idBytes, 0, rec, 33, idBytes.length);
        out.write(rec);
    }

    private static File buildTestIso(
            File isoFile,
            boolean joliet,
            List<String> gameDirFiles,
            String subDirPath
    ) throws Exception {
        final int sectorSize = 2048;
        final int rootLba = 18;
        final int midLba = 19;
        final int leafLba = 20;
        final int dataLba = 21;
        final int totalSectors = 23;
        byte[] img = new byte[totalSectors * sectorSize];

        int vdOff = 16 * sectorSize;
        img[vdOff] = (byte) (joliet ? 2 : 1);
        img[vdOff + 1] = 'C';
        img[vdOff + 2] = 'D';
        img[vdOff + 3] = '0';
        img[vdOff + 4] = '0';
        img[vdOff + 5] = '1';
        if (joliet) {
            img[vdOff + 88] = 0x25;
            img[vdOff + 89] = 0x2F;
            img[vdOff + 90] = 0x45;
        }
        putLe32(img, vdOff + 158, rootLba);
        putLe32(img, vdOff + 166, sectorSize);

        int termOff = 17 * sectorSize;
        img[termOff] = (byte) 255;
        img[termOff + 1] = 'C';
        img[termOff + 2] = 'D';
        img[termOff + 3] = '0';
        img[termOff + 4] = '0';
        img[termOff + 5] = '1';

        int targetDirLba = rootLba;
        if (subDirPath != null && !subDirPath.isEmpty()) {
            String[] segs = subDirPath.split("/");
            if (segs.length == 1) {
                ByteArrayOutputStream rootOut = new ByteArrayOutputStream();
                writeDirRecord(rootOut, segs[0], true, midLba, sectorSize, joliet);
                byte[] rootBytes = rootOut.toByteArray();
                System.arraycopy(rootBytes, 0, img, rootLba * sectorSize, rootBytes.length);
                targetDirLba = midLba;
            } else if (segs.length == 2) {
                ByteArrayOutputStream rootOut = new ByteArrayOutputStream();
                writeDirRecord(rootOut, segs[0], true, midLba, sectorSize, joliet);
                byte[] rootBytes = rootOut.toByteArray();
                System.arraycopy(rootBytes, 0, img, rootLba * sectorSize, rootBytes.length);

                ByteArrayOutputStream midOut = new ByteArrayOutputStream();
                writeDirRecord(midOut, segs[1], true, leafLba, sectorSize, joliet);
                byte[] midBytes = midOut.toByteArray();
                System.arraycopy(midBytes, 0, img, midLba * sectorSize, midBytes.length);
                targetDirLba = leafLba;
            }
        }

        byte[] payload = "SYS4".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(payload, 0, img, dataLba * sectorSize, payload.length);

        ByteArrayOutputStream gameOut = new ByteArrayOutputStream();
        for (String name : gameDirFiles) {
            writeDirRecord(gameOut, name + ";1", false, dataLba, payload.length, joliet);
        }
        byte[] gameBytes = gameOut.toByteArray();
        System.arraycopy(gameBytes, 0, img, targetDirLba * sectorSize, gameBytes.length);

        writeFile(isoFile, img);
        return isoFile;
    }

    @Test
    public void placesRance06InSeriesChronologicalOrderBetweenRance03AndSengokuRance() {
        assertEquals("rance06", GameCatalog.ID_RANCE06);
        List<String> order = GameCatalog.builtInSelectOrder();
        int idx01 = order.indexOf(GameCatalog.ID_RANCE01);
        int idx02 = order.indexOf(GameCatalog.ID_RANCE02);
        int idx03 = order.indexOf(GameCatalog.ID_RANCE03);
        int idx06 = order.indexOf(GameCatalog.ID_RANCE06);
        int idxSr = order.indexOf(GameCatalog.ID_SENGOKU_RANCE);
        int idx08 = order.indexOf(GameCatalog.ID_RANCE08);
        int idx09 = order.indexOf(GameCatalog.ID_RANCE09);

        assertTrue(idx01 >= 0 && idx02 > idx01 && idx03 > idx02);
        assertEquals(idx03 + 1, idx06);
        assertEquals(idx06 + 1, idxSr);
        assertTrue(idx08 > idxSr && idx09 > idx08);
    }

    @Test
    public void classifiesChineseAndJapaneseRance06GameRoots() throws Exception {
        File cnDir = tmp.newFolder("Rance06");
        String cnIni = "GameName\t= \"Rance6中文版 v1.10（哈尼喵汉化组×爱丽丝汉化组 制作）\"\r\n" +
                "CodeName\t= \"Rance6.ain\"\r\n" +
                "SaveFolder\t= \"SaveData\"\r\n";
        writeFile(new File(cnDir, "System40.ini"), cnIni.getBytes(Charset.forName("GBK")));
        writeFile(new File(cnDir, "Rance6.ain"), new byte[]{1, 2, 3, 4});
        writeFile(new File(cnDir, "Rance6GA.ald"), new byte[]{0});
        writeFile(new File(cnDir, "Rance6GM.ald"), new byte[]{0});
        writeFile(new File(cnDir, "Rance6GZ.ald"), new byte[]{0});
        writeFile(new File(cnDir, "Fzzdxfw.ttf"), new byte[]{0});

        System40Ini parsed = System40Ini.Companion.parse(new File(cnDir, "System40.ini"));
        assertEquals("Rance6中文版 v1.10（哈尼喵汉化组×爱丽丝汉化组 制作）", parsed.getGameName());
        assertEquals("SaveData", parsed.getSaveFolder());
        assertFalse(GameCatalog.INSTANCE.isSystem3xGameDir(cnDir));
        assertEquals(GameCatalog.ID_RANCE06, GameCatalog.INSTANCE.classifyGameRootDir(cnDir));
        assertEquals(GameCatalog.ID_RANCE06, GameCatalog.INSTANCE.classifyDirectory(cnDir, cnDir.getName(), null, null));

        File jpDir = tmp.newFolder("JP_Rance6");
        String jpIni = "GameName = \"Rance6\"\r\nCodeName = \"Rance6.ain\"\r\nSaveFolder = \"SaveData\"\r\n";
        writeFile(new File(jpDir, "System40.ini"), jpIni.getBytes(Charset.forName("Shift_JIS")));
        writeFile(new File(jpDir, "Rance6.ain"), new byte[]{1, 2, 3, 4});
        writeFile(new File(jpDir, "Rance6BA.ald"), new byte[]{0});
        writeFile(new File(jpDir, "Rance6WA.ald"), new byte[]{0});
        assertEquals(GameCatalog.ID_RANCE06, GameCatalog.INSTANCE.classifyDirectory(jpDir, jpDir.getName(), null, null));

        File altDir = tmp.newFolder("AltR06");
        writeFile(new File(altDir, "AliceStart.ini"), jpIni.getBytes(StandardCharsets.UTF_8));
        writeFile(new File(altDir, "Rance06.ain"), new byte[]{1});
        assertEquals(GameCatalog.ID_RANCE06, GameCatalog.INSTANCE.classifyDirectory(altDir, altDir.getName(), null, null));

        // Even if a CN repack renames Rance6.ain to AliceCN.ain, Rance6*.ald / Rance6DLL.inc classify as Rance 06 (never Sengoku Rance).
        File aliceCnR6Dir = tmp.newFolder("RepackR6");
        writeFile(new File(aliceCnR6Dir, "System40.ini"), cnIni.getBytes(Charset.forName("GBK")));
        writeFile(new File(aliceCnR6Dir, "AliceCN.ain"), new byte[]{1});
        writeFile(new File(aliceCnR6Dir, "Rance6GA.ald"), new byte[]{0});
        writeFile(new File(aliceCnR6Dir, "Rance6DLL.inc"), new byte[]{0});
        assertEquals(GameCatalog.ID_RANCE06, GameCatalog.INSTANCE.classifyGameRootDir(aliceCnR6Dir));

        File extractedCacheRoot = new File("/root/.cache/r06_game");
        File extractedGameDir = new File("/root/.cache/r06_game/Rance6 －ゼス崩壊－/Rance6");
        if (extractedGameDir.isDirectory()) {
            assertEquals(GameCatalog.ID_RANCE06, GameCatalog.INSTANCE.classifyGameRootDir(extractedGameDir));
            File discovered = GameListKt.findGameRootDirInternal(extractedCacheRoot, null, GameCatalog.ID_RANCE06);
            assertNotNull(discovered);
            assertEquals(extractedGameDir.getCanonicalPath(), discovered.getCanonicalPath());
        }
    }

    @Test
    public void classifiesRance06BySaveDirAndAliasesWithoutCollisions() throws Exception {
        File saveBySlot = tmp.newFolder("SaveSlots");
        writeFile(new File(saveBySlot, "Config.asd"), new byte[]{1, 2});
        writeFile(new File(saveBySlot, "SaveA.asd"), new byte[]{3, 4});
        assertEquals(
                GameCatalog.ID_RANCE06,
                GameCatalog.INSTANCE.classifyDirectory(null, "UnknownSaveDir", saveBySlot, null)
        );

        File saveByProgress = tmp.newFolder("SaveProgress");
        writeFile(new File(saveByProgress, "Progress.asd"), new byte[]{1});
        assertEquals(
                GameCatalog.ID_RANCE06,
                GameCatalog.INSTANCE.classifyDirectory(null, "CustomSave", saveByProgress, null)
        );

        assertTrue(GameModifierBridge.INSTANCE.isRance06GameDir(null, "Rance06", saveBySlot));
        assertFalse(GameModifierBridge.INSTANCE.isRance10GameDir(null, "Rance06", saveBySlot));
        assertFalse(GameModifierBridge.INSTANCE.isEvenicleGameDir(null, "Rance06", saveBySlot));
        assertFalse(GameModifierBridge.INSTANCE.isDohnaDohnaGameDir(null, "Rance06", saveBySlot));
        assertFalse(GameModifierBridge.INSTANCE.isRance01GameDir(null, "Rance06", saveBySlot));
        assertFalse(GameModifierBridge.INSTANCE.isRance02GameDir(null, "Rance06", saveBySlot));
        assertFalse(GameModifierBridge.INSTANCE.isRance03GameDir(null, "Rance06", saveBySlot));
        assertFalse(GameModifierBridge.INSTANCE.isSengokuRanceGameDir(null, "Rance06", saveBySlot));
        assertFalse(GameModifierBridge.INSTANCE.isRance08GameDir(null, "Rance06", saveBySlot));
        assertFalse(GameModifierBridge.INSTANCE.isRance09GameDir(null, "Rance06", saveBySlot));
        assertFalse(GameModifierBridge.INSTANCE.isEscalayerGameDir(null, "Rance06", saveBySlot));

        String[] positiveAliases = new String[]{
                "Rance06",
                "Rance06-1",
                "Rance6",
                "Rance 6",
                "Rance 06: The Collapse of Zeth",
                "Rance VI",
                "Rance6 －ゼス崩壊－",
                "ランス6",
                "ランス６",
                "ランス06 ゼス崩壊",
                "兰斯6",
                "兰斯06 赛斯崩坏",
                "Rance6中文版 v1.10（哈尼喵汉化组×爱丽丝汉化组 制作）"
        };
        for (String alias : positiveAliases) {
            assertTrue("Expected positive alias: " + alias, GameCatalog.INSTANCE.isRance06Alias(alias));
            assertEquals(
                    "Expected ID_RANCE06 for dirName: " + alias,
                    GameCatalog.ID_RANCE06,
                    GameCatalog.INSTANCE.classifyDirectory(null, alias, null, null)
            );
            assertEquals(
                    "Expected ID_RANCE06 for displayName: " + alias,
                    GameCatalog.ID_RANCE06,
                    GameCatalog.INSTANCE.classifyDirectory(null, "OtherFolder", null, alias)
            );
        }

        String[] negativeAliases = new String[]{
                "兰斯10",
                "兰斯16",
                "兰斯60",
                "ランス16",
                "ランス60",
                "Rance10",
                "Rance16",
                "Rance60",
                "Rance VII",
                "Rance VIII",
                "Rance IX",
                "Rance X",
                "SengokuRance",
                "KichikuRance",
                "Rance01",
                "Rance02",
                "Rance03",
                "Rance08",
                "Rance09"
        };
        for (String nonR06 : negativeAliases) {
            assertFalse("Expected negative alias: " + nonR06, GameCatalog.INSTANCE.isRance06Alias(nonR06));
            assertNotEquals(
                    "Must not classify as ID_RANCE06: " + nonR06,
                    GameCatalog.ID_RANCE06,
                    GameCatalog.INSTANCE.classifyDirectory(null, nonR06, null, nonR06)
            );
        }
    }

    @Test
    public void validatesTargetGameFilesDirsAndIsoImagesWhenExpectedAinIsNull() throws Exception {
        File r06Cn = tmp.newFolder("ImportedR6Cn");
        writeFile(new File(r06Cn, "System40.ini"), "GameName = \"Rance6\"\n".getBytes(StandardCharsets.UTF_8));
        writeFile(new File(r06Cn, "Rance6.ain"), new byte[]{0});

        File r06Alt = tmp.newFolder("ImportedR06Alt");
        writeFile(new File(r06Alt, "AliceStart.ini"), "GameName = \"Rance06\"\n".getBytes(StandardCharsets.UTF_8));
        writeFile(new File(r06Alt, "Rance06.ain"), new byte[]{0});

        // Unrelated game inside a folder deceptively named "Rance06" must still be rejected!
        File fakeR06 = tmp.newFolder("Rance06");
        writeFile(new File(fakeR06, "AliceStart.ini"), "GameName = \"Other\"\n".getBytes(StandardCharsets.UTF_8));
        writeFile(new File(fakeR06, "OtherGame.ain"), new byte[]{0});

        File rance03 = tmp.newFolder("Rance03Dir");
        writeFile(new File(rance03, "AliceStart.ini"), "GameName = \"Rance03\"\n".getBytes(StandardCharsets.UTF_8));
        writeFile(new File(rance03, "Rance03.ain"), new byte[]{0});

        File sengoku = tmp.newFolder("SengokuDir");
        writeFile(new File(sengoku, "AliceStart.ini"), "GameName = \"Sengoku\"\n".getBytes(StandardCharsets.UTF_8));
        writeFile(new File(sengoku, "SengokuRance.ain"), new byte[]{0});

        File rance10 = tmp.newFolder("Rance10Dir");
        writeFile(new File(rance10, "AliceStart.ini"), "GameName = \"Rance10\"\n".getBytes(StandardCharsets.UTF_8));
        writeFile(new File(rance10, "Rance10.ain"), new byte[]{0});

        File kichiku = tmp.newFolder("KichikuDir");
        writeFile(new File(kichiku, "KichikuRanceSA.ALD"), new byte[]{0});

        assertTrue(GameCatalog.INSTANCE.validateImportedGameDir(r06Cn, null, GameCatalog.ID_RANCE06));
        assertTrue(GameCatalog.INSTANCE.validateImportedGameDir(r06Alt, null, GameCatalog.ID_RANCE06));
        assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(fakeR06, null, GameCatalog.ID_RANCE06));
        assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(rance03, null, GameCatalog.ID_RANCE06));
        assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(sengoku, null, GameCatalog.ID_RANCE06));
        assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(rance10, null, GameCatalog.ID_RANCE06));
        assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(kichiku, null, GameCatalog.ID_RANCE06));

        assertTrue(GameCatalog.INSTANCE.matchesTargetGameFiles(
                GameCatalog.ID_RANCE06,
                Arrays.asList("System40.ini", "Rance6.ain", "Rance6GA.ald")
        ));
        assertTrue(GameCatalog.INSTANCE.matchesTargetGameFiles(
                GameCatalog.ID_RANCE06,
                Arrays.asList("AliceStart.ini", "Rance06.ain")
        ));
        assertFalse(GameCatalog.INSTANCE.matchesTargetGameFiles(
                GameCatalog.ID_RANCE06,
                Arrays.asList("AliceStart.ini", "Rance03.ain")
        ));
        assertFalse(GameCatalog.INSTANCE.matchesTargetGameFiles(
                GameCatalog.ID_RANCE06,
                Collections.singletonList("Rance4SA.ALD")
        ));

        File r06Iso = buildTestIso(
                tmp.newFile("rance6.iso"),
                false,
                Arrays.asList("System40.ini", "Rance6.ain", "Rance6GA.ald"),
                "GameData"
        );
        File r06NestedIso = buildTestIso(
                tmp.newFile("rance06_joliet.mdf"),
                true,
                Arrays.asList("AliceStart.ini", "Rance06.ain"),
                "InstallSet/InstallData"
        );
        File r03Iso = buildTestIso(
                tmp.newFile("rance03.iso"),
                false,
                Arrays.asList("AliceStart.ini", "Rance03.ain"),
                "GameData"
        );

        assertTrue(Iso9660Extractor.INSTANCE.containsGameRoot(r06Iso, null, GameCatalog.ID_RANCE06));
        assertTrue(Iso9660Extractor.INSTANCE.containsGameRoot(r06NestedIso, null, GameCatalog.ID_RANCE06));
        assertFalse(Iso9660Extractor.INSTANCE.containsGameRoot(r03Iso, null, GameCatalog.ID_RANCE06));
        assertFalse(Iso9660Extractor.INSTANCE.containsGameRoot(r06Iso, null, GameCatalog.ID_RANCE03));

        File isoExtractOut = tmp.newFolder("iso_out");
        int extractedCount = Iso9660Extractor.INSTANCE.extractFileToDirectory(
                r06NestedIso,
                isoExtractOut,
                rel -> NativeGameImport.INSTANCE.skip(rel),
                false,
                null,
                null,
                null
        );
        assertEquals(2, extractedCount);
        File foundFromIso = GameListKt.findGameRootDirInternal(isoExtractOut, null, GameCatalog.ID_RANCE06);
        assertNotNull(foundFromIso);
        assertEquals("InstallData", foundFromIso.getName());
    }

    @Test
    public void prunesRance06BloatAndResolvesRance06DefaultDirName() throws Exception {
        assertEquals(
                "Rance06",
                NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                        Arrays.asList("System40.ini", "Rance6.ain", "Rance6GA.ald"),
                        false
                )
        );
        assertEquals(
                "Rance06",
                NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                        Arrays.asList("AliceStart.ini", "Rance06.ain"),
                        false
                )
        );

        assertTrue(GameListKt.isRance06BloatRelPath("System40.exe", true));
        assertTrue(GameListKt.isRance06BloatRelPath("Rance6/System40.exe", false));
        assertTrue(GameListKt.isRance06BloatRelPath("Rance6/ijl15.dll", false));
        assertTrue(GameListKt.isRance06BloatRelPath("Rance6/DLL/DrawDungeon.dll", false));
        assertTrue(GameListKt.isRance06BloatRelPath("Rance6/BackData/Rance6.ain", false));
        assertTrue(GameListKt.isRance06BloatRelPath("Rance6/BackData/System40.ini", false));
        assertTrue(GameListKt.isRance06BloatRelPath("Rance6/System40Tcn.ini", false));
        assertTrue(GameListKt.isRance06BloatRelPath("Rance6/desktop.ini", false));
        assertTrue(GameListKt.isRance06BloatRelPath("Rance6/骑／步切换.bat", false));
        assertTrue(GameListKt.isRance06BloatRelPath("Rance6/补丁/原版骑兵/Rance6GM.ald", false));
        assertTrue(GameListKt.isRance06BloatRelPath("Rance6/补丁/步兵/Rance6GM.ald", false));
        assertTrue(GameListKt.isRance06BloatRelPath("Rance6/补丁/R6汉化补丁v1.10/GameData/Rance6.ain", false));

        // Nested archive directly inside 补丁/ is allowed during multi-stage extraction (skipPatchSubdir=false)
        assertFalse(GameListKt.isRance06BloatRelPath("补丁/Rance06_Full.7z", false));
        assertTrue(GameListKt.isRance06BloatRelPath("补丁/Rance06_Full.7z", true));

        // All 23 runtime files must never be flagged as bloat!
        String[] runtimePaths = new String[]{
                "System40.ini",
                "StreamMusic.ini",
                "SACT_DLL.inc",
                "Rance6DLL.inc",
                "DrawPluginDLL.inc",
                "Rance6.ain",
                "Fzzdxfw.ttf",
                "AliceLogo.alk",
                "Rance6OP.mpg",
                "Rance6BA.ald",
                "Rance6BA.bgi",
                "Rance6GA.ald",
                "Rance6GB.ald",
                "Rance6GM.ald",
                "Rance6GZ.ald",
                "Rance6WA.ald",
                "Rance6WA.wai",
                "Data/DungeonData.dlf",
                "Data/Event.alk",
                "Data/EnemyA.acx",
                "Data/EnemyB.acx",
                "Data/MineA.acx",
                "Data/MineB.acx"
        };
        File stagedGame = tmp.newFolder("Rance6");
        for (String rel : runtimePaths) {
            assertFalse("Runtime file must be kept: " + rel, GameListKt.isRance06BloatRelPath(rel, true));
            assertFalse("Runtime file must not be skipped by NativeGameImport: " + rel, NativeGameImport.INSTANCE.skip(rel));
            writeFile(new File(stagedGame, rel), new byte[]{1, 2});
        }
        // Populate bloat files & folders inside stagedGame
        writeFile(new File(stagedGame, "System40.exe"), new byte[]{9});
        writeFile(new File(stagedGame, "ijl15.dll"), new byte[]{9});
        writeFile(new File(stagedGame, "DLL/DrawDungeon.dll"), new byte[]{9});
        writeFile(new File(stagedGame, "BackData/Rance6.ain"), new byte[]{9});
        writeFile(new File(stagedGame, "BackData/System40.ini"), new byte[]{9});
        writeFile(new File(stagedGame, "System40Tcn.ini"), new byte[]{9});
        writeFile(new File(stagedGame, "desktop.ini"), new byte[]{9});
        writeFile(new File(stagedGame, "骑／步切换.bat"), new byte[]{9});
        writeFile(new File(stagedGame, "补丁/原版骑兵/Rance6GM.ald"), new byte[]{9});
        writeFile(new File(stagedGame, "补丁/步兵/Rance6GM.ald"), new byte[]{9});
        writeFile(new File(stagedGame, "补丁/R6汉化补丁v1.10/GameData/System40.ini"), new byte[]{9});
        writeFile(new File(stagedGame, "补丁/R6汉化补丁v1.10/GameData/Rance6.ain"), new byte[]{9});

        // Root discovery must pick outer Rance6 over BackData and 补丁/R6汉化补丁v1.10/GameData
        File chosenRoot = GameListKt.findGameRootDirInternal(stagedGame.getParentFile(), null, GameCatalog.ID_RANCE06);
        assertNotNull(chosenRoot);
        assertEquals(stagedGame.getCanonicalPath(), chosenRoot.getCanonicalPath());

        GameListKt.pruneRedundantBaseBloat(stagedGame);
        for (String rel : runtimePaths) {
            assertTrue("Runtime file must survive pruning: " + rel, new File(stagedGame, rel).isFile());
        }
        assertFalse(new File(stagedGame, "System40.exe").exists());
        assertFalse(new File(stagedGame, "ijl15.dll").exists());
        assertFalse(new File(stagedGame, "DLL").exists());
        assertFalse(new File(stagedGame, "BackData").exists());
        assertFalse(new File(stagedGame, "补丁").exists());
        assertFalse(new File(stagedGame, "System40Tcn.ini").exists());
        assertFalse(new File(stagedGame, "desktop.ini").exists());
        assertFalse(new File(stagedGame, "骑／步切换.bat").exists());
    }

    @Test
    public void verifiesOnlineInstallerCdnUrlAndExpectedBytes() throws Exception {
        assertEquals("https://alicehub.000.moe/rance06", OnlineInstaller.RANCE06_CDN_URL);
        assertEquals(2260544882L, AppSettings.defaultBaseGameSizeForUrl(OnlineInstaller.RANCE06_CDN_URL));
        assertEquals(2260544882L, AppSettings.defaultBaseGameSizeForUrl("https://alicehub.000.moe/rance06/"));

        File storage = tmp.newFolder("storage");
        File r06Staging = new File(storage, ".online_base_staging_Rance06");
        File r6Staging = new File(storage, ".online_base_staging_Rance6");
        File r03Staging = new File(storage, ".online_base_staging_Rance03");
        File r10Staging = new File(storage, ".online_base_staging_Rance10");
        assertTrue(r06Staging.mkdirs());
        assertTrue(r6Staging.mkdirs());
        assertTrue(r03Staging.mkdirs());
        assertTrue(r10Staging.mkdirs());

        List<File> evicted = OnlineInstaller.INSTANCE.evictBaseStagingToTrash(storage, GameCatalog.ID_RANCE06, null);
        assertEquals(2, evicted.size());
        assertFalse(r06Staging.exists());
        assertFalse(r6Staging.exists());
        assertTrue(r03Staging.exists());
        assertTrue(r10Staging.exists());
    }
}

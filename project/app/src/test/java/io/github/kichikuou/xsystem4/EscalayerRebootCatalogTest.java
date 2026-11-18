package io.github.kichikuou.xsystem4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import kotlin.jvm.functions.Function1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class EscalayerRebootCatalogTest {
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

    @Test
    public void classifiesEscalayerRebootFilesAndDisambiguatesFromSystem3xEscalayer() throws Exception {
        File rebootDir = tmp.newFolder("EscalayerReboot");
        writeFile(
                new File(rebootDir, "AliceStart.ini"),
                ("GameName = \"超昂天使Escalayer，by axela\"\n"
                        + "BootName = \"EscalayerReboot\"\n"
                        + "CodeName = \"EscalayerReboot.ain\"\n").getBytes(StandardCharsets.UTF_8)
        );
        writeFile(new File(rebootDir, "EscalayerReboot.ain"), new byte[]{0});

        File rebootLower = tmp.newFolder("CustomGameFolder");
        writeFile(new File(rebootLower, "AliceStart.ini"), "GameName = \"Escalayer\"\n".getBytes(StandardCharsets.UTF_8));
        writeFile(new File(rebootLower, "escalayerreboot.ain"), new byte[]{0});

        File sys3Escalayer = tmp.newFolder("Escalayer");
        writeFile(new File(sys3Escalayer, "DOKI_SA.ALD"), new byte[]{0});
        writeFile(new File(sys3Escalayer, "System39.ini"), "GameName = \"超昂天使エスカレイヤー\"\n".getBytes(StandardCharsets.UTF_8));

        File fakeReboot = tmp.newFolder("EscalayerRebootFake");
        writeFile(new File(fakeReboot, "AliceStart.ini"), "GameName = \"Other\"\n".getBytes(StandardCharsets.UTF_8));
        writeFile(new File(fakeReboot, "OtherGame.ain"), new byte[]{0});

        assertFalse(GameCatalog.INSTANCE.isSystem3xGameDir(rebootDir));
        assertFalse(GameCatalog.INSTANCE.isEscalayerGameDir(rebootDir));
        assertTrue(GameCatalog.INSTANCE.isSystem3xGameDir(sys3Escalayer));
        assertTrue(GameCatalog.INSTANCE.isEscalayerGameDir(sys3Escalayer));

        assertEquals(
                GameCatalog.ID_ESCALAYER_REBOOT,
                GameCatalog.INSTANCE.classifyDirectory(rebootDir, rebootDir.getName(), null, null)
        );
        assertEquals(
                GameCatalog.ID_ESCALAYER_REBOOT,
                GameCatalog.INSTANCE.classifyDirectory(rebootLower, rebootLower.getName(), null, "超昂天使Escalayer，by axela")
        );
        assertEquals(
                GameCatalog.ID_ESCALAYER,
                GameCatalog.INSTANCE.classifyDirectory(sys3Escalayer, sys3Escalayer.getName(), null, null)
        );
        assertEquals(
                GameCatalog.PREFIX_CUSTOM + "EscalayerRebootFake",
                GameCatalog.INSTANCE.classifyDirectory(fakeReboot, fakeReboot.getName(), null, null)
        );

        assertTrue(GameCatalog.INSTANCE.validateImportedGameDir(rebootDir, "EscalayerReboot.ain", GameCatalog.ID_ESCALAYER_REBOOT));
        assertTrue(GameCatalog.INSTANCE.validateImportedGameDir(rebootDir, null, GameCatalog.ID_ESCALAYER_REBOOT));
        assertTrue(GameCatalog.INSTANCE.validateImportedGameDir(rebootLower, "EscalayerReboot.ain", GameCatalog.ID_ESCALAYER_REBOOT));

        assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(rebootDir, "DOKI_SA.ALD", GameCatalog.ID_ESCALAYER));
        assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(rebootDir, null, GameCatalog.ID_ESCALAYER));
        assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(sys3Escalayer, "EscalayerReboot.ain", GameCatalog.ID_ESCALAYER_REBOOT));
        assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(sys3Escalayer, null, GameCatalog.ID_ESCALAYER_REBOOT));
        assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(fakeReboot, "EscalayerReboot.ain", GameCatalog.ID_ESCALAYER_REBOOT));
        assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(fakeReboot, null, GameCatalog.ID_ESCALAYER_REBOOT));
    }

    @Test
    public void classifiesAliasesAndSaveDirsWithoutShadowing() throws Exception {
        String[] rebootAliases = {
                "EscalayerReboot", "EscalayerReboot-1", "Escalayer Reboot",
                "Beat Angel Escalayer Reboot", "エスカレイヤー・リブート",
                "エスカレイヤーリブート", "リブート", "超昂天使エスカレイヤー・リブート",
                "超昂天使 艾斯卡蕾雅 Reboot", "超昂天使エスカレイヤーR"
        };
        for (String alias : rebootAliases) {
            assertEquals(alias, GameCatalog.ID_ESCALAYER_REBOOT,
                    GameCatalog.INSTANCE.classifyDirectory(null, alias, null, null));
            assertEquals(alias, GameCatalog.ID_ESCALAYER_REBOOT,
                    GameCatalog.INSTANCE.classifyDirectory(null, "custom", null, alias));
        }

        String[] sys3Aliases = {
                "Escalayer", "Escalayer-1", "Beat Angel Escalayer",
                "超昂天使エスカレイヤー", "超昂天使 艾斯卡蕾雅"
        };
        for (String alias : sys3Aliases) {
            assertEquals(alias, GameCatalog.ID_ESCALAYER,
                    GameCatalog.INSTANCE.classifyDirectory(null, alias, null, null));
            assertEquals(alias, GameCatalog.ID_ESCALAYER,
                    GameCatalog.INSTANCE.classifyDirectory(null, "custom", null, alias));
        }

        File rebootSaveDir = tmp.newFolder("RebootSaves");
        writeFile(new File(rebootSaveDir, "AFConfig.asd"), new byte[]{1});
        writeFile(new File(rebootSaveDir, "CommonSystemData.sav"), new byte[]{1});
        writeFile(new File(rebootSaveDir, "Esca.sav"), new byte[]{1});
        writeFile(new File(rebootSaveDir, "EscalayerReboot_AFCGMode.asd"), new byte[]{1});
        assertEquals(
                GameCatalog.ID_ESCALAYER_REBOOT,
                GameCatalog.INSTANCE.classifyDirectory(null, "UnknownDir", rebootSaveDir, null)
        );

        File evenicleSaveDir = tmp.newFolder("EvenicleSaves");
        writeFile(new File(evenicleSaveDir, "CommonSystemData.sav"), new byte[]{1});
        assertEquals(
                GameCatalog.ID_EVENICLE,
                GameCatalog.INSTANCE.classifyDirectory(null, "UnknownDir", evenicleSaveDir, null)
        );

        File sys3EscSaveDir = tmp.newFolder("Sys3EscSaves");
        writeFile(new File(sys3EscSaveDir, "doki_sa.asd"), new byte[]{1});
        assertEquals(
                GameCatalog.ID_ESCALAYER,
                GameCatalog.INSTANCE.classifyDirectory(null, "UnknownDir", sys3EscSaveDir, null)
        );
    }

    @Test
    public void modifierBridgeDisambiguatesEscalayerRebootFromEscalayerAndOthers() throws Exception {
        File rebootDir = tmp.newFolder("EscalayerRebootDir");
        writeFile(new File(rebootDir, "AliceStart.ini"), "GameName = \"超昂天使Escalayer，by axela\"\n".getBytes(StandardCharsets.UTF_8));
        writeFile(new File(rebootDir, "EscalayerReboot.ain"), new byte[]{0});

        File rebootSaveDir = tmp.newFolder("EscalayerReboot");
        writeFile(new File(rebootSaveDir, "Esca.sav"), new byte[]{1});
        writeFile(new File(rebootSaveDir, "CommonSystemData.sav"), new byte[]{1});

        assertTrue(GameModifierBridge.INSTANCE.isEscalayerRebootGameDir(rebootDir, null, rebootSaveDir));
        assertFalse(GameModifierBridge.INSTANCE.isEscalayerGameDir(rebootDir, null, rebootSaveDir));
        assertFalse(GameModifierBridge.INSTANCE.isEvenicleGameDir(rebootDir, null, rebootSaveDir));
        assertFalse(GameModifierBridge.INSTANCE.isRance10GameDir(rebootDir, null, rebootSaveDir));
        assertFalse(GameModifierBridge.INSTANCE.isDohnaDohnaGameDir(rebootDir, null, rebootSaveDir));

        assertTrue(GameModifierBridge.INSTANCE.isEscalayerRebootGameDir(null, "EscalayerReboot", rebootSaveDir));
        assertFalse(GameModifierBridge.INSTANCE.isEscalayerGameDir(null, "EscalayerReboot", rebootSaveDir));
        assertFalse(GameModifierBridge.INSTANCE.isEvenicleGameDir(null, "EscalayerReboot", rebootSaveDir));
        assertFalse(GameModifierBridge.INSTANCE.isEscalayerGameDir(null, "超昂天使エスカレイヤー・リブート", null));

        File sys3Dir = tmp.newFolder("Sys3Escalayer");
        writeFile(new File(sys3Dir, "DOKI_SA.ALD"), new byte[]{0});
        assertTrue(GameModifierBridge.INSTANCE.isEscalayerGameDir(sys3Dir, null, null));
        assertFalse(GameModifierBridge.INSTANCE.isEscalayerRebootGameDir(sys3Dir, null, null));
    }

    @Test
    public void prunesEscalayerRebootBloatAndPreservesRuntimeFiles() throws Exception {
        String[] bloatPaths = {
                "EscalayerReboot.exe",
                "EscalayerReboot_cn.exe",
                "OpenSaveFolder.exe",
                "ResetConfig.exe",
                "DLL/DrawMovie3.dll",
                "备份文件/日文文件备份/EscalayerReboot.ain",
                "备份文件/无码补丁文件/EscalayerRebootCG.afa",
                "全cg存档.rar",
                "超级存档.rar",
                "超昂天使エスカレイヤー・リブート翻译补丁.rar",
                "玩前必看！！！！.docx",
                "战力对比数字对照表.txt",
                "战斗技能表.txt"
        };
        for (String rel : bloatPaths) {
            assertTrue(rel, GameListKt.isEscalayerRebootBloatRelPath(rel));
        }

        // Archive extraction keeps generic outer .rar wrappers when skipArchives=false,
        // while still filtering known bundled archive bloat.
        assertFalse(GameListKt.isEscalayerRebootBloatRelPath("outer_game.rar", false));
        assertTrue(GameListKt.isEscalayerRebootBloatRelPath("全cg存档.rar", false));
        assertTrue(GameListKt.isEscalayerRebootBloatRelPath("超昂天使エスカレイヤー・リブート翻译补丁.rar", false));

        String[] runtimePaths = {
                "AliceStart.ini",
                "AsraFrameworkDLL.inc",
                "EscalayerReboot.ain",
                "EscalayerReboot.ex",
                "EscalayerRebootFont.fnl",
                "EscalayerRebootBA.afa",
                "EscalayerRebootBA.bgi",
                "EscalayerRebootCG.afa",
                "EscalayerRebootFlat.afa",
                "EscalayerRebootWA.afa",
                "EscalayerRebootWA.wai",
                "Movie/Opening.alm",
                "Movie/Henshin01.alm",
                "Manual/index.html",
                "Manual/img/pm01.jpg",
                "License/libogg/COPYING",
                "InstallInfo/Installer.ini",
                "InstallInfo/InstallFiles.ini",
                "InstallInfo/SaveFolderSetting.ini"
        };
        for (String rel : runtimePaths) {
            assertFalse(rel, GameListKt.isEscalayerRebootBloatRelPath(rel));
            assertFalse(rel, NativeGameImport.INSTANCE.skip(rel));
        }

        File gameDir = tmp.newFolder("EscalayerReboot");
        for (String rel : runtimePaths) {
            writeFile(new File(gameDir, rel), new byte[]{1, 2});
        }
        for (String rel : bloatPaths) {
            writeFile(new File(gameDir, rel), new byte[]{3, 4});
        }
        writeFile(new File(gameDir, "SaveData/Esca.sav"), new byte[]{5});

        GameListKt.pruneRedundantBaseBloat(gameDir);

        for (String rel : runtimePaths) {
            assertTrue("Expected runtime file kept: " + rel, new File(gameDir, rel).isFile());
        }
        for (String rel : bloatPaths) {
            assertFalse("Expected bloat deleted: " + rel, new File(gameDir, rel).exists());
        }
        assertFalse(new File(gameDir, "备份文件").exists());
        assertFalse(new File(gameDir, "DLL").exists());
        assertFalse(new File(gameDir, "SaveData").exists());
    }

    @Test
    public void resolvesImportBaseNameForEscalayerReboot() {
        assertEquals(
                "EscalayerReboot",
                NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                        Arrays.asList("AliceStart.ini", "EscalayerReboot.ain"),
                        false
                )
        );
        assertEquals(
                "EscalayerReboot",
                NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                        Arrays.asList("AliceStart.ini", "エスカレイヤー・リブート.ain"),
                        false
                )
        );
        assertEquals(
                "Escalayer",
                NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                        Collections.singletonList("DOKI_SA.ALD"),
                        false
                )
        );
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
            int size
    ) throws Exception {
        byte[] idBytes = name.getBytes(StandardCharsets.UTF_16BE);
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

    private static File buildEscalayerRebootIso(File isoFile) throws Exception {
        final int sectorSize = 2048;
        final int rootLba = 18;
        final int gameLba = 19;
        final int dllLba = 20;
        final int backupLba = 21;
        final int movieLba = 22;
        final int installInfoLba = 23;
        final int fileDataLba = 24;
        final int totalSectors = 25;
        byte[] img = new byte[totalSectors * sectorSize];

        byte[] payload = "test".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(payload, 0, img, fileDataLba * sectorSize, payload.length);

        int svdOff = 16 * sectorSize;
        img[svdOff] = 2;
        img[svdOff + 1] = 'C';
        img[svdOff + 2] = 'D';
        img[svdOff + 3] = '0';
        img[svdOff + 4] = '0';
        img[svdOff + 5] = '1';
        img[svdOff + 88] = 0x25;
        img[svdOff + 89] = 0x2F;
        img[svdOff + 90] = 0x45;
        putLe32(img, svdOff + 158, rootLba);
        putLe32(img, svdOff + 166, sectorSize);

        int termOff = 17 * sectorSize;
        img[termOff] = (byte) 255;
        img[termOff + 1] = 'C';
        img[termOff + 2] = 'D';
        img[termOff + 3] = '0';
        img[termOff + 4] = '0';
        img[termOff + 5] = '1';

        ByteArrayOutputStream rootOut = new ByteArrayOutputStream();
        writeDirRecord(rootOut, "EscalayerReboot", true, gameLba, sectorSize);
        System.arraycopy(rootOut.toByteArray(), 0, img, rootLba * sectorSize, rootOut.size());

        ByteArrayOutputStream gameOut = new ByteArrayOutputStream();
        writeDirRecord(gameOut, "AliceStart.ini;1", false, fileDataLba, payload.length);
        writeDirRecord(gameOut, "EscalayerReboot.ain;1", false, fileDataLba, payload.length);
        writeDirRecord(gameOut, "EscalayerReboot.exe;1", false, fileDataLba, payload.length);
        writeDirRecord(gameOut, "OpenSaveFolder.exe;1", false, fileDataLba, payload.length);
        writeDirRecord(gameOut, "DLL", true, dllLba, sectorSize);
        writeDirRecord(gameOut, "备份文件", true, backupLba, sectorSize);
        writeDirRecord(gameOut, "Movie", true, movieLba, sectorSize);
        writeDirRecord(gameOut, "InstallInfo", true, installInfoLba, sectorSize);
        System.arraycopy(gameOut.toByteArray(), 0, img, gameLba * sectorSize, gameOut.size());

        ByteArrayOutputStream dllOut = new ByteArrayOutputStream();
        writeDirRecord(dllOut, "DrawMovie3.dll;1", false, fileDataLba, payload.length);
        System.arraycopy(dllOut.toByteArray(), 0, img, dllLba * sectorSize, dllOut.size());

        ByteArrayOutputStream backupOut = new ByteArrayOutputStream();
        writeDirRecord(backupOut, "EscalayerReboot.ain;1", false, fileDataLba, payload.length);
        System.arraycopy(backupOut.toByteArray(), 0, img, backupLba * sectorSize, backupOut.size());

        ByteArrayOutputStream movieOut = new ByteArrayOutputStream();
        writeDirRecord(movieOut, "Opening.alm;1", false, fileDataLba, payload.length);
        System.arraycopy(movieOut.toByteArray(), 0, img, movieLba * sectorSize, movieOut.size());

        ByteArrayOutputStream infoOut = new ByteArrayOutputStream();
        writeDirRecord(infoOut, "Installer.ini;1", false, fileDataLba, payload.length);
        System.arraycopy(infoOut.toByteArray(), 0, img, installInfoLba * sectorSize, infoOut.size());

        writeFile(isoFile, img);
        return isoFile;
    }

    @Test
    public void detectsAndExtractsEscalayerRebootIsoWithBloatPruning() throws Exception {
        File iso = buildEscalayerRebootIso(tmp.newFile("escalayer_reboot.iso"));

        assertTrue(Iso9660Extractor.INSTANCE.containsGameRoot(iso, "EscalayerReboot.ain", GameCatalog.ID_ESCALAYER_REBOOT));
        assertFalse(Iso9660Extractor.INSTANCE.containsGameRoot(iso, "DOKI_SA.ALD", GameCatalog.ID_ESCALAYER));
        assertFalse(Iso9660Extractor.INSTANCE.containsGameRoot(iso, null, GameCatalog.ID_ESCALAYER));
        assertTrue(Iso9660Extractor.INSTANCE.containsGameRoot(iso, null, null));

        File extractedDir = tmp.newFolder("extracted_iso");
        Function1<String, Boolean> skip = rel ->
                NativeGameImport.INSTANCE.skip(rel) || GameListKt.isEscalayerRebootBloatRelPath(rel);
        int count = Iso9660Extractor.INSTANCE.extractFileToDirectory(
                iso,
                extractedDir,
                skip,
                false,
                null,
                null,
                null
        );
        assertEquals(4, count);

        File gameRoot = GameListKt.findGameRootDirInternal(
                extractedDir,
                "EscalayerReboot.ain",
                GameCatalog.ID_ESCALAYER_REBOOT
        );
        assertNotNull(gameRoot);
        assertEquals(new File(extractedDir, "EscalayerReboot"), gameRoot);
        assertTrue(new File(gameRoot, "AliceStart.ini").isFile());
        assertTrue(new File(gameRoot, "EscalayerReboot.ain").isFile());
        assertTrue(new File(gameRoot, "Movie/Opening.alm").isFile());
        assertTrue(new File(gameRoot, "InstallInfo/Installer.ini").isFile());

        assertFalse(new File(gameRoot, "EscalayerReboot.exe").exists());
        assertFalse(new File(gameRoot, "OpenSaveFolder.exe").exists());
        assertFalse(new File(gameRoot, "DLL").exists());
        assertFalse(new File(gameRoot, "备份文件").exists());
    }

    @Test
    public void exposesExpectedOnlineCdnUrlBaseSizeAndStagingEviction() throws Exception {
        assertEquals("https://alicehub.000.moe/escalayerreboot", OnlineInstaller.ESCALAYER_REBOOT_CDN_URL);
        assertEquals(
                2470174002L,
                AppSettings.INSTANCE.defaultBaseGameSizeForUrl(OnlineInstaller.ESCALAYER_REBOOT_CDN_URL)
        );
        assertEquals(
                1563994084L,
                AppSettings.INSTANCE.defaultBaseGameSizeForUrl(OnlineInstaller.ESCALAYER_CDN_URL)
        );

        File storage = tmp.newFolder("storage");
        File escStaging = new File(storage, ".online_base_staging_Escalayer");
        File rebootStaging = new File(storage, ".online_base_staging_EscalayerReboot");
        assertTrue(escStaging.mkdirs());
        assertTrue(rebootStaging.mkdirs());

        List<File> evictedEsc = OnlineInstaller.INSTANCE.evictBaseStagingToTrash(storage, GameCatalog.ID_ESCALAYER, null);
        assertEquals(1, evictedEsc.size());
        assertFalse(escStaging.exists());
        assertTrue(rebootStaging.exists());

        assertTrue(escStaging.mkdirs());
        List<File> evictedReboot = OnlineInstaller.INSTANCE.evictBaseStagingToTrash(storage, GameCatalog.ID_ESCALAYER_REBOOT, null);
        assertEquals(1, evictedReboot.size());
        assertFalse(rebootStaging.exists());
        assertTrue(escStaging.exists());
    }
}

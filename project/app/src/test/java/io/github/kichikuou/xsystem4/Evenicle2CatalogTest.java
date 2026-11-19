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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import kotlin.jvm.functions.Function1;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class Evenicle2CatalogTest {
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
    public void classifiesEvenicle2FilesAndDisambiguatesFromEvenicle1() throws Exception {
        File e2Dir = tmp.newFolder("Evenicle2");
        writeFile(new File(e2Dir, "AliceStart.ini"), "GameName = \"イブニクル２\"\n".getBytes(StandardCharsets.UTF_8));
        writeFile(new File(e2Dir, "Evenicle2.ain"), new byte[]{0});

        File e2Lower = tmp.newFolder("CustomNamedFolder");
        writeFile(new File(e2Lower, "AliceStart.ini"), "GameName = \"Evenicle\"\n".getBytes(StandardCharsets.UTF_8));
        writeFile(new File(e2Lower, "evenicle2.ain"), new byte[]{0});

        File e1Dir = tmp.newFolder("Evenicle");
        writeFile(new File(e1Dir, "AliceStart.ini"), "GameName = \"イブニクル\"\n".getBytes(StandardCharsets.UTF_8));
        writeFile(new File(e1Dir, "Evenicle.ain"), new byte[]{0});

        File bonusDir = tmp.newFolder("Evenicle2Rance");
        writeFile(new File(bonusDir, "AliceStart.ini"), "GameName = \"Evenicle2Rance\"\n".getBytes(StandardCharsets.UTF_8));
        writeFile(new File(bonusDir, "Evenicle2Rance.ain"), new byte[]{0});

        assertEquals(
                GameCatalog.ID_EVENICLE2,
                GameCatalog.INSTANCE.classifyDirectory(e2Dir, e2Dir.getName(), null, null)
        );
        assertEquals(
                GameCatalog.ID_EVENICLE2,
                GameCatalog.INSTANCE.classifyDirectory(e2Lower, e2Lower.getName(), null, null)
        );
        assertEquals(
                GameCatalog.ID_EVENICLE,
                GameCatalog.INSTANCE.classifyDirectory(e1Dir, e1Dir.getName(), null, null)
        );
        assertEquals(
                GameCatalog.PREFIX_CUSTOM + "Evenicle2Rance",
                GameCatalog.INSTANCE.classifyDirectory(bonusDir, bonusDir.getName(), null, null)
        );

        assertTrue(GameCatalog.INSTANCE.validateImportedGameDir(e2Dir, "Evenicle2.ain", GameCatalog.ID_EVENICLE2));
        assertTrue(GameCatalog.INSTANCE.validateImportedGameDir(e2Lower, "Evenicle2.ain", GameCatalog.ID_EVENICLE2));
        assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(e2Dir, "Evenicle.ain", GameCatalog.ID_EVENICLE));
        assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(e2Dir, null, GameCatalog.ID_EVENICLE));
        assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(e1Dir, "Evenicle2.ain", GameCatalog.ID_EVENICLE2));
        assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(e1Dir, null, GameCatalog.ID_EVENICLE2));
        assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(bonusDir, "Evenicle2.ain", GameCatalog.ID_EVENICLE2));
        assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(bonusDir, null, GameCatalog.ID_EVENICLE2));
    }

    @Test
    public void classifiesAliasesAndSaveDirsWithoutShadowing() throws Exception {
        String[] e2Aliases = {
                "Evenicle2", "Evenicle2-1", "Evenicle 2", "Evenicle II",
                "イブニクル2", "イブニクル２", "夏娃年代记2", "夏娃2"
        };
        for (String alias : e2Aliases) {
            assertEquals(
                    "dir alias failed: " + alias,
                    GameCatalog.ID_EVENICLE2,
                    GameCatalog.INSTANCE.classifyDirectory(null, alias, null, null)
            );
            assertEquals(
                    "display alias failed: " + alias,
                    GameCatalog.ID_EVENICLE2,
                    GameCatalog.INSTANCE.classifyDirectory(null, "custom", null, alias)
            );
            assertTrue("isEvenicle2Alias failed: " + alias, GameCatalog.INSTANCE.isEvenicle2Alias(alias.toLowerCase()));
        }

        String[] e1Aliases = {"Evenicle", "Evenicle-1", "Evenicle-2", "イブニクル", "夏娃年代记"};
        for (String alias : e1Aliases) {
            assertEquals(
                    "Evenicle1 dir shadowed: " + alias,
                    GameCatalog.ID_EVENICLE,
                    GameCatalog.INSTANCE.classifyDirectory(null, alias, null, null)
            );
            assertEquals(
                    "Evenicle1 display shadowed: " + alias,
                    GameCatalog.ID_EVENICLE,
                    GameCatalog.INSTANCE.classifyDirectory(null, "custom", null, alias)
            );
            assertFalse("Evenicle1 falsely matched isEvenicle2Alias: " + alias,
                    GameCatalog.INSTANCE.isEvenicle2Alias(alias.toLowerCase()));
        }

        // Save directory disambiguation using the real runtime output files of Evenicle 2
        // (including CommonSystemData.sav, 3DConfig.vsf, AFConfig.asd, CommonOtherData.sav,
        // Thumb0000.qnt, and GameName-prefixed イブニクル２AF*.asd).
        File e2Save = tmp.newFolder("e2_save");
        writeFile(new File(e2Save, "3DConfig.vsf"), new byte[]{1});
        writeFile(new File(e2Save, "AFConfig.asd"), new byte[]{1});
        writeFile(new File(e2Save, "CommonOtherData.sav"), new byte[]{1});
        writeFile(new File(e2Save, "CommonSystemData.sav"), new byte[]{1});
        writeFile(new File(e2Save, "MsgSkip.asd"), new byte[]{1});
        writeFile(new File(e2Save, "Thumb0000.qnt"), new byte[]{1});
        writeFile(new File(e2Save, "WindowSetting.json"), new byte[]{1});
        writeFile(new File(e2Save, "イブニクル２AFBGMMode.asd"), new byte[]{1});
        writeFile(new File(e2Save, "イブニクル２AFCGMode.asd"), new byte[]{1});
        writeFile(new File(e2Save, "イブニクル２AFInfo.asd"), new byte[]{1});
        assertEquals(
                GameCatalog.ID_EVENICLE2,
                GameCatalog.INSTANCE.classifyDirectory(null, "UnknownDir", e2Save, null)
        );
        assertTrue(GameModifierBridge.INSTANCE.isEvenicle2GameDir(null, "UnknownDir", e2Save));
        assertFalse(GameModifierBridge.INSTANCE.isEvenicleGameDir(null, "イブニクル2", e2Save));
        assertFalse(GameModifierBridge.INSTANCE.isDohnaDohnaGameDir(null, "UnknownDir", e2Save));
        assertFalse(GameModifierBridge.INSTANCE.isRance09GameDir(null, "UnknownDir", e2Save));
        assertFalse(GameModifierBridge.INSTANCE.isRance03GameDir(null, "UnknownDir", e2Save));

        // Also verify SJIS-as-GBK mojibake-repaired イブニクル２AF*.asd save dirs (e.g. Windows import)
        File e2MojibakeSave = tmp.newFolder("e2_mojibake_save");
        writeFile(new File(e2MojibakeSave, "僀僽僯僋儖俀AFInfo.asd"), new byte[]{1});
        assertEquals(
                GameCatalog.ID_EVENICLE2,
                GameCatalog.INSTANCE.classifyDirectory(null, "UnknownDir", e2MojibakeSave, null)
        );

        // Escalayer Reboot saves must NOT be misclassified as Evenicle 2
        File erSave = tmp.newFolder("er_save");
        writeFile(new File(erSave, "CommonSystemData.sav"), new byte[]{1});
        writeFile(new File(erSave, "AFConfig.asd"), new byte[]{1});
        writeFile(new File(erSave, "Esca.sav"), new byte[]{1});
        writeFile(new File(erSave, "EscalayerReboot_87.asd"), new byte[]{1});
        writeFile(new File(erSave, "EscalayerReboot_87.vsf"), new byte[]{1});
        assertFalse(
                GameCatalog.ID_EVENICLE2.equals(
                        GameCatalog.INSTANCE.classifyDirectory(null, "UnknownDir", erSave, null)
                )
        );
        assertFalse(GameModifierBridge.INSTANCE.isEvenicle2GameDir(null, "UnknownDir", erSave));

        // Rance 09 saves must still classify as Rance 09
        File r09Save = tmp.newFolder("r09_save");
        writeFile(new File(r09Save, "AFConfig.asd"), new byte[]{1});
        writeFile(new File(r09Save, "AFCGMode.asd"), new byte[]{1});
        writeFile(new File(r09Save, "Thumb0000.qnt"), new byte[]{1});
        assertEquals(
                GameCatalog.ID_RANCE09,
                GameCatalog.INSTANCE.classifyDirectory(null, "UnknownDir", r09Save, null)
        );
        assertTrue(GameModifierBridge.INSTANCE.isRance09GameDir(null, "UnknownDir", r09Save));

        File e1Save = tmp.newFolder("e1_save");
        writeFile(new File(e1Save, "CommonSystemData.sav"), new byte[]{1});
        writeFile(new File(e1Save, "CommonOtherData.sav"), new byte[]{1});
        assertEquals(
                GameCatalog.ID_EVENICLE,
                GameCatalog.INSTANCE.classifyDirectory(null, "UnknownDir", e1Save, null)
        );
        assertTrue(GameModifierBridge.INSTANCE.isEvenicleGameDir(null, "UnknownDir", e1Save));
        assertFalse(GameModifierBridge.INSTANCE.isEvenicle2GameDir(null, "UnknownDir", e1Save));

        File ddSave = tmp.newFolder("dd_save");
        writeFile(new File(ddSave, "GlobalSaveData.asd"), new byte[]{1});
        writeFile(new File(ddSave, "AFInfo.asd"), new byte[]{1});
        assertEquals(
                GameCatalog.ID_DOHNA_DOHNA,
                GameCatalog.INSTANCE.classifyDirectory(null, "UnknownDir", ddSave, null)
        );
        assertTrue(GameModifierBridge.INSTANCE.isDohnaDohnaGameDir(null, "UnknownDir", ddSave));
        assertFalse(GameModifierBridge.INSTANCE.isEvenicle2GameDir(null, "UnknownDir", ddSave));
    }

    @Test
    public void stripsEvenicle2BloatAndMapsImportBaseName() throws Exception {
        String[] bloatPaths = {
                "Evenicle2.exe",
                "InstallSet/InstallData/Evenicle2.exe",
                "OpenSaveFolder.exe",
                "ResetConfig.exe",
                "Uninstaller.exe",
                "InstallSet/Installer.exe",
                "InstallSet/Installer.ini",
                "InstallSet/InstallFiles.ini",
                "InstallSet/RegClean.exe",
                "StartMenu.exe",
                "Autorun.inf",
                "InstallSet/StartMenu.bmp",
                "DirectX/DXSETUP.exe"
        };
        for (String rel : bloatPaths) {
            assertTrue("expected bloat: " + rel, GameListKt.isEvenicle2BloatRelPath(rel));
        }

        String[] keepPaths = {
                "AliceStart.ini",
                "Evenicle2.ain",
                "Evenicle23D.afa",
                "Evenicle2CG.afa",
                "Evenicle2EX.ex",
                "Evenicle2Flat.afa",
                "Evenicle2Font.fnl",
                "Evenicle2Pact.afa",
                "Evenicle2Sound.afa",
                "Evenicle2Voice.afa",
                "Data/Shader.slk",
                "Manual/index.html",
                "Manual/img/bnr_evenicle2.jpg",
                "Movie/Evenicle2_OP.apeg",
                "License/Copyright",
                "Version.txt"
        };
        for (String rel : keepPaths) {
            assertFalse("must preserve: " + rel, GameListKt.isEvenicle2BloatRelPath(rel));
        }

        File gameRoot = tmp.newFolder("Evenicle2Prune");
        writeFile(new File(gameRoot, "AliceStart.ini"), "GameName = \"イブニクル２\"\n".getBytes(StandardCharsets.UTF_8));
        writeFile(new File(gameRoot, "Evenicle2.ain"), new byte[]{1, 2});
        writeFile(new File(gameRoot, "Data/Shader.slk"), new byte[]{3, 4});
        writeFile(new File(gameRoot, "Movie/Evenicle2_OP.apeg"), new byte[]{5, 6});
        writeFile(new File(gameRoot, "Manual/index.html"), new byte[]{7, 8});
        writeFile(new File(gameRoot, "Evenicle2.exe"), new byte[]{9});
        writeFile(new File(gameRoot, "OpenSaveFolder.exe"), new byte[]{9});
        writeFile(new File(gameRoot, "ResetConfig.exe"), new byte[]{9});
        writeFile(new File(gameRoot, "Uninstaller.exe"), new byte[]{9});
        writeFile(new File(gameRoot, "Autorun.inf"), new byte[]{9});
        writeFile(new File(gameRoot, "StartMenu.bmp"), new byte[]{9});
        writeFile(new File(gameRoot, "DirectX/dxsetup.exe"), new byte[]{9});

        GameListKt.pruneRedundantBaseBloat(gameRoot);

        assertTrue(new File(gameRoot, "AliceStart.ini").isFile());
        assertTrue(new File(gameRoot, "Evenicle2.ain").isFile());
        assertTrue(new File(gameRoot, "Data/Shader.slk").isFile());
        assertTrue(new File(gameRoot, "Movie/Evenicle2_OP.apeg").isFile());
        assertTrue(new File(gameRoot, "Manual/index.html").isFile());
        assertFalse(new File(gameRoot, "Evenicle2.exe").exists());
        assertFalse(new File(gameRoot, "OpenSaveFolder.exe").exists());
        assertFalse(new File(gameRoot, "ResetConfig.exe").exists());
        assertFalse(new File(gameRoot, "Uninstaller.exe").exists());
        assertFalse(new File(gameRoot, "Autorun.inf").exists());
        assertFalse(new File(gameRoot, "StartMenu.bmp").exists());
        assertFalse(new File(gameRoot, "DirectX").exists());

        assertEquals(
                "Evenicle2",
                NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                        Arrays.asList("AliceStart.ini", "Evenicle2.ain"),
                        false
                )
        );
        assertEquals(
                "Evenicle2",
                NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                        Arrays.asList("AliceStart.ini", "evenicle2.ain"),
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

    private static File buildEvenicle2NestedIso(File isoFile, String ainName) throws Exception {
        final int sectorSize = 2048;
        final int rootLba = 18;
        final int installSetLba = 19;
        final int installDataLba = 20;
        final int dataDirLba = 21;
        final int movieDirLba = 22;
        final int fileDataLba = 23;
        final int totalSectors = 25;
        byte[] img = new byte[totalSectors * sectorSize];

        // Sector 16: Joliet SVD
        int vdOff = 16 * sectorSize;
        img[vdOff] = 2;
        img[vdOff + 1] = 'C';
        img[vdOff + 2] = 'D';
        img[vdOff + 3] = '0';
        img[vdOff + 4] = '0';
        img[vdOff + 5] = '1';
        img[vdOff + 88] = 0x25;
        img[vdOff + 89] = 0x2F;
        img[vdOff + 90] = 0x45;
        putLe32(img, vdOff + 158, rootLba);
        putLe32(img, vdOff + 166, sectorSize);

        // Sector 17: Volume Descriptor Set Terminator
        int termOff = 17 * sectorSize;
        img[termOff] = (byte) 255;
        img[termOff + 1] = 'C';
        img[termOff + 2] = 'D';
        img[termOff + 3] = '0';
        img[termOff + 4] = '0';
        img[termOff + 5] = '1';

        byte[] payload = "DATA".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(payload, 0, img, fileDataLba * sectorSize, payload.length);

        ByteArrayOutputStream rootOut = new ByteArrayOutputStream();
        writeDirRecord(rootOut, "Autorun.inf;1", false, fileDataLba, payload.length);
        writeDirRecord(rootOut, "StartMenu.exe;1", false, fileDataLba, payload.length);
        writeDirRecord(rootOut, "InstallSet", true, installSetLba, sectorSize);
        System.arraycopy(rootOut.toByteArray(), 0, img, rootLba * sectorSize, rootOut.size());

        ByteArrayOutputStream setOut = new ByteArrayOutputStream();
        writeDirRecord(setOut, "Installer.exe;1", false, fileDataLba, payload.length);
        writeDirRecord(setOut, "Installer.ini;1", false, fileDataLba, payload.length);
        writeDirRecord(setOut, "InstallFiles.ini;1", false, fileDataLba, payload.length);
        writeDirRecord(setOut, "RegClean.exe;1", false, fileDataLba, payload.length);
        writeDirRecord(setOut, "StartMenu.bmp;1", false, fileDataLba, payload.length);
        writeDirRecord(setOut, "InstallData", true, installDataLba, sectorSize);
        System.arraycopy(setOut.toByteArray(), 0, img, installSetLba * sectorSize, setOut.size());

        ByteArrayOutputStream dataOut = new ByteArrayOutputStream();
        writeDirRecord(dataOut, "AliceStart.ini;1", false, fileDataLba, payload.length);
        writeDirRecord(dataOut, ainName + ";1", false, fileDataLba, payload.length);
        writeDirRecord(dataOut, "Evenicle2.exe;1", false, fileDataLba, payload.length);
        writeDirRecord(dataOut, "OpenSaveFolder.exe;1", false, fileDataLba, payload.length);
        writeDirRecord(dataOut, "Version.txt;1", false, fileDataLba, payload.length);
        writeDirRecord(dataOut, "Data", true, dataDirLba, sectorSize);
        writeDirRecord(dataOut, "Movie", true, movieDirLba, sectorSize);
        System.arraycopy(dataOut.toByteArray(), 0, img, installDataLba * sectorSize, dataOut.size());

        ByteArrayOutputStream subDataOut = new ByteArrayOutputStream();
        writeDirRecord(subDataOut, "Shader.slk;1", false, fileDataLba, payload.length);
        System.arraycopy(subDataOut.toByteArray(), 0, img, dataDirLba * sectorSize, subDataOut.size());

        ByteArrayOutputStream movieOut = new ByteArrayOutputStream();
        writeDirRecord(movieOut, "Evenicle2_OP.apeg;1", false, fileDataLba, payload.length);
        System.arraycopy(movieOut.toByteArray(), 0, img, movieDirLba * sectorSize, movieOut.size());

        writeFile(isoFile, img);
        return isoFile;
    }

    @Test
    public void detectsAndExtractsNestedInstallSetInstallDataIso() throws Exception {
        File e2Iso = buildEvenicle2NestedIso(tmp.newFile("EVENICLE2.mdf"), "Evenicle2.ain");
        File bonusIso = buildEvenicle2NestedIso(tmp.newFile("EVENICLE2R.mdf"), "Evenicle2Rance.ain");

        assertTrue(Iso9660Extractor.INSTANCE.containsGameRoot(e2Iso, "Evenicle2.ain", GameCatalog.ID_EVENICLE2));
        assertFalse(Iso9660Extractor.INSTANCE.containsGameRoot(e2Iso, "Evenicle.ain", GameCatalog.ID_EVENICLE));
        assertFalse(Iso9660Extractor.INSTANCE.containsGameRoot(e2Iso, null, GameCatalog.ID_EVENICLE));
        assertTrue(Iso9660Extractor.INSTANCE.containsGameRoot(e2Iso, null, null));

        assertFalse(Iso9660Extractor.INSTANCE.containsGameRoot(bonusIso, "Evenicle2.ain", GameCatalog.ID_EVENICLE2));

        File extractedDir = tmp.newFolder("extracted_iso");
        Function1<String, Boolean> skip = rel ->
                NativeGameImport.INSTANCE.skip(rel) || GameListKt.isEvenicle2BloatRelPath(rel);
        int count = Iso9660Extractor.INSTANCE.extractFileToDirectory(
                e2Iso,
                extractedDir,
                skip,
                false,
                null,
                null,
                null
        );
        assertEquals(5, count);

        File gameRoot = GameListKt.findGameRootDirInternal(
                extractedDir,
                "Evenicle2.ain",
                GameCatalog.ID_EVENICLE2
        );
        assertNotNull(gameRoot);
        assertEquals(new File(extractedDir, "InstallSet/InstallData"), gameRoot);
        assertTrue(new File(gameRoot, "AliceStart.ini").isFile());
        assertTrue(new File(gameRoot, "Evenicle2.ain").isFile());
        assertTrue(new File(gameRoot, "Version.txt").isFile());
        assertTrue(new File(gameRoot, "Data/Shader.slk").isFile());
        assertTrue(new File(gameRoot, "Movie/Evenicle2_OP.apeg").isFile());

        assertFalse(new File(extractedDir, "Autorun.inf").exists());
        assertFalse(new File(extractedDir, "StartMenu.exe").exists());
        assertFalse(new File(extractedDir, "InstallSet/Installer.exe").exists());
        assertFalse(new File(extractedDir, "InstallSet/StartMenu.bmp").exists());
        assertFalse(new File(gameRoot, "Evenicle2.exe").exists());
        assertFalse(new File(gameRoot, "OpenSaveFolder.exe").exists());
    }

    @Test
    public void exposesExpectedOnlineCdnUrlAndBaseSize() {
        assertEquals("https://alicehub.000.moe/evenicle2", OnlineInstaller.EVENICLE2_CDN_URL);
        assertEquals(
                3399191886L,
                AppSettings.INSTANCE.defaultBaseGameSizeForUrl(OnlineInstaller.EVENICLE2_CDN_URL)
        );
        assertEquals(
                2453622110L,
                AppSettings.INSTANCE.defaultBaseGameSizeForUrl(OnlineInstaller.EVENICLE_CDN_URL)
        );
    }

    @Test
    public void exposesCnPatchDescriptorAndOverlayCatalogConfig() {
        GameDescriptor e2 = GameCatalog.INSTANCE.descriptorForWithResolver(
                GameCatalog.ID_EVENICLE2,
                null,
                resId -> "res:" + resId
        );
        assertEquals(GameCatalog.ID_EVENICLE2, e2.getId());
        assertEquals("Evenicle2", e2.getDefaultDirName());
        assertEquals("Evenicle2.ain", e2.getExpectedAinName());
        assertTrue(e2.getSupportsOnlineBaseInstall());
        assertEquals(OnlineInstaller.EVENICLE2_CDN_URL, e2.getOnlineBaseCdnUrl());
        assertTrue(e2.getSupportsOnlineMod());
        assertTrue(e2.getSupportsLocalMod());
        assertFalse(e2.isRance10());
        assertTrue(e2.isCnPatch());
        assertEquals(OnlineInstaller.EVENICLE2_CDN_URL, e2.getOverlayCdnBaseUrl());
        assertEquals(OnlineInstaller.EVENICLE2_CDN_URL, e2.getEffectiveOverlayCdnUrl());
        assertEquals("files/cn-patch/index.json", e2.getOverlayCatalogRelPath());
        assertEquals(InstalledModInfo.KIND_CN_PATCH, e2.getOverlayKind());

        GameDescriptor e1 = GameCatalog.INSTANCE.descriptorForWithResolver(
                GameCatalog.ID_EVENICLE,
                null,
                resId -> "res:" + resId
        );
        assertTrue(e1.isCnPatch());
        assertEquals(OnlineInstaller.EVENICLE_CDN_URL, e1.getOverlayCdnBaseUrl());
        assertEquals("files/cn-patch/index.json", e1.getOverlayCatalogRelPath());
    }

    @Test
    public void installsAndUninstallsEvenicle2CnPatchWithBackupsAndClassification() throws Exception {
        File gameDir = tmp.newFolder("Evenicle2");
        byte[] iniBytes = "GameName = \"イブニクル２\"\r\nCodeName = \"Evenicle2.ain\"\r\n"
                .getBytes(Charset.forName("Shift_JIS"));
        byte[] vanillaAin = "VANILLA_AIN".getBytes(StandardCharsets.US_ASCII);
        byte[] vanillaEx = "VANILLA_EX".getBytes(StandardCharsets.US_ASCII);
        byte[] vanillaCg = "VANILLA_CG".getBytes(StandardCharsets.US_ASCII);
        byte[] vanillaFlat = "VANILLA_FLAT".getBytes(StandardCharsets.US_ASCII);
        byte[] vanillaPact = "VANILLA_PACT".getBytes(StandardCharsets.US_ASCII);

        writeFile(new File(gameDir, "AliceStart.ini"), iniBytes);
        writeFile(new File(gameDir, "Evenicle2.ain"), vanillaAin);
        writeFile(new File(gameDir, "Evenicle2EX.ex"), vanillaEx);
        writeFile(new File(gameDir, "Evenicle2CG.afa"), vanillaCg);
        writeFile(new File(gameDir, "Evenicle2Flat.afa"), vanillaFlat);
        writeFile(new File(gameDir, "Evenicle2Pact.afa"), vanillaPact);

        File staging = tmp.newFolder("cn_patch_online_staging");
        byte[] cnAin = "CN_V102_AIN".getBytes(StandardCharsets.US_ASCII);
        byte[] cnEx = "CN_V102_EX".getBytes(StandardCharsets.US_ASCII);
        byte[] cnCg = "CN_V10_CG".getBytes(StandardCharsets.US_ASCII);
        byte[] cnFlat = "CN_FLAT".getBytes(StandardCharsets.US_ASCII);
        byte[] cnWordMai = "CN_WORD_MAI".getBytes(StandardCharsets.US_ASCII);

        writeFile(new File(staging, "Evenicle2.ain"), cnAin);
        writeFile(new File(staging, "Evenicle2EX.ex"), cnEx);
        writeFile(new File(staging, "Evenicle2CG.afa"), cnCg);
        writeFile(new File(staging, "Evenicle2Flat.afa"), cnFlat);
        writeFile(new File(staging, "mai/word.mai"), cnWordMai);

        boolean wasFull = GameListKt.applyModStagingToGameDirInternal(staging, gameDir, true, true, false);
        assertTrue(wasFull);

        // Simulate metadata written after online install
        writeFile(
                new File(gameDir, InstalledModInfo.META_FILE_NAME),
                "{\"version\":\"V1.02\",\"kind\":\"cn_patch\"}".getBytes(StandardCharsets.UTF_8)
        );

        // Classification and validation still recognize Evenicle 2 after CN patch overlay
        assertEquals(
                GameCatalog.ID_EVENICLE2,
                GameCatalog.INSTANCE.classifyDirectory(gameDir, gameDir.getName(), null, null)
        );
        assertTrue(GameCatalog.INSTANCE.validateImportedGameDir(gameDir, "Evenicle2.ain", GameCatalog.ID_EVENICLE2));
        assertTrue(Item.Companion.detectInstalledMod(gameDir, null));
        assertTrue(Item.Companion.canRestoreVanillaDir(gameDir));

        // Overwritten vanilla files are backed up and CN files are active
        assertArrayEquals(cnAin, Files.readAllBytes(new File(gameDir, "Evenicle2.ain").toPath()));
        assertArrayEquals(cnEx, Files.readAllBytes(new File(gameDir, "Evenicle2EX.ex").toPath()));
        assertArrayEquals(cnCg, Files.readAllBytes(new File(gameDir, "Evenicle2CG.afa").toPath()));
        assertArrayEquals(cnFlat, Files.readAllBytes(new File(gameDir, "Evenicle2Flat.afa").toPath()));
        assertArrayEquals(cnWordMai, Files.readAllBytes(new File(gameDir, "mai/word.mai").toPath()));
        assertArrayEquals(vanillaPact, Files.readAllBytes(new File(gameDir, "Evenicle2Pact.afa").toPath()));

        assertArrayEquals(vanillaAin, Files.readAllBytes(new File(gameDir, "Evenicle2.ain.vanilla.bak").toPath()));
        assertArrayEquals(vanillaEx, Files.readAllBytes(new File(gameDir, "Evenicle2EX.ex.vanilla.bak").toPath()));
        assertArrayEquals(vanillaCg, Files.readAllBytes(new File(gameDir, "Evenicle2CG.afa.vanilla.bak").toPath()));
        assertArrayEquals(vanillaFlat, Files.readAllBytes(new File(gameDir, "Evenicle2Flat.afa.vanilla.bak").toPath()));
        assertFalse(new File(gameDir, "Evenicle2Pact.afa.vanilla.bak").exists());
        assertFalse(new File(gameDir, "mai/word.mai.vanilla.bak").exists());

        String manifestContent = new String(
                Files.readAllBytes(new File(gameDir, ".mod_installed_files.txt").toPath()),
                StandardCharsets.UTF_8
        );
        assertTrue(manifestContent.contains("mai/word.mai"));

        // Uninstall restores original Japanese files and deletes added CN files + empty mai/ dir
        GameListKt.restoreVanillaFromModDir(gameDir);

        assertArrayEquals(vanillaAin, Files.readAllBytes(new File(gameDir, "Evenicle2.ain").toPath()));
        assertArrayEquals(vanillaEx, Files.readAllBytes(new File(gameDir, "Evenicle2EX.ex").toPath()));
        assertArrayEquals(vanillaCg, Files.readAllBytes(new File(gameDir, "Evenicle2CG.afa").toPath()));
        assertArrayEquals(vanillaFlat, Files.readAllBytes(new File(gameDir, "Evenicle2Flat.afa").toPath()));
        assertArrayEquals(vanillaPact, Files.readAllBytes(new File(gameDir, "Evenicle2Pact.afa").toPath()));

        assertFalse(new File(gameDir, "mai/word.mai").exists());
        assertFalse(new File(gameDir, "mai").exists());
        assertFalse(new File(gameDir, "Evenicle2.ain.vanilla.bak").exists());
        assertFalse(new File(gameDir, ".mod_installed_files.txt").exists());
        assertFalse(new File(gameDir, InstalledModInfo.META_FILE_NAME).exists());
        assertFalse(Item.Companion.detectInstalledMod(gameDir, null));
        assertFalse(Item.Companion.canRestoreVanillaDir(gameDir));
        assertEquals(
                GameCatalog.ID_EVENICLE2,
                GameCatalog.INSTANCE.classifyDirectory(gameDir, gameDir.getName(), null, null)
        );
    }

    @Test
    public void normalizesLocalPcMaiScCnPatchAndSupportsIncrementalUpdate() throws Exception {
        File gameDir = tmp.newFolder("Evenicle2Local");
        byte[] vanillaAin = "VANILLA_AIN".getBytes(StandardCharsets.US_ASCII);
        byte[] vanillaEx = "VANILLA_EX".getBytes(StandardCharsets.US_ASCII);
        byte[] vanillaCg = "VANILLA_CG".getBytes(StandardCharsets.US_ASCII);
        writeFile(new File(gameDir, "AliceStart.ini"), "GameName = \"イブニクル２\"\n".getBytes(StandardCharsets.UTF_8));
        writeFile(new File(gameDir, "Evenicle2.ain"), vanillaAin);
        writeFile(new File(gameDir, "Evenicle2EX.ex"), vanillaEx);
        writeFile(new File(gameDir, "Evenicle2CG.afa"), vanillaCg);

        // Local PC V1.0 zip layout: CG at root + ain/ex inside mai/sc/ + Windows junk
        File v10Staging = tmp.newFolder("pc_v10_staging");
        byte[] v10Cg = "V10_CG".getBytes(StandardCharsets.US_ASCII);
        byte[] v10Ain = "V10_AIN".getBytes(StandardCharsets.US_ASCII);
        byte[] v10Ex = "V10_EX".getBytes(StandardCharsets.US_ASCII);
        byte[] v10Word = "V10_WORD".getBytes(StandardCharsets.US_ASCII);
        writeFile(new File(v10Staging, "Evenicle2CG.afa"), v10Cg);
        writeFile(new File(v10Staging, "Evenicle2_cn.exe"), new byte[]{1});
        writeFile(new File(v10Staging, "mai.dll"), new byte[]{2});
        writeFile(new File(v10Staging, "libfusionex.dll"), new byte[]{3});
        writeFile(new File(v10Staging, "Staff.png"), new byte[]{4});
        writeFile(new File(v10Staging, "请读我（转载请务必保留）.png"), new byte[]{5});
        writeFile(new File(v10Staging, "9E4FAB614BE2E943BE448BA7E89CFFD68BEEBDE0.torrent"), new byte[]{6});
        writeFile(new File(v10Staging, "mai/word.mai"), v10Word);
        writeFile(new File(v10Staging, "mai/sc/Evenicle2.ain"), v10Ain);
        writeFile(new File(v10Staging, "mai/sc/Evenicle2EX.ex"), v10Ex);

        assertTrue(GameListKt.applyModStagingToGameDirInternal(v10Staging, gameDir, true, true, true));
        assertArrayEquals(v10Cg, Files.readAllBytes(new File(gameDir, "Evenicle2CG.afa").toPath()));
        assertArrayEquals(v10Ain, Files.readAllBytes(new File(gameDir, "Evenicle2.ain").toPath()));
        assertArrayEquals(v10Ex, Files.readAllBytes(new File(gameDir, "Evenicle2EX.ex").toPath()));
        assertArrayEquals(v10Word, Files.readAllBytes(new File(gameDir, "mai/word.mai").toPath()));
        assertFalse(new File(gameDir, "Evenicle2_cn.exe").exists());
        assertFalse(new File(gameDir, "mai.dll").exists());
        assertFalse(new File(gameDir, "libfusionex.dll").exists());
        assertFalse(new File(gameDir, "Staff.png").exists());
        assertFalse(new File(gameDir, "请读我（转载请务必保留）.png").exists());
        assertFalse(new File(gameDir, "9E4FAB614BE2E943BE448BA7E89CFFD68BEEBDE0.torrent").exists());
        assertFalse(new File(gameDir, "mai/sc").exists());

        // Local PC V1.02 incremental zip layout: only mai/word.mai + mai/sc/{ain,ex} + Windows junk
        File v102Staging = tmp.newFolder("pc_v102_staging");
        byte[] v102Ain = "V102_AIN".getBytes(StandardCharsets.US_ASCII);
        byte[] v102Ex = "V102_EX".getBytes(StandardCharsets.US_ASCII);
        byte[] v102Word = "V102_WORD".getBytes(StandardCharsets.US_ASCII);
        writeFile(new File(v102Staging, "Evenicle2_cn.exe"), new byte[]{1});
        writeFile(new File(v102Staging, "mai.dll"), new byte[]{2});
        writeFile(new File(v102Staging, "mai/word.mai"), v102Word);
        writeFile(new File(v102Staging, "mai/sc/Evenicle2.ain"), v102Ain);
        writeFile(new File(v102Staging, "mai/sc/Evenicle2EX.ex"), v102Ex);

        assertFalse(GameListKt.applyModStagingToGameDirInternal(v102Staging, gameDir, true, true, true));
        assertArrayEquals(v10Cg, Files.readAllBytes(new File(gameDir, "Evenicle2CG.afa").toPath()));
        assertArrayEquals(v102Ain, Files.readAllBytes(new File(gameDir, "Evenicle2.ain").toPath()));
        assertArrayEquals(v102Ex, Files.readAllBytes(new File(gameDir, "Evenicle2EX.ex").toPath()));
        assertArrayEquals(v102Word, Files.readAllBytes(new File(gameDir, "mai/word.mai").toPath()));
        assertArrayEquals(vanillaAin, Files.readAllBytes(new File(gameDir, "Evenicle2.ain.vanilla.bak").toPath()));
        assertArrayEquals(vanillaEx, Files.readAllBytes(new File(gameDir, "Evenicle2EX.ex.vanilla.bak").toPath()));
        assertArrayEquals(vanillaCg, Files.readAllBytes(new File(gameDir, "Evenicle2CG.afa.vanilla.bak").toPath()));

        GameListKt.restoreVanillaFromModDir(gameDir);
        assertArrayEquals(vanillaAin, Files.readAllBytes(new File(gameDir, "Evenicle2.ain").toPath()));
        assertArrayEquals(vanillaEx, Files.readAllBytes(new File(gameDir, "Evenicle2EX.ex").toPath()));
        assertArrayEquals(vanillaCg, Files.readAllBytes(new File(gameDir, "Evenicle2CG.afa").toPath()));
        assertFalse(new File(gameDir, "mai").exists());
    }

    @Test
    public void infersAndMatchesCnPatchVersionsForLocalAndOnlineCatalog() throws Exception {
        File iniDir = tmp.newFolder("e2_ini");
        writeFile(
                new File(iniDir, "AliceStart.ini"),
                "GameName = \"夏娃年代记2 汉化版 V1.0\"\n".getBytes(StandardCharsets.UTF_8)
        );
        assertEquals(
                "V1.02",
                OnlineInstaller.INSTANCE.inferLocalOverlayVersion(
                        "【天之圣杯汉化组】夏娃年代记2V1.02.zip",
                        false,
                        iniDir,
                        true
                )
        );
        assertEquals(
                "V1.02",
                OnlineInstaller.INSTANCE.inferLocalOverlayVersion(
                        "evenicle2-cn-v1.02.zip",
                        false,
                        iniDir,
                        true
                )
        );
        assertEquals(
                "V1.0",
                OnlineInstaller.INSTANCE.inferLocalOverlayVersion(
                        "cn_patch.zip",
                        false,
                        iniDir,
                        true
                )
        );

        ModReleaseEntry release = new ModReleaseEntry(
                1,
                "V1.02",
                "夏娃年代记2汉化补丁_V1.02.zip",
                "简体中文汉化补丁 V1.02",
                "changelog",
                "2019-09-27 15:56",
                "https://alicehub.000.moe/evenicle2/files/cn-patch/evenicle2-cn-v1.02.zip",
                false,
                null,
                null,
                562251373L,
                "b8d4ce1990245982195e6bc2a287368fd4a07d9b928296d4ee44b721fefae5ad",
                "天之圣杯汉化组"
        );
        List<ModReleaseEntry> catalog = Collections.singletonList(release);

        InstalledModInfo installedLatest = new InstalledModInfo(
                "V1.02",
                "V1.02",
                1,
                1,
                "夏娃年代记2汉化补丁_V1.02.zip",
                "2019-09-27 15:56",
                "online",
                1234L,
                InstalledModInfo.KIND_CN_PATCH
        );
        assertEquals(0, OnlineInstaller.INSTANCE.matchInstalledIndex(catalog, installedLatest));
        assertNull(OnlineInstaller.INSTANCE.computeUpdatePlan(catalog, true, installedLatest));

        InstalledModInfo installedLocalV102 = new InstalledModInfo(
                "V1.02",
                "V1.02",
                -1,
                -1,
                "【天之圣杯汉化组】夏娃年代记2V1.02.zip",
                "",
                "local",
                1234L,
                InstalledModInfo.KIND_CN_PATCH
        );
        assertEquals(0, OnlineInstaller.INSTANCE.matchInstalledIndex(catalog, installedLocalV102));
        assertNull(OnlineInstaller.INSTANCE.computeUpdatePlan(catalog, true, installedLocalV102));

        InstalledModInfo installedOld = new InstalledModInfo(
                "V1.0",
                "V1.0",
                -1,
                -1,
                "【天之圣杯汉化组】夏娃年代记2 V1.0汉化补丁.zip",
                "",
                "local",
                1234L,
                InstalledModInfo.KIND_CN_PATCH
        );
        assertEquals(-1, OnlineInstaller.INSTANCE.matchInstalledIndex(catalog, installedOld));
        ModUpdatePlan plan = OnlineInstaller.INSTANCE.computeUpdatePlan(catalog, true, installedOld);
        assertNotNull(plan);
        assertEquals(1, plan.getSteps().size());
        assertEquals(release, plan.getSteps().get(0));
    }
}

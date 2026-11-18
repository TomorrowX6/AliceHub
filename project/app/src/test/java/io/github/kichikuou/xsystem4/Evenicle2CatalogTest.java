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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import kotlin.jvm.functions.Function1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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

        // Save directory disambiguation: Evenicle 2 uses CommonOtherData.sav + AF*.asd (and even QC.asd),
        // never CommonSystemData.sav / GlobalSaveData.asd.
        File e2Save = tmp.newFolder("e2_save");
        writeFile(new File(e2Save, "CommonOtherData.sav"), new byte[]{1});
        writeFile(new File(e2Save, "AFCommon.asd"), new byte[]{1});
        writeFile(new File(e2Save, "AFConfig.asd"), new byte[]{1});
        writeFile(new File(e2Save, "AFInfo.asd"), new byte[]{1});
        writeFile(new File(e2Save, "AFMemMode.asd"), new byte[]{1});
        writeFile(new File(e2Save, "QC.asd"), new byte[]{1});
        assertEquals(
                GameCatalog.ID_EVENICLE2,
                GameCatalog.INSTANCE.classifyDirectory(null, "UnknownDir", e2Save, null)
        );
        assertTrue(GameModifierBridge.INSTANCE.isEvenicle2GameDir(null, "UnknownDir", e2Save));
        assertFalse(GameModifierBridge.INSTANCE.isEvenicleGameDir(null, "イブニクル2", e2Save));
        assertFalse(GameModifierBridge.INSTANCE.isDohnaDohnaGameDir(null, "UnknownDir", e2Save));

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
}

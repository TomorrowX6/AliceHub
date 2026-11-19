package io.github.kichikuou.xsystem4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DaiakujiCatalogTest {
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

    private static byte[] shiftJisSystem39Ini() {
        return ("; System 3.9\r\nRegist\t\t= \"大悪司\"\r\nUseCD\t\t= true\r\n")
                .getBytes(Charset.forName("Shift_JIS"));
    }

    @Test
    public void classifiesDaiakujiDirectoriesWithoutCollidingWithKichikuouOrEscalayer() throws Exception {
        File cnDir = tmp.newFolder("DAIAKUJICN");
        writeFile(new File(cnDir, "System39.ini"), shiftJisSystem39Ini());
        writeFile(new File(cnDir, "System39.ain"), "AINI".getBytes(StandardCharsets.US_ASCII));
        writeFile(new File(cnDir, "大悪司SA.ALD"), new byte[]{0});
        writeFile(new File(cnDir, "大悪司GA.ALD"), new byte[]{0});
        writeFile(new File(cnDir, "大悪司WA.ALD"), new byte[]{0});
        writeFile(new File(cnDir, "大悪司DA.ALD"), new byte[]{0});
        writeFile(new File(cnDir, "dDemo.alk"), new byte[]{0});
        writeFile(new File(cnDir, "_inmm.ini"), "bgm\\Daiakuji_02.wav\r\n".getBytes(StandardCharsets.US_ASCII));
        writeFile(new File(cnDir, "bgm/Daiakuji_02.wav"), new byte[]{0});

        assertTrue(GameCatalog.INSTANCE.isSystem3xGameDir(cnDir));
        assertTrue(GameCatalog.INSTANCE.isDaiakujiGameDir(cnDir));
        assertFalse(GameCatalog.INSTANCE.isKichikuouGameDir(cnDir));
        assertFalse(GameCatalog.INSTANCE.isEscalayerGameDir(cnDir));
        assertEquals(
                GameCatalog.ID_DAIAKUJI,
                GameCatalog.INSTANCE.classifyDirectory(cnDir, cnDir.getName(), null, null)
        );
        assertEquals("大悪司", System40Ini.Companion.parse(new File(cnDir, "System39.ini")).getGameName());

        File scDir = tmp.newFolder("GAMEDATA_SC");
        writeFile(new File(scDir, "大恶司SA.ALD"), new byte[]{0});
        assertTrue(GameCatalog.INSTANCE.isDaiakujiGameDir(scDir));
        assertEquals(
                GameCatalog.ID_DAIAKUJI,
                GameCatalog.INSTANCE.classifyDirectory(scDir, scDir.getName(), null, null)
        );

        File asciiDir = tmp.newFolder("GAMEDATA_ASCII");
        writeFile(new File(asciiDir, "DAIAKUJISA.ALD"), new byte[]{0});
        assertTrue(GameCatalog.INSTANCE.isDaiakujiGameDir(asciiDir));
        assertEquals(
                GameCatalog.ID_DAIAKUJI,
                GameCatalog.INSTANCE.classifyDirectory(asciiDir, asciiDir.getName(), null, null)
        );

        File mojibakeDir = tmp.newFolder("GAMEDATA_MOJI");
        writeFile(new File(mojibakeDir, "戭埆巌SA.ALD"), new byte[]{0});
        assertTrue(GameCatalog.INSTANCE.isDaiakujiGameDir(mojibakeDir));
        assertEquals(
                GameCatalog.ID_DAIAKUJI,
                GameCatalog.INSTANCE.classifyDirectory(mojibakeDir, mojibakeDir.getName(), null, null)
        );

        // Must never collide with Kichikuou or Escalayer even when using generic System 3.x markers.
        File kichikuDir = tmp.newFolder("KichikuRance");
        writeFile(new File(kichikuDir, "KichikuRanceSA.ALD"), new byte[]{0});
        assertFalse(GameCatalog.INSTANCE.isDaiakujiGameDir(kichikuDir));
        assertEquals(
                GameCatalog.ID_KICHIKUOU,
                GameCatalog.INSTANCE.classifyDirectory(kichikuDir, kichikuDir.getName(), null, null)
        );

        File escalayerDir = tmp.newFolder("Escalayer");
        writeFile(new File(escalayerDir, "DOKI_SA.ALD"), new byte[]{0});
        assertFalse(GameCatalog.INSTANCE.isDaiakujiGameDir(escalayerDir));
        assertEquals(
                GameCatalog.ID_ESCALAYER,
                GameCatalog.INSTANCE.classifyDirectory(escalayerDir, escalayerDir.getName(), null, null)
        );
    }

    @Test
    public void classifiesDaiakujiBySaveDirAndAliases() throws Exception {
        File saveJa = tmp.newFolder("DaiakujiSavesJa");
        writeFile(new File(saveJa, "大悪司SA.asd"), new byte[]{1, 2, 3});
        assertEquals(
                GameCatalog.ID_DAIAKUJI,
                GameCatalog.INSTANCE.classifyDirectory(null, "UnknownDir", saveJa, null)
        );

        File saveMoji = tmp.newFolder("DaiakujiSavesMoji");
        writeFile(new File(saveMoji, "戝埆巌SZ.ASD"), new byte[]{1, 2, 3});
        assertEquals(
                GameCatalog.ID_DAIAKUJI,
                GameCatalog.INSTANCE.classifyDirectory(null, "UnknownDir", saveMoji, null)
        );

        File saveGlobal = tmp.newFolder("GlobalCollision");
        writeFile(new File(saveGlobal, "GlobalSave.asd"), new byte[]{1});
        writeFile(new File(saveGlobal, "大悪司SA.asd"), new byte[]{1});
        assertFalse(GameCatalog.ID_DAIAKUJI.equals(
                GameCatalog.INSTANCE.classifyDirectory(null, "UnknownDir", saveGlobal, null)
        ));

        for (String dirAlias : Arrays.asList("大悪司", "大恶司", "DAIAKUJICN", "Daiakuji", "Daiakuji-1", "Daiakuzi")) {
            assertEquals(
                    GameCatalog.ID_DAIAKUJI,
                    GameCatalog.INSTANCE.classifyDirectory(null, dirAlias, null, null)
            );
        }
        assertEquals(
                GameCatalog.ID_DAIAKUJI,
                GameCatalog.INSTANCE.classifyDirectory(null, "custom", null, "大恶司 [汉化]")
        );
    }

    @Test
    public void validatesTargetGameDirectoriesEvenWhenExpectedAinIsNull() throws Exception {
        File daiaCn = tmp.newFolder("MyDaiakujiFolder");
        writeFile(new File(daiaCn, "大悪司SA.ALD"), new byte[]{0});

        File daiaByBgm = tmp.newFolder("Sys3WithDaiaBgm");
        writeFile(new File(daiaByBgm, "adisk.ald"), new byte[]{0});
        writeFile(new File(daiaByBgm, "bgm/Daiakuji_02.wav"), new byte[]{0});

        File daiaByInmm = tmp.newFolder("Sys3WithInmm");
        writeFile(new File(daiaByInmm, "adisk.ald"), new byte[]{0});
        writeFile(new File(daiaByInmm, "_inmm.ini"), "bgm\\Daiakuji_05.wav\r\n".getBytes(StandardCharsets.US_ASCII));

        File daiaByIni = tmp.newFolder("Sys3WithIni");
        writeFile(new File(daiaByIni, "adisk.ald"), new byte[]{0});
        writeFile(new File(daiaByIni, "System39.ini"), shiftJisSystem39Ini());

        // Tricky folder names matching Daiakuji aliases but containing other games must be rejected.
        File fakeSys3 = tmp.newFolder("Daiakuji");
        writeFile(new File(fakeSys3, "Rance4SA.ALD"), new byte[]{0});

        File fakeKichiku = tmp.newFolder("大悪司");
        writeFile(new File(fakeKichiku, "KichikuRanceSA.ALD"), new byte[]{0});

        File fakeEscalayer = tmp.newFolder("大恶司");
        writeFile(new File(fakeEscalayer, "DOKI_SA.ALD"), new byte[]{0});

        File fakeSys4 = tmp.newFolder("DAIAKUJICN_SYS4");
        writeFile(new File(fakeSys4, "AliceStart.ini"), "GameName = \"Other\"\n".getBytes(StandardCharsets.UTF_8));
        writeFile(new File(fakeSys4, "OtherGame.ain"), new byte[]{0});

        assertTrue(GameCatalog.INSTANCE.validateImportedGameDir(daiaCn, null, GameCatalog.ID_DAIAKUJI));
        assertTrue(GameCatalog.INSTANCE.validateImportedGameDir(daiaByBgm, null, GameCatalog.ID_DAIAKUJI));
        assertTrue(GameCatalog.INSTANCE.validateImportedGameDir(daiaByInmm, null, GameCatalog.ID_DAIAKUJI));
        assertTrue(GameCatalog.INSTANCE.validateImportedGameDir(daiaByIni, null, GameCatalog.ID_DAIAKUJI));

        assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(fakeSys3, null, GameCatalog.ID_DAIAKUJI));
        assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(fakeKichiku, null, GameCatalog.ID_DAIAKUJI));
        assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(fakeEscalayer, null, GameCatalog.ID_DAIAKUJI));
        assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(fakeSys4, null, GameCatalog.ID_DAIAKUJI));

        assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(daiaCn, null, GameCatalog.ID_KICHIKUOU));
        assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(daiaCn, null, GameCatalog.ID_ESCALAYER));
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
            String subDirName
    ) throws Exception {
        final int sectorSize = 2048;
        final int rootLba = 18;
        final int gameLba = 19;
        final int totalSectors = 21;
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

        if (subDirName != null) {
            ByteArrayOutputStream rootOut = new ByteArrayOutputStream();
            writeDirRecord(rootOut, subDirName, true, gameLba, sectorSize, joliet);
            byte[] rootBytes = rootOut.toByteArray();
            System.arraycopy(rootBytes, 0, img, rootLba * sectorSize, rootBytes.length);
        }

        ByteArrayOutputStream gameOut = new ByteArrayOutputStream();
        int targetLba = (subDirName != null) ? gameLba : rootLba;
        for (String name : gameDirFiles) {
            writeDirRecord(gameOut, name + ";1", false, 20, 4, joliet);
        }
        byte[] gameBytes = gameOut.toByteArray();
        System.arraycopy(gameBytes, 0, img, targetLba * sectorSize, gameBytes.length);

        writeFile(isoFile, img);
        return isoFile;
    }

    @Test
    public void validatesSafFileListsResolveBaseNameAndIsoImagesByTargetGameId() throws Exception {
        assertTrue(GameCatalog.INSTANCE.matchesTargetGameFiles(
                GameCatalog.ID_DAIAKUJI,
                Collections.singletonList("大悪司SA.ALD")
        ));
        assertTrue(GameCatalog.INSTANCE.matchesTargetGameFiles(
                GameCatalog.ID_DAIAKUJI,
                Collections.singletonList("大恶司SA.ALD")
        ));
        assertTrue(GameCatalog.INSTANCE.matchesTargetGameFiles(
                GameCatalog.ID_DAIAKUJI,
                Collections.singletonList("adisk.ald"),
                Collections.singletonList("Daiakuji_02.wav")
        ));
        assertFalse(GameCatalog.INSTANCE.matchesTargetGameFiles(
                GameCatalog.ID_DAIAKUJI,
                Collections.singletonList("KichikuRanceSA.ALD")
        ));
        assertFalse(GameCatalog.INSTANCE.matchesTargetGameFiles(
                GameCatalog.ID_DAIAKUJI,
                Collections.singletonList("DOKI_SA.ALD")
        ));
        assertFalse(GameCatalog.INSTANCE.matchesTargetGameFiles(
                GameCatalog.ID_DAIAKUJI,
                Collections.singletonList("Rance4SA.ALD")
        ));
        assertFalse(GameCatalog.INSTANCE.matchesTargetGameFiles(
                GameCatalog.ID_DAIAKUJI,
                Arrays.asList("AliceStart.ini", "Rance10.ain")
        ));

        assertEquals(
                "Daiakuji",
                NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                        Arrays.asList("System39.ini", "System39.ain", "大悪司SA.ALD")
                )
        );
        assertEquals(
                "Daiakuji",
                NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                        Collections.singletonList("大恶司SA.ALD")
                )
        );
        assertEquals(
                "Daiakuji",
                NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                        Collections.singletonList("戭埆巌SA.ALD")
                )
        );
        assertEquals(
                "KichikuRance",
                NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                        Collections.singletonList("KichikuRanceSA.ALD")
                )
        );

        File daiaJolietIso = buildTestIso(
                tmp.newFile("daiakuji_jp.iso"),
                true,
                Collections.singletonList("大悪司SA.ALD"),
                "GAMEDATA"
        );
        File daiaAsciiIso = buildTestIso(
                tmp.newFile("daiakuji_ascii.iso"),
                false,
                Collections.singletonList("DAIAKUJISA.ALD"),
                null
        );
        File kichikuIso = buildTestIso(
                tmp.newFile("kichiku.iso"),
                false,
                Collections.singletonList("KichikuRanceSA.ALD"),
                null
        );
        File escalayerIso = buildTestIso(
                tmp.newFile("escalayer.iso"),
                false,
                Collections.singletonList("DOKI_SA.ALD"),
                null
        );
        File unrelatedSys3Iso = buildTestIso(
                tmp.newFile("rance4.iso"),
                false,
                Collections.singletonList("Rance4SA.ALD"),
                "GAMEDATA"
        );

        assertTrue(Iso9660Extractor.INSTANCE.containsGameRoot(daiaJolietIso, null, GameCatalog.ID_DAIAKUJI));
        assertTrue(Iso9660Extractor.INSTANCE.containsGameRoot(daiaAsciiIso, null, GameCatalog.ID_DAIAKUJI));

        assertFalse(Iso9660Extractor.INSTANCE.containsGameRoot(kichikuIso, null, GameCatalog.ID_DAIAKUJI));
        assertFalse(Iso9660Extractor.INSTANCE.containsGameRoot(escalayerIso, null, GameCatalog.ID_DAIAKUJI));
        assertFalse(Iso9660Extractor.INSTANCE.containsGameRoot(unrelatedSys3Iso, null, GameCatalog.ID_DAIAKUJI));

        assertTrue(Iso9660Extractor.INSTANCE.containsGameRoot(unrelatedSys3Iso, null, null));
    }

    @Test
    public void preservesRuntimeFilesDetectsBloatAndPrioritizesMainRootOverOld() throws Exception {
        assertFalse(NativeGameImport.INSTANCE.skip(".xsys35rc"));
        assertFalse(NativeGameImport.INSTANCE.skip("Daiakuji/.xsys35rc"));
        assertFalse(NativeGameImport.INSTANCE.skip("playlist.txt"));

        assertTrue(GameListKt.isDaiakujiBloatRelPath("Alice.exe"));
        assertTrue(GameListKt.isDaiakujiBloatRelPath("System39_CN.exe"));
        assertTrue(GameListKt.isDaiakujiBloatRelPath("Sys39Reg.exe"));
        assertTrue(GameListKt.isDaiakujiBloatRelPath("_inmm238.exe"));
        assertTrue(GameListKt.isDaiakujiBloatRelPath("_inmmcnf.exe"));
        assertTrue(GameListKt.isDaiakujiBloatRelPath("_inmmserv.exe"));
        assertTrue(GameListKt.isDaiakujiBloatRelPath("_inmm.dll"));
        assertTrue(GameListKt.isDaiakujiBloatRelPath("innmhack.dll"));
        assertTrue(GameListKt.isDaiakujiBloatRelPath("MAKO32.DLL"));
        assertTrue(GameListKt.isDaiakujiBloatRelPath("DLL/Sys39Mem.dll"));
        assertTrue(GameListKt.isDaiakujiBloatRelPath("_inmmcnf.ini"));
        assertTrue(GameListKt.isDaiakujiBloatRelPath("注册表去除启动弹窗.bat"));
        assertTrue(GameListKt.isDaiakujiBloatRelPath("全CG存档/戝埆巌SZ.ASD"));
        assertTrue(GameListKt.isDaiakujiBloatRelPath("staff.jpg"));
        assertTrue(GameListKt.isDaiakujiBloatRelPath("请读我.jpg"));
        assertTrue(GameListKt.isDaiakujiBloatRelPath("运行游戏前必看进入方法.txt"));
        assertTrue(GameListKt.isDaiakujiBloatRelPath("@vieira.txt"));
        assertFalse(GameListKt.isDaiakujiBloatRelPath("dDemo0.alk", false));
        assertTrue(GameListKt.isDaiakujiBloatRelPath("dDemo0.alk", true));

        List<String> runtimePaths = new ArrayList<>(Arrays.asList(
                "System39.ini",
                "System39.ain",
                "大悪司SA.ALD",
                "大悪司GA.ALD",
                "大悪司WA.ALD",
                "大悪司DA.ALD",
                "dDemo.alk",
                "_inmm.ini"
        ));
        for (int i = 2; i <= 23; i++) {
            runtimePaths.add(String.format("bgm/Daiakuji_%02d.wav", i));
        }

        File extractRoot = tmp.newFolder("extractRoot");
        File mainDir = new File(extractRoot, "DAIAKUJICN");
        File oldDir = new File(extractRoot, "OLD/DAIAKUJI_CN");

        for (String rel : runtimePaths) {
            assertFalse("Runtime file must not be skipped by NativeGameImport: " + rel, NativeGameImport.INSTANCE.skip(rel));
            assertFalse("Runtime file must be kept: " + rel, GameListKt.isDaiakujiBloatRelPath(rel, true));
            writeFile(new File(mainDir, rel), new byte[]{1, 2});
        }
        writeFile(new File(mainDir, "System39.ini"), shiftJisSystem39Ini());
        writeFile(new File(mainDir, "_inmm.ini"), "bgm\\Daiakuji_02.wav\r\n".getBytes(StandardCharsets.US_ASCII));

        // Populate realistic Windows/guide/backup bloat inside mainDir.
        writeFile(new File(mainDir, "dDemo0.alk"), new byte[]{9});
        writeFile(new File(mainDir, "Alice.exe"), new byte[]{9});
        writeFile(new File(mainDir, "System39_CN.exe"), new byte[]{9});
        writeFile(new File(mainDir, "_inmm238.exe"), new byte[]{9});
        writeFile(new File(mainDir, "_inmmcnf.ini"), new byte[]{9});
        writeFile(new File(mainDir, "_inmm.dll"), new byte[]{9});
        writeFile(new File(mainDir, "DLL/draw.dll"), new byte[]{9});
        writeFile(new File(mainDir, "全CG存档/戝埆巌SZ.ASD"), new byte[]{9});
        writeFile(new File(mainDir, "staff.jpg"), new byte[]{9});
        writeFile(new File(mainDir, "请读我.jpg"), new byte[]{9});
        writeFile(new File(mainDir, "运行游戏前必看进入方法.txt"), new byte[]{9});
        writeFile(new File(mainDir, "@vieira.txt"), new byte[]{9});
        writeFile(new File(mainDir, "注册表去除启动弹窗.bat"), new byte[]{9});

        // Populate OLD/DAIAKUJI_CN sibling tree: findGameRootDirInternal must prefer DAIAKUJICN.
        writeFile(new File(oldDir, "System39.ini"), shiftJisSystem39Ini());
        writeFile(new File(oldDir, "大悪司SA.ALD"), new byte[]{3});

        File chosenRoot = GameListKt.findGameRootDirInternal(extractRoot, null, GameCatalog.ID_DAIAKUJI);
        assertNotNull(chosenRoot);
        assertEquals(mainDir.getCanonicalPath(), chosenRoot.getCanonicalPath());

        GameListKt.pruneRedundantBaseBloat(mainDir);
        for (String rel : runtimePaths) {
            assertTrue("Runtime file must survive pruning: " + rel, new File(mainDir, rel).isFile());
        }
        assertFalse(new File(mainDir, "dDemo0.alk").exists());
        assertFalse(new File(mainDir, "Alice.exe").exists());
        assertFalse(new File(mainDir, "System39_CN.exe").exists());
        assertFalse(new File(mainDir, "_inmm238.exe").exists());
        assertFalse(new File(mainDir, "_inmmcnf.ini").exists());
        assertFalse(new File(mainDir, "_inmm.dll").exists());
        assertFalse(new File(mainDir, "DLL").exists());
        assertFalse(new File(mainDir, "全CG存档").exists());
        assertFalse(new File(mainDir, "staff.jpg").exists());
        assertFalse(new File(mainDir, "请读我.jpg").exists());
        assertFalse(new File(mainDir, "运行游戏前必看进入方法.txt").exists());
        assertFalse(new File(mainDir, "@vieira.txt").exists());
        assertFalse(new File(mainDir, "注册表去除启动弹窗.bat").exists());

        // If a standalone dir only has dDemo0.alk without dDemo.alk, dDemo0.alk must be retained.
        File noMainDemoDir = tmp.newFolder("DaiakujiOnlyDemo0");
        writeFile(new File(noMainDemoDir, "大悪司SA.ALD"), new byte[]{1});
        writeFile(new File(noMainDemoDir, "dDemo0.alk"), new byte[]{2});
        writeFile(new File(noMainDemoDir, "Alice.exe"), new byte[]{9});
        GameListKt.pruneRedundantBaseBloat(noMainDemoDir);
        assertTrue(new File(noMainDemoDir, "dDemo0.alk").isFile());
        assertFalse(new File(noMainDemoDir, "Alice.exe").exists());
    }

    @Test
    public void verifiesOnlineInstallerCdnUrlBytesCatalogAndSortMetadata() throws Exception {
        assertEquals("https://alicehub.000.moe/daiakuji", OnlineInstaller.DAIAKUJI_CDN_URL);
        assertEquals(923623502L, AppSettings.defaultBaseGameSizeForUrl(OnlineInstaller.DAIAKUJI_CDN_URL));
        assertEquals(923623502L, AppSettings.defaultBaseGameSizeForUrl("https://alicehub.000.moe/daiakuji/"));

        File storage = tmp.newFolder("storage");
        File daiaStaging = new File(storage, ".online_base_staging_Daiakuji");
        File kichikuStaging = new File(storage, ".online_base_staging_KichikuRance");
        File escStaging = new File(storage, ".online_base_staging_Escalayer");
        assertTrue(daiaStaging.mkdirs());
        assertTrue(kichikuStaging.mkdirs());
        assertTrue(escStaging.mkdirs());

        List<File> evicted = OnlineInstaller.INSTANCE.evictBaseStagingToTrash(storage, GameCatalog.ID_DAIAKUJI, null);
        assertEquals(1, evicted.size());
        assertFalse(daiaStaging.exists());
        assertTrue(kichikuStaging.exists());
        assertTrue(escStaging.exists());

        assertTrue(GameCatalog.INSTANCE.builtInSelectOrder().contains(GameCatalog.ID_DAIAKUJI));
        GameSortInfo sortInfo = GameSortMetadata.INSTANCE.forGame(GameCatalog.ID_DAIAKUJI);
        assertNotNull(sortInfo);
        assertEquals("2001-11-30", sortInfo.getReleaseDate());
        assertEquals(Double.valueOf(77.2), sortInfo.getRating());
        assertEquals("https://vndb.org/v298", sortInfo.getScoreSourceUrl());
        assertEquals("https://www.alicesoft.com/akuji/index.html", sortInfo.getReleaseSourceUrl());
    }

    @Test
    public void validatesRealExtractedDaiakujiDirIfPresent() {
        File realExtractParent = new File("/root/.cache/alicehub-daiakuji-game");
        File realMainDir = new File(realExtractParent, "DAIAKUJICN");
        if (!realMainDir.isDirectory()) {
            return;
        }
        assertTrue(GameCatalog.INSTANCE.isSystem3xGameDir(realMainDir));
        assertTrue(GameCatalog.INSTANCE.isDaiakujiGameDir(realMainDir));
        assertFalse(GameCatalog.INSTANCE.isKichikuouGameDir(realMainDir));
        assertFalse(GameCatalog.INSTANCE.isEscalayerGameDir(realMainDir));
        assertTrue(GameCatalog.INSTANCE.validateImportedGameDir(realMainDir, null, GameCatalog.ID_DAIAKUJI));
        assertEquals("大悪司", System40Ini.Companion.parse(new File(realMainDir, "System39.ini")).getGameName());
        File chosenRoot = GameListKt.findGameRootDirInternal(realExtractParent, null, GameCatalog.ID_DAIAKUJI);
        assertNotNull(chosenRoot);
        assertEquals(realMainDir.getAbsolutePath(), chosenRoot.getAbsolutePath());
    }
}

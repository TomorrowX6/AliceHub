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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KichikuouCatalogTest {
    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    private static void writeFile(File file, byte[] bytes) throws Exception {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(bytes);
        }
    }

    @Test
    public void classifiesChineseAndJapaneseKichikuouDirs() throws Exception {
        File cnDir = tmp.newFolder("KichikuRance");
        writeFile(new File(cnDir, "KichikuRanceSA.ALD"), new byte[]{0});
        writeFile(new File(cnDir, ".xsys35rc"), "savedir: Saves\n".getBytes(StandardCharsets.UTF_8));

        assertTrue(GameCatalog.INSTANCE.isSystem3xGameDir(cnDir));
        assertTrue(GameCatalog.INSTANCE.isKichikuouGameDir(cnDir));
        assertFalse(GameCatalog.INSTANCE.isEscalayerGameDir(cnDir));
        assertEquals(
                GameCatalog.ID_KICHIKUOU,
                GameCatalog.INSTANCE.classifyDirectory(cnDir, cnDir.getName(), null, null)
        );

        File jpDir = tmp.newFolder("GAMEDATA");
        writeFile(new File(jpDir, "鬼畜王SA.ALD"), new byte[]{0});
        assertTrue(GameCatalog.INSTANCE.isSystem3xGameDir(jpDir));
        assertTrue(GameCatalog.INSTANCE.isKichikuouGameDir(jpDir));
        assertEquals(
                GameCatalog.ID_KICHIKUOU,
                GameCatalog.INSTANCE.classifyDirectory(jpDir, jpDir.getName(), null, null)
        );
    }

    @Test
    public void classifiesKichikuouBySaveDirAndAliases() throws Exception {
        File saveDir = tmp.newFolder("KichikuSaves");
        writeFile(new File(saveDir, "KichikuRancesa.asd"), new byte[]{1, 2, 3});
        assertEquals(
                GameCatalog.ID_KICHIKUOU,
                GameCatalog.INSTANCE.classifyDirectory(null, "UnknownDir", saveDir, null)
        );

        assertEquals(
                GameCatalog.ID_KICHIKUOU,
                GameCatalog.INSTANCE.classifyDirectory(null, "鬼畜王ランス【卡拉保护协会V1.0】", null, null)
        );
        assertEquals(
                GameCatalog.ID_KICHIKUOU,
                GameCatalog.INSTANCE.classifyDirectory(null, "KichikuRance-1", null, null)
        );
        assertEquals(
                GameCatalog.ID_KICHIKUOU,
                GameCatalog.INSTANCE.classifyDirectory(null, "custom", null, "鬼畜王兰斯")
        );
    }

    @Test
    public void preservesXsys35RcAndDetectsKichikuouBloat() {
        assertFalse(NativeGameImport.INSTANCE.skip(".xsys35rc"));
        assertFalse(NativeGameImport.INSTANCE.skip("KichikuRance/.xsys35rc"));
        assertFalse(NativeGameImport.INSTANCE.skip("bgm/kichiku_2.mp3"));

        assertTrue(NativeGameImport.INSTANCE.skip(".git/config"));
        assertTrue(NativeGameImport.INSTANCE.skip(".hidden"));
        assertTrue(GameListKt.isKichikuouBloatRelPath("xsystem35.exe"));
        assertTrue(GameListKt.isKichikuouBloatRelPath("Kichiku/System35.EXE"));
        assertFalse(GameListKt.isKichikuouBloatRelPath("KichikuRanceSA.ALD"));
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

        // Sector 16: PVD or Joliet SVD
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

        // Sector 17: Volume Descriptor Set Terminator
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
    public void validatesTargetGameDirectoriesEvenWhenExpectedAinIsNull() throws Exception {
        File kichikuCn = tmp.newFolder("MyFolder");
        writeFile(new File(kichikuCn, "KichikuRanceSA.ALD"), new byte[]{0});

        File kichikuJp = tmp.newFolder("JP_DATA");
        writeFile(new File(kichikuJp, "鬼畜王SA.ALD"), new byte[]{0});

        File kichikuByBgm = tmp.newFolder("Sys3WithBgm");
        writeFile(new File(kichikuByBgm, "adisk.ald"), new byte[]{0});
        File bgmDir = new File(kichikuByBgm, "bgm");
        assertTrue(bgmDir.mkdir());
        writeFile(new File(bgmDir, "kichiku_2.mp3"), new byte[]{0});

        // Tricky folder names containing unrelated System 3.x / System 4.x games must be rejected.
        File fakeSys3 = tmp.newFolder("KichikuRance");
        writeFile(new File(fakeSys3, "Rance4SA.ALD"), new byte[]{0});

        File fakeSys4 = tmp.newFolder("鬼畜王ランス");
        writeFile(new File(fakeSys4, "AliceStart.ini"), "GameName = \"Other\"\n".getBytes(StandardCharsets.UTF_8));
        writeFile(new File(fakeSys4, "OtherGame.ain"), new byte[]{0});

        File escalayer = tmp.newFolder("EscalayerDir");
        writeFile(new File(escalayer, "DOKI_SA.ALD"), new byte[]{0});

        File rance10 = tmp.newFolder("Rance10Dir");
        writeFile(new File(rance10, "AliceStart.ini"), "GameName = \"Rance10\"\n".getBytes(StandardCharsets.UTF_8));
        writeFile(new File(rance10, "Rance10.ain"), new byte[]{0});

        File sengoku = tmp.newFolder("SengokuDir");
        writeFile(new File(sengoku, "AliceStart.ini"), "GameName = \"Sengoku\"\n".getBytes(StandardCharsets.UTF_8));
        writeFile(new File(sengoku, "AliceCN.ain"), new byte[]{0});

        File rance02 = tmp.newFolder("Rance02Dir");
        writeFile(new File(rance02, "AliceStart.ini"), "GameName = \"Rance02\"\n".getBytes(StandardCharsets.UTF_8));
        writeFile(new File(rance02, "ランス０２.ain"), new byte[]{0});

        assertTrue(GameCatalog.INSTANCE.validateImportedGameDir(kichikuCn, null, GameCatalog.ID_KICHIKUOU));
        assertTrue(GameCatalog.INSTANCE.validateImportedGameDir(kichikuJp, null, GameCatalog.ID_KICHIKUOU));
        assertTrue(GameCatalog.INSTANCE.validateImportedGameDir(kichikuByBgm, null, GameCatalog.ID_KICHIKUOU));

        assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(fakeSys3, null, GameCatalog.ID_KICHIKUOU));
        assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(fakeSys4, null, GameCatalog.ID_KICHIKUOU));
        assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(escalayer, null, GameCatalog.ID_KICHIKUOU));
        assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(rance10, null, GameCatalog.ID_KICHIKUOU));
        assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(sengoku, null, GameCatalog.ID_KICHIKUOU));
        assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(rance02, null, GameCatalog.ID_KICHIKUOU));

        // Other expectedAinName == null games also validate target identity cleanly.
        assertTrue(GameCatalog.INSTANCE.validateImportedGameDir(sengoku, null, GameCatalog.ID_SENGOKU_RANCE));
        assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(kichikuCn, null, GameCatalog.ID_SENGOKU_RANCE));
        assertTrue(GameCatalog.INSTANCE.validateImportedGameDir(rance02, null, GameCatalog.ID_RANCE02));
        assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(sengoku, null, GameCatalog.ID_RANCE02));

        // Unrestricted / custom imports still accept any valid game directory.
        assertTrue(GameCatalog.INSTANCE.validateImportedGameDir(fakeSys3, null, null));
        assertTrue(GameCatalog.INSTANCE.validateImportedGameDir(fakeSys3, null, GameCatalog.PREFIX_CUSTOM + "Other"));
    }

    @Test
    public void validatesSafFileListsAndIsoImagesByTargetGameId() throws Exception {
        assertTrue(GameCatalog.INSTANCE.matchesTargetGameFiles(
                GameCatalog.ID_KICHIKUOU,
                Collections.singletonList("KichikuRanceSA.ALD")
        ));
        assertTrue(GameCatalog.INSTANCE.matchesTargetGameFiles(
                GameCatalog.ID_KICHIKUOU,
                Collections.singletonList("adisk.ald"),
                Collections.singletonList("kichiku_3.mp3")
        ));
        assertFalse(GameCatalog.INSTANCE.matchesTargetGameFiles(
                GameCatalog.ID_KICHIKUOU,
                Collections.singletonList("Rance4SA.ALD")
        ));
        assertFalse(GameCatalog.INSTANCE.matchesTargetGameFiles(
                GameCatalog.ID_KICHIKUOU,
                Arrays.asList("AliceStart.ini", "Rance10.ain")
        ));

        File kichikuCnIso = buildTestIso(
                tmp.newFile("kichiku_cn.iso"),
                false,
                Collections.singletonList("KichikuRanceSA.ALD"),
                null
        );
        File kichikuJpIso = buildTestIso(
                tmp.newFile("kichiku_jp.iso"),
                true,
                Collections.singletonList("鬼畜王SA.ALD"),
                "GAMEDATA"
        );
        File unrelatedSys3Iso = buildTestIso(
                tmp.newFile("rance4.iso"),
                false,
                Collections.singletonList("Rance4SA.ALD"),
                "GAMEDATA"
        );
        File escalayerIso = buildTestIso(
                tmp.newFile("escalayer.iso"),
                false,
                Collections.singletonList("DOKI_SA.ALD"),
                null
        );
        File rance10Iso = buildTestIso(
                tmp.newFile("rance10.iso"),
                false,
                Arrays.asList("AliceStart.ini", "Rance10.ain"),
                null
        );

        assertTrue(Iso9660Extractor.INSTANCE.containsGameRoot(kichikuCnIso, null, GameCatalog.ID_KICHIKUOU));
        assertTrue(Iso9660Extractor.INSTANCE.containsGameRoot(kichikuJpIso, null, GameCatalog.ID_KICHIKUOU));

        assertFalse(Iso9660Extractor.INSTANCE.containsGameRoot(unrelatedSys3Iso, null, GameCatalog.ID_KICHIKUOU));
        assertFalse(Iso9660Extractor.INSTANCE.containsGameRoot(escalayerIso, null, GameCatalog.ID_KICHIKUOU));
        assertFalse(Iso9660Extractor.INSTANCE.containsGameRoot(rance10Iso, null, GameCatalog.ID_KICHIKUOU));

        // Unrestricted ISO check (Launcher "Import other game") still detects all game discs.
        assertTrue(Iso9660Extractor.INSTANCE.containsGameRoot(unrelatedSys3Iso, null, null));
        assertTrue(Iso9660Extractor.INSTANCE.containsGameRoot(rance10Iso, null, null));
    }
}

package io.github.kichikuou.xsystem4;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.CRC32;

public class FukeiPc98FloppyTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static byte[] make256BytesWithCrc32(long target) {
        byte[] buf = new byte[256];
        long[] table = new long[256];
        int[] rev = new int[256];
        for (int i = 0; i < 256; i++) {
            long v = i;
            for (int j = 0; j < 8; j++) {
                v = ((v & 1L) != 0) ? ((v >>> 1) ^ 0xedb88320L) : (v >>> 1);
            }
            table[i] = v;
            rev[(int) (v >>> 24)] = i;
        }
        long forward = 0xffffffffL;
        for (int i = 0; i < 252; i++) {
            forward = (forward >>> 8) ^ table[(int) ((forward ^ (buf[i] & 0xffL)) & 0xffL)];
        }
        long r = (target ^ 0xffffffffL) & 0xffffffffL;
        int[] idx = new int[4];
        for (int i = 3; i >= 0; i--) {
            idx[i] = rev[(int) (r >>> 24)];
            r = ((r ^ table[idx[i]]) << 8) & 0xffffffffL;
        }
        for (int i = 0; i < 4; i++) {
            byte b = (byte) ((forward ^ idx[i]) & 0xffL);
            buf[252 + i] = b;
            forward = (forward >>> 8) ^ table[(int) ((forward ^ (b & 0xffL)) & 0xffL)];
        }
        CRC32 check = new CRC32();
        check.update(buf);
        assertEquals(target & 0xffffffffL, check.getValue());
        return buf;
    }

    private static void setFat12(byte[] raw, int fatOff, int cluster, int value) {
        int off = fatOff + (cluster * 3) / 2;
        int cur = (raw[off] & 0xFF) | ((raw[off + 1] & 0xFF) << 8);
        if ((cluster & 1) != 0) {
            cur = (cur & 0x000F) | ((value & 0x0FFF) << 4);
        } else {
            cur = (cur & 0xF000) | (value & 0x0FFF);
        }
        raw[off] = (byte) (cur & 0xFF);
        raw[off + 1] = (byte) ((cur >>> 8) & 0xFF);
    }

    private static void writeDirEntry(byte[] raw, int off, String name8, String ext3, int attr, int cluster, int size) {
        byte[] n = Arrays.copyOf(name8.getBytes(StandardCharsets.US_ASCII), 8);
        byte[] e = Arrays.copyOf(ext3.getBytes(StandardCharsets.US_ASCII), 3);
        for (int i = name8.length(); i < 8; i++) n[i] = ' ';
        for (int i = ext3.length(); i < 3; i++) e[i] = ' ';
        System.arraycopy(n, 0, raw, off, 8);
        System.arraycopy(e, 0, raw, off + 8, 3);
        raw[off + 11] = (byte) attr;
        raw[off + 26] = (byte) (cluster & 0xFF);
        raw[off + 27] = (byte) ((cluster >>> 8) & 0xFF);
        raw[off + 28] = (byte) (size & 0xFF);
        raw[off + 29] = (byte) ((size >>> 8) & 0xFF);
        raw[off + 30] = (byte) ((size >>> 16) & 0xFF);
        raw[off + 31] = (byte) ((size >>> 24) & 0xFF);
    }

    /** Builds a synthetic Anex86 1.2MB PC-98 2HD FDI image with IO.SYS + FUKEI/ subdirectory. */
    private static byte[] buildSyntheticFukeiFdi(byte[] adiskData, byte[] acgData) {
        final int hdrSize = 4096;
        final int bps = 1024;
        final int totSectors = 1232;
        final int fddSize = bps * totSectors;
        byte[] fdi = new byte[hdrSize + fddSize];
        ByteBuffer hdr = ByteBuffer.wrap(fdi, 0, 32).order(ByteOrder.LITTLE_ENDIAN);
        hdr.putInt(0);          // zero
        hdr.putInt(0x90);       // 2HD
        hdr.putInt(hdrSize);
        hdr.putInt(fddSize);
        hdr.putInt(bps);
        hdr.putInt(8);          // sectors
        hdr.putInt(2);          // heads
        hdr.putInt(77);         // cylinders

        byte[] raw = new byte[fddSize];
        raw[0] = (byte) 0xEB;
        raw[1] = 0x3C;
        raw[2] = (byte) 0x90;
        System.arraycopy("NEC  5.0".getBytes(StandardCharsets.US_ASCII), 0, raw, 3, 8);
        ByteBuffer bpb = ByteBuffer.wrap(raw, 11, 20).order(ByteOrder.LITTLE_ENDIAN);
        bpb.putShort((short) bps);   // +11 bps
        bpb.put((byte) 1);           // +13 spc
        bpb.putShort((short) 1);     // +14 rsv
        bpb.put((byte) 2);           // +16 nfats
        bpb.putShort((short) 192);   // +17 rootEnt
        bpb.putShort((short) totSectors); // +19 tot16
        bpb.put((byte) 0xFE);        // +21 media
        bpb.putShort((short) 2);     // +22 spf

        int fatOff = bps;
        int rootOff = (1 + 2 * 2) * bps; // sector 5
        int dataOff = rootOff + 6 * bps; // 192 * 32 = 6144 = 6 sectors

        // Cluster 2: IO.SYS (boot system file, should be skipped)
        setFat12(raw, fatOff, 2, 0xFFF);
        // Cluster 3: FUKEI/ directory
        setFat12(raw, fatOff, 3, 0xFFF);
        // Cluster 4: ADISK.DAT
        setFat12(raw, fatOff, 4, 0xFFF);
        // Cluster 5: ACG.DAT
        setFat12(raw, fatOff, 5, 0xFFF);

        writeDirEntry(raw, rootOff, "IO", "SYS", 0x27, 2, 32);
        writeDirEntry(raw, rootOff + 32, "FUKEI", "", 0x10, 3, 0);

        int fukeiDirOff = dataOff + (3 - 2) * bps;
        writeDirEntry(raw, fukeiDirOff, "ADISK", "DAT", 0x20, 4, adiskData.length);
        writeDirEntry(raw, fukeiDirOff + 32, "ACG", "DAT", 0x20, 5, acgData.length);

        System.arraycopy(adiskData, 0, raw, dataOff + (4 - 2) * bps, adiskData.length);
        System.arraycopy(acgData, 0, raw, dataOff + (5 - 2) * bps, acgData.length);

        System.arraycopy(raw, 0, fdi, hdrSize, fddSize);
        return fdi;
    }

    /** Builds a minimal valid 2048x2048 1bpp PC-98 FONT.bmp (optionally populated in GB2312 rows 85..87). */
    private static byte[] buildPc98FontBmp(boolean populateGb2312Ext) {
        final int dataOff = 62;
        final int planeSize = 256 * 2048;
        byte[] bmp = new byte[dataOff + planeSize];
        bmp[0] = 'B';
        bmp[1] = 'M';
        ByteBuffer h = ByteBuffer.wrap(bmp, 2, 60).order(ByteOrder.LITTLE_ENDIAN);
        h.putInt(bmp.length);
        h.putInt(0);
        h.putInt(dataOff);
        h.putInt(40);        // BITMAPINFOHEADER
        h.putInt(2048);      // width
        h.putInt(2048);      // height (bottom-up)
        h.putShort((short) 1);
        h.putShort((short) 1);
        h.putInt(0);         // BI_RGB
        h.putInt(planeSize);
        h.putInt(0);
        h.putInt(0);
        h.putInt(2);
        h.putInt(0);
        // Palette: 0 = black (ink), 1 = white (bg)
        h.putInt(0x00000000);
        h.putInt(0x00FFFFFF);

        // Fill background with white (0xFF)
        Arrays.fill(bmp, dataOff, bmp.length, (byte) 0xFF);
        if (populateGb2312Ext) {
            for (int ry = 0x21; ry < 0x21 + 40; ry++) {
                int y = ry * 16;
                int srcY = 2047 - y;
                bmp[dataOff + srcY * 256 + 85 * 2] = 0x00; // ink pixels in GB2312 zone 85
            }
        }
        return bmp;
    }

    @Test
    public void fukeiCatalogMetadataAliasesAndClassificationWork() throws Exception {
        List<String> order = GameCatalog.builtInSelectOrder();
        assertEquals(171, order.size());
        assertTrue(order.contains(GameCatalog.ID_FUKEI));

        GameDescriptor desc = GameCatalog.INSTANCE.descriptorForWithResolver(GameCatalog.ID_FUKEI, null, resId -> "fukei");
        assertEquals(GameCatalog.ID_FUKEI, desc.getId());
        assertEquals("FukeiSanVX", desc.getDefaultDirName());
        assertFalse(desc.getSupportsOnlineBaseInstall());
        assertNull(desc.getOnlineBaseCdnUrl());
        assertFalse(desc.getHasChinese());
        assertTrue(GameModifierBridge.INSTANCE.isNonModifierBuiltInGameDir(null, desc.getDefaultDirName(), null));

        GameSortInfo sortInfo = GameSortMetadata.INSTANCE.forGame(GameCatalog.ID_FUKEI);
        assertNotNull(sortInfo);
        assertEquals("1989-07-15", sortInfo.getReleaseDate());
        assertNull(sortInfo.getRating());

        assertTrue(GameCatalog.INSTANCE.isFukeiAlias("Fukei-san VX"));
        assertTrue(GameCatalog.INSTANCE.isFukeiAlias("婦警さんVX"));
        assertTrue(GameCatalog.INSTANCE.isFukeiAlias("妇警小姐VX"));
        assertTrue(GameCatalog.INSTANCE.isFukeiAlias("女警小姐VX"));
        assertFalse(GameCatalog.INSTANCE.isFukeiAlias("Rance10"));

        File crcDir = tmp.newFolder("OpaqueFukei");
        Files.write(new File(crcDir, "ADISK.DAT").toPath(), make256BytesWithCrc32(0x026de326L));
        Files.write(new File(crcDir, "ACG.DAT").toPath(), new byte[32]);
        assertEquals(GameCatalog.ID_FUKEI, GameCatalog.INSTANCE.classifyDirectory(crcDir, "OpaqueFukei", null, "OpaqueFukei"));
        assertFalse(GameCatalog.hasLocalChineseMarkers(crcDir));

        // Japanese FONT.bmp must NOT flip to Chinese; GB2312 FONT.bmp MUST flip to Chinese.
        File fontBmp = new File(crcDir, "FONT.bmp");
        Files.write(fontBmp.toPath(), buildPc98FontBmp(false));
        assertTrue(Pc98FloppyExtractor.INSTANCE.isValidPc98FontBmp(fontBmp));
        assertFalse(Pc98FloppyExtractor.INSTANCE.isChinesePc98FontBmp(fontBmp));
        assertFalse(GameCatalog.hasLocalChineseMarkers(crcDir));

        Files.write(fontBmp.toPath(), buildPc98FontBmp(true));
        assertTrue(Pc98FloppyExtractor.INSTANCE.isChinesePc98FontBmp(fontBmp));
        assertTrue(GameCatalog.hasLocalChineseMarkers(crcDir));
        Item item = new Item("FukeiSanVX", crcDir, crcDir, null, null, null);
        assertTrue(GameCatalog.hasChineseTranslation(desc, item, null));
    }

    @Test
    public void pc98FloppyExtractorExtractsFdiAndCopiesCompanionFont() throws Exception {
        byte[] adisk = make256BytesWithCrc32(0x026de326L);
        byte[] acg = "GL3_STUB_DATA".getBytes(StandardCharsets.US_ASCII);
        File stage0 = tmp.newFolder("stage0");
        File fdiFile = new File(stage0, "FUKEI.fdi");
        Files.write(fdiFile.toPath(), buildSyntheticFukeiFdi(adisk, acg));
        File siblingFont = new File(stage0, "FONT.bmp");
        Files.write(siblingFont.toPath(), buildPc98FontBmp(true));

        assertTrue(ArchiveExtractor.INSTANCE.isSupportedArchiveName("FUKEI.fdi"));
        assertTrue(Pc98FloppyExtractor.INSTANCE.isFdiFile(fdiFile));
        assertTrue(Pc98FloppyExtractor.INSTANCE.containsGameRoot(fdiFile, null, GameCatalog.ID_FUKEI));
        assertFalse(Pc98FloppyExtractor.INSTANCE.containsGameRoot(fdiFile, null, GameCatalog.ID_TENGU));
        assertTrue(Pc98FloppyExtractor.INSTANCE.containsGameRoot(fdiFile, null, null));

        File outDir = tmp.newFolder("stage1");
        int count = Pc98FloppyExtractor.INSTANCE.extractFileToDirectory(fdiFile, outDir, rel -> false, false, null, null, null);
        assertEquals(2, count);
        assertFalse("DOS boot file IO.SYS should be skipped", new File(outDir, "IO.SYS").exists());

        File gameDir = new File(outDir, "FUKEI");
        assertTrue(gameDir.isDirectory());
        assertArrayEquals(adisk, Files.readAllBytes(new File(gameDir, "ADISK.DAT").toPath()));
        assertArrayEquals(acg, Files.readAllBytes(new File(gameDir, "ACG.DAT").toPath()));

        assertTrue(Pc98FloppyExtractor.INSTANCE.copyCompanionFontIfNeeded(Collections.singletonList(stage0), gameDir));
        assertTrue(new File(gameDir, "FONT.bmp").isFile());
        assertTrue(GameCatalog.hasLocalChineseMarkers(gameDir));
        assertEquals(GameCatalog.ID_FUKEI, GameCatalog.INSTANCE.classifyDirectory(gameDir, "FUKEI", null, "FUKEI"));
    }

    @Test
    public void pc98FloppyExtractorRejectsTruncatedChainAndZeroStartClusterForNonEmptyFile() throws Exception {
        byte[] adisk = make256BytesWithCrc32(0x026de326L);
        byte[] acg = "GL3_STUB_DATA".getBytes(StandardCharsets.US_ASCII);

        // Case 1: Declared size exceeds FAT cluster chain (2048 bytes declared, single 1024-byte cluster allocated)
        byte[] shortChainFdi = buildSyntheticFukeiFdi(adisk, acg);
        int fukeiDirOff = 4096 + (11 + (3 - 2)) * 1024;
        // ACG.DAT is the 2nd directory entry (+32); size field is at +28
        ByteBuffer.wrap(shortChainFdi).order(ByteOrder.LITTLE_ENDIAN).putInt(fukeiDirOff + 32 + 28, 2048);
        File shortFile = tmp.newFile("short_chain.fdi");
        Files.write(shortFile.toPath(), shortChainFdi);
        try {
            Pc98FloppyExtractor.INSTANCE.extractFileToDirectory(shortFile, tmp.newFolder("out_short"), rel -> false, false, null, null, null);
            fail("Expected IOException for truncated FAT chain");
        } catch (java.io.IOException expected) {
            assertTrue(expected.getMessage().contains("Truncated FAT"));
        }

        // Case 2: Non-empty file with startCluster == 0
        byte[] zeroClusterFdi = buildSyntheticFukeiFdi(adisk, acg);
        ByteBuffer.wrap(zeroClusterFdi).order(ByteOrder.LITTLE_ENDIAN).putShort(fukeiDirOff + 32 + 26, (short) 0);
        File zeroFile = tmp.newFile("zero_cluster.fdi");
        Files.write(zeroFile.toPath(), zeroClusterFdi);
        try {
            Pc98FloppyExtractor.INSTANCE.extractFileToDirectory(zeroFile, tmp.newFolder("out_zero"), rel -> false, false, null, null, null);
            fail("Expected IOException for invalid start cluster 0");
        } catch (java.io.IOException expected) {
            assertTrue(expected.getMessage().contains("Invalid starting cluster"));
        }
    }
}

package io.github.kichikuou.xsystem4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.CRC32;

public class Wave7RetroClassicsCatalogTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final String[] WAVE7_IDS = new String[] {
            GameCatalog.ID_RANCE1CLASSIC,
            GameCatalog.ID_RANCE2CLASSIC,
            GameCatalog.ID_RANCE3CLASSIC,
            GameCatalog.ID_DPSSGFAHREN,
            GameCatalog.ID_DPSSGKATEI,
            GameCatalog.ID_DPSSGNOBUNAGA,
            GameCatalog.ID_DPSSG2ANTIQUE,
            GameCatalog.ID_DPSSG2IKENAI,
            GameCatalog.ID_DPSSG2AKAI,
            GameCatalog.ID_DPSSG3RABBIT,
            GameCatalog.ID_DPSSG3SHINKON,
            GameCatalog.ID_DPSSG3SOTSUGYOU
    };

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

    @Test
    public void descriptorsCdnUrlsSizesAndSortMetadataAreWired() {
        List<String> order = GameCatalog.builtInSelectOrder();
        assertEquals(170, order.size());
        for (String id : WAVE7_IDS) {
            assertTrue("Missing from builtInSelectOrder: " + id, order.contains(id));
            GameDescriptor desc = GameCatalog.INSTANCE.descriptorForWithResolver(id, null, resId -> id);
            assertEquals(id, desc.getId());
            assertTrue(desc.getSupportsOnlineBaseInstall());
            assertFalse(desc.getSupportsOnlineMod());
            assertFalse(desc.getSupportsLocalMod());
            assertFalse(desc.getHasChinese());
            assertFalse(GameCatalog.hasChineseTranslation(desc));
            assertEquals("https://alicehub.000.moe/" + id, desc.getOnlineBaseCdnUrl());
            assertTrue("Missing default download size for " + id,
                    AppSettings.INSTANCE.defaultBaseGameSizeForUrl(desc.getOnlineBaseCdnUrl()) > 0L);
            assertTrue("Should be non-modifier built-in: " + id,
                    GameModifierBridge.INSTANCE.isNonModifierBuiltInGameDir(null, desc.getDefaultDirName(), null));
            GameSortInfo sortInfo = GameSortMetadata.INSTANCE.forGame(id);
            assertNotNull("Missing sort metadata for " + id, sortInfo);
            assertNotNull(sortInfo.getReleaseDate());
            assertNotNull(sortInfo.getRating());
        }

        // Exact byte counts and VNDB metadata spot-checks
        assertEquals(2775360L, AppSettings.INSTANCE.defaultBaseGameSizeForUrl(OnlineInstaller.RANCE1CLASSIC_CDN_URL));
        assertEquals(5358400L, AppSettings.INSTANCE.defaultBaseGameSizeForUrl(OnlineInstaller.RANCE2CLASSIC_CDN_URL));
        assertEquals(9186896L, AppSettings.INSTANCE.defaultBaseGameSizeForUrl(OnlineInstaller.RANCE3CLASSIC_CDN_URL));
        assertEquals(847104L, AppSettings.INSTANCE.defaultBaseGameSizeForUrl(OnlineInstaller.DPSSGFAHREN_CDN_URL));
        assertEquals(1286656L, AppSettings.INSTANCE.defaultBaseGameSizeForUrl(OnlineInstaller.DPSSG3SOTSUGYOU_CDN_URL));

        assertEquals("1989-07-15", GameSortMetadata.INSTANCE.forGame(GameCatalog.ID_RANCE1CLASSIC).getReleaseDate());
        assertEquals(Double.valueOf(54.4), GameSortMetadata.INSTANCE.forGame(GameCatalog.ID_RANCE1CLASSIC).getRating());
        assertEquals("1990-05-15", GameSortMetadata.INSTANCE.forGame(GameCatalog.ID_RANCE2CLASSIC).getReleaseDate());
        assertEquals(Double.valueOf(61.9), GameSortMetadata.INSTANCE.forGame(GameCatalog.ID_RANCE2CLASSIC).getRating());
        assertEquals("1991-10-15", GameSortMetadata.INSTANCE.forGame(GameCatalog.ID_RANCE3CLASSIC).getReleaseDate());
        assertEquals(Double.valueOf(65.5), GameSortMetadata.INSTANCE.forGame(GameCatalog.ID_RANCE3CLASSIC).getRating());
        assertEquals("1990-08-15", GameSortMetadata.INSTANCE.forGame(GameCatalog.ID_DPSSGFAHREN).getReleaseDate());
        assertEquals(Double.valueOf(58.2), GameSortMetadata.INSTANCE.forGame(GameCatalog.ID_DPSSGFAHREN).getRating());
        assertEquals("1991-04-15", GameSortMetadata.INSTANCE.forGame(GameCatalog.ID_DPSSG2ANTIQUE).getReleaseDate());
        assertEquals(Double.valueOf(58.1), GameSortMetadata.INSTANCE.forGame(GameCatalog.ID_DPSSG2ANTIQUE).getRating());
        assertEquals("1991-12-15", GameSortMetadata.INSTANCE.forGame(GameCatalog.ID_DPSSG3RABBIT).getReleaseDate());
        assertEquals(Double.valueOf(59.4), GameSortMetadata.INSTANCE.forGame(GameCatalog.ID_DPSSG3RABBIT).getRating());
    }

    @Test
    public void classicRanceFilesClassifyWithoutCollidingWithRemakesOrDigest() throws Exception {
        File r1 = tmp.newFolder("OpaqueRance1");
        Files.write(new File(r1, "RANCESA.ALD").toPath(), new byte[16]);
        Files.write(new File(r1, "RANCEGA.ALD").toPath(), new byte[16]);
        assertEquals(GameCatalog.ID_RANCE1CLASSIC, GameCatalog.INSTANCE.classifyDirectory(r1, "OpaqueRance1", null, "OpaqueRance1"));

        File r2 = tmp.newFolder("OpaqueRance2");
        Files.write(new File(r2, "RANCE2SA.ALD").toPath(), new byte[16]);
        assertEquals(GameCatalog.ID_RANCE2CLASSIC, GameCatalog.INSTANCE.classifyDirectory(r2, "OpaqueRance2", null, "OpaqueRance2"));

        File r3 = tmp.newFolder("OpaqueRance3");
        Files.write(new File(r3, "RANCE3SA.ALD").toPath(), new byte[16]);
        assertEquals(GameCatalog.ID_RANCE3CLASSIC, GameCatalog.INSTANCE.classifyDirectory(r3, "OpaqueRance3", null, "OpaqueRance3"));

        // System 4 remakes and digest must NEVER misclassify as classic originals
        assertEquals(GameCatalog.ID_RANCE01, GameCatalog.INSTANCE.classifyGameRootFiles(Arrays.asList("AliceStart.ini", "Rance01.ain")));
        assertEquals(GameCatalog.ID_RANCE02, GameCatalog.INSTANCE.classifyGameRootFiles(Arrays.asList("AliceStart.ini", "Rance02.ain")));
        assertEquals(GameCatalog.ID_RANCE03, GameCatalog.INSTANCE.classifyGameRootFiles(Arrays.asList("AliceStart.ini", "Rance03.ain")));
        assertEquals(GameCatalog.ID_RANCE1DIGEST, GameCatalog.INSTANCE.classifyGameRootFiles(Arrays.asList("System40.ini", "Rance1.ain")));
        assertEquals(GameCatalog.ID_RANCE04, GameCatalog.INSTANCE.classifyGameRootFiles(Collections.singletonList("RANCE4SA.ALD")));

        // Alias collision guards (Roman numerals must not match prefixes of II / III / IV / IX)
        assertTrue(GameCatalog.INSTANCE.isRance1ClassicAlias("Rance I"));
        assertTrue(GameCatalog.INSTANCE.isRance1ClassicAlias("兰斯I 寻找小光（原版）"));
        assertFalse(GameCatalog.INSTANCE.isRance1ClassicAlias("Rance II"));
        assertFalse(GameCatalog.INSTANCE.isRance1ClassicAlias("Rance III"));
        assertFalse(GameCatalog.INSTANCE.isRance1ClassicAlias("Rance IV"));
        assertFalse(GameCatalog.INSTANCE.isRance1ClassicAlias("Rance IX"));
        assertFalse(GameCatalog.INSTANCE.isRance1ClassicAlias("Rance01"));
        assertFalse(GameCatalog.INSTANCE.isRance1ClassicAlias("兰斯1 摘要版"));

        assertTrue(GameCatalog.INSTANCE.isRance2ClassicAlias("Rance II"));
        assertFalse(GameCatalog.INSTANCE.isRance2ClassicAlias("Rance III"));
        assertFalse(GameCatalog.INSTANCE.isRance2ClassicAlias("Rance02"));

        assertTrue(GameCatalog.INSTANCE.isRance3ClassicAlias("Rance III"));
        assertFalse(GameCatalog.INSTANCE.isRance3ClassicAlias("Rance03"));
    }

    @Test
    public void dpsSgTrilogyClassifiesByAdiskBdiskCrc32AndIniWithoutDpsCollision() throws Exception {
        Object[][] cases = new Object[][] {
                {GameCatalog.ID_DPSSGFAHREN, 0xab4cda48L, 0xe405d57cL, "dps_sg_fahren"},
                {GameCatalog.ID_DPSSGKATEI, 0xab4cda48L, 0x23e67d18L, "dps_sg_katei"},
                {GameCatalog.ID_DPSSGNOBUNAGA, 0xab4cda48L, 0x2ec116f2L, "dps_sg_nobunaga"},
                {GameCatalog.ID_DPSSG2ANTIQUE, 0xab4cda48L, 0x41fe8b3dL, "dps_sg2_antique"},
                {GameCatalog.ID_DPSSG2IKENAI, 0xab4cda48L, 0x6b562c09L, "dps_sg2_ikenai"},
                {GameCatalog.ID_DPSSG2AKAI, 0xab4cda48L, 0x1098e78cL, "dps_sg2_akai"},
                {GameCatalog.ID_DPSSG3RABBIT, 0xb77ae133L, 0xa3228b6cL, "dps_sg3_rabbit"},
                {GameCatalog.ID_DPSSG3SHINKON, 0xb77ae133L, 0x09b4448aL, "dps_sg3_shinkon"},
                {GameCatalog.ID_DPSSG3SOTSUGYOU, 0xb77ae133L, 0xbc4525d8L, "dps_sg3_sotsugyou"}
        };
        for (int i = 0; i < cases.length; i++) {
            String expectedId = (String) cases[i][0];
            long acrc = (Long) cases[i][1];
            long bcrc = (Long) cases[i][2];
            String iniId = (String) cases[i][3];

            File crcDir = tmp.newFolder("OpaqueSgCrc_" + i);
            Files.write(new File(crcDir, "ADISK.DAT").toPath(), make256BytesWithCrc32(acrc));
            Files.write(new File(crcDir, "BDISK.DAT").toPath(), make256BytesWithCrc32(bcrc));
            Files.write(new File(crcDir, "ACG.DAT").toPath(), new byte[16]);
            assertEquals("CRC classification failed for " + expectedId,
                    expectedId, GameCatalog.INSTANCE.classifyDirectory(crcDir, "OpaqueSgCrc_" + i, null, "OpaqueSgCrc_" + i));

            File iniDir = tmp.newFolder("OpaqueSgIni_" + i);
            Files.write(new File(iniDir, "ADISK.DAT").toPath(), new byte[16]);
            Files.write(new File(iniDir, "ACG.DAT").toPath(), new byte[16]);
            Files.write(new File(iniDir, "system3.ini").toPath(),
                    ("[config]\ngame = " + iniId + "\n").getBytes(StandardCharsets.UTF_8));
            assertEquals("INI classification failed for " + expectedId,
                    expectedId, GameCatalog.INSTANCE.classifyDirectory(iniDir, "OpaqueSgIni_" + i, null, "OpaqueSgIni_" + i));
        }

        // D.P.S. SG aliases must not be stolen by original D.P.S.
        assertFalse(GameCatalog.INSTANCE.isDpsAlias("D.P.S. SG - Fahren Fliegen"));
        assertFalse(GameCatalog.INSTANCE.isDpsAlias("DpsSg2Antique"));
        assertTrue(GameCatalog.INSTANCE.isDpsAlias("DPS"));
    }
}

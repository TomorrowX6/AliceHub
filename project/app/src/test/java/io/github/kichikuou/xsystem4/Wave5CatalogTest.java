package io.github.kichikuou.xsystem4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Unit tests for Wave 5 AliceSoft port catalog additions (127 -> 157 built-in titles):
 * 3 System 4 CN titles (bladebriders, dorapeko, haniwapetit),
 * 7 official System 3.x titles (yakata456, uminiochite, gakuenhyouryuu2, yousei, tobira, buroburo, kudakero),
 * 12 classic System 1/2/3 titles (littlevampire, intruder, crescent, tengu, dps, yakata1,
 *   sdpstono, sdpskaizoku, progomake, ayumihint, ayumilive, nisenaguri),
 * 8 Alice CD System 3.x bonus titles (carol, othellot, masquerade, dekirukana,
 *   tatakikoroshi, balloonpop, hiyoko2, selfgame).
 */
public class Wave5CatalogTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void wave5CatalogOrderDescriptorsAndMetadataAreWired() {
        List<String> order = GameCatalog.builtInSelectOrder();
        assertEquals(171, order.size());

        Object[][] specs = new Object[][]{
            {GameCatalog.ID_BLADEBRIDERS, "BladeBriders", true, "2014-10-24", 67.1, "https://vndb.org/v15652", "https://vndb.org/v15652"},
            {GameCatalog.ID_DORAPEKO, "Dorapeko", true, "2013-06-28", 73.6, "https://vndb.org/v12418", "https://vndb.org/v12418"},
            {GameCatalog.ID_HANIWAPETIT, "HaniwaPetit", true, "2012-12-25", null, null, "https://vndb.org/v49509"},
            {GameCatalog.ID_YAKATA456, "Yakata456", false, "1997-12-18", null, null, "https://www.alicesoft.com/"},
            {GameCatalog.ID_UMINIOCHITE, "UmiNiOchite", false, "1998-11-26", null, null, "https://vndb.org/v20391"},
            {GameCatalog.ID_GAKUENHYOURYUU2, "GakuenHyouryuu2", false, "2000-04-13", 66.8, "https://vndb.org/v20394", "https://vndb.org/v20394"},
            {GameCatalog.ID_YOUSEI, "Yousei", false, "2000-07-06", null, null, "https://vndb.org/v20395"},
            {GameCatalog.ID_TOBIRA, "Tobira", false, "2001-04-19", null, null, "https://vndb.org/v20396"},
            {GameCatalog.ID_BUROBURO, "Buroburo", false, "2001-07-26", null, null, "https://www.alicesoft.com/"},
            {GameCatalog.ID_KUDAKERO, "Kudakero", false, "2000-07-06", null, null, "https://www.alicesoft.com/"},
            {GameCatalog.ID_LITTLEVAMPIRE, "LittleVampire", false, "1988-05-05", 44.3, "https://vndb.org/v7360", "https://vndb.org/v7360"},
            {GameCatalog.ID_INTRUDER, "Intruder", false, "1989-07-15", 56.5, "https://vndb.org/v7370", "https://vndb.org/v7370"},
            {GameCatalog.ID_CRESCENT, "CrescentMoonGirl", false, "1989-08-15", 57.6, "https://vndb.org/v5457", "https://vndb.org/v5457"},
            {GameCatalog.ID_TENGU, "AbunaiTengu", false, "1989-10-15", 58.3, "https://vndb.org/v8549", "https://vndb.org/v8549"},
            {GameCatalog.ID_DPS, "DPS", false, "1989-12-15", 45.0, "https://vndb.org/v8666", "https://vndb.org/v8666"},
            {GameCatalog.ID_YAKATA1, "Yakata1", false, "1990-02-15", 51.5, "https://vndb.org/v9610", "https://vndb.org/v9610"},
            {GameCatalog.ID_SDPSTONO, "SuperDpsTono", false, "1992-09-01", 61.2, "https://vndb.org/v8670", "https://vndb.org/v8670"},
            {GameCatalog.ID_SDPSKAIZOKU, "SuperDpsKaizoku", false, "1992-09-01", 61.2, "https://vndb.org/v8670", "https://vndb.org/v8670"},
            {GameCatalog.ID_PROGOMAKE, "ProstudentGOmake", false, "1993-05-15", null, null, "https://vndb.org/v4701"},
            {GameCatalog.ID_AYUMIHINT, "AyumiHint", false, "1993-11-04", null, null, "https://vndb.org/v1929"},
            {GameCatalog.ID_AYUMILIVE, "AyumiLive", false, "1995-01-12", null, null, "https://vndb.org/v8566"},
            {GameCatalog.ID_NISENAGURI, "NiseNaguri", false, "1995-04-01", null, null, "https://vndb.org/v9426"},
            {GameCatalog.ID_CAROL, "Carol", false, "2001-04-19", null, null, "https://www.alicesoft.com/"},
            {GameCatalog.ID_OTHELLOT, "Othellot", false, "1999-11-18", null, null, "https://www.alicesoft.com/"},
            {GameCatalog.ID_MASQUERADE, "Masquerade", false, "2001-04-19", null, null, "https://www.alicesoft.com/"},
            {GameCatalog.ID_DEKIRUKANA, "Dekirukana", false, "1999-03-25", null, null, "https://www.alicesoft.com/"},
            {GameCatalog.ID_TATAKIKOROSHI, "Tatakikoroshi", false, "1999-07-01", null, null, "https://www.alicesoft.com/"},
            {GameCatalog.ID_BALLOONPOP, "BalloonPop", false, "1999-03-25", null, null, "https://www.alicesoft.com/"},
            {GameCatalog.ID_HIYOKO2, "Hiyoko2", false, "2000-04-13", null, null, "https://www.alicesoft.com/"},
            {GameCatalog.ID_SELFGAME, "SelfGame", false, "1999-11-18", null, null, "https://www.alicesoft.com/"}
        };

        for (Object[] spec : specs) {
            String id = (String) spec[0];
            String dirName = (String) spec[1];
            boolean hasCn = (Boolean) spec[2];
            String releaseDate = (String) spec[3];
            Double rating = (Double) spec[4];
            String scoreUrl = (String) spec[5];
            String releaseUrl = (String) spec[6];

            assertTrue(id, order.contains(id));
            GameDescriptor desc = GameCatalog.INSTANCE.descriptorForWithResolver(id, null, resId -> id);
            assertEquals(dirName, desc.getDefaultDirName());
            assertNull(id, desc.getExpectedAinName());
            assertTrue("Expected supportsOnlineBaseInstall=true for " + id,
                    desc.getSupportsOnlineBaseInstall());
            String expectedUrl = "https://alicehub.000.moe/" + id;
            assertEquals(id, expectedUrl, desc.getOnlineBaseCdnUrl());
            assertTrue("Expected default byte size > 0 for " + id,
                    AppSettings.INSTANCE.defaultBaseGameSizeForUrl(expectedUrl) > 0L);
            assertEquals(id, hasCn, desc.getHasChinese());

            GameSortInfo meta = GameSortMetadata.INSTANCE.forGame(id);
            assertNotNull(id, meta);
            assertEquals(releaseDate, meta.getReleaseDate());
            assertEquals(rating, meta.getRating());
            assertEquals(scoreUrl, meta.getScoreSourceUrl());
            assertEquals(releaseUrl, meta.getReleaseSourceUrl());
        }
    }

    @Test
    public void wave5FileAndAliasClassificationAvoidsCollisions() {
        // System 4 CN titles + UIF local Chinese marker
        assertEquals(GameCatalog.ID_BLADEBRIDERS, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("AliceStart.ini", "burebura.ain", "bureburaCG.afa", "uif_config.json")));
        assertEquals(GameCatalog.ID_DORAPEKO, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("AliceStart.ini", "どらぺこ！.ain", "どらぺこ！CG.afa", "uif_config.json")));
        assertEquals(GameCatalog.ID_HANIWAPETIT, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("AliceStart.ini", "ハニワ闇開発室ぷち.ain", "ハニワ闇開発室ぷちCG.afa")));
        assertTrue(GameCatalog.hasLocalChineseMarkers(Arrays.asList("AliceStart.ini", "burebura.ain", "uif_config.json")));

        // Official System 3.x titles (including GakuenHyouryuu2 vs GakuenHyouryuu)
        assertEquals(GameCatalog.ID_YAKATA456, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("４５６_SA.ALD", "４５６_GA.ALD", "４５６_MA.ALD")));
        assertEquals(GameCatalog.ID_UMINIOCHITE, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("蒼海SA.ald", "蒼海GA.ald")));
        assertEquals(GameCatalog.ID_GAKUENHYOURYUU2, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("戦記２SA.ALD", "戦記２GA.ALD")));
        assertEquals(GameCatalog.ID_YOUSEI, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("System38.ini", "妖精SA.ald", "妖精GA.ald")));
        assertEquals(GameCatalog.ID_TOBIRA, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("System39.ini", "System39.ain", "［扉］SA.ALD", "［扉］GA.ALD")));
        assertEquals(GameCatalog.ID_BUROBURO, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("壁SA.ALD", "壁GA.ALD")));
        assertEquals(GameCatalog.ID_KUDAKERO, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("System38.ini", "当砕SA.ALD", "当砕GA.ALD")));

        // Alice CD System 3.x bonus titles
        assertEquals(GameCatalog.ID_CAROL, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("キャロルsa.ald", "キャロルga.ald")));
        assertEquals(GameCatalog.ID_OTHELLOT, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("おせろっとSA.ALD", "おせろっとGA.ALD")));
        assertEquals(GameCatalog.ID_MASQUERADE, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("MQN_sa.ald", "MQN_GA.ALD")));
        assertEquals(GameCatalog.ID_DEKIRUKANA, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("できSA.ALD", "できGA.ALD")));
        assertEquals(GameCatalog.ID_TATAKIKOROSHI, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("叩き殺しSA.ALD", "叩き殺しGA.ALD")));
        assertEquals(GameCatalog.ID_BALLOONPOP, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("風船_SA.ALD", "風船_GA.ALD")));
        assertEquals(GameCatalog.ID_HIYOKO2, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("sa.ald", "GA.ALD", "piyod.dat", "hsc.dat")));
        assertEquals(GameCatalog.ID_SELFGAME, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("SelfSA.ald", "SelfGA.ald")));

        // Classic System 1/2/3 titles — collision checks against existing siblings
        assertTrue(GameCatalog.INSTANCE.isLegacySystem3GameFiles(Arrays.asList("ADISK.DAT", "ACG3.DAT", "ACG4.DAT")));
        assertEquals(GameCatalog.ID_AYUMILIVE, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("ADISK.DAT", "ACG3.DAT", "ACG4.DAT")));
        assertEquals(GameCatalog.ID_PROGOMAKE, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("ADISK.DAT", "ACG.DAT", "AGAME.DAT", "system3.ini"),
                Arrays.asList(),
                name -> "system3.ini".equalsIgnoreCase(name)
                        ? "[config]\ngame = prog_omake\n".getBytes(StandardCharsets.UTF_8) : null));
        assertEquals(GameCatalog.ID_SDPSTONO, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("ADISK.DAT", "BDISK.DAT", "ACG.DAT", "system3.ini"),
                Arrays.asList(),
                name -> "system3.ini".equalsIgnoreCase(name)
                        ? "[config]\ngame = sdps_tono\n".getBytes(StandardCharsets.UTF_8) : null));
        assertEquals(GameCatalog.ID_SDPSKAIZOKU, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("ADISK.DAT", "BDISK.DAT", "ACG.DAT", "system3.ini"),
                Arrays.asList(),
                name -> "system3.ini".equalsIgnoreCase(name)
                        ? "[config]\ngame = sdps_kaizoku\n".getBytes(StandardCharsets.UTF_8) : null));

        // Directory/alias classification avoids collisions with siblings
        assertEquals(GameCatalog.ID_GAKUENHYOURYUU2, GameCatalog.INSTANCE.classifyDirectory(null, "GakuenHyouryuu2", null, null));
        assertEquals(GameCatalog.ID_GAKUENHYOURYUU, GameCatalog.INSTANCE.classifyDirectory(null, "GakuenHyouryuu", null, null));
        assertEquals(GameCatalog.ID_SDPSTONO, GameCatalog.INSTANCE.classifyDirectory(null, "Super D.P.S 远野之森", null, null));
        assertEquals(GameCatalog.ID_SDPSKAIZOKU, GameCatalog.INSTANCE.classifyDirectory(null, "Super D.P.S 欢乐海盗生涯", null, null));
        assertEquals(GameCatalog.ID_SDPSMARIA, GameCatalog.INSTANCE.classifyDirectory(null, "Super D.P.S 玛利亚和坎潘", null, null));
        assertEquals(GameCatalog.ID_DPS, GameCatalog.INSTANCE.classifyDirectory(null, "DPS", null, null));
        assertEquals(GameCatalog.ID_DPSALL, GameCatalog.INSTANCE.classifyDirectory(null, "DpsAll", null, null));
        assertEquals(GameCatalog.ID_YAKATA1, GameCatalog.INSTANCE.classifyDirectory(null, "ALICEの館", null, null));
        assertEquals(GameCatalog.ID_YAKATA2, GameCatalog.INSTANCE.classifyDirectory(null, "ALICEの館2", null, null));
        assertEquals(GameCatalog.ID_YAKATA3, GameCatalog.INSTANCE.classifyDirectory(null, "ALICEの館3", null, null));
        assertEquals(GameCatalog.ID_YAKATA456, GameCatalog.INSTANCE.classifyDirectory(null, "アリスの館4・5・6", null, null));
        assertEquals(GameCatalog.ID_AYUMILIVE, GameCatalog.INSTANCE.classifyDirectory(null, "あゆみちゃん物語 実写版", null, null));
        assertEquals(GameCatalog.ID_AYUMIHINT, GameCatalog.INSTANCE.classifyDirectory(null, "あゆみちゃん物語 ヒントディスク", null, null));
        assertEquals(GameCatalog.ID_AYUMI, GameCatalog.INSTANCE.classifyDirectory(null, "あゆみちゃん物語", null, null));
        assertEquals(GameCatalog.ID_PROGOMAKE, GameCatalog.INSTANCE.classifyDirectory(null, "Prostudent G Omake", null, null));
        assertEquals(GameCatalog.ID_PROSTUDENTG, GameCatalog.INSTANCE.classifyDirectory(null, "ProstudentG", null, null));
    }

    private static byte[] makeBytesWithPrefixCrc32(long targetCrc, int totalLen) {
        byte[] buf = new byte[Math.max(256, totalLen)];
        int[] table = new int[256];
        int[] rev = new int[256];
        for (int i = 0; i < 256; i++) {
            int v = i;
            for (int b = 0; b < 8; b++) {
                v = ((v & 1) != 0) ? ((v >>> 1) ^ 0xedb88320) : (v >>> 1);
            }
            table[i] = v;
            rev[(v >>> 24) & 0xff] = i;
        }
        int forward = 0xffffffff;
        for (int i = 0; i < 252; i++) {
            forward = (forward >>> 8) ^ table[(forward ^ (buf[i] & 0xff)) & 0xff];
        }
        int r = (int) (targetCrc ^ 0xffffffffL);
        int[] idx = new int[4];
        for (int i = 3; i >= 0; i--) {
            idx[i] = rev[(r >>> 24) & 0xff];
            r = (r ^ table[idx[i]]) << 8;
        }
        for (int i = 0; i < 4; i++) {
            int b = (forward ^ idx[i]) & 0xff;
            buf[252 + i] = (byte) b;
            forward = (forward >>> 8) ^ table[(forward ^ b) & 0xff];
        }
        return buf;
    }

    @Test
    public void largeBdiskPrefixReadClassifiesSuperDpsFolderImportsWithoutIni() throws Exception {
        int largeSize = 300 * 1024;
        assertEquals(Integer.valueOf(256),
                NativeGameImport.INSTANCE.boundedPrimaryReadLimitBytes("ADISK.DAT", largeSize));
        assertEquals(Integer.valueOf(256),
                NativeGameImport.INSTANCE.boundedPrimaryReadLimitBytes("BDISK.DAT", largeSize));
        assertNull(NativeGameImport.INSTANCE.boundedPrimaryReadLimitBytes("ACG.DAT", largeSize));

        byte[] adisk = makeBytesWithPrefixCrc32(0xc7a20cdfL, largeSize);
        byte[] bdiskTono = makeBytesWithPrefixCrc32(0xbb1edff1L, largeSize);
        byte[] bdiskKaizoku = makeBytesWithPrefixCrc32(0xf81829e3L, largeSize);
        List<String> files = Arrays.asList("ADISK.DAT", "BDISK.DAT", "ACG.DAT");

        // Simulate NativeGameImport.copyTree's boundedPrimaryReadLimitBytes callback for >256 KiB BDISK.DAT
        assertEquals(GameCatalog.ID_SDPSTONO, GameCatalog.INSTANCE.classifyGameRootFiles(
                files, Arrays.asList(), name -> {
                    Integer limit = NativeGameImport.INSTANCE.boundedPrimaryReadLimitBytes(name, largeSize);
                    if (limit == null) return null;
                    if ("ADISK.DAT".equalsIgnoreCase(name)) return Arrays.copyOf(adisk, limit);
                    if ("BDISK.DAT".equalsIgnoreCase(name)) return Arrays.copyOf(bdiskTono, limit);
                    return null;
                }));
        assertEquals(GameCatalog.ID_SDPSKAIZOKU, GameCatalog.INSTANCE.classifyGameRootFiles(
                files, Arrays.asList(), name -> {
                    Integer limit = NativeGameImport.INSTANCE.boundedPrimaryReadLimitBytes(name, largeSize);
                    if (limit == null) return null;
                    if ("ADISK.DAT".equalsIgnoreCase(name)) return Arrays.copyOf(adisk, limit);
                    if ("BDISK.DAT".equalsIgnoreCase(name)) return Arrays.copyOf(bdiskKaizoku, limit);
                    return null;
                }));
        assertTrue(GameCatalog.INSTANCE.matchesTargetGameFiles(
                GameCatalog.ID_SDPSTONO, files, Arrays.asList(), name -> {
                    Integer limit = NativeGameImport.INSTANCE.boundedPrimaryReadLimitBytes(name, largeSize);
                    if (limit == null) return null;
                    if ("ADISK.DAT".equalsIgnoreCase(name)) return Arrays.copyOf(adisk, limit);
                    if ("BDISK.DAT".equalsIgnoreCase(name)) return Arrays.copyOf(bdiskTono, limit);
                    return null;
                }));
        assertEquals("SuperDpsTono", NativeGameImport.INSTANCE.resolveCopyTreeBaseName(
                files, GameCatalog.ID_SDPSTONO, GameCatalog.ID_SDPSTONO, false, name -> {
                    Integer limit = NativeGameImport.INSTANCE.boundedPrimaryReadLimitBytes(name, largeSize);
                    if (limit == null) return null;
                    if ("ADISK.DAT".equalsIgnoreCase(name)) return Arrays.copyOf(adisk, limit);
                    if ("BDISK.DAT".equalsIgnoreCase(name)) return Arrays.copyOf(bdiskTono, limit);
                    return null;
                }, null));
        assertEquals("SuperDpsKaizoku", NativeGameImport.INSTANCE.resolveCopyTreeBaseName(
                files, GameCatalog.ID_SDPSKAIZOKU, GameCatalog.ID_SDPSKAIZOKU, false, name -> {
                    Integer limit = NativeGameImport.INSTANCE.boundedPrimaryReadLimitBytes(name, largeSize);
                    if (limit == null) return null;
                    if ("ADISK.DAT".equalsIgnoreCase(name)) return Arrays.copyOf(adisk, limit);
                    if ("BDISK.DAT".equalsIgnoreCase(name)) return Arrays.copyOf(bdiskKaizoku, limit);
                    return null;
                }, null));
    }
}

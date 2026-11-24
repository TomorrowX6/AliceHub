package io.github.kichikuou.xsystem4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;

/**
 * Unit tests for Wave 1B classic System 3 / System 3.x AliceSoft additions:
 * - DiaboLiQuE (diabolique)
 * - Gakuen KING (gakuenking, Legacy System 3)
 * - Gakuen KING Bangai Hen (gakuenkingex)
 * - Oudou Yuusha (oudouyuusha)
 * - Toushin Toshi (toushin1)
 * - Toushin Toshi II (toushin2)
 * - DARCROWS (darcrows, CN dialogue-patched)
 * - Ikusa Miko -Vestal Virgin- (ikusamiko)
 * - Only You -Seikimatsu no Juliette-tachi- (onlyyou, Legacy System 3)
 */
public class Wave1BCatalogTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static byte[] make256BytesWithCrc32(long targetCrc) {
        // Construct a 256-byte buffer whose CRC32 equals targetCrc by solving the last 4 bytes.
        byte[] buf = new byte[256];
        CRC32 crc = new CRC32();
        crc.update(buf, 0, 252);
        int forward = (int) (crc.getValue() ^ 0xffffffffL);
        int want = (int) (targetCrc ^ 0xffffffffL);
        int[] crcTable = new int[256];
        for (int i = 0; i < 256; i++) {
            int v = i;
            for (int k = 0; k < 8; k++) {
                v = ((v & 1) != 0) ? (0xedb88320 ^ (v >>> 1)) : (v >>> 1);
            }
            crcTable[i] = v;
        }
        int[] revIdx = new int[256];
        for (int i = 0; i < 256; i++) {
            revIdx[(crcTable[i] >>> 24) & 0xff] = i;
        }
        int r = want;
        int[] tableIdx = new int[4];
        for (int i = 3; i >= 0; i--) {
            tableIdx[i] = revIdx[(r >>> 24) & 0xff];
            r = (r ^ crcTable[tableIdx[i]]) << 8;
        }
        for (int i = 0; i < 4; i++) {
            int b = (forward ^ tableIdx[i]) & 0xff;
            buf[252 + i] = (byte) b;
            forward = (forward >>> 8) ^ crcTable[(forward ^ b) & 0xff];
        }
        return buf;
    }

    @Test
    public void wave1BCatalogOrderDescriptorsAndMetadataAreWired() {
        List<String> order = GameCatalog.builtInSelectOrder();
        assertEquals(158, order.size());

        Object[][] specs = new Object[][]{
            {GameCatalog.ID_DIABOLIQUE, "DiaboLiQuE", OnlineInstaller.DIABOLIQUE_CDN_URL, 212543299L, "1998-05-28", 76.4, "https://vndb.org/v2261"},
            {GameCatalog.ID_GAKUENKING, "GakuenKING", OnlineInstaller.GAKUENKING_CDN_URL, 83874519L, "1996-06-07", 65.1, "https://vndb.org/v2461"},
            {GameCatalog.ID_GAKUENKINGEX, "GakuenKINGEx", OnlineInstaller.GAKUENKINGEX_CDN_URL, 6975808L, "1998-11-26", null, null},
            {GameCatalog.ID_OUDOUYUUSHA, "OudouYuusha", OnlineInstaller.OUDOUYUUSHA_CDN_URL, 162434751L, "1998-04-02", 65.3, "https://vndb.org/v4476"},
            {GameCatalog.ID_TOUSHIN1, "Toushin1", OnlineInstaller.TOUSHIN1_CDN_URL, 6276307L, "1990-12-15", 59.9, "https://vndb.org/v888"},
            {GameCatalog.ID_TOUSHIN2, "Toushin2", OnlineInstaller.TOUSHIN2_CDN_URL, 168804761L, "1994-12-10", 76.4, "https://vndb.org/v889"},
            {GameCatalog.ID_DARCROWS, "DARCROWS", OnlineInstaller.DARCROWS_CDN_URL, 558857840L, "1999-11-18", 70.5, "https://vndb.org/v1095"},
            {GameCatalog.ID_IKUSAMIKO, "IkusaMiko", OnlineInstaller.IKUSAMIKO_CDN_URL, 381340553L, "1997-10-30", 54.8, "https://vndb.org/v3083"},
            {GameCatalog.ID_ONLYYOU, "OnlyYou", OnlineInstaller.ONLYYOU_CDN_URL, 77592471L, "1996-01-01", 71.2, "https://vndb.org/v2003"}
        };

        for (Object[] spec : specs) {
            String id = (String) spec[0];
            String dirName = (String) spec[1];
            String cdnUrl = (String) spec[2];
            long sizeBytes = (Long) spec[3];
            String releaseDate = (String) spec[4];
            Double rating = (Double) spec[5];
            String scoreUrl = (String) spec[6];

            assertTrue(id, order.contains(id));
            GameDescriptor desc = GameCatalog.INSTANCE.descriptorForWithResolver(id, null, resId -> id);
            assertEquals(dirName, desc.getDefaultDirName());
            assertNull(id, desc.getExpectedAinName());
            assertEquals(cdnUrl, desc.getOnlineBaseCdnUrl());
            assertTrue(id, desc.getSupportsOnlineBaseInstall());
            assertFalse(id, desc.getSupportsOnlineMod());
            assertEquals(sizeBytes, AppSettings.defaultBaseGameSizeForUrl(cdnUrl));

            GameSortInfo meta = GameSortMetadata.INSTANCE.forGame(id);
            assertNotNull(id, meta);
            assertEquals(releaseDate, meta.getReleaseDate());
            assertEquals(rating, meta.getRating());
            assertEquals(scoreUrl, meta.getScoreSourceUrl());
        }
    }

    @Test
    public void wave1BFileAndAliasClassificationAvoidsCollisions() throws Exception {
        // System 3.x ALD classification
        assertEquals(GameCatalog.ID_DIABOLIQUE, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("DIABO_SA.ALD", "DIABO_GA.ALD", "playlist.txt")));
        assertEquals(GameCatalog.ID_GAKUENKINGEX, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("学園番外編SA.ald", "学園番外編GA.ald", "学園番外編MA.ald")));
        assertEquals(GameCatalog.ID_OUDOUYUUSHA, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("王道SA.ALD", "王道GA.ALD", "playlist.txt")));
        assertEquals(GameCatalog.ID_TOUSHIN1, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("System39.ain", "System39.ini", "TOUSHINSA.ALD", "TOUSHINGA.ALD")));
        assertEquals(GameCatalog.ID_TOUSHIN2, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("System39.ain", "tt2SA.ALD", "tt2GA.ald", "playlist.txt")));
        assertEquals(GameCatalog.ID_TOUSHIN3, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("System40.ini", "闘神都市３.ain", "闘神都市３GA.ald")));
        assertEquals(GameCatalog.ID_DARCROWS, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("HIME_SA.ALD", "HIME_GA.ALD", "HIME_MA.ALD")));
        assertEquals(GameCatalog.ID_IKUSAMIKO, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("戦巫女SA.ALD", "戦巫女GA.ALD", "playlist.txt")));

        // Legacy System 3 classification via system3.ini and ADISK.DAT CRC32
        File gkDir = tmp.newFolder("legacy_gakuenking");
        assertTrue(new File(gkDir, "ADISK.DAT").createNewFile());
        assertTrue(new File(gkDir, "ACG.DAT").createNewFile());
        try (FileOutputStream out = new FileOutputStream(new File(gkDir, "system3.ini"))) {
            out.write("game = gakuen_king\n".getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(GameCatalog.ID_GAKUENKING, GameCatalog.INSTANCE.classifyGameRootDir(gkDir));
        assertTrue(GameCatalog.INSTANCE.validateImportedGameDir(gkDir, null, GameCatalog.ID_GAKUENKING));
        assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(gkDir, null, GameCatalog.ID_GAKUENKINGEX));
        assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(gkDir, null, GameCatalog.ID_ONLYYOU));

        File gkCrcDir = tmp.newFolder("legacy_gakuenking_crc");
        assertTrue(new File(gkCrcDir, "ACG.DAT").createNewFile());
        byte[] gkHeader = make256BytesWithCrc32(0xd1bf243bL);
        assertEquals(Long.valueOf(0xd1bf243bL), GameCatalog.INSTANCE.legacyAdiskCrc32(gkHeader));
        try (FileOutputStream out = new FileOutputStream(new File(gkCrcDir, "ADISK.DAT"))) {
            out.write(gkHeader);
        }
        assertEquals(GameCatalog.ID_GAKUENKING, GameCatalog.INSTANCE.classifyGameRootDir(gkCrcDir));

        File oyDir = tmp.newFolder("legacy_onlyyou");
        assertTrue(new File(oyDir, "ADISK.DAT").createNewFile());
        assertTrue(new File(oyDir, "ACG.DAT").createNewFile());
        try (FileOutputStream out = new FileOutputStream(new File(oyDir, "system3.ini"))) {
            out.write("game = onlyyou\n".getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(GameCatalog.ID_ONLYYOU, GameCatalog.INSTANCE.classifyGameRootDir(oyDir));
        assertTrue(GameCatalog.INSTANCE.validateImportedGameDir(oyDir, null, GameCatalog.ID_ONLYYOU));
        assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(oyDir, null, GameCatalog.ID_GAKUENKING));

        File oyCrcDir = tmp.newFolder("legacy_onlyyou_crc");
        assertTrue(new File(oyCrcDir, "ACG.DAT").createNewFile());
        byte[] oyHeader = make256BytesWithCrc32(0x832aeb97L);
        assertEquals(Long.valueOf(0x832aeb97L), GameCatalog.INSTANCE.legacyAdiskCrc32(oyHeader));
        try (FileOutputStream out = new FileOutputStream(new File(oyCrcDir, "ADISK.DAT"))) {
            out.write(oyHeader);
        }
        assertEquals(GameCatalog.ID_ONLYYOU, GameCatalog.INSTANCE.classifyGameRootDir(oyCrcDir));

        // Toushin 1 / 2 / 3 alias non-collision (including SJIS-as-GBK mojibake)
        assertTrue(GameCatalog.INSTANCE.isToushin1Alias("斗神都市"));
        assertTrue(GameCatalog.INSTANCE.isToushin1Alias("闘神都市"));
        assertTrue(GameCatalog.INSTANCE.isToushin1Alias("摤恄搒巗"));
        assertFalse(GameCatalog.INSTANCE.isToushin2Alias("斗神都市"));
        assertFalse(GameCatalog.INSTANCE.isToushin3Alias("斗神都市"));
        assertFalse(GameCatalog.INSTANCE.isToushin3Alias("摤恄搒巗"));

        assertTrue(GameCatalog.INSTANCE.isToushin2Alias("斗神都市II"));
        assertTrue(GameCatalog.INSTANCE.isToushin2Alias("闘神都市II"));
        assertTrue(GameCatalog.INSTANCE.isToushin2Alias("Toushin Toshi II"));
        assertTrue(GameCatalog.INSTANCE.isToushin2Alias("摤恄搒巗俀"));
        assertFalse(GameCatalog.INSTANCE.isToushin1Alias("斗神都市II"));
        assertFalse(GameCatalog.INSTANCE.isToushin3Alias("斗神都市II"));

        assertTrue(GameCatalog.INSTANCE.isToushin3Alias("斗神都市3"));
        assertTrue(GameCatalog.INSTANCE.isToushin3Alias("闘神都市III"));
        assertTrue(GameCatalog.INSTANCE.isToushin3Alias("摤恄搒巗俁"));
        assertFalse(GameCatalog.INSTANCE.isToushin1Alias("斗神都市3"));
        assertFalse(GameCatalog.INSTANCE.isToushin2Alias("斗神都市3"));

        // GakuenKING vs GakuenKINGEx alias non-collision
        assertTrue(GameCatalog.INSTANCE.isGakuenKingAlias("学园KING"));
        assertFalse(GameCatalog.INSTANCE.isGakuenKingExAlias("学园KING"));
        assertTrue(GameCatalog.INSTANCE.isGakuenKingExAlias("学园KING 番外篇"));
        assertTrue(GameCatalog.INSTANCE.isGakuenKingExAlias("学園KING番外編"));
        assertFalse(GameCatalog.INSTANCE.isGakuenKingAlias("学园KING 番外篇"));

        // Only You vs future Re Cross non-collision
        assertTrue(GameCatalog.INSTANCE.isOnlyYouAlias("Only You 世纪末的朱丽叶们"));
        assertFalse(GameCatalog.INSTANCE.isOnlyYouAlias("Only You ～リ・クルス～"));

        // Directory classification by name
        assertEquals(GameCatalog.ID_TOUSHIN1, GameCatalog.INSTANCE.classifyDirectory(null, "Toushin1", null, "斗神都市"));
        assertEquals(GameCatalog.ID_TOUSHIN2, GameCatalog.INSTANCE.classifyDirectory(null, "Toushin2", null, "斗神都市II"));
        assertEquals(GameCatalog.ID_TOUSHIN3, GameCatalog.INSTANCE.classifyDirectory(null, "Toushin3", null, "斗神都市3"));
        assertEquals(GameCatalog.ID_GAKUENKING, GameCatalog.INSTANCE.classifyDirectory(null, "GakuenKING", null, "学园KING"));
        assertEquals(GameCatalog.ID_GAKUENKINGEX, GameCatalog.INSTANCE.classifyDirectory(null, "GakuenKINGEx", null, "学园KING 番外篇"));
        assertEquals(GameCatalog.ID_DIABOLIQUE, GameCatalog.INSTANCE.classifyDirectory(null, "DiaboLiQuE", null, "魔鬼恋曲 DiaboLiQuE"));
        assertEquals(GameCatalog.ID_OUDOUYUUSHA, GameCatalog.INSTANCE.classifyDirectory(null, "OudouYuusha", null, "王道勇者"));
        assertEquals(GameCatalog.ID_DARCROWS, GameCatalog.INSTANCE.classifyDirectory(null, "DARCROWS", null, "暗鸦 DARCROWS"));
        assertEquals(GameCatalog.ID_IKUSAMIKO, GameCatalog.INSTANCE.classifyDirectory(null, "IkusaMiko", null, "战巫女"));
        assertEquals(GameCatalog.ID_ONLYYOU, GameCatalog.INSTANCE.classifyDirectory(null, "OnlyYou", null, "Only You 世纪末的朱丽叶们"));
    }

    @Test
    public void wave1BInstallDirNamesStagingCleanupAndModifierGuardsWork() throws Exception {
        File diaboDir = tmp.newFolder("stage_diabo");
        assertTrue(new File(diaboDir, "DIABO_SA.ALD").createNewFile());
        assertEquals("DiaboLiQuE", OnlineInstaller.INSTANCE.resolveInstallDirBaseName(diaboDir, "DiaboLiQuE"));
        assertTrue(GameModifierBridge.INSTANCE.isDiaboliqueGameDir(diaboDir));
        assertTrue(GameModifierBridge.INSTANCE.isNonModifierBuiltInGameDir(diaboDir));

        File gkExDir = tmp.newFolder("stage_gkex");
        assertTrue(new File(gkExDir, "学園番外編SA.ald").createNewFile());
        assertEquals("GakuenKINGEx", OnlineInstaller.INSTANCE.resolveInstallDirBaseName(gkExDir, "GakuenKINGEx"));
        assertTrue(GameModifierBridge.INSTANCE.isGakuenKingExGameDir(gkExDir));
        assertTrue(GameModifierBridge.INSTANCE.isNonModifierBuiltInGameDir(gkExDir));

        File oudouDir = tmp.newFolder("stage_oudou");
        assertTrue(new File(oudouDir, "王道SA.ALD").createNewFile());
        assertEquals("OudouYuusha", OnlineInstaller.INSTANCE.resolveInstallDirBaseName(oudouDir, "OudouYuusha"));
        assertTrue(GameModifierBridge.INSTANCE.isOudouYuushaGameDir(oudouDir));
        assertTrue(GameModifierBridge.INSTANCE.isNonModifierBuiltInGameDir(oudouDir));

        File t1Dir = tmp.newFolder("stage_t1");
        assertTrue(new File(t1Dir, "TOUSHINSA.ALD").createNewFile());
        assertEquals("Toushin1", OnlineInstaller.INSTANCE.resolveInstallDirBaseName(t1Dir, "Toushin1"));
        assertTrue(GameModifierBridge.INSTANCE.isToushin1GameDir(t1Dir));
        assertTrue(GameModifierBridge.INSTANCE.isNonModifierBuiltInGameDir(t1Dir));

        File t2Dir = tmp.newFolder("stage_t2");
        assertTrue(new File(t2Dir, "tt2SA.ALD").createNewFile());
        assertEquals("Toushin2", OnlineInstaller.INSTANCE.resolveInstallDirBaseName(t2Dir, "Toushin2"));
        assertTrue(GameModifierBridge.INSTANCE.isToushin2GameDir(t2Dir));
        assertTrue(GameModifierBridge.INSTANCE.isNonModifierBuiltInGameDir(t2Dir));

        File darcDir = tmp.newFolder("stage_darc");
        assertTrue(new File(darcDir, "HIME_SA.ALD").createNewFile());
        assertEquals("DARCROWS", OnlineInstaller.INSTANCE.resolveInstallDirBaseName(darcDir, "DARCROWS"));
        assertTrue(GameModifierBridge.INSTANCE.isDarcrowsGameDir(darcDir));
        assertTrue(GameModifierBridge.INSTANCE.isNonModifierBuiltInGameDir(darcDir));

        File mikoDir = tmp.newFolder("stage_miko");
        assertTrue(new File(mikoDir, "戦巫女SA.ALD").createNewFile());
        assertEquals("IkusaMiko", OnlineInstaller.INSTANCE.resolveInstallDirBaseName(mikoDir, "IkusaMiko"));
        assertTrue(GameModifierBridge.INSTANCE.isIkusaMikoGameDir(mikoDir));
        assertTrue(GameModifierBridge.INSTANCE.isNonModifierBuiltInGameDir(mikoDir));

        // Verify staging cleanup non-collision across Toushin 1/2/3 and GakuenKING/Ex
        File storage = tmp.newFolder("storage");
        File t1Stage = new File(storage, ".online_base_staging_闘神都市");
        File t2Stage = new File(storage, ".online_base_staging_闘神都市2");
        File t3Stage = new File(storage, ".online_base_staging_闘神都市3");
        File gkStage = new File(storage, ".online_base_staging_gakuenking");
        File gkExStage = new File(storage, ".online_base_staging_gakuenkingex");
        for (File f : Arrays.asList(t1Stage, t2Stage, t3Stage, gkStage, gkExStage)) {
            assertTrue(f.mkdirs());
        }
        OnlineInstaller.INSTANCE.cleanupBaseStagingForGame(storage, GameCatalog.ID_TOUSHIN3, null);
        assertFalse(t3Stage.exists());
        assertTrue(t2Stage.exists());
        assertTrue(t1Stage.exists());

        OnlineInstaller.INSTANCE.cleanupBaseStagingForGame(storage, GameCatalog.ID_TOUSHIN2, null);
        assertFalse(t2Stage.exists());
        assertTrue(t1Stage.exists());

        OnlineInstaller.INSTANCE.cleanupBaseStagingForGame(storage, GameCatalog.ID_TOUSHIN1, null);
        assertFalse(t1Stage.exists());

        OnlineInstaller.INSTANCE.cleanupBaseStagingForGame(storage, GameCatalog.ID_GAKUENKING, null);
        assertFalse(gkStage.exists());
        assertTrue(gkExStage.exists());

        OnlineInstaller.INSTANCE.cleanupBaseStagingForGame(storage, GameCatalog.ID_GAKUENKINGEX, null);
        assertFalse(gkExStage.exists());

        // NativeGameImport dir name suggestions
        assertEquals("DiaboLiQuE", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("DIABO_SA.ALD", "DIABO_GA.ALD")));
        assertEquals("GakuenKINGEx", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("学園番外編SA.ald", "学園番外編GA.ald")));
        assertEquals("OudouYuusha", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("王道SA.ALD", "王道GA.ALD")));
        assertEquals("Toushin1", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("System39.ain", "TOUSHINSA.ALD")));
        assertEquals("Toushin2", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("System39.ain", "tt2SA.ALD")));
        assertEquals("DARCROWS", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("HIME_SA.ALD", "HIME_GA.ALD")));
        assertEquals("IkusaMiko", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("戦巫女SA.ALD", "戦巫女GA.ALD")));

        // Verify copyTree destination naming uses canonical names for legacy GakuenKING / OnlyYou
        // even when system3.ini has no title or is absent (ADISK.DAT CRC32-only).
        List<String> legacyWithIni = Arrays.asList("ADISK.DAT", "ACG.DAT", "system3.ini");
        assertEquals("GakuenKING", NativeGameImport.INSTANCE.resolveCopyTreeBaseName(
                legacyWithIni,
                GameCatalog.INSTANCE.classifyGameRootFiles(legacyWithIni, Arrays.asList(), name ->
                        name.equalsIgnoreCase("system3.ini") ? "game = gakuen_king\n".getBytes(StandardCharsets.UTF_8) : null),
                null,
                false,
                name -> name.equalsIgnoreCase("system3.ini") ? "game = gakuen_king\n".getBytes(StandardCharsets.UTF_8) : null));
        assertEquals("OnlyYou", NativeGameImport.INSTANCE.resolveCopyTreeBaseName(
                legacyWithIni,
                GameCatalog.INSTANCE.classifyGameRootFiles(legacyWithIni, Arrays.asList(), name ->
                        name.equalsIgnoreCase("system3.ini") ? "game = onlyyou\n".getBytes(StandardCharsets.UTF_8) : null),
                null,
                false,
                name -> name.equalsIgnoreCase("system3.ini") ? "game = onlyyou\n".getBytes(StandardCharsets.UTF_8) : null));

        List<String> legacyCrcOnly = Arrays.asList("ADISK.DAT", "ACG.DAT");
        byte[] gkHeader = make256BytesWithCrc32(0xd1bf243bL);
        byte[] oyHeader = make256BytesWithCrc32(0x832aeb97L);
        assertEquals("GakuenKING", NativeGameImport.INSTANCE.resolveCopyTreeBaseName(
                legacyCrcOnly,
                GameCatalog.INSTANCE.classifyGameRootFiles(legacyCrcOnly, Arrays.asList(), name ->
                        name.equalsIgnoreCase("ADISK.DAT") ? gkHeader : null),
                null,
                false,
                name -> name.equalsIgnoreCase("ADISK.DAT") ? gkHeader : null));
        assertEquals("OnlyYou", NativeGameImport.INSTANCE.resolveCopyTreeBaseName(
                legacyCrcOnly,
                GameCatalog.INSTANCE.classifyGameRootFiles(legacyCrcOnly, Arrays.asList(), name ->
                        name.equalsIgnoreCase("ADISK.DAT") ? oyHeader : null),
                null,
                false,
                name -> name.equalsIgnoreCase("ADISK.DAT") ? oyHeader : null));
    }
}

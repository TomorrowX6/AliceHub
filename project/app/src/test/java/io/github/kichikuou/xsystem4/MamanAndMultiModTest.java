package io.github.kichikuou.xsystem4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MamanAndMultiModTest {
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

    private static void writeText(File file, String text) throws Exception {
        writeFile(file, text.getBytes(StandardCharsets.UTF_8));
    }

    private File makeDir(String folderName, String... fileNames) throws Exception {
        File dir = tmp.newFolder(folderName);
        for (String name : fileNames) {
            writeFile(new File(dir, name), new byte[]{1});
        }
        return dir;
    }

    @Test
    public void heartfulMamanCatalogClassificationMetadataAndSnapshotParity() throws Exception {
        File maman = makeDir(
                "HeartfulMamanTest",
                "AliceStart.ini",
                "Maman.ain",
                "Maman.ex",
                "MamanCG.afa",
                "MamanFlat.afa",
                "MamanFont.fnl",
                "MamanPact.afa",
                "MamanSound.afa",
                "MamanVoice.afa"
        );
        assertTrue(GameListKt.isSystem4GameRoot(maman));
        assertFalse(GameCatalog.INSTANCE.isSystem3xGameDir(maman));
        assertEquals(GameCatalog.ID_MAMAN, GameCatalog.INSTANCE.classifyGameRootDir(maman));
        assertEquals(GameCatalog.ID_MAMAN, GameCatalog.INSTANCE.classifyDirectory(maman, "UnknownFolder", null, null));
        assertTrue(GameCatalog.INSTANCE.validateImportedGameDir(maman, null, GameCatalog.ID_MAMAN));
        assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(maman, null, GameCatalog.ID_RANCE10));
        assertEquals("Maman", OnlineInstaller.INSTANCE.resolveInstallDirBaseName(maman, "Maman"));
        assertEquals("Maman", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(
                Arrays.asList("AliceStart.ini", "Maman.ain", "MamanCG.afa"), false));

        assertTrue(GameModifierBridge.INSTANCE.isMamanGameDir(maman));
        assertTrue(GameModifierBridge.INSTANCE.isNonModifierBuiltInGameDir(maman));
        assertFalse(GameModifierBridge.INSTANCE.isRance10GameDir(maman, null, null));

        for (String alias : new String[]{
                "maman", "Heartful Maman", "は～とふるママン", "は〜とふるママン",
                "はーとふるママン", "再婚妈妈", "再婚媽媽"
        }) {
            assertTrue("Expected Maman alias: " + alias, GameCatalog.INSTANCE.isMamanAlias(alias));
            assertEquals(
                    "Expected ID_MAMAN for alias: " + alias,
                    GameCatalog.ID_MAMAN,
                    GameCatalog.INSTANCE.classifyDirectory(null, alias, null, null)
            );
        }

        assertEquals(49, GameCatalog.builtInSelectOrder().size());
        assertTrue(GameCatalog.builtInSelectOrder().contains(GameCatalog.ID_MAMAN));

        GameDescriptor desc = GameCatalog.INSTANCE.descriptorForWithResolver(
                GameCatalog.ID_MAMAN, null, r -> "res:" + r);
        assertEquals("res:" + R.string.game_maman_title, desc.getTitle());
        assertEquals("Maman", desc.getDefaultDirName());
        assertEquals(R.drawable.ic_game_maman, desc.getIconRes());
        assertTrue(desc.getSupportsOnlineBaseInstall());
        assertEquals(OnlineInstaller.MAMAN_CDN_URL, desc.getOnlineBaseCdnUrl());
        assertFalse(desc.getSupportsOnlineMod());
        assertFalse(desc.getSupportsLocalMod());
        assertEquals(AppSettings.DEFAULT_MAMAN_BYTES,
                AppSettings.defaultBaseGameSizeForUrl(OnlineInstaller.MAMAN_CDN_URL + "/"));
        assertEquals(1562216703L, AppSettings.DEFAULT_MAMAN_BYTES);

        GameSortInfo sortInfo = GameSortMetadata.INSTANCE.forGame(GameCatalog.ID_MAMAN);
        assertNotNull(sortInfo);
        assertEquals("2017-01-27", sortInfo.getReleaseDate());
        assertEquals(78.1, sortInfo.getRating(), 0.0001);
        assertEquals("https://vndb.org/v20161", sortInfo.getScoreSourceUrl());

        File snapshotFile = new File("../docs/game-sort-vndb-snapshot.json");
        if (!snapshotFile.isFile()) snapshotFile = new File("../../docs/game-sort-vndb-snapshot.json");
        String snapshotText = new String(Files.readAllBytes(snapshotFile.toPath()), StandardCharsets.UTF_8);
        Matcher rowMatcher = Pattern.compile("\\{[^}]*\"id\"\\s*:\\s*\"v20161\"[^}]*\\}").matcher(snapshotText);
        assertTrue("v20161 must exist in VNDB snapshot", rowMatcher.find());
        Matcher ratingMatcher = Pattern.compile("\"rating\"\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)").matcher(rowMatcher.group(0));
        assertTrue(ratingMatcher.find());
        assertEquals(Double.parseDouble(ratingMatcher.group(1)), sortInfo.getRating(), 0.0001);
    }

    @Test
    public void multiModGroupIsolationSwitchingAndBfyyDetection() throws Exception {
        ModReleaseEntry ribuluoPatch = new ModReleaseEntry(
                20, "v3.1修复补丁", "R10_Ribuluo_patch.zip", "", "", "2026-04-01 00:00",
                "https://alicehub.000.moe/rance10/files/mods/ribuluo-p.zip",
                true, 19, "v3.1正式版", 1000L, null, OnlineInstaller.MOD_GROUP_RIBULUO
        );
        ModReleaseEntry ribuluoBase = new ModReleaseEntry(
                19, "v3.1正式版", "R10_Ribuluo_full.zip", "", "", "2026-03-01 00:00",
                "https://alicehub.000.moe/rance10/files/mods/ribuluo-full.zip",
                false, null, null, 2000L, null, OnlineInstaller.MOD_GROUP_RIBULUO
        );
        ModReleaseEntry bfyyFull = new ModReleaseEntry(
                21, "v0.62正式版", "R10全系列MOD-V0.62.zip", "", "", "2025-09-14 01:39",
                "https://alicehub.000.moe/rance10/files/mods/bfyy-0.62-full.zip",
                false, null, null, 3686008857L, null, OnlineInstaller.MOD_GROUP_BFYY
        );

        List<ModReleaseEntry> all = Arrays.asList(ribuluoPatch, ribuluoBase, bfyyFull);
        assertEquals(
                Arrays.asList(OnlineInstaller.MOD_GROUP_RIBULUO, OnlineInstaller.MOD_GROUP_BFYY),
                OnlineInstaller.INSTANCE.distinctModGroups(all)
        );
        List<ModReleaseEntry> ribuluoOnly = OnlineInstaller.INSTANCE.releasesForGroup(all, OnlineInstaller.MOD_GROUP_RIBULUO);
        List<ModReleaseEntry> bfyyOnly = OnlineInstaller.INSTANCE.releasesForGroup(all, OnlineInstaller.MOD_GROUP_BFYY);
        assertEquals(2, ribuluoOnly.size());
        assertEquals(1, bfyyOnly.size());
        assertEquals(21, bfyyOnly.get(0).getFileId());

        assertTrue(OnlineInstaller.INSTANCE.groupsConflict(OnlineInstaller.MOD_GROUP_RIBULUO, OnlineInstaller.MOD_GROUP_BFYY));
        assertFalse(OnlineInstaller.INSTANCE.groupsConflict(OnlineInstaller.MOD_GROUP_BFYY, OnlineInstaller.MOD_GROUP_BFYY));
        assertFalse(OnlineInstaller.INSTANCE.groupsConflict(OnlineInstaller.MOD_GROUP_BFYY, ""));

        InstalledModInfo installedRibuluo = new InstalledModInfo(
                "v3.1修复补丁", "v3.1正式版", 20, 19, "R10_Ribuluo_patch.zip", "", "online", 100L,
                InstalledModInfo.KIND_MOD, OnlineInstaller.MOD_GROUP_RIBULUO
        );
        InstalledModInfo installedBfyy = new InstalledModInfo(
                "v0.62正式版", "v0.62正式版", 21, 21, "R10全系列MOD-V0.62.zip", "", "online", 200L,
                InstalledModInfo.KIND_MOD, OnlineInstaller.MOD_GROUP_BFYY
        );

        // Up-to-date within own series
        assertEquals(0, OnlineInstaller.INSTANCE.matchInstalledIndex(ribuluoOnly, installedRibuluo));
        assertNull(OnlineInstaller.INSTANCE.computeUpdatePlan(ribuluoOnly, true, installedRibuluo));
        assertEquals(0, OnlineInstaller.INSTANCE.matchInstalledIndex(bfyyOnly, installedBfyy));
        assertNull(OnlineInstaller.INSTANCE.computeUpdatePlan(bfyyOnly, true, installedBfyy));

        // Cross-series switching: BFYY (fileId=21) installed must NOT suppress Ribuluo (fileId=20) install!
        assertEquals(-1, OnlineInstaller.INSTANCE.matchInstalledIndex(ribuluoOnly, installedBfyy));
        assertFalse(OnlineInstaller.INSTANCE.hasMatchingBaseForPatch(ribuluoOnly, 0, true, installedBfyy));
        ModUpdatePlan switchToRibuluo = OnlineInstaller.INSTANCE.computeUpdatePlan(ribuluoOnly, true, installedBfyy);
        assertNotNull(switchToRibuluo);
        assertFalse(switchToRibuluo.isIncrementalOnly());
        assertEquals(Arrays.asList(ribuluoBase, ribuluoPatch), switchToRibuluo.getSteps());

        // Switching from Ribuluo to BFYY offers BFYY full package
        assertEquals(-1, OnlineInstaller.INSTANCE.matchInstalledIndex(bfyyOnly, installedRibuluo));
        ModUpdatePlan switchToBfyy = OnlineInstaller.INSTANCE.computeUpdatePlan(bfyyOnly, true, installedRibuluo);
        assertNotNull(switchToBfyy);
        assertFalse(switchToBfyy.isIncrementalOnly());
        assertEquals(Collections.singletonList(bfyyFull), switchToBfyy.getSteps());

        // Cross-series patch-only selection must be rejected; only full base+patch is allowed, and empty if no base
        assertTrue(OnlineInstaller.INSTANCE.computeStepsForSelectedRelease(
                ribuluoOnly, 0, true, installedBfyy, false, true).isEmpty());
        assertEquals(
                Arrays.asList(ribuluoBase, ribuluoPatch),
                OnlineInstaller.INSTANCE.computeStepsForSelectedRelease(
                        ribuluoOnly, 0, true, installedBfyy, true, true)
        );
        List<ModReleaseEntry> ribuluoPatchOnlyCatalog = Collections.singletonList(ribuluoPatch);
        assertTrue(OnlineInstaller.INSTANCE.computeStepsForSelectedRelease(
                ribuluoPatchOnlyCatalog, 0, true, installedBfyy, true, true).isEmpty());
        assertTrue(OnlineInstaller.INSTANCE.computeStepsForSelectedRelease(
                ribuluoPatchOnlyCatalog, 0, true, installedBfyy, false, true).isEmpty());

        // Without recoverable vanilla backups (canRestore = false), in-place series switching must be blocked
        assertNull(OnlineInstaller.INSTANCE.computeUpdatePlan(ribuluoOnly, true, installedBfyy, false));
        assertNull(OnlineInstaller.INSTANCE.computeUpdatePlan(bfyyOnly, true, installedRibuluo, false));
        assertTrue(OnlineInstaller.INSTANCE.computeStepsForSelectedRelease(
                bfyyOnly, 0, true, installedRibuluo, false, false).isEmpty());
        assertTrue(OnlineInstaller.INSTANCE.computeStepsForSelectedRelease(
                ribuluoOnly, 0, true, installedBfyy, true, false).isEmpty());
        // Same-series updates remain allowed even when canRestore = false (e.g. preinstalled Ribuluo repack)
        InstalledModInfo installedRibuluoBase = new InstalledModInfo(
                "v3.1正式版", "v3.1正式版", 19, 19, "R10_Ribuluo_full.zip", "", "preinstalled", 100L,
                InstalledModInfo.KIND_MOD, OnlineInstaller.MOD_GROUP_RIBULUO
        );
        ModUpdatePlan sameSeriesUpdate = OnlineInstaller.INSTANCE.computeUpdatePlan(
                ribuluoOnly, true, installedRibuluoBase, false);
        assertNotNull(sameSeriesUpdate);
        assertEquals(Collections.singletonList(ribuluoPatch), sameSeriesUpdate.getSteps());

        // Version key normalization & group inference for BFYY
        assertEquals("0.62", OnlineInstaller.INSTANCE.normalizeModVersionKey("R10全系列MOD-V0.62.zip"));
        assertEquals("0.62", OnlineInstaller.INSTANCE.normalizeModVersionKey("v0.62正式版"));
        assertEquals(OnlineInstaller.MOD_GROUP_BFYY, OnlineInstaller.INSTANCE.inferModGroup("R10全系列MOD-V0.62.zip", null));
        assertEquals(OnlineInstaller.MOD_GROUP_RIBULUO, OnlineInstaller.INSTANCE.inferModGroup("兰斯10日不落MOD v3.1.zip", null));

        // BFYY directory detection, full-mod clean replacement, and metadata group persistence
        File r10 = makeDir("Rance10Game", "Rance10.ain", "AliceStart.ini");
        writeText(new File(r10, "AliceStart.ini"), "GameName = \"兰斯10 决战\"\n");
        assertFalse(Item.Companion.detectInstalledMod(r10, null));

        File ribuluoStage = makeDir("RibuluoStage", "Rance10.ain", "Rance10MODCG.afa", "Rance10ModFlat.afa");
        assertTrue(GameListKt.applyModStagingToGameDirInternal(ribuluoStage, r10, true, false, false));
        assertTrue(Item.Companion.detectInstalledMod(r10, null));
        assertEquals(OnlineInstaller.MOD_GROUP_RIBULUO, OnlineInstaller.INSTANCE.inferModGroup("", r10));

        // Switching to BFYY full mod cleanly removes old Ribuluo files and installs BFYY files
        File bfyyStage = makeDir("BfyyStage", "Rance10.ain", "Rance10CGBFYY.afa", "Rance10FlatBFYY.afa", "AliceStart.ini");
        writeText(new File(bfyyStage, "AliceStart.ini"), "GameName = \"兰斯10\"\nSaveFolder = \"SaveDataBFYY\"\n");
        assertTrue(GameListKt.applyModStagingToGameDirInternal(bfyyStage, r10, true, false, false));
        assertFalse(new File(r10, "Rance10MODCG.afa").exists());
        assertTrue(new File(r10, "Rance10CGBFYY.afa").isFile());
        assertTrue(Item.Companion.detectInstalledMod(r10, null));
        assertEquals(OnlineInstaller.MOD_GROUP_BFYY, OnlineInstaller.INSTANCE.inferModGroup("", r10));

        // Restoring vanilla removes BFYY files cleanly
        GameListKt.restoreVanillaFromModDir(r10);
        assertFalse(new File(r10, "Rance10CGBFYY.afa").exists());
        assertFalse(Item.Companion.detectInstalledMod(r10, null));

        // Preinstalled Ribuluo repack without journal or .vanilla.bak must reject cross-series BFYY overlay
        File preinstalledRibuluo = makeDir(
                "PreinstalledRibuluo",
                "Rance10.ain",
                "Rance10MODCG.afa",
                "Rance10ModFlat.afa",
                "AliceStart.ini"
        );
        writeText(new File(preinstalledRibuluo, "AliceStart.ini"), "GameName = \"兰斯10日不落\"\nSaveFolder = \"ModSaveData\"\n");
        assertTrue(Item.Companion.detectInstalledMod(preinstalledRibuluo, null));
        assertFalse(Item.Companion.canRestoreVanillaDir(preinstalledRibuluo));
        File bfyyStage2 = makeDir("BfyyStage2", "Rance10.ain", "Rance10CGBFYY.afa", "Rance10FlatBFYY.afa");
        boolean rejected = false;
        try {
            GameListKt.applyModStagingToGameDirInternal(bfyyStage2, preinstalledRibuluo, true, false, false);
        } catch (Throwable t) {
            rejected = t instanceof GameList.InstallFailureException &&
                    ((GameList.InstallFailureException) t).getMsgId() == R.string.uninstall_mod_no_backup_msg;
        }
        assertTrue("Cross-series switch onto un-restorable preinstalled MOD must fail", rejected);
        assertTrue(new File(preinstalledRibuluo, "Rance10MODCG.afa").isFile());
        assertFalse(new File(preinstalledRibuluo, "Rance10CGBFYY.afa").exists());

        // Same-series fix patch on a preinstalled Ribuluo repack creates .vanilla.bak + journal,
        // but must persist .mod_non_vanilla_baseline so canRestoreVanillaDir stays false and
        // a later BFYY cross-series switch remains blocked!
        File ribuluoFixPatch = makeDir("RibuluoFixPatch", "Rance10.ain", "extra_fix.txt");
        GameListKt.applyModStagingToGameDirInternal(ribuluoFixPatch, preinstalledRibuluo, false, false, false);
        assertTrue(new File(preinstalledRibuluo, "Rance10.ain.vanilla.bak").isFile());
        assertTrue(new File(preinstalledRibuluo, ".mod_installed_files.txt").isFile());
        assertTrue(new File(preinstalledRibuluo, Item.NON_VANILLA_BASELINE_MARKER).isFile());
        assertFalse(Item.Companion.canRestoreVanillaDir(preinstalledRibuluo));
        assertNull(OnlineInstaller.INSTANCE.computeUpdatePlan(
                bfyyOnly, true, installedRibuluo, Item.Companion.canRestoreVanillaDir(preinstalledRibuluo)));
        boolean rejectedAfterPatch = false;
        try {
            GameListKt.applyModStagingToGameDirInternal(bfyyStage2, preinstalledRibuluo, true, false, false);
        } catch (Throwable t) {
            rejectedAfterPatch = t instanceof GameList.InstallFailureException &&
                    ((GameList.InstallFailureException) t).getMsgId() == R.string.uninstall_mod_no_backup_msg;
        }
        assertTrue("Cross-series switch after same-series patch on repack must still fail", rejectedAfterPatch);
        assertTrue(new File(preinstalledRibuluo, "Rance10MODCG.afa").isFile());
        assertFalse(new File(preinstalledRibuluo, "Rance10CGBFYY.afa").exists());
    }
}

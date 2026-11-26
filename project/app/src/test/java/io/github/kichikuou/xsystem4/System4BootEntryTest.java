package io.github.kichikuou.xsystem4;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.view.View;
import android.widget.TextView;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowEnvironment;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class System4BootEntryTest {
    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    private static void writeUtf8(File file, String content) throws Exception {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            assertTrue(parent.mkdirs());
        }
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void detectsAlternateIniPersistsSelectionAndFallsBackWhenRemoved() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        File installed = tmp.newFolder("SengokuRance");
        File otherInstall = tmp.newFolder("OtherInstall");

        String original = "System40.ini";
        String patch = "ChineseLauncher.ini";
        writeUtf8(new File(installed, original), "GameName = \"Sengoku Rance\"\nCodeName = \"Original.ain\"\n");
        writeUtf8(new File(installed, patch), "GameName = \"Translated edition\"\n\tCodeName = \"ALICECN.AIN\"\n");
        writeUtf8(new File(installed, "ChineseLauncher.exe"), "");
        writeUtf8(new File(installed, "Config.ini"), "UseJoypad = true\n");
        writeUtf8(new File(installed, "Missing.ini"), "CodeName = \"Missing.ain\"\n");
        writeUtf8(new File(installed, "Original.ain"), "");
        writeUtf8(new File(installed, "AliceCN.ain"), "");

        assertEquals(Arrays.asList(patch, original), System4BootEntry.listEntries(installed));
        assertEquals(installed.getPath(), XSystem4Activity.resolveSystem4LaunchArg(installed.getPath(), ""));

        System4BootEntry.setSelectedEntry(context, installed, patch);
        assertEquals(patch, System4BootEntry.getSelectedEntry(context, installed));
        assertEquals("", System4BootEntry.getSelectedEntry(context, otherInstall));
        assertEquals(
                new File(installed, patch).getPath(),
                XSystem4Activity.resolveSystem4LaunchArg(installed.getPath(), System4BootEntry.getSelectedEntry(context, installed))
        );

        assertTrue(new File(installed, patch).delete());
        assertEquals(Arrays.asList(original, "AliceCN.ain"), System4BootEntry.listEntries(installed));
        assertEquals(
                installed.getPath(),
                XSystem4Activity.resolveSystem4LaunchArg(installed.getPath(), patch)
        );
        assertEquals(
                new File(installed, "AliceCN.ain").getPath(),
                XSystem4Activity.resolveSystem4LaunchArg(installed.getPath(), "AliceCN.ain")
        );
        assertEquals(
                installed.getPath(),
                XSystem4Activity.resolveSystem4LaunchArg(installed.getPath(), "ChineseLauncher.exe")
        );

        System4BootEntry.clearSelectedEntry(context, installed);
        assertEquals("", System4BootEntry.getSelectedEntry(context, installed));
    }

    @Test
    public void alternateIniOnlyFolderIsRecognizedAndLaunchesDirectly() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        File dir = tmp.newFolder("CustomLauncherOnly");
        byte[] patchBytes = "GameName = \"战国兰斯中文版\"\nCodeName = \"ALICECN.AIN\"\n".getBytes(StandardCharsets.UTF_8);
        Files.write(new File(dir, "ChineseLauncher.ini").toPath(), patchBytes);
        Files.write(new File(dir, "AliceCN.ain").toPath(), new byte[]{1});

        assertTrue(GameListKt.isSystem4GameRoot(dir));
        Item item = Item.Companion.fromDirectory(dir, tmp.getRoot(), context);
        assertNull(item.getError());
        assertNotNull(item.getSavedir());
        assertEquals(
                new File(dir, "ChineseLauncher.ini").getPath(),
                System4BootEntry.resolveLaunchTarget(dir, "").getPath()
        );

        List<NativeGameImport.TreeEntry<String, byte[]>> rootEntries = Arrays.asList(
                new NativeGameImport.TreeEntry<>("ini", "ChineseLauncher.ini", false, patchBytes),
                new NativeGameImport.TreeEntry<>("ain", "AliceCN.ain", false, new byte[]{1})
        );
        assertNotNull(NativeGameImport.INSTANCE.discoverImportSources(
                "root", rootEntries, id -> Collections.emptyList(), NativeGameImport.TreeEntry::getPayload
        ).getPrimaryEntries());
    }

    @Test
    public void supportsConfiguredChsWithoutTableNestedCodeNameAndRejectsTraversalOrSymlinks() throws Exception {
        File outer = tmp.newFolder("BilingualContainer");
        File bilingual = new File(outer, "Bilingual");
        assertTrue(bilingual.mkdirs());
        writeUtf8(new File(bilingual, "AliceStart.ini"), "CodeName = \"Original.ain\"\n");
        writeUtf8(new File(bilingual, "AliceStart.CHS"), "CodeName = \"Oya.CHS\"\n");
        writeUtf8(new File(bilingual, "Nested.ini"), "CodeName = \".\\dAtA\\PATCH.AIN\"\n");
        writeUtf8(new File(bilingual, "Original.ain"), "");
        writeUtf8(new File(bilingual, "Oya.CHS"), "");
        writeUtf8(new File(bilingual, "Unconfigured.CHS"), "");
        writeUtf8(new File(bilingual, "Data/Patch.ain"), "");

        writeUtf8(new File(bilingual, "Outside.ini"), "CodeName = \"../Outside.ain\"\n");
        writeUtf8(new File(outer, "Outside.ain"), "");
        Files.createSymbolicLink(new File(bilingual, "Alias").toPath(), outer.toPath());
        writeUtf8(new File(bilingual, "Alias.ini"), "CodeName = \"Alias/Outside.ain\"\n");

        List<String> expectedConfigured = Arrays.asList("AliceStart.CHS", "AliceStart.ini", "Nested.ini");
        assertEquals(expectedConfigured, System4BootEntry.listEntries(bilingual));
        assertEquals(
                new File(bilingual, "AliceStart.CHS").getPath(),
                XSystem4Activity.resolveSystem4LaunchArg(bilingual.getPath(), "AliceStart.CHS")
        );
        assertEquals(
                new File(bilingual, "Nested.ini").getPath(),
                XSystem4Activity.resolveSystem4LaunchArg(bilingual.getPath(), "Nested.ini")
        );

        writeUtf8(new File(bilingual, "CHS.TBL"), "");
        assertEquals(
                Arrays.asList("AliceStart.CHS", "AliceStart.ini", "Nested.ini", "Unconfigured.CHS"),
                System4BootEntry.listEntries(bilingual)
        );

        // Verify nested CodeName-only SAF tree is discovered as a System 4 root.
        byte[] nestedIni = "CodeName = \".\\Data\\Patch.ain\"\n".getBytes(StandardCharsets.UTF_8);
        List<NativeGameImport.TreeEntry<String, byte[]>> treeRoot = Arrays.asList(
                new NativeGameImport.TreeEntry<>("ini", "Nested.ini", false, nestedIni),
                new NativeGameImport.TreeEntry<>("data", "Data", true, null)
        );
        List<NativeGameImport.TreeEntry<String, byte[]>> dataChildren = Collections.singletonList(
                new NativeGameImport.TreeEntry<>("ain", "Patch.ain", false, new byte[]{1})
        );
        assertNotNull(NativeGameImport.INSTANCE.discoverImportSources(
                "root",
                treeRoot,
                id -> "data".equals(id) ? dataChildren : Collections.emptyList(),
                NativeGameImport.TreeEntry::getPayload
        ).getPrimaryEntries());
    }

    @Test
    public void daiSengokuSaveDirectoryFollowsSelectedBootEntryAndLaunchIntent() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        File srDir = tmp.newFolder("SengokuRanceDSR");
        writeUtf8(
                new File(srDir, "System40.ini"),
                "GameName = \"战国兰斯 大战国\"\nCodeName = \"DaiSengoku.ain\"\nSaveFolder = \"SaveDataDSR\"\n"
        );
        writeUtf8(new File(srDir, "DaiSengoku.ain"), "");
        writeUtf8(new File(srDir, "AliceCN.ain"), "");
        writeUtf8(new File(srDir, "Unknown.ain"), "");

        assertEquals(Arrays.asList("System40.ini", "AliceCN.ain"), System4BootEntry.listEntries(srDir));

        File vanillaSaveDir = new File(context.getFilesDir(), "saves/" + srDir.getName());
        File dsrSaveDir = new File(context.getFilesDir(), "saves/" + srDir.getName() + Item.DAISENGOKU_SAVE_DIR_SUFFIX);

        System4BootEntry.setSelectedEntry(context, srDir, "System40.ini");
        assertEquals(dsrSaveDir, Item.Companion.resolveSaveDir(context, srDir));
        assertEquals(dsrSaveDir, Item.Companion.fromDirectory(srDir, tmp.getRoot(), context).getSavedir());

        System4BootEntry.setSelectedEntry(context, srDir, "AliceCN.ain");
        assertEquals(vanillaSaveDir, Item.Companion.resolveSaveDir(context, srDir));
        Item vanillaItem = Item.Companion.fromDirectory(srDir, tmp.getRoot(), context);
        assertEquals(vanillaSaveDir, vanillaItem.getSavedir());

        // Verify GameCatalog.launchGameItem passes selected entry and resolved saveDir to XSystem4Activity.
        GameHubActivity activity = Robolectric.buildActivity(GameHubActivity.class).create().get();
        GameCatalog.INSTANCE.launchGameItem(activity, vanillaItem);
        Intent launched = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(launched);
        assertEquals(srDir.getPath(), launched.getStringExtra(XSystem4Activity.EXTRA_GAME_ROOT));
        assertEquals(vanillaSaveDir.getPath(), launched.getStringExtra(XSystem4Activity.EXTRA_SAVE_DIR));
        assertEquals("AliceCN.ain", launched.getStringExtra(XSystem4Activity.EXTRA_BOOT_ENTRY));

        System4BootEntry.setSelectedEntry(context, srDir, "Unknown.ain");
        assertEquals(dsrSaveDir, Item.Companion.resolveSaveDir(context, srDir));

        System4BootEntry.clearSelectedEntry(context, srDir);
    }

    @Test
    public void utf8BomBeforeSaveFolderIsParsedAndSaveManagerRespectsTargetEdition() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        File srDir = tmp.newFolder("BomSengokuRance");
        File bomIni = new File(srDir, "System40.ini");
        writeUtf8(bomIni, "\uFEFFSaveFolder = \"SaveDataDSR\"\nCodeName = \"DaiSengoku.ain\"\n");
        writeUtf8(new File(srDir, "DaiSengoku.ain"), "");
        writeUtf8(new File(srDir, "AliceCN.ain"), "");

        assertEquals(Boolean.TRUE, System4BootEntry.iniUsesDaiSengokuSaveFolder(bomIni));

        // Backup containing both vanilla SaveData and DaiSengoku SaveDataDSR.
        File backupRoot = tmp.newFolder("DualSaveBackup");
        writeUtf8(new File(backupRoot, "SaveData/Save001.asd"), "vanilla");
        writeUtf8(new File(backupRoot, "SaveDataDSR/Save001.asd"), "dsr");

        // Vanilla selected in Hub, but user picks DaiSengoku save destination in SaveManager.
        System4BootEntry.setSelectedEntry(context, srDir, "AliceCN.ain");
        File dsrSaveDir = new File(context.getFilesDir(), "saves/" + srDir.getName() + Item.DAISENGOKU_SAVE_DIR_SUFFIX);
        SaveTarget dsrTarget = new SaveTarget(
                dsrSaveDir.getName(), dsrSaveDir.getName(), dsrSaveDir, srDir
        );
        SaveImportPaths dsrPaths = SaveManagerActivity.Companion.saveImportPathsForTarget(context, dsrTarget);
        File dsrPicked = dsrPaths.sourceRoot(
                backupRoot,
                f -> f.listFiles() != null ? Arrays.asList(f.listFiles()) : Collections.emptyList(),
                File::getName,
                File::isDirectory
        );
        assertEquals("SaveDataDSR", dsrPicked.getName());

        // DaiSengoku selected in Hub, but user picks vanilla save destination in SaveManager.
        System4BootEntry.setSelectedEntry(context, srDir, "System40.ini");
        File vanillaSaveDir = new File(context.getFilesDir(), "saves/" + srDir.getName());
        SaveTarget vanillaTarget = new SaveTarget(
                vanillaSaveDir.getName(), vanillaSaveDir.getName(), vanillaSaveDir, srDir
        );
        SaveImportPaths vanillaPaths = SaveManagerActivity.Companion.saveImportPathsForTarget(context, vanillaTarget);
        File vanillaPicked = vanillaPaths.sourceRoot(
                backupRoot,
                f -> f.listFiles() != null ? Arrays.asList(f.listFiles()) : Collections.emptyList(),
                File::getName,
                File::isDirectory
        );
        assertEquals("SaveData", vanillaPicked.getName());

        System4BootEntry.clearSelectedEntry(context, srDir);
    }

    @Test
    public void nestedCodeNamePassesImportedGameDirValidation() throws Exception {
        File nestedDir = tmp.newFolder("NestedImportedDir");
        writeUtf8(new File(nestedDir, "Nested.ini"), "CodeName = \".\\Data\\Expected.ain\"\n");
        writeUtf8(new File(nestedDir, "Data/Expected.ain"), "x");

        assertTrue(GameCatalog.INSTANCE.validateImportedGameDir(nestedDir, "Expected.ain", null));
        assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(nestedDir, "Other.ain", null));
    }

    @Test
    public void gameHubShowsBootEntryPickerAndPersistsSelection() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        File extRoot = context.getExternalFilesDir(null);
        File storageRoot = (extRoot != null && Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState(extRoot)))
                ? extRoot
                : new File(context.getFilesDir(), "games");
        File gameDir = new File(storageRoot, "BootPickerTestGame");
        assertTrue(gameDir.exists() || gameDir.mkdirs());
        try {
            writeUtf8(new File(gameDir, "System40.ini"), "GameName = \"Picker Test\"\nCodeName = \"Original.ain\"\n");
            writeUtf8(new File(gameDir, "ChineseLauncher.ini"), "GameName = \"Picker Test CN\"\nCodeName = \"AliceCN.ain\"\n");
            writeUtf8(new File(gameDir, "Original.ain"), "");
            writeUtf8(new File(gameDir, "AliceCN.ain"), "");
            System4BootEntry.clearSelectedEntry(context, gameDir);

            // Force GameList refresh so GameHubActivity sees the new installation.
            GameHubActivity seedActivity = Robolectric.buildActivity(GameHubActivity.class).create().get();
            GameList refreshed = LauncherActivity.Companion.obtainGameList(seedActivity, true);
            assertTrue("Expected GameList to contain BootPickerTestGame (size=" + refreshed.getSize()
                    + ", storageRoot=" + storageRoot + ")", refreshed.getSize() > 0);
            String gameId = GameCatalog.INSTANCE.classifyItem(refreshed.get(0));

            Intent intent = new Intent(context, GameHubActivity.class);
            intent.putExtra(GameHubActivity.EXTRA_GAME_ID, gameId);
            intent.putExtra(GameHubActivity.EXTRA_GAME_PATH, gameDir.getPath());
            GameHubActivity activity = Robolectric.buildActivity(GameHubActivity.class, intent).create().resume().get();

            View bootRow = activity.findViewById(R.id.hub_boot_entry_row);
            TextView bootValue = activity.findViewById(R.id.hub_boot_entry_value);
            assertEquals(View.VISIBLE, bootRow.getVisibility());
            assertEquals(activity.getString(R.string.hub_boot_entry_default), bootValue.getText().toString());

            activity.findViewById(R.id.btn_hub_switch_boot_entry).performClick();
            AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
            assertNotNull(dialog);
            // Index 0 = Default launch entry, Index 1 = ChineseLauncher.ini, Index 2 = System40.ini
            Shadows.shadowOf(dialog).clickOnItem(1);

            assertEquals("ChineseLauncher.ini", System4BootEntry.getSelectedEntry(context, gameDir));
            assertEquals("ChineseLauncher.ini", bootValue.getText().toString());
            assertFalse(dialog.isShowing());
        } finally {
            System4BootEntry.clearSelectedEntry(context, gameDir);
            deleteRecursively(gameDir);
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File c : children) {
                deleteRecursively(c);
            }
        }
        file.delete();
    }
}

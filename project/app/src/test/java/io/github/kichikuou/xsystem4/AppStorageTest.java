package io.github.kichikuou.xsystem4;

import android.app.Activity;
import android.app.AppOpsManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowEnvironment;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import kotlin.Unit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class AppStorageTest {
    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    private static void write(File file, String value) throws IOException {
        Files.createDirectories(file.getParentFile().toPath());
        Files.write(file.toPath(), value.getBytes(StandardCharsets.UTF_8));
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static void setStorageAccess(Context context, int mode) {
        Shadows.shadowOf(context.getSystemService(AppOpsManager.class)).setMode(
                "android:manage_external_storage",
                context.getApplicationInfo().uid, context.getPackageName(), mode);
    }

    @Test
    public void migrationReportsCurrentItemAndIntermediatePercent() throws IOException {
        Context context = RuntimeEnvironment.getApplication();
        File volume = tmp.newFolder();
        ShadowEnvironment.setExternalStorageDirectory(volume.toPath());
        ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED);
        setStorageAccess(context, AppOpsManager.MODE_ALLOWED);
        AppSettings.INSTANCE.setPublicStorage(context, true);
        File original = new File(context.getFilesDir(), "saves/LegacyGame/slot.asd");
        File shared = new File(volume, "AliceHub/saves/LegacyGame/slot.asd");
        byte[] data = new byte[128 * 1024];
        Files.createDirectories(original.getParentFile().toPath());
        Files.createDirectories(shared.getParentFile().toPath());
        Files.write(original.toPath(), data);
        Files.write(shared.toPath(), data);
        List<Integer> percentages = new ArrayList<>();

        assertTrue(AppStorage.INSTANCE.preparePublicStorage(context, status -> {
            assertEquals("LegacyGame", status.getItemName());
            percentages.add(status.getPercent());
            return Unit.INSTANCE;
        }));
        assertEquals(Integer.valueOf(0), percentages.get(0));
        assertTrue(percentages.stream().anyMatch(percent -> percent > 0 && percent < 100));
        assertEquals(Integer.valueOf(100), percentages.get(percentages.size() - 1));
        assertFalse(original.exists());
        assertTrue(shared.isFile());
    }

    @Test
    public void permissionGatesMigrationAndNewInstalls() throws IOException {
        Context context = RuntimeEnvironment.getApplication();
        File volume = tmp.newFolder();
        ShadowEnvironment.setExternalStorageDirectory(volume.toPath());
        ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED);
        File legacyGame = new File(context.getExternalFilesDir(null), "Game");
        ShadowEnvironment.setExternalStorageState(legacyGame.getParentFile(), Environment.MEDIA_MOUNTED);
        File legacySave = new File(context.getFilesDir(), "saves/Game");
        File legacyBackup = new File(context.getFilesDir(), "save_backups/Game");
        write(new File(legacyGame, "Game.ain"), "game");
        write(new File(legacySave, "SaveData/save.asd"), "save");
        write(new File(legacyBackup, "backup.zip"), "backup");
        System4BootEntry.setSelectedEntry(context, legacyGame, "System40.ini");

        AppStorage storage = AppStorage.INSTANCE;
        setStorageAccess(context, AppOpsManager.MODE_ALLOWED);
        assertFalse(AppSettings.INSTANCE.isOnboardingComplete(context));
        assertFalse(storage.needsPublicStoragePreparation(context));
        assertTrue(storage.preparePublicStorage(context));
        assertEquals(legacyGame.getPath(), storage.resolveGamePath(context, legacyGame.getPath()));
        assertEquals("save", read(new File(storage.saveDir(context, "Game"), "SaveData/save.asd")));
        assertEquals("backup", read(new File(storage.backupDir(context, "Game"), "backup.zip")));
        assertEquals("System40.ini", System4BootEntry.getSelectedEntry(context, legacyGame));
        assertFalse(new File(volume, "AliceHub").exists());

        AppSettings.INSTANCE.setPublicStorage(context, true);
        setStorageAccess(context, AppOpsManager.MODE_IGNORED);
        assertEquals(legacyGame.getParentFile(), storage.gameRoots(context).get(0));
        assertEquals(legacySave, storage.saveDir(context, "Game"));
        assertTrue(storage.preparePublicStorage(context));
        assertTrue(legacyGame.isDirectory());
        assertFalse(new File(volume, "AliceHub").exists());

        setStorageAccess(context, AppOpsManager.MODE_ALLOWED);
        assertTrue(storage.needsPublicStoragePreparation(context));
        assertTrue(storage.preparePublicStorage(context));
        assertFalse(storage.needsPublicStoragePreparation(context));
        File publicGames = new File(volume, "AliceHub/games");
        File publicGame = new File(publicGames, "Game");
        File publicSave = new File(volume, "AliceHub/saves/Game");
        assertEquals(publicGames, storage.gameRoots(context).get(0));
        assertFalse(publicGame.exists());
        assertEquals("game", read(new File(legacyGame, "Game.ain")));
        assertEquals(publicSave, storage.saveDir(context, "Game"));
        assertEquals("save", read(new File(publicSave, "SaveData/save.asd")));
        assertEquals("backup", read(new File(storage.backupDir(context, "Game"), "backup.zip")));
        assertTrue(legacyGame.isDirectory());
        assertFalse(legacySave.exists());
        assertFalse(legacyBackup.exists());
        assertEquals(legacyGame.getPath(), storage.resolveGamePath(context, legacyGame.getPath()));
        assertEquals(new File(publicSave, "SaveData/save.asd").getPath(),
                storage.resolveSavePath(context, new File(legacySave, "SaveData/save.asd").getPath()));
        assertEquals("System40.ini", System4BootEntry.getSelectedEntry(context, legacyGame));
        assertTrue(storage.preparePublicStorage(context));
        assertTrue(new File(volume, "AliceHub/save_backups").renameTo(new File(volume, "backup-on-hold")));
        assertTrue(storage.needsPublicStoragePreparation(context));
        assertTrue(storage.preparePublicStorage(context));
        assertFalse(storage.needsPublicStoragePreparation(context));

        setStorageAccess(context, AppOpsManager.MODE_IGNORED);
        assertEquals(legacyGame.getParentFile(), storage.gameRoots(context).get(0));
        assertEquals(legacySave, storage.saveDir(context, "Game"));
        assertEquals(legacyGame.getPath(), storage.resolveGamePath(context, legacyGame.getPath()));
        assertTrue(publicSave.isDirectory());
        setStorageAccess(context, AppOpsManager.MODE_ALLOWED);
        assertFalse(storage.needsPublicStoragePreparation(context));
        AppSettings.INSTANCE.setPublicStorage(context, false);
        assertFalse(storage.needsPublicStoragePreparation(context));
        assertEquals(legacyGame.getParentFile(), storage.gameRoots(context).get(0));
        assertEquals(legacySave, storage.saveDir(context, "Game"));
    }

    @Test
    public void gameLibraryPrefersLegacyCopyAndStillListsNewSharedGames() throws IOException {
        Context context = RuntimeEnvironment.getApplication();
        File volume = tmp.newFolder();
        ShadowEnvironment.setExternalStorageDirectory(volume.toPath());
        ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED);
        setStorageAccess(context, AppOpsManager.MODE_ALLOWED);
        AppSettings.INSTANCE.setPublicStorage(context, true);
        AppStorage storage = AppStorage.INSTANCE;
        assertTrue(storage.preparePublicStorage(context));
        File publicGames = new File(volume, "AliceHub/games");
        File legacyGame = new File(context.getFilesDir(), "games/Rance");
        File duplicate = new File(publicGames, "rance");
        File newGame = new File(publicGames, "Other");
        for (File directory : new File[] {legacyGame, duplicate, newGame}) {
            write(new File(directory, "System40.ini"), "GameName=Test");
        }
        assertEquals(publicGames, storage.gameRoots(context).get(0));
        assertTrue(storage.privateGameNamesForInstall(context, publicGames).contains("rance"));
        try (org.robolectric.android.controller.ActivityController<Activity> controller =
                     Robolectric.buildActivity(Activity.class).setup()) {
            GameList games = new GameList(controller.get());
            try {
                List<File> listed = new ArrayList<>();
                for (int i = 0; i < games.getSize(); i++) listed.add(games.get(i).getPath());
                assertTrue(listed.contains(legacyGame));
                assertTrue(listed.contains(newGame));
                assertFalse(listed.contains(duplicate));
            } finally {
                games.close();
            }
        }
    }

    @Test
    public void interruptedStagingAndEmptyPrivateSaveDoNotHideSharedData() throws IOException {
        Context context = RuntimeEnvironment.getApplication();
        File volume = tmp.newFolder();
        ShadowEnvironment.setExternalStorageDirectory(volume.toPath());
        ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED);
        setStorageAccess(context, AppOpsManager.MODE_ALLOWED);
        AppSettings.INSTANCE.setPublicStorage(context, true);
        AppStorage storage = AppStorage.INSTANCE;
        File legacySave = new File(context.getFilesDir(), "saves/Game");
        assertTrue(legacySave.mkdirs());
        File sharedSave = new File(volume, "AliceHub/saves/Game");
        write(new File(sharedSave, "slot.asd"), "shared");
        File staging = new File(volume, "AliceHub/games/.alicehub-migration-abandoned");
        write(new File(staging, "data/partial.ain"), "partial");
        context.getSharedPreferences("public_storage_paths", Context.MODE_PRIVATE).edit()
                .putString("active_staging", staging.getAbsolutePath()).commit();
        File unrelated = new File(volume, "AliceHub/games/.alicehub-migration-user-copy/data/backup.ain");
        write(unrelated, "keep");

        assertEquals(sharedSave, storage.saveDir(context, "Game"));
        assertTrue(storage.preparePublicStorage(context));
        assertFalse(staging.exists());
        assertEquals("keep", read(unrelated));
        assertFalse(legacySave.exists());
        assertEquals("shared", read(new File(storage.saveDir(context, "Game"), "slot.asd")));
    }

    @Test
    public void sharedSaveDocumentCheckUsesProviderVolumeAndPathBoundaries() throws IOException {
        ShadowEnvironment.setExternalStorageDirectory(tmp.newFolder().toPath());
        String provider = "content://com.android.externalstorage.documents/";
        AppStorage storage = AppStorage.INSTANCE;
        assertTrue(storage.isSharedSaveDocument(Uri.parse(provider + "tree/primary%3AAliceHub%2Fsaves"), true));
        assertTrue(storage.isSharedSaveDocument(Uri.parse(provider +
                "tree/primary%3AAliceHub/document/primary%3AAliceHub%2Fsaves%2FGame%2Fslot.asd"), false));
        assertFalse(storage.isSharedSaveDocument(Uri.parse(provider + "tree/primary%3AAliceHub%2Fsaves-copy"), true));
        assertFalse(storage.isSharedSaveDocument(Uri.parse(provider + "tree/AB12-3456%3AAliceHub%2Fsaves"), true));
    }

    @Test
    public void conflictLeavesBothVersionsAndCanBeRetried() throws IOException {
        File legacy = new File(tmp.newFolder(), "Game");
        File shared = new File(tmp.newFolder(), "Game");
        write(new File(legacy, "slot.asd"), "old");
        write(new File(shared, "slot.asd"), "new");

        assertFalse(AppStorage.migrateEntry(legacy, shared));
        assertEquals("old", read(new File(legacy, "slot.asd")));
        assertEquals("new", read(new File(shared, "slot.asd")));

        write(new File(shared, "slot.asd"), "old");
        assertTrue(AppStorage.migrateEntry(legacy, shared));
        assertFalse(legacy.exists());
        assertTrue(AppStorage.migrateEntry(legacy, shared));
        assertEquals("old", read(new File(shared, "slot.asd")));
    }

    @Test
    public void retainedConflictDoesNotTriggerStartupAgainAndDoesNotBlockNewBackups() throws IOException {
        Context context = RuntimeEnvironment.getApplication();
        File volume = enableSharedStorage(context);
        File privateSave = new File(context.getFilesDir(), "saves/Game/SaveData/slot.asd");
        File sharedSave = new File(volume, "AliceHub/saves/Game/SaveData/slot.asd");
        write(privateSave, "private version");
        write(sharedSave, "shared version");
        AppStorage storage = AppStorage.INSTANCE;

        assertTrue(storage.needsPublicStoragePreparation(context));
        assertFalse(storage.preparePublicStorage(context));
        assertFalse(storage.needsPublicStoragePreparation(context));
        assertTrue(storage.needsPublicStoragePreparation(context, true));
        assertEquals("private version", read(privateSave));
        assertEquals("shared version", read(sharedSave));

        File privateBackup = new File(context.getFilesDir(), "save_backups/NewGame/backup.zip");
        write(privateBackup, "backup");
        assertTrue(storage.needsPublicStoragePreparation(context));
        List<String> attempted = new ArrayList<>();
        assertFalse(storage.preparePublicStorage(context, status -> {
            attempted.add(status.getItemName());
            return Unit.INSTANCE;
        }));
        assertTrue(attempted.contains("NewGame"));
        assertFalse(attempted.contains("Game"));
        assertEquals("backup", read(new File(volume, "AliceHub/save_backups/NewGame/backup.zip")));
        assertFalse(storage.needsPublicStoragePreparation(context));

        File additionalSlot = new File(privateSave.getParentFile(), "new-slot.asd");
        write(additionalSlot, "new save");
        assertTrue(storage.needsPublicStoragePreparation(context));
        assertFalse(storage.preparePublicStorage(context));
        assertFalse(storage.needsPublicStoragePreparation(context));

        assertTrue(sharedSave.delete());
        assertFalse(storage.needsPublicStoragePreparation(context));
        write(sharedSave, "private version");
        write(new File(sharedSave.getParentFile(), additionalSlot.getName()), "new save");
        assertTrue(storage.preparePublicStorage(context, true, progress -> Unit.INSTANCE));
        assertFalse(privateSave.exists());
        assertFalse(storage.needsPublicStoragePreparation(context, true));
    }

    @Test
    public void emptySharedPlaceholderAcceptsLegacySaveAndNewPrivatePlaceholderDoesNotTriggerMigration() throws IOException {
        Context context = RuntimeEnvironment.getApplication();
        File volume = enableSharedStorage(context);
        File privateSave = new File(context.getFilesDir(), "saves/Game/slot.asd");
        File sharedDir = new File(volume, "AliceHub/saves/Game");
        write(privateSave, "save");
        assertTrue(sharedDir.mkdirs());
        AppStorage storage = AppStorage.INSTANCE;

        assertTrue(storage.preparePublicStorage(context));
        assertEquals("save", read(new File(sharedDir, "slot.asd")));
        assertFalse(privateSave.exists());
        assertTrue(new File(context.getFilesDir(), "saves/Placeholder").mkdirs());
        assertFalse(storage.needsPublicStoragePreparation(context));
        assertEquals(new File(volume, "AliceHub/saves/Placeholder"), storage.saveDir(context, "Placeholder"));
    }

    @Test
    public void blockedSharedSaveAndBackupPathsFallBackToPrivateStorage() throws IOException {
        Context context = RuntimeEnvironment.getApplication();
        File volume = enableSharedStorage(context);
        File privateSaveDir = new File(context.getFilesDir(), "saves/Game");
        assertTrue(privateSaveDir.mkdirs());
        File sharedSave = new File(volume, "AliceHub/saves/Game");
        File sharedBackup = new File(volume, "AliceHub/save_backups/Game");
        write(sharedSave, "occupied");
        write(sharedBackup, "occupied");
        AppStorage storage = AppStorage.INSTANCE;

        assertFalse(storage.preparePublicStorage(context));
        assertEquals(privateSaveDir, storage.saveDir(context, "Game"));
        assertEquals(new File(context.getFilesDir(), "save_backups/Game"), storage.backupDir(context, "Game"));
        assertEquals("occupied", read(sharedSave));
        assertEquals("occupied", read(sharedBackup));
    }

    @Test
    public void incompleteWarningStillShowsWhenItsPreferenceCannotBeWritten() throws IOException {
        Context context = RuntimeEnvironment.getApplication().createDeviceProtectedStorageContext();
        context.getSharedPreferences("public_storage_paths", Context.MODE_PRIVATE);
        File prefsDir = new File(context.getDataDir(), "shared_prefs");
        assertTrue(prefsDir.delete());
        write(prefsDir, "occupied");

        assertTrue(AppStorage.INSTANCE.consumePublicStorageFailureNotice(context));
        assertFalse(AppStorage.INSTANCE.consumePublicStorageFailureNotice(context));
    }

    @Test
    public void blockedLayoutIsNotRetriedUntilItChangesAndWarningCanOnlyBeConsumedOnce() throws IOException {
        Context context = RuntimeEnvironment.getApplication();
        File volume = enableSharedStorage(context);
        File blocked = new File(volume, "AliceHub/saves");
        write(blocked, "path occupied");
        File privateSave = new File(context.getFilesDir(), "saves/Game/slot.asd");
        write(privateSave, "save");
        AppStorage storage = AppStorage.INSTANCE;

        assertFalse(storage.preparePublicStorage(context));
        assertFalse(storage.needsPublicStoragePreparation(context));
        assertTrue(storage.consumePublicStorageFailureNotice(context));
        AppSettings.INSTANCE.setPublicStorage(context, false);
        AppSettings.INSTANCE.setPublicStorage(context, true);
        assertFalse(storage.consumePublicStorageFailureNotice(context));
        assertFalse(storage.needsPublicStoragePreparation(context));

        assertTrue(blocked.delete());
        assertTrue(storage.needsPublicStoragePreparation(context));
        assertTrue(storage.preparePublicStorage(context));
        assertEquals("save", read(new File(blocked, "Game/slot.asd")));
    }

    private File enableSharedStorage(Context context) throws IOException {
        File volume = tmp.newFolder();
        ShadowEnvironment.setExternalStorageDirectory(volume.toPath());
        ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED);
        setStorageAccess(context, AppOpsManager.MODE_ALLOWED);
        AppSettings.INSTANCE.setPublicStorage(context, true);
        return volume;
    }

    @Test
    public void unwritableDestinationDoesNotRetireSource() throws IOException {
        File legacy = new File(tmp.newFolder(), "Game");
        write(new File(legacy, "slot.asd"), "save");
        File blocked = tmp.newFile();
        File shared = new File(blocked, "Game");

        assertThrows(IOException.class, () -> AppStorage.migrateEntry(legacy, shared));
        assertEquals("save", read(new File(legacy, "slot.asd")));
    }
}

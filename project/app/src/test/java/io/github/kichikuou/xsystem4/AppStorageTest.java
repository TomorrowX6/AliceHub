package io.github.kichikuou.xsystem4;

import android.app.AppOpsManager;
import android.content.Context;
import android.os.Environment;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowEnvironment;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

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

        setStorageAccess(context, AppOpsManager.MODE_IGNORED);
        AppStorage storage = AppStorage.INSTANCE;
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
        assertEquals("game", read(new File(publicGame, "Game.ain")));
        assertEquals(publicSave, storage.saveDir(context, "Game"));
        assertEquals("save", read(new File(publicSave, "SaveData/save.asd")));
        assertEquals("backup", read(new File(storage.backupDir(context, "Game"), "backup.zip")));
        assertFalse(legacyGame.exists());
        assertFalse(legacySave.exists());
        assertFalse(legacyBackup.exists());
        assertEquals(publicGame.getPath(), storage.resolveGamePath(context, legacyGame.getPath()));
        assertEquals(new File(publicSave, "SaveData/save.asd").getPath(),
                storage.resolveSavePath(context, new File(legacySave, "SaveData/save.asd").getPath()));
        assertEquals("System40.ini", System4BootEntry.getSelectedEntry(context, publicGame));
        assertTrue(storage.preparePublicStorage(context));

        setStorageAccess(context, AppOpsManager.MODE_IGNORED);
        assertEquals(legacyGame.getParentFile(), storage.gameRoots(context).get(0));
        assertEquals(legacySave, storage.saveDir(context, "Game"));
        assertEquals(legacyGame.getPath(), storage.resolveGamePath(context, legacyGame.getPath()));
        assertTrue(publicSave.isDirectory());
        storage.resetPublicStoragePreparation(context);
        setStorageAccess(context, AppOpsManager.MODE_ALLOWED);
        assertTrue(storage.needsPublicStoragePreparation(context));
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
    public void unwritableDestinationDoesNotRetireSource() throws IOException {
        File legacy = new File(tmp.newFolder(), "Game");
        write(new File(legacy, "slot.asd"), "save");
        File blocked = tmp.newFile();
        File shared = new File(blocked, "Game");

        assertThrows(IOException.class, () -> AppStorage.migrateEntry(legacy, shared));
        assertEquals("save", read(new File(legacy, "slot.asd")));
    }
}

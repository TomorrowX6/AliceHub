package io.github.kichikuou.xsystem4;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LauncherPerformanceTest {
    @Test
    public void isBuiltInGameIdMatchesSelectOrder() {
        List<String> order = GameCatalog.builtInSelectOrder();
        assertFalse(order.isEmpty());
        for (String id : order) {
            assertTrue("Expected built-in id: " + id, GameCatalog.isBuiltInGameId(id));
        }
        assertFalse(GameCatalog.isBuiltInGameId(GameCatalog.PREFIX_CUSTOM + "MyCustomGame"));
        assertFalse(GameCatalog.isBuiltInGameId(""));
    }

    @Test
    public void classifyItemCachesAndInvalidatesMetadata() throws IOException {
        File root = Files.createTempDirectory("alicehub-cache-test").toFile();
        try {
            File gameDir = new File(root, "GameDir");
            assertTrue(gameDir.mkdirs());
            assertTrue(new File(gameDir, "AliceStart.ini").createNewFile());
            assertTrue(new File(gameDir, "大帝国.ain").createNewFile());

            Item item = new Item("Daiteikoku", gameDir, root, null, null, null);
            assertEquals(GameCatalog.ID_DAITEIKOKU, GameCatalog.INSTANCE.classifyItem(item));
            assertEquals(GameCatalog.ID_DAITEIKOKU, item.getCachedClassifiedId$app_debug());

            // Replace marker on disk; cached id stays until explicitly invalidated.
            assertTrue(new File(gameDir, "大帝国.ain").delete());
            assertTrue(new File(gameDir, "Rance03.ain").createNewFile());
            assertEquals(GameCatalog.ID_DAITEIKOKU, GameCatalog.INSTANCE.classifyItem(item));

            item.invalidateMetadataCache$app_debug();
            assertNull(item.getCachedClassifiedId$app_debug());
            assertNull(item.getCachedHasMod$app_debug());
            assertFalse(item.getCachedModInfoResolved$app_debug());
            assertEquals(GameCatalog.ID_RANCE03, GameCatalog.INSTANCE.classifyItem(item));
        } finally {
            deleteRecursively(root);
        }
    }

    @Test
    public void dirSignatureDetectsDualIniAndSystem3AndIconInPlaceEdits() throws IOException {
        File root = Files.createTempDirectory("alicehub-sig-test").toFile();
        try {
            // 1. Haruurare dual-INI: AliceStart2.ini takes precedence over AliceStart.ini when haru_c.ain exists.
            File haruDir = new File(root, "Haru");
            assertTrue(haruDir.mkdirs());
            assertTrue(new File(haruDir, "haru_c.ain").createNewFile());
            File ini1 = new File(haruDir, "AliceStart.ini");
            File ini2 = new File(haruDir, "AliceStart2.ini");
            Files.write(ini1.toPath(), "GameName = \"Haru1\"\n".getBytes());
            Files.write(ini2.toPath(), "".getBytes());
            long origDirMtime = haruDir.lastModified();

            GameList.DirSignature before = GameList.Companion.computeDirSignature$app_debug(haruDir);
            assertEquals("AliceStart2.ini", before.getLaunchIniName());

            // In-place edit to AliceStart2.ini without changing parent dir mtime.
            Files.write(ini2.toPath(), "GameName = \"HaruCN\"\nCodeName = \"haru_c.ain\"\n".getBytes());
            ini2.setLastModified(before.getLaunchIniModified() + 2000L);
            haruDir.setLastModified(origDirMtime);

            GameList.DirSignature after = GameList.Companion.computeDirSignature$app_debug(haruDir);
            assertFalse("In-place AliceStart2.ini edit must invalidate signature", before.equals(after));

            // 2. Case-variant System3.ini in-place edit.
            File sys3Dir = new File(root, "Sys3");
            assertTrue(sys3Dir.mkdirs());
            File sys3Ini = new File(sys3Dir, "sYsTeM3.InI");
            Files.write(sys3Ini.toPath(), "game=yakata2\n".getBytes());
            long sys3Mtime = sys3Dir.lastModified();
            GameList.DirSignature sys3Before = GameList.Companion.computeDirSignature$app_debug(sys3Dir);
            assertEquals("sYsTeM3.InI", sys3Before.getSystem3IniName());
            Files.write(sys3Ini.toPath(), "game=sdps_maria\n".getBytes());
            sys3Ini.setLastModified(sys3Before.getSystem3IniModified() + 2000L);
            sys3Dir.setLastModified(sys3Mtime);
            GameList.DirSignature sys3After = GameList.Companion.computeDirSignature$app_debug(sys3Dir);
            assertFalse("In-place System3.ini edit must invalidate signature", sys3Before.equals(sys3After));

            // 3. Custom .ico overwrite at same path updates both DirSignature and Item.iconFingerprint
            //    even when filesystem timestamp granularity preserves lastModified().
            File icoFile = new File(haruDir, "custom.ico");
            Files.write(icoFile.toPath(), new byte[]{0, 0, 1, 0, 1, 0});
            icoFile.setLastModified(1_000_000L);
            GameList.DirSignature icoBefore = GameList.Companion.computeDirSignature$app_debug(haruDir);
            Item itemBefore = new Item("Haru", haruDir, root, null, icoFile, null);
            String fpBefore = itemBefore.iconFingerprint$app_debug();

            Files.write(icoFile.toPath(), new byte[]{0, 0, 1, 0, 1, 0, 9, 9});
            icoFile.setLastModified(1_000_000L);
            GameList.DirSignature icoAfter = GameList.Companion.computeDirSignature$app_debug(haruDir);
            assertFalse("Overwriting custom .ico must invalidate signature", icoBefore.equals(icoAfter));
            itemBefore.invalidateMetadataCache$app_debug();
            assertFalse("Overwriting custom .ico must change iconFingerprint", fpBefore.equals(itemBefore.iconFingerprint$app_debug()));
        } finally {
            deleteRecursively(root);
        }
    }

    private static void deleteRecursively(File f) {
        if (f == null || !f.exists()) return;
        File[] children = f.listFiles();
        if (children != null) {
            for (File c : children) {
                deleteRecursively(c);
            }
        }
        f.delete();
    }
}

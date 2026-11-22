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

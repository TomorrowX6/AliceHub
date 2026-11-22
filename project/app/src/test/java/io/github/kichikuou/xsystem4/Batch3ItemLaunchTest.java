package io.github.kichikuou.xsystem4;

import android.content.Context;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class Batch3ItemLaunchTest {
    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void untitledConfiguredChsFolderDiscoversAndCreatesLaunchableItem() throws Exception {
        File dir = tmp.newFolder("UntitledConfiguredChs");
        byte[] iniBytes = "CodeName = \"CustomGame.CHS\" ; Chinese build\n".getBytes(StandardCharsets.UTF_8);
        Files.write(new File(dir, "System40.ini").toPath(), iniBytes);
        Files.write(new File(dir, "CustomGame.CHS").toPath(), new byte[]{1});

        assertTrue(GameListKt.isSystem4GameRoot(dir));
        List<NativeGameImport.TreeEntry<String, byte[]>> entries = Arrays.asList(
                new NativeGameImport.TreeEntry<>("ini", "System40.ini", false, iniBytes),
                new NativeGameImport.TreeEntry<>("chs", "CustomGame.CHS", false, new byte[]{1})
        );
        assertNotNull(NativeGameImport.INSTANCE.discoverImportSources(
                "root", entries, id -> Collections.emptyList(), NativeGameImport.TreeEntry::getPayload
        ).getPrimaryEntries());

        Context context = RuntimeEnvironment.getApplication();
        Item item = Item.Companion.fromDirectory(dir, tmp.getRoot(), context);
        assertNull(item.getError());
        assertNotNull(item.getSavedir());
        assertEquals("CustomGame", item.getName());
    }

    @Test
    public void untitledUtf8CodeNameWithSlashCommentCreatesLaunchableItem() throws Exception {
        File dir = tmp.newFolder("UntitledUtf8Chs");
        byte[] iniBytes = "CodeName = \"游戏.CHS\" // UTF-8 build\n".getBytes(StandardCharsets.UTF_8);
        Files.write(new File(dir, "System40.ini").toPath(), iniBytes);
        Files.write(new File(dir, "游戏.CHS").toPath(), new byte[]{1});

        assertTrue(GameListKt.isSystem4GameRoot(dir));
        Context context = RuntimeEnvironment.getApplication();
        Item item = Item.Companion.fromDirectory(dir, tmp.getRoot(), context);
        assertNull(item.getError());
        assertNotNull(item.getSavedir());
        assertEquals("游戏", item.getName());
    }

    @Test
    public void untitledIniWithoutMatchingCodeFileStillReportsError() throws Exception {
        File dir = tmp.newFolder("BrokenUntitled");
        Files.write(new File(dir, "System40.ini").toPath(),
                "CodeName = \"Missing.CHS\"\n".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "00_aux.chs").toPath(), new byte[]{1});

        Context context = RuntimeEnvironment.getApplication();
        Item item = Item.Companion.fromDirectory(dir, tmp.getRoot(), context);
        assertNotNull(item.getError());
        assertNull(item.getSavedir());
    }
}

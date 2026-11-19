package io.github.kichikuou.xsystem4;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import kotlin.Unit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

public class SaveBackupRestoreTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private File live() throws IOException {
        File live = temporary.newFolder("saves");
        write(new File(live, "original.asd"), "original");
        write(new File(live, ".hidden"), "hidden");
        assertTrue(new File(live, ".settings").mkdir());
        write(new File(live, ".settings/config"), "settings");
        return live;
    }

    private static void write(File file, String value) throws IOException {
        Files.write(file.toPath(), value.getBytes(StandardCharsets.UTF_8));
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private File archive(String... names) throws IOException {
        File archive = temporary.newFile("backup.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive.toPath()))) {
            for (String name : names) {
                byte[] bytes = "replacement".getBytes(StandardCharsets.UTF_8);
                ZipEntry entry = new ZipEntry(name);
                entry.setMethod(ZipEntry.STORED);
                entry.setSize(bytes.length);
                entry.setCompressedSize(bytes.length);
                CRC32 crc = new CRC32();
                crc.update(bytes);
                entry.setCrc(crc.getValue());
                zip.putNextEntry(entry);
                zip.write(bytes);
                zip.closeEntry();
            }
        }
        return archive;
    }

    private int restore(File zip, File live) {
        return SaveBackupRestore.INSTANCE.restore(zip, live, () -> Unit.INSTANCE, File::renameTo);
    }

    private void assertNoTemporaryDirectories() {
        assertFalse(Arrays.stream(temporary.getRoot().listFiles())
                .anyMatch(file -> file.getName().startsWith(".restore_")));
    }

    private void assertOriginal(File live) throws IOException {
        assertEquals("original", read(new File(live, "original.asd")));
        assertEquals("hidden", read(new File(live, ".hidden")));
        assertEquals("settings", read(new File(live, ".settings/config")));
        assertEquals(3, live.listFiles().length);
        assertNoTemporaryDirectories();
    }

    @Test public void corruptCrcDoesNotTouchLiveSaves() throws IOException {
        File live = live();
        File zip = archive("SaveData/new.asd");
        byte[] bytes = Files.readAllBytes(zip.toPath());
        // Change stored payload without changing its size or either header's CRC.
        int offset = new String(bytes, StandardCharsets.ISO_8859_1).indexOf("replacement");
        assertTrue(offset >= 0);
        bytes[offset] ^= 1;
        Files.write(zip.toPath(), bytes);
        IOException error = assertThrows(IOException.class, () -> restore(zip, live));
        assertTrue(error.getMessage().contains("Corrupt entry"));
        assertOriginal(live);
    }

    @Test public void successfulRestoreReplacesWholeSaveSet() throws IOException {
        File live = live();
        assertEquals(2, restore(archive("SaveData/new.asd", "SaveData/sub/slot.asd"), live));
        assertEquals("replacement", read(new File(live, "new.asd")));
        assertEquals("replacement", read(new File(live, "sub/slot.asd")));
        assertEquals("hidden", read(new File(live, ".hidden")));
        assertEquals("settings", read(new File(live, ".settings/config")));
        assertFalse(new File(live, "original.asd").exists());
        assertNoTemporaryDirectories();
    }

    @Test public void extractionFailureAfterFirstFilePreservesOriginal() throws IOException {
        File live = live();
        File zip = archive("SaveData/new.asd", "SaveData/new.asd/child");
        assertThrows(IOException.class, () -> restore(zip, live));
        assertOriginal(live);
    }

    @Test public void failedInstallRollsBackDirectorySwap() throws IOException {
        File live = live();
        File zip = archive("SaveData/new.asd");
        int[] moves = {0};
        assertThrows(IOException.class, () -> SaveBackupRestore.INSTANCE.restore(
                zip, live, () -> Unit.INSTANCE, (from, to) -> {
                    moves[0]++;
                    return moves[0] != 2 && from.renameTo(to);
                }));
        assertEquals(3, moves[0]);
        assertOriginal(live);
    }

    @Test public void emptyArchivePreservesOnlyHiddenEntries() throws IOException {
        File live = live();
        assertEquals(0, restore(archive(), live));
        assertTrue(live.isDirectory());
        assertEquals(new HashSet<>(Arrays.asList(".hidden", ".settings")),
                new HashSet<>(Arrays.asList(live.list())));
        assertEquals("hidden", read(new File(live, ".hidden")));
        assertEquals("settings", read(new File(live, ".settings/config")));
    }

    @Test public void beforeSwapFailurePreservesOriginal() throws IOException {
        File live = live();
        File zip = archive("SaveData/new.asd");
        assertThrows(UncheckedIOException.class, () -> SaveBackupRestore.INSTANCE.restore(
                zip, live, () -> { throw new UncheckedIOException(new IOException("Backup failed")); },
                File::renameTo));
        assertOriginal(live);
    }
}

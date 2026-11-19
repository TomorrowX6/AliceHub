package io.github.kichikuou.xsystem4;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ModJournalTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private static void write(File file, String content) throws IOException {
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    private static class InterruptedInstall extends RuntimeException {}

    @Test public void addedFileIsJournaledBeforePublicationAndRollbackHandlesInterruptedWrites() throws Exception {
        for (boolean partiallyPublished : new boolean[]{false, true}) {
            File game = temp.newFolder();
            File staging = temp.newFolder();
            File vanilla = new File(game, "vanilla.txt");
            write(vanilla, "vanilla");
            File live = new File(game, "new.afa");
            File journal = new File(game, ".mod_installed_files.txt");
            File source = new File(staging, "new.afa") {
                @Override public boolean renameTo(File destination) {
                    assertTrue("Journal must exist before staging or live writes", journal.isFile());
                    try {
                        assertTrue(Files.readAllLines(journal.toPath()).contains("new.afa"));
                        assertTrue("Later files must already be journaled",
                                Files.readAllLines(journal.toPath()).contains("later.afa"));
                        assertFalse(Files.readAllLines(journal.toPath()).contains(".hidden.afa"));
                        assertFalse(live.exists());
                        assertFalse(new File(game, "later.afa").exists());
                        if (partiallyPublished) write(live, "partial mod content");
                    } catch (IOException error) {
                        throw new AssertionError(error);
                    }
                    throw new InterruptedInstall();
                }
            };
            write(source, "mod content");
            File later = new File(staging, "later.afa");
            write(later, "later mod content");
            File skipped = new File(staging, ".hidden.afa");
            write(skipped, "skipped content");
            File interruptedStaging = new File(staging.getPath()) {
                @Override public File[] listFiles() { return new File[]{source, later, skipped}; }
            };

            assertThrows(InterruptedInstall.class, () -> GameListKt.applyModStagingToGameDirInternal(
                    interruptedStaging, game, false, false, false));
            assertTrue(Files.readAllLines(journal.toPath()).contains("new.afa"));
            GameListKt.restoreVanillaFromModDir(game);
            assertFalse(live.exists());
            assertFalse(new File(game, "later.afa").exists());
            assertFalse(journal.exists());
            assertEquals("vanilla", new String(Files.readAllBytes(vanilla.toPath()), StandardCharsets.UTF_8));
            assertEquals(Arrays.asList("vanilla.txt"), Arrays.asList(game.list()));
        }
    }

    @Test public void failedJournalWriteDoesNotPublishNewFile() throws Exception {
        File game = temp.newFolder();
        File staging = temp.newFolder();
        File source = new File(staging, "new.afa");
        write(source, "mod content");
        assertTrue(new File(game, ".mod_installed_files.txt.tmp").mkdir());

        IOException error = assertThrows(IOException.class, () -> GameListKt.applyModStagingToGameDirInternal(
                staging, game, false, false, false));
        assertEquals(1, error.getSuppressed().length);
        assertTrue(error.getSuppressed()[0] instanceof IOException);
        assertTrue(source.isFile());
        assertFalse(new File(game, "new.afa").exists());
    }

    @Test public void rollbackPrunesDirectoriesForJournaledFileThatWasNeverCreated() throws Exception {
        File game = temp.newFolder();
        assertTrue(new File(game, "nested/child").mkdirs());
        write(new File(game, ".mod_installed_files.txt"), "nested/child/new.afa");
        GameListKt.restoreVanillaFromModDir(game);
        assertEquals(0, game.list().length);
    }

    @Test public void successfulInstallJournalsAddedFilesAndRestoresVanilla() throws Exception {
        File game = temp.newFolder();
        File staging = temp.newFolder();
        write(new File(game, "original.afa"), "vanilla");
        write(new File(staging, "original.afa"), "replacement");
        write(new File(staging, "new.afa"), "added");

        GameListKt.applyModStagingToGameDirInternal(staging, game, false, false, false);
        assertEquals(Arrays.asList("new.afa"),
                Files.readAllLines(new File(game, ".mod_installed_files.txt").toPath()));
        assertEquals("added", new String(Files.readAllBytes(new File(game, "new.afa").toPath()), StandardCharsets.UTF_8));
        GameListKt.restoreVanillaFromModDir(game);
        assertFalse(new File(game, "new.afa").exists());
        assertEquals("vanilla", new String(Files.readAllBytes(new File(game, "original.afa").toPath()), StandardCharsets.UTF_8));
    }

    @Test public void prepassKeepsCaseInsensitiveParentResolutionConsistent() throws Exception {
        File game = temp.newFolder();
        File staging = temp.newFolder();
        assertTrue(new File(staging, "Data").mkdir());
        assertTrue(new File(staging, "data").mkdir());
        write(new File(staging, "Data/first.afa"), "first");
        write(new File(staging, "data/second.afa"), "second");

        GameListKt.applyModStagingToGameDirInternal(staging, game, false, false, false);
        for (String rel : Files.readAllLines(new File(game, ".mod_installed_files.txt").toPath())) {
            assertTrue("Journal path must match the live file: " + rel, new File(game, rel).isFile());
        }
        GameListKt.restoreVanillaFromModDir(game);
        assertEquals(0, game.list().length);
    }
}

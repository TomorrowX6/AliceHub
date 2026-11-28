package io.github.kichikuou.xsystem4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;

import kotlin.Pair;

public class ErrorReportTimestampTest {
    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    private static void writeFile(File file, String text, long mtimeMs) throws IOException {
        Files.createDirectories(file.getParentFile().toPath());
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
        assertTrue(file.setLastModified(mtimeMs));
    }

    private static String formatHuman(long ms) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(ms));
    }

    @Test
    public void staleCrashCopiedWithFreshMtimeIsIgnoredForHumanAndEpochTimestamps() throws IOException {
        long now = System.currentTimeMillis();
        long elevenDaysAgo = now - 11L * 24L * 60L * 60L * 1000L;
        long fiveDaysAgo = now - 5L * 24L * 60L * 60L * 1000L;

        File migratedHuman = new File(tmp.getRoot(), "saves/Rance10/crash.log");
        writeFile(
                migratedHuman,
                "============================================================\n"
                        + "AliceHub Crash Report\n"
                        + "============================================================\n"
                        + "Timestamp   : " + formatHuman(elevenDaysAgo) + "\n"
                        + "Engine      : xsystem4 d3308c4\n"
                        + "Crash Type  : FATAL SIGNAL\n"
                        + "Signal      : 6 - SIGABRT (Abort)\n",
                now);

        File migratedEpoch = new File(tmp.getRoot(), "saves/Evenicle/crash.log");
        writeFile(
                migratedEpoch,
                "============================================================\n"
                        + "AliceHub Crash Report\n"
                        + "============================================================\n"
                        + "Timestamp   : epoch:" + (elevenDaysAgo / 1000L) + "\n"
                        + "Engine      : xsystem4 d3308c4\n"
                        + "Crash Type  : FATAL SIGNAL\n",
                now);

        assertTrue(ErrorReportKit.INSTANCE.effectiveCrashTimeMs(migratedHuman) <= fiveDaysAgo);
        assertTrue(ErrorReportKit.INSTANCE.effectiveCrashTimeMs(migratedEpoch) <= fiveDaysAgo);
        assertNull(ErrorReportKit.INSTANCE.selectPendingCrashFile(
                Arrays.asList(migratedHuman, migratedEpoch), fiveDaysAgo));
    }

    @Test
    public void freshCrashWinsOverStaleMigratedCrashWithNewerMtime() throws IOException {
        long now = System.currentTimeMillis();
        long seen = now - 60_000L;
        long freshTime = now - 5_000L;
        long oldTime = now - 10L * 24L * 60L * 60L * 1000L;

        File freshCrash = new File(tmp.getRoot(), "saves/Rance10/crash.log");
        writeFile(
                freshCrash,
                "============================================================\n"
                        + "AliceHub Crash Report\n"
                        + "============================================================\n"
                        + "Timestamp   : epoch:" + (freshTime / 1000L) + "\n"
                        + "Crash Type  : FATAL SIGNAL\n",
                freshTime);

        File staleCopied = new File(tmp.getRoot(), "saves/OldGame/crash.log");
        writeFile(
                staleCopied,
                "============================================================\n"
                        + "AliceHub Crash Report\n"
                        + "============================================================\n"
                        + "Timestamp   : " + formatHuman(oldTime) + "\n"
                        + "Crash Type  : FATAL SIGNAL\n",
                now);

        Pair<File, Long> pending = ErrorReportKit.INSTANCE.selectPendingCrashFile(
                Arrays.asList(staleCopied, freshCrash), seen);
        assertNotNull(pending);
        assertEquals(freshCrash.getCanonicalPath(), pending.getFirst().getCanonicalPath());
    }

    @Test
    public void sameSecondCrashIsStillReportedDespiteSecondTruncation() throws IOException {
        long wholeSecond = (System.currentTimeMillis() / 1000L) * 1000L;
        long seen = wholeSecond + 200L;

        File crashFile = new File(tmp.getRoot(), "saves/Rance10/crash.log");
        writeFile(
                crashFile,
                "============================================================\n"
                        + "AliceHub Crash Report\n"
                        + "============================================================\n"
                        + "Timestamp   : epoch:" + (wholeSecond / 1000L) + "\n"
                        + "Crash Type  : FATAL SIGNAL\n",
                wholeSecond + 700L);

        Pair<File, Long> pending = ErrorReportKit.INSTANCE.selectPendingCrashFile(
                Collections.singletonList(crashFile), seen);
        assertNotNull(pending);
        assertEquals(crashFile.getCanonicalPath(), pending.getFirst().getCanonicalPath());
    }

    @Test
    public void timestamplessCrashFallsBackToFileMtime() throws IOException {
        long now = System.currentTimeMillis();
        File sys35Crash = new File(tmp.getRoot(), "saves/Kichikuou/crash.log");
        writeFile(
                sys35Crash,
                "=== XSystem35 Crash Report ===\nReason: test\nPage: 1 Address: 0x10\n",
                now);

        assertNull(ErrorReportKit.INSTANCE.parseEmbeddedTimestampUpperBoundMs(sys35Crash));
        assertEquals(sys35Crash.lastModified(), ErrorReportKit.INSTANCE.effectiveCrashTimeMs(sys35Crash));
        assertNotNull(ErrorReportKit.INSTANCE.selectPendingCrashFile(
                Collections.singletonList(sys35Crash), now - 5_000L));
        assertNull(ErrorReportKit.INSTANCE.selectPendingCrashFile(
                Collections.singletonList(sys35Crash), now + 1_000L));
    }
}

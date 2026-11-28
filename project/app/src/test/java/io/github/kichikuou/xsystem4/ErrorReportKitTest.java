package io.github.kichikuou.xsystem4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import kotlin.Pair;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ErrorReportKitTest {
    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    private static void writeFile(File file, String text, long mtimeMs) throws IOException {
        Files.createDirectories(file.getParentFile().toPath());
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
        assertTrue(file.setLastModified(mtimeMs));
    }

    private static void setSeenMtime(Context context, long seenMs) throws IOException {
        File marker = new File(context.getFilesDir(), ".crash_seen_mtime");
        Files.write(marker.toPath(), Long.toString(seenMs).getBytes(StandardCharsets.UTF_8));
    }

    private static String formatHuman(long ms) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(ms));
    }

    @Test
    public void migratedOldCrashWithResetFuseMtimeDoesNotTriggerPendingDialog() throws IOException {
        Context context = RuntimeEnvironment.getApplication();
        File volume = tmp.newFolder("shared_vol");
        ShadowEnvironment.setExternalStorageDirectory(volume.toPath());
        ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED);
        Shadows.shadowOf(context.getSystemService(AppOpsManager.class)).setMode(
                "android:manage_external_storage",
                context.getApplicationInfo().uid,
                context.getPackageName(),
                AppOpsManager.MODE_ALLOWED);
        AppSettings.INSTANCE.setPublicStorage(context, true);

        long now = System.currentTimeMillis();
        long elevenDaysAgo = now - 11L * 24L * 60L * 60L * 1000L;
        long fiveDaysAgo = now - 5L * 24L * 60L * 60L * 1000L;
        setSeenMtime(context, fiveDaysAgo);

        // Simulate Android 11+ FUSE resetting copied crash.log mtime to NOW while preserving content.
        File migratedHumanCrash = new File(volume, "AliceHub/saves/Rance10/crash.log");
        writeFile(
                migratedHumanCrash,
                "============================================================\n"
                        + "AliceHub Crash Report\n"
                        + "============================================================\n"
                        + "Timestamp   : " + formatHuman(elevenDaysAgo) + "\n"
                        + "Engine      : xsystem4 d3308c4\n"
                        + "Crash Type  : FATAL SIGNAL\n"
                        + "Signal      : 6 - SIGABRT (Abort)\n",
                now);

        File migratedEpochCrash = new File(volume, "AliceHub/saves/Evenicle/crash.log");
        writeFile(
                migratedEpochCrash,
                "============================================================\n"
                        + "AliceHub Crash Report\n"
                        + "============================================================\n"
                        + "Timestamp   : epoch:" + (elevenDaysAgo / 1000L) + "\n"
                        + "Engine      : xsystem4 d3308c4\n"
                        + "Crash Type  : FATAL SIGNAL\n",
                now);

        assertNull(
                "Old crash logs copied with a fresh filesystem mtime must not pop up as new crashes",
                ErrorReportKit.INSTANCE.findPendingCrashFile(context));
    }

    @Test
    public void genuineFreshCrashIsStillDetectedEvenWhenStaleCopiedCrashHasNewerMtime() throws IOException {
        Context context = RuntimeEnvironment.getApplication();
        long now = System.currentTimeMillis();
        long seen = now - 60_000L;
        setSeenMtime(context, seen);

        long freshTime = now - 5_000L;
        File freshCrash = new File(context.getFilesDir(), "saves/Rance10/crash.log");
        writeFile(
                freshCrash,
                "============================================================\n"
                        + "AliceHub Crash Report\n"
                        + "============================================================\n"
                        + "Timestamp   : epoch:" + (freshTime / 1000L) + "\n"
                        + "Engine      : xsystem4 latest\n"
                        + "Crash Type  : FATAL SIGNAL\n",
                freshTime);

        File staleCopiedCrash = new File(context.getFilesDir(), "saves/OldGame/crash.log");
        long oldTime = now - 10L * 24L * 60L * 60L * 1000L;
        writeFile(
                staleCopiedCrash,
                "============================================================\n"
                        + "AliceHub Crash Report\n"
                        + "============================================================\n"
                        + "Timestamp   : " + formatHuman(oldTime) + "\n"
                        + "Engine      : xsystem4 d3308c4\n"
                        + "Crash Type  : FATAL SIGNAL\n",
                now);

        Pair<File, Long> pending = ErrorReportKit.INSTANCE.findPendingCrashFile(context);
        assertNotNull(pending);
        assertEquals(freshCrash.getCanonicalPath(), pending.getFirst().getCanonicalPath());
    }

    @Test
    public void sameSecondCrashIsNotDroppedByOneSecondTimestampPrecision() throws IOException {
        Context context = RuntimeEnvironment.getApplication();
        long wholeSecond = (System.currentTimeMillis() / 1000L) * 1000L;
        setSeenMtime(context, wholeSecond + 200L);

        File crashFile = new File(context.getFilesDir(), "saves/Rance10/crash.log");
        writeFile(
                crashFile,
                "============================================================\n"
                        + "AliceHub Crash Report\n"
                        + "============================================================\n"
                        + "Timestamp   : epoch:" + (wholeSecond / 1000L) + "\n"
                        + "Crash Type  : FATAL SIGNAL\n",
                wholeSecond + 700L);

        Pair<File, Long> pending = ErrorReportKit.INSTANCE.findPendingCrashFile(context);
        assertNotNull(pending);
        assertEquals(crashFile.getCanonicalPath(), pending.getFirst().getCanonicalPath());
    }

    @Test
    public void markExistingCrashesSeenSuppressesTimestamplessMigratedLogButAllowsLaterCrash() throws IOException {
        Context context = RuntimeEnvironment.getApplication();
        long now = System.currentTimeMillis();
        setSeenMtime(context, now - 10_000L);

        File legacySys35Crash = new File(context.getFilesDir(), "saves/Kichikuou/crash.log");
        writeFile(
                legacySys35Crash,
                "=== XSystem35 Crash Report ===\nReason: old error\nPage: 1 Address: 0x10\n",
                now);

        ErrorReportKit.INSTANCE.markExistingCrashesSeen(context);
        assertNull(
                "Timestampless pre-existing crash must be suppressed after storage switch",
                ErrorReportKit.INSTANCE.findPendingCrashFile(context));

        writeFile(
                legacySys35Crash,
                "=== XSystem35 Crash Report ===\nReason: real new error\nPage: 2 Address: 0x20\n",
                System.currentTimeMillis() + 5_000L);
        Pair<File, Long> pending = ErrorReportKit.INSTANCE.findPendingCrashFile(context);
        assertNotNull(pending);
        assertEquals(legacySys35Crash.getCanonicalPath(), pending.getFirst().getCanonicalPath());
    }
}

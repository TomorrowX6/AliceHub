package io.github.kichikuou.xsystem4;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowStatFs;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import kotlin.Unit;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class DownloadConcurrencyTest {
    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    private static final class Resource {
        final byte[] body;
        final String etag;
        boolean supportRange = true;
        long delayMs = 0L;

        Resource(byte[] body, String etag) {
            this.body = body;
            this.etag = etag;
        }
    }

    private ServerSocket serverSocket;
    private ExecutorService serverPool;
    private Thread acceptThread;
    private final Map<String, Resource> routes = new ConcurrentHashMap<>();
    private final List<Map<String, String>> recordedRequests = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger inFlight = new AtomicInteger(0);
    private final AtomicInteger peakInFlight = new AtomicInteger(0);

    @Before
    public void setUp() throws Exception {
        Context ctx = RuntimeEnvironment.getApplication();
        ctx.getSharedPreferences("alicehub_settings", Context.MODE_PRIVATE).edit().clear().commit();
        inFlight.set(0);
        peakInFlight.set(0);
        routes.clear();
        recordedRequests.clear();
        serverPool = Executors.newCachedThreadPool();
        serverSocket = new ServerSocket(0, 64, InetAddress.getByName("127.0.0.1"));
        acceptThread = new Thread(() -> {
            while (!serverSocket.isClosed()) {
                try {
                    Socket s = serverSocket.accept();
                    serverPool.execute(() -> {
                        try (Socket sock = s) {
                            handleHttp(sock);
                        } catch (Exception ignored) {
                        }
                    });
                } catch (Exception ignored) {
                }
            }
        });
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    @After
    public void tearDown() throws Exception {
        if (serverSocket != null) serverSocket.close();
        if (acceptThread != null) acceptThread.join(2000);
        if (serverPool != null) serverPool.shutdownNow();
    }

    private void handleHttp(Socket s) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.US_ASCII));
        String reqLine = reader.readLine();
        if (reqLine == null) return;
        String[] parts = reqLine.split(" ");
        String path = parts.length > 1 ? parts[1] : "/";
        Map<String, String> headers = new HashMap<>();
        headers.put("_path", path);
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                headers.put(line.substring(0, colon).trim().toLowerCase(Locale.ROOT), line.substring(colon + 1).trim());
            }
        }
        recordedRequests.add(headers);

        Resource r = routes.get(path);
        OutputStream out = s.getOutputStream();
        if (r == null) {
            out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            out.flush();
            return;
        }

        int cur = inFlight.incrementAndGet();
        peakInFlight.accumulateAndGet(cur, Math::max);
        try {
            if (r.delayMs > 0L) {
                Thread.sleep(r.delayMs);
            }
            String range = headers.get("range");
            String ifRange = headers.get("if-range");
            int start = 0;
            int end = r.body.length - 1;
            boolean partial = false;
            if (r.supportRange && range != null && range.startsWith("bytes=")) {
                if (ifRange == null || ifRange.equals(r.etag)) {
                    String spec = range.substring("bytes=".length());
                    int dash = spec.indexOf('-');
                    start = Integer.parseInt(spec.substring(0, dash));
                    String endStr = spec.substring(dash + 1).trim();
                    end = endStr.isEmpty() ? (r.body.length - 1) : Integer.parseInt(endStr);
                    partial = true;
                }
            }
            int len = r.body.length == 0 ? 0 : (end - start + 1);
            StringBuilder sb = new StringBuilder();
            if (partial) {
                sb.append("HTTP/1.1 206 Partial Content\r\n");
                sb.append("Content-Range: bytes ").append(start).append("-").append(end)
                        .append("/").append(r.body.length).append("\r\n");
            } else {
                sb.append("HTTP/1.1 200 OK\r\n");
            }
            if (r.etag != null) sb.append("ETag: ").append(r.etag).append("\r\n");
            if (r.supportRange) sb.append("Accept-Ranges: bytes\r\n");
            sb.append("Content-Length: ").append(len).append("\r\n");
            sb.append("Connection: close\r\n\r\n");
            out.write(sb.toString().getBytes(StandardCharsets.US_ASCII));
            if (len > 0) {
                out.write(r.body, start, len);
            }
            out.flush();
        } finally {
            inFlight.decrementAndGet();
        }
    }

    private String localUrl(String path) {
        return "http://127.0.0.1:" + serverSocket.getLocalPort() + path;
    }

    private static String sha256(byte[] data) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) sb.append(String.format(Locale.ROOT, "%02x", b));
        return sb.toString();
    }

    private static byte[] pattern(int size, int seed) {
        byte[] b = new byte[size];
        for (int i = 0; i < size; i++) {
            b[i] = (byte) ((i * 31 + seed) & 0xFF);
        }
        return b;
    }

    private static SeekBar findSeekBar(View root) {
        if (root instanceof SeekBar) return (SeekBar) root;
        if (root instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) root;
            for (int i = 0; i < vg.getChildCount(); i++) {
                SeekBar found = findSeekBar(vg.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    @Test
    public void defaultConcurrencyIsEightAndClampsOutOfRangeValues() {
        Context ctx = RuntimeEnvironment.getApplication();
        assertEquals(8, AppSettings.DEFAULT_DOWNLOAD_CONCURRENCY);
        assertEquals(8, AppSettings.getDownloadConcurrency(ctx));

        AppSettings.setDownloadConcurrency(ctx, 4);
        assertEquals(4, AppSettings.getDownloadConcurrency(ctx));

        AppSettings.setDownloadConcurrency(ctx, 0);
        assertEquals(AppSettings.MIN_DOWNLOAD_CONCURRENCY, AppSettings.getDownloadConcurrency(ctx));

        AppSettings.setDownloadConcurrency(ctx, -10);
        assertEquals(AppSettings.MIN_DOWNLOAD_CONCURRENCY, AppSettings.getDownloadConcurrency(ctx));

        AppSettings.setDownloadConcurrency(ctx, 99);
        assertEquals(AppSettings.MAX_DOWNLOAD_CONCURRENCY, AppSettings.getDownloadConcurrency(ctx));
    }

    @Test
    public void settingsActivityAdjustsPersistsAndResetsDownloadConcurrency() {
        ActivityController<SettingsActivity> controller = Robolectric.buildActivity(SettingsActivity.class).setup();
        SettingsActivity settings = controller.get();
        try {
            View row = settings.findViewById(R.id.row_settings_download_concurrency);
            TextView value = settings.findViewById(R.id.value_settings_download_concurrency);
            assertEquals(settings.getString(R.string.download_concurrency_value, 8), value.getText().toString());

            // Open dialog, drag SeekBar to 4 (progress = 4 - 1 = 3), confirm
            row.performClick();
            AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
            assertTrue(dialog.isShowing());
            SeekBar seekBar = findSeekBar(dialog.getWindow().getDecorView());
            assertNotNull(seekBar);
            assertEquals(15, seekBar.getMax());
            assertEquals(7, seekBar.getProgress());
            seekBar.setProgress(3);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();

            assertEquals(4, AppSettings.getDownloadConcurrency(settings));
            assertEquals(settings.getString(R.string.download_concurrency_value, 4), value.getText().toString());

            // Reopen dialog and click Neutral reset button -> restores default 8
            row.performClick();
            AlertDialog dialog2 = ShadowAlertDialog.getLatestAlertDialog();
            assertTrue(dialog2.isShowing());
            dialog2.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();

            assertEquals(8, AppSettings.getDownloadConcurrency(settings));
            assertEquals(settings.getString(R.string.download_concurrency_value, 8), value.getText().toString());
        } finally {
            controller.pause().stop().destroy();
        }
    }

    @Test
    public void multiFileBaseGameDownloadsConcurrentlyAndRespectsConcurrencySetting() throws Exception {
        File storage = tmp.newFolder("storage-concurrent").getCanonicalFile();
        ShadowStatFs.registerStats(storage.getPath(), 1_000_000, 1_000_000, 1_000_000);

        byte[] ini = "CodeName = \"ConcGame.ain\"\n".getBytes(StandardCharsets.UTF_8);
        byte[] ain = pattern(4096, 1);
        byte[] bigAfa = pattern(128 * 1024, 99);

        JSONArray filesArr = new JSONArray();
        Map<String, byte[]> expectedData = new HashMap<>();

        // 10 files including nested subdirs + a large file that also splits into segments in the shared pool
        Object[][] entries = new Object[][]{
                {"System40.ini", ini},
                {"ConcGame.ain", ain},
                {"ConcGameCG.afa", bigAfa},
                {"Manual/readme.txt", "hello-readme".getBytes(StandardCharsets.UTF_8)},
                {"data1.afa", pattern(4096, 11)},
                {"data2.afa", pattern(4096, 12)},
                {"data3.afa", pattern(4096, 13)},
                {"data4.afa", pattern(4096, 14)},
                {"data5.afa", pattern(4096, 15)},
                {"data6.afa", pattern(4096, 16)},
        };
        for (Object[] row : entries) {
            String name = (String) row[0];
            byte[] data = (byte[]) row[1];
            String hash = sha256(data);
            filesArr.put(new JSONObject()
                    .put("path", name)
                    .put("key", name)
                    .put("size", data.length)
                    .put("sha256", hash)
                    .put("extract", false));
            Resource res = new Resource(data, "\"" + hash + "\"");
            res.delayMs = 80L;
            routes.put("/cdn/files/" + name, res);
            expectedData.put(name, data);
        }
        JSONObject manifest = new JSONObject()
                .put("gameName", "ConcGame")
                .put("baseName", "ConcGame")
                .put("files", filesArr);
        routes.put("/cdn/manifest.json", new Resource(manifest.toString().getBytes(StandardCharsets.UTF_8), null));

        // Run with concurrency = 8 (default) + minSegmentedBytes = 32 KB: must achieve real parallel overlap >= 4 and <= 8!
        Context ctx = RuntimeEnvironment.getApplication();
        assertEquals(8, AppSettings.getDownloadConcurrency(ctx));
        File installed = OnlineInstaller.INSTANCE.installBaseGameFromCloud(
                ctx, storage, localUrl("/cdn"), null, null,
                () -> Unit.INSTANCE,
                update -> Unit.INSTANCE,
                8,
                32 * 1024L
        );

        assertTrue("Expected concurrent multi-file downloads (peak >= 4), got " + peakInFlight.get(),
                peakInFlight.get() >= 4);
        assertTrue("Concurrent connections must not exceed setting 8, got " + peakInFlight.get(),
                peakInFlight.get() <= 8);

        for (Map.Entry<String, byte[]> e : expectedData.entrySet()) {
            assertArrayEquals(e.getKey(), e.getValue(), Files.readAllBytes(new File(installed, e.getKey()).toPath()));
        }

        // Now run with concurrency = 1: must stay strictly serial (peak == 1)
        File storageSerial = tmp.newFolder("storage-serial").getCanonicalFile();
        ShadowStatFs.registerStats(storageSerial.getPath(), 1_000_000, 1_000_000, 1_000_000);
        peakInFlight.set(0);
        AppSettings.setDownloadConcurrency(ctx, 1);
        File installedSerial = OnlineInstaller.INSTANCE.installBaseGameFromCloud(
                ctx, storageSerial, localUrl("/cdn"), null, null,
                () -> Unit.INSTANCE,
                update -> Unit.INSTANCE
        );
        assertEquals(1, peakInFlight.get());
        for (Map.Entry<String, byte[]> e : expectedData.entrySet()) {
            assertArrayEquals(e.getKey(), e.getValue(), Files.readAllBytes(new File(installedSerial, e.getKey()).toPath()));
        }

        // Verify fail-fast cancellation when abortHandle is aborted mid-batch without leaking hung workers
        File storageAbort = tmp.newFolder("storage-abort").getCanonicalFile();
        ShadowStatFs.registerStats(storageAbort.getPath(), 1_000_000, 1_000_000, 1_000_000);
        InstallAbortHandle abortHandle = new InstallAbortHandle();
        AppSettings.setDownloadConcurrency(ctx, 8);
        boolean cancelled = false;
        try {
            OnlineInstaller.INSTANCE.installBaseGameFromCloud(
                    ctx, storageAbort, localUrl("/cdn"), abortHandle, null,
                    () -> {
                        if (abortHandle.isAborted()) {
                            throw new java.util.concurrent.CancellationException();
                        }
                        return Unit.INSTANCE;
                    },
                    update -> {
                        if (update.getPermille() != null && update.getPermille() > 0) {
                            abortHandle.abort();
                        }
                        return Unit.INSTANCE;
                    },
                    8,
                    32 * 1024L
            );
        } catch (java.util.concurrent.CancellationException expected) {
            cancelled = true;
        }
        assertTrue("Aborting concurrent batch must throw CancellationException", cancelled);
        assertFalse(new File(storageAbort, "ConcGame").exists());
    }

    @Test
    public void singleFileSegmentedRangeDownloadResumesAcrossConcurrencyChangeAndFallsBackOn200() throws Exception {
        byte[] payload = pattern(256 * 1024, 42);
        String hash = sha256(payload);
        Resource res = new Resource(payload, "\"seg-etag\"");
        res.delayMs = 60L;
        routes.put("/big.zip", res);

        File dir = tmp.newFolder("seg-dl").getCanonicalFile();
        ShadowStatFs.registerStats(dir.getPath(), 1_000_000, 1_000_000, 1_000_000);
        File part = new File(dir, "big.zip.part");

        OnlineInstaller.INSTANCE.downloadFileWithResume(
                localUrl("/big.zip"),
                part,
                payload.length,
                hash,
                Collections.emptyMap(),
                null,
                () -> Unit.INSTANCE,
                (done, total, speed) -> Unit.INSTANCE,
                8,
                32 * 1024L
        );

        assertArrayEquals(payload, Files.readAllBytes(part.toPath()));
        assertTrue("Expected >= 4 concurrent segment connections, got peak=" + peakInFlight.get(),
                peakInFlight.get() >= 4);
        assertTrue("Segment connections must not exceed 8, got peak=" + peakInFlight.get(),
                peakInFlight.get() <= 8);
        // Completed meta must clear segmented flag
        JSONObject finishedMeta = new JSONObject(
                new String(Files.readAllBytes(new File(part.getPath() + ".meta").toPath()), StandardCharsets.UTF_8));
        assertFalse(finishedMeta.optBoolean("segmented", false));

        // Resume a half-finished segmented .part after changing concurrency down to 1!
        File resumeDir = tmp.newFolder("seg-resume").getCanonicalFile();
        ShadowStatFs.registerStats(resumeDir.getPath(), 1_000_000, 1_000_000, 1_000_000);
        File resumePart = new File(resumeDir, "big.zip.part");
        int half = payload.length / 2;
        byte[] sparse = new byte[payload.length];
        System.arraycopy(payload, 0, sparse, 0, half);
        Files.write(resumePart.toPath(), sparse);
        JSONObject segMeta = new JSONObject()
                .put("url", localUrl("/big.zip"))
                .put("etag", "\"seg-etag\"")
                .put("totalBytes", payload.length)
                .put("sha256", hash)
                .put("segmented", true)
                .put("segments", new JSONArray()
                        .put(new JSONArray().put(0).put(half - 1).put(half))
                        .put(new JSONArray().put(half).put(payload.length - 1).put(0)));
        Files.write(new File(resumePart.getPath() + ".meta").toPath(),
                segMeta.toString().getBytes(StandardCharsets.UTF_8));
        recordedRequests.clear();

        OnlineInstaller.INSTANCE.downloadFileWithResume(
                localUrl("/big.zip"),
                resumePart,
                payload.length,
                hash,
                Collections.emptyMap(),
                null,
                () -> Unit.INSTANCE,
                (done, total, speed) -> Unit.INSTANCE,
                1,
                32 * 1024L
        );
        assertArrayEquals(payload, Files.readAllBytes(resumePart.toPath()));
        assertEquals(1, recordedRequests.size());
        assertEquals("bytes=" + half + "-" + (payload.length - 1), recordedRequests.get(0).get("range"));

        // 200 OK fallback when server ignores Range
        Resource noRangeRes = new Resource(payload, "\"no-range\"");
        noRangeRes.supportRange = false;
        routes.put("/no-range.zip", noRangeRes);
        File fallbackDir = tmp.newFolder("seg-fallback").getCanonicalFile();
        ShadowStatFs.registerStats(fallbackDir.getPath(), 1_000_000, 1_000_000, 1_000_000);
        File fallbackPart = new File(fallbackDir, "no-range.zip.part");
        recordedRequests.clear();

        OnlineInstaller.INSTANCE.downloadFileWithResume(
                localUrl("/no-range.zip"),
                fallbackPart,
                payload.length,
                hash,
                Collections.emptyMap(),
                null,
                () -> Unit.INSTANCE,
                (done, total, speed) -> Unit.INSTANCE,
                8,
                32 * 1024L
        );
        assertArrayEquals(payload, Files.readAllBytes(fallbackPart.toPath()));
        assertEquals("200 OK fallback must stream directly in 1 request", 1, recordedRequests.size());

        // Resumed download receiving HTTP 200 truncates the existing .part file, so space check
        // must credit existingPartBytes instead of falsely failing with import_no_space.
        File tightDir = tmp.newFolder("seg-tight-200").getCanonicalFile();
        File tightPart = new File(tightDir, "no-range.zip.part");
        byte[] existingPrefix = new byte[payload.length / 2];
        System.arraycopy(payload, 0, existingPrefix, 0, existingPrefix.length);
        Files.write(tightPart.toPath(), existingPrefix);
        // Available bytes = 64 MB headroom + (payload.length / 2) + 8 KB: enough for remaining half, < full payload!
        long tightAvailBytes = 64L * 1024L * 1024L + (payload.length / 2L) + 8192L;
        ShadowStatFs.registerStats(tightDir.getPath(), (int) (tightAvailBytes / 4096L) + 100, (int) (tightAvailBytes / 4096L), (int) (tightAvailBytes / 4096L));
        OnlineInstaller.INSTANCE.downloadFileWithResume(
                localUrl("/no-range.zip"),
                tightPart,
                payload.length,
                hash,
                Collections.emptyMap(),
                null,
                () -> Unit.INSTANCE,
                (done, total, speed) -> Unit.INSTANCE,
                8,
                32 * 1024L
        );
        assertArrayEquals(payload, Files.readAllBytes(tightPart.toPath()));

        // Hashless 200 OK fallback with mismatched Content-Length / oversized body must be rejected
        byte[] wrongPayload = pattern(payload.length + 1024, 77);
        Resource wrongSizeNoRange = new Resource(wrongPayload, "\"wrong-size\"");
        wrongSizeNoRange.supportRange = false;
        routes.put("/wrong-size-no-range.zip", wrongSizeNoRange);
        File wrongDir = tmp.newFolder("seg-wrong-size").getCanonicalFile();
        ShadowStatFs.registerStats(wrongDir.getPath(), 1_000_000, 1_000_000, 1_000_000);
        File wrongPart = new File(wrongDir, "wrong.zip.part");
        boolean rejectedWrongSize = false;
        try {
            OnlineInstaller.INSTANCE.downloadFileWithResume(
                    localUrl("/wrong-size-no-range.zip"),
                    wrongPart,
                    payload.length,
                    null,
                    Collections.emptyMap(),
                    null,
                    () -> Unit.INSTANCE,
                    (done, total, speed) -> Unit.INSTANCE,
                    8,
                    32 * 1024L
            );
        } catch (Exception e) {
            rejectedWrongSize = e.getMessage() != null && e.getMessage().contains("size mismatch");
        }
        assertTrue("Hashless 200 OK fallback with wrong size must fail", rejectedWrongSize);
        assertFalse(wrongPart.exists());
    }
}

package io.github.kichikuou.xsystem4;

import kotlin.Triple;
import kotlin.Unit;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Pure-JVM (non-Robolectric) regression tests covering Wave-2 stability, concurrency,
 * memory-safety, and security fixes across OnlineInstaller, Iso9660Extractor,
 * ArchiveExtractor, SaveBackupRestore, UncensoredPatchManager, NativeGameImport,
 * GameModifierBridge, and XSystem4Activity helpers.
 */
public class Wave2StabilitySecurityTest {
    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    @FunctionalInterface
    private interface RouteHandler {
        void handle(Map<String, String> headers, OutputStream out) throws Exception;
    }

    private ServerSocket serverSocket;
    private Thread serverThread;
    private final Map<String, RouteHandler> handlers = new ConcurrentHashMap<>();

    @Before
    public void setUp() throws Exception {
        handlers.clear();
        serverSocket = new ServerSocket(0, 32, InetAddress.getByName("127.0.0.1"));
        serverThread = new Thread(() -> {
            while (!serverSocket.isClosed()) {
                try {
                    Socket sock = serverSocket.accept();
                    Thread worker = new Thread(() -> {
                        try (Socket s = sock) {
                            BufferedReader reader = new BufferedReader(
                                    new InputStreamReader(s.getInputStream(), StandardCharsets.ISO_8859_1));
                            String reqLine = reader.readLine();
                            if (reqLine == null) return;
                            String[] parts = reqLine.split(" ");
                            String method = parts.length > 0 ? parts[0] : "GET";
                            String path = parts.length > 1 ? parts[1] : "/";
                            Map<String, String> headers = new HashMap<>();
                            headers.put("_method", method);
                            headers.put("_path", path);
                            String line;
                            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                                int colon = line.indexOf(':');
                                if (colon > 0) {
                                    headers.put(
                                            line.substring(0, colon).trim().toLowerCase(Locale.ROOT),
                                            line.substring(colon + 1).trim());
                                }
                            }
                            OutputStream out = s.getOutputStream();
                            RouteHandler handler = handlers.get(path);
                            if (handler == null) {
                                out.write(("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
                                        .getBytes(StandardCharsets.ISO_8859_1));
                                out.flush();
                            } else {
                                handler.handle(headers, out);
                            }
                        } catch (Throwable ignored) {
                        }
                    }, "Wave2TestHttpWorker");
                    worker.setDaemon(true);
                    worker.start();
                } catch (Throwable ignored) {
                }
            }
        }, "Wave2TestHttp");
        serverThread.setDaemon(true);
        serverThread.start();
    }

    @After
    public void tearDown() throws Exception {
        UncensoredPatchManager.INSTANCE.setStateDispatcherOverride(null);
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (Throwable ignored) {
            }
        }
        if (serverThread != null) {
            serverThread.join(2000);
        }
    }

    private String url(String path) {
        return "http://127.0.0.1:" + serverSocket.getLocalPort() + path;
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format(Locale.ROOT, "%02x", b));
        }
        return sb.toString();
    }

    private static byte[] filled(byte value, int len) {
        byte[] out = new byte[len];
        Arrays.fill(out, value);
        return out;
    }

    // -------------------------------------------------------------------------
    // S3-2: OnlineInstaller single-stream unbounded write, EOF, 206, 416
    // -------------------------------------------------------------------------

    @Test
    public void singleStreamCapsOversizedBodyWithoutContentLengthAndDeletesPart() throws Exception {
        byte[] oversized = new byte[4096];
        for (int i = 0; i < oversized.length; i++) oversized[i] = (byte) (i & 0xFF);
        handlers.put("/oversized.bin", (req, out) -> {
            out.write(("HTTP/1.1 200 OK\r\nETag: \"v1\"\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.ISO_8859_1));
            out.write(oversized);
            out.flush();
        });
        File part = new File(tmp.newFolder("cap"), "file.bin.part");
        try {
            OnlineInstaller.INSTANCE.downloadFileWithResume(
                    url("/oversized.bin"), part, 1024L, null,
                    Collections.emptyMap(), null,
                    () -> Unit.INSTANCE, (d, t, s) -> Unit.INSTANCE, 1);
            fail("Expected IOException when server streams more bytes than expectedSize");
        } catch (Exception e) {
            assertTrue("Expected IOException, got: " + e, e instanceof IOException);
            assertTrue("Expected size mismatch, got: " + e.getMessage(),
                    e.getMessage() != null && e.getMessage().contains("size mismatch"));
        }
        assertFalse("Oversized .part must be deleted", part.exists());
    }

    @Test
    public void singleStream206DoesNotOverwriteExpectedSizeWithSmallerTotal() throws Exception {
        File dir = tmp.newFolder("partial206");
        File part = new File(dir, "file.bin.part");
        File meta = new File(part.getPath() + ".meta");
        Files.write(part.toPath(), filled((byte) 0x41, 500));
        String metaJson = "{\"url\":\"" + url("/mismatch206.bin") + "\",\"etag\":\"\\\"v1\\\"\",\"totalBytes\":2000}";
        Files.write(meta.toPath(), metaJson.getBytes(StandardCharsets.UTF_8));

        AtomicBoolean sawRangeHeader = new AtomicBoolean(false);
        handlers.put("/mismatch206.bin", (req, out) -> {
            if ("bytes=500-".equals(req.get("range"))) {
                sawRangeHeader.set(true);
            }
            byte[] tail = filled((byte) 0x42, 100);
            String head = "HTTP/1.1 206 Partial Content\r\n"
                    + "Content-Range: bytes 500-599/600\r\n"
                    + "ETag: \"v1\"\r\n"
                    + "Content-Length: " + tail.length + "\r\n"
                    + "Connection: close\r\n\r\n";
            out.write(head.getBytes(StandardCharsets.ISO_8859_1));
            out.write(tail);
            out.flush();
        });
        try {
            OnlineInstaller.INSTANCE.downloadFileWithResume(
                    url("/mismatch206.bin"), part, 2000L, null,
                    Collections.emptyMap(), null,
                    () -> Unit.INSTANCE, (d, t, s) -> Unit.INSTANCE, 1);
            fail("Expected IOException when 206 Content-Range total != expectedSize");
        } catch (Exception e) {
            assertTrue("Expected IOException, got: " + e, e instanceof IOException);
            assertTrue("Expected size mismatch, got: " + e.getMessage(),
                    e.getMessage() != null && e.getMessage().contains("size mismatch"));
        }
        assertTrue("Client must send Range: bytes=500- when resuming from valid meta", sawRangeHeader.get());
        assertFalse(part.exists());
    }

    @Test
    public void singleStreamRejectsTruncatedEofWithoutContentLength() throws Exception {
        byte[] shortBody = filled((byte) 0x33, 400);
        handlers.put("/short-eof.bin", (req, out) -> {
            out.write(("HTTP/1.1 200 OK\r\nETag: \"v1\"\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.ISO_8859_1));
            out.write(shortBody);
            out.flush();
        });
        File part = new File(tmp.newFolder("short-eof"), "file.bin.part");
        try {
            OnlineInstaller.INSTANCE.downloadFileWithResume(
                    url("/short-eof.bin"), part, 1024L, null,
                    Collections.emptyMap(), null,
                    () -> Unit.INSTANCE, (d, t, s) -> Unit.INSTANCE, 1);
            fail("Expected IOException when stream hits EOF before expectedSize");
        } catch (Exception e) {
            assertTrue("Expected IOException, got: " + e, e instanceof IOException);
        }
    }

    @Test
    public void singleStream416ValidatesExpectedSize() throws Exception {
        File dir = tmp.newFolder("http416-size");
        File part = new File(dir, "file.bin.part");
        File meta = new File(part.getPath() + ".meta");
        Files.write(part.toPath(), filled((byte) 0x11, 1000));
        String metaJson = "{\"url\":\"" + url("/shrunk.bin") + "\",\"etag\":\"\\\"v1\\\"\",\"totalBytes\":2000}";
        Files.write(meta.toPath(), metaJson.getBytes(StandardCharsets.UTF_8));

        handlers.put("/shrunk.bin", (req, out) -> {
            String head = "HTTP/1.1 416 Range Not Satisfiable\r\n"
                    + "Content-Range: bytes */1000\r\n"
                    + "ETag: \"v1\"\r\n"
                    + "Content-Length: 0\r\n"
                    + "Connection: close\r\n\r\n";
            out.write(head.getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
        });
        try {
            OnlineInstaller.INSTANCE.downloadFileWithResume(
                    url("/shrunk.bin"), part, 2000L, null,
                    Collections.emptyMap(), null,
                    () -> Unit.INSTANCE, (d, t, s) -> Unit.INSTANCE, 1);
            fail("Expected IOException when 416 slashTotal != expectedSize");
        } catch (Exception e) {
            assertTrue("Expected IOException, got: " + e, e instanceof IOException);
            assertTrue("Expected size mismatch, got: " + e.getMessage(),
                    e.getMessage() != null && e.getMessage().contains("size mismatch"));
        }
        assertFalse(part.exists());
    }

    @Test
    public void singleStream416HashlessRestartsFreshWhenEtagMismatched() throws Exception {
        File dir = tmp.newFolder("http416-etag");
        File part = new File(dir, "file.bin.part");
        File meta = new File(part.getPath() + ".meta");
        byte[] staleBytes = filled((byte) 0x22, 1024);
        byte[] freshBytes = filled((byte) 0x55, 1024);
        Files.write(part.toPath(), staleBytes);
        String metaJson = "{\"url\":\"" + url("/changed416.bin") + "\",\"etag\":\"\\\"old-etag\\\"\"}";
        Files.write(meta.toPath(), metaJson.getBytes(StandardCharsets.UTF_8));

        AtomicInteger reqCount = new AtomicInteger(0);
        handlers.put("/changed416.bin", (req, out) -> {
            reqCount.incrementAndGet();
            if (req.containsKey("range")) {
                String head = "HTTP/1.1 416 Range Not Satisfiable\r\n"
                        + "Content-Range: bytes */1024\r\n"
                        + "ETag: \"new-etag\"\r\n"
                        + "Content-Length: 0\r\n"
                        + "Connection: close\r\n\r\n";
                out.write(head.getBytes(StandardCharsets.ISO_8859_1));
            } else {
                String head = "HTTP/1.1 200 OK\r\n"
                        + "ETag: \"new-etag\"\r\n"
                        + "Content-Length: " + freshBytes.length + "\r\n"
                        + "Connection: close\r\n\r\n";
                out.write(head.getBytes(StandardCharsets.ISO_8859_1));
                out.write(freshBytes);
            }
            out.flush();
        });

        OnlineInstaller.INSTANCE.downloadFileWithResume(
                url("/changed416.bin"), part, -1L, null,
                Collections.emptyMap(), null,
                () -> Unit.INSTANCE, (d, t, s) -> Unit.INSTANCE, 1);
        assertEquals(2, reqCount.get());
        assertArrayEquals(freshBytes, Files.readAllBytes(part.toPath()));
    }

    // -------------------------------------------------------------------------
    // S3-6: Sparse pre-allocated .part without valid/completed meta on hashless entry
    // -------------------------------------------------------------------------

    @Test
    public void sparsePartWithoutValidMetaIsNotAcceptedAsCompleteOnHashlessEntry() throws Exception {
        File dir = tmp.newFolder("sparse-hashless");
        File part = new File(dir, "sparse.bin.part");
        byte[] realData = new byte[2048];
        for (int i = 0; i < realData.length; i++) realData[i] = (byte) ((i * 7) & 0xFF);
        // Pre-allocate full-length zero-filled sparse .part with NO .part.meta
        Files.write(part.toPath(), new byte[realData.length]);

        AtomicInteger reqCount = new AtomicInteger(0);
        handlers.put("/sparse.bin", (req, out) -> {
            reqCount.incrementAndGet();
            String head = "HTTP/1.1 200 OK\r\n"
                    + "ETag: \"real-v1\"\r\n"
                    + "Content-Length: " + realData.length + "\r\n"
                    + "Connection: close\r\n\r\n";
            out.write(head.getBytes(StandardCharsets.ISO_8859_1));
            out.write(realData);
            out.flush();
        });

        OnlineInstaller.INSTANCE.downloadFileWithResume(
                url("/sparse.bin"), part, realData.length, null,
                Collections.emptyMap(), null,
                () -> Unit.INSTANCE, (d, t, s) -> Unit.INSTANCE, 1);
        assertEquals(1, reqCount.get());
        assertArrayEquals(realData, Files.readAllBytes(part.toPath()));
        String savedMetaStr = new String(Files.readAllBytes(new File(part.getPath() + ".meta").toPath()), StandardCharsets.UTF_8);
        assertTrue("Completed meta must mark completed:true, got " + savedMetaStr, savedMetaStr.contains("\"completed\":true"));
    }

    @Test
    public void segmentedDownloadRejectsSparsePartWithoutMetaAndCompletesAccurately() throws Exception {
        File dir = tmp.newFolder("seg-sparse");
        File part = new File(dir, "seg.bin.part");
        byte[] realData = new byte[4096];
        for (int i = 0; i < realData.length; i++) realData[i] = (byte) ((i * 13 + 5) & 0xFF);
        // Simulate crash leaving a full-size zero-filled sparse .part with no .part.meta
        Files.write(part.toPath(), new byte[realData.length]);

        handlers.put("/seg.bin", (req, out) -> {
            boolean isHead = "HEAD".equalsIgnoreCase(req.get("_method"));
            String range = req.get("range");
            if (range != null && range.startsWith("bytes=")) {
                String spec = range.substring("bytes=".length());
                String[] bounds = spec.split("-", -1);
                int start = Integer.parseInt(bounds[0]);
                int end = bounds[1].isEmpty() ? (realData.length - 1) : Integer.parseInt(bounds[1]);
                if (end >= realData.length) end = realData.length - 1;
                int len = end - start + 1;
                String head = "HTTP/1.1 206 Partial Content\r\n"
                        + "ETag: \"seg-v1\"\r\n"
                        + "Accept-Ranges: bytes\r\n"
                        + "Content-Range: bytes " + start + "-" + end + "/" + realData.length + "\r\n"
                        + "Content-Length: " + len + "\r\n"
                        + "Connection: close\r\n\r\n";
                out.write(head.getBytes(StandardCharsets.ISO_8859_1));
                if (!isHead) out.write(realData, start, len);
            } else {
                String head = "HTTP/1.1 200 OK\r\n"
                        + "ETag: \"seg-v1\"\r\n"
                        + "Accept-Ranges: bytes\r\n"
                        + "Content-Length: " + realData.length + "\r\n"
                        + "Connection: close\r\n\r\n";
                out.write(head.getBytes(StandardCharsets.ISO_8859_1));
                if (!isHead) out.write(realData);
            }
            out.flush();
        });

        OnlineInstaller.INSTANCE.downloadFileWithResume(
                url("/seg.bin"), part, realData.length, null,
                Collections.emptyMap(), null,
                () -> Unit.INSTANCE, (d, t, s) -> Unit.INSTANCE,
                2, 512L);
        assertArrayEquals(realData, Files.readAllBytes(part.toPath()));
    }

    // -------------------------------------------------------------------------
    // S3-7, S3-8, S3-18: Redirect scheme, timeout, bounded read, entry.key, negative size, marker hash, unique part, abort handle
    // -------------------------------------------------------------------------

    @Test
    public void openFollowingRedirectsRejectsNonHttpSchemeAndUses15sDefaultTimeout() throws Exception {
        try {
            OnlineInstaller.INSTANCE.openFollowingRedirects("file:///etc/passwd", Collections.emptyMap());
            fail("Expected IOException for file:// URL");
        } catch (Exception e) {
            assertTrue("Expected IOException, got: " + e, e instanceof IOException);
            assertTrue(e.getMessage() != null && e.getMessage().contains("Unsupported URL scheme"));
        }

        handlers.put("/bad-redir", (req, out) -> {
            out.write(("HTTP/1.1 302 Found\r\nLocation: file:///etc/passwd\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
        });
        try {
            OnlineInstaller.INSTANCE.openFollowingRedirects(url("/bad-redir"), Collections.emptyMap());
            fail("Expected IOException for redirect to file://");
        } catch (Exception e) {
            assertTrue("Expected IOException, got: " + e, e instanceof IOException);
            assertTrue(e.getMessage() != null && e.getMessage().contains("Unsupported URL scheme"));
        }

        handlers.put("/ok-timeout", (req, out) -> {
            out.write(("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok")
                    .getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
        });
        HttpURLConnection conn = OnlineInstaller.INSTANCE.openFollowingRedirects(url("/ok-timeout"), Collections.emptyMap());
        try {
            assertEquals(15_000, conn.getReadTimeout());
        } finally {
            conn.disconnect();
        }
    }

    @Test
    public void readBoundedUtf8EnforcesLimitAndFetchModCatalogFiltersBadKeys() throws Exception {
        String small = OnlineInstaller.INSTANCE.readBoundedUtf8(
                new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)), 10);
        assertEquals("hello", small);

        try {
            OnlineInstaller.INSTANCE.readBoundedUtf8(
                    new ByteArrayInputStream(filled((byte) 0x41, 32)), 16);
            fail("Expected IOException when response exceeds maxBytes");
        } catch (Exception e) {
            assertTrue("Expected IOException, got: " + e, e instanceof IOException);
            assertTrue(e.getMessage() != null && e.getMessage().contains("exceeds maximum"));
        }

        String catalogStr = "{\"releases\":["
                + "{\"key\":\"../escape.zip\",\"fileName\":\"escape.zip\",\"version\":\"v1\",\"size\":10},"
                + "{\"key\":\"/abs.zip\",\"fileName\":\"abs.zip\",\"version\":\"v2\",\"size\":10},"
                + "{\"key\":\"mods/neg.zip\",\"fileName\":\"neg.zip\",\"version\":\"v3\",\"size\":-5},"
                + "{\"key\":\"mods/good.zip\",\"fileName\":\"good.zip\",\"version\":\"v4\",\"size\":100}"
                + "]}";
        byte[] catalogBytes = catalogStr.getBytes(StandardCharsets.UTF_8);
        handlers.put("/files/mods/index.json", (req, out) -> {
            String head = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: "
                    + catalogBytes.length + "\r\nConnection: close\r\n\r\n";
            out.write(head.getBytes(StandardCharsets.ISO_8859_1));
            out.write(catalogBytes);
            out.flush();
        });
        List<ModReleaseEntry> catalog = OnlineInstaller.INSTANCE.fetchModCatalog(url(""), "files/mods/index.json");
        assertEquals(1, catalog.size());
        assertEquals("good.zip", catalog.get(0).getFileName());
    }

    @Test
    public void parseManifestRejectsNegativeSizeAndTraversalKey() throws Exception {
        String validSha = sha256(new byte[]{1, 2, 3});
        String negManifest = "{\"gameName\":\"G\",\"baseName\":\"G\",\"files\":[{\"path\":\"a.bin\",\"key\":\"a.bin\",\"size\":-1,\"sha256\":\"" + validSha + "\"}]}";
        try {
            OnlineInstaller.INSTANCE.parseManifest(negManifest);
            fail("Expected InvalidManifestException for negative size");
        } catch (Exception e) {
            assertTrue("Expected InvalidManifestException, got: " + e,
                    e instanceof OnlineInstaller.InvalidManifestException);
        }

        String[] badKeys = new String[]{
                "", "/a.bin", "../a.bin", "sub/../a.bin", "sub\\\\a.bin", "a//b", "http://evil/x", "a?b=1"
        };
        for (String badKey : badKeys) {
            String m = "{\"gameName\":\"G\",\"baseName\":\"G\",\"files\":[{\"path\":\"a.bin\",\"key\":\""
                    + badKey + "\",\"size\":3,\"sha256\":\"" + validSha + "\"}]}";
            try {
                OnlineInstaller.INSTANCE.parseManifest(m);
                fail("Expected InvalidManifestException for key '" + badKey + "'");
            } catch (Exception e) {
                assertTrue("Expected InvalidManifestException for '" + badKey + "', got: " + e,
                        e instanceof OnlineInstaller.InvalidManifestException);
            }
        }

        String goodManifest = "{\"gameName\":\"G\",\"baseName\":\"G\",\"files\":[{\"path\":\"a.bin\",\"key\":\"sub/a.bin\",\"size\":3,\"sha256\":\"" + validSha + "\"}]}";
        OnlineInstaller.OnlineManifest parsed = OnlineInstaller.INSTANCE.parseManifest(goodManifest);
        assertEquals(1, parsed.getEntries().size());
        assertEquals("sub/a.bin", parsed.getEntries().get(0).getKey());
    }

    @Test
    public void extractMarkerHashPreventsCjkCollisionsAndUniquePartFilesNeverCollide() throws Exception {
        File dir = tmp.newFolder("markers");
        String sha = sha256(new byte[]{42});
        OnlineInstaller.ManifestEntry e1 = new OnlineInstaller.ManifestEntry("第一卷.rar", "vol1.rar", 10L, sha, true);
        OnlineInstaller.ManifestEntry e2 = new OnlineInstaller.ManifestEntry("第二卷.rar", "vol2.rar", 10L, sha, true);
        File m1 = OnlineInstaller.INSTANCE.markerFileForExtractEntry(dir, e1);
        File m2 = OnlineInstaller.INSTANCE.markerFileForExtractEntry(dir, e2);
        assertNotEquals("CJK extract markers must not collide", m1.getName(), m2.getName());
        assertTrue(m1.getName().startsWith(".extracted_") && m1.getName().endsWith(".done"));

        Set<String> used = new HashSet<>();
        File target = new File(dir, "same.bin");
        File p1 = OnlineInstaller.INSTANCE.uniquePartFileForEntry(target, 0, e1, used);
        File p2 = OnlineInstaller.INSTANCE.uniquePartFileForEntry(target, 1, e1, used);
        assertNotEquals("Concurrent entries targeting same file must use unique .part files",
                p1.getCanonicalPath(), p2.getCanonicalPath());
    }

    @Test
    public void segmentCompletionRequiresDurableSyncedDoneAndStagingIdentityIsV2() throws Exception {
        // SegmentProgress.isComplete must check durable syncedDone, never unsynced done
        OnlineInstaller.SegmentProgress unsynced = new OnlineInstaller.SegmentProgress(0L, 1023L, 1024L, 0L);
        assertEquals(1024L, unsynced.getLength());
        assertFalse("Unsynced segment must NOT report isComplete even when done == length", unsynced.isComplete());

        unsynced.setSyncedDone(1023L);
        assertFalse("Partially synced segment must NOT report isComplete", unsynced.isComplete());

        unsynced.setSyncedDone(1024L);
        assertTrue("Fully synced segment must report isComplete", unsynced.isComplete());

        // Default constructor syncs syncedDone = done for resumed/already-durable segments
        OnlineInstaller.SegmentProgress resumed = new OnlineInstaller.SegmentProgress(0L, 511L, 512L, 512L);
        assertTrue(resumed.isComplete());

        // Staging identity must be bumped to v2 so pre-update v1 staging dirs with legacy <name>.part files reset cleanly
        String sha = sha256(new byte[]{1, 2, 3});
        OnlineInstaller.ManifestEntry entry = new OnlineInstaller.ManifestEntry("game.bin", "game.bin", 3L, sha, false);
        OnlineInstaller.OnlineManifest manifest = new OnlineInstaller.OnlineManifest(
                "Game", "Game", 3L, Collections.singletonList(entry));
        String legacyV1Payload = "v1\nhttps://cdn/g\nGame\ngame.bin\tgame.bin\t3\t" + sha + "\tfalse\n";
        String legacyV1Identity = sha256(legacyV1Payload.getBytes(StandardCharsets.UTF_8));
        String currentIdentity = OnlineInstaller.INSTANCE.computeBaseStagingIdentity("https://cdn/g", manifest);
        assertNotEquals("Staging identity must differ from legacy v1 hash", legacyV1Identity, currentIdentity);
    }

    @Test
    public void installAbortHandleClearsWorkerInterruptUnderSameLock() throws Exception {
        InstallAbortHandle handle = new InstallAbortHandle();
        CountDownLatch registered = new CountDownLatch(1);
        CountDownLatch aborted = new CountDownLatch(1);
        AtomicBoolean leakedInterrupt = new AtomicBoolean(true);
        HttpURLConnection fakeConn = new HttpURLConnection(new URL("http://127.0.0.1/")) {
            @Override public void disconnect() {}
            @Override public boolean usingProxy() { return false; }
            @Override public void connect() {}
        };
        Thread worker = new Thread(() -> {
            handle.register(fakeConn);
            registered.countDown();
            try {
                aborted.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                handle.unregister(fakeConn);
                leakedInterrupt.set(Thread.currentThread().isInterrupted());
            }
        });
        worker.start();
        assertTrue(registered.await(2, TimeUnit.SECONDS));
        handle.abort();
        aborted.countDown();
        worker.join(2000);
        assertFalse("Worker thread must not retain stale interrupt flag after unregister", leakedInterrupt.get());
    }

    // -------------------------------------------------------------------------
    // S3-9 & S3-19: Iso9660Extractor & ArchiveExtractor
    // -------------------------------------------------------------------------

    private static byte[] buildMinimalIsoBytes(String fileName, byte[] content, boolean raw2352) {
        int stride = raw2352 ? 2352 : 2048;
        int dataOffset = raw2352 ? 16 : 0;
        byte[] bytes = new byte[24 * stride];
        int pvd = 16 * stride + dataOffset;
        bytes[pvd] = 1;
        System.arraycopy("CD001".getBytes(StandardCharsets.US_ASCII), 0, bytes, pvd + 1, 5);
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(pvd + 158, 20);
        buf.putInt(pvd + 166, 2048);
        int dirOff = 20 * stride + dataOffset;
        byte[] nameBytes = fileName.getBytes(StandardCharsets.US_ASCII);
        int recLen = 33 + nameBytes.length + (nameBytes.length % 2 == 0 ? 1 : 0);
        bytes[dirOff] = (byte) recLen;
        buf.putInt(dirOff + 2, 21);
        buf.putInt(dirOff + 10, content.length);
        bytes[dirOff + 25] = 0; // regular file
        bytes[dirOff + 32] = (byte) nameBytes.length;
        System.arraycopy(nameBytes, 0, bytes, dirOff + 33, nameBytes.length);
        System.arraycopy(content, 0, bytes, 21 * stride + dataOffset, content.length);
        return bytes;
    }

    @Test
    public void iso9660DetectsPartAndMagicExtractsSafelyAndChecksOverflowAndEnospc() throws Exception {
        assertTrue(Iso9660Extractor.INSTANCE.isIsoOrMdfName("disc.iso"));
        assertTrue(Iso9660Extractor.INSTANCE.isIsoOrMdfName("disc.mdf"));
        assertTrue(Iso9660Extractor.INSTANCE.isIsoOrMdfName("disc.iso.part"));
        assertTrue(Iso9660Extractor.INSTANCE.isIsoOrMdfName("disc.iso.0123abcd4567.part"));
        assertTrue(Iso9660Extractor.INSTANCE.isIsoOrMdfName("disc.mdf.tmp"));
        assertFalse(Iso9660Extractor.INSTANCE.isIsoOrMdfName("disc.iso.zip"));
        assertFalse(Iso9660Extractor.INSTANCE.isIsoOrMdfName("archive.zip.part"));

        // .part-suffixed ISO & .tmp raw 2352 MDF extracted via ArchiveExtractor without JNI
        byte[] payload = "hello-iso-content".getBytes(StandardCharsets.UTF_8);
        File isoPart = new File(tmp.newFolder("iso-part"), "game.iso.part");
        Files.write(isoPart.toPath(), buildMinimalIsoBytes("TEST.TXT", payload, false));
        File out1 = tmp.newFolder("iso-out1");
        assertEquals(1, ArchiveExtractor.INSTANCE.extractFileToDirectory(
                isoPart, out1, rel -> false, false, null, null, null));
        assertArrayEquals(payload, Files.readAllBytes(new File(out1, "TEST.TXT").toPath()));

        File mdfTmp = new File(tmp.newFolder("mdf-tmp"), ".archive_src_random.tmp");
        Files.write(mdfTmp.toPath(), buildMinimalIsoBytes("RAW.TXT", payload, true));
        assertTrue(Iso9660Extractor.INSTANCE.isIsoOrMdfFile(mdfTmp));
        File out2 = tmp.newFolder("mdf-out2");
        assertEquals(1, ArchiveExtractor.INSTANCE.extractFileToDirectory(
                mdfTmp, out2, rel -> false, false, null, null, null));
        assertArrayEquals(payload, Files.readAllBytes(new File(out2, "RAW.TXT").toPath()));

        // Path containment: reject dest == outCanonical for regular files, reject traversal
        File root = tmp.newFolder("iso-root").getCanonicalFile();
        try {
            Iso9660Extractor.INSTANCE.resolveSafeExtractTarget(root, ".", false);
            fail("Regular file targeting outCanonical must be rejected");
        } catch (Exception e) {
            assertTrue("Expected IOException, got: " + e, e instanceof IOException);
        }
        try {
            Iso9660Extractor.INSTANCE.resolveSafeExtractTarget(root, "", false);
            fail("Regular file with empty rel path must be rejected");
        } catch (Exception e) {
            assertTrue("Expected IOException, got: " + e, e instanceof IOException);
        }
        try {
            Iso9660Extractor.INSTANCE.resolveSafeExtractTarget(root, "../outside.txt", false);
            fail("Traversal path must be rejected");
        } catch (Exception e) {
            assertTrue("Expected IOException, got: " + e, e instanceof IOException);
        }
        assertEquals(
                new File(root, "sub/ok.txt").getCanonicalFile(),
                Iso9660Extractor.INSTANCE.resolveSafeExtractTarget(root, "sub/ok.txt", false));

        // Overflow-safe addition
        assertEquals(15L, Iso9660Extractor.INSTANCE.addBytesChecked(10L, 5L));
        try {
            Iso9660Extractor.INSTANCE.addBytesChecked(Long.MAX_VALUE, 1L);
            fail("Expected IOException on Long overflow");
        } catch (Exception e) {
            assertTrue("Expected IOException, got: " + e, e instanceof IOException);
        }
        try {
            Iso9660Extractor.INSTANCE.addBytesChecked(10L, -1L);
            fail("Expected IOException on negative size");
        } catch (Exception e) {
            assertTrue("Expected IOException, got: " + e, e instanceof IOException);
        }

        // ENOSPC classification
        assertTrue(Iso9660Extractor.INSTANCE.isNoSpaceException(
                new IOException("write failed: ENOSPC (No space left on device)")));
        assertTrue(Iso9660Extractor.INSTANCE.isNoSpaceException(
                new IOException("wrapper", new IOException("Disk quota exceeded"))));
        assertFalse(Iso9660Extractor.INSTANCE.isNoSpaceException(new IOException("connection reset")));
    }

    // -------------------------------------------------------------------------
    // S3-21: SaveBackupRestore parent mkdirs & rollback cleanup
    // -------------------------------------------------------------------------

    @Test
    public void saveBackupRestoreCreatesMissingParentAndRollsBackOnThrowable() throws Exception {
        File zipFile = tmp.newFile("save_backup.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile.toPath()))) {
            byte[] data = "restored-save".getBytes(StandardCharsets.UTF_8);
            ZipEntry entry = new ZipEntry("SaveData/slot01.asd");
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(data.length);
            entry.setCompressedSize(data.length);
            CRC32 crc = new CRC32();
            crc.update(data);
            entry.setCrc(crc.getValue());
            zos.putNextEntry(entry);
            zos.write(data);
            zos.closeEntry();
        }

        // Missing parent directory should be created automatically
        File nestedSaveDir = new File(tmp.getRoot(), "nested/brand_new/SaveData");
        assertFalse(nestedSaveDir.getParentFile().exists());
        int count = SaveBackupRestore.INSTANCE.restore(zipFile, nestedSaveDir);
        assertEquals(1, count);
        assertEquals("restored-save",
                new String(Files.readAllBytes(new File(nestedSaveDir, "slot01.asd").toPath()), StandardCharsets.UTF_8));

        // Rollback on Error (Throwable) must restore original and leave zero .restore_old_* behind
        File liveDir = tmp.newFolder("live_saves");
        Files.write(new File(liveDir, "orig.asd").toPath(), "keep-me".getBytes(StandardCharsets.UTF_8));
        int[] renameCalls = {0};
        try {
            SaveBackupRestore.INSTANCE.restore(
                    zipFile, liveDir, () -> Unit.INSTANCE, (from, to) -> {
                        renameCalls[0]++;
                        if (renameCalls[0] == 2) throw new AssertionError("simulated fatal error during swap");
                        return from.renameTo(to);
                    });
            fail("Expected AssertionError");
        } catch (AssertionError expected) {
        }
        assertEquals(3, renameCalls[0]);
        assertEquals("keep-me",
                new String(Files.readAllBytes(new File(liveDir, "orig.asd").toPath()), StandardCharsets.UTF_8));
        File[] siblings = liveDir.getParentFile().listFiles();
        assertTrue(siblings != null);
        for (File f : siblings) {
            assertFalse("Leaked temporary dir: " + f.getName(), f.getName().startsWith(".restore_"));
        }
    }

    // -------------------------------------------------------------------------
    // S3-13: UncensoredPatchManager generation guard & atomic fallback copy
    // -------------------------------------------------------------------------

    @Test
    public void uncensoredPatchManagerGenerationGuardPreventsStaleOverwriteAndAtomicCopyVerifiesHash() throws Exception {
        List<UncensoredInstallState> delivered = new ArrayList<>();
        UncensoredPatchManager.INSTANCE.setStateDispatcherOverride(block -> {
            block.invoke();
            return Unit.INSTANCE;
        });
        kotlin.jvm.functions.Function1<UncensoredInstallState, Unit> listener = st -> {
            delivered.add(st);
            return Unit.INSTANCE;
        };
        try {
            long gen1 = UncensoredPatchManager.INSTANCE.beginGenerationForTest(
                    new UncensoredInstallState.Downloading("/gameA", "rance10", "patch.afa", 10L, 100L, 10L));
            UncensoredPatchManager.INSTANCE.addListener(listener);

            // Transition to terminal Succeeded
            assertTrue(UncensoredPatchManager.INSTANCE.publishStateForTest(
                    new UncensoredInstallState.Succeeded("/gameA", "rance10"), gen1, false));
            // Late progress callback from gen1 must NOT overwrite Succeeded!
            assertFalse(UncensoredPatchManager.INSTANCE.publishStateForTest(
                    new UncensoredInstallState.Downloading("/gameA", "rance10", "patch.afa", 99L, 100L, 10L), gen1, false));
            assertTrue(UncensoredPatchManager.INSTANCE.getState() instanceof UncensoredInstallState.Succeeded);

            // Consume to Idle; late progress from gen1 must NOT overwrite Idle!
            UncensoredPatchManager.INSTANCE.consumeTerminalState();
            assertTrue(UncensoredPatchManager.INSTANCE.getState() instanceof UncensoredInstallState.Idle);
            assertFalse(UncensoredPatchManager.INSTANCE.publishStateForTest(
                    new UncensoredInstallState.Downloading("/gameA", "rance10", "patch.afa", 100L, 100L, 10L), gen1, false));
            assertTrue(UncensoredPatchManager.INSTANCE.getState() instanceof UncensoredInstallState.Idle);

            // New generation 2 starts; stale gen1 terminal state must be rejected
            long gen2 = UncensoredPatchManager.INSTANCE.beginGenerationForTest(
                    new UncensoredInstallState.Downloading("/gameB", "rance10", "patch.afa", 0L, 100L, 0L));
            assertNotEquals(gen1, gen2);
            assertFalse(UncensoredPatchManager.INSTANCE.publishStateForTest(
                    new UncensoredInstallState.Failed("/gameA", "rance10", "stale"), gen1, false));
            assertTrue(UncensoredPatchManager.INSTANCE.getState() instanceof UncensoredInstallState.Downloading);
            assertTrue(UncensoredPatchManager.INSTANCE.publishStateForTest(
                    new UncensoredInstallState.Cancelled("/gameB", "rance10"), gen2, false));
            UncensoredPatchManager.INSTANCE.consumeTerminalState();
        } finally {
            UncensoredPatchManager.INSTANCE.removeListener(listener);
        }

        // Atomic fallback copy when direct renameTo fails
        File srcDir = tmp.newFolder("unc-src");
        File dstDir = tmp.newFolder("unc-dst");
        byte[] content = "uncensored-payload-data".getBytes(StandardCharsets.UTF_8);
        File partFile = new File(srcDir, "uncensored_abc.part");
        Files.write(partFile.toPath(), content);
        File destFile = new File(dstDir, "AliceHubUncensoredCG.afa");
        Files.write(destFile.toPath(), "old-content".getBytes(StandardCharsets.UTF_8));

        char[] zeros = new char[64];
        Arrays.fill(zeros, '0');
        // Corrupt hash must fail, clean up .tmp, and leave existing destFile untouched!
        try {
            UncensoredPatchManager.INSTANCE.installPartFileAtomically(
                    partFile, destFile, content.length, new String(zeros),
                    () -> Unit.INSTANCE, (src, dst) -> !src.equals(partFile) && src.renameTo(dst));
            fail("Expected IOException for SHA-256 mismatch in fallback copy");
        } catch (Exception e) {
            assertTrue("Expected IOException, got: " + e, e instanceof IOException);
        }
        assertEquals("old-content",
                new String(Files.readAllBytes(destFile.toPath()), StandardCharsets.UTF_8));
        for (File f : dstDir.listFiles()) {
            assertFalse("Leaked temp file: " + f.getName(), f.getName().endsWith(".tmp"));
        }

        // Valid hash succeeds atomically via sibling .tmp -> renameTo(dest)
        UncensoredPatchManager.INSTANCE.installPartFileAtomically(
                partFile, destFile, content.length, sha256(content),
                () -> Unit.INSTANCE, (src, dst) -> !src.equals(partFile) && src.renameTo(dst));
        assertArrayEquals(content, Files.readAllBytes(destFile.toPath()));
        assertFalse(partFile.exists());
        for (File f : dstDir.listFiles()) {
            assertFalse("Leaked temp file: " + f.getName(), f.getName().endsWith(".tmp"));
        }
    }

    // -------------------------------------------------------------------------
    // S3-1, S3-10, S3-12: NativeGameImport case-insensitive dir, config_seq guard, inputNumber bounds
    // -------------------------------------------------------------------------

    @Test
    public void caseInsensitiveSubdirStaleConfigSeqAndInputNumberBoundsWorkCorrectly() throws Exception {
        // S3-10: match existing Data/ when looking up data / DATA on ext4
        File parent = tmp.newFolder("mod-tree");
        File existingData = new File(parent, "Data");
        assertTrue(existingData.mkdir());
        assertEquals(existingData, NativeGameImport.INSTANCE.resolveCaseInsensitiveSubdir(parent, "data"));
        assertEquals(existingData, NativeGameImport.INSTANCE.resolveCaseInsensitiveSubdir(parent, "DATA"));
        assertEquals(new File(parent, "NewDir"), NativeGameImport.INSTANCE.resolveCaseInsensitiveSubdir(parent, "NewDir"));

        // S3-12: stale config_seq write guard
        assertTrue(GameModifierBridge.INSTANCE.shouldSkipStaleConfigWrite(6, 5));
        assertFalse(GameModifierBridge.INSTANCE.shouldSkipStaleConfigWrite(5, 5));
        assertFalse(GameModifierBridge.INSTANCE.shouldSkipStaleConfigWrite(4, 5));
        assertFalse(GameModifierBridge.INSTANCE.shouldSkipStaleConfigWrite(10, -1));

        // S3-1: inputNumber bounds normalization & clamping with negative min and reversed min > max
        assertEquals(new Triple<>(-50, 50, -10), XSystem4Activity.normalizeInputNumberBounds(-50, 50, -10));
        assertEquals(new Triple<>(-20, 10, 10), XSystem4Activity.normalizeInputNumberBounds(10, -20, 999));
        assertEquals(new Triple<>(-20, 10, -20), XSystem4Activity.normalizeInputNumberBounds(10, -20, -999));
        assertEquals(-15, XSystem4Activity.parseClampedInputNumber("-15", -20, 10, 0));
        assertEquals(-20, XSystem4Activity.parseClampedInputNumber("-999", -20, 10, 0));
        assertEquals(10, XSystem4Activity.parseClampedInputNumber("999", -20, 10, 0));
        assertEquals(3, XSystem4Activity.parseClampedInputNumber("not-a-num", -20, 10, 3));
    }
}

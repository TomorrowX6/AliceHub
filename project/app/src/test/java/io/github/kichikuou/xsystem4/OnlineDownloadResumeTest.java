package io.github.kichikuou.xsystem4;

import android.content.Context;
import android.content.res.Configuration;

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
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
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

import kotlin.Unit;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class OnlineDownloadResumeTest {
    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    private ServerSocket serverSocket;
    private Thread serverThread;
    private final Map<String, byte[]> payloads = new ConcurrentHashMap<>();
    private final Map<String, String> redirects = new ConcurrentHashMap<>();
    private final Map<String, String> etags = new ConcurrentHashMap<>();
    private final Map<String, java.util.concurrent.atomic.AtomicInteger> failOnce503 = new ConcurrentHashMap<>();
    private final List<Map<String, String>> recordedRequests = Collections.synchronizedList(new ArrayList<>());

    @Before
    public void setUp() throws Exception {
        Context ctx = RuntimeEnvironment.getApplication();
        ctx.getSharedPreferences("alicehub_settings", Context.MODE_PRIVATE).edit().clear().commit();
        serverSocket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
        serverThread = new Thread(() -> {
            while (!serverSocket.isClosed()) {
                try (Socket s = serverSocket.accept()) {
                    handleHttp(s);
                } catch (Exception ignored) {
                }
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();
    }

    @After
    public void tearDown() throws Exception {
        if (serverSocket != null) {
            serverSocket.close();
        }
        if (serverThread != null) {
            serverThread.join(2000);
        }
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
        OutputStream out = s.getOutputStream();

        String redir = redirects.get(path);
        if (redir != null) {
            String resp = "HTTP/1.1 302 Found\r\nLocation: " + redir + "\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
            out.write(resp.getBytes(StandardCharsets.US_ASCII));
            out.flush();
            return;
        }

        java.util.concurrent.atomic.AtomicInteger remaining503 = failOnce503.get(path);
        if (remaining503 != null && remaining503.getAndDecrement() > 0) {
            out.write("HTTP/1.1 503 Service Unavailable\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            out.flush();
            return;
        }

        byte[] body = payloads.get(path);
        if (body == null) {
            out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            out.flush();
            return;
        }

        String etag = etags.getOrDefault(path, "\"default\"");
        String range = headers.get("range");
        String ifRange = headers.get("if-range");
        int start = 0;
        boolean partial = false;
        if (range != null && range.startsWith("bytes=")) {
            if (ifRange == null || ifRange.equals(etag)) {
                start = Integer.parseInt(range.substring("bytes=".length()).replace("-", ""));
                partial = true;
            }
        }
        int len = body.length - start;
        StringBuilder sb = new StringBuilder();
        if (partial) {
            sb.append("HTTP/1.1 206 Partial Content\r\n");
            sb.append("Content-Range: bytes ").append(start).append("-").append(body.length - 1).append("/").append(body.length).append("\r\n");
        } else {
            sb.append("HTTP/1.1 200 OK\r\n");
        }
        sb.append("ETag: ").append(etag).append("\r\n");
        sb.append("Accept-Ranges: bytes\r\n");
        sb.append("Content-Length: ").append(len).append("\r\n");
        sb.append("Connection: close\r\n\r\n");
        out.write(sb.toString().getBytes(StandardCharsets.US_ASCII));
        out.write(body, start, len);
        out.flush();
    }

    private String localUrl(String path) {
        return "http://127.0.0.1:" + serverSocket.getLocalPort() + path;
    }

    private static String sha256(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format(Locale.ROOT, "%02x", b));
        }
        return sb.toString();
    }

    @Test
    public void canonicalUrlNormalizesLegacyPrefixAndSharesCachedSizes() {
        Context ctx = RuntimeEnvironment.getApplication();
        assertEquals(OnlineInstaller.RANCE10_CDN_URL,
                AppSettings.canonicalCdnUrl("https://alicehub.000.moe/cn/rance10/"));
        assertEquals("https://alicehub.000.moe/rance10/files/mods/0.49-patch.rar",
                AppSettings.canonicalCdnUrl("https://alicehub.000.moe/cn/rance10/files/mods/0.49-patch.rar"));
        assertEquals("http://127.0.0.1:8080/rance10",
                AppSettings.canonicalCdnUrl("http://127.0.0.1:8080/rance10/"));
        assertEquals(AppSettings.defaultBaseGameSizeForUrl(OnlineInstaller.RANCE10_CDN_URL),
                AppSettings.defaultBaseGameSizeForUrl("https://alicehub.000.moe/cn/rance10"));

        AppSettings.INSTANCE.setCachedBaseGameSize(ctx, "https://alicehub.000.moe/cn/rance01", 123456789L);
        assertEquals(123456789L, AppSettings.INSTANCE.getCachedBaseGameSize(ctx, OnlineInstaller.RANCE01_CDN_URL));
    }

    @Test
    public void settingsActivityLaunchesCleanlyWithoutRouteSelector() {
        ActivityController<SettingsActivity> controller = Robolectric.buildActivity(SettingsActivity.class).setup();
        try {
            SettingsActivity settings = controller.get();
            assertEquals(0, settings.getResources().getIdentifier("row_settings_download_route", "id", settings.getPackageName()));
            for (String lang : new String[] { "en", "zh", "ja" }) {
                Configuration cfg = new Configuration(settings.getResources().getConfiguration());
                cfg.setLocale(Locale.forLanguageTag(lang));
                Context localized = settings.createConfigurationContext(cfg);
                String label = localized.getString(R.string.settings_online_install);
                String disclaimer = localized.getString(R.string.online_disclaimer_msg);
                assertFalse("Unexpected language suffix in " + label,
                        label.contains("Chinese") || label.contains("中文") || label.contains("中国語"));
                String finalArticle = lang.equals("zh") ? "第七条" : lang.equals("ja") ? "第7条" : "Article 7";
                assertTrue("Missing localized Article 7 in " + lang, disclaimer.contains("\n" + finalArticle + " · "));
            }
        } finally {
            controller.pause().stop().destroy();
        }
    }

    @Test
    public void resumeAcrossUrlChangeOmitsStaleIfRangeAndFollows302WithRange() throws Exception {
        byte[] data = new byte[4096];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) ((i * 31 + 7) & 0xFF);
        }
        String hash = sha256(data);

        redirects.put("/redirect/file.bin", localUrl("/asset/file.bin"));
        payloads.put("/asset/file.bin", data);
        etags.put("/asset/file.bin", "\"asset-etag\"");

        File dir = tmp.newFolder("resume");
        ShadowStatFs.registerStats(dir.getPath(), 1024 * 1024, 1024 * 1024, 1024 * 1024);
        File part = new File(dir, "file.bin.part");
        File meta = new File(part.getPath() + ".meta");

        byte[] prefix = new byte[1024];
        System.arraycopy(data, 0, prefix, 0, prefix.length);
        Files.write(part.toPath(), prefix);
        JSONObject savedMeta = new JSONObject()
                .put("url", localUrl("/old/file.bin"))
                .put("etag", "\"old-etag\"")
                .put("totalBytes", data.length)
                .put("sha256", hash);
        Files.write(meta.toPath(), savedMeta.toString().getBytes(StandardCharsets.UTF_8));

        OnlineInstaller.INSTANCE.downloadFileWithResume(
                localUrl("/redirect/file.bin"),
                part,
                data.length,
                hash,
                Collections.singletonMap("User-Agent", "AliceHubTest/1.0"),
                null,
                () -> Unit.INSTANCE,
                (done, total, speed) -> Unit.INSTANCE
        );

        assertArrayEquals(data, Files.readAllBytes(part.toPath()));
        Map<String, String> assetReq = recordedRequests.stream()
                .filter(h -> "/asset/file.bin".equals(h.get("_path")))
                .findFirst()
                .orElseThrow(AssertionError::new);
        assertEquals("bytes=1024-", assetReq.get("range"));
        assertNull("Stale ETag from previous URL must not be sent as If-Range", assetReq.get("if-range"));
    }

    @Test
    public void clearLegacyDownloadRoutePrefRemovesRouteAndMigratesLegacySizeKeys() {
        Context ctx = RuntimeEnvironment.getApplication();
        android.content.SharedPreferences sp = ctx.getSharedPreferences("alicehub_settings", Context.MODE_PRIVATE);
        sp.edit()
                .putString("download_route", "cn")
                .putBoolean("online_install_enabled", true)
                .putInt("download_concurrency", 6)
                .putLong("base_size_https://alicehub.000.moe/cn/evenicle2", 3399000111L)
                .putLong("base_size_https://alicehub.000.moe/cn/rance10", 1111111111L)
                .putLong("base_size_https://alicehub.000.moe/rance10", 4028000222L)
                .commit();

        AppSettings.clearLegacyDownloadRoutePref(ctx);

        assertFalse(sp.contains("download_route"));
        assertFalse(sp.contains("base_size_https://alicehub.000.moe/cn/evenicle2"));
        assertFalse(sp.contains("base_size_https://alicehub.000.moe/cn/rance10"));
        assertEquals(3399000111L, sp.getLong("base_size_https://alicehub.000.moe/evenicle2", -1L));
        assertEquals(4028000222L, sp.getLong("base_size_https://alicehub.000.moe/rance10", -1L));
        assertTrue(AppSettings.INSTANCE.isOnlineInstallEnabled(ctx));
        assertEquals(6, AppSettings.getDownloadConcurrency(ctx));
    }

    @Test
    public void catalogAndUncensoredManifestFetchRetryTransientServerError() throws Exception {
        String modCatalogJson = "{\"releases\":[{\"fileId\":1,\"version\":\"v1.0\",\"fileName\":\"mod.zip\",\"key\":\"mods/mod.zip\",\"size\":1024}]}";
        payloads.put("/game/files/mods/index.json", modCatalogJson.getBytes(StandardCharsets.UTF_8));
        failOnce503.put("/game/files/mods/index.json", new java.util.concurrent.atomic.AtomicInteger(1));

        List<ModReleaseEntry> releases = OnlineInstaller.INSTANCE.fetchModCatalog(localUrl("/game"), "files/mods/index.json");
        assertEquals(1, releases.size());
        long modHits = recordedRequests.stream().filter(h -> "/game/files/mods/index.json".equals(h.get("_path"))).count();
        assertEquals(2L, modHits);

        String hash = sha256("uncensored".getBytes(StandardCharsets.UTF_8));
        String uncJson = "{\"version\":\"1.0\",\"gameId\":\"rance10\",\"title\":\"Patch\",\"summary\":\"ok\","
                + "\"fileName\":\"AliceHubUncensoredCG.afa\",\"key\":\"uncensored/AliceHubUncensoredCG.afa\","
                + "\"size\":4096,\"sha256\":\"" + hash + "\",\"cgCount\":10}";
        payloads.put("/game/files/uncensored/manifest.json", uncJson.getBytes(StandardCharsets.UTF_8));
        failOnce503.put("/game/files/uncensored/manifest.json", new java.util.concurrent.atomic.AtomicInteger(1));

        UncensoredPatchManifest manifest = UncensoredPatchManager.INSTANCE.fetchManifest(localUrl("/game"));
        assertEquals("AliceHubUncensoredCG.afa", manifest.getFileName());
        long uncHits = recordedRequests.stream().filter(h -> "/game/files/uncensored/manifest.json".equals(h.get("_path"))).count();
        assertEquals(2L, uncHits);

        // Non-retryable 404 must fail on the first attempt without retrying.
        boolean threw = false;
        try {
            OnlineInstaller.INSTANCE.fetchModCatalog(localUrl("/missing"), "files/mods/index.json");
        } catch (Exception expected) {
            assertTrue(expected instanceof java.io.IOException);
            threw = true;
        }
        assertTrue(threw);
        long missingHits = recordedRequests.stream().filter(h -> "/missing/files/mods/index.json".equals(h.get("_path"))).count();
        assertEquals(1L, missingHits);
    }
}

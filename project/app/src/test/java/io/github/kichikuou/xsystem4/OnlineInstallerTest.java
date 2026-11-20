package io.github.kichikuou.xsystem4;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowStatFs;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class OnlineInstallerTest {
    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    /** A served resource; honours Range, and If-Range when {@code honorIfRange}. */
    private static final class Resource {
        byte[] body;
        String etag;
        boolean honorIfRange = true;
        boolean sendContentRange = true;

        Resource(byte[] body, String etag) {
            this.body = body;
            this.etag = etag;
        }
    }

    private ServerSocket serverSocket;
    private Thread serverThread;
    private final Map<String, Resource> routes = new ConcurrentHashMap<>();
    private final List<Map<String, String>> requests = Collections.synchronizedList(new ArrayList<>());

    @Before
    public void setUp() throws Exception {
        serverSocket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
        serverThread = new Thread(() -> {
            while (!serverSocket.isClosed()) {
                try (Socket s = serverSocket.accept()) {
                    handle(s);
                } catch (Exception ignored) {
                }
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();
    }

    @After
    public void tearDown() throws Exception {
        serverSocket.close();
        serverThread.join(2000);
    }

    private void handle(Socket s) throws Exception {
        BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.ISO_8859_1));
        String requestLine = in.readLine();
        if (requestLine == null) return;
        String path = requestLine.split(" ")[1];
        Map<String, String> req = new HashMap<>();
        req.put("path", path);
        String line;
        while ((line = in.readLine()) != null && !line.isEmpty()) {
            int idx = line.indexOf(':');
            if (idx > 0) req.put(line.substring(0, idx).trim().toLowerCase(Locale.ROOT), line.substring(idx + 1).trim());
        }
        requests.add(req);

        OutputStream out = s.getOutputStream();
        Resource r = routes.get(path);
        if (r == null) {
            out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
            return;
        }
        String range = req.get("range");
        String ifRange = req.get("if-range");
        boolean rangeApplies = range != null && (!r.honorIfRange || ifRange == null || ifRange.equals(r.etag));
        StringBuilder head = new StringBuilder();
        byte[] payload;
        if (rangeApplies) {
            int start = Integer.parseInt(range.replace("bytes=", "").replace("-", ""));
            payload = Arrays.copyOfRange(r.body, start, r.body.length);
            head.append("HTTP/1.1 206 Partial Content\r\n");
            if (r.sendContentRange) {
                head.append("Content-Range: bytes ").append(start).append('-').append(r.body.length - 1)
                    .append('/').append(r.body.length).append("\r\n");
            }
        } else {
            payload = r.body;
            head.append("HTTP/1.1 200 OK\r\n");
        }
        if (r.etag != null) head.append("ETag: ").append(r.etag).append("\r\n");
        head.append("Content-Length: ").append(payload.length).append("\r\nConnection: close\r\n\r\n");
        out.write(head.toString().getBytes(StandardCharsets.ISO_8859_1));
        out.write(payload);
        out.flush();
    }

    private String url(String path) {
        return "http://127.0.0.1:" + serverSocket.getLocalPort() + path;
    }

    private static void registerSpace(File... dirs) throws Exception {
        for (File d : dirs) {
            ShadowStatFs.registerStats(d.getCanonicalFile(), 1_000_000, 1_000_000, 1_000_000);
        }
    }

    private static String sha256(byte[] data) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static byte[] filled(char c, int size) {
        byte[] b = new byte[size];
        Arrays.fill(b, (byte) c);
        return b;
    }

    private static byte[] pattern(int size) {
        byte[] b = new byte[size];
        for (int i = 0; i < size; i++) b[i] = (byte) (i % 251);
        return b;
    }

    private static void write(File f, byte[] data) throws Exception {
        File parent = f.getParentFile();
        if (parent != null) parent.mkdirs();
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(data);
        }
    }

    private void download(File part, String path, long expectedSize, String sha) {
        OnlineInstaller.INSTANCE.downloadFileWithResume(
            url(path), part, expectedSize, sha, Collections.emptyMap(), null,
            () -> Unit.INSTANCE,
            (done, total, speed) -> Unit.INSTANCE
        );
    }

    private File partialDownload(byte[] prefix, String etag) throws Exception {
        File dir = tmp.newFolder("dl");
        registerSpace(dir);
        File part = new File(dir, "mod.zip.part");
        write(part, prefix);
        JSONObject meta = new JSONObject().put("url", url("/mod.zip")).put("totalBytes", 4096);
        if (etag != null) meta.put("etag", etag);
        write(new File(part.getPath() + ".meta"), meta.toString().getBytes(StandardCharsets.UTF_8));
        return part;
    }

    // --- Hashless HTTP resume ---

    @Test
    public void hashlessResumeWithoutValidatorRestartsInsteadOfSplicing() throws Exception {
        File part = partialDownload(Arrays.copyOf(filled('A', 4096), 2048), null);
        Resource r = new Resource(filled('B', 4096), null);
        r.honorIfRange = false;
        routes.put("/mod.zip", r);

        download(part, "/mod.zip", 4096, null);

        assertArrayEquals(filled('B', 4096), Files.readAllBytes(part.toPath()));
        assertEquals(1, requests.size());
        assertNull(requests.get(0).get("range"));
    }

    @Test
    public void hashlessResumeWithWeakEtagRestarts() throws Exception {
        File part = partialDownload(filled('A', 2048), "W/\"a\"");
        Resource r = new Resource(filled('B', 4096), "W/\"a\"");
        r.honorIfRange = false;
        routes.put("/mod.zip", r);

        download(part, "/mod.zip", 4096, null);

        assertArrayEquals(filled('B', 4096), Files.readAllBytes(part.toPath()));
        assertNull(requests.get(0).get("range"));
    }

    @Test
    public void hashlessResumeSendsIfRangeAndRestartsWhenRemoteChanged() throws Exception {
        File part = partialDownload(filled('A', 2048), "\"a\"");
        routes.put("/mod.zip", new Resource(filled('B', 4096), "\"b\""));

        download(part, "/mod.zip", 4096, null);

        assertArrayEquals(filled('B', 4096), Files.readAllBytes(part.toPath()));
        assertEquals("bytes=2048-", requests.get(0).get("range"));
        assertEquals("\"a\"", requests.get(0).get("if-range"));
    }

    @Test
    public void hashlessResumeRejectsPartialResponseWithoutMatchingEtag() throws Exception {
        File part = partialDownload(filled('A', 2048), "\"a\"");
        Resource r = new Resource(filled('B', 4096), null);
        r.honorIfRange = false;
        routes.put("/mod.zip", r);

        download(part, "/mod.zip", 4096, null);

        assertArrayEquals(filled('B', 4096), Files.readAllBytes(part.toPath()));
        assertEquals(2, requests.size());
        assertNull(requests.get(1).get("range"));
    }

    @Test
    public void hashlessResumeWithMatchingStrongEtagAppends() throws Exception {
        byte[] data = pattern(4096);
        File part = partialDownload(Arrays.copyOf(data, 2048), "\"a\"");
        routes.put("/mod.zip", new Resource(data, "\"a\""));

        download(part, "/mod.zip", 4096, null);

        assertArrayEquals(data, Files.readAllBytes(part.toPath()));
        assertEquals(1, requests.size());
        assertEquals("bytes=2048-", requests.get(0).get("range"));
    }

    @Test
    public void partialResponseWithoutContentRangeIsNotAppended() throws Exception {
        byte[] data = pattern(4096);
        File part = partialDownload(Arrays.copyOf(data, 2048), "\"a\"");
        Resource r = new Resource(data, "\"a\"");
        r.sendContentRange = false;
        routes.put("/mod.zip", r);

        download(part, "/mod.zip", 4096, null);

        assertArrayEquals(data, Files.readAllBytes(part.toPath()));
        assertEquals(2, requests.size());
        assertNull(requests.get(1).get("range"));
    }

    // --- Base manifest SHA-256 validation ---

    private static JSONObject manifestJson(JSONObject... files) throws Exception {
        JSONArray arr = new JSONArray();
        for (JSONObject f : files) arr.put(f);
        return new JSONObject().put("gameName", "Test").put("baseName", "TestGame").put("files", arr);
    }

    private static JSONObject fileEntry(String path, byte[] content, String sha) throws Exception {
        return new JSONObject().put("path", path).put("key", path).put("size", content.length).put("sha256", sha);
    }

    private static JSONObject fileEntry(String path, byte[] content) throws Exception {
        return fileEntry(path, content, sha256(content));
    }

    private void serveCdn(JSONObject manifest, Map<String, byte[]> files) throws Exception {
        routes.put("/cdn/manifest.json", new Resource(manifest.toString().getBytes(StandardCharsets.UTF_8), null));
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            routes.put("/cdn/files/" + e.getKey(), new Resource(e.getValue(), "\"" + sha256(e.getValue()) + "\""));
        }
    }

    private File installFromCdn(File storage) {
        return installFromCdn(storage, null);
    }

    private File installFromCdn(File storage, kotlin.jvm.functions.Function1<File, Unit> onStagingDirReady) {
        return OnlineInstaller.INSTANCE.installBaseGameFromCloud(
            RuntimeEnvironment.getApplication(), storage, url("/cdn"), null, onStagingDirReady,
            () -> Unit.INSTANCE,
            update -> Unit.INSTANCE
        );
    }

    @Test
    public void manifestRejectsBlankOrMalformedSha256() throws Exception {
        char[] a64 = new char[64];
        Arrays.fill(a64, 'a');
        String valid = new String(a64);
        for (String bad : new String[]{"", "   ", "abc", valid.replace('a', 'z'), valid.substring(1), valid + "a"}) {
            String raw = manifestJson(fileEntry("a.bin", new byte[]{1}, bad)).toString();
            try {
                OnlineInstaller.INSTANCE.parseManifest(raw);
                fail("Accepted sha256 '" + bad + "'");
            } catch (Exception e) {
                assertTrue(e instanceof OnlineInstaller.InvalidManifestException);
            }
        }
    }

    @Test
    public void manifestNormalizesValidSha256() throws Exception {
        String sha = sha256(new byte[]{1});
        OnlineInstaller.OnlineManifest parsed = OnlineInstaller.INSTANCE.parseManifest(
            manifestJson(fileEntry("a.bin", new byte[]{1}, " " + sha.toUpperCase(Locale.ROOT) + " ")).toString()
        );
        assertEquals(sha, parsed.getEntries().get(0).getSha256());
    }

    @Test
    public void installRejectsManifestWithBlankSha256BeforeStaging() throws Exception {
        File storage = tmp.newFolder("storage");
        registerSpace(storage);
        byte[] ini = "[Game]\n".getBytes(StandardCharsets.UTF_8);
        Map<String, byte[]> files = new HashMap<>();
        files.put("System40.ini", ini);
        serveCdn(manifestJson(fileEntry("System40.ini", ini, "")), files);
        try {
            installFromCdn(storage);
            fail("Install accepted a blank sha256");
        } catch (Exception e) {
            assertTrue(e instanceof OnlineInstaller.InvalidManifestException);
        }
        assertEquals(1, requests.size());
        for (File f : storage.listFiles()) {
            assertFalse(f.getName().startsWith(".online_base_staging_"));
        }
    }

    // --- Base staging identity ---

    @Test
    public void stagingIdentityDependsOnSourceAndManifest() throws Exception {
        OnlineInstaller.OnlineManifest a =
            OnlineInstaller.INSTANCE.parseManifest(manifestJson(fileEntry("a.bin", new byte[]{1})).toString());
        OnlineInstaller.OnlineManifest b =
            OnlineInstaller.INSTANCE.parseManifest(manifestJson(fileEntry("a.bin", new byte[]{2})).toString());
        String idA = OnlineInstaller.INSTANCE.computeBaseStagingIdentity("https://cdn/x", a);
        assertEquals(idA, OnlineInstaller.INSTANCE.computeBaseStagingIdentity("https://cdn/x", a));
        assertNotEquals(idA, OnlineInstaller.INSTANCE.computeBaseStagingIdentity("https://cdn/x", b));
        assertNotEquals(idA, OnlineInstaller.INSTANCE.computeBaseStagingIdentity("https://cdn/y", a));
    }

    @Test
    public void stagingIsKeptForSameIdentityAndResetOtherwise() throws Exception {
        File staging = new File(tmp.newFolder("storage"), ".online_base_staging_Game");
        OnlineInstaller.INSTANCE.prepareBaseStagingDir(staging, "id-a");
        File progress = new File(staging, "data.bin");
        write(progress, "partial".getBytes(StandardCharsets.UTF_8));

        OnlineInstaller.INSTANCE.prepareBaseStagingDir(staging, "id-a");
        assertTrue(progress.isFile());

        OnlineInstaller.INSTANCE.prepareBaseStagingDir(staging, "id-b");
        assertFalse(progress.exists());
        assertEquals("id-b", new String(Files.readAllBytes(
            new File(staging, OnlineInstaller.BASE_STAGING_IDENTITY_FILE).toPath()), StandardCharsets.UTF_8));
        for (File f : staging.getParentFile().listFiles()) {
            assertFalse(f.getName().startsWith(".trash_"));
        }
    }

    @Test
    public void legacyStagingWithoutIdentityIsReset() throws Exception {
        File staging = new File(tmp.newFolder("storage"), ".online_base_staging_Game");
        File legacy = new File(staging, "old.bin");
        write(legacy, "old".getBytes(StandardCharsets.UTF_8));

        OnlineInstaller.INSTANCE.prepareBaseStagingDir(staging, "id-a");

        assertFalse(legacy.exists());
    }

    @Test
    public void installAfterManifestChangeDropsObsoleteStagedFiles() throws Exception {
        File storage = tmp.newFolder("storage").getCanonicalFile();
        File staging = new File(storage, ".online_base_staging_TestGame");
        byte[] ini = "[Game]\n".getBytes(StandardCharsets.UTF_8);
        byte[] ainV1 = "ain-v1".getBytes(StandardCharsets.UTF_8);
        byte[] ainV2 = "ain-v2".getBytes(StandardCharsets.UTF_8);
        byte[] obsolete = "patch-v1".getBytes(StandardCharsets.UTF_8);

        // Version 1 was paused after staging a file that version 2 no longer ships.
        JSONObject manifestV1 = manifestJson(
            fileEntry("System40.ini", ini), fileEntry("TestGame.ain", ainV1), fileEntry("patch_v1.dat", obsolete));
        OnlineInstaller.INSTANCE.prepareBaseStagingDir(staging, OnlineInstaller.INSTANCE.computeBaseStagingIdentity(
            url("/cdn"), OnlineInstaller.INSTANCE.parseManifest(manifestV1.toString())));
        write(new File(staging, "patch_v1.dat"), obsolete);
        registerSpace(storage, staging);

        Map<String, byte[]> files = new HashMap<>();
        files.put("System40.ini", ini);
        files.put("TestGame.ain", ainV2);
        serveCdn(manifestJson(fileEntry("System40.ini", ini), fileEntry("TestGame.ain", ainV2)), files);
        File installed = installFromCdn(storage);

        assertFalse(new File(installed, "patch_v1.dat").exists());
        assertFalse(new File(installed, OnlineInstaller.BASE_STAGING_IDENTITY_FILE).exists());
        assertArrayEquals(ainV2, Files.readAllBytes(new File(installed, "TestGame.ain").toPath()));
        assertArrayEquals(ini, Files.readAllBytes(new File(installed, "System40.ini").toPath()));
    }

    @Test
    public void installResumesStagingForSameManifest() throws Exception {
        File storage = tmp.newFolder("storage").getCanonicalFile();
        File staging = new File(storage, ".online_base_staging_TestGame");
        byte[] ini = "[Game]\n".getBytes(StandardCharsets.UTF_8);
        byte[] ain = "ain".getBytes(StandardCharsets.UTF_8);
        JSONObject manifest = manifestJson(fileEntry("System40.ini", ini), fileEntry("TestGame.ain", ain));
        OnlineInstaller.INSTANCE.prepareBaseStagingDir(staging, OnlineInstaller.INSTANCE.computeBaseStagingIdentity(
            url("/cdn"), OnlineInstaller.INSTANCE.parseManifest(manifest.toString())));
        write(new File(staging, "System40.ini"), ini);
        registerSpace(storage, staging);

        Map<String, byte[]> files = new HashMap<>();
        files.put("System40.ini", ini);
        files.put("TestGame.ain", ain);
        serveCdn(manifest, files);
        File installed = installFromCdn(storage);

        assertArrayEquals(ain, Files.readAllBytes(new File(installed, "TestGame.ain").toPath()));
        for (Map<String, String> req : requests) {
            assertNotEquals("/cdn/files/System40.ini", req.get("path"));
        }
    }

    @Test
    public void failedFinalMoveKeepsStagingReusable() throws Exception {
        File storage = tmp.newFolder("storage").getCanonicalFile();
        byte[] ini = "[Game]\n".getBytes(StandardCharsets.UTF_8);
        byte[] ain = "ain".getBytes(StandardCharsets.UTF_8);
        Map<String, byte[]> files = new HashMap<>();
        files.put("System40.ini", ini);
        files.put("TestGame.ain", ain);
        serveCdn(manifestJson(fileEntry("System40.ini", ini), fileEntry("TestGame.ain", ain)), files);
        registerSpace(storage, new File(storage, ".online_base_staging_TestGame"));

        // A read-only storage dir lets staging downloads succeed but makes the final move fail.
        // Root ignores directory permissions, so skip there instead of failing.
        storage.setWritable(false);
        boolean readOnlyHonored = !storage.canWrite();
        storage.setWritable(true);
        assumeTrue("read-only directories are not enforced (running as root?)", readOnlyHonored);

        File[] staging = new File[1];
        try {
            installFromCdn(storage, dir -> {
                staging[0] = dir;
                storage.setWritable(false);
                return Unit.INSTANCE;
            });
            fail("Install succeeded although the final move could not happen");
        } catch (Exception e) {
            assertTrue(e instanceof java.io.IOException);
        } finally {
            storage.setWritable(true);
        }
        assertTrue(new File(staging[0], OnlineInstaller.BASE_STAGING_IDENTITY_FILE).isFile());
        assertTrue(new File(staging[0], "TestGame.ain").isFile());

        requests.clear();
        File installed = installFromCdn(storage);

        assertArrayEquals(ain, Files.readAllBytes(new File(installed, "TestGame.ain").toPath()));
        assertFalse(new File(installed, OnlineInstaller.BASE_STAGING_IDENTITY_FILE).exists());
        for (Map<String, String> req : requests) {
            assertFalse(req.get("path").startsWith("/cdn/files/"));
        }
    }
    @Test
    public void reinstallOfSameGameRefreshesExistingDirInsteadOfDuplicating() throws Exception {
        File storage = tmp.newFolder("storage").getCanonicalFile();
        byte[] ini = "CodeName = \"Rance6.ain\"\n".getBytes(StandardCharsets.UTF_8);
        byte[] ain = "ain".getBytes(StandardCharsets.UTF_8);
        byte[] dll = "dll".getBytes(StandardCharsets.UTF_8);
        Map<String, byte[]> files = new HashMap<>();
        files.put("System40.ini", ini);
        files.put("Rance6.ain", ain);
        files.put("Rance6DLL.inc", dll);
        JSONObject manifest = manifestJson(
            fileEntry("System40.ini", ini), fileEntry("Rance6.ain", ain), fileEntry("Rance6DLL.inc", dll));
        serveCdn(manifest, files);
        registerSpace(storage, new File(storage, ".online_base_staging_TestGame"));

        File first = installFromCdn(storage);
        File save = new File(first, "SaveData/slot01.asd");
        write(save, "save".getBytes(StandardCharsets.UTF_8));
        write(new File(first, "Rance6.ain"), "stale".getBytes(StandardCharsets.UTF_8));
        registerSpace(storage, first, new File(storage, ".online_base_staging_TestGame"));

        File second = installFromCdn(storage);

        assertEquals(first.getCanonicalFile(), second.getCanonicalFile());
        assertArrayEquals(ain, Files.readAllBytes(new File(second, "Rance6.ain").toPath()));
        assertTrue(save.isFile());
        String[] names = storage.list();
        Arrays.sort(names);
        for (String name : names) {
            assertFalse(name, name.startsWith(first.getName() + "-"));
            assertFalse(name, name.startsWith(".online_base_staging_"));
        }
    }

    @Test
    public void installNextToUnrelatedDirWithSameNameUsesSuffix() throws Exception {
        File storage = tmp.newFolder("storage").getCanonicalFile();
        byte[] ini = "[Game]\n".getBytes(StandardCharsets.UTF_8);
        byte[] ain = "ain".getBytes(StandardCharsets.UTF_8);
        Map<String, byte[]> files = new HashMap<>();
        files.put("System40.ini", ini);
        files.put("TestGame.ain", ain);
        serveCdn(manifestJson(fileEntry("System40.ini", ini), fileEntry("TestGame.ain", ain)), files);
        registerSpace(storage, new File(storage, ".online_base_staging_TestGame"));

        File first = installFromCdn(storage);
        registerSpace(storage, first, new File(storage, ".online_base_staging_TestGame"));
        File second = installFromCdn(storage);

        // Unrecognized games cannot be proven identical, so the existing dir is left alone.
        assertNotEquals(first.getCanonicalFile(), second.getCanonicalFile());
        assertTrue(new File(first, "TestGame.ain").isFile());
    }
}

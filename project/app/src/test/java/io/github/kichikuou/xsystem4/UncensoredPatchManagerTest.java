package io.github.kichikuou.xsystem4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class UncensoredPatchManagerTest {
    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    private static final String VALID_SHA =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    public void safeFileNameAndKeyValidation() {
        assertTrue(UncensoredPatchManager.INSTANCE.isSafeOverlayFileName("AliceHubUncensoredCG.afa"));
        assertTrue(UncensoredPatchManager.INSTANCE.isSafeOverlayFileName("AliceHubUncensoredCG.ald"));
        assertTrue(UncensoredPatchManager.INSTANCE.isSafeOverlayFileName("Aiuncensored.afa"));
        assertTrue(UncensoredPatchManager.INSTANCE.isManagedOverlayFileName("AliceHubUncensoredCG.afa"));
        assertTrue(UncensoredPatchManager.INSTANCE.isManagedOverlayFileName("AliceHubUncensoredCG.ald"));
        assertFalse(UncensoredPatchManager.INSTANCE.isManagedOverlayFileName("MyUncensoredMod.afa"));

        assertFalse(UncensoredPatchManager.INSTANCE.isSafeOverlayFileName("Rance10CG.afa"));
        assertFalse(UncensoredPatchManager.INSTANCE.isSafeOverlayFileName(".AliceHubUncensoredCG.afa"));
        assertFalse(UncensoredPatchManager.INSTANCE.isSafeOverlayFileName("../AliceHubUncensoredCG.afa"));
        assertFalse(UncensoredPatchManager.INSTANCE.isSafeOverlayFileName("sub/AliceHubUncensoredCG.afa"));
        assertFalse(UncensoredPatchManager.INSTANCE.isSafeOverlayFileName("AliceHubUncensoredCG.afa.vanilla.bak"));
        assertFalse(UncensoredPatchManager.INSTANCE.isSafeOverlayFileName("AliceHubUncensoredCG.ain"));

        assertTrue(UncensoredPatchManager.INSTANCE.isSafeKey("uncensored/AliceHubUncensoredCG.afa"));
        assertFalse(UncensoredPatchManager.INSTANCE.isSafeKey("Rance10/Rance10.ain"));
        assertFalse(UncensoredPatchManager.INSTANCE.isSafeKey("uncensored/../Rance10.ain"));
    }

    @Test
    public void parseManifestAndRejectMalformed() throws Exception {
        String json = "{"
                + "\"version\":\"1.0\","
                + "\"gameId\":\"rance10\","
                + "\"title\":\"兰斯10 无码补丁\","
                + "\"summary\":\"231 张高清无码 H-CG\","
                + "\"fileName\":\"AliceHubUncensoredCG.afa\","
                + "\"key\":\"uncensored/AliceHubUncensoredCG.afa\","
                + "\"size\":526468123,"
                + "\"sha256\":\"" + VALID_SHA + "\","
                + "\"cgCount\":231,"
                + "\"updatedAt\":\"2026-11-23\""
                + "}";
        UncensoredPatchManifest m = UncensoredPatchManager.INSTANCE.parseManifest(
                json, OnlineInstaller.RANCE10_CDN_URL
        );
        assertEquals("rance10", m.getGameId());
        assertEquals("AliceHubUncensoredCG.afa", m.getFileName());
        assertEquals(231, m.getCgCount());
        assertEquals(
                "https://alicehub.000.moe/rance10/files/uncensored/AliceHubUncensoredCG.afa",
                m.getDownloadUrl()
        );

        try {
            UncensoredPatchManager.INSTANCE.parseManifest(
                    json.replace("AliceHubUncensoredCG.afa", "Rance10.ain"),
                    OnlineInstaller.RANCE10_CDN_URL
            );
            fail("Expected IOException for unsafe fileName");
        } catch (IOException expected) {
            // ok
        }
    }

    @Test
    public void installDetectUninstallIsOrthogonalToMod() throws Exception {
        File gameDir = tmp.newFolder("Rance10");
        File baseAin = new File(gameDir, "Rance10.ain");
        File modMarker = new File(gameDir, InstalledModInfo.META_FILE_NAME);
        File uncFile = new File(gameDir, "AliceHubUncensoredCG.afa");
        Files.write(baseAin.toPath(), "AIN".getBytes(StandardCharsets.UTF_8));
        assertFalse(UncensoredPatchManager.INSTANCE.detectInstalled(gameDir));
        assertFalse(Item.Companion.detectInstalledMod(gameDir, null));

        // Unrelated user MOD file containing "uncensored" must NOT be misclassified or deleted
        File userModUnc = new File(gameDir, "MyUncensoredMod.afa");
        Files.write(userModUnc.toPath(), "USER_MOD".getBytes(StandardCharsets.UTF_8));
        assertFalse(UncensoredPatchManager.INSTANCE.detectInstalled(gameDir));

        // Installing uncensored overlay must NOT trigger MOD detection
        Files.write(uncFile.toPath(), "AFA".getBytes(StandardCharsets.UTF_8));
        InstalledUncensoredInfo.Companion.write(
                gameDir,
                new InstalledUncensoredInfo(
                        "1.0", "rance10", "AliceHubUncensoredCG.afa",
                        3L, VALID_SHA, 231, "2026-11-23", 12345L
                )
        );
        assertTrue(UncensoredPatchManager.INSTANCE.detectInstalled(gameDir));
        assertNotNull(InstalledUncensoredInfo.Companion.read(gameDir));
        assertFalse(Item.Companion.detectInstalledMod(gameDir, null));

        // Coexisting with a MOD marker + user-supplied MyUncensoredMod.afa:
        // uninstalling uncensored patch must leave base, MOD marker, AND MyUncensoredMod.afa intact
        Files.write(modMarker.toPath(), "{\"version\":\"v0.49\"}".getBytes(StandardCharsets.UTF_8));
        assertTrue(UncensoredPatchManager.INSTANCE.uninstall(gameDir));
        assertFalse(UncensoredPatchManager.INSTANCE.detectInstalled(gameDir));
        assertNull(InstalledUncensoredInfo.Companion.read(gameDir));
        assertFalse(uncFile.exists());
        assertTrue(baseAin.exists());
        assertTrue(modMarker.exists());
        assertTrue(userModUnc.exists());
    }

    @Test
    public void supportedGamesFlaggedInCatalog() {
        List<String> expected = Arrays.asList(
                GameCatalog.ID_RANCE01,
                GameCatalog.ID_RANCE02,
                GameCatalog.ID_RANCE03,
                GameCatalog.ID_RANCE5D,
                GameCatalog.ID_RANCE06,
                GameCatalog.ID_SENGOKU_RANCE,
                GameCatalog.ID_RANCE08,
                GameCatalog.ID_RANCE09,
                GameCatalog.ID_RANCE10,
                GameCatalog.ID_DOHNA_DOHNA,
                GameCatalog.ID_EVENICLE,
                GameCatalog.ID_IXSEAL,
                GameCatalog.ID_HARUKA
        );
        for (String id : expected) {
            GameDescriptor d = GameCatalog.INSTANCE.descriptorForWithResolver(
                    id, null, resId -> "res_" + resId
            );
            assertTrue("Expected supportsUncensoredPatch=true for " + id, d.getSupportsUncensoredPatch());
            assertNotNull("Expected onlineBaseCdnUrl for " + id, d.getOnlineBaseCdnUrl());
        }
        GameDescriptor unsupported = GameCatalog.INSTANCE.descriptorForWithResolver(
                GameCatalog.ID_EVENICLE2, null, resId -> "res_" + resId
        );
        assertFalse(unsupported.getSupportsUncensoredPatch());
    }
}

package io.github.kichikuou.xsystem4;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

public class Rance10InstanceSwitchTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void rance10SwitchDirectoryRowIsAlwaysVisibleWhenInstalled() {
        GameDescriptor r10 = GameCatalog.INSTANCE.descriptorForWithResolver(
                GameCatalog.ID_RANCE10, null, resId -> "Rance10");
        GameDescriptor evenicle = GameCatalog.INSTANCE.descriptorForWithResolver(
                GameCatalog.ID_EVENICLE, null, resId -> "Evenicle");

        assertFalse(GameHubActivity.Companion.shouldShowInstanceRow(r10, 0));
        assertTrue(GameHubActivity.Companion.shouldShowInstanceRow(r10, 1));
        assertTrue(GameHubActivity.Companion.shouldShowInstanceRow(r10, 2));

        assertFalse(GameHubActivity.Companion.shouldShowInstanceRow(evenicle, 0));
        assertFalse(GameHubActivity.Companion.shouldShowInstanceRow(evenicle, 1));
        assertTrue(GameHubActivity.Companion.shouldShowInstanceRow(evenicle, 2));
    }

    @Test
    public void suggestAndValidateDirectoryNamesHandleCollisionsAndIllegalInput() throws Exception {
        File root = tmp.newFolder("games");
        assertTrue(new File(root, "Rance10").mkdir());
        assertEquals("Rance10-1", Rance10InstanceManager.INSTANCE.suggestNextDirName(root, "Rance10"));

        assertTrue(new File(root, "rance10-1").mkdir());
        assertEquals("Rance10-2", Rance10InstanceManager.INSTANCE.suggestNextDirName(root, "Rance10"));

        Rance10InstanceManager.NameValidationResult validCn =
                Rance10InstanceManager.INSTANCE.validateDirName(root, "  Rance10-日不落  ");
        assertTrue(validCn.isValid());
        assertEquals("Rance10-日不落", validCn.getSanitizedName());
        assertNull(validCn.getError());

        assertEquals(Rance10InstanceManager.NameValidationError.EMPTY,
                Rance10InstanceManager.INSTANCE.validateDirName(root, "   ").getError());
        assertEquals(Rance10InstanceManager.NameValidationError.INVALID_CHARS,
                Rance10InstanceManager.INSTANCE.validateDirName(root, ".hidden").getError());
        assertEquals(Rance10InstanceManager.NameValidationError.INVALID_CHARS,
                Rance10InstanceManager.INSTANCE.validateDirName(root, "..").getError());
        assertEquals(Rance10InstanceManager.NameValidationError.INVALID_CHARS,
                Rance10InstanceManager.INSTANCE.validateDirName(root, "a/b").getError());
        assertEquals(Rance10InstanceManager.NameValidationError.INVALID_CHARS,
                Rance10InstanceManager.INSTANCE.validateDirName(root, "a:b").getError());
        assertEquals(Rance10InstanceManager.NameValidationError.ALREADY_EXISTS,
                Rance10InstanceManager.INSTANCE.validateDirName(root, "RANCE10-1").getError());
    }

    @Test
    public void cloneCleanVanillaStripsModFilesAndRestoresBackupsWithoutTouchingSource() throws Exception {
        File root = tmp.newFolder("storage");
        File src = new File(root, "Rance10");
        assertTrue(src.mkdir());

        byte[] vanillaAin = "VANILLA_AIN_BYTES".getBytes(StandardCharsets.UTF_8);
        byte[] modAin = "MODDED_AIN_BYTES_LONGER".getBytes(StandardCharsets.UTF_8);
        byte[] baseCg = "BASE_CG_DATA".getBytes(StandardCharsets.UTF_8);
        byte[] modCg = "LARGE_MOD_CG_SHOULD_BE_SKIPPED".getBytes(StandardCharsets.UTF_8);
        byte[] iniData = "GameName = \"Rance10\"\n".getBytes(StandardCharsets.UTF_8);

        Files.write(new File(src, "AliceStart.ini").toPath(), iniData);
        Files.write(new File(src, "Rance10.ain").toPath(), modAin);
        Files.write(new File(src, "Rance10.ain.vanilla.bak").toPath(), vanillaAin);
        Files.write(new File(src, "Rance10CG.afa").toPath(), baseCg);
        Files.write(new File(src, "Rance10MODCG.afa").toPath(), modCg);
        Files.write(new File(src, "AsraTest.ini").toPath(), "mod=1\n".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(src, ".mod_version.json").toPath(),
                "{\"version\":\"v0.49\",\"group\":\"日不落\"}".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(src, ".mod_installed_files.txt").toPath(),
                "Rance10MODCG.afa\nRance10.ain\n".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(src, ".mod_apply_123.tmp").toPath(), new byte[]{1, 2, 3});

        Rance10InstanceManager.CopyPlan cleanPlan =
                Rance10InstanceManager.INSTANCE.planCopy(src, true);
        assertTrue(cleanPlan.getCleanVanilla());
        assertEquals(3, cleanPlan.getFileCount());
        assertEquals(iniData.length + vanillaAin.length + baseCg.length, cleanPlan.getTotalBytes());

        File dst = Rance10InstanceManager.INSTANCE.executeCopy(
                cleanPlan, root, "Rance10-原版", 1024L * 1024L * 1024L, null,
                () -> kotlin.Unit.INSTANCE, (done, total, rel) -> kotlin.Unit.INSTANCE);

        assertEquals("Rance10-原版", dst.getName());
        assertArrayEquals(vanillaAin, Files.readAllBytes(new File(dst, "Rance10.ain").toPath()));
        assertArrayEquals(baseCg, Files.readAllBytes(new File(dst, "Rance10CG.afa").toPath()));
        assertFalse(new File(dst, "Rance10.ain.vanilla.bak").exists());
        assertFalse(new File(dst, "Rance10MODCG.afa").exists());
        assertFalse(new File(dst, "AsraTest.ini").exists());
        assertFalse(new File(dst, ".mod_version.json").exists());
        assertFalse(new File(dst, ".mod_installed_files.txt").exists());
        assertFalse(new File(dst, ".mod_apply_123.tmp").exists());
        assertFalse(Item.Companion.detectInstalledMod(dst, null));

        // Source MOD directory must remain untouched
        assertArrayEquals(modAin, Files.readAllBytes(new File(src, "Rance10.ain").toPath()));
        assertTrue(new File(src, "Rance10MODCG.afa").isFile());
        assertTrue(new File(src, "Rance10.ain.vanilla.bak").isFile());

        // Non-clean clone preserves MOD files & backups but still skips .tmp
        Rance10InstanceManager.CopyPlan fullPlan =
                Rance10InstanceManager.INSTANCE.planCopy(src, false);
        assertFalse(fullPlan.getCleanVanilla());
        File dstMod = Rance10InstanceManager.INSTANCE.executeCopy(
                fullPlan, root, "Rance10-日不落", 1024L * 1024L * 1024L, null,
                () -> kotlin.Unit.INSTANCE, (done, total, rel) -> kotlin.Unit.INSTANCE);
        assertArrayEquals(modAin, Files.readAllBytes(new File(dstMod, "Rance10.ain").toPath()));
        assertTrue(new File(dstMod, "Rance10MODCG.afa").isFile());
        assertTrue(new File(dstMod, "Rance10.ain.vanilla.bak").isFile());
        assertFalse(new File(dstMod, ".mod_apply_123.tmp").exists());
    }

    @Test
    public void cloneChecksFreeSpaceAndCleansStagingOnCancelOrStaleStartup() throws Exception {
        File root = tmp.newFolder("storage2");
        File src = new File(root, "Rance10");
        assertTrue(src.mkdir());
        Files.write(new File(src, "AliceStart.ini").toPath(), "GameName = \"Rance10\"\n".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(src, "Rance10.ain").toPath(), new byte[1024]);

        Rance10InstanceManager.CopyPlan plan = Rance10InstanceManager.INSTANCE.planCopy(src, false);

        // Insufficient space fails before creating target
        try {
            Rance10InstanceManager.INSTANCE.executeCopy(
                    plan, root, "Rance10-NoSpace", 1024L, null,
                    () -> kotlin.Unit.INSTANCE, (d, t, r) -> kotlin.Unit.INSTANCE);
            fail("Expected InstallFailureException for low space");
        } catch (Exception e) {
            assertTrue("Expected InstallFailureException, got: " + e,
                    e instanceof GameList.InstallFailureException);
            assertEquals(R.string.hub_create_instance_no_space,
                    ((GameList.InstallFailureException) e).getMsgId());
        }
        assertFalse(new File(root, "Rance10-NoSpace").exists());

        // Cancellation cleans staging dir completely
        AtomicInteger checks = new AtomicInteger(0);
        try {
            Rance10InstanceManager.INSTANCE.executeCopy(
                    plan, root, "Rance10-Cancelled", 1024L * 1024L * 1024L, null,
                    () -> {
                        if (checks.incrementAndGet() >= 2) {
                            throw new CancellationException("cancelled");
                        }
                        return kotlin.Unit.INSTANCE;
                    }, (d, t, r) -> kotlin.Unit.INSTANCE);
            fail("Expected CancellationException");
        } catch (CancellationException expected) {
        }
        assertFalse(new File(root, "Rance10-Cancelled").exists());
        for (File child : root.listFiles()) {
            assertFalse("Leftover clone staging: " + child.getName(),
                    child.getName().startsWith(Rance10InstanceManager.CLONE_STAGING_PREFIX));
        }

        // Stale staging cleanup removes only .clone_rance10_* dirs
        File stale = new File(root, Rance10InstanceManager.CLONE_STAGING_PREFIX + "deadbeef");
        assertTrue(stale.mkdir());
        Files.write(new File(stale, "partial.bin").toPath(), new byte[]{1});
        Rance10InstanceManager.INSTANCE.cleanupStaleCloneStaging(root);
        assertFalse(stale.exists());
        assertTrue(src.isDirectory());
    }
}

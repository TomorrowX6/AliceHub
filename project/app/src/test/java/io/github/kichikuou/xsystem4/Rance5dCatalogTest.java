package io.github.kichikuou.xsystem4;

import android.content.Context;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class Rance5dCatalogTest {
    @Rule public final TemporaryFolder tmp = new TemporaryFolder();

    private static void write(File root, String name) throws Exception {
        File file = new File(root, name);
        if (!file.getParentFile().isDirectory()) assertTrue(file.getParentFile().mkdirs());
        Files.write(file.toPath(), "test".getBytes(StandardCharsets.UTF_8));
    }

    @Test public void recognizesPackageAndCanonicalImportName() throws Exception {
        File root = tmp.newFolder("兰斯５Ｄ 汉化版");
        for (String name : Arrays.asList("R5D_SA.ALD", "R5D_GA.ALD", "System39.ain", ".xsys35rc")) write(root, name);
        assertTrue(GameCatalog.INSTANCE.isSystem3xGameDir(root));
        assertTrue(GameCatalog.INSTANCE.isRance5dGameDir(root));
        assertEquals(GameCatalog.ID_RANCE5D, GameCatalog.INSTANCE.classifyGameRootDir(root));
        assertEquals(GameCatalog.ID_RANCE5D, GameCatalog.INSTANCE.classifyDirectory(root, root.getName(), null, null));
        assertTrue(GameCatalog.INSTANCE.validateImportedGameDir(root, null, GameCatalog.ID_RANCE5D));
        assertEquals("Rance5D", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(Arrays.asList(root.list()), false));
        assertTrue(GameCatalog.INSTANCE.isRance5dGameFiles(Collections.singletonList("r5d_sa.ald")));
        write(root, "System40.ini");
        assertFalse(GameCatalog.INSTANCE.isRance5dGameDir(root));
        assertFalse(GameCatalog.INSTANCE.matchesTargetGame(GameCatalog.ID_RANCE5D, root));
    }

    @Test public void usesLocalizedFallbackWhenIniHasNoGameName() throws Exception {
        File root = tmp.newFolder("Unknown");
        write(root, "R5D_SA.ALD");
        Files.write(new File(root, "System39.ini").toPath(), "[System39]\n".getBytes(StandardCharsets.US_ASCII));
        Context context = RuntimeEnvironment.getApplication();
        Item item = Item.Companion.fromDirectory(root, tmp.getRoot(), context);
        assertNull(item.getError());
        assertEquals(context.getString(R.string.game_rance5d_title), item.getName());
    }

    @Test public void recognizesBoundedAliases() {
        for (String name : Arrays.asList("Rance5D", "Rance 5D", "ランス５Ｄ", "ランス5D", "兰斯5D", "兰斯５Ｄ", "Rance 5D: The Lonely Girl")) {
            assertTrue(name, GameCatalog.INSTANCE.isRance5dAlias(name));
            assertEquals(name, GameCatalog.ID_RANCE5D, GameCatalog.INSTANCE.classifyDirectory(null, name, null, null));
            assertEquals(name, GameCatalog.ID_RANCE5D, GameCatalog.INSTANCE.classifyDirectory(null, "Unknown", null, name));
        }
    }

    @Test public void rejectsSequelsOtherTitlesEmptyAndInvalidRoots() throws Exception {
        for (String name : Arrays.asList("", "Rance4.1", "Rance 4.2", "Rance４．１", "Rance41", "Rance42", "Rance40", "Rance VI", "Rance5", "Rance5Da", "Rance15D", "Rance6", "ランス4.1", "兰斯IV.2", "Rance4.1 教团的遗产", "RanceIVa")) {
            assertFalse(name, GameCatalog.INSTANCE.isRance5dAlias(name));
            assertNotEquals(name, GameCatalog.ID_RANCE5D, GameCatalog.INSTANCE.classifyDirectory(null, name, null, null));
        }
        assertFalse(GameCatalog.INSTANCE.isRance5dGameFiles(Collections.emptyList()));
        assertFalse(GameCatalog.INSTANCE.isRance5dGameDir(null));
        File root = tmp.newFolder("Rance5D");
        assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(root, null, GameCatalog.ID_RANCE5D));
        for (String marker : Arrays.asList("RANCE41SA.ALD", "RANCE42SA.ALD", "Rance06.ain")) {
            write(root, marker);
            assertFalse(GameCatalog.INSTANCE.isRance5dGameDir(root));
            assertFalse(GameCatalog.INSTANCE.matchesTargetGame(GameCatalog.ID_RANCE5D, root));
            assertNotEquals(GameCatalog.ID_RANCE5D, GameCatalog.INSTANCE.classifyDirectory(root, root.getName(), null, "兰斯5D"));
            assertTrue(new File(root, marker).delete());
        }
        assertTrue(new File(root, "R5D_SA.ALD").mkdir());
        assertFalse(GameCatalog.INSTANCE.isRance5dGameDir(root));
    }

    @Test public void registersOnlineDescriptorAndSeriesOrder() {
        GameDescriptor descriptor = GameCatalog.INSTANCE.descriptorForWithResolver(GameCatalog.ID_RANCE5D, null, id -> "title");
        assertEquals("rance5d", descriptor.getId());
        assertEquals("Rance5D", descriptor.getDefaultDirName());
        assertNull(descriptor.getExpectedAinName());
        assertTrue(descriptor.getSupportsOnlineBaseInstall());
        assertEquals(OnlineInstaller.RANCE5D_CDN_URL, descriptor.getOnlineBaseCdnUrl());
        assertEquals(AppSettings.DEFAULT_RANCE5D_BYTES, AppSettings.defaultBaseGameSizeForUrl(OnlineInstaller.RANCE5D_CDN_URL));
        assertFalse(descriptor.getSupportsOnlineMod());
        assertFalse(descriptor.getSupportsLocalMod());
        List<String> order = GameCatalog.builtInSelectOrder();
        assertEquals(order.indexOf(GameCatalog.ID_RANCE42) + 1, order.indexOf(GameCatalog.ID_RANCE5D));
        assertEquals(order.indexOf(GameCatalog.ID_RANCE5D) + 1, order.indexOf(GameCatalog.ID_RANCE06));
        assertTrue(order.indexOf(GameCatalog.ID_RANCE5D) < order.indexOf(GameCatalog.ID_RANCE06));
        assertEquals("2002-10-25", GameSortMetadata.INSTANCE.forGame(GameCatalog.ID_RANCE5D).getReleaseDate());
        assertEquals(Double.valueOf(57.5), GameSortMetadata.INSTANCE.forGame(GameCatalog.ID_RANCE5D).getRating());
    }

}

package io.github.kichikuou.xsystem4;

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

public class Rance04CatalogTest {
    @Rule public final TemporaryFolder tmp = new TemporaryFolder();

    private static void write(File root, String name) throws Exception {
        File file = new File(root, name);
        if (!file.getParentFile().isDirectory()) assertTrue(file.getParentFile().mkdirs());
        Files.write(file.toPath(), "test".getBytes(StandardCharsets.UTF_8));
    }

    @Test public void recognizesPackageAndCanonicalImportName() throws Exception {
        File root = tmp.newFolder("【卡拉保护协会】兰斯ＩＶ　－教团的遗产－");
        for (String name : Arrays.asList("RANCE4SA.ALD", "RANCE4GA.ALD", "System39.ain", ".xsys35rc")) write(root, name);
        assertTrue(GameCatalog.INSTANCE.isSystem3xGameDir(root));
        assertTrue(GameCatalog.INSTANCE.isRance04GameDir(root));
        assertEquals(GameCatalog.ID_RANCE04, GameCatalog.INSTANCE.classifyGameRootDir(root));
        assertEquals(GameCatalog.ID_RANCE04, GameCatalog.INSTANCE.classifyDirectory(root, root.getName(), null, null));
        assertTrue(GameCatalog.INSTANCE.validateImportedGameDir(root, null, GameCatalog.ID_RANCE04));
        assertEquals("Rance04", NativeGameImport.INSTANCE.resolveImportBaseNameFromFileNames(Arrays.asList(root.list()), false));
        assertTrue(GameCatalog.INSTANCE.isRance04GameFiles(Collections.singletonList("rance4sa.ald")));
    }

    @Test public void recognizesBoundedAliases() {
        for (String name : Arrays.asList("Rance4", "Rance04", "Rance IV", "RanceⅣ", "兰斯IV", "兰斯ＩＶ", "兰斯4", "兰斯４", "ランス4", "ランスIV", "教团的遗产", "教団の遺産", "【卡拉保护协会】兰斯ＩＶ　－教团的遗产－")) {
            assertTrue(name, GameCatalog.INSTANCE.isRance04Alias(name));
            assertEquals(name, GameCatalog.ID_RANCE04, GameCatalog.INSTANCE.classifyDirectory(null, name, null, null));
            assertEquals(name, GameCatalog.ID_RANCE04, GameCatalog.INSTANCE.classifyDirectory(null, "Unknown", null, name));
        }
    }

    @Test public void rejectsSequelsOtherTitlesEmptyAndInvalidRoots() throws Exception {
        for (String name : Arrays.asList("", "Rance4.1", "Rance 4.2", "Rance４．１", "Rance41", "Rance42", "Rance40", "Rance VI", "Rance5D", "Rance6", "ランス4.1", "兰斯IV.2", "Rance4.1 教团的遗产", "RanceIVa")) {
            assertFalse(name, GameCatalog.INSTANCE.isRance04Alias(name));
            assertNotEquals(name, GameCatalog.ID_RANCE04, GameCatalog.INSTANCE.classifyDirectory(null, name, null, null));
        }
        assertFalse(GameCatalog.INSTANCE.isRance04GameFiles(Collections.emptyList()));
        assertFalse(GameCatalog.INSTANCE.isRance04GameDir(null));
        File root = tmp.newFolder("Rance4");
        assertFalse(GameCatalog.INSTANCE.validateImportedGameDir(root, null, GameCatalog.ID_RANCE04));
        for (String marker : Arrays.asList("RANCE41SA.ALD", "RANCE42SA.ALD", "Rance06.ain")) {
            write(root, marker);
            assertFalse(GameCatalog.INSTANCE.isRance04GameDir(root));
            assertFalse(GameCatalog.INSTANCE.matchesTargetGame(GameCatalog.ID_RANCE04, root));
            assertNotEquals(GameCatalog.ID_RANCE04, GameCatalog.INSTANCE.classifyDirectory(root, root.getName(), null, "教团的遗产"));
            assertTrue(new File(root, marker).delete());
        }
        assertTrue(new File(root, "RANCE4SA.ALD").mkdir());
        assertFalse(GameCatalog.INSTANCE.isRance04GameDir(root));
    }

    @Test public void registersLocalOnlyDescriptorAndSeriesOrder() {
        GameDescriptor descriptor = GameCatalog.INSTANCE.descriptorForWithResolver(GameCatalog.ID_RANCE04, null, id -> "title");
        assertEquals("rance04", descriptor.getId());
        assertEquals("Rance04", descriptor.getDefaultDirName());
        assertNull(descriptor.getExpectedAinName());
        assertFalse(descriptor.getSupportsOnlineBaseInstall());
        assertNull(descriptor.getOnlineBaseCdnUrl());
        assertFalse(descriptor.getSupportsOnlineMod());
        assertFalse(descriptor.getSupportsLocalMod());
        List<String> order = GameCatalog.builtInSelectOrder();
        assertTrue(order.indexOf(GameCatalog.ID_RANCE04) > order.indexOf(GameCatalog.ID_RANCE03));
        assertTrue(order.indexOf(GameCatalog.ID_RANCE04) < order.indexOf(GameCatalog.ID_RANCE06));
        assertEquals("1993-12-11", GameSortMetadata.INSTANCE.forGame(GameCatalog.ID_RANCE04).getReleaseDate());
    }

    @Test public void prunesOnlyWindowsExecutableAndKeepsRuntimePayload() throws Exception {
        File root = tmp.newFolder("Game");
        List<String> payload = Arrays.asList("RANCE4SA.ALD", "RANCE4GA.ALD", "RANCE4WA.ALD", "System39.ain", ".xsys35rc", "SourceHanSansCN-Normal.otf", "_inmm.ini", "bgm/02.mp3", "bgm/43.mp3");
        for (String name : payload) {
            write(root, name);
            assertFalse(name, NativeGameImport.INSTANCE.skip(name));
            assertFalse(name, GameListKt.isRance04BloatRelPath(name));
        }
        write(root, "RANCE4CN.EXE");
        assertTrue(GameListKt.isRance04BloatRelPath("folder/rance4cn.exe"));
        assertFalse(GameListKt.isRance04BloatRelPath("RANCE41CN.EXE"));
        GameListKt.pruneRedundantBaseBloat(root);
        assertFalse(new File(root, "RANCE4CN.EXE").exists());
        for (String name : payload) assertTrue(name, new File(root, name).isFile());
        File other = tmp.newFolder("Rance4.1");
        write(other, "RANCE41SA.ALD");
        write(other, "RANCE4CN.EXE");
        GameListKt.pruneRedundantBaseBloat(other);
        assertTrue(new File(other, "RANCE4CN.EXE").isFile());
    }
}

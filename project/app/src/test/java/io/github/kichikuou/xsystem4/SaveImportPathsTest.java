package io.github.kichikuou.xsystem4;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SaveImportPathsTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private static final Charset CP932 = Charset.forName("windows-31j");
    private static final Charset GBK = Charset.forName("GBK");

    private static void touch(File dir, String name) throws IOException {
        assertTrue(new File(dir, name).createNewFile());
    }

    private static File sourceRoot(SaveImportPaths paths, File root) {
        return sourceRoot(paths, root, false);
    }

    private static File sourceRoot(SaveImportPaths paths, File root, boolean fromArchive) {
        return paths.sourceRoot(root, dir -> {
            File[] files = dir.listFiles();
            return files == null ? Collections.emptyList() : Arrays.asList(files);
        }, File::getName, File::isDirectory, fromArchive);
    }

    @Test public void windowsSlotsMatchTheScenarioArchiveForBothTsumamiguiGames() throws IOException {
        for (String prefix : new String[]{"妻みぐい", "妻みぐい２"}) {
            File game = temporary.newFolder(prefix);
            touch(game, "System39.ain");
            touch(game, prefix + "SA.ALD");
            SaveImportPaths paths = new SaveImportPaths(game);
            String gbkSpelling = new String(prefix.getBytes(CP932), GBK);
            String cp932Spelling = new String(prefix.getBytes(GBK), CP932);

            assertEquals(prefix + "sa.asd", paths.nameForImport(prefix + "SA.ASD", true));
            assertEquals(prefix + "sb.asd", paths.nameForImport(gbkSpelling + "SB.ASD", true));
            assertEquals(prefix + "sz.asd", paths.nameForImport(cp932Spelling + "SZ.ASD", true));
            assertEquals(prefix + ".msgskip", paths.nameForImport(gbkSpelling + ".MSGSKIP", true));
            assertEquals("另一个游戏SA.ASD", paths.nameForImport("另一个游戏SA.ASD", true));
            assertEquals(prefix + "SA.ASD", paths.nameForImport(prefix + "SA.ASD", false));
        }
    }

    @Test public void translatedScenarioPrefixIsPreferredAndSurvivesRefresh() throws IOException {
        File game = temporary.newFolder("translated");
        touch(game, "System39.ain");
        touch(game, "妻みぐいSA.ALD");
        String prefix = new String("妻みぐい".getBytes(CP932), GBK);
        touch(game, prefix + "SA_chs.ald");
        SaveImportPaths paths = new SaveImportPaths(game);

        assertEquals(prefix + "sa.asd", paths.nameForImport("妻みぐいSA.ASD", true));
        assertEquals(prefix + "sa.asd", paths.normalizedName(prefix + "sa.asd", true));
    }

    @Test public void findsMamanPcSaveDataInsideAWrapper() throws IOException {
        File game = temporary.newFolder("Maman");
        touch(game, "Maman.ain");
        Files.write(new File(game, "AliceStart.ini").toPath(),
                "SaveFolder = \"SaveData\"\n".getBytes(StandardCharsets.US_ASCII));
        File root = temporary.newFolder("import");
        touch(root, "readme.txt");
        File wrapper = new File(root, "偼乣偲傆傞儅儅儞");
        File saveData = new File(wrapper, "SaveData");
        assertTrue(saveData.mkdirs());
        touch(saveData, "01.asd");

        SaveImportPaths paths = new SaveImportPaths(game);
        assertEquals(saveData, sourceRoot(paths, root));
        assertEquals(saveData, sourceRoot(paths, root, true));
        assertEquals("01.asd", paths.nameForImport("01.asd", true));
        touch(root, "02.rsm");
        assertEquals(root, sourceRoot(paths, root));
    }

    @Test public void archiveStillUnwrapsSingleFoldersWithOtherSaveExtensions() throws IOException {
        File root = temporary.newFolder("archive");
        File wrapper = new File(root, "backup");
        assertTrue(wrapper.mkdir());
        touch(wrapper, "KeyFile.dat");
        assertEquals(wrapper, sourceRoot(new SaveImportPaths(null), root, true));
    }

    @Test public void findsLocaleSwitchedConfiguredFolderWithoutPickingUnrelatedDirectories() throws IOException {
        File game = temporary.newFolder("custom");
        Files.write(new File(game, "System40.ini").toPath(),
                "SaveFolder = \"セーブ\"\n".getBytes(CP932));
        File root = temporary.newFolder("folders");
        assertTrue(new File(root, "Data").mkdir());
        File saveData = new File(root, new String("セーブ".getBytes(CP932), GBK));
        assertTrue(saveData.mkdir());
        touch(saveData, "GlobalSave.asd");

        assertEquals(saveData, sourceRoot(new SaveImportPaths(game), root));
        assertEquals(root, sourceRoot(new SaveImportPaths(null), root));
    }
}

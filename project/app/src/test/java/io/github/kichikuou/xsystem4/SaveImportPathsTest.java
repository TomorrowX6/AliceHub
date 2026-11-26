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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SaveImportPathsTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private static final Charset CP932 = Charset.forName("windows-31j");
    private static final Charset GBK = Charset.forName("GBK");

    private static void touch(File dir, String name) throws IOException {
        assertTrue(new File(dir, name).createNewFile());
    }

    private static void write(File file, String value) throws IOException {
        Files.write(file.toPath(), value.getBytes(StandardCharsets.US_ASCII));
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.US_ASCII);
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

    @Test public void migrationPreservesConflictingAliasWithoutRevivingDeletedSlot() throws IOException {
        File game = temporary.newFolder("Tsumamigui");
        touch(game, "System39.ain");
        touch(game, "妻みぐいSA.ALD");
        File saves = temporary.newFolder("saves");
        File canonical = new File(saves, "妻みぐいsa.asd");
        File alias = new File(saves, "妻みぐいSA.ASD");
        File existingBackup = new File(saves, canonical.getName() + ".import-conflict.bak");
        File backup = new File(saves, canonical.getName() + ".1.import-conflict.bak");
        write(canonical, "new");
        write(alias, "old");
        write(existingBackup, "older");
        File secondSlot = new File(saves, "妻みぐいsb.asd");
        write(secondSlot, "second");

        SaveImportPaths paths = new SaveImportPaths(game);
        paths.migrateExistingSaves(saves);
        assertEquals("new", read(canonical));
        assertFalse(alias.exists());
        assertEquals("older", read(existingBackup));
        assertEquals("old", read(backup));

        assertTrue(canonical.delete());
        File renamed = new File(saves, "妻みぐいSB.ASD");
        assertTrue(secondSlot.renameTo(renamed));
        paths.migrateExistingSaves(saves);
        assertFalse(canonical.exists());
        assertEquals("old", read(backup));
        assertFalse(secondSlot.exists());
        assertEquals("second", read(renamed));
    }

    @Test public void importKeepsBothSpellingsOfOneSlotWithoutOverwritingTheFirstCopy() throws IOException {
        File game = temporary.newFolder("game");
        touch(game, "System39.ain");
        touch(game, "妻みぐいSA.ALD");
        File saves = temporary.newFolder("saves");
        SaveImportPaths paths = new SaveImportPaths(game);
        SaveImportPaths.ImportWrites writes = new SaveImportPaths.ImportWrites();
        File canonical = new File(saves, paths.nameForImport("妻みぐいsa.asd", true));
        File aliasDestination = new File(saves, paths.nameForImport("嵢傒偖偄SA.ASD", true));
        assertEquals(canonical, aliasDestination);
        write(canonical, "previous");
        File first = temporary.newFile();
        File second = temporary.newFile();
        write(first, "first");
        write(second, "second");
        writes.write(first, canonical);
        writes.write(second, aliasDestination);
        assertEquals("first", read(canonical));
        assertEquals("second", read(new File(saves, canonical.getName() + ".import-conflict.bak")));
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
        assertEquals(saveData, sourceRoot(paths, root, true));
    }

    @Test public void archivePrefersExplicitSaveDataBesideLooseSlots() throws IOException {
        File root = temporary.newFolder("mixed");
        File saveData = new File(root, "SaveData");
        assertTrue(saveData.mkdir());
        touch(saveData, "01.asd");
        touch(root, "old.asd");

        SaveImportPaths paths = new SaveImportPaths(null);
        assertEquals(root, sourceRoot(paths, root));
        assertEquals(saveData, sourceRoot(paths, root, true));
    }

    @Test public void archiveDoesNotPreferALogOnlySaveDataFolderOverLooseSlots() throws IOException {
        File root = temporary.newFolder("archive");
        File wrapper = new File(root, "backup");
        File saveData = new File(wrapper, "SaveData");
        assertTrue(saveData.mkdirs());
        touch(saveData, "session.log");
        File looseSlot = new File(wrapper, "01.asd");
        assertTrue(looseSlot.createNewFile());

        SaveImportPaths paths = new SaveImportPaths(null);
        assertEquals(wrapper, sourceRoot(paths, root, true));
        assertTrue(looseSlot.delete());
        touch(saveData, "KeyFile.dat");
        assertEquals(saveData, sourceRoot(paths, root, true));
    }

    @Test public void usesTheLocalizedLaunchIniAndTheWinningScenarioArchive() throws IOException {
        File system4 = temporary.newFolder("Haruka");
        touch(system4, "haru_c.ain");
        write(new File(system4, "System40.ini"), "SaveFolder = \"Untranslated\"\n");
        write(new File(system4, "AliceStart2.ini"), "SaveFolder = \"Translated\"\n");
        File source = temporary.newFolder("source");
        assertTrue(new File(source, "Untranslated").mkdir());
        File translated = new File(source, "Translated");
        assertTrue(translated.mkdir());
        assertEquals(translated, sourceRoot(new SaveImportPaths(system4), source));

        File system35 = temporary.newFolder("System39");
        touch(system35, "System39.ain");
        touch(system35, "OriginalSA.ALD");
        touch(system35, "TranslationOneSA_chs.ald");
        touch(system35, "TranslationTwoSB_chs.ald");
        String winner = Arrays.stream(system35.list()).filter(name -> name.endsWith("_chs.ald"))
                .reduce((first, last) -> last).get().split("S[A-Z]_chs")[0];
        assertEquals(winner + "sa.asd", new SaveImportPaths(system35).nameForImport(winner + "SA.ASD", true));
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
        touch(root, "old.sav");
        assertEquals(root, sourceRoot(new SaveImportPaths(game), root));
        assertEquals(saveData, sourceRoot(new SaveImportPaths(game), root, true));
    }
}

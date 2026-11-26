package io.github.kichikuou.xsystem4;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
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

    private File importZip(File archive) throws IOException {
        File extracted = temporary.newFolder();
        try (ZipFile zip = new ZipFile(archive)) {
            for (ZipEntry entry : Collections.list(zip.entries())) {
                File output = new File(extracted, entry.getName());
                Files.createDirectories(output.getParentFile().toPath());
                try (InputStream input = zip.getInputStream(entry)) {
                    Files.copy(input, output.toPath());
                }
            }
        }
        File target = temporary.newFolder();
        try (SaveImportPaths.ImportWrites writes = new SaveImportPaths.ImportWrites()) {
            assertEquals(1, new SaveImportPaths(null).importExtractedArchive(extracted, target, writes));
            assertEquals(0, writes.finishFallbacks());
        }
        return target;
    }

    @Test public void exportedZipPreservesALoneSaveDataSubdirectory() throws IOException {
        File source = temporary.newFile("Collection.asd");
        write(source, "collection progress");
        File archive = temporary.newFile("export.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive.toPath()))) {
            SaveFileEntry entry = new SaveFileEntry(source, "SaveData/Collection.asd",
                    source.length(), source.lastModified(), "collection");
            SaveZipLayout.INSTANCE.write(zip, Collections.singletonList(entry), (file, output) -> {
                try {
                    Files.copy(file.toPath(), output);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                return false;
            });
        }

        File target = importZip(archive);
        assertEquals("collection progress", read(new File(target, "SaveData/Collection.asd")));
        assertEquals(Collections.singletonList("SaveData"), Arrays.asList(target.list()));
    }

    @Test public void unmarkedZipStillUnwrapsRedundantSaveDataDirectories() throws IOException {
        File archive = temporary.newFile("external.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive.toPath()))) {
            zip.putNextEntry(new ZipEntry("SaveData/SaveData/Collection.asd"));
            zip.write("external progress".getBytes(StandardCharsets.US_ASCII));
            zip.closeEntry();
        }

        File target = importZip(archive);
        assertEquals("external progress", read(new File(target, "Collection.asd")));
        assertEquals(Collections.singletonList("Collection.asd"), Arrays.asList(target.list()));
    }

    @Test public void folderRestoresKeepGenuineSaveDataPaths() throws IOException {
        File export = temporary.newFolder("exported-folder");
        File nested = new File(export, "SaveData");
        assertTrue(nested.mkdir());
        write(new File(nested, "01.asd"), "progress");
        SaveImportPaths paths = new SaveImportPaths(null);
        assertEquals(nested, sourceRoot(paths, export));
        touch(export, "GlobalSave.asd");
        assertEquals(export, sourceRoot(paths, export));
        assertTrue(new File(export, "GlobalSave.asd").delete());
        touch(export, ".alicehub-save-layout-v1");
        assertEquals(export, sourceRoot(paths, export));
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
        File garbled = new File(saves, "嵢傒偖偄sc.asd");
        write(garbled, "third");

        SaveImportPaths paths = new SaveImportPaths(game);
        paths.migrateExistingSaves(saves);
        assertEquals("new", read(canonical));
        assertFalse(alias.exists());
        assertEquals("older", read(existingBackup));
        assertEquals("old", read(backup));
        assertFalse(garbled.exists());
        assertEquals("third", read(new File(saves, "妻みぐいsc.asd")));

        assertTrue(canonical.delete());
        File renamed = new File(saves, "妻みぐいSB.ASD");
        assertTrue(secondSlot.renameTo(renamed));
        paths.migrateExistingSaves(saves);
        assertFalse(canonical.exists());
        assertEquals("old", read(backup));
        assertFalse(secondSlot.exists());
        assertEquals("second", read(renamed));
    }

    @Test public void scriptManagedSystem39SavesKeepTheirEngineSpelling() throws IOException {
        File game = temporary.newFolder("Tsumamigui");
        touch(game, "System39.ain");
        touch(game, "妻みぐいSA.ALD");
        SaveImportPaths paths = new SaveImportPaths(game);
        File archive = temporary.newFolder("extracted");
        File saveData = new File(archive, "SaveData");
        assertTrue(saveData.mkdir());
        File target = temporary.newFolder();
        String engineName = "柤慜.ASD";
        String decodedPath = paths.decodeArchivePath(("SaveData/" + engineName).getBytes(StandardCharsets.UTF_8));
        assertEquals("SaveData/" + engineName, decodedPath);
        write(new File(archive, decodedPath), "script records");
        assertEquals(engineName, paths.nameForImport(engineName, true));
        assertEquals("名前.ASD", paths.nameForImport("名前.ASD", true));
        try (SaveImportPaths.ImportWrites writes = new SaveImportPaths.ImportWrites()) {
            assertEquals(1, paths.importExtractedArchive(archive, target, writes));
            assertEquals(0, writes.finishFallbacks());
        }
        paths.migrateExistingSaves(target);
        assertEquals("script records", read(new File(target, engineName)));
        assertFalse(new File(target, "名前.ASD").exists());
    }

    @Test public void nativeSystem35SaveNamesWinOverImportedAliases() throws IOException {
        File game = temporary.newFolder("System39");
        touch(game, "System39.ain");
        touch(game, "OriginalSA.ald");
        SaveImportPaths paths = new SaveImportPaths(game);
        File source = temporary.newFolder("extracted");
        write(new File(source, "墹巕_M1.asd"), "map data");
        write(new File(source, "volume.sav"), "imported volume");
        File target = temporary.newFolder();
        write(new File(target, "Volume.sav"), "old volume");
        try (SaveImportPaths.ImportWrites writes = new SaveImportPaths.ImportWrites()) {
            assertEquals(2, paths.importExtractedArchive(source, target, writes));
            assertEquals(0, writes.finishFallbacks());
        }
        assertEquals("map data", read(new File(target, "王子_M1.asd")));
        assertEquals("imported volume", read(new File(target, "Volume.sav")));
        assertFalse(new File(target, "墹巕_M1.asd").exists());
        assertFalse(new File(target, "volume.sav").exists());
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

    @Test public void archivePrefersVsfAndKeyfileInsideSaveDataOverLooseSlots() throws IOException {
        for (String name : new String[]{"EscalayerReboot_87.vsf", "KeyFile.dat"}) {
            File root = temporary.newFolder(name);
            File saveData = new File(root, "SaveData");
            assertTrue(saveData.mkdir());
            touch(root, "old.asd");
            touch(saveData, name);
            SaveImportPaths paths = new SaveImportPaths(null);
            assertEquals(saveData, sourceRoot(paths, root, true));
            assertEquals(root, sourceRoot(paths, root));
        }
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

    @Test public void looseArchiveConfigDoesNotHideWrappedSaveSlots() throws IOException {
        for (String name : new String[]{"3DConfig.VSF", "KeyFile.dat"}) {
            File root = temporary.newFolder(name);
            File wrapper = new File(root, "backup");
            assertTrue(wrapper.mkdir());
            write(new File(root, name), "outer");
            write(new File(wrapper, "CommonSystemData.sav"), "progress");
            File target = temporary.newFolder();
            SaveImportPaths paths = new SaveImportPaths(null);
            assertEquals(wrapper, sourceRoot(paths, root, true));
            try (SaveImportPaths.ImportWrites writes = new SaveImportPaths.ImportWrites()) {
                assertEquals(1, paths.importExtractedArchive(root, target, writes));
                assertEquals(1, writes.finishFallbacks());
            }
            assertEquals("progress", read(new File(target, "CommonSystemData.sav")));
            assertEquals("outer", read(new File(target, name)));
        }
    }

    @Test public void wrappedArchiveMarkerWinsOverAnOuterCopy() throws IOException {
        File root = temporary.newFolder("archive");
        File wrapper = new File(root, "backup");
        assertTrue(wrapper.mkdir());
        write(new File(root, "EscalayerReboot_87.vsf"), "outer");
        write(new File(wrapper, "EscalayerReboot_87.VSF"), "wrapped");
        touch(wrapper, "01.asd");
        File target = temporary.newFolder();

        SaveImportPaths paths = new SaveImportPaths(null);
        try (SaveImportPaths.ImportWrites writes = new SaveImportPaths.ImportWrites()) {
            assertEquals(2, paths.importExtractedArchive(root, target, writes));
            assertEquals(1, writes.finishFallbacks());
        }
        assertEquals("wrapped", read(new File(target, "EscalayerReboot_87.VSF")));
        assertFalse(new File(target, "EscalayerReboot_87.vsf").exists());
        assertEquals("outer", read(new File(target, "EscalayerReboot_87.VSF.import-conflict.bak")));
    }

    @Test public void explicitSaveDataDoesNotDiscardNetworkSettingsOutsideTheFolder() throws IOException {
        File root = temporary.newFolder("archive");
        File wrapper = new File(root, "SaveData");
        File saveData = new File(wrapper, "SaveData");
        assertTrue(saveData.mkdirs());
        write(new File(root, "NetUserID.vsf"), "outer profile");
        write(new File(wrapper, "NetUserID.vsf"), "game profile");
        write(new File(saveData, "AFCommon.asd"), "progress");
        File target = temporary.newFolder();

        SaveImportPaths paths = new SaveImportPaths(null);
        assertEquals(saveData, sourceRoot(paths, root));
        assertEquals(saveData, sourceRoot(paths, root, true));
        try (SaveImportPaths.ImportWrites writes = new SaveImportPaths.ImportWrites()) {
            assertEquals(1, paths.importExtractedArchive(root, target, writes));
            assertEquals(2, writes.finishFallbacks());
        }
        assertEquals("progress", read(new File(target, "AFCommon.asd")));
        assertEquals("game profile", read(new File(target, "NetUserID.vsf")));
        assertEquals("outer profile", read(new File(target, "NetUserID.vsf.import-conflict.bak")));
    }

    @Test public void selectedMarkerWinsOverArchiveFallbackInEitherOrder() throws IOException {
        File root = temporary.newFolder("archive");
        File wrapper = new File(root, "backup");
        assertTrue(wrapper.mkdir());
        write(new File(root, "3DConfig.vsf"), "outer");
        touch(wrapper, "01.asd");
        SaveImportPaths paths = new SaveImportPaths(null);
        for (boolean archiveFirst : new boolean[]{false, true}) {
            File target = temporary.newFolder();
            File staged = temporary.newFile();
            write(staged, "selected");
            File selected = new File(target, "3DConfig.VSF");
            try (SaveImportPaths.ImportWrites writes = new SaveImportPaths.ImportWrites()) {
                if (!archiveFirst) writes.write(staged, selected);
                assertEquals(1, paths.importExtractedArchive(root, target, writes));
                if (archiveFirst) writes.write(staged, selected);
                assertEquals(1, writes.finishFallbacks());
            }
            assertEquals("selected", read(selected));
            assertFalse(new File(target, "3DConfig.vsf").exists());
            assertEquals("outer", read(new File(target, "3DConfig.VSF.import-conflict.bak")));
        }
    }

    @Test public void nestedSaveDataCarriesOnlyOuterStateAndPrefersInnerCopies() throws IOException {
        File root = temporary.newFolder("archive");
        File wrapper = new File(root, "SaveData");
        File saveData = new File(wrapper, "SaveData");
        assertTrue(saveData.mkdirs());
        write(new File(root, "Collection.sav"), "outer collection");
        write(new File(wrapper, "Extra.sav"), "outer extra");
        write(new File(wrapper, "CommonSystemData.sav"), "outer common");
        write(new File(wrapper, "AFConfig.asd"), "outer config");
        write(new File(wrapper, "Volume.sav"), "outer volume");
        touch(root, "SaveData02.sav");
        touch(root, "03.asd");
        touch(root, "readme.txt");
        touch(wrapper, "System40.ini");
        write(new File(saveData, "collection.SAV"), "inner collection");
        write(new File(saveData, "01.asd"), "inner slot");

        SaveImportPaths paths = new SaveImportPaths(null);
        assertEquals(saveData, sourceRoot(paths, root, true));
        assertEquals(wrapper, sourceRoot(paths, wrapper));
        File target = temporary.newFolder();
        try (SaveImportPaths.ImportWrites writes = new SaveImportPaths.ImportWrites()) {
            assertEquals(2, paths.importExtractedArchive(root, target, writes));
            assertEquals(5, writes.finishFallbacks());
        }
        assertEquals("inner slot", read(new File(target, "01.asd")));
        assertEquals("inner collection", read(new File(target, "collection.SAV")));
        assertEquals("outer collection", read(new File(target, "collection.SAV.import-conflict.bak")));
        assertEquals("outer extra", read(new File(target, "Extra.sav")));
        assertEquals("outer common", read(new File(target, "CommonSystemData.sav")));
        assertEquals("outer config", read(new File(target, "AFConfig.asd")));
        assertEquals("outer volume", read(new File(target, "Volume.sav")));
        assertFalse(new File(target, "Collection.sav").exists());
        assertFalse(new File(target, "SaveData02.sav").exists());
        assertFalse(new File(target, "03.asd").exists());
        assertFalse(new File(target, "readme.txt").exists());
        assertFalse(new File(target, "System40.ini").exists());
    }

    @Test public void wrappedProgressReplacesAnExistingCaseVariant() throws IOException {
        File root = temporary.newFolder("archive");
        File wrapper = new File(root, "backup");
        assertTrue(wrapper.mkdir());
        String activeName = "EscalayerReboot_87.vsf";
        String wrappedName = "EscalayerReboot_87.VSF";
        write(new File(root, activeName), "outer");
        write(new File(wrapper, wrappedName), "wrapped");
        touch(wrapper, "01.asd");
        File target = temporary.newFolder();
        File active = new File(target, activeName);
        write(active, "previous");

        SaveImportPaths paths = new SaveImportPaths(null);
        try (SaveImportPaths.ImportWrites writes = new SaveImportPaths.ImportWrites()) {
            assertEquals(2, paths.importExtractedArchive(root, target, writes));
            assertEquals(1, writes.finishFallbacks());
        }
        assertEquals("wrapped", read(new File(target, wrappedName)));
        assertFalse(active.exists());
        assertEquals("previous", read(new File(target, wrappedName + ".import-conflict.bak")));
        assertEquals("outer", read(new File(target, wrappedName + ".1.import-conflict.bak")));
    }

    @Test public void archivePreservesDistinctPrimaryMarkerSpellings() throws IOException {
        SaveImportPaths paths = new SaveImportPaths(null);
        for (String first : new String[]{"slot0.VSF", "slot0.vsf"}) {
            File root = temporary.newFolder();
            File target = temporary.newFolder();
            String second = first.equals("slot0.VSF") ? "slot0.vsf" : "slot0.VSF";
            write(new File(root, first), "first");
            write(new File(root, second), "second");
            try (SaveImportPaths.ImportWrites writes = new SaveImportPaths.ImportWrites()) {
                assertEquals(2, paths.importExtractedArchive(root, target, writes));
                assertEquals(0, writes.finishFallbacks());
            }
            assertEquals("first", read(new File(target, first)));
            assertEquals("second", read(new File(target, second)));
        }
    }

    @Test public void importedBackupCannotOverwriteAnAliasPreservedInTheSameBatch() throws IOException {
        File target = temporary.newFolder();
        File active = new File(target, "slot0.vsf");
        File backup = new File(target, "slot0.vsf.import-conflict.bak");
        write(new File(target, "slot0.VSF"), "previous");
        File staged = temporary.newFile();
        File stagedBackup = temporary.newFile();
        write(staged, "progress");
        write(stagedBackup, "archive backup");
        try (SaveImportPaths.ImportWrites writes = new SaveImportPaths.ImportWrites()) {
            writes.write(staged, active);
            writes.write(stagedBackup, backup);
        }
        assertEquals("progress", read(active));
        assertEquals("previous", read(backup));
        assertEquals("archive backup", read(new File(target, backup.getName() + ".import-conflict.bak")));
    }

    @Test public void markerMatchingDoesNotFoldNonAsciiBasenames() throws IOException {
        File target = temporary.newFolder();
        File existing = new File(target, "Ä.vsf");
        File imported = new File(target, "ä.VSF");
        write(existing, "previous");
        File staged = temporary.newFile();
        write(staged, "imported");
        try (SaveImportPaths.ImportWrites writes = new SaveImportPaths.ImportWrites()) {
            writes.write(staged, imported);
        }
        assertEquals("previous", read(existing));
        assertEquals("imported", read(imported));
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

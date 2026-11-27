package io.github.kichikuou.xsystem4;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PcSaveExportTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private static final byte[] X4SS_MAGIC = new byte[]{'X', '4', 'S', 'S', 1, 0, 0, 0};

    private static SaveFileEntry entry(File file, String relativePath) {
        return new SaveFileEntry(file, relativePath, file.length(), file.lastModified(), "slot");
    }

    private static List<String> zipNames(File archive) throws IOException {
        try (ZipFile zip = new ZipFile(archive)) {
            return Collections.list(zip.entries()).stream()
                    .map(ZipEntry::getName)
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    private File extractZip(File archive) throws IOException {
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
        return extracted;
    }

    @Test
    public void pcZipOmitsAliceHubLayoutMarkerWhileRawZipPreservesItAndRoundTrips() throws IOException {
        File save = temporary.newFile("GlobalSave.asd");
        Files.write(save.toPath(), "save-bytes".getBytes(StandardCharsets.US_ASCII));
        List<SaveFileEntry> entries = Collections.singletonList(entry(save, "GlobalSave.asd"));

        File pcArchive = temporary.newFile("pc.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(pcArchive.toPath()))) {
            SaveZipLayout.INSTANCE.write(zos, entries, false, "SaveData", (file, out) -> {
                try {
                    Files.copy(file.toPath(), out);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                return false;
            });
        }
        List<String> pcNames = zipNames(pcArchive);
        assertEquals(Collections.singletonList("SaveData/GlobalSave.asd"), pcNames);
        assertFalse(pcNames.contains(SaveZipLayout.marker));

        // Verify PC ZIP without marker still re-imports cleanly via SaveImportPaths
        File extracted = extractZip(pcArchive);
        File reimportTarget = temporary.newFolder("reimport");
        try (SaveImportPaths.ImportWrites writes = new SaveImportPaths.ImportWrites()) {
            assertEquals(1, new SaveImportPaths(null).importExtractedArchive(extracted, reimportTarget, writes));
            assertEquals(0, writes.finishFallbacks());
        }
        assertEquals(
                "save-bytes",
                new String(Files.readAllBytes(new File(reimportTarget, "GlobalSave.asd").toPath()), StandardCharsets.US_ASCII)
        );

        File rawArchive = temporary.newFile("raw.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(rawArchive.toPath()))) {
            SaveZipLayout.INSTANCE.write(zos, entries, true, "SaveData", (file, out) -> {
                try {
                    Files.copy(file.toPath(), out);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                return false;
            });
        }
        List<String> rawNames = zipNames(rawArchive);
        assertEquals(Arrays.asList(SaveZipLayout.marker, "SaveData/GlobalSave.asd"), rawNames);
    }

    @Test
    public void pcExportFiltersCommentSidecarsAndInternalFilesWhileRawKeepsAll() throws IOException {
        File dir = temporary.newFolder("saves");
        File slot = new File(dir, "01.asd");
        File comment = new File(dir, "01.asd.comment");
        File upperComment = new File(dir, "02.asd.COMMENT");
        File bak = new File(dir, "GlobalSave.asd.unlock.bak");
        File log = new File(dir, "crash.log");
        File modAbsent = new File(dir, "CommonSystemData.sav.mod.absent");
        File thumb = new File(dir, "01.qnt");
        for (File f : Arrays.asList(slot, comment, upperComment, bak, log, modAbsent, thumb)) {
            assertTrue(f.createNewFile());
        }

        List<SaveFileEntry> all = Arrays.asList(
                entry(slot, "01.asd"),
                entry(comment, "01.asd.comment"),
                entry(upperComment, "02.asd.COMMENT"),
                entry(bak, "GlobalSave.asd.unlock.bak"),
                entry(log, "crash.log"),
                entry(modAbsent, "CommonSystemData.sav.mod.absent"),
                entry(thumb, "01.qnt")
        );

        List<String> pcExported = SaveManagerActivity.filterEntriesForExport(all, true).stream()
                .map(SaveFileEntry::getRelativePath)
                .collect(Collectors.toList());
        assertEquals(Arrays.asList("01.asd", "01.qnt"), pcExported);

        List<String> rawExported = SaveManagerActivity.filterEntriesForExport(all, false).stream()
                .map(SaveFileEntry::getRelativePath)
                .collect(Collectors.toList());
        assertEquals(7, rawExported.size());
        assertTrue(rawExported.contains("01.asd.comment"));
        assertTrue(rawExported.contains("GlobalSave.asd.unlock.bak"));
    }

    @Test
    public void pcCopyPassesAinPathDetectsNonX4ssConversionAndNeverCopiesUnconvertedX4ss() throws IOException {
        File scratch = temporary.newFolder("scratch");

        // 1. Non-X4SS file (e.g. GD\x01\x01 or gsave+comment) gets routed through exporter with ainPath
        File gdSave = temporary.newFile("SaveData0001.asd");
        Files.write(gdSave.toPath(), new byte[]{'G', 'D', 1, 1, 0, 0});
        AtomicReference<String> seenAin = new AtomicReference<>();
        ByteArrayOutputStream outRepaired = new ByteArrayOutputStream();
        boolean converted = GameModifierBridge.copySaveFileForPcWithExporter(
                gdSave,
                outRepaired,
                scratch,
                "/games/Dohna/dohnadohna.ain",
                (src, dst, ain) -> {
                    seenAin.set(ain);
                    try {
                        Files.write(dst.toPath(), new byte[]{'G', 'D', 1, 1, 42, 0});
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    return true;
                }
        );
        assertTrue(converted);
        assertEquals("/games/Dohna/dohnadohna.ain", seenAin.get());
        assertArrayEquals(new byte[]{'G', 'D', 1, 1, 42, 0}, outRepaired.toByteArray());

        // 2. Identical pass-through file returns converted == false
        ByteArrayOutputStream outSame = new ByteArrayOutputStream();
        boolean unchanged = GameModifierBridge.copySaveFileForPcWithExporter(
                gdSave,
                outSame,
                scratch,
                null,
                (src, dst, ain) -> {
                    try {
                        Files.write(dst.toPath(), Files.readAllBytes(src.toPath()));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    return true;
                }
        );
        assertFalse(unchanged);
        assertArrayEquals(new byte[]{'G', 'D', 1, 1, 0, 0}, outSame.toByteArray());

        // 3. Failed conversion of X4SS throws IOException and writes zero bytes
        File x4ssSave = temporary.newFile("SaveData0002.asd");
        Files.write(x4ssSave.toPath(), X4SS_MAGIC);
        ByteArrayOutputStream outFailed = new ByteArrayOutputStream();
        try {
            GameModifierBridge.copySaveFileForPcWithExporter(
                    x4ssSave, outFailed, scratch, null, (src, dst, ain) -> false
            );
            fail("Expected IOException when X4SS conversion fails");
        } catch (IOException expected) {
            assertEquals(0, outFailed.size());
        }

        // 4. Exporter that leaves X4SS magic untouched is refused
        ByteArrayOutputStream outPassthroughX4ss = new ByteArrayOutputStream();
        try {
            GameModifierBridge.copySaveFileForPcWithExporter(
                    x4ssSave,
                    outPassthroughX4ss,
                    scratch,
                    null,
                    (src, dst, ain) -> {
                        try {
                            Files.write(dst.toPath(), X4SS_MAGIC);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                        return true;
                    }
            );
            fail("Expected IOException when output still has X4SS magic");
        } catch (IOException expected) {
            assertEquals(0, outPassthroughX4ss.size());
        }
    }

    @Test
    public void resolvesAinFileForIniConfiguredDsrAndExcludesSystem39() throws IOException {
        File dohnaDir = temporary.newFolder("Dohna");
        File dohnaAin = new File(dohnaDir, "dohnadohna.ain");
        assertTrue(dohnaAin.createNewFile());
        Files.write(new File(dohnaDir, "System40.ini").toPath(),
                "CodeName = \"dohnadohna.ain\"\nSaveFolder = \"SaveData\"\n".getBytes(StandardCharsets.US_ASCII));
        assertEquals(dohnaAin, SaveManagerActivity.resolveAinFile(dohnaDir));
        assertEquals("SaveData", new SaveImportPaths(dohnaDir).getPcSaveFolderName());

        File srDir = temporary.newFolder("SengokuRance");
        File vanillaAin = new File(srDir, "AliceCN.ain");
        File dsrAin = new File(srDir, "DaiSengoku.ain");
        assertTrue(vanillaAin.createNewFile());
        assertTrue(dsrAin.createNewFile());
        Files.write(new File(srDir, "System40.ini").toPath(),
                "CodeName = \"DaiSengoku.ain\"\nSaveFolder = \"SaveDataDSR\"\n".getBytes(StandardCharsets.US_ASCII));
        assertEquals(dsrAin, SaveManagerActivity.resolveAinFile(srDir, null, true));
        assertEquals(vanillaAin, SaveManagerActivity.resolveAinFile(srDir, null, false));
        assertEquals("SaveDataDSR", new SaveImportPaths(srDir).getPcSaveFolderName());

        File sys39Dir = temporary.newFolder("Sys39");
        assertTrue(new File(sys39Dir, "System39.ain").createNewFile());
        assertTrue(new File(sys39Dir, "妻みぐいSA.ALD").createNewFile());
        assertNull(SaveManagerActivity.resolveAinFile(sys39Dir));
        // Default-layout System 3.5 expects saves directly beside scenario files (no SaveData subfolder)
        assertNull(new SaveImportPaths(sys39Dir).getPcSaveFolderName());
        // Explicit non-root save folder override is still honored
        assertEquals("CustomSaves", new SaveImportPaths(sys39Dir, null, "CustomSaves").getPcSaveFolderName());
    }

    @Test
    public void pcExportResourceStringsExistWithValidFormatSpecifiersInAllLocales() throws Exception {
        for (String valuesDir : Arrays.asList("values", "values-zh", "values-ja")) {
            File xml = new File("src/main/res/" + valuesDir + "/strings.xml");
            assertTrue("Missing " + xml.getPath(), xml.isFile());
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml);
            NodeList nodes = doc.getElementsByTagName("string");
            Map<String, String> strings = new HashMap<>();
            for (int i = 0; i < nodes.getLength(); i++) {
                Element el = (Element) nodes.item(i);
                strings.put(el.getAttribute("name"), el.getTextContent());
            }
            assertNotNull(valuesDir + ": save_export_pc_converted_done_msg",
                    strings.get("save_export_pc_converted_done_msg"));
            assertNotNull(valuesDir + ": save_export_pc_done_msg",
                    strings.get("save_export_pc_done_msg"));
            String xpeNotice = strings.get("save_export_pc_xsystem4_only_notice");
            assertNotNull(valuesDir + ": save_export_pc_xsystem4_only_notice", xpeNotice);
            String singleNotice = strings.get("save_export_single_xsystem4_only_notice");
            assertNotNull(valuesDir + ": save_export_single_xsystem4_only_notice", singleNotice);

            assertTrue(String.format(Locale.US, strings.get("save_export_pc_converted_done_msg"), 5, 2).contains("5"));
            assertTrue(String.format(Locale.US, strings.get("save_export_pc_done_msg"), 3).contains("3"));
            String formattedXpe = String.format(Locale.US, xpeNotice, 1, "00.asd");
            assertTrue(formattedXpe.contains("00.asd"));
            assertTrue(formattedXpe.contains("xsystem4"));
            String formattedSingle = String.format(Locale.US, singleNotice, "00.asd");
            assertTrue(formattedSingle.contains("00.asd"));
            assertTrue(formattedSingle.contains("xsystem4"));
        }
    }
}

package io.github.kichikuou.xsystem4;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CancellationException;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class Iso9660ExtractorTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private File image(int... links) throws IOException {
        byte[] bytes = new byte[24 * 2048];
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        bytes[16 * 2048] = 1;
        System.arraycopy("CD001".getBytes(StandardCharsets.US_ASCII), 0, bytes, 16 * 2048 + 1, 5);
        buffer.putInt(16 * 2048 + 158, 20);
        buffer.putInt(16 * 2048 + 166, 2048);
        for (int i = 0; i < links.length; i += 2) {
            int offset = links[i] * 2048;
            bytes[offset] = 38;
            buffer.putInt(offset + 2, links[i + 1]);
            buffer.putInt(offset + 10, 2048);
            bytes[offset + 25] = 2;
            bytes[offset + 32] = 4;
            System.arraycopy("LOOP".getBytes(StandardCharsets.US_ASCII), 0, bytes, offset + 33, 4);
        }
        File file = temp.newFile();
        Files.write(file.toPath(), bytes);
        return file;
    }

    private int extract(File iso, Function0<Unit> checkCancelled) throws IOException {
        return Iso9660Extractor.INSTANCE.extractFileToDirectory(
                iso, temp.newFolder(), path -> false, false, checkCancelled, null, null);
    }

    @Test(timeout = 2000) public void cyclicDirectoriesAreRejected() throws IOException {
        for (File iso : new File[]{image(20, 20), image(20, 21, 21, 20)}) {
            assertFalse(Iso9660Extractor.INSTANCE.containsGameRoot(iso));
            IOException error = assertThrows(IOException.class, () -> extract(iso, null));
            assertTrue(error.getMessage().contains("Cyclic or repeated ISO directory extent"));
        }
    }

    @Test(timeout = 2000) public void scanCancellationIsNotSwallowed() throws IOException {
        File iso = image(20, 21);
        int[] checks = {0};
        CancellationException cancelled = new CancellationException("cancel scan");
        CancellationException error = assertThrows(CancellationException.class, () ->
                Iso9660Extractor.INSTANCE.containsGameRoot(iso, null, null, () -> {
                    if (++checks[0] == 4) throw cancelled;
                    return Unit.INSTANCE;
                }));
        assertSame(cancelled, error);
        assertEquals(4, checks[0]);
    }

    @Test(timeout = 2000) public void extractionScanHonorsCancellation() throws IOException {
        File iso = image(20, 21);
        int[] checks = {0};
        assertThrows(CancellationException.class, () -> extract(iso, () -> {
            if (++checks[0] == 2) throw new CancellationException();
            return Unit.INSTANCE;
        }));
        assertEquals(2, checks[0]);
    }

    @Test public void emptyAndAcyclicDirectoriesTerminateNormally() throws IOException {
        for (File iso : new File[]{image(), image(20, 21)}) {
            assertFalse(Iso9660Extractor.INSTANCE.containsGameRoot(iso));
            assertEquals(0, extract(iso, null));
        }
    }
}

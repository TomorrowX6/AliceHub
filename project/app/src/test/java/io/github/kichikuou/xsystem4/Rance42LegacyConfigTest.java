package io.github.kichikuou.xsystem4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.rules.TemporaryFolder;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class Rance42LegacyConfigTest {
    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    private static final String CONFIG = "[config]\r\ngame = rance42\r\nplaylist = bgm\\playlist.txt\r\n"
            + "fontfile = simhei.ttf\r\nencoding = utf-8\r\ntitle = 兰斯 4.2 ～天使组～";

    private File game() throws Exception {
        File game = tmp.newFolder("rance42");
        assertTrue(new File(game, "ADISK.dat").createNewFile());
        assertTrue(new File(game, "ACG.dat").createNewFile());
        return game;
    }

    private Item item(File game) {
        return Item.Companion.fromDirectory(game, tmp.getRoot(), RuntimeEnvironment.getApplication());
    }

    @Test
    public void readsOriginalUtf8TitleWithoutChangingRuntimeSettings() throws Exception {
        File game = game();
        File ini = new File(game, "system3.ini");
        Files.write(ini.toPath(), CONFIG.getBytes(StandardCharsets.UTF_8));
        Item item = item(game);
        assertNull(item.getError());
        assertEquals("兰斯 4.2 ～天使组～", item.getName());
        assertEquals(CONFIG, new String(Files.readAllBytes(ini.toPath()), StandardCharsets.UTF_8));
    }

    @Test
    public void missingOrEmptyTitleFallsBackToDirectoryName() throws Exception {
        File game = game();
        assertEquals(game.getName(), item(game).getName());
        File ini = new File(game, "system3.ini");
        Files.write(ini.toPath(), new byte[0]);
        assertEquals(game.getName(), item(game).getName());
        Files.write(ini.toPath(), "[config]\ngame=rance42\ntitle= ".getBytes(StandardCharsets.UTF_8));
        assertEquals(game.getName(), item(game).getName());
    }

    @Test
    public void ignoresOversizedConfiguration() throws Exception {
        File game = game();
        Files.write(new File(game, "system3.ini").toPath(),
                (CONFIG + new String(new char[256 * 1024]).replace('\0', ' ')).getBytes(StandardCharsets.UTF_8));
        assertEquals(game.getName(), item(game).getName());
    }
}

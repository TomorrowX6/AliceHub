package io.github.kichikuou.xsystem4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KichikuouCatalogTest {
    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    private static void writeFile(File file, byte[] bytes) throws Exception {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(bytes);
        }
    }

    @Test
    public void classifiesChineseAndJapaneseKichikuouDirs() throws Exception {
        File cnDir = tmp.newFolder("KichikuRance");
        writeFile(new File(cnDir, "KichikuRanceSA.ALD"), new byte[]{0});
        writeFile(new File(cnDir, ".xsys35rc"), "savedir: Saves\n".getBytes(StandardCharsets.UTF_8));

        assertTrue(GameCatalog.INSTANCE.isSystem3xGameDir(cnDir));
        assertTrue(GameCatalog.INSTANCE.isKichikuouGameDir(cnDir));
        assertFalse(GameCatalog.INSTANCE.isEscalayerGameDir(cnDir));
        assertEquals(
                GameCatalog.ID_KICHIKUOU,
                GameCatalog.INSTANCE.classifyDirectory(cnDir, cnDir.getName(), null, null)
        );

        File jpDir = tmp.newFolder("GAMEDATA");
        writeFile(new File(jpDir, "鬼畜王SA.ALD"), new byte[]{0});
        assertTrue(GameCatalog.INSTANCE.isSystem3xGameDir(jpDir));
        assertTrue(GameCatalog.INSTANCE.isKichikuouGameDir(jpDir));
        assertEquals(
                GameCatalog.ID_KICHIKUOU,
                GameCatalog.INSTANCE.classifyDirectory(jpDir, jpDir.getName(), null, null)
        );
    }

    @Test
    public void classifiesKichikuouBySaveDirAndAliases() throws Exception {
        File saveDir = tmp.newFolder("KichikuSaves");
        writeFile(new File(saveDir, "KichikuRancesa.asd"), new byte[]{1, 2, 3});
        assertEquals(
                GameCatalog.ID_KICHIKUOU,
                GameCatalog.INSTANCE.classifyDirectory(null, "UnknownDir", saveDir, null)
        );

        assertEquals(
                GameCatalog.ID_KICHIKUOU,
                GameCatalog.INSTANCE.classifyDirectory(null, "鬼畜王ランス【卡拉保护协会V1.0】", null, null)
        );
        assertEquals(
                GameCatalog.ID_KICHIKUOU,
                GameCatalog.INSTANCE.classifyDirectory(null, "KichikuRance-1", null, null)
        );
        assertEquals(
                GameCatalog.ID_KICHIKUOU,
                GameCatalog.INSTANCE.classifyDirectory(null, "custom", null, "鬼畜王兰斯")
        );
    }

    @Test
    public void preservesXsys35RcAndDetectsKichikuouBloat() {
        assertFalse(NativeGameImport.INSTANCE.skip(".xsys35rc"));
        assertFalse(NativeGameImport.INSTANCE.skip("KichikuRance/.xsys35rc"));
        assertFalse(NativeGameImport.INSTANCE.skip("bgm/kichiku_2.mp3"));

        assertTrue(NativeGameImport.INSTANCE.skip(".git/config"));
        assertTrue(NativeGameImport.INSTANCE.skip(".hidden"));
        assertTrue(GameListKt.isKichikuouBloatRelPath("xsystem35.exe"));
        assertTrue(GameListKt.isKichikuouBloatRelPath("Kichiku/System35.EXE"));
        assertFalse(GameListKt.isKichikuouBloatRelPath("KichikuRanceSA.ALD"));
    }
}

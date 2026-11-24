package io.github.kichikuou.xsystem4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Unit tests for Chinese translation metadata (熟肉 / 生肉) and local CN marker detection across
 * all 127 built-in games plus custom-imported directories.
 */
public class TranslationBadgeTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final Set<String> EXPECTED_CHINESE_IDS = new HashSet<>(Arrays.asList(
            GameCatalog.ID_RANCE10,
            GameCatalog.ID_DOHNA_DOHNA,
            GameCatalog.ID_EVENICLE,
            GameCatalog.ID_EVENICLE2,
            GameCatalog.ID_SENGOKU_RANCE,
            GameCatalog.ID_RANCE01,
            GameCatalog.ID_RANCE02,
            GameCatalog.ID_RANCE03,
            GameCatalog.ID_RANCE04,
            GameCatalog.ID_RANCE41,
            GameCatalog.ID_RANCE42,
            GameCatalog.ID_RANCE5D,
            GameCatalog.ID_RANCE06,
            GameCatalog.ID_RANCE08,
            GameCatalog.ID_RANCE09,
            GameCatalog.ID_KICHIKUOU,
            GameCatalog.ID_DAIAKUJI,
            GameCatalog.ID_DAIBANCHOU,
            GameCatalog.ID_DAITEIKOKU,
            GameCatalog.ID_ESCALAYER_REBOOT,
            GameCatalog.ID_GALZOO,
            GameCatalog.ID_IXSEAL,
            GameCatalog.ID_TSUMASHIBORI,
            GameCatalog.ID_TSUMAMIGUI,
            GameCatalog.ID_TSUMAMIGUI3,
            GameCatalog.ID_KAERUNYO,
            GameCatalog.ID_ATLACHNACHA,
            GameCatalog.ID_REISHIKI,
            GameCatalog.ID_HARUURARE,
            GameCatalog.ID_OKUSAMAJUN,
            GameCatalog.ID_OKUSAMANTR,
            GameCatalog.ID_EVENICLE2RANCE,
            GameCatalog.ID_EVENICLEGAIDEN,
            GameCatalog.ID_WAIDONYO,
            GameCatalog.ID_RANCE6AFTER,
            GameCatalog.ID_YAKATA2,
            GameCatalog.ID_SDPSMARIA,
            GameCatalog.ID_TOUSHIN3,
            GameCatalog.ID_OYAKORANKAN,
            GameCatalog.ID_YORUGAKURU,
            GameCatalog.ID_RANCE1DIGEST,
            GameCatalog.ID_MAMAN,
            GameCatalog.ID_EVENICLERANCE,
            GameCatalog.ID_HAHARANMAN,
            GameCatalog.ID_TSUMAMIGUI2,
            GameCatalog.ID_TSUMAMIGUI3JUN,
            GameCatalog.ID_TSUMAMIGUI3NTR,
            GameCatalog.ID_DARCROWS,
            GameCatalog.ID_SANBIKI,
            GameCatalog.ID_PASCHA3,
            GameCatalog.ID_BLADEBRIDERS,
            GameCatalog.ID_DORAPEKO,
            GameCatalog.ID_HANIWAPETIT
    ));

    @Test
    public void builtInCatalogMatchesVerifiedChineseAndRawClassification() {
        List<String> order = GameCatalog.builtInSelectOrder();
        assertEquals(157, order.size());
        assertEquals(53, EXPECTED_CHINESE_IDS.size());

        int cnCount = 0;
        int rawCount = 0;
        for (String id : order) {
            GameDescriptor desc = GameCatalog.INSTANCE.descriptorForWithResolver(id, null, resId -> id);
            boolean expectedCn = EXPECTED_CHINESE_IDS.contains(id);
            assertEquals("hasChinese mismatch for " + id, expectedCn, desc.getHasChinese());
            assertEquals("hasChineseTranslation mismatch for " + id, expectedCn, GameCatalog.hasChineseTranslation(desc));
            if (desc.getHasChinese()) {
                cnCount++;
            } else {
                rawCount++;
            }
        }
        assertEquals(53, cnCount);
        assertEquals(104, rawCount);

        // Spot-check titles whose raw vs CN status is non-obvious
        assertFalse(GameCatalog.INSTANCE.descriptorForWithResolver(GameCatalog.ID_ESCALAYER, null, r -> "escalayer").getHasChinese());
        assertFalse(GameCatalog.INSTANCE.descriptorForWithResolver(GameCatalog.ID_HARUKA, null, r -> "haruka").getHasChinese());
        assertFalse(GameCatalog.INSTANCE.descriptorForWithResolver(GameCatalog.ID_TOUSHIN1, null, r -> "toushin1").getHasChinese());
        assertFalse(GameCatalog.INSTANCE.descriptorForWithResolver(GameCatalog.ID_TOUSHIN2, null, r -> "toushin2").getHasChinese());
        assertFalse(GameCatalog.INSTANCE.descriptorForWithResolver(GameCatalog.ID_PASCHACPP, null, r -> "paschacpp").getHasChinese());
        assertTrue(GameCatalog.INSTANCE.descriptorForWithResolver(GameCatalog.ID_DARCROWS, null, r -> "darcrows").getHasChinese());
        assertTrue(GameCatalog.INSTANCE.descriptorForWithResolver(GameCatalog.ID_SANBIKI, null, r -> "sanbiki").getHasChinese());
        assertTrue(GameCatalog.INSTANCE.descriptorForWithResolver(GameCatalog.ID_PASCHA3, null, r -> "pascha3").getHasChinese());
        assertTrue(GameCatalog.INSTANCE.descriptorForWithResolver(GameCatalog.ID_EVENICLE, null, r -> "evenicle").getHasChinese());
        assertTrue(GameCatalog.INSTANCE.descriptorForWithResolver(GameCatalog.ID_EVENICLE2, null, r -> "evenicle2").getHasChinese());
    }

    @Test
    public void localChineseMarkersAndCnPatchOverrideRawClassification() throws Exception {
        assertFalse(GameCatalog.hasLocalChineseMarkers(Collections.emptyList()));
        assertFalse(GameCatalog.hasLocalChineseMarkers(Arrays.asList("AliceStart.ini", "超昂閃忍ハルカ.ain", "超昂閃忍ハルカGA.ald")));
        // Chinese documentation/readme files must NOT flip an untranslated game to Chinese
        assertFalse(GameCatalog.hasLocalChineseMarkers(Arrays.asList("AliceStart.ini", "超昂閃忍ハルカ.ain", "中文说明.txt", "汉化指南.md", "SourceHanSansCN_README.txt")));
        assertTrue(GameCatalog.hasLocalChineseMarkers(Arrays.asList("AliceStart.CHS", "CHS.TBL")));
        assertTrue(GameCatalog.hasLocalChineseMarkers(Arrays.asList("AliceStart.ini", "burebura.ain", "uif_config.json")));
        assertTrue(GameCatalog.hasLocalChineseMarkers(Arrays.asList("System40.ini", "AliceCN.ain")));
        assertTrue(GameCatalog.hasLocalChineseMarkers(Arrays.asList("AliceStart.ini", "haru_c.ain")));
        assertTrue(GameCatalog.hasLocalChineseMarkers(Arrays.asList("AliceStart.ini", "GBK.ain")));
        assertTrue(GameCatalog.hasLocalChineseMarkers(Arrays.asList("System39.ini", "蜘蛛SA_CHS.ALD")));
        assertTrue(GameCatalog.hasLocalChineseMarkers(Arrays.asList("AliceStart.ini", "Game_cn.afa")));
        assertTrue(GameCatalog.hasLocalChineseMarkers(Arrays.asList("system3.ini", "simhei.ttf")));
        assertTrue(GameCatalog.hasLocalChineseMarkers(Arrays.asList("AliceStart.ini", "某游戏汉化版.ain")));

        GameDescriptor customDesc = GameCatalog.INSTANCE.descriptorForWithResolver("custom:MyGame", null, r -> "MyGame");
        assertFalse(customDesc.getHasChinese());

        File rawDir = tmp.newFolder("custom_raw");
        assertTrue(new File(rawDir, "System40.ini").createNewFile());
        assertTrue(new File(rawDir, "Game.ain").createNewFile());
        assertTrue(new File(rawDir, "中文说明.txt").createNewFile());
        Item rawItem = new Item("MyGame", rawDir, rawDir, null, null, null);
        assertFalse(GameCatalog.hasChineseTranslation(customDesc, rawItem, null));

        // Installing a CN patch flips raw -> Chinese
        InstalledModInfo cnPatchInfo = new InstalledModInfo(
                "V1.00", null, -1, -1, "cn_patch.zip", "", "local", 0L, InstalledModInfo.KIND_CN_PATCH, ""
        );
        assertTrue(GameCatalog.hasChineseTranslation(customDesc, rawItem, cnPatchInfo));

        // Explicit local Chinese asset in a custom game directory flips raw -> Chinese
        File cnDir = tmp.newFolder("custom_cn");
        assertTrue(new File(cnDir, "AliceStart.ini").createNewFile());
        assertTrue(new File(cnDir, "Game_cn.afa").createNewFile());
        Item cnItem = new Item("MyGameCN", cnDir, cnDir, null, null, null);
        assertTrue(GameCatalog.hasChineseTranslation(customDesc, cnItem, null));

        // Fresh Item instance picks up newly added CN marker files
        assertTrue(new File(rawDir, "CHS.TBL").createNewFile());
        Item updatedRawItem = new Item("MyGame", rawDir, rawDir, null, null, null);
        assertTrue(GameCatalog.hasChineseTranslation(customDesc, updatedRawItem, null));

        // Explicit English Legacy System 3 import (e.g., rance41_eng) overrides online CN default
        GameDescriptor rance41Desc = GameCatalog.INSTANCE.descriptorForWithResolver(GameCatalog.ID_RANCE41, null, r -> "rance41");
        assertTrue(GameCatalog.hasChineseTranslation(rance41Desc, null, null));

        File engRance41Dir = tmp.newFolder("rance41_eng");
        assertTrue(new File(engRance41Dir, "ADISK.DAT").createNewFile());
        assertTrue(new File(engRance41Dir, "ACG.DAT").createNewFile());
        Files.write(new File(engRance41Dir, "system3.ini").toPath(), "[config]\ngame = rance41_eng\n".getBytes(StandardCharsets.UTF_8));
        Item engRance41Item = new Item("Rance 4.1", engRance41Dir, engRance41Dir, null, null, null);
        assertTrue(GameCatalog.hasExplicitNonChineseEdition(engRance41Dir));
        assertFalse(GameCatalog.hasChineseTranslation(rance41Desc, engRance41Item, null));

        // Chinese Rance 4.1 download stays Chinese
        File cnRance41Dir = tmp.newFolder("rance41_cn");
        assertTrue(new File(cnRance41Dir, "ADISK.DAT").createNewFile());
        assertTrue(new File(cnRance41Dir, "ACG.DAT").createNewFile());
        assertTrue(new File(cnRance41Dir, "simhei.ttf").createNewFile());
        Files.write(new File(cnRance41Dir, "system3.ini").toPath(), "[config]\ngame = rance41\nfontfile = simhei.ttf\n".getBytes(StandardCharsets.UTF_8));
        Item cnRance41Item = new Item("兰斯4.1", cnRance41Dir, cnRance41Dir, null, null, null);
        assertFalse(GameCatalog.hasExplicitNonChineseEdition(cnRance41Dir));
        assertTrue(GameCatalog.hasChineseTranslation(rance41Desc, cnRance41Item, null));
    }
}

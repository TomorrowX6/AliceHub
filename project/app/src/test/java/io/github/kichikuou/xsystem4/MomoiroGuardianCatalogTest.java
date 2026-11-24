package io.github.kichikuou.xsystem4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MomoiroGuardianCatalogTest {
    @Test
    public void momoiroGuardianCatalogDescriptorMetadataAndCdnAreWired() {
        List<String> order = GameCatalog.builtInSelectOrder();
        assertEquals(170, order.size());
        assertTrue(order.contains(GameCatalog.ID_MOMOIROGUARDIAN));

        GameDescriptor desc = GameCatalog.INSTANCE.descriptorForWithResolver(
                GameCatalog.ID_MOMOIROGUARDIAN, null, resId -> "MomoiroGuardian");
        assertEquals("MomoiroGuardian", desc.getDefaultDirName());
        assertNull(desc.getExpectedAinName());
        assertTrue(desc.getSupportsOnlineBaseInstall());
        assertEquals(OnlineInstaller.MOMOIROGUARDIAN_CDN_URL, desc.getOnlineBaseCdnUrl());
        assertEquals("https://alicehub.000.moe/momoiroguardian", desc.getOnlineBaseCdnUrl());
        assertEquals(904356575L, AppSettings.INSTANCE.defaultBaseGameSizeForUrl(OnlineInstaller.MOMOIROGUARDIAN_CDN_URL));
        assertFalse(desc.getHasChinese());
        assertFalse(desc.getSupportsOnlineMod());
        assertFalse(desc.getSupportsLocalMod());

        GameSortInfo meta = GameSortMetadata.INSTANCE.forGame(GameCatalog.ID_MOMOIROGUARDIAN);
        assertNotNull(meta);
        assertEquals("2009-09-18", meta.getReleaseDate());
        assertEquals(Double.valueOf(69.2), meta.getRating());
        assertEquals("https://vndb.org/v2515", meta.getScoreSourceUrl());
        assertEquals("https://vndb.org/v2515", meta.getReleaseSourceUrl());
    }

    @Test
    public void momoiroGuardianFileAndAliasClassificationWorksCleanly() {
        assertEquals(GameCatalog.ID_MOMOIROGUARDIAN, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("AliceStart.ini", "ももんが.ain", "ももんがGA.ald", "ももんがWA.ald", "ももんがBA.ald")));
        assertEquals(GameCatalog.ID_MOMOIROGUARDIAN, GameCatalog.INSTANCE.classifyGameRootFiles(
                Arrays.asList("AliceStart.ini", "momonga.inc")));
        assertEquals(GameCatalog.ID_MOMOIROGUARDIAN, GameCatalog.INSTANCE.classifyGameRootFiles(
                Collections.singletonList("AliceStart.ini"),
                Collections.emptyList(),
                name -> "GameName = \"ももいろガーディアン\"\n".getBytes(StandardCharsets.UTF_8)));

        for (String alias : Arrays.asList(
                "MomoiroGuardian", "Momonga", "ももいろガーディアン", "桃色守护者", "桃色守護者", "ももんが")) {
            assertTrue(alias, GameCatalog.INSTANCE.isMomoiroGuardianAlias(alias));
            assertEquals(alias, GameCatalog.ID_MOMOIROGUARDIAN,
                    GameCatalog.INSTANCE.classifyDirectory(null, alias, null, null));
        }

        assertTrue(GameModifierBridge.INSTANCE.isMomoiroGuardianGameDir(null, "MomoiroGuardian", null));
        assertTrue(GameModifierBridge.INSTANCE.isNonModifierBuiltInGameDir(null, "MomoiroGuardian", null));
    }
}

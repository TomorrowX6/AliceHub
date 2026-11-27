package io.github.kichikuou.xsystem4;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class GenericModifierRegistryTest {

    @Test
    public void allRegisteredGamesExistInCatalogAndHaveValidSpecs() {
        Set<String> catalogIds = new HashSet<>(GameCatalog.builtInSelectOrder());
        Set<String> seenIds = new HashSet<>();
        List<GenericModifierSpec> all = GenericModifierRegistry.INSTANCE.getAll();

        assertEquals(28, all.size());
        int sys4Count = 0;
        int sys35Count = 0;
        for (GenericModifierSpec spec : all) {
            if (spec.isSystem35()) {
                sys35Count++;
            } else {
                sys4Count++;
            }
            assertTrue("Duplicate spec id: " + spec.getId(), seenIds.add(spec.getId()));
            assertTrue("Spec id not in GameCatalog: " + spec.getId(), catalogIds.contains(spec.getId()));
            assertFalse("Spec " + spec.getId() + " must have at least one lock", spec.getLocks().isEmpty());
            assertFalse("Spec " + spec.getId() + " must have at least one cheat", spec.getCheats().isEmpty());

            Set<Integer> bits = new HashSet<>();
            for (ModLockSpec lock : spec.getLocks()) {
                assertEquals("Lock bit must be single power of 2 in " + spec.getId(),
                        1, Integer.bitCount(lock.getBit()));
                assertTrue("Duplicate lock bit in " + spec.getId(), bits.add(lock.getBit()));
            }

            for (ModCheatSpec cheat : spec.getCheats()) {
                if (spec.isSystem35()) {
                    assertNotNull("Sys35 cheat must define s35Action in " + spec.getId(), cheat.getS35Action());
                    assertFalse("Sys35 cheat s35Action must not be blank in " + spec.getId(),
                            cheat.getS35Action().trim().isEmpty());
                } else {
                    Map<String, Object> action = cheat.getJsonAction();
                    assertNotNull("Sys4 cheat must define jsonAction in " + spec.getId(), action);
                    assertFalse("Sys4 cheat jsonAction must not be empty in " + spec.getId(), action.isEmpty());
                }
            }
        }
        assertEquals(23, sys4Count);
        assertEquals(5, sys35Count);
    }

    @Test
    public void evenicleFamilyAndLookupMatchExpectedGames() {
        assertTrue(GenericModifierRegistry.isEvenicleFamily(GameCatalog.ID_EVENICLE));
        assertTrue(GenericModifierRegistry.isEvenicleFamily(GameCatalog.ID_EVENICLERANCE));
        assertTrue(GenericModifierRegistry.isEvenicleFamily(GameCatalog.ID_EVENICLEGAIDEN));
        assertFalse(GenericModifierRegistry.isEvenicleFamily(GameCatalog.ID_EVENICLE2));

        assertNotNull(GenericModifierRegistry.find(GameCatalog.ID_RANCE06));
        assertNotNull(GenericModifierRegistry.find(GameCatalog.ID_EVENICLE2));
        assertNotNull(GenericModifierRegistry.find(GameCatalog.ID_DAIBANCHOU));
        assertNotNull(GenericModifierRegistry.find(GameCatalog.ID_DAITEIKOKU));
        assertNotNull(GenericModifierRegistry.find(GameCatalog.ID_ESCALAYER_REBOOT));
        assertEquals(
                GenericModifierRegistry.find(GameCatalog.ID_ESCALAYER_REBOOT),
                GenericModifierRegistry.find("escalayerreboot")
        );
        assertNotNull(GenericModifierRegistry.find(GameCatalog.ID_OKUSAMAJUN));
        assertNotNull(GenericModifierRegistry.find(GameCatalog.ID_OKUSAMANTR));
        assertNotNull(GenericModifierRegistry.find(GameCatalog.ID_IXSEAL));
        assertNotNull(GenericModifierRegistry.find(GameCatalog.ID_DAIAKUJI));
        assertNotNull(GenericModifierRegistry.find(GameCatalog.ID_RANCE5D));
        assertNotNull(GenericModifierRegistry.find(GameCatalog.ID_TSUMAMIGUI));
        assertNotNull(GenericModifierRegistry.find(GameCatalog.ID_KICHIKUOU));
        assertNotNull(GenericModifierRegistry.find(GameCatalog.ID_KAERUNYO));

        assertEquals(
                GameCatalog.ID_RANCE06,
                GameModifierBridge.INSTANCE.resolveGenericModifierGameId(null, "Rance06", null)
        );
        assertTrue(GameModifierBridge.INSTANCE.isEvenicleFamilyGameDir(null, "EvenicleRance", null));
        assertTrue(GameModifierBridge.INSTANCE.isEvenicleFamilyGameDir(null, "EvenicleGaiden", null));
    }

    @Test
    public void ixsealCheatsExposeMaxMagicPlates12AndMaxItems() {
        GenericModifierSpec ixseal = GenericModifierRegistry.find(GameCatalog.ID_IXSEAL);
        assertNotNull(ixseal);
        boolean foundMaxPlates = false;
        boolean foundMaxItems = false;
        for (ModCheatSpec cheat : ixseal.getCheats()) {
            Map<String, Object> action = cheat.getJsonAction();
            assertNotNull(action);
            assertFalse("ixseal should not send addGold", action.containsKey("addGold"));
            if (Integer.valueOf(12).equals(action.get("gold"))) {
                assertEquals("魔石板拉满 (12)", cheat.getLabel().getZh());
                foundMaxPlates = true;
            }
            if ("maxItems".equals(action.get("cheat"))) {
                foundMaxItems = true;
            }
        }
        assertTrue("ixseal must expose gold=12 cheat", foundMaxPlates);
        assertTrue("ixseal must expose maxItems cheat", foundMaxItems);
    }
}

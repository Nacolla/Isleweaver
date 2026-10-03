package com.thebeyond.isleweaver.compat.legendary_monsters;

import com.thebeyond.api.worldgen.BeyondForeignStructureProfiles;
import com.thebeyond.api.worldgen.StructureIntegrationProfile;
import com.thebeyond.api.worldgen.StructureIntegrationProfile.Anchor;
import com.thebeyond.isleweaver.Isleweaver;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

// integration policy for Legendary Monsters' three End structures, the carve and seat live in Beyond
public final class LegendaryMonstersProfiles {
    private static final String MOD = "legendary_monsters";

    private LegendaryMonstersProfiles() {}

    public static void register() {
        if (!ModList.get().isLoaded(MOD)) return;

        // the two towers stay unregistered so Beyond auto-hosts them, a SEATED profile would drop their skirt

        // space_station floats in the void (none -> FLOATING): ceiling-clip rejection + carve only, no foundation.
        BeyondForeignStructureProfiles.register(
                ResourceLocation.fromNamespaceAndPath(MOD, "space_station"),
                StructureIntegrationProfile.builder(Anchor.FLOATING).build());

        Isleweaver.LOGGER.debug("[Isleweaver] Registered Legendary Monsters End structure integration profiles "
                + "(space_station FLOATING; shulker_tower + ancient_tower_remains auto-hosted distributed).");
    }
}

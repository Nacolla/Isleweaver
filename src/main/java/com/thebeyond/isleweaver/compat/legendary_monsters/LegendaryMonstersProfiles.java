package com.thebeyond.isleweaver.compat.legendary_monsters;

import com.thebeyond.api.worldgen.BeyondForeignStructureProfiles;
import com.thebeyond.api.worldgen.StructureIntegrationProfile;
import com.thebeyond.api.worldgen.StructureIntegrationProfile.Anchor;
import com.thebeyond.isleweaver.Isleweaver;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

// supplies per-structure integration policy for LM's three End structures; the rejection/carve/foundation mechanism lives in Beyond
public final class LegendaryMonstersProfiles {
    private static final String MOD = "legendary_monsters";

    private LegendaryMonstersProfiles() {}

    public static void register() {
        if (!ModList.get().isLoaded(MOD)) return;

        // shulker_tower + ancient_tower_remains stay unregistered: as minecraft:jigsaw with project_start_to_heightmap they auto-host via Beyond's surface path, and a SEATED profile would suppress that skirt and let the island intrude their bases

        // space_station floats in the void (none -> FLOATING): ceiling-clip rejection + carve only, no foundation.
        BeyondForeignStructureProfiles.register(
                ResourceLocation.fromNamespaceAndPath(MOD, "space_station"),
                StructureIntegrationProfile.builder(Anchor.FLOATING).build());

        Isleweaver.LOGGER.debug("[Isleweaver] Registered Legendary Monsters End structure integration profiles "
                + "(space_station FLOATING; shulker_tower + ancient_tower_remains auto-hosted distributed).");
    }
}

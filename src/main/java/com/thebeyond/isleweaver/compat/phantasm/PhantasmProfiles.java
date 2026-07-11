package com.thebeyond.isleweaver.compat.phantasm;

import com.thebeyond.api.worldgen.BeyondForeignStructureProfiles;
import com.thebeyond.api.worldgen.StructureIntegrationProfile;
import com.thebeyond.api.worldgen.StructureIntegrationProfile.Anchor;
import com.thebeyond.isleweaver.Isleweaver;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

// obsidian_tower lands foundationless on Beyond's pancakes (no terrain_adaptation/self-rejection); SEATED adds the footprint rejection + foundation
public final class PhantasmProfiles {
    private static final String MOD = "phantasm";

    private PhantasmProfiles() {}

    public static void register() {
        if (!ModList.get().isLoaded(MOD)) return;

        BeyondForeignStructureProfiles.register(
                ResourceLocation.fromNamespaceAndPath(MOD, "obsidian_tower"),
                StructureIntegrationProfile.builder(Anchor.SEATED).build());

        Isleweaver.LOGGER.debug("[Isleweaver] Registered End's Phantasm End structure integration profiles (1).");
    }
}

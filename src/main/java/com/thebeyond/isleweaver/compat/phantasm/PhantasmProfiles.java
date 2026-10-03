package com.thebeyond.isleweaver.compat.phantasm;

import com.thebeyond.api.worldgen.BeyondForeignStructureProfiles;
import com.thebeyond.api.worldgen.StructureIntegrationProfile;
import com.thebeyond.api.worldgen.StructureIntegrationProfile.Anchor;
import com.thebeyond.isleweaver.Isleweaver;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

// obsidian_tower digs its shaft from y=0 up through every layer, so it keeps vanilla's projection
public final class PhantasmProfiles {
    private static final String MOD = "phantasm";

    private PhantasmProfiles() {}

    public static void register() {
        if (!ModList.get().isLoaded(MOD)) return;

        BeyondForeignStructureProfiles.register(
                ResourceLocation.fromNamespaceAndPath(MOD, "obsidian_tower"),
                StructureIntegrationProfile.builder(Anchor.FLOATING)
                        .carve(false)
                        .selfHollowing(true)
                        .floatEnvelope(57) // CountConfiguration 30..50 over the origin, rounded up to a multiple of 7
                        .build());

        Isleweaver.LOGGER.debug("[Isleweaver] Registered End's Phantasm End structure integration profiles (1).");
    }
}

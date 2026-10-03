package com.thebeyond.isleweaver.compat.unusualend;

import com.thebeyond.api.worldgen.BeyondForeignStructureProfiles;
import com.thebeyond.api.worldgen.StructureIntegrationProfile;
import com.thebeyond.api.worldgen.StructureIntegrationProfile.Anchor;
import com.thebeyond.isleweaver.Isleweaver;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

// Unusual End's beard_thin towers float or bury on Beyond's layers, so they are SEATED
public final class UnusualEndStructureProfiles {
    private static final String MOD = "unusualend";

    private UnusualEndStructureProfiles() {}

    public static void register() {
        if (!ModList.get().isLoaded(MOD)) return;

        StructureIntegrationProfile seated = StructureIntegrationProfile.builder(Anchor.SEATED).build();
        for (String id : new String[]{
                "ancient_end_tower", "end_house", "gloopy_nest",
                "small_gloopy_ruins", "chorus_cane", "firefly_nest"}) {
            BeyondForeignStructureProfiles.register(ResourceLocation.fromNamespaceAndPath(MOD, id), seated);
        }

        Isleweaver.LOGGER.debug("[Isleweaver] Registered Unusual End End structure integration profiles (6).");
    }
}

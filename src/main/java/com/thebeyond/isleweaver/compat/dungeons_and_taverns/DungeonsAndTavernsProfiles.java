package com.thebeyond.isleweaver.compat.dungeons_and_taverns;

import com.thebeyond.api.worldgen.BeyondForeignStructureProfiles;
import com.thebeyond.api.worldgen.StructureIntegrationProfile;
import com.thebeyond.api.worldgen.StructureIntegrationProfile.Anchor;
import com.thebeyond.isleweaver.Isleweaver;
import net.minecraft.resources.ResourceLocation;

// Dungeons & Taverns can come as a bare datapack, so ids alone: castle and ships hang, the lighthouse sits
public final class DungeonsAndTavernsProfiles {
    private static final String NS = "nova_structures";

    private DungeonsAndTavernsProfiles() {}

    public static void register() {
        for (String id : new String[]{"end_castle", "end_ship"}) {
            BeyondForeignStructureProfiles.register(ResourceLocation.fromNamespaceAndPath(NS, id),
                    StructureIntegrationProfile.builder(Anchor.FLOATING).build());
        }
        Isleweaver.LOGGER.debug("[Isleweaver] Dungeons & Taverns end_castle and end_ship registered FLOATING.");
    }
}

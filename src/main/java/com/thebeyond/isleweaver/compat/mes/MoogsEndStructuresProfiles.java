package com.thebeyond.isleweaver.compat.mes;

import com.thebeyond.api.worldgen.BeyondForeignStructureProfiles;
import com.thebeyond.isleweaver.Isleweaver;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

// per-structure tweaks for MES ruins: dirt-cover opt-out for gardens, embedded carve for the crashed megaships
public final class MoogsEndStructuresProfiles {
    private static final String MOD = "mes";

    private MoogsEndStructuresProfiles() {}

    public static void register() {
        if (!ModList.get().isLoaded(MOD)) return;
        BeyondForeignStructureProfiles.suppressDirtCover(ResourceLocation.fromNamespaceAndPath(MOD, "mythic_garden"));
        BeyondForeignStructureProfiles.suppressDirtCover(ResourceLocation.fromNamespaceAndPath(MOD, "placid_prairie"));
        // Crashed megaships: embedded (carve-only). The island buries the sunk hull, interior stays hollow.
        for (String ship : new String[]{"mega_ship_crashed", "mega_ship_crashed_2", "mega_ship_crashed_deepslate"}) {
            BeyondForeignStructureProfiles.markEmbedded(ResourceLocation.fromNamespaceAndPath(MOD, ship));
        }
        Isleweaver.LOGGER.debug("[Isleweaver] MES present, dirt-cover opt-out (mythic_garden, placid_prairie); "
                + "crashed megaships embedded (distributed carve-only, island-buried).");
    }
}

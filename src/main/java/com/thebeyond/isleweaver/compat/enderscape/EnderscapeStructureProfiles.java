package com.thebeyond.isleweaver.compat.enderscape;

import com.thebeyond.api.worldgen.BeyondForeignStructureProfiles;
import com.thebeyond.isleweaver.Isleweaver;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

// Enderscape's End City brings a platform disc that can hang over void, where the base beard has nothing
public final class EnderscapeStructureProfiles {

    private static final String MOD = "enderscape";

    private EnderscapeStructureProfiles() {}

    public static void register() {
        if (!ModList.get().isLoaded(MOD)) return;
        // a marker, not a register(): a profile would turn off the auto-host carve class and far-field re-anchor
        BeyondForeignStructureProfiles.markBasePedestal(
                ResourceLocation.fromNamespaceAndPath(MOD, "end_city"));
        Isleweaver.LOGGER.debug("[Isleweaver] Enderscape End City gets a base pedestal under its own platform.");
    }
}

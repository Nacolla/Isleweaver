package com.thebeyond.isleweaver.compat.wover;

import com.thebeyond.api.worldgen.BeyondBiomeTagScanRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

// register Wover's End biome tags so auto-discovery finds its land biomes
public final class WoverBiomeTagRegistration {
    private WoverBiomeTagRegistration() {}

    public static void register() {
        BeyondBiomeTagScanRegistry.registerScanTag(woverTag("is_end/land"));
        BeyondBiomeTagScanRegistry.registerScanTag(woverTag("is_end/highland"));
        BeyondBiomeTagScanRegistry.registerScanTag(woverTag("is_end/midland"));
        BeyondBiomeTagScanRegistry.registerScanTag(woverTag("is_end/barrens"));
        BeyondBiomeTagScanRegistry.registerScanTag(woverTag("is_end/high_or_midland"));
        // small_island carries BetterEnd's ice_starfield and friends; drop it and their step-0 features flatten to a single Y
        BeyondBiomeTagScanRegistry.registerScanTag(woverTag("is_end/small_island"));
    }

    private static TagKey<Biome> woverTag(String path) {
        return TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("wover", path));
    }
}

package com.thebeyond.isleweaver.compat.unusualend;

import com.thebeyond.api.worldgen.BeyondBiomeRegistration;
import com.thebeyond.api.worldgen.BeyondMacroRegions;
import com.thebeyond.api.worldgen.BeyondTerrain;
import com.thebeyond.api.worldgen.MacroRegionOverride;
import com.thebeyond.util.HashSimplexNoise;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biome;

// coarse 2D simplex keeps warped_reef as one coherent macro region instead of a thin scatter
public final class WarpedReefMacroRegion implements MacroRegionOverride {
    private static final ResourceLocation BIOME_ID =
            ResourceLocation.fromNamespaceAndPath("unusualend", "warped_reef");
    private static final double MACRO_SCALE = 1.0 / 40.0;
    private static final double MACRO_THRESHOLD = 0.65;

    public static final WarpedReefMacroRegion INSTANCE = new WarpedReefMacroRegion();

    private volatile Holder<Biome> cachedHolder = null;

    private WarpedReefMacroRegion() {}

    public static void register() {
        BeyondMacroRegions.register(INSTANCE);
        BeyondBiomeRegistration.addPoolExcludedBiome(BIOME_ID);
    }

    public static void cache(MinecraftServer server) {
        INSTANCE.cachedHolder = server.registryAccess()
                .registryOrThrow(Registries.BIOME)
                .getHolder(ResourceKey.create(Registries.BIOME, BIOME_ID))
                .orElse(null);
    }

    public static void clear() {
        INSTANCE.cachedHolder = null;
    }

    @Override
    public Holder<Biome> biomeAt(int blockX, int blockZ) {
        Holder<Biome> reef = cachedHolder;
        if (reef == null) return null;
        HashSimplexNoise field = BeyondTerrain.biomeSimplexNoise();
        if (field == null) return null;
        double n = field.getValue(blockX * MACRO_SCALE, blockZ * MACRO_SCALE);
        return n > MACRO_THRESHOLD ? reef : null;
    }
}

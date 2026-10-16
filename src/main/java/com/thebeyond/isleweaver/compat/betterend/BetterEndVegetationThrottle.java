package com.thebeyond.isleweaver.compat.betterend;

import com.thebeyond.isleweaver.Isleweaver;
import com.thebeyond.isleweaver.mixin.core.CountOnEveryLayerPlacementAccessor;
import com.thebeyond.isleweaver.mixin.enderscape.PlacedFeatureAccessor;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.levelgen.placement.CountOnEveryLayerPlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;

import java.util.ArrayList;
import java.util.List;

// Beyond's count_on_every_layer mixin fires once per pancake tier, so divide BetterEnd's high counts back down
public final class BetterEndVegetationThrottle {

    private static final String NAMESPACE = "betterend";
    private static final int OVERLOAD_THRESHOLD = 8;
    private static final int TIER_DIVISOR = 5;

    private BetterEndVegetationThrottle() {}

    public static void apply(MinecraftServer server) {
        Registry<PlacedFeature> registry =
                server.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
        int fixed = 0;
        for (PlacedFeature pf : registry) {
            ResourceLocation id = registry.getKey(pf);
            if (id == null || !NAMESPACE.equals(id.getNamespace())) continue;

            List<PlacementModifier> mods = pf.placement();
            List<PlacementModifier> updated = null;
            for (int i = 0; i < mods.size(); i++) {
                if (!(mods.get(i) instanceof CountOnEveryLayerPlacement layer)) continue;
                int max = ((CountOnEveryLayerPlacementAccessor) (Object) layer)
                        .isleweaver$getCount().getMaxValue();
                if (max < OVERLOAD_THRESHOLD) continue;
                int scaled = Math.max(1, Math.round(max / (float) TIER_DIVISOR));
                if (updated == null) updated = new ArrayList<>(mods);
                updated.set(i, CountOnEveryLayerPlacement.of(UniformInt.of(0, scaled)));
            }
            if (updated != null) {
                ((PlacedFeatureAccessor) (Object) pf).isleweaver$setPlacement(List.copyOf(updated));
                fixed++;
            }
        }
        if (fixed > 0) {
            Isleweaver.LOGGER.info(
                    "[Isleweaver] Throttled {} BetterEnd count_on_every_layer placed_feature(s) "
                            + "(count max >= {}) to ~1/{} density vs Beyond per-pancake-tier expansion",
                    fixed, OVERLOAD_THRESHOLD, TIER_DIVISOR);
        }
    }
}

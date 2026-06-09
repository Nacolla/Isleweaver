package com.thebeyond.isleweaver.compat;

import com.thebeyond.isleweaver.Isleweaver;
import com.thebeyond.isleweaver.mixin.enderscape.PlacedFeatureAccessor;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.EndPlatformFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.SpikeFeature;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.CarvingMaskPlacement;
import net.minecraft.world.level.levelgen.placement.CountOnEveryLayerPlacement;
import net.minecraft.world.level.levelgen.placement.EnvironmentScanPlacement;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.HeightmapPlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

// Beyond's End source returns bottom_biome at low Y, which breaks features with no vertical placement;
// patch in a MOTION_BLOCKING heightmap before the biome filter, but only on steps 0-8
public final class EndFeatureHeightmapFix {
    private EndFeatureHeightmapFix() {}

    // VEGETAL_DECORATION onward is skipped so dense vegetation and aquatic plants keep their original placement
    private static final int FIRST_EXCLUDED_STEP = 9;

    public static void apply(MinecraftServer server) {
        RegistryAccess registryAccess = server.registryAccess();
        Registry<LevelStem> dimensions = registryAccess.registryOrThrow(Registries.LEVEL_STEM);
        LevelStem endStem = dimensions.get(LevelStem.END);
        if (endStem == null) return;
        BiomeSource endBiomeSource = endStem.generator().getBiomeSource();

        Set<PlacedFeature> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        int fixed = 0;
        for (Holder<Biome> biomeHolder : endBiomeSource.possibleBiomes()) {
            List<HolderSet<PlacedFeature>> steps = biomeHolder.value().getGenerationSettings().features();
            for (int step = 0; step < steps.size() && step < FIRST_EXCLUDED_STEP; step++) {
                for (Holder<PlacedFeature> pfHolder : steps.get(step)) {
                    PlacedFeature pf = pfHolder.value();
                    if (!visited.add(pf)) continue;
                    if (injectHeightmapIfMissing(pf)) fixed++;
                }
            }
        }
        if (fixed > 0) {
            Isleweaver.LOGGER.info(
                    "[Isleweaver] Injected MOTION_BLOCKING heightmap into {} structural End "
                            + "placed_feature(s) lacking a vertical placement (steps 0-8 only)", fixed);
        }
    }

    private static boolean injectHeightmapIfMissing(PlacedFeature pf) {
        // end spikes and the spawn platform place themselves at absolute Y; re-anchoring them to a surface
        // heightmap is wrong and makes the ring spikes spawn extra end crystals, so leave them alone
        Feature<?> feature = pf.feature().value().feature();
        if (feature instanceof SpikeFeature || feature instanceof EndPlatformFeature) return false;

        List<PlacementModifier> mods = pf.placement();
        int biomeFilterIdx = -1;
        for (int i = 0; i < mods.size(); i++) {
            PlacementModifier m = mods.get(i);
            if (m instanceof HeightmapPlacement || m instanceof HeightRangePlacement
                    || m instanceof EnvironmentScanPlacement || m instanceof CarvingMaskPlacement
                    || m instanceof CountOnEveryLayerPlacement) {
                // count_on_every_layer already drops one per pancake layer — a heightmap on top would flatten them all to the surface
                return false;
            }
            if (m instanceof BiomeFilter && biomeFilterIdx < 0) biomeFilterIdx = i;
        }
        List<PlacementModifier> updated = new ArrayList<>(mods.size() + 1);
        updated.addAll(mods);
        int insertAt = biomeFilterIdx >= 0 ? biomeFilterIdx : updated.size();
        updated.add(insertAt, HeightmapPlacement.onHeightmap(Heightmap.Types.MOTION_BLOCKING));
        ((PlacedFeatureAccessor) (Object) pf).convergence$setPlacement(List.copyOf(updated));
        return true;
    }
}

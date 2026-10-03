package com.thebeyond.isleweaver.compat;

import com.thebeyond.isleweaver.Isleweaver;
import com.thebeyond.isleweaver.mixin.enderscape.PlacedFeatureAccessor;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.EnvironmentScanPlacement;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.HeightmapPlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.RarityFilter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

// Beyond's source answers bottom_biome at low Y, so placement-less features get a heightmap (steps 0-8)
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
                    ResourceLocation id = pfHolder.unwrapKey().map(k -> k.location()).orElse(null);
                    if (injectHeightmapIfMissing(pf, id)) fixed++;
                }
            }
        }
        if (fixed > 0) {
            Isleweaver.LOGGER.debug(
                    "[Isleweaver] Injected MOTION_BLOCKING heightmap into {} structural End "
                            + "placed_feature(s) lacking a vertical placement (steps 0-8 only)", fixed);
        }
    }

    private static boolean injectHeightmapIfMissing(PlacedFeature pf, ResourceLocation id) {
        // spikes and the spawn platform place at absolute Y, a surface heightmap would spawn extra ring crystals
        Feature<?> feature = pf.feature().value().feature();
        if (feature instanceof SpikeFeature || feature instanceof EndPlatformFeature) return false;

        List<PlacementModifier> mods = pf.placement();
        int biomeFilterIdx = -1;
        boolean hasRarity = false;
        boolean hasCount = false;
        boolean hasBiomeFilter = false;
        for (int i = 0; i < mods.size(); i++) {
            PlacementModifier m = mods.get(i);
            if (m instanceof HeightmapPlacement || m instanceof HeightRangePlacement
                    || m instanceof EnvironmentScanPlacement || m instanceof CarvingMaskPlacement
                    || m instanceof CountOnEveryLayerPlacement) {
                // count_on_every_layer already places one per layer, a heightmap would flatten them to the surface
                return false;
            }
            if (m instanceof RarityFilter) hasRarity = true;
            if (m instanceof CountPlacement) hasCount = true;
            if (m instanceof BiomeFilter) {
                hasBiomeFilter = true;
                if (biomeFilterIdx < 0) biomeFilterIdx = i;
            }
        }
        // self-placing scatter (fallen_star) would stack one copy per layer with a heightmap
        if (hasRarity && !hasCount && !hasBiomeFilter) {
            Isleweaver.LOGGER.debug("[Isleweaver][EndHeightmapFix] feature={} action=SKIP_SELF_SCATTER", id);
            return false;
        }
        // crystal_spike's biome filter runs at the floor Y, the heightmap lets it reach place()
        if (hasRarity && !hasCount) {
            Isleweaver.LOGGER.debug("[Isleweaver][EndHeightmapFix] feature={} action=ANCHOR_BIOME_GATED_SCATTER", id);
        }
        List<PlacementModifier> updated = new ArrayList<>(mods.size() + 1);
        updated.addAll(mods);
        int insertAt = biomeFilterIdx >= 0 ? biomeFilterIdx : updated.size();
        updated.add(insertAt, HeightmapPlacement.onHeightmap(Heightmap.Types.MOTION_BLOCKING));
        ((PlacedFeatureAccessor) (Object) pf).isleweaver$setPlacement(List.copyOf(updated));
        return true;
    }
}

package com.thebeyond.isleweaver.compat;

import com.thebeyond.isleweaver.Isleweaver;
import com.thebeyond.isleweaver.mixin.core.BiomeGenerationSettingsAccessor;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

// one global per-step feature order for every End biome, so FeatureSorter never finds a cycle
public final class EndFeatureOrderNormalizer {
    private EndFeatureOrderNormalizer() {}

    public static void apply(MinecraftServer server) {
        RegistryAccess access = server.registryAccess();
        Registry<LevelStem> dims = access.registryOrThrow(Registries.LEVEL_STEM);
        LevelStem end = dims.get(LevelStem.END);
        if (end == null) return;
        BiomeSource src = end.generator().getBiomeSource();

        // deterministic scan order: by registry key, keyless last
        List<Holder<Biome>> biomes = new ArrayList<>(src.possibleBiomes());
        biomes.sort(Comparator.comparing(h -> h.unwrapKey()
                .map(k -> k.location().toString()).orElse("~" )));

        int maxSteps = 0;
        for (Holder<Biome> h : biomes)
            maxSteps = Math.max(maxSteps, h.value().getGenerationSettings().features().size());

        int reordered = 0;
        for (Holder<Biome> bh : biomes) {
            BiomeGenerationSettings gs = bh.value().getGenerationSettings();
            List<HolderSet<PlacedFeature>> steps = gs.features();
            List<HolderSet<PlacedFeature>> newSteps = new ArrayList<>(steps.size());
            boolean changed = false;
            for (int s = 0; s < steps.size(); s++) {
                List<Holder<PlacedFeature>> cur = new ArrayList<>(steps.get(s).stream().toList());
                Map<PlacedFeature, Integer> rank = rankForStep(biomes, s);
                List<Holder<PlacedFeature>> sorted = new ArrayList<>(cur);
                sorted.sort(Comparator.comparingInt(
                        hp -> rank.getOrDefault(hp.value(), Integer.MAX_VALUE)));
                if (!sorted.equals(cur)) changed = true;
                newSteps.add(HolderSet.direct(sorted));
            }
            if (changed) {
                ((BiomeGenerationSettingsAccessor) (Object) gs)
                        .isleweaver$setFeatures(List.copyOf(newSteps));
                reordered++;
            }
        }

        String cycleAfter = findCycle(biomes, maxSteps);
        Isleweaver.LOGGER.debug(
                "[CycleFix] End feature order normalised: {} biome(s) reordered. cycle after={}",
                reordered, cycleAfter == null ? "none" : cycleAfter);
    }

    // rank each feature by where it first shows up in step s across the scan order
    private static Map<PlacedFeature, Integer> rankForStep(List<Holder<Biome>> biomes, int s) {
        Map<PlacedFeature, Integer> rank = new IdentityHashMap<>();
        int next = 0;
        for (Holder<Biome> bh : biomes) {
            List<HolderSet<PlacedFeature>> steps = bh.value().getGenerationSettings().features();
            if (s >= steps.size()) continue;
            for (Holder<PlacedFeature> hp : steps.get(s))
                if (rank.putIfAbsent(hp.value(), next) == null) next++;
        }
        return rank;
    }

    // rebuilds FeatureSorter's per-step graph and returns the first clashing pair, null when clean
    private static String findCycle(List<Holder<Biome>> biomes, int maxSteps) {
        for (int s = 0; s < maxSteps; s++) {
            Map<PlacedFeature, Map<PlacedFeature, Boolean>> edges = new IdentityHashMap<>();
            for (Holder<Biome> bh : biomes) {
                List<HolderSet<PlacedFeature>> steps = bh.value().getGenerationSettings().features();
                if (s >= steps.size()) continue;
                List<Holder<PlacedFeature>> seq = steps.get(s).stream().toList();
                for (int i = 0; i + 1 < seq.size(); i++) {
                    PlacedFeature a = seq.get(i).value(), b = seq.get(i + 1).value();
                    if (a == b) continue;
                    edges.computeIfAbsent(a, k -> new IdentityHashMap<>()).put(b, Boolean.TRUE);
                    if (edges.getOrDefault(b, Map.of()).containsKey(a))
                        return "step " + s + " (" + key(seq.get(i)) + " <-> " + key(seq.get(i + 1)) + ")";
                }
            }
        }
        return null;
    }

    private static String key(Holder<PlacedFeature> h) {
        return h.unwrapKey().map(ResourceKey::location).map(Object::toString).orElse("?");
    }
}

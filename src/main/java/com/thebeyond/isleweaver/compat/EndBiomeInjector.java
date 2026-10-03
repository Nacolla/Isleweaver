package com.thebeyond.isleweaver.compat;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.thebeyond.isleweaver.Isleweaver;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.TheEndBiomeSource;
import net.minecraft.world.level.dimension.LevelStem;

import java.util.*;

// Soup mode only: Beyond biomes enter Enderscape's climate boxes or the vanilla End source
public final class EndBiomeInjector {

    private static final String BEYOND_NAMESPACE = "the_beyond";

    private static final ResourceKey<Biome> ATTRACTA_EXPANSE = ResourceKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(BEYOND_NAMESPACE, "attracta_expanse"));
    private static final ResourceKey<Biome> PEER_LANDS = ResourceKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(BEYOND_NAMESPACE, "peer_lands"));
    private static final ResourceKey<Biome> THE_PATHS = ResourceKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(BEYOND_NAMESPACE, "the_paths"));
    private static final ResourceKey<Biome> TRUE_VOID = ResourceKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(BEYOND_NAMESPACE, "true_void"));
    private static final ResourceKey<Biome> FUMAROLE_UPLANDS = ResourceKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(BEYOND_NAMESPACE, "fumarole_uplands"));
    private static final ResourceKey<Biome> ENDERSCAPE_VOID_DEPTHS = ResourceKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath("enderscape", "void_depths"));

    // read by TheEndBiomeSourceMixin, null unless the End source is vanilla
    public static volatile VanillaEndHolders vanillaEndHolders;

    private EndBiomeInjector() {}

    public static class VanillaEndHolders {
        public final Holder<Biome> attractaExpanse;
        public final Holder<Biome> peerLands;
        public final Holder<Biome> trueVoid;
        public final Holder<Biome> thePaths;
        public final Holder<Biome> fumaroleUplands;

        public VanillaEndHolders(Holder<Biome> attractaExpanse, Holder<Biome> peerLands,
                                  Holder<Biome> trueVoid, Holder<Biome> thePaths,
                                  Holder<Biome> fumaroleUplands) {
            this.attractaExpanse = attractaExpanse;
            this.peerLands = peerLands;
            this.trueVoid = trueVoid;
            this.thePaths = thePaths;
            this.fumaroleUplands = fumaroleUplands;
        }
    }

    public static void injectBiomes(MinecraftServer server) {
        RegistryAccess registryAccess = server.registryAccess();

        Registry<LevelStem> dimensions = registryAccess.registryOrThrow(Registries.LEVEL_STEM);
        LevelStem endStem = dimensions.get(LevelStem.END);
        if (endStem == null) {
            Isleweaver.LOGGER.warn("[Isleweaver] End dimension stem not found in registry, skipping biome injection");
            return;
        }

        BiomeSource biomeSource = endStem.generator().getBiomeSource();

        if (biomeSource instanceof MultiNoiseBiomeSource multiNoise) {
            injectIntoMultiNoise(multiNoise, registryAccess);
        } else if (biomeSource instanceof TheEndBiomeSource) {
            setupVanillaEnd(biomeSource, registryAccess);
        } else {
            Isleweaver.LOGGER.debug("[Isleweaver] End biome source is {} (unhandled type), skipping injection",
                    biomeSource.getClass().getSimpleName());
        }
    }

    // Slices the existing End biome boxes to seat the Beyond biomes without competing for climate space or risking a tie-break.
    private static void injectIntoMultiNoise(MultiNoiseBiomeSource multiNoise, RegistryAccess registryAccess) {
        Registry<Biome> biomeRegistry = registryAccess.registryOrThrow(Registries.BIOME);

        Climate.ParameterList<Holder<Biome>> paramList = multiNoise.parameters
                .map(direct -> direct, preset -> preset.value().parameters());

        List<Pair<Climate.ParameterPoint, Holder<Biome>>> merged = new ArrayList<>(paramList.values());

        int added = 0;
        added += splitBox(merged, biomeRegistry, Biomes.END_HIGHLANDS, FUMAROLE_UPLANDS, ATTRACTA_EXPANSE);
        added += splitBox(merged, biomeRegistry, Biomes.END_MIDLANDS, PEER_LANDS);
        added += splitBox(merged, biomeRegistry, Biomes.SMALL_END_ISLANDS, TRUE_VOID);
        added += splitBox(merged, biomeRegistry, ENDERSCAPE_VOID_DEPTHS, THE_PATHS);

        if (added == 0) return;

        multiNoise.parameters = Either.left(new Climate.ParameterList<>(merged));

        Set<Holder<Biome>> allBiomes = merged.stream()
                .map(Pair::getSecond)
                .distinct()
                .collect(ImmutableSet.toImmutableSet());
        ((BiomeSource) multiNoise).possibleBiomes = Suppliers.memoize(() -> allBiomes);

        Isleweaver.LOGGER.debug("[Isleweaver] Sliced {} Beyond biome band(s) into End MultiNoiseBiomeSource ({} entries total)",
                added, merged.size());
    }

    // stages the holders only, TheEndBiomeSourceMixin does the replacement
    private static void setupVanillaEnd(BiomeSource biomeSource, RegistryAccess registryAccess) {
        Registry<Biome> biomeRegistry = registryAccess.registryOrThrow(Registries.BIOME);

        Holder<Biome> attracta = biomeRegistry.getHolder(ATTRACTA_EXPANSE).orElse(null);
        Holder<Biome> peer = biomeRegistry.getHolder(PEER_LANDS).orElse(null);
        Holder<Biome> trueV = biomeRegistry.getHolder(TRUE_VOID).orElse(null);
        Holder<Biome> paths = biomeRegistry.getHolder(THE_PATHS).orElse(null);
        Holder<Biome> fumarole = biomeRegistry.getHolder(FUMAROLE_UPLANDS).orElse(null);

        if (attracta == null && peer == null && trueV == null && paths == null && fumarole == null) {
            Isleweaver.LOGGER.warn("[Isleweaver] No Beyond biomes found in registry, skipping vanilla End setup");
            return;
        }

        vanillaEndHolders = new VanillaEndHolders(attracta, peer, trueV, paths, fumarole);

        // keep the possibleBiomes cache in sync so /locate can find these
        Set<Holder<Biome>> allBiomes = new HashSet<>(biomeSource.possibleBiomes());
        if (attracta != null) allBiomes.add(attracta);
        if (peer != null) allBiomes.add(peer);
        if (trueV != null) allBiomes.add(trueV);
        if (paths != null) allBiomes.add(paths);
        if (fumarole != null) allBiomes.add(fumarole);
        ImmutableSet<Holder<Biome>> finalBiomes = ImmutableSet.copyOf(allBiomes);
        biomeSource.possibleBiomes = Suppliers.memoize(() -> finalBiomes);

        Isleweaver.LOGGER.debug("[Isleweaver] Set up vanilla End biome injection (TheEndBiomeSourceMixin active)");
    }

    // continentalness bands spread each Beyond biome over separate islands, a depth split would stack them
    @SafeVarargs
    private static int splitBox(List<Pair<Climate.ParameterPoint, Holder<Biome>>> merged,
                                Registry<Biome> registry, ResourceKey<Biome> existingKey,
                                ResourceKey<Biome>... beyondKeys) {
        int idx = -1;
        for (int i = 0; i < merged.size(); i++) {
            if (merged.get(i).getSecond().is(existingKey)) { idx = i; break; }
        }
        if (idx < 0) {
            Isleweaver.LOGGER.debug("[Isleweaver] {} not in End parameter list, skipping split", existingKey.location());
            return 0;
        }

        List<Holder<Biome>> beyond = new ArrayList<>();
        for (ResourceKey<Biome> key : beyondKeys) {
            Optional<Holder.Reference<Biome>> holder = registry.getHolder(key);
            if (holder.isPresent()) beyond.add(holder.get());
            else Isleweaver.LOGGER.warn("[Isleweaver] Biome {} not in registry, skipping split band", key.location());
        }
        if (beyond.isEmpty()) return 0;

        Climate.ParameterPoint point = merged.get(idx).getFirst();
        Holder<Biome> original = merged.get(idx).getSecond();
        Climate.Parameter cont = point.continentalness();
        float min = cont.min() / 10000.0F;
        float max = cont.max() / 10000.0F;
        int bands = beyond.size() + 1;
        float step = (max - min) / bands;

        merged.set(idx, Pair.of(withContinentalness(point, min, min + step), original));
        for (int b = 1; b < bands; b++) {
            float lo = min + step * b;
            float hi = (b == bands - 1) ? max : min + step * (b + 1);
            merged.add(Pair.of(withContinentalness(point, lo, hi), beyond.get(b - 1)));
        }
        return beyond.size();
    }

    private static Climate.ParameterPoint withContinentalness(Climate.ParameterPoint p, float min, float max) {
        return Climate.parameters(p.temperature(), p.humidity(), Climate.Parameter.span(min, max),
                p.erosion(), p.depth(), p.weirdness(), p.offset());
    }
}

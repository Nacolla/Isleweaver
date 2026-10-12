package com.thebeyond.isleweaver.compat;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.thebeyond.isleweaver.Isleweaver;
import com.thebeyond.util.VoronoiNoise;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
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
    private static final int BOTTOM_LAYER_HEIGHT = 10;
    private static final List<ResourceLocation> END_TAGS = List.of(
            ResourceLocation.withDefaultNamespace("is_end"),
            ResourceLocation.fromNamespaceAndPath("c", "is_end"));
    private static final List<ResourceKey<Biome>> LAND_ZONES = List.of(
            ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(BEYOND_NAMESPACE, "fumarole_uplands")),
            ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(BEYOND_NAMESPACE, "attracta_expanse")),
            ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(BEYOND_NAMESPACE, "peer_lands")),
            ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(BEYOND_NAMESPACE, "lustrous_echoes")),
            ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(BEYOND_NAMESPACE, "chestral_hollows")));

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
    private static final ResourceKey<Biome> LUSTROUS_ECHOES = ResourceKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(BEYOND_NAMESPACE, "lustrous_echoes"));
    private static final ResourceKey<Biome> CHESTRAL_HOLLOWS = ResourceKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(BEYOND_NAMESPACE, "chestral_hollows"));
    private static final ResourceKey<Biome> ENDERSCAPE_VOID_DEPTHS = ResourceKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath("enderscape", "void_depths"));

    // read by TheEndBiomeSourceMixin, null unless the End source is vanilla
    public static volatile VanillaEndHolders vanillaEndHolders;

    private EndBiomeInjector() {}

    public static class VanillaEndHolders {
        private static final double ZONE_SIZE = 320.0;

        public final Holder<Biome> trueVoid;
        public final Holder<Biome> thePaths;
        public final int bottomLayerTop;
        private final List<Holder<Biome>> zones;
        private final VoronoiNoise zoneNoise;

        public VanillaEndHolders(Holder<Biome> trueVoid, Holder<Biome> thePaths, int bottomLayerTop,
                                  List<Holder<Biome>> zones, long seed) {
            this.trueVoid = trueVoid;
            this.thePaths = thePaths;
            this.bottomLayerTop = bottomLayerTop;
            this.zones = zones;
            this.zoneNoise = new VoronoiNoise(seed, (short) 0);
        }

        public Holder<Biome> zoneAt(int blockX, int blockZ) {
            long id = zoneNoise.getCell(blockX, blockZ, 1.0 / ZONE_SIZE).cellId();
            return zones.get((int) Math.floorMod((id * 0x9E3779B97F4A7C15L) >>> 33, (long) zones.size()));
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
            setupVanillaEnd(biomeSource, registryAccess, endStem.type().value().minY() + BOTTOM_LAYER_HEIGHT,
                    server.getWorldData().worldGenOptions().seed());
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
        added += splitBox(merged, biomeRegistry, Biomes.END_HIGHLANDS, FUMAROLE_UPLANDS, ATTRACTA_EXPANSE, LUSTROUS_ECHOES);
        added += splitBox(merged, biomeRegistry, Biomes.END_MIDLANDS, PEER_LANDS, CHESTRAL_HOLLOWS);
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
    private static void setupVanillaEnd(BiomeSource biomeSource, RegistryAccess registryAccess, int bottomLayerTop, long seed) {
        Registry<Biome> biomeRegistry = registryAccess.registryOrThrow(Registries.BIOME);

        Holder<Biome> trueV = biomeRegistry.getHolder(TRUE_VOID).orElse(null);
        Holder<Biome> paths = biomeRegistry.getHolder(THE_PATHS).orElse(null);
        List<Holder<Biome>> beyondZones = new ArrayList<>();
        for (ResourceKey<Biome> key : LAND_ZONES) biomeRegistry.getHolder(key).ifPresent(beyondZones::add);
        Set<String> otherProviders = foreignEndNamespaces(biomeRegistry, biomeSource);

        if (beyondZones.isEmpty() && trueV == null && paths == null) {
            Isleweaver.LOGGER.warn("[Isleweaver] No Beyond biomes found in registry, skipping vanilla End setup");
            return;
        }

        List<Holder<Biome>> zones = new ArrayList<>();
        for (int i = Math.max(1, beyondZones.size() * otherProviders.size()); i > 0; i--) zones.add(null);
        zones.addAll(beyondZones);
        vanillaEndHolders = new VanillaEndHolders(trueV, paths, bottomLayerTop, zones, seed);
        Isleweaver.LOGGER.debug("[Isleweaver] Vanilla End land shared with {} other biome provider(s) {}, {} of {} zones left vanilla",
                otherProviders.size(), otherProviders, zones.size() - beyondZones.size(), zones.size());

        // keep the possibleBiomes cache in sync so /locate can find these
        Set<Holder<Biome>> allBiomes = new HashSet<>(biomeSource.possibleBiomes());
        for (Holder<Biome> zone : zones) if (zone != null) allBiomes.add(zone);
        if (trueV != null) allBiomes.add(trueV);
        if (paths != null) allBiomes.add(paths);
        ImmutableSet<Holder<Biome>> finalBiomes = ImmutableSet.copyOf(allBiomes);
        biomeSource.possibleBiomes = Suppliers.memoize(() -> finalBiomes);

        Isleweaver.LOGGER.debug("[Isleweaver] Set up vanilla End biome injection (TheEndBiomeSourceMixin active)");
    }

    private static Set<String> foreignEndNamespaces(Registry<Biome> registry, BiomeSource source) {
        Set<String> namespaces = new TreeSet<>();
        for (ResourceLocation tag : END_TAGS) {
            registry.getTag(TagKey.create(Registries.BIOME, tag)).ifPresent(set -> set.forEach(h -> addForeign(namespaces, h)));
        }
        source.possibleBiomes().forEach(h -> addForeign(namespaces, h));
        return namespaces;
    }

    private static void addForeign(Set<String> out, Holder<Biome> biome) {
        biome.unwrapKey().map(k -> k.location().getNamespace())
                .filter(ns -> !ns.equals("minecraft") && !ns.equals(BEYOND_NAMESPACE))
                .ifPresent(out::add);
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

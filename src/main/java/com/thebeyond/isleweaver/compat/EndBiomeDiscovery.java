package com.thebeyond.isleweaver.compat;

import com.mojang.datafixers.util.Pair;
import com.thebeyond.api.event.BeyondBiomeDiscoveryEvent;
import com.thebeyond.api.worldgen.BeyondBiomeTagScanRegistry;
import com.thebeyond.api.worldgen.BeyondEndBiomeSourceApi;
import com.thebeyond.isleweaver.Isleweaver;
import com.thebeyond.isleweaver.compat.biomesoplenty.BopBiomeConfigFilter;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.random.WeightedEntry;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.TheEndBiomeSource;
import net.minecraft.world.level.dimension.LevelStem;
import net.neoforged.neoforge.common.NeoForge;

import java.lang.reflect.RecordComponent;
import java.util.*;

// finds End biomes (tags, vanilla source mixins, TerraBlender and Lithostitched registries) for Beyond's tainted pool
public final class EndBiomeDiscovery {

    private static final String BEYOND_NAMESPACE = "the_beyond";

    private static final TagKey<Biome> IS_END = TagKey.create(Registries.BIOME,
            ResourceLocation.withDefaultNamespace("is_end"));

    // the c: convention tag shared across the NeoForge/Fabric ecosystem
    private static final TagKey<Biome> C_IS_END_BIOME = TagKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath("c", "is_end_biome"));

    private EndBiomeDiscovery() {}

    public static void discoverAndInject(MinecraftServer server) {
        RegistryAccess registryAccess = server.registryAccess();

        Registry<LevelStem> dimensions = registryAccess.registryOrThrow(Registries.LEVEL_STEM);
        LevelStem endStem = dimensions.get(LevelStem.END);
        if (endStem == null) {
            Isleweaver.LOGGER.warn("[Isleweaver] End dimension stem not found, skipping biome auto-discovery");
            return;
        }

        BiomeSource biomeSource = endStem.generator().getBiomeSource();
        if (!(biomeSource instanceof BeyondEndBiomeSourceApi beyondSource)) {
            Isleweaver.LOGGER.debug("[Isleweaver] End biome source is not Beyond's, skipping auto-discovery");
            return;
        }

        Registry<Biome> biomeRegistry = registryAccess.registryOrThrow(Registries.BIOME);

        Map<ResourceKey<Biome>, Holder<Biome>> candidates = new LinkedHashMap<>();
        collectFromTag(biomeRegistry, IS_END, candidates);
        collectFromTag(biomeRegistry, C_IS_END_BIOME, candidates);
        for (TagKey<Biome> extraTag : BeyondBiomeTagScanRegistry.getRegisteredTags()) {
            collectFromTag(biomeRegistry, extraTag, candidates);
        }
        collectFromVanillaEndBiomeSource(registryAccess, biomeRegistry, candidates);
        int fromTerraBlender = collectFromTerraBlender(biomeRegistry, candidates);
        int fromLithostitched = collectFromLithostitched(registryAccess, candidates);

        NeoForge.EVENT_BUS.post(new BeyondBiomeDiscoveryEvent(server, biomeRegistry, candidates));

        // run last so it also prunes anything the event added, per BoP's biome-disable config
        BopBiomeConfigFilter.filterDisabled(candidates);

        if (candidates.isEmpty()) {
            Isleweaver.LOGGER.debug("[Isleweaver] No End biomes found via tags, nothing to auto-discover");
            return;
        }

        int injected = beyondSource.injectBiomesIntoTaintedPool(candidates.values());
        Isleweaver.LOGGER.info("[Isleweaver] End biomes for the Beyond pool: {} new of {} found"
                + " (TerraBlender {}, Lithostitched {})", injected, candidates.size(), fromTerraBlender, fromLithostitched);

        Set<String> namespaces = foreignNamespaces(candidates.values());
        if (injected > 0) {
            Isleweaver.LOGGER.debug("[Isleweaver] Auto-discovered {} End biome(s) from tags and injected into tainted pool. " +
                    "Contributing mod namespaces: {}", injected, namespaces);
        } else {
            Isleweaver.LOGGER.debug("[Isleweaver] All tagged End biomes already present in biome pools, nothing new to inject. " +
                    "Contributing mod namespaces: {}", namespaces);
        }

        if (Isleweaver.LOGGER.isDebugEnabled()) {
            List<String> ids = candidates.keySet().stream()
                    .map(k -> k.location().toString()).sorted().toList();
            Isleweaver.LOGGER.debug("[Isleweaver] End discovery placed {} biome(s) into the pool: {}", ids.size(), ids);
        }
    }

    public static Set<String> foreignNamespaces(Iterable<Holder<Biome>> holders) {
        Set<String> out = new TreeSet<>();
        for (Holder<Biome> h : holders) {
            h.unwrapKey().ifPresent(k -> {
                String ns = k.location().getNamespace();
                if (!ns.equals("minecraft") && !ns.equals(BEYOND_NAMESPACE)) out.add(ns);
            });
        }
        return out;
    }

    // foreign is_end namespaces only, safe in soup mode where discovery never runs
    public static Set<String> scanEndModNamespaces(MinecraftServer server) {
        Registry<Biome> biomeRegistry = server.registryAccess().registryOrThrow(Registries.BIOME);
        return foreignNamespaces(biomeRegistry.getTagOrEmpty(IS_END));
    }

    private static void collectFromTag(Registry<Biome> registry, TagKey<Biome> tag,
                                        Map<ResourceKey<Biome>, Holder<Biome>> out) {
        Iterable<Holder<Biome>> entries = registry.getTagOrEmpty(tag);
        for (Holder<Biome> holder : entries) {
            holder.unwrapKey().ifPresent(key -> out.putIfAbsent(key, holder));
        }
    }

    // TerraBlender hands its End biomes only to a TheEndBiomeSource it initializes itself, which the Beyond's End never is
    private static int collectFromTerraBlender(Registry<Biome> registry, Map<ResourceKey<Biome>, Holder<Biome>> out) {
        Class<?> endRegistry;
        try {
            endRegistry = Class.forName("terrablender.api.EndBiomeRegistry");
        } catch (ClassNotFoundException e) {
            return 0;
        }
        int before = out.size();
        for (String getter : new String[]{"getHighlandsBiomes", "getMidlandsBiomes", "getEdgeBiomes", "getIslandBiomes"}) {
            try {
                if (!(endRegistry.getMethod(getter).invoke(null) instanceof List<?> entries)) continue;
                for (Object entry : entries) {
                    if (entry instanceof WeightedEntry.Wrapper<?> w && w.data() instanceof ResourceKey<?> k) {
                        ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, k.location());
                        registry.getHolder(key).ifPresent(h -> out.putIfAbsent(key, h));
                    }
                }
            } catch (Throwable t) {
                Isleweaver.LOGGER.warn("[Isleweaver] TerraBlender End biome read failed at {}", getter, t);
            }
        }
        return out.size() - before;
    }

    private static final ResourceKey<Registry<Object>> LITHOSTITCHED_INJECTORS =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("lithostitched", "biome_injector"));

    // the Beyond source is in #lithostitched:cannot_inject_into, so End injector biomes join the pool (targets skipped)
    private static int collectFromLithostitched(RegistryAccess access, Map<ResourceKey<Biome>, Holder<Biome>> out) {
        Optional<Registry<Object>> injectors = access.registry(LITHOSTITCHED_INJECTORS);
        if (injectors.isEmpty()) return 0;
        int before = out.size();
        for (Object injector : injectors.get()) {
            try {
                if (!(injector instanceof Record record)
                        || !LevelStem.END.equals(record.getClass().getMethod("dimension").invoke(record))) continue;
                for (RecordComponent component : record.getClass().getRecordComponents()) {
                    Object value = component.getAccessor().invoke(record);
                    if (value instanceof Climate.ParameterList<?> points) {
                        for (Pair<Climate.ParameterPoint, ?> point : points.values()) addBiome(point.getSecond(), out);
                    } else {
                        addBiome(value, out);
                    }
                }
            } catch (Throwable t) {
                Isleweaver.LOGGER.warn("[Isleweaver] Lithostitched biome injector read failed for {}",
                        injector.getClass().getName(), t);
            }
        }
        return out.size() - before;
    }

    @SuppressWarnings("unchecked")
    private static void addBiome(Object value, Map<ResourceKey<Biome>, Holder<Biome>> out) {
        if (value instanceof Holder<?> holder && holder.isBound() && holder.value() instanceof Biome) {
            Holder<Biome> biome = (Holder<Biome>) holder;
            biome.unwrapKey().ifPresent(key -> out.putIfAbsent(key, biome));
        }
    }

    // a throwaway TheEndBiomeSource lets construction mixins (Phantasm, UnusualEnd, Biolith) add their biomes
    private static void collectFromVanillaEndBiomeSource(
            RegistryAccess registryAccess,
            Registry<Biome> biomeRegistry,
            Map<ResourceKey<Biome>, Holder<Biome>> out) {
        try {
            HolderGetter<Biome> getter = registryAccess.lookupOrThrow(Registries.BIOME);
            TheEndBiomeSource transientSource = TheEndBiomeSource.create(getter);

            // mixins hand these over in hash order, which changes between runs, and the pool picks by position
            List<Holder<Biome>> found = new ArrayList<>();
            for (Holder<Biome> holder : transientSource.possibleBiomes()) {
                holder.unwrapKey().filter(key -> !out.containsKey(key)).ifPresent(key -> found.add(holder));
            }
            found.sort(Comparator.comparing(h -> h.unwrapKey().orElseThrow().location().toString()));
            for (Holder<Biome> holder : found) out.put(holder.unwrapKey().orElseThrow(), holder);
            if (!found.isEmpty()) {
                Isleweaver.LOGGER.debug("[Isleweaver] Discovered {} biome(s) via transient TheEndBiomeSource "
                                + "(mods using mixin-based End injection): {}",
                        found.size(), found.stream().map(h -> h.unwrapKey().orElseThrow().location().toString()).toList());
            }
        } catch (Throwable t) {
            Isleweaver.LOGGER.debug("[Isleweaver] Transient TheEndBiomeSource discovery failed (non-fatal): {}",
                    t.toString());
        }
    }
}

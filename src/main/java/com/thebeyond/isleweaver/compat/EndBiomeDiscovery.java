package com.thebeyond.isleweaver.compat;

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
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.TheEndBiomeSource;
import net.minecraft.world.level.dimension.LevelStem;
import net.neoforged.neoforge.common.NeoForge;

import java.util.*;

// finds End biomes by their is_end tags and feeds them into Beyond's tainted pool — tag-driven, no hardcoded mod list
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

        // lets other mods' scanners contribute candidates before the empty check below
        NeoForge.EVENT_BUS.post(new BeyondBiomeDiscoveryEvent(server, biomeRegistry, candidates));

        // run last so it also prunes anything the event added, per BoP's biome-disable config
        BopBiomeConfigFilter.filterDisabled(candidates);

        if (candidates.isEmpty()) {
            Isleweaver.LOGGER.info("[Isleweaver] No End biomes found via tags — nothing to auto-discover");
            return;
        }

        int injected = beyondSource.injectBiomesIntoTaintedPool(candidates.values());

        Set<String> namespaces = foreignNamespaces(candidates.values());
        if (injected > 0) {
            Isleweaver.LOGGER.info("[Isleweaver] Auto-discovered {} End biome(s) from tags and injected into tainted pool. " +
                    "Contributing mod namespaces: {}", injected, namespaces);
        } else {
            Isleweaver.LOGGER.info("[Isleweaver] All tagged End biomes already present in biome pools — nothing new to inject. " +
                    "Contributing mod namespaces: {}", namespaces);
        }
    }

    // foreign namespaces only; skip vanilla and Beyond itself
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

    // foreign is_end namespaces only; safe to call in soup mode where discover-and-inject never runs
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

    // spins up a throwaway TheEndBiomeSource so construction-hooking mixins (Phantasm, UnusualEnd) fire, then grabs its possibleBiomes()
    private static void collectFromVanillaEndBiomeSource(
            RegistryAccess registryAccess,
            Registry<Biome> biomeRegistry,
            Map<ResourceKey<Biome>, Holder<Biome>> out) {
        try {
            HolderGetter<Biome> getter = registryAccess.lookupOrThrow(Registries.BIOME);
            TheEndBiomeSource transientSource = TheEndBiomeSource.create(getter);

            int before = out.size();
            for (Holder<Biome> holder : transientSource.possibleBiomes()) {
                holder.unwrapKey().ifPresent(key -> out.putIfAbsent(key, holder));
            }
            int discovered = out.size() - before;
            if (discovered > 0) {
                Isleweaver.LOGGER.debug(
                        "[Isleweaver] Discovered {} biome(s) via transient TheEndBiomeSource (mods using mixin-based End injection)",
                        discovered);
            }
        } catch (Throwable t) {
            Isleweaver.LOGGER.debug("[Isleweaver] Transient TheEndBiomeSource discovery failed (non-fatal): {}",
                    t.toString());
        }
    }
}

package com.thebeyond.isleweaver.compat;

import com.thebeyond.api.worldgen.BeyondEndBiomeSourceApi;
import com.thebeyond.isleweaver.Isleweaver;
import com.thebeyond.isleweaver.IsleweaverConfig;
import com.thebeyond.isleweaver.compat.biomesoplenty.BopBiomeConfigFilter;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

// Isleweaver's disable list (any End biome) plus each End mod's own toggle (discovered biomes only), applied to the Beyond pool
public final class EndBiomeToggle {

    private static final ResourceKey<Registry<Object>> WOVER_BIOME_DATA =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("wover", "worldgen/biome_data"));
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private EndBiomeToggle() {}

    public record Result(Map<ResourceKey<Biome>, String> off, Method stableDisable) {
        public int disable(BeyondEndBiomeSourceApi source) {
            if (off.isEmpty() || stableDisable == null) return off.size();
            try {
                return (int) stableDisable.invoke(source, off.keySet());
            } catch (Throwable t) {
                Isleweaver.LOGGER.error("[Isleweaver] Could not turn off {} End biome(s) in the Beyond pool", off.size(), t);
                return 0;
            }
        }

        public String summary() {
            if (off.isEmpty()) return "";
            Map<String, Long> bySource = off.values().stream()
                    .collect(Collectors.groupingBy(s -> s, TreeMap::new, Collectors.counting()));
            return " " + bySource + (stableDisable != null ? " in place" : " before injection");
        }
    }

    public static Result apply(RegistryAccess access, BiomeSource source, BeyondEndBiomeSourceApi beyond,
                               Map<ResourceKey<Biome>, Holder<Biome>> candidates) {
        Set<ResourceKey<Biome>> base = source.possibleBiomes().stream()
                .flatMap(h -> h.unwrapKey().stream()).collect(Collectors.toSet());
        Map<ResourceKey<Biome>, String> off = new LinkedHashMap<>();

        Set<ResourceKey<Biome>> known = new LinkedHashSet<>(base);
        known.addAll(candidates.keySet());
        List<String> ownLists = new ArrayList<>();
        for (String raw : IsleweaverConfig.DISABLED_END_BIOMES.get()) {
            String entry = raw.trim();
            List<ResourceKey<Biome>> hits;
            if (entry.endsWith(":*")) {
                String namespace = entry.substring(0, entry.length() - 2);
                hits = known.stream().filter(k -> k.location().getNamespace().equals(namespace)).toList();
            } else {
                ResourceLocation id = ResourceLocation.tryParse(entry);
                ResourceKey<Biome> key = id == null ? null : ResourceKey.create(Registries.BIOME, id);
                hits = key != null && known.contains(key) ? List.of(key) : List.of();
            }
            if (hits.isEmpty()) warnOnce(entry, "matches no End biome in this pack");
            hits.forEach(k -> off.putIfAbsent(k, "config"));
            if (hits.stream().anyMatch(base::contains)) ownLists.add(entry);
        }

        Optional<Registry<Object>> wover = access.registry(WOVER_BIOME_DATA);
        for (ResourceKey<Biome> key : candidates.keySet()) {
            if (off.containsKey(key) || base.contains(key)) continue;
            if (wover.isPresent() && !enabled(wover.get().get(key.location()))) off.put(key, "WorldWeaver");
        }

        List<ResourceKey<Biome>> judged = candidates.keySet().stream().filter(k -> !off.containsKey(k) && !base.contains(k)).toList();
        BopBiomeConfigFilter.disabledAmong(judged).forEach(k -> off.put(k, "BoP"));

        long foreign = candidates.keySet().stream().filter(k -> !base.contains(k)).count();
        if (foreign > 0 && candidates.keySet().stream().filter(k -> !base.contains(k)).allMatch(off::containsKey)) {
            Isleweaver.LOGGER.warn("[Isleweaver] Every discovered End biome is toggled off, the pool keeps only the Beyond's own lists");
        }

        Method stable = null;
        try {
            stable = beyond.getClass().getMethod("disableBiomes", Collection.class);
        } catch (NoSuchMethodException older) {
            off.keySet().forEach(candidates::remove);
            off.keySet().removeIf(base::contains);
            if (!ownLists.isEmpty()) {
                Isleweaver.LOGGER.warn("[Isleweaver] This Beyond version cannot turn off its own End biomes, these entries do nothing: {}", ownLists);
            }
            ownLists.clear();
        }
        if (!ownLists.isEmpty()) {
            Isleweaver.LOGGER.warn("[Isleweaver] disabledBiomes turns off biomes of the Beyond's own End lists, progression may break: {}", ownLists);
        }
        if (!off.isEmpty()) {
            Isleweaver.LOGGER.debug("[Isleweaver] End biomes toggled off: {}", off);
        }
        return new Result(off, stable);
    }

    private static boolean enabled(Object biomeData) {
        if (biomeData == null) return true;
        try {
            return (boolean) biomeData.getClass().getMethod("isEnabled").invoke(biomeData);
        } catch (Throwable t) {
            Isleweaver.LOGGER.debug("[Isleweaver] WorldWeaver isEnabled failed on {}: {}", biomeData.getClass().getName(), t.toString());
            return true;
        }
    }

    private static void warnOnce(String entry, String why) {
        if (WARNED.add(entry)) Isleweaver.LOGGER.warn("[Isleweaver] disabledBiomes entry \"{}\" {}", entry, why);
    }
}

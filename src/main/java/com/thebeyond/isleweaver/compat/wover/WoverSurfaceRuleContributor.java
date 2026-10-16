package com.thebeyond.isleweaver.compat.wover;

import com.thebeyond.api.event.BeyondSurfaceRuleContributionEvent;
import com.thebeyond.isleweaver.Isleweaver;
import com.thebeyond.isleweaver.compat.SurfaceRuleOrdering;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.SurfaceRules;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// pulls Wover's per-biome rules out of SURFACE_RULES_REGISTRY (via reflection) into Beyond's merge
public final class WoverSurfaceRuleContributor {
    private WoverSurfaceRuleContributor() {}

    public static void onBeyondSurfaceRuleContribution(BeyondSurfaceRuleContributionEvent event) {
        WoverRegistryAccess access = WoverRegistryAccess.INSTANCE;
        if (access == null) return;

        for (SurfaceRules.RuleSource rule : collectFor(event.getRegistryAccess(), event.getEndBiomeSource(), access)) {
            event.contribute(rule);
        }
    }

    private static List<SurfaceRules.RuleSource> collectFor(
            RegistryAccess registryAccess, BiomeSource biomeSource, WoverRegistryAccess access) {

        @SuppressWarnings("unchecked")
        ResourceKey<Registry<Object>> key = (ResourceKey<Registry<Object>>) access.registryKey;
        Registry<Object> registry = registryAccess.registry(key).orElse(null);
        if (registry == null) return List.of();

        Map<ResourceLocation, List<Entry>> byBiome = new HashMap<>();
        try {
            for (Object assigned : registry) {
                if (assigned == null) continue;
                ResourceLocation id = (ResourceLocation) access.biomeIdField.get(assigned);
                if (id == null) continue;
                SurfaceRules.RuleSource rule = (SurfaceRules.RuleSource) access.ruleSourceField.get(assigned);
                if (rule == null) continue;
                int priority = access.priorityField.getInt(assigned);
                byBiome.computeIfAbsent(id, k -> new ArrayList<>()).add(new Entry(rule, priority));
            }
        } catch (IllegalAccessException e) {
            Isleweaver.LOGGER.warn("[Isleweaver] Failed to read Wover AssignedSurfaceRule fields; per-biome rules skipped", e);
            return List.of();
        }
        if (byBiome.isEmpty()) return List.of();

        List<SurfaceRules.RuleSource> out = new ArrayList<>();
        int ruleCount = 0;
        for (Holder<Biome> holder : biomeSource.possibleBiomes()) {
            ResourceKey<Biome> biomeKey = holder.unwrapKey().orElse(null);
            if (biomeKey == null) continue;
            List<Entry> matches = byBiome.get(biomeKey.location());
            if (matches == null || matches.isEmpty()) continue;
            // high priority first, same as Wover's own SequenceRuleSource, so overrides still win
            matches.sort((a, b) -> b.priority - a.priority);
            SurfaceRules.RuleSource[] perBiome = new SurfaceRules.RuleSource[matches.size()];
            for (int i = 0; i < matches.size(); i++) {
                perBiome[i] = normalizeFillerOrdering(matches.get(i).rule);
            }
            out.add(SurfaceRules.ifTrue(
                    SurfaceRules.isBiome(biomeKey),
                    SurfaceRules.sequence(perBiome)));
            ruleCount += perBiome.length;
        }
        if (ruleCount > 0) {
            Isleweaver.LOGGER.info(
                    "[Isleweaver] Collected {} per-biome surface rules from Wover SURFACE_RULES_REGISTRY across {} biomes",
                    ruleCount, out.size());
        }
        return out;
    }

    private record Entry(SurfaceRules.RuleSource rule, int priority) {}

    // push unconditional fillers to the end, or Wover's priority-900 filler short-circuits the match
    private static SurfaceRules.RuleSource normalizeFillerOrdering(SurfaceRules.RuleSource rule) {
        return SurfaceRuleOrdering.bubbleTerminatorsLast(rule, false);
    }

    private static final class WoverRegistryAccess {
        static final WoverRegistryAccess INSTANCE = tryCreate();

        final ResourceKey<? extends Registry<?>> registryKey;
        final Field biomeIdField;
        final Field ruleSourceField;
        final Field priorityField;

        private WoverRegistryAccess(ResourceKey<? extends Registry<?>> registryKey,
                                     Field biomeIdField, Field ruleSourceField, Field priorityField) {
            this.registryKey = registryKey;
            this.biomeIdField = biomeIdField;
            this.ruleSourceField = ruleSourceField;
            this.priorityField = priorityField;
        }

        private static WoverRegistryAccess tryCreate() {
            try {
                Class<?> registryApi = Class.forName("org.betterx.wover.surface.api.SurfaceRuleRegistry");
                ResourceKey<? extends Registry<?>> key =
                        (ResourceKey<? extends Registry<?>>) registryApi.getField("SURFACE_RULES_REGISTRY").get(null);
                Class<?> ruleClass = Class.forName("org.betterx.wover.surface.api.AssignedSurfaceRule");
                Field biomeIdField = ruleClass.getField("biomeID");
                Field ruleSourceField = ruleClass.getField("ruleSource");
                Field priorityField = ruleClass.getField("priority");
                Isleweaver.LOGGER.debug("[Isleweaver] Wover surface rule reflection bound, per-biome collection enabled");
                return new WoverRegistryAccess(key, biomeIdField, ruleSourceField, priorityField);
            } catch (ClassNotFoundException e) {
                Isleweaver.LOGGER.debug("[Isleweaver] Wover not present on classpath; per-biome surface rule collection disabled");
                return null;
            } catch (Throwable t) {
                Isleweaver.LOGGER.warn("[Isleweaver] Failed to bind Wover surface rule reflection; per-biome collection disabled", t);
                return null;
            }
        }
    }
}

package com.thebeyond.isleweaver.compat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.thebeyond.api.event.BeyondSurfaceRuleContributionEvent;
import com.thebeyond.api.registry.BeyondBlockRegistry;
import com.thebeyond.isleweaver.Isleweaver;
import com.thebeyond.isleweaver.mixin.core.NoiseGeneratorSettingsAccessor;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.placement.CaveSurface;
import net.neoforged.neoforge.common.NeoForge;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// merges Beyond's surface rules into the End settings, wider plate thresholds over Enderscape terrain
public final class SurfaceRuleMerger {

    private static final String BEYOND_NAMESPACE = "the_beyond";

    private static final ResourceKey<NoiseGeneratorSettings> BEYOND_END_SETTINGS =
            ResourceKey.create(Registries.NOISE_SETTINGS,
                    ResourceLocation.fromNamespaceAndPath(BEYOND_NAMESPACE, "the_end"));

    // set only while Beyond owns the End, EndSurfaceRuleReassertMixin restores it after createLevels
    public static volatile SurfaceRules.RuleSource beyondOwnedEndRule;

    private SurfaceRuleMerger() {}

    public static void mergeSurfaceRules(MinecraftServer server, boolean beyondActive) {
        RegistryAccess registryAccess = server.registryAccess();

        Registry<LevelStem> dimensions = registryAccess.registryOrThrow(Registries.LEVEL_STEM);
        LevelStem endStem = dimensions.get(LevelStem.END);
        if (endStem == null) {
            Isleweaver.LOGGER.warn("[Isleweaver] End dimension stem not found, skipping surface rule merge");
            return;
        }

        ChunkGenerator chunkGenerator = endStem.generator();
        if (!(chunkGenerator instanceof NoiseBasedChunkGenerator noiseGen)) {
            Isleweaver.LOGGER.debug("[Isleweaver] End generator is not NoiseBasedChunkGenerator, skipping surface rule merge");
            return;
        }

        NoiseGeneratorSettings activeSettings = noiseGen.generatorSettings().value();
        BiomeSource endBiomeSource = endStem.generator().getBiomeSource();
        boolean externalTerrain = endBiomeSource instanceof MultiNoiseBiomeSource;

        SurfaceRules.RuleSource beyondRule;
        if (externalTerrain) {
            beyondRule = buildExternalTerrainRules();
            Isleweaver.LOGGER.debug("[Isleweaver] Using wider surface rule thresholds for external terrain (Enderscape)");
        } else {
            Registry<NoiseGeneratorSettings> noiseRegistry = registryAccess.registryOrThrow(Registries.NOISE_SETTINGS);
            NoiseGeneratorSettings beyondEnd = noiseRegistry.get(BEYOND_END_SETTINGS);
            if (beyondEnd == null) {
                Isleweaver.LOGGER.warn("[Isleweaver] Beyond End noise settings not found, skipping surface rule merge");
                return;
            }
            beyondRule = ((NoiseGeneratorSettingsAccessor) (Object) beyondEnd).isleweaver$getSurfaceRule();
            Isleweaver.LOGGER.debug("[Isleweaver] Using default surface rule thresholds for Beyond/vanilla terrain");
        }

        // order: Beyond, addons, foreign, then active (it carries in-place injections), terminators sink last
        List<SurfaceRules.RuleSource> allRules = new ArrayList<>();
        allRules.add(beyondRule);

        if (beyondActive) {
            BeyondSurfaceRuleContributionEvent contribEvent =
                    new BeyondSurfaceRuleContributionEvent(server, registryAccess, endBiomeSource);
            NeoForge.EVENT_BUS.post(contribEvent);
            allRules.addAll(contribEvent.getContributions());
            // only the biome-guarded clauses of a foreign rule, so no foreign global paints Beyond islands
            for (SurfaceRules.RuleSource tb : collectTerraBlenderEndRules()) {
                allRules.addAll(SurfaceRuleOrdering.biomeGuardedClausesOf(tb, registryAccess));
            }
            for (SurfaceRules.RuleSource foreign : collectForeignNoiseSettingsRules(server, registryAccess)) {
                allRules.addAll(SurfaceRuleOrdering.biomeGuardedClausesOf(foreign, registryAccess));
            }
        }

        SurfaceRules.RuleSource activePre = activeSettings.surfaceRule();
        allRules.add(activePre);

        SurfaceRules.RuleSource sequenced =
                SurfaceRules.sequence(allRules.toArray(new SurfaceRules.RuleSource[0]));
        // true: the merged rule is not biome-wrapped, so only biome-confined clauses may pass a foreign filler
        SurfaceRules.RuleSource mergedRule = SurfaceRuleOrdering.bubbleTerminatorsLast(sequenced, true);

        ((NoiseGeneratorSettingsAccessor) (Object) activeSettings).isleweaver$setSurfaceRule(mergedRule);
        beyondOwnedEndRule = beyondActive ? mergedRule : null;
        Isleweaver.LOGGER.debug("[Isleweaver] Merged surface rules into End generator settings (terminator reorder: {})",
                mergedRule == sequenced ? "no change" : "fillers sunk after biome-guarded clauses");
    }

    public static void mergeSurfaceRules(MinecraftServer server) {
        mergeSurfaceRules(server, false);
    }

    private static final ResourceLocation END_NOISE_SETTINGS_FILE =
            ResourceLocation.withDefaultNamespace("worldgen/noise_settings/end.json");

    // these may contain terminators, so the caller orders them last
    private static List<SurfaceRules.RuleSource> collectForeignNoiseSettingsRules(
            MinecraftServer server, RegistryAccess registryAccess) {
        Registry<NoiseGeneratorSettings> noiseRegistry = registryAccess.registryOrThrow(Registries.NOISE_SETTINGS);
        List<SurfaceRules.RuleSource> foreignRules = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        ResourceKey<NoiseGeneratorSettings> vanillaEnd = ResourceKey.create(Registries.NOISE_SETTINGS,
                ResourceLocation.withDefaultNamespace("end"));
        NoiseGeneratorSettings vanillaEndSettings = noiseRegistry.get(vanillaEnd);
        if (vanillaEndSettings != null) {
            SurfaceRules.RuleSource rule = ((NoiseGeneratorSettingsAccessor) (Object) vanillaEndSettings)
                    .isleweaver$getSurfaceRule();
            if (rule != null && seen.add(rule.toString())) {
                foreignRules.add(rule);
                Isleweaver.LOGGER.debug("[Isleweaver] Extracted surface rules from minecraft:end noise settings (may contain foreign mod overrides)");
            }
        }

        // the registry keeps only the winning pack, the losers' biome-guarded rules come from the stack
        for (SurfaceRules.RuleSource rule : decodeStackedEndSurfaceRules(server, registryAccess, seen)) {
            foreignRules.add(rule);
        }

        for (Map.Entry<ResourceKey<NoiseGeneratorSettings>, NoiseGeneratorSettings> entry :
                noiseRegistry.entrySet()) {
            ResourceKey<NoiseGeneratorSettings> key = entry.getKey();
            String ns = key.location().getNamespace();
            String path = key.location().getPath();

            if (ns.equals("minecraft") || ns.equals(BEYOND_NAMESPACE)) continue;
            if (!path.contains("end")) continue;

            SurfaceRules.RuleSource rule = ((NoiseGeneratorSettingsAccessor) (Object) entry.getValue())
                    .isleweaver$getSurfaceRule();
            if (rule != null) {
                foreignRules.add(rule);
                Isleweaver.LOGGER.debug("[Isleweaver] Extracted surface rules from {} noise settings", key.location());
            }
        }

        return foreignRules;
    }

    // reflection so TerraBlender stays optional. Bail quietly if it isn't loaded
    private static List<SurfaceRules.RuleSource> collectTerraBlenderEndRules() {
        try {
            Class<?> manager = Class.forName("terrablender.api.SurfaceRuleManager");
            Class<?> ruleCategory = Class.forName("terrablender.api.SurfaceRuleManager$RuleCategory");
            Object endCategory = ruleCategory.getField("END").get(null);
            java.lang.reflect.Field field = manager.getDeclaredField("surfaceRules");
            field.setAccessible(true);
            if (!(field.get(null) instanceof Map<?, ?> byCategory)) return List.of();
            if (!(byCategory.get(endCategory) instanceof Map<?, ?> byNamespace)
                    || byNamespace.isEmpty()) {
                return List.of();
            }
            List<SurfaceRules.RuleSource> out = new ArrayList<>();
            List<String> namespaces = new ArrayList<>();
            for (Map.Entry<?, ?> e : byNamespace.entrySet()) {
                if (e.getValue() instanceof SurfaceRules.RuleSource rs) {
                    out.add(rs);
                    namespaces.add(String.valueOf(e.getKey()));
                }
            }
            if (!out.isEmpty()) {
                Isleweaver.LOGGER.debug(
                        "[Isleweaver] Captured {} TerraBlender END surface rule(s) from namespaces {}",
                        out.size(), namespaces);
            }
            return out;
        } catch (ClassNotFoundException | NoSuchFieldException e) {
            return List.of();
        } catch (Throwable t) {
            Isleweaver.LOGGER.warn("[Isleweaver] TerraBlender END surface-rule capture failed", t);
            return List.of();
        }
    }

    private static List<SurfaceRules.RuleSource> decodeStackedEndSurfaceRules(
            MinecraftServer server, RegistryAccess registryAccess, Set<String> seen) {
        List<SurfaceRules.RuleSource> out = new ArrayList<>();
        ResourceManager rm = server.getResourceManager();
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registryAccess);
        List<Resource> stack;
        try {
            stack = rm.getResourceStack(END_NOISE_SETTINGS_FILE);
        } catch (Throwable t) {
            Isleweaver.LOGGER.warn("[Isleweaver] Could not read {} resource stack",
                    END_NOISE_SETTINGS_FILE, t);
            return out;
        }
        for (Resource res : stack) {
            try (BufferedReader reader = res.openAsReader()) {
                JsonObject json = GsonHelper.parse(reader);
                if (!json.has("surface_rule")) continue;
                SurfaceRules.RuleSource rule = SurfaceRules.RuleSource.CODEC
                        .parse(ops, json.get("surface_rule")).result().orElse(null);
                if (rule == null || !seen.add(rule.toString())) continue;
                out.add(rule);
                String rs = rule.toString();
                Isleweaver.LOGGER.debug(
                        "[Isleweaver] Captured minecraft:end surface_rule from pack '{}' "
                                + "(len={} hasStellarity={} hasPhantasm={})",
                        res.sourcePackId(), rs.length(),
                        rs.contains("stellarity"), rs.contains("phantasm"));
            } catch (Throwable t) {
                Isleweaver.LOGGER.warn(
                        "[Isleweaver] Failed to decode a minecraft:end surface_rule from the stack", t);
            }
        }
        return out;
    }

    private static SurfaceRules.RuleSource buildExternalTerrainRules() {
        BlockState plateBlock = BeyondBlockRegistry.get("plate_block").defaultBlockState();
        BlockState platedEndStone = BeyondBlockRegistry.get("plated_end_stone").defaultBlockState();

        ResourceKey<Biome> attractaExpanse = ResourceKey.create(
                Registries.BIOME, ResourceLocation.fromNamespaceAndPath(BEYOND_NAMESPACE, "attracta_expanse"));

        return SurfaceRules.ifTrue(
                SurfaceRules.isBiome(attractaExpanse),
                SurfaceRules.sequence(
                        SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR,
                                SurfaceRules.sequence(
                                        SurfaceRules.ifTrue(
                                                SurfaceRules.noiseCondition(Noises.NETHER_STATE_SELECTOR, -0.3, 0.3),
                                                SurfaceRules.state(plateBlock)),
                                        SurfaceRules.ifTrue(
                                                SurfaceRules.noiseCondition(Noises.NETHER_STATE_SELECTOR, -0.5, 0.5),
                                                SurfaceRules.state(platedEndStone))
                                )),
                        SurfaceRules.ifTrue(SurfaceRules.stoneDepthCheck(1, false, CaveSurface.FLOOR),
                                SurfaceRules.ifTrue(
                                        SurfaceRules.noiseCondition(Noises.NETHER_STATE_SELECTOR, -0.3, 0.3),
                                        SurfaceRules.state(platedEndStone)))
                ));
    }
}

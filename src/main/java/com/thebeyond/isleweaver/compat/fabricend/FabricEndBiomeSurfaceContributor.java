package com.thebeyond.isleweaver.compat.fabricend;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.thebeyond.api.event.BeyondSurfaceRuleContributionEvent;
import com.thebeyond.isleweaver.Isleweaver;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.SurfaceRules;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// picks up surface rules from any mod that drops files under worldgen/end_biomes/*.json
public final class FabricEndBiomeSurfaceContributor {
    private FabricEndBiomeSurfaceContributor() {}

    private static final String DATA_PATH = "worldgen/end_biomes";

    public static void onBeyondSurfaceRuleContribution(BeyondSurfaceRuleContributionEvent event) {
        ResourceManager resourceManager = event.getServer().getResourceManager();
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, event.getRegistryAccess());

        Map<ResourceLocation, Resource> files =
                resourceManager.listResources(DATA_PATH, loc -> loc.getPath().endsWith(".json"));

        List<String> packIds = resourceManager.listPacks().map(PackResources::packId).toList();
        Isleweaver.LOGGER.info(
                "[FabricEndDiag] {} .json found={} | RM namespaces={} | packs={}",
                DATA_PATH, files.size(), resourceManager.getNamespaces(), packIds);

        List<String> contributed = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Resource> entry : files.entrySet()) {
            try (BufferedReader reader = entry.getValue().openAsReader()) {
                JsonObject json = GsonHelper.parse(reader);
                if (!json.has("biome") || !json.has("surface_rule")) continue;
                ResourceLocation biomeId = ResourceLocation.parse(GsonHelper.getAsString(json, "biome"));
                tryContribute(event, ops, biomeId, json.get("surface_rule"),
                        entry.getKey().toString(), contributed);
            } catch (Throwable t) {
                Isleweaver.LOGGER.warn(
                        "[Isleweaver] Failed to read End biome surface data {}", entry.getKey(), t);
            }
        }

        if (!contributed.isEmpty()) {
            Isleweaver.LOGGER.info(
                    "[Isleweaver] Contributed {} End biome surface rule(s) from {} convention: {}",
                    contributed.size(), DATA_PATH, contributed);
        }
    }

    private static void tryContribute(BeyondSurfaceRuleContributionEvent event, RegistryOps<JsonElement> ops,
                                      ResourceLocation biomeId, JsonElement ruleJson,
                                      String source, List<String> contributed) {
        DataResult<SurfaceRules.RuleSource> decoded = SurfaceRules.RuleSource.CODEC.parse(ops, ruleJson);
        SurfaceRules.RuleSource rule = decoded.result().orElse(null);
        if (rule == null) {
            Isleweaver.LOGGER.warn(
                    "[Isleweaver] Could not decode surface rule for {} ({}): {}",
                    biomeId, source, decoded.error().map(Object::toString).orElse("unknown"));
            return;
        }
        ResourceKey<Biome> biomeKey = ResourceKey.create(Registries.BIOME, biomeId);
        event.contribute(SurfaceRules.ifTrue(SurfaceRules.isBiome(biomeKey), rule));
        contributed.add(biomeId + "<-" + source);
    }
}

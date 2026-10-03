package com.thebeyond.isleweaver.compat.mes;

import com.thebeyond.api.worldgen.BeyondForeignStructureProfiles;
import com.thebeyond.api.worldgen.StructureIntegrationProfile;
import com.thebeyond.isleweaver.Isleweaver;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.neoforged.fml.ModList;

import java.util.Set;

// per-structure tweaks for MES ruins: dirt-cover opt-out for gardens, embedded carve for the crashed megaships
public final class MoogsEndStructuresProfiles {
    private static final String MOD = "mes";

    private MoogsEndStructuresProfiles() {}

    public static void register() {
        if (!ModList.get().isLoaded(MOD)) return;
        BeyondForeignStructureProfiles.suppressDirtCover(ResourceLocation.fromNamespaceAndPath(MOD, "mythic_garden"));
        BeyondForeignStructureProfiles.suppressDirtCover(ResourceLocation.fromNamespaceAndPath(MOD, "placid_prairie"));
        // Crashed megaships: embedded (carve-only). The island buries the sunk hull, interior stays hollow.
        for (String ship : new String[]{"mega_ship_crashed", "mega_ship_crashed_2", "mega_ship_crashed_deepslate"}) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MOD, ship);
            BeyondForeignStructureProfiles.markEmbedded(id);
            BeyondForeignStructureProfiles.markHugTerrain(id);
        }
        Isleweaver.LOGGER.debug("[Isleweaver] MES present, dirt-cover opt-out (mythic_garden, placid_prairie); "
                + "crashed megaships embedded (distributed carve-only, island-buried).");
    }

    // Gives an MES type its carve, floating for a hovering ship and distributed organic otherwise. Returns whether it hovers.
    public static boolean host(ResourceLocation key, Structure structure) {
        // a crashed hull also declares NONE but sinks itself, only a hovering ship wants the floating skirt
        boolean hovering = structure.terrainAdaptation() == TerrainAdjustment.NONE
                && !BeyondForeignStructureProfiles.isEmbedded(key);
        if (hovering) {
            if (BeyondForeignStructureProfiles.get(key) == null) {
                BeyondForeignStructureProfiles.register(key,
                        StructureIntegrationProfile.builder(StructureIntegrationProfile.Anchor.FLOATING).build());
            }
        } else {
            BeyondForeignStructureProfiles.markAutoSeatedProjected(key);
        }
        return hovering;
    }

    // Hosts every End MES type at start-up, or after a restart the ships already in the world generate without their carve.
    public static void warmUp(MinecraftServer server) {
        try {
            LevelStem end = server.registryAccess().registryOrThrow(Registries.LEVEL_STEM).get(LevelStem.END);
            if (end == null) return;
            Set<Holder<Biome>> endBiomes = end.generator().getBiomeSource().possibleBiomes();
            int hovering = 0, carved = 0;
            for (var e : server.registryAccess().registryOrThrow(Registries.STRUCTURE).entrySet()) {
                Structure s = e.getValue();
                if (!(s instanceof MesHostedStructure) || s.biomes().stream().noneMatch(endBiomes::contains)) continue;
                if (host(e.getKey().location(), s)) hovering++;
                else carved++;
            }
            if (hovering + carved > 0) {
                Isleweaver.LOGGER.info("[Isleweaver][MES] restart warm-up: {} hovering, {} carved", hovering, carved);
            }
        } catch (Throwable t) {
            Isleweaver.LOGGER.warn("[Isleweaver][MES] restart warm-up failed, so ships already in the world generate"
                    + " without their carve until one is placed", t);
        }
    }
}

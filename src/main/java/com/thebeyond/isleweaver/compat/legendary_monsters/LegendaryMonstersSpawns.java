package com.thebeyond.isleweaver.compat.legendary_monsters;

import com.thebeyond.isleweaver.Isleweaver;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

// Legendary Monsters ships no spawn placements, so its mobs get ON_GROUND with vanilla monster rules
public final class LegendaryMonstersSpawns {
    private static final String MOD = "legendary_monsters";

    private LegendaryMonstersSpawns() {}

    public static void onRegisterSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        if (!ModList.get().isLoaded(MOD)) return;
        int n = 0;
        n += registerGroundMonster(event, "chorusling");
        n += registerGroundMonster(event, "bomber");
        n += registerGroundMonster(event, "skeletosaurus");
        Isleweaver.LOGGER.debug("[Isleweaver] Registered ground SpawnPlacements for {} Legendary Monsters mob(s) "
                + "(LM ships none, so they would otherwise spawn mid-air).", n);
    }

    @SuppressWarnings("unchecked")
    private static int registerGroundMonster(RegisterSpawnPlacementsEvent event, String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MOD, path);
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(id)) return 0;
        EntityType<Monster> type = (EntityType<Monster>) BuiltInRegistries.ENTITY_TYPE.get(id);
        event.register(type, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Monster::checkMonsterSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        return 1;
    }
}

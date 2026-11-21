package com.thebeyond.isleweaver.compat.executiveorders;

import com.thebeyond.isleweaver.Isleweaver;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

public final class ExecutiveOrdersCompat {
    public static final String MODID = "executiveorders";
    public static final boolean TRACE = Boolean.getBoolean("isleweaver.traceExecutive");
    private static final ResourceLocation STARFALL = ResourceLocation.fromNamespaceAndPath(MODID, "starfall");
    private static final double VANILLA_STAR_Y = 255.0;

    private ExecutiveOrdersCompat() {}

    public static void register() {
        if (ModList.get().isLoaded(MODID)) NeoForge.EVENT_BUS.addListener(ExecutiveOrdersCompat::liftStarToTheWorldTop);
    }

    public static boolean isEnd(Level level) {
        return level.dimensionTypeRegistration().is(BuiltinDimensionTypes.END);
    }

    public static double fromEndBottom(Level level, double vanillaY) {
        return isEnd(level) ? level.getMinBuildHeight() + vanillaY : vanillaY;
    }

    private static void liftStarToTheWorldTop(EntityJoinLevelEvent event) {
        Entity star = event.getEntity();
        if (event.loadedFromDisk() || star.getY() != VANILLA_STAR_Y || !(event.getLevel() instanceof ServerLevel level)) return;
        if (!isEnd(level) || !STARFALL.equals(BuiltInRegistries.ENTITY_TYPE.getKey(star.getType()))) return;
        int top = level.getMaxBuildHeight() - 1;
        if (top == VANILLA_STAR_Y) return;
        star.setPos(star.getX(), top, star.getZ());
        if (TRACE) Isleweaver.LOGGER.info("[Isleweaver] Executive star lifted from y 255 to {} at {} {}", top, star.getBlockX(), star.getBlockZ());
    }
}

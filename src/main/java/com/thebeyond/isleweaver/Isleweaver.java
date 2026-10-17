package com.thebeyond.isleweaver;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(Isleweaver.MODID)
public final class Isleweaver {
    public static final String MODID = "isleweaver";
    public static final Logger LOGGER = LogUtils.getLogger();
    // per-structure worldgen traces, off so they never reach players' logs
    public static boolean TRACE = false;

    public Isleweaver(IEventBus modEventBus, ModContainer modContainer) {
        // has to bind before any End biome source is built, so do it here at mod-construct rather than in common setup
        com.thebeyond.isleweaver.compat.unusualend.WarpedReefMacroRegion.register();
        com.thebeyond.isleweaver.compat.LiftedBodyRule.register(modEventBus);
        modEventBus.addListener(com.thebeyond.isleweaver.lifecycle.IsleweaverLifecycle::onBeyondCommonSetup);
        modEventBus.addListener(com.thebeyond.isleweaver.lifecycle.IsleweaverLifecycle::onBeyondTerrainPackAssemble);
        modEventBus.addListener(com.thebeyond.isleweaver.compat.legendary_monsters.LegendaryMonstersSpawns::onRegisterSpawnPlacements);
        // MES megaship tuning. Register only when MES is present, so the config file appears.
        if (ModList.get().isLoaded("mes")) {
            modContainer.registerConfig(ModConfig.Type.COMMON, IsleweaverConfig.SPEC);
        }
    }
}

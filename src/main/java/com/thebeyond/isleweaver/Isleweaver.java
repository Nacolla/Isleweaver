package com.thebeyond.isleweaver;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

// just wires up listeners; each compat module gates itself on whether its mod is loaded
@Mod(Isleweaver.MODID)
public final class Isleweaver {
    public static final String MODID = "isleweaver";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Isleweaver(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("[Isleweaver] Mod constructor invoked — wiring Beyond API listeners");
        // has to bind before any End biome source is built, so do it here at mod-construct rather than in common setup
        com.thebeyond.isleweaver.compat.unusualend.WarpedReefMacroRegion.register();
        modEventBus.addListener(com.thebeyond.isleweaver.lifecycle.IsleweaverLifecycle::onBeyondCommonSetup);
        modEventBus.addListener(com.thebeyond.isleweaver.lifecycle.IsleweaverLifecycle::onBeyondTerrainPackAssemble);
        LOGGER.info("[Isleweaver] Mod-bus listeners registered: BeyondCommonSetupEvent + BeyondTerrainPackAssembleEvent");
    }
}

package com.thebeyond.isleweaver.lifecycle;

import com.thebeyond.api.event.BeyondBiomeDiscoveryEvent;
import com.thebeyond.api.event.BeyondCommonSetupEvent;
import com.thebeyond.api.event.BeyondServerLifecycleEvent;
import com.thebeyond.api.event.BeyondSurfaceRuleContributionEvent;
import com.thebeyond.api.event.BeyondTerrainPackAssembleEvent;
import com.thebeyond.isleweaver.Isleweaver;
import com.thebeyond.isleweaver.compat.astrological.AstrologicalBoundsPackContribution;
import com.thebeyond.isleweaver.compat.enderscape.EnderscapeBoundsPackContribution;
import com.thebeyond.isleweaver.compat.enderscape.PlacedFeatureRewriter;
import com.thebeyond.isleweaver.compat.EndFeatureHeightmapFix;
import com.thebeyond.isleweaver.compat.EndFeatureOrderNormalizer;
import com.thebeyond.isleweaver.compat.fabricend.FabricEndBiomeSurfaceContributor;
import com.thebeyond.isleweaver.compat.oak.OakEndBiomeDiscoveryContributor;
import com.thebeyond.isleweaver.compat.unusualend.WarpedReefMacroRegion;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;

// routes Beyond's API events to the per-compat handlers, which self-gate on ModList.isLoaded or key by structure id
@EventBusSubscriber(modid = Isleweaver.MODID)
public final class IsleweaverLifecycle {
    private IsleweaverLifecycle() {}

    public static void onBeyondCommonSetup(BeyondCommonSetupEvent event) {
        com.thebeyond.isleweaver.compat.legendary_monsters.LegendaryMonstersProfiles.register();
        com.thebeyond.isleweaver.compat.phantasm.PhantasmProfiles.register();
        com.thebeyond.isleweaver.compat.unusualend.UnusualEndStructureProfiles.register();
        com.thebeyond.isleweaver.compat.mes.MoogsEndStructuresProfiles.register();
        com.thebeyond.isleweaver.compat.enderscape.EnderscapeStructureProfiles.register();
        com.thebeyond.isleweaver.compat.dungeons_and_taverns.DungeonsAndTavernsProfiles.register();
        com.thebeyond.isleweaver.compat.wover.WoverBiomeTagRegistration.register();
    }

    @SubscribeEvent
    public static void onBeyondSurfaceRuleContribution(BeyondSurfaceRuleContributionEvent event) {
        com.thebeyond.isleweaver.compat.wover.WoverSurfaceRuleContributor.onBeyondSurfaceRuleContribution(event);
        FabricEndBiomeSurfaceContributor.onBeyondSurfaceRuleContribution(event);
    }

    @SubscribeEvent
    public static void onBeyondBiomeDiscovery(BeyondBiomeDiscoveryEvent event) {
        OakEndBiomeDiscoveryContributor.onBeyondBiomeDiscovery(event);
    }

    public static void onBeyondTerrainPackAssemble(BeyondTerrainPackAssembleEvent event) {
        boolean astroLoaded = ModList.get().isLoaded("astrological");
        boolean enderLoaded = ModList.get().isLoaded("enderscape");
        if (astroLoaded) {
            AstrologicalBoundsPackContribution.contribute(event);
        }
        if (enderLoaded) {
            EnderscapeBoundsPackContribution.contribute(event);
        }
        Isleweaver.LOGGER.debug(
                "[Isleweaver] BeyondTerrainPackAssembleEvent: {} contribution(s) submitted",
                event.getContributions().size());
    }

    @SubscribeEvent
    public static void onBeyondAboutToStart(BeyondServerLifecycleEvent.AboutToStart event) {
        if (!event.isBeyondTerrainActive()) return;
        com.thebeyond.isleweaver.compat.mes.MoogsEndStructuresProfiles.warmUp(event.getServer());
        if (ModList.get().isLoaded("enderscape")) {
            PlacedFeatureRewriter.rewrite(event.getServer());
        }
        if (ModList.get().isLoaded("betterend")) {
            com.thebeyond.isleweaver.compat.betterend.BetterEndVegetationThrottle.apply(event.getServer());
        }
        EndFeatureHeightmapFix.apply(event.getServer());
        // must follow every feature rewrite: one global per-step order keeps vanilla FeatureSorter from hitting a cycle
        EndFeatureOrderNormalizer.apply(event.getServer());
        WarpedReefMacroRegion.cache(event.getServer());
    }

    @SubscribeEvent
    public static void onBeyondStopped(BeyondServerLifecycleEvent.Stopped event) {
        WarpedReefMacroRegion.clear();
    }
}

package com.thebeyond.isleweaver.lifecycle;

import com.thebeyond.api.event.BeyondBiomeDiscoveryEvent;
import com.thebeyond.api.event.BeyondCommonSetupEvent;
import com.thebeyond.api.event.BeyondServerLifecycleEvent;
import com.thebeyond.api.event.BeyondSurfaceRuleContributionEvent;
import com.thebeyond.api.event.BeyondTerrainPackAssembleEvent;
import com.thebeyond.isleweaver.Isleweaver;
import com.thebeyond.isleweaver.compat.astrological.AstrologicalBoundsPackContribution;
import com.thebeyond.isleweaver.compat.betterend.BetterEndVegetationThrottle;
import com.thebeyond.isleweaver.compat.enderscape.EnderscapeBoundsPackContribution;
import com.thebeyond.isleweaver.compat.enderscape.PlacedFeatureRewriter;
import com.thebeyond.isleweaver.compat.EndFeatureHeightmapFix;
import com.thebeyond.isleweaver.compat.EndFeatureOrderNormalizer;
import com.thebeyond.isleweaver.compat.fabricend.FabricEndBiomeSurfaceContributor;
import com.thebeyond.isleweaver.compat.oak.OakEndBiomeDiscoveryContributor;
import com.thebeyond.isleweaver.compat.unusualend.WarpedReefMacroRegion;
import com.thebeyond.isleweaver.compat.wover.WoverBiomeTagRegistration;
import com.thebeyond.isleweaver.compat.wover.WoverSurfaceRuleContributor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;

// routes Beyond's API events to the per-compat handlers; each one self-gates on ModList.isLoaded
@EventBusSubscriber(modid = Isleweaver.MODID)
public final class IsleweaverLifecycle {
    private IsleweaverLifecycle() {}

    public static void onBeyondCommonSetup(BeyondCommonSetupEvent event) {
        Isleweaver.LOGGER.info("[Isleweaver] Hooked into BeyondCommonSetupEvent.");
        // safe to always register — discovery just finds nothing matching when Wover is absent
        WoverBiomeTagRegistration.register();
    }

    @SubscribeEvent
    public static void onBeyondSurfaceRuleContribution(BeyondSurfaceRuleContributionEvent event) {
        WoverSurfaceRuleContributor.onBeyondSurfaceRuleContribution(event);
        FabricEndBiomeSurfaceContributor.onBeyondSurfaceRuleContribution(event);
    }

    @SubscribeEvent
    public static void onBeyondBiomeDiscovery(BeyondBiomeDiscoveryEvent event) {
        OakEndBiomeDiscoveryContributor.onBeyondBiomeDiscovery(event);
    }

    public static void onBeyondTerrainPackAssemble(BeyondTerrainPackAssembleEvent event) {
        boolean astroLoaded = ModList.get().isLoaded("astrological");
        boolean enderLoaded = ModList.get().isLoaded("enderscape");
        Isleweaver.LOGGER.info(
                "[Isleweaver] BeyondTerrainPackAssembleEvent received — astrological={} enderscape={}",
                astroLoaded, enderLoaded);
        if (astroLoaded) {
            AstrologicalBoundsPackContribution.contribute(event);
        }
        if (enderLoaded) {
            EnderscapeBoundsPackContribution.contribute(event);
        }
        Isleweaver.LOGGER.info(
                "[Isleweaver] BeyondTerrainPackAssembleEvent: {} contribution(s) submitted",
                event.getContributions().size());
    }

    @SubscribeEvent
    public static void onBeyondAboutToStart(BeyondServerLifecycleEvent.AboutToStart event) {
        if (!event.isBeyondTerrainActive()) return;
        if (ModList.get().isLoaded("enderscape")) {
            PlacedFeatureRewriter.rewrite(event.getServer());
        }
        if (ModList.get().isLoaded("betterend")) {
            BetterEndVegetationThrottle.apply(event.getServer());
        }
        // End structures without a heightmap mis-resolve the surface under Beyond's biome source, so raise their origin (steps 0-8 only)
        EndFeatureHeightmapFix.apply(event.getServer());
        // has to run last — collapses to a single global per-step feature order so vanilla FeatureSorter can't hit a cycle (fatal under Wover)
        EndFeatureOrderNormalizer.apply(event.getServer());
        // no-op without UnusualEnd — biomeAt() returns null and the macro overlay stays silent
        WarpedReefMacroRegion.cache(event.getServer());
    }

    @SubscribeEvent
    public static void onBeyondStopped(BeyondServerLifecycleEvent.Stopped event) {
        WarpedReefMacroRegion.clear();
    }
}

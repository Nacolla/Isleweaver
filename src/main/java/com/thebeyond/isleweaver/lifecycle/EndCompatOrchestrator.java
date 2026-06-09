package com.thebeyond.isleweaver.lifecycle;

import com.thebeyond.api.event.BeyondServerLifecycleEvent;
import com.thebeyond.isleweaver.Isleweaver;
import com.thebeyond.isleweaver.compat.EndBiomeDiscovery;
import com.thebeyond.isleweaver.compat.EndBiomeInjector;
import com.thebeyond.isleweaver.compat.SurfaceRuleMerger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

import java.util.List;
import java.util.Set;

// runs at HIGH so discovery/injection/surface-merge finish before the per-compat handlers in IsleweaverLifecycle
@EventBusSubscriber(modid = Isleweaver.MODID)
public final class EndCompatOrchestrator {

    private static final ResourceLocation END_DIMENSION = ResourceLocation.fromNamespaceAndPath(
            "minecraft", "dimension/the_end.json");

    private EndCompatOrchestrator() {}

    // fire before default-priority listeners so mods gated on possibleBiomes() can see their biomes
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onServerAboutToStartEarlyDiscovery(ServerAboutToStartEvent event) {
        EndBiomeDiscovery.discoverAndInject(event.getServer());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBeyondAboutToStart(BeyondServerLifecycleEvent.AboutToStart event) {
        MinecraftServer server = event.getServer();

        // the is_end biome tag is the contract here — no hardcoded mod ids
        Set<String> endModNamespaces = EndBiomeDiscovery.scanEndModNamespaces(server);
        if (!endModNamespaces.isEmpty()) {
            Isleweaver.LOGGER.info("[Isleweaver] End-mod biome namespaces detected via tag: {}", endModNamespaces);
        }

        // when Beyond is active it owns the End, so skip fallback injection; soup mode falls through to the compat paths
        if (event.isBeyondTerrainActive()) {
            Isleweaver.LOGGER.info("[Isleweaver] Beyond terrain is active — merging foreign End surface rules");
            SurfaceRuleMerger.mergeSurfaceRules(server, true);
            return;
        }

        // expect at most one entry (the foreign pack that won); more than one means something bypassed the wrapping
        try {
            List<Resource> stack = server.getResourceManager().getResourceStack(END_DIMENSION);
            if (stack.isEmpty()) {
                Isleweaver.LOGGER.warn("[Isleweaver] Soup mode active but no pack provides {} — End dimension will be vanilla", END_DIMENSION);
            } else {
                List<String> sources = stack.stream().map(Resource::sourcePackId).toList();
                Isleweaver.LOGGER.info("[Isleweaver] Soup mode: End dimension provided by {} pack(s): {}", sources.size(), sources);
            }
        } catch (Exception e) {
            Isleweaver.LOGGER.warn("[Isleweaver] Failed to inspect End dimension provider stack", e);
        }

        Isleweaver.LOGGER.info("[Isleweaver] Beyond terrain not active — running fallback biome injection");
        SurfaceRuleMerger.mergeSurfaceRules(server);
        EndBiomeInjector.injectBiomes(server);
    }

    @SubscribeEvent
    public static void onBeyondStopped(BeyondServerLifecycleEvent.Stopped event) {
        EndBiomeInjector.vanillaEndHolders = null;
    }
}

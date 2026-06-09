package com.thebeyond.isleweaver.compat.oak;

import com.thebeyond.api.event.BeyondBiomeDiscoveryEvent;
import com.thebeyond.isleweaver.Isleweaver;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

// Oak's End and Oak Frontier register biomes outside the tag system, so we reflect into their
// ADDED_BIOMES sets to find them
public final class OakEndBiomeDiscoveryContributor {
    private OakEndBiomeDiscoveryContributor() {}

    private static final List<String> FABRIC_END_BIOMES_DATA_CLASSES = List.of(
            "com.oakmods.oaksend.endbiomes.TheEndBiomeData",
            "com.oakmods.oakfrontier.endbiomes.TheEndBiomeData"
    );

    public static void onBeyondBiomeDiscovery(BeyondBiomeDiscoveryEvent event) {
        Registry<Biome> biomeRegistry = event.getBiomeRegistry();
        for (String className : FABRIC_END_BIOMES_DATA_CLASSES) {
            collectFrom(className, biomeRegistry, event);
        }
    }

    @SuppressWarnings("unchecked")
    private static void collectFrom(String className, Registry<Biome> biomeRegistry,
                                     BeyondBiomeDiscoveryEvent event) {
        try {
            Class<?> cls = Class.forName(className);
            Field field = cls.getField("ADDED_BIOMES");
            Object raw = field.get(null);
            if (!(raw instanceof Set<?> set)) return;

            int discovered = 0;
            for (Object obj : set) {
                if (!(obj instanceof ResourceKey<?> key)) continue;
                ResourceKey<Biome> biomeKey = (ResourceKey<Biome>) key;
                Holder<Biome> holder = biomeRegistry.getHolder(biomeKey).orElse(null);
                if (holder != null && event.contribute(holder)) discovered++;
            }
            if (discovered > 0) {
                Isleweaver.LOGGER.debug(
                        "[Isleweaver] Discovered {} biome(s) via {} ADDED_BIOMES",
                        discovered, className);
            }
        } catch (ClassNotFoundException ignored) {
            // mod isn't installed
        } catch (Throwable t) {
            Isleweaver.LOGGER.debug("[Isleweaver] Reading {} ADDED_BIOMES failed (non-fatal): {}",
                    className, t.toString());
        }
    }
}

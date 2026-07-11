package com.thebeyond.isleweaver.compat.biomesoplenty;

import com.thebeyond.isleweaver.Isleweaver;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BopBiomeConfigFilter {

    private static final String MOD_CONFIG = "biomesoplenty.init.ModConfig";
    private static final String BOP_BIOMES = "biomesoplenty.api.biome.BOPBiomes";

    private static Boolean available;
    private static Method isBiomeEnabled;
    private static Set<ResourceKey<Biome>> managedKeys = Set.of();

    private BopBiomeConfigFilter() {}

    @SuppressWarnings("unchecked")
    private static synchronized void bind() {
        if (available != null) return;
        try {
            Class<?> cfg = Class.forName(MOD_CONFIG);
            isBiomeEnabled = cfg.getMethod("isBiomeEnabled", ResourceKey.class);

            Object all = Class.forName(BOP_BIOMES).getMethod("getAllBiomes").invoke(null);
            Set<ResourceKey<Biome>> keys = new HashSet<>();
            if (all instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof ResourceKey<?> key) keys.add((ResourceKey<Biome>) key);
                }
            }
            managedKeys = keys;
            available = true;
        } catch (ClassNotFoundException notPresent) {
            available = false;
        } catch (Throwable t) {
            Isleweaver.LOGGER.debug("[Isleweaver] BoP biome-config bind failed (non-fatal): {}", t.toString());
            available = false;
        }
    }

    public static int filterDisabled(Map<ResourceKey<Biome>, Holder<Biome>> candidates) {
        bind();
        if (!Boolean.TRUE.equals(available) || managedKeys.isEmpty()) return 0;

        List<ResourceKey<Biome>> remove = new ArrayList<>();
        for (ResourceKey<Biome> key : candidates.keySet()) {
            if (!managedKeys.contains(key)) continue; // only judge biomes BoP manages
            try {
                if (!(boolean) isBiomeEnabled.invoke(null, key)) remove.add(key);
            } catch (Throwable t) {
                Isleweaver.LOGGER.debug("[Isleweaver] BoP isBiomeEnabled({}) failed (non-fatal): {}",
                        key.location(), t.toString());
            }
        }

        remove.forEach(candidates::remove);
        if (!remove.isEmpty()) {
            Isleweaver.LOGGER.debug("[Isleweaver] Excluded {} BoP-config-disabled End biome(s): {}",
                    remove.size(),
                    remove.stream().map(k -> k.location().toString()).sorted().toList());
        }
        return remove.size();
    }
}

package com.thebeyond.isleweaver.compat.unusualend;

import com.thebeyond.isleweaver.Isleweaver;
import com.thebeyond.isleweaver.mixin.enderscape.PlacedFeatureAccessor;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.CountOnEveryLayerPlacement;
import net.minecraft.world.level.levelgen.placement.HeightmapPlacement;
import net.minecraft.world.level.levelgen.placement.InSquarePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// decoration runs the features of every biome in the 3x3 chunks, so a reef feature without a biome filter lands on other islands
public final class ReefPlacementGuard {
    private static final Set<String> REEF_FEATURES = Set.of("warped_isles", "warped_citrine_isles", "warped_bubbles_feature",
            "warped_vegetation", "warped_rock_1_feature", "warped_patchs", "warped_bush", "grown_warped_bush", "warped_end_sprouts");
    private static final Set<String> SURFACE_FEATURES = Set.of("warped_patchs", "warped_bush", "grown_warped_bush", "warped_end_sprouts");

    private ReefPlacementGuard() {}

    public static void apply(MinecraftServer server) {
        Registry<PlacedFeature> registry = server.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
        int filtered = 0;
        int layered = 0;
        for (PlacedFeature pf : registry) {
            ResourceLocation id = registry.getKey(pf);
            if (id == null || !"unusualend".equals(id.getNamespace()) || !REEF_FEATURES.contains(id.getPath())) continue;
            List<PlacementModifier> mods = new ArrayList<>(pf.placement());
            boolean changed = false;
            if (SURFACE_FEATURES.contains(id.getPath()) && toEveryLayer(mods)) {
                layered++;
                changed = true;
            }
            if (mods.stream().noneMatch(m -> m instanceof BiomeFilter)) {
                mods.add(BiomeFilter.biome());
                filtered++;
                changed = true;
            }
            if (changed) ((PlacedFeatureAccessor) (Object) pf).isleweaver$setPlacement(List.copyOf(mods));
        }
        Isleweaver.LOGGER.debug("[Isleweaver] Reef guard: biome filter added to {}, {} surface feature(s) on every layer", filtered, layered);
    }

    // a reef islet is rarely the column top in layered terrain, so the surface decoration looks at every layer's top instead
    private static boolean toEveryLayer(List<PlacementModifier> mods) {
        if (mods.stream().noneMatch(m -> m instanceof HeightmapPlacement)) return false;
        int at = -1;
        for (int i = 0; i < mods.size() && at < 0; i++) {
            if (mods.get(i) instanceof HeightmapPlacement || mods.get(i) instanceof InSquarePlacement) at = i;
        }
        mods.removeIf(m -> m instanceof HeightmapPlacement || m instanceof InSquarePlacement);
        mods.add(at, CountOnEveryLayerPlacement.of(1));
        return true;
    }
}

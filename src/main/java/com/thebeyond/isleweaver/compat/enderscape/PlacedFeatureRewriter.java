package com.thebeyond.isleweaver.compat.enderscape;

import com.thebeyond.isleweaver.Isleweaver;
import com.thebeyond.isleweaver.mixin.enderscape.HeightRangePlacementAccessor;
import com.thebeyond.isleweaver.mixin.enderscape.PlacedFeatureAccessor;
import com.thebeyond.isleweaver.mixin.enderscape.UniformHeightAccessor;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.heightproviders.UniformHeight;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;

import java.util.ArrayList;
import java.util.List;

// Enderscape's height_range floors are absolute >= 0, swapped to above_bottom:0 so features cover the new [-64, 0] band
public final class PlacedFeatureRewriter {
    private PlacedFeatureRewriter() {}

    public static void rewrite(MinecraftServer server) {
        Registry<PlacedFeature> registry = server.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
        int total = 0;
        for (PlacedFeature pf : registry) {
            ResourceLocation id = registry.getKey(pf);
            if (id == null || !"enderscape".equals(id.getNamespace())) continue;
            if (rewritePlacedFeature(pf)) total++;
        }
        if (total > 0) {
            Isleweaver.LOGGER.debug("[Isleweaver] Rewrote {} Enderscape placed_features for Y<0 coverage", total);
        }
    }

    private static boolean rewritePlacedFeature(PlacedFeature pf) {
        List<PlacementModifier> original = pf.placement();
        List<PlacementModifier> modified = null;
        for (int i = 0; i < original.size(); i++) {
            PlacementModifier mod = original.get(i);
            PlacementModifier rewritten = rewriteModifier(mod);
            if (rewritten != mod) {
                if (modified == null) modified = new ArrayList<>(original);
                modified.set(i, rewritten);
            }
        }
        if (modified == null) return false;
        ((PlacedFeatureAccessor) (Object) pf).isleweaver$setPlacement(List.copyOf(modified));
        return true;
    }

    private static PlacementModifier rewriteModifier(PlacementModifier mod) {
        if (mod instanceof HeightRangePlacement hrp) {
            HeightProvider hp = ((HeightRangePlacementAccessor) (Object) hrp).isleweaver$getHeight();
            HeightProvider rewrittenHp = rewriteHeightProvider(hp);
            if (rewrittenHp != hp) {
                return HeightRangePlacement.of(rewrittenHp);
            }
        }
        return mod;
    }

    private static HeightProvider rewriteHeightProvider(HeightProvider hp) {
        if (hp instanceof UniformHeight uh) {
            UniformHeightAccessor acc = (UniformHeightAccessor) (Object) uh;
            VerticalAnchor min = acc.isleweaver$getMinInclusive();
            VerticalAnchor newMin = rewriteLowerAnchor(min);
            if (newMin != min) {
                return UniformHeight.of(newMin, acc.isleweaver$getMaxInclusive());
            }
        }
        return hp;
    }

    private static VerticalAnchor rewriteLowerAnchor(VerticalAnchor anchor) {
        if (anchor instanceof VerticalAnchor.Absolute abs && abs.y() >= 0) {
            return VerticalAnchor.aboveBottom(0);
        }
        return anchor;
    }
}

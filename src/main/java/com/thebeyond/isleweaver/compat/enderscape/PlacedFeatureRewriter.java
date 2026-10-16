package com.thebeyond.isleweaver.compat.enderscape;

import com.thebeyond.isleweaver.Isleweaver;
import com.thebeyond.isleweaver.mixin.enderscape.HeightRangePlacementAccessor;
import com.thebeyond.isleweaver.mixin.enderscape.PlacedFeatureAccessor;
import com.thebeyond.isleweaver.mixin.enderscape.UniformHeightAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.heightproviders.UniformHeight;
import net.minecraft.world.level.levelgen.placement.CountOnEveryLayerPlacement;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.InSquarePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

// Enderscape's height_range floors are absolute >= 0, swapped to above_bottom:0 so features cover the new [-64, 0] band
public final class PlacedFeatureRewriter {
    private static final Set<String> WIDE_CAPS = Set.of("large_celestial_chanterelles");

    private PlacedFeatureRewriter() {}

    public static void rewrite(MinecraftServer server) {
        Registry<PlacedFeature> registry = server.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
        int total = 0;
        for (PlacedFeature pf : registry) {
            ResourceLocation id = registry.getKey(pf);
            if (id == null || !"enderscape".equals(id.getNamespace())) continue;
            if (rewritePlacedFeature(pf)) total++;
            if (WIDE_CAPS.contains(id.getPath()) && boundSecondSpread(pf)) {
                Isleweaver.LOGGER.debug("[Isleweaver] {} keeps in_square but holds its cap within the neighbour chunks", id);
            }
        }
        if (total > 0) {
            Isleweaver.LOGGER.debug("[Isleweaver] Rewrote {} Enderscape placed_features for Y<0 coverage", total);
        }
    }

    private static boolean boundSecondSpread(PlacedFeature pf) {
        List<PlacementModifier> mods = new ArrayList<>(pf.placement());
        boolean spread = false;
        for (int i = 0; i < mods.size(); i++) {
            if (mods.get(i) instanceof CountOnEveryLayerPlacement) {
                spread = true;
            } else if (spread && mods.get(i) instanceof InSquarePlacement) {
                mods.set(i, CapSafeSquare.INSTANCE);
                ((PlacedFeatureAccessor) (Object) pf).isleweaver$setPlacement(List.copyOf(mods));
                return true;
            }
        }
        return false;
    }

    // caps reach 7 blocks, so an origin past local 24 would spill two chunks out and moves back 16, the rest stays put
    private static final class CapSafeSquare extends PlacementModifier {
        private static final CapSafeSquare INSTANCE = new CapSafeSquare();
        private static final int LAST_SAFE = 24;

        @Override
        public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos pos) {
            int x = pos.getX() + random.nextInt(16);
            int z = pos.getZ() + random.nextInt(16);
            if (x - (pos.getX() & ~15) > LAST_SAFE) x -= 16;
            if (z - (pos.getZ() & ~15) > LAST_SAFE) z -= 16;
            return Stream.of(new BlockPos(x, pos.getY(), z));
        }

        @Override
        public PlacementModifierType<?> type() {
            return PlacementModifierType.IN_SQUARE;
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

package com.thebeyond.isleweaver.mixin.unusualend;

import com.thebeyond.api.worldgen.BeyondEndBiomeSourceApi;
import com.thebeyond.api.worldgen.BeyondTerrainState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.Set;

@Mixin(BiomeFilter.class)
public abstract class BiomeFilterMixin {
    // these resolve their biome from the 3D Voronoi cell instead of the air->void mapping;
    // otherwise they sample air and never match their target biome
    private static final Set<ResourceLocation> convergence$VORONOI_FLOATING_FEATURES = Set.of(
            ResourceLocation.fromNamespaceAndPath("unusualend", "warped_isles"),
            ResourceLocation.fromNamespaceAndPath("unusualend", "warped_citrine_isles"),
            ResourceLocation.fromNamespaceAndPath("unusualend", "warped_bubbles_feature"),
            ResourceLocation.fromNamespaceAndPath("unusualend", "warped_vegetation")
    );

    @Inject(method = "shouldPlace", at = @At("HEAD"), cancellable = true)
    private void convergence$resolveBiome(
            PlacementContext ctx, RandomSource random, BlockPos pos,
            CallbackInfoReturnable<Boolean> cir) {
        if (!BeyondTerrainState.isActive()) return;
        if (!ctx.getLevel().getBlockState(pos).isAir()) return;

        Holder<Biome> biome;
        if (convergence$isVoronoiFloatingFeature(ctx)) {
            biome = convergence$lookupVoronoiBiome(ctx.getLevel(), pos);
            if (biome == null) return;
        } else {
            BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos(pos.getX(), 0, pos.getZ());
            boolean foundSurface = false;
            for (int dy = 1; dy <= 4; dy++) {
                probe.setY(pos.getY() - dy);
                if (!ctx.getLevel().getBlockState(probe).isAir()) {
                    foundSurface = true;
                    break;
                }
            }
            if (!foundSurface) return;
            biome = ctx.getLevel().getBiome(probe);
        }

        final Holder<Biome> resolved = biome;
        boolean result = ctx.topFeature()
            .filter(feature -> resolved.value().getGenerationSettings().hasFeature(feature))
            .isPresent();
        cir.setReturnValue(result);
    }

    private static boolean convergence$isVoronoiFloatingFeature(PlacementContext ctx) {
        Optional<PlacedFeature> top = ctx.topFeature();
        if (top.isEmpty()) return false;
        ResourceLocation id = ctx.getLevel().registryAccess()
            .registryOrThrow(Registries.PLACED_FEATURE)
            .getKey(top.get());
        return id != null && convergence$VORONOI_FLOATING_FEATURES.contains(id);
    }

    private static Holder<Biome> convergence$lookupVoronoiBiome(WorldGenLevel level, BlockPos pos) {
        BiomeSource source = level.getLevel().getChunkSource().getGenerator().getBiomeSource();
        if (!(source instanceof BeyondEndBiomeSourceApi bebs)) return null;
        return bebs.voronoiCellBiomeIgnoringDensity(pos.getX(), pos.getY(), pos.getZ());
    }
}

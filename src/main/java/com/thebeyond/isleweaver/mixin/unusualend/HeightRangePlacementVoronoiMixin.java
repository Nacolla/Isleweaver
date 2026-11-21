package com.thebeyond.isleweaver.mixin.unusualend;

import com.thebeyond.api.worldgen.BeyondEndBiomeSourceApi;
import com.thebeyond.api.worldgen.BeyondTerrainState;
import com.thebeyond.isleweaver.compat.unusualend.ChunkColumnReefCache;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

// Voronoi-floating features pick Y from the reef cell bands, a uniform Y would almost always miss
@Mixin(HeightRangePlacement.class)
public abstract class HeightRangePlacementVoronoiMixin {

    private static final Set<ResourceLocation> isleweaver$VORONOI_FLOATING_FEATURES = Set.of(
            ResourceLocation.fromNamespaceAndPath("unusualend", "warped_isles"),
            ResourceLocation.fromNamespaceAndPath("unusualend", "warped_citrine_isles"),
            ResourceLocation.fromNamespaceAndPath("unusualend", "warped_rock_1_feature"),
            ResourceLocation.fromNamespaceAndPath("unusualend", "warped_bubbles_feature"),
            ResourceLocation.fromNamespaceAndPath("unusualend", "warped_vegetation")
    );

    private static final int isleweaver$SCAN_STEP = 10;
    // Unusual End spreads each reef feature over y 15..115, so one draw per 100 blocks of reef keeps its density
    private static final int isleweaver$UE_BAND = 100;

    private static final ThreadLocal<ChunkColumnReefCache> isleweaver$CACHE =
            ThreadLocal.withInitial(ChunkColumnReefCache::new);
    private static final ThreadLocal<ChunkColumnReefCache> isleweaver$VOID_CACHE =
            ThreadLocal.withInitial(ChunkColumnReefCache::new);

    @Inject(method = "getPositions", at = @At("HEAD"), cancellable = true)
    private void isleweaver$voronoiCellAwareY(
            PlacementContext ctx, RandomSource random, BlockPos pos,
            CallbackInfoReturnable<Stream<BlockPos>> cir) {
        if (!BeyondTerrainState.isActive()) return;

        Optional<PlacedFeature> top = ctx.topFeature();
        if (top.isEmpty()) return;
        ResourceLocation featureId = ctx.getLevel().registryAccess()
                .registryOrThrow(Registries.PLACED_FEATURE)
                .getKey(top.get());
        if (featureId == null || !isleweaver$VORONOI_FLOATING_FEATURES.contains(featureId)) return;

        BeyondEndBiomeSourceApi bebs = isleweaver$lookupBiomeSource(ctx.getLevel());
        if (bebs == null) return;

        int x = pos.getX();
        int z = pos.getZ();
        int minY = ctx.getMinBuildHeight();
        int maxY = ctx.getLevel().getMaxBuildHeight() - 1;

        IntList reefYs = isleweaver$scanReefBands(bebs, top.get(), x, z, minY, maxY);
        if (reefYs.isEmpty()) {
            // a reef listed as a void biome is no Voronoi cell, so the reef quarts of this column give the Y
            IntList quarts = isleweaver$scanVoidReef(ctx.getLevel(), top.get(), x, z, minY, maxY);
            cir.setReturnValue(isleweaver$voidReefY(quarts, random, x, z));
            return;
        }

        int bandY = reefYs.getInt(random.nextInt(reefYs.size()));
        int y = Math.min(maxY, bandY + random.nextInt(isleweaver$SCAN_STEP));
        cir.setReturnValue(Stream.of(new BlockPos(x, y, z)));
    }

    private static Stream<BlockPos> isleweaver$voidReefY(IntList quarts, RandomSource random, int x, int z) {
        if (quarts.isEmpty()) return Stream.empty();
        double draws = quarts.size() * 4.0 / isleweaver$UE_BAND;
        int n = (int) draws + (random.nextDouble() < draws - (int) draws ? 1 : 0);
        List<BlockPos> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int qy = quarts.getInt(random.nextInt(quarts.size()));
            out.add(new BlockPos(x, QuartPos.toBlock(qy) + random.nextInt(4), z));
        }
        return out.stream();
    }

    private static BeyondEndBiomeSourceApi isleweaver$lookupBiomeSource(WorldGenLevel level) {
        ServerLevel sl = level.getLevel();
        BiomeSource source = sl.getChunkSource().getGenerator().getBiomeSource();
        return source instanceof BeyondEndBiomeSourceApi bebs ? bebs : null;
    }

    private static IntList isleweaver$scanReefBands(
            BeyondEndBiomeSourceApi bebs, PlacedFeature feature, int x, int z, int minY, int maxY) {
        long chunkKey = ((long) (x >> 4) << 32) | ((z >> 4) & 0xFFFFFFFFL);
        long colKey = ((long) x << 32) | (z & 0xFFFFFFFFL);
        ChunkColumnReefCache cache = isleweaver$CACHE.get();
        IntList cached = cache.get(chunkKey, colKey);
        if (cached != null) return cached;

        IntList reefYs = new IntArrayList();
        for (int y = minY; y <= maxY; y += isleweaver$SCAN_STEP) {
            Holder<Biome> biome = bebs.voronoiCellBiomeIgnoringDensity(x, y, z);
            if (biome.value().getGenerationSettings().hasFeature(feature)) {
                reefYs.add(y);
            }
        }
        cache.put(chunkKey, colKey, reefYs);
        return reefYs;
    }

    // reads the biomes stored in the chunk being decorated, so the reef found here is the reef the chunk really has
    private static IntList isleweaver$scanVoidReef(
            WorldGenLevel level, PlacedFeature feature, int x, int z, int minY, int maxY) {
        long chunkKey = ((long) (x >> 4) << 32) | ((z >> 4) & 0xFFFFFFFFL);
        long colKey = ((long) x << 32) | (z & 0xFFFFFFFFL);
        ChunkColumnReefCache cache = isleweaver$VOID_CACHE.get();
        IntList cached = cache.get(chunkKey, colKey);
        if (cached != null) return cached;

        IntList quarts = new IntArrayList();
        int qx = QuartPos.fromBlock(x);
        int qz = QuartPos.fromBlock(z);
        for (int qy = QuartPos.fromBlock(minY); qy <= QuartPos.fromBlock(maxY); qy++) {
            if (level.getNoiseBiome(qx, qy, qz).value().getGenerationSettings().hasFeature(feature)) quarts.add(qy);
        }
        cache.put(chunkKey, colKey, quarts);
        return quarts;
    }
}

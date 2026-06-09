package com.thebeyond.isleweaver.compat.betterend;

import com.thebeyond.common.worldgen.BeyondEndChunkGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

// anchors the glowshroom to the thickest real foggy_mushroomland run; one spot per chunk so the biome check and the placement agree
public final class MushroomAnchor {
    private static final ResourceKey<Biome> FOGGY = ResourceKey.create(
            Registries.BIOME, ResourceLocation.fromNamespaceAndPath("betterend", "foggy_mushroomland"));
    private static final int BURY = 3;
    private static final int MIN_ISLAND_THICK = 4;
    // a foggy run this thick is good enough to take right away instead of scanning the rest
    private static final int EARLY_THICK = 8;
    private static final int[] SAMPLES = {5, 11};
    private static final int CACHE_LIMIT = 4096;
    private static final int[] NO_SPOT = new int[0];
    private static final ConcurrentMap<Long, int[]> CACHE = new ConcurrentHashMap<>();

    private MushroomAnchor() {}

    private static long key(int cx, int cz) {
        return ((long) cx << 32) | ((long) cz & 0xFFFFFFFFL);
    }

    // best foggy island surface in the chunk, sunk BURY blocks down; null means none found, so skip generation here
    public static int[] computeFoggySpot(ChunkGenerator gen, int cx, int cz,
                                         LevelHeightAccessor lvl, RandomState rs) {
        long k = key(cx, cz);
        int[] hit = CACHE.get(k);
        if (hit != null) return hit.length == 3 ? hit : null;

        int[] result;
        try {
            result = scan(gen, cx, cz, lvl, rs);
        } catch (Throwable t) {
            result = null; // any failure just means no spot here, never crash worldgen
        }
        if (CACHE.size() < CACHE_LIMIT) CACHE.put(k, result != null ? result : NO_SPOT);
        return result;
    }

    // scans the same density field generateEndTerrain uses, so we anchor to terrain that'll actually exist; non-foggy islands are skipped
    private static int[] scan(ChunkGenerator gen, int cx, int cz,
                              LevelHeightAccessor lvl, RandomState rs) {
        int minY = lvl.getMinBuildHeight();
        int maxY = lvl.getMaxBuildHeight() - 1;
        BeyondEndChunkGenerator.ColumnScratch scratch = BeyondEndChunkGenerator.getColumnScratch();
        int[] bestFoggy = null;
        int bestFoggyThick = -1;

        for (int sx : SAMPLES) {
            int wx = (cx << 4) + sx;
            for (int sz : SAMPLES) {
                int wz = (cz << 4) + sz;
                float dist = (float) Math.sqrt((double) wx * wx + (double) wz * wz);
                BeyondEndChunkGenerator.initColumnScratch(wx, wz, dist, scratch);

                int y = maxY;
                while (y >= minY) {
                    if (!BeyondEndChunkGenerator.isSolidTerrainScratch(y, scratch)) { y--; continue; }
                    int top = y;
                    int bot = y;
                    while (bot - 1 >= minY
                            && BeyondEndChunkGenerator.isSolidTerrainScratch(bot - 1, scratch)) bot--;
                    int thick = top - bot + 1;
                    if (thick >= MIN_ISLAND_THICK
                            && gen.getBiomeSource()
                                  .getNoiseBiome(wx >> 2, top >> 2, wz >> 2, rs.sampler()).is(FOGGY)) {
                        if (thick >= EARLY_THICK) {                 // thick enough, take it and stop scanning
                            return new int[]{wx, top - BURY, wz};
                        }
                        if (thick > bestFoggyThick) {
                            bestFoggyThick = thick;
                            bestFoggy = new int[]{wx, top - BURY, wz};
                        }
                    }
                    y = bot - 1;
                }
            }
        }
        return bestFoggy;
    }

    public static int[] cached(int cx, int cz) {
        int[] c = CACHE.get(key(cx, cz));
        return (c != null && c.length == 3) ? c : null;
    }

    // tells the glowshroom path apart from painted_mountain inside the shared FeatureBaseStructure wraps
    public static boolean has(int cx, int cz) {
        return cached(cx, cz) != null;
    }

    public static int alignedY(int worldX, int worldZ, int fallback) {
        int[] s = cached(worldX >> 4, worldZ >> 4);
        return s != null ? s[1] : fallback;
    }

    public static BlockPos alignedBlockPos(int x, int y, int z) {
        int[] s = cached(x >> 4, z >> 4);
        return s != null ? new BlockPos(s[0], s[1], s[2]) : new BlockPos(x, y, z);
    }

    public static BlockPos.MutableBlockPos alignedSet(BlockPos.MutableBlockPos pos,
                                                      int x, int y, int z) {
        int[] s = cached(x >> 4, z >> 4);
        return s != null ? pos.set(s[0], s[1], s[2]) : pos.set(x, y, z);
    }
}

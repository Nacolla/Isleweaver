package com.thebeyond.isleweaver.compat;

import com.thebeyond.api.worldgen.BeyondEndBiomeSourceApi;
import com.thebeyond.isleweaver.Isleweaver;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

/** Gives a foreign biome a lifted body on Beyond islands when its native End islands are nearly free of end stone. */
public final class NativeBodySampler {
    private static final double MAX_DEFAULT_SHARE = 0.15;
    private static final int SAMPLE_CHUNKS = 8;
    private static final int MIN_SOLID_PER_CHUNK = 1024;
    private static final int DENSITY_BUDGET = 200_000;
    private static final long BIOME_TIMEOUT_MS = 10_000L;
    private static final long TOTAL_BUDGET_MS = 60_000L;
    private static final int FOREIGN_CHUNK_BUDGET = 96;

    private NativeBodySampler() {
    }

    public static List<SurfaceRules.RuleSource> fills(RegistryAccess access, Iterable<Holder<Biome>> pool,
                                                      SurfaceRules.RuleSource surfaceRule, long seed) {
        List<SurfaceRules.RuleSource> out = new ArrayList<>();
        List<Holder<Biome>> foreign = new ArrayList<>();
        for (Holder<Biome> biome : pool) {
            String ns = biome.unwrapKey().map(k -> k.location().getNamespace()).orElse("minecraft");
            if (biome.isBound() && !ns.equals("minecraft") && !ns.equals("the_beyond")) {
                foreign.add(biome);
            }
        }
        if (foreign.isEmpty()) {
            return out;
        }
        ChunkGenerator[] terrain = new ChunkGenerator[1];
        NoiseGeneratorSettings nativeEnd = nativeEndSettings(access, terrain);
        if (nativeEnd == null) {
            Isleweaver.LOGGER.info("[Isleweaver] No native End terrain to sample, no body fills");
            return out;
        }
        NoiseGeneratorSettings settings = new NoiseGeneratorSettings(nativeEnd.noiseSettings(), nativeEnd.defaultBlock(),
                nativeEnd.defaultFluid(), nativeEnd.noiseRouter(), surfaceRule, nativeEnd.spawnTarget(), nativeEnd.seaLevel(),
                nativeEnd.disableMobGeneration(), nativeEnd.aquifersEnabled(), nativeEnd.oreVeinsEnabled(),
                nativeEnd.useLegacyRandomSource());
        Registry<Biome> biomes = access.registryOrThrow(Registries.BIOME);
        long start = System.nanoTime();
        ExecutorService worker = newWorker();
        try {
            RandomState random = RandomState.create(settings, access.lookupOrThrow(Registries.NOISE), seed);
            NoiseBasedChunkGenerator probe = new NoiseBasedChunkGenerator(new FixedBiomeSource(foreign.get(0)), Holder.direct(settings));
            Map<ChunkPos, Integer> searched;
            try {
                searched = worker.submit(() -> findIslands(probe, terrain[0], settings, random, biomes, seed)).get(BIOME_TIMEOUT_MS * 2, TimeUnit.MILLISECONDS);
            } catch (Throwable t) {
                Isleweaver.LOGGER.warn("[Isleweaver] Native End terrain could not be searched, no body fills: {}", t.toString());
                worker.shutdownNow();
                return out;
            }
            Map<ChunkPos, Integer> islands = searched;
            if (islands.isEmpty()) {
                Isleweaver.LOGGER.info("[Isleweaver] No native End island found to sample, no body fills");
                return out;
            }
            int sampled = 0;
            for (Holder<Biome> biome : foreign) {
                ResourceKey<Biome> key = biome.unwrapKey().orElseThrow();
                if ((System.nanoTime() - start) / 1_000_000L > TOTAL_BUDGET_MS) {
                    Isleweaver.LOGGER.warn("[Isleweaver] Native body sampling ran out of time before {}, the rest are left as is", key.location());
                    break;
                }
                Future<Map<BlockState, Integer>> job = worker.submit(() -> sample(biome, terrain[0], settings, random, biomes, seed, islands));
                Map<BlockState, Integer> counts;
                try {
                    counts = job.get(BIOME_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                } catch (Throwable t) {
                    job.cancel(true);
                    worker.shutdownNow();
                    worker = newWorker();
                    Isleweaver.LOGGER.warn("[Isleweaver] Native body of {} could not be sampled, left as is: {}", key.location(), t.toString());
                    continue;
                }
                sampled++;
                int solid = counts.values().stream().mapToInt(Integer::intValue).sum();
                int noiseSolid = islands.values().stream().mapToInt(Integer::intValue).sum();
                int defaults = counts.entrySet().stream()
                        .filter(e -> e.getKey().is(settings.defaultBlock().getBlock()) || e.getKey().is(Blocks.END_STONE))
                        .mapToInt(Map.Entry::getValue).sum();
                BlockState top = counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
                double defaultShare = solid == 0 ? 1.0 : defaults / (double) solid;
                // a surface pass that erased most of the terrain means something else ran on the temporary generator
                boolean coherent = solid >= noiseSolid / 2;
                boolean fill = coherent && defaultShare <= MAX_DEFAULT_SHARE;
                Isleweaver.LOGGER.info("[Isleweaver] Native body of {}: end stone {}% of {} blocks, mostly {}, fill {}{}", key.location(),
                        Math.round(defaultShare * 100), solid, top == null ? "none" : top, fill ? "on" : "off",
                        coherent ? "" : " (sample discarded, the surface pass removed the terrain)");
                if (fill) {
                    out.add(SurfaceRules.ifTrue(SurfaceRules.isBiome(key), new LiftedBodyRule(surfaceRule)));
                }
            }
            Isleweaver.LOGGER.info("[Isleweaver] Sampled {} foreign End biome(s) on {} native island chunk(s) in {} ms, {} body fill(s)",
                    sampled, islands.size(), (System.nanoTime() - start) / 1_000_000L, out.size());
        } finally {
            worker.shutdownNow();
        }
        return out;
    }

    // the End as the world preset defines it (datapack terrain included), a foreign generator there only shapes the islands
    private static NoiseGeneratorSettings nativeEndSettings(RegistryAccess access, ChunkGenerator[] terrain) {
        try {
            WorldPreset preset = access.registryOrThrow(Registries.WORLD_PRESET).get(WorldPresets.NORMAL);
            LevelStem end = preset == null ? null : preset.createWorldDimensions().dimensions().get(LevelStem.END);
            if (end != null && end.generator() instanceof NoiseBasedChunkGenerator noise
                    && !(noise.getBiomeSource() instanceof BeyondEndBiomeSourceApi)) {
                return noise.generatorSettings().value();
            }
            if (end != null && !(end.generator() instanceof NoiseBasedChunkGenerator)) {
                terrain[0] = end.generator();
                Isleweaver.LOGGER.info("[Isleweaver] Native End uses {}, sampling its terrain", end.generator().getClass().getName());
            }
        } catch (Throwable t) {
            Isleweaver.LOGGER.info("[Isleweaver] Native End preset could not be read, using minecraft:end noise settings: {}", t.toString());
        }
        return access.registryOrThrow(Registries.NOISE_SETTINGS).get(NoiseGeneratorSettings.END);
    }

    private static ExecutorService newWorker() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Isleweaver native body sampler");
            t.setDaemon(true);
            return t;
        });
    }

    private static Map<ChunkPos, Integer> findIslands(NoiseBasedChunkGenerator generator, @Nullable ChunkGenerator terrain,
                                                      NoiseGeneratorSettings settings, RandomState random, Registry<Biome> biomes,
                                                      long seed) {
        Map<ChunkPos, Integer> found = new HashMap<>();
        if (terrain != null) {
            // a foreign generator exposes no density, so its chunks are generated along the same rings
            for (int i = 0; i < FOREIGN_CHUNK_BUDGET && found.size() < SAMPLE_CHUNKS && !Thread.currentThread().isInterrupted(); i++) {
                double angle = i * 2.399963;
                int radius = 16 + 4 * i;
                ChunkPos pos = new ChunkPos((int) (Math.cos(angle) * radius), (int) (Math.sin(angle) * radius));
                if (nearFound(found, pos.getMiddleBlockX(), pos.getMiddleBlockZ())) {
                    continue;
                }
                int solid = count(generate(generator, terrain, settings, random, biomes, seed, pos, false), new HashMap<>());
                if (solid >= MIN_SOLID_PER_CHUNK) {
                    found.put(pos, solid);
                }
            }
            return found;
        }
        DensityFunction density = random.router().finalDensity();
        NoiseSettings noise = settings.noiseSettings();
        int[] budget = {DENSITY_BUDGET};
        searchRings(1024, 8192, density, noise, generator, settings, random, biomes, seed, found, budget);
        if (found.size() < SAMPLE_CHUNKS) {
            searchRings(0, 1024, density, noise, generator, settings, random, biomes, seed, found, budget);
        }
        return found;
    }

    private static void searchRings(int from, int to, DensityFunction density, NoiseSettings noise, NoiseBasedChunkGenerator generator,
                                    NoiseGeneratorSettings settings, RandomState random, Registry<Biome> biomes, long seed,
                                    Map<ChunkPos, Integer> found, int[] budget) {
        for (int radius = from; radius <= to; radius += 64) {
            int steps = Math.max(1, (int) (2 * Math.PI * radius / 64));
            for (int i = 0; i < steps; i++) {
                if (found.size() >= SAMPLE_CHUNKS || budget[0] <= 0 || Thread.currentThread().isInterrupted()) {
                    return;
                }
                double angle = 2 * Math.PI * i / steps;
                int x = (int) (Math.cos(angle) * radius);
                int z = (int) (Math.sin(angle) * radius);
                if (!hasLand(density, noise, x, z, budget) || nearFound(found, x, z)) {
                    continue;
                }
                ChunkPos pos = new ChunkPos(x >> 4, z >> 4);
                int solid = count(generate(generator, null, settings, random, biomes, seed, pos, false), new HashMap<>());
                if (solid >= MIN_SOLID_PER_CHUNK) {
                    found.put(pos, solid);
                }
            }
        }
    }

    private static boolean hasLand(DensityFunction density, NoiseSettings noise, int x, int z, int[] budget) {
        for (int y = noise.minY() + 4; y < noise.minY() + noise.height(); y += 8) {
            budget[0]--;
            if (density.compute(new DensityFunction.SinglePointContext(x, y, z)) > 0.0) {
                return true;
            }
        }
        return false;
    }

    private static boolean nearFound(Map<ChunkPos, Integer> found, int x, int z) {
        for (ChunkPos pos : found.keySet()) {
            int dx = pos.getMiddleBlockX() - x;
            int dz = pos.getMiddleBlockZ() - z;
            if (dx * dx + dz * dz < 96 * 96) {
                return true;
            }
        }
        return false;
    }

    private static Map<BlockState, Integer> sample(Holder<Biome> biome, @Nullable ChunkGenerator terrain,
                                                   NoiseGeneratorSettings settings, RandomState random,
                                                   Registry<Biome> biomes, long seed, Map<ChunkPos, Integer> islands) {
        NoiseBasedChunkGenerator generator = new NoiseBasedChunkGenerator(new FixedBiomeSource(biome), Holder.direct(settings));
        Map<BlockState, Integer> counts = new HashMap<>();
        for (ChunkPos pos : islands.keySet()) {
            count(generate(generator, terrain, settings, random, biomes, seed, pos, true), counts);
        }
        return counts;
    }

    private static ChunkAccess generate(NoiseBasedChunkGenerator generator, @Nullable ChunkGenerator terrain,
                                        NoiseGeneratorSettings settings, RandomState random, Registry<Biome> biomes, long seed,
                                        ChunkPos pos, boolean surface) {
        LevelHeightAccessor height = terrain != null ? LevelHeightAccessor.create(terrain.getMinY(), terrain.getGenDepth())
                : LevelHeightAccessor.create(settings.noiseSettings().minY(), settings.noiseSettings().height());
        ProtoChunk chunk = new ProtoChunk(pos, UpgradeData.EMPTY, height, biomes, null);
        StructureManager noStructures = new StructureManager(null, null, null) {
            @Override
            public List<StructureStart> startsForStructure(ChunkPos chunkPos, Predicate<Structure> predicate) {
                return List.of();
            }
        };
        generator.createBiomes(random, Blender.empty(), noStructures, chunk).join();
        chunk.setPersistedStatus(ChunkStatus.BIOMES);
        (terrain != null ? terrain : generator).fillFromNoise(Blender.empty(), random, noStructures, chunk).join();
        chunk.setPersistedStatus(ChunkStatus.NOISE);
        if (surface) {
            generator.buildSurface(chunk, new WorldGenerationContext(generator, chunk), random, noStructures,
                    new BiomeManager(chunk, BiomeManager.obfuscateSeed(seed)), biomes, Blender.empty());
        }
        return chunk;
    }

    private static int count(ChunkAccess chunk, Map<BlockState, Integer> counts) {
        int solid = 0;
        for (LevelChunkSection section : chunk.getSections()) {
            if (section.hasOnlyAir()) {
                continue;
            }
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (state.isAir() || !state.getFluidState().isEmpty()) {
                            continue;
                        }
                        counts.merge(state, 1, Integer::sum);
                        solid++;
                    }
                }
            }
        }
        return solid;
    }
}

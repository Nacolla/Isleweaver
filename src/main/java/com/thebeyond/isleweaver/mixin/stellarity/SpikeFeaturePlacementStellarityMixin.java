package com.thebeyond.isleweaver.mixin.stellarity;

import com.thebeyond.isleweaver.Isleweaver;
import com.thebeyond.api.worldgen.BeyondTerrainState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.levelgen.feature.SpikeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.SpikeConfiguration;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.HashMap;
import java.util.Map;

// places Stellarity's ring spikes with vanilla absolute-Y semantics; heights come from a static table
// because BetterEnd intercepts getHeight() and would otherwise remap them
@Mixin(SpikeFeature.class)
public abstract class SpikeFeaturePlacementStellarityMixin {

    // raw heights from Stellarity's ring.json keyed by BlockPos.asLong(x, 0, z), so we sidestep BetterEnd's getHeight() mixin
    private static final Map<Long, Integer> CONVERGENCE$RAW_HEIGHTS = new HashMap<>();

    static {
        int[][] data = {
                { 63,   0, 100}, { 50,  36, 105}, { 18,  59,  94}, {-19,  59, 106},
                {-51,  36, 105}, {-63,   0,  93}, {-51, -39, 100}, {-19, -60,  96},
                { 18, -60,  87}, { 50, -39,  95}
        };
        for (int[] d : data) {
            CONVERGENCE$RAW_HEIGHTS.put(BlockPos.asLong(d[0], 0, d[1]), d[2]);
        }
    }

    private static int convergence$getRawHeight(SpikeFeature.EndSpike spike) {
        Integer raw = CONVERGENCE$RAW_HEIGHTS.get(
                BlockPos.asLong(spike.getCenterX(), 0, spike.getCenterZ()));
        return raw != null ? raw : spike.getHeight();
    }

    @Shadow
    private void placeSpike(ServerLevelAccessor level, RandomSource random,
                            SpikeConfiguration config, SpikeFeature.EndSpike spike) {}

    @Redirect(method = "place",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/world/level/levelgen/feature/SpikeFeature;placeSpike(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/level/levelgen/feature/configurations/SpikeConfiguration;Lnet/minecraft/world/level/levelgen/feature/SpikeFeature$EndSpike;)V"))
    private void convergence$redirectPlaceSpike(
            SpikeFeature instance,
            ServerLevelAccessor level,
            RandomSource random,
            SpikeConfiguration config,
            SpikeFeature.EndSpike spike) {
        if (!ModList.get().isLoaded("stellarity") || !ModList.get().isLoaded("betterend")) {
            placeSpike(level, random, config, spike);
            return;
        }
        if (!BeyondTerrainState.isActive()) {
            placeSpike(level, random, config, spike);
            return;
        }

        // let BetterEnd handle the center spike (0,0); an event handler places its marker crystal
        if (spike.getCenterX() == 0 && spike.getCenterZ() == 0) {
            placeSpike(level, random, config, spike);
            return;
        }

        int rawHeight = convergence$getRawHeight(spike);
        Isleweaver.LOGGER.debug(
                "[Isleweaver] Stellarity compat: placing spike at ({}, {}) with vanilla absolute-Y semantics (rawHeight={}, interceptedHeight={}, radius={}, crystal will be at y={})",
                spike.getCenterX(), spike.getCenterZ(), rawHeight, spike.getHeight(), spike.getRadius(), rawHeight + 1);

        convergence$vanillaPlaceSpike(level, random, config, spike);
    }

    // vanilla SpikeFeature.placeSpike reimplemented to build from raw heights instead of the intercepted ones
    private void convergence$vanillaPlaceSpike(
            ServerLevelAccessor level,
            RandomSource random,
            SpikeConfiguration config,
            SpikeFeature.EndSpike spike) {
        int radius = spike.getRadius();
        int centerX = spike.getCenterX();
        int centerZ = spike.getCenterZ();
        int height = convergence$getRawHeight(spike);

        for (BlockPos blockpos : BlockPos.betweenClosed(
                new BlockPos(centerX - radius, level.getMinBuildHeight(), centerZ - radius),
                new BlockPos(centerX + radius, height + 10, centerZ + radius))) {
            if (blockpos.distToLowCornerSqr((double) centerX, (double) blockpos.getY(), (double) centerZ)
                    <= (double) (radius * radius + 1)
                    && blockpos.getY() < height) {
                level.setBlock(blockpos, Blocks.OBSIDIAN.defaultBlockState(), 3);
            } else if (blockpos.getY() > 65) {
                level.setBlock(blockpos, Blocks.AIR.defaultBlockState(), 3);
            }
        }

        EndCrystal crystal = EntityType.END_CRYSTAL.create(level.getLevel());
        if (crystal != null) {
            crystal.setBeamTarget(config.getCrystalBeamTarget());
            crystal.setInvulnerable(config.isCrystalInvulnerable());
            crystal.moveTo(
                    (double) centerX + 0.5D,
                    (double) (height + 1),
                    (double) centerZ + 0.5D,
                    random.nextFloat() * 360.0F,
                    0.0F);
            level.addFreshEntity(crystal);
            BlockPos crystalPos = crystal.blockPosition();
            level.setBlock(crystalPos.below(), Blocks.BEDROCK.defaultBlockState(), 3);
            level.setBlock(crystalPos, FireBlock.getState(level, crystalPos), 3);
        }
    }
}

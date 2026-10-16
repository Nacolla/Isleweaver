package com.thebeyond.isleweaver.mixin.phantasm;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thebeyond.api.worldgen.BeyondTerrainState;
import com.thebeyond.isleweaver.Isleweaver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.atomic.AtomicInteger;

// crystal_spike's origin sits in air, so its biome gate and pillar scan re-anchor on the surface below
@Pseudo
@Mixin(targets = "net.lyof.phantasm.world.feature.CrystalSpikeFeature")
public abstract class CrystalSpikeFeatureMixin {
    private static final TagKey<Biome> IS_DREAMING_DEN = TagKey.create(
            Registries.BIOME, ResourceLocation.fromNamespaceAndPath("phantasm", "is_dreaming_den"));

    // safe: getBiome writes before the pillar reads, inside one single-threaded place() call
    private static final ThreadLocal<Integer> the_beyond$pillarStartY =
            ThreadLocal.withInitial(() -> Integer.MIN_VALUE);

    private static final AtomicInteger the_beyond$logBudget = new AtomicInteger(40);

    @WrapOperation(
            method = "place",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/WorldGenLevel;getBiome(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Holder;"),
            require = 1)
    private Holder<Biome> the_beyond$resolveGateFromSurface(
            WorldGenLevel level, BlockPos origin, Operation<Holder<Biome>> original) {
        if (!BeyondTerrainState.isActive()) {
            return original.call(level, origin);
        }
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos(origin.getX(), 0, origin.getZ());
        Holder<Biome> resolved = null;
        int surfaceY = Integer.MIN_VALUE;
        for (int dy = 1; dy <= 4; dy++) {
            probe.setY(origin.getY() - dy);
            if (!level.getBlockState(probe).isAir()) {
                surfaceY = probe.getY();
                resolved = level.getBiome(probe);
                break;
            }
        }
        the_beyond$pillarStartY.set(surfaceY != Integer.MIN_VALUE ? surfaceY : origin.getY() - 2);
        if (resolved == null) {
            // no surface: the origin biome is likely air, so place() bails instead of a wrong-biome crystal
            resolved = original.call(level, origin);
        }
        boolean matched = resolved.is(IS_DREAMING_DEN);
        if (Isleweaver.TRACE && the_beyond$logBudget.getAndDecrement() > 0) {
            Isleweaver.LOGGER.debug(
                    "[Isleweaver][CrystalSpike] gate originY={} surfaceY={} matched={} (place {})",
                    origin.getY(), surfaceY, matched, matched ? "REACHED" : "BAILED");
        }
        return resolved;
    }

    @Redirect(
            method = "place",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;atY(I)Lnet/minecraft/core/BlockPos;"),
            require = 2)
    private BlockPos the_beyond$reanchorPillarScan(BlockPos self, int y) {
        // y == 1 starts the upward pillar scan, y == 0 is the downward scan and stays absolute
        if (!BeyondTerrainState.isActive() || y != 1) {
            return self.atY(y);
        }
        int s = the_beyond$pillarStartY.get();
        int anchored = s != Integer.MIN_VALUE ? s : self.getY() - 2;
        if (Isleweaver.TRACE && the_beyond$logBudget.get() > 0) {
            Isleweaver.LOGGER.debug(
                    "[Isleweaver][CrystalSpike] pillar scan re-anchor originY={} -> startY={}", self.getY(), anchored);
        }
        return self.atY(anchored);
    }
}

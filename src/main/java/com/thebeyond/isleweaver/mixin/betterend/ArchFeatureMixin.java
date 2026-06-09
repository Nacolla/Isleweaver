package com.thebeyond.isleweaver.mixin.betterend;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.thebeyond.api.worldgen.BeyondTerrainState;
import com.thebeyond.isleweaver.compat.betterend.ArchCarve;
import com.thebeyond.isleweaver.compat.betterend.ArchOrient;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// keep the arch torus anchored on the island and clip the buried cone so it doesn't end up floating on Beyond's stacks
@Pseudo
@Mixin(targets = "org.betterx.betterend.world.features.terrain.ArchFeature", remap = false)
public abstract class ArchFeatureMixin {
    @ModifyExpressionValue(
        method = "place",
        at = @At(
            value = "INVOKE",
            target = "Lorg/betterx/bclib/api/v2/levelgen/features/features/DefaultFeature;getPosOnSurfaceWG(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;",
            remap = false
        )
    )
    private BlockPos convergence$preserveOrigin(
            BlockPos original,
            @Local(argsOnly = true) FeaturePlaceContext<?> ctx) {
        return BeyondTerrainState.isActive() ? ctx.origin() : original;
    }

    // pick the ring axis whose springpoints (centre ± bigRadius·axis) sit most on the island
    @ModifyExpressionValue(
        method = "place",
        at = @At(
            value = "INVOKE",
            target = "Lorg/betterx/bclib/util/MHelper;randomHorizontal(Lnet/minecraft/util/RandomSource;)Lorg/joml/Vector3f;",
            remap = false
        )
    )
    private Vector3f convergence$fitOrientation(
            Vector3f original,
            @Local(argsOnly = true) FeaturePlaceContext<?> ctx,
            @Local(index = 9) float bigR) {
        if (!BeyondTerrainState.isActive()) return original;
        return ArchOrient.fit(ctx.level(), ctx.origin(), bigR, original);
    }

    @WrapOperation(
        method = "place",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/WorldGenLevel;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
        )
    )
    private BlockState convergence$relaxDepthProbe(
            WorldGenLevel level, BlockPos posBelow5, Operation<BlockState> op) {
        if (BeyondTerrainState.isActive()) {
            return op.call(level, posBelow5.above(4)); // net pos.below(1) — probe just under the arch instead of 5 deep
        }
        return op.call(level, posBelow5);
    }

    @Inject(
        method = "place",
        at = @At(
            value = "INVOKE",
            target = "Lorg/betterx/bclib/sdf/SDF;fillArea(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/AABB;)V",
            remap = false,
            shift = At.Shift.BEFORE
        ),
        cancellable = true
    )
    private void convergence$carveSnapshot(
            FeaturePlaceContext<?> ctx, CallbackInfoReturnable<Boolean> cir,
            @Local(index = 8) BlockPos sdfCenter,
            @Local(index = 14) float side,
            @Local(index = 3) WorldGenLevel world) {
        if (!BeyondTerrainState.isActive()) return;
        ArchCarve.snapshot(world, sdfCenter, side);
    }

    @Inject(
        method = "place",
        at = @At(
            value = "INVOKE",
            target = "Lorg/betterx/bclib/sdf/SDF;fillArea(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/AABB;)V",
            remap = false,
            shift = At.Shift.AFTER
        )
    )
    private void convergence$carveApply(
            FeaturePlaceContext<?> ctx, CallbackInfoReturnable<Boolean> cir,
            @Local(index = 3) WorldGenLevel world) {
        if (BeyondTerrainState.isActive()) {
            ArchCarve.carve(world);
        }
    }
}

package com.thebeyond.isleweaver.mixin.betterend;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.thebeyond.api.worldgen.BeyondTerrainState;
import com.thebeyond.isleweaver.compat.betterend.ArchCarve;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// tilted-arch counterpart of ArchFeatureMixin: anchor the SDF at ctx.origin() and clip the buried cone
@Pseudo
@Mixin(targets = "org.betterx.betterend.world.features.terrain.ThinArchFeature", remap = false)
public abstract class ThinArchFeatureMixin {
    @ModifyExpressionValue(
        method = "place",
        at = @At(
            value = "INVOKE",
            target = "Lorg/betterx/bclib/api/v2/levelgen/features/features/DefaultFeature;getPosOnSurfaceWG(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;",
            remap = false
        )
    )
    private BlockPos isleweaver$preserveOrigin(
            BlockPos original,
            @Local(argsOnly = true) FeaturePlaceContext<?> ctx) {
        return BeyondTerrainState.isActive() ? ctx.origin() : original;
    }

    @WrapOperation(
        method = "place",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/WorldGenLevel;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
        )
    )
    private BlockState isleweaver$relaxDepthProbe(
            WorldGenLevel level, BlockPos posBelow5, Operation<BlockState> op) {
        if (BeyondTerrainState.isActive()) {
            return op.call(level, posBelow5.above(4)); // net pos.below(1), probe just under origin instead of 5 down
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
        )
    )
    private void isleweaver$carveSnapshot(
            FeaturePlaceContext<?> ctx, CallbackInfoReturnable<Boolean> cir,
            @Local(index = 8) BlockPos sdfCenter,
            @Local(index = 14) float side,
            @Local(index = 3) WorldGenLevel world) {
        if (BeyondTerrainState.isActive()) {
            ArchCarve.snapshot(world, sdfCenter, side);
        }
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
    private void isleweaver$carveApply(
            FeaturePlaceContext<?> ctx, CallbackInfoReturnable<Boolean> cir,
            @Local(index = 3) WorldGenLevel world) {
        if (BeyondTerrainState.isActive()) {
            ArchCarve.carve(world);
        }
    }
}

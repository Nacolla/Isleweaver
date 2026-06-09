package com.thebeyond.isleweaver.mixin.betterend;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.thebeyond.api.worldgen.BeyondTerrainState;
import com.thebeyond.isleweaver.compat.betterend.BetterEndYGate;
import com.thebeyond.isleweaver.compat.betterend.SpireOriginContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// pin the biome lookup to the origin pancake or it resolves against raw end_stone; the lambda also covers FloatingSpire
@Pseudo
@Mixin(targets = "org.betterx.betterend.world.features.terrain.SpireFeature", remap = false)
public abstract class SpireFeatureMixin {
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

    @ModifyExpressionValue(method = "place", at = @At(value = "CONSTANT", args = "intValue=10"))
    private int convergence$relativizeFloor(int original) {
        return BetterEndYGate.dimFloor(original);
    }

    @Inject(method = "place", at = @At("HEAD"))
    private void convergence$captureOrigin(
            FeaturePlaceContext<?> ctx, CallbackInfoReturnable<Boolean> cir) {
        if (BeyondTerrainState.isActive()) {
            SpireOriginContext.set(ctx.origin().getY());
        }
    }

    @Inject(method = "place", at = @At("RETURN"))
    private void convergence$clearOrigin(
            FeaturePlaceContext<?> ctx, CallbackInfoReturnable<Boolean> cir) {
        SpireOriginContext.clear();
    }

    @WrapOperation(
        method = "lambda$place$1",
        at = @At(
            value = "INVOKE",
            target = "Lorg/betterx/betterend/world/biome/EndBiome;findTopMaterial(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;",
            remap = false
        )
    )
    private static BlockState convergence$pinTopMaterialBiome(
            WorldGenLevel level, BlockPos pos, Operation<BlockState> op) {
        Integer y = SpireOriginContext.get();
        if (BeyondTerrainState.isActive() && y != null) {
            return op.call(level, new BlockPos(pos.getX(), y, pos.getZ()));
        }
        return op.call(level, pos);
    }

    @WrapOperation(
        method = "lambda$place$1",
        at = @At(
            value = "INVOKE",
            target = "Lorg/betterx/betterend/world/biome/EndBiome;findUnderMaterial(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;",
            remap = false
        )
    )
    private static BlockState convergence$pinUnderMaterialBiome(
            WorldGenLevel level, BlockPos pos, Operation<BlockState> op) {
        Integer y = SpireOriginContext.get();
        if (BeyondTerrainState.isActive() && y != null) {
            return op.call(level, new BlockPos(pos.getX(), y, pos.getZ()));
        }
        return op.call(level, pos);
    }
}

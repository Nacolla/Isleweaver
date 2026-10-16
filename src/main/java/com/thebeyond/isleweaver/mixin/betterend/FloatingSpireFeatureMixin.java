package com.thebeyond.isleweaver.mixin.betterend;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.thebeyond.api.worldgen.BeyondTerrainState;
import com.thebeyond.isleweaver.compat.betterend.SpireOriginContext;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// randRange(64,192) underflows under negative Y, rebased to originY+[0,128] so the spire floats over its pancake
@Pseudo
@Mixin(targets = "org.betterx.betterend.world.features.terrain.FloatingSpireFeature", remap = false)
public abstract class FloatingSpireFeatureMixin {
    @ModifyExpressionValue(
        method = "place",
        at = @At(
            value = "INVOKE",
            target = "getYOnSurface(Lnet/minecraft/world/level/WorldGenLevel;II)I",
            remap = false
        )
    )
    private int isleweaver$preserveOriginY(
            int original,
            @Local(argsOnly = true) FeaturePlaceContext<?> ctx) {
        return BeyondTerrainState.isActive() ? ctx.origin().getY() : original;
    }

    @WrapOperation(
        method = "place",
        at = @At(value = "INVOKE", target = "Lorg/betterx/bclib/util/MHelper;randRange(IILnet/minecraft/util/RandomSource;)I")
    )
    private int isleweaver$rebaseFloatBand(
            int min, int max, RandomSource random, Operation<Integer> op,
            @Local(argsOnly = true) FeaturePlaceContext<?> ctx) {
        if (BeyondTerrainState.isActive() && min == 64 && max == 192) {
            return ctx.origin().getY() + (op.call(min, max, random) - 64);
        }
        return op.call(min, max, random);
    }

    @Inject(method = "place", at = @At("HEAD"))
    private void isleweaver$captureOrigin(
            FeaturePlaceContext<?> ctx, CallbackInfoReturnable<Boolean> cir) {
        if (BeyondTerrainState.isActive()) {
            SpireOriginContext.set(ctx.origin().getY());
        }
    }

    @Inject(method = "place", at = @At("RETURN"))
    private void isleweaver$clearOrigin(
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
    private static BlockState isleweaver$pinTopMaterialBiome(
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
    private static BlockState isleweaver$pinUnderMaterialBiome(
            WorldGenLevel level, BlockPos pos, Operation<BlockState> op) {
        Integer y = SpireOriginContext.get();
        if (BeyondTerrainState.isActive() && y != null) {
            return op.call(level, new BlockPos(pos.getX(), y, pos.getZ()));
        }
        return op.call(level, pos);
    }
}

package com.thebeyond.isleweaver.mixin.betterend;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.thebeyond.api.worldgen.BeyondTerrainState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

// getCenterGround re-resolves via WORLD_SURFACE_WG, which snaps to the topmost pancake, keep the origin Y instead
@Pseudo
@Mixin(targets = "org.betterx.betterend.world.features.ScatterFeature", remap = false)
public abstract class ScatterFeatureMixin {
    @ModifyExpressionValue(
        method = "place",
        at = @At(
            value = "INVOKE",
            target = "Lorg/betterx/betterend/world/features/ScatterFeature;getCenterGround(Lorg/betterx/betterend/world/features/ScatterFeatureConfig;Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;",
            remap = false
        )
    )
    private BlockPos isleweaver$preserveCenterY(
            BlockPos original,
            @Local(argsOnly = true) FeaturePlaceContext<?> ctx) {
        return BeyondTerrainState.isActive() ? ctx.origin() : original;
    }

    @WrapOperation(
        method = "canSpawn",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;getY()I"
        )
    )
    private int isleweaver$bypassYGate(BlockPos pos, Operation<Integer> op) {
        return BeyondTerrainState.isActive() ? Integer.MAX_VALUE : op.call(pos);
    }
}

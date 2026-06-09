package com.thebeyond.isleweaver.mixin.betterend;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.thebeyond.api.worldgen.BeyondTerrainState;
import com.thebeyond.isleweaver.compat.betterend.BetterEndYGate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

// pin upRay to the origin Y — otherwise a negative-Y pancake gives it a negative distance and the surfaceY >= upRay guard aborts placement
@Pseudo
@Mixin(targets = "org.betterx.betterend.world.features.WallScatterFeature", remap = false)
public abstract class WallScatterFeatureMixin {
    @WrapOperation(
        method = "place",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/WorldGenLevel;getHeight(Lnet/minecraft/world/level/levelgen/Heightmap$Types;II)I"
        )
    )
    private int convergence$preserveY(
            WorldGenLevel world, Heightmap.Types type, int x, int z,
            Operation<Integer> op,
            @Local(ordinal = 0) BlockPos center) {
        if (BeyondTerrainState.isActive()) {
            return center.getY();
        }
        return op.call(world, type, x, z);
    }

    @WrapOperation(
        method = "place",
        at = @At(
            value = "INVOKE",
            target = "Lorg/betterx/bclib/util/BlocksHelper;upRay(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;I)I",
            remap = false
        )
    )
    private int convergence$pinUpRay(
            LevelAccessor level, BlockPos from, int distance, Operation<Integer> op,
            @Local(ordinal = 0) BlockPos center) {
        if (BeyondTerrainState.isActive()) {
            // returning surfaceY here makes the guard pass and randRange(upRay, surfaceY) collapse to the pancake Y
            return center.getY();
        }
        return op.call(level, from, distance);
    }

    @ModifyExpressionValue(method = "place", at = @At(value = "CONSTANT", args = "intValue=10"))
    private int convergence$relativizeFloor(int original) {
        return BetterEndYGate.dimFloor(original);
    }
}

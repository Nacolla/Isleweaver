package com.thebeyond.isleweaver.mixin.betterend;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.thebeyond.api.worldgen.BeyondTerrainState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

// origin Y instead of a heightmap, with upRay pinned just below it so the upRay guard never aborts on negative-Y pancakes
@Pseudo
@Mixin(targets = "org.betterx.betterend.world.features.InvertedScatterFeature", remap = false)
public abstract class InvertedScatterFeatureMixin {
    @WrapOperation(
        method = "place",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/WorldGenLevel;getHeight(Lnet/minecraft/world/level/levelgen/Heightmap$Types;II)I"
        )
    )
    private int isleweaver$preserveY(
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
            remap = false,
            ordinal = 0
        )
    )
    private int isleweaver$pinGuardUpRay(
            LevelAccessor level, BlockPos from, int distance, Operation<Integer> op,
            @Local(ordinal = 0) BlockPos center) {
        if (BeyondTerrainState.isActive()) {
            return center.getY() - 1;
        }
        return op.call(level, from, distance);
    }
}

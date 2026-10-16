package com.thebeyond.isleweaver.mixin.betterend;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thebeyond.isleweaver.compat.betterend.BetterEndYGate;
import com.thebeyond.isleweaver.compat.betterend.MushroomAnchor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// this static path only runs for glowshroom (giant_ice_star overrides it), so we anchor placement to the MushroomAnchor spot
@Pseudo
@Mixin(targets = "org.betterx.betterend.world.structures.features.SDFStructureFeature", remap = false)
public abstract class SDFStructureFeatureMixin {
    @ModifyExpressionValue(method = "generatePieces", at = @At(value = "CONSTANT", args = "intValue=5"))
    private static int isleweaver$relativizeYFloor(int original) {
        return BetterEndYGate.dimRelative(original);
    }

    @WrapOperation(
        method = "generatePieces",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/ChunkGenerator;getBaseHeight(IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;)I")
    )
    private static int isleweaver$readSpotYForPlacement(
            ChunkGenerator gen, int x, int z, Heightmap.Types type,
            LevelHeightAccessor level, RandomState random, Operation<Integer> op) {
        int original = op.call(gen, x, z, type, level, random);
        return MushroomAnchor.alignedY(x, z, original);
    }

    @Redirect(
        method = "generatePieces",
        at = @At(value = "NEW", target = "(III)Lnet/minecraft/core/BlockPos;")
    )
    private static BlockPos isleweaver$alignStartPos(int x, int y, int z) {
        return MushroomAnchor.alignedBlockPos(x, y, z);
    }
}

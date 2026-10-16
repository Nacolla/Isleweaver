package com.thebeyond.isleweaver.mixin.betterend;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thebeyond.isleweaver.compat.betterend.BetterEndYGate;
import com.thebeyond.api.compat.PancakeScan;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// ordinal=0 deliberately hits only the y > 50 floor, the randRange(50,100) bound must stay as is
@Pseudo
@Mixin(targets = "org.betterx.betterend.world.structures.features.PaintedMountainStructure", remap = false)
public abstract class PaintedMountainStructureMixin {
    @ModifyExpressionValue(method = "generatePieces", at = @At(value = "CONSTANT", args = "intValue=50", ordinal = 0))
    private int isleweaver$relativizeYFloor(int original) {
        return BetterEndYGate.dimRelative(original);
    }

    @WrapOperation(
        method = "generatePieces",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/ChunkGenerator;getBaseHeight(IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;)I")
    )
    private int isleweaver$readSpotYForPlacement(
            ChunkGenerator gen, int x, int z, Heightmap.Types type,
            LevelHeightAccessor level, RandomState random, Operation<Integer> op) {
        int original = op.call(gen, x, z, type, level, random);
        int aligned = PancakeScan.alignedY(x, z, original);
        return aligned;
    }

    @Redirect(
        method = "generatePieces",
        at = @At(value = "NEW", target = "(III)Lnet/minecraft/core/BlockPos;")
    )
    private BlockPos isleweaver$alignPieceCenter(int x, int y, int z) {
        return PancakeScan.alignedBlockPos(x, y, z);
    }
}

package com.thebeyond.isleweaver.mixin.betterend;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thebeyond.api.worldgen.BeyondTerrainState;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

// remaps the randRange(32,128) Y picker onto 12.5% to 75% of the dimension height so the star spans Beyond's vertical range
@Pseudo
@Mixin(targets = "org.betterx.betterend.world.structures.features.GiantIceStarStructure", remap = false)
public abstract class GiantIceStarStructureMixin {
    @WrapOperation(
        method = "generatePieces",
        at = @At(value = "INVOKE", target = "Lorg/betterx/bclib/util/MHelper;randRange(IILnet/minecraft/util/RandomSource;)I")
    )
    private int isleweaver$rebaseYRange(int min, int max, RandomSource random, Operation<Integer> op) {
        int original = op.call(min, max, random);
        if (!BeyondTerrainState.isActive()) return original;
        // match only the Y picker, the (4, 12) chunk-local X/Z picks share this call and must pass through
        if (min == 32 && max == 128) {
            int dimMinY = BeyondTerrainState.getDimMinY();
            int dimMaxY = BeyondTerrainState.getDimMaxY();
            int range = dimMaxY - dimMinY;
            int newMin = dimMinY + range / 8;
            int newMax = dimMinY + (range * 3) / 4;
            int result = newMin + (original - 32) * (newMax - newMin) / 96;
            return result;
        }
        return original;
    }
}

package com.thebeyond.isleweaver.mixin.betterend;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.thebeyond.api.worldgen.BeyondTerrainState;
import com.thebeyond.isleweaver.compat.betterend.BetterEndYGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

// floor(...) can go negative, and a signed % then yields a negative index that crashes on slices[index] — force it non-negative
@Pseudo
@Mixin(targets = "org.betterx.betterend.world.structures.piece.PaintedMountainPiece", remap = false)
public abstract class PaintedMountainPieceMixin {
    @ModifyExpressionValue(
        method = "postProcess",
        at = @At(value = "CONSTANT", args = "intValue=50")
    )
    private int convergence$lowerWalkdownFloor(int original) {
        return BetterEndYGate.dimFloor(original);
    }

    @ModifyExpressionValue(
        method = "postProcess",
        at = @At(value = "INVOKE", target = "Lorg/betterx/bclib/util/MHelper;floor(D)I")
    )
    private int convergence$nonNegativeFloorForSliceIndex(int original) {
        if (!BeyondTerrainState.isActive()) return original;
        int result = original < 0 ? -original : original;
        return result;
    }
}

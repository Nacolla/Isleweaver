package com.thebeyond.isleweaver.mixin.betterend;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.thebeyond.isleweaver.compat.betterend.BetterEndYGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

// rebases Crystal Mountain's Y thresholds onto the dim's min Y, inert while FeatureBaseStructureMixin stubs the mountain
@Pseudo
@Mixin(targets = "org.betterx.betterend.world.structures.piece.CrystalMountainPiece", remap = false)
public abstract class CrystalMountainPieceMixin {
    @ModifyExpressionValue(method = "postProcess", at = @At(value = "CONSTANT", args = "intValue=60"))
    private int isleweaver$bigCrystalYFloor(int original) {
        return BetterEndYGate.dimRelative(original);
    }

    @ModifyExpressionValue(method = "postProcess", at = @At(value = "CONSTANT", args = "intValue=20"))
    private int isleweaver$smallCrystalYFloor(int original) {
        return BetterEndYGate.dimRelative(original);
    }

    @ModifyExpressionValue(method = "placeMountain", at = @At(value = "CONSTANT", args = "intValue=10"))
    private int isleweaver$placeMountainMinY(int original) {
        return BetterEndYGate.dimRelative(original);
    }

    @ModifyExpressionValue(method = "placeMountain", at = @At(value = "CONSTANT", args = "intValue=56"))
    private int isleweaver$placeMountainSearchFloor(int original) {
        return BetterEndYGate.dimRelative(original);
    }
}

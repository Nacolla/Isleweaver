package com.thebeyond.isleweaver.mixin.betterend;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.thebeyond.isleweaver.compat.betterend.BetterEndYGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

// let the vanilla-End [57,70] band reads through (ordinals 0/1) and relativise the 56 circle floor
@Pseudo
@Mixin(targets = "org.betterx.betterend.world.features.terrain.SulphurHillFeature", remap = false)
public abstract class SulphurHillFeatureMixin {
    @ModifyExpressionValue(method = "place",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;getY()I", ordinal = 0))
    private int convergence$bandLo(int original) { return BetterEndYGate.passGate(original); }

    @ModifyExpressionValue(method = "place",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;getY()I", ordinal = 1))
    private int convergence$bandHi(int original) { return BetterEndYGate.passGate(original); }

    @ModifyConstant(method = "place", constant = @Constant(intValue = 56))
    private int convergence$pyFloor(int original) { return BetterEndYGate.dimFloor(original); }
}

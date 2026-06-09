package com.thebeyond.isleweaver.mixin.betterend;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.thebeyond.isleweaver.compat.betterend.BetterEndYGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

// same getY()<10 gate as EndLakeFeatureMixin, which never passes on Beyond's negative-Y surface
@Pseudo
@Mixin(targets = "org.betterx.betterend.world.features.terrain.DesertLakeFeature", remap = false)
public abstract class DesertLakeFeatureMixin {
    @ModifyExpressionValue(
        method = "place",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;getY()I", ordinal = 0)
    )
    private int convergence$lakeGate(int original) {
        return BetterEndYGate.passGate(original);
    }
}

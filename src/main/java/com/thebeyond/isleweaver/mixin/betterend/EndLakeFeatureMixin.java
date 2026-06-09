package com.thebeyond.isleweaver.mixin.betterend;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.thebeyond.isleweaver.compat.betterend.BetterEndYGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

// place rejects when surface getY()<10, which is always true under Beyond's negative-Y surface — force the gate to pass.
// safe because the lake re-reads the real surface afterward anyway
@Pseudo
@Mixin(targets = "org.betterx.betterend.world.features.terrain.EndLakeFeature", remap = false)
public abstract class EndLakeFeatureMixin {
    @ModifyExpressionValue(
        method = "place",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;getY()I", ordinal = 0)
    )
    private int convergence$lakeGate(int original) {
        return BetterEndYGate.passGate(original);
    }
}

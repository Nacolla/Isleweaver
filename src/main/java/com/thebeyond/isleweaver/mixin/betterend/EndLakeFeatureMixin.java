package com.thebeyond.isleweaver.mixin.betterend;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.thebeyond.isleweaver.compat.betterend.BetterEndYGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

// place rejects a surface getY()<10, always true on Beyond's negative-Y surface, so the gate passes (the lake re-reads it)
@Pseudo
@Mixin(targets = "org.betterx.betterend.world.features.terrain.EndLakeFeature", remap = false)
public abstract class EndLakeFeatureMixin {
    @ModifyExpressionValue(
        method = "place",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;getY()I", ordinal = 0)
    )
    private int isleweaver$lakeGate(int original) {
        return BetterEndYGate.passGate(original);
    }
}

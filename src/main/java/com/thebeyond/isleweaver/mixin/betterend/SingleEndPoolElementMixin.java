package com.thebeyond.isleweaver.mixin.betterend;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.thebeyond.isleweaver.compat.betterend.BetterEndYGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "org.betterx.wover.structure.impl.pools.SingleEndPoolElement", remap = false)
public abstract class SingleEndPoolElementMixin {
    @ModifyExpressionValue(method = "place", at = @At(value = "CONSTANT", args = "intValue=5"))
    private int convergence$relativizeYFloor(int original) {
        return BetterEndYGate.dimRelative(original);
    }
}

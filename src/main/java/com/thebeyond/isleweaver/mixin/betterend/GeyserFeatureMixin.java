package com.thebeyond.isleweaver.mixin.betterend;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.thebeyond.isleweaver.compat.betterend.BetterEndYGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

// only the two early-reject Y reads (getY()<10, depth<25) get rebased into the mid-band; the actual placement reads stay untouched
@Pseudo
@Mixin(targets = "org.betterx.betterend.world.features.terrain.GeyserFeature", remap = false)
public abstract class GeyserFeatureMixin {
    @ModifyExpressionValue(method = "place",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;getY()I", ordinal = 0))
    private int convergence$geyserYGate(int original) { return BetterEndYGate.passGate(original); }

    @ModifyExpressionValue(method = "place",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;getY()I", ordinal = 1))
    private int convergence$geyserDepth(int original) { return BetterEndYGate.passGate(original); }
}

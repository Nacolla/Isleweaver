package com.thebeyond.isleweaver.mixin.betterend;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.thebeyond.isleweaver.compat.betterend.BetterEndYGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

// skip the <57 reject (ordinal 0), the lake re-reads the real surface right after so bypassing it is safe
@Pseudo
@Mixin(targets = "org.betterx.betterend.world.features.terrain.SulphuricLakeFeature", remap = false)
public abstract class SulphuricLakeFeatureMixin {
    @ModifyExpressionValue(
        method = "place",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;getY()I", ordinal = 0)
    )
    private int isleweaver$sulphuricLakeGate(int original) {
        return BetterEndYGate.passGate(original);
    }
}

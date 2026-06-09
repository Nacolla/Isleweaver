package com.thebeyond.isleweaver.mixin.betterend;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.thebeyond.api.worldgen.BeyondTerrainState;
import com.thebeyond.isleweaver.compat.betterend.BetterEndYGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

// drop the hardcoded Y=0/Y=128 scan bounds so plants can find water in pancake lakes outside the vanilla End's surface band
@Pseudo
@Mixin(targets = "org.betterx.betterend.world.features.UnderwaterPlantScatter", remap = false)
public abstract class UnderwaterPlantScatterMixin {
    @ModifyExpressionValue(method = "getCenterGround", at = @At(value = "CONSTANT", args = "intValue=0"))
    private int convergence$scanStart(int original) {
        return BetterEndYGate.dimFloor(original);
    }

    @ModifyExpressionValue(method = "getGround", at = @At(value = "CONSTANT", args = "intValue=128"))
    private int convergence$scanCap(int original) {
        if (!BeyondTerrainState.isActive()) return original;
        int result = BeyondTerrainState.getDimMaxY();
        return result;
    }

    @ModifyExpressionValue(method = "getGroundPlant", at = @At(value = "CONSTANT", args = "intValue=128"))
    private int convergence$plantYCap(int original) {
        if (!BeyondTerrainState.isActive()) return original;
        int result = BeyondTerrainState.getDimMaxY();
        return result;
    }
}

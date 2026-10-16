package com.thebeyond.isleweaver.mixin.betterend;

import com.thebeyond.isleweaver.compat.betterend.BetterEndYGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Constant;

// fillSets starts its loop at y=1, so on Beyond's negative-Y dim nothing carves below 1, rebase the floor to the dim's min Y
@Pseudo
@Mixin(targets = "org.betterx.betterend.world.features.terrain.caves.CaveChunkPopulatorFeature", remap = false)
public abstract class CaveChunkPopulatorFeatureMixin {
    @ModifyConstant(method = "fillSets", constant = @Constant(intValue = 1))
    private int isleweaver$caveScanFloor(int original) {
        return BetterEndYGate.dimFloor(original);
    }
}

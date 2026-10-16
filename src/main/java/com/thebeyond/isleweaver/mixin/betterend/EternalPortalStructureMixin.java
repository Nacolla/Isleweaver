package com.thebeyond.isleweaver.mixin.betterend;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.thebeyond.isleweaver.compat.betterend.BetterEndYGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

// rebases the y<5 early skip to dimMinY+5 so it spares Beyond's negative-Y floor, the portal still spawns at the heightmap Y
@Pseudo
@Mixin(targets = "org.betterx.betterend.world.structures.features.EternalPortalStructure", remap = false)
public abstract class EternalPortalStructureMixin {
    @ModifyExpressionValue(method = "findGenerationPoint", at = @At(value = "CONSTANT", args = "intValue=5"))
    private int isleweaver$relativizeYFloor(int original) {
        return BetterEndYGate.dimRelative(original);
    }
}

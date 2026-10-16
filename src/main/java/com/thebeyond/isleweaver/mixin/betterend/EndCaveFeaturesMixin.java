package com.thebeyond.isleweaver.mixin.betterend;

import com.thebeyond.isleweaver.compat.betterend.BetterEndYGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

// findPos's >5 floor and <10 reject assume a positive-Y world, both rebase to dimMinY to hold under Beyond's negative-Y stack
@Pseudo
@Mixin(targets = "org.betterx.betterend.world.features.terrain.caves.EndCaveFeatures", remap = false)
public abstract class EndCaveFeaturesMixin {
    @ModifyConstant(method = "findPos", constant = @Constant(intValue = 5))
    private int isleweaver$caveScanFloor(int original) {
        return BetterEndYGate.dimFloor(original);
    }

    @ModifyConstant(method = "findPos", constant = @Constant(intValue = 10))
    private int isleweaver$caveReject(int original) {
        return BetterEndYGate.dimFloor(original);
    }
}

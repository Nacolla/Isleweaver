package com.thebeyond.isleweaver.mixin.betterend;

import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// when Stellarity is present, suppress BetterEnd's portal/pillars/central-island — but leave hasDragonFights alone or the fight breaks
@Pseudo
@Mixin(targets = "org.betterx.betterend.world.generator.GeneratorOptions", remap = false)
public abstract class BetterEndGeneratorOptionsMixin {

    @Inject(method = "replacePortal", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void convergence$disablePortalReplace(CallbackInfoReturnable<Boolean> cir) {
        if (ModList.get().isLoaded("stellarity")) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "replacePillars", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void convergence$disablePillarReplace(CallbackInfoReturnable<Boolean> cir) {
        if (ModList.get().isLoaded("stellarity")) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "hasCentralIsland", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void convergence$disableCentralIsland(CallbackInfoReturnable<Boolean> cir) {
        if (ModList.get().isLoaded("stellarity")) {
            cir.setReturnValue(false);
        }
    }

    // leave hasPillars() alone — Stellarity's ring routes through SpikeFeature.place, so returning false would leave dragons_den bare
}

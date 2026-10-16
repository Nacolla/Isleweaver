package com.thebeyond.isleweaver.mixin.betterend;

import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// with Stellarity the portal, pillar swap and central island go, hasDragonFights and hasPillars stay or the fight and ring break
@Pseudo
@Mixin(targets = "org.betterx.betterend.world.generator.GeneratorOptions", remap = false)
public abstract class BetterEndGeneratorOptionsMixin {

    @Inject(method = "replacePortal", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void isleweaver$disablePortalReplace(CallbackInfoReturnable<Boolean> cir) {
        if (ModList.get().isLoaded("stellarity")) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "replacePillars", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void isleweaver$disablePillarReplace(CallbackInfoReturnable<Boolean> cir) {
        if (ModList.get().isLoaded("stellarity")) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "hasCentralIsland", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void isleweaver$disableCentralIsland(CallbackInfoReturnable<Boolean> cir) {
        if (ModList.get().isLoaded("stellarity")) {
            cir.setReturnValue(false);
        }
    }
}

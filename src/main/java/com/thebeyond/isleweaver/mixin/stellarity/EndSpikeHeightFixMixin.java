package com.thebeyond.isleweaver.mixin.stellarity;

import com.thebeyond.api.worldgen.BeyondTerrainState;
import net.minecraft.world.level.levelgen.feature.SpikeFeature;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// priority 1500 beats BetterEnd's 1000, so callers outside the placement redirect get the raw, unwrapped height
@Mixin(value = SpikeFeature.EndSpike.class, priority = 1500)
public class EndSpikeHeightFixMixin {

    @Shadow
    @Final
    private int height;

    @Inject(method = "getHeight", at = @At("HEAD"), cancellable = true)
    private void convergence$returnRawHeight(CallbackInfoReturnable<Integer> cir) {
        if (!ModList.get().isLoaded("stellarity")) {
            return;
        }
        if (!BeyondTerrainState.isActive()) {
            return;
        }
        cir.setReturnValue(this.height);
    }
}

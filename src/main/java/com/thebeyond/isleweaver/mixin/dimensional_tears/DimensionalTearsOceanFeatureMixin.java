package com.thebeyond.isleweaver.mixin.dimensional_tears;

import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// kill DT's ocean slab — it floods over Beyond's auroracite islands, and Beyond owns low-Y fluid anyway
@Pseudo
@Mixin(targets = "com.ordana.dimensional_tears.worldgen_features.DimensionalTearsOceanFeature")
public abstract class DimensionalTearsOceanFeatureMixin {

    @Inject(method = "place", at = @At("HEAD"), cancellable = true, remap = false)
    private void convergence$cancelDTOceanLayer(
            FeaturePlaceContext<?> context,
            CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}

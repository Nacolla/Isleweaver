package com.thebeyond.isleweaver.mixin.core;

import com.thebeyond.api.worldgen.BeyondTerrainState;
import net.minecraft.world.level.levelgen.feature.EndPlatformFeature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EndPlatformFeature.class)
public class EndPlatformFeatureMixin {

    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void isleweaver$skipWorldgenPlatformOnBeyond(
            FeaturePlaceContext<NoneFeatureConfiguration> ctx, CallbackInfoReturnable<Boolean> cir) {
        if (BeyondTerrainState.isActive()) {
            cir.setReturnValue(false);
        }
    }
}

package com.thebeyond.isleweaver.mixin.stellarity;

import com.thebeyond.api.worldgen.BeyondTerrainState;
import net.minecraft.world.level.levelgen.feature.EndPodiumFeature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// with Beyond + BetterEnd + Stellarity loaded, skip the vanilla podium since Stellarity places it via mcfunction
@Mixin(EndPodiumFeature.class)
public class EndPodiumFeatureMixin {

    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void isleweaver$skipVanillaPodiumInStellarityCombo(
            FeaturePlaceContext<NoneFeatureConfiguration> ctx,
            CallbackInfoReturnable<Boolean> cir) {

        if (!BeyondTerrainState.isActive()) {
            return;
        }
        if (!ModList.get().isLoaded("stellarity") || !ModList.get().isLoaded("betterend")) {
            return;
        }

        // Stellarity owns the portal. Without this, BetterEnd would duplicate the bedrock/egg up in the sky
        cir.setReturnValue(false);
    }
}

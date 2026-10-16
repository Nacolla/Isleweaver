package com.thebeyond.isleweaver.mixin.wover;

import com.thebeyond.api.worldgen.BeyondEndBiomeSourceApi;
import com.thebeyond.isleweaver.Isleweaver;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// skip Wover's End surface-rule injection when Beyond owns the dim, so its SurfaceRuleMerger isn't overwritten
@Pseudo
@Mixin(targets = "org.betterx.wover.surface.impl.SurfaceRuleUtil", remap = false)
public class WoverSurfaceRuleUtilMixin {

    @Inject(
            method = "injectNoiseBasedSurfaceRules",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void isleweaver$skipEndWhenBeyondActive(
            ResourceKey<LevelStem> dimensionKey,
            Holder<NoiseGeneratorSettings> noiseSettings,
            BiomeSource loadedBiomeSource,
            CallbackInfo ci) {

        if (!LevelStem.END.equals(dimensionKey)) {
            return;
        }
        if (!(loadedBiomeSource instanceof BeyondEndBiomeSourceApi)) {
            return;
        }

        Isleweaver.LOGGER.info(
                "[Isleweaver] Beyond owns minecraft:the_end, vetoing Wover SurfaceRuleUtil.injectNoiseBasedSurfaceRules injection (prevents foreign biome surface rules from overwriting Beyond's noise settings).");
        ci.cancel();
    }
}

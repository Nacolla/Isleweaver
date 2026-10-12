package com.thebeyond.isleweaver.mixin.blueprint;

import com.thebeyond.isleweaver.compat.PhantasmSamplerSeed;
import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.teamabnormals.blueprint.core.util.BiomeUtil$ScopedDensityFunctionContext", remap = false)
public abstract class ScopedDensityFunctionContextMixin {

    @Shadow
    private Climate.Sampler optimizedSampler;

    @Inject(method = "reset(Lnet/minecraft/world/level/biome/Climate$Sampler;III)V", at = @At("RETURN"))
    private void isleweaver$carryPhantasmSeed(Climate.Sampler sampler, int x, int y, int z, CallbackInfo ci) {
        PhantasmSamplerSeed.copy(sampler, this.optimizedSampler);
    }
}

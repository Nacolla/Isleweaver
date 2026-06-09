package com.thebeyond.isleweaver.mixin.core;

import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.SurfaceRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(NoiseGeneratorSettings.class)
public interface NoiseGeneratorSettingsAccessor {
    @Accessor("surfaceRule")
    SurfaceRules.RuleSource convergence$getSurfaceRule();

    @Mutable
    @Accessor("surfaceRule")
    void convergence$setSurfaceRule(SurfaceRules.RuleSource rule);
}

package com.thebeyond.isleweaver.mixin.core;

import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.SurfaceRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(NoiseGeneratorSettings.class)
public interface NoiseGeneratorSettingsAccessor {
    @Accessor("surfaceRule")
    SurfaceRules.RuleSource isleweaver$getSurfaceRule();

    @Mutable
    @Accessor("surfaceRule")
    void isleweaver$setSurfaceRule(SurfaceRules.RuleSource rule);
}

package com.thebeyond.isleweaver.mixin.core;

import net.minecraft.core.HolderSet;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

// lets us rewrite a foreign biome's feature order to break the vanilla FeatureSorter cycle
@Mixin(BiomeGenerationSettings.class)
public interface BiomeGenerationSettingsAccessor {
    @Mutable
    @Accessor("features")
    void isleweaver$setFeatures(List<HolderSet<PlacedFeature>> features);
}

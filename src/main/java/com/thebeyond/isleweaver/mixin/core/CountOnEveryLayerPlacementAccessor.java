package com.thebeyond.isleweaver.mixin.core;

import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.levelgen.placement.CountOnEveryLayerPlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// read-only getter so the throttle can detect (never raise) the BetterEnd counts that pancake expansion inflates
@Mixin(CountOnEveryLayerPlacement.class)
public interface CountOnEveryLayerPlacementAccessor {
    @Accessor("count")
    IntProvider convergence$getCount();
}

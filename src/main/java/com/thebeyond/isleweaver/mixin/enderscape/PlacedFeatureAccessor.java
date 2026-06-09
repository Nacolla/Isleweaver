package com.thebeyond.isleweaver.mixin.enderscape;

import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

// lets us swap placement modifiers in place at server start without unfreezing the registry
@Mixin(PlacedFeature.class)
public interface PlacedFeatureAccessor {
    @Mutable
    @Accessor("placement")
    void convergence$setPlacement(List<PlacementModifier> placement);
}

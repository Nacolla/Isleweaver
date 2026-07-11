package com.thebeyond.isleweaver.mixin.enderscape;

import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.heightproviders.UniformHeight;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(UniformHeight.class)
public interface UniformHeightAccessor {
    @Accessor("minInclusive")
    VerticalAnchor isleweaver$getMinInclusive();

    @Accessor("maxInclusive")
    VerticalAnchor isleweaver$getMaxInclusive();
}

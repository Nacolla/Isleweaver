package com.thebeyond.isleweaver.mixin.betterend;

import com.thebeyond.isleweaver.compat.betterend.BetterEndYGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Slice;

// relativise the >5/<10 surface gate, the getHeight slice picks out the right 10 since the constant shows up more than once
@Pseudo
@Mixin(targets = "org.betterx.betterend.world.features.terrain.SulphuricCaveFeature", remap = false)
public abstract class SulphuricCaveFeatureMixin {
    @ModifyConstant(method = "place", constant = @Constant(intValue = 10),
        slice = @Slice(from = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/WorldGenLevel;getHeight(Lnet/minecraft/world/level/levelgen/Heightmap$Types;II)I")))
    private int isleweaver$caveReject(int original) { return BetterEndYGate.dimFloor(original); }

    @ModifyConstant(method = "place", constant = @Constant(intValue = 5),
        slice = @Slice(from = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/WorldGenLevel;getHeight(Lnet/minecraft/world/level/levelgen/Heightmap$Types;II)I")))
    private int isleweaver$caveScanFloor(int original) { return BetterEndYGate.dimFloor(original); }
}

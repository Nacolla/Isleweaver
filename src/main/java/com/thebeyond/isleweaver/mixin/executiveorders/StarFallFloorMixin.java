package com.thebeyond.isleweaver.mixin.executiveorders;

import com.thebeyond.isleweaver.compat.executiveorders.ExecutiveOrdersCompat;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Slice;

@Pseudo
@Mixin(targets = "net.atired.executiveorders.enemies.custom.StarFallEntity", remap = false)
public abstract class StarFallFloorMixin {

    // the second 0.1 in tick() is the moving-up test, the 0.0 right after it is the floor where the star dies
    @ModifyConstant(
            method = "tick",
            slice = @Slice(from = @At(value = "CONSTANT", args = "doubleValue=0.1", ordinal = 1)),
            constant = @Constant(doubleValue = 0.0, ordinal = 0),
            remap = false,
            require = 1)
    private double isleweaver$fallToTheEndBottom(double vanillaY) {
        return ExecutiveOrdersCompat.fromEndBottom(((Entity) (Object) this).level(), vanillaY);
    }
}

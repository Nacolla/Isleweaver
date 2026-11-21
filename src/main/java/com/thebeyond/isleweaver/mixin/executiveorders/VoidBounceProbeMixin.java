package com.thebeyond.isleweaver.mixin.executiveorders;

import com.thebeyond.isleweaver.compat.executiveorders.VoidBounceProbe;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// priority 900 lands before Executive's own move hook (1000), so the fall speed is read before the bounce flips it
@Mixin(value = Entity.class, priority = 900)
public abstract class VoidBounceProbeMixin implements VoidBounceProbe {

    @Shadow public abstract Level level();
    @Shadow public abstract double getY();
    @Shadow public abstract Vec3 getDeltaMovement();

    @Unique private double isleweaver$voidFallSpeed;

    @ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true)
    private Vec3 isleweaver$rememberVoidFall(Vec3 movement) {
        this.isleweaver$voidFallSpeed = this.getY() + movement.y < this.level().getMinBuildHeight() ? -this.getDeltaMovement().y : 0.0;
        return movement;
    }

    @Override
    public double isleweaver$voidFallSpeed() {
        return this.isleweaver$voidFallSpeed;
    }
}

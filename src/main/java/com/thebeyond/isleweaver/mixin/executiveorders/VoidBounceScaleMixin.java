package com.thebeyond.isleweaver.mixin.executiveorders;

import com.thebeyond.isleweaver.Isleweaver;
import com.thebeyond.isleweaver.compat.executiveorders.ExecutiveOrdersCompat;
import com.thebeyond.isleweaver.compat.executiveorders.VoidBounce;
import com.thebeyond.isleweaver.compat.executiveorders.VoidBounceProbe;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// priority 1100 lands after Executive's move hook (1000), once the bounce is already in the velocity
@Mixin(value = Entity.class, priority = 1100)
public abstract class VoidBounceScaleMixin {

    @Shadow public abstract Level level();
    @Shadow public abstract Vec3 getDeltaMovement();
    @Shadow public abstract void setDeltaMovement(double x, double y, double z);
    @Shadow public abstract double getGravity();

    @ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true)
    private Vec3 isleweaver$scaleVoidBounce(Vec3 movement) {
        double fallSpeed = ((VoidBounceProbe) this).isleweaver$voidFallSpeed();
        if (fallSpeed <= 0.0) return movement;
        Vec3 velocity = this.getDeltaMovement();
        if (Math.abs(velocity.y - fallSpeed * VoidBounce.RICOCHET) > 1.0E-6) return movement;
        Level level = this.level();
        double worldScale = level.getHeight() / (double) VoidBounce.VANILLA_END_HEIGHT;
        if (worldScale <= 1.0 || !ExecutiveOrdersCompat.isEnd(level)) return movement;
        double scaled = VoidBounce.scaledSpeed(fallSpeed, this.getGravity(), worldScale);
        this.setDeltaMovement(velocity.x, scaled, velocity.z);
        if (ExecutiveOrdersCompat.TRACE) {
            Isleweaver.LOGGER.info("[Isleweaver] Executive void bounce: {} fell at {}, bounce {} scaled to {} (world x{})",
                    ((Entity) (Object) this).getType(), fallSpeed, velocity.y, scaled, worldScale);
        }
        return movement;
    }
}

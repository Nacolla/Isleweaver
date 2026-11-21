package com.thebeyond.isleweaver.mixin.executiveorders;

import com.thebeyond.isleweaver.compat.executiveorders.ExecutiveOrdersCompat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Pseudo
@Mixin(targets = "net.atired.executiveorders.ExecutiveOrders", remap = false)
public abstract class VoidTransmutationFloorMixin {

    // matched by signature, since the compiler-given name of this lambda changes between Executive builds
    @ModifyConstant(
            method = "*(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/server/level/ServerLevel;)V",
            constant = @Constant(doubleValue = -20.0),
            remap = false,
            require = 1)
    private static double isleweaver$belowTheEndBottom(double vanillaY, Entity unloaded, ServerLevel level) {
        return ExecutiveOrdersCompat.fromEndBottom(level, vanillaY);
    }
}

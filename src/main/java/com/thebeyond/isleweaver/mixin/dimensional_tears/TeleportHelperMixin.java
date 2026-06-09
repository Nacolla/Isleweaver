package com.thebeyond.isleweaver.mixin.dimensional_tears;

import com.thebeyond.isleweaver.Isleweaver;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;
import java.util.Optional;

@Pseudo
@Mixin(targets = "com.ordana.dimensional_tears.util.TeleportHelper", remap = false)
public abstract class TeleportHelperMixin {

    @Unique private static Boolean convergence$bound;
    @Unique private static Method convergence$fishDataGet;
    @Unique private static Method convergence$profile;
    @Unique private static Method convergence$group;

    @Inject(method = "canTeleportTo", at = @At("HEAD"), cancellable = true, remap = false)
    private static void convergence$exemptTideEndFish(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (convergence$isTideEndFish(entity)) cir.setReturnValue(false);
    }

    @Unique
    private static boolean convergence$isTideEndFish(Entity entity) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (id == null || !"tide".equals(id.getNamespace()) || !convergence$bind()) return false;
        try {
            Object dataOpt = convergence$fishDataGet.invoke(null, entity);
            if (!(dataOpt instanceof Optional<?> opt) || opt.isEmpty()) return false;
            Object group = convergence$group.invoke(convergence$profile.invoke(opt.get()));
            return group instanceof Enum<?> e && "VOID".equals(e.name());
        } catch (Throwable t) {
            return false;
        }
    }

    @Unique
    private static synchronized boolean convergence$bind() {
        if (convergence$bound != null) return convergence$bound;
        try {
            Class<?> fishData = Class.forName("com.li64.tide.data.fishing.FishData");
            convergence$fishDataGet = fishData.getMethod("get", Entity.class);
            convergence$profile = fishData.getMethod("profile");
            convergence$group = Class.forName("com.li64.tide.data.fishing.ProfileData").getMethod("group");
            convergence$bound = true;
        } catch (ClassNotFoundException notPresent) {
            convergence$bound = false;
        } catch (Throwable t) {
            Isleweaver.LOGGER.debug("[Isleweaver] Tide FishData bind failed (non-fatal): {}", t.toString());
            convergence$bound = false;
        }
        return convergence$bound;
    }
}

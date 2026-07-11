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

    @Unique private static Boolean isleweaver$bound;
    @Unique private static Method isleweaver$fishDataGet;
    @Unique private static Method isleweaver$profile;
    @Unique private static Method isleweaver$group;

    @Inject(method = "canTeleportTo", at = @At("HEAD"), cancellable = true, remap = false)
    private static void isleweaver$exemptTideEndFish(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (isleweaver$isTideEndFish(entity)) cir.setReturnValue(false);
    }

    @Unique
    private static boolean isleweaver$isTideEndFish(Entity entity) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (id == null || !"tide".equals(id.getNamespace()) || !isleweaver$bind()) return false;
        try {
            Object dataOpt = isleweaver$fishDataGet.invoke(null, entity);
            if (!(dataOpt instanceof Optional<?> opt) || opt.isEmpty()) return false;
            Object group = isleweaver$group.invoke(isleweaver$profile.invoke(opt.get()));
            return group instanceof Enum<?> e && "VOID".equals(e.name());
        } catch (Throwable t) {
            return false;
        }
    }

    @Unique
    private static synchronized boolean isleweaver$bind() {
        if (isleweaver$bound != null) return isleweaver$bound;
        try {
            Class<?> fishData = Class.forName("com.li64.tide.data.fishing.FishData");
            isleweaver$fishDataGet = fishData.getMethod("get", Entity.class);
            isleweaver$profile = fishData.getMethod("profile");
            isleweaver$group = Class.forName("com.li64.tide.data.fishing.ProfileData").getMethod("group");
            isleweaver$bound = true;
        } catch (ClassNotFoundException notPresent) {
            isleweaver$bound = false;
        } catch (Throwable t) {
            Isleweaver.LOGGER.debug("[Isleweaver] Tide FishData bind failed (non-fatal): {}", t.toString());
            isleweaver$bound = false;
        }
        return isleweaver$bound;
    }
}

package com.thebeyond.isleweaver.mixin.phantasm;

import com.thebeyond.api.worldgen.BeyondTerrainState;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Phantasm sends End arrivals to a fixed point (1280, 65, 0), which is often void on Beyond's islands
@Pseudo
@Mixin(targets = "net.lyof.phantasm.setup.ModGameEvents")
public abstract class ModGameEventsMixin {

    @Inject(method = "onPlayerChangedDimension", at = @At("HEAD"), cancellable = true, remap = false)
    private static void isleweaver$skipOuterEndTeleportOnBeyond(PlayerEvent.PlayerChangedDimensionEvent event, CallbackInfo ci) {
        if (event.getTo() == Level.END && BeyondTerrainState.isActive()) {
            ci.cancel();
        }
    }
}

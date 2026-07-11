package com.thebeyond.isleweaver.mixin.blueprint;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thebeyond.api.worldgen.BeyondTerrainState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.dimension.LevelStem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.HashMap;

@Pseudo
@Mixin(targets = "com.teamabnormals.blueprint.common.world.modification.ModdedBiomeSlicesManager", remap = false)
public abstract class ModdedBiomeSlicesManagerMixin {

    @WrapOperation(
        method = "onServerAboutToStart",
        at = @At(value = "INVOKE", target = "Ljava/util/HashMap;get(Ljava/lang/Object;)Ljava/lang/Object;")
    )
    private static Object isleweaver$skipBeyondEndSlices(HashMap<?, ?> assignedSlices, Object location,
                                                          Operation<Object> original) {
        if (BeyondTerrainState.isActive()
                && location instanceof ResourceLocation loc
                && loc.equals(LevelStem.END.location())) {
            return null;
        }
        return original.call(assignedSlices, location);
    }
}

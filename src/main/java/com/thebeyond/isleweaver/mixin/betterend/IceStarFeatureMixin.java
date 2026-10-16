package com.thebeyond.isleweaver.mixin.betterend;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.thebeyond.api.worldgen.BeyondTerrainState;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

// this feature has no surface resolver, so override the hard-coded randRange(32,128) centre Y to sit above the pancake origin
@Pseudo
@Mixin(targets = "org.betterx.betterend.world.features.terrain.IceStarFeature", remap = false)
public abstract class IceStarFeatureMixin {
    @WrapOperation(
        method = "place",
        at = @At(value = "INVOKE", target = "Lorg/betterx/bclib/util/MHelper;randRange(IILnet/minecraft/util/RandomSource;)I")
    )
    private int isleweaver$rebaseStarY(
            int min, int max, RandomSource random, Operation<Integer> op,
            @Local(argsOnly = true) FeaturePlaceContext<?> ctx) {
        if (BeyondTerrainState.isActive() && min == 32 && max == 128) {
            return ctx.origin().getY() + 40;
        }
        return op.call(min, max, random);
    }
}

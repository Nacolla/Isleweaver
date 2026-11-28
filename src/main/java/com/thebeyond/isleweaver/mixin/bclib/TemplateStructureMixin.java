package com.thebeyond.isleweaver.mixin.bclib;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.thebeyond.api.worldgen.BeyondEndBiomeSourceApi;
import com.thebeyond.isleweaver.compat.betterend.BetterEndYGate;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

// drop the search floor to the dim's min Y so sub-sea-level pancakes get covered, MIN_VALUE would just spin forever
@Pseudo
@Mixin(targets = "org.betterx.bclib.api.v2.levelgen.structures.TemplateStructure", remap = false)
public abstract class TemplateStructureMixin {
    @ModifyExpressionValue(
        method = "findGenerationPoint",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/ChunkGenerator;getSeaLevel()I"
        )
    )
    private int isleweaver$lowerSeaLevelFloor(int original, @Local(argsOnly = true) Structure.GenerationContext context) {
        if (!(context.chunkGenerator().getBiomeSource() instanceof BeyondEndBiomeSourceApi)) return original;
        return BetterEndYGate.dimFloor(original);
    }
}

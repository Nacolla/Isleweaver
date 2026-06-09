package com.thebeyond.isleweaver.mixin.betterend;

import com.thebeyond.api.worldgen.BeyondTerrainState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// small variant hooks generatePieces rather than findGenerationPoint, which it inherits and so can't be targeted here
@Pseudo
@Mixin(targets = "org.betterx.betterend.world.structures.features.MegaLakeSmallStructure", remap = false)
public abstract class MegaLakeSmallStructureMixin {
    @Inject(method = "generatePieces", at = @At("HEAD"), cancellable = true, remap = true)
    private void convergence$bailGeneratePieces(
            StructurePiecesBuilder builder,
            Structure.GenerationContext context,
            CallbackInfo ci) {
        if (BeyondTerrainState.isActive()) {
            ci.cancel();
        }
    }
}

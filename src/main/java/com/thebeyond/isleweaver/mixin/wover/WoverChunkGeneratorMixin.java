package com.thebeyond.isleweaver.mixin.wover;

import com.thebeyond.api.worldgen.BeyondEndBiomeSourceApi;
import com.thebeyond.isleweaver.Isleweaver;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// stops Wover/BetterX from swapping out Beyond's End generator at dim-load, but only when Beyond actually owns the End
@Pseudo
@Mixin(targets = "org.betterx.wover.generator.impl.chunkgenerator.WoverChunkGenerator", remap = false)
public class WoverChunkGeneratorMixin {

    @Inject(
            method = "enforceGeneratorInWorldGenSettings",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void isleweaver$preserveBeyondEnd(
            RegistryAccess access,
            ResourceKey<LevelStem> dimensionKey,
            ResourceKey<DimensionType> dimensionTypeKey,
            ChunkGenerator loadedChunkGenerator,
            Registry<LevelStem> dimensionRegistry,
            CallbackInfoReturnable<Registry<LevelStem>> cir) {

        if (!LevelStem.END.equals(dimensionKey)) {
            return;
        }
        if (loadedChunkGenerator == null
                || !(loadedChunkGenerator.getBiomeSource() instanceof BeyondEndBiomeSourceApi)) {
            return;
        }

        Isleweaver.LOGGER.info(
                "[Isleweaver] Beyond owns minecraft:the_end, vetoing WoverChunkGenerator.enforceGeneratorInWorldGenSettings replacement.");
        cir.setReturnValue(dimensionRegistry);
    }
}

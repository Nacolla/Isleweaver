package com.thebeyond.isleweaver.mixin.quark;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// in End dims, redirect Zeta's offset=true biome sample from the world top down to the surface. The 3D source reports outer-void up top
@Pseudo
@Mixin(targets = "org.violetmoon.zeta.world.generator.Generator", remap = false)
public abstract class ZetaGeneratorGetBiomeMixin {

    @Inject(
        method = "getBiome(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Z)Lnet/minecraft/core/Holder;",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void isleweaver$sampleAtSurfaceInEnd(
            LevelAccessor world, BlockPos pos, boolean offset,
            CallbackInfoReturnable<Holder<Biome>> cir) {

        if (!offset) return; // the offset=false path is already correct

        // there's no DimensionType.isEnd(), so detect the End by ruling out Overworld (natural) and Nether (ceiling/ultraWarm)
        DimensionType dim = world.dimensionType();
        if (dim.natural() || dim.hasCeiling() || dim.ultraWarm()) return;

        if (!(world instanceof LevelReader lr)) return;

        int surfaceY = lr.getHeight(Heightmap.Types.WORLD_SURFACE_WG, pos.getX(), pos.getZ());
        BlockPos sample = new BlockPos(pos.getX(), surfaceY, pos.getZ());
        cir.setReturnValue(world.getBiomeManager().getBiome(sample));
    }
}

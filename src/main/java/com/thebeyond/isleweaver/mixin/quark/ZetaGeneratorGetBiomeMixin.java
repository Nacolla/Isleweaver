package com.thebeyond.isleweaver.mixin.quark;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// End only: Zeta's offset=true biome sample moves from the world top, which reads void, to the surface
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

        if (!offset) return;

        // there's no DimensionType.isEnd(), so detect the End by ruling out Overworld (natural) and Nether (ceiling/ultraWarm)
        DimensionType dim = world.dimensionType();
        if (dim.natural() || dim.hasCeiling() || dim.ultraWarm()) return;

        if (!(world instanceof WorldGenLevel level)) return;

        ServerChunkCache chunks = level.getLevel().getChunkSource();
        int surfaceY = chunks.getGenerator().getBaseHeight(pos.getX(), pos.getZ(), Heightmap.Types.WORLD_SURFACE_WG, world,
                chunks.randomState());
        cir.setReturnValue(world.getUncachedNoiseBiome(QuartPos.fromBlock(pos.getX()), QuartPos.fromBlock(surfaceY),
                QuartPos.fromBlock(pos.getZ())));
    }
}

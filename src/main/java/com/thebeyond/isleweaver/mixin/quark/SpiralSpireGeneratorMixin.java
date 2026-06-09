package com.thebeyond.isleweaver.mixin.quark;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Random;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// move Quark's spiral spire biome check from Y=256 down to the surface — the 3D biome source reports outer-void at the world top
@Pseudo
@Mixin(targets = "org.violetmoon.quark.content.world.gen.SpiralSpireGenerator", remap = false)
public abstract class SpiralSpireGeneratorMixin {

    @Shadow(remap = false)
    public abstract void makeSpike(WorldGenRegion world, ChunkGenerator chunk, Random rand, BlockPos pos);

    private static Field convergence$biomesField;
    private static Field convergence$radiusField;
    private static Method convergence$canSpawnMethod;
    private static boolean convergence$reflectInit;
    private static boolean convergence$reflectOk;

    private static boolean convergence$initReflect() {
        if (convergence$reflectInit) return convergence$reflectOk;
        convergence$reflectInit = true;
        try {
            Class<?> module = Class.forName("org.violetmoon.quark.content.world.module.SpiralSpiresModule");
            convergence$biomesField = module.getField("biomes");
            convergence$radiusField = module.getField("radius");
            convergence$canSpawnMethod = convergence$biomesField.getType().getMethod("canSpawn", Holder.class);
            convergence$reflectOk = true;
        } catch (Throwable t) {
            convergence$reflectOk = false;
        }
        return convergence$reflectOk;
    }

    // sample the biome only after walking down to END_STONE so the 3D source sees real surface; RNG order matches Quark's
    @Inject(
        method = "generateChunkPart(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/chunk/ChunkGenerator;Ljava/util/Random;Lnet/minecraft/core/BlockPos;Lnet/minecraft/server/level/WorldGenRegion;)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void convergence$sampleBiomeAtLandingY(
            BlockPos src, ChunkGenerator generator, Random random,
            BlockPos chunkCorner, WorldGenRegion world, CallbackInfo ci) {

        if (!convergence$initReflect()) return; // can't reach Quark's fields — let the original run

        int radius;
        Object biomesConfig;
        try {
            radius = convergence$radiusField.getInt(null);
            biomesConfig = convergence$biomesField.get(null);
        } catch (Throwable t) {
            return; // let Quark's original run
        }

        // mirror Quark's distance guard verbatim to consume the same RNG
        double dist = (chunkCorner.distSqr(src)) / ((16 * radius) * (16 * radius));
        if (dist > 0.5 && random.nextDouble() < (1.5F - dist)) {
            ci.cancel();
            return;
        }

        // two nextInt calls in the same order Quark uses
        BlockPos pos = chunkCorner.offset(random.nextInt(16), 256, random.nextInt(16));

        while (world.getBlockState(pos).getBlock() != Blocks.END_STONE) {
            pos = pos.below();
            if (pos.getY() < 10) {
                ci.cancel();
                return;
            }
        }

        Holder<Biome> biome = world.getBiomeManager().getBiome(pos);
        boolean ok;
        try {
            ok = (Boolean) convergence$canSpawnMethod.invoke(biomesConfig, biome);
        } catch (Throwable t) {
            ok = false;
        }
        if (!ok) {
            ci.cancel();
            return;
        }

        makeSpike(world, generator, random, pos);
        ci.cancel(); // we fully handled the method
    }
}

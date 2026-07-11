package com.thebeyond.isleweaver.mixin.core;

import com.thebeyond.api.worldgen.BeyondTerrainState;
import com.thebeyond.isleweaver.compat.EndBiomeInjector;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.TheEndBiomeSource;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// priority 500 so our erosion-range injection lands before Phantasm (900) and UnusualEnd (1000)
@Mixin(value = TheEndBiomeSource.class, priority = 500)
public class TheEndBiomeSourceMixin {

    @Inject(method = "getNoiseBiome", at = @At("RETURN"), cancellable = true)
    private void isleweaver$injectBiomes(int x, int y, int z, Climate.Sampler sampler,
                                          CallbackInfoReturnable<Holder<Biome>> cir) {
        // when Beyond drives terrain itself it handles its own biomes, so stay out of the way
        if (BeyondTerrainState.isActive()) return;

        EndBiomeInjector.VanillaEndHolders holders = EndBiomeInjector.vanillaEndHolders;
        if (holders == null) return;

        Holder<Biome> original = cir.getReturnValue();

        // leave the central island alone
        int blockX = QuartPos.toBlock(x);
        int blockZ = QuartPos.toBlock(z);
        int sectionX = SectionPos.blockToSectionCoord(blockX);
        int sectionZ = SectionPos.blockToSectionCoord(blockZ);
        if ((long) sectionX * (long) sectionX + (long) sectionZ * (long) sectionZ <= 4096L) return;

        // sample erosion at the exact point vanilla samples, so our ranges line up with its biomes
        int j1 = (sectionX * 2 + 1) * 8;
        int k1 = (sectionZ * 2 + 1) * 8;
        double erosion = sampler.erosion().compute(
                new DensityFunction.SinglePointContext(j1, 0, k1));

        if (original.is(Biomes.END_HIGHLANDS)) {
            if (erosion >= 0.35 && erosion < 0.5 && holders.attractaExpanse != null) {
                cir.setReturnValue(holders.attractaExpanse);
            } else if (erosion >= 0.5 && holders.peerLands != null) {
                cir.setReturnValue(holders.peerLands);
            }
        } else if (original.is(Biomes.SMALL_END_ISLANDS)) {
            if (erosion < -0.35 && holders.trueVoid != null) {
                cir.setReturnValue(holders.trueVoid);
            }
        } else if (original.is(Biomes.END_BARRENS)) {
            if (erosion < -0.1 && holders.thePaths != null) {
                cir.setReturnValue(holders.thePaths);
            }
        }
    }
}

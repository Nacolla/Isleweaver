package com.thebeyond.isleweaver.mixin.betterend;

import com.thebeyond.api.worldgen.BeyondTerrainState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// reimplements canSpawn for Beyond's stacked End using Holder.is(key) instead of the broken Holder.equals, and drops the vanilla-End water gate
@Pseudo
@Mixin(targets = "org.betterx.betterend.entity.EndSlimeEntity", remap = false)
public abstract class EndSlimeSpawnFixMixin {
    @Unique
    private static final TagKey<net.minecraft.world.level.block.Block> convergence$END_STONES =
            TagKey.create(Registries.BLOCK,
                    ResourceLocation.fromNamespaceAndPath("wover", "surfaces/end/stones"));
    @Unique
    private static ResourceKey<Biome> convergence$biome(String path) {
        return ResourceKey.create(Registries.BIOME,
                ResourceLocation.fromNamespaceAndPath("betterend", path));
    }
    @Unique
    private static final ResourceKey<Biome> convergence$FOGGY = convergence$biome("foggy_mushroomland");
    @Unique
    private static final ResourceKey<Biome> convergence$CHORUS = convergence$biome("chorus_forest");
    @Unique
    private static final ResourceKey<Biome> convergence$MEGALAKE = convergence$biome("megalake");
    @Unique
    private static final ResourceKey<Biome> convergence$MEGALAKE_GROVE = convergence$biome("megalake_grove");
    @Unique
    private static final ResourceKey<Biome> convergence$AMBER = convergence$biome("amber_land");

    @Inject(method = "canSpawn", at = @At("HEAD"), cancellable = true, remap = false)
    private static void convergence$stackedEndSpawn(
            EntityType<?> type, LevelAccessor world, MobSpawnType reason, BlockPos pos,
            RandomSource random, CallbackInfoReturnable<Boolean> cir) {
        if (!BeyondTerrainState.isActive()) return;

        BlockState below = world.getBlockState(pos.below());
        boolean endGround = below.is(convergence$END_STONES) || below.is(Blocks.END_STONE);
        if (!endGround) {
            cir.setReturnValue(false);
            return;
        }
        Holder<Biome> biome = world.getBiome(pos);
        // BetterEnd's intended per-biome shortcuts — its own code never hits these because it compares with the wrong Holder.equals
        if (biome.is(convergence$CHORUS) || biome.is(convergence$MEGALAKE)) {
            cir.setReturnValue(true);
            return;
        }
        if (biome.is(convergence$MEGALAKE_GROVE)) {
            cir.setReturnValue(random.nextBoolean());
            return;
        }
        // no water gate here: water is a vanilla-End artifact that doesn't exist on Beyond's stacked islands
        if (biome.is(convergence$FOGGY) || biome.is(convergence$AMBER)) {
            cir.setReturnValue(random.nextInt(4) == 0);
            return;
        }
        cir.setReturnValue(false);
    }
}

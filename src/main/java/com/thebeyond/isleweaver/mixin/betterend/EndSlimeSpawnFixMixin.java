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

// reimplements canSpawn for Beyond's stacked End with Holder.is(key) instead of the broken Holder.equals, minus the water gate
@Pseudo
@Mixin(targets = "org.betterx.betterend.entity.EndSlimeEntity", remap = false)
public abstract class EndSlimeSpawnFixMixin {
    @Unique
    private static final TagKey<net.minecraft.world.level.block.Block> isleweaver$END_STONES =
            TagKey.create(Registries.BLOCK,
                    ResourceLocation.fromNamespaceAndPath("wover", "surfaces/end/stones"));
    @Unique
    private static ResourceKey<Biome> isleweaver$biome(String path) {
        return ResourceKey.create(Registries.BIOME,
                ResourceLocation.fromNamespaceAndPath("betterend", path));
    }
    @Unique
    private static final ResourceKey<Biome> isleweaver$FOGGY = isleweaver$biome("foggy_mushroomland");
    @Unique
    private static final ResourceKey<Biome> isleweaver$CHORUS = isleweaver$biome("chorus_forest");
    @Unique
    private static final ResourceKey<Biome> isleweaver$MEGALAKE = isleweaver$biome("megalake");
    @Unique
    private static final ResourceKey<Biome> isleweaver$MEGALAKE_GROVE = isleweaver$biome("megalake_grove");
    @Unique
    private static final ResourceKey<Biome> isleweaver$AMBER = isleweaver$biome("amber_land");

    @Inject(method = "canSpawn", at = @At("HEAD"), cancellable = true, remap = false)
    private static void isleweaver$stackedEndSpawn(
            EntityType<?> type, LevelAccessor world, MobSpawnType reason, BlockPos pos,
            RandomSource random, CallbackInfoReturnable<Boolean> cir) {
        if (!BeyondTerrainState.isActive()) return;

        BlockState below = world.getBlockState(pos.below());
        boolean endGround = below.is(isleweaver$END_STONES) || below.is(Blocks.END_STONE);
        if (!endGround) {
            cir.setReturnValue(false);
            return;
        }
        Holder<Biome> biome = world.getBiome(pos);
        if (biome.is(isleweaver$CHORUS) || biome.is(isleweaver$MEGALAKE)) {
            cir.setReturnValue(true);
            return;
        }
        if (biome.is(isleweaver$MEGALAKE_GROVE)) {
            cir.setReturnValue(random.nextBoolean());
            return;
        }
        if (biome.is(isleweaver$FOGGY) || biome.is(isleweaver$AMBER)) {
            cir.setReturnValue(random.nextInt(4) == 0);
            return;
        }
        cir.setReturnValue(false);
    }
}

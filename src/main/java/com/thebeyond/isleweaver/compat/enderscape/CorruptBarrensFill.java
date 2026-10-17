package com.thebeyond.isleweaver.compat.enderscape;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.SurfaceRules;
import org.jetbrains.annotations.Nullable;

/** Enderscape 1.0.9 keeps corrupt barrens mirestone within surface depth, 2.x fills the whole biome and wins before this rule. */
public final class CorruptBarrensFill {
    private static final ResourceKey<Biome> CORRUPT_BARRENS =
            ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("enderscape", "corrupt_barrens"));

    private CorruptBarrensFill() {
    }

    public static @Nullable SurfaceRules.RuleSource rule(RegistryAccess access) {
        if (!access.registryOrThrow(Registries.BIOME).containsKey(CORRUPT_BARRENS)) {
            return null;
        }
        Block mirestone = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("enderscape", "mirestone"));
        if (mirestone == Blocks.AIR) {
            return null;
        }
        return SurfaceRules.ifTrue(SurfaceRules.isBiome(CORRUPT_BARRENS), SurfaceRules.state(mirestone.defaultBlockState()));
    }
}

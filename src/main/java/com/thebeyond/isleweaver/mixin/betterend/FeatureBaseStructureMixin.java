package com.thebeyond.isleweaver.mixin.betterend;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thebeyond.api.worldgen.BeyondTerrainState;
import com.thebeyond.api.compat.PancakeScan;
import com.thebeyond.isleweaver.compat.betterend.BetterEndYGate;
import com.thebeyond.isleweaver.compat.betterend.MushroomAnchor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

// stubs the mountain to zero pieces, and prefetches a per-chunk spot so the biome check and the actual placement land in the same Voronoi cell
@Pseudo
@Mixin(targets = "org.betterx.betterend.world.structures.features.FeatureBaseStructure", remap = false)
public abstract class FeatureBaseStructureMixin {
    private static final ResourceLocation MOUNTAIN_TYPE_ID =
            ResourceLocation.fromNamespaceAndPath("betterend", "mountain");
    private static final ResourceLocation GLOWSHROOM_TYPE_ID =
            ResourceLocation.fromNamespaceAndPath("betterend", "giant_mossy_glowshroom");
    private static final ResourceLocation PAINTED_MOUNTAIN_TYPE_ID =
            ResourceLocation.fromNamespaceAndPath("betterend", "painted_mountain");

    @ModifyExpressionValue(method = "findGenerationPoint", at = @At(value = "CONSTANT", args = "intValue=10"))
    private int convergence$relativizeYFloor(int original) {
        return BetterEndYGate.dimRelative(original);
    }

    @Inject(method = "findGenerationPoint", at = @At("HEAD"), cancellable = true)
    private void convergence$prefetchSpotAndRedirectMountain(
            Structure.GenerationContext context,
            CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {
        if (!BeyondTerrainState.isActive() || cir.isCancelled()) return;

        Structure self = (Structure) (Object) this;
        ResourceLocation typeId = BuiltInRegistries.STRUCTURE_TYPE.getKey(self.type());
        if (typeId == null) return;
        boolean isMountain = MOUNTAIN_TYPE_ID.equals(typeId);
        boolean needsSpot = isMountain
                || GLOWSHROOM_TYPE_ID.equals(typeId)
                || PAINTED_MOUNTAIN_TYPE_ID.equals(typeId);
        if (!needsSpot) return;

        ChunkPos chunkPos = context.chunkPos();

        // bail only if the chunk has no foggy solid surface — otherwise a mid-air 3D-biome match would float a mushroom
        if (GLOWSHROOM_TYPE_ID.equals(typeId)) {
            int[] mush = MushroomAnchor.computeFoggySpot(
                    context.chunkGenerator(), chunkPos.x, chunkPos.z,
                    context.heightAccessor(), context.randomState());
            if (mush == null) {
                cir.setReturnValue(Optional.empty());
            }
            return; // spot is cached; the aligned wraps below anchor both placement and the biome check
        }

        int[] spot = PancakeScan.pickEndBiomeSpotInChunk(
                context.chunkGenerator(), chunkPos.x, chunkPos.z,
                context.heightAccessor(), context.randomState(), self.biomes());

        // betterend:mountain gets an empty stub — its visuals come from aurora_crystal_cluster instead
        if (isMountain) {
            if (spot == null) {
                cir.setReturnValue(Optional.empty());
            } else {
                BlockPos pos = new BlockPos(spot[0], spot[1], spot[2]);
                cir.setReturnValue(Optional.of(new Structure.GenerationStub(pos, builder -> {})));
            }
        }
    }

    @WrapOperation(
        method = "getGenerationHeight",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/ChunkGenerator;getFirstOccupiedHeight(IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;)I")
    )
    private static int convergence$readSpotYForSpawnCheck(
            ChunkGenerator gen, int x, int z, Heightmap.Types type,
            LevelHeightAccessor level, RandomState random, Operation<Integer> op) {
        int original = op.call(gen, x, z, type, level, random);
        if (MushroomAnchor.has(x >> 4, z >> 4)) return MushroomAnchor.alignedY(x, z, original);
        return PancakeScan.alignedY(x, z, original);
    }

    @Redirect(
        method = "getGenerationHeight",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos$MutableBlockPos;set(III)Lnet/minecraft/core/BlockPos$MutableBlockPos;")
    )
    private static BlockPos.MutableBlockPos convergence$pinResultToSpot(
            BlockPos.MutableBlockPos self, int x, int y, int z) {
        if (MushroomAnchor.has(x >> 4, z >> 4)) return MushroomAnchor.alignedSet(self, x, y, z);
        return PancakeScan.alignedSet(self, x, y, z);
    }
}

package com.thebeyond.isleweaver.mixin.mes;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thebeyond.api.compat.PancakeScan;
import com.thebeyond.isleweaver.IsleweaverConfig;
import com.thebeyond.isleweaver.compat.mes.MesTuning;
import com.thebeyond.api.worldgen.BeyondForeignStructureProfiles;
import com.thebeyond.api.worldgen.BeyondTerrainState;
import com.thebeyond.api.worldgen.StructureIntegrationProfile;
import com.thebeyond.common.worldgen.BeyondEndChunkGenerator;
import com.thebeyond.isleweaver.Isleweaver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

// Hosts MES on Beyond's stacked islands: its GenericJigsawStructure extends Structure (not JigsawStructure), so Beyond's auto-host mixin misses it.
// Carve by terrain_adaptation: NONE (hovering ships) -> FLOATING; beard_thin (ground ruins) -> organic carve, re-anchored onto a lower pancake by the placement wrap below.
@Pseudo
@Mixin(targets = "com.finndog.moogs_structures.world.structures.GenericJigsawStructure", remap = false)
public abstract class GenericJigsawStructureMixin {

    // Armed only while a hosted MES ruin runs findGenerationPoint, so the flatness-gate skip fires for exactly that call.
    // HEAD resets it per entry so it never leaks across placements; extraSpawningChecks runs once on the same thread (no recursion).
    private static final ThreadLocal<Boolean> the_beyond$hosted = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final Set<String> the_beyond$logged = ConcurrentHashMap.newKeySet();
    private static final Set<String> the_beyond$loggedDistributed = ConcurrentHashMap.newKeySet();
    private static final Set<String> the_beyond$loggedFallback = ConcurrentHashMap.newKeySet();
    // Drawn pancake top under an embedded ship's footprint, stashed by the HEAD pancake pick for the sink.
    private static final ThreadLocal<Integer> the_beyond$islandSurfaceY = ThreadLocal.withInitial(() -> Integer.MIN_VALUE);
    // Footprint-coverage probe for embedded ships. Ship-sized radius (a megaship is ~78x120), coarse stride.
    private static final int the_beyond$MEGASHIP_SUPPORT_RADIUS = 56;
    private static final int the_beyond$MEGASHIP_SUPPORT_STRIDE = 8;

    @Inject(method = "findGenerationPoint", at = @At("HEAD"), cancellable = true, remap = false)
    private void the_beyond$hostMesHead(Structure.GenerationContext context,
            CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {
        the_beyond$hosted.set(Boolean.FALSE);
        try {
            if (!BeyondTerrainState.isActive()) return;
            Structure self = (Structure) (Object) this;
            // Dimension gate via biome: MES targets End biomes, so a match means we are in Beyond's End generator.
            boolean endMatch = false;
            for (Holder<Biome> b : self.biomes()) {
                if (context.chunkGenerator().getBiomeSource().possibleBiomes().contains(b)) { endMatch = true; break; }
            }
            if (!endMatch) return;
            ResourceLocation key = context.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(self);
            if (key == null) return;
            the_beyond$hosted.set(Boolean.TRUE);
            // Embedded ships: draw one pancake whose ship-sized footprint covers enough to hold the hull (any layer); no valid pancake means don't spawn (would float), and the drawn top feeds the sink.
            if (BeyondForeignStructureProfiles.isEmbedded(key)
                    && context.chunkGenerator() instanceof BeyondEndChunkGenerator beg) {
                int pancakeTop = PancakeScan.pickColumnPancakeY(beg,
                        context.chunkPos().getMinBlockX(), context.chunkPos().getMinBlockZ(),
                        context.heightAccessor(), context.randomState(), self.biomes(),
                        MesTuning.LAYER_HEADROOM, the_beyond$MEGASHIP_SUPPORT_RADIUS, the_beyond$MEGASHIP_SUPPORT_STRIDE,
                        IsleweaverConfig.MEGASHIP_MIN_ISLAND_SUPPORT.get() / 100.0);
                if (pancakeTop == Integer.MIN_VALUE) {
                    the_beyond$hosted.set(Boolean.FALSE);
                    cir.setReturnValue(Optional.empty());
                    if (the_beyond$loggedFallback.add("no-pancake@" + key + "@" + context.chunkPos().x + "," + context.chunkPos().z)
                            && the_beyond$loggedFallback.size() <= 40) {
                        Isleweaver.LOGGER.debug("[Isleweaver][MES] {} REJECTED: no pancake with enough footprint coverage (would float) chunk=[{},{}]",
                                key, context.chunkPos().x, context.chunkPos().z);
                    }
                    return;
                }
                the_beyond$islandSurfaceY.set(pancakeTop);
            }
            boolean none = self.terrainAdaptation() == TerrainAdjustment.NONE;
            if (none) {
                if (BeyondForeignStructureProfiles.get(key) == null) {
                    BeyondForeignStructureProfiles.register(key,
                            StructureIntegrationProfile.builder(StructureIntegrationProfile.Anchor.FLOATING).build());
                }
            } else {
                // beard_thin ruins (incl. crashed ships): distributed organic carve.
                BeyondForeignStructureProfiles.markAutoSeatedProjected(key);
            }
            if (the_beyond$logged.add(key.toString())) {
                Isleweaver.LOGGER.debug("[Isleweaver][MES] {} hosted: flatness-skip + {}", key,
                        none ? "FLOATING carve (hovering ship)"
                             : BeyondForeignStructureProfiles.isEmbedded(key) ? "EMBEDDED distributed carve-only (island-buried, no platform)"
                             : "distributed organic carve (ground ruin, beard-protected)");
            }
        } catch (Throwable t) {
            the_beyond$hosted.set(Boolean.FALSE);
        }
    }

    @Inject(method = "extraSpawningChecks", at = @At("HEAD"), cancellable = true, remap = false)
    private void the_beyond$skipFlatnessGate(Structure.GenerationContext context, BlockPos pos,
            CallbackInfoReturnable<Boolean> cir) {
        if (the_beyond$hosted.get()) cir.setReturnValue(Boolean.TRUE);
    }

    @Inject(method = "findGenerationPoint", at = @At("RETURN"), remap = false)
    private void the_beyond$hostMesReturn(Structure.GenerationContext context,
            CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {
        the_beyond$hosted.set(Boolean.FALSE);
    }

    // Wraps assembleJigsawStructure to seat at a biome-matched pancake Y with an empty heightmap so the callee seats verbatim (skips re-projection and the y_allowance clip); any miss falls through to vanilla topmost.
    @WrapOperation(
            method = "findGenerationPoint",
            at = @At(value = "INVOKE",
                    target = "Lcom/finndog/moogs_structures/world/structures/pieces/PieceLimitedJigsawManager;"
                            + "assembleJigsawStructure(Lnet/minecraft/world/level/levelgen/structure/Structure$GenerationContext;"
                            + "Lnet/minecraft/core/Holder;ILnet/minecraft/resources/ResourceLocation;Lnet/minecraft/core/BlockPos;Z"
                            + "Ljava/util/Optional;IILjava/util/Set;Ljava/util/Optional;Ljava/util/Optional;"
                            + "Lnet/minecraft/world/level/levelgen/structure/templatesystem/LiquidSettings;"
                            + "Ljava/util/function/BiConsumer;)Ljava/util/Optional;",
                    remap = false),
            remap = false)
    private Optional<Structure.GenerationStub> the_beyond$distributeMesBetweenPancakes(
            Structure.GenerationContext context, Holder<?> startPool, int size, ResourceLocation structureId, BlockPos startPos,
            boolean doBoundaryAdjustments, Optional<Heightmap.Types> heightmapType, int maxY, int minY,
            Set<ResourceLocation> poolsThatIgnoreBounds, Optional<Integer> maxDistanceFromCenter, Optional<?> buryingType,
            LiquidSettings liquidSettings, BiConsumer<?, ?> structureBoundsAdjuster,
            Operation<Optional<Structure.GenerationStub>> original) {
        // Fires once on the first wrapped call; its absence means the @WrapOperation didn't apply (descriptor mismatch) and every placement was silently vanilla-topmost.
        if (the_beyond$logged.add("wrap-active")) {
            Isleweaver.LOGGER.debug("[Isleweaver][MES] placement wrap active (assembleJigsawStructure intercepted).");
        }
        BlockPos placeAt = startPos;
        Optional<Heightmap.Types> heightmap = heightmapType;
        if (the_beyond$hosted.get()) {
            try {
                Structure self = (Structure) (Object) this;
                if (self.terrainAdaptation() != TerrainAdjustment.NONE
                        && context.chunkGenerator() instanceof BeyondEndChunkGenerator beg) {
                    // MES's centerAllPieces re-roots the start-piece center at the chunk-min corner, so gate support/biome there (not the chunk centre) to match where the ruin actually lands.
                    int cx = context.chunkPos().getMinBlockX(), cz = context.chunkPos().getMinBlockZ();
                    ResourceLocation key = the_beyond$structureKey(context, self);
                    if (key != null && BeyondForeignStructureProfiles.isEmbedded(key)) {
                        // Embedded (crashed ships): seat verbatim at (pancakeTop - sinkDepth) so the buriedAbove keep buries the sub-surface hull; pancakeTop was drawn by the HEAD pick.
                        int pancakeTop = the_beyond$islandSurfaceY.get();
                        if (pancakeTop != Integer.MIN_VALUE) {
                            int sunkY = Math.max(minY, Math.min(maxY, pancakeTop - IsleweaverConfig.MEGASHIP_SINK_DEPTH.get()));
                            placeAt = new BlockPos(startPos.getX(), sunkY, startPos.getZ());
                            heightmap = Optional.empty();
                            if (the_beyond$loggedDistributed.add("sink@" + key + "@" + context.chunkPos().x + "," + context.chunkPos().z)
                                    && the_beyond$loggedDistributed.size() <= 40) {
                                Isleweaver.LOGGER.debug("[Isleweaver][MES] {} EMBEDDED sunk -> Y={} (pancakeTop={} depth={}) chunk=[{},{}]",
                                        key, sunkY, pancakeTop, IsleweaverConfig.MEGASHIP_SINK_DEPTH.get(), context.chunkPos().x, context.chunkPos().z);
                            }
                        }
                    } else if ((double) cx * cx + (double) cz * cz >= 650.0 * 650.0) {
                        beg.computeNoisesIfNotPresent(context.randomState());
                        // Seat the ruin on an island its footprint overlaps. The base-beard bridges island columns but never void, so reject a void/split footprint.
                        int coverR = the_beyond$startFootprintRadius(context, startPool, startPos);
                        PancakeScan.GroundedSeat g = PancakeScan.pickGroundedPancake(beg, cx, cz, context.heightAccessor(), context.randomState(),
                                self.biomes(), MesTuning.LAYER_HEADROOM, coverR, MesTuning.GROUND_STRIDE,
                                MesTuning.MIN_COVER, MesTuning.SUPPORT_FILL, MesTuning.SUPPORT_OVER);
                        int picked = g.seatY();
                        // y_allowance guard: minY/maxY ARE this structure's min/max_y_allowed.
                        if (g.grounded() && picked >= minY && picked <= maxY) {
                            placeAt = new BlockPos(startPos.getX(), picked, startPos.getZ());
                            heightmap = Optional.empty();
                            BeyondForeignStructureProfiles.markLayerDistributed(key, context.chunkPos().toLong());
                            if (key != null && the_beyond$loggedDistributed.add(key + "@" + context.chunkPos().x + "," + context.chunkPos().z)
                                    && the_beyond$loggedDistributed.size() <= 40) {
                                Isleweaver.LOGGER.debug("[Isleweaver][MES] {} grounded -> Y={} r={} cover={}% topmostLayer={} layers={}valid/{}total chosenLayerTop={} chunk=[{},{}]",
                                        key, picked, coverR, g.coverPct(), g.topmostLayerY(), g.validLayers(), g.totalLayers(), g.chosenLayerTop(), context.chunkPos().x, context.chunkPos().z);
                            }
                        } else {
                            if (key != null && the_beyond$loggedFallback.add("reject@" + key + "@" + context.chunkPos().x + "," + context.chunkPos().z)
                                    && the_beyond$loggedFallback.size() <= 40) {
                                Isleweaver.LOGGER.debug("[Isleweaver][MES] {} REJECTED (grounded, {}): r={} topmostLayer={} layers=0valid/{}total chunk=[{},{}]",
                                        key, picked == Integer.MIN_VALUE ? "voidy/split" : "y-allowance(" + picked + " vs " + minY + ".." + maxY + ")",
                                        coverR, g.topmostLayerY(), g.totalLayers(), context.chunkPos().x, context.chunkPos().z);
                            }
                            the_beyond$hosted.set(Boolean.FALSE);
                            return Optional.empty();
                        }
                    } else {
                        the_beyond$logFallback(context, self, "central-island");
                    }
                }
            } catch (Throwable t) {
                placeAt = startPos;
                heightmap = heightmapType;
                if (the_beyond$logged.add("wrap-error")) {
                    Isleweaver.LOGGER.warn("[Isleweaver][MES] placement wrap error; falling back to topmost.", t);
                }
            }
        }
        return original.call(context, startPool, size, structureId, placeAt, doBoundaryAdjustments, heightmap,
                maxY, minY, poolsThatIgnoreBounds, maxDistanceFromCenter, buryingType, liquidSettings, structureBoundsAdjuster);
    }

    private static void the_beyond$logFallback(Structure.GenerationContext context, Structure self, String reason) {
        ResourceLocation key = the_beyond$structureKey(context, self);
        String id = key == null ? "?" : key.toString();
        if (the_beyond$loggedFallback.add(id + "@" + context.chunkPos().x + "," + context.chunkPos().z)
                && the_beyond$loggedFallback.size() <= 40) {
            Isleweaver.LOGGER.debug("[Isleweaver][MES] {} fallback -> topmost ({}) chunk=[{},{}]",
                    id, reason, context.chunkPos().x, context.chunkPos().z);
        }
    }

    private static ResourceLocation the_beyond$structureKey(Structure.GenerationContext context, Structure self) {
        try {
            return context.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(self);
        } catch (Throwable t) {
            return null;
        }
    }

    // footprint radius from the widest piece's lateral span / 2, so a sprawling ruin gets wide enough support that its base can't overhang the void; read-fail falls back to the fixed radius.
    private static int the_beyond$startFootprintRadius(Structure.GenerationContext context,
            net.minecraft.core.Holder<?> startPool, net.minecraft.core.BlockPos startPos) {
        int radius = MesTuning.SUPPORT_RADIUS;
        try {
            if (startPool.value() instanceof net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool pool) {
                net.minecraft.util.RandomSource r = net.minecraft.util.RandomSource.create(
                        (long) startPos.getX() * 341873128712L ^ (long) startPos.getZ() * 132897987541L);
                int maxLat = 0;
                for (net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement e : pool.getShuffledTemplates(r)) {
                    net.minecraft.world.level.levelgen.structure.BoundingBox bb =
                            e.getBoundingBox(context.structureTemplateManager(), startPos, net.minecraft.world.level.block.Rotation.NONE);
                    maxLat = Math.max(maxLat, Math.max(bb.getXSpan(), bb.getZSpan()));
                }
                if (maxLat > 0) radius = Math.max(MesTuning.SUPPORT_RADIUS, maxLat / 2);
            }
        } catch (Throwable ignored) { }
        return radius;
    }
}

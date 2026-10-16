package com.thebeyond.isleweaver.mixin.mes;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thebeyond.api.compat.PancakeScan;
import com.thebeyond.isleweaver.IsleweaverConfig;
import com.thebeyond.isleweaver.compat.mes.MesHostedStructure;
import com.thebeyond.isleweaver.compat.mes.MesTuning;
import com.thebeyond.isleweaver.compat.mes.MoogsEndStructuresProfiles;
import com.thebeyond.api.worldgen.BeyondForeignStructureProfiles;
import com.thebeyond.api.worldgen.BeyondTerrain;
import com.thebeyond.api.worldgen.BeyondTerrainState;
import com.thebeyond.common.worldgen.BeyondEndChunkGenerator;
import com.thebeyond.isleweaver.Isleweaver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

// Hosts MES on Beyond's layers: its GenericJigsawStructure is no JigsawStructure, so auto-host misses it
@Pseudo
@Mixin(targets = "com.finndog.moogs_structures.world.structures.GenericJigsawStructure", remap = false)
public abstract class GenericJigsawStructureMixin implements MesHostedStructure {

    // set only while a hosted MES ruin runs findGenerationPoint, reset at HEAD so it never leaks
    private static final ThreadLocal<Boolean> the_beyond$hosted = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final Set<String> the_beyond$logged = ConcurrentHashMap.newKeySet();
    private static final Set<String> the_beyond$loggedDistributed = ConcurrentHashMap.newKeySet();
    private static final Set<String> the_beyond$loggedFallback = ConcurrentHashMap.newKeySet();
    private static final ThreadLocal<Integer> the_beyond$islandSurfaceY = ThreadLocal.withInitial(() -> Integer.MIN_VALUE);
    private static final int the_beyond$MEGASHIP_SUPPORT_RADIUS = 56;
    private static final int the_beyond$MEGASHIP_SUPPORT_STRIDE = 8;
    private static final int the_beyond$MAX_RESINK = 64;
    private static final int the_beyond$SEAT_STRIDE = 4;
    private static final int the_beyond$SEAT_LOOK_DOWN = 48;
    private static final int the_beyond$SEAT_LOOK_UP = 24;
    private static final int the_beyond$LAYER_PREF_BAND = 16;
    private static final int the_beyond$SEAT_LAYER_TOL = 6;
    private static final int the_beyond$KEEL_MARGIN = 2;
    private static final int the_beyond$HULL_HALF_W = 24;
    private static final int the_beyond$HULL_STERN = 24;
    private static final int the_beyond$HULL_BOW = 72;
    // The end_side piece hangs off the hull, so the predicted plan carries slack for it.
    private static final int the_beyond$HULL_SLACK = 8;

    @Inject(method = "findGenerationPoint", at = @At("HEAD"), cancellable = true, remap = false)
    private void the_beyond$hostMesHead(Structure.GenerationContext context,
            CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {
        the_beyond$hosted.set(Boolean.FALSE);
        the_beyond$islandSurfaceY.set(Integer.MIN_VALUE);
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
            // embedded ships draw a layer big enough to hold the hull, none means no spawn
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
                    if (Isleweaver.TRACE && the_beyond$loggedFallback.add("no-pancake@" + key + "@" + context.chunkPos().x + "," + context.chunkPos().z)
                            && the_beyond$loggedFallback.size() <= 40) {
                        Isleweaver.LOGGER.debug("[Isleweaver][MES] {} REJECTED: no pancake with enough footprint coverage (would float) chunk=[{},{}]",
                                key, context.chunkPos().x, context.chunkPos().z);
                    }
                    return;
                }
                // the hull lands up to 72 blocks off the chunk corner, so the gate samples its predicted footprint
                BoundingBox plan = the_beyond$predictedFootprint(context);
                int[] seat = the_beyond$seatUnder(plan, pancakeTop);
                int need = IsleweaverConfig.MEGASHIP_MIN_ISLAND_SUPPORT.get();
                if (seat[1] < need) {
                    the_beyond$hosted.set(Boolean.FALSE);
                    cir.setReturnValue(Optional.empty());
                    if (Isleweaver.TRACE && the_beyond$loggedFallback.add("thin@" + key + "@" + context.chunkPos().x + "," + context.chunkPos().z)
                            && the_beyond$loggedFallback.size() <= 40) {
                        Isleweaver.LOGGER.debug("[Isleweaver][MES] {} REJECTED: hull footprint [{},{}..{},{}] covers only {}% of one layer (needs {}%) chunk=[{},{}]",
                                key, plan.minX(), plan.minZ(), plan.maxX(), plan.maxZ(), seat[1], need,
                                context.chunkPos().x, context.chunkPos().z);
                    }
                    return;
                }
                the_beyond$islandSurfaceY.set(pancakeTop);
            }
            boolean embedded = BeyondForeignStructureProfiles.isEmbedded(key);
            boolean hovering = MoogsEndStructuresProfiles.host(key, self);
            if (the_beyond$logged.add(key.toString())) {
                Isleweaver.LOGGER.debug("[Isleweaver][MES] {} hosted: flatness-skip + {}", key,
                        hovering ? "FLOATING carve (hovering ship)"
                             : embedded ? "EMBEDDED distributed carve-only (island-buried, no platform)"
                             : "distributed organic carve (ground ruin, beard-protected)");
            }
        } catch (Throwable t) {
            the_beyond$hosted.set(Boolean.FALSE);
        }
    }

    // MES re-seats every piece on the average land, undoing the sink, so the hull goes back to its layer
    @Inject(method = "postLayoutAdjustments", at = @At("RETURN"), remap = false)
    private void the_beyond$resinkEmbeddedHull(StructurePiecesBuilder builder, Structure.GenerationContext context,
            int startY, BlockPos startPos, int yAllowanceA, int yAllowanceB,
            List<PoolElementStructurePiece> pieces, CallbackInfo ci) {
        int pancakeTop = the_beyond$islandSurfaceY.get();
        if (pancakeTop == Integer.MIN_VALUE || pieces == null || pieces.isEmpty()) return;
        try {
            Structure self = (Structure) (Object) this;
            ResourceLocation key = the_beyond$structureKey(context, self);
            if (key == null || !BeyondForeignStructureProfiles.isEmbedded(key)) return;
            int from = pieces.get(0).getBoundingBox().minY();
            BoundingBox real = the_beyond$hullFootprint(pieces);
            BoundingBox pred = the_beyond$predictedFootprint(context);
            if (real != null && Isleweaver.TRACE && the_beyond$logged.add("plan@" + key)) {
                boolean fits = pred.minX() <= real.minX() && pred.maxX() >= real.maxX()
                        && pred.minZ() <= real.minZ() && pred.maxZ() >= real.maxZ();
                Isleweaver.LOGGER.debug("[Isleweaver][MES] {} footprint predicted=[{},{}..{},{}] real=[{},{}..{},{}] covers={}",
                        key, pred.minX(), pred.minZ(), pred.maxX(), pred.maxZ(),
                        real.minX(), real.minZ(), real.maxX(), real.maxZ(), fits);
            }
            int anchor = Math.abs(pancakeTop - from) > the_beyond$MAX_RESINK ? from : pancakeTop;
            int[] seat = the_beyond$seatUnderHull(pieces, anchor);
            int layerTop = seat[0], cover = seat[1], thick = seat[2];
            int want = IsleweaverConfig.MEGASHIP_SINK_DEPTH.get();
            int sink = Math.max(0, Math.min(want, thick - the_beyond$KEEL_MARGIN));
            // MES's absent max_y_allowed reads as MAX_VALUE, so only the world limits can clamp here
            int to = Math.max(context.heightAccessor().getMinBuildHeight(),
                    Math.min(context.heightAccessor().getMaxBuildHeight() - 1, layerTop - sink));
            int dy = to - from;
            if (dy == 0) return;
            if (Math.abs(dy) > the_beyond$MAX_RESINK) {
                if (the_beyond$logged.add("resink-absurd")) {
                    Isleweaver.LOGGER.warn("[Isleweaver][MES] {} re-sink of {} blocks refused (from={} to={} pancakeTop={}).",
                            key, dy, from, to, pancakeTop);
                }
                return;
            }
            for (PoolElementStructurePiece p : pieces) p.move(0, dy, 0);
            if (Isleweaver.TRACE && the_beyond$loggedDistributed.add("resink@" + key + "@" + context.chunkPos().x + "," + context.chunkPos().z)
                    && the_beyond$loggedDistributed.size() <= 40) {
                Isleweaver.LOGGER.debug("[Isleweaver][MES] {} EMBEDDED re-sunk after burying: {} -> {} (dy={} layerTop={} cover={}% thick={} sink={}/{} pancakeTop={} anchor={}) chunk=[{},{}]",
                        key, from, to, dy, layerTop, cover, thick, sink, want, pancakeTop, anchor,
                        context.chunkPos().x, context.chunkPos().z);
            }
        } catch (Throwable t) {
            if (the_beyond$logged.add("resink-error")) {
                Isleweaver.LOGGER.warn("[Isleweaver][MES] re-sink skipped; the hull keeps MES's burying.", t);
            }
        }
    }

    // Layer top, coverage percent and rock depth under the assembled hull, or the anchor given when no sampled column has rock.
    private static int[] the_beyond$seatUnderHull(List<PoolElementStructurePiece> pieces, int pancakeTop) {
        BoundingBox all = the_beyond$hullFootprint(pieces);
        if (all == null) return new int[]{pancakeTop, 0, Integer.MAX_VALUE};
        return the_beyond$seatUnder(all, pancakeTop);
    }

    @org.jetbrains.annotations.Nullable
    private static BoundingBox the_beyond$hullFootprint(List<PoolElementStructurePiece> pieces) {
        BoundingBox all = null;
        for (PoolElementStructurePiece p : pieces) {
            BoundingBox b = p.getBoundingBox();
            all = all == null ? b
                    : BoundingBox.encapsulatingBoxes(java.util.List.of(all, b)).orElse(all);
        }
        return all;
    }

    // Where the hull will land, from the rotation MES draws for this chunk with the same seed it uses.
    private static BoundingBox the_beyond$predictedFootprint(Structure.GenerationContext context) {
        WorldgenRandom rng = new WorldgenRandom(new LegacyRandomSource(0L));
        rng.setLargeFeatureSeed(context.seed(), context.chunkPos().x, context.chunkPos().z);
        Rotation rot = Rotation.getRandom(rng);
        int cx = context.chunkPos().getMinBlockX(), cz = context.chunkPos().getMinBlockZ();
        int sl = the_beyond$HULL_SLACK;
        int w = the_beyond$HULL_HALF_W + sl, back = the_beyond$HULL_STERN + sl, fwd = the_beyond$HULL_BOW + sl;
        switch (rot) {
            case CLOCKWISE_90:        return new BoundingBox(cx - fwd, 0, cz - back, cx + back, 0, cz + w - 1);
            case COUNTERCLOCKWISE_90: return new BoundingBox(cx - back, 0, cz - w, cx + fwd, 0, cz + back - 1);
            case CLOCKWISE_180:       return new BoundingBox(cx - w, 0, cz - fwd, cx + back - 1, 0, cz + back);
            default:                  return new BoundingBox(cx - w, 0, cz - back, cx + back - 1, 0, cz + fwd);
        }
    }

    private static int[] the_beyond$seatUnder(BoundingBox all, int pancakeTop) {
        int lo = pancakeTop - the_beyond$SEAT_LOOK_DOWN, hi = pancakeTop + the_beyond$SEAT_LOOK_UP;
        java.util.List<int[]> tops = new java.util.ArrayList<>();
        int sampled = 0;
        for (int x = all.minX(); x <= all.maxX(); x += the_beyond$SEAT_STRIDE) {
            for (int z = all.minZ(); z <= all.maxZ(); z += the_beyond$SEAT_STRIDE) {
                sampled++;
                int t = BeyondTerrain.findSurfaceTop(x, z, lo, hi);
                if (t == Integer.MIN_VALUE) continue;
                int d = 0;
                while (d < the_beyond$SEAT_LOOK_DOWN && BeyondTerrain.isSolidAt(x, t - d, z)) d++;
                tops.add(new int[]{t, d});
            }
        }
        if (tops.isEmpty() || sampled == 0) return new int[]{pancakeTop, 0, Integer.MAX_VALUE};
        int want = IsleweaverConfig.MEGASHIP_SINK_DEPTH.get();
        java.util.List<Integer> seen = new java.util.ArrayList<>();
        java.util.List<int[]> layers = new java.util.ArrayList<>();
        for (int[] c : tops) {
            boolean dup = false;
            for (int t : seen) if (Math.abs(t - c[0]) <= the_beyond$SEAT_LAYER_TOL) { dup = true; break; }
            if (dup) continue;
            seen.add(c[0]);
            java.util.List<Integer> depths = new java.util.ArrayList<>();
            for (int[] o : tops) if (Math.abs(o[0] - c[0]) <= the_beyond$SEAT_LAYER_TOL) depths.add(o[1]);
            java.util.Collections.sort(depths);
            int thick = depths.get(depths.size() / 4);
            int keel = c[0] - Math.max(0, Math.min(want, thick - the_beyond$KEEL_MARGIN));
            int solid = 0;
            for (int x = all.minX(); x <= all.maxX(); x += the_beyond$SEAT_STRIDE) {
                for (int z = all.minZ(); z <= all.maxZ(); z += the_beyond$SEAT_STRIDE) {
                    if (BeyondTerrain.isSolidAt(x, keel, z)) solid++;
                }
            }
            layers.add(new int[]{c[0], solid, thick});
        }
        // a thin crust covers a hull without holding it: depth first, relaxed rung by rung, the deepest carrying layer wins
        int[] rungs = {want + the_beyond$KEEL_MARGIN, want / 2 + the_beyond$KEEL_MARGIN,
                the_beyond$KEEL_MARGIN + 2, 0};
        int floor = IsleweaverConfig.MEGASHIP_MIN_ISLAND_SUPPORT.get() * sampled / 100;
        for (int need : rungs) {
            int hiTop = Integer.MIN_VALUE;
            for (int[] l : layers) if (l[2] >= need && l[1] >= floor && l[0] > hiTop) hiTop = l[0];
            if (hiTop == Integer.MIN_VALUE) continue;
            int[] pick = null;
            for (int[] l : layers) {
                if (l[2] < need || l[1] < floor || l[0] < hiTop - the_beyond$LAYER_PREF_BAND) continue;
                if (pick == null || l[0] < pick[0]) pick = l;
            }
            if (pick != null) return new int[]{pick[0], (int) Math.round(100.0 * pick[1] / sampled), pick[2]};
        }
        int[] wide = null;
        for (int need : rungs) {
            for (int[] l : layers) if (l[2] >= need && (wide == null || l[1] > wide[1])) wide = l;
            if (wide != null) break;
        }
        if (wide != null) return new int[]{wide[0], (int) Math.round(100.0 * wide[1] / sampled), wide[2]};
        return new int[]{pancakeTop, 0, Integer.MAX_VALUE};
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

    // seats at a biome-matched layer with an empty heightmap so MES places it verbatim
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
        // logged once, its absence means the wrap did not apply and placements stayed vanilla topmost
        if (the_beyond$logged.add("wrap-active")) {
            Isleweaver.LOGGER.debug("[Isleweaver][MES] placement wrap active (assembleJigsawStructure intercepted).");
        }
        BlockPos placeAt = startPos;
        Optional<Heightmap.Types> heightmap = heightmapType;
        if (the_beyond$hosted.get()) {
            try {
                Structure self = (Structure) (Object) this;
                ResourceLocation key = the_beyond$structureKey(context, self);
                boolean embedded = key != null && BeyondForeignStructureProfiles.isEmbedded(key);
                if ((embedded || self.terrainAdaptation() != TerrainAdjustment.NONE)
                        && context.chunkGenerator() instanceof BeyondEndChunkGenerator beg) {
                    // MES re-roots the start piece at the chunk-min corner, so support and biome are checked there
                    int cx = context.chunkPos().getMinBlockX(), cz = context.chunkPos().getMinBlockZ();
                    if (embedded) {
                        int pancakeTop = the_beyond$islandSurfaceY.get();
                        if (pancakeTop != Integer.MIN_VALUE) {
                            int sunkY = Math.max(minY, Math.min(maxY, pancakeTop - IsleweaverConfig.MEGASHIP_SINK_DEPTH.get()));
                            placeAt = new BlockPos(startPos.getX(), sunkY, startPos.getZ());
                            heightmap = Optional.empty();
                            if (Isleweaver.TRACE && the_beyond$loggedDistributed.add("sink@" + key + "@" + context.chunkPos().x + "," + context.chunkPos().z)
                                    && the_beyond$loggedDistributed.size() <= 40) {
                                Isleweaver.LOGGER.debug("[Isleweaver][MES] {} EMBEDDED sunk -> Y={} (pancakeTop={} depth={}) chunk=[{},{}]",
                                        key, sunkY, pancakeTop, IsleweaverConfig.MEGASHIP_SINK_DEPTH.get(), context.chunkPos().x, context.chunkPos().z);
                            }
                        }
                    } else if ((double) cx * cx + (double) cz * cz >= 650.0 * 650.0) {
                        beg.computeNoisesIfNotPresent(context.randomState());
                        // the footprint must sit on an island, the base beard bridges island columns but never void
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
                            if (Isleweaver.TRACE && key != null && the_beyond$loggedDistributed.add(key + "@" + context.chunkPos().x + "," + context.chunkPos().z)
                                    && the_beyond$loggedDistributed.size() <= 40) {
                                Isleweaver.LOGGER.debug("[Isleweaver][MES] {} grounded -> Y={} r={} cover={}% topmostLayer={} layers={}valid/{}total chosenLayerTop={} chunk=[{},{}]",
                                        key, picked, coverR, g.coverPct(), g.topmostLayerY(), g.validLayers(), g.totalLayers(), g.chosenLayerTop(), context.chunkPos().x, context.chunkPos().z);
                            }
                        } else {
                            if (Isleweaver.TRACE && key != null && the_beyond$loggedFallback.add("reject@" + key + "@" + context.chunkPos().x + "," + context.chunkPos().z)
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
        if (Isleweaver.TRACE && the_beyond$loggedFallback.add(id + "@" + context.chunkPos().x + "," + context.chunkPos().z)
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

    // half the widest piece span, so a sprawling ruin gets support wide enough not to overhang
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

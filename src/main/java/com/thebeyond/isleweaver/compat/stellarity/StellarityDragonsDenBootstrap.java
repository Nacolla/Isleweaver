package com.thebeyond.isleweaver.compat.stellarity;

import com.thebeyond.api.worldgen.BeyondTerrainState;
import com.thebeyond.isleweaver.Isleweaver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;

// places the center crystal in Stellarity's dragons_den so its exit_portal predicate fires
@EventBusSubscriber(modid = Isleweaver.MODID)
public final class StellarityDragonsDenBootstrap {
    private StellarityDragonsDenBootstrap() {}

    private static final BlockPos BEDROCK_POS = new BlockPos(0, 60, 0);
    private static final double CRYSTAL_X = 0.5D;
    private static final double CRYSTAL_Y = 61.0D;
    private static final double CRYSTAL_Z = 0.5D;

    private static final AABB CENTER_SEARCH_BOX = new AABB(-3.0D, 58.0D, -3.0D, 4.0D, 66.0D, 4.0D);

    // (x, z) centers of the 10 ring spikes, matching Stellarity's ring.json
    private static final int[][] STELLARITY_RING_POSITIONS = new int[][] {
            {  63,   0 }, {  50,  36 }, {  18,  59 }, { -19,  59 },
            { -51,  36 }, { -63,   0 }, { -51, -39 }, { -19, -60 },
            {  18, -60 }, {  50, -39 }
    };

    // 2x2 chunk pad around the altar template anchor at (42, 67, -48)
    private static final ChunkPos[] ALTAR_CHUNKS = new ChunkPos[] {
            new ChunkPos(2, -4),
            new ChunkPos(2, -3),
            new ChunkPos(3, -4),
            new ChunkPos(3, -3)
    };

    // hold the forced-chunk tickets ~10s so Stellarity's mcfunction chain has time to finish
    private static final int UNFORCE_DELAY_TICKS = 200;

    private static boolean placedThisSession = false;
    private static final List<ChunkPos> forcedChunks = new ArrayList<>();
    private static int tickSincePlacement = -1;

    private static ChunkPos[] altarAndRingChunks() {
        List<ChunkPos> out = new ArrayList<>();
        out.add(new ChunkPos(0, 0));
        for (ChunkPos cp : ALTAR_CHUNKS) {
            if (!out.contains(cp)) out.add(cp);
        }
        for (int[] pos : STELLARITY_RING_POSITIONS) {
            ChunkPos cp = new ChunkPos(pos[0] >> 4, pos[1] >> 4);
            if (!out.contains(cp)) out.add(cp);
        }
        return out.toArray(new ChunkPos[0]);
    }

    @SubscribeEvent
    public static void onServerTickPost(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel end = server.getLevel(Level.END);
        if (end == null) {
            return;
        }

        if (tickSincePlacement >= 0) {
            tickSincePlacement++;

            if (tickSincePlacement >= 1 && tickSincePlacement <= 20) {
                int lateKills = 0;
                for (int[] pos : STELLARITY_RING_POSITIONS) {
                    AABB spikeBox = new AABB(
                            pos[0] - 10.0D, 0.0D, pos[1] - 10.0D,
                            pos[0] + 10.0D, 256.0D, pos[1] + 10.0D);
                    for (EndCrystal ec : end.getEntitiesOfClass(EndCrystal.class, spikeBox)) {
                        ec.discard();
                        lateKills++;
                    }
                }
                if (lateKills > 0) {
                    Isleweaver.LOGGER.debug(
                            "[Isleweaver] Delayed kill pass (tick {}): removed {} late-indexed ring crystal(s).",
                            tickSincePlacement, lateKills);
                }
            }

            if (tickSincePlacement >= UNFORCE_DELAY_TICKS) {
                releaseForcedChunks(end);
                tickSincePlacement = -1;
            }
        }

        if (placedThisSession) {
            return;
        }

        if (!ModList.get().isLoaded("stellarity")) {
            placedThisSession = true;
            return;
        }

        if (!BeyondTerrainState.isActive()) {
            placedThisSession = true;
            return;
        }

        if (!end.hasChunk(0, 0)) {
            return;
        }

        // gate cleanup behind a per-world flag, else the ring-crystal kill re-runs on reload and wipes live fight crystals when you rejoin mid-fight
        State state = State.get(end);
        if (state.done) {
            placedThisSession = true;
            return;
        }

        List<Entity> nearby = end.getEntitiesOfClass(Entity.class, CENTER_SEARCH_BOX);
        for (Entity e : nearby) {
            if (!(e instanceof EndCrystal)
                    && (e.getTags().contains("fe.exit_portal") || e.getTags().contains("stellarity.marker"))) {
                Isleweaver.LOGGER.debug(
                        "[Isleweaver] Stellarity marker entity already present, skipping compat fix.");
                state.markDone();
                placedThisSession = true;
                return;
            }
        }

        ChunkPos[] targets = altarAndRingChunks();
        for (ChunkPos cp : targets) {
            end.getChunk(cp.x, cp.z);
            if (end.setChunkForced(cp.x, cp.z, true)) {
                forcedChunks.add(cp);
            }
        }

        int ringCrystalsKilled = 0;
        for (int[] pos : STELLARITY_RING_POSITIONS) {
            AABB spikeBox = new AABB(
                    pos[0] - 10.0D, 0.0D, pos[1] - 10.0D,
                    pos[0] + 10.0D, 256.0D, pos[1] + 10.0D);
            for (EndCrystal ec : end.getEntitiesOfClass(EndCrystal.class, spikeBox)) {
                Isleweaver.LOGGER.debug(
                        "[Isleweaver] Killing ring crystal at ({}, {}, {}) near spike ({}, {})",
                        ec.getX(), ec.getY(), ec.getZ(), pos[0], pos[1]);
                ec.discard();
                ringCrystalsKilled++;
            }
        }
        if (ringCrystalsKilled > 0) {
            Isleweaver.LOGGER.debug(
                    "[Isleweaver] Killed {} pre-existing ring crystal(s) at Stellarity spike positions.",
                    ringCrystalsKilled);
        }

        boolean centerCrystalExists = false;
        for (Entity e : nearby) {
            if (e instanceof EndCrystal ec) {
                double dy = ec.getY() - CRYSTAL_Y;
                double dx = ec.getX() - CRYSTAL_X;
                double dz = ec.getZ() - CRYSTAL_Z;
                if (Math.abs(dy) < 0.6D && Math.abs(dx) < 0.6D && Math.abs(dz) < 0.6D) {
                    centerCrystalExists = true;
                    Isleweaver.LOGGER.debug(
                            "[Isleweaver] Center crystal already exists at ({}, {}, {}), skipping placement, but force-load and ring kill still ran.",
                            ec.getX(), ec.getY(), ec.getZ());
                    break;
                }
            }
        }

        if (!centerCrystalExists) {
            end.setBlockAndUpdate(BEDROCK_POS, Blocks.BEDROCK.defaultBlockState());

            EndCrystal crystal = EntityType.END_CRYSTAL.create(end);
            if (crystal != null) {
                crystal.setInvulnerable(true);
                crystal.setShowBottom(false);
                crystal.moveTo(CRYSTAL_X, CRYSTAL_Y, CRYSTAL_Z, 0.0F, 0.0F);
                end.addFreshEntity(crystal);
                Isleweaver.LOGGER.debug(
                        "[Isleweaver] Placed exit-portal crystal at (0.5, 61, 0.5).");
            } else {
                Isleweaver.LOGGER.warn(
                        "[Isleweaver] Failed to create end crystal entity for Stellarity compat fix.");
            }
        }

        Isleweaver.LOGGER.debug(
                "[Isleweaver] Stellarity compat: force-loaded {} chunks for {} ticks, killed {} ring crystals.",
                forcedChunks.size(), UNFORCE_DELAY_TICKS, ringCrystalsKilled);

        state.markDone();
        tickSincePlacement = 0;
        placedThisSession = true;
    }

    private static void releaseForcedChunks(ServerLevel end) {
        if (forcedChunks.isEmpty()) {
            return;
        }
        for (ChunkPos cp : forcedChunks) {
            end.setChunkForced(cp.x, cp.z, false);
        }
        Isleweaver.LOGGER.debug(
                "[Isleweaver] Released {} forced chunks after Stellarity post_gen window closed.",
                forcedChunks.size());
        forcedChunks.clear();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        placedThisSession = false;
        tickSincePlacement = -1;
        forcedChunks.clear();
    }

    // persisted per-world so the one-time den cleanup never re-runs and re-kills ring crystals on reload
    public static final class State extends SavedData {
        private static final SavedData.Factory<State> FACTORY =
                new SavedData.Factory<>(State::new, State::load, null);

        private boolean done;

        private static State get(ServerLevel end) {
            return end.getDataStorage().computeIfAbsent(FACTORY, "isleweaver_dragons_den");
        }

        private static State load(CompoundTag tag, HolderLookup.Provider provider) {
            State s = new State();
            s.done = tag.getBoolean("done");
            return s;
        }

        private void markDone() {
            if (!done) {
                done = true;
                setDirty();
            }
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
            tag.putBoolean("done", done);
            return tag;
        }
    }
}

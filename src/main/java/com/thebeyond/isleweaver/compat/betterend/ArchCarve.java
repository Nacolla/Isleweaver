package com.thebeyond.isleweaver.compat.betterend;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

// clips an arch's buried cone at the pancake surface, mounds up feet left dangling over void, trims the rest
public final class ArchCarve {
    private static final long MAX_VOLUME = 70L * 70L * 70L;
    private static final int SURF_DOWN = 6, SURF_UP = 6;
    // a foot is a bottom arc cell within this of the springline (excludes the high crown)
    private static final int LEG_BAND = 4;
    private static final int REST_LAT = 1, REST_DOWN = 2;
    // search radius for an island to mound onto; past this the tip is trimmed instead
    private static final int BEARD_MAX = 16;
    // mound radius at the island base, tapering to 1 at the foot
    private static final double BASE_R = 3.0;
    // umbralith, or END_STONE if BetterEnd somehow isn't present
    private static BlockState umbralith;
    private static final ThreadLocal<Snap> SNAP = new ThreadLocal<>();

    private record Snap(int x0, int y0, int z0, int sx, int sy, int sz,
                        int centerY, boolean[] wasSolid) {}

    private ArchCarve() {}

    // snapshots solidity over the arch AABB; centerY (the SDF centre Y) is the pancake surface
    public static void snapshot(WorldGenLevel level, BlockPos center, float side) {
        double cx = center.getX() + 0.5, cy = center.getY() + 0.5, cz = center.getZ() + 0.5;
        double h = side / 2.0;
        int x0 = (int) Math.floor(cx - h), y0 = (int) Math.floor(cy - h), z0 = (int) Math.floor(cz - h);
        int x1 = (int) Math.floor(cx + h), y1 = (int) Math.floor(cy + h), z1 = (int) Math.floor(cz + h);
        int sx = x1 - x0 + 1, sy = y1 - y0 + 1, sz = z1 - z0 + 1;
        if (sx <= 0 || sy <= 0 || sz <= 0 || (long) sx * sy * sz > MAX_VOLUME) {
            SNAP.set(null);
            return;
        }
        try {
            boolean[] wasSolid = new boolean[sx * sy * sz];
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (int dx = 0; dx < sx; dx++)
                for (int dy = 0; dy < sy; dy++)
                    for (int dz = 0; dz < sz; dz++)
                        wasSolid[idx(dx, dy, dz, sy, sz)] =
                                !level.getBlockState(m.set(x0 + dx, y0 + dy, z0 + dz)).isAir();
            SNAP.set(new Snap(x0, y0, z0, sx, sy, sz, center.getY(), wasSolid));
        } catch (Throwable t) {
            SNAP.set(null);   // don't let a failed snapshot leak stale state into the next arch
        }
    }

    public static void carve(WorldGenLevel level) {
        Snap s = SNAP.get();
        try {
            if (s == null) return;
            int sx = s.sx, sy = s.sy, sz = s.sz, cY = s.centerY;
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            BlockState air = Blocks.AIR.defaultBlockState();

            // per-column clip height: the local pancake surface within a band around centerY
            int[] colSurf = new int[sx * sz];
            int loY = cY - SURF_DOWN, hiY = cY + SURF_UP;
            for (int dx = 0; dx < sx; dx++)
                for (int dz = 0; dz < sz; dz++) {
                    int top = Integer.MIN_VALUE;
                    for (int dy = sy - 1; dy >= 0; dy--) {
                        int wy = s.y0 + dy;
                        if (wy < loY) break;
                        if (wy <= hiY && s.wasSolid[idx(dx, dy, dz, sy, sz)]) { top = wy; break; }
                    }
                    colSurf[dx * sz + dz] = (top == Integer.MIN_VALUE) ? cY : top + 1;
                }

            boolean[] arch = new boolean[sx * sy * sz];
            for (int dx = 0; dx < sx; dx++)
                for (int dz = 0; dz < sz; dz++) {
                    int surf = colSurf[dx * sz + dz];
                    for (int dy = 0; dy < sy; dy++) {
                        int i = idx(dx, dy, dz, sy, sz);
                        if (s.wasSolid[i]) continue;
                        int wy = s.y0 + dy;
                        BlockState st = level.getBlockState(m.set(s.x0 + dx, wy, s.z0 + dz));
                        if (st.isAir()) continue;
                        if (wy < surf) { level.setBlock(m, air, 2); } // buried cone
                        else arch[i] = true;
                    }
                }

            // feet: bottom arc cells near the springline that don't rest on terrain
            List<int[]> feet = new ArrayList<>();
            for (int dx = 0; dx < sx; dx++)
                for (int dz = 0; dz < sz; dz++)
                    for (int dy = 0; dy < sy; dy++) {
                        int i = idx(dx, dy, dz, sy, sz);
                        if (!arch[i]) continue;
                        int wy = s.y0 + dy;
                        if (wy > cY + LEG_BAND) continue;                       // not the crown
                        if (dy > 0 && arch[idx(dx, dy - 1, dz, sy, sz)]) continue; // not a bottom
                        if (rests(s, dx, dy, dz)) continue;                     // already on land
                        feet.add(new int[]{dx, dy, dz});
                    }

            for (int[] f : feet) {
                if (rests(s, f[0], f[1], f[2])) continue;                       // a prior mound caught it
                int[] b = nearestSolid(level, s, f[0], f[1], f[2], m);
                if (b != null) mound(level, s, b, f, m);
                else trimTip(level, s, arch, f, air, m);
            }
        } finally {
            SNAP.remove();
        }
    }

    private static boolean rests(Snap s, int dx, int dy, int dz) {
        for (int ox = -REST_LAT; ox <= REST_LAT; ox++)
            for (int oz = -REST_LAT; oz <= REST_LAT; oz++)
                for (int oy = -REST_DOWN; oy <= 0; oy++) {
                    int nx = dx + ox, ny = dy + oy, nz = dz + oz;
                    if (nx >= 0 && nx < s.sx && ny >= 0 && ny < s.sy && nz >= 0 && nz < s.sz
                            && s.wasSolid[idx(nx, ny, nz, s.sy, s.sz)]) return true;
                }
        return false;
    }

    // nearest solid to the foot within BEARD_MAX, biased downward so it prefers footing below
    private static int[] nearestSolid(WorldGenLevel level, Snap s, int fx, int fy, int fz,
                                      BlockPos.MutableBlockPos m) {
        for (int r = 1; r <= BEARD_MAX; r++) {
            int[] best = null;
            int bestScore = Integer.MAX_VALUE;
            for (int ox = -r; ox <= r; ox++)
                for (int oy = -r; oy <= r; oy++)
                    for (int oz = -r; oz <= r; oz++) {
                        if (Math.max(Math.abs(ox), Math.max(Math.abs(oy), Math.abs(oz))) != r) continue;
                        if (!preSolid(level, s, fx + ox, fy + oy, fz + oz, m)) continue;
                        int score = ox * ox + oz * oz + (oy > 0 ? oy * oy * 6 : oy * oy);
                        if (score < bestScore) { bestScore = score; best = new int[]{fx + ox, fy + oy, fz + oz}; }
                    }
            if (best != null) return best;
        }
        return null;
    }

    // tapered mound from base b up to foot f: wide noisy base, narrowing to the leg
    private static void mound(WorldGenLevel level, Snap s, int[] b, int[] f,
                             BlockPos.MutableBlockPos m) {
        BlockState fill = umbralith();
        int steps = Math.max(Math.abs(f[0] - b[0]), Math.max(Math.abs(f[1] - b[1]), Math.abs(f[2] - b[2])));
        if (steps == 0) return;
        for (int k = 0; k <= steps; k++) {
            double t = k / (double) steps;
            int cx = b[0] + (int) Math.round((f[0] - b[0]) * t);
            int cy = b[1] + (int) Math.round((f[1] - b[1]) * t);
            int cz = b[2] + (int) Math.round((f[2] - b[2]) * t);
            int rad = (int) Math.ceil(BASE_R * (1.0 - t) + 0.5);            // wide base -> thin top
            for (int ox = -rad; ox <= rad; ox++)
                for (int oz = -rad; oz <= rad; oz++) {
                    double n = noise(s.x0 + cx + ox, s.y0 + cy, s.z0 + cz + oz);
                    if (ox * ox + oz * oz > rad * rad * (0.55 + 0.45 * n)) continue; // noisy edge
                    int nx = cx + ox, ny = cy, nz = cz + oz;
                    if (nx < 0 || nx >= s.sx || ny < 0 || ny >= s.sy || nz < 0 || nz >= s.sz) continue;
                    if (s.wasSolid[idx(nx, ny, nz, s.sy, s.sz)]) continue;
                    if (level.getBlockState(m.set(s.x0 + nx, s.y0 + ny, s.z0 + nz)).isAir()) {
                        level.setBlock(m, fill, 2);
                    }
                }
        }
    }

    // removes the dangling tip (foot cell + small neighbourhood) when no terrain is in mound reach
    private static void trimTip(WorldGenLevel level, Snap s, boolean[] arch, int[] f,
                               BlockState air, BlockPos.MutableBlockPos m) {
        for (int oy = 0; oy >= -3; oy--)
            for (int ox = -1; ox <= 1; ox++)
                for (int oz = -1; oz <= 1; oz++) {
                    int nx = f[0] + ox, ny = f[1] + oy, nz = f[2] + oz;
                    if (nx < 0 || nx >= s.sx || ny < 0 || ny >= s.sy || nz < 0 || nz >= s.sz) continue;
                    int i = idx(nx, ny, nz, s.sy, s.sz);
                    if (!arch[i]) continue;
                    arch[i] = false;
                    level.setBlock(m.set(s.x0 + nx, s.y0 + ny, s.z0 + nz), air, 2);
                }
    }

    private static boolean preSolid(WorldGenLevel level, Snap s, int nx, int ny, int nz,
                                    BlockPos.MutableBlockPos m) {
        if (nx >= 0 && nx < s.sx && ny >= 0 && ny < s.sy && nz >= 0 && nz < s.sz)
            return s.wasSolid[idx(nx, ny, nz, s.sy, s.sz)];
        return !level.getBlockState(m.set(s.x0 + nx, s.y0 + ny, s.z0 + nz)).isAir();
    }

    private static BlockState umbralith() {
        if (umbralith == null)
            umbralith = BuiltInRegistries.BLOCK
                    .getOptional(ResourceLocation.fromNamespaceAndPath("betterend", "umbralith"))
                    .map(net.minecraft.world.level.block.Block::defaultBlockState)
                    .orElse(Blocks.END_STONE.defaultBlockState());
        return umbralith;
    }

    // deterministic value hash in [0,1)
    private static double noise(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 2147483647;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0x7fffffff) / (double) 0x7fffffff;
    }

    private static int idx(int dx, int dy, int dz, int sy, int sz) {
        return (dx * sy + dy) * sz + dz;
    }
}

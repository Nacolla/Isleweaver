package com.thebeyond.isleweaver.compat.betterend;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import org.joml.Vector3f;

// picks the arch axis whose two feet rest most solidly on a pancake island
public final class ArchOrient {
    private static final int DIRS = 24;
    private static final int FOOT_DOWN = 3, FOOT_UP = 0;

    private ArchOrient() {}

    public static Vector3f fit(WorldGenLevel level, BlockPos origin, float bigR, Vector3f fallback) {
        int r = Math.max(4, Math.round(bigR));
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        Vector3f best = fallback;
        int bestScore = -1;
        for (int d = 0; d < DIRS; d++) {
            double a = d * (Math.PI * 2.0 / DIRS);
            float sx = (float) Math.sin(a), sz = (float) Math.cos(a);
            int score = 0;
            if (footOnIsland(level, origin, sx, sz, r, m)) score += 2;
            if (footOnIsland(level, origin, -sx, -sz, r, m)) score += 2;
            if (columnSolid(level, origin, Math.round((r + 3) * sx), Math.round((r + 3) * sz), m)) score++;
            if (columnSolid(level, origin, Math.round((r + 3) * -sx), Math.round((r + 3) * -sz), m)) score++;
            if (score > bestScore) {
                bestScore = score;
                best = new Vector3f(sx, 0.0f, sz);
                if (score == 6) break; // both feet fully grounded and inland, can't do better
            }
        }
        return best;
    }

    private static boolean footOnIsland(WorldGenLevel level, BlockPos o, float sx, float sz,
                                        int r, BlockPos.MutableBlockPos m) {
        for (int rr = r - 3; rr <= r + 3; rr += 3)
            if (!columnSolid(level, o, Math.round(rr * sx), Math.round(rr * sz), m)) return false;
        return true;
    }

    private static boolean columnSolid(WorldGenLevel level, BlockPos o, int dx, int dz,
                                       BlockPos.MutableBlockPos m) {
        int x = o.getX() + dx, z = o.getZ() + dz;
        for (int y = o.getY() + FOOT_UP; y >= o.getY() - FOOT_DOWN; y--)
            if (!level.getBlockState(m.set(x, y, z)).isAir()) return true;
        return false;
    }
}

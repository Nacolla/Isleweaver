package com.thebeyond.isleweaver.compat.betterend;

import com.thebeyond.api.worldgen.BeyondTerrainState;

// fakes the Y reads feeding BetterEnd's hardcoded vanilla-End gates, without touching the actual placement value
public final class BetterEndYGate {
    private BetterEndYGate() {}

    // 64 is the one value that satisfies every vanilla-End validity range at once
    public static int passGate(int originalSurfaceY) {
        return BeyondTerrainState.isActive() ? 64 : originalSurfaceY;
    }

    // swap a scan/void-floor constant so the scan can reach Beyond's negative-Y terrain
    public static int dimFloor(int original) {
        return BeyondTerrainState.isActive() ? BeyondTerrainState.getDimMinY() : original;
    }

    // treat an absolute vanilla-End Y constant as an offset from dimMinY instead
    public static int dimRelative(int original) {
        return BeyondTerrainState.isActive() ? BeyondTerrainState.getDimMinY() + original : original;
    }
}

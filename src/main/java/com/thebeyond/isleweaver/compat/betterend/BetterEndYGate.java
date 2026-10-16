package com.thebeyond.isleweaver.compat.betterend;

import com.thebeyond.api.worldgen.BeyondTerrainState;

// fakes the Y reads feeding BetterEnd's hardcoded vanilla-End gates (64 passes every one) without touching the placement value
public final class BetterEndYGate {
    private BetterEndYGate() {}

    public static int passGate(int originalSurfaceY) {
        return BeyondTerrainState.isActive() ? 64 : originalSurfaceY;
    }

    public static int dimFloor(int original) {
        return BeyondTerrainState.isActive() ? BeyondTerrainState.getDimMinY() : original;
    }

    public static int dimRelative(int original) {
        return BeyondTerrainState.isActive() ? BeyondTerrainState.getDimMinY() + original : original;
    }
}

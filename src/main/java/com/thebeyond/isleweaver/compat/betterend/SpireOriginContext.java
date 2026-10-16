package com.thebeyond.isleweaver.compat.betterend;

// per-thread spire origin Y so the skinning lambda's biome lookup hits the origin cell rather than each block's own
public final class SpireOriginContext {
    private static final ThreadLocal<Integer> ORIGIN_Y = new ThreadLocal<>();

    private SpireOriginContext() {}

    public static void set(int y) {
        ORIGIN_Y.set(y);
    }

    public static void clear() {
        ORIGIN_Y.remove();
    }

    public static Integer get() {
        return ORIGIN_Y.get();
    }
}

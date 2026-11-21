package com.thebeyond.isleweaver.compat.executiveorders;

public final class VoidBounce {
    public static final int VANILLA_END_HEIGHT = 256;
    public static final double RICOCHET = 1.5;
    // the server rejects a player move of 10 blocks in one tick, the bounce stays under that
    public static final double MAX_SPEED = 9.5;
    private static final double DRAG = 0.98;
    private static final double LONG_FALL = 4096.0;

    private VoidBounce() {}

    public static double scaledSpeed(double fallSpeed, double gravity, double worldScale) {
        double own = fallSpeed * RICOCHET;
        if (worldScale <= 1.0 || gravity <= 0.0 || fallSpeed <= 0.0 || own >= MAX_SPEED) return own;
        double terminal = gravity * DRAG / (1.0 - DRAG);
        double vanillaSpeed = impactSpeed(fallHeight(fallSpeed, terminal) / worldScale, terminal);
        double target = worldScale * (2.0 * vanillaSpeed + rise(vanillaSpeed * RICOCHET, terminal)) - 2.0 * fallSpeed;
        if (rise(MAX_SPEED, terminal) <= target) return MAX_SPEED;
        double low = own, high = MAX_SPEED;
        for (int i = 0; i < 40; i++) {
            double mid = 0.5 * (low + high);
            if (rise(mid, terminal) < target) low = mid; else high = mid;
        }
        return 0.5 * (low + high);
    }

    static double rise(double speed, double terminal) {
        if (speed <= 0.0) return 0.0;
        int ticks = (int) Math.floor(Math.log((speed + terminal) / terminal) / -Math.log(DRAG));
        return (speed + terminal) * DRAG * (1.0 - Math.pow(DRAG, ticks)) / (1.0 - DRAG) - ticks * terminal;
    }

    static double fallHeight(double speed, double terminal) {
        if (speed >= terminal * 0.9999) return LONG_FALL;
        double ticks = Math.log(1.0 - speed / terminal) / Math.log(DRAG);
        return terminal * ticks - speed / (1.0 - DRAG);
    }

    static double impactSpeed(double height, double terminal) {
        double low = 0.0, high = terminal * 0.9999;
        for (int i = 0; i < 40; i++) {
            double mid = 0.5 * (low + high);
            if (fallHeight(mid, terminal) < height) low = mid; else high = mid;
        }
        return 0.5 * (low + high);
    }
}

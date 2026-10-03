package com.thebeyond.isleweaver.compat.mes;

public final class MesTuning {
    // air above a layer for a ground ruin: enough to read as exposed, low enough that dense stacks qualify
    public static final int LAYER_HEADROOM = 8;
    public static final int SUPPORT_RADIUS = 5;
    public static final int GROUND_STRIDE = 6;
    // A layer counts as grounded when its footprint surface sits in [layerY - FILL, layerY + OVER].
    public static final int SUPPORT_FILL = 20;
    public static final int SUPPORT_OVER = 8;
    public static final double MIN_COVER = 0.8;

    private MesTuning() {}
}

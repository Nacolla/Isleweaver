package com.thebeyond.isleweaver.compat.mes;

public final class MesTuning {
    // Air gap above a layer to seat a ground ruin: enough that the ruin reads as exposed, low enough that dense island stacks still qualify.
    public static final int LAYER_HEADROOM = 8;
    public static final int SUPPORT_RADIUS = 5;
    public static final int GROUND_STRIDE = 6;
    // A layer counts as grounded when its footprint surface sits in [layerY - FILL, layerY + OVER].
    public static final int SUPPORT_FILL = 20;
    public static final int SUPPORT_OVER = 8;
    public static final double MIN_COVER = 0.5;

    private MesTuning() {}
}

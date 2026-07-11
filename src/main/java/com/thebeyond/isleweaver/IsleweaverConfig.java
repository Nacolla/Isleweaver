package com.thebeyond.isleweaver;

import net.neoforged.neoforge.common.ModConfigSpec;

// Isleweaver config. Registered only when the relevant mod is present (see Isleweaver). Currently MES megaship tuning.
public final class IsleweaverConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue MEGASHIP_MIN_ISLAND_SUPPORT;
    public static final ModConfigSpec.IntValue MEGASHIP_SINK_DEPTH;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("MES crashed megaships").push("mes_megaships");
        MEGASHIP_MIN_ISLAND_SUPPORT = b
                .comment("Min % of a crashed megaship's footprint that must sit over one pancake to spawn there;",
                        "below it the pancake's too small and the ship would float. 0 = never skip. Default: 45")
                .defineInRange("minIslandSupportPercent", 45, 0, 100);
        MEGASHIP_SINK_DEPTH = b
                .comment("Blocks the megaship's hull sinks into the island below it. Default: 8")
                .defineInRange("sinkDepth", 8, 0, 64);
        b.pop();
        SPEC = b.build();
    }

    private IsleweaverConfig() {}
}

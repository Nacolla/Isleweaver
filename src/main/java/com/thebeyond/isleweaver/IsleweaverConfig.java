package com.thebeyond.isleweaver;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public final class IsleweaverConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue MEGASHIP_MIN_ISLAND_SUPPORT;
    public static final ModConfigSpec.IntValue MEGASHIP_SINK_DEPTH;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DISABLED_END_BIOMES;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("MES crashed megaships").push("mes_megaships");
        MEGASHIP_MIN_ISLAND_SUPPORT = b
                .comment("Min % of a crashed megaship's footprint that must sit over one island to spawn there,"
                        + " below it the island is too small and the ship would float. 0 = never skip")
                .defineInRange("minIslandSupportPercent", 45, 0, 100);
        MEGASHIP_SINK_DEPTH = b
                .comment("Blocks the megaship's hull sinks into the island below it")
                .defineInRange("sinkDepth", 8, 0, 64);
        b.pop();
        b.comment("End biomes in the Beyond's biome pool").push("end_biomes");
        DISABLED_END_BIOMES = b
                .comment("Biomes kept out of new End chunks, by id (\"betterend:megalake\") or whole mod (\"stellarity:*\")."
                        + " Better End's biome toggle and Biomes O' Plenty's biome config are respected on top of this list."
                        + " Listing Beyond or vanilla End biomes can soft-lock Beyond progression")
                .defineListAllowEmpty("disabledBiomes", List.of(), () -> "", o -> o instanceof String);
        b.pop();
        SPEC = b.build();
    }

    private IsleweaverConfig() {}
}

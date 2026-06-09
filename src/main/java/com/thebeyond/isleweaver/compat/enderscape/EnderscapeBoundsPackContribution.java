package com.thebeyond.isleweaver.compat.enderscape;

import com.thebeyond.api.event.BeyondTerrainPackAssembleEvent;
import com.thebeyond.isleweaver.Isleweaver;
import com.thebeyond.isleweaver.compat.BoundsPackBuilder;
import net.minecraft.server.packs.repository.Pack;

// extends End y-bounds to -64..320 for Enderscape; priority 50 deliberately loses to Astrological's 100
public final class EnderscapeBoundsPackContribution {
    private static final String PACK_NAME = "convergence_enderscape_bounds";
    private static final int PRIORITY = 50;
    private static final String LOG_MESSAGE =
            "Enderscape detected — attaching convergence_enderscape_bounds (extends End y-bounds to -64..320).";

    private EnderscapeBoundsPackContribution() {}

    public static void contribute(BeyondTerrainPackAssembleEvent event) {
        Pack pack = BoundsPackBuilder.build(PACK_NAME, "Isleweaver - " + PACK_NAME);
        if (pack == null) {
            Isleweaver.LOGGER.warn("[Isleweaver] {} pack build failed; Enderscape y-bounds override skipped.", PACK_NAME);
            return;
        }
        event.contributeBoundsOverride(PACK_NAME, PRIORITY, pack, LOG_MESSAGE);
    }
}

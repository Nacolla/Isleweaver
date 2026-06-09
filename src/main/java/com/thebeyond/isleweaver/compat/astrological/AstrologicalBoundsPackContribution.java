package com.thebeyond.isleweaver.compat.astrological;

import com.thebeyond.api.event.BeyondTerrainPackAssembleEvent;
import com.thebeyond.isleweaver.Isleweaver;
import com.thebeyond.isleweaver.compat.BoundsPackBuilder;
import net.minecraft.server.packs.repository.Pack;

// Astrological y-bounds override (-256..384); priority 100 beats Enderscape's 50
public final class AstrologicalBoundsPackContribution {
    private static final String PACK_NAME = "convergence_astrological_bounds";
    private static final int PRIORITY = 100;
    private static final String LOG_MESSAGE =
            "Astrological detected — attaching convergence_astrological_bounds (extends End y-bounds to -256..384).";

    private AstrologicalBoundsPackContribution() {}

    public static void contribute(BeyondTerrainPackAssembleEvent event) {
        Pack pack = BoundsPackBuilder.build(PACK_NAME, "Isleweaver - " + PACK_NAME);
        if (pack == null) {
            Isleweaver.LOGGER.warn("[Isleweaver] {} pack build failed; Astrological y-bounds override skipped.", PACK_NAME);
            return;
        }
        event.contributeBoundsOverride(PACK_NAME, PRIORITY, pack, LOG_MESSAGE);
    }
}

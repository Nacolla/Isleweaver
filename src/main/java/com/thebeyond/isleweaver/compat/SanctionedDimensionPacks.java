package com.thebeyond.isleweaver.compat;

import org.jetbrains.annotations.ApiStatus;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

// pack ids exempt from EndDimensionFilteringPackResources stripping, so their End dimension-type override survives
@ApiStatus.Internal
public final class SanctionedDimensionPacks {
    private static final Set<String> IDS = new CopyOnWriteArraySet<>();

    private SanctionedDimensionPacks() {}

    public static void sanction(String packId) {
        if (packId != null) IDS.add(packId);
    }

    public static boolean isSanctioned(String packId) {
        return packId != null && IDS.contains(packId);
    }
}

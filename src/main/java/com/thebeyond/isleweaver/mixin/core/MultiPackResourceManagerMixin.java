package com.thebeyond.isleweaver.mixin.core;

import com.thebeyond.isleweaver.Isleweaver;
import com.thebeyond.isleweaver.compat.EndDimensionFilteringPackResources;
import com.thebeyond.isleweaver.compat.SanctionedDimensionPacks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;

// wraps foreign packs so beyond_terrain wins the End dimension slots; no-op when beyond_terrain isn't loaded
@Mixin(MultiPackResourceManager.class)
public class MultiPackResourceManagerMixin {

    private static final String BEYOND_PACK_ID_PREFIX = "mod/the_beyond:";
    private static final String BEYOND_MAIN_PACK_ID = BEYOND_PACK_ID_PREFIX + "beyond_terrain";

    private static final ResourceLocation END_DIMENSION = ResourceLocation.fromNamespaceAndPath(
            "minecraft", "dimension/the_end.json");
    private static final ResourceLocation END_DIMENSION_TYPE = ResourceLocation.fromNamespaceAndPath(
            "minecraft", "dimension_type/the_end.json");

    @ModifyVariable(
            method = "<init>(Lnet/minecraft/server/packs/PackType;Ljava/util/List;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private static List<PackResources> convergence$filterEndDimensionOverrides(List<PackResources> packs) {
        if (packs == null || packs.isEmpty()) {
            Isleweaver.LOGGER.info("[MultiPackFilter] construction with {} pack(s) — bailing early (null or empty).",
                    packs == null ? "null" : "0");
            return packs;
        }

        List<String> allIds = new ArrayList<>(packs.size());
        for (PackResources pr : packs) {
            allIds.add(pr.packId());
        }
        Isleweaver.LOGGER.info("[MultiPackFilter] construction with {} pack(s): {}", packs.size(), allIds);

        // the bounds sidecar is a child of beyond_terrain, so checking for the parent is enough
        boolean beyondPresent = false;
        for (PackResources pr : packs) {
            if (BEYOND_MAIN_PACK_ID.equals(pr.packId())) {
                beyondPresent = true;
                break;
            }
        }
        if (!beyondPresent) {
            Isleweaver.LOGGER.info("[MultiPackFilter] beyond_terrain NOT present — filter DISABLED, passing packs through unmodified (soup-mode / Enderscape-only / vanilla).");
            return packs;
        }

        List<PackResources> filtered = new ArrayList<>(packs.size());
        List<String> wrappedIds = new ArrayList<>();
        List<String> skippedBeyondIds = new ArrayList<>();
        List<String> unwrappedForeignIds = new ArrayList<>();
        for (PackResources pr : packs) {
            String id = pr.packId();

            // sanctioned bounds-override packs ship in addon jars, so they don't carry the the_beyond: prefix
            if (id != null && (id.startsWith(BEYOND_PACK_ID_PREFIX)
                    || SanctionedDimensionPacks.isSanctioned(id))) {
                filtered.add(pr);
                skippedBeyondIds.add(id);
                continue;
            }
            // already wrapped — don't wrap again
            if (pr instanceof EndDimensionFilteringPackResources) {
                filtered.add(pr);
                continue;
            }

            // only wrap packs that actually ship a contested End dimension file
            boolean hasDimension = pr.getResource(PackType.SERVER_DATA, END_DIMENSION) != null;
            boolean hasDimensionType = pr.getResource(PackType.SERVER_DATA, END_DIMENSION_TYPE) != null;
            if (hasDimension || hasDimensionType) {
                filtered.add(new EndDimensionFilteringPackResources(pr));
                wrappedIds.add(id + "[dim=" + hasDimension + ",dimType=" + hasDimensionType + "]");
            } else {
                filtered.add(pr);
                unwrappedForeignIds.add(id);
            }
        }

        Isleweaver.LOGGER.info(
                "[MultiPackFilter] beyond_terrain ACTIVE — wrapped {} foreign pack(s), passed {} Beyond pack(s) through, passed {} foreign pack(s) through unmodified.",
                wrappedIds.size(), skippedBeyondIds.size(), unwrappedForeignIds.size());
        if (!wrappedIds.isEmpty()) {
            Isleweaver.LOGGER.info("[MultiPackFilter]   wrapped (dim files STRIPPED): {}", wrappedIds);
        }
        if (!skippedBeyondIds.isEmpty()) {
            Isleweaver.LOGGER.info("[MultiPackFilter]   Beyond-owned (passed through): {}", skippedBeyondIds);
        }
        if (!unwrappedForeignIds.isEmpty()) {
            Isleweaver.LOGGER.info("[MultiPackFilter]   foreign, no End dim files (passed through): {}", unwrappedForeignIds);
        }
        return filtered;
    }
}

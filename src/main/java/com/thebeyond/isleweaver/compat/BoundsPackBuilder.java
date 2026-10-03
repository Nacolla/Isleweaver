package com.thebeyond.isleweaver.compat;

import com.thebeyond.isleweaver.Isleweaver;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.fml.ModList;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

// builds a bounds-override child pack from resourcepacks/<name>/ inside the addon jar
public final class BoundsPackBuilder {
    private BoundsPackBuilder() {}

    public static Pack build(String packName, String displayName) {
        Path path = ModList.get().getModFileById(Isleweaver.MODID)
                .getFile().findResource("resourcepacks/" + packName);
        boolean pathExists = path != null && Files.exists(path);
        boolean mcmetaExists = path != null && Files.exists(path.resolve("pack.mcmeta"));
        Isleweaver.LOGGER.debug(
                "[Isleweaver] BoundsPackBuilder: packName={} resolvedPath={} dirExists={} mcmetaExists={}",
                packName, path, pathExists, mcmetaExists);

        PackLocationInfo info = new PackLocationInfo(
                "mod/" + Isleweaver.MODID + ":" + packName,
                Component.literal(displayName),
                PackSource.DEFAULT, Optional.empty());
        Pack.ResourcesSupplier supplier = new PathPackResources.PathResourcesSupplier(path);
        // position/required don't matter for child packs. NeoForge forces them right after the parent anyway
        PackSelectionConfig selection = new PackSelectionConfig(false, Pack.Position.TOP, false);
        Pack result = Pack.readMetaAndCreate(info, supplier, PackType.SERVER_DATA, selection);
        Isleweaver.LOGGER.debug(
                "[Isleweaver] BoundsPackBuilder: Pack.readMetaAndCreate({}) -> {}",
                packName, result == null ? "NULL (pack.mcmeta missing or invalid)" : "OK");
        if (result != null) {
            // the End dimension filter would strip this pack, whose id lacks the mod/the_beyond prefix
            SanctionedDimensionPacks.sanction(result.getId());
        }
        return result;
    }
}

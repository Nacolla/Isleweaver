package com.thebeyond.isleweaver.mixin.unusualend;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thebeyond.api.worldgen.BeyondEndBiomeSourceApi;
import com.thebeyond.api.worldgen.BeyondTerrainState;
import com.thebeyond.isleweaver.compat.unusualend.ReefPoolProcessor;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

// only the reef islets carry water among Unusual End's templates, so every template it pastes leaves pools whole
@Pseudo
@Mixin(targets = "net.sweety.unusualend.world.features.StructureFeature", remap = false)
public abstract class ReefStructureFeatureMixin {
    @WrapOperation(
        method = "place",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;placeInWorld(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructurePlaceSettings;Lnet/minecraft/util/RandomSource;I)Z"),
        require = 0
    )
    private boolean isleweaver$guardReefPools(
            StructureTemplate template, ServerLevelAccessor level, BlockPos offset, BlockPos pos,
            StructurePlaceSettings settings, RandomSource random, int flags, Operation<Boolean> op) {
        if (BeyondTerrainState.isActive()
                && level.getLevel().getChunkSource().getGenerator().getBiomeSource() instanceof BeyondEndBiomeSourceApi) {
            settings.addProcessor(ReefPoolProcessor.engage());
        }
        return op.call(template, level, offset, pos, settings, random, flags);
    }
}

package com.thebeyond.isleweaver.compat.unusualend;

import com.mojang.serialization.MapCodec;
import com.thebeyond.isleweaver.Isleweaver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// keeps what is already there next to water: a reef piece pasted over a pool's rim lets the pool pour down the void
public final class ReefPoolProcessor extends StructureProcessor {
    private static final ReefPoolProcessor INSTANCE = new ReefPoolProcessor();
    private static final MapCodec<ReefPoolProcessor> CODEC = MapCodec.unit(INSTANCE);
    private static final DeferredRegister<StructureProcessorType<?>> TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PROCESSOR, Isleweaver.MODID);
    private static final DeferredHolder<StructureProcessorType<?>, StructureProcessorType<ReefPoolProcessor>> TYPE =
            TYPES.register("reef_pool", () -> () -> CODEC);
    private static boolean engaged;

    private ReefPoolProcessor() {}

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }

    public static ReefPoolProcessor engage() {
        if (!engaged) {
            engaged = true;
            Isleweaver.LOGGER.debug("[Isleweaver] Reef pool guard engaged");
        }
        return INSTANCE;
    }

    @Override
    public StructureTemplate.StructureBlockInfo processBlock(
            LevelReader level, BlockPos offset, BlockPos pos,
            StructureTemplate.StructureBlockInfo blockInfo, StructureTemplate.StructureBlockInfo relativeBlockInfo,
            StructurePlaceSettings settings) {
        BlockPos at = relativeBlockInfo.pos();
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        if (wet(level, probe.set(at))) return null;
        for (Direction side : Direction.values()) {
            if (wet(level, probe.setWithOffset(at, side))) return null;
        }
        return relativeBlockInfo;
    }

    // a piece may reach past the chunks worldgen can read
    private static boolean wet(LevelReader level, BlockPos pos) {
        return level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4) && level.getFluidState(pos).is(FluidTags.WATER);
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return TYPE.get();
    }
}

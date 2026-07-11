package com.thebeyond.isleweaver.mixin.stellarity;

import com.google.common.collect.ImmutableList;
import com.thebeyond.api.worldgen.BeyondTerrainState;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.SpikeFeature;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

// swaps vanilla's seed-random spike ring for Stellarity's fixed 10-spike layout; this list mirrors ring.json, keep them in sync
@Mixin(SpikeFeature.class)
public abstract class SpikeFeatureStellarityCompatMixin {

    private static final List<SpikeFeature.EndSpike> ISLEWEAVER$STELLARITY_RING_SPIKES = ImmutableList.of(
            new SpikeFeature.EndSpike( 63,   0, 5, 100, false),
            new SpikeFeature.EndSpike( 50,  36, 4, 105, false),
            new SpikeFeature.EndSpike( 18,  59, 4,  94, false),
            new SpikeFeature.EndSpike(-19,  59, 5, 106, false),
            new SpikeFeature.EndSpike(-51,  36, 4, 105, false),
            new SpikeFeature.EndSpike(-63,   0, 5,  93, false),
            new SpikeFeature.EndSpike(-51, -39, 6, 100, false),
            new SpikeFeature.EndSpike(-19, -60, 6,  96, false),
            new SpikeFeature.EndSpike( 18, -60, 5,  87, false),
            new SpikeFeature.EndSpike( 50, -39, 8,  95, false)
    );

    @Inject(method = "getSpikesForLevel", at = @At("HEAD"), cancellable = true)
    private static void isleweaver$substituteStellarityRingSpikes(
            WorldGenLevel level, CallbackInfoReturnable<List<SpikeFeature.EndSpike>> cir) {
        if (!ModList.get().isLoaded("stellarity")) {
            return;
        }
        if (!BeyondTerrainState.isActive()) {
            return;
        }
        cir.setReturnValue(ISLEWEAVER$STELLARITY_RING_SPIKES);
    }
}

package com.thebeyond.isleweaver.mixin.core;

import com.thebeyond.api.worldgen.BeyondEndBiomeSourceApi;
import com.thebeyond.isleweaver.Isleweaver;
import com.thebeyond.isleweaver.compat.SurfaceRuleMerger;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.SurfaceRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// re-asserts Beyond's End surface merge after mods that prepend their own rules at createLevels TAIL
@Mixin(value = MinecraftServer.class, priority = 1500)
public abstract class EndSurfaceRuleReassertMixin {

    @Shadow
    public abstract RegistryAccess.Frozen registryAccess();

    @Inject(method = "createLevels", at = @At("TAIL"))
    private void isleweaver$reassertBeyondEndSurfaceRule(ChunkProgressListener listener, CallbackInfo ci) {
        SurfaceRules.RuleSource beyondRule = SurfaceRuleMerger.beyondOwnedEndRule;
        if (beyondRule == null) return;

        Registry<LevelStem> dimensions = registryAccess().registryOrThrow(Registries.LEVEL_STEM);
        LevelStem endStem = dimensions.get(LevelStem.END);
        if (endStem == null) return;
        if (!(endStem.generator().getBiomeSource() instanceof BeyondEndBiomeSourceApi)) {
            // A wrapper installed after the merge hides the Beyond source, and foreign End globals then reach its islands
            Isleweaver.LOGGER.warn("[Isleweaver] The End biome source became {} after the surface merge,"
                    + " End surface rules not re-asserted", endStem.generator().getBiomeSource().getClass().getName());
            return;
        }
        if (!(endStem.generator() instanceof NoiseBasedChunkGenerator noiseGen)) return;

        NoiseGeneratorSettings settings = noiseGen.generatorSettings().value();
        NoiseGeneratorSettingsAccessor accessor = (NoiseGeneratorSettingsAccessor) (Object) settings;
        SurfaceRules.RuleSource active = accessor.isleweaver$getSurfaceRule();
        if (active == beyondRule) return;

        // foreign per-biome clauses keep their skin, the globals that would re-skin Beyond islands go
        java.util.List<SurfaceRules.RuleSource> foreignBiome =
                com.thebeyond.isleweaver.compat.SurfaceRuleOrdering.foreignBiomeClausesNotIn(active, beyondRule, registryAccess());
        SurfaceRules.RuleSource result;
        if (foreignBiome.isEmpty()) {
            result = beyondRule;
        } else {
            SurfaceRules.RuleSource[] arr = new SurfaceRules.RuleSource[foreignBiome.size() + 1];
            for (int i = 0; i < foreignBiome.size(); i++) arr[i] = foreignBiome.get(i);
            arr[foreignBiome.size()] = beyondRule;   // foreign per-biome clauses first (win in-biome), Beyond's merge as base
            result = SurfaceRules.sequence(arr);
        }
        accessor.isleweaver$setSurfaceRule(result);
        Isleweaver.LOGGER.info(
                "[Isleweaver] Beyond owns minecraft:the_end, re-asserted merged End surface rule at createLevels TAIL; "
                        + "preserved {} foreign biome-guarded surface clause(s), dropped foreign unconditional globals.",
                foreignBiome.size());
    }
}

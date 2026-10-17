package com.thebeyond.isleweaver.mixin.core;

import com.thebeyond.api.worldgen.BeyondEndBiomeSourceApi;
import com.thebeyond.isleweaver.Isleweaver;
import com.thebeyond.isleweaver.compat.NativeBodySampler;
import com.thebeyond.isleweaver.compat.SurfaceRuleMerger;
import com.thebeyond.isleweaver.compat.enderscape.CorruptBarrensFill;
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
        long seed = ((MinecraftServer) (Object) this).getWorldData().worldGenOptions().seed();
        java.util.Set<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>> pool =
                endStem.generator().getBiomeSource().possibleBiomes();

        java.util.List<SurfaceRules.RuleSource> rules = active == beyondRule ? new java.util.ArrayList<>()
                : new java.util.ArrayList<>(com.thebeyond.isleweaver.compat.SurfaceRuleOrdering.foreignBiomeClausesNotIn(
                        active, beyondRule, registryAccess()));
        int foreignCount = rules.size();
        rules.add(beyondRule);
        // Beyond's rule ends without a global filler, so fills placed last reach only the blocks no rule claims
        java.util.List<SurfaceRules.RuleSource> fills = new java.util.ArrayList<>();
        SurfaceRules.RuleSource corruptFill = CorruptBarrensFill.rule(registryAccess());
        if (corruptFill != null) fills.add(corruptFill);
        try {
            fills.addAll(NativeBodySampler.fills(registryAccess(), pool,
                    SurfaceRules.sequence(rules.toArray(new SurfaceRules.RuleSource[0])), seed));
        } catch (Throwable t) {
            Isleweaver.LOGGER.warn("[Isleweaver] Native body sampling failed, no sampled fills: {}", t.toString());
        }
        rules.addAll(fills);
        if (rules.size() == 1 && active == beyondRule) return;
        accessor.isleweaver$setSurfaceRule(rules.size() == 1 ? beyondRule
                : SurfaceRules.sequence(rules.toArray(new SurfaceRules.RuleSource[0])));
        Isleweaver.LOGGER.info(
                "[Isleweaver] Beyond owns minecraft:the_end, re-asserted merged End surface rule at createLevels TAIL; "
                        + "preserved {} foreign biome-guarded surface clause(s), dropped foreign unconditional globals, "
                        + "{} body fill(s).",
                foreignCount, fills.size());
    }
}

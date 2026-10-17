package com.thebeyond.isleweaver.compat;

import com.mojang.serialization.MapCodec;
import com.thebeyond.isleweaver.Isleweaver;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

/** Paints an island's core with what the wrapped rule gives at the deepest layer of its own shell, top or bottom. */
public record LiftedBodyRule(SurfaceRules.RuleSource rule) implements SurfaceRules.RuleSource {
    private static final int MAX_SHELL = 64;
    static final MapCodec<LiftedBodyRule> MAP_CODEC =
            SurfaceRules.RuleSource.CODEC.fieldOf("rule").xmap(LiftedBodyRule::new, LiftedBodyRule::rule);
    private static final KeyDispatchDataCodec<LiftedBodyRule> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);
    private static final DeferredRegister<MapCodec<? extends SurfaceRules.RuleSource>> RULES =
            DeferredRegister.create(Registries.MATERIAL_RULE, Isleweaver.MODID);

    static {
        RULES.register("lifted_body", () -> MAP_CODEC);
    }

    public static void register(IEventBus modBus) {
        RULES.register(modBus);
    }

    @Override
    public KeyDispatchDataCodec<? extends SurfaceRules.RuleSource> codec() {
        return CODEC;
    }

    @Override
    public SurfaceRules.SurfaceRule apply(SurfaceRules.Context context) {
        return new Lifted(context, this.rule.apply(context));
    }

    private static final class Lifted implements SurfaceRules.SurfaceRule {
        private final SurfaceRules.Context context;
        private final SurfaceRules.SurfaceRule inner;
        private int columnX = Integer.MIN_VALUE;
        private int columnZ = Integer.MIN_VALUE;
        private int floorShell;
        private int ceilingShell;

        Lifted(SurfaceRules.Context context, SurfaceRules.SurfaceRule inner) {
            this.context = context;
            this.inner = inner;
        }

        @Override
        public @Nullable BlockState tryApply(int x, int y, int z) {
            if (x != this.columnX || z != this.columnZ) {
                this.columnX = x;
                this.columnZ = z;
                this.floorShell = shellDepth(true, x, y, z);
                this.ceilingShell = shellDepth(false, x, y, z);
            }
            // only the core below both shells, a block the mod left open inside its shell stays as the mod made it
            if (this.context.stoneDepthAbove <= this.floorShell || this.context.stoneDepthBelow <= this.ceilingShell) {
                return null;
            }
            if (this.floorShell > 0) {
                return at(true, this.floorShell, x, y, z);
            }
            return this.ceilingShell > 0 ? at(false, this.ceilingShell, x, y, z) : null;
        }

        private int shellDepth(boolean floor, int x, int y, int z) {
            int deepest = 0;
            for (int depth = 1; depth <= MAX_SHELL; depth++) {
                if (at(floor, depth, x, y, z) != null) {
                    deepest = depth;
                } else if (deepest > 0 || depth >= 16) {
                    break;
                }
            }
            return deepest;
        }

        // conditions cache by lastUpdateY, so it moves on before and after the held depth
        private @Nullable BlockState at(boolean floor, int depth, int x, int y, int z) {
            int above = this.context.stoneDepthAbove;
            int below = this.context.stoneDepthBelow;
            if (floor) {
                this.context.stoneDepthAbove = depth;
            } else {
                this.context.stoneDepthBelow = depth;
            }
            this.context.lastUpdateY++;
            try {
                return this.inner.tryApply(x, y, z);
            } finally {
                this.context.stoneDepthAbove = above;
                this.context.stoneDepthBelow = below;
                this.context.lastUpdateY++;
            }
        }
    }
}

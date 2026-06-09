package com.thebeyond.isleweaver.compat;

import com.thebeyond.isleweaver.Isleweaver;
import net.minecraft.world.level.levelgen.SurfaceRules;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

// sinks unconditional terminators to the end of a sequence (first non-null wins) so they
// don't shadow biome-guarded rules that come after them
public final class SurfaceRuleOrdering {
    private SurfaceRuleOrdering() {}

    // true when r always yields a block; a conditional TestRuleSource can return null, so it's never terminal
    private static boolean isEffectivelyTerminal(SurfaceRules.RuleSource r) {
        if (r == null) return false;
        String simple = r.getClass().getSimpleName();
        if ("BlockRuleSource".equals(simple)) return true;
        if ("SequenceRuleSource".equals(simple)) {
            try {
                @SuppressWarnings("unchecked")
                List<SurfaceRules.RuleSource> es =
                        (List<SurfaceRules.RuleSource>) r.getClass().getMethod("sequence").invoke(r);
                return es != null && !es.isEmpty() && isEffectivelyTerminal(es.get(es.size() - 1));
            } catch (Throwable t) {
                return false;
            }
        }
        return false;
    }

    public static SurfaceRules.RuleSource bubbleTerminatorsLast(SurfaceRules.RuleSource rule) {
        if (rule == null) return null;
        if (!"SequenceRuleSource".equals(rule.getClass().getSimpleName())) return rule;
        try {
            Method seq = rule.getClass().getMethod("sequence");
            @SuppressWarnings("unchecked")
            List<SurfaceRules.RuleSource> entries = (List<SurfaceRules.RuleSource>) seq.invoke(rule);
            if (entries == null || entries.size() < 2) return rule;

            // recurse first so terminators bubble up at every nesting level
            List<SurfaceRules.RuleSource> normalized = new ArrayList<>(entries.size());
            for (SurfaceRules.RuleSource entry : entries) {
                normalized.add(bubbleTerminatorsLast(entry));
            }

            List<SurfaceRules.RuleSource> conditional = new ArrayList<>(normalized.size());
            List<SurfaceRules.RuleSource> filler = new ArrayList<>(2);
            for (SurfaceRules.RuleSource entry : normalized) {
                if (isEffectivelyTerminal(entry)) {
                    filler.add(entry);
                } else {
                    conditional.add(entry);
                }
            }
            if (filler.isEmpty() || conditional.isEmpty()) {
                return SurfaceRules.sequence(normalized.toArray(new SurfaceRules.RuleSource[0]));
            }

            List<SurfaceRules.RuleSource> reordered = new ArrayList<>(normalized.size());
            reordered.addAll(conditional);
            reordered.addAll(filler);
            return SurfaceRules.sequence(reordered.toArray(new SurfaceRules.RuleSource[0]));
        } catch (Throwable t) {
            Isleweaver.LOGGER.debug("[Isleweaver] bubbleTerminatorsLast skipped: {}", t.toString());
            return rule;
        }
    }
}

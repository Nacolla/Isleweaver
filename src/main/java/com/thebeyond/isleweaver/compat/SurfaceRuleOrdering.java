package com.thebeyond.isleweaver.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.thebeyond.isleweaver.Isleweaver;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.levelgen.SurfaceRules;

import java.util.ArrayList;
import java.util.List;

// The rule records are package-private, so these helpers work on the codec JSON (openTransientRules aside)
public final class SurfaceRuleOrdering {
    private SurfaceRuleOrdering() {}

    // codec JSON types carry the full minecraft: namespace as encoded
    private static final String TYPE = "type";
    private static final String SEQUENCE = "sequence";
    private static final String IF_TRUE = "if_true";
    private static final String THEN_RUN = "then_run";
    private static final String BIOME_IS = "biome_is";
    private static final String SEQUENCE_TYPE = "minecraft:sequence";
    private static final String CONDITION_TYPE = "minecraft:condition";
    private static final String BIOME_TYPE = "minecraft:biome";
    private static final String BLOCK_TYPE = "minecraft:block";
    private static final String LITHOSTITCHED_TRANSIENT =
            "dev.worldgen.lithostitched.impl.worldgen.surface.rule.TransientMergedRule";
    private static final Class<?> SEQUENCE_CLASS =
            SurfaceRules.sequence(SurfaceRules.bandlands(), SurfaceRules.bandlands()).getClass();

    // confined: only biome-confined clauses may pass a filler. The rule is unchanged if the codec fails
    public static SurfaceRules.RuleSource bubbleTerminatorsLast(SurfaceRules.RuleSource rule, boolean confineToBiomeGuarded) {
        if (rule == null) return null;
        JsonElement json = encode(rule, JsonOps.INSTANCE, "surface rule for terminator reorder");
        if (json == null) {
            Isleweaver.LOGGER.warn("[Isleweaver] surface-rule codec introspection unavailable, terminator reordering "
                    + "skipped; an unconditional filler may shadow later biome-guarded clauses");
            return rule;
        }
        JsonElement reordered = bubbleTerminatorsLastJson(json, confineToBiomeGuarded);
        if (reordered == json) return rule;   // nothing moved. Keep the original instance, skip a no-op re-decode
        SurfaceRules.RuleSource decoded = decode(reordered, JsonOps.INSTANCE, "reordered surface rule");
        return decoded != null ? decoded : rule;
    }

    // recurses only through sequence arrays and returns the same element when nothing changed
    private static JsonElement bubbleTerminatorsLastJson(JsonElement el, boolean confineToBiomeGuarded) {
        if (el == null || !el.isJsonObject()) return el;
        JsonObject o = el.getAsJsonObject();
        if (!SEQUENCE_TYPE.equals(typeOf(o))) return el;
        JsonArray seq = arrayMember(o, SEQUENCE);
        if (seq == null || seq.size() < 2) return el;

        JsonArray normalized = new JsonArray();
        boolean childChanged = false;
        for (JsonElement entry : seq) {
            JsonElement n = bubbleTerminatorsLastJson(entry, confineToBiomeGuarded);
            if (n != entry) childChanged = true;
            normalized.add(n);
        }

        // in confined mode a global conditional is a barrier, or a promoted clause would re-skin every biome
        JsonArray result = new JsonArray();
        JsonArray segPromotable = new JsonArray();
        JsonArray segFiller = new JsonArray();
        boolean segHasFiller = false;
        boolean moved = false;
        for (JsonElement entry : normalized) {
            boolean terminal = isEffectivelyTerminalJson(entry);
            if (confineToBiomeGuarded && !terminal && !isBiomeConfined(entry)) {
                result.addAll(segPromotable);
                result.addAll(segFiller);
                result.add(entry);
                segPromotable = new JsonArray();
                segFiller = new JsonArray();
                segHasFiller = false;
            } else if (terminal) {
                segFiller.add(entry);
                segHasFiller = true;
            } else {
                segPromotable.add(entry);
                if (segHasFiller) moved = true;
            }
        }
        result.addAll(segPromotable);
        result.addAll(segFiller);
        if (!childChanged && !moved) return el;

        JsonObject out = new JsonObject();
        out.addProperty(TYPE, SEQUENCE_TYPE);
        out.add(SEQUENCE, result);
        return out;
    }

    // a biome test, or a condition or sequence whose rules are all biome-confined, anything else matches globally
    private static boolean isBiomeConfined(JsonElement el) {
        if (el == null || !el.isJsonObject()) return false;
        JsonObject o = el.getAsJsonObject();
        if (isBiomeGuarded(o)) return true;
        if (CONDITION_TYPE.equals(typeOf(o))) return isBiomeConfined(o.get(THEN_RUN));
        if (SEQUENCE_TYPE.equals(typeOf(o))) {
            JsonArray seq = arrayMember(o, SEQUENCE);
            if (seq == null || seq.size() == 0) return false;
            for (JsonElement e : seq) {
                if (!isBiomeConfined(e)) return false;
            }
            return true;
        }
        return false;
    }

    // A condition (TestRuleSource) can fall through and yield nothing, so it is never terminal.
    private static boolean isEffectivelyTerminalJson(JsonElement el) {
        if (el == null || !el.isJsonObject()) return false;
        JsonObject o = el.getAsJsonObject();
        String type = typeOf(o);
        if (BLOCK_TYPE.equals(type)) return true;
        if (SEQUENCE_TYPE.equals(type)) {
            JsonArray seq = arrayMember(o, SEQUENCE);
            if (seq == null || seq.size() == 0) return false;
            return isEffectivelyTerminalJson(seq.get(seq.size() - 1));
        }
        return false;
    }

    // biome-guarded clauses in active that base lacks. Globals are dropped and an encode failure warns
    public static List<SurfaceRules.RuleSource> foreignBiomeClausesNotIn(
            SurfaceRules.RuleSource active, SurfaceRules.RuleSource base, RegistryAccess registryAccess) {
        List<SurfaceRules.RuleSource> out = new ArrayList<>();
        DynamicOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registryAccess);

        JsonElement activeJson = encode(openTransientRules(active), ops, "active End surface rule");
        if (activeJson == null) {
            Isleweaver.LOGGER.warn("[Isleweaver] surface-rule codec introspection unavailable, could not encode the "
                    + "active End rule; foreign biome clauses NOT spliced this run (foreign biomes keep no skin block)");
            return out;
        }
        JsonElement baseJson = encode(base, ops, "Beyond merged End surface rule");
        List<JsonElement> baseNodes = new ArrayList<>();
        if (baseJson != null) collectNodes(baseJson, baseNodes);

        List<JsonObject> biomeClauses = new ArrayList<>();
        collectBiomeClauses(activeJson, biomeClauses);

        for (JsonObject clause : biomeClauses) {
            if (containsNode(baseNodes, clause)) continue;
            SurfaceRules.RuleSource decoded = decode(clause, ops, "foreign biome clause " + biomeIdsOf(clause));
            if (decoded == null) continue;
            out.add(decoded);
            Isleweaver.LOGGER.debug("[Isleweaver] foreign End biome surface clause PRESERVED for {}", biomeIdsOf(clause));
        }
        Isleweaver.LOGGER.debug("[Isleweaver] surface-rule codec scan: {} biome-guarded clause(s) found in active, "
                + "kept {} not already in Beyond's merge", biomeClauses.size(), out.size());
        return out;
    }

    // The one reflective helper: Lithostitched's TransientMergedRule encodes only its original, so its additions are opened here
    static SurfaceRules.RuleSource openTransientRules(SurfaceRules.RuleSource rule) {
        List<SurfaceRules.RuleSource> parts = new ArrayList<>();
        try {
            if (!openInto(rule, parts) || parts.isEmpty()) return rule;
        } catch (ReflectiveOperationException | RuntimeException e) {
            Isleweaver.LOGGER.warn("[Isleweaver] could not open Lithostitched surface additions,"
                    + " their biome clauses are not re-asserted", e);
            return rule;
        }
        Isleweaver.LOGGER.info("[Isleweaver] opened Lithostitched surface additions for the End scan ({} rule(s))", parts.size());
        return SurfaceRules.sequence(parts.toArray(new SurfaceRules.RuleSource[0]));
    }

    private static boolean openInto(SurfaceRules.RuleSource rule, List<SurfaceRules.RuleSource> out)
            throws ReflectiveOperationException {
        boolean transientRule = rule.getClass().getName().equals(LITHOSTITCHED_TRANSIENT);
        if (!transientRule && rule.getClass() != SEQUENCE_CLASS) {
            out.add(rule);
            return false;
        }
        java.lang.reflect.Method parts = rule.getClass().getDeclaredMethod(transientRule ? "rules" : "sequence");
        parts.setAccessible(true);
        boolean opened = transientRule;
        for (Object part : (List<?>) parts.invoke(rule)) opened |= openInto((SurfaceRules.RuleSource) part, out);
        return opened;
    }

    // the positive biome-guarded clauses of a foreign rule, empty if it cannot encode
    public static List<SurfaceRules.RuleSource> biomeGuardedClausesOf(
            SurfaceRules.RuleSource rule, RegistryAccess registryAccess) {
        List<SurfaceRules.RuleSource> out = new ArrayList<>();
        if (rule == null) return out;
        DynamicOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registryAccess);
        JsonElement json = encode(rule, ops, "foreign End rule (biome-clause gate)");
        if (json == null) {
            Isleweaver.LOGGER.warn("[Isleweaver] surface-rule codec introspection unavailable, a foreign End rule could "
                    + "not be encoded; DROPPED wholesale so no foreign global can re-skin Beyond (its foreign biomes keep no skin)");
            return out;
        }
        List<JsonObject> clauses = new ArrayList<>();
        collectBiomeClauses(json, clauses);
        for (JsonObject clause : clauses) {
            SurfaceRules.RuleSource decoded = decode(clause, ops, "foreign biome clause " + biomeIdsOf(clause));
            if (decoded != null) out.add(decoded);
        }
        if (!clauses.isEmpty()) {
            Isleweaver.LOGGER.debug("[Isleweaver] foreign End rule gated to {} positive-biome clause(s); globals dropped",
                    out.size());
        }
        return out;
    }

    // A matched isBiome node is a leaf. Its then_run is not descended, so nested sub-clauses aren't harvested.
    private static void collectBiomeClauses(JsonElement el, List<JsonObject> out) {
        if (el == null || !el.isJsonObject()) return;
        JsonObject o = el.getAsJsonObject();
        if (isBiomeGuarded(o)) {
            out.add(o);
            return;
        }
        if (SEQUENCE_TYPE.equals(typeOf(o))) {
            JsonArray seq = arrayMember(o, SEQUENCE);
            if (seq != null) {
                for (JsonElement e : seq) collectBiomeClauses(e, out);
            }
        } else if (CONDITION_TYPE.equals(typeOf(o))) {
            List<JsonObject> inner = new ArrayList<>();
            collectBiomeClauses(o.get(THEN_RUN), inner);
            for (JsonObject clause : inner) {
                JsonObject wrapped = new JsonObject();
                wrapped.addProperty(TYPE, CONDITION_TYPE);
                wrapped.add(IF_TRUE, o.get(IF_TRUE));
                wrapped.add(THEN_RUN, clause);
                out.add(wrapped);
            }
        }
    }

    private static void collectNodes(JsonElement el, List<JsonElement> out) {
        if (el == null || !el.isJsonObject()) return;
        JsonObject o = el.getAsJsonObject();
        out.add(o);
        if (SEQUENCE_TYPE.equals(typeOf(o))) {
            JsonArray seq = arrayMember(o, SEQUENCE);
            if (seq != null) {
                for (JsonElement e : seq) collectNodes(e, out);
            }
        }
    }

    // {type:condition, if_true:{type:biome, ...}}
    private static boolean isBiomeGuarded(JsonObject o) {
        if (!CONDITION_TYPE.equals(typeOf(o))) return false;
        JsonElement ifTrue = o.get(IF_TRUE);
        return ifTrue != null && ifTrue.isJsonObject() && BIOME_TYPE.equals(typeOf(ifTrue.getAsJsonObject()));
    }

    private static String biomeIdsOf(JsonObject clause) {
        JsonElement ifTrue = clause.get(IF_TRUE);
        if (ifTrue != null && ifTrue.isJsonObject()) {
            JsonElement biomes = ifTrue.getAsJsonObject().get(BIOME_IS);
            if (biomes != null) return biomes.toString();
        }
        JsonElement thenRun = clause.get(THEN_RUN);
        if (!isBiomeGuarded(clause) && thenRun != null && thenRun.isJsonObject()) return biomeIdsOf(thenRun.getAsJsonObject());
        return "<unknown biome>";
    }

    private static String typeOf(JsonObject o) {
        JsonElement t = o.get(TYPE);
        return t != null && t.isJsonPrimitive() ? t.getAsString() : null;
    }

    private static JsonArray arrayMember(JsonObject o, String name) {
        JsonElement e = o.get(name);
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : null;
    }

    // Gson JsonElement equality is structural (key-order independent)
    private static boolean containsNode(List<JsonElement> nodes, JsonElement target) {
        for (JsonElement n : nodes) {
            if (n.equals(target)) return true;
        }
        return false;
    }

    private static JsonElement encode(SurfaceRules.RuleSource rule, DynamicOps<JsonElement> ops, String what) {
        DataResult<JsonElement> r = SurfaceRules.RuleSource.CODEC.encodeStart(ops, rule);
        JsonElement el = r.result().orElse(null);
        if (el == null) {
            Isleweaver.LOGGER.warn("[Isleweaver] failed to encode {} for surface-rule introspection: {}", what,
                    r.error().map(Object::toString).orElse("unknown error"));
        }
        return el;
    }

    private static SurfaceRules.RuleSource decode(JsonElement json, DynamicOps<JsonElement> ops, String what) {
        DataResult<SurfaceRules.RuleSource> r = SurfaceRules.RuleSource.CODEC.parse(ops, json);
        SurfaceRules.RuleSource rule = r.result().orElse(null);
        if (rule == null) {
            Isleweaver.LOGGER.warn("[Isleweaver] failed to decode {} during surface-rule splice: {}", what,
                    r.error().map(Object::toString).orElse("unknown error"));
        }
        return rule;
    }
}

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

// SequenceRuleSource/TestRuleSource are package-private, so these helpers round-trip through the public RuleSource codec JSON instead of reflecting into the records directly
public final class SurfaceRuleOrdering {
    private SurfaceRuleOrdering() {}

    // codec JSON types carry the full minecraft: namespace as encoded
    private static final String TYPE = "type";
    private static final String SEQUENCE = "sequence";
    private static final String IF_TRUE = "if_true";
    private static final String BIOME_IS = "biome_is";
    private static final String SEQUENCE_TYPE = "minecraft:sequence";
    private static final String CONDITION_TYPE = "minecraft:condition";
    private static final String BIOME_TYPE = "minecraft:biome";
    private static final String BLOCK_TYPE = "minecraft:block";

    // confineToBiomeGuarded true: only biome-confined clauses may promote past a filler (caller isn't isBiome-wrapped); false: any clause promotes. Fail-soft: rule unchanged if the codec round-trip fails
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

    // recurses only through sequence[] arrays (a condition node is opaque). Returns the same element by reference when nothing changed so the caller can detect a no-op
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

        // in confined mode a non-biome-confined conditional is a barrier fillers can't cross: promoting a clause past it would let a global conditional match ahead, re-skinning every biome incl. Beyond's fall-through
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

    // true only for a biome condition, or a sequence whose every entry is itself biome-confined. Anything else (block, non-biome condition, or a sequence containing one) matches globally
    private static boolean isBiomeConfined(JsonElement el) {
        if (el == null || !el.isJsonObject()) return false;
        JsonObject o = el.getAsJsonObject();
        if (isBiomeGuarded(o)) return true;
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

    // biome-guarded clauses in `active` absent from `base`: per-biome surfaces a foreign mod injected at runtime. Drops globals, fail-soft (WARN, caller keeps the Beyond rule) if encoding fails
    public static List<SurfaceRules.RuleSource> foreignBiomeClausesNotIn(
            SurfaceRules.RuleSource active, SurfaceRules.RuleSource base, RegistryAccess registryAccess) {
        List<SurfaceRules.RuleSource> out = new ArrayList<>();
        DynamicOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registryAccess);

        JsonElement activeJson = encode(active, ops, "active End surface rule");
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

    // positive biome-guarded clauses of a scraped foreign rule; globals and non-biome conditionals dropped so nothing bleeds onto unnamed biomes (unencodable rule returns empty, dropping it wholesale, WARN)
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

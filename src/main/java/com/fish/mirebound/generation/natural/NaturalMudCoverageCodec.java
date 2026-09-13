package com.fish.mirebound.generation.natural;

import com.fish.mirebound.mud.SinkingMedium;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Shared bounded schema for saved worlds and user presets. */
public final class NaturalMudCoverageCodec {
    private NaturalMudCoverageCodec() {}
    public static JsonObject encode(NaturalMudGenerationProfile profile) {
        JsonObject root = new JsonObject();
        JsonArray disabled = new JsonArray();
        profile.disabledDimensions().stream().sorted().forEach(disabled::add);
        root.add("disabled_dimensions", disabled);
        JsonObject biomes = new JsonObject();
        profile.coverageRules().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            var r = entry.getValue();
            JsonObject rule = new JsonObject();
            rule.addProperty("enabled", r.enabled());
            rule.addProperty("generation_chance", r.generationChance());
            rule.addProperty("replacement_layers", r.replacementLayers());
            rule.addProperty("coverage", r.coverage());
            rule.addProperty("coverage_variation", r.coverageVariation());
            rule.addProperty("depth", r.depth());
            rule.addProperty("deeper_near_trees", r.deeperNearTrees());
            rule.addProperty("tree_depth", r.treeDepth());
            rule.addProperty("tree_radius", r.treeRadius());
            rule.addProperty("depth_variation", r.depthVariation());
            rule.addProperty("mix_mode", r.mixMode().name());
            rule.addProperty("patch_size", r.patchSize());
            rule.addProperty("mix_variation", r.mixVariation());
            JsonObject weights = new JsonObject();
            for (SinkingMedium m : SinkingMedium.values()) if (r.weights().containsKey(m))
                weights.addProperty(m.serializedName(), r.weights().get(m));
            rule.add("weights", weights);
            biomes.add(entry.getKey(), rule);
        });
        root.add("biome_coverage", biomes);
        return root;
    }

    public static NaturalMudGenerationProfile decode(JsonObject root, NaturalMudGenerationProfile base) {
        Set<String> disabled = new LinkedHashSet<>(base.disabledDimensions());
        if (root.has("disabled_dimensions")) {
            disabled.clear();
            var array = root.getAsJsonArray("disabled_dimensions");
            if (array.size() > 4096) throw new IllegalArgumentException("Too many dimensions");
            for (var entry : array) disabled.add(id(entry.getAsString()));
        }
        Map<String, NaturalMudCoverageRule> rules = new HashMap<>(base.coverageRules());
        if (root.has("biome_coverage")) {
            rules.clear();
            var biomes = root.getAsJsonObject("biome_coverage");
            if (biomes.size() > 4096) throw new IllegalArgumentException("Too many biomes");
            for (var entry : biomes.entrySet()) {
                var r = entry.getValue().getAsJsonObject();
                var d = NaturalMudCoverageRule.defaults(false);
                EnumMap<SinkingMedium, Integer> weights = new EnumMap<>(SinkingMedium.class);
                if (r.has("weights")) {
                    var w = r.getAsJsonObject("weights");
                    for (SinkingMedium m : SinkingMedium.values())
                        if (w.has(m.serializedName())) weights.put(m, w.get(m.serializedName()).getAsInt());
                } else weights.putAll(d.weights());
                rules.put(id(entry.getKey()), new NaturalMudCoverageRule(
                        bool(r, "enabled", false), number(r, "coverage", d.coverage()),
                        number(r, "coverage_variation", d.coverageVariation()), number(r, "depth", d.depth()),
                        bool(r, "deeper_near_trees", d.deeperNearTrees()), number(r, "tree_depth", d.treeDepth()),
                        (int) number(r, "tree_radius", d.treeRadius()), number(r, "depth_variation", d.depthVariation()),
                        r.has("mix_mode") ? NaturalMudCoverageRule.MixMode.valueOf(r.get("mix_mode").getAsString()) : d.mixMode(),
                        (int) number(r, "patch_size", d.patchSize()), number(r, "mix_variation", d.mixVariation()), weights,
                        number(r, "generation_chance", 1), (int) number(r, "replacement_layers", 4)));
            }
        }
        return new NaturalMudGenerationProfile(base.rules(), rules, disabled);
    }
    private static String id(String value) {
        if (value.length() > 256 || ResourceLocation.tryParse(value) == null)
            throw new IllegalArgumentException("Invalid registry id");
        return value;
    }
    private static double number(JsonObject o, String key, double fallback) {
        return o.has(key) ? o.get(key).getAsDouble() : fallback;
    }
    private static boolean bool(JsonObject o, String key, boolean fallback) {
        return o.has(key) ? o.get(key).getAsBoolean() : fallback;
    }
}

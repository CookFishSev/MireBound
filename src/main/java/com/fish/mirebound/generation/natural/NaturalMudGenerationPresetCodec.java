package com.fish.mirebound.generation.natural;

import com.fish.mirebound.generation.natural.NaturalMudGenerationProfile.Rule;
import com.fish.mirebound.mud.SinkingMedium;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Human-readable JSON format for natural-generation presets. */
public final class NaturalMudGenerationPresetCodec {
    public static final String FORMAT = "mirebound_natural_generation";
    public static final int VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private NaturalMudGenerationPresetCodec() {
    }

    public static String encode(String name, NaturalMudGenerationProfile profile) {
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT);
        root.addProperty("version", VERSION);
        root.addProperty("name", name == null ? "" : name);
        JsonArray rules = new JsonArray();
        for (Rule rule : profile.rules()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("medium", rule.medium().serializedName());
            entry.addProperty("enabled", rule.enabled());
            entry.addProperty("chance_per_100000_chunks",
                    rule.chancePerHundredThousandChunks());
            addStrings(entry, "biome_selectors", rule.biomeSelectors());
            addStrings(entry, "deposit_forms",
                    rule.forms().stream().map(Enum::name).toList());
            entry.addProperty("minimum_radius", rule.minimumRadius());
            entry.addProperty("maximum_radius", rule.maximumRadius());
            entry.addProperty("minimum_depth", rule.minimumDepth());
            entry.addProperty("maximum_depth", rule.maximumDepth());
            entry.addProperty("full_height_top", rule.fullHeightTop());
            rules.add(entry);
        }
        root.add("rules", rules);
        return GSON.toJson(root);
    }

    public static Optional<NamedProfile> decode(
            String fallbackName, String serialized) {
        try {
            JsonElement parsed = JsonParser.parseString(serialized);
            if (!parsed.isJsonObject()) {
                return Optional.empty();
            }
            JsonObject root = parsed.getAsJsonObject();
            if (!FORMAT.equals(string(root, "format", ""))
                    || number(root, "version", -1) != VERSION
                    || !root.has("rules") || !root.get("rules").isJsonArray()) {
                return Optional.empty();
            }
            NaturalMudGenerationProfile defaults =
                    NaturalMudGenerationProfile.defaults();
            List<Rule> decoded = new ArrayList<>();
            for (JsonElement element : root.getAsJsonArray("rules")) {
                if (!element.isJsonObject()) {
                    return Optional.empty();
                }
                JsonObject entry = element.getAsJsonObject();
                SinkingMedium medium = medium(string(entry, "medium", ""));
                Rule baseline = medium == null ? null : defaults.rule(medium);
                if (baseline == null) {
                    return Optional.empty();
                }
                Set<String> selectors = strings(entry, "biome_selectors",
                        baseline.biomeSelectors());
                List<NaturalMudDepositForm> forms = forms(entry, "deposit_forms",
                        baseline.forms());
                decoded.add(new Rule(
                        medium,
                        bool(entry, "enabled", baseline.enabled()),
                        number(entry, "chance_per_100000_chunks",
                                baseline.chancePerHundredThousandChunks()),
                        selectors,
                        forms,
                        number(entry, "minimum_radius", baseline.minimumRadius()),
                        number(entry, "maximum_radius", baseline.maximumRadius()),
                        number(entry, "minimum_depth", baseline.minimumDepth()),
                        number(entry, "maximum_depth", baseline.maximumDepth()),
                        bool(entry, "full_height_top", baseline.fullHeightTop())));
            }
            if (decoded.isEmpty()) {
                return Optional.empty();
            }
            String name = string(root, "name", fallbackName);
            return Optional.of(new NamedProfile(
                    name.isBlank() ? fallbackName : name,
                    new NaturalMudGenerationProfile(decoded)));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static void addStrings(JsonObject object, String key,
            Iterable<String> values) {
        JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(value);
        }
        object.add(key, array);
    }

    private static Set<String> strings(JsonObject object, String key,
            Set<String> fallback) {
        if (!object.has(key) || !object.get(key).isJsonArray()) {
            return new LinkedHashSet<>(fallback);
        }
        Set<String> result = new LinkedHashSet<>();
        for (JsonElement value : object.getAsJsonArray(key)) {
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                String text = value.getAsString();
                if (!text.isBlank()) {
                    result.add(text);
                }
            }
        }
        return result;
    }

    private static List<NaturalMudDepositForm> forms(JsonObject object, String key,
            List<NaturalMudDepositForm> fallback) {
        if (!object.has(key) || !object.get(key).isJsonArray()) {
            return fallback;
        }
        List<NaturalMudDepositForm> result = new ArrayList<>();
        for (JsonElement value : object.getAsJsonArray(key)) {
            if (!value.isJsonPrimitive()) {
                continue;
            }
            try {
                NaturalMudDepositForm form = NaturalMudDepositForm.valueOf(
                        value.getAsString());
                if (!result.contains(form)) {
                    result.add(form);
                }
            } catch (IllegalArgumentException ignored) {
                return fallback;
            }
        }
        return result;
    }

    private static SinkingMedium medium(String value) {
        for (SinkingMedium candidate : SinkingMedium.values()) {
            if (candidate.serializedName().equals(value)) {
                return candidate;
            }
        }
        return null;
    }

    private static String string(JsonObject object, String key, String fallback) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive()
                ? value.getAsString() : fallback;
    }

    private static int number(JsonObject object, String key, int fallback) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive()) {
            return fallback;
        }
        return value.getAsInt();
    }

    private static boolean bool(JsonObject object, String key, boolean fallback) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive()) {
            return fallback;
        }
        return value.getAsBoolean();
    }

    public record NamedProfile(String name, NaturalMudGenerationProfile profile) {
    }
}

package com.fish.mirebound.generation.natural;

import com.fish.mirebound.mud.SinkingMedium;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

/** Immutable per-world natural generation profile. */
public final class NaturalMudGenerationProfile {
    public static final int PROBABILITY_SCALE = 100_000;
    public static final int MAXIMUM_CHANCE = 5_000;

    private final EnumMap<SinkingMedium, Rule> rules;

    public NaturalMudGenerationProfile(List<Rule> requested) {
        rules = new EnumMap<>(SinkingMedium.class);
        for (Rule rule : requested) {
            rules.put(rule.medium(), rule);
        }
    }

    public static NaturalMudGenerationProfile defaults() {
        return NaturalMudGenerationDefaults.create();
    }

    public List<Rule> rules() {
        return List.copyOf(rules.values());
    }

    public Rule rule(SinkingMedium medium) {
        return rules.get(medium);
    }

    public NaturalMudGenerationProfile withRule(Rule replacement) {
        List<Rule> copy = new ArrayList<>(rules.values());
        copy.removeIf(rule -> rule.medium() == replacement.medium());
        copy.add(replacement);
        return new NaturalMudGenerationProfile(copy);
    }

    public NaturalMudGenerationProfile withAllEnabled(boolean enabled) {
        return new NaturalMudGenerationProfile(rules.values().stream()
                .map(rule -> rule.withEnabled(enabled))
                .toList());
    }

    public NaturalMudGenerationProfile reset(SinkingMedium medium) {
        Rule defaults = defaults().rule(medium);
        return defaults == null ? this : withRule(defaults);
    }

    public record Rule(
            SinkingMedium medium,
            boolean enabled,
            int chancePerHundredThousandChunks,
            Set<String> biomeSelectors,
            List<NaturalMudDepositForm> forms,
            int minimumRadius,
            int maximumRadius,
            int minimumDepth,
            int maximumDepth,
            boolean fullHeightTop) {
        public Rule {
            Objects.requireNonNull(medium);
            chancePerHundredThousandChunks = Math.max(0,
                    Math.min(MAXIMUM_CHANCE, chancePerHundredThousandChunks));
            biomeSelectors = Set.copyOf(new LinkedHashSet<>(biomeSelectors));
            forms = List.copyOf(forms);
            minimumRadius = Math.max(2, Math.min(12, minimumRadius));
            maximumRadius = Math.max(minimumRadius, Math.min(12, maximumRadius));
            minimumDepth = Math.max(1, Math.min(6, minimumDepth));
            maximumDepth = Math.max(minimumDepth, Math.min(6, maximumDepth));
        }

        public Rule withEnabled(boolean value) {
            return new Rule(medium, value, chancePerHundredThousandChunks,
                    biomeSelectors, forms, minimumRadius, maximumRadius,
                    minimumDepth, maximumDepth, fullHeightTop);
        }

        public Rule withChance(int value) {
            return new Rule(medium, enabled, value, biomeSelectors, forms,
                    minimumRadius, maximumRadius, minimumDepth, maximumDepth,
                    fullHeightTop);
        }

        public Rule withBiomeSelectors(Set<String> value) {
            return new Rule(medium, enabled, chancePerHundredThousandChunks,
                    value, forms, minimumRadius, maximumRadius,
                    minimumDepth, maximumDepth, fullHeightTop);
        }

        public Rule withForms(List<NaturalMudDepositForm> value) {
            return new Rule(medium, enabled, chancePerHundredThousandChunks,
                    biomeSelectors, value, minimumRadius, maximumRadius,
                    minimumDepth, maximumDepth, fullHeightTop);
        }

        public Rule withRadiusRange(int minimum, int maximum) {
            return new Rule(medium, enabled, chancePerHundredThousandChunks,
                    biomeSelectors, forms, minimum, maximum,
                    minimumDepth, maximumDepth, fullHeightTop);
        }

        public double averageRadius() {
            return (minimumRadius + maximumRadius) * 0.5D;
        }

        public double radiusVariation() {
            return (maximumRadius - minimumRadius) * 0.5D;
        }

        public boolean matches(Holder<Biome> biome) {
            return matches(biome, null);
        }

        public boolean matches(
                Holder<Biome> biome, ResourceKey<Level> dimension) {
            if (!enabled || chancePerHundredThousandChunks <= 0
                    || biomeSelectors.isEmpty()) {
                return false;
            }
            for (String selector : biomeSelectors) {
                if (matchesSelector(biome, selector, dimension)) {
                    return true;
                }
            }
            return false;
        }
    }

    public static boolean matchesSelector(Holder<Biome> biome, String selector) {
        if (selector == null || selector.isBlank()) {
            return false;
        }
        boolean tag = selector.charAt(0) == '#';
        ResourceLocation id = ResourceLocation.tryParse(
                tag ? selector.substring(1) : selector);
        if (id == null) {
            return false;
        }
        if (tag) {
            return biome.is(TagKey.create(Registries.BIOME, id));
        }
        return biome.unwrapKey().map(ResourceKey::location)
                .filter(id::equals).isPresent();
    }

    public static boolean matchesSelector(
            Holder<Biome> biome, String selector,
            ResourceKey<Level> dimension) {
        return matchesSelector(biome, selector)
                || matchesDimensionSelector(selector, dimension);
    }

    static boolean matchesDimensionSelector(
            String selector, ResourceKey<Level> dimension) {
        if (dimension == null || selector == null) {
            return false;
        }
        return switch (selector) {
            case "#minecraft:is_overworld" -> dimension == Level.OVERWORLD;
            case "#minecraft:is_nether" -> dimension == Level.NETHER;
            case "#minecraft:is_end" -> dimension == Level.END;
            default -> false;
        };
    }

    public static Map<SinkingMedium, Rule> indexedDefaults() {
        EnumMap<SinkingMedium, Rule> indexed = new EnumMap<>(SinkingMedium.class);
        for (Rule rule : defaults().rules()) {
            indexed.put(rule.medium(), rule);
        }
        return Map.copyOf(indexed);
    }
}

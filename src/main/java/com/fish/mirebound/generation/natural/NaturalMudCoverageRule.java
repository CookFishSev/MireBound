package com.fish.mirebound.generation.natural;

import com.fish.mirebound.mud.SinkingMedium;
import java.util.EnumMap;
import java.util.Map;

/** One biome's continuous surface layer; independent of sparse deposit rarity. */
public record NaturalMudCoverageRule(boolean enabled, double coverage, double coverageVariation,
        double depth, boolean deeperNearTrees, double treeDepth, int treeRadius,
        double depthVariation, MixMode mixMode, int patchSize, double mixVariation,
        Map<SinkingMedium, Integer> weights, double generationChance, int replacementLayers) {
    public enum MixMode { PATCHES, RANDOM }

    public NaturalMudCoverageRule {
        generationChance = bounded(generationChance, 0, 1);
        replacementLayers = Math.clamp(replacementLayers, 1, 12);
        coverage = bounded(coverage, 0, 1);
        coverageVariation = bounded(coverageVariation, 0, 1);
        depth = bounded(depth, 1.0 / 16, 6);
        treeDepth = bounded(treeDepth, depth, 6);
        treeRadius = Math.clamp(treeRadius, 1, 8);
        depthVariation = bounded(depthVariation, 0, 3);
        patchSize = Math.clamp(patchSize, 4, 96);
        mixVariation = bounded(mixVariation, 0, 1);
        mixMode = mixMode == null ? MixMode.PATCHES : mixMode;
        EnumMap<SinkingMedium, Integer> clean = new EnumMap<>(SinkingMedium.class);
        if (weights != null) weights.forEach((medium, weight) -> {
            if (medium != null && weight != null && weight > 0)
                clean.put(medium, Math.min(1000, weight));
        });
        weights = Map.copyOf(clean);
    }

    public NaturalMudCoverageRule(boolean enabled, double coverage, double coverageVariation,
            double depth, boolean deeperNearTrees, double treeDepth, int treeRadius,
            double depthVariation, MixMode mixMode, int patchSize, double mixVariation,
            Map<SinkingMedium, Integer> weights) {
        this(enabled, coverage, coverageVariation, depth, deeperNearTrees, treeDepth, treeRadius,
                depthVariation, mixMode, patchSize, mixVariation, weights, 1, 4);
    }

    public static NaturalMudCoverageRule defaults(boolean enabled) {
        return new NaturalMudCoverageRule(enabled, .65, .16, .25, true, 2.5, 4,
                .2, MixMode.PATCHES, 24, .2, Map.of(SinkingMedium.MUD, 100), .8, 4);
    }

    public static Map<String, NaturalMudCoverageRule> defaultBiomes() {
        var river = new NaturalMudCoverageRule(true, .55, .12, .375, false, .375, 4,
                .2, MixMode.PATCHES, 24, .2, Map.of(SinkingMedium.SILT, 100), .12, 4);
        var ocean = new NaturalMudCoverageRule(true, .55, .12, .5, false, .5, 4,
                .25, MixMode.PATCHES, 32, .2, Map.of(SinkingMedium.SILT, 100), .08, 4);
        return Map.ofEntries(
                Map.entry("minecraft:swamp", defaults(true)),
                Map.entry("minecraft:river", river), Map.entry("minecraft:frozen_river", river),
                Map.entry("minecraft:ocean", ocean), Map.entry("minecraft:cold_ocean", ocean),
                Map.entry("minecraft:frozen_ocean", ocean), Map.entry("minecraft:lukewarm_ocean", ocean),
                Map.entry("minecraft:warm_ocean", ocean), Map.entry("minecraft:deep_ocean", ocean),
                Map.entry("minecraft:deep_cold_ocean", ocean), Map.entry("minecraft:deep_frozen_ocean", ocean),
                Map.entry("minecraft:deep_lukewarm_ocean", ocean));
    }

    public NaturalMudCoverageRule withEnabled(boolean value) {
        return new NaturalMudCoverageRule(value, coverage, coverageVariation, depth,
                deeperNearTrees, treeDepth, treeRadius, depthVariation, mixMode,
                patchSize, mixVariation, weights, generationChance, replacementLayers);
    }

    public NaturalMudCoverageRule withWeight(SinkingMedium medium, int weight) {
        EnumMap<SinkingMedium, Integer> copy = new EnumMap<>(SinkingMedium.class);
        copy.putAll(weights);
        copy.put(medium, weight);
        return new NaturalMudCoverageRule(enabled, coverage, coverageVariation, depth,
                deeperNearTrees, treeDepth, treeRadius, depthVariation, mixMode,
                patchSize, mixVariation, copy, generationChance, replacementLayers);
    }

    private static double bounded(double value, double min, double max) {
        return Double.isFinite(value) ? Math.clamp(value, min, max) : min;
    }
}

package com.fish.mirebound.generation.natural;

import static org.junit.jupiter.api.Assertions.*;
import com.fish.mirebound.mud.SinkingMedium;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class NaturalMudCoverageTest {
    private static NaturalMudCoverageRule mixed(NaturalMudCoverageRule.MixMode mode) {
        return new NaturalMudCoverageRule(true, .85, .2, .25, true, 2.5, 4,
                0, mode, 24, 0, Map.of(SinkingMedium.MUD, 3, SinkingMedium.PEAT_BOG, 1));
    }

    @Test void defaultCoverageOnlyEnablesSwampMudAndRiverOrOceanSilt() {
        var profile = NaturalMudGenerationProfile.defaults();
        assertEquals(Set.of("minecraft:swamp", "minecraft:river", "minecraft:frozen_river",
                "minecraft:ocean", "minecraft:cold_ocean", "minecraft:frozen_ocean",
                "minecraft:lukewarm_ocean", "minecraft:warm_ocean",
                "minecraft:deep_ocean", "minecraft:deep_cold_ocean", "minecraft:deep_frozen_ocean",
                "minecraft:deep_lukewarm_ocean"), profile.coverageRules().keySet());
        assertTrue(profile.coverageRule("minecraft:swamp").enabled());
        assertEquals(Map.of(SinkingMedium.MUD, 100), profile.coverageRule("minecraft:swamp").weights());
        profile.coverageRules().forEach((biome, rule) -> {
            assertTrue(rule.enabled());
            assertTrue(rule.coverage() < 1);
            if (!biome.equals("minecraft:swamp")) {
                assertEquals(Map.of(SinkingMedium.SILT, 100), rule.weights());
                assertTrue(rule.generationChance() <= .12);
            }
        });
        assertFalse(profile.coverageRule("minecraft:mangrove_swamp").enabled());
        assertFalse(profile.coverageRule("example:wetlands").enabled());
        assertFalse(profile.coverageRule("minecraft:plains").enabled());
        assertFalse(profile.coverageRule("minecraft:nether_wastes").enabled());
        assertFalse(profile.coverageRule("minecraft:the_end").enabled());
    }

    @Test void treeDepthFallsSmoothlyToTheShallowExterior() {
        var rule = mixed(NaturalMudCoverageRule.MixMode.PATCHES);
        double previous = 3;
        for (int distance = 0; distance <= 6; distance++) {
            double depth = NaturalMudCoveragePattern.depth(rule, 17, 0, 0, distance);
            assertTrue(depth <= previous);
            previous = depth;
        }
        assertEquals(2.5, NaturalMudCoveragePattern.depth(rule, 17, 0, 0, 0));
        assertEquals(.25, NaturalMudCoveragePattern.depth(rule, 17, 0, 0, 4));
        assertEquals(1.375, NaturalMudCoveragePattern.depth(rule, 17, 0, 0, 2));
        var noTrees = new NaturalMudCoverageRule(true, 1, 0, .25, false, 2.5, 4,
                0, rule.mixMode(), 24, 0, rule.weights());
        assertEquals(.25, NaturalMudCoveragePattern.depth(noTrees, 17, 0, 0, 0));
    }

    @Test void randomBlendHonorsWeightsAndDoesNotIntroduceOtherMaterials() {
        var rule = mixed(NaturalMudCoverageRule.MixMode.RANDOM);
        int mud = 0, total = 256 * 256;
        for (int x = -128; x < 128; x++) for (int z = -128; z < 128; z++) {
            var medium = NaturalMudCoveragePattern.medium(rule, 482, x, z);
            assertTrue(rule.weights().containsKey(medium));
            if (medium == SinkingMedium.MUD) mud++;
        }
        assertEquals(.75, mud / (double) total, .015);
    }

    @Test void patchBlendHasContinuousInteriors() {
        var rule = mixed(NaturalMudCoverageRule.MixMode.PATCHES);
        int transitions = 0;
        for (int x = -128; x < 128; x++) for (int z = -128; z < 128; z++)
            if (NaturalMudCoveragePattern.medium(rule, 482, x, z)
                    != NaturalMudCoveragePattern.medium(rule, 482, x + 1, z)) transitions++;
        assertTrue(transitions < 256 * 256 * .08, "Patches should not be per-column noise");
        assertTrue(transitions > 0, "Both materials should appear");
    }

    @Test void chunkPartitionAndVisitOrderDoNotChangeCoverageOrMix() {
        var rule = mixed(NaturalMudCoverageRule.MixMode.PATCHES);
        int[] full = new int[64 * 64];
        int[] chunked = new int[64 * 64];
        for (int x = 0; x < 64; x++) for (int z = 0; z < 64; z++)
            full[x * 64 + z] = sample(rule, x - 32, z - 32);
        for (int cx = 3; cx >= 0; cx--) for (int cz = 3; cz >= 0; cz--)
            for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
                int x = cx * 16 + dx, z = cz * 16 + dz;
                chunked[x * 64 + z] = sample(rule, x - 32, z - 32);
            }
        assertArrayEquals(full, chunked);
    }

    @Test void worldAndPresetRoundTripsRetainModIdsAndDimensionVeto() {
        var dimension = ResourceLocation.parse("example:moon");
        var profile = NaturalMudGenerationProfile.defaults()
                .withDimensionEnabled(dimension, false)
                .withCoverageRule("example:marsh", rate(.22, .65));
        var saved = new NaturalMudGenerationSettings(profile).save(new CompoundTag(), null);
        var loaded = NaturalMudGenerationSettings.loadProfile(saved);
        assertEquals(profile.coverageRules(), loaded.coverageRules());
        assertEquals(profile.disabledDimensions(), loaded.disabledDimensions());
        assertFalse(loaded.dimensionEnabled(dimension));
        assertTrue(loaded.dimensionEnabled(ResourceLocation.parse("example:other")));
        var preset = NaturalMudGenerationPresetCodec.decode("test",
                NaturalMudGenerationPresetCodec.encode("test", profile)).orElseThrow().profile();
        assertEquals(profile.coverageRules(), preset.coverageRules());
        assertEquals(profile.disabledDimensions(), preset.disabledDimensions());
        var edited = profile.withRule(profile.rule(SinkingMedium.MUD).withChance(900));
        assertFalse(edited.dimensionEnabled(dimension));
        assertEquals(profile.coverageRules(), edited.coverageRules());
    }

    @Test void oldWorldsKeepSparseGenerationAndEmptyWeightsProduceNoCover() {
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("Version", 6);
        assertTrue(NaturalMudGenerationSettings.loadProfile(legacy).coverageRules().isEmpty());
        var empty = NaturalMudCoverageRule.defaults(true).withWeight(SinkingMedium.MUD, 0);
        assertFalse(NaturalMudCoveragePattern.covered(empty, 15, 0, 0));
        assertNull(NaturalMudCoveragePattern.medium(empty, 15, 0, 0));
    }

    @Test void generationChanceAndCoverageRatioBothControlTheFinalArea() {
        assertEquals(.65, coverageShare(rate(1, .65)), .025);
        assertEquals(.8 * .65, coverageShare(rate(.8, .65)), .025);
        assertEquals(.12 * .55, coverageShare(rate(.12, .55)), .015);
        assertEquals(.08 * .55, coverageShare(rate(.08, .55)), .012);
        assertEquals(0, coverageShare(rate(0, 1)));
        assertEquals(0, coverageShare(rate(1, 0)));
        assertEquals(1, coverageShare(rate(1, 1)));
    }

    @Test void raisingChanceOrCoverageOnlyAddsAreaAndKeepsRegionInteriorsContinuous() {
        var low = rate(.2, .3);
        var high = rate(.6, .7);
        int transitions = 0;
        for (int x = -128; x < 128; x++) for (int z = -128; z < 128; z++) {
            if (NaturalMudCoveragePattern.covered(low, 827, x, z))
                assertTrue(NaturalMudCoveragePattern.covered(high, 827, x, z));
            if (NaturalMudCoveragePattern.covered(high, 827, x, z)
                    != NaturalMudCoveragePattern.covered(high, 827, x + 1, z)) transitions++;
        }
        assertTrue(transitions > 0 && transitions < 256 * 256 * .10);
    }

    @Test void oldCoveragePresetsWithoutTheNewFieldsKeepTheirOriginalOccurrence() {
        var base = NaturalMudGenerationProfile.defaults().withCoverageRule("example:marsh", rate(.22, .65));
        var json = NaturalMudCoverageCodec.encode(base);
        var marsh = json.getAsJsonObject("biome_coverage").getAsJsonObject("example:marsh");
        marsh.remove("generation_chance");
        marsh.remove("replacement_layers");
        var restored = NaturalMudCoverageCodec.decode(json, base).coverageRule("example:marsh");
        assertEquals(1, restored.generationChance());
        assertEquals(4, restored.replacementLayers());
        assertEquals(.65, restored.coverage());
    }

    private static NaturalMudCoverageRule rate(double chance, double coverage) {
        return new NaturalMudCoverageRule(true, coverage, 0, .25, false, 2.5, 4,
                0, NaturalMudCoverageRule.MixMode.PATCHES, 24, 0,
                Map.of(SinkingMedium.MUD, 100), chance, 8);
    }

    private static double coverageShare(NaturalMudCoverageRule rule) {
        int covered = 0, total = 0;
        // Sample many independent regions as well as negative coordinates.
        for (int x = -4096; x < 4096; x += 16) for (int z = -4096; z < 4096; z += 16) {
            if (NaturalMudCoveragePattern.covered(rule, 197, x, z)) covered++;
            total++;
        }
        return covered / (double) total;
    }

    private static int sample(NaturalMudCoverageRule rule, int x, int z) {
        return NaturalMudCoveragePattern.covered(rule, 197, x, z)
                ? NaturalMudCoveragePattern.medium(rule, 197, x, z).ordinal() : -1;
    }
}

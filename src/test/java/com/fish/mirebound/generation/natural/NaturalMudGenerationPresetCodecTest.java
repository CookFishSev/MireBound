package com.fish.mirebound.generation.natural;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fish.mirebound.mud.SinkingMedium;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class NaturalMudGenerationPresetCodecTest {
    @ParameterizedTest
    @EnumSource(NaturalMudCoverageRule.MixMode.class)
    void coverageOnlyEditsAndDimensionSwitchesSurviveSavingAndUpdatingPresets(NaturalMudCoverageRule.MixMode mode) {
        var coverage = new NaturalMudCoverageRule(true, .37, .22, 1.25, true, 5.5, 7, 1.31,
                mode, 61, .67, Map.of(SinkingMedium.MUD, 2, SinkingMedium.SILT, 5, SinkingMedium.TAR, 3), .09, 9);
        var profile = NaturalMudGenerationProfile.defaults()
                .withCoverageRule("example:marsh", coverage)
                .withCoverageRule("minecraft:swamp", NaturalMudCoverageRule.defaults(false))
                .withDimensionEnabled(ResourceLocation.parse("minecraft:the_end"), false)
                .withDimensionEnabled(ResourceLocation.parse("example:moon"), false);
        profile = profile.withRule(profile.rule(SinkingMedium.SILT).withEnabled(false));
        var loaded = NaturalMudGenerationPresetCodec.decode("fallback",
                NaturalMudGenerationPresetCodec.encode("coverage", profile)).orElseThrow().profile();
        assertEquals(profile.rules(), loaded.rules());
        assertEquals(profile.coverageRules(), loaded.coverageRules());
        assertEquals(profile.disabledDimensions(), loaded.disabledDimensions());
        assertFalse(loaded.coverageRule("minecraft:swamp").enabled());
        assertTrue(loaded.coverageRule("minecraft:river").enabled());

        var edited = loaded.withCoverageRule("example:marsh", coverage.withWeight(SinkingMedium.MUD, 0));
        var updated = NaturalMudGenerationPresetCodec.decode("fallback",
                NaturalMudGenerationPresetCodec.encode("coverage", edited)).orElseThrow().profile();
        assertEquals(edited.coverageRules(), updated.coverageRules());
        assertEquals(loaded.rules(), updated.rules());
        assertEquals(loaded.disabledDimensions(), updated.disabledDimensions());
    }

    @Test
    void roundTripKeepsNameAndRuleValues() {
        NaturalMudGenerationProfile profile =
                NaturalMudGenerationProfile.defaults().withRule(
                        NaturalMudGenerationProfile.defaults()
                                .rule(SinkingMedium.MUD)
                                .withChance(321)
                                .withRadiusRange(4, 9));

        Optional<NaturalMudGenerationPresetCodec.NamedProfile> decoded =
                NaturalMudGenerationPresetCodec.decode("fallback",
                        NaturalMudGenerationPresetCodec.encode("河岸测试", profile));

        assertTrue(decoded.isPresent());
        assertEquals("河岸测试", decoded.get().name());
        assertEquals(321, decoded.get().profile().rule(SinkingMedium.MUD)
                .chancePerHundredThousandChunks());
        assertEquals(4, decoded.get().profile().rule(SinkingMedium.MUD)
                .minimumRadius());
        assertEquals(9, decoded.get().profile().rule(SinkingMedium.MUD)
                .maximumRadius());
    }

    @Test
    void missingRuleFieldsUseTheCurrentDefaults() {
        String json = "{\"format\":\"mirebound_natural_generation\","
                + "\"version\":1,\"rules\":[{\"medium\":\"mud\"}]}";

        Optional<NaturalMudGenerationPresetCodec.NamedProfile> decoded =
                NaturalMudGenerationPresetCodec.decode("fallback", json);

        assertTrue(decoded.isPresent());
        NaturalMudGenerationProfile.Rule expected =
                NaturalMudGenerationProfile.defaults().rule(SinkingMedium.MUD);
        NaturalMudGenerationProfile.Rule actual = decoded.get().profile()
                .rule(SinkingMedium.MUD);
        assertEquals(expected, actual);
    }

    @Test
    void wrongFormatVersionOrMediumIsRejected() {
        String wrongVersion = "{\"format\":\"mirebound_natural_generation\","
                + "\"version\":2,\"rules\":[]}";
        String wrongMedium = "{\"format\":\"mirebound_natural_generation\","
                + "\"version\":1,\"rules\":[{\"medium\":\"missing\"}]}";

        assertFalse(NaturalMudGenerationPresetCodec.decode("fallback",
                wrongVersion).isPresent());
        assertFalse(NaturalMudGenerationPresetCodec.decode("fallback",
                wrongMedium).isPresent());
    }
}

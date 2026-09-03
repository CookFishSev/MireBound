package com.fish.mirebound.generation.natural;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fish.mirebound.mud.SinkingMedium;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NaturalMudGenerationPresetCodecTest {
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

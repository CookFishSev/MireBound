package com.fish.mirebound.generation.natural;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fish.mirebound.generation.natural.NaturalMudGenerationProfile.Rule;
import com.fish.mirebound.mud.SinkingMedium;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class NaturalMudGenerationProfileTest {
    private static final EnumSet<SinkingMedium> FULL_HEIGHT_TOP = EnumSet.of(
            SinkingMedium.MUD,
            SinkingMedium.INSECT_MOUND,
            SinkingMedium.TENDER_FLESH,
            SinkingMedium.ASSIMILATION_SLIME);

    @AfterEach
    void clearCreationBridge() {
        NaturalMudWorldCreationBridge.clear();
    }

    @Test
    void defaultsCoverEveryNaturalMedium() {
        NaturalMudGenerationProfile profile =
                NaturalMudGenerationProfile.defaults();
        for (SinkingMedium medium : SinkingMedium.values()) {
            Rule rule = profile.rule(medium);
            assertNotNull(rule, medium.name());
            assertTrue(rule.enabled(), medium.name());
            assertTrue(rule.chancePerHundredThousandChunks() > 0,
                    medium.name());
            assertFalse(rule.biomeSelectors().isEmpty(), medium.name());
            assertFalse(rule.forms().isEmpty(), medium.name());
            assertEquals(FULL_HEIGHT_TOP.contains(medium),
                    rule.fullHeightTop(), medium.name());
        }
    }

    @Test
    void ruleConstructorClampsProbabilityAndDepositBounds() {
        Rule rule = new Rule(SinkingMedium.MUD, true, Integer.MAX_VALUE,
                Set.of("minecraft:river"),
                List.of(NaturalMudDepositForm.RIVERBANK_CRESCENT),
                -5, 80, -2, 90, false);

        assertEquals(NaturalMudGenerationProfile.MAXIMUM_CHANCE,
                rule.chancePerHundredThousandChunks());
        assertEquals(2, rule.minimumRadius());
        assertEquals(12, rule.maximumRadius());
        assertEquals(1, rule.minimumDepth());
        assertEquals(6, rule.maximumDepth());
    }

    @Test
    void resetOnlyRestoresTheSelectedMedium() {
        NaturalMudGenerationProfile defaults =
                NaturalMudGenerationProfile.defaults();
        Rule changedMud = defaults.rule(SinkingMedium.MUD)
                .withChance(777)
                .withBiomeSelectors(Set.of("minecraft:desert"));
        NaturalMudGenerationProfile changed = defaults.withRule(changedMud)
                .withRule(defaults.rule(SinkingMedium.SOFT_QUICKSAND)
                        .withChance(333));

        NaturalMudGenerationProfile reset = changed.reset(SinkingMedium.MUD);
        assertEquals(defaults.rule(SinkingMedium.MUD),
                reset.rule(SinkingMedium.MUD));
        assertEquals(333, reset.rule(SinkingMedium.SOFT_QUICKSAND)
                .chancePerHundredThousandChunks());
    }

    @Test
    void globalGenerationSwitchChangesOnlyEnabledFlags() {
        NaturalMudGenerationProfile defaults =
                NaturalMudGenerationProfile.defaults();
        NaturalMudGenerationProfile disabled = defaults.withAllEnabled(false);

        for (SinkingMedium medium : SinkingMedium.values()) {
            assertFalse(disabled.rule(medium).enabled(), medium.name());
            assertEquals(defaults.rule(medium).chancePerHundredThousandChunks(),
                    disabled.rule(medium).chancePerHundredThousandChunks(),
                    medium.name());
            assertEquals(defaults.rule(medium).biomeSelectors(),
                    disabled.rule(medium).biomeSelectors(), medium.name());
        }

        NaturalMudGenerationProfile enabled = disabled.withAllEnabled(true);
        for (SinkingMedium medium : SinkingMedium.values()) {
            assertTrue(enabled.rule(medium).enabled(), medium.name());
        }
    }

    @Test
    void generationFormsCanBeReplacedIndependently() {
        Rule original = NaturalMudGenerationProfile.defaults()
                .rule(SinkingMedium.MUD);
        List<NaturalMudDepositForm> forms = List.of(
                NaturalMudDepositForm.CAVE_SEEP,
                NaturalMudDepositForm.ORGANIC_NEST);

        Rule changed = original.withForms(forms);

        assertEquals(forms, changed.forms());
        assertEquals(original.biomeSelectors(), changed.biomeSelectors());
        assertEquals(original.chancePerHundredThousandChunks(),
                changed.chancePerHundredThousandChunks());
    }

    @Test
    void radiusRangeExposesExactAverageAndVariation() {
        Rule original = NaturalMudGenerationProfile.defaults()
                .rule(SinkingMedium.MUD);
        Rule changed = original.withRadiusRange(4, 7);

        assertEquals(5.5D, changed.averageRadius());
        assertEquals(1.5D, changed.radiusVariation());
        assertEquals(4, changed.minimumRadius());
        assertEquals(7, changed.maximumRadius());
    }

    @Test
    void savedRadiusRangeLoadsWithoutChangingOtherRuleValues() {
        CompoundTag saved = new CompoundTag();
        ListTag rules = new ListTag();
        CompoundTag mud = savedRule(SinkingMedium.MUD);
        mud.putInt("MinimumRadius", 5);
        mud.putInt("MaximumRadius", 9);
        rules.add(mud);
        saved.put("Rules", rules);

        Rule loaded = NaturalMudGenerationSettings.loadProfile(saved)
                .rule(SinkingMedium.MUD);

        assertEquals(5, loaded.minimumRadius());
        assertEquals(9, loaded.maximumRadius());
        assertEquals(NaturalMudGenerationProfile.defaults()
                        .rule(SinkingMedium.MUD).forms(),
                loaded.forms());
    }

    @Test
    void savedGenerationFormsRoundTripAndLegacyRulesKeepDefaults() {
        CompoundTag saved = new CompoundTag();
        ListTag rules = new ListTag();
        CompoundTag mud = savedRule(SinkingMedium.MUD);
        ListTag forms = new ListTag();
        forms.add(StringTag.valueOf(NaturalMudDepositForm.CAVE_SEEP.name()));
        mud.put("Forms", forms);
        rules.add(mud);
        rules.add(savedRule(SinkingMedium.SOFT_QUICKSAND));
        saved.put("Rules", rules);

        NaturalMudGenerationProfile loaded =
                NaturalMudGenerationSettings.loadProfile(saved);

        assertEquals(List.of(NaturalMudDepositForm.CAVE_SEEP),
                loaded.rule(SinkingMedium.MUD).forms());
        assertEquals(NaturalMudGenerationProfile.defaults()
                        .rule(SinkingMedium.SOFT_QUICKSAND).forms(),
                loaded.rule(SinkingMedium.SOFT_QUICKSAND).forms());
    }

    @Test
    void anExplicitlyEmptyGenerationFormListRemainsEmpty() {
        CompoundTag saved = new CompoundTag();
        ListTag rules = new ListTag();
        CompoundTag mud = savedRule(SinkingMedium.MUD);
        mud.put("Forms", new ListTag());
        rules.add(mud);
        saved.put("Rules", rules);

        NaturalMudGenerationProfile loaded =
                NaturalMudGenerationSettings.loadProfile(saved);

        assertTrue(loaded.rule(SinkingMedium.MUD).forms().isEmpty());
    }

    @Test
    void stagedCreateWorldProfileIsConsumedOnlyOnce() {
        NaturalMudGenerationProfile staged =
                NaturalMudGenerationProfile.defaults().withRule(
                        NaturalMudGenerationProfile.defaults()
                                .rule(SinkingMedium.MUD).withChance(621));
        NaturalMudWorldCreationBridge.stage(staged);

        assertSame(staged, NaturalMudWorldCreationBridge.consumeOrDefault());
        assertEquals(NaturalMudGenerationProfile.defaults().rules(),
                NaturalMudWorldCreationBridge.consumeOrDefault().rules());
    }

    @Test
    void commonBiomesUseMudInsteadOfOrdinaryQuicksand() {
        NaturalMudGenerationProfile profile =
                NaturalMudGenerationProfile.defaults();

        assertTrue(profile.rule(SinkingMedium.MUD).biomeSelectors()
                .contains("minecraft:plains"));
        assertTrue(profile.rule(SinkingMedium.MUD).biomeSelectors()
                .contains("minecraft:forest"));
        assertFalse(profile.rule(SinkingMedium.SOFT_QUICKSAND).biomeSelectors()
                .contains("minecraft:plains"));
        assertFalse(profile.rule(SinkingMedium.SOFT_QUICKSAND).biomeSelectors()
                .contains("#minecraft:is_river"));
        assertFalse(profile.rule(SinkingMedium.SOFT_QUICKSAND).biomeSelectors()
                .contains("minecraft:forest"));
    }

    @Test
    void lakeFormsAreLimitedToCuratedMedia() {
        NaturalMudGenerationProfile profile =
                NaturalMudGenerationProfile.defaults();

        assertTrue(profile.rule(SinkingMedium.MUD).forms()
                .contains(NaturalMudDepositForm.SURFACE_LAKE));
        assertTrue(profile.rule(SinkingMedium.TAR).forms()
                .contains(NaturalMudDepositForm.UNDERGROUND_LAKE));
        assertFalse(profile.rule(SinkingMedium.SOFT_QUICKSAND).forms().stream()
                .anyMatch(NaturalMudDepositForm::lake));
        assertFalse(profile.rule(SinkingMedium.END_SILT).forms().stream()
                .anyMatch(NaturalMudDepositForm::lake));
    }

    @Test
    void dimensionSelectorsCanMatchWithoutBiomeTags() {
        assertTrue(NaturalMudGenerationProfile.matchesDimensionSelector(
                "#minecraft:is_end", Level.END));
        assertFalse(NaturalMudGenerationProfile.matchesDimensionSelector(
                "#minecraft:is_end", Level.OVERWORLD));
    }

    private static CompoundTag savedRule(SinkingMedium medium) {
        CompoundTag entry = new CompoundTag();
        Rule defaults = NaturalMudGenerationProfile.defaults().rule(medium);
        entry.putString("Medium", medium.serializedName());
        entry.putBoolean("Enabled", defaults.enabled());
        entry.putInt("Chance", defaults.chancePerHundredThousandChunks());
        ListTag biomes = new ListTag();
        for (String selector : defaults.biomeSelectors()) {
            biomes.add(StringTag.valueOf(selector));
        }
        entry.put("Biomes", biomes);
        return entry;
    }

}

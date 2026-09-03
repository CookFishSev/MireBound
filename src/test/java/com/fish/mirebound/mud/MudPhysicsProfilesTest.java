package com.fish.mirebound.mud;

import com.fish.mirebound.assimilation.AssimilationProfile;
import com.fish.mirebound.itemphysics.DroppedItemPhysicsProfile;
import com.fish.mirebound.mud.harvest.MudHarvestProfile;
import com.fish.mirebound.mud.flow.MudFlowProfile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MudPhysicsProfilesTest {
    @Test
    void ordinaryMediaDefaultToARestrictedStepHeight() {
        assertEquals(0.35D,
                value(MudPhysicsProfiles.defaultValues(SinkingMedium.MUD),
                        MudPhysicsParameter.STEP_HEIGHT),
                1.0E-9D);
        assertFalse(MudPhysicsParameter.STEP_HEIGHT.appliesTo(SinkingMedium.LIVING_SLIME));
    }

    @Test
    void customizedStepHeightRoundTripsThroughTheOrdinaryProfile() {
        double[] values = MudPhysicsProfiles.defaultValues(SinkingMedium.MUD);
        values[MudPhysicsParameter.STEP_HEIGHT.ordinal()] = 0.37D;
        SinkingPhysicsProfile profile = SinkingPhysicsProfile.fromValues(values);
        double[] roundTrip = new double[MudPhysicsParameter.COUNT];

        profile.writeTo(roundTrip);

        assertEquals(0.37D,
                roundTrip[MudPhysicsParameter.STEP_HEIGHT.ordinal()],
                1.0E-9D);
    }

    @Test
    void surfaceClosingDefaultsMatchThePerformanceTunedTiming() {
        assertEquals(35.0D,
                value(MudPhysicsProfiles.defaultValues(SinkingMedium.MUD),
                        MudPhysicsParameter.SURFACE_CLOSE_TICKS),
                1.0E-9D);
        assertEquals(45.0D,
                value(MudPhysicsProfiles.defaultValues(SinkingMedium.TENDER_FLESH),
                        MudPhysicsParameter.SURFACE_CLOSE_TICKS),
                1.0E-9D);
    }

    @Test
    void binaryParametersExposeToggleMetadata() {
        assertTrue(MudPhysicsParameter.ENABLED.isToggle());
        assertTrue(MudPhysicsParameter.FLESH_MEMBRANE_OPAQUE.isToggle());
        assertTrue(MudPhysicsParameter.ERUPTION_CONTINUOUS_ENABLED.isToggle());
        assertFalse(MudPhysicsParameter.COVERAGE_MAXIMUM.isToggle());
        assertFalse(MudPhysicsParameter.ERUPTION_FLOW_INTERVAL_TICKS.isToggle());
    }

    @Test
    void everyDefaultProfileRoundTripsThroughTheEditableValueArray() {
        for (SinkingMedium medium : SinkingMedium.values()) {
            double[] values = MudPhysicsProfiles.defaultValues(medium);
            SinkingPhysicsProfile ordinary = SinkingPhysicsProfile.fromValues(values);
            LivingSlimePhysicsProfile slime = LivingSlimePhysicsProfile.fromValues(values);
            double[] roundTrip = new double[MudPhysicsParameter.COUNT];
            ordinary.writeTo(roundTrip);
            slime.writeTo(roundTrip);
            AdhesionStrandProfile.fromValues(values).writeTo(roundTrip);
            AssimilationProfile.fromValues(values).writeTo(roundTrip);
            MudHarvestProfile.fromValues(values).writeTo(roundTrip);
            DroppedItemPhysicsProfile.fromValues(values).writeTo(roundTrip);
            MudFlowProfile.fromValues(values).writeTo(roundTrip);

            for (MudPhysicsParameter parameter : MudPhysicsParameter.values()) {
                assertTrue(values[parameter.ordinal()] >= parameter.minimum());
                assertTrue(values[parameter.ordinal()] <= parameter.maximum());
                assertEquals(values[parameter.ordinal()], roundTrip[parameter.ordinal()], 1.0E-9D,
                        medium + "/" + parameter);
            }
        }
    }

    @Test
    void assimilationTemplateDefaultsOffExceptForAssimilationSlime() {
        for (SinkingMedium medium : SinkingMedium.values()) {
            AssimilationProfile profile = AssimilationProfile.fromValues(
                    MudPhysicsProfiles.defaultValues(medium));
            assertEquals(medium == SinkingMedium.ASSIMILATION_SLIME, profile.enabled(),
                    medium + " has the wrong assimilation-template default");
            assertEquals(AssimilationProfile.DEFAULT.gainPerTick(), profile.gainPerTick(), 1.0E-9D);
        }
    }

    @Test
    void struggleCooldownAndPurgeCancellationDefaultsAreConfigurableAndEnabled() {
        double[] ordinary = MudPhysicsProfiles.defaultValues(SinkingMedium.MUD);
        double[] slime = MudPhysicsProfiles.defaultValues(SinkingMedium.LIVING_SLIME);
        AssimilationProfile assimilation = AssimilationProfile.fromValues(
                MudPhysicsProfiles.defaultValues(SinkingMedium.ASSIMILATION_SLIME));

        assertEquals(30.0D,
                ordinary[MudPhysicsParameter.STRUGGLE_MAX_COOLDOWN_TICKS.ordinal()],
                1.0E-9D);
        assertEquals(30.0D,
                slime[MudPhysicsParameter.SLIME_STRUGGLE_MAX_COOLDOWN_TICKS.ordinal()],
                1.0E-9D);
        assertTrue(assimilation.partialPurgeCancelOnMove());
    }

    @Test
    void assimilationRangesAreSanitizedAsOrderedPairs() {
        double[] values = MudPhysicsProfiles.defaultValues(SinkingMedium.ASSIMILATION_SLIME);
        values[MudPhysicsParameter.ASSIMILATION_PARTIAL_PURGE_ZONE_MIN_WIDTH.ordinal()] = 0.60D;
        values[MudPhysicsParameter.ASSIMILATION_PARTIAL_PURGE_ZONE_MAX_WIDTH.ordinal()] = 0.20D;
        values[MudPhysicsParameter.ASSIMILATION_SCREEN_CRACK_MIN_LENGTH.ordinal()] = 0.80D;
        values[MudPhysicsParameter.ASSIMILATION_SCREEN_CRACK_MAX_LENGTH.ordinal()] = 0.30D;

        double[] sanitized = MudPhysicsProfiles.sanitize(SinkingMedium.ASSIMILATION_SLIME, values);

        assertEquals(0.60D,
                sanitized[MudPhysicsParameter.ASSIMILATION_PARTIAL_PURGE_ZONE_MAX_WIDTH.ordinal()],
                1.0E-9D);
        assertEquals(0.80D,
                sanitized[MudPhysicsParameter.ASSIMILATION_SCREEN_CRACK_MAX_LENGTH.ordinal()],
                1.0E-9D);
    }

    @Test
    void tuningEqualityUsesDisplayedPrecisionInsteadOfFloatStorageNoise() {
        MudPhysicsParameter opacity = MudPhysicsParameter.ASSIMILATION_SCREEN_OPACITY;
        assertTrue(opacity.displayEquivalent(0.94D, 0.9399999976158142D));
        assertTrue(!opacity.displayEquivalent(0.94D, 0.93D));

        MudPhysicsParameter ticks = MudPhysicsParameter.ASSIMILATION_RESTORE_TICKS;
        assertTrue(ticks.displayEquivalent(32.0D, 32.0000001D));
        assertTrue(!ticks.displayEquivalent(32.0D, 33.0D));
    }

    @Test
    void everyMediumExposesOneAssimilationCategorySplitIntoFiveSubcategories() {
        java.util.Set<MudPhysicsParameter.Subcategory> expected = java.util.Set.of(
                MudPhysicsParameter.Subcategory.ASSIMILATION_CORE,
                MudPhysicsParameter.Subcategory.ASSIMILATION_SOUL,
                MudPhysicsParameter.Subcategory.ASSIMILATION_RESCUE,
                MudPhysicsParameter.Subcategory.ASSIMILATION_PURGE,
                MudPhysicsParameter.Subcategory.ASSIMILATION_CRACKS);
        java.util.Set<MudPhysicsParameter> assimilation = java.util.EnumSet.noneOf(
                MudPhysicsParameter.class);
        java.util.Set<MudPhysicsParameter.Subcategory> subcategories = java.util.EnumSet.noneOf(
                MudPhysicsParameter.Subcategory.class);
        for (MudPhysicsParameter parameter : MudPhysicsParameter.values()) {
            if (parameter.category() != MudPhysicsParameter.Category.ASSIMILATION) {
                continue;
            }
            assimilation.add(parameter);
            assertTrue(parameter.subcategory() != MudPhysicsParameter.Subcategory.NONE,
                    parameter + " is missing an assimilation subcategory");
            subcategories.add(parameter.subcategory());
        }
        assertEquals(expected, subcategories);
        assertEquals(92, assimilation.size());
        for (SinkingMedium medium : SinkingMedium.values()) {
            java.util.Set<MudPhysicsParameter> visible = java.util.Set.of(
                    MudPhysicsParameter.forMedium(medium));
            assertTrue(visible.containsAll(assimilation),
                    medium + " hides part of the reusable assimilation template");
        }
    }

    @Test
    void sanitizingKeepsStruggleMaximumAtLeastAsLargeAsMinimum() {
        double[] values = MudPhysicsProfiles.defaultValues(SinkingMedium.SOFT_QUICKSAND);
        values[MudPhysicsParameter.STRUGGLE_MIN.ordinal()] = 0.30D;
        values[MudPhysicsParameter.STRUGGLE_MAX.ordinal()] = 0.10D;

        double[] sanitized = MudPhysicsProfiles.sanitize(SinkingMedium.SOFT_QUICKSAND, values);

        assertEquals(0.30D, sanitized[MudPhysicsParameter.STRUGGLE_MIN.ordinal()], 1.0E-9D);
        assertEquals(0.30D, sanitized[MudPhysicsParameter.STRUGGLE_MAX.ordinal()], 1.0E-9D);
    }

    @Test
    void sanitizingKeepsNaturalSinkingDepthWithinTheMaximum() {
        double[] values = MudPhysicsProfiles.defaultValues(SinkingMedium.MUD);
        values[MudPhysicsParameter.SIMPLE_MAXIMUM_SINKING_DEPTH.ordinal()] = 0.45D;
        values[MudPhysicsParameter.SIMPLE_NATURAL_SINKING_DEPTH.ordinal()] = 0.80D;

        double[] sanitized = MudPhysicsProfiles.sanitize(SinkingMedium.MUD, values);

        assertEquals(0.45D,
                sanitized[MudPhysicsParameter.SIMPLE_MAXIMUM_SINKING_DEPTH.ordinal()], 1.0E-9D);
        assertEquals(0.45D,
                sanitized[MudPhysicsParameter.SIMPLE_NATURAL_SINKING_DEPTH.ordinal()], 1.0E-9D);
    }

    @Test
    void defaultMediaExposeDistinctComposableMaterialIdentities() {
        double[] sand = MudPhysicsProfiles.defaultValues(SinkingMedium.SOFT_QUICKSAND);
        double[] mud = MudPhysicsProfiles.defaultValues(SinkingMedium.MUD);
        double[] tar = MudPhysicsProfiles.defaultValues(SinkingMedium.TAR);

        assertTrue(value(sand, MudPhysicsParameter.GRANULAR_COLLAPSE)
                > value(mud, MudPhysicsParameter.GRANULAR_COLLAPSE));
        assertTrue(value(mud, MudPhysicsParameter.COHESIVE_SUCTION)
                > value(sand, MudPhysicsParameter.COHESIVE_SUCTION));
        assertTrue(value(tar, MudPhysicsParameter.ADHESIVE_GRIP)
                > value(mud, MudPhysicsParameter.ADHESIVE_GRIP));
    }

    @Test
    void adhesionStrandsDefaultToTarButRemainAvailableToEveryMedium() {
        double[] values = MudPhysicsProfiles.defaultValues(SinkingMedium.TAR);
        assertTrue(value(values, MudPhysicsParameter.ADHESION_STRANDS_ENABLED) >= 0.5D);
        assertTrue(value(values, MudPhysicsParameter.ADHESION_STRAND_MIN_COUNT)
                <= value(values, MudPhysicsParameter.ADHESION_STRAND_MAX_COUNT));
        assertTrue(MudPhysicsParameter.ADHESION_STRAND_BREAK_LENGTH.appliesTo(SinkingMedium.TAR));
        assertTrue(MudPhysicsParameter.ADHESION_STRAND_BREAK_LENGTH.appliesTo(SinkingMedium.MUD));
        assertTrue(value(values, MudPhysicsParameter.ADHESION_SHEET_ENABLED) >= 0.5D);
        assertTrue(value(values, MudPhysicsParameter.ADHESION_STRAND_MIN_COUNT) >= 6.0D);
        assertTrue(value(values, MudPhysicsParameter.ADHESION_STRAND_MAX_COUNT) >= 8.0D);
        assertTrue(value(values, MudPhysicsParameter.ADHESION_STRAND_SPAWN_HEIGHT) >= 1.40D);
        assertTrue(value(values, MudPhysicsParameter.ADHESION_SHEET_MIN_RIBS) >= 6.0D);
        assertEquals(16.0D,
                value(values, MudPhysicsParameter.ADHESION_STRAND_MAX_COUNT), 1.0E-9D);
        assertEquals(2.00D,
                value(values, MudPhysicsParameter.ADHESION_STRAND_BREAK_LENGTH), 1.0E-9D);
        assertEquals(4.0D,
                value(values, MudPhysicsParameter.ADHESION_INITIAL_COUNT), 1.0E-9D);
        assertEquals(0.70D,
                value(values, MudPhysicsParameter.ADHESION_RING_RADIUS), 1.0E-9D);
        assertTrue(value(values, MudPhysicsParameter.ADHESION_GEOMETRIC_ANCHORS) >= 0.5D);
        assertTrue(value(values, MudPhysicsParameter.ADHESION_RING_REFRESH_TICKS) >= 1.0D);
        assertEquals(0.18D,
                value(values, MudPhysicsParameter.ADHESION_RING_CLEARANCE), 1.0E-9D);
        assertTrue(value(values, MudPhysicsParameter.ADHESION_RING_DRIFT_AMOUNT) > 0.0D);
        assertTrue(value(values, MudPhysicsParameter.ADHESION_RING_DRIFT_SPEED) > 0.0D);
        assertEquals(0.14D,
                value(values, MudPhysicsParameter.ADHESION_BODY_ANCHOR_LIFT), 1.0E-9D);
        assertTrue(value(values, MudPhysicsParameter.ADHESION_ATTACH_DELAY_TICKS) > 0.0D);
        assertTrue(value(values, MudPhysicsParameter.ADHESION_ATTACH_GROW_TICKS) > 1.0D);
        assertTrue(value(values, MudPhysicsParameter.ADHESION_SPAWN_INTERVAL_TICKS) > 1.0D);
        assertTrue(value(values, MudPhysicsParameter.ADHESION_ANCHOR_GRACE_TICKS)
                > value(values, MudPhysicsParameter.ADHESION_BREAK_CONFIRM_TICKS));
        assertTrue(value(values, MudPhysicsParameter.ADHESION_ANCHOR_SEARCH_PIXELS) >= 1.0D);
        assertTrue(value(MudPhysicsProfiles.defaultValues(SinkingMedium.MUD),
                MudPhysicsParameter.ADHESION_STRANDS_ENABLED) < 0.5D);

        values[MudPhysicsParameter.ADHESION_STRAND_MIN_COUNT.ordinal()] = 7.0D;
        values[MudPhysicsParameter.ADHESION_STRAND_MAX_COUNT.ordinal()] = 2.0D;
        double[] sanitized = MudPhysicsProfiles.sanitize(SinkingMedium.TAR, values);
        assertEquals(7.0D,
                value(sanitized, MudPhysicsParameter.ADHESION_STRAND_MAX_COUNT), 1.0E-9D);
    }

    @Test
    void genericAdhesionTemplateMatchesTunedTarBehindTwoFeatureSwitches() {
        double[] tar = MudPhysicsProfiles.defaultValues(SinkingMedium.TAR);
        for (SinkingMedium medium : SinkingMedium.values()) {
            if (medium == SinkingMedium.TAR || medium == SinkingMedium.TENDER_FLESH) {
                continue;
            }
            double[] values = MudPhysicsProfiles.defaultValues(medium);
            assertEquals(0.0D,
                    value(values, MudPhysicsParameter.ADHESION_STRANDS_ENABLED), 1.0E-9D);
            assertEquals(0.0D,
                    value(values, MudPhysicsParameter.ADHESION_SHEET_ENABLED), 1.0E-9D);
            for (MudPhysicsParameter parameter : MudPhysicsParameter.values()) {
                if (parameter.category() == MudPhysicsParameter.Category.ADHESION_STRANDS
                        && !AdhesionStrandProfile.isFeatureSwitch(parameter)) {
                    assertEquals(value(tar, parameter), value(values, parameter), 1.0E-9D,
                            medium + " did not inherit " + parameter);
                }
            }
        }
        assertEquals(1.45D,
                value(tar, MudPhysicsParameter.ADHESION_STRAND_SPAWN_HEIGHT), 1.0E-9D);
        assertEquals(6.0D,
                value(tar, MudPhysicsParameter.ADHESION_SHEET_MIN_RIBS), 1.0E-9D);
    }

    @Test
    void everyMediumExposesTheCompleteAdhesionTemplateToTheTuningGui() {
        for (SinkingMedium medium : SinkingMedium.values()) {
            java.util.Set<MudPhysicsParameter> visible = java.util.Set.of(
                    MudPhysicsParameter.forMedium(medium));
            for (MudPhysicsParameter parameter : MudPhysicsParameter.values()) {
                if (parameter.category() == MudPhysicsParameter.Category.ADHESION_STRANDS) {
                    assertTrue(visible.contains(parameter),
                            medium + " hides adhesion setting " + parameter);
                }
            }
        }
    }

    @Test
    void clientProfileHotSyncRebuildsAdhesionTemplate() {
        double[] values = MudPhysicsProfiles.defaultValues(SinkingMedium.MUD);
        values[MudPhysicsParameter.ADHESION_STRANDS_ENABLED.ordinal()] = 1.0D;
        values[MudPhysicsParameter.ADHESION_SHEET_ENABLED.ordinal()] = 1.0D;
        values[MudPhysicsParameter.ADHESION_STRAND_WIDTH_PIXELS.ordinal()] = 2.35D;

        MudPhysicsProfiles.acceptClientProfile(SinkingMedium.MUD, values);

        AdhesionStrandProfile synced =
                MudPhysicsProfiles.adhesionStrandsClient(SinkingMedium.MUD);
        assertTrue(synced.enabled());
        assertTrue(synced.sheetEnabled());
        assertEquals(2.35D, synced.widthPixels(), 1.0E-9D);
        MudPhysicsProfiles.resetClient();
    }

    @Test
    void coverageAppearanceDefaultsPreserveExistingMediaAndGiveRedSandSubtleVariation() {
        double[] mud = MudPhysicsProfiles.defaultValues(SinkingMedium.MUD);
        assertEquals(1.0D, value(mud, MudPhysicsParameter.COVERAGE_MAXIMUM), 1.0E-9D);
        assertEquals(1.0D, value(mud, MudPhysicsParameter.COVERAGE_OPACITY), 1.0E-9D);
        assertEquals(0.0D, value(mud, MudPhysicsParameter.COVERAGE_OPACITY_VARIATION), 1.0E-9D);
        assertEquals(0.0D,
                value(mud, MudPhysicsParameter.ADAPTIVE_COVERAGE_SMOOTHING_RADIUS),
                1.0E-9D);
        assertEquals(0.90D,
                value(mud, MudPhysicsParameter.ADAPTIVE_COVERAGE_TEXTURE_DETAIL),
                1.0E-9D);
        assertEquals(0.08D,
                value(mud, MudPhysicsParameter.COVERAGE_BRIGHTNESS_VARIATION),
                1.0E-9D);

        double[] redSand = MudPhysicsProfiles.defaultValues(SinkingMedium.RED_QUICKSAND);
        assertEquals(1.0D, value(redSand, MudPhysicsParameter.COVERAGE_MAXIMUM), 1.0E-9D);
        assertEquals(0.70D, value(redSand, MudPhysicsParameter.COVERAGE_OPACITY), 1.0E-6D);
        assertEquals(0.14D, value(redSand, MudPhysicsParameter.COVERAGE_OPACITY_VARIATION), 1.0E-9D);

        double[] slime = MudPhysicsProfiles.defaultValues(SinkingMedium.LIVING_SLIME);
        assertEquals(0.60D, value(slime, MudPhysicsParameter.COVERAGE_OPACITY), 1.0E-6D);
    }

    @Test
    void adaptiveCoverageSettingsInvalidateClientAppearanceCaches() {
        MudPhysicsProfiles.resetClient();
        double[] values = MudPhysicsProfiles.defaultValues(SinkingMedium.MUD);
        values[MudPhysicsParameter.ADAPTIVE_COVERAGE_SMOOTHING_RADIUS.ordinal()] = 2.0D;
        assertTrue(MudPhysicsProfiles.acceptClientProfile(SinkingMedium.MUD, values));

        values[MudPhysicsParameter.ADAPTIVE_COVERAGE_TEXTURE_DETAIL.ordinal()] = 0.35D;
        assertTrue(MudPhysicsProfiles.acceptClientProfile(SinkingMedium.MUD, values));

        values[MudPhysicsParameter.COVERAGE_BRIGHTNESS_VARIATION.ordinal()] = 0.24D;
        assertTrue(MudPhysicsProfiles.acceptClientProfile(SinkingMedium.MUD, values));
        MudPhysicsProfiles.resetClient();
    }

    @Test
    void everyMediumDefaultsToFullPixelCoverage() {
        for (SinkingMedium medium : SinkingMedium.values()) {
            assertEquals(1.0D,
                    value(MudPhysicsProfiles.defaultValues(medium), MudPhysicsParameter.COVERAGE_MAXIMUM),
                    1.0E-9D,
                    medium + " should allow every canonical pixel to be stained");
        }
    }

    @Test
    void newMediaExposeDistinctPhysicsAndCoverageDefaults() {
        double[] quicksand = MudPhysicsProfiles.defaultValues(SinkingMedium.SOFT_QUICKSAND);
        double[] ash = MudPhysicsProfiles.defaultValues(SinkingMedium.ASH_QUICKSAND);
        double[] soul = MudPhysicsProfiles.defaultValues(SinkingMedium.SOUL_SILT);
        double[] gel = MudPhysicsProfiles.defaultValues(SinkingMedium.GEL_CLAY);
        double[] lime = MudPhysicsProfiles.defaultValues(SinkingMedium.LIME_MUD);

        assertTrue(value(ash, MudPhysicsParameter.MOVEMENT_SINK_SCALE)
                > value(quicksand, MudPhysicsParameter.MOVEMENT_SINK_SCALE));
        assertTrue(value(soul, MudPhysicsParameter.MAX_DEPTH_FACTOR)
                > value(ash, MudPhysicsParameter.MAX_DEPTH_FACTOR));
        assertTrue(value(soul, MudPhysicsParameter.STRUGGLE_MAX)
                < value(ash, MudPhysicsParameter.STRUGGLE_MAX));
        assertTrue(value(gel, MudPhysicsParameter.ADHESIVE_GRIP)
                > value(lime, MudPhysicsParameter.ADHESIVE_GRIP));
        assertTrue(value(gel, MudPhysicsParameter.WALK_THIGH)
                < value(lime, MudPhysicsParameter.WALK_THIGH));

        assertEquals(1.0D, value(ash, MudPhysicsParameter.COVERAGE_MAXIMUM), 1.0E-9D);
        assertEquals(0.62D, value(ash, MudPhysicsParameter.COVERAGE_OPACITY), 1.0E-9D);
        assertEquals(0.68D, value(soul, MudPhysicsParameter.COVERAGE_OPACITY), 1.0E-9D);
        assertEquals(1.0D, value(gel, MudPhysicsParameter.COVERAGE_OPACITY), 1.0E-9D);
        assertEquals(1.0D, value(lime, MudPhysicsParameter.COVERAGE_OPACITY), 1.0E-9D);
    }

    @Test
    void stackedPartialMudFillsByDefault() {
        for (SinkingMedium medium : SinkingMedium.values()) {
            assertEquals(1.0D, value(MudPhysicsProfiles.defaultValues(medium),
                    MudPhysicsParameter.AUTO_STACK_FILL), 1.0E-9D);
        }
    }

    @Test
    void oldLocalTarDefaultsMigrateWithoutOverwritingCustomizedValues() {
        double[] values = MudPhysicsProfiles.defaultValues(SinkingMedium.TAR);
        values[MudPhysicsParameter.ADHESION_STRAND_MIN_COUNT.ordinal()] = 2.0D;
        values[MudPhysicsParameter.ADHESION_STRAND_MAX_COUNT.ordinal()] = 5.0D;
        values[MudPhysicsParameter.ADHESION_STRAND_SPAWN_HEIGHT.ordinal()] = 0.62D;
        values[MudPhysicsParameter.ADHESION_SHEET_MIN_RIBS.ordinal()] = 4.0D;
        values[MudPhysicsParameter.ADHESION_SHEET_MAX_SPAN.ordinal()] = 1.55D;

        MudBlockProfileStore.migrateLoadedValues(0, SinkingMedium.TAR, values);

        assertEquals(10.0D, value(values, MudPhysicsParameter.ADHESION_STRAND_MIN_COUNT), 1.0E-9D);
        assertEquals(16.0D, value(values, MudPhysicsParameter.ADHESION_STRAND_MAX_COUNT), 1.0E-9D);
        assertEquals(1.45D, value(values, MudPhysicsParameter.ADHESION_STRAND_SPAWN_HEIGHT), 1.0E-9D);
        assertEquals(6.0D, value(values, MudPhysicsParameter.ADHESION_SHEET_MIN_RIBS), 1.0E-9D);
        assertEquals(1.55D, value(values, MudPhysicsParameter.ADHESION_SHEET_MAX_SPAN), 1.0E-9D);
    }

    @Test
    void oldVerticalCycleSlotMigratesToTheNewBodyAnchorLift() {
        double[] values = MudPhysicsProfiles.defaultValues(SinkingMedium.TAR);
        values[MudPhysicsParameter.ADHESION_BODY_ANCHOR_LIFT.ordinal()] = 0.028D;

        MudBlockProfileStore.migrateLoadedValues(1, SinkingMedium.TAR, values);

        assertEquals(0.14D,
                value(values, MudPhysicsParameter.ADHESION_BODY_ANCHOR_LIFT), 1.0E-9D);
    }

    @Test
    void oldTarBreakLengthMigratesWithoutOverwritingCustomLengths() {
        double[] defaults = MudPhysicsProfiles.defaultValues(SinkingMedium.TAR);
        defaults[MudPhysicsParameter.ADHESION_STRAND_BREAK_LENGTH.ordinal()] = 2.90D;
        MudBlockProfileStore.migrateLoadedValues(2, SinkingMedium.TAR, defaults);
        assertEquals(2.00D,
                value(defaults, MudPhysicsParameter.ADHESION_STRAND_BREAK_LENGTH), 1.0E-9D);

        double[] customized = MudPhysicsProfiles.defaultValues(SinkingMedium.TAR);
        customized[MudPhysicsParameter.ADHESION_STRAND_BREAK_LENGTH.ordinal()] = 3.35D;
        MudBlockProfileStore.migrateLoadedValues(2, SinkingMedium.TAR, customized);
        assertEquals(3.35D,
                value(customized, MudPhysicsParameter.ADHESION_STRAND_BREAK_LENGTH), 1.0E-9D);
    }

    @Test
    void previousTarPresentationDefaultsMigrateWithoutOverwritingCustomValues() {
        double[] defaults = MudPhysicsProfiles.defaultValues(SinkingMedium.TAR);
        defaults[MudPhysicsParameter.ADHESION_STRAND_MIN_COUNT.ordinal()] = 6.0D;
        defaults[MudPhysicsParameter.ADHESION_STRAND_MAX_COUNT.ordinal()] = 8.0D;
        defaults[MudPhysicsParameter.ADHESION_STRAND_BREAK_LENGTH.ordinal()] = 4.20D;
        defaults[MudPhysicsParameter.ADHESION_SPAWN_INTERVAL_TICKS.ordinal()] = 3.0D;
        defaults[MudPhysicsParameter.ADHESION_BREAK_CONFIRM_TICKS.ordinal()] = 6.0D;
        MudBlockProfileStore.migrateLoadedValues(3, SinkingMedium.TAR, defaults);
        assertEquals(10.0D, value(defaults, MudPhysicsParameter.ADHESION_STRAND_MIN_COUNT), 1.0E-9D);
        assertEquals(16.0D, value(defaults, MudPhysicsParameter.ADHESION_STRAND_MAX_COUNT), 1.0E-9D);
        assertEquals(2.00D, value(defaults, MudPhysicsParameter.ADHESION_STRAND_BREAK_LENGTH), 1.0E-9D);
        assertEquals(2.0D, value(defaults, MudPhysicsParameter.ADHESION_SPAWN_INTERVAL_TICKS), 1.0E-9D);
        assertEquals(10.0D, value(defaults, MudPhysicsParameter.ADHESION_BREAK_CONFIRM_TICKS), 1.0E-9D);

        double[] customized = defaults.clone();
        customized[MudPhysicsParameter.ADHESION_STRAND_BREAK_LENGTH.ordinal()] = 3.85D;
        MudBlockProfileStore.migrateLoadedValues(3, SinkingMedium.TAR, customized);
        assertEquals(3.85D,
                value(customized, MudPhysicsParameter.ADHESION_STRAND_BREAK_LENGTH), 1.0E-9D);
    }

    @Test
    void legacyGenericAdhesionDefaultsMigrateToTarTemplateWithoutOverwritingCustomValues() {
        double[] values = MudPhysicsProfiles.defaultValues(SinkingMedium.MUD);
        AdhesionStrandProfile.defaultsBeforeSharedTarTemplate(SinkingMedium.MUD)
                .writeTo(values);
        values[MudPhysicsParameter.ADHESION_STRAND_CURVE.ordinal()] = 0.67D;

        MudBlockProfileStore.migrateLoadedValues(5, SinkingMedium.MUD, values);

        double[] expected = MudPhysicsProfiles.defaultValues(SinkingMedium.MUD);
        assertEquals(value(expected, MudPhysicsParameter.ADHESION_STRAND_WIDTH_PIXELS),
                value(values, MudPhysicsParameter.ADHESION_STRAND_WIDTH_PIXELS), 1.0E-9D);
        assertEquals(value(expected, MudPhysicsParameter.ADHESION_GEOMETRIC_ANCHORS),
                value(values, MudPhysicsParameter.ADHESION_GEOMETRIC_ANCHORS), 1.0E-9D);
        assertEquals(0.67D,
                value(values, MudPhysicsParameter.ADHESION_STRAND_CURVE), 1.0E-9D);
        assertEquals(0.0D,
                value(values, MudPhysicsParameter.ADHESION_STRANDS_ENABLED), 1.0E-9D);
        assertEquals(0.0D,
                value(values, MudPhysicsParameter.ADHESION_SHEET_ENABLED), 1.0E-9D);
    }

    @Test
    void oldDroppedItemDepthsMigrateWithoutOverwritingCustomizedValues() {
        for (SinkingMedium medium : SinkingMedium.values()) {
            double[] values = MudPhysicsProfiles.defaultValues(medium);
            DroppedItemPhysicsProfile previous =
                    DroppedItemPhysicsProfile.defaultsBeforeVisibleSettling(medium);
            previous.writeTo(values);

            MudBlockProfileStore.migrateLoadedValues(6, medium, values);

            DroppedItemPhysicsProfile expected = DroppedItemPhysicsProfile.defaultsFor(medium);
            assertEquals(expected.maximumSinkDepth(),
                    value(values, MudPhysicsParameter.ITEM_MAXIMUM_SINK_DEPTH), 1.0E-9D);
            assertEquals(expected.maximumImpactPenetration(),
                    value(values, MudPhysicsParameter.ITEM_MAXIMUM_IMPACT_PENETRATION), 1.0E-9D);
        }

        double[] customized = MudPhysicsProfiles.defaultValues(SinkingMedium.MUD);
        customized[MudPhysicsParameter.ITEM_MAXIMUM_SINK_DEPTH.ordinal()] = 0.73D;
        customized[MudPhysicsParameter.ITEM_MAXIMUM_IMPACT_PENETRATION.ordinal()] = 0.31D;

        MudBlockProfileStore.migrateLoadedValues(6, SinkingMedium.MUD, customized);

        assertEquals(0.73D,
                value(customized, MudPhysicsParameter.ITEM_MAXIMUM_SINK_DEPTH), 1.0E-9D);
        assertEquals(0.31D,
                value(customized, MudPhysicsParameter.ITEM_MAXIMUM_IMPACT_PENETRATION), 1.0E-9D);
    }

    private static double value(double[] values, MudPhysicsParameter parameter) {
        return values[parameter.ordinal()];
    }
}

package com.fish.mirebound.mud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MudSinkingDepthControlTest {
    @Test
    void effectiveDepthUsesTheMoreRestrictiveLegacyLimit() {
        assertEquals(0.45D, MudSinkingDepthControl.maximumDepth(0.25D, 0.10D), 1.0E-9D);
        assertEquals(0.70D, MudSinkingDepthControl.maximumDepth(0.80D, 0.30D), 1.0E-9D);
    }

    @Test
    void simpleAndAdvancedDepthConfigurationsRemainIndependent() {
        double[] values = MudPhysicsProfiles.defaultValues(SinkingMedium.SOFT_QUICKSAND);
        double advancedDepth = MudSinkingDepthControl.maximumDepth(
                values[MudPhysicsParameter.MAX_DEPTH_FACTOR.ordinal()],
                values[MudPhysicsParameter.COLUMN_MARGIN.ordinal()]);
        values[MudPhysicsParameter.SIMPLE_MAXIMUM_SINKING_DEPTH.ordinal()] = 0.37D;
        values[MudPhysicsParameter.SIMPLE_NATURAL_SINKING_DEPTH.ordinal()] = 0.29D;

        assertEquals(0.37D, MudSinkingDepthControl.selectedMaximumDepth(values), 1.0E-9D);
        assertEquals(0.29D, MudSinkingDepthControl.simpleNaturalDepth(values), 1.0E-9D);

        values[MudPhysicsParameter.SINKING_DEPTH_CONTROL_MODE.ordinal()] =
                MudSinkingDepthControl.Mode.ADVANCED.parameterValue();

        assertEquals(advancedDepth, MudSinkingDepthControl.selectedMaximumDepth(values), 1.0E-9D);
        assertEquals(0.37D, MudSinkingDepthControl.simpleMaximumDepth(values), 1.0E-9D);
        assertEquals(0.29D, MudSinkingDepthControl.simpleNaturalDepth(values), 1.0E-9D);
    }

    @Test
    void naturalDepthCannotExceedTheMaximum() {
        double[] values = MudPhysicsProfiles.defaultValues(SinkingMedium.MUD);
        values[MudPhysicsParameter.SIMPLE_MAXIMUM_SINKING_DEPTH.ordinal()] = 0.40D;
        values[MudPhysicsParameter.SIMPLE_NATURAL_SINKING_DEPTH.ordinal()] = 0.75D;

        assertEquals(0.40D, MudSinkingDepthControl.simpleNaturalDepth(values), 1.0E-9D);
    }

    @Test
    void legacySimpleLocalProfileMigratesWithoutChangingAdvancedValues() {
        double[] values = MudPhysicsProfiles.defaultValues(SinkingMedium.SOFT_QUICKSAND);
        values[MudPhysicsParameter.MAX_DEPTH_FACTOR.ordinal()] = 0.25D;
        values[MudPhysicsParameter.COLUMN_MARGIN.ordinal()] = 0.10D;
        values[MudPhysicsParameter.SIMPLE_MAXIMUM_SINKING_DEPTH.ordinal()] = 0.90D;

        MudBlockProfileStore.migrateLoadedValues(7, SinkingMedium.SOFT_QUICKSAND, values);

        assertEquals(0.45D,
                values[MudPhysicsParameter.SIMPLE_MAXIMUM_SINKING_DEPTH.ordinal()], 1.0E-9D);
        assertEquals(0.25D,
                values[MudPhysicsParameter.MAX_DEPTH_FACTOR.ordinal()], 1.0E-9D);
        assertEquals(0.10D,
                values[MudPhysicsParameter.COLUMN_MARGIN.ordinal()], 1.0E-9D);
    }
}

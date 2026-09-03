package com.fish.mirebound.mud.flow;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fish.mirebound.mud.MudPhysicsParameter;
import com.fish.mirebound.mud.MudPhysicsProfiles;
import com.fish.mirebound.mud.SinkingMedium;
import org.junit.jupiter.api.Test;

class MudBlockMotionModeTest {
    @Test
    void nativeMediaDefaultToNoAutomaticBlockMotion() {
        for (SinkingMedium medium : SinkingMedium.values()) {
            double[] values = MudPhysicsProfiles.defaultValues(medium);
            assertFalse(enabled(values, MudPhysicsParameter.FLOW_ENABLED), medium.name());
            assertFalse(enabled(values, MudPhysicsParameter.GRAVITY_FALLING_ENABLED), medium.name());
        }
    }

    @Test
    void gravityModeIsClassifiedWithFiniteVolumeFlow() {
        assertTrue(MudPhysicsParameter.GRAVITY_FALLING_ENABLED.isFiniteVolumeFlowParameter());
    }

    @Test
    void enablingGravityDisablesFiniteFlow() {
        double[] values = defaultsWithBothModesEnabled();
        boolean[] changed = new boolean[MudPhysicsParameter.values().length];
        changed[MudPhysicsParameter.GRAVITY_FALLING_ENABLED.ordinal()] = true;

        MudBlockMotionMode.enforceExclusive(values, changed);

        assertTrue(MudBlockMotionMode.gravityEnabled(values));
        assertFalse(enabled(values, MudPhysicsParameter.FLOW_ENABLED));
    }

    @Test
    void enablingFiniteFlowDisablesGravity() {
        double[] values = defaultsWithBothModesEnabled();
        boolean[] changed = new boolean[MudPhysicsParameter.values().length];
        changed[MudPhysicsParameter.FLOW_ENABLED.ordinal()] = true;

        MudBlockMotionMode.enforceExclusive(values, changed);

        assertTrue(MudFlowProfile.fromValues(values).enabled());
        assertFalse(enabled(values, MudPhysicsParameter.GRAVITY_FALLING_ENABLED));
    }

    @Test
    void invalidPersistedCombinationDeterministicallyPrefersFiniteFlow() {
        double[] values = defaultsWithBothModesEnabled();

        MudBlockMotionMode.enforceExclusive(values);

        assertEquals(1.0D, values[MudPhysicsParameter.FLOW_ENABLED.ordinal()]);
        assertEquals(0.0D, values[MudPhysicsParameter.GRAVITY_FALLING_ENABLED.ordinal()]);
    }

    @Test
    void fallingProfileValuesRoundTripWithoutChangingTheirBits() {
        double[] values = MudPhysicsProfiles.defaultValues(SinkingMedium.MUD);
        values[MudPhysicsParameter.GRAVITY_FALLING_ENABLED.ordinal()] = 1.0D;
        values[MudPhysicsParameter.FLOW_ENABLED.ordinal()] = 0.0D;
        long[] packed = MudGravitySystem.pack(values);

        assertArrayEquals(packed, MudGravitySystem.pack(
                MudGravitySystem.unpack(packed)));
        assertTrue(enabled(MudGravitySystem.unpack(packed),
                MudPhysicsParameter.GRAVITY_FALLING_ENABLED));
    }

    @Test
    void oversizedFallingProfileIsBoundedToKnownParameters() {
        long[] oversized = new long[MudPhysicsParameter.COUNT + 1024];

        assertEquals(MudPhysicsParameter.COUNT,
                MudGravitySystem.unpack(oversized).length);
    }

    private static double[] defaultsWithBothModesEnabled() {
        double[] values = MudPhysicsProfiles.defaultValues(SinkingMedium.MUD);
        values[MudPhysicsParameter.FLOW_ENABLED.ordinal()] = 1.0D;
        values[MudPhysicsParameter.GRAVITY_FALLING_ENABLED.ordinal()] = 1.0D;
        return values;
    }

    private static boolean enabled(double[] values, MudPhysicsParameter parameter) {
        return values[parameter.ordinal()] >= 0.5D;
    }
}

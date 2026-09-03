package com.fish.mirebound.adaptive;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fish.mirebound.mud.MudPhysicsParameter;
import com.fish.mirebound.mud.MudSinkingDepthControl;
import org.junit.jupiter.api.Test;

class AdaptiveMudBehaviorSettingsTest {
    @Test
    void migratesOnlyThePreviousSurfaceClosingDefault() {
        int parameter = MudPhysicsParameter.SURFACE_CLOSE_TICKS.ordinal();
        double[] previousDefault = AdaptiveMudBehaviorSettings.defaults();
        previousDefault[parameter] = 90.0D;
        AdaptiveMudBehaviorSettings.migrateLoadedValues(0, previousDefault);
        assertEquals(35.0D, previousDefault[parameter]);

        double[] customized = AdaptiveMudBehaviorSettings.defaults();
        customized[parameter] = 72.0D;
        AdaptiveMudBehaviorSettings.migrateLoadedValues(0, customized);
        assertEquals(72.0D, customized[parameter]);
    }

    @Test
    void migratesTheLegacySimpleDepthWithoutChangingAdvancedValues() {
        double[] values = AdaptiveMudBehaviorSettings.defaults();
        values[MudPhysicsParameter.MAX_DEPTH_FACTOR.ordinal()] = 0.30D;
        values[MudPhysicsParameter.COLUMN_MARGIN.ordinal()] = 0.20D;

        AdaptiveMudBehaviorSettings.migrateLoadedValues(1, values);

        assertEquals(MudSinkingDepthControl.maximumDepth(0.30D, 0.20D),
                values[MudPhysicsParameter.SIMPLE_MAXIMUM_SINKING_DEPTH.ordinal()], 1.0E-9D);
        assertEquals(0.30D,
                values[MudPhysicsParameter.MAX_DEPTH_FACTOR.ordinal()], 1.0E-9D);
        assertEquals(0.20D,
                values[MudPhysicsParameter.COLUMN_MARGIN.ordinal()], 1.0E-9D);
    }
}

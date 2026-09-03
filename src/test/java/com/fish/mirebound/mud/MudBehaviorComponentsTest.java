package com.fish.mirebound.mud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fish.mirebound.adaptive.AdaptiveMudBehaviorSettings;
import org.junit.jupiter.api.Test;

class MudBehaviorComponentsTest {
    @Test
    void emptyCompositionIsAnExactNeutralLayer() {
        MudBehaviorComponents components = MudBehaviorComponents.NONE;

        assertEquals(0.0D,
                components.additionalSinkDrive(0.2D, 0.8D, true, 0.5D, 0.05D),
                1.0E-12D);
        assertEquals(1.0D, components.yieldMultiplier(0.7D, 0.5D), 1.0E-12D);
        assertEquals(1.0D, components.viscosityMultiplier(0.7D, 0.5D), 1.0E-12D);
        assertEquals(1.0D, components.walkMultiplier(0.7D), 1.0E-12D);
        assertEquals(1.0D, components.verticalMultiplier(0.7D), 1.0E-12D);
        assertEquals(1.0D, components.struggleMultiplier(0.7D), 1.0E-12D);
    }

    @Test
    void granularCollapseRespondsToDisturbanceInsteadOfConstantlyPullingDown() {
        MudBehaviorComponents granular = new MudBehaviorComponents(1.0D, 0.0D, 0.0D);

        double calm = granular.additionalSinkDrive(0.0D, 0.0D, false, 0.35D, 0.05D);
        double moving = granular.additionalSinkDrive(0.16D, 0.65D, true, 0.35D, 0.05D);

        assertTrue(moving > calm * 5.0D);
        assertTrue(granular.yieldMultiplier(0.5D, 0.8D) < 1.0D);
    }

    @Test
    void cohesiveAndAdhesiveComponentsCanBeCombinedContinuously() {
        MudBehaviorComponents mud = new MudBehaviorComponents(0.0D, 0.8D, 0.0D);
        MudBehaviorComponents oil = new MudBehaviorComponents(0.0D, 0.0D, 1.0D);
        MudBehaviorComponents mixture = new MudBehaviorComponents(0.0D, 0.8D, 1.0D);

        assertTrue(mixture.viscosityMultiplier(0.8D, 0.0D)
                > mud.viscosityMultiplier(0.8D, 0.0D));
        assertTrue(mixture.walkMultiplier(0.7D) < mud.walkMultiplier(0.7D));
        assertTrue(mixture.struggleMultiplier(0.8D) < oil.struggleMultiplier(0.8D));
    }

    @Test
    void adaptiveProfilesRetainIndependentSpecialTemplateSwitches() {
        double[] values = AdaptiveMudBehaviorSettings.defaults();
        values[MudPhysicsParameter.TENTACLE_ENABLED.ordinal()] = 1.0D;
        values[MudPhysicsParameter.SWARM_ENABLED.ordinal()] = 1.0D;
        values[MudPhysicsParameter.SCULK_ENABLED.ordinal()] = 1.0D;
        values[MudPhysicsParameter.FLESH_ENABLED.ordinal()] = 1.0D;
        MudBlockProfileStore.Profile profile =
                MudBlockProfileStore.Profile.createAdaptive(values);

        assertEquals(1.0D, profile.value(MudPhysicsParameter.TENTACLE_ENABLED));
        assertEquals(1.0D, profile.value(MudPhysicsParameter.SWARM_ENABLED));
        assertEquals(1.0D, profile.value(MudPhysicsParameter.SCULK_ENABLED));
        assertEquals(1.0D, profile.value(MudPhysicsParameter.FLESH_ENABLED));
        assertFalse(MudPhysicsParameter.SLIME_VERTICAL_SPRING.appliesToAdaptive());
    }

    @Test
    void nativeTemplateDefaultsPreserveExistingSpecialMediaOnly() {
        assertEquals(1.0D, MudPhysicsProfiles.tentacleDefaultValues()[
                MudPhysicsParameter.TENTACLE_ENABLED.ordinal()]);
        assertEquals(0.0D, value(SinkingMedium.MIRE,
                MudPhysicsParameter.TENTACLE_ENABLED));
        assertEquals(1.0D, value(SinkingMedium.INSECT_MOUND,
                MudPhysicsParameter.SWARM_ENABLED));
        assertEquals(1.0D, value(SinkingMedium.SCULK_MIRE,
                MudPhysicsParameter.SCULK_ENABLED));
        assertEquals(1.0D, value(SinkingMedium.TENDER_FLESH,
                MudPhysicsParameter.FLESH_ENABLED));
        assertEquals(0.0D, value(SinkingMedium.MUD,
                MudPhysicsParameter.FLESH_ENABLED));
    }

    @Test
    void heightOnlyShapesDoNotCountAsSpecialBlockOverrides() {
        assertFalse(MudBlockProfileStore.shapeCountsAsModified(
                MudBlockVariant.DEFAULT));
        assertFalse(MudBlockProfileStore.shapeCountsAsModified(
                MudBlockVariant.HEIGHT));
        assertTrue(MudBlockProfileStore.shapeCountsAsModified(
                MudBlockVariant.SPECIAL));
    }

    private static double value(SinkingMedium medium, MudPhysicsParameter parameter) {
        return MudPhysicsProfiles.defaultValues(medium)[parameter.ordinal()];
    }
}

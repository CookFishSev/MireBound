package com.fish.mirebound.eruption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fish.mirebound.mud.MudPhysicsParameter;
import com.fish.mirebound.mud.MudPhysicsProfiles;
import com.fish.mirebound.mud.MudPhysicsSettings;
import com.fish.mirebound.mud.SinkingMedium;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class MudEruptionDynamicsTest {
    @Test
    void defaultDimensionCapacityAllowsFortyEightVents() {
        assertEquals(48, MudPhysicsSettings.eruptionMaximumActivePerLevel());
    }

    @Test
    void ventsAreAnOptInTemplateForEveryMedium() {
        for (SinkingMedium medium : SinkingMedium.values()) {
            MudEruptionProfile profile = MudEruptionProfile.fromValues(
                    MudPhysicsProfiles.defaultValues(medium));
            assertTrue(!profile.spawning().enabled());
            assertTrue(profile.continuous().enabled());
            assertEquals(80, profile.continuous().particleLifetimeTicks());
            assertTrue(profile.surges().enabled());
            assertTrue(profile.spawning().maximumRadiusPixels()
                    >= profile.spawning().minimumRadiusPixels());
            assertTrue(profile.surges().maximumHeight() >= profile.surges().minimumHeight());
            for (Direction face : Direction.values()) {
                assertTrue(profile.spawning().allows(face));
            }
        }
    }

    @Test
    void eachEruptionFaceCanBeDisabledIndependently() {
        double[] values = MudPhysicsProfiles.defaultValues(SinkingMedium.MUD);
        values[MudPhysicsParameter.ERUPTION_FACE_NORTH_ENABLED.ordinal()] = 0.0D;
        MudEruptionProfile.SpawnSettings spawning =
                MudEruptionProfile.fromValues(values).spawning();

        assertFalse(spawning.allows(Direction.NORTH));
        for (Direction face : Direction.values()) {
            if (face != Direction.NORTH) {
                assertTrue(spawning.allows(face));
            }
        }
    }

    @Test
    void continuousFlowIsSmallSmoothAndStillSizeBounded() {
        MudEruptionProfile profile = MudEruptionProfile.fromValues(
                MudPhysicsProfiles.defaultValues(SinkingMedium.MUD));

        MudEruptionDynamics.Burst small = MudEruptionDynamics.continuousBurst(
                profile, profile.spawning().minimumRadiusPixels(), 0.55D, 0.5D, 1.0D);
        MudEruptionDynamics.Burst large = MudEruptionDynamics.continuousBurst(
                profile, profile.spawning().maximumRadiusPixels(), 0.55D, 0.5D, 1.0D);
        MudEruptionDynamics.Burst lowerFlow = MudEruptionDynamics.continuousBurst(
                profile, profile.spawning().maximumRadiusPixels(), 0.10D, 0.5D, 0.5D);
        MudEruptionDynamics.Burst higherFlow = MudEruptionDynamics.continuousBurst(
                profile, profile.spawning().maximumRadiusPixels(), 0.90D, 0.5D, 0.5D);

        assertTrue(large.height() > small.height());
        assertTrue(large.droplets() >= small.droplets());
        assertTrue(higherFlow.height() > lowerFlow.height());
        assertTrue(large.droplets() >= Math.round(
                profile.continuous().minimumDroplets() * profile.continuous().volumeScale()));
        assertTrue(large.droplets() <= Math.round(
                profile.continuous().maximumDroplets() * profile.continuous().volumeScale()));
        assertTrue(large.height() <= profile.continuous().maximumHeight()
                * profile.continuous().heightScale());
    }

    @Test
    void continuousAndPulseOutputsUseIndependentSettings() {
        double[] values = MudPhysicsProfiles.defaultValues(SinkingMedium.MUD);
        values[MudPhysicsParameter.ERUPTION_POWER_SCALE.ordinal()] = 4.0D;
        values[MudPhysicsParameter.ERUPTION_VOLUME_SCALE.ordinal()] = 4.0D;
        MudEruptionProfile pulseBoosted = MudEruptionProfile.fromValues(values);
        MudEruptionDynamics.Burst continuous = MudEruptionDynamics.continuousBurst(
                pulseBoosted, pulseBoosted.spawning().maximumRadiusPixels(),
                1.0D, 1.0D, 1.0D);

        values[MudPhysicsParameter.ERUPTION_POWER_SCALE.ordinal()] = 0.25D;
        values[MudPhysicsParameter.ERUPTION_VOLUME_SCALE.ordinal()] = 0.25D;
        MudEruptionProfile pulseReduced = MudEruptionProfile.fromValues(values);
        MudEruptionDynamics.Burst unchangedContinuous = MudEruptionDynamics.continuousBurst(
                pulseReduced, pulseReduced.spawning().maximumRadiusPixels(),
                1.0D, 1.0D, 1.0D);

        assertEquals(continuous.height(), unchangedContinuous.height(), 1.0E-9D);
        assertEquals(continuous.droplets(), unchangedContinuous.droplets());
    }

    @Test
    void everyEruptionSettingBelongsToExactlyOneVentPage() {
        for (MudPhysicsParameter parameter : MudPhysicsParameter.values()) {
            if (parameter.category() != MudPhysicsParameter.Category.ERUPTION_VENTS) {
                continue;
            }
            assertTrue(parameter.subcategory()
                    == MudPhysicsParameter.Subcategory.ERUPTION_SPAWNING
                    || parameter.subcategory()
                    == MudPhysicsParameter.Subcategory.ERUPTION_CONTINUOUS
                    || parameter.subcategory()
                    == MudPhysicsParameter.Subcategory.ERUPTION_SURGES);
        }
    }

    @Test
    void profileGroupsMatchTheThreeTuningPages() {
        MudEruptionProfile profile = MudEruptionProfile.fromValues(
                MudPhysicsProfiles.defaultValues(SinkingMedium.MUD));

        assertTrue(profile.spawning().spawnAttempts() >= 1);
        assertTrue(profile.continuous().intervalTicks() >= 1);
        assertTrue(profile.surges().durationTicks() >= 1);
    }

    @Test
    void activeVentBudgetIsDimensionWideAndStillHardBounded() {
        assertTrue(MudEruptionSpawner.hasCapacity(11, 12));
        assertFalse(MudEruptionSpawner.hasCapacity(12, 12));
        assertFalse(MudEruptionSpawner.hasCapacity(96, 120));
        assertFalse(MudEruptionSpawner.hasCapacity(0, 0));
    }

    @Test
    void largeVentRaisesOnlyTheRandomOutputCeiling() {
        double[] values = MudPhysicsProfiles.defaultValues(SinkingMedium.MUD);
        values[MudPhysicsParameter.ERUPTION_ENABLED.ordinal()] = 1.0D;
        MudEruptionProfile profile = MudEruptionProfile.fromValues(values);

        MudEruptionDynamics.Burst smallHigh = MudEruptionDynamics.burst(
                profile, profile.spawning().minimumRadiusPixels(), 1.0D, 1.0D);
        MudEruptionDynamics.Burst largeLow = MudEruptionDynamics.burst(
                profile, profile.spawning().maximumRadiusPixels(), 0.0D, 0.0D);
        MudEruptionDynamics.Burst largeHigh = MudEruptionDynamics.burst(
                profile, profile.spawning().maximumRadiusPixels(), 1.0D, 1.0D);

        assertTrue(largeHigh.height() > smallHigh.height());
        assertTrue(largeHigh.droplets() > smallHigh.droplets());
        assertEquals(profile.surges().minimumHeight() * profile.surges().powerScale(),
                largeLow.height(), 1.0E-9D);
        assertEquals(Math.round(
                profile.surges().minimumDroplets() * profile.surges().volumeScale()),
                largeLow.droplets());
    }

    @Test
    void configuredHeightConvertsToBoundedBallisticLaunchSpeed() {
        double low = MudEruptionDynamics.launchSpeed(0.35D, 0.04D);
        double high = MudEruptionDynamics.launchSpeed(1.40D, 0.04D);
        assertTrue(low > 0.0D);
        assertTrue(high > low);
        assertTrue(high < 1.0D);
    }

    @Test
    void localCandidateAttemptsIncreaseChanceWithoutExceedingOne() {
        double one = MudEruptionDynamics.combinedAttemptChance(0.10D, 1);
        double four = MudEruptionDynamics.combinedAttemptChance(0.10D, 4);
        assertEquals(0.10D, one, 1.0E-9D);
        assertTrue(four > one && four < 1.0D);
        assertEquals(1.0D, MudEruptionDynamics.combinedAttemptChance(1.0D, 8), 1.0E-9D);
    }

    @Test
    void crossingUsesTheCircularVentFootprintAndSurfacePlane() {
        AABB player = new AABB(0.70D, 1.0D, 0.35D, 1.30D, 2.80D, 0.95D);
        Vec3 origin = new Vec3(0.50D, 1.0D, 0.50D);
        assertTrue(MudEruptionSystem.intersectsPlayerFootprint(player, origin, 0.25D));
        assertFalse(MudEruptionSystem.intersectsPlayerFootprint(
                player.move(0.40D, 0.0D, 0.40D), origin, 0.25D));
        assertFalse(MudEruptionSystem.intersectsPlayerFootprint(
                player.move(0.0D, 0.30D, 0.0D), origin, 0.25D));
    }
}

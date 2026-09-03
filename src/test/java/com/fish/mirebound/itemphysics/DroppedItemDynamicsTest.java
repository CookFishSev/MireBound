package com.fish.mirebound.itemphysics;

import com.fish.mirebound.mud.MudPhysicsParameter;
import com.fish.mirebound.mud.MudPhysicsProfiles;
import com.fish.mirebound.mud.SinkingMedium;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DroppedItemDynamicsTest {
    private static final DroppedItemPhysicsProfile PROFILE =
            new DroppedItemPhysicsProfile(
                    true, 0.45D, 0.020D, 0.25D,
                    0.90D, 0.20D, 0.20D, 0.18D, 0.25D, 0.08D,
                    true, 6, 24.0D, 0.018D, 0.18D, 0.30D, 0.65D);

    @Test
    void mediumDefaultsProduceDistinctSettlingBehavior() {
        DroppedItemPhysicsProfile mud = DroppedItemPhysicsProfile.defaultsFor(SinkingMedium.MUD);
        DroppedItemPhysicsProfile tar = DroppedItemPhysicsProfile.defaultsFor(SinkingMedium.TAR);

        assertNotEquals(mud.maximumSinkDepth(), tar.maximumSinkDepth());
        assertNotEquals(mud.submergedHorizontalRetention(), tar.submergedHorizontalRetention());
        assertTrue(mud.stablePresentationEnabled());
        assertEquals(6, mud.presentationSettleTicks());
    }

    @Test
    void currentDefaultsProduceVisibleSettlingWithoutExceedingTuningBounds() {
        for (SinkingMedium medium : SinkingMedium.values()) {
            DroppedItemPhysicsProfile previous =
                    DroppedItemPhysicsProfile.defaultsBeforeVisibleSettling(medium);
            DroppedItemPhysicsProfile current = DroppedItemPhysicsProfile.defaultsFor(medium);

            assertTrue(current.maximumSinkDepth() > previous.maximumSinkDepth(),
                    medium + " did not gain visible settling depth");
            assertTrue(current.maximumSinkDepth() >= 0.14D);
            assertTrue(current.maximumSinkDepth() <= 0.55D);
            assertTrue(current.maximumImpactPenetration() >= 0.055D);
            assertTrue(current.maximumImpactPenetration() <= 0.22D);
        }
    }

    @Test
    void availableColumnAndImpactBothCapEntryDepth() {
        assertEquals(0.10D, DroppedItemDynamics.maximumDepth(PROFILE, 0.10D + 1.0D / 64.0D),
                1.0E-9D);
        assertEquals(0.18D, DroppedItemDynamics.entryDepth(0.02D, 4.0D, 0.45D, PROFILE),
                1.0E-9D);
        assertEquals(0.12D, DroppedItemDynamics.entryDepth(0.02D, 4.0D, 0.12D, PROFILE),
                1.0E-9D);
    }

    @Test
    void existingPenetrationDoesNotSnapBackToImpactCap() {
        assertEquals(0.32D, DroppedItemDynamics.entryDepth(0.32D, 0.10D, 0.45D, PROFILE),
                1.0E-9D);
    }

    @Test
    void deepEntryStaysInsideTheColumnWithoutSkippingBuoyantReturn() {
        double depth = DroppedItemDynamics.anchoredEntryDepth(
                1.20D, 0.10D, 0.45D, 1.0D, PROFILE);

        assertEquals(1.0D - 1.0D / 64.0D, depth,
                1.0E-9D);
        assertTrue(depth > PROFILE.maximumSinkDepth());
        assertEquals(0.18D,
                DroppedItemDynamics.anchoredEntryDepth(
                        0.02D, 4.0D, 0.45D, 1.0D, PROFILE),
                1.0E-9D);
    }

    @Test
    void settlingCannotOvershootTheRemainingDepth() {
        double velocity = DroppedItemDynamics.settlingDepthSpeed(0.20D, 0.003D, PROFILE);

        assertEquals(0.003D, velocity, 1.0E-9D);
        assertEquals(0.0D,
                DroppedItemDynamics.settlingDepthSpeed(0.04D, 0.0D, PROFILE), 1.0E-9D);
    }

    @Test
    void sideAndUndersideContactsReturnAlongTheirActualSurfaceNormal() {
        Vec3 north = new Vec3(0.0D, 0.0D, -1.0D);
        Vec3 underside = new Vec3(0.0D, -1.0D, 0.0D);
        Vec3 sidePosition = new Vec3(0.5D, 0.5D, 0.85D);
        Vec3 undersidePosition = new Vec3(0.5D, 0.75D, 0.5D);

        assertEquals(0.35D,
                DroppedItemDynamics.depth(sidePosition, north, -0.50D), 1.0E-9D);
        assertEquals(0.50D,
                DroppedItemDynamics.depth(undersidePosition, underside, -0.25D), 1.0E-9D);

        Vec3 returnedSide = DroppedItemDynamics.positionAtDepth(sidePosition, north, -0.50D, 0.18D);
        Vec3 returnedUnderside = DroppedItemDynamics.positionAtDepth(
                undersidePosition, underside, -0.25D, 0.18D);
        assertEquals(0.68D, returnedSide.z, 1.0E-9D);
        assertEquals(0.43D, returnedUnderside.y, 1.0E-9D);
    }

    @Test
    void rotatedSableDepthUsesTheWorldVerticalAxis() {
        Vec3 localWorldUp = new Vec3(0.6D, 0.8D, 0.0D);
        Vec3 surfaceNormal = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 position = new Vec3(0.0D, 0.60D, 0.0D);

        assertEquals(0.50D, DroppedItemDynamics.depthFromSurfacePlane(
                position, surfaceNormal, 1.0D, localWorldUp), 1.0E-9D);
        Vec3 returned = DroppedItemDynamics.positionAtDepthAlongAxis(
                position, surfaceNormal, 1.0D, localWorldUp, 0.20D);
        assertEquals(0.18D, returned.x, 1.0E-9D);
        assertEquals(0.84D, returned.y, 1.0E-9D);
        assertEquals(0.20D, DroppedItemDynamics.depthFromSurfacePlane(
                returned, surfaceNormal, 1.0D, localWorldUp), 1.0E-9D);
    }

    @Test
    void buoyantReturnMovesOutwardWithoutOvershootingTheTargetDepth() {
        double speed = DroppedItemDynamics.buoyantReturnDepthSpeed(-0.08D, 0.004D, PROFILE);
        assertTrue(speed < 0.0D);
        assertTrue(speed > -0.004D);
        double distantSpeed = DroppedItemDynamics.buoyantReturnDepthSpeed(0.0D, 0.30D, PROFILE);
        double nearSpeed = DroppedItemDynamics.buoyantReturnDepthSpeed(0.0D, 0.03D, PROFILE);
        assertTrue(Math.abs(distantSpeed) > Math.abs(nearSpeed));
        assertEquals(0.0D,
                DroppedItemDynamics.buoyantReturnDepthSpeed(0.0D, 0.0D, PROFILE), 1.0E-9D);
    }

    @Test
    void deeperImmersionRetainsLessHorizontalMotion() {
        double surface = DroppedItemDynamics.horizontalRetention(0.0D, 0.25D, PROFILE);
        double middle = DroppedItemDynamics.horizontalRetention(0.125D, 0.25D, PROFILE);
        double submerged = DroppedItemDynamics.horizontalRetention(0.25D, 0.25D, PROFILE);

        assertTrue(surface > middle);
        assertTrue(middle > submerged);
        assertEquals(PROFILE.surfaceHorizontalRetention(), surface, 1.0E-9D);
        assertEquals(PROFILE.submergedHorizontalRetention(), submerged, 1.0E-9D);
    }

    @Test
    void disabledProfileRoundTripsThroughEditableValues() {
        double[] values = MudPhysicsProfiles.defaultValues(SinkingMedium.SOFT_QUICKSAND);
        values[MudPhysicsParameter.ITEM_PHYSICS_ENABLED.ordinal()] = 0.0D;
        DroppedItemPhysicsProfile disabled = DroppedItemPhysicsProfile.fromValues(values);
        double[] roundTrip = new double[MudPhysicsParameter.COUNT];
        disabled.writeTo(roundTrip);

        assertFalse(disabled.enabled());
        assertEquals(0.0D, roundTrip[MudPhysicsParameter.ITEM_PHYSICS_ENABLED.ordinal()]);
        assertEquals(disabled.maximumSinkDepth(),
                roundTrip[MudPhysicsParameter.ITEM_MAXIMUM_SINK_DEPTH.ordinal()], 1.0E-9D);
    }
}

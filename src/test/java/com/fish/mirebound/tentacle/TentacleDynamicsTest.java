package com.fish.mirebound.tentacle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fish.mirebound.mud.MudPhysicsParameter;
import com.fish.mirebound.mud.MudPhysicsProfiles;
import com.fish.mirebound.mud.SinkingMedium;
import java.util.HashSet;
import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class TentacleDynamicsTest {
    private static final double RADIUS = 0.10D;

    @Test
    void directPathUsesOnlyItsEndpoints() {
        AabbTentacleCollisionSpace collision = space();
        Vec3 start = new Vec3(0.0D, 0.0D, 0.0D);
        Vec3 goal = new Vec3(2.0D, 0.0D, 0.0D);

        List<Vec3> path = TentaclePathfinder.find(start, goal, collision,
                RADIUS, 0.25D, 1.0D, 2048);

        assertEquals(List.of(start, goal), path);
    }

    @Test
    void pathfinderRoutesAroundSolidGeometry() {
        AabbTentacleCollisionSpace collision = space(
                new AABB(0.85D, -0.65D, -0.65D, 1.15D, 0.65D, 0.65D));
        Vec3 start = new Vec3(0.0D, 0.0D, 0.0D);
        Vec3 goal = new Vec3(2.0D, 0.0D, 0.0D);

        List<Vec3> path = TentaclePathfinder.find(start, goal, collision,
                RADIUS, 0.25D, 1.25D, 4096);

        assertFalse(path.isEmpty());
        assertTrue(path.size() > 2);
        assertEquals(start, path.getFirst());
        assertEquals(goal, path.getLast());
        for (int index = 1; index < path.size(); index++) {
            assertTrue(collision.clear(path.get(index - 1), path.get(index), RADIUS));
        }
    }

    @Test
    void blockedEndpointsInSameGridCellStillRequireARealRoute() {
        AabbTentacleCollisionSpace collision = space(
                new AABB(0.145D, -0.08D, -0.08D, 0.155D, 0.08D, 0.08D));
        Vec3 start = new Vec3(0.10D, 0.0D, 0.0D);
        Vec3 goal = new Vec3(0.20D, 0.0D, 0.0D);

        List<Vec3> path = TentaclePathfinder.find(start, goal, collision,
                0.01D, 0.50D, 0.50D, 2048);

        assertFalse(path.isEmpty());
        assertTrue(path.size() > 2, () -> "unsafe direct path=" + path);
        for (int index = 1; index < path.size(); index++) {
            assertTrue(collision.clear(path.get(index - 1), path.get(index), 0.01D));
        }
    }

    @Test
    void sweptMovementStopsBeforeAThinWall() {
        AabbTentacleCollisionSpace collision = space(
                new AABB(0.90D, -1.0D, -1.0D, 0.94D, 1.0D, 1.0D));

        Vec3 result = collision.move(Vec3.ZERO, new Vec3(2.0D, 0.0D, 0.0D), RADIUS);

        assertTrue(result.x < 0.80D);
        assertTrue(collision.clear(result, RADIUS));
    }

    @Test
    void xpbdChainConvergesOnConfiguredSegmentLength() {
        TentaclePhysicsProfile profile = profile();
        TentacleChainSolver chain = new TentacleChainSolver(profile.segmentCount(), Vec3.ZERO);
        List<Vec3> guide = List.of(Vec3.ZERO, new Vec3(0.0D, 1.40D, 0.0D));

        for (int tick = 0; tick < 120; tick++) {
            chain.step(profile, Vec3.ZERO, 1.0D, 1.0D, guide, space(), tick, 42L);
        }

        List<Vec3> points = chain.snapshot();
        for (int index = 1; index < points.size(); index++) {
            assertEquals(profile.segmentLength(), points.get(index - 1).distanceTo(points.get(index)), 0.025D);
        }
    }

    @Test
    void bendGuardAllowsATautTrackingChainToBecomeStraight() {
        TentaclePhysicsProfile profile = profile();
        Vec3 root = Vec3.ZERO;
        Vec3 goal = new Vec3(profile.maximumLength(), 0.0D, 0.0D);
        List<Vec3> guide = List.of(root, goal);
        TentacleChainSolver chain = new TentacleChainSolver(profile.segmentCount(), root);
        chain.initializeAlongPath(profile, guide, space(), profile.maximumLength());

        for (int tick = 0; tick < 100; tick++) {
            chain.step(profile, root, 1.0D, 1.0D, guide,
                    profile.trackingTipAdvanceSpeed(), space(), tick, 42L,
                    Vec3.ZERO, 0.0D);
        }

        Vec3 tip = chain.point(chain.pointCount() - 1);
        assertTrue(tip.distanceTo(root) >= profile.maximumLength() * 0.97D,
                () -> "taut bend guard kept the chain curled: tip=" + tip
                        + " reach=" + tip.distanceTo(root)
                        + " maximum=" + profile.maximumLength());
        Vec3 firstSegment = chain.point(1).subtract(root).normalize();
        assertTrue(firstSegment.dot(goal.normalize()) > 0.98D,
                () -> "root segment still orbits instead of aligning: " + firstSegment);
    }

    @Test
    void requestedTrackingScaleChangesThePhysicalRestLength() {
        TentaclePhysicsProfile profile = profile();
        TentacleChainSolver chain = new TentacleChainSolver(profile.segmentCount(), Vec3.ZERO);
        double lengthScale = profile.trackingMaximumStretch();
        List<Vec3> guide = List.of(Vec3.ZERO,
                new Vec3(profile.maximumLength() * 2.0D, 0.0D, 0.0D));
        chain.initializeAlongPath(profile, guide, space(),
                profile.maximumLength() * lengthScale);

        for (int tick = 0; tick < 80; tick++) {
            chain.step(profile, Vec3.ZERO, 1.0D, lengthScale, guide,
                    profile.trackingTipAdvanceSpeed(), space(), tick, 42L,
                    Vec3.ZERO, 1.0D);
        }

        List<Vec3> points = chain.snapshot();
        double totalLength = 0.0D;
        for (int index = 1; index < points.size(); index++) {
            double segmentLength = points.get(index - 1).distanceTo(points.get(index));
            totalLength += segmentLength;
            assertEquals(profile.segmentLength() * lengthScale,
                    segmentLength, 0.025D);
        }
        assertEquals(profile.maximumLength() * lengthScale, totalLength, 0.06D);
    }

    @Test
    void trackingLengthUsesDistanceInsteadOfTrailReleaseThreshold() {
        assertEquals(0.97D, TentacleSystem.trackingLengthScale(
                10.0D, 9.7D, 1.6D), 1.0E-9D);
        assertEquals(TentacleChainSolver.MINIMUM_TRACKING_LENGTH_SCALE,
                TentacleSystem.trackingLengthScale(
                        10.0D, 2.0D, 1.6D), 1.0E-9D);
        assertEquals(1.4D, TentacleSystem.trackingLengthScale(
                10.0D, 14.0D, 1.6D), 1.0E-9D);
        assertEquals(1.6D, TentacleSystem.trackingLengthScale(
                10.0D, 20.0D, 1.6D), 1.0E-9D);
        assertEquals(1.06D, TentacleSystem.approachLengthScale(
                1.0D, 1.6D, 0.10D, 1.6D), 1.0E-9D);
        assertEquals(0.96D, TentacleSystem.approachLengthScale(
                1.0D, 0.60D, 0.10D, 1.6D), 1.0E-9D);
    }

    @Test
    void respawnedPlayerEntityCannotInheritAnOldEntityGrab() {
        Object oldPlayerEntity = new Object();
        Object respawnedPlayerEntity = new Object();

        assertFalse(TentacleSystem.grabbedEntityWasReplaced(
                oldPlayerEntity, oldPlayerEntity));
        assertFalse(TentacleSystem.grabbedEntityWasReplaced(
                null, respawnedPlayerEntity));
        assertTrue(TentacleSystem.grabbedEntityWasReplaced(
                oldPlayerEntity, respawnedPlayerEntity));
    }

    @Test
    void holdGravityOverridePreventsContinuedChainSag() {
        TentaclePhysicsProfile profile = profile();
        Vec3 root = new Vec3(0.0D, 2.0D, 0.0D);
        List<Vec3> guide = List.of(root, root.add(profile.maximumLength(), 0.0D, 0.0D));
        TentacleChainSolver held = new TentacleChainSolver(profile.segmentCount(), root);
        TentacleChainSolver ordinary = new TentacleChainSolver(profile.segmentCount(), root);
        held.initializeAlongPath(profile, guide, space(), profile.maximumLength());
        ordinary.initializeAlongPath(profile, guide, space(), profile.maximumLength());

        for (int tick = 0; tick < 80; tick++) {
            held.step(profile, root, 1.0D, 1.0D, guide,
                    profile.tipAdvanceSpeed(), space(), tick, 42L, Vec3.ZERO, 0.0D);
            ordinary.step(profile, root, 1.0D, 1.0D, guide,
                    profile.tipAdvanceSpeed(), space(), tick, 42L, Vec3.ZERO, 1.0D);
        }

        double heldAverageY = held.snapshot().stream().skip(1)
                .mapToDouble(point -> point.y).average().orElseThrow();
        double ordinaryAverageY = ordinary.snapshot().stream().skip(1)
                .mapToDouble(point -> point.y).average().orElseThrow();
        assertTrue(heldAverageY >= root.y,
                () -> "held chain sagged below root: " + heldAverageY);
        assertTrue(ordinaryAverageY < heldAverageY - 0.015D,
                () -> "held=" + heldAverageY + " ordinary=" + ordinaryAverageY);
    }

    @Test
    void chainNodesAreProjectedOutOfCollisionBoxes() {
        TentaclePhysicsProfile profile = profile();
        AabbTentacleCollisionSpace collision = space(
                new AABB(-0.45D, 0.20D, -0.45D, 0.45D, 1.15D, 0.45D));
        TentacleChainSolver chain = new TentacleChainSolver(profile.segmentCount(), Vec3.ZERO);
        List<Vec3> guide = List.of(
                Vec3.ZERO,
                new Vec3(0.75D, 0.0D, 0.0D),
                new Vec3(0.75D, 1.40D, 0.0D));

        for (int tick = 0; tick < 160; tick++) {
            chain.step(profile, Vec3.ZERO, 1.0D, 1.0D, guide, collision, tick, 42L);
        }

        List<Vec3> points = chain.snapshot();
        for (int index = 1; index < points.size(); index++) {
            assertTrue(collision.clear(points.get(index), profile.radiusAt(index / (double) (points.size() - 1))));
        }
    }

    @Test
    void everyChainSegmentStaysOutsideAOneBlockPillar() {
        TentaclePhysicsProfile profile = profile();
        AabbTentacleCollisionSpace collision = space(
                new AABB(0.70D, -1.0D, -0.50D, 1.30D, 2.0D, 0.50D));
        Vec3 root = new Vec3(0.0D, 0.45D, 0.0D);
        Vec3 goal = new Vec3(2.0D, 0.45D, 0.0D);
        List<Vec3> guide = TentaclePathfinder.find(root, goal, collision,
                profile.tipPathClearance(), profile.pathCellSize(), 1.5D, 4096);
        assertFalse(guide.isEmpty());
        TentacleChainSolver chain = new TentacleChainSolver(profile.segmentCount(), root);

        for (int tick = 0; tick < 180; tick++) {
            chain.step(profile, root, 1.0D, 1.0D, guide,
                    profile.trackingTipAdvanceSpeed(), collision, tick, 42L);
            assertChainSegmentsClear(chain, profile, collision);
        }
    }

    @Test
    void rotatedSableLocalSpaceBlocksWorldSweeps() {
        AabbTentacleCollisionSpace localCollision = space(
                new AABB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D));
        SableTentacleCollisionSpace.RigidTransform transform =
                new SableTentacleCollisionSpace.RigidTransform() {
                    @Override
                    public Vec3 toLocal(Vec3 point) {
                        return new Vec3(-point.z, point.y, point.x - 10.0D);
                    }

                    @Override
                    public Vec3 toWorld(Vec3 point) {
                        return new Vec3(10.0D + point.z, point.y, -point.x);
                    }
                };
        TentacleCollisionSpace collision = SableTentacleCollisionSpace.combine(
                space(), List.of(new SableTentacleCollisionSpace.LocalSpace(
                        transform, localCollision)));
        Vec3 from = new Vec3(9.0D, 0.5D, -0.5D);
        Vec3 desired = new Vec3(12.0D, 0.5D, -0.5D);

        assertFalse(collision.clear(from, desired, 0.10D));
        Vec3 resolved = collision.move(from, desired, 0.10D);
        assertTrue(resolved.x < 9.91D, () -> "resolved=" + resolved);
        assertTrue(collision.clear(resolved, 0.10D));
    }

    @Test
    void heldPlayerCapsuleCannotCrossAVanillaWall() {
        TentacleCollisionSpace collision = space(
                new AABB(1.0D, -1.0D, -2.0D, 2.0D, 3.0D, 2.0D));
        AABB player = new AABB(-0.30D, 0.0D, -0.30D,
                0.30D, 1.80D, 0.30D);

        Vec3 resolved = TentacleHeldPlayerCollision.constrainVelocity(
                player, new Vec3(2.0D, 0.0D, 0.55D), collision, 0.12D);

        assertTrue(resolved.x < 0.70D, () -> "resolved=" + resolved);
        assertTrue(resolved.z > 0.30D, () -> "wall should allow tangential slide: " + resolved);
    }

    @Test
    void heldPlayerCapsuleCannotCrossASableWall() {
        AabbTentacleCollisionSpace localCollision = space(
                new AABB(1.0D, -1.0D, -2.0D, 2.0D, 3.0D, 2.0D));
        SableTentacleCollisionSpace.RigidTransform identity =
                new SableTentacleCollisionSpace.RigidTransform() {
                    @Override
                    public Vec3 toLocal(Vec3 point) {
                        return point;
                    }

                    @Override
                    public Vec3 toWorld(Vec3 point) {
                        return point;
                    }
                };
        TentacleCollisionSpace collision = SableTentacleCollisionSpace.combine(
                space(), List.of(new SableTentacleCollisionSpace.LocalSpace(
                        identity, localCollision)));
        AABB player = new AABB(-0.30D, 0.0D, -0.30D,
                0.30D, 1.80D, 0.30D);

        Vec3 resolved = TentacleHeldPlayerCollision.constrainVelocity(
                player, new Vec3(2.0D, 0.0D, 0.0D), collision, 0.12D);

        assertTrue(resolved.x < 0.70D, () -> "resolved=" + resolved);
    }

    @Test
    void grabCollisionCacheCoversMaximumMovementUntilRefresh() {
        double reach = TentacleHeldPlayerCollision.captureReach(
                1.80D, 0.12D, 0.72D, 8.0D, 3);
        double maximumTravel = 0.72D * Math.sqrt(Math.pow(8.0D, 0.55D)) * 3.0D;

        assertTrue(reach >= 1.80D * 1.05D + 0.12D + maximumTravel - 1.0E-8D);
    }

    @Test
    void grabbedTentacleGoalCannotLeadTheActualGripThroughAVanillaWall() {
        TentacleCollisionSpace collision = space(
                new AABB(1.0D, -1.0D, -2.0D, 2.0D, 3.0D, 2.0D));
        Vec3 grip = new Vec3(0.0D, 0.5D, 0.0D);

        TentacleGrabTether.Result result = TentacleGrabTether.constrain(
                grip, new Vec3(3.0D, 0.5D, 0.6D),
                collision, 2.5D, 0.10D);

        assertTrue(result.goal().x < 0.90D, () -> "goal=" + result.goal());
        assertTrue(result.goal().z > 0.0D,
                () -> "wall contact should preserve tangential travel: " + result.goal());
        assertTrue(result.correction() > 1.0D);
    }

    @Test
    void grabbedTentacleGoalCannotLeadTheActualGripThroughASableWall() {
        AabbTentacleCollisionSpace localCollision = space(
                new AABB(1.0D, -1.0D, -2.0D, 2.0D, 3.0D, 2.0D));
        SableTentacleCollisionSpace.RigidTransform identity =
                new SableTentacleCollisionSpace.RigidTransform() {
                    @Override
                    public Vec3 toLocal(Vec3 point) {
                        return point;
                    }

                    @Override
                    public Vec3 toWorld(Vec3 point) {
                        return point;
                    }
                };
        TentacleCollisionSpace collision = SableTentacleCollisionSpace.combine(
                space(), List.of(new SableTentacleCollisionSpace.LocalSpace(
                        identity, localCollision)));
        Vec3 grip = new Vec3(0.0D, 0.5D, 0.0D);

        TentacleGrabTether.Result result = TentacleGrabTether.constrain(
                grip, new Vec3(3.0D, 0.5D, 0.6D),
                collision, 2.5D, 0.10D);

        assertTrue(result.goal().x < 0.90D, () -> "goal=" + result.goal());
    }

    @Test
    void grabbedTentacleLeadIsBoundedEvenWithoutAnObstacle() {
        double maximumLead = TentacleGrabTether.maximumLead(
                0.60D, 0.10D, 0.08D, 0.72D, 1.0D);
        TentacleGrabTether.Result result = TentacleGrabTether.constrain(
                Vec3.ZERO, new Vec3(8.0D, 0.0D, 0.0D),
                space(), maximumLead, 0.10D);

        assertEquals(maximumLead, result.goal().length(), 1.0E-8D);
        assertTrue(result.requestedLead() > maximumLead);
    }

    @Test
    void tentacleCannotAcquireAGrabThroughAVanillaWall() {
        TentacleCollisionSpace collision = space(
                new AABB(0.40D, -1.0D, -1.0D, 0.60D, 2.0D, 1.0D));

        assertFalse(TentacleGrabTether.contactClear(
                Vec3.ZERO, new Vec3(1.0D, 0.0D, 0.0D),
                collision, 0.02D));
        assertTrue(TentacleGrabTether.contactClear(
                Vec3.ZERO, new Vec3(0.25D, 0.0D, 0.0D),
                collision, 0.02D));
    }

    @Test
    void tentacleCannotAcquireAGrabThroughASableWall() {
        AabbTentacleCollisionSpace localCollision = space(
                new AABB(0.40D, -1.0D, -1.0D, 0.60D, 2.0D, 1.0D));
        SableTentacleCollisionSpace.RigidTransform identity =
                new SableTentacleCollisionSpace.RigidTransform() {
                    @Override
                    public Vec3 toLocal(Vec3 point) {
                        return point;
                    }

                    @Override
                    public Vec3 toWorld(Vec3 point) {
                        return point;
                    }
                };
        TentacleCollisionSpace collision = SableTentacleCollisionSpace.combine(
                space(), List.of(new SableTentacleCollisionSpace.LocalSpace(
                        identity, localCollision)));

        assertFalse(TentacleGrabTether.contactClear(
                Vec3.ZERO, new Vec3(1.0D, 0.0D, 0.0D),
                collision, 0.02D));
    }

    @Test
    void stuckRecoveryUsesProgressivelyThickerFollowerClearance() {
        TentaclePhysicsProfile profile = profile();
        TentacleChainSolver chain = new TentacleChainSolver(profile.segmentCount(), Vec3.ZERO);
        double previous = TentacleSystem.recoveryPathClearance(chain, profile, 0);

        for (int step = 1; step <= profile.stuckClearanceSteps(); step++) {
            double current = TentacleSystem.recoveryPathClearance(chain, profile, step);
            assertTrue(current >= previous, "clearance decreased at recovery step");
            previous = current;
        }
        assertTrue(previous > profile.tipPathClearance());
    }

    @Test
    void impossibleRootCollisionRemainsFiniteWithoutRejectingEveryFrame() {
        TentaclePhysicsProfile profile = profile();
        Vec3 root = Vec3.ZERO;
        TentacleChainSolver chain = new TentacleChainSolver(profile.segmentCount(), root);
        List<Vec3> guide = List.of(root, new Vec3(profile.maximumLength(), 0.0D, 0.0D));
        chain.initializeAlongPath(profile, guide, space(), profile.maximumLength());
        AabbTentacleCollisionSpace blockedRoot = space(
                new AABB(-0.20D, -0.20D, -0.20D, 0.20D, 0.80D, 0.20D));
        for (int tick = 0; tick < 160; tick++) {
            chain.step(profile, root, 1.0D, 1.0D, guide,
                    profile.trackingTipAdvanceSpeed(), blockedRoot, tick, 712L);
            assertFalse(chain.stepRejected());
        }

        double arcLength = TentacleChainSolver.pathLength(chain.snapshot());
        assertTrue(Double.isFinite(arcLength));
        assertTrue(arcLength <= profile.maximumLength() * 1.50D,
                () -> "arcLength=" + arcLength);
    }

    @Test
    void midBodyRigidObstacleRepairsLocallyInsteadOfFreezingTheWholeChain() {
        TentaclePhysicsProfile profile = profile();
        Vec3 root = Vec3.ZERO;
        TentacleChainSolver chain = new TentacleChainSolver(profile.segmentCount(), root);
        List<Vec3> straight = List.of(root, new Vec3(profile.maximumLength(), 0.0D, 0.0D));
        chain.initializeAlongPath(profile, straight, space(), profile.maximumLength());
        List<Vec3> before = chain.snapshot();
        AabbTentacleCollisionSpace obstacle = space(
                new AABB(0.52D, -0.28D, -0.28D, 0.92D, 0.28D, 0.28D));
        List<Vec3> detour = List.of(root,
                new Vec3(0.35D, 0.0D, 0.0D),
                new Vec3(0.70D, 0.55D, 0.0D),
                new Vec3(profile.maximumLength(), 0.55D, 0.0D));

        double movement = 0.0D;
        Vec3 previousTip = chain.point(chain.pointCount() - 1);
        for (int tick = 0; tick < 120; tick++) {
            chain.step(profile, root, 1.0D, 1.0D, detour,
                    profile.trackingTipAdvanceSpeed(), obstacle, tick, 913L);
            assertFalse(chain.stepRejected());
            Vec3 tip = chain.point(chain.pointCount() - 1);
            movement += tip.distanceTo(previousTip);
            previousTip = tip;
        }

        assertFalse(before.equals(chain.snapshot()));
        double arcLength = TentacleChainSolver.pathLength(chain.snapshot());
        assertTrue(Double.isFinite(arcLength));
        assertTrue(arcLength <= profile.maximumLength() * 1.35D,
                () -> "arcLength=" + arcLength);
        assertTrue(movement > 0.10D, "tip movement=" + movement);
    }

    @Test
    void emergingChainDoesNotBounceThroughFrameRejection() {
        TentaclePhysicsProfile profile = profile();
        Vec3 root = Vec3.ZERO;
        TentacleChainSolver chain = new TentacleChainSolver(profile.segmentCount(), root);
        List<Vec3> guide = List.of(root,
                new Vec3(0.45D, 0.55D, 0.0D),
                new Vec3(1.20D, 1.35D, 0.25D));
        chain.initializeAlongPath(profile, guide, space(), profile.maximumLength() * 0.02D);

        double extension = 0.0D;
        for (int tick = 0; tick < 80; tick++) {
            extension = Math.min(1.0D, extension + 0.055D);
            chain.step(profile, root, extension, 1.0D, guide,
                    Math.max(profile.tipAdvanceSpeed(), profile.maximumLength() * 0.055D),
                    space(), tick, 1017L);
            assertFalse(chain.stepRejected(), "frame rejected at tick " + tick);
            for (Vec3 point : chain.snapshot()) {
                assertTrue(Double.isFinite(point.x) && Double.isFinite(point.y)
                        && Double.isFinite(point.z));
            }
        }
    }

    @Test
    void largerRootsScaleToClearlyLongerBodies() {
        TentaclePhysicsProfile base = TentaclePhysicsProfile.fromValues(
                MudPhysicsProfiles.tentacleDefaultValues());

        TentaclePhysicsProfile small = base.scaledForVolume(1.0D, 1234L);
        TentaclePhysicsProfile large = base.scaledForVolume(8.0D, 1234L);

        assertTrue(large.maximumLength() > small.maximumLength() * 2.50D);
        assertTrue(large.rootRadius() > small.rootRadius() * 1.90D);
        assertTrue(large.tipRadius() > small.tipRadius() * 1.90D);
        assertTrue(large.segmentCount() > small.segmentCount());
    }

    @Test
    void nearbyTrackingGoalDoesNotShortenThePhysicalChain() {
        TentaclePhysicsProfile profile = profile();
        TentacleChainSolver chain = new TentacleChainSolver(profile.segmentCount(), Vec3.ZERO);
        List<Vec3> nearbyGuide = List.of(Vec3.ZERO, new Vec3(0.30D, 0.35D, 0.0D));

        for (int tick = 0; tick < 180; tick++) {
            chain.step(profile, Vec3.ZERO, 1.0D, 1.0D, nearbyGuide, space(), tick, 77L);
        }

        List<Vec3> points = chain.snapshot();
        double bodyLength = 0.0D;
        for (int index = 1; index < points.size(); index++) {
            bodyLength += points.get(index - 1).distanceTo(points.get(index));
        }
        assertEquals(profile.maximumLength(), bodyLength, 0.08D);
    }

    @Test
    void terminalDirectionConstraintCanTurnAHeldTipDownward() {
        TentaclePhysicsProfile profile = profile();
        TentacleChainSolver chain = new TentacleChainSolver(profile.segmentCount(), Vec3.ZERO);
        Vec3 target = new Vec3(0.0D, profile.maximumLength() * 0.62D, 0.0D);
        List<Vec3> guide = List.of(Vec3.ZERO, target);
        chain.initializeAlongPath(profile, guide, space(), profile.maximumLength());

        for (int tick = 0; tick < 180; tick++) {
            chain.step(profile, Vec3.ZERO, 1.0D, 1.0D, guide,
                    profile.trackingTipAdvanceSpeed(), space(), tick, 73L,
                    new Vec3(0.0D, -1.0D, 0.0D));
        }

        int tip = chain.pointCount() - 1;
        Vec3 terminal = chain.point(tip).subtract(chain.point(tip - 1)).normalize();
        assertTrue(terminal.y < -0.20D, () -> "terminal=" + terminal);
        assertFalse(chain.stepRejected());
    }

    @Test
    void individualThicknessAndLengthVariationStayCorrelated() {
        TentaclePhysicsProfile base = TentaclePhysicsProfile.fromValues(
                MudPhysicsProfiles.tentacleDefaultValues());

        for (long firstSeed = 1L; firstSeed <= 24L; firstSeed++) {
            TentaclePhysicsProfile first = base.scaledForVolume(1.0D, firstSeed);
            for (long secondSeed = firstSeed + 1L; secondSeed <= 24L; secondSeed++) {
                TentaclePhysicsProfile second = base.scaledForVolume(1.0D, secondSeed);
                if (first.rootRadius() > second.rootRadius() + 1.0E-9D) {
                    assertTrue(first.maximumLength() > second.maximumLength());
                } else if (second.rootRadius() > first.rootRadius() + 1.0E-9D) {
                    assertTrue(second.maximumLength() > first.maximumLength());
                }
            }
        }
    }

    @Test
    void morphologyIsStableForASeedAndClampsMaximumVolume() {
        TentaclePhysicsProfile base = TentaclePhysicsProfile.fromValues(
                MudPhysicsProfiles.tentacleDefaultValues());

        TentaclePhysicsProfile first = base.scaledForVolume(3.0D, 987654321L);
        TentaclePhysicsProfile repeated = base.scaledForVolume(3.0D, 987654321L);
        TentaclePhysicsProfile capped = base.scaledForVolume(base.maximumVolume() * 2.0D, 987654321L);
        TentaclePhysicsProfile maximum = base.scaledForVolume(base.maximumVolume(), 987654321L);

        assertEquals(first, repeated);
        assertEquals(maximum, capped);
    }

    @Test
    void trackingTipMovesFasterAcrossAllSupportedVolumes() {
        TentaclePhysicsProfile base = TentaclePhysicsProfile.fromValues(
                MudPhysicsProfiles.tentacleDefaultValues());

        for (double volume : new double[] {0.125D, 1.0D, 8.0D, 50.0D}) {
            TentaclePhysicsProfile scaled = base.scaledForVolume(volume, 42L);
            assertTrue(scaled.trackingTipAdvanceSpeed() > scaled.tipAdvanceSpeed());
        }
    }

    @Test
    void defaultIdleRangeCanApproachWithoutCollapsingIntoTheRoot() {
        TentaclePhysicsProfile profile = TentaclePhysicsProfile.fromValues(
                MudPhysicsProfiles.tentacleDefaultValues());

        assertTrue(profile.idleMinimumReach() >= 0.10D);
        assertTrue(profile.idleMaximumReach() >= 0.85D);
    }

    @Test
    void tipRoutePrefixAdvancesWithoutExposingTheWholePath() {
        List<Vec3> route = List.of(Vec3.ZERO,
                new Vec3(1.0D, 0.0D, 0.0D), new Vec3(1.0D, 1.0D, 0.0D));

        List<Vec3> prefix = TentacleChainSolver.trimPath(route, 0.65D);

        assertEquals(2, prefix.size());
        assertEquals(new Vec3(0.65D, 0.0D, 0.0D), prefix.getLast());
    }

    @Test
    void tipControllerAcceleratesAndBrakesWithoutPositionSteps() {
        TentacleTipController controller = new TentacleTipController();
        List<Vec3> route = List.of(Vec3.ZERO, new Vec3(2.0D, 0.0D, 0.0D));
        controller.reset(Vec3.ZERO);
        double previousDistance = 0.0D;
        double previousSpeed = 0.0D;
        boolean observedBraking = false;

        for (int tick = 0; tick < 40 && controller.distance() < 2.0D; tick++) {
            controller.advance(route, 0.24D, 0.04D, 0.40D,
                    controller.position(), 100.0D, space(), 0.02D);
            assertTrue(controller.distance() >= previousDistance);
            assertTrue(Math.abs(controller.speed() - previousSpeed) <= 0.0400001D);
            if (controller.speed() + 1.0E-8D < previousSpeed) {
                observedBraking = true;
            }
            previousDistance = controller.distance();
            previousSpeed = controller.speed();
        }

        assertEquals(2.0D, controller.distance(), 1.0E-6D);
        assertTrue(observedBraking);
    }

    @Test
    void routeRemapPreservesTipVelocity() {
        TentacleTipController controller = new TentacleTipController();
        List<Vec3> original = List.of(Vec3.ZERO, new Vec3(3.0D, 0.0D, 0.0D));
        controller.reset(Vec3.ZERO);
        controller.advance(original, 0.30D, 0.05D, 0.40D,
                controller.position(), 100.0D, space(), 0.02D);
        controller.advance(original, 0.30D, 0.05D, 0.40D,
                controller.position(), 100.0D, space(), 0.02D);
        double speed = controller.speed();
        Vec3 exposedTip = controller.position();

        List<Vec3> replacement = List.of(Vec3.ZERO,
                new Vec3(1.5D, 0.08D, 0.0D), new Vec3(3.0D, 0.20D, 0.0D));
        controller.rebind(replacement, exposedTip, false);

        assertEquals(speed, controller.speed(), 1.0E-9D);
        assertEquals(exposedTip, controller.position());
        assertTrue(controller.distance() > 0.0D);
    }

    @Test
    void physicalGuideIncludesTheUntravelledTrackingRoute() {
        Vec3 root = Vec3.ZERO;
        Vec3 controller = new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 goal = new Vec3(8.0D, 0.0D, 0.0D);
        List<Vec3> trail = List.of(root, controller);
        List<Vec3> planned = List.of(root, goal);

        List<Vec3> guide = TentacleGuidePath.compose(
                trail, planned, controller, 1.0D, space(), 0.04D, 12.0D);

        assertEquals(root, guide.getFirst());
        assertEquals(goal, guide.getLast());
        assertEquals(8.0D, TentacleChainSolver.pathLength(guide), 1.0E-9D);
    }

    @Test
    void physicalGuideBoundsLongPlansToMaximumReach() {
        Vec3 root = Vec3.ZERO;
        Vec3 controller = new Vec3(0.5D, 0.0D, 0.0D);
        List<Vec3> trail = List.of(root, controller);
        List<Vec3> planned = List.of(root, new Vec3(20.0D, 0.0D, 0.0D));

        List<Vec3> guide = TentacleGuidePath.compose(
                trail, planned, controller, 0.5D, space(), 0.04D, 4.0D);

        assertEquals(4.0D, TentacleChainSolver.pathLength(guide), 1.0E-9D);
        assertEquals(new Vec3(4.0D, 0.0D, 0.0D), guide.getLast());
        assertTrue(guide.size() <= trail.size() + planned.size());
    }

    @Test
    void completedGuideReusesTheCachedTrailSnapshot() {
        Vec3 goal = new Vec3(4.0D, 0.0D, 0.0D);
        List<Vec3> trail = List.of(Vec3.ZERO, goal);
        List<Vec3> planned = List.of(Vec3.ZERO, goal);

        List<Vec3> guide = TentacleGuidePath.compose(
                trail, planned, goal, 4.0D, space(), 0.04D, 8.0D);

        assertSame(trail, guide);
    }

    @Test
    void leadLimitDoesNotPullAnExposedControllerBackwards() {
        Vec3 physicalTip = Vec3.ZERO;
        Vec3 current = new Vec3(1.0D, 0.0D, 0.0D);

        Vec3 blockedAdvance = TentacleTipController.limitLeadWithoutRetreat(
                current, new Vec3(1.2D, 0.0D, 0.0D), physicalTip, 0.25D);
        Vec3 permittedReturn = TentacleTipController.limitLeadWithoutRetreat(
                current, new Vec3(0.80D, 0.0D, 0.0D), physicalTip, 0.25D);
        Vec3 newlyCapped = TentacleTipController.limitLeadWithoutRetreat(
                new Vec3(0.10D, 0.0D, 0.0D),
                new Vec3(0.40D, 0.0D, 0.0D), physicalTip, 0.25D);

        assertEquals(current, blockedAdvance);
        assertEquals(new Vec3(0.80D, 0.0D, 0.0D), permittedReturn);
        assertEquals(new Vec3(0.25D, 0.0D, 0.0D), newlyCapped);
    }

    @Test
    void composedPursuitSettlesWithoutAControllerRetreatLoop() {
        double[] values = MudPhysicsProfiles.tentacleDefaultValues();
        values[MudPhysicsParameter.TENTACLE_GUIDE_STRENGTH.ordinal()] = 0.10D;
        values[MudPhysicsParameter.TENTACLE_TIP_ACCELERATION.ordinal()] = 0.045D;
        values[MudPhysicsParameter.TENTACLE_SOLVER_STRETCH_LIMIT.ordinal()] = 1.01D;
        TentaclePhysicsProfile profile = TentaclePhysicsProfile.fromValues(values)
                .scaledForVolume(6.0D, 991L);
        Vec3 root = Vec3.ZERO;
        Vec3 goal = new Vec3(profile.maximumLength() * 0.62D, 0.35D, 0.0D);
        List<Vec3> planned = List.of(root, goal);
        TentacleChainSolver chain = new TentacleChainSolver(profile.segmentCount(), root);
        List<Vec3> initial = List.of(
                root, new Vec3(0.0D, profile.maximumLength(), 0.0D));
        chain.initializeAlongPath(profile, initial, space(), profile.maximumLength());
        chain.setDiagnosticsEnabled(true);
        TentacleTipController controller = new TentacleTipController();
        controller.reset(root);
        TentacleTrail trail = new TentacleTrail();
        trail.reset(root);
        double previousControllerDistance = 0.0D;
        double maximumControllerRetreat = 0.0D;
        double maximumSettledTurn = 0.0D;
        double maximumSettledError = 0.0D;
        int maximumTurnTick = -1;
        TentacleChainSolver.StepDiagnostics maximumTurnDiagnostics = null;
        Vec3 previousRootDirection = chain.point(1).subtract(root).normalize();

        for (int tick = 0; tick < 260; tick++) {
            controller.advance(planned, profile.trackingTipAdvanceSpeed(),
                    profile.tipAcceleration(), profile.tipLookaheadDistance(),
                    chain.point(chain.pointCount() - 1),
                    profile.tipMaximumLeadDistance(), space(), profile.tipPathClearance());
            maximumControllerRetreat = Math.max(maximumControllerRetreat,
                    previousControllerDistance - controller.distance());
            previousControllerDistance = controller.distance();
            assertTrue(trail.record(root, controller.position(), space(),
                    profile.tipPathClearance(), profile.trailSampleDistance(),
                    profile.trailUnwrapTicks(), profile.trailMaximumPoints()));
            List<Vec3> guide = TentacleGuidePath.compose(
                    trail.path(), planned, controller.position(), controller.distance(),
                    space(), profile.tipPathClearance(), profile.maximumLength());
            assertTrue(TentacleChainSolver.pathLength(guide)
                            >= root.distanceTo(goal) - 1.0E-8D,
                    "future route disappeared at tick " + tick);
            chain.step(profile, root, 1.0D, 1.0D, guide,
                    profile.trackingTipAdvanceSpeed(), space(), tick, 991L);

            Vec3 rootDirection = chain.point(1).subtract(root).normalize();
            if (tick >= 140) {
                double turn = angleBetween(rootDirection, previousRootDirection);
                if (turn > maximumSettledTurn) {
                    maximumSettledTurn = turn;
                    maximumTurnTick = tick;
                    maximumTurnDiagnostics = chain.diagnostics();
                }
                maximumSettledError = Math.max(maximumSettledError,
                        chain.point(chain.pointCount() - 1).distanceTo(goal));
            }
            previousRootDirection = rootDirection;
        }

        double finalError = chain.point(chain.pointCount() - 1).distanceTo(goal);
        assertTrue(maximumControllerRetreat < profile.segmentLength() * 0.50D,
                "controller retreat=" + maximumControllerRetreat);
        assertTrue(finalError < profile.segmentLength() * 1.50D,
                "final error=" + finalError
                        + " maximumLength=" + profile.maximumLength()
                        + " controller=" + controller.position()
                        + " controllerDistance=" + controller.distance()
                        + " diagnostics=" + chain.diagnostics());
        assertTrue(maximumSettledTurn < Math.toRadians(8.0D),
                "settled root turn=" + Math.toDegrees(maximumSettledTurn)
                        + " tick=" + maximumTurnTick
                        + " diagnostics=" + maximumTurnDiagnostics);
        assertTrue(maximumSettledError < profile.segmentLength() * 2.25D,
                "settled error=" + maximumSettledError);
    }

    @Test
    void tipControllerTurnsGraduallyAroundACorner() {
        TentacleTipController controller = new TentacleTipController();
        List<Vec3> route = List.of(Vec3.ZERO,
                new Vec3(1.0D, 0.0D, 0.0D), new Vec3(1.0D, 0.0D, 1.0D));
        controller.reset(Vec3.ZERO);
        Vec3 previousPosition = controller.position();
        Vec3 previousVelocity = controller.velocity();
        boolean enteredTurn = false;
        boolean retainedForwardInertia = false;

        for (int tick = 0; tick < 60 && controller.distance() < 2.0D - 1.0E-6D; tick++) {
            controller.advance(route, 0.20D, 0.035D, 0.32D,
                    controller.position(), 100.0D, space(), 0.02D);
            Vec3 velocity = controller.velocity();
            assertTrue(velocity.subtract(previousVelocity).length() <= 0.0350001D);
            assertTrue(controller.position().distanceTo(previousPosition) <= 0.2000001D);
            if (controller.position().x > 0.82D && velocity.z > 0.01D) {
                enteredTurn = true;
                retainedForwardInertia |= velocity.x > 0.01D;
            }
            previousPosition = controller.position();
            previousVelocity = velocity;
        }

        assertTrue(enteredTurn);
        assertTrue(retainedForwardInertia);
        assertEquals(route.getLast().x, controller.position().x, 0.04D);
        assertEquals(route.getLast().z, controller.position().z, 0.04D);
    }

    @Test
    void trailPreservesObstacleWrapThenUnwrapsGradually() {
        Vec3 root = new Vec3(-1.0D, 0.0D, 0.0D);
        Vec3 tip = new Vec3(1.0D, 0.0D, 0.0D);
        AabbTentacleCollisionSpace pillar = space(
                new AABB(-0.20D, -0.50D, -0.20D, 0.20D, 0.50D, 0.20D));
        TentacleTrail trail = new TentacleTrail();
        trail.reset(root);

        trail.record(root, new Vec3(-0.55D, 0.0D, 0.45D), pillar, 0.04D, 0.12D, 3, 32);
        trail.record(root, new Vec3(0.0D, 0.0D, 0.52D), pillar, 0.04D, 0.12D, 3, 32);
        trail.record(root, new Vec3(0.55D, 0.0D, 0.45D), pillar, 0.04D, 0.12D, 3, 32);
        trail.record(root, tip, pillar, 0.04D, 0.12D, 3, 32);

        int wrappedPoints = trail.size();
        assertTrue(wrappedPoints >= 3);
        assertFalse(pillar.clear(root, tip, 0.04D));

        AabbTentacleCollisionSpace cleared = space();
        for (int tick = 1; tick <= 6; tick++) {
            int sizeBefore = trail.size();
            trail.record(root, tip, cleared, 0.04D, 0.12D, 3, 32);
            int sizeAfter = trail.size();
            int removed = sizeBefore - sizeAfter;
            assertTrue(removed >= 0 && removed <= 2,
                    "unwrap removed " + removed + " points at tick " + tick);
            if (tick < 3) {
                assertEquals(0, removed, "no unwrap before tick 3");
            }
        }
        assertEquals(2, trail.size(), "trail should collapse to root+tip");
    }

    @Test
    void trailCapacityRejectsUnsafeGrowthInsteadOfExceedingItsLimit() {
        TentacleCollisionSpace shortSegmentsOnly = new TentacleCollisionSpace() {
            @Override
            public Vec3 move(Vec3 from, Vec3 desired, double radius) {
                return desired;
            }

            @Override
            public Vec3 project(Vec3 point, double radius) {
                return point;
            }

            @Override
            public boolean clear(Vec3 from, Vec3 to, double radius) {
                return from.distanceTo(to) <= 0.135D;
            }

            @Override
            public boolean clear(Vec3 point, double radius) {
                return true;
            }
        };
        TentacleTrail trail = new TentacleTrail();
        trail.reset(Vec3.ZERO);
        boolean accepted = true;
        for (int index = 1; index < 30 && accepted; index++) {
            accepted = trail.record(Vec3.ZERO, new Vec3(index * 0.10D, 0.0D, 0.0D),
                    shortSegmentsOnly, 0.01D, 0.05D, 20, 8);
        }

        assertFalse(accepted);
        assertTrue(trail.size() <= 8, () -> "trail size=" + trail.size());
    }

    @Test
    void trailSnapshotsAreReusedUntilTheTrailChanges() {
        TentacleTrail trail = new TentacleTrail();
        trail.reset(Vec3.ZERO);
        List<Vec3> first = trail.path();
        assertSame(first, trail.path());

        trail.record(Vec3.ZERO, new Vec3(0.2D, 0.0D, 0.0D),
                space(), 0.01D, 0.05D, 2, 32);
        List<Vec3> changed = trail.path();
        assertNotSame(first, changed);
        assertSame(changed, trail.path());
    }

    @Test
    void substepNormalizationPreservesConfiguredPerTickFactors() {
        double damping = 0.91D;
        double guide = 0.18D;
        for (int substeps : List.of(1, 2, 4, 8)) {
            assertEquals(damping,
                    Math.pow(TentacleChainSolver.substepDamping(damping, substeps), substeps),
                    1.0E-12D);
            assertEquals(guide,
                    1.0D - Math.pow(1.0D
                            - TentacleChainSolver.substepGuideStrength(guide, substeps), substeps),
                    1.0E-12D);
        }
    }

    @Test
    void staggeredWorkKeepsTheConfiguredInterval() {
        int interval = 8;
        HashSet<Integer> firstTicks = new HashSet<>();
        for (int instanceId = 1; instanceId <= 12; instanceId++) {
            int first = TentacleSystem.nextStaggeredTick(0, instanceId, interval, 0x53);
            int second = TentacleSystem.nextStaggeredTick(
                    first, instanceId, interval, 0x53);
            assertTrue(first >= 1 && first <= interval, "first=" + first);
            assertEquals(interval, second - first);
            firstTicks.add(first);
        }
        assertTrue(firstTicks.size() >= interval / 2,
                "insufficient phase spread=" + firstTicks);
    }

    @Test
    void tentacleTargetCommandIsNotRegistered() {
        var root = TentacleCommands.command().build();

        assertTrue(root.getChild("target") == null);
        assertNotNull(root.getChild("grab"));
        assertNotNull(root.getChild("state"));
    }

    @Test
    void onlyVolumeFiveOrGreaterCanEnableGrabbing() {
        assertFalse(TentacleSystem.grabEligible(4.999999D));
        assertTrue(TentacleSystem.grabEligible(5.0D));
        assertTrue(TentacleSystem.grabEligible(64.0D));
        assertFalse(TentacleSystem.grabEligible(Double.NaN));
        assertFalse(TentacleSystem.grabEligible(Double.POSITIVE_INFINITY));
    }

    @Test
    void configuredTipSegmentCountControlsGrabContactWindow() {
        TentaclePhysicsProfile profile = profile();
        TentacleChainSolver chain = new TentacleChainSolver(profile.segmentCount(), Vec3.ZERO);
        List<Vec3> guide = List.of(Vec3.ZERO,
                new Vec3(profile.maximumLength(), 0.0D, 0.0D));
        chain.initializeAlongPath(profile, guide, space(), profile.maximumLength());
        Vec3 target = chain.point(chain.pointCount() - 3);

        TentacleEntityCollider.GrabContact oneSegment =
                TentacleEntityCollider.grabTargetContact(
                        chain, profile, 1.0D, target, 0.01D, 1, 0.0D);
        TentacleEntityCollider.GrabContact threeSegments =
                TentacleEntityCollider.grabTargetContact(
                        chain, profile, 1.0D, target, 0.01D, 3, 0.0D);

        assertTrue(oneSegment == null);
        assertNotNull(threeSegments);
    }

    @Test
    void selfCollisionSeparatesFoldedNonAdjacentNodes() {
        TentaclePhysicsProfile profile = profile();
        TentacleChainSolver chain = new TentacleChainSolver(profile.segmentCount(), Vec3.ZERO);
        List<Vec3> folded = List.of(
                Vec3.ZERO,
                new Vec3(0.72D, 0.0D, 0.0D),
                new Vec3(0.0D, 0.012D, 0.0D),
                new Vec3(0.72D, 0.024D, 0.0D));
        chain.initializeAlongPath(profile, folded, space(), profile.maximumLength());
        double before = minimumNonAdjacentSegmentDistance(chain.snapshot());

        for (int tick = 0; tick < 40; tick++) {
            chain.step(profile, Vec3.ZERO, 1.0D, 1.0D, folded,
                    profile.trackingTipAdvanceSpeed(), space(), tick, 501L);
        }

        double after = minimumNonAdjacentSegmentDistance(chain.snapshot());
        assertTrue(after > before + 0.015D, () -> "before=" + before + " after=" + after);
        assertEquals(Vec3.ZERO, chain.point(0));
    }

    @Test
    void independentTentaclesResolveCapsuleIntersectionsWithoutMovingTheirRoots() {
        TentaclePhysicsProfile profile = profile();
        Vec3 firstRoot = new Vec3(-0.70D, 0.0D, 0.0D);
        Vec3 secondRoot = new Vec3(0.0D, 0.0D, -0.70D);
        TentacleChainSolver first = new TentacleChainSolver(
                profile.segmentCount(), firstRoot);
        TentacleChainSolver second = new TentacleChainSolver(
                profile.segmentCount(), secondRoot);
        first.initializeAlongPath(profile,
                List.of(firstRoot, new Vec3(0.70D, 0.0D, 0.0D)),
                space(), profile.maximumLength());
        second.initializeAlongPath(profile,
                List.of(secondRoot, new Vec3(0.0D, 0.0D, 0.70D)),
                space(), profile.maximumLength());
        double before = minimumSegmentDistance(first.snapshot(), second.snapshot());

        TentacleInterCollider.Stats stats = TentacleInterCollider.resolve(List.of(
                new TentacleInterCollider.Body(1, first, profile, 1.0D, space(), false),
                new TentacleInterCollider.Body(2, second, profile, 1.0D, space(), false)), 0L);
        double after = minimumSegmentDistance(first.snapshot(), second.snapshot());

        assertTrue(stats.contacts() > 0, () -> "stats=" + stats);
        assertTrue(stats.capsuleTests() > 0, () -> "stats=" + stats);
        assertTrue(after > before + 1.0E-5D,
                () -> "inter-tentacle separation before=" + before + " after=" + after);
        assertEquals(firstRoot, first.point(0));
        assertEquals(secondRoot, second.point(0));
    }

    @Test
    void separatedTentaclesAreRejectedByTheBodyBroadphase() {
        TentaclePhysicsProfile profile = profile();
        TentacleChainSolver first = new TentacleChainSolver(
                profile.segmentCount(), Vec3.ZERO);
        TentacleChainSolver second = new TentacleChainSolver(
                profile.segmentCount(), new Vec3(30.0D, 0.0D, 0.0D));

        TentacleInterCollider.Stats stats = TentacleInterCollider.resolve(List.of(
                new TentacleInterCollider.Body(1, first, profile, 1.0D, space(), false),
                new TentacleInterCollider.Body(2, second, profile, 1.0D, space(), false)), 0L);

        assertEquals(0, stats.candidatePairs());
        assertEquals(0, stats.capsuleTests());
        assertEquals(0, stats.contacts());
    }

    @Test
    void thickStraightBodyDoesNotCollideWithItsOwnContinuousNeighborhood() {
        TentaclePhysicsProfile profile = TentaclePhysicsProfile.fromValues(
                MudPhysicsProfiles.tentacleDefaultValues())
                .scaledForVolume(6.0D, 991L);
        Vec3 root = Vec3.ZERO;
        Vec3 goal = new Vec3(profile.maximumLength(), 0.0D, 0.0D);
        List<Vec3> guide = List.of(root, goal);
        TentacleChainSolver chain = new TentacleChainSolver(profile.segmentCount(), root);
        chain.initializeAlongPath(profile, guide, space(), profile.maximumLength());
        chain.setDiagnosticsEnabled(true);

        chain.step(profile, root, 1.0D, 1.0D, guide,
                profile.trackingTipAdvanceSpeed(), space(), 1.0D, 991L,
                Vec3.ZERO, 0.0D);

        TentacleChainSolver.StepDiagnostics diagnostics = chain.diagnostics();
        assertEquals(0, diagnostics.selfCollisionContacts(),
                () -> "straight body contacts=" + diagnostics);
        assertEquals(0.0D, diagnostics.selfCollisionCorrection(), 1.0E-12D);
    }

    @Test
    void curvatureSmoothingSuppressesAlternatingSegmentWaves() {
        double[] unsmoothedValues = MudPhysicsProfiles.tentacleDefaultValues();
        unsmoothedValues[MudPhysicsParameter.TENTACLE_SEGMENTS.ordinal()] = 14.0D;
        unsmoothedValues[MudPhysicsParameter.TENTACLE_CURVATURE_SMOOTHING.ordinal()] = 0.0D;
        double[] smoothedValues = unsmoothedValues.clone();
        smoothedValues[MudPhysicsParameter.TENTACLE_CURVATURE_SMOOTHING.ordinal()] = 0.45D;
        TentaclePhysicsProfile unsmoothedProfile = TentaclePhysicsProfile.fromValues(unsmoothedValues);
        TentaclePhysicsProfile smoothedProfile = TentaclePhysicsProfile.fromValues(smoothedValues);
        List<Vec3> alternatingGuide = List.of(
                Vec3.ZERO,
                new Vec3(0.45D, 0.35D, 0.24D),
                new Vec3(0.90D, 0.70D, -0.24D),
                new Vec3(1.35D, 1.05D, 0.24D),
                new Vec3(1.80D, 1.40D, -0.24D),
                new Vec3(2.25D, 1.75D, 0.0D));
        TentacleChainSolver unsmoothed = new TentacleChainSolver(
                unsmoothedProfile.segmentCount(), Vec3.ZERO);
        TentacleChainSolver smoothed = new TentacleChainSolver(
                smoothedProfile.segmentCount(), Vec3.ZERO);
        unsmoothed.initializeAlongPath(unsmoothedProfile, alternatingGuide, space(),
                unsmoothedProfile.maximumLength());
        smoothed.initializeAlongPath(smoothedProfile, alternatingGuide, space(),
                smoothedProfile.maximumLength());

        for (int tick = 0; tick < 80; tick++) {
            unsmoothed.step(unsmoothedProfile, Vec3.ZERO, 1.0D, 1.0D,
                    alternatingGuide, space(), tick, 601L);
            smoothed.step(smoothedProfile, Vec3.ZERO, 1.0D, 1.0D,
                    alternatingGuide, space(), tick, 601L);
        }

        double rough = tangentVariation(unsmoothed.snapshot());
        double smooth = tangentVariation(smoothed.snapshot());
        assertTrue(smooth < rough && rough - smooth > 0.01D,
                () -> "rough=" + rough + " smooth=" + smooth);
    }

    @Test
    void capsuleGeometryFindsClosestPointsAcrossSkewSegments() {
        TentacleEntityCollider.SegmentPair pair = TentacleEntityCollider.closestPoints(
                new Vec3(-1.0D, 0.0D, 0.0D), new Vec3(1.0D, 0.0D, 0.0D),
                new Vec3(0.0D, -1.0D, 0.40D), new Vec3(0.0D, 1.0D, 0.40D));

        assertEquals(new Vec3(0.0D, 0.0D, 0.0D), pair.first());
        assertEquals(new Vec3(0.0D, 0.0D, 0.40D), pair.second());
    }

    @Test
    void largerTentaclesPushMoreAndYieldLessWithoutBecomingRigid() {
        double smallForce = TentacleEntityCollider.forceMultiplier(0.65D, 0.82D);
        double largeForce = TentacleEntityCollider.forceMultiplier(2.0D, 0.82D);
        double smallYield = TentacleEntityCollider.deflectionMultiplier(0.65D, 0.90D);
        double largeYield = TentacleEntityCollider.deflectionMultiplier(2.0D, 0.90D);

        assertTrue(largeForce > smallForce);
        assertTrue(largeYield < smallYield);
        assertTrue(largeYield > 0.0D);
    }

    @Test
    void repeatedTentacleContactsCannotAccumulateLaunchVelocity() {
        Vec3 horizontal = Vec3.ZERO;
        Vec3 upward = Vec3.ZERO;
        for (int tick = 0; tick < 20; tick++) {
            horizontal = TentacleEntityCollider.boundedPushVelocity(
                    horizontal, new Vec3(0.18D, 0.0D, 0.0D), 0.18D);
            upward = TentacleEntityCollider.boundedPushVelocity(
                    upward, new Vec3(0.0D, 0.18D, 0.0D), 0.18D);
        }

        assertEquals(0.18D, horizontal.x, 1.0E-8D);
        assertEquals(0.0D, horizontal.y, 1.0E-8D);
        assertTrue(upward.y <= 0.04000001D, "upward push=" + upward);
    }

    @Test
    void contactReactionNeverMovesTheRootNode() {
        TentacleChainSolver chain = new TentacleChainSolver(8, Vec3.ZERO);

        chain.displaceSegment(0, new Vec3(-0.4D, 0.0D, 0.0D), 1.0D);

        assertEquals(Vec3.ZERO, chain.point(0));
        assertTrue(chain.point(1).x < 0.0D);
    }

    @Test
    void finalSegmentAimsAlongThePathTangent() {
        TentaclePhysicsProfile profile = profile();
        TentacleChainSolver chain = new TentacleChainSolver(profile.segmentCount(), Vec3.ZERO);
        List<Vec3> guide = List.of(Vec3.ZERO, new Vec3(1.40D, 0.45D, 0.0D));

        for (int tick = 0; tick < 140; tick++) {
            chain.step(profile, Vec3.ZERO, 1.0D, 1.0D, guide, space(), tick, 91L);
        }

        Vec3 expected = guide.getLast().subtract(guide.getFirst()).normalize();
        Vec3 actual = chain.point(chain.pointCount() - 1)
                .subtract(chain.point(chain.pointCount() - 2)).normalize();
        assertTrue(actual.dot(expected) > 0.72D);
    }

    @Test
    void tipPathClearanceUsesTheTipRatherThanRootThickness() {
        TentaclePhysicsProfile profile = profile();

        assertEquals(profile.tipRadius() * profile.pathTipClearanceScale(),
                profile.tipPathClearance(), 1.0E-9D);
        assertTrue(profile.tipPathClearance() < profile.rootRadius());
    }

    @Test
    void removingAContactDoesNotReleaseStoredProjectionEnergy() {
        TentaclePhysicsProfile profile = profile();
        TentacleChainSolver chain = new TentacleChainSolver(profile.segmentCount(), Vec3.ZERO);
        List<Vec3> guide = List.of(Vec3.ZERO, new Vec3(1.40D, 0.35D, 0.0D));
        AabbTentacleCollisionSpace wall = space(
                new AABB(0.62D, -0.25D, -0.60D, 0.92D, 1.20D, 0.60D));

        for (int tick = 0; tick < 120; tick++) {
            chain.step(profile, Vec3.ZERO, 1.0D, 1.0D, guide, wall, tick, 123L);
        }
        Vec3 beforeRelease = chain.point(chain.pointCount() - 1);
        chain.step(profile, Vec3.ZERO, 1.0D, 1.0D, guide, space(), 121.0D, 123L);

        double releaseMovement = chain.point(chain.pointCount() - 1).distanceTo(beforeRelease);
        assertTrue(releaseMovement < profile.segmentLength() * 2.0D,
                () -> "release movement=" + releaseMovement);
    }

    @Test
    void guideFrameParallelTransportDoesNotFlipWhileTargetCircles() {
        Vec3 direction = new Vec3(0.8D, 0.6D, 0.0D).normalize();
        Vec3 lateral = new Vec3(0.0D, 0.0D, 1.0D);
        for (int step = 1; step <= 360; step++) {
            double phase = step * Math.PI * 2.0D / 360.0D;
            Vec3 nextDirection = new Vec3(
                    Math.cos(phase) * 0.8D, 0.6D, Math.sin(phase) * 0.8D).normalize();
            Vec3 transported = TentacleChainSolver.parallelTransport(
                    lateral, direction, nextDirection);
            transported = transported.subtract(
                    nextDirection.scale(transported.dot(nextDirection))).normalize();

            assertEquals(0.0D, transported.dot(nextDirection), 1.0E-8D);
            assertTrue(transported.dot(lateral) > 0.99D,
                    "guide frame flipped at step " + step);
            direction = nextDirection;
            lateral = transported;
        }
    }

    @Test
    void largerTentacleUsesAWiderButSlowerThrashPath() {
        TentaclePhysicsProfile morphology = profile();
        TentacleGrabProfile grab = grabProfile();
        Vec3 root = Vec3.ZERO;
        Vec3 target = new Vec3(0.0D, 1.0D, 0.0D);

        Vec3 smallStart = TentacleGrabController.behaviorGoal(
                TentacleGrabMode.THRASH, root, target, morphology, grab, 0, 91L, 0.5D);
        Vec3 smallNext = TentacleGrabController.behaviorGoal(
                TentacleGrabMode.THRASH, root, target, morphology, grab, 1, 91L, 0.5D);
        Vec3 largeStart = TentacleGrabController.behaviorGoal(
                TentacleGrabMode.THRASH, root, target, morphology, grab, 0, 91L, 3.0D);
        Vec3 largeNext = TentacleGrabController.behaviorGoal(
                TentacleGrabMode.THRASH, root, target, morphology, grab, 1, 91L, 3.0D);

        assertTrue(largeStart.horizontalDistance() > smallStart.horizontalDistance());
        double smallAngularStep = angleBetweenHorizontal(smallStart, smallNext);
        double largeAngularStep = angleBetweenHorizontal(largeStart, largeNext);
        assertTrue(largeAngularStep < smallAngularStep);
    }

    @Test
    void wrapFollowsItsStableAnchorWhileThrashRemainsRootCentric() {
        TentaclePhysicsProfile morphology = profile();
        TentacleGrabProfile grab = grabProfile();
        Vec3 root = new Vec3(2.0D, 0.0D, -1.0D);
        Vec3 firstAnchor = new Vec3(4.0D, 2.0D, 3.0D);
        Vec3 secondAnchor = firstAnchor.add(1.5D, -0.25D, 0.75D);

        Vec3 firstWrap = TentacleGrabController.behaviorGoal(
                TentacleGrabMode.WRAP, root, firstAnchor, morphology, grab,
                37, 91L, 1.0D);
        Vec3 secondWrap = TentacleGrabController.behaviorGoal(
                TentacleGrabMode.WRAP, root, secondAnchor, morphology, grab,
                37, 91L, 1.0D);
        Vec3 firstThrash = TentacleGrabController.behaviorGoal(
                TentacleGrabMode.THRASH, root, firstAnchor, morphology, grab,
                37, 91L, 1.0D);
        Vec3 secondThrash = TentacleGrabController.behaviorGoal(
                TentacleGrabMode.THRASH, root, secondAnchor, morphology, grab,
                37, 91L, 1.0D);

        Vec3 anchorDelta = secondAnchor.subtract(firstAnchor);
        Vec3 wrapDelta = secondWrap.subtract(firstWrap);
        assertEquals(anchorDelta.x, wrapDelta.x, 1.0E-8D);
        assertEquals(anchorDelta.y, wrapDelta.y, 1.0E-8D);
        assertEquals(anchorDelta.z, wrapDelta.z, 1.0E-8D);
        assertEquals(firstThrash.x, secondThrash.x, 1.0E-8D);
        assertEquals(firstThrash.y, secondThrash.y, 1.0E-8D);
        assertEquals(firstThrash.z, secondThrash.z, 1.0E-8D);
    }

    @Test
    void pursuitKeepsMidBodyAliveWithoutCostingTipAccuracy() {
        // A tracking tentacle used to freeze into a rigid rod because the muscle wave
        // was gated off entirely during pursuit. The wave now survives at reduced
        // amplitude, biased into the mid-body so the tip still lands on the route.
        TentaclePhysicsProfile morphology = profile().scaledForVolume(6.0D, 4111L);
        Vec3 root = Vec3.ZERO;
        Vec3 goal = new Vec3(morphology.maximumLength() * 0.55D, 0.0D, 0.0D);
        List<Vec3> guide = List.of(root, goal);
        TentacleChainSolver chain = new TentacleChainSolver(morphology.segmentCount(), root);
        chain.initializeAlongPath(morphology, guide, space(), morphology.maximumLength());

        int middle = morphology.segmentCount() / 2;
        double[] travelWrapper = {0.0D};
        Vec3 previousMid = chain.point(middle);
        for (int tick = 0; tick < 90; tick++) {
            chain.step(morphology, root, 1.0D, 1.0D, guide,
                    morphology.trackingTipAdvanceSpeed(), space(), tick, 4111L,
                    Vec3.ZERO, 0.0D, 1.0D);
            Vec3 currentMid = chain.point(middle);
            if (tick >= 30) {
                travelWrapper[0] += currentMid.distanceTo(previousMid);
            }
            previousMid = currentMid;
        }

        Vec3 tip = chain.point(morphology.segmentCount() - 1);
        double tipOffAxis = Math.abs(tip.z) + Math.abs(tip.y);
        assertTrue(travelWrapper[0] > 0.02D,
                () -> "pursuit mid-body should keep moving, travelled " + travelWrapper[0]);
        assertTrue(tipOffAxis < 0.45D,
                () -> "pursuit tip drifted off the route: " + tip);
    }

    @Test
    void tautTrackingRemovesRootArchAndDampensMuscleWave() {
        TentaclePhysicsProfile morphology = profile().scaledForVolume(6.0D, 991L);
        Vec3 root = Vec3.ZERO;
        // Matches the reproduced volume-6/8 case: the target is well outside the
        // root area, but only about sixty percent of the generated full body length.
        Vec3 goal = new Vec3(morphology.maximumLength() * 0.60D, 0.0D,
                morphology.maximumLength() * 0.12D);
        List<Vec3> guide = List.of(root, goal);
        double scale = TentacleSystem.trackingLengthScale(
                morphology.maximumLength(), TentacleChainSolver.pathLength(guide),
                morphology.trackingMaximumStretch());
        TentacleChainSolver tracking = new TentacleChainSolver(morphology.segmentCount(), root);
        TentacleChainSolver decorative = new TentacleChainSolver(morphology.segmentCount(), root);
        tracking.setDiagnosticsEnabled(true);
        decorative.setDiagnosticsEnabled(true);
        tracking.initializeAlongPath(
                morphology, guide, space(), morphology.maximumLength());
        decorative.initializeAlongPath(
                morphology, guide, space(), morphology.maximumLength());

        double currentScale = 1.0D;
        for (int tick = 0; tick < 120; tick++) {
            currentScale = TentacleSystem.approachLengthScale(
                    currentScale, scale, morphology.lengthResponse(),
                    morphology.trackingMaximumStretch());
            tracking.step(morphology, root, 1.0D, currentScale, guide,
                    morphology.trackingTipAdvanceSpeed(), space(), tick, 991L,
                    Vec3.ZERO, 0.0D, 1.0D);
            decorative.step(morphology, root, 1.0D, 1.0D, guide,
                    morphology.trackingTipAdvanceSpeed(), space(), tick, 991L,
                    Vec3.ZERO, 1.0D, 0.0D);
        }

        Vec3 goalDirection = goal.normalize();
        Vec3 trackingFirst = tracking.point(1).subtract(root).normalize();
        assertEquals(scale, currentScale, 0.001D);
        assertTrue(trackingFirst.dot(goalDirection) > 0.94D,
                () -> "tracking root failed to follow taut centerline: " + trackingFirst);
        assertEquals(0.0D, tracking.diagnostics().curveAmplitude(), 1.0E-8D);
        double trackingMuscle = tracking.diagnostics().muscleScale();
        double decorativeMuscle = decorative.diagnostics().muscleScale();
        assertTrue(trackingMuscle < 0.25D,
                () -> "tracking muscle scale should be reduced: " + trackingMuscle);
        assertTrue(decorativeMuscle > trackingMuscle * 2.0D,
                () -> "decorative muscle should exceed tracking: decorative=" + decorativeMuscle
                        + ", tracking=" + trackingMuscle);
        assertTrue(decorative.diagnostics().curveAmplitude() > 0.0D);
    }

    @Test
    void offCenterGrabProducesFiniteArticulatedPose() {
        TentacleGrabProfile grab = grabProfile();
        Vec3 center = new Vec3(0.0D, 1.0D, 0.0D);
        TentacleRagdollBody body = new TentacleRagdollBody(
                1.8D, 0.6D, 0.0F, new Vec3(0.28D, 0.62D, 0.0D));
        TentacleRagdollBody.Update update = null;

        for (int tick = 0; tick < 30; tick++) {
            update = body.update(Vec3.ZERO, center,
                    center.add(-0.8D, 0.15D, 0.55D), new Vec3(0.04D, 0.0D, 0.03D),
                    space(), grab, 2.0D);
        }

        assertTrue(update != null);
        var orientation = update.pose().bodyOrientation();
        assertTrue(Double.isFinite(orientation.x)
                && Double.isFinite(orientation.y)
                && Double.isFinite(orientation.z)
                && Double.isFinite(orientation.w));
        double orientationLength = Math.sqrt(orientation.x * orientation.x
                + orientation.y * orientation.y
                + orientation.z * orientation.z
                + orientation.w * orientation.w);
        assertEquals(1.0D, orientationLength, 1.0E-6D);
        assertTrue(Math.abs(orientation.x) + Math.abs(orientation.y)
                + Math.abs(orientation.z) > 0.05D);
        assertTrue(Double.isFinite(update.pose().headOffset().x)
                && Double.isFinite(update.pose().headOffset().y)
                && Double.isFinite(update.pose().headOffset().z));
        assertEquals(1.0D, update.pose().leftArmDirection().length(), 1.0E-6D);
    }

    @Test
    void ragdollReferenceOrientationPreservesGrabYaw() {
        float grabYaw = 73.0F;
        double height = 1.8D;
        Vec3 center = new Vec3(4.0D, 6.0D, -2.0D);
        TentacleRagdollBody body = new TentacleRagdollBody(
                height, 0.6D, grabYaw, new Vec3(0.0D, height * 0.2D, 0.0D));
        TentacleRagdollBody.Update update = body.update(
                Vec3.ZERO, center, center.add(0.0D, height * 0.2D, 0.0D),
                Vec3.ZERO, space(), grabProfile(), 1.0D);

        org.joml.Vector3d forward = update.pose().referenceOrientation().transform(
                new org.joml.Vector3d(0.0D, 0.0D, 1.0D));
        double decodedYaw = Math.toDegrees(Math.atan2(forward.x, forward.z));
        double difference = Math.toDegrees(Math.atan2(
                Math.sin(Math.toRadians(decodedYaw - grabYaw)),
                Math.cos(Math.toRadians(decodedYaw - grabYaw))));
        assertEquals(0.0D, difference, 1.0E-4D,
                () -> "reference yaw=" + decodedYaw + " expected=" + grabYaw);
    }

    @Test
    void ragdollNodesAndBonesStayOutsideNearbyTerrain() {
        TentacleGrabProfile grab = grabProfile();
        Vec3 center = new Vec3(0.0D, 1.15D, 0.0D);
        Vec3 head = new Vec3(0.0D, 1.8D * 0.43D, 0.0D);
        TentacleRagdollBody body = new TentacleRagdollBody(
                1.8D, 0.6D, 0.0F, head, head, 0.10D,
                grab.wholeBodyTipRatio(), grab.surfaceClearanceScale(),
                TentacleGrabTarget.HEAD, true);
        AabbTentacleCollisionSpace floor = space(
                new AABB(-3.0D, -1.0D, -3.0D, 3.0D, 0.0D, 3.0D));

        for (int tick = 0; tick < 80; tick++) {
            body.update(Vec3.ZERO, center, center.add(head), Vec3.ZERO,
                    floor, grab, 1.0D, new Vec3(0.0D, 1.0D, 0.0D), 1.0D);
        }

        for (Vec3 point : body.collisionSamplePoints(center)) {
            assertTrue(floor.clear(point, grab.ragdollCollisionRadius()),
                    () -> "ragdoll collision sample inside terrain: " + point);
        }
    }

    @Test
    void ungrippedArmsHangDownUnderWorldGravity() {
        TentacleGrabProfile grab = grabProfile();
        Vec3 center = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 head = new Vec3(0.0D, 1.8D * 0.43D, 0.0D);
        TentacleRagdollBody body = new TentacleRagdollBody(
                1.8D, 0.6D, 0.0F, head, head, 0.10D,
                grab.wholeBodyTipRatio(), grab.surfaceClearanceScale(),
                TentacleGrabTarget.HEAD, true);
        TentacleRagdollBody.Update update = null;

        for (int tick = 0; tick < 80; tick++) {
            update = body.update(Vec3.ZERO, center, center.add(head), Vec3.ZERO,
                    space(), grab, 1.0D, new Vec3(0.0D, 1.0D, 0.0D), 1.0D);
        }

        assertNotNull(update);
        org.joml.Vector3d left = update.pose().bodyOrientation().transform(
                new org.joml.Vector3d(update.pose().leftArmDirection().x,
                        update.pose().leftArmDirection().y,
                        update.pose().leftArmDirection().z));
        org.joml.Vector3d right = update.pose().bodyOrientation().transform(
                new org.joml.Vector3d(update.pose().rightArmDirection().x,
                        update.pose().rightArmDirection().y,
                        update.pose().rightArmDirection().z));
        assertTrue(left.y < -0.55D, () -> "left arm direction=" + left);
        assertTrue(right.y < -0.55D, () -> "right arm direction=" + right);
    }

    @Test
    void headFirstGrabCanTurnTheRagdollUpsideDown() {
        TentacleGrabProfile grab = grabProfile();
        Vec3 center = new Vec3(0.0D, 1.0D, 0.0D);
        TentacleRagdollBody body = new TentacleRagdollBody(
                1.8D, 0.6D, 0.0F, new Vec3(0.0D, 0.78D, 0.0D));

        TentacleRagdollBody.Update update = null;
        for (int tick = 0; tick < 80; tick++) {
            update = body.update(Vec3.ZERO, center,
                    center.add(0.0D, -1.25D, 0.10D), Vec3.ZERO,
                    space(), grab, 1.5D);
        }

        assertTrue(update != null);
        org.joml.Vector3d up = update.pose().bodyOrientation().transform(
                new org.joml.Vector3d(0.0D, 1.0D, 0.0D));
        assertTrue(up.y < -0.15D, () -> "ragdoll up=" + up);
    }

    @Test
    void ragdollSelectsTheActuallyTouchedLimb() {
        TentacleRagdollBody leftLeg = new TentacleRagdollBody(
                1.8D, 0.6D, 0.0F, new Vec3(0.12D, -0.72D, 0.0D));
        TentacleRagdollBody rightArm = new TentacleRagdollBody(
                1.8D, 0.6D, 0.0F, new Vec3(-0.44D, 0.02D, 0.0D));
        TentacleRagdollBody leftHand = new TentacleRagdollBody(
                1.8D, 0.6D, 0.0F, new Vec3(0.49D, -0.45D, 0.0D));

        assertEquals(TentacleGrabTarget.LEFT_LEG, leftLeg.pose().grabTarget());
        assertEquals(TentacleGrabTarget.RIGHT_ARM, rightArm.pose().grabTarget());
        assertEquals(TentacleGrabTarget.LEFT_HAND, leftHand.pose().grabTarget());
    }

    @Test
    void surfaceGripDoesNotPullThePlayerCenterIntoTheTentacleTip() {
        TentacleGrabProfile grab = grabProfile();
        Vec3 center = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 contact = new Vec3(0.12D, -0.72D, 0.0D);
        Vec3 tentacle = new Vec3(0.45D, -0.72D, 0.0D);
        TentacleRagdollBody body = new TentacleRagdollBody(
                1.8D, 0.6D, 0.0F, contact, tentacle,
                0.10D, grab.wholeBodyTipRatio(), grab.surfaceClearanceScale(),
                TentacleGrabTarget.LEFT_LEG);

        TentacleRagdollBody.Update update = body.update(Vec3.ZERO, center,
                center.add(tentacle), Vec3.ZERO, space(), grab, 1.0D);

        assertEquals(TentacleGrabTarget.LEFT_LEG, update.pose().grabTarget());
        assertTrue(update.velocity().length() < 0.03D,
                () -> "surface-aligned grab velocity=" + update.velocity());
    }

    @Test
    void wideTipUsesDistributedWholeBodyGrip() {
        TentacleGrabProfile grab = grabProfile();
        TentacleRagdollBody body = new TentacleRagdollBody(
                1.8D, 0.6D, 0.0F,
                new Vec3(0.26D, 0.18D, 0.0D), new Vec3(0.55D, 0.18D, 0.0D),
                0.34D, grab.wholeBodyTipRatio(), grab.surfaceClearanceScale());

        assertEquals(TentacleGrabTarget.WHOLE_BODY, body.pose().grabTarget());
        assertEquals(0.0D, body.gripClearance(), 1.0E-9D);
    }

    @Test
    void mediumTipKeepsItsPreselectedLimbTarget() {
        TentacleGrabProfile grab = grabProfile();
        TentacleRagdollBody body = new TentacleRagdollBody(
                1.8D, 0.6D, 0.0F,
                new Vec3(0.0D, 0.10D, 0.0D), new Vec3(-0.50D, -0.35D, 0.0D),
                0.17D, grab.wholeBodyTipRatio(), grab.surfaceClearanceScale(),
                TentacleGrabTarget.RIGHT_HAND);

        assertEquals(TentacleGrabTarget.RIGHT_HAND, body.pose().grabTarget());
        assertTrue(body.gripClearance() > 0.0D);
    }

    @Test
    void explicitTargetOverridesAutomaticWholeBodyClassification() {
        TentacleGrabProfile grab = grabProfile();
        TentacleRagdollBody body = new TentacleRagdollBody(
                1.8D, 0.6D, 0.0F,
                new Vec3(0.0D, 0.10D, 0.0D), new Vec3(-0.50D, -0.35D, 0.0D),
                0.30D, grab.wholeBodyTipRatio(), grab.surfaceClearanceScale(),
                TentacleGrabTarget.RIGHT_HAND, true);

        assertEquals(TentacleGrabTarget.RIGHT_HAND, body.pose().grabTarget());
        assertEquals(TentacleGrabTarget.LEFT_LEG, TentacleGrabTarget.byName("left_leg"));
        assertEquals(TentacleGrabTarget.NONE, TentacleGrabTarget.byName("auto"));
    }

    @Test
    void endpointGripOrientationRespondsToIncomingPhysicalTipDirection() {
        TentacleGrabProfile grab = grabProfile();
        Vec3 center = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 foot = new Vec3(0.11D, -1.47D, 0.04D);
        TentacleRagdollBody first = new TentacleRagdollBody(
                1.8D, 0.6D, 0.0F, foot, foot.add(0.0D, -0.20D, 0.0D),
                0.10D, grab.wholeBodyTipRatio(), grab.surfaceClearanceScale(),
                TentacleGrabTarget.LEFT_FOOT);
        TentacleRagdollBody second = new TentacleRagdollBody(
                1.8D, 0.6D, 0.0F, foot, foot.add(0.0D, -0.20D, 0.0D),
                0.10D, grab.wholeBodyTipRatio(), grab.surfaceClearanceScale(),
                TentacleGrabTarget.LEFT_FOOT);
        TentacleRagdollBody.Update firstUpdate = null;
        TentacleRagdollBody.Update secondUpdate = null;
        for (int tick = 0; tick < 24; tick++) {
            firstUpdate = first.update(Vec3.ZERO, center,
                    center.add(foot).add(0.0D, 0.20D, 0.0D), Vec3.ZERO,
                    space(), grab, 1.0D, new Vec3(0.0D, 1.0D, 0.0D), 1.0D);
            secondUpdate = second.update(Vec3.ZERO, center,
                    center.add(foot).add(0.0D, 0.20D, 0.0D), Vec3.ZERO,
                    space(), grab, 1.0D, new Vec3(1.0D, 0.0D, 0.0D), 1.0D);
        }

        assertNotNull(firstUpdate);
        assertNotNull(secondUpdate);
        Vec3 firstDirection = firstUpdate.pose().leftLegDirection();
        Vec3 secondDirection = secondUpdate.pose().leftLegDirection();
        // Both extreme requests now settle near the configured hip limit, but the
        // incoming terminal tangent must still influence the constrained pose.
        assertTrue(firstDirection.distanceTo(secondDirection) > 0.015D,
                "first=" + firstDirection + " second=" + secondDirection);
    }

    @Test
    void ragdollRecenteringCannotExceedConfiguredGrabAcceleration() {
        TentacleGrabProfile grab = grabProfile();
        Vec3 center = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 foot = new Vec3(0.11D, -1.47D, 0.04D);
        TentacleRagdollBody body = new TentacleRagdollBody(
                1.8D, 0.6D, 0.0F, foot, foot,
                0.10D, grab.wholeBodyTipRatio(), grab.surfaceClearanceScale(),
                TentacleGrabTarget.LEFT_FOOT);
        Vec3 currentVelocity = new Vec3(0.03D, -0.02D, 0.01D);

        TentacleRagdollBody.Update update = body.update(currentVelocity, center,
                center.add(4.0D, 5.0D, -3.0D), new Vec3(1.0D, 1.0D, 1.0D),
                space(), grab, 1.0D, new Vec3(0.0D, -1.0D, 0.0D), 1.0D, 1.0D);

        assertTrue(update.velocity().subtract(currentVelocity).length()
                        <= grab.maximumAcceleration() + 1.0E-8D,
                () -> "velocity delta=" + update.velocity().subtract(currentVelocity));
        assertTrue(update.velocity().length() <= grab.maximumSpeed() + 1.0E-8D,
                () -> "velocity=" + update.velocity());
    }

    @Test
    void holdWaitsForWrappingThenLiftsFromAStableAnchor() {
        TentaclePhysicsProfile morphology = profile();
        TentacleGrabProfile grab = grabProfile();
        assertEquals(0, grab.maximumTicks());
        Vec3 anchor = new Vec3(1.0D, 2.0D, 3.0D);
        Vec3 beforeLift = TentacleGrabController.behaviorGoal(
                TentacleGrabMode.HOLD, Vec3.ZERO, anchor, morphology, grab,
                grab.holdWrapTicks() - 1, 12L, 1.0D);
        Vec3 afterLift = TentacleGrabController.behaviorGoal(
                TentacleGrabMode.HOLD, Vec3.ZERO, anchor, morphology, grab,
                grab.holdWrapTicks() + grab.holdLiftTicks(), 12L, 1.0D);

        assertEquals(anchor.y, beforeLift.y, 1.0E-8D);
        assertEquals(anchor.y + grab.holdLiftHeight(), afterLift.y, 1.0E-8D);
    }

    @Test
    void holdMovesBetweenSeveralElevatedHangingPositions() {
        TentaclePhysicsProfile morphology = profile();
        TentacleGrabProfile grab = grabProfile();
        Vec3 anchor = new Vec3(1.0D, 2.0D, 3.0D);
        int elevatedStart = grab.holdWrapTicks() + grab.holdLiftTicks();

        Vec3 first = TentacleGrabController.behaviorGoal(
                TentacleGrabMode.HOLD, Vec3.ZERO, anchor, morphology, grab,
                elevatedStart, 29L, 1.0D);
        Vec3 second = TentacleGrabController.behaviorGoal(
                TentacleGrabMode.HOLD, Vec3.ZERO, anchor, morphology, grab,
                elevatedStart + grab.holdPositionTicks(), 29L, 1.0D);
        Vec3 third = TentacleGrabController.behaviorGoal(
                TentacleGrabMode.HOLD, Vec3.ZERO, anchor, morphology, grab,
                elevatedStart + grab.holdPositionTicks() * 2, 29L, 1.0D);

        assertTrue(first.y >= anchor.y + grab.holdLiftHeight() - 1.0E-8D);
        assertTrue(second.distanceTo(first) > 0.25D, () -> "second=" + second);
        assertTrue(third.distanceTo(second) > 0.25D, () -> "third=" + third);
    }

    @Test
    void onlyHoldSuppressesRagdollInertia() {
        TentacleGrabProfile grab = grabProfile();
        Vec3 bodyVelocity = new Vec3(0.02D, 0.0D, 0.0D);
        Vec3 tipVelocity = new Vec3(0.34D, 0.08D, -0.12D);

        Vec3 held = TentacleRagdollBody.inertialForce(
                TentacleGrabMode.HOLD, bodyVelocity, tipVelocity, grab);
        Vec3 wrapped = TentacleRagdollBody.inertialForce(
                TentacleGrabMode.WRAP, bodyVelocity, tipVelocity, grab);
        Vec3 thrashed = TentacleRagdollBody.inertialForce(
                TentacleGrabMode.THRASH, bodyVelocity, tipVelocity, grab);

        assertEquals(Vec3.ZERO, held);
        assertTrue(wrapped.lengthSqr() > 0.0D);
        assertEquals(wrapped, thrashed);
        assertTrue(wrapped.x < 0.0D && wrapped.z > 0.0D, () -> "inertia=" + wrapped);
    }

    @Test
    void wrapAndThrashFollowThePhysicalTerminalAxisButHoldRemainsGravityOriented() {
        TentacleGrabProfile grab = grabProfile();
        Vec3 center = new Vec3(0.0D, 2.0D, 0.0D);
        Vec3 torso = new Vec3(0.0D, 0.12D, 0.0D);
        TentacleRagdollBody held = new TentacleRagdollBody(
                1.8D, 0.6D, 0.0F, torso, torso,
                0.18D, grab.wholeBodyTipRatio(), grab.surfaceClearanceScale(),
                TentacleGrabTarget.WHOLE_BODY, true);
        TentacleRagdollBody wrapped = new TentacleRagdollBody(
                1.8D, 0.6D, 0.0F, torso, torso,
                0.18D, grab.wholeBodyTipRatio(), grab.surfaceClearanceScale(),
                TentacleGrabTarget.WHOLE_BODY, true);
        TentacleRagdollBody thrashed = new TentacleRagdollBody(
                1.8D, 0.6D, 0.0F, torso, torso,
                0.18D, grab.wholeBodyTipRatio(), grab.surfaceClearanceScale(),
                TentacleGrabTarget.WHOLE_BODY, true);
        TentacleRagdollBody.Update heldUpdate = null;
        TentacleRagdollBody.Update wrapUpdate = null;
        TentacleRagdollBody.Update thrashUpdate = null;
        Vec3 terminal = new Vec3(1.0D, 0.0D, 0.0D);

        for (int tick = 0; tick < 48; tick++) {
            heldUpdate = held.update(Vec3.ZERO, center, center.add(torso), Vec3.ZERO,
                    space(), grab, 1.0D, terminal, 1.0D, 1.0D, TentacleGrabMode.HOLD);
            wrapUpdate = wrapped.update(Vec3.ZERO, center, center.add(torso), Vec3.ZERO,
                    space(), grab, 1.0D, terminal, 1.0D, 1.0D, TentacleGrabMode.WRAP);
            thrashUpdate = thrashed.update(Vec3.ZERO, center, center.add(torso), Vec3.ZERO,
                    space(), grab, 1.0D, terminal, 1.0D, 1.0D, TentacleGrabMode.THRASH);
        }

        assertNotNull(heldUpdate);
        assertNotNull(wrapUpdate);
        assertNotNull(thrashUpdate);
        Vec3 heldAxis = worldBodyUp(heldUpdate.pose());
        Vec3 wrapAxis = worldBodyUp(wrapUpdate.pose());
        Vec3 thrashAxis = worldBodyUp(thrashUpdate.pose());
        assertTrue(wrapAxis.dot(terminal) > 0.72D, () -> "wrap axis=" + wrapAxis);
        assertTrue(thrashAxis.dot(terminal) > 0.72D, () -> "thrash axis=" + thrashAxis);
        assertTrue(Math.abs(heldAxis.dot(terminal)) < 0.55D,
                () -> "HOLD unexpectedly hard-locked to terminal axis=" + heldAxis);
    }

    @Test
    void wholeBodyHoldKeepsBothLegsHangingOutsideTheTorso() {
        TentacleGrabProfile grab = grabProfile();
        double height = 1.8D;
        double width = 0.6D;
        Vec3 center = new Vec3(0.0D, 4.0D, 0.0D);
        Vec3 torso = new Vec3(0.0D, height * 0.08D, 0.0D);
        TentacleRagdollBody body = new TentacleRagdollBody(
                height, width, 38.0F, torso, torso.add(0.32D, 0.0D, 0.18D),
                0.17D, grab.wholeBodyTipRatio(), grab.surfaceClearanceScale(),
                TentacleGrabTarget.WHOLE_BODY, true);
        TentacleRagdollBody.Update update = null;
        Vec3 tip = center.add(0.32D, 0.25D, 0.18D);
        Vec3 velocity = Vec3.ZERO;

        for (int tick = 0; tick < 100; tick++) {
            update = body.update(velocity, center, tip, Vec3.ZERO,
                    space(), grab, 1.0D, new Vec3(0.0D, -1.0D, 0.0D),
                    1.0D, 1.0D, TentacleGrabMode.HOLD);
            velocity = update.velocity();
            center = center.add(velocity);
        }

        assertNotNull(update);
        assertEquals(TentacleGrabTarget.WHOLE_BODY, update.pose().grabTarget());
        org.joml.Quaterniond worldBody = new org.joml.Quaterniond(
                update.pose().referenceOrientation()).mul(update.pose().bodyOrientation());
        org.joml.Vector3d left = worldBody.transform(new org.joml.Vector3d(
                update.pose().leftLegDirection().x,
                update.pose().leftLegDirection().y,
                update.pose().leftLegDirection().z));
        org.joml.Vector3d right = worldBody.transform(new org.joml.Vector3d(
                update.pose().rightLegDirection().x,
                update.pose().rightLegDirection().y,
                update.pose().rightLegDirection().z));
        // With the tighter 100-degree hip swing, a horizontally held torso can
        // prevent a leg from pointing steeply toward world-down. Both legs must
        // still remain on the downward side instead of reproducing the old
        // one-up/one-down pose.
        assertTrue(left.y < -0.05D, () -> "left leg points upward: " + left);
        assertTrue(right.y < -0.05D, () -> "right leg points upward: " + right);
        double requiredClearance = width * 0.43D + grab.ragdollCollisionRadius() * 0.35D;
        assertTrue(body.minimumRenderedLegTorsoDistance() >= requiredClearance - 0.025D,
                () -> "leg/torso clearance=" + body.minimumRenderedLegTorsoDistance()
                        + " required=" + requiredClearance);
    }

    @Test
    void matchingTipVelocityDoesNotAccumulateEveryTick() {
        TentacleGrabProfile grab = grabProfile();
        Vec3 sharedVelocity = new Vec3(0.18D, -0.04D, 0.07D);
        Vec3 result = TentacleGrabController.constrainedVelocity(
                sharedVelocity, Vec3.ZERO, Vec3.ZERO, sharedVelocity,
                grab, 1.0D, 1.0D);

        assertEquals(sharedVelocity.x, result.x, 1.0E-8D);
        assertEquals(sharedVelocity.y, result.y, 1.0E-8D);
        assertEquals(sharedVelocity.z, result.z, 1.0E-8D);
    }

    @Test
    void flightControlScaleReducesGrabVelocity() {
        TentacleGrabProfile grab = grabProfile();
        Vec3 normal = TentacleGrabController.constrainedVelocity(
                Vec3.ZERO, Vec3.ZERO, new Vec3(2.0D, 0.0D, 0.0D),
                Vec3.ZERO, grab, 1.0D, 1.0D);
        Vec3 flying = TentacleGrabController.constrainedVelocity(
                Vec3.ZERO, Vec3.ZERO, new Vec3(2.0D, 0.0D, 0.0D),
                Vec3.ZERO, grab, 1.0D, grab.flightControlScale());

        assertTrue(flying.length() < normal.length() * 0.60D,
                () -> "normal=" + normal + " flying=" + flying);
    }

    @Test
    void unheldLimbsTrailBehindRapidGrabMotion() {
        TentacleGrabProfile grab = grabProfile();
        Vec3 center = new Vec3(0.0D, 1.0D, 0.0D);
        TentacleRagdollBody body = new TentacleRagdollBody(
                1.8D, 0.6D, 0.0F, new Vec3(0.0D, 0.10D, 0.0D));
        Vec3 initialArm = body.pose().leftArmDirection();
        double maximumChange = 0.0D;

        for (int tick = 0; tick < 48; tick++) {
            double side = (tick / 6) % 2 == 0 ? 1.0D : -1.0D;
            TentacleRagdollBody.Update update = body.update(Vec3.ZERO, center,
                    center.add(side * 1.25D, 0.25D, 0.35D),
                    new Vec3(side * 0.18D, 0.0D, 0.04D), space(), grab, 1.5D);
            maximumChange = Math.max(maximumChange,
                    update.pose().leftArmDirection().distanceTo(initialArm));
        }

        assertTrue(maximumChange > 0.12D, "limb direction change=" + maximumChange);
    }

    private static AabbTentacleCollisionSpace space(AABB... boxes) {
        return new AabbTentacleCollisionSpace(List.of(boxes), 0.002D);
    }

    private static double angleBetweenHorizontal(Vec3 first, Vec3 second) {
        double firstAngle = Math.atan2(first.z, first.x);
        double secondAngle = Math.atan2(second.z, second.x);
        return Math.abs(Math.atan2(Math.sin(secondAngle - firstAngle), Math.cos(secondAngle - firstAngle)));
    }

    private static double angleBetween(Vec3 first, Vec3 second) {
        double cosine = Math.max(-1.0D, Math.min(1.0D,
                first.normalize().dot(second.normalize())));
        return Math.acos(cosine);
    }

    private static TentacleGrabProfile grabProfile() {
        return TentacleGrabProfile.fromValues(
                MudPhysicsProfiles.tentacleDefaultValues());
    }

    private static void assertChainSegmentsClear(TentacleChainSolver chain,
            TentaclePhysicsProfile profile, TentacleCollisionSpace collision) {
        for (int index = 0; index < chain.pointCount() - 1; index++) {
            double radius = Math.max(chain.radiusAt(profile, index),
                    chain.radiusAt(profile, index + 1));
            int segment = index;
            assertTrue(collision.clear(chain.point(index), chain.point(index + 1), radius),
                    () -> "terrain penetration at segment " + segment);
        }
    }

    private static double minimumNonAdjacentSegmentDistance(List<Vec3> points) {
        double minimum = Double.POSITIVE_INFINITY;
        for (int first = 0; first < points.size() - 1; first++) {
            for (int second = first + 3; second < points.size() - 1; second++) {
                TentacleEntityCollider.SegmentPair pair = TentacleEntityCollider.closestPoints(
                        points.get(first), points.get(first + 1),
                        points.get(second), points.get(second + 1));
                minimum = Math.min(minimum, pair.first().distanceTo(pair.second()));
            }
        }
        return minimum;
    }

    private static double minimumSegmentDistance(List<Vec3> first, List<Vec3> second) {
        double minimum = Double.POSITIVE_INFINITY;
        for (int firstSegment = 0; firstSegment < first.size() - 1; firstSegment++) {
            for (int secondSegment = 0; secondSegment < second.size() - 1; secondSegment++) {
                TentacleEntityCollider.SegmentPair pair = TentacleEntityCollider.closestPoints(
                        first.get(firstSegment), first.get(firstSegment + 1),
                        second.get(secondSegment), second.get(secondSegment + 1));
                minimum = Math.min(minimum, pair.first().distanceTo(pair.second()));
            }
        }
        return minimum;
    }

    private static double tangentVariation(List<Vec3> points) {
        double variation = 0.0D;
        Vec3 previous = points.get(1).subtract(points.getFirst()).normalize();
        for (int index = 2; index < points.size(); index++) {
            Vec3 current = points.get(index).subtract(points.get(index - 1)).normalize();
            variation += current.subtract(previous).length();
            previous = current;
        }
        return variation;
    }

    private static Vec3 worldBodyUp(TentacleRagdollPose pose) {
        org.joml.Vector3d up = new org.joml.Quaterniond(pose.referenceOrientation())
                .mul(pose.bodyOrientation())
                .transform(new org.joml.Vector3d(0.0D, 1.0D, 0.0D));
        return new Vec3(up.x, up.y, up.z).normalize();
    }

    private static TentaclePhysicsProfile profile() {
        return new TentaclePhysicsProfile(
                16, 64.0D,
                8, 0.20D, 0.14D, 0.06D,
                0.008D, 0.92D, 0.0D, 1.18D, 0.0005D, 0.32D, 0.96D, 2.40D,
                true, 0.92D, 0.68D,
                2, 8, 0.08D, 0.10D,
                2.0D, 1.4D, 0.25D, 0.06D, 56,
                0.06D, 0.82D, 0.58D,
                0.0D, 0.0D, 0.48D, 0.65D, 0.0D, 0.0D, 0.08D,
                0.22D, 0.85D, 0.30D, 0.48D, 4, 0.045D, 2.75D, 2.40D,
                0.11D, 0.34D, 1.18D, 0.055D, 0.42D,
                3, 0.34D,
                0.25D, 0.12D, 1.05D, 1.5D, 8, 40, 0.12D, 4, 4096, 0.45D, 3,
                0.45D, 4, 96, 0.97D, 0.002D,
                true, 0.72D, 0.055D, 0.18D, 0.68D, 0.16D, 0.82D, 0.90D, 12, 3, 8192,
                2);
    }
}

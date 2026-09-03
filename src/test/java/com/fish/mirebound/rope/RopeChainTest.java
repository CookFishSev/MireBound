package com.fish.mirebound.rope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class RopeChainTest {
    @Test
    void defaultRopeHasTwentyOneBlockEdges() {
        RopeProperties properties = RopeProperties.DEFAULT;

        assertEquals(20, properties.segmentCount());
        assertEquals(21, properties.nodeCount());
        assertEquals(1.0D, properties.segmentLength(), 0.0D);
        assertEquals(20.0D, properties.totalLength(), 0.0D);
    }

    @Test
    void thrownRopeStartsFoldedWithEveryEdgeAtRestLength() {
        RopeChain rope = RopeChain.thrown(
                RopeProperties.DEFAULT, new Vec3(0.0D, 2.0D, 0.0D),
                new Vec3(0.0D, 0.0D, 1.0D), 1.0F);

        assertEquals(0.0D, rope.maximumSegmentError(), 1.0E-9D);
    }

    @Test
    void rescueLassoUsesFiveClosedFixedLengthSegments() {
        RopeProperties properties = RopeProperties.DEFAULT.withSegmentCount(10);
        int firstNode = properties.segmentCount() - 5;
        Vec3 center = new Vec3(0.0D, 3.0D, 4.0D);
        double radius = properties.segmentLength()
                / (2.0D * Math.sin(Math.PI / 5.0D));
        Vec3[] loop = new Vec3[6];
        for (int index = 0; index < 5; index++) {
            double angle = Math.PI * 2.0D * index / 5.0D;
            loop[index] = center.add(
                    Math.cos(angle) * radius,
                    Math.sin(angle) * radius,
                    0.0D);
        }
        loop[5] = loop[0];
        RopeChain rope = RopeChain.rescueThrown(
                properties, new Vec3(0.0D, 2.0D, 0.0D),
                new Vec3(0.0D, 0.0D, 1.0D), 1.0F,
                firstNode, loop);

        for (int tick = 0; tick < 8; tick++) {
            rope.step(null);
        }

        for (int node = 0; node < loop.length; node++) {
            assertEquals(loop[node], rope.point(firstNode + node));
        }
        for (int segment = 0; segment < 5; segment++) {
            assertEquals(properties.segmentLength(),
                    rope.point(firstNode + segment)
                            .distanceTo(rope.point(firstNode + segment + 1)),
                    1.0E-9D);
        }
        assertTrue(rope.anchorLasso(firstNode, loop));
        rope.step(null);
        assertEquals(loop[0], rope.point(firstNode + 5));
        assertEquals(firstNode, rope.rescueLassoFirstSegment());
    }

    @Test
    void rescueThrowReleasesTheFirstNodeImmediately() {
        RopeProperties properties = RopeProperties.DEFAULT.withSegmentCount(10);
        int firstNode = properties.segmentCount() - 5;
        Vec3 origin = new Vec3(0.0D, 3.0D, 0.0D);
        Vec3 center = new Vec3(0.0D, 3.0D, 5.0D);
        double lassoRadius = properties.segmentLength()
                / (2.0D * Math.sin(Math.PI / 5.0D));
        Vec3[] loop = new Vec3[6];
        for (int node = 0; node < 5; node++) {
            double angle = Math.PI * 2.0D * node / 5.0D;
            loop[node] = center.add(
                    Math.cos(angle) * lassoRadius,
                    Math.sin(angle) * lassoRadius,
                    0.0D);
        }
        loop[5] = loop[0];
        RopeChain rope = RopeChain.rescueThrown(
                properties, origin, new Vec3(0.0D, 0.0D, 1.0D),
                1.0F, firstNode, loop);

        rope.step(null);

        assertTrue(rope.point(0).distanceToSqr(origin) > 1.0E-6D,
                "the released rope end remained fixed to the throwing hand");
    }

    @Test
    void rescueLassoEndCannotBeExtendedAfterItIsAnchored() {
        RopeProperties properties = RopeProperties.DEFAULT.withSegmentCount(10);
        int firstNode = properties.segmentCount() - 5;
        Vec3 center = new Vec3(0.0D, 3.0D, 4.0D);
        double radius = properties.segmentLength()
                / (2.0D * Math.sin(Math.PI / 5.0D));
        Vec3[] loop = new Vec3[6];
        for (int index = 0; index < 5; index++) {
            double angle = Math.PI * 2.0D * index / 5.0D;
            loop[index] = center.add(
                    Math.cos(angle) * radius,
                    Math.sin(angle) * radius,
                    0.0D);
        }
        loop[5] = loop[0];
        RopeChain rope = RopeChain.rescueThrown(
                properties, new Vec3(0.0D, 2.0D, 0.0D),
                new Vec3(0.0D, 0.0D, 1.0D), 1.0F,
                firstNode, loop);

        assertTrue(!rope.canExtendAt(firstNode),
                "the rescue lasso end must be reserved before it is anchored");
        assertTrue(rope.anchorLasso(firstNode, loop));
        assertTrue(rope.canExtendAt(0));
        assertTrue(rope.canExtendAt(firstNode - 1));
        assertTrue(!rope.canExtendAt(firstNode));
        assertTrue(!rope.canExtendAt(properties.segmentCount() - 1));
    }

    @Test
    void draggingTheRopeReleasesItsRescueAnchors() {
        RopeProperties properties = RopeProperties.DEFAULT.withSegmentCount(10);
        int firstNode = properties.segmentCount() - 5;
        Vec3 center = new Vec3(0.0D, 3.0D, 4.0D);
        double radius = properties.segmentLength()
                / (2.0D * Math.sin(Math.PI / 5.0D));
        Vec3[] loop = new Vec3[6];
        for (int index = 0; index < 5; index++) {
            double angle = Math.PI * 2.0D * index / 5.0D;
            loop[index] = center.add(
                    Math.cos(angle) * radius,
                    Math.sin(angle) * radius,
                    0.0D);
        }
        loop[5] = loop[0];
        RopeChain rope = RopeChain.rescueThrown(
                properties, new Vec3(0.0D, 2.0D, 0.0D),
                new Vec3(0.0D, 0.0D, 1.0D), 1.0F,
                firstNode, loop);
        assertTrue(rope.anchorLasso(firstNode, loop));

        RopeFrame frame = RopeFrame.fromTangent(
                rope.point(1).subtract(rope.point(0)));
        assertTrue(rope.setDragTarget(firstNode,
                rope.segmentCenter(firstNode).add(0.0D, 0.5D, 0.0D), frame));
        assertTrue(rope.rescueAnchoredOrientations().isEmpty());
    }

    @Test
    void draggingRescueLockedSegmentReleasesOnlyItsContiguousGroup() {
        RopeProperties properties = RopeProperties.DEFAULT;
        Vec3[] positions = new Vec3[properties.nodeCount()];
        Vec3[] velocities = new Vec3[positions.length];
        for (int node = 0; node < positions.length; node++) {
            positions[node] = new Vec3(node, 4.0D, 0.0D);
            velocities[node] = Vec3.ZERO;
        }
        RopeChain rope = new RopeChain(properties, positions, velocities);

        assertTrue(rope.anchorLasso(1, positionsFrom(positions, 1, 6)));
        assertTrue(rope.anchorLasso(10, positionsFrom(positions, 10, 6)));
        assertTrue(rope.setDragTarget(2,
                rope.segmentCenter(2).add(0.0D, 0.25D, 0.0D)));

        assertEquals(5, rope.rescueAnchoredOrientations().stream()
                .filter(anchor -> anchor.segment() >= 10
                        && anchor.segment() <= 14)
                .count());
        assertEquals(0, rope.rescueAnchoredOrientations().stream()
                .filter(anchor -> anchor.segment() >= 1
                        && anchor.segment() <= 5)
                .count());
    }

    @Test
    void draggingAnUnlockedSegmentKeepsAllRescueLockedGroups() {
        RopeProperties properties = RopeProperties.DEFAULT;
        Vec3[] positions = new Vec3[properties.nodeCount()];
        Vec3[] velocities = new Vec3[positions.length];
        for (int node = 0; node < positions.length; node++) {
            positions[node] = new Vec3(node, 4.0D, 0.0D);
            velocities[node] = Vec3.ZERO;
        }
        RopeChain rope = new RopeChain(properties, positions, velocities);

        assertTrue(rope.anchorLasso(1, positionsFrom(positions, 1, 6)));
        assertTrue(rope.anchorLasso(10, positionsFrom(positions, 10, 6)));
        assertTrue(rope.setDragTarget(7,
                rope.segmentCenter(7).add(0.0D, 0.25D, 0.0D)));

        assertEquals(10, rope.rescueAnchoredOrientations().size());
    }

    private static Vec3[] positionsFrom(Vec3[] positions, int start, int count) {
        Vec3[] result = new Vec3[count];
        System.arraycopy(positions, start, result, 0, count);
        return result;
    }

    @Test
    void dragDampingIsLocalToTheTwoAttachedFreeNodes() {
        RopeProperties properties = RopeProperties.DEFAULT.withSegmentCount(6);
        Vec3[] positions = new Vec3[properties.nodeCount()];
        Vec3[] velocities = new Vec3[positions.length];
        for (int node = 0; node < positions.length; node++) {
            positions[node] = new Vec3(node, 2.0D, 0.0D);
            velocities[node] = new Vec3(1.0D, 0.0D, 0.0D);
        }
        RopeSimulation simulation = RopeSimulation.server(
                properties, positions, velocities);

        simulation.dampFreeVelocities(3);

        assertTrue(simulation.velocity(2).x < simulation.velocity(1).x);
        assertEquals(simulation.velocity(1).x,
                simulation.velocity(4).x, 1.0E-12D);
    }

    @Test
    void rescueHaulMovesHorizontallyWithoutAddingLift() {
        Vec3 current = new Vec3(0.02D, -0.18D, -0.01D);

        Vec3 pulled = RopeRuntime.rescueHaulMotion(
                current, new Vec3(1.0D, 3.0D, 0.0D), 1.0D);

        assertEquals(current.y, pulled.y, 0.0D);
        assertTrue(pulled.x > current.x);
        assertEquals(current.z, pulled.z, 1.0E-12D);
    }

    @Test
    void rescueHaulRemovesVelocityOpposingTheAnchor() {
        Vec3 pulled = RopeRuntime.rescueHaulMotion(
                new Vec3(-0.12D, 0.0D, 0.0D),
                new Vec3(1.0D, 0.0D, 0.0D), 1.0D);

        assertTrue(pulled.x > 0.0D);
        assertEquals(0.0D, pulled.z, 1.0E-12D);
    }

    @Test
    void rescueHaulDoesNotEnterTautStateWhileTheTargetIsTooClose() {
        assertEquals(0.0D, RopeRuntime.rescueHaulTautness(
                new Vec3(0.0D, 0.0D, 2.0D),
                Vec3.ZERO, 3.0D), 1.0E-12D);
        assertTrue(RopeRuntime.rescueHaulTautness(
                new Vec3(0.0D, 0.0D, 3.0D), Vec3.ZERO, 3.0D) > 0.0D);
    }

    @Test
    void draggingReleasesOnlyAfterTheConfiguredDistanceIsExceeded() {
        assertTrue(!RopeRuntime.dragDistanceExceeded(
                Vec3.ZERO, new Vec3(6.0D, 0.0D, 0.0D), 6.0D));
        assertTrue(RopeRuntime.dragDistanceExceeded(
                Vec3.ZERO, new Vec3(6.01D, 0.0D, 0.0D), 6.0D));
    }

    @Test
    void rescueHaulCanClimbOnlyWhenARealUpwardRopeDirectionIsBlocked() {
        Vec3 current = new Vec3(0.02D, -0.18D, -0.01D);

        Vec3 pulled = RopeRuntime.rescueHaulMotion(
                current, new Vec3(1.0D, 0.75D, 0.0D), 1.0D, true);

        assertTrue(pulled.y > 0.0D);
        assertTrue(pulled.y < 0.02D);
    }

    @Test
    void rescueHaulCanClimbAVerticalRope() {
        Vec3 pulled = RopeRuntime.rescueHaulMotion(
                Vec3.ZERO, new Vec3(0.0D, 1.0D, 0.0D), 1.0D, true);

        assertTrue(pulled.y > 0.0D);
        assertEquals(0.0D, pulled.x, 1.0E-12D);
        assertEquals(0.0D, pulled.z, 1.0E-12D);
    }

    @Test
    void rescueHaulKeepsUsefulPullStrengthInsideMud() {
        Vec3 pull = RopeRuntime.rescueHaulMudPull(
                new Vec3(1.0D, 0.0D, 0.0D), 1.0D);

        assertTrue(pull.x >= 0.05D);
        assertEquals(0.0D, pull.y, 1.0E-12D);
        assertEquals(0.0D, pull.z, 1.0E-12D);
    }

    @Test
    void nearVerticalRopeTouchesThePlayerLikeALadder() {
        AABB player = new AABB(-0.3D, 0.0D, -0.3D, 0.3D, 1.8D, 0.3D);

        Vec3 contact = RopeClimbing.contactPoint(
                player,
                new Vec3(0.42D, -1.0D, 0.0D),
                new Vec3(0.42D, 3.0D, 0.10D),
                RopeProperties.DEFAULT.collisionRadius());

        assertTrue(contact != null);
        assertTrue(RopeClimbing.contactPoint(
                player,
                new Vec3(0.52D, -1.0D, 0.52D),
                new Vec3(0.52D, 3.0D, 0.52D),
                RopeProperties.DEFAULT.collisionRadius()) == null,
                "a diagonal air gap must not count as rope contact");
        assertTrue(RopeClimbing.contactPoint(
                player,
                new Vec3(-1.0D, 0.9D, 0.0D),
                new Vec3(1.0D, 0.9D, 0.0D),
                RopeProperties.DEFAULT.collisionRadius()) == null);
    }

    @Test
    void ropeClimbingRisesOrHoldsLikeALadder() {
        Vec3 rising = RopeClimbing.motion(
                new Vec3(0.02D, -0.08D, 0.01D), true, false);
        Vec3 holding = RopeClimbing.motion(rising, false, true);
        Vec3 sliding = RopeClimbing.motion(
                new Vec3(0.02D, -0.60D, 0.01D), false, false);

        assertEquals(0.15D, rising.y, 1.0E-12D);
        assertEquals(0.0D, holding.y, 1.0E-12D);
        assertEquals(-0.15D, sliding.y, 1.0E-12D);
        assertEquals(rising.x, holding.x, 1.0E-12D);
        assertEquals(rising.z, holding.z, 1.0E-12D);
    }

    @Test
    void rescueHaulTargetCannotExceedItsRemainingRopeLength() {
        Vec3 clamped = RopeRuntime.clampRescueHaulTarget(
                new Vec3(5.0D, 0.0D, 0.0D), Vec3.ZERO, 3.0D);

        assertTrue(clamped.length() < 3.0D);
        assertEquals(2.9999D, clamped.length(), 1.0E-9D);
    }

    @Test
    void mudSupportsRopeShallowlyButDoesNotBlockUpwardTravel() {
        double radius = RopeProperties.DEFAULT.collisionRadius();
        RopeCollisionWorld mud = RopeCollisionWorld.testing(
                List.of(), List.of(new AABB(-1.0D, 0.0D, -1.0D,
                        1.0D, 1.0D, 1.0D)));
        double expectedRest = 1.0D - 1.25D / 16.0D + radius;

        Vec3 supported = mud.sweep(
                new Vec3(0.0D, 1.5D, 0.0D),
                new Vec3(0.0D, 0.5D, 0.0D), radius);
        Vec3 rising = mud.sweep(
                new Vec3(0.0D, 0.75D, 0.0D),
                new Vec3(0.0D, 1.5D, 0.0D), radius);

        assertTrue(supported.y < expectedRest,
                "a falling rope should enter mud before resurfacing");
        assertEquals(new Vec3(0.0D, 1.5D, 0.0D), rising);

        Vec3 resurfacing = mud.sweep(
                supported, new Vec3(0.0D, supported.y - 0.05D, 0.0D), radius);
        assertTrue(resurfacing.y > supported.y && resurfacing.y <= expectedRest,
                () -> "rope did not resurface gradually: " + resurfacing);
    }

    @Test
    void fiveStraightAnchorsAreNotMistakenForARescueLasso() {
        RopeProperties properties = RopeProperties.DEFAULT.withSegmentCount(10);
        RopeChain rope = RopeChain.thrown(
                properties, new Vec3(0.0D, 2.0D, 0.0D),
                new Vec3(0.0D, 0.0D, 1.0D), 1.0F);
        Vec3[] straight = new Vec3[6];
        for (int node = 0; node < straight.length; node++) {
            straight[node] = new Vec3(node, 3.0D, 4.0D);
        }

        assertTrue(rope.anchorLasso(properties.segmentCount() - 5, straight));
        assertEquals(-1, rope.rescueLassoFirstSegment());
    }

    @Test
    void edgeConstraintsRecoverFromASeverelyStretchedChain() {
        RopeProperties properties = RopeProperties.DEFAULT;
        Vec3[] positions = new Vec3[properties.nodeCount()];
        Vec3[] velocities = new Vec3[properties.nodeCount()];
        for (int node = 0; node < positions.length; node++) {
            positions[node] = new Vec3(node * 2.5D, 8.0D, 0.0D);
            velocities[node] = Vec3.ZERO;
        }
        RopeChain rope = new RopeChain(properties, positions, velocities);

        for (int tick = 0; tick < 12; tick++) {
            rope.step(null);
        }

        assertTrue(rope.maximumSegmentError() < 0.03D,
                () -> "segment error=" + rope.maximumSegmentError());
    }

    @Test
    void terrainSweepStopsTheChainAtAFullBlockWall() {
        RopeProperties properties = RopeProperties.DEFAULT;
        Vec3[] positions = new Vec3[properties.nodeCount()];
        Vec3[] velocities = new Vec3[properties.nodeCount()];
        for (int node = 0; node < positions.length; node++) {
            positions[node] = new Vec3(-1.5D - node, 1.5D, 0.0D);
            velocities[node] = new Vec3(0.45D, 0.0D, 0.0D);
        }
        RopeChain rope = new RopeChain(properties, positions, velocities);
        RopeCollisionWorld collision = RopeCollisionWorld.testing(List.of(
                new AABB(0.0D, -100.0D, -2.0D, 1.0D, 100.0D, 2.0D)));

        for (int tick = 0; tick < 12; tick++) {
            rope.step(collision);
        }

        for (Vec3 node : rope.positions()) {
            assertTrue(node.x < -properties.collisionRadius() + 0.02D,
                    () -> "node crossed wall: " + node);
        }
    }

    @Test
    void terrainSweepPreservesMovementAlongAFlatWall() {
        RopeCollisionWorld wall = RopeCollisionWorld.testing(List.of(
                new AABB(0.0D, 0.0D, -4.0D, 1.0D, 2.0D, 4.0D)));
        double radius = 0.1D;

        Vec3 result = wall.sweep(
                new Vec3(-0.25D, 1.0D, 0.0D),
                new Vec3(0.50D, 1.0D, 1.0D), radius);

        assertTrue(result.x <= -radius + 0.001D,
                () -> "rope crossed the wall: " + result);
        assertTrue(result.z > 0.9D,
                () -> "wall contact discarded tangential motion: " + result);
    }

    @Test
    void cornerSlideStopsAtTheSecondBlockingFace() {
        RopeCollisionWorld corner = RopeCollisionWorld.testing(List.of(
                new AABB(0.0D, 0.0D, -1.0D, 1.0D, 2.0D, 1.0D),
                new AABB(-1.0D, 0.0D, 0.0D, 1.0D, 2.0D, 1.0D)));
        double radius = 0.1D;

        Vec3 result = corner.sweep(
                new Vec3(-0.25D, 1.0D, -0.25D),
                new Vec3(0.50D, 1.0D, 0.50D), radius);

        assertTrue(result.x <= -radius + 0.01D,
                () -> "corner slide crossed the first face: " + result);
        assertTrue(result.z <= -radius + 0.01D,
                () -> "corner slide crossed the second face: " + result);
    }

    @Test
    void heldSegmentDeliberatelyIgnoresTerrainClamping() {
        RopeProperties properties = RopeProperties.DEFAULT.withSegmentCount(6);
        Vec3[] positions = new Vec3[properties.nodeCount()];
        Vec3[] velocities = new Vec3[positions.length];
        for (int node = 0; node < positions.length; node++) {
            positions[node] = new Vec3(-2.0D, 1.0D + node, 0.0D);
            velocities[node] = Vec3.ZERO;
        }
        RopeChain rope = new RopeChain(properties, positions, velocities);
        RopeCollisionWorld collision = RopeCollisionWorld.testing(List.of(
                new AABB(0.0D, -100.0D, -2.0D, 1.0D, 100.0D, 2.0D)));
        int segment = 3;
        RopeFrame frame = RopeFrame.fromTangent(
                positions[segment + 1].subtract(positions[segment]));

        Vec3 safe = rope.clampDragTarget(
                segment, new Vec3(3.0D, 4.5D, 0.0D), frame);

        assertEquals(new Vec3(3.0D, 4.5D, 0.0D), safe);
    }

    @Test
    void aRopeRestingOnTheFloorCanStillBeDragged() {
        RopeProperties properties = RopeProperties.DEFAULT.withSegmentCount(6);
        Vec3[] positions = new Vec3[properties.nodeCount()];
        Vec3[] velocities = new Vec3[positions.length];
        for (int node = 0; node < positions.length; node++) {
            positions[node] = new Vec3(node, 0.14D, 0.0D);
            velocities[node] = Vec3.ZERO;
        }
        RopeChain rope = new RopeChain(properties, positions, velocities);
        RopeCollisionWorld floor = RopeCollisionWorld.testing(List.of(
                new AABB(-100.0D, -1.0D, -100.0D, 100.0D, 0.0D, 100.0D)));
        for (int tick = 0; tick < 4; tick++) {
            rope.step(floor);
        }
        int segment = properties.segmentCount() - 1;
        RopeFrame frame = RopeFrame.fromTangent(rope.positions().get(segment + 1)
                .subtract(rope.positions().get(segment)));

        Vec3 before = rope.segmentCenter(segment);
        Vec3 safe = rope.clampDragTarget(
                segment, new Vec3(7.0D, 0.5D, 0.0D), frame);

        assertTrue(safe.distanceTo(before) > 0.1D,
                () -> "resting contacts incorrectly locked the drag: " + safe);
    }

    @Test
    void sweepFromInsideABlockDoesNotExitTheFarSide() {
        RopeCollisionWorld box = RopeCollisionWorld.testing(List.of(
                new AABB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D)));
        double radius = 2.125D / 16.0D;

        Vec3 resolved = box.sweep(
                new Vec3(0.5D, 0.5D, 0.5D), new Vec3(4.0D, 0.5D, 0.5D), radius);

        assertTrue(resolved.x < 1.25D,
                () -> "sweep crossed the solid: " + resolved);
        assertTrue(Math.abs(resolved.y - 0.5D) < 0.08D
                        && Math.abs(resolved.z - 0.5D) < 0.08D,
                () -> "sweep launched off-axis: " + resolved);
    }

    @Test
    void aSegmentTouchingTheFloorCanMoveAwayFromTheContact() {
        RopeCollisionWorld floor = RopeCollisionWorld.testing(List.of(
                new AABB(-10.0D, -1.0D, -10.0D, 10.0D, 0.0D, 10.0D)));
        double radius = 2.125D / 16.0D;

        Vec3 away = floor.sweep(new Vec3(0.0D, 0.14D, 0.0D),
                new Vec3(0.0D, 0.8D, 0.0D), radius);
        Vec3 into = floor.sweep(new Vec3(0.0D, 0.14D, 0.0D),
                new Vec3(0.0D, -0.2D, 0.0D), radius);

        assertTrue(away.y > 0.7D, "contact blocked motion away from the floor");
        assertTrue(into.y >= radius - 0.002D,
                "motion into the floor crossed the surface");
    }

    @Test
    void aFoldedChainStaysFlexibleInsteadOfOpeningLikeARod() {
        RopeProperties properties = RopeProperties.DEFAULT.withSegmentCount(6);
        Vec3[] positions = new Vec3[properties.nodeCount()];
        Vec3[] velocities = new Vec3[positions.length];
        for (int node = 0; node < positions.length; node++) {
            positions[node] = (node & 1) == 0
                    ? new Vec3(node * 0.5D, 8.0D, 0.0D)
                    : new Vec3(node * 0.5D, 8.0D, 1.0D);
            velocities[node] = Vec3.ZERO;
        }
        double folded = positions[0].distanceTo(positions[2]);
        RopeChain rope = new RopeChain(properties, positions, velocities);

        for (int tick = 0; tick < 8; tick++) {
            rope.step(null);
        }

        assertTrue(rope.positions().get(0).distanceTo(rope.positions().get(2))
                < folded + 0.35D,
                () -> "fold opened like a stiff rod: "
                        + rope.positions().get(0).distanceTo(rope.positions().get(2)));
        assertTrue(rope.maximumSegmentError() < 0.02D,
                () -> "segment error=" + rope.maximumSegmentError());
    }

    @Test
    void aDrapedChainRestsOnTheFloorInsteadOfSinkingThroughIt() {
        RopeProperties properties = RopeProperties.DEFAULT.withSegmentCount(6);
        Vec3[] positions = new Vec3[properties.nodeCount()];
        Vec3[] velocities = new Vec3[positions.length];
        for (int node = 0; node < positions.length; node++) {
            positions[node] = new Vec3(node, 0.40D, 0.0D);
            velocities[node] = Vec3.ZERO;
        }
        RopeChain rope = new RopeChain(properties, positions, velocities);
        RopeCollisionWorld floor = RopeCollisionWorld.testing(List.of(
                new AABB(-100.0D, -1.0D, -100.0D, 100.0D, 0.0D, 100.0D)));

        for (int tick = 0; tick < 24; tick++) {
            rope.step(floor);
        }

        double floorClearance = properties.collisionRadius();
        for (Vec3 node : rope.positions()) {
            assertTrue(node.y >= floorClearance - 0.02D,
                    () -> "node sank through the floor: " + node);
        }
        assertTrue(rope.maximumSegmentError() < 0.02D,
                () -> "segment error=" + rope.maximumSegmentError());
    }

    @Test
    void draggedSegmentArrivesAtTheFirstTargetWithoutOvershooting() {
        RopeProperties properties = RopeProperties.DEFAULT;
        Vec3[] positions = new Vec3[properties.nodeCount()];
        Vec3[] velocities = new Vec3[properties.nodeCount()];
        for (int node = 0; node < positions.length; node++) {
            positions[node] = new Vec3(node, 4.0D, 0.0D);
            velocities[node] = Vec3.ZERO;
        }
        RopeChain rope = new RopeChain(properties, positions, velocities);
        Vec3 initialCenter = rope.segmentCenter(5);
        Vec3 target = new Vec3(5.0D, 6.0D, 0.0D);

        assertTrue(rope.setDragTarget(5, target));
        assertEquals(initialCenter, rope.segmentCenter(5));
        double previousY = initialCenter.y;
        for (int tick = 0; tick < 4; tick++) {
            rope.step(null);
            double currentY = rope.segmentCenter(5).y;
            assertTrue(currentY >= previousY && currentY <= target.y,
                    () -> "pickup crossed target at y=" + currentY);
            previousY = currentY;
        }
        assertEquals(target, rope.segmentCenter(5));
        assertEquals(1.0D, rope.positions().get(4)
                .distanceTo(rope.positions().get(5)), 0.01D);
        assertEquals(1.0D, rope.positions().get(5)
                .distanceTo(rope.positions().get(6)), 0.01D);
    }


    @Test
    void heldSegmentFollowsTheLatestTargetAfterPickupHandoff() {
        RopeProperties properties = RopeProperties.DEFAULT;
        Vec3[] positions = new Vec3[properties.nodeCount()];
        Vec3[] velocities = new Vec3[positions.length];
        for (int node = 0; node < positions.length; node++) {
            positions[node] = new Vec3(node, 4.0D, 0.0D);
            velocities[node] = Vec3.ZERO;
        }
        RopeChain rope = new RopeChain(properties, positions, velocities);

        assertTrue(rope.setDragTarget(5, new Vec3(5.0D, 6.0D, 0.0D)));
        rope.step(null);
        assertTrue(rope.setDragTarget(5, new Vec3(5.0D, 8.0D, 0.0D)));
        for (int tick = 0; tick < 3; tick++) {
            rope.step(null);
        }

        assertEquals(8.0D, rope.segmentCenter(5).y, 0.01D);
    }

    @Test
    void heldSegmentIgnoresGravityUntilDragIsCleared() {
        RopeProperties properties = RopeProperties.DEFAULT;
        Vec3[] positions = new Vec3[properties.nodeCount()];
        Vec3[] velocities = new Vec3[properties.nodeCount()];
        for (int node = 0; node < positions.length; node++) {
            positions[node] = new Vec3(node, 8.0D, 0.0D);
            velocities[node] = Vec3.ZERO;
        }
        RopeChain rope = new RopeChain(properties, positions, velocities);
        rope.setDragTarget(5, new Vec3(5.0D, 10.0D, 0.0D));

        for (int tick = 0; tick < 20; tick++) {
            rope.step(null);
        }

        assertEquals(10.0D, rope.segmentCenter(5).y, 0.01D);
    }

    @Test
    void releasedSegmentFallsAgainInsteadOfStayingAnchored() {
        RopeProperties properties = RopeProperties.DEFAULT;
        Vec3[] positions = new Vec3[properties.nodeCount()];
        Vec3[] velocities = new Vec3[properties.nodeCount()];
        for (int node = 0; node < positions.length; node++) {
            positions[node] = new Vec3(node, 8.0D, 0.0D);
            velocities[node] = Vec3.ZERO;
        }
        RopeChain rope = new RopeChain(properties, positions, velocities);
        rope.setDragTarget(5, new Vec3(5.0D, 10.0D, 0.0D));
        for (int tick = 0; tick < 20; tick++) {
            rope.step(null);
        }
        double releasedAt = rope.segmentCenter(5).y;
        rope.clearDrag();
        for (int tick = 0; tick < 20; tick++) {
            rope.step(null);
        }

        assertTrue(rope.segmentCenter(5).y < releasedAt - 0.05D,
                () -> "released center stayed at " + rope.segmentCenter(5).y);
    }

    @Test
    void anchoredSegmentKeepsItsPositionAfterTheDragIsReleased() {
        RopeProperties properties = RopeProperties.DEFAULT;
        Vec3[] positions = new Vec3[properties.nodeCount()];
        Vec3[] velocities = new Vec3[properties.nodeCount()];
        for (int node = 0; node < positions.length; node++) {
            positions[node] = new Vec3(node, 8.0D, 0.0D);
            velocities[node] = Vec3.ZERO;
        }
        RopeChain rope = new RopeChain(properties, positions, velocities);
        RopeFrame rotated = RopeFrame.fromTangent(new Vec3(0.0D, 1.0D, 0.0D))
                .rotateAround(new Vec3(0.0D, 1.0D, 0.0D), Math.PI / 2.0D);
        assertTrue(rope.setDragTarget(5, new Vec3(5.0D, 10.0D, 0.0D), rotated));
        rope.step(null);
        assertTrue(rope.anchorSegment(5));
        Vec3 anchored = rope.segmentCenter(5);

        for (int tick = 0; tick < 20; tick++) {
            rope.step(null);
        }

        assertEquals(anchored, rope.segmentCenter(5));
        assertTrue(rope.isAnchored(5));
        assertEquals(-1, rope.draggedSegment());
        assertEquals(rotated, rope.anchoredOrientations().getFirst().frame());
    }

    @Test
    void draggingAnAnchoredSegmentAutomaticallyUnlocksIt() {
        RopeProperties properties = RopeProperties.DEFAULT;
        Vec3[] positions = new Vec3[properties.nodeCount()];
        Vec3[] velocities = new Vec3[properties.nodeCount()];
        for (int node = 0; node < positions.length; node++) {
            positions[node] = new Vec3(node, 8.0D, 0.0D);
            velocities[node] = Vec3.ZERO;
        }
        RopeChain rope = new RopeChain(properties, positions, velocities);
        rope.setDragTarget(5, new Vec3(5.0D, 10.0D, 0.0D));
        assertTrue(rope.anchorSegment(5));
        assertTrue(rope.setDragTarget(5, new Vec3(5.0D, 12.0D, 0.0D)));
        for (int tick = 0; tick < 20; tick++) {
            rope.step(null);
        }
        rope.clearDrag();
        assertTrue(!rope.isAnchored(5));
        assertEquals(12.0D, rope.segmentCenter(5).y, 0.01D);
    }

    @Test
    void severalSegmentsCanBeAnchoredIndependently() {
        RopeProperties properties = RopeProperties.DEFAULT;
        Vec3[] positions = new Vec3[properties.nodeCount()];
        Vec3[] velocities = new Vec3[properties.nodeCount()];
        for (int node = 0; node < positions.length; node++) {
            positions[node] = new Vec3(node, 8.0D, 0.0D);
            velocities[node] = Vec3.ZERO;
        }
        RopeChain rope = new RopeChain(properties, positions, velocities);
        for (int segment : new int[] {3, 10, 17}) {
            assertTrue(rope.setDragTarget(segment,
                    new Vec3(segment, 10.0D, 0.0D)));
            assertTrue(rope.anchorSegment(segment));
        }
        rope.step(null);

        assertTrue(rope.isAnchored(3));
        assertTrue(rope.isAnchored(10));
        assertTrue(rope.isAnchored(17));
        assertEquals(3, rope.anchoredOrientations().size());
    }

    @Test
    void anotherDraggedSegmentCannotMoveAnAnchoredSegment() {
        RopeProperties properties = RopeProperties.DEFAULT;
        Vec3[] positions = new Vec3[properties.nodeCount()];
        Vec3[] velocities = new Vec3[properties.nodeCount()];
        for (int node = 0; node < positions.length; node++) {
            positions[node] = new Vec3(node, 8.0D, 0.0D);
            velocities[node] = Vec3.ZERO;
        }
        RopeChain rope = new RopeChain(properties, positions, velocities);
        rope.setDragTarget(5, new Vec3(5.0D, 10.0D, 0.0D));
        rope.step(null);
        assertTrue(rope.anchorSegment(5));
        Vec3 anchored = rope.segmentCenter(5);
        assertTrue(rope.setDragTarget(8, new Vec3(8.0D, 14.0D, 0.0D)));
        rope.step(null);
        assertEquals(anchored, rope.segmentCenter(5));
    }

    @Test
    void anchoredSegmentRemainsExactlyFixedWhenRemoteDragReachesItsLimit() {
        RopeProperties properties = RopeProperties.DEFAULT;
        Vec3[] positions = new Vec3[properties.nodeCount()];
        Vec3[] velocities = new Vec3[properties.nodeCount()];
        for (int node = 0; node < positions.length; node++) {
            positions[node] = new Vec3(node, 8.0D, 0.0D);
            velocities[node] = Vec3.ZERO;
        }
        RopeChain rope = new RopeChain(properties, positions, velocities);
        assertTrue(rope.setDragTarget(0, new Vec3(0.0D, 10.0D, 0.0D)));
        rope.step(null);
        assertTrue(rope.anchorSegment(0));
        Vec3 fixedStart = rope.positions().get(0);
        Vec3 fixedEnd = rope.positions().get(1);

        assertTrue(rope.setDragTarget(6, new Vec3(6.0D, 40.0D, 0.0D)));
        for (int tick = 0; tick < 20; tick++) {
            rope.step(null);
            assertEquals(fixedStart, rope.positions().get(0));
            assertEquals(fixedEnd, rope.positions().get(1));
        }

        rope.clearDrag();
        assertTrue(rope.segmentCenter(6).y < 16.5D,
                () -> "drag ignored the anchored slack: " + rope.segmentCenter(6));
    }

    @Test
    void releasingAfterAnAcceptedTargetKeepsThatPose() {
        RopeProperties properties = RopeProperties.DEFAULT;
        Vec3[] positions = new Vec3[properties.nodeCount()];
        Vec3[] velocities = new Vec3[properties.nodeCount()];
        for (int node = 0; node < positions.length; node++) {
            positions[node] = new Vec3(node, 8.0D, 0.0D);
            velocities[node] = Vec3.ZERO;
        }
        RopeChain rope = new RopeChain(properties, positions, velocities);

        Vec3 before = rope.segmentCenter(5);
        assertTrue(rope.setDragTarget(5, new Vec3(5.0D, 11.0D, 0.0D)));
        for (int tick = 0; tick < 4; tick++) {
            rope.step(null);
        }
        rope.clearDrag();

        assertTrue(rope.segmentCenter(5).distanceTo(before) > 0.5D);
        assertEquals(11.0D, rope.segmentCenter(5).y, 0.01D);
    }

    @Test
    void wrappingAColumnDoesNotLaunchTheChain() {
        RopeProperties properties = RopeProperties.DEFAULT.withSegmentCount(8);
        Vec3[] positions = new Vec3[properties.nodeCount()];
        Vec3[] velocities = new Vec3[positions.length];
        for (int node = 0; node < positions.length; node++) {
            positions[node] = new Vec3(node * 0.5D, 1.0D, 0.0D);
            velocities[node] = Vec3.ZERO;
        }
        RopeChain rope = new RopeChain(properties, positions, velocities);
        RopeCollisionWorld column = RopeCollisionWorld.testing(List.of(
                new AABB(1.5D, 0.0D, -0.5D, 2.5D, 2.0D, 0.5D)));

        for (int tick = 0; tick < 30; tick++) {
            rope.step(column);
            for (Vec3 node : rope.positions()) {
                assertTrue(node.y < 6.0D && Math.abs(node.x) < 12.0D
                                && Math.abs(node.z) < 6.0D,
                        () -> "node launched off the column: " + node);
            }
        }
    }

    @Test
    void draggingAroundAColumnDoesNotLaunchTheTail() {
        RopeProperties properties = RopeProperties.DEFAULT.withSegmentCount(8);
        Vec3[] positions = new Vec3[properties.nodeCount()];
        Vec3[] velocities = new Vec3[positions.length];
        for (int node = 0; node < positions.length; node++) {
            positions[node] = new Vec3(3.0D + node, 1.0D, 0.0D);
            velocities[node] = Vec3.ZERO;
        }
        RopeChain rope = new RopeChain(properties, positions, velocities);
        RopeCollisionWorld column = RopeCollisionWorld.testing(List.of(
                new AABB(1.0D, 0.0D, -1.0D, 2.0D, 3.0D, 0.0D)));
        RopeFrame frame = RopeFrame.fromTangent(new Vec3(1.0D, 0.0D, 0.0D));
        assertTrue(rope.setDragTarget(0, new Vec3(3.5D, 1.0D, 0.0D), frame));
        assertTrue(rope.anchorSegment(0));

        Vec3[] path = {
                new Vec3(10.0D, 1.0D, 1.5D),
                new Vec3(1.5D, 1.0D, 3.0D),
                new Vec3(-1.0D, 1.0D, 1.5D),
                new Vec3(-1.0D, 1.0D, -2.5D),
                new Vec3(1.5D, 1.0D, -3.0D)
        };
        for (Vec3 target : path) {
            assertTrue(rope.setDragTarget(7, target, frame));
            for (int tick = 0; tick < 8; tick++) {
                rope.step(column);
                for (Vec3 node : rope.positions()) {
                    assertTrue(node.y > -2.0D && node.y < 6.0D
                                    && node.x > -6.0D && node.x < 14.0D
                                    && node.z > -8.0D && node.z < 8.0D,
                            () -> "wrapped node launched: " + node);
                }
            }
        }
        assertTrue(rope.maximumSegmentError() < 0.35D,
                () -> "segment error=" + rope.maximumSegmentError());
    }

    @Test
    void releasingAGrabClearsItsStoredVelocity() {
        RopeProperties properties = RopeProperties.DEFAULT.withSegmentCount(8);
        Vec3[] positions = new Vec3[properties.nodeCount()];
        Vec3[] velocities = new Vec3[positions.length];
        for (int node = 0; node < positions.length; node++) {
            positions[node] = new Vec3(node, 8.0D, 0.0D);
            velocities[node] = Vec3.ZERO;
        }
        RopeChain rope = new RopeChain(properties, positions, velocities);

        assertTrue(rope.setDragTarget(0, new Vec3(0.5D, 12.0D, 0.0D)));
        rope.step(null);
        rope.step(null);
        rope.clearDrag();
        Vec3 released = rope.segmentCenter(0);
        for (int tick = 0; tick < 8; tick++) {
            rope.step(null);
        }
        assertTrue(rope.segmentCenter(0).y < released.y,
                "released segment retained an upward launch");
    }

    @Test
    void extendingAtEitherEndpointAddsExactlyOneRigidSegment() {
        RopeProperties properties = RopeProperties.DEFAULT.withSegmentCount(6);
        Vec3[] positions = new Vec3[properties.nodeCount()];
        Vec3[] velocities = new Vec3[positions.length];
        for (int node = 0; node < positions.length; node++) {
            positions[node] = new Vec3(0.0D, node, 0.0D);
            velocities[node] = Vec3.ZERO;
        }
        RopeChain rope = new RopeChain(properties, positions, velocities);

        RopeChain atStart = rope.extended(true);
        RopeChain atEnd = rope.extended(false);

        assertEquals(7, atStart.segmentCount());
        assertEquals(7, atEnd.segmentCount());
        assertEquals(0.0D, atStart.maximumSegmentError(), 1.0E-9D);
        assertEquals(0.0D, atEnd.maximumSegmentError(), 1.0E-9D);
        assertEquals(new Vec3(0.0D, -1.0D, 0.0D), atStart.positions().getFirst());
        assertEquals(new Vec3(0.0D, 7.0D, 0.0D), atEnd.positions().getLast());
    }

    @Test
    void onlyFreeEndpointsCanConnect() {
        RopeProperties properties = RopeProperties.DEFAULT.withSegmentCount(3);
        Vec3[] positions = {
                new Vec3(0.0D, 2.0D, 0.0D),
                new Vec3(1.0D, 2.0D, 0.0D),
                new Vec3(2.0D, 2.0D, 0.0D),
                new Vec3(3.0D, 2.0D, 0.0D)
        };
        Vec3[] velocities = {Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO};
        RopeChain rope = new RopeChain(properties, positions, velocities);

        assertTrue(rope.canConnectAt(0));
        assertTrue(rope.canConnectAt(2));
        assertTrue(!rope.canConnectAt(1));
    }

    @Test
    void joiningEndpointsPreservesDraggedRopeAndSharesTheEndpoint() {
        RopeProperties properties = RopeProperties.DEFAULT.withSegmentCount(2);
        Vec3[] velocities = {Vec3.ZERO, Vec3.ZERO, Vec3.ZERO};
        RopeChain dragged = new RopeChain(properties, new Vec3[] {
                new Vec3(0.0D, 2.0D, 0.0D),
                new Vec3(1.0D, 2.0D, 0.0D),
                new Vec3(2.0D, 2.0D, 0.0D)
        }, velocities);
        RopeChain target = new RopeChain(properties, new Vec3[] {
                new Vec3(10.0D, 2.0D, 0.0D),
                new Vec3(11.0D, 2.0D, 0.0D),
                new Vec3(12.0D, 2.0D, 0.0D)
        }, velocities);

        RopeChain joined = dragged.join(target, 1, 0);

        assertTrue(joined != null);
        assertEquals(4, joined.segmentCount());
        assertEquals(new Vec3(0.0D, 2.0D, 0.0D), joined.positions().get(0));
        assertEquals(new Vec3(1.0D, 2.0D, 0.0D), joined.positions().get(1));
        assertEquals(new Vec3(2.0D, 2.0D, 0.0D), joined.positions().get(2));
        assertEquals(new Vec3(3.0D, 2.0D, 0.0D), joined.positions().get(3));
        assertEquals(new Vec3(4.0D, 2.0D, 0.0D), joined.positions().get(4));
        assertEquals(0.0D, joined.maximumSegmentError(), 1.0E-9D);
    }

    @Test
    void joiningReversedEndpointsKeepsAllSegmentsRigid() {
        RopeProperties properties = RopeProperties.DEFAULT.withSegmentCount(2);
        Vec3[] velocities = {Vec3.ZERO, Vec3.ZERO, Vec3.ZERO};
        RopeChain first = new RopeChain(properties, new Vec3[] {
                new Vec3(0.0D, 2.0D, 0.0D),
                new Vec3(1.0D, 2.0D, 0.0D),
                new Vec3(2.0D, 2.0D, 0.0D)
        }, velocities);
        RopeChain second = new RopeChain(properties, new Vec3[] {
                new Vec3(10.0D, 2.0D, 0.0D),
                new Vec3(11.0D, 2.0D, 0.0D),
                new Vec3(12.0D, 2.0D, 0.0D)
        }, velocities);

        RopeChain joined = first.join(second, 0, 1);

        assertTrue(joined != null);
        assertEquals(new Vec3(2.0D, 2.0D, 0.0D), joined.positions().get(0));
        assertEquals(new Vec3(1.0D, 2.0D, 0.0D), joined.positions().get(1));
        assertEquals(new Vec3(0.0D, 2.0D, 0.0D), joined.positions().get(2));
        assertEquals(new Vec3(-1.0D, 2.0D, 0.0D), joined.positions().get(3));
        assertEquals(new Vec3(-2.0D, 2.0D, 0.0D), joined.positions().get(4));
        assertEquals(0.0D, joined.maximumSegmentError(), 1.0E-9D);
    }

    @Test
    void everyHeadAndTailPairCanConnectWithoutStretching() {
        RopeProperties properties = RopeProperties.DEFAULT.withSegmentCount(2);
        int[] endpoints = {0, 1};
        for (int firstEndpoint : endpoints) {
            for (int secondEndpoint : endpoints) {
                RopeChain first = new RopeChain(properties, new Vec3[] {
                        new Vec3(0.0D, 2.0D, 0.0D),
                        new Vec3(1.0D, 2.0D, 0.0D),
                        new Vec3(2.0D, 2.0D, 0.0D)
                }, new Vec3[] {Vec3.ZERO, Vec3.ZERO, Vec3.ZERO});
                RopeChain second = new RopeChain(properties, new Vec3[] {
                        new Vec3(10.0D, 2.0D, 0.0D),
                        new Vec3(11.0D, 2.0D, 0.0D),
                        new Vec3(12.0D, 2.0D, 0.0D)
                }, new Vec3[] {Vec3.ZERO, Vec3.ZERO, Vec3.ZERO});

                RopeChain joined = first.join(
                        second, firstEndpoint, secondEndpoint);

                assertTrue(joined != null);
                assertEquals(4, joined.segmentCount());
                assertEquals(0.0D, joined.maximumSegmentError(), 1.0E-9D);
            }
        }
    }

    @Test
    void endpointReachUsesTheNearestVisiblePartInsteadOfTheSegmentCenter() {
        Vec3 eye = Vec3.ZERO;
        Vec3 start = new Vec3(4.0D, 0.0D, 0.0D);
        Vec3 end = new Vec3(5.0D, 0.0D, 0.0D);

        Vec3 nearest = RopeHitGeometry.closestPointOnSegment(eye, start, end);

        assertEquals(start, nearest);
        assertTrue(eye.distanceTo(nearest) <= 4.2D);
        assertTrue(eye.distanceTo(start.lerp(end, 0.5D)) > 4.2D);
    }

    @Test
    void breakingOneSegmentSplitsTheRemainingChainWithoutMovingItsNodes() {
        RopeProperties properties = RopeProperties.DEFAULT.withSegmentCount(6);
        Vec3[] positions = new Vec3[properties.nodeCount()];
        Vec3[] velocities = new Vec3[positions.length];
        for (int node = 0; node < positions.length; node++) {
            positions[node] = new Vec3(node, 3.0D, 0.0D);
            velocities[node] = Vec3.ZERO;
        }
        RopeChain rope = new RopeChain(properties, positions, velocities);
        RopeChain.Split split = rope.splitAt(2);

        assertEquals(2, split.first().segmentCount());
        assertEquals(3, split.second().segmentCount());
        assertEquals(new Vec3(0.0D, 3.0D, 0.0D), split.first().positions().getFirst());
        assertEquals(new Vec3(6.0D, 3.0D, 0.0D), split.second().positions().getLast());
        assertEquals(0.0D, split.first().maximumSegmentError(), 1.0E-9D);
        assertEquals(0.0D, split.second().maximumSegmentError(), 1.0E-9D);
    }

    @Test
    void extensionStopsAtTheConfiguredMaximumSegmentCount() {
        RopeProperties properties = RopeProperties.DEFAULT.withSegmentCount(
                RopeProperties.MAX_SEGMENTS);
        Vec3[] positions = new Vec3[properties.nodeCount()];
        Vec3[] velocities = new Vec3[positions.length];
        for (int node = 0; node < positions.length; node++) {
            positions[node] = new Vec3(0.0D, node, 0.0D);
            velocities[node] = Vec3.ZERO;
        }

        RopeChain rope = new RopeChain(properties, positions, velocities);

        assertEquals(null, rope.extended(false));
        assertEquals(RopeProperties.MAX_SEGMENTS, rope.segmentCount());
    }

    @Test
    void breakingTheConnectedRopeTakesTwiceTheSingleSegmentDuration() {
        assertEquals(15, RopeRuntime.breakDurationTicks(false));
        assertEquals(30, RopeRuntime.breakDurationTicks(true));
    }

    @Test
    void aFastChainCannotTunnelThroughAThinPane() {
        RopeProperties properties = RopeProperties.DEFAULT.withSegmentCount(8);
        Vec3[] positions = new Vec3[properties.nodeCount()];
        Vec3[] velocities = new Vec3[positions.length];
        for (int node = 0; node < positions.length; node++) {
            positions[node] = new Vec3(-1.0D - node, 1.0D, 0.0D);
            velocities[node] = new Vec3(1.2D, 0.0D, 0.0D);
        }
        RopeChain rope = new RopeChain(properties, positions, velocities);
        RopeCollisionWorld pane = RopeCollisionWorld.testing(List.of(
                new AABB(0.40D, -100.0D, -4.0D, 0.60D, 100.0D, 4.0D)));

        for (int tick = 0; tick < 20; tick++) {
            rope.step(pane);
            for (Vec3 node : rope.positions()) {
                assertTrue(node.x < 0.40D,
                        () -> "node tunneled through the pane: " + node);
            }
        }
    }

    @Test
    void aSettledChainStopsMovingInsteadOfJittering() {
        RopeProperties properties = RopeProperties.DEFAULT.withSegmentCount(8);
        Vec3[] positions = new Vec3[properties.nodeCount()];
        Vec3[] velocities = new Vec3[positions.length];
        for (int node = 0; node < positions.length; node++) {
            positions[node] = new Vec3(node, 0.45D, 0.0D);
            velocities[node] = Vec3.ZERO;
        }
        RopeChain rope = new RopeChain(properties, positions, velocities);
        RopeCollisionWorld floor = RopeCollisionWorld.testing(List.of(
                new AABB(-100.0D, -1.0D, -100.0D, 100.0D, 0.0D, 100.0D)));
        for (int tick = 0; tick < 40; tick++) {
            rope.step(floor);
        }

        List<Vec3> settled = rope.positions();
        for (int tick = 0; tick < 10; tick++) {
            rope.step(floor);
            List<Vec3> now = rope.positions();
            for (int node = 0; node < now.size(); node++) {
                double drift = now.get(node).distanceTo(settled.get(node));
                assertTrue(drift < 0.002D,
                        "settled node " + node + " drifted " + drift);
            }
        }
    }

    @Test
    void theCollisionCorridorCoversTheNextTickOfMotion() {
        RopeChain rope = RopeChain.thrown(RopeProperties.DEFAULT,
                new Vec3(0.0D, 4.0D, 0.0D), new Vec3(0.0D, 0.0D, 1.0D), 1.0F);

        List<Vec3> nodes = rope.positions();
        List<Vec3> targets = rope.motionTargets(2);

        assertEquals(nodes.size(), targets.size());
        double reach = 0.0D;
        for (int node = 0; node < nodes.size(); node++) {
            reach = Math.max(reach, nodes.get(node).distanceTo(targets.get(node)));
        }
        assertTrue(reach > 2.0D, "corridor ignored the throw speed: " + reach);
    }

}

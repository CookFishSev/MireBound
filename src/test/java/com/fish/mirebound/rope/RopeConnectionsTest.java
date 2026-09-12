package com.fish.mirebound.rope;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class RopeConnectionsTest {
    @Test
    void allEndpointPairingsKeepBothPosesAndVelocitiesOnConnect() {
        for (boolean firstStart : new boolean[] {true, false}) {
            for (boolean secondStart : new boolean[] {true, false}) {
                RopeChain first = straight(4, new Vec3(0, 3, 0), new Vec3(0.15, -0.1, 0));
                RopeChain second = straight(4, new Vec3(0, 4, 1), new Vec3(-0.1, 0, 0.2));
                Map<Integer, RopeChain> ropes = Map.of(1, first, 2, second);
                List<Vec3> a = first.positions(), b = second.positions();
                List<Vec3> va = first.velocities(), vb = second.velocities();
                RopeConnections links = new RopeConnections();
                assertTrue(connect(links, ropes, new RopeEndpoint(1, firstStart), new RopeEndpoint(2, secondStart)));
                assertEquals(a, first.positions());
                assertEquals(b, second.positions());
                assertEquals(va, first.velocities());
                assertEquals(vb, second.velocities());
            }
        }
    }

    @Test
    void aRopeCanCloseOntoItselfButAnOccupiedEndCannotConnectAgain() {
        RopeChain rope = straight(4, Vec3.ZERO, Vec3.ZERO);
        Map<Integer, RopeChain> ropes = Map.of(1, rope);
        RopeConnections links = new RopeConnections();
        RopeEndpoint start = new RopeEndpoint(1, true), end = new RopeEndpoint(1, false);
        assertFalse(connect(links, ropes, start, start));
        assertTrue(connect(links, ropes, start, end));
        assertEquals(end, links.other(start));
        assertEquals(start, links.other(end));
        assertFalse(connect(links, ropes, end, start));
    }

    @Test
    void rescueFreeEndsConnectToOrdinaryAndOtherRescueRopesWithoutMovingTheLoops() {
        for (boolean secondRescue : new boolean[] {true, false}) {
            RopeChain first = rescue(Vec3.ZERO);
            RopeChain second = secondRescue ? rescue(new Vec3(0, 0, 2))
                    : straight(4, new Vec3(0, 2, 2), Vec3.ZERO);
            Map<Integer, RopeChain> ropes = Map.of(1, first, 2, second);
            List<RopeChain.AnchorState> firstAnchors = first.anchorStates();
            List<RopeChain.AnchorState> secondAnchors = second.anchorStates();
            RopeConnections links = new RopeConnections();
            assertFalse(first.canConnectAt(first.segmentCount() - 1));
            assertFalse(connect(links, ropes, new RopeEndpoint(1, false), new RopeEndpoint(2, true)));
            assertTrue(connect(links, ropes, new RopeEndpoint(1, true), new RopeEndpoint(2, true)));
            for (int tick = 0; tick < 80; tick++) step(links, ropes);
            assertEquals(firstAnchors, first.anchorStates());
            assertEquals(secondAnchors, second.anchorStates());
            for (RopeChain.AnchorState anchor : firstAnchors) {
                assertEquals(anchor.start(), first.point(anchor.segment()));
                assertEquals(anchor.end(), first.point(anchor.segment() + 1));
            }
            assertTrue(first.point(0).distanceTo(second.point(0)) < 0.04,
                    () -> "gap=" + first.point(0).distanceTo(second.point(0)));
            assertTrue(first.maximumSegmentError() < 0.12, () -> "stretch=" + first.maximumSegmentError());
        }
    }

    @Test
    void connectingApproachesGraduallyWithoutMovingTheOtherNodes() {
        RopeChain first = straight(4, Vec3.ZERO, new Vec3(0.1, 0.05, 0));
        RopeChain second = straight(4, new Vec3(0, 0, 2), new Vec3(0.1, 0.05, 0));
        Map<Integer, RopeChain> ropes = Map.of(1, first, 2, second);
        RopeConnections links = new RopeConnections();
        assertTrue(connect(links, ropes, new RopeEndpoint(1, true), new RopeEndpoint(2, true)));
        List<Vec3> positions = first.positions();
        links.advance(ropes::get);
        links.solve(ropes::get, id -> null);
        assertTrue(first.point(0).length() <= 0.061);
        for (int point = 1; point <= first.segmentCount(); point++) {
            assertEquals(positions.get(point), first.point(point));
        }
    }

    @Test
    void closedLoopKeepsFiniteLengthUnderGravity() {
        Vec3[] nodes = {new Vec3(0, 10, 0), new Vec3(1, 10, 0), new Vec3(2, 10, 0),
                new Vec3(2, 10, 1), new Vec3(2, 10, 2), new Vec3(1, 10, 2), new Vec3(0, 10, 2)};
        Vec3[] velocities = new Vec3[nodes.length];
        Arrays.fill(velocities, Vec3.ZERO);
        RopeChain rope = new RopeChain(RopeProperties.DEFAULT.withSegmentCount(6), nodes, velocities);
        Map<Integer, RopeChain> ropes = Map.of(1, rope);
        RopeConnections links = new RopeConnections();
        assertTrue(connect(links, ropes, new RopeEndpoint(1, true), new RopeEndpoint(1, false)));
        for (int tick = 0; tick < 200; tick++) step(links, ropes);
        assertTrue(rope.point(0).distanceTo(rope.point(rope.segmentCount())) < 0.02);
        assertTrue(rope.maximumSegmentError() < 0.08, () -> "stretch=" + rope.maximumSegmentError());
    }

    @Test
    void savingRestoresLinksAndSplittingRemapsOnlyTheSurvivingEnds() {
        RopeChain first = straight(4, Vec3.ZERO, Vec3.ZERO);
        RopeChain second = straight(4, new Vec3(0, 0, 1), Vec3.ZERO);
        Map<Integer, RopeChain> ropes = Map.of(1, first, 2, second);
        RopeConnections links = new RopeConnections();
        assertTrue(connect(links, ropes, new RopeEndpoint(1, true), new RopeEndpoint(2, false)));
        RopeSavedData saved = RopeSavedData.load(new CompoundTag(), null);
        saved.replace(3, List.of(state(1, first), state(2, second)), links.states());
        RopeSavedData restored = RopeSavedData.load(saved.save(new CompoundTag(), null), null);
        assertEquals(links.states(), restored.connections());
        assertEquals(2, restored.states().size());
        links.split(1, 3, 4);
        assertEquals(new RopeEndpoint(3, true), links.other(new RopeEndpoint(2, false)));
        links.split(3, -1, 5);
        assertNull(links.other(new RopeEndpoint(2, false)));
    }

    @Test
    void unknownOrReservedRestoredEndpointsAreRejected() {
        Map<Integer, RopeChain> ropes = Map.of(1, rescue(Vec3.ZERO));
        RopeConnections links = new RopeConnections();
        assertFalse(links.restore(new RopeConnections.State(new RopeEndpoint(1, true), new RopeEndpoint(7, false), 0), ropes::get));
        assertFalse(links.restore(new RopeConnections.State(new RopeEndpoint(1, true), new RopeEndpoint(1, false), 0), ropes::get));
    }

    @Test
    void lettingGoPreservesTheMotionOfEveryUnheldNode() {
        RopeChain rope = straight(8, new Vec3(0, 4, 0), new Vec3(0.3, -0.1, 0.2));
        rope.setDragTarget(3, new Vec3(3.5, 4, 0));
        for (int tick = 0; tick < 6; tick++) rope.step(null);
        List<Vec3> before = rope.velocities();
        List<Vec3> positions = rope.positions();
        rope.clearDrag();
        assertEquals(positions, rope.positions());
        for (int point = 0; point <= rope.segmentCount(); point++) {
            if (point == 3 || point == 4) continue;
            assertEquals(0, before.get(point).distanceTo(rope.velocities().get(point)), 1e-12);
        }
    }

    @Test
    void blockedJointDoesNotPullEndpointsThroughAWallOrReleaseItsLink() {
        RopeChain first = straight(4, new Vec3(0, 3, -1), Vec3.ZERO);
        RopeChain second = straight(4, new Vec3(0, 3, 1), Vec3.ZERO);
        Map<Integer, RopeChain> ropes = Map.of(1, first, 2, second);
        RopeConnections links = new RopeConnections();
        RopeEndpoint a = new RopeEndpoint(1, true), b = new RopeEndpoint(2, true);
        assertTrue(connect(links, ropes, a, b));
        RopeCollisionWorld wall = RopeCollisionWorld.testing(List.of(
                new net.minecraft.world.phys.AABB(-8, -100, -0.25, 12, 100, 0.25)));
        for (int tick = 0; tick < 40; tick++) links.step(ropes::get, id -> wall);
        assertTrue(first.point(0).z < -0.25);
        assertTrue(second.point(0).z > 0.25);
        assertEquals(b, links.other(a));
        assertTrue(first.positions().stream().allMatch(p -> Double.isFinite(p.lengthSqr())));
    }

    @Test
    void closedLoopDragUsesTheAnchorAcrossItsJoinedEnds() {
        RopeChain rope = straight(8, Vec3.ZERO, Vec3.ZERO);
        Map<Integer, RopeChain> ropes = Map.of(1, rope);
        RopeConnections links = new RopeConnections();
        assertTrue(links.restore(new RopeConnections.State(
                new RopeEndpoint(1, true), new RopeEndpoint(1, false), 0), ropes::get));
        rope.restoreAnchors(List.of(new RopeChain.AnchorState(5,
                RopeFrame.fromTangent(new Vec3(1, 0, 0)), rope.point(5), rope.point(6))));
        RopeFrame frame = RopeFrame.fromTangent(new Vec3(1, 0, 0));
        Vec3 clamped = links.clampDragTarget(1, 0, new Vec3(-20, 0, 0), frame, ropes::get);
        Vec3 start = clamped.subtract(frame.y().scale(0.5));
        assertTrue(start.distanceTo(rope.point(6)) <= 2.0 + 1e-6);
    }

    private static RopeSavedData.State state(int id, RopeChain chain) {
        return new RopeSavedData.State(id, null, 0, chain.properties(), chain.positions(), chain.velocities(), chain.anchorStates());
    }

    private static void step(RopeConnections links, Map<Integer, RopeChain> ropes) {
        links.step(ropes::get, id -> null);
    }

    private static boolean connect(RopeConnections links, Map<Integer, RopeChain> ropes,
            RopeEndpoint first, RopeEndpoint second) {
        double slack = ropes.get(first.ropeId()).point(first.point(ropes.get(first.ropeId())))
                .distanceTo(ropes.get(second.ropeId()).point(second.point(ropes.get(second.ropeId()))));
        return links.add(new RopeConnections.State(first, second, slack), ropes::get);
    }

    private static RopeChain straight(int segments, Vec3 origin, Vec3 velocity) {
        Vec3[] nodes = new Vec3[segments + 1], velocities = new Vec3[segments + 1];
        for (int i = 0; i < nodes.length; i++) {
            nodes[i] = origin.add(i, 0, 0);
            velocities[i] = velocity;
        }
        return new RopeChain(RopeProperties.DEFAULT.withSegmentCount(segments), nodes, velocities);
    }

    private static RopeChain rescue(Vec3 offset) {
        Vec3[] nodes = new Vec3[10], velocity = new Vec3[10], loop = new Vec3[6];
        double radius = 1.0 / (2 * Math.sin(Math.PI / 5));
        Vec3 center = offset.add(3 - radius, 3, 0);
        for (int i = 0; i < 5; i++) loop[i] = center.add(Math.cos(i * Math.PI * 2 / 5) * radius, 0, Math.sin(i * Math.PI * 2 / 5) * radius);
        loop[5] = loop[0];
        for (int i = 0; i < 4; i++) nodes[i] = offset.add(i - 1, 3, 0);
        System.arraycopy(loop, 0, nodes, 4, 6);
        Arrays.fill(velocity, Vec3.ZERO);
        RopeChain chain = new RopeChain(RopeProperties.DEFAULT.withSegmentCount(9), nodes, velocity);
        assertTrue(chain.anchorLasso(4, loop));
        return chain;
    }
}

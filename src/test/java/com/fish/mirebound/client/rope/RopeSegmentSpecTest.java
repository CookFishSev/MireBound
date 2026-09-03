package com.fish.mirebound.client.rope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fish.mirebound.rope.RopeFrame;
import com.fish.mirebound.rope.RopeHitGeometry;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class RopeSegmentSpecTest {
    @Test
    void cuboidFaceNormalsPointOutward() {
        Vec3 negativeX = RopeSegmentSpec.faceNormal(
                new Vec3(-1.0D, -1.0D, -1.0D),
                new Vec3(-1.0D, 1.0D, -1.0D),
                new Vec3(-1.0D, 1.0D, 1.0D));
        Vec3 positiveY = RopeSegmentSpec.faceNormal(
                new Vec3(-1.0D, 1.0D, -1.0D),
                new Vec3(1.0D, 1.0D, -1.0D),
                new Vec3(1.0D, 1.0D, 1.0D));

        assertEquals(new Vec3(-1.0D, 0.0D, 0.0D), negativeX);
        assertEquals(new Vec3(0.0D, 1.0D, 0.0D), positiveY);
    }

    @Test
    void jointFacesFollowTheAdjacentSideDirection() {
        Vec3 a = new Vec3(-1.0D, -1.0D, 1.0D);
        Vec3 b = new Vec3(-1.0D, 1.0D, 1.0D);
        Vec3 c = new Vec3(1.0D, 1.0D, 1.0D);

        assertEquals(false, RopeSegmentSpec.facePointsAwayFrom(
                new Vec3(0.0D, 0.0D, 1.0D), a, b, c));
        assertEquals(true, RopeSegmentSpec.facePointsAwayFrom(
                new Vec3(0.0D, 0.0D, -1.0D), a, b, c));
    }

    @Test
    void jointGeometrySkipsDegenerateStraightAndFoldedCases() {
        assertEquals(false, RopeSegmentSpec.shouldRenderJoint(1.0D));
        assertEquals(false, RopeSegmentSpec.shouldRenderJoint(-1.0D));
        assertEquals(true, RopeSegmentSpec.shouldRenderJoint(0.0D));
        assertEquals(true, RopeSegmentSpec.shouldRenderJoint(0.8D));
    }

    @Test
    void worldStableFramesDoNotInheritRollFromPreviousSegments() {
        List<Vec3> nodes = List.of(
                Vec3.ZERO,
                new Vec3(1.0D, 0.0D, 0.0D),
                new Vec3(1.0D, 1.0D, 0.0D),
                new Vec3(2.0D, 1.0D, 0.0D));

        RopeSegmentPose.Frame[] frames = RopeSegmentPose.frames(nodes);

        assertEquals(3, frames.length);
        assertEquals(RopeSegmentPose.initial(new Vec3(1.0D, 0.0D, 0.0D)),
                frames[0]);
        assertEquals(RopeSegmentPose.initial(new Vec3(0.0D, 1.0D, 0.0D)),
                frames[1]);
        assertEquals(frames[0], frames[2]);
    }

    @Test
    void selectionBoxKeepsAStableFixedSegmentLength() {
        RopeSelectionGeometry.Box box = RopeSelectionGeometry.of(
                Vec3.ZERO, new Vec3(0.0D, 0.0D, 1.0D),
                2.125D / 16.0D, 0.5D);

        assertEquals(8, box.corners().size());
        assertEquals(1.0D, box.halfLength() * 2.0D, 0.0D);
        assertEquals(4.25D / 16.0D, box.halfWidth() * 2.0D, 0.0D);
    }

    @Test
    void selectionRayHitsTheMiddleOfAnOrientedSegment() {
        RopeSelectionGeometry.Box box = RopeSelectionGeometry.of(
                Vec3.ZERO, new Vec3(1.0D, 0.0D, 0.0D),
                RopeSegmentPose.initial(new Vec3(1.0D, 0.0D, 0.0D)),
                2.125D / 16.0D, 0.5D);

        double hit = RopeSelectionGeometry.rayHitDistance(
                new Vec3(0.5D, 0.0D, -3.0D), new Vec3(0.0D, 0.0D, 1.0D),
                box, 0.01D, 10.0D);
        assertEquals(3.0D - 2.125D / 16.0D - 0.01D, hit, 0.02D);
    }

    @Test
    void selectionRayHitsTheMiddleOfAVerticalSegment() {
        double radius = RopeHitGeometry.SELECTION_RADIUS;
        double hit = RopeHitGeometry.rayCapsuleHitDistance(
                new Vec3(0.05D, 0.5D, -3.0D), new Vec3(0.0D, 0.0D, 1.0D),
                Vec3.ZERO, new Vec3(0.0D, 1.0D, 0.0D), radius, 10.0D);

        assertEquals(3.0D - Math.sqrt(radius * radius - 0.05D * 0.05D),
                hit, 1.0E-9D);
    }

    @Test
    void rollingAFrameKeepsItsLongAxisAndBasisOrthonormal() {
        RopeFrame base = RopeFrame.fromTangent(new Vec3(0.0D, 1.0D, 0.0D));
        RopeFrame rolled = base.rotateAround(base.y(), Math.PI / 3.0D);

        assertEquals(base.y(), rolled.y());
        assertEquals(1.0D, rolled.x().length(), 1.0E-9D);
        assertEquals(1.0D, rolled.z().length(), 1.0E-9D);
        assertEquals(0.0D, rolled.x().dot(rolled.z()), 1.0E-9D);
    }

    @Test
    void physicsStaffInputUsesOnlyTheTwoScreenPlaneAxes() {
        RopeFrame base = RopeFrame.fromTangent(new Vec3(0.35D, 0.82D, -0.44D));
        Vec3 forward = new Vec3(0.31D, -0.22D, 0.92D).normalize();
        ClientRopes.ScreenPlaneAxes axes = ClientRopes.screenPlaneAxes(
                forward, new Vec3(0.05D, 0.98D, 0.18D));
        RopeFrame horizontal = base.applyScreenPlaneInput(
                27.0D, 0.0D, axes.up(), axes.right(), 0.35D);
        RopeFrame vertical = base.applyScreenPlaneInput(
                0.0D, 31.0D, axes.up(), axes.right(), 0.35D);
        RopeFrame rotated = base.applyScreenPlaneInput(
                27.0D, 31.0D, axes.up(), axes.right(), 0.35D);
        RopeFrame horizontalAxisRotation = RopeFrame.fromTangent(forward)
                .applyScreenPlaneInput(
                        0.0D, 10.0D, axes.up(), axes.right(), 1.0D);

        assertEquals(0.0D, axes.up().dot(forward), 1.0E-9D);
        assertEquals(0.0D, axes.right().dot(forward), 1.0E-9D);
        assertEquals(0.0D, axes.up().dot(axes.right()), 1.0E-9D);
        assertTrue(horizontalAxisRotation.y().dot(axes.up()) > 0.0D);
        assertEquals(base.y().dot(axes.up()),
                horizontal.y().dot(axes.up()), 1.0E-9D);
        assertEquals(base.y().dot(axes.right()),
                vertical.y().dot(axes.right()), 1.0E-9D);
        assertEquals(1.0D, rotated.x().length(), 1.0E-9D);
        assertEquals(1.0D, rotated.y().length(), 1.0E-9D);
        assertEquals(1.0D, rotated.z().length(), 1.0E-9D);
        assertEquals(0.0D, rotated.x().dot(rotated.y()), 1.0E-9D);
        assertEquals(0.0D, rotated.y().dot(rotated.z()), 1.0E-9D);
        assertEquals(0.0D, rotated.z().dot(rotated.x()), 1.0E-9D);
        assertTrue(rotated.y().subtract(base.y()).length() > 1.0E-4D);
        assertTrue(rotated.x().subtract(base.x()).length() > 1.0E-4D);
    }

    @Test
    void localTabRotationKeepsTheHeldSegmentRigid() {
        List<Vec3> nodes = List.of(
                new Vec3(-1.0D, 0.0D, 0.0D),
                Vec3.ZERO,
                new Vec3(0.0D, 1.0D, 0.0D),
                new Vec3(1.0D, 1.0D, 0.0D));
        RopeFrame frame = RopeFrame.fromTangent(
                new Vec3(0.6D, 0.2D, 0.77D));
        Vec3 originalCenter = nodes.get(1).lerp(nodes.get(2), 0.5D);

        List<Vec3> adjusted = RopeSegmentPose.withRigidSegment(
                nodes, 1, frame, 1.0D);

        assertEquals(1.0D, adjusted.get(1).distanceTo(adjusted.get(2)), 1.0E-9D);
        assertEquals(originalCenter,
                adjusted.get(1).lerp(adjusted.get(2), 0.5D));
        assertTrue(frame.y().distanceTo(
                adjusted.get(2).subtract(adjusted.get(1)).normalize()) < 1.0E-9D);
        assertEquals(nodes.getFirst(), adjusted.getFirst());
        assertEquals(nodes.getLast(), adjusted.getLast());
    }

    @Test
    void authoredCuboidsRetainOneBlockLength() {
        RopeSegmentSpec.Cuboid inner = RopeSegmentSpec.INNER;
        RopeSegmentSpec.Cuboid outer = RopeSegmentSpec.OUTER;

        assertEquals(4.0D / 16.0D, inner.halfWidth() * 2.0D, 0.0D);
        assertEquals(16.0D / 16.0D, inner.halfLength() * 2.0D, 0.0D);
        assertEquals(4.25D / 16.0D, outer.halfWidth() * 2.0D, 0.0D);
        assertEquals(16.25D / 16.0D, outer.halfLength() * 2.0D, 0.0D);
    }

    @Test
    void authoredEndCapsUseTheOriginalModelUvs() {
        assertEquals(new RopeSegmentSpec.Uv(
                        4.0F / 32.0F, 16.0F / 32.0F, 0.0F, 20.0F / 32.0F),
                RopeSegmentSpec.INNER.cap());
        assertEquals(new RopeSegmentSpec.Uv(
                        8.0F / 32.0F, 0.0F, 4.0F / 32.0F, 4.0F / 32.0F),
                RopeSegmentSpec.OUTER.cap());
    }
}

package com.fish.mirebound.client.tentacle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fish.mirebound.tentacle.TentacleGrabTarget;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class ProceduralTentacleRendererTest {
    private static final double EPSILON = 1.0E-5D;

    @Test
    void primaryWrapStartsAtThePhysicalTipWithoutAnExtraConnector() {
        Vec3 physicalTip = new Vec3(1.0D, 2.0D, 0.0D);
        List<Vec3> coil = List.of(
                new Vec3(0.35D, -0.80D, 0.0D),
                new Vec3(0.30D, -0.65D, 0.10D),
                new Vec3(0.10D, 0.0D, 0.35D),
                new Vec3(-0.35D, 0.80D, 0.0D));
        List<Double> radii = List.of(0.08D, 0.10D, 0.10D, 0.07D);

        GrabWrapDirectGeometry.Strand attached = GrabWrapDirectGeometry.attach(
                physicalTip, coil, radii, Vec3.ZERO,
                new Vec3(0.0D, 1.0D, 0.0D), -0.80D, 0.80D, 0.14D);

        assertEquals(coil.size(), attached.points().size(),
                "direct attachment must replace points, not add a rope section");
        assertEquals(physicalTip, attached.points().getFirst());
        assertEquals(0.14D, attached.radii().getFirst(), EPSILON);
        assertEquals(coil.get(2), attached.points().get(2),
                "the physical tip must not reshape the stable remainder of the coil");
        assertEquals(coil.get(3), attached.points().get(3));
    }

    @Test
    void directWrapEntryClampsToTheNearestBodyAxialPosition() {
        Vec3 physicalTip = new Vec3(2.0D, 4.0D, -1.0D);
        List<Vec3> coil = List.of(
                new Vec3(0.30D, -0.80D, 0.0D),
                new Vec3(0.25D, -0.65D, 0.10D),
                new Vec3(0.0D, 0.80D, 0.30D));

        GrabWrapDirectGeometry.Strand attached = GrabWrapDirectGeometry.attach(
                physicalTip, coil, List.of(0.08D, 0.10D, 0.08D),
                Vec3.ZERO, new Vec3(0.0D, 1.0D, 0.0D),
                -0.80D, 0.80D, 0.14D);

        assertEquals(0.80D, attached.points().get(1).y, EPSILON);
        assertEquals(coil.get(1).x, attached.points().get(1).x, EPSILON);
        assertEquals(coil.get(1).z, attached.points().get(1).z, EPSILON);
    }

    @Test
    void onlyWholeBodyTargetIsUsedByWrapGeometry() {
        assertEquals(TentacleGrabTarget.WHOLE_BODY,
                TentacleGrabTarget.byName("whole_body"));
    }

    @Test
    void bodyAxisComposesAcquisitionYawWithRelativeRagdollRotation() {
        Quaternionf reference = new Quaternionf().rotationY((float) Math.toRadians(90.0D));
        Quaternionf relative = new Quaternionf().rotationX((float) Math.toRadians(90.0D));
        ClientTentacleManager.RagdollPose pose = new ClientTentacleManager.RagdollPose(
                relative, new Quaternionf(), reference,
                Vec3.ZERO, TentacleGrabTarget.WHOLE_BODY, Vec3.ZERO,
                new Vec3(0.0D, -1.0D, 0.0D), new Vec3(0.0D, -1.0D, 0.0D),
                new Vec3(0.0D, -1.0D, 0.0D), new Vec3(0.0D, -1.0D, 0.0D));

        Vec3 axis = TentaclePoseTransforms.bodyAxis(pose);
        Vector3f expected = new Quaternionf(reference).mul(relative)
                .transform(new Vector3f(0.0F, 1.0F, 0.0F));
        Vector3f relativeOnly = new Quaternionf(relative)
                .transform(new Vector3f(0.0F, 1.0F, 0.0F));

        assertEquals(expected.x, axis.x, EPSILON);
        assertEquals(expected.y, axis.y, EPSILON);
        assertEquals(expected.z, axis.z, EPSILON);
        assertTrue(axis.distanceTo(new Vec3(
                relativeOnly.x, relativeOnly.y, relativeOnly.z)) > 0.5D,
                "test must distinguish world composition from the old relative-only axis");
    }

    @Test
    void cameraEyeOffsetRotatesWithTheRagdollHead() {
        Quaternionf reference = new Quaternionf().rotationY(0.61F);
        Quaternionf relativeHead = new Quaternionf().rotationZ((float) Math.PI);
        ClientTentacleManager.RagdollPose pose = new ClientTentacleManager.RagdollPose(
                new Quaternionf(), relativeHead, reference,
                Vec3.ZERO, TentacleGrabTarget.WHOLE_BODY, Vec3.ZERO,
                new Vec3(0.0D, -1.0D, 0.0D), new Vec3(0.0D, -1.0D, 0.0D),
                new Vec3(0.0D, -1.0D, 0.0D), new Vec3(0.0D, -1.0D, 0.0D));

        Vec3 offset = TentaclePoseTransforms.headLocalOffset(
                pose, 0.0D, -0.054D, 0.0D);
        Vector3f expected = new Quaternionf(reference).mul(relativeHead)
                .transform(new Vector3f(0.0F, -0.054F, 0.0F));

        assertEquals(expected.x, offset.x, EPSILON);
        assertEquals(expected.y, offset.y, EPSILON);
        assertEquals(expected.z, offset.z, EPSILON);
        assertTrue(offset.y > 0.0D,
                "the eye offset must invert together with an upside-down head");
    }

    @Test
    void fullBodyStrandsUseOnlyTheStableBodyFrame() {
        Vec3 axis = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 fallback = new Vec3(0.0D, 0.0D, 1.0D);
        Vec3 first = TentacleWrapStability.strandNormal(fallback, axis, 0.0D);
        Vec3 opposite = TentacleWrapStability.strandNormal(fallback, axis, Math.PI);

        assertEquals(fallback, first);
        assertEquals(-fallback.x, opposite.x, EPSILON);
        assertEquals(-fallback.y, opposite.y, EPSILON);
        assertEquals(-fallback.z, opposite.z, EPSILON);
    }

    @Test
    void fullBodySpanAlwaysRunsFromAnatomicalBottomToTop() {
        TentacleWrapStability.AxialSpan span =
                TentacleWrapStability.fullBodySpan(0.80D);

        assertEquals(-0.80D, span.start(), EPSILON);
        assertEquals(0.80D, span.end(), EPSILON);
        Vec3 center = Vec3.ZERO;
        Vec3 uprightAxis = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 invertedAxis = new Vec3(0.0D, -1.0D, 0.0D);
        assertTrue(center.add(uprightAxis.scale(span.start())).y
                < center.add(uprightAxis.scale(span.end())).y);
        assertTrue(center.add(invertedAxis.scale(span.start())).y
                > center.add(invertedAxis.scale(span.end())).y,
                "an upside-down body must wrap from world top toward world bottom");
    }

    private static List<Vec3> straightChain(int points) {
        return java.util.stream.IntStream.range(0, points)
                .mapToObj(index -> new Vec3(index, 0.0D, 0.0D))
                .toList();
    }

}

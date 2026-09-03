package com.fish.mirebound.client.tentacle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class ClientTentaclePoseInterpolationTest {
    private static final double EPSILON = 1.0E-5D;

    @Test
    void legDirectionInterpolatesInWorldSpaceDuringBodyRotation() {
        float amount = 0.37F;
        Quaternionf fromWorldBody = new Quaternionf().rotationYXZ(-0.8F, 0.4F, 0.2F);
        Quaternionf targetWorldBody = new Quaternionf().rotationYXZ(1.1F, -0.9F, 0.7F);
        Quaternionf interpolatedWorldBody = new Quaternionf(fromWorldBody)
                .slerp(targetWorldBody, amount).normalize();
        Quaternionf inverseInterpolatedBody =
                new Quaternionf(interpolatedWorldBody).conjugate();
        Vec3 fromLocal = new Vec3(0.20D, -0.90D, 0.38D).normalize();
        Vec3 targetLocal = new Vec3(-0.65D, -0.30D, 0.69D).normalize();

        Vec3 actualLocal = ClientTentacleManager.interpolateLocalDirection(
                fromWorldBody, targetWorldBody, inverseInterpolatedBody,
                fromLocal, targetLocal, amount);
        Vector3f actualWorld = interpolatedWorldBody.transform(vector(actualLocal));
        Vector3f expectedWorld = fromWorldBody.transform(vector(fromLocal))
                .lerp(targetWorldBody.transform(vector(targetLocal)), amount)
                .normalize();

        assertEquals(expectedWorld.x, actualWorld.x, EPSILON);
        assertEquals(expectedWorld.y, actualWorld.y, EPSILON);
        assertEquals(expectedWorld.z, actualWorld.z, EPSILON);

        Vec3 separatelyInterpolatedLocal = fromLocal.lerp(targetLocal, amount).normalize();
        Vector3f oldWorld = interpolatedWorldBody.transform(
                vector(separatelyInterpolatedLocal));
        assertTrue(oldWorld.distance(expectedWorld) > 0.02F,
                "the former independent interpolation must expose the reproduced phase error");
    }

    @Test
    void legDirectionInterpolationKeepsBothNetworkEndpointsExact() {
        Quaternionf fromWorldBody = new Quaternionf().rotationXYZ(0.4F, -0.7F, 0.2F);
        Quaternionf targetWorldBody = new Quaternionf().rotationXYZ(-0.8F, 0.3F, 1.0F);
        Vec3 fromLocal = new Vec3(0.12D, -0.97D, -0.21D).normalize();
        Vec3 targetLocal = new Vec3(-0.72D, -0.40D, 0.57D).normalize();

        Vec3 atStart = ClientTentacleManager.interpolateLocalDirection(
                fromWorldBody, targetWorldBody,
                new Quaternionf(fromWorldBody).conjugate(),
                fromLocal, targetLocal, 0.0F);
        Vec3 atEnd = ClientTentacleManager.interpolateLocalDirection(
                fromWorldBody, targetWorldBody,
                new Quaternionf(targetWorldBody).conjugate(),
                fromLocal, targetLocal, 1.0F);

        assertEquals(fromLocal.x, atStart.x, EPSILON);
        assertEquals(fromLocal.y, atStart.y, EPSILON);
        assertEquals(fromLocal.z, atStart.z, EPSILON);
        assertEquals(targetLocal.x, atEnd.x, EPSILON);
        assertEquals(targetLocal.y, atEnd.y, EPSILON);
        assertEquals(targetLocal.z, atEnd.z, EPSILON);
    }

    private static Vector3f vector(Vec3 value) {
        return new Vector3f((float) value.x, (float) value.y, (float) value.z);
    }
}

package com.fish.mirebound.client.tentacle;

import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Converts body-local ragdoll directions to twist-locked Minecraft
 * ModelPart rotations.
 *
 * <p>The synchronized ragdoll uses +Y as up, while humanoid limb cubes extend
 * from their hip/shoulder pivot along model +Y. LivingEntityRenderer then
 * reflects model Y and Z before applying the entity yaw. Solving the two
 * meaningful limb angles directly avoids both the lateral sign inversion and
 * the XYZ/ZYX Euler branch mismatch from the former quaternion conversion.</p>
 */
final class TentacleModelPartRotations {
    private static final double EPSILON = 1.0E-10D;

    private TentacleModelPartRotations() {
    }

    static Vector3f armEuler(Vec3 direction) {
        return twistLockedLimbEuler(direction);
    }

    static Vector3f legEuler(Vec3 direction) {
        return twistLockedLimbEuler(direction);
    }

    private static Vector3f twistLockedLimbEuler(Vec3 direction) {
        double length = direction.length();
        if (!Double.isFinite(length) || length <= EPSILON) {
            return new Vector3f();
        }
        double inverseLength = 1.0D / length;
        double ragdollX = direction.x * inverseLength;
        double ragdollY = direction.y * inverseLength;
        double ragdollZ = direction.z * inverseLength;

        // ModelPart rebuilds rotationZYX(zRot, yRot, xRot). Keeping yRot at
        // zero locks the otherwise undefined rotation around the limb's long
        // axis while retaining the two visible swing degrees of freedom.
        float xRot = (float) Math.asin(clamp(-ragdollZ, -1.0D, 1.0D));
        float zRot = (float) Math.atan2(-ragdollX, -ragdollY);
        return new Vector3f(xRot, 0.0F, zRot);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}

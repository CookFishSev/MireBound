package com.fish.mirebound.client.tentacle;

import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/** Converts equivalent quaternion rotations into the Euler branch nearest the previous frame. */
final class TentacleCameraRotation {
    private static final float PI = (float) Math.PI;
    private static final float TWO_PI = PI * 2.0F;

    private TentacleCameraRotation() {
    }

    static Vector3f closestEulerXyz(Quaternionfc rotation, Vector3fc previous,
            Vector3f destination) {
        Vector3f raw = rotation.getEulerAnglesXYZ(new Vector3f());
        if (previous == null) {
            return destination.set(raw);
        }

        Vector3f principal = unwrap(raw.x, raw.y, raw.z, previous, new Vector3f());
        Vector3f positiveAlternate = unwrap(
                raw.x + PI, PI - raw.y, raw.z + PI, previous, new Vector3f());
        Vector3f negativeAlternate = unwrap(
                raw.x + PI, -PI - raw.y, raw.z + PI, previous, new Vector3f());

        Vector3f closest = principal;
        float closestDistance = distanceSquared(principal, previous);
        float positiveDistance = distanceSquared(positiveAlternate, previous);
        if (positiveDistance < closestDistance) {
            closest = positiveAlternate;
            closestDistance = positiveDistance;
        }
        if (distanceSquared(negativeAlternate, previous) < closestDistance) {
            closest = negativeAlternate;
        }
        return destination.set(closest);
    }

    /**
     * Same branch selection for Minecraft camera quaternions, whose rotation order
     * is Y-X-Z rather than the model-part X-Y-Z order.
     */
    static Vector3f closestEulerYxz(Quaternionfc rotation, Vector3fc previous,
            Vector3f destination) {
        Vector3f raw = rotation.getEulerAnglesYXZ(new Vector3f());
        if (previous == null) {
            return destination.set(raw);
        }

        Vector3f principal = unwrap(raw.x, raw.y, raw.z, previous, new Vector3f());
        Vector3f positiveAlternate = unwrap(
                PI - raw.x, raw.y + PI, raw.z + PI, previous, new Vector3f());
        Vector3f negativeAlternate = unwrap(
                -PI - raw.x, raw.y + PI, raw.z + PI, previous, new Vector3f());

        Vector3f closest = principal;
        float closestDistance = distanceSquared(principal, previous);
        float positiveDistance = distanceSquared(positiveAlternate, previous);
        if (positiveDistance < closestDistance) {
            closest = positiveAlternate;
            closestDistance = positiveDistance;
        }
        if (distanceSquared(negativeAlternate, previous) < closestDistance) {
            closest = negativeAlternate;
        }
        return destination.set(closest);
    }

    static Quaternionf fromCameraAngles(float yaw, float pitch, float roll,
            Quaternionf destination) {
        float radians = PI / 180.0F;
        return destination.rotationYXZ(
                PI - yaw * radians,
                -pitch * radians,
                -roll * radians);
    }

    static Quaternionf composeRagdollCamera(Quaternionfc referenceBody,
            Quaternionfc referenceCamera, Quaternionfc relativeHead,
            float strength, Quaternionf destination) {
        Quaternionf localCamera = new Quaternionf(referenceBody).conjugate()
                .mul(referenceCamera).normalize();
        Quaternionf drivenHead = new Quaternionf().slerp(
                relativeHead, Math.max(0.0F, Math.min(1.0F, strength))).normalize();
        return destination.set(referenceBody).mul(drivenHead).mul(localCamera).normalize();
    }

    /**
     * Applies the player's live view delta in the captured camera's local
     * coordinate system. Editing world Y-X-Z Euler components directly makes
     * horizontal mouse motion drift diagonally while the ragdoll is rolled.
     */
    static Quaternionf composeViewInput(Quaternionfc drivenCamera,
            Quaternionfc referenceInput, Quaternionfc currentInput,
            Quaternionf destination) {
        Quaternionf localInputDelta = new Quaternionf(referenceInput)
                .conjugate().mul(currentInput).normalize();
        return destination.set(drivenCamera).mul(localInputDelta).normalize();
    }

    static Vector3f cameraForward(Quaternionfc camera, Vector3f destination) {
        destination.set(0.0F, 0.0F, -1.0F);
        camera.transform(destination);
        return destination;
    }

    static float minecraftYaw(Quaternionfc bodyOrientation) {
        Vector3f forward = bodyOrientation.transform(new Vector3f(0.0F, 0.0F, 1.0F));
        return (float) Math.toDegrees(Math.atan2(forward.x, forward.z));
    }

    private static Vector3f unwrap(float x, float y, float z,
            Vector3fc reference, Vector3f destination) {
        return destination.set(
                unwrap(x, reference.x()),
                unwrap(y, reference.y()),
                unwrap(z, reference.z()));
    }

    private static float unwrap(float angle, float reference) {
        float turns = Math.round((reference - angle) / TWO_PI);
        return angle + turns * TWO_PI;
    }

    private static float distanceSquared(Vector3fc first, Vector3fc second) {
        float x = first.x() - second.x();
        float y = first.y() - second.y();
        float z = first.z() - second.z();
        return x * x + y * y + z * z;
    }
}

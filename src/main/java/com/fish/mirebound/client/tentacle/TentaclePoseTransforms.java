package com.fish.mirebound.client.tentacle;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Pure coordinate transforms shared by grab rendering and its unit tests. */
final class TentaclePoseTransforms {
    private TentaclePoseTransforms() {
    }

    static Quaternionf worldBodyOrientation(ClientTentacleManager.RagdollPose pose) {
        // bodyOrientation is relative to the player's orientation at acquisition.
        // Procedural wrap geometry is emitted directly in world coordinates, so it
        // needs reference * relative. Player model parts already inherit the vanilla
        // entity-yaw transform and therefore intentionally use the relative value.
        return new Quaternionf(pose.referenceOrientation())
                .mul(pose.bodyOrientation()).normalize();
    }

    static Quaternionf worldHeadOrientation(ClientTentacleManager.RagdollPose pose) {
        return new Quaternionf(pose.referenceOrientation())
                .mul(pose.headOrientation()).normalize();
    }

    static Vec3 bodyAxis(ClientTentacleManager.RagdollPose pose) {
        Vector3f transformed = worldBodyOrientation(pose)
                .transform(new Vector3f(0.0F, 1.0F, 0.0F));
        return new Vec3(transformed.x, transformed.y, transformed.z);
    }

    static Vec3 headLocalOffset(ClientTentacleManager.RagdollPose pose,
            double localX, double localY, double localZ) {
        Vector3f transformed = worldHeadOrientation(pose).transform(
                new Vector3f((float) localX, (float) localY, (float) localZ));
        return new Vec3(transformed.x, transformed.y, transformed.z);
    }
}

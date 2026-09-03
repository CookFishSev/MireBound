package com.fish.mirebound.tentacle;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;

/** Compact articulated pose sent only while a player is held by a tentacle. */
public record TentacleRagdollPose(
        Quaterniond bodyOrientation,
        Quaterniond headOrientation,
        Quaterniond referenceOrientation,
        Vec3 headOffset,
        TentacleGrabTarget grabTarget,
        Vec3 gripOffset,
        Vec3 leftArmDirection,
        Vec3 rightArmDirection,
        Vec3 leftLegDirection,
        Vec3 rightLegDirection) {
    public static final TentacleRagdollPose IDENTITY = new TentacleRagdollPose(
            new Quaterniond(), new Quaterniond(), new Quaterniond(),
            new Vec3(0.0D, 0.72D, 0.0D),
            TentacleGrabTarget.NONE, Vec3.ZERO,
            new Vec3(0.0D, -1.0D, 0.0D), new Vec3(0.0D, -1.0D, 0.0D),
            new Vec3(0.0D, -1.0D, 0.0D), new Vec3(0.0D, -1.0D, 0.0D));

    public TentacleRagdollPose {
        bodyOrientation = new Quaterniond(bodyOrientation).normalize();
        headOrientation = new Quaterniond(headOrientation).normalize();
        referenceOrientation = new Quaterniond(referenceOrientation).normalize();
        grabTarget = grabTarget == null ? TentacleGrabTarget.NONE : grabTarget;
    }
}

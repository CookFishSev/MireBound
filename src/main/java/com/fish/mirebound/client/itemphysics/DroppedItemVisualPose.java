package com.fish.mirebound.client.itemphysics;

import java.util.UUID;
import net.minecraft.util.Mth;

/** Stable per-entity orientation and easing math for embedded dropped items. */
public record DroppedItemVisualPose(
        float yawRadians,
        float tiltXDegrees,
        float tiltZDegrees) {
    static final float MAXIMUM_RANDOM_YAW_DEGREES = 10.0F;

    public static DroppedItemVisualPose create(UUID uuid, float maximumTiltDegrees) {
        return create(uuid, maximumTiltDegrees, 0.0F);
    }

    public static DroppedItemVisualPose create(
            UUID uuid, float maximumTiltDegrees, float referenceYawRadians) {
        long first = mix(uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits());
        long second = mix(first + 0x9E3779B97F4A7C15L);
        float yawOffset = (float) ((unit(first) * 2.0D - 1.0D)
                * Math.toRadians(MAXIMUM_RANDOM_YAW_DEGREES));
        double axis = unit(second) * Math.PI * 2.0D;
        float magnitude = maximumTiltDegrees
                * (float) (0.45D + unit(mix(second)) * 0.55D);
        return new DroppedItemVisualPose(
                referenceYawRadians + yawOffset,
                (float) Math.cos(axis) * magnitude,
                (float) Math.sin(axis) * magnitude);
    }

    static float easedAmount(float elapsedTicks, int settleTicks) {
        if (settleTicks <= 0) {
            return 1.0F;
        }
        float amount = Mth.clamp(elapsedTicks / settleTicks, 0.0F, 1.0F);
        return amount * amount * (3.0F - 2.0F * amount);
    }

    float settleYaw(float originalRadians, float amount) {
        float deltaDegrees = Mth.wrapDegrees(
                (yawRadians - originalRadians) * Mth.RAD_TO_DEG);
        return originalRadians + deltaDegrees * Mth.DEG_TO_RAD * amount;
    }

    float bottomAlignment(float collisionRadius, float amount) {
        double tiltX = Math.toRadians(tiltXDegrees * amount);
        double tiltZ = Math.toRadians(tiltZDegrees * amount);
        return (float) (collisionRadius
                * (Math.abs(Math.sin(tiltX)) + Math.abs(Math.sin(tiltZ))));
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    private static double unit(long value) {
        return (value >>> 11) * 0x1.0p-53D;
    }
}

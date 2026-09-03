package com.fish.mirebound.rope;

/** Immutable settings for the Memento In Abyss-derived XPBD rope solver. */
public record RopeProperties(
        int segmentCount,
        double segmentLength,
        double collisionRadius,
        double gravityPerTick,
        double velocityDamping,
        double contactVelocityDamping,
        double dragVelocityDamping,
        int collisionRefreshTicks,
        int maximumCollisionBlockSamples,
        double maximumThrowSpeed) {
    private static final double SQRT_TWO = 1.4142135623730951D;

    public static final int MAX_SEGMENTS = 64;
    public static final double GRAB_DISTANCE = 1.5D;
    public static final RopeProperties DEFAULT = new RopeProperties(
        20, 1.0D, 2.25D / 16.0D,
            9.81D / 400.0D, 0.985D, 0.84D, 0.82D,
            2, 4096, 1.20D);

    public int nodeCount() {
        return segmentCount + 1;
    }

    public double totalLength() {
        return segmentCount * segmentLength;
    }

    /**
     * Padding required to include the complete swept cube of one rope segment
     * when the terrain collision snapshot is captured around its centerline.
     */
    public double collisionCapturePadding() {
        return segmentLength * 0.5D + collisionRadius * SQRT_TWO + 0.05D;
    }

    public RopeProperties withSegmentCount(int count) {
        return new RopeProperties(count, segmentLength, collisionRadius, gravityPerTick,
                velocityDamping, contactVelocityDamping, dragVelocityDamping,
                collisionRefreshTicks,
                maximumCollisionBlockSamples, maximumThrowSpeed);
    }
}

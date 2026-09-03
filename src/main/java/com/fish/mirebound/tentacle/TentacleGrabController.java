package com.fish.mirebound.tentacle;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

final class TentacleGrabController {
    private static final double EPSILON = 1.0E-10D;

    private TentacleGrabController() {
    }

    static Vec3 behaviorGoal(TentacleGrabMode mode, Vec3 root, Vec3 targetCenter,
            TentaclePhysicsProfile morphology, TentacleGrabProfile profile,
            int grabTicks, long seed, double sizeScale) {
        double seedPhase = (seed & 0xFFFFL) * (Math.PI * 2.0D / 65536.0D);
        double linearScale = Math.pow(Mth.clamp(sizeScale, 0.25D, 8.0D), 0.72D);
        double frequencyScale = 1.0D / Math.sqrt(Math.max(0.35D, linearScale));
        return switch (mode) {
            case HOLD -> holdGoal(targetCenter, profile, grabTicks, seed, linearScale);
            case WRAP -> {
                double phase = grabTicks * profile.wrapSpeed() * frequencyScale + seedPhase;
                double radius = profile.wrapRadius() * linearScale;
                yield targetCenter.add(
                        Math.cos(phase) * radius,
                        Math.sin(phase * 0.73D) * radius * 0.45D,
                        Math.sin(phase) * radius);
            }
            case THRASH -> {
                double phase = grabTicks * profile.thrashSpeed() * frequencyScale + seedPhase;
                double radius = Math.min(profile.thrashRadius() * linearScale,
                        morphology.maximumLength() * 0.72D);
                double jitter = profile.thrashJitter() * Math.sqrt(linearScale);
                double x = Math.cos(phase) * radius
                        + Math.sin(phase * 2.31D + seedPhase * 0.7D) * jitter;
                double z = Math.sin(phase * 1.17D) * radius
                        + Math.cos(phase * 1.83D - seedPhase) * jitter;
                double y = Math.max(morphology.rootRadius() * 2.0D,
                        profile.thrashVerticalRange() * linearScale * 0.70D)
                        + Math.sin(phase * 1.43D + seedPhase * 0.4D)
                                * profile.thrashVerticalRange() * linearScale;
                yield root.add(x, y, z);
            }
        };
    }

    private static Vec3 holdGoal(Vec3 anchor, TentacleGrabProfile profile,
            int modeTicks, long seed, double linearScale) {
        int liftStart = profile.holdWrapTicks();
        int liftDuration = Math.max(1, profile.holdLiftTicks());
        double liftProgress = Mth.clamp(
                (modeTicks - liftStart) / (double) liftDuration, 0.0D, 1.0D);
        liftProgress = smootherStep(liftProgress);
        double scale = Math.sqrt(linearScale);
        Vec3 elevated = anchor.add(0.0D,
                profile.holdLiftHeight() * scale * liftProgress, 0.0D);
        if (liftProgress < 1.0D || profile.holdPositionRadius() <= 0.0D) {
            return elevated;
        }

        int elapsed = Math.max(0, modeTicks - liftStart - liftDuration);
        int positionTicks = Math.max(1, profile.holdPositionTicks());
        int moveTicks = Math.min(positionTicks, Math.max(1, profile.holdMoveTicks()));
        long positionIndex = Math.floorDiv((long) elapsed, positionTicks);
        int localTicks = Math.floorMod(elapsed, positionTicks);
        int moveStart = positionTicks - moveTicks;
        double moveProgress = smootherStep(Mth.clamp(
                (localTicks - moveStart) / (double) moveTicks, 0.0D, 1.0D));
        Vec3 current = holdOffset(profile, seed, positionIndex, scale);
        Vec3 next = holdOffset(profile, seed, positionIndex + 1L, scale);
        return elevated.add(current.lerp(next, moveProgress));
    }

    private static Vec3 holdOffset(TentacleGrabProfile profile,
            long seed, long positionIndex, double scale) {
        if (positionIndex <= 0L) {
            return Vec3.ZERO;
        }
        double angle = unit(seed, positionIndex, 0x484F4C445F414E47L) * Math.PI * 2.0D;
        double radius = profile.holdPositionRadius() * scale
                * Mth.lerp(unit(seed, positionIndex, 0x484F4C445F524144L), 0.45D, 1.0D);
        double height = profile.holdHeightVariation() * scale
                * (unit(seed, positionIndex, 0x484F4C445F484754L) * 2.0D - 1.0D);
        return new Vec3(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
    }

    private static double unit(long seed, long index, long salt) {
        long mixed = mix(seed ^ salt ^ index * 0x9E3779B97F4A7C15L);
        return (mixed >>> 11) * 0x1.0p-53;
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private static double smootherStep(double value) {
        double clamped = Mth.clamp(value, 0.0D, 1.0D);
        return clamped * clamped * clamped
                * (clamped * (clamped * 6.0D - 15.0D) + 10.0D);
    }

    static Vec3 constrainedVelocity(Vec3 currentVelocity, Vec3 currentGripPoint,
            Vec3 desiredGripPoint, Vec3 gripVelocity, TentacleGrabProfile profile,
            double sizeScale, double controlScale) {
        double forceScale = Math.pow(Mth.clamp(sizeScale, 0.25D, 8.0D), 0.55D);
        double response = Mth.clamp(controlScale, 0.10D, 1.0D);
        Vec3 positionCorrection = desiredGripPoint.subtract(currentGripPoint)
                .scale(profile.spring() * forceScale * response);
        Vec3 velocityCorrection = gripVelocity.subtract(currentVelocity)
                .scale(profile.damping());
        Vec3 acceleration = positionCorrection.add(velocityCorrection);
        acceleration = clampLength(acceleration,
                profile.maximumAcceleration() * forceScale * response);
        return clampLength(currentVelocity.add(acceleration),
                profile.maximumSpeed() * Math.sqrt(forceScale) * response);
    }

    private static Vec3 clampLength(Vec3 value, double maximum) {
        double length = value.length();
        return length > maximum && length > EPSILON ? value.scale(maximum / length) : value;
    }
}

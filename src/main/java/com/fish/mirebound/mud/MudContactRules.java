package com.fish.mirebound.mud;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Pure contact thresholds and resistance curves shared by world and Sable sampling. */
final class MudContactRules {
    static final double REQUIRED_PENETRATION = 0.012D;
    private static final double MIN_HORIZONTAL_OVERLAP = 0.040D;
    private static final double MIN_VOLUME_IMMERSION = 0.060D;
    private static final double SOLE_ENTRY_INSET = 0.020D;

    private MudContactRules() {
    }

    static boolean qualifiesWorldContact(double horizontalOverlap) {
        return horizontalOverlap >= MIN_HORIZONTAL_OVERLAP;
    }

    static boolean qualifiesWorldVerticalContact(double feetY, double surfaceY) {
        return feetY <= surfaceY - REQUIRED_PENETRATION;
    }

    static boolean qualifiesZeroDepthSurfaceContact(double feetDepth, double surfaceGrace) {
        return feetDepth >= -surfaceGrace && feetDepth <= surfaceGrace;
    }

    static boolean qualifiesSableFeetContact(double feetDepth) {
        return feetDepth >= REQUIRED_PENETRATION;
    }

    static boolean qualifiesSculkSurfaceContact(double feetDepth, boolean crouching) {
        return crouching && feetDepth >= -0.004D && feetDepth <= 0.040D;
    }

    static boolean insideSableLayerBounds(Vec3 point, BlockPos pos,
            double surfaceHeight, double tolerance) {
        return point.x >= pos.getX() - tolerance
                && point.x <= pos.getX() + 1.0D + tolerance
                && point.y >= pos.getY() - tolerance
                && point.y <= pos.getY() + surfaceHeight + tolerance
                && point.z >= pos.getZ() - tolerance
                && point.z <= pos.getZ() + 1.0D + tolerance;
    }

    static boolean insideSableLayerBounds(Vec3 point, BlockPos pos,
            BlockState state, SinkingMedium medium, double tolerance) {
        if (state.getBlock() instanceof MudBlock) {
            return MudBlock.containsLocalPoint(
                    state,
                    medium,
                    point.subtract(pos.getX(), pos.getY(), pos.getZ()),
                    tolerance);
        }
        return insideSableLayerBounds(
                point,
                pos,
                MudMediumRuntime.surfaceHeight(null, state, medium),
                tolerance);
    }

    static double effectiveVolumeImmersion(double measured) {
        if (measured <= 0.0D) {
            return 0.0D;
        }
        return Mth.clamp(Math.max(measured, MIN_VOLUME_IMMERSION), 0.0D, 1.0D);
    }

    static VolumeResistance volumeResistance(
            SinkingPhysicsProfile profile, double immersion) {
        double clamped = Mth.clamp(immersion, 0.0D, 1.0D);
        double walkScale = SinkingPhysicsSolver.walkScale(profile, clamped);
        double deepProgress = smoothStep(Mth.clamp(
                clamped / Math.max(0.10D, profile.walkWaistDepth),
                0.0D,
                1.0D));
        double verticalScale = Mth.lerp(
                deepProgress,
                0.96D,
                Math.max(0.18D, profile.verticalDeep));
        return new VolumeResistance(walkScale, verticalScale);
    }

    static boolean requiresSoleEntry(MudBodyPart part, MudSurface surface) {
        return requiresSoleEntry(part, surface, 0)
                && surface == MudSurface.BOTTOM;
    }

    static boolean requiresSoleEntry(MudBodyPart part, MudSurface surface, int row) {
        if (part != MudBodyPart.LEFT_LEG && part != MudBodyPart.RIGHT_LEG) {
            return false;
        }
        return surface == MudSurface.BOTTOM
                || MudSurfaceLayout.face(part, surface).vertical() && row == 0;
    }

    static Vec3 soleEntryProbePoint(Vec3 surfacePoint, Vec3 outwardNormal) {
        return surfacePoint.subtract(outwardNormal.scale(SOLE_ENTRY_INSET));
    }

    static Vec3 soleEntryProbePoint(double authoritativeFeetY,
            Vec3 surfacePoint, Vec3 outwardNormal) {
        Vec3 probe = soleEntryProbePoint(surfacePoint, outwardNormal);
        double minimumY = authoritativeFeetY + SOLE_ENTRY_INSET;
        if (outwardNormal.y < -0.50D && probe.y < minimumY) {
            return new Vec3(probe.x, minimumY, probe.z);
        }
        return probe;
    }

    private static double smoothStep(double value) {
        double clamped = Mth.clamp(value, 0.0D, 1.0D);
        return clamped * clamped * (3.0D - 2.0D * clamped);
    }

    record VolumeResistance(double walkScale, double verticalScale) {
    }
}

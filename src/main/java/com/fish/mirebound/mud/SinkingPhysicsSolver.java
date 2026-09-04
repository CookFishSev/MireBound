package com.fish.mirebound.mud;

import net.minecraft.util.Mth;

/**
 * Deterministic low-cost viscoplastic solver for ordinary sinking media.
 *
 * <p>The medium resists motion until the combined body load and disturbance
 * exceed its yield threshold. Once yielded, settling approaches a
 * depth-dependent terminal velocity instead of being reassigned every tick.
 * The depth cap is compression-only: it can stop downward motion but can never
 * generate passive buoyancy.</p>
 */
final class SinkingPhysicsSolver {
    private static final double LAYER_TRANSITION_DEPTH = 0.025D;

    private SinkingPhysicsSolver() {
    }

    static Result solve(SinkingPhysicsProfile profile, Input input) {
        double columnDepth = Math.max(0.0D, input.columnDepth());
        double sinkLimit = sinkLimit(
                profile,
                columnDepth,
                input.layerTopDepth(),
                input.layerDepth(),
                input.hasDeeperLayer(),
                input.depthLimitScale());
        double depth = Math.max(0.0D, input.depth());
        double remaining = sinkLimit - depth;
        double naturalLimit = profile.depthControlMode == MudSinkingDepthControl.Mode.SIMPLE
                ? layerLimit(
                        columnDepth,
                        input.layerTopDepth(),
                        input.layerDepth(),
                        input.hasDeeperLayer(),
                        profile.simpleNaturalDepth,
                        input.depthLimitScale())
                : sinkLimit;
        double naturalRemaining = naturalLimit - depth;
        double limitProgress = Mth.clamp(
                depth / Math.max(sinkLimit, 0.08D), 0.0D, 1.0D);
        double progress = profile.depthControlMode == MudSinkingDepthControl.Mode.SIMPLE
                ? Mth.clamp(input.immersionFraction(), 0.0D, 1.0D)
                : limitProgress;
        double depthEase = smooth(progress);

        double horizontalSpeed = Math.sqrt(input.motionX() * input.motionX() + input.motionZ() * input.motionZ());
        MudBehaviorComponents behavior = profile.behavior;
        // Charging is an escape attempt, so it may still make noise but must
        // not feed the same full downward disturbance as ordinary movement.
        double struggleSinkFactor = input.holdingStruggle()
                ? 1.0D - Mth.clamp(profile.struggleSinkSuppression, 0.0D, 1.0D)
                : 1.0D;
        double activity = Mth.clamp(
                input.agitation() + horizontalSpeed * 3.0D
                        + (input.holdingStruggle() ? 0.18D * struggleSinkFactor : 0.0D),
                0.0D,
                1.0D);
        double fullyImmersedWalkScale = walkScale(profile, input.immersionFraction())
                * behavior.walkMultiplier(input.immersionFraction());
        double walkScale = lerp(
                1.0D,
                fullyImmersedWalkScale,
                input.horizontalCoverage());
        walkScale = MudEnchantmentEffects.restoreWalkScale(
                walkScale,
                input.walkRestoration());
        double viscosity = lerp(profile.viscositySurface, profile.viscosityDeep, depthEase)
                * behavior.viscosityMultiplier(progress, activity);
        double verticalScale = lerp(profile.verticalSurface, profile.verticalDeep, depthEase)
                * behavior.verticalMultiplier(progress);
        walkScale = Mth.clamp(walkScale, 0.0D, 1.20D);
        verticalScale = Mth.clamp(verticalScale, 0.0D, 1.20D);

        double naturalDrive = profile.baseSinkSpeed * lerp(1.0D, profile.deepSinkRatio, depthEase);
        double movementDrive = Math.min(
                profile.maxSinkSpeed * 0.38D,
                horizontalSpeed * profile.movementSinkScale * (0.30D + input.agitation() * 0.70D));
        double disturbanceDrive = input.agitation() * profile.agitationSinkScale
                + (input.crouching() ? profile.crouchSink : 0.0D)
                + Math.min(profile.maxSinkSpeed * 0.12D,
                        Math.max(0.0D, input.lookDelta()) * profile.lookSinkScale)
                + (input.holdingStruggle() ? profile.holdSink * struggleSinkFactor : 0.0D)
                + Math.max(0.0D, input.slurpImpulse()) * struggleSinkFactor
                + behavior.additionalSinkDrive(
                        horizontalSpeed,
                        input.agitation(),
                        input.holdingStruggle(),
                        progress,
                        profile.maxSinkSpeed,
                        struggleSinkFactor);

        double yieldDepthScale = lerp(0.72D, 1.0D + profile.yieldDepthGain, depthEase);
        double disturbanceSoftening = 1.0D
                - Mth.clamp(input.agitation() * profile.disturbanceYieldReduction, 0.0D, 0.95D);
        double yieldResistance = profile.yieldThreshold
                * yieldDepthScale
                * disturbanceSoftening
                * behavior.yieldMultiplier(progress, activity);
        double yieldedDrive;
        if (profile.depthControlMode == MudSinkingDepthControl.Mode.SIMPLE) {
            double naturalCapProgress = Mth.clamp(
                    naturalRemaining / Math.max(profile.brakeDistance, 0.02D),
                    0.0D,
                    1.0D);
            naturalDrive *= smooth(Math.sqrt(naturalCapProgress));
            double yieldedDisturbance = softPositive(
                    movementDrive + disturbanceDrive - yieldResistance,
                    profile.yieldSoftness);
            yieldedDrive = naturalDrive + yieldedDisturbance;
        } else {
            yieldedDrive = softPositive(
                    naturalDrive + movementDrive + disturbanceDrive - yieldResistance,
                    profile.yieldSoftness);
        }
        boolean crossingLayer = allowsLayerTransition(
                profile, input.hasDeeperLayer(), input.depthLimitScale());
        double capProgress = crossingLayer ? 1.0D : Mth.clamp(
                remaining / Math.max(profile.brakeDistance, 0.02D),
                0.0D,
                1.0D);
        double capFactor = crossingLayer
                ? 1.0D
                : profile.depthControlMode == MudSinkingDepthControl.Mode.SIMPLE
                        ? smooth(Math.sqrt(capProgress))
                        : smooth(capProgress);
        double targetSinkSpeed = Mth.clamp(
                yieldedDrive / Math.max(0.10D, viscosity),
                0.0D,
                profile.maxSinkSpeed) * capFactor;

        double inheritedDownward = Math.max(0.0D, -input.motionY()) * verticalScale;
        double previousSettling = Mth.clamp(
                Math.max(input.settlingVelocity(), inheritedDownward),
                0.0D,
                profile.maxSinkSpeed);
        if (input.holdingStruggle()) {
            // Stored settling momentum is part of the solver state. Braking
            // only the new target would leave a player sliding downward for
            // several ticks after starting to struggle.
            previousSettling *= struggleSinkFactor;
        }
        double response = profile.settlingResponse;
        if (targetSinkSpeed < previousSettling) {
            response = Math.max(response, profile.capStopResponse * (1.0D - capFactor));
        }
        double settlingVelocity = lerp(previousSettling, targetSinkSpeed, response);
        if (remaining <= 0.0D) {
            settlingVelocity = 0.0D;
        } else if (remaining <= 0.002D) {
            settlingVelocity = remaining;
        } else {
            settlingVelocity = Math.min(settlingVelocity, remaining);
        }

        double struggleImpulse = 0.0D;
        double y;
        if (input.struggleCharge() >= 0.0D) {
            double charge = smooth(Mth.clamp(input.struggleCharge(), 0.0D, 1.0D));
            double depthStrength = lerp(1.0D, profile.struggleDeepMultiplier, depthEase);
            struggleImpulse = lerp(profile.struggleMin, profile.struggleMax, charge)
                    * depthStrength
                    * behavior.struggleMultiplier(progress);
            settlingVelocity *= 1.0D - charge;
            y = Math.min(profile.struggleMax + 0.08D,
                    Math.max(0.0D, input.motionY()) * 0.12D
                            + struggleImpulse);
        } else if (input.carryingStruggle() && input.motionY() > 0.0D) {
            settlingVelocity = 0.0D;
            y = input.motionY() * Math.max(0.30D, verticalScale);
        } else {
            y = -settlingVelocity;
        }

        return new Result(
                input.motionX() * walkScale + input.wobbleX(),
                y,
                input.motionZ() * walkScale + input.wobbleZ(),
                columnDepth,
                sinkLimit,
                naturalLimit,
                remaining,
                progress,
                horizontalSpeed,
                walkScale,
                verticalScale,
                naturalDrive,
                movementDrive,
                disturbanceDrive,
                yieldResistance,
                targetSinkSpeed,
                settlingVelocity,
                struggleImpulse);
    }

    static double sinkLimit(SinkingPhysicsProfile profile, double columnDepth,
            double layerTopDepth, double layerDepth, boolean hasDeeperLayer,
            double depthLimitScale) {
        return layerLimit(
                columnDepth,
                layerTopDepth,
                layerDepth,
                hasDeeperLayer,
                configuredDepth(profile),
                depthLimitScale);
    }

    private static double layerLimit(double columnDepth, double layerTopDepth,
            double layerDepth, boolean hasDeeperLayer, double configuredDepth,
            double depthLimitScale) {
        double available = Math.max(0.0D, columnDepth);
        double topDepth = Mth.clamp(layerTopDepth, 0.0D, available);
        double currentLayerDepth = Mth.clamp(
                layerDepth, 0.0D, Math.max(0.0D, available - topDepth));
        double enchantmentScale = Mth.clamp(depthLimitScale, 0.10D, 1.0D);
        double limit = topDepth + currentLayerDepth * configuredDepth * enchantmentScale;
        if (hasDeeperLayer
                && configuredDepth >= 1.0D - 1.0E-9D
                && depthLimitScale >= 1.0D - 1.0E-9D) {
            limit += LAYER_TRANSITION_DEPTH;
        }
        return Mth.clamp(limit, 0.0D, available);
    }

    private static boolean allowsLayerTransition(SinkingPhysicsProfile profile,
            boolean hasDeeperLayer, double depthLimitScale) {
        return hasDeeperLayer
                && configuredDepth(profile) >= 1.0D - 1.0E-9D
                && depthLimitScale >= 1.0D - 1.0E-9D;
    }

    static double configuredDepth(SinkingPhysicsProfile profile) {
        return profile.depthControlMode == MudSinkingDepthControl.Mode.SIMPLE
                ? profile.simpleMaximumDepth
                : MudSinkingDepthControl.maximumDepth(
                        profile.maxDepthFactor, profile.columnMargin);
    }

    static double walkScale(SinkingPhysicsProfile profile, double normalizedHeight) {
        double x0 = 0.0D;
        double x1 = profile.walkKneeDepth;
        double x2 = profile.walkThighDepth;
        double x3 = profile.walkWaistDepth;
        if (normalizedHeight <= x0) {
            return profile.walkSurface;
        }
        if (normalizedHeight >= x3) {
            return profile.walkWaist;
        }

        double h0 = x1 - x0;
        double h1 = x2 - x1;
        double h2 = x3 - x2;
        double d0 = (profile.walkKnee - profile.walkSurface) / h0;
        double d1 = (profile.walkThigh - profile.walkKnee) / h1;
        double d2 = (profile.walkWaist - profile.walkThigh) / h2;
        double m0 = endpointSlope(h0, h1, d0, d1);
        double m1 = interiorSlope(h0, h1, d0, d1);
        double m2 = interiorSlope(h1, h2, d1, d2);
        double m3 = endpointSlope(h2, h1, d2, d1);

        if (normalizedHeight <= x1) {
            return cubicHermite(x0, x1, profile.walkSurface, profile.walkKnee, m0, m1, normalizedHeight);
        }
        if (normalizedHeight <= x2) {
            return cubicHermite(x1, x2, profile.walkKnee, profile.walkThigh, m1, m2, normalizedHeight);
        }
        return cubicHermite(x2, x3, profile.walkThigh, profile.walkWaist, m2, m3, normalizedHeight);
    }

    private static double interiorSlope(double previousWidth, double nextWidth,
            double previousSlope, double nextSlope) {
        if (previousSlope == 0.0D || nextSlope == 0.0D
                || Math.signum(previousSlope) != Math.signum(nextSlope)) {
            return 0.0D;
        }
        double previousWeight = 2.0D * nextWidth + previousWidth;
        double nextWeight = nextWidth + 2.0D * previousWidth;
        return (previousWeight + nextWeight)
                / (previousWeight / previousSlope + nextWeight / nextSlope);
    }

    private static double endpointSlope(double width, double neighborWidth,
            double slope, double neighborSlope) {
        double result = ((2.0D * width + neighborWidth) * slope - width * neighborSlope)
                / (width + neighborWidth);
        if (Math.signum(result) != Math.signum(slope)) {
            return 0.0D;
        }
        if (Math.signum(slope) != Math.signum(neighborSlope)
                && Math.abs(result) > Math.abs(3.0D * slope)) {
            return 3.0D * slope;
        }
        return result;
    }

    private static double cubicHermite(double fromX, double toX, double fromY, double toY,
            double fromSlope, double toSlope, double x) {
        double width = toX - fromX;
        double t = Mth.clamp((x - fromX) / width, 0.0D, 1.0D);
        double t2 = t * t;
        double t3 = t2 * t;
        return (2.0D * t3 - 3.0D * t2 + 1.0D) * fromY
                + (t3 - 2.0D * t2 + t) * width * fromSlope
                + (-2.0D * t3 + 3.0D * t2) * toY
                + (t3 - t2) * width * toSlope;
    }

    private static double softPositive(double value, double softness) {
        double width = Math.max(1.0E-6D, softness);
        if (value <= -width) {
            return 0.0D;
        }
        if (value >= width) {
            return value;
        }
        double shifted = value + width;
        return shifted * shifted / (4.0D * width);
    }

    private static double lerp(double from, double to, double value) {
        return from + (to - from) * Mth.clamp(value, 0.0D, 1.0D);
    }

    private static double smooth(double value) {
        double clamped = Mth.clamp(value, 0.0D, 1.0D);
        return clamped * clamped * (3.0D - 2.0D * clamped);
    }

    record Input(
            double depth,
            double columnDepth,
            double standingHeight,
            double motionX,
            double motionY,
            double motionZ,
            double settlingVelocity,
            double agitation,
            double lookDelta,
            boolean crouching,
            boolean holdingStruggle,
            double struggleCharge,
            boolean carryingStruggle,
            double slurpImpulse,
            double wobbleX,
            double wobbleZ,
            double immersionFraction,
            double horizontalCoverage,
            double depthLimitScale,
            double walkRestoration,
            double layerTopDepth,
            double layerDepth,
            boolean hasDeeperLayer) {
        Input(
                double depth,
                double columnDepth,
                double standingHeight,
                double motionX,
                double motionY,
                double motionZ,
                double settlingVelocity,
                double agitation,
                double lookDelta,
                boolean crouching,
                boolean holdingStruggle,
                double struggleCharge,
                boolean carryingStruggle,
                double slurpImpulse,
                double wobbleX,
                double wobbleZ,
                double immersionFraction,
                double horizontalCoverage,
                double depthLimitScale,
                double walkRestoration) {
            this(
                    depth,
                    columnDepth,
                    standingHeight,
                    motionX,
                    motionY,
                    motionZ,
                    settlingVelocity,
                    agitation,
                    lookDelta,
                    crouching,
                    holdingStruggle,
                    struggleCharge,
                    carryingStruggle,
                    slurpImpulse,
                    wobbleX,
                    wobbleZ,
                    immersionFraction,
                    horizontalCoverage,
                    depthLimitScale,
                    walkRestoration,
                    0.0D,
                    columnDepth,
                    false);
        }

        Input(
                double depth,
                double columnDepth,
                double standingHeight,
                double motionX,
                double motionY,
                double motionZ,
                double settlingVelocity,
                double agitation,
                double lookDelta,
                boolean crouching,
                boolean holdingStruggle,
                double struggleCharge,
                boolean carryingStruggle,
                double slurpImpulse,
                double wobbleX,
                double wobbleZ,
                double immersionFraction,
                double depthLimitScale,
                double walkRestoration) {
            this(
                    depth,
                    columnDepth,
                    standingHeight,
                    motionX,
                    motionY,
                    motionZ,
                    settlingVelocity,
                    agitation,
                    lookDelta,
                    crouching,
                    holdingStruggle,
                    struggleCharge,
                    carryingStruggle,
                    slurpImpulse,
                    wobbleX,
                    wobbleZ,
                    immersionFraction,
                    1.0D,
                    depthLimitScale,
                    walkRestoration);
        }

        Input(
                double depth,
                double columnDepth,
                double standingHeight,
                double motionX,
                double motionY,
                double motionZ,
                double settlingVelocity,
                double agitation,
                double lookDelta,
                boolean crouching,
                boolean holdingStruggle,
                double struggleCharge,
                boolean carryingStruggle,
                double slurpImpulse,
                double wobbleX,
                double wobbleZ,
                double immersionFraction) {
            this(
                    depth,
                    columnDepth,
                    standingHeight,
                    motionX,
                    motionY,
                    motionZ,
                    settlingVelocity,
                    agitation,
                    lookDelta,
                    crouching,
                    holdingStruggle,
                    struggleCharge,
                    carryingStruggle,
                    slurpImpulse,
                    wobbleX,
                    wobbleZ,
                    immersionFraction,
                    1.0D,
                    1.0D,
                    0.0D);
        }

        Input(
                double depth,
                double columnDepth,
                double standingHeight,
                double motionX,
                double motionY,
                double motionZ,
                double settlingVelocity,
                double agitation,
                double lookDelta,
                boolean crouching,
                boolean holdingStruggle,
                double struggleCharge,
                boolean carryingStruggle,
                double slurpImpulse,
                double wobbleX,
                double wobbleZ) {
            this(
                    depth,
                    columnDepth,
                    standingHeight,
                    motionX,
                    motionY,
                    motionZ,
                    settlingVelocity,
                    agitation,
                    lookDelta,
                    crouching,
                    holdingStruggle,
                    struggleCharge,
                    carryingStruggle,
                    slurpImpulse,
                    wobbleX,
                    wobbleZ,
                    depth / Math.max(0.10D, standingHeight),
                    1.0D,
                    1.0D,
                    0.0D);
        }
    }

    record Result(
            double motionX,
            double motionY,
            double motionZ,
            double columnDepth,
            double sinkLimit,
            double naturalSinkLimit,
            double remainingDepth,
            double depthProgress,
            double horizontalSpeed,
            double walkScale,
            double verticalScale,
            double naturalSink,
            double movementSink,
            double disturbanceSink,
            double yieldResistance,
            double targetSinkSpeed,
            double settlingVelocity,
            double struggleImpulse) {
        double sinkStep() {
            return settlingVelocity;
        }
    }
}

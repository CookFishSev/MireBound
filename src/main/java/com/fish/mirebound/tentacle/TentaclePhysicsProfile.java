package com.fish.mirebound.tentacle;

import com.fish.mirebound.mud.MudPhysicsParameter;
import net.minecraft.util.Mth;

public record TentaclePhysicsProfile(
        int maximumInstances,
        double maximumVolume,
        int segmentCount,
        double segmentLength,
        double rootRadius,
        double tipRadius,
        double gravity,
        double damping,
        double stretchCompliance,
        double solverStretchLimit,
        double bendCompliance,
        double curvatureSmoothing,
        double bendRestRatio,
        double tipBendFlexibility,
        boolean selfCollisionEnabled,
        double selfCollisionRadiusScale,
        double selfCollisionResponse,
        int substeps,
        int iterations,
        double emergeSpeed,
        double retractSpeed,
        double idleReach,
        double idleHeight,
        double idleSway,
        double idleSwaySpeed,
        int idleDecisionTicks,
        double idleMinimumReach,
        double idleMaximumReach,
        double idleRestRatio,
        double lengthVariation,
        double thicknessVariation,
        double volumeLengthExponent,
        double thicknessLengthCoupling,
        double motionVariation,
        double muscleAmplitude,
        double muscleSpeed,
        double guideStrength,
        double guideDeadZoneScale,
        double guideInertiaTransfer,
        double tipOrientationStrength,
        int tipOrientationSegments,
        double tipAcceleration,
        double tipLookaheadSegments,
        double tipMaximumLeadSegments,
        double tipAdvanceSpeed,
        double trackingTipAdvanceSpeed,
        double trackingMaximumStretch,
        double lengthResponse,
        double slackCurve,
        int curveWaves,
        double curveDetail,
        double pathCellSize,
        double pathClearance,
        double pathTipClearanceScale,
        double pathMargin,
        int pathReplanTicks,
        int stuckReplanTicks,
        double stuckProgressDistance,
        int stuckClearanceSteps,
        int pathMaximumNodes,
        double pathGoalTolerance,
        int pathRepairLookahead,
        double trailSampleDistanceScale,
        int trailUnwrapTicks,
        int trailMaximumPoints,
        double trailReleaseRatio,
        double collisionSlop,
        boolean entityCollisionEnabled,
        double collisionResponse,
        double collisionImpulse,
        double collisionMaximumPushSpeed,
        double bodyCompliance,
        double bodyMaximumDeflection,
        double sizeForceExponent,
        double sizeStiffnessExponent,
        int collisionMaximumEntities,
        int entityQueryInterval,
        int collisionMaximumBlockSamples,
        int syncIntervalTicks) {

    public static TentaclePhysicsProfile fromValues(double[] values) {
        return new TentaclePhysicsProfile(
                integer(values, MudPhysicsParameter.TENTACLE_MAX_INSTANCES),
                value(values, MudPhysicsParameter.TENTACLE_MAX_VOLUME),
                integer(values, MudPhysicsParameter.TENTACLE_SEGMENTS),
                value(values, MudPhysicsParameter.TENTACLE_SEGMENT_LENGTH),
                value(values, MudPhysicsParameter.TENTACLE_ROOT_RADIUS),
                value(values, MudPhysicsParameter.TENTACLE_TIP_RADIUS),
                value(values, MudPhysicsParameter.TENTACLE_GRAVITY),
                value(values, MudPhysicsParameter.TENTACLE_DAMPING),
                value(values, MudPhysicsParameter.TENTACLE_STRETCH_COMPLIANCE),
                value(values, MudPhysicsParameter.TENTACLE_SOLVER_STRETCH_LIMIT),
                value(values, MudPhysicsParameter.TENTACLE_BEND_COMPLIANCE),
                value(values, MudPhysicsParameter.TENTACLE_CURVATURE_SMOOTHING),
                value(values, MudPhysicsParameter.TENTACLE_BEND_REST_RATIO),
                value(values, MudPhysicsParameter.TENTACLE_TIP_BEND_FLEXIBILITY),
                value(values, MudPhysicsParameter.TENTACLE_SELF_COLLISION_ENABLED) >= 0.5D,
                value(values, MudPhysicsParameter.TENTACLE_SELF_COLLISION_RADIUS_SCALE),
                value(values, MudPhysicsParameter.TENTACLE_SELF_COLLISION_RESPONSE),
                integer(values, MudPhysicsParameter.TENTACLE_SUBSTEPS),
                integer(values, MudPhysicsParameter.TENTACLE_ITERATIONS),
                value(values, MudPhysicsParameter.TENTACLE_EMERGE_SPEED),
                value(values, MudPhysicsParameter.TENTACLE_RETRACT_SPEED),
                value(values, MudPhysicsParameter.TENTACLE_IDLE_REACH),
                value(values, MudPhysicsParameter.TENTACLE_IDLE_HEIGHT),
                value(values, MudPhysicsParameter.TENTACLE_IDLE_SWAY),
                value(values, MudPhysicsParameter.TENTACLE_IDLE_SWAY_SPEED),
                integer(values, MudPhysicsParameter.TENTACLE_IDLE_DECISION_TICKS),
                value(values, MudPhysicsParameter.TENTACLE_IDLE_MIN_REACH),
                value(values, MudPhysicsParameter.TENTACLE_IDLE_MAX_REACH),
                value(values, MudPhysicsParameter.TENTACLE_IDLE_REST_RATIO),
                value(values, MudPhysicsParameter.TENTACLE_LENGTH_VARIATION),
                value(values, MudPhysicsParameter.TENTACLE_THICKNESS_VARIATION),
                value(values, MudPhysicsParameter.TENTACLE_VOLUME_LENGTH_EXPONENT),
                value(values, MudPhysicsParameter.TENTACLE_THICKNESS_LENGTH_COUPLING),
                value(values, MudPhysicsParameter.TENTACLE_MOTION_VARIATION),
                value(values, MudPhysicsParameter.TENTACLE_MUSCLE_AMPLITUDE),
                value(values, MudPhysicsParameter.TENTACLE_MUSCLE_SPEED),
                value(values, MudPhysicsParameter.TENTACLE_GUIDE_STRENGTH),
                value(values, MudPhysicsParameter.TENTACLE_GUIDE_DEAD_ZONE_SCALE),
                value(values, MudPhysicsParameter.TENTACLE_GUIDE_INERTIA_TRANSFER),
                value(values, MudPhysicsParameter.TENTACLE_TIP_ORIENTATION_STRENGTH),
                integer(values, MudPhysicsParameter.TENTACLE_TIP_ORIENTATION_SEGMENTS),
                value(values, MudPhysicsParameter.TENTACLE_TIP_ACCELERATION),
                value(values, MudPhysicsParameter.TENTACLE_TIP_LOOKAHEAD_SEGMENTS),
                value(values, MudPhysicsParameter.TENTACLE_TIP_MAX_LEAD_SEGMENTS),
                value(values, MudPhysicsParameter.TENTACLE_TIP_ADVANCE_SPEED),
                value(values, MudPhysicsParameter.TENTACLE_TRACK_TIP_ADVANCE_SPEED),
                value(values, MudPhysicsParameter.TENTACLE_TRACK_MAX_STRETCH),
                value(values, MudPhysicsParameter.TENTACLE_LENGTH_RESPONSE),
                value(values, MudPhysicsParameter.TENTACLE_SLACK_CURVE),
                integer(values, MudPhysicsParameter.TENTACLE_CURVE_WAVES),
                value(values, MudPhysicsParameter.TENTACLE_CURVE_DETAIL),
                value(values, MudPhysicsParameter.TENTACLE_PATH_CELL_SIZE),
                value(values, MudPhysicsParameter.TENTACLE_PATH_CLEARANCE),
                value(values, MudPhysicsParameter.TENTACLE_PATH_TIP_CLEARANCE_SCALE),
                value(values, MudPhysicsParameter.TENTACLE_PATH_MARGIN),
                integer(values, MudPhysicsParameter.TENTACLE_PATH_REPLAN_TICKS),
                integer(values, MudPhysicsParameter.TENTACLE_STUCK_REPLAN_TICKS),
                value(values, MudPhysicsParameter.TENTACLE_STUCK_PROGRESS_DISTANCE),
                integer(values, MudPhysicsParameter.TENTACLE_STUCK_CLEARANCE_STEPS),
                integer(values, MudPhysicsParameter.TENTACLE_PATH_MAX_NODES),
                value(values, MudPhysicsParameter.TENTACLE_PATH_GOAL_TOLERANCE),
                integer(values, MudPhysicsParameter.TENTACLE_PATH_REPAIR_LOOKAHEAD),
                value(values, MudPhysicsParameter.TENTACLE_TRAIL_SAMPLE_DISTANCE_SCALE),
                integer(values, MudPhysicsParameter.TENTACLE_TRAIL_UNWRAP_TICKS),
                integer(values, MudPhysicsParameter.TENTACLE_TRAIL_MAX_POINTS),
                value(values, MudPhysicsParameter.TENTACLE_TRAIL_RELEASE_RATIO),
                value(values, MudPhysicsParameter.TENTACLE_COLLISION_SLOP),
                value(values, MudPhysicsParameter.TENTACLE_ENTITY_COLLISION_ENABLED) >= 0.5D,
                value(values, MudPhysicsParameter.TENTACLE_COLLISION_RESPONSE),
                value(values, MudPhysicsParameter.TENTACLE_COLLISION_IMPULSE),
                value(values, MudPhysicsParameter.TENTACLE_COLLISION_MAX_PUSH_SPEED),
                value(values, MudPhysicsParameter.TENTACLE_BODY_COMPLIANCE),
                value(values, MudPhysicsParameter.TENTACLE_BODY_MAX_DEFLECTION),
                value(values, MudPhysicsParameter.TENTACLE_SIZE_FORCE_EXPONENT),
                value(values, MudPhysicsParameter.TENTACLE_SIZE_STIFFNESS_EXPONENT),
                integer(values, MudPhysicsParameter.TENTACLE_COLLISION_MAX_ENTITIES),
                integer(values, MudPhysicsParameter.TENTACLE_ENTITY_QUERY_INTERVAL),
                integer(values, MudPhysicsParameter.TENTACLE_COLLISION_MAX_BLOCK_SAMPLES),
                integer(values, MudPhysicsParameter.TENTACLE_SYNC_INTERVAL_TICKS));
    }

    TentaclePhysicsProfile scaledForVolume(double requestedVolume, long seed) {
        double volume = Mth.clamp(requestedVolume, 0.015625D, maximumVolume);
        double linearScale = Math.cbrt(volume);
        // A shared morphology sample keeps thick individuals correlated with long ones.
        double lengthFactor = varied(seed, 0x4D17A2B3L, lengthVariation);
        double thicknessFactor = varied(seed, 0x4D17A2B3L, thicknessVariation);
        double motionFactor = varied(seed, 0x32F19E57L, motionVariation);
        double lengthScale = Math.pow(volume, volumeLengthExponent)
                * lengthFactor * Math.pow(thicknessFactor, thicknessLengthCoupling);
        double targetLength = maximumLength() * lengthScale;
        int points = Mth.clamp((int) Math.round((segmentCount - 1) * lengthScale) + 1, 6, 32);
        double pointSpacing = targetLength / Math.max(1, points - 1);
        double root = rootRadius * linearScale * thicknessFactor;
        double tip = Math.min(root, tipRadius * linearScale * thicknessFactor);
        double speedScale = Math.max(0.40D, linearScale);

        return new TentaclePhysicsProfile(
                maximumInstances, maximumVolume,
                points, pointSpacing, root, tip,
                gravity / Math.max(0.65D, Math.sqrt(linearScale)),
                Mth.clamp(damping + (motionFactor - 1.0D) * 0.04D, 0.50D, 0.999D),
                stretchCompliance, solverStretchLimit, bendCompliance, curvatureSmoothing,
                bendRestRatio, tipBendFlexibility,
                selfCollisionEnabled, selfCollisionRadiusScale, selfCollisionResponse,
                substeps, iterations,
                emergeSpeed / speedScale, retractSpeed / speedScale,
                idleReach * lengthScale,
                idleHeight * Math.sqrt(linearScale * lengthScale),
                idleSway * linearScale * motionFactor,
                idleSwaySpeed * motionFactor,
                idleDecisionTicks,
                idleMinimumReach, idleMaximumReach, idleRestRatio,
                lengthVariation, thicknessVariation,
                volumeLengthExponent, thicknessLengthCoupling, motionVariation,
                muscleAmplitude * linearScale * motionFactor,
                muscleSpeed * motionFactor,
                guideStrength, guideDeadZoneScale, guideInertiaTransfer,
                tipOrientationStrength, tipOrientationSegments,
                tipAcceleration / Math.max(0.75D, Math.sqrt(linearScale)),
                tipLookaheadSegments, tipMaximumLeadSegments,
                tipAdvanceSpeed / Math.max(0.75D, Math.sqrt(linearScale)),
                trackingTipAdvanceSpeed * Math.sqrt(lengthScale)
                        / Math.max(0.75D, Math.sqrt(linearScale)),
                trackingMaximumStretch, lengthResponse, slackCurve, curveWaves, curveDetail,
                Mth.clamp(pathCellSize * Math.sqrt(linearScale), 0.25D, 1.0D),
                Math.max(pathClearance * linearScale, root * 0.72D),
                pathTipClearanceScale,
                pathMargin,
                pathReplanTicks, stuckReplanTicks, stuckProgressDistance, stuckClearanceSteps,
                pathMaximumNodes, pathGoalTolerance, pathRepairLookahead,
                trailSampleDistanceScale, trailUnwrapTicks, trailMaximumPoints, trailReleaseRatio,
                collisionSlop, entityCollisionEnabled, collisionResponse, collisionImpulse,
                collisionMaximumPushSpeed, bodyCompliance, bodyMaximumDeflection,
                sizeForceExponent, sizeStiffnessExponent, collisionMaximumEntities,
                entityQueryInterval, collisionMaximumBlockSamples,
                syncIntervalTicks);
    }

    double maximumLength() {
        return segmentLength * (segmentCount - 1);
    }

    double radiusAt(double fraction) {
        double shaped = Math.pow(Mth.clamp(fraction, 0.0D, 1.0D), 0.82D);
        return Mth.lerp(shaped, rootRadius, tipRadius);
    }

    double tipPathClearance() {
        return Math.max(collisionSlop, tipRadius * pathTipClearanceScale);
    }

    double tipLookaheadDistance() {
        return segmentLength * tipLookaheadSegments;
    }

    double tipMaximumLeadDistance() {
        return segmentLength * tipMaximumLeadSegments;
    }

    double trailSampleDistance() {
        return Math.max(tipRadius * 0.5D, segmentLength * trailSampleDistanceScale);
    }

    private static double varied(long seed, long salt, double amount) {
        long mixed = mix(seed ^ salt);
        double unit = ((mixed >>> 11) * 0x1.0p-53);
        return 1.0D + (unit * 2.0D - 1.0D) * amount;
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private static int integer(double[] values, MudPhysicsParameter parameter) {
        return Math.max(0, Mth.floor(value(values, parameter) + 0.5D));
    }

    private static double value(double[] values, MudPhysicsParameter parameter) {
        return parameter.sanitize(values[parameter.ordinal()]);
    }
}

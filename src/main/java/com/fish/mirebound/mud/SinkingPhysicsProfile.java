package com.fish.mirebound.mud;

import java.util.Arrays;
import net.minecraft.util.Mth;

final class SinkingPhysicsProfile {
    private static final SinkingPhysicsProfile MUD = builder(0.24D)
            .behavior(0.05D, 0.65D, 0.12D)
            .sink(0.0036D, 0.16D, 0.036D, 0.20D)
            .walk(0.985D, 0.88D, 0.16D, 0.002D)
            .vertical(0.28D, 0.065D)
            .disturbance(0.050D, 0.0022D, 0.00007D, 0.0012D, 0.0045D)
            .struggle(0.032D, 0.145D, 0.42D)
            .build();
    private static final SinkingPhysicsProfile RED_QUICKSAND = builder(0.82D)
            .behavior(0.98D, 0.08D, 0.0D)
            .sink(0.0065D, 0.09D, 0.052D, 0.29D)
            .walk(0.993D, 0.86D, 0.16D, 0.0008D)
            .vertical(0.19D, 0.047D)
            .disturbance(0.096D, 0.0051D, 0.00012D, 0.0021D, 0.0074D)
            .struggle(0.029D, 0.150D, 0.34D)
            .build();
    private static final SinkingPhysicsProfile SOFT_QUICKSAND = builder(0.92D)
            .behavior(1.10D, 0.08D, 0.0D)
            .sink(0.0070D, 0.08D, 0.056D, 0.30D)
            .walk(0.990D, 0.84D, 0.13D, 0.001D)
            .vertical(0.18D, 0.045D)
            .disturbance(0.105D, 0.0056D, 0.00013D, 0.0024D, 0.0080D)
            .struggle(0.028D, 0.145D, 0.32D)
            .build();
    private static final SinkingPhysicsProfile SILT = builder(0.62D)
            .behavior(0.60D, 0.28D, 0.03D)
            .sink(0.0050D, 0.12D, 0.044D, 0.24D)
            .walk(0.990D, 0.88D, 0.20D, 0.002D)
            .vertical(0.23D, 0.060D)
            .disturbance(0.070D, 0.0032D, 0.00008D, 0.0016D, 0.0050D)
            .struggle(0.034D, 0.155D, 0.40D)
            .build();
    private static final SinkingPhysicsProfile THIN_MUD = builder(0.16D)
            .behavior(0.02D, 0.25D, 0.04D)
            .sink(0.0024D, 0.20D, 0.028D, 0.14D)
            .walk(0.995D, 0.92D, 0.34D, 0.010D)
            .vertical(0.38D, 0.12D)
            .disturbance(0.028D, 0.0010D, 0.00004D, 0.0007D, 0.0025D)
            .struggle(0.045D, 0.175D, 0.62D)
            .build();
    private static final SinkingPhysicsProfile SHALLOW_MUD = builder(0.30D)
            .behavior(0.03D, 0.48D, 0.08D)
            .sink(0.0032D, 0.16D, 0.034D, 0.18D)
            .walk(0.990D, 0.90D, 0.23D, 0.004D)
            .vertical(0.31D, 0.080D)
            .disturbance(0.042D, 0.0018D, 0.00006D, 0.0010D, 0.0038D)
            .struggle(0.040D, 0.165D, 0.52D)
            .build();
    private static final SinkingPhysicsProfile TIDAL_MUD = builder(0.32D)
            .behavior(0.08D, 0.38D, 0.05D)
            .sink(0.0030D, 0.18D, 0.032D, 0.18D)
            .walk(0.995D, 0.91D, 0.28D, 0.006D)
            .vertical(0.34D, 0.090D)
            .disturbance(0.036D, 0.0015D, 0.00005D, 0.0009D, 0.0032D)
            .struggle(0.042D, 0.170D, 0.56D)
            .build();
    private static final SinkingPhysicsProfile PEAT_BOG = builder(0.88D)
            .behavior(0.03D, 0.88D, 0.28D)
            .sink(0.0048D, 0.09D, 0.042D, 0.30D)
            .walk(0.975D, 0.77D, 0.09D, 0.0004D)
            .vertical(0.18D, 0.040D)
            .disturbance(0.062D, 0.0030D, 0.00007D, 0.0016D, 0.0048D)
            .struggle(0.024D, 0.115D, 0.28D)
            .build();
    private static final SinkingPhysicsProfile DENSE_ORGANIC = builder(0.84D)
            .behavior(0.02D, 0.90D, 0.20D)
            .sink(0.0052D, 0.10D, 0.044D, 0.28D)
            .walk(0.980D, 0.80D, 0.10D, 0.0005D)
            .vertical(0.20D, 0.045D)
            .disturbance(0.072D, 0.0035D, 0.00008D, 0.0018D, 0.0055D)
            .struggle(0.026D, 0.125D, 0.30D)
            .build();
    private static final SinkingPhysicsProfile MIRE = builder(1.02D)
            .behavior(0.02D, 0.95D, 0.32D)
            .sink(0.0054D, 0.08D, 0.046D, 0.32D)
            .walk(0.970D, 0.72D, 0.06D, 0.0002D)
            .vertical(0.16D, 0.035D)
            .disturbance(0.070D, 0.0036D, 0.00008D, 0.0018D, 0.0055D)
            .struggle(0.022D, 0.105D, 0.24D)
            .build();
    private static final SinkingPhysicsProfile LIVING_SLIME = builder(0.72D)
            .sink(0.0018D, 0.30D, 0.050D, 0.24D)
            .walk(0.998D, 0.96D, 0.78D, 0.55D)
            .vertical(0.68D, 0.34D)
            .disturbance(0.016D, 0.0005D, 0.00003D, 0.0004D, 0.0015D)
            .struggle(0.060D, 0.220D, 0.70D)
            .build();
    private static final SinkingPhysicsProfile TAR = builder(0.94D)
            .behavior(0.0D, 0.35D, 1.15D)
            .sink(0.0032D, 0.08D, 0.032D, 0.34D)
            .walk(0.940D, 0.58D, 0.045D, 0.0001D)
            .vertical(0.11D, 0.022D)
            .disturbance(0.034D, 0.0018D, 0.00004D, 0.0010D, 0.0030D)
            .struggle(0.016D, 0.075D, 0.18D)
            .build();
    private static final SinkingPhysicsProfile JUNGLE_QUICKSAND = builder(0.90D)
            .behavior(0.82D, 0.25D, 0.05D)
            .sink(0.0066D, 0.08D, 0.054D, 0.32D)
            .walk(0.985D, 0.80D, 0.09D, 0.0004D)
            .vertical(0.17D, 0.040D)
            .disturbance(0.098D, 0.0050D, 0.00012D, 0.0022D, 0.0075D)
            .struggle(0.025D, 0.125D, 0.27D)
            .build();
    private static final SinkingPhysicsProfile ASH_QUICKSAND = builder(0.86D)
            .behavior(1.05D, 0.05D, 0.0D)
            .sink(0.0064D, 0.085D, 0.053D, 0.29D)
            .walk(0.994D, 0.86D, 0.15D, 0.0008D)
            .vertical(0.19D, 0.047D)
            .disturbance(0.112D, 0.0058D, 0.00014D, 0.0024D, 0.0084D)
            .struggle(0.030D, 0.150D, 0.34D)
            .build();
    private static final SinkingPhysicsProfile SOUL_SILT = builder(1.12D)
            .behavior(0.58D, 0.38D, 0.10D)
            .sink(0.0056D, 0.075D, 0.047D, 0.34D)
            .walk(0.980D, 0.76D, 0.075D, 0.0003D)
            .vertical(0.15D, 0.032D)
            .disturbance(0.075D, 0.0038D, 0.00009D, 0.0019D, 0.0058D)
            .struggle(0.020D, 0.098D, 0.22D)
            .build();
    private static final SinkingPhysicsProfile GEL_CLAY = builder(0.72D)
            .behavior(0.03D, 0.82D, 0.82D)
            .sink(0.0034D, 0.085D, 0.034D, 0.34D)
            .walk(0.955D, 0.60D, 0.045D, 0.0002D)
            .vertical(0.12D, 0.024D)
            .disturbance(0.038D, 0.0019D, 0.00005D, 0.0011D, 0.0032D)
            .struggle(0.018D, 0.082D, 0.20D)
            .build();
    private static final SinkingPhysicsProfile LIME_MUD = builder(0.58D)
            .behavior(0.08D, 0.48D, 0.06D)
            .sink(0.0031D, 0.17D, 0.033D, 0.19D)
            .walk(0.992D, 0.91D, 0.25D, 0.005D)
            .vertical(0.33D, 0.086D)
            .disturbance(0.040D, 0.0016D, 0.00005D, 0.0009D, 0.0034D)
            .struggle(0.043D, 0.172D, 0.56D)
            .build();
    private static final SinkingPhysicsProfile END_SILT = builder(0.84D)
            .behavior(0.72D, 0.24D, 0.02D)
            .sink(0.0058D, 0.08D, 0.048D, 0.32D)
            .walk(0.982D, 0.78D, 0.09D, 0.0003D)
            .vertical(0.16D, 0.035D)
            .disturbance(0.082D, 0.0040D, 0.00010D, 0.0020D, 0.0061D)
            .struggle(0.023D, 0.110D, 0.25D)
            .build();
    private static final SinkingPhysicsProfile SCULK_MIRE = builder(1.08D)
            .behavior(0.03D, 0.88D, 0.48D)
            .sink(0.0045D, 0.08D, 0.040D, 0.30D)
            .walk(0.965D, 0.70D, 0.055D, 0.0002D)
            .vertical(0.15D, 0.030D)
            .disturbance(0.060D, 0.0030D, 0.00008D, 0.0017D, 0.0050D)
            .struggle(0.020D, 0.095D, 0.22D)
            .build();
    private static final SinkingPhysicsProfile GRAVEL_SILT = builder(0.86D)
            .behavior(0.78D, 0.20D, 0.02D)
            .sink(0.0056D, 0.08D, 0.046D, 0.31D)
            .walk(0.985D, 0.80D, 0.10D, 0.0004D)
            .vertical(0.17D, 0.037D)
            .disturbance(0.078D, 0.0038D, 0.00010D, 0.0019D, 0.0058D)
            .struggle(0.023D, 0.110D, 0.25D)
            .build();
    private static final SinkingPhysicsProfile FUNGAL_MIRE = builder(1.04D)
            .behavior(0.03D, 0.78D, 0.18D)
            .sink(0.0042D, 0.085D, 0.040D, 0.31D)
            .walk(0.968D, 0.72D, 0.060D, 0.0002D)
            .vertical(0.15D, 0.030D)
            .disturbance(0.058D, 0.0028D, 0.00008D, 0.0016D, 0.0048D)
            .struggle(0.020D, 0.098D, 0.23D)
            .build();
    private static final SinkingPhysicsProfile STONE_CLAY = builder(0.80D)
            .behavior(0.03D, 0.84D, 0.54D)
            .sink(0.0038D, 0.09D, 0.036D, 0.32D)
            .walk(0.958D, 0.64D, 0.050D, 0.0002D)
            .vertical(0.13D, 0.026D)
            .disturbance(0.042D, 0.0020D, 0.00005D, 0.0012D, 0.0035D)
            .struggle(0.018D, 0.084D, 0.20D)
            .build();
    private static final SinkingPhysicsProfile PALE_MIRE = builder(0.82D)
            .behavior(0.03D, 0.78D, 0.28D)
            .sink(0.0040D, 0.085D, 0.040D, 0.31D)
            .walk(0.968D, 0.70D, 0.060D, 0.0002D)
            .vertical(0.15D, 0.030D)
            .disturbance(0.052D, 0.0026D, 0.00007D, 0.0015D, 0.0046D)
            .struggle(0.020D, 0.096D, 0.23D)
            .build();
    private static final SinkingPhysicsProfile PEAT_SILT = builder(0.90D)
            .behavior(0.03D, 0.82D, 0.22D)
            .sink(0.0041D, 0.085D, 0.040D, 0.31D)
            .walk(0.966D, 0.68D, 0.055D, 0.0002D)
            .vertical(0.14D, 0.028D)
            .disturbance(0.050D, 0.0024D, 0.00007D, 0.0014D, 0.0044D)
            .struggle(0.019D, 0.092D, 0.22D)
            .build();

    final double maxDepthFactor;
    final double columnMargin;
    final double simpleMaximumDepth;
    final double simpleNaturalDepth;
    final MudSinkingDepthControl.Mode depthControlMode;
    final double baseSinkSpeed;
    final double deepSinkRatio;
    final double maxSinkSpeed;
    final double brakeDistance;
    final double walkSurface;
    final double walkKnee;
    final double walkThigh;
    final double walkWaist;
    final double walkKneeDepth;
    final double walkThighDepth;
    final double walkWaistDepth;
    final double stepHeight;
    final double verticalSurface;
    final double verticalDeep;
    final double movementSinkScale;
    final double crouchSink;
    final double lookSinkScale;
    final double holdSink;
    final double agitationSinkScale;
    final double struggleMin;
    final double struggleMax;
    final double struggleDeepMultiplier;
    final double struggleSinkSuppression;
    final int struggleLiftTicks;
    final double yieldThreshold;
    final double yieldDepthGain;
    final double settlingResponse;
    final double viscositySurface;
    final double viscosityDeep;
    final double yieldSoftness;
    final double disturbanceYieldReduction;
    final double capStopResponse;
    final MudBehaviorComponents behavior;
    private double[] passthroughValues;

    private SinkingPhysicsProfile(Builder builder) {
        maxDepthFactor = builder.maxDepthFactor;
        columnMargin = builder.columnMargin;
        simpleMaximumDepth = Double.isFinite(builder.simpleMaximumDepth)
                ? MudSinkingDepthControl.clamp(builder.simpleMaximumDepth)
                : MudSinkingDepthControl.maximumDepth(maxDepthFactor, columnMargin);
        simpleNaturalDepth = Math.min(
                Double.isFinite(builder.simpleNaturalDepth)
                        ? MudSinkingDepthControl.clamp(builder.simpleNaturalDepth)
                        : MudSinkingDepthControl.maximumDepth(maxDepthFactor, columnMargin),
                simpleMaximumDepth);
        depthControlMode = builder.depthControlMode;
        baseSinkSpeed = builder.baseSinkSpeed;
        deepSinkRatio = builder.deepSinkRatio;
        maxSinkSpeed = builder.maxSinkSpeed;
        brakeDistance = builder.brakeDistance;
        walkSurface = builder.walkSurface;
        walkKnee = builder.walkKnee;
        walkThigh = builder.walkThigh;
        walkWaist = builder.walkWaist;
        walkKneeDepth = builder.walkKneeDepth;
        walkThighDepth = builder.walkThighDepth;
        walkWaistDepth = builder.walkWaistDepth;
        stepHeight = builder.stepHeight;
        verticalSurface = builder.verticalSurface;
        verticalDeep = builder.verticalDeep;
        movementSinkScale = builder.movementSinkScale;
        crouchSink = builder.crouchSink;
        lookSinkScale = builder.lookSinkScale;
        holdSink = builder.holdSink;
        agitationSinkScale = builder.agitationSinkScale;
        struggleMin = builder.struggleMin;
        struggleMax = builder.struggleMax;
        struggleDeepMultiplier = builder.struggleDeepMultiplier;
        struggleSinkSuppression = builder.struggleSinkSuppression;
        struggleLiftTicks = builder.struggleLiftTicks;
        yieldThreshold = builder.yieldThreshold;
        yieldDepthGain = builder.yieldDepthGain;
        settlingResponse = builder.settlingResponse;
        viscositySurface = builder.viscositySurface;
        viscosityDeep = builder.viscosityDeep;
        yieldSoftness = builder.yieldSoftness;
        disturbanceYieldReduction = builder.disturbanceYieldReduction;
        capStopResponse = builder.capStopResponse;
        behavior = builder.behavior;
    }

    static SinkingPhysicsProfile forMedium(SinkingMedium medium) {
        return switch (medium) {
            case MUD -> MUD;
            case RED_QUICKSAND -> RED_QUICKSAND;
            case SOFT_QUICKSAND -> SOFT_QUICKSAND;
            case SILT -> SILT;
            case THIN_MUD -> THIN_MUD;
            case SHALLOW_MUD -> SHALLOW_MUD;
            case TIDAL_MUD -> TIDAL_MUD;
            case PEAT_BOG -> PEAT_BOG;
            case LIVING_SLIME -> LIVING_SLIME;
            case TAR -> TAR;
            case JUNGLE_QUICKSAND -> JUNGLE_QUICKSAND;
            case INSECT_MOUND -> PEAT_BOG;
            case ASH_QUICKSAND -> ASH_QUICKSAND;
            case SOUL_SILT -> SOUL_SILT;
            case GEL_CLAY -> GEL_CLAY;
            case LIME_MUD -> LIME_MUD;
            case END_SILT -> END_SILT;
            case SCULK_MIRE -> SCULK_MIRE;
            case GRAVEL_SILT -> GRAVEL_SILT;
            case FUNGAL_MIRE -> FUNGAL_MIRE;
            case STONE_CLAY -> STONE_CLAY;
            case PALE_MIRE -> PALE_MIRE;
            case PEAT_SILT -> PEAT_SILT;
            case TENDER_FLESH, ASSIMILATION_SLIME -> DENSE_ORGANIC;
            case MIRE -> MIRE;
        };
    }

    static SinkingPhysicsProfile fromValues(double[] values) {
        Builder builder = builder(value(values, MudPhysicsParameter.MAX_DEPTH_FACTOR));
        builder.columnMargin = value(values, MudPhysicsParameter.COLUMN_MARGIN);
        builder.simpleMaximumDepth = value(
                values, MudPhysicsParameter.SIMPLE_MAXIMUM_SINKING_DEPTH);
        builder.simpleNaturalDepth = value(
                values, MudPhysicsParameter.SIMPLE_NATURAL_SINKING_DEPTH);
        builder.depthControlMode = MudSinkingDepthControl.mode(
                value(values, MudPhysicsParameter.SINKING_DEPTH_CONTROL_MODE));
        SinkingPhysicsProfile profile = builder
                .sink(
                        value(values, MudPhysicsParameter.BASE_SINK_SPEED),
                        value(values, MudPhysicsParameter.DEEP_SINK_RATIO),
                        value(values, MudPhysicsParameter.MAX_SINK_SPEED),
                        value(values, MudPhysicsParameter.BRAKE_DISTANCE))
                .walk(
                        value(values, MudPhysicsParameter.WALK_SURFACE),
                        value(values, MudPhysicsParameter.WALK_KNEE),
                        value(values, MudPhysicsParameter.WALK_THIGH),
                        value(values, MudPhysicsParameter.WALK_WAIST))
                .walkDepths(
                        value(values, MudPhysicsParameter.WALK_KNEE_DEPTH),
                        value(values, MudPhysicsParameter.WALK_THIGH_DEPTH),
                        value(values, MudPhysicsParameter.WALK_WAIST_DEPTH))
                .stepHeight(value(values, MudPhysicsParameter.STEP_HEIGHT))
                .vertical(
                        value(values, MudPhysicsParameter.VERTICAL_SURFACE),
                        value(values, MudPhysicsParameter.VERTICAL_DEEP))
                .disturbance(
                        value(values, MudPhysicsParameter.MOVEMENT_SINK_SCALE),
                        value(values, MudPhysicsParameter.CROUCH_SINK),
                        value(values, MudPhysicsParameter.LOOK_SINK_SCALE),
                        value(values, MudPhysicsParameter.HOLD_SINK),
                        value(values, MudPhysicsParameter.AGITATION_SINK_SCALE))
                .struggle(
                        value(values, MudPhysicsParameter.STRUGGLE_MIN),
                        value(values, MudPhysicsParameter.STRUGGLE_MAX),
                        value(values, MudPhysicsParameter.STRUGGLE_DEEP_MULTIPLIER))
                .struggleProtection(
                        value(values, MudPhysicsParameter.STRUGGLE_SINK_SUPPRESSION),
                        integer(values, MudPhysicsParameter.STRUGGLE_LIFT_TICKS))
                .rheology(
                        value(values, MudPhysicsParameter.YIELD_THRESHOLD),
                        value(values, MudPhysicsParameter.YIELD_DEPTH_GAIN),
                        value(values, MudPhysicsParameter.SETTLING_RESPONSE),
                        value(values, MudPhysicsParameter.VISCOSITY_SURFACE),
                        value(values, MudPhysicsParameter.VISCOSITY_DEEP),
                        value(values, MudPhysicsParameter.YIELD_SOFTNESS),
                        value(values, MudPhysicsParameter.DISTURBANCE_YIELD_REDUCTION),
                        value(values, MudPhysicsParameter.CAP_STOP_RESPONSE))
                .behavior(
                        value(values, MudPhysicsParameter.GRANULAR_COLLAPSE),
                        value(values, MudPhysicsParameter.COHESIVE_SUCTION),
                        value(values, MudPhysicsParameter.ADHESIVE_GRIP))
                .build();
        profile.passthroughValues = Arrays.copyOf(values, MudPhysicsParameter.COUNT);
        return profile;
    }

    void writeTo(double[] values) {
        if (passthroughValues != null) {
            System.arraycopy(passthroughValues, 0, values, 0,
                    Math.min(values.length, passthroughValues.length));
        }
        put(values, MudPhysicsParameter.MAX_DEPTH_FACTOR, maxDepthFactor);
        put(values, MudPhysicsParameter.COLUMN_MARGIN, columnMargin);
        put(values, MudPhysicsParameter.SIMPLE_MAXIMUM_SINKING_DEPTH,
                simpleMaximumDepth);
        put(values, MudPhysicsParameter.SIMPLE_NATURAL_SINKING_DEPTH,
                simpleNaturalDepth);
        put(values, MudPhysicsParameter.SINKING_DEPTH_CONTROL_MODE,
                depthControlMode.parameterValue());
        put(values, MudPhysicsParameter.BASE_SINK_SPEED, baseSinkSpeed);
        put(values, MudPhysicsParameter.DEEP_SINK_RATIO, deepSinkRatio);
        put(values, MudPhysicsParameter.MAX_SINK_SPEED, maxSinkSpeed);
        put(values, MudPhysicsParameter.BRAKE_DISTANCE, brakeDistance);
        put(values, MudPhysicsParameter.WALK_SURFACE, walkSurface);
        put(values, MudPhysicsParameter.WALK_KNEE, walkKnee);
        put(values, MudPhysicsParameter.WALK_THIGH, walkThigh);
        put(values, MudPhysicsParameter.WALK_WAIST, walkWaist);
        put(values, MudPhysicsParameter.WALK_KNEE_DEPTH, walkKneeDepth);
        put(values, MudPhysicsParameter.WALK_THIGH_DEPTH, walkThighDepth);
        put(values, MudPhysicsParameter.WALK_WAIST_DEPTH, walkWaistDepth);
        put(values, MudPhysicsParameter.STEP_HEIGHT, stepHeight);
        put(values, MudPhysicsParameter.VERTICAL_SURFACE, verticalSurface);
        put(values, MudPhysicsParameter.VERTICAL_DEEP, verticalDeep);
        put(values, MudPhysicsParameter.MOVEMENT_SINK_SCALE, movementSinkScale);
        put(values, MudPhysicsParameter.CROUCH_SINK, crouchSink);
        put(values, MudPhysicsParameter.LOOK_SINK_SCALE, lookSinkScale);
        put(values, MudPhysicsParameter.HOLD_SINK, holdSink);
        put(values, MudPhysicsParameter.AGITATION_SINK_SCALE, agitationSinkScale);
        put(values, MudPhysicsParameter.STRUGGLE_MIN, struggleMin);
        put(values, MudPhysicsParameter.STRUGGLE_MAX, struggleMax);
        put(values, MudPhysicsParameter.STRUGGLE_DEEP_MULTIPLIER, struggleDeepMultiplier);
        put(values, MudPhysicsParameter.STRUGGLE_SINK_SUPPRESSION, struggleSinkSuppression);
        put(values, MudPhysicsParameter.STRUGGLE_LIFT_TICKS, struggleLiftTicks);
        put(values, MudPhysicsParameter.YIELD_THRESHOLD, yieldThreshold);
        put(values, MudPhysicsParameter.YIELD_DEPTH_GAIN, yieldDepthGain);
        put(values, MudPhysicsParameter.SETTLING_RESPONSE, settlingResponse);
        put(values, MudPhysicsParameter.VISCOSITY_SURFACE, viscositySurface);
        put(values, MudPhysicsParameter.VISCOSITY_DEEP, viscosityDeep);
        put(values, MudPhysicsParameter.YIELD_SOFTNESS, yieldSoftness);
        put(values, MudPhysicsParameter.DISTURBANCE_YIELD_REDUCTION, disturbanceYieldReduction);
        put(values, MudPhysicsParameter.CAP_STOP_RESPONSE, capStopResponse);
        put(values, MudPhysicsParameter.GRANULAR_COLLAPSE, behavior.granularCollapse());
        put(values, MudPhysicsParameter.COHESIVE_SUCTION, behavior.cohesiveSuction());
        put(values, MudPhysicsParameter.ADHESIVE_GRIP, behavior.adhesiveGrip());
    }

    static SinkingPhysicsProfile blend(SinkingPhysicsProfile from, SinkingPhysicsProfile to, double amount) {
        double t = smooth(amount);
        Builder builder = builder(lerp(from.maxDepthFactor, to.maxDepthFactor, t));
        builder.columnMargin = lerp(from.columnMargin, to.columnMargin, t);
        builder.simpleMaximumDepth = lerp(
                from.simpleMaximumDepth, to.simpleMaximumDepth, t);
        builder.simpleNaturalDepth = lerp(
                from.simpleNaturalDepth, to.simpleNaturalDepth, t);
        builder.depthControlMode = t < 0.5D ? from.depthControlMode : to.depthControlMode;
        return builder
                .sink(
                        lerp(from.baseSinkSpeed, to.baseSinkSpeed, t),
                        lerp(from.deepSinkRatio, to.deepSinkRatio, t),
                        lerp(from.maxSinkSpeed, to.maxSinkSpeed, t),
                        lerp(from.brakeDistance, to.brakeDistance, t))
                .walk(
                        lerp(from.walkSurface, to.walkSurface, t),
                        lerp(from.walkKnee, to.walkKnee, t),
                        lerp(from.walkThigh, to.walkThigh, t),
                        lerp(from.walkWaist, to.walkWaist, t))
                .walkDepths(
                        lerp(from.walkKneeDepth, to.walkKneeDepth, t),
                        lerp(from.walkThighDepth, to.walkThighDepth, t),
                        lerp(from.walkWaistDepth, to.walkWaistDepth, t))
                .stepHeight(lerp(from.stepHeight, to.stepHeight, t))
                .vertical(
                        lerp(from.verticalSurface, to.verticalSurface, t),
                        lerp(from.verticalDeep, to.verticalDeep, t))
                .disturbance(
                        lerp(from.movementSinkScale, to.movementSinkScale, t),
                        lerp(from.crouchSink, to.crouchSink, t),
                        lerp(from.lookSinkScale, to.lookSinkScale, t),
                        lerp(from.holdSink, to.holdSink, t),
                        lerp(from.agitationSinkScale, to.agitationSinkScale, t))
                .struggle(
                        lerp(from.struggleMin, to.struggleMin, t),
                        lerp(from.struggleMax, to.struggleMax, t),
                        lerp(from.struggleDeepMultiplier, to.struggleDeepMultiplier, t))
                .struggleProtection(
                        lerp(from.struggleSinkSuppression, to.struggleSinkSuppression, t),
                        (int) Math.round(lerp(from.struggleLiftTicks, to.struggleLiftTicks, t)))
                .rheology(
                        lerp(from.yieldThreshold, to.yieldThreshold, t),
                        lerp(from.yieldDepthGain, to.yieldDepthGain, t),
                        lerp(from.settlingResponse, to.settlingResponse, t),
                        lerp(from.viscositySurface, to.viscositySurface, t),
                        lerp(from.viscosityDeep, to.viscosityDeep, t),
                        lerp(from.yieldSoftness, to.yieldSoftness, t),
                        lerp(from.disturbanceYieldReduction, to.disturbanceYieldReduction, t),
                        lerp(from.capStopResponse, to.capStopResponse, t))
                .behavior(
                        lerp(from.behavior.granularCollapse(), to.behavior.granularCollapse(), t),
                        lerp(from.behavior.cohesiveSuction(), to.behavior.cohesiveSuction(), t),
                        lerp(from.behavior.adhesiveGrip(), to.behavior.adhesiveGrip(), t))
                .build();
    }

    private static Builder builder(double maxDepthFactor) {
        return new Builder(maxDepthFactor);
    }

    private static double lerp(double from, double to, double amount) {
        return from + (to - from) * Mth.clamp(amount, 0.0D, 1.0D);
    }

    private static double value(double[] values, MudPhysicsParameter parameter) {
        return parameter.sanitize(values[parameter.ordinal()]);
    }

    private static int integer(double[] values, MudPhysicsParameter parameter) {
        return Math.max(1, (int) Math.round(value(values, parameter)));
    }

    private static void put(double[] values, MudPhysicsParameter parameter, double value) {
        values[parameter.ordinal()] = value;
    }

    private static double smooth(double value) {
        double clamped = Mth.clamp(value, 0.0D, 1.0D);
        return clamped * clamped * (3.0D - 2.0D * clamped);
    }

    private static final class Builder {
        private final double maxDepthFactor;
        private double columnMargin = 0.035D;
        private double simpleMaximumDepth = Double.NaN;
        private double simpleNaturalDepth = Double.NaN;
        private MudSinkingDepthControl.Mode depthControlMode = MudSinkingDepthControl.Mode.SIMPLE;
        private double baseSinkSpeed;
        private double deepSinkRatio;
        private double maxSinkSpeed;
        private double brakeDistance;
        private double walkSurface;
        private double walkKnee;
        private double walkThigh;
        private double walkWaist;
        private double walkKneeDepth = 0.25D;
        private double walkThighDepth = 0.38D;
        private double walkWaistDepth = 0.52D;
        private double stepHeight = 0.35D;
        private double verticalSurface;
        private double verticalDeep;
        private double movementSinkScale;
        private double crouchSink;
        private double lookSinkScale;
        private double holdSink;
        private double agitationSinkScale;
        private double struggleMin;
        private double struggleMax;
        private double struggleDeepMultiplier;
        private double struggleSinkSuppression = 0.82D;
        private int struggleLiftTicks = 4;
        private double yieldThreshold = 0.0008D;
        private double yieldDepthGain = 1.25D;
        private double settlingResponse = 0.18D;
        private double viscositySurface = 1.0D;
        private double viscosityDeep = 2.6D;
        private double yieldSoftness = 0.0006D;
        private double disturbanceYieldReduction = 0.72D;
        private double capStopResponse = 0.48D;
        private MudBehaviorComponents behavior = MudBehaviorComponents.NONE;

        private Builder(double maxDepthFactor) {
            this.maxDepthFactor = maxDepthFactor;
        }

        private Builder sink(double base, double deepRatio, double max, double brake) {
            baseSinkSpeed = base;
            deepSinkRatio = deepRatio;
            maxSinkSpeed = max;
            brakeDistance = brake;
            yieldThreshold = Math.max(0.0001D, base * 0.12D);
            yieldSoftness = Math.max(0.0002D, base * 0.10D);
            return this;
        }

        private Builder walk(double surface, double knee, double thigh, double waist) {
            walkSurface = surface;
            walkKnee = knee;
            walkThigh = thigh;
            walkWaist = waist;
            return this;
        }

        private Builder walkDepths(double knee, double thigh, double waist) {
            walkKneeDepth = knee;
            walkThighDepth = thigh;
            walkWaistDepth = waist;
            return this;
        }

        private Builder stepHeight(double value) {
            stepHeight = value;
            return this;
        }

        private Builder vertical(double surface, double deep) {
            verticalSurface = surface;
            verticalDeep = deep;
            return this;
        }

        private Builder disturbance(double movement, double crouch, double look, double hold, double agitation) {
            movementSinkScale = movement;
            crouchSink = crouch;
            lookSinkScale = look;
            holdSink = hold;
            agitationSinkScale = agitation;
            return this;
        }

        private Builder struggle(double min, double max, double deepMultiplier) {
            struggleMin = min;
            struggleMax = max;
            struggleDeepMultiplier = deepMultiplier;
            return this;
        }

        private Builder struggleProtection(double sinkSuppression, int liftTicks) {
            struggleSinkSuppression = Mth.clamp(sinkSuppression, 0.0D, 1.0D);
            struggleLiftTicks = Math.max(1, liftTicks);
            return this;
        }

        private Builder rheology(double yield, double depthGain, double response,
                double surfaceViscosity, double deepViscosity, double softness,
                double disturbanceReduction, double stopResponse) {
            yieldThreshold = yield;
            yieldDepthGain = depthGain;
            settlingResponse = response;
            viscositySurface = surfaceViscosity;
            viscosityDeep = deepViscosity;
            yieldSoftness = softness;
            disturbanceYieldReduction = disturbanceReduction;
            capStopResponse = stopResponse;
            return this;
        }

        private Builder behavior(double granular, double cohesive, double adhesive) {
            behavior = new MudBehaviorComponents(granular, cohesive, adhesive);
            return this;
        }

        private SinkingPhysicsProfile build() {
            return new SinkingPhysicsProfile(this);
        }
    }
}

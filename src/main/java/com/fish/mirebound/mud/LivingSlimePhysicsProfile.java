package com.fish.mirebound.mud;

final class LivingSlimePhysicsProfile {
    static final LivingSlimePhysicsProfile DEFAULT = new LivingSlimePhysicsProfile(
            0.30D,
            0.025D,
            0.52D,
            0.62D,
            0.10D,
            0.85D,
            0.0010D,
            0.0D,
            0.0015D,
            0.180D,
            1.500D,
            0.045D,
            0.82D,
            1.0D,
            0.75D,
            0.10D,
            0.10D,
            0.010D,
            0.001D,
            0.100D,
            0.720D,
            0.85D,
            0.12D,
            2,
            4);

    final double minColumnDepth;
    final double columnMargin;
    final double elasticDepthHeightFactor;
    final double elasticDepthColumnFactor;
    final double verticalTug;
    final double verticalRetention;
    final double baseSinkBias;
    final double movementSinkScale;
    final double crouchSink;
    final double maxDownSpeed;
    final double maxUpSpeed;
    final double impactThreshold;
    final double impactRestitution;
    final double walkShallow;
    final double walkDeep;
    final double anchorTugShallow;
    final double anchorTugDeep;
    final double anchorFollowShallow;
    final double anchorFollowDeep;
    final double struggleMin;
    final double struggleMax;
    final double struggleDeepMultiplier;
    final double struggleExistingUpwardRetention;
    final int struggleLiftTicksMin;
    final int struggleLiftTicksMax;

    private LivingSlimePhysicsProfile(double minColumnDepth, double columnMargin, double elasticDepthHeightFactor,
            double elasticDepthColumnFactor, double verticalTug, double verticalRetention, double baseSinkBias,
            double movementSinkScale, double crouchSink, double maxDownSpeed, double maxUpSpeed,
            double impactThreshold, double impactRestitution, double walkShallow, double walkDeep,
            double anchorTugShallow, double anchorTugDeep, double anchorFollowShallow, double anchorFollowDeep,
            double struggleMin, double struggleMax, double struggleDeepMultiplier,
            double struggleExistingUpwardRetention, int struggleLiftTicksMin, int struggleLiftTicksMax) {
        this.minColumnDepth = minColumnDepth;
        this.columnMargin = columnMargin;
        this.elasticDepthHeightFactor = elasticDepthHeightFactor;
        this.elasticDepthColumnFactor = elasticDepthColumnFactor;
        this.verticalTug = verticalTug;
        this.verticalRetention = verticalRetention;
        this.baseSinkBias = baseSinkBias;
        this.movementSinkScale = movementSinkScale;
        this.crouchSink = crouchSink;
        this.maxDownSpeed = maxDownSpeed;
        this.maxUpSpeed = maxUpSpeed;
        this.impactThreshold = impactThreshold;
        this.impactRestitution = impactRestitution;
        this.walkShallow = walkShallow;
        this.walkDeep = walkDeep;
        this.anchorTugShallow = anchorTugShallow;
        this.anchorTugDeep = anchorTugDeep;
        this.anchorFollowShallow = anchorFollowShallow;
        this.anchorFollowDeep = anchorFollowDeep;
        this.struggleMin = struggleMin;
        this.struggleMax = struggleMax;
        this.struggleDeepMultiplier = struggleDeepMultiplier;
        this.struggleExistingUpwardRetention = struggleExistingUpwardRetention;
        this.struggleLiftTicksMin = struggleLiftTicksMin;
        this.struggleLiftTicksMax = struggleLiftTicksMax;
    }

    static LivingSlimePhysicsProfile fromValues(double[] values) {
        return new LivingSlimePhysicsProfile(
                value(values, MudPhysicsParameter.SLIME_MIN_COLUMN_DEPTH),
                value(values, MudPhysicsParameter.SLIME_COLUMN_MARGIN),
                value(values, MudPhysicsParameter.SLIME_REST_HEIGHT_FACTOR),
                value(values, MudPhysicsParameter.SLIME_REST_COLUMN_FACTOR),
                value(values, MudPhysicsParameter.SLIME_VERTICAL_SPRING),
                value(values, MudPhysicsParameter.SLIME_VERTICAL_DAMPING),
                value(values, MudPhysicsParameter.SLIME_BASE_SINK_BIAS),
                value(values, MudPhysicsParameter.SLIME_MOVEMENT_SINK_SCALE),
                value(values, MudPhysicsParameter.SLIME_CROUCH_SINK),
                value(values, MudPhysicsParameter.SLIME_MAX_DOWN_SPEED),
                value(values, MudPhysicsParameter.SLIME_MAX_UP_SPEED),
                value(values, MudPhysicsParameter.SLIME_IMPACT_THRESHOLD),
                value(values, MudPhysicsParameter.SLIME_IMPACT_RESTITUTION),
                value(values, MudPhysicsParameter.SLIME_WALK_SHALLOW),
                value(values, MudPhysicsParameter.SLIME_WALK_DEEP),
                value(values, MudPhysicsParameter.SLIME_ANCHOR_TUG_SHALLOW),
                value(values, MudPhysicsParameter.SLIME_ANCHOR_TUG_DEEP),
                value(values, MudPhysicsParameter.SLIME_ANCHOR_FOLLOW_SHALLOW),
                value(values, MudPhysicsParameter.SLIME_ANCHOR_FOLLOW_DEEP),
                value(values, MudPhysicsParameter.SLIME_STRUGGLE_MIN),
                value(values, MudPhysicsParameter.SLIME_STRUGGLE_MAX),
                value(values, MudPhysicsParameter.SLIME_STRUGGLE_DEEP_MULTIPLIER),
                value(values, MudPhysicsParameter.SLIME_STRUGGLE_EXISTING_UPWARD_RETENTION),
                integerValue(values, MudPhysicsParameter.SLIME_STRUGGLE_LIFT_TICKS_MIN),
                integerValue(values, MudPhysicsParameter.SLIME_STRUGGLE_LIFT_TICKS_MAX));
    }

    void writeTo(double[] values) {
        put(values, MudPhysicsParameter.SLIME_MIN_COLUMN_DEPTH, minColumnDepth);
        put(values, MudPhysicsParameter.SLIME_COLUMN_MARGIN, columnMargin);
        put(values, MudPhysicsParameter.SLIME_REST_HEIGHT_FACTOR, elasticDepthHeightFactor);
        put(values, MudPhysicsParameter.SLIME_REST_COLUMN_FACTOR, elasticDepthColumnFactor);
        put(values, MudPhysicsParameter.SLIME_VERTICAL_SPRING, verticalTug);
        put(values, MudPhysicsParameter.SLIME_VERTICAL_DAMPING, verticalRetention);
        put(values, MudPhysicsParameter.SLIME_BASE_SINK_BIAS, baseSinkBias);
        put(values, MudPhysicsParameter.SLIME_MOVEMENT_SINK_SCALE, movementSinkScale);
        put(values, MudPhysicsParameter.SLIME_CROUCH_SINK, crouchSink);
        put(values, MudPhysicsParameter.SLIME_MAX_DOWN_SPEED, maxDownSpeed);
        put(values, MudPhysicsParameter.SLIME_MAX_UP_SPEED, maxUpSpeed);
        put(values, MudPhysicsParameter.SLIME_IMPACT_THRESHOLD, impactThreshold);
        put(values, MudPhysicsParameter.SLIME_IMPACT_RESTITUTION, impactRestitution);
        put(values, MudPhysicsParameter.SLIME_WALK_SHALLOW, walkShallow);
        put(values, MudPhysicsParameter.SLIME_WALK_DEEP, walkDeep);
        put(values, MudPhysicsParameter.SLIME_ANCHOR_TUG_SHALLOW, anchorTugShallow);
        put(values, MudPhysicsParameter.SLIME_ANCHOR_TUG_DEEP, anchorTugDeep);
        put(values, MudPhysicsParameter.SLIME_ANCHOR_FOLLOW_SHALLOW, anchorFollowShallow);
        put(values, MudPhysicsParameter.SLIME_ANCHOR_FOLLOW_DEEP, anchorFollowDeep);
        put(values, MudPhysicsParameter.SLIME_STRUGGLE_MIN, struggleMin);
        put(values, MudPhysicsParameter.SLIME_STRUGGLE_MAX, struggleMax);
        put(values, MudPhysicsParameter.SLIME_STRUGGLE_DEEP_MULTIPLIER, struggleDeepMultiplier);
        put(values, MudPhysicsParameter.SLIME_STRUGGLE_EXISTING_UPWARD_RETENTION, struggleExistingUpwardRetention);
        put(values, MudPhysicsParameter.SLIME_STRUGGLE_LIFT_TICKS_MIN, struggleLiftTicksMin);
        put(values, MudPhysicsParameter.SLIME_STRUGGLE_LIFT_TICKS_MAX, struggleLiftTicksMax);
    }

    private static double value(double[] values, MudPhysicsParameter parameter) {
        return parameter.sanitize(values[parameter.ordinal()]);
    }

    private static int integerValue(double[] values, MudPhysicsParameter parameter) {
        return (int) Math.round(value(values, parameter));
    }

    private static void put(double[] values, MudPhysicsParameter parameter, double value) {
        values[parameter.ordinal()] = value;
    }
}

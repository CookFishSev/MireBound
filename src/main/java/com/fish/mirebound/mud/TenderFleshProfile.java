package com.fish.mirebound.mud;

/** Cached tuning for the contractile tissue layer applied above ordinary rheology. */
public record TenderFleshProfile(
        int pulsePeriodTicks,
        double contractionStrength,
        double wrapGain,
        double wrapDecay,
        double activityThreshold,
        double wrapMovementResistance,
        double contractionSinkSpeed,
        double escapeRelaxationThreshold,
        double escapeBadMultiplier,
        double escapeGoodMultiplier,
        double surfacePulseStrength,
        double soundVolume,
        double releaseOpportunityThreshold,
        double goodReleaseWrapLoss,
        double badReleaseWrapGain,
        double escapePulseSpeed,
        int escapePulseTicks,
        double escapePulseDamping,
        double pressureGain,
        double pressureDecay,
        double pressureResistance,
        double pressureSinkSpeed,
        double foldHeightPixels,
        double foldWidthPixels,
        int foldCount,
        double tentacleLengthPixels,
        double tentacleSwayPixels,
        int tentacleSegments,
        double tentacleHeightScale,
        double enclosureWalkScaleThreshold,
        double enclosureRiseRate,
        double enclosureWithdrawRate,
        int enclosureMinLayers,
        int enclosureMinPoolWidth,
        double membraneOpacity,
        double enclosureCollisionStart,
        double enclosureOpenRadius,
        double enclosureClosedRadius,
        double enclosureHeightMarginPixels,
        double enclosureMaxHeightPixels,
        int enclosureCooldownTicks,
        int enclosureStrikeCooldownTicks,
        double struggleSinkSuppression,
        boolean membraneOpaque,
        double enclosureMinHeightPixels,
        double enclosureForcedReleaseDistance,
        int enclosureMinPillarHits,
        int enclosureMaxPillarHits) {

    public static final TenderFleshProfile DEFAULT = new TenderFleshProfile(
            72,
            0.72D,
            0.026D,
            0.010D,
            0.025D,
            0.78D,
            0.010D,
            0.72D,
            0.38D,
            1.42D,
            0.38D,
            0.28D,
            0.42D,
            0.34D,
            0.10D,
            0.26D,
            6,
            0.82D,
            0.020D,
            0.008D,
            0.62D,
            0.014D,
            1.50D,
            1.25D,
            4,
            5.0D,
            1.50D,
            3,
            1.0D,
            0.08D,
            0.018D,
            0.025D,
            2,
            2,
            0.48D,
            0.35D,
            0.42D,
            0.07D,
            5.0D,
            36.0D,
            600,
            4,
            0.70D,
            true,
            18.0D,
            0.85D,
            3,
            6);

    public static TenderFleshProfile fromValues(double[] values) {
        return new TenderFleshProfile(
                integer(values, MudPhysicsParameter.FLESH_PULSE_PERIOD_TICKS),
                value(values, MudPhysicsParameter.FLESH_CONTRACTION_STRENGTH),
                value(values, MudPhysicsParameter.FLESH_WRAP_GAIN),
                value(values, MudPhysicsParameter.FLESH_WRAP_DECAY),
                value(values, MudPhysicsParameter.FLESH_ACTIVITY_THRESHOLD),
                value(values, MudPhysicsParameter.FLESH_WRAP_MOVEMENT_RESISTANCE),
                value(values, MudPhysicsParameter.FLESH_CONTRACTION_SINK_SPEED),
                value(values, MudPhysicsParameter.FLESH_ESCAPE_RELAXATION_THRESHOLD),
                value(values, MudPhysicsParameter.FLESH_ESCAPE_BAD_MULTIPLIER),
                value(values, MudPhysicsParameter.FLESH_ESCAPE_GOOD_MULTIPLIER),
                value(values, MudPhysicsParameter.FLESH_SURFACE_PULSE_STRENGTH),
                value(values, MudPhysicsParameter.FLESH_SOUND_VOLUME),
                value(values, MudPhysicsParameter.FLESH_RELEASE_OPPORTUNITY_THRESHOLD),
                value(values, MudPhysicsParameter.FLESH_GOOD_RELEASE_WRAP_LOSS),
                value(values, MudPhysicsParameter.FLESH_BAD_RELEASE_WRAP_GAIN),
                value(values, MudPhysicsParameter.FLESH_ESCAPE_PULSE_SPEED),
                integer(values, MudPhysicsParameter.FLESH_ESCAPE_PULSE_TICKS),
                value(values, MudPhysicsParameter.FLESH_ESCAPE_PULSE_DAMPING),
                value(values, MudPhysicsParameter.FLESH_PRESSURE_GAIN),
                value(values, MudPhysicsParameter.FLESH_PRESSURE_DECAY),
                value(values, MudPhysicsParameter.FLESH_PRESSURE_RESISTANCE),
                value(values, MudPhysicsParameter.FLESH_PRESSURE_SINK_SPEED),
                value(values, MudPhysicsParameter.FLESH_FOLD_HEIGHT_PIXELS),
                value(values, MudPhysicsParameter.FLESH_FOLD_WIDTH_PIXELS),
                integer(values, MudPhysicsParameter.FLESH_FOLD_COUNT),
                value(values, MudPhysicsParameter.FLESH_TENTACLE_LENGTH_PIXELS),
                value(values, MudPhysicsParameter.FLESH_TENTACLE_SWAY_PIXELS),
                integer(values, MudPhysicsParameter.FLESH_TENTACLE_SEGMENTS),
                value(values, MudPhysicsParameter.FLESH_TENTACLE_HEIGHT_SCALE),
                value(values, MudPhysicsParameter.FLESH_ENCLOSURE_WALK_SCALE_THRESHOLD),
                value(values, MudPhysicsParameter.FLESH_ENCLOSURE_RISE_RATE),
                value(values, MudPhysicsParameter.FLESH_ENCLOSURE_WITHDRAW_RATE),
                integer(values, MudPhysicsParameter.FLESH_ENCLOSURE_MIN_LAYERS),
                integer(values, MudPhysicsParameter.FLESH_ENCLOSURE_MIN_POOL_WIDTH),
                value(values, MudPhysicsParameter.FLESH_MEMBRANE_OPACITY),
                value(values, MudPhysicsParameter.FLESH_ENCLOSURE_COLLISION_START),
                value(values, MudPhysicsParameter.FLESH_ENCLOSURE_OPEN_RADIUS),
                value(values, MudPhysicsParameter.FLESH_ENCLOSURE_CLOSED_RADIUS),
                value(values, MudPhysicsParameter.FLESH_ENCLOSURE_HEIGHT_MARGIN_PIXELS),
                value(values, MudPhysicsParameter.FLESH_ENCLOSURE_MAX_HEIGHT_PIXELS),
                integer(values, MudPhysicsParameter.FLESH_ENCLOSURE_COOLDOWN_TICKS),
                integer(values, MudPhysicsParameter.FLESH_ENCLOSURE_STRIKE_COOLDOWN_TICKS),
                value(values, MudPhysicsParameter.FLESH_STRUGGLE_SINK_SUPPRESSION),
                value(values, MudPhysicsParameter.FLESH_MEMBRANE_OPAQUE) >= 0.5D,
                value(values, MudPhysicsParameter.FLESH_ENCLOSURE_MIN_HEIGHT_PIXELS),
                value(values, MudPhysicsParameter.FLESH_ENCLOSURE_FORCED_RELEASE_DISTANCE),
                integer(values, MudPhysicsParameter.FLESH_ENCLOSURE_MIN_PILLAR_HITS),
                integer(values, MudPhysicsParameter.FLESH_ENCLOSURE_MAX_PILLAR_HITS));
    }

    public void writeTo(double[] values) {
        put(values, MudPhysicsParameter.FLESH_PULSE_PERIOD_TICKS, pulsePeriodTicks);
        put(values, MudPhysicsParameter.FLESH_CONTRACTION_STRENGTH, contractionStrength);
        put(values, MudPhysicsParameter.FLESH_WRAP_GAIN, wrapGain);
        put(values, MudPhysicsParameter.FLESH_WRAP_DECAY, wrapDecay);
        put(values, MudPhysicsParameter.FLESH_ACTIVITY_THRESHOLD, activityThreshold);
        put(values, MudPhysicsParameter.FLESH_WRAP_MOVEMENT_RESISTANCE, wrapMovementResistance);
        put(values, MudPhysicsParameter.FLESH_CONTRACTION_SINK_SPEED, contractionSinkSpeed);
        put(values, MudPhysicsParameter.FLESH_ESCAPE_RELAXATION_THRESHOLD, escapeRelaxationThreshold);
        put(values, MudPhysicsParameter.FLESH_ESCAPE_BAD_MULTIPLIER, escapeBadMultiplier);
        put(values, MudPhysicsParameter.FLESH_ESCAPE_GOOD_MULTIPLIER, escapeGoodMultiplier);
        put(values, MudPhysicsParameter.FLESH_SURFACE_PULSE_STRENGTH, surfacePulseStrength);
        put(values, MudPhysicsParameter.FLESH_SOUND_VOLUME, soundVolume);
        put(values, MudPhysicsParameter.FLESH_RELEASE_OPPORTUNITY_THRESHOLD,
                releaseOpportunityThreshold);
        put(values, MudPhysicsParameter.FLESH_GOOD_RELEASE_WRAP_LOSS, goodReleaseWrapLoss);
        put(values, MudPhysicsParameter.FLESH_BAD_RELEASE_WRAP_GAIN, badReleaseWrapGain);
        put(values, MudPhysicsParameter.FLESH_ESCAPE_PULSE_SPEED, escapePulseSpeed);
        put(values, MudPhysicsParameter.FLESH_ESCAPE_PULSE_TICKS, escapePulseTicks);
        put(values, MudPhysicsParameter.FLESH_ESCAPE_PULSE_DAMPING, escapePulseDamping);
        put(values, MudPhysicsParameter.FLESH_PRESSURE_GAIN, pressureGain);
        put(values, MudPhysicsParameter.FLESH_PRESSURE_DECAY, pressureDecay);
        put(values, MudPhysicsParameter.FLESH_PRESSURE_RESISTANCE, pressureResistance);
        put(values, MudPhysicsParameter.FLESH_PRESSURE_SINK_SPEED, pressureSinkSpeed);
        put(values, MudPhysicsParameter.FLESH_FOLD_HEIGHT_PIXELS, foldHeightPixels);
        put(values, MudPhysicsParameter.FLESH_FOLD_WIDTH_PIXELS, foldWidthPixels);
        put(values, MudPhysicsParameter.FLESH_FOLD_COUNT, foldCount);
        put(values, MudPhysicsParameter.FLESH_TENTACLE_LENGTH_PIXELS, tentacleLengthPixels);
        put(values, MudPhysicsParameter.FLESH_TENTACLE_SWAY_PIXELS, tentacleSwayPixels);
        put(values, MudPhysicsParameter.FLESH_TENTACLE_SEGMENTS, tentacleSegments);
        put(values, MudPhysicsParameter.FLESH_TENTACLE_HEIGHT_SCALE, tentacleHeightScale);
        put(values, MudPhysicsParameter.FLESH_ENCLOSURE_WALK_SCALE_THRESHOLD,
                enclosureWalkScaleThreshold);
        put(values, MudPhysicsParameter.FLESH_ENCLOSURE_RISE_RATE, enclosureRiseRate);
        put(values, MudPhysicsParameter.FLESH_ENCLOSURE_WITHDRAW_RATE, enclosureWithdrawRate);
        put(values, MudPhysicsParameter.FLESH_ENCLOSURE_MIN_LAYERS, enclosureMinLayers);
        put(values, MudPhysicsParameter.FLESH_ENCLOSURE_MIN_POOL_WIDTH, enclosureMinPoolWidth);
        put(values, MudPhysicsParameter.FLESH_MEMBRANE_OPACITY, membraneOpacity);
        put(values, MudPhysicsParameter.FLESH_ENCLOSURE_COLLISION_START,
                enclosureCollisionStart);
        put(values, MudPhysicsParameter.FLESH_ENCLOSURE_OPEN_RADIUS, enclosureOpenRadius);
        put(values, MudPhysicsParameter.FLESH_ENCLOSURE_CLOSED_RADIUS, enclosureClosedRadius);
        put(values, MudPhysicsParameter.FLESH_ENCLOSURE_HEIGHT_MARGIN_PIXELS,
                enclosureHeightMarginPixels);
        put(values, MudPhysicsParameter.FLESH_ENCLOSURE_MAX_HEIGHT_PIXELS,
                enclosureMaxHeightPixels);
        put(values, MudPhysicsParameter.FLESH_ENCLOSURE_COOLDOWN_TICKS,
                enclosureCooldownTicks);
        put(values, MudPhysicsParameter.FLESH_ENCLOSURE_STRIKE_COOLDOWN_TICKS,
                enclosureStrikeCooldownTicks);
        put(values, MudPhysicsParameter.FLESH_STRUGGLE_SINK_SUPPRESSION,
                struggleSinkSuppression);
        put(values, MudPhysicsParameter.FLESH_MEMBRANE_OPAQUE,
                membraneOpaque ? 1.0D : 0.0D);
        put(values, MudPhysicsParameter.FLESH_ENCLOSURE_MIN_HEIGHT_PIXELS,
                enclosureMinHeightPixels);
        put(values, MudPhysicsParameter.FLESH_ENCLOSURE_FORCED_RELEASE_DISTANCE,
                enclosureForcedReleaseDistance);
        put(values, MudPhysicsParameter.FLESH_ENCLOSURE_MIN_PILLAR_HITS,
                enclosureMinPillarHits);
        put(values, MudPhysicsParameter.FLESH_ENCLOSURE_MAX_PILLAR_HITS,
                enclosureMaxPillarHits);
    }

    private static double value(double[] values, MudPhysicsParameter parameter) {
        return parameter.sanitize(values[parameter.ordinal()]);
    }

    private static int integer(double[] values, MudPhysicsParameter parameter) {
        return Math.max(1, (int) Math.round(value(values, parameter)));
    }

    private static void put(double[] values, MudPhysicsParameter parameter, double value) {
        values[parameter.ordinal()] = parameter.sanitize(value);
    }
}

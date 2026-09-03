package com.fish.mirebound.mud;

/** Configurable surface-to-body strand template shared by adhesive media. */
public record AdhesionStrandProfile(
        boolean enabled,
        int minimumCount,
        int maximumCount,
        int initialCount,
        double minimumCoverage,
        double widthPixels,
        double breakLength,
        double curve,
        int retractTicks,
        double inertia,
        double response,
        double neckScale,
        double endWidthScale,
        double spawnHeight,
        boolean sheetEnabled,
        int sheetMinimumRibs,
        double sheetMaximumSpan,
        double sheetFingeringStart,
        double sheetIrregularity,
        double ringRadius,
        double ringVariation,
        double bodySlideSpeed,
        int ringRefreshTicks,
        boolean geometricAnchors,
        double ringClearance,
        double ringDriftAmount,
        double ringDriftSpeed,
        double bodyAnchorLift,
        int attachDelayTicks,
        int attachGrowTicks,
        int spawnIntervalTicks,
        int anchorGraceTicks,
        int breakConfirmTicks,
        int anchorSearchPixels) {

    private static final AdhesionStrandProfile TAR_TEMPLATE = new AdhesionStrandProfile(
            true, 10, 16, 4, 0.15D, 1.55D, 2.00D, 0.30D, 18,
            0.76D, 0.30D, 0.34D, 1.45D, 1.45D,
            true, 6, 1.20D, 0.58D, 0.46D,
            0.70D, 0.18D, 0.006D, 2, true,
            0.18D, 0.10D, 0.025D, 0.14D,
            8, 12, 2, 24, 10, 2);

    private static final AdhesionStrandProfile LEGACY_TAR_DEFAULTS =
            new AdhesionStrandProfile(
                    true, 10, 16, 4, 0.15D, 1.55D, 2.00D, 0.30D, 18,
                    0.76D, 0.30D, 0.34D, 1.45D, 1.88D,
                    true, 8, 1.20D, 0.58D, 0.46D,
                    0.70D, 0.18D, 0.006D, 2, true,
                    0.18D, 0.10D, 0.025D, 0.14D,
                    8, 12, 2, 24, 10, 2);

    private static final AdhesionStrandProfile LEGACY_GENERIC_DEFAULTS =
            new AdhesionStrandProfile(
                    false, 0, 0, 1, 0.15D, 0.75D, 1.0D, 0.25D, 10,
                    0.68D, 0.34D, 0.42D, 1.25D, 0.45D,
                    false, 3, 0.72D, 0.55D, 0.35D,
                    0.70D, 0.12D, 0.004D, 2, false,
                    0.14D, 0.0D, 0.0D, 0.08D,
                    6, 8, 3, 12, 4, 1);

    private static final AdhesionStrandProfile TENDER_FLESH_DEFAULTS =
            new AdhesionStrandProfile(
                    true, 5, 7, 3, 0.08D, 2.20D, 2.15D, 0.20D, 22,
                    0.74D, 0.28D, 0.56D, 1.28D, 1.20D,
                    true, 5, 1.08D, 0.70D, 0.30D,
                    0.70D, 0.12D, 0.004D, 2, true,
                    0.14D, 0.06D, 0.020D, 0.10D,
                    5, 8, 2, 16, 4, 1);

    public static AdhesionStrandProfile defaultsFor(SinkingMedium medium) {
        return switch (medium) {
            case TAR -> TAR_TEMPLATE;
            case TENDER_FLESH -> TENDER_FLESH_DEFAULTS;
            default -> withFeatureSwitches(TAR_TEMPLATE, false, false);
        };
    }

    static AdhesionStrandProfile defaultsBeforeSharedTarTemplate(SinkingMedium medium) {
        return switch (medium) {
            case TAR -> LEGACY_TAR_DEFAULTS;
            case TENDER_FLESH -> TENDER_FLESH_DEFAULTS;
            default -> LEGACY_GENERIC_DEFAULTS;
        };
    }

    static boolean isFeatureSwitch(MudPhysicsParameter parameter) {
        return parameter == MudPhysicsParameter.ADHESION_STRANDS_ENABLED
                || parameter == MudPhysicsParameter.ADHESION_SHEET_ENABLED;
    }

    private static AdhesionStrandProfile withFeatureSwitches(
            AdhesionStrandProfile source, boolean strandsEnabled, boolean sheetEnabled) {
        return new AdhesionStrandProfile(
                strandsEnabled,
                source.minimumCount,
                source.maximumCount,
                source.initialCount,
                source.minimumCoverage,
                source.widthPixels,
                source.breakLength,
                source.curve,
                source.retractTicks,
                source.inertia,
                source.response,
                source.neckScale,
                source.endWidthScale,
                source.spawnHeight,
                sheetEnabled,
                source.sheetMinimumRibs,
                source.sheetMaximumSpan,
                source.sheetFingeringStart,
                source.sheetIrregularity,
                source.ringRadius,
                source.ringVariation,
                source.bodySlideSpeed,
                source.ringRefreshTicks,
                source.geometricAnchors,
                source.ringClearance,
                source.ringDriftAmount,
                source.ringDriftSpeed,
                source.bodyAnchorLift,
                source.attachDelayTicks,
                source.attachGrowTicks,
                source.spawnIntervalTicks,
                source.anchorGraceTicks,
                source.breakConfirmTicks,
                source.anchorSearchPixels);
    }

    static AdhesionStrandProfile fromValues(double[] values) {
        int minimum = integer(values, MudPhysicsParameter.ADHESION_STRAND_MIN_COUNT);
        int maximum = Math.max(minimum, integer(values, MudPhysicsParameter.ADHESION_STRAND_MAX_COUNT));
        return new AdhesionStrandProfile(
                value(values, MudPhysicsParameter.ADHESION_STRANDS_ENABLED) >= 0.5D,
                minimum,
                maximum,
                integer(values, MudPhysicsParameter.ADHESION_INITIAL_COUNT),
                value(values, MudPhysicsParameter.ADHESION_STRAND_MIN_COVERAGE),
                value(values, MudPhysicsParameter.ADHESION_STRAND_WIDTH_PIXELS),
                value(values, MudPhysicsParameter.ADHESION_STRAND_BREAK_LENGTH),
                value(values, MudPhysicsParameter.ADHESION_STRAND_CURVE),
                integer(values, MudPhysicsParameter.ADHESION_STRAND_RETRACT_TICKS),
                value(values, MudPhysicsParameter.ADHESION_STRAND_INERTIA),
                value(values, MudPhysicsParameter.ADHESION_STRAND_RESPONSE),
                value(values, MudPhysicsParameter.ADHESION_STRAND_NECK_SCALE),
                value(values, MudPhysicsParameter.ADHESION_STRAND_END_WIDTH_SCALE),
                value(values, MudPhysicsParameter.ADHESION_STRAND_SPAWN_HEIGHT),
                value(values, MudPhysicsParameter.ADHESION_SHEET_ENABLED) >= 0.5D,
                integer(values, MudPhysicsParameter.ADHESION_SHEET_MIN_RIBS),
                value(values, MudPhysicsParameter.ADHESION_SHEET_MAX_SPAN),
                value(values, MudPhysicsParameter.ADHESION_SHEET_FINGERING_START),
                value(values, MudPhysicsParameter.ADHESION_SHEET_IRREGULARITY),
                value(values, MudPhysicsParameter.ADHESION_RING_RADIUS),
                value(values, MudPhysicsParameter.ADHESION_RING_VARIATION),
                value(values, MudPhysicsParameter.ADHESION_BODY_SLIDE_SPEED),
                integer(values, MudPhysicsParameter.ADHESION_RING_REFRESH_TICKS),
                value(values, MudPhysicsParameter.ADHESION_GEOMETRIC_ANCHORS) >= 0.5D,
                value(values, MudPhysicsParameter.ADHESION_RING_CLEARANCE),
                value(values, MudPhysicsParameter.ADHESION_RING_DRIFT_AMOUNT),
                value(values, MudPhysicsParameter.ADHESION_RING_DRIFT_SPEED),
                value(values, MudPhysicsParameter.ADHESION_BODY_ANCHOR_LIFT),
                integer(values, MudPhysicsParameter.ADHESION_ATTACH_DELAY_TICKS),
                integer(values, MudPhysicsParameter.ADHESION_ATTACH_GROW_TICKS),
                integer(values, MudPhysicsParameter.ADHESION_SPAWN_INTERVAL_TICKS),
                integer(values, MudPhysicsParameter.ADHESION_ANCHOR_GRACE_TICKS),
                integer(values, MudPhysicsParameter.ADHESION_BREAK_CONFIRM_TICKS),
                integer(values, MudPhysicsParameter.ADHESION_ANCHOR_SEARCH_PIXELS));
    }

    void writeTo(double[] values) {
        put(values, MudPhysicsParameter.ADHESION_STRANDS_ENABLED, enabled ? 1.0D : 0.0D);
        put(values, MudPhysicsParameter.ADHESION_STRAND_MIN_COUNT, minimumCount);
        put(values, MudPhysicsParameter.ADHESION_STRAND_MAX_COUNT, maximumCount);
        put(values, MudPhysicsParameter.ADHESION_INITIAL_COUNT, initialCount);
        put(values, MudPhysicsParameter.ADHESION_STRAND_MIN_COVERAGE, minimumCoverage);
        put(values, MudPhysicsParameter.ADHESION_STRAND_WIDTH_PIXELS, widthPixels);
        put(values, MudPhysicsParameter.ADHESION_STRAND_BREAK_LENGTH, breakLength);
        put(values, MudPhysicsParameter.ADHESION_STRAND_CURVE, curve);
        put(values, MudPhysicsParameter.ADHESION_STRAND_RETRACT_TICKS, retractTicks);
        put(values, MudPhysicsParameter.ADHESION_STRAND_INERTIA, inertia);
        put(values, MudPhysicsParameter.ADHESION_STRAND_RESPONSE, response);
        put(values, MudPhysicsParameter.ADHESION_STRAND_NECK_SCALE, neckScale);
        put(values, MudPhysicsParameter.ADHESION_STRAND_END_WIDTH_SCALE, endWidthScale);
        put(values, MudPhysicsParameter.ADHESION_STRAND_SPAWN_HEIGHT, spawnHeight);
        put(values, MudPhysicsParameter.ADHESION_SHEET_ENABLED, sheetEnabled ? 1.0D : 0.0D);
        put(values, MudPhysicsParameter.ADHESION_SHEET_MIN_RIBS, sheetMinimumRibs);
        put(values, MudPhysicsParameter.ADHESION_SHEET_MAX_SPAN, sheetMaximumSpan);
        put(values, MudPhysicsParameter.ADHESION_SHEET_FINGERING_START, sheetFingeringStart);
        put(values, MudPhysicsParameter.ADHESION_SHEET_IRREGULARITY, sheetIrregularity);
        put(values, MudPhysicsParameter.ADHESION_RING_RADIUS, ringRadius);
        put(values, MudPhysicsParameter.ADHESION_RING_VARIATION, ringVariation);
        put(values, MudPhysicsParameter.ADHESION_BODY_SLIDE_SPEED, bodySlideSpeed);
        put(values, MudPhysicsParameter.ADHESION_RING_REFRESH_TICKS, ringRefreshTicks);
        put(values, MudPhysicsParameter.ADHESION_GEOMETRIC_ANCHORS, geometricAnchors ? 1.0D : 0.0D);
        put(values, MudPhysicsParameter.ADHESION_RING_CLEARANCE, ringClearance);
        put(values, MudPhysicsParameter.ADHESION_RING_DRIFT_AMOUNT, ringDriftAmount);
        put(values, MudPhysicsParameter.ADHESION_RING_DRIFT_SPEED, ringDriftSpeed);
        put(values, MudPhysicsParameter.ADHESION_BODY_ANCHOR_LIFT, bodyAnchorLift);
        put(values, MudPhysicsParameter.ADHESION_ATTACH_DELAY_TICKS, attachDelayTicks);
        put(values, MudPhysicsParameter.ADHESION_ATTACH_GROW_TICKS, attachGrowTicks);
        put(values, MudPhysicsParameter.ADHESION_SPAWN_INTERVAL_TICKS, spawnIntervalTicks);
        put(values, MudPhysicsParameter.ADHESION_ANCHOR_GRACE_TICKS, anchorGraceTicks);
        put(values, MudPhysicsParameter.ADHESION_BREAK_CONFIRM_TICKS, breakConfirmTicks);
        put(values, MudPhysicsParameter.ADHESION_ANCHOR_SEARCH_PIXELS, anchorSearchPixels);
    }

    private static double value(double[] values, MudPhysicsParameter parameter) {
        return values[parameter.ordinal()];
    }

    private static int integer(double[] values, MudPhysicsParameter parameter) {
        return (int) Math.round(value(values, parameter));
    }

    private static void put(double[] values, MudPhysicsParameter parameter, double value) {
        values[parameter.ordinal()] = value;
    }
}

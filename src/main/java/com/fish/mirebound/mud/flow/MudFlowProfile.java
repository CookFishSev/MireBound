package com.fish.mirebound.mud.flow;

import com.fish.mirebound.mud.MudPhysicsParameter;
import net.minecraft.util.Mth;

/** Immutable hot-path settings for one finite-volume mud flow. */
public record MudFlowProfile(
        boolean enabled,
        int intervalTicks,
        int pixelsPerTransfer,
        int horizontalMinimumPixels,
        int horizontalLevelDifference,
        int maximumSpreadDistance,
        int maximumUpdatesPerTick) {
    public static final MudFlowProfile DEFAULT = new MudFlowProfile(
            false, 4, 2, 3, 2, 12, 128);

    public MudFlowProfile {
        intervalTicks = Mth.clamp(intervalTicks, 1, 40);
        pixelsPerTransfer = Mth.clamp(pixelsPerTransfer, 1, 16);
        horizontalMinimumPixels = Mth.clamp(horizontalMinimumPixels, 1, 16);
        horizontalLevelDifference = Mth.clamp(horizontalLevelDifference, 1, 16);
        maximumSpreadDistance = Mth.clamp(maximumSpreadDistance, 1, 64);
        maximumUpdatesPerTick = Mth.clamp(maximumUpdatesPerTick, 1, 512);
    }

    public static MudFlowProfile fromValues(double[] values) {
        return new MudFlowProfile(
                read(values, MudPhysicsParameter.FLOW_ENABLED) >= 0.5D
                        && read(values, MudPhysicsParameter.GRAVITY_FALLING_ENABLED) < 0.5D,
                rounded(values, MudPhysicsParameter.FLOW_INTERVAL_TICKS),
                rounded(values, MudPhysicsParameter.FLOW_PIXELS_PER_TRANSFER),
                rounded(values, MudPhysicsParameter.FLOW_HORIZONTAL_MINIMUM_PIXELS),
                rounded(values, MudPhysicsParameter.FLOW_HORIZONTAL_LEVEL_DIFFERENCE),
                rounded(values, MudPhysicsParameter.FLOW_MAXIMUM_SPREAD_DISTANCE),
                rounded(values, MudPhysicsParameter.FLOW_MAXIMUM_UPDATES_PER_TICK));
    }

    public void writeTo(double[] values) {
        values[MudPhysicsParameter.FLOW_ENABLED.ordinal()] = enabled ? 1.0D : 0.0D;
        values[MudPhysicsParameter.FLOW_INTERVAL_TICKS.ordinal()] = intervalTicks;
        values[MudPhysicsParameter.FLOW_PIXELS_PER_TRANSFER.ordinal()] = pixelsPerTransfer;
        values[MudPhysicsParameter.FLOW_HORIZONTAL_MINIMUM_PIXELS.ordinal()] = horizontalMinimumPixels;
        values[MudPhysicsParameter.FLOW_HORIZONTAL_LEVEL_DIFFERENCE.ordinal()] = horizontalLevelDifference;
        values[MudPhysicsParameter.FLOW_MAXIMUM_SPREAD_DISTANCE.ordinal()] = maximumSpreadDistance;
        values[MudPhysicsParameter.FLOW_MAXIMUM_UPDATES_PER_TICK.ordinal()] = maximumUpdatesPerTick;
    }

    private static int rounded(double[] values, MudPhysicsParameter parameter) {
        return (int) Math.round(read(values, parameter));
    }

    private static double read(double[] values, MudPhysicsParameter parameter) {
        int index = parameter.ordinal();
        return values != null && index < values.length
                ? values[index]
                : defaultValue(parameter);
    }

    private static double defaultValue(MudPhysicsParameter parameter) {
        return switch (parameter) {
            case FLOW_ENABLED -> DEFAULT.enabled ? 1.0D : 0.0D;
            case FLOW_INTERVAL_TICKS -> DEFAULT.intervalTicks;
            case FLOW_PIXELS_PER_TRANSFER -> DEFAULT.pixelsPerTransfer;
            case FLOW_HORIZONTAL_MINIMUM_PIXELS -> DEFAULT.horizontalMinimumPixels;
            case FLOW_HORIZONTAL_LEVEL_DIFFERENCE -> DEFAULT.horizontalLevelDifference;
            case FLOW_MAXIMUM_SPREAD_DISTANCE -> DEFAULT.maximumSpreadDistance;
            case FLOW_MAXIMUM_UPDATES_PER_TICK -> DEFAULT.maximumUpdatesPerTick;
            default -> parameter.minimum();
        };
    }
}

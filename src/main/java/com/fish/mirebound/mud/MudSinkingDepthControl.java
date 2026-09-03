package com.fish.mirebound.mud;

import net.minecraft.util.Mth;

/** Selects the independent simple or advanced maximum depth configuration. */
public final class MudSinkingDepthControl {
    public static final double MINIMUM = 0.0D;
    public static final double MAXIMUM = 1.0D;
    public static final double STEP = 0.01D;
    public static final int DECIMALS = 2;

    private static final double REFERENCE_STANDING_HEIGHT = 1.8D;

    private MudSinkingDepthControl() {
    }

    public static double maximumDepth(double depthFactor, double columnMargin) {
        double factorLimit = Mth.clamp(
                depthFactor * REFERENCE_STANDING_HEIGHT, MINIMUM, MAXIMUM);
        double marginLimit = MAXIMUM - Mth.clamp(columnMargin, MINIMUM, MAXIMUM);
        return Math.min(factorLimit, marginLimit);
    }

    public static double clamp(double value) {
        return Mth.clamp(value, MINIMUM, MAXIMUM);
    }

    public static boolean displayEquivalent(double first, double second) {
        double scale = Math.pow(10.0D, DECIMALS);
        return Math.round(first * scale) == Math.round(second * scale);
    }

    public static Mode mode(double value) {
        return value >= 0.5D ? Mode.ADVANCED : Mode.SIMPLE;
    }

    public static Mode mode(double[] values) {
        int index = MudPhysicsParameter.SINKING_DEPTH_CONTROL_MODE.ordinal();
        return values != null && index < values.length ? mode(values[index]) : Mode.SIMPLE;
    }

    public static double simpleMaximumDepth(double[] values) {
        int index = MudPhysicsParameter.SIMPLE_MAXIMUM_SINKING_DEPTH.ordinal();
        if (values != null && index < values.length) {
            return clamp(values[index]);
        }
        return values == null || values.length <= MudPhysicsParameter.COLUMN_MARGIN.ordinal()
                ? MINIMUM
                : maximumDepth(
                        values[MudPhysicsParameter.MAX_DEPTH_FACTOR.ordinal()],
                        values[MudPhysicsParameter.COLUMN_MARGIN.ordinal()]);
    }

    public static double simpleNaturalDepth(double[] values) {
        int index = MudPhysicsParameter.SIMPLE_NATURAL_SINKING_DEPTH.ordinal();
        double maximum = simpleMaximumDepth(values);
        if (values != null && index < values.length) {
            return Math.min(clamp(values[index]), maximum);
        }
        return maximum;
    }

    public static double selectedMaximumDepth(double[] values) {
        if (mode(values) == Mode.SIMPLE) {
            return simpleMaximumDepth(values);
        }
        return values == null || values.length <= MudPhysicsParameter.COLUMN_MARGIN.ordinal()
                ? MINIMUM
                : maximumDepth(
                        values[MudPhysicsParameter.MAX_DEPTH_FACTOR.ordinal()],
                        values[MudPhysicsParameter.COLUMN_MARGIN.ordinal()]);
    }

    public static void enforceSimpleBounds(double[] values) {
        if (values == null
                || values.length <= MudPhysicsParameter.SIMPLE_NATURAL_SINKING_DEPTH.ordinal()) {
            return;
        }
        int maximumIndex = MudPhysicsParameter.SIMPLE_MAXIMUM_SINKING_DEPTH.ordinal();
        int naturalIndex = MudPhysicsParameter.SIMPLE_NATURAL_SINKING_DEPTH.ordinal();
        values[maximumIndex] = clamp(values[maximumIndex]);
        values[naturalIndex] = Math.min(clamp(values[naturalIndex]), values[maximumIndex]);
    }

    public enum Mode {
        SIMPLE,
        ADVANCED;

        public double parameterValue() {
            return ordinal();
        }
    }
}

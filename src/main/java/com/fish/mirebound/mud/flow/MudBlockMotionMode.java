package com.fish.mirebound.mud.flow;

import com.fish.mirebound.mud.MudPhysicsParameter;

/** Resolves the two exclusive native block-motion modes. */
public final class MudBlockMotionMode {
    private MudBlockMotionMode() {
    }

    public static void enforceExclusive(double[] values) {
        if (enabled(values, MudPhysicsParameter.FLOW_ENABLED)
                && enabled(values, MudPhysicsParameter.GRAVITY_FALLING_ENABLED)) {
            values[MudPhysicsParameter.GRAVITY_FALLING_ENABLED.ordinal()] = 0.0D;
        }
    }

    public static void enforceExclusive(double[] values, boolean[] changed) {
        boolean gravityChanged = changed(changed,
                MudPhysicsParameter.GRAVITY_FALLING_ENABLED);
        boolean flowChanged = changed(changed, MudPhysicsParameter.FLOW_ENABLED);
        if (gravityChanged
                && enabled(values, MudPhysicsParameter.GRAVITY_FALLING_ENABLED)) {
            values[MudPhysicsParameter.FLOW_ENABLED.ordinal()] = 0.0D;
        } else if (flowChanged && enabled(values, MudPhysicsParameter.FLOW_ENABLED)) {
            values[MudPhysicsParameter.GRAVITY_FALLING_ENABLED.ordinal()] = 0.0D;
        } else {
            enforceExclusive(values);
        }
    }

    public static boolean gravityEnabled(double[] values) {
        return enabled(values, MudPhysicsParameter.GRAVITY_FALLING_ENABLED)
                && !enabled(values, MudPhysicsParameter.FLOW_ENABLED);
    }

    private static boolean changed(boolean[] changed, MudPhysicsParameter parameter) {
        int index = parameter.ordinal();
        return changed != null && index < changed.length && changed[index];
    }

    private static boolean enabled(double[] values, MudPhysicsParameter parameter) {
        int index = parameter.ordinal();
        return values != null && index < values.length && values[index] >= 0.5D;
    }
}

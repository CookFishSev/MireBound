package com.fish.mirebound.mud.behavior;

/** Generic hidden buildup meter with monotonic disturbance gain and calm decay. */
public final class HiddenThreatTemplate {
    private HiddenThreatTemplate() {
    }

    public static boolean step(Settings settings, State state,
            boolean disturbed, double disturbanceStrength, boolean calming) {
        if (disturbed) {
            state.value += settings.hiddenValueGain() * Math.max(0.0D, disturbanceStrength);
        } else if (calming) {
            state.value = Math.max(0.0D, state.value - settings.hiddenValueDecay());
        }
        return state.value >= settings.hiddenTriggerThreshold();
    }

    public interface Settings {
        double hiddenValueGain();

        double hiddenValueDecay();

        double hiddenTriggerThreshold();
    }

    public static final class State {
        private double value;

        public double value() {
            return value;
        }

        public void clear() {
            value = 0.0D;
        }
    }
}

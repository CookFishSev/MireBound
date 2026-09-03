package com.fish.mirebound.mud.behavior;

/** Reusable surface-walking and delayed still-crouch escape state machine. */
public final class QuietCrouchEscapeTemplate {
    private QuietCrouchEscapeTemplate() {
    }

    public static Decision step(Settings settings, State state, double depth,
            boolean crouching, boolean action) {
        if (!state.sunk && depth >= settings.triggerDepth()) {
            state.enter();
        }

        if (!state.sunk && crouching) {
            state.quietTicks = 0;
            return new Decision(Mode.SURFACE_PASS, 0.0D);
        }

        if (state.sunk && crouching && !action) {
            state.quietTicks++;
            double safeDepth = 0.0D;
            double rise = state.quietTicks >= settings.quietCrouchDelayTicks()
                    ? Math.min(settings.quietRiseSpeed(), Math.max(0.0D, depth - safeDepth))
                    : 0.0D;
            if (depth - rise <= safeDepth + 1.0E-4D) {
                state.sunk = false;
                state.escaped = true;
                state.quietTicks = 0;
            }
            return new Decision(rise > 0.0D ? Mode.RISING : Mode.WAITING, rise);
        }

        state.quietTicks = 0;
        return new Decision(Mode.NONE, 0.0D);
    }

    public interface Settings {
        double triggerDepth();

        int quietCrouchDelayTicks();

        double quietRiseSpeed();
    }

    public static final class State {
        private boolean sunk;
        private boolean escaped;
        private int quietTicks;

        public boolean sunk() {
            return sunk;
        }

        public int quietTicks() {
            return quietTicks;
        }

        public boolean escaped() {
            return escaped;
        }

        public void enter() {
            sunk = true;
            escaped = false;
            quietTicks = 0;
        }

        public void reset() {
            sunk = false;
            escaped = false;
            quietTicks = 0;
        }
    }

    public enum Mode {
        NONE,
        SURFACE_PASS,
        WAITING,
        RISING
    }

    public record Decision(Mode mode, double upwardSpeed) {
        public boolean overridesVerticalMotion() {
            return mode != Mode.NONE;
        }

        public boolean calming() {
            return mode == Mode.WAITING || mode == Mode.RISING;
        }
    }
}

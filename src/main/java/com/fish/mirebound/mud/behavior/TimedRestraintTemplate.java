package com.fish.mirebound.mud.behavior;

/** Reusable restraint timer: calm expires it, resistance extends it up to a cap. */
public final class TimedRestraintTemplate {
    private TimedRestraintTemplate() {
    }

    public static void start(Settings settings, State state) {
        state.active = true;
        state.remainingTicks = settings.restraintDurationTicks();
    }

    public static void force(State state, boolean active, int remainingTicks) {
        state.active = active;
        state.remainingTicks = active ? Math.max(1, remainingTicks) : 0;
    }

    public static Result step(Settings settings, State state, boolean resisting) {
        if (!state.active) {
            return new Result(false, false);
        }
        if (resisting) {
            state.remainingTicks = Math.min(settings.restraintMaximumTicks(),
                    state.remainingTicks + settings.restraintExtensionTicks());
        } else {
            state.remainingTicks--;
        }
        if (state.remainingTicks <= 0) {
            state.active = false;
            state.remainingTicks = 0;
            return new Result(false, true);
        }
        return new Result(true, false);
    }

    public interface Settings {
        int restraintDurationTicks();

        int restraintMaximumTicks();

        int restraintExtensionTicks();
    }

    public static final class State {
        private boolean active;
        private int remainingTicks;

        public boolean active() {
            return active;
        }

        public int remainingTicks() {
            return remainingTicks;
        }

        public void reset() {
            active = false;
            remainingTicks = 0;
        }
    }

    public record Result(boolean active, boolean released) {
    }
}

package com.fish.mirebound.mud.behavior;

/** Rate-limits an external sound/game-event emitter without owning world access. */
public final class ResonancePulseTemplate {
    private ResonancePulseTemplate() {
    }

    public static boolean step(Settings settings, State state, boolean disturbed) {
        if (state.cooldown > 0) {
            state.cooldown--;
        }
        if (!disturbed || state.cooldown > 0) {
            return false;
        }
        state.cooldown = settings.resonanceIntervalTicks();
        return true;
    }

    public interface Settings {
        int resonanceIntervalTicks();
    }

    public static final class State {
        private int cooldown;

        public int cooldown() {
            return cooldown;
        }

        public void reset() {
            cooldown = 0;
        }
    }
}

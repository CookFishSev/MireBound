package com.fish.mirebound.content.mudwork;

/** Pure weather transition used by the outdoor adobe drying block. */
public final class WetAdobeDrying {
    public static final int MAXIMUM_DRYNESS = 3;

    private WetAdobeDrying() {
    }

    public static Result update(
            int currentDryness, boolean skyExposed,
            boolean raining, double randomUnit) {
        int dryness = Math.max(0, Math.min(MAXIMUM_DRYNESS, currentDryness));
        double roll = Math.max(0.0D, Math.min(1.0D, randomUnit));
        if (raining) {
            return new Result(roll < 0.72D ? Math.max(0, dryness - 1) : dryness,
                    false);
        }
        if (!skyExposed || roll >= 0.62D) {
            return new Result(dryness, false);
        }
        if (dryness >= MAXIMUM_DRYNESS) {
            return new Result(dryness, true);
        }
        return new Result(dryness + 1, false);
    }

    public record Result(int dryness, boolean complete) {
    }
}

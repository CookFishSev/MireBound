package com.fish.mirebound.client;

/** Pure timing curves shared by the wand cage and selection beam. */
final class MudTuningWandAnimation {
    static final double BEAM_END_TICKS = 13.0D;
    private static final float CAGE_RESPONSE = 0.50F;
    private static final float CAGE_OPEN_THRESHOLD = 0.97F;
    private static final float CAGE_CLOSED_THRESHOLD = 0.005F;
    private static final double BEAM_EXTEND_TICKS = 0.65D;
    private static final double BEAM_FADE_START_TICKS = 8.0D;

    private MudTuningWandAnimation() {
    }

    static float nextCageOpening(float current, boolean opening) {
        float next = lerp(clamp(current), opening ? 1.0F : 0.0F, CAGE_RESPONSE);
        return !opening && next < CAGE_CLOSED_THRESHOLD ? 0.0F : next;
    }

    static boolean cageReachedOpen(float opening) {
        return opening >= CAGE_OPEN_THRESHOLD;
    }

    static float cageOpening(float previous, float current, float partialTick) {
        return lerp(previous, current, clamp(partialTick));
    }

    static boolean beamKeepsWandAimed(boolean hasTarget, double age) {
        return hasTarget && age < BEAM_END_TICKS;
    }

    static float beamExtension(double age) {
        return smootherstep(age / BEAM_EXTEND_TICKS);
    }

    static float beamAlpha(double age) {
        if (age < 0.0D || age >= BEAM_END_TICKS) {
            return 0.0F;
        }
        float appear = smootherstep(age / 0.45D);
        float fade = 1.0F - smootherstep(
                (age - BEAM_FADE_START_TICKS)
                        / (BEAM_END_TICKS - BEAM_FADE_START_TICKS));
        return appear * fade;
    }

    private static float lerp(float from, float to, float amount) {
        return from + (to - from) * amount;
    }

    private static float smootherstep(double value) {
        float clamped = clamp((float) value);
        return clamped * clamped * clamped
                * (clamped * (clamped * 6.0F - 15.0F) + 10.0F);
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}

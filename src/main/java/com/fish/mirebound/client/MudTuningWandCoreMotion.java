package com.fish.mirebound.client;

import net.minecraft.util.Mth;

/** Keeps core rotation phase continuous while selection states change speed. */
final class MudTuningWandCoreMotion {
    private static final double RESPONSE_TICKS = 2.4D;
    private static final double MAX_STEP_TICKS = 2.0D;
    private static double lastTime = Double.NaN;
    private static double rotationDegrees;
    private static double bobPhase;
    private static float awaitingAmount;
    private static float completeAmount;

    private MudTuningWandCoreMotion() {
    }

    static Motion sample(double time, boolean awaitingSecond, boolean completeSelection) {
        if (!Double.isFinite(lastTime) || time < lastTime || time - lastTime > 20.0D) {
            initialize(time, awaitingSecond, completeSelection);
        } else {
            double delta = Math.min(MAX_STEP_TICKS, Math.max(0.0D, time - lastTime));
            float blend = response(delta);
            awaitingAmount = Mth.lerp(blend, awaitingAmount, awaitingSecond ? 1.0F : 0.0F);
            completeAmount = Mth.lerp(blend, completeAmount, completeSelection ? 1.0F : 0.0F);
            double rotationSpeed = 1.8D + awaitingAmount * 4.2D + completeAmount * 1.4D;
            double bobSpeed = 0.12D + awaitingAmount * 0.10D;
            rotationDegrees = Mth.positiveModulo(
                    rotationDegrees + rotationSpeed * delta, 360.0D);
            bobPhase = Mth.positiveModulo(
                    bobPhase + bobSpeed * delta, Math.PI * 2.0D);
            lastTime = time;
        }

        float bobPixels = (float) Math.sin(bobPhase)
                * (0.20F + awaitingAmount * 0.14F);
        float pulse = 1.0F + awaitingAmount * 0.02F
                + completeAmount * (0.03F + 0.025F * (float) Math.sin(time * 0.16D));
        float pitchAmplitude = 14.0F + awaitingAmount * 8.0F;
        float rollAmplitude = 10.0F + awaitingAmount * 7.0F;
        return new Motion(
                (float) rotationDegrees,
                bobPixels,
                pulse,
                (float) Math.sin(time * 0.041D + 1.724D) * pitchAmplitude,
                (float) Math.sin(time * 0.027D + 4.113D) * rollAmplitude);
    }

    static void reset() {
        lastTime = Double.NaN;
        rotationDegrees = 0.0D;
        bobPhase = 0.0D;
        awaitingAmount = 0.0F;
        completeAmount = 0.0F;
    }

    static float response(double deltaTicks) {
        return (float) (1.0D - Math.exp(-Math.max(0.0D, deltaTicks) / RESPONSE_TICKS));
    }

    private static void initialize(double time,
            boolean awaitingSecond, boolean completeSelection) {
        lastTime = time;
        awaitingAmount = awaitingSecond ? 1.0F : 0.0F;
        completeAmount = completeSelection ? 1.0F : 0.0F;
        double rotationSpeed = 1.8D + awaitingAmount * 4.2D + completeAmount * 1.4D;
        double bobSpeed = 0.12D + awaitingAmount * 0.10D;
        rotationDegrees = Mth.positiveModulo(time * rotationSpeed, 360.0D);
        bobPhase = Mth.positiveModulo(time * bobSpeed, Math.PI * 2.0D);
    }

    record Motion(float rotationDegrees, float bobPixels, float pulse,
            float pitchDegrees, float rollDegrees) {
    }
}

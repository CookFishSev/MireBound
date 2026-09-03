package com.fish.mirebound.eruption;

import net.minecraft.util.Mth;

/** Pure size-to-output envelope used by the randomized server burst schedule. */
public final class MudEruptionDynamics {
    private MudEruptionDynamics() {
    }

    public static Burst burst(MudEruptionProfile profile, double radiusPixels,
            double heightRoll, double amountRoll) {
        MudEruptionProfile.SpawnSettings spawning = profile.spawning();
        MudEruptionProfile.SurgeSettings surges = profile.surges();
        double size = normalizedSize(
                radiusPixels, spawning.minimumRadiusPixels(), spawning.maximumRadiusPixels());
        double heightCap = Mth.lerp(0.46D + size * 0.54D,
                surges.minimumHeight(), surges.maximumHeight());
        double height = Mth.lerp(Mth.clamp(heightRoll, 0.0D, 1.0D),
                surges.minimumHeight(), heightCap);
        int dropletCap = Math.max(
                surges.minimumDroplets(),
                (int) Math.round(Mth.lerp(size,
                        surges.minimumDroplets(), surges.maximumDroplets())));
        int droplets = Mth.clamp(
                (int) Math.round(Mth.lerp(Mth.clamp(amountRoll, 0.0D, 1.0D),
                        surges.minimumDroplets(), dropletCap)),
                surges.minimumDroplets(), dropletCap);
        return new Burst(
                height * surges.powerScale(),
                scaledDroplets(droplets, surges.volumeScale()));
    }

    /** Smooth low-volume stream layered underneath the existing random surge. */
    public static Burst continuousBurst(MudEruptionProfile profile, double radiusPixels,
            double flow, double heightJitter, double amountRoll) {
        MudEruptionProfile.SpawnSettings spawning = profile.spawning();
        MudEruptionProfile.ContinuousSettings continuous = profile.continuous();
        double size = normalizedSize(
                radiusPixels, spawning.minimumRadiusPixels(), spawning.maximumRadiusPixels());
        double signal = Mth.clamp(
                Mth.clamp(flow, 0.0D, 1.0D) * 0.86D
                        + Mth.clamp(heightJitter, 0.0D, 1.0D) * 0.14D,
                0.0D, 1.0D);
        double heightCap = Mth.lerp(0.46D + size * 0.54D,
                continuous.minimumHeight(), continuous.maximumHeight());
        double height = Mth.lerp(signal,
                continuous.minimumHeight(), heightCap) * continuous.heightScale();
        int dropletCap = Math.max(
                continuous.minimumDroplets(),
                (int) Math.round(Mth.lerp(size,
                        continuous.minimumDroplets(), continuous.maximumDroplets())));
        double amountSignal = signal * 0.55D
                + Mth.clamp(amountRoll, 0.0D, 1.0D) * 0.45D;
        int droplets = Mth.clamp(
                (int) Math.round(Mth.lerp(amountSignal,
                        continuous.minimumDroplets(), dropletCap)),
                continuous.minimumDroplets(), dropletCap);
        return new Burst(height,
                scaledDroplets(droplets, continuous.volumeScale()));
    }

    private static int scaledDroplets(int droplets, double scale) {
        return Mth.clamp((int) Math.round(droplets * scale), 1, 64);
    }

    static double normalizedSize(double radius, double minimum, double maximum) {
        return maximum <= minimum
                ? 1.0D : Mth.clamp((radius - minimum) / (maximum - minimum), 0.0D, 1.0D);
    }

    public static double launchSpeed(double requestedHeight, double gravity) {
        return Math.sqrt(Math.max(0.0D, 2.0D * Math.max(0.001D, gravity)
                * Math.max(0.0D, requestedHeight))) * 1.08D;
    }

    static double combinedAttemptChance(double singleChance, int attempts) {
        double chance = Mth.clamp(singleChance, 0.0D, 1.0D);
        return 1.0D - Math.pow(1.0D - chance, Math.max(1, attempts));
    }

    public record Burst(double height, int droplets) {
    }
}

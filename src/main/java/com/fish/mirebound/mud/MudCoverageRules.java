package com.fish.mirebound.mud;

import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/** Stable occupancy and strength rules for persistent player/equipment pollution. */
public final class MudCoverageRules {
    public static final int DOMAIN_SKIN = 0x13579BDF;
    public static final int DOMAIN_CAPE = 0x2468ACE1;
    public static final int DOMAIN_TEXTURE = 0x5F3759DF;
    static final float SPLASH_COVERAGE_MAXIMUM = 0.85F;
    private static final float SPLASH_COVERAGE_GAIN_MINIMUM = 0.005F;
    private static final float SPLASH_COVERAGE_GAIN_MAXIMUM = 0.015F;

    private MudCoverageRules() {
    }

    public static float contactTarget(Level level, SinkingMedium medium, float sourceStrength) {
        if (!MudBehaviorContext.coverage(level, medium)) {
            return 0.0F;
        }
        return target(sourceStrength, MudMediumRuntime.pollutionMultiplier(level, medium));
    }

    public static float contactTarget(Level level, net.minecraft.core.BlockPos pos,
            SinkingMedium medium, float sourceStrength) {
        if (!MudBehaviorContext.coverage(level, pos, medium)) {
            return 0.0F;
        }
        return target(sourceStrength, MudMediumRuntime.pollutionMultiplier(level, pos, medium));
    }

    static float target(float sourceStrength, float pollutionMultiplier) {
        if (!Float.isFinite(sourceStrength)
                || !Float.isFinite(pollutionMultiplier)) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, sourceStrength * pollutionMultiplier));
    }

    static float accumulateSplash(float current, float hitStrength) {
        return accumulateSplash(current, hitStrength, 1);
    }

    static float accumulateSplash(
            float current, float hitStrength, int passes) {
        float existing = Float.isFinite(current)
                ? Math.max(0.0F, Math.min(1.0F, current)) : 0.0F;
        if (existing >= SPLASH_COVERAGE_MAXIMUM) {
            return existing;
        }
        float hit = Float.isFinite(hitStrength)
                ? Math.max(0.0F, Math.min(1.0F, hitStrength))
                : 0.0F;
        if (hit <= 0.0F) {
            return existing;
        }
        float gain = Mth.lerp(hit,
                SPLASH_COVERAGE_GAIN_MINIMUM,
                SPLASH_COVERAGE_GAIN_MAXIMUM);
        int safePasses = Mth.clamp(passes, 1, 16);
        return Math.min(SPLASH_COVERAGE_MAXIMUM,
                existing + gain * safePasses);
    }

    static boolean splashChangesCell(float current, float next,
            SinkingMedium currentMedium, SinkingMedium incomingMedium,
            long currentVisualSource, long incomingVisualSource) {
        return next > current + 0.001F
                || currentMedium != incomingMedium
                || currentVisualSource != incomingVisualSource;
    }

    public static boolean allowsPixel(Level level, SinkingMedium medium,
            int domain, int pixel, int pixelCount) {
        return allowsPixel(
                medium.id(),
                domain,
                pixel,
                pixelCount,
                MudMediumRuntime.coverageMaximum(level, medium));
    }

    public static boolean allowsPixel(Level level, net.minecraft.core.BlockPos pos,
            SinkingMedium medium, int domain, int pixel, int pixelCount) {
        return allowsPixel(medium.id(), domain, pixel, pixelCount,
                MudMediumRuntime.coverageMaximum(level, pos, medium));
    }

    public static boolean allowsPixel(SinkingMedium medium, int appearance,
            int domain, int pixel, int pixelCount) {
        return allowsPixel(medium.id(), domain, pixel, pixelCount,
                MudCoverageAppearanceSnapshot.maximum(appearance, medium));
    }

    public static boolean allowsPixel(int mediumId, int domain,
            int pixel, int pixelCount, float maximumRatio) {
        if (pixel < 0 || pixel >= pixelCount || pixelCount <= 0 || !Float.isFinite(maximumRatio)) {
            return false;
        }
        float maximum = Math.max(0.0F, Math.min(1.0F, maximumRatio));
        if (maximum >= 1.0F) {
            return true;
        }
        if (maximum <= 0.0F) {
            return false;
        }

        int seed = mix(mediumId * 0x9E3779B9 ^ domain * 0x632BE5AB);
        long priority = Integer.toUnsignedLong(Integer.reverse(pixel ^ seed));
        long threshold = (long) Math.floor(maximum * 4294967296.0D);
        return priority < threshold;
    }

    public static int armorDomain(int armorSlotIndex) {
        return 0x4F1BBCDD ^ armorSlotIndex * 0x85157AF5;
    }

    public static int textureDomain(int textureHash, int width, int height) {
        return DOMAIN_TEXTURE
                ^ textureHash * 0x58F38DED
                ^ width * 0x632BE5AB
                ^ height * 0x85157AF5;
    }

    private static int mix(int value) {
        value ^= value >>> 16;
        value *= 0x7FEB352D;
        value ^= value >>> 15;
        value *= 0x846CA68B;
        return value ^ value >>> 16;
    }
}

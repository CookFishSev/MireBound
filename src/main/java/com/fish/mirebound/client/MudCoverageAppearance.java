package com.fish.mirebound.client;

import com.fish.mirebound.mud.MudMediumRuntime;
import com.fish.mirebound.mud.MudCoverageAppearanceSnapshot;
import com.fish.mirebound.mud.MudCoverageRules;
import com.fish.mirebound.mud.SinkingMedium;

/** Stable per-pixel opacity variation evaluated only while rebuilding coverage textures. */
public final class MudCoverageAppearance {
    private static final int BRIGHTNESS_CELL_SIZE = 4;

    private MudCoverageAppearance() {
    }

    public static float opacityScale(SinkingMedium medium, int x, int y, int salt) {
        return opacityScale(
                medium.id(),
                x,
                y,
                salt,
                MudMediumRuntime.clientCoverageOpacity(medium),
                MudMediumRuntime.clientCoverageOpacityVariation(medium));
    }

    static float opacityScale(SinkingMedium medium, int appearance, int x, int y, int salt) {
        return opacityScale(
                medium.id(),
                x,
                y,
                salt,
                MudCoverageAppearanceSnapshot.opacity(appearance, medium),
                MudCoverageAppearanceSnapshot.variation(appearance, medium));
    }

    static float opacityScale(int mediumId, int x, int y, int salt,
            float opacity, float variation) {
        float baseOpacity = Math.max(0.0F, Math.min(1.0F, opacity));
        float amount = Math.max(0.0F, Math.min(1.0F, variation));
        if (amount <= 0.0F) {
            return baseOpacity;
        }

        int hash = mediumId * 0x9E3779B9;
        hash ^= x * 0x632BE5AB;
        hash = Integer.rotateLeft(hash, 13);
        hash ^= y * 0x85157AF5;
        hash = Integer.rotateLeft(hash, 11);
        hash ^= salt * 0x58F38DED;
        hash ^= hash >>> 16;
        hash *= 0x7FEB352D;
        hash ^= hash >>> 15;
        float unit = (hash & 0xFFFF) / 65535.0F;
        return baseOpacity * (1.0F - amount * unit);
    }

    static float brightnessScale(SinkingMedium medium, int x, int y) {
        return brightnessScale(
                medium.id(), x, y,
                MudMediumRuntime.clientCoverageBrightnessVariation(medium));
    }

    static float brightnessScale(SinkingMedium medium, int appearance, int x, int y) {
        return brightnessScale(
                medium.id(), x, y,
                MudCoverageAppearanceSnapshot.brightnessVariation(appearance, medium));
    }

    static float brightnessScale(int mediumId, int x, int y, float variation) {
        float amount = Math.max(0.0F, Math.min(1.0F, variation));
        if (amount <= 0.0F) {
            return 1.0F;
        }

        int gridX = Math.floorDiv(x, BRIGHTNESS_CELL_SIZE);
        int gridY = Math.floorDiv(y, BRIGHTNESS_CELL_SIZE);
        float localX = Math.floorMod(x, BRIGHTNESS_CELL_SIZE)
                / (float) BRIGHTNESS_CELL_SIZE;
        float localY = Math.floorMod(y, BRIGHTNESS_CELL_SIZE)
                / (float) BRIGHTNESS_CELL_SIZE;
        float smoothX = smooth(localX);
        float smoothY = smooth(localY);
        float top = lerp(smoothX,
                brightnessNode(mediumId, gridX, gridY),
                brightnessNode(mediumId, gridX + 1, gridY));
        float bottom = lerp(smoothX,
                brightnessNode(mediumId, gridX, gridY + 1),
                brightnessNode(mediumId, gridX + 1, gridY + 1));
        return Math.max(0.0F, 1.0F + amount * lerp(smoothY, top, bottom));
    }

    private static float brightnessNode(int mediumId, int gridX, int gridY) {
        int hash = mediumId * 0x9E3779B9;
        hash ^= gridX * 0x632BE5AB;
        hash = Integer.rotateLeft(hash, 13);
        hash ^= gridY * 0x85157AF5;
        hash ^= hash >>> 16;
        hash *= 0x7FEB352D;
        hash ^= hash >>> 15;
        return (hash & 0xFFFF) / 32767.5F - 1.0F;
    }

    private static float smooth(float value) {
        return value * value * (3.0F - 2.0F * value);
    }

    private static float lerp(float amount, float start, float end) {
        return start + (end - start) * amount;
    }

    static boolean allowsCoveragePixel(SinkingMedium medium,
            int domain, int pixel, int pixelCount) {
        return MudCoverageRules.allowsPixel(
                medium.id(), domain, pixel, pixelCount,
                MudMediumRuntime.clientCoverageMaximum(medium));
    }


    static boolean allowsCoveragePixel(SinkingMedium medium, int appearance,
            int domain, int pixel, int pixelCount) {
        return MudCoverageRules.allowsPixel(medium, appearance, domain, pixel, pixelCount);
    }
}

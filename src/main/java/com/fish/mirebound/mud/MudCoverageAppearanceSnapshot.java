package com.fish.mirebound.mud;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/** Visual coverage parameters packed into one persistent per-cell integer. */
public final class MudCoverageAppearanceSnapshot {
    public static final int GLOBAL_FALLBACK = 0;
    private static final int LEGACY_MARKER = 0x5A000000;
    private static final int LEGACY_MARKER_MASK = 0xFF000000;
    private static final int MARKER = 0xB0000000;
    private static final int MARKER_MASK = 0xF0000000;
    private static final int SIX_BIT_MASK = 0x3F;

    private MudCoverageAppearanceSnapshot() {
    }

    public static int at(Level level, BlockPos pos, SinkingMedium medium) {
        float maximum = MudMediumRuntime.coverageMaximum(level, pos, medium);
        float opacity = MudMediumRuntime.coverageOpacity(level, pos, medium);
        float variation = MudMediumRuntime.coverageOpacityVariation(level, pos, medium);
        float brightnessVariation = MudMediumRuntime.coverageBrightnessVariation(level, pos, medium);
        return pack(maximum, opacity, variation, brightnessVariation);
    }

    public static int global(Level level, SinkingMedium medium) {
        return pack(
                MudMediumRuntime.coverageMaximum(level, medium),
                MudMediumRuntime.coverageOpacity(level, medium),
                MudMediumRuntime.coverageOpacityVariation(level, medium),
                MudMediumRuntime.coverageBrightnessVariation(level, medium));
    }

    public static int pack(float maximum, float opacity, float variation) {
        return pack(maximum, opacity, variation, 0.0F);
    }

    public static int pack(float maximum, float opacity, float variation,
            float brightnessVariation) {
        return MARKER
                | quantizeEight(maximum) << 20
                | quantizeEight(opacity) << 12
                | quantizeSix(variation) << 6
                | quantizeSix(brightnessVariation);
    }

    public static float maximum(int packed, SinkingMedium medium) {
        if (modern(packed)) {
            return ((packed >>> 20) & 0xFF) / 255.0F;
        }
        return legacy(packed)
                ? ((packed >>> 16) & 0xFF) / 255.0F
                : MudMediumRuntime.clientCoverageMaximum(medium);
    }

    public static float opacity(int packed, SinkingMedium medium) {
        if (modern(packed)) {
            return ((packed >>> 12) & 0xFF) / 255.0F;
        }
        return legacy(packed)
                ? (packed & 0xFF) / 255.0F
                : MudMediumRuntime.clientCoverageOpacity(medium);
    }

    public static float variation(int packed, SinkingMedium medium) {
        if (modern(packed)) {
            return ((packed >>> 6) & SIX_BIT_MASK) / 63.0F;
        }
        return legacy(packed)
                ? ((packed >>> 8) & 0xFF) / 255.0F
                : MudMediumRuntime.clientCoverageOpacityVariation(medium);
    }

    public static float brightnessVariation(int packed, SinkingMedium medium) {
        return modern(packed)
                ? (packed & SIX_BIT_MASK) / 63.0F
                : MudMediumRuntime.clientCoverageBrightnessVariation(medium);
    }

    private static boolean modern(int packed) {
        return (packed & MARKER_MASK) == MARKER;
    }

    private static boolean legacy(int packed) {
        return (packed & LEGACY_MARKER_MASK) == LEGACY_MARKER;
    }

    private static int quantizeEight(float value) {
        return Mth.clamp(Math.round(Mth.clamp(value, 0.0F, 1.0F) * 255.0F), 0, 255);
    }

    private static int quantizeSix(float value) {
        return Mth.clamp(Math.round(Mth.clamp(value, 0.0F, 1.0F) * 63.0F), 0, 63);
    }
}

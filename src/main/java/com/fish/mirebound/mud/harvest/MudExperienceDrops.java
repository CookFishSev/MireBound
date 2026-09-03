package com.fish.mirebound.mud.harvest;

import com.fish.mirebound.mud.SinkingMedium;
import net.minecraft.util.RandomSource;

/** Volume-aware experience rewards for the few media that contain spiritual energy. */
final class MudExperienceDrops {
    private MudExperienceDrops() {
    }

    static int roll(SinkingMedium medium, int pixels, RandomSource random) {
        return switch (medium) {
            case SCULK_MIRE -> scaledRange(pixels, 1, 3, random);
            case SOUL_SILT -> scaledRange(pixels, 1, 2, random);
            default -> 0;
        };
    }

    private static int scaledRange(
            int pixels, int minimumFull, int maximumFull,
            RandomSource random) {
        int clamped = MudDropYield.clampPixels(pixels);
        if (clamped == 0) {
            return 0;
        }
        int full = minimumFull
                + random.nextInt(maximumFull - minimumFull + 1);
        int scaled = full * clamped;
        int whole = scaled / MudDropYield.MAX_PIXELS;
        int remainder = scaled % MudDropYield.MAX_PIXELS;
        return whole + (remainder > 0
                && random.nextInt(MudDropYield.MAX_PIXELS) < remainder ? 1 : 0);
    }
}

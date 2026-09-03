package com.fish.mirebound.mud.harvest;

import net.minecraft.util.RandomSource;

/**
 * Converts a broken block's 1/16 volume into a stack size.
 *
 * <p>This is the single place that owns the volume-to-count curve, so every medium scales the same
 * way and a partially drained block never pays out like a full one. It is deliberately a pure
 * function of {@code (pixels, random)} with no level or registry access, which keeps it unit
 * testable and keeps the break path allocation free.
 *
 * <p>{@code SCALED} is the shared mud-ball curve. Compact vanilla materials use either
 * quarter-block pieces or one whole-block item so a full medium block has a predictable material
 * value.
 */
public enum MudDropYield {
    /** Intentional no-item result, used when a medium pays out experience instead. */
    NONE {
        @Override
        public int count(int pixels, RandomSource random) {
            return 0;
        }
    },
    /** One item for every non-empty broken volume, used when the medium drops itself. */
    SINGLE {
        @Override
        public int count(int pixels, RandomSource random) {
            return clampPixels(pixels) > 0 ? 1 : 0;
        }
    },
    /**
     * The medium's own mud ball: 1-5px yields 0-1, 6-10px yields 1-2, 11-15px yields 2-3, and a
     * full 16px block always yields four.
     */
    SCALED {
        @Override
        public int count(int pixels, RandomSource random) {
            int clamped = clampPixels(pixels);
            if (clamped == 0) {
                return 0;
            }
            if (clamped <= 5) {
                return random.nextInt(2);
            }
            if (clamped <= 10) {
                return 1 + random.nextInt(2);
            }
            if (clamped <= 15) {
                return 2 + random.nextInt(2);
            }
            return 4;
        }
    },
    /**
     * Loose pieces such as clay or slime balls. Four pixels equal one item, with the remainder
     * rolled proportionally; a full block always yields four pieces.
     */
    PIECE_SCALED {
        @Override
        public int count(int pixels, RandomSource random) {
            int clamped = clampPixels(pixels);
            int whole = clamped / 4;
            int remainder = clamped % 4;
            return whole + (remainder > 0 && random.nextInt(4) < remainder ? 1 : 0);
        }
    },
    /** Nine loose pieces make one block, with partial volume rounded probabilistically. */
    NINE_PIECE_SCALED {
        @Override
        public int count(int pixels, RandomSource random) {
            int scaled = clampPixels(pixels) * 9;
            int whole = scaled / MAX_PIXELS;
            int remainder = scaled % MAX_PIXELS;
            return whole + (remainder > 0
                    && random.nextInt(MAX_PIXELS) < remainder ? 1 : 0);
        }
    },
    /**
     * A material represented by a vanilla block item. Partial blocks use a proportional chance;
     * a full block always yields exactly one item.
     */
    BLOCK_SCALED {
        @Override
        public int count(int pixels, RandomSource random) {
            return random.nextInt(MAX_PIXELS) < clampPixels(pixels) ? 1 : 0;
        }
    },
    /** One whole material item is paid only by a complete 16px block. */
    FULL_BLOCK_ONLY {
        @Override
        public int count(int pixels, RandomSource random) {
            return clampPixels(pixels) == MAX_PIXELS ? 1 : 0;
        }
    },
    /** Embedded debris: uncommon regardless of volume, slightly likelier in a deeper block. */
    SPARSE {
        @Override
        public int count(int pixels, RandomSource random) {
            return random.nextInt(64) < clampPixels(pixels) ? 1 : 0;
        }
    },
    /** Rare embedded debris, capped at a 1/16 chance for a full block. */
    RARE {
        @Override
        public int count(int pixels, RandomSource random) {
            return random.nextInt(256) < clampPixels(pixels) ? 1 : 0;
        }
    };

    public static final int MAX_PIXELS = 16;

    /** Returns how many items this yield produces, never negative. */
    public abstract int count(int pixels, RandomSource random);

    static int clampPixels(int pixels) {
        return Math.max(0, Math.min(MAX_PIXELS, pixels));
    }
}

package com.fish.mirebound.mud;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/** Creates stable, nonzero seeds for one persistent contamination batch. */
public final class MudCoveragePatternSeed {
    private static final AtomicInteger SEQUENCE = new AtomicInteger(
            ThreadLocalRandom.current().nextInt());

    private MudCoveragePatternSeed() {
    }

    public static int next() {
        int seed = SEQUENCE.addAndGet(0x9E3779B9);
        return seed == 0 ? SEQUENCE.addAndGet(0x9E3779B9) : seed;
    }

    public static int mix(int salt, int seed) {
        int value = salt ^ Integer.rotateLeft(seed, 11) ^ 0x632BE5AB;
        value ^= value >>> 16;
        value *= 0x7FEB352D;
        value ^= value >>> 15;
        value *= 0x846CA68B;
        return value ^ value >>> 16;
    }

    public static int sampleOffsetX(int seed) {
        return seed == 0 ? 0 : Math.floorMod(mix(0x2C9277B5, seed), 31) - 15;
    }

    public static int sampleOffsetY(int seed) {
        return seed == 0 ? 0 : Math.floorMod(mix(0x58F38DED, seed), 31) - 15;
    }
}

package com.fish.mirebound.client;

import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.network.payload.MudClodScreenImpactPayload;
import net.minecraft.util.Mth;

/** Short-lived coverage source consumed by the ordinary dynamic vision grid. */
final class ScreenMudImpactVision {
    private static final int POOL_SIZE = 4;
    private static final long LIFETIME_TICKS = 90L;
    private static final long HOLD_TICKS = 12L;
    private static final Impact[] IMPACTS = createPool();

    private ScreenMudImpactVision() {
    }

    static void accept(MudClodScreenImpactPayload payload, long gameTime) {
        expire(gameTime);
        Impact selected = IMPACTS[0];
        for (Impact impact : IMPACTS) {
            if (!impact.active) {
                selected = impact;
                break;
            }
            if (impact.startedAt < selected.startedAt) {
                selected = impact;
            }
        }
        selected.configure(payload, gameTime);
    }

    static boolean active(long gameTime) {
        expire(gameTime);
        for (Impact impact : IMPACTS) {
            if (impact.active) {
                return true;
            }
        }
        return false;
    }

    static void sampleAt(
            int band, int lane, long gameTime, Sample result) {
        float remainingClear = 1.0F;
        float strongest = 0.0F;
        SinkingMedium medium = SinkingMedium.MUD;
        for (Impact impact : IMPACTS) {
            if (!impact.active) {
                continue;
            }
            long age = age(impact, gameTime);
            if (age >= LIFETIME_TICKS) {
                continue;
            }
            float coverage = impact.coverageAt(band, lane, age);
            remainingClear *= 1.0F - coverage;
            if (coverage > strongest) {
                strongest = coverage;
                medium = impact.medium;
            }
        }
        result.set(
                Mth.clamp(1.0F - remainingClear, 0.0F, 1.0F),
                medium);
    }

    /** Compatibility view used by the fixed-grid tests and older client helpers. */
    static float coverageAt(int band, int lane, long gameTime) {
        expire(gameTime);
        float remainingClear = 1.0F;
        for (Impact impact : IMPACTS) {
            if (!impact.active) {
                continue;
            }
            long age = age(impact, gameTime);
            if (age < LIFETIME_TICKS) {
                remainingClear *= 1.0F - impact.coverageAt(band, lane, age);
            }
        }
        return Mth.clamp(1.0F - remainingClear, 0.0F, 1.0F);
    }

    static void reset() {
        for (Impact impact : IMPACTS) {
            impact.active = false;
        }
    }

    private static void expire(long gameTime) {
        for (Impact impact : IMPACTS) {
            if (impact.active && age(impact, gameTime) >= LIFETIME_TICKS) {
                impact.active = false;
            }
        }
    }

    private static long age(Impact impact, long gameTime) {
        return Math.max(0L, gameTime - impact.startedAt);
    }

    private static float smooth(float value) {
        float clamped = Mth.clamp(value, 0.0F, 1.0F);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    private static float noise(int x, int y, long seed) {
        long value = seed ^ x * 0x9E3779B97F4A7C15L
                ^ y * 0xD1B54A32D192ED03L;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (value >>> 40) / (float) (1L << 24);
    }

    private static Impact[] createPool() {
        Impact[] impacts = new Impact[POOL_SIZE];
        for (int index = 0; index < impacts.length; index++) {
            impacts[index] = new Impact();
        }
        return impacts;
    }

    private static final class Impact {
        private boolean active;
        private long startedAt;
        private long seed;
        private float intensity;
        private float centerBand;
        private float centerLane;
        private float radiusBand;
        private float radiusLane;
        private SinkingMedium medium = SinkingMedium.MUD;

        private void configure(
                MudClodScreenImpactPayload payload, long gameTime) {
            active = true;
            startedAt = gameTime;
            seed = payload.seed();
            intensity = payload.intensity();
            medium = payload.medium();
            centerBand = 28.0F + noise(3, 7, seed) * 5.0F;
            centerLane = 21.5F + noise(11, 5, seed) * 5.0F;
            radiusBand = 11.5F + intensity * 4.0F;
            radiusLane = 14.0F + intensity * 4.5F;
        }

        private float coverageAt(int band, int lane, long age) {
            float growth = 0.42F + smooth((age + 1.0F) / 6.0F) * 0.58F;
            float fade = age <= HOLD_TICKS
                    ? 1.0F
                    : 1.0F - smooth((age - HOLD_TICKS)
                            / (float) (LIFETIME_TICKS - HOLD_TICKS));
            float dx = (lane - centerLane)
                    / Math.max(1.0F, radiusLane * growth);
            float dy = (band - centerBand)
                    / Math.max(1.0F, radiusBand * growth);
            float distance = Mth.sqrt(dx * dx + dy * dy);
            float edgeNoise = (noise(lane / 3, band / 3, seed) - 0.5F)
                    * 0.18F;
            float shape = smooth((1.08F - distance + edgeNoise) / 0.30F);
            return Mth.clamp(shape * intensity * fade, 0.0F, 1.0F);
        }
    }

    static final class Sample {
        private float coverage;
        private SinkingMedium medium = SinkingMedium.MUD;

        float coverage() {
            return coverage;
        }

        SinkingMedium medium() {
            return medium;
        }

        void set(float coverage, SinkingMedium medium) {
            this.coverage = coverage;
            this.medium = medium;
        }
    }
}

package com.fish.mirebound.generation;

import net.minecraft.util.Mth;

/** Deterministic, continuous horizontal footprint and depth for one mud deposit. */
public final class MudTerrainDepositShape {
    private static final double EDGE_FREQUENCY = 0.18D;
    private static final double DEPTH_FREQUENCY = 0.31D;

    private MudTerrainDepositShape() {
    }

    public static boolean contains(
            int centerX, int centerZ, int x, int z,
            MudTerrainGenerationSettings settings) {
        double dx = x - centerX;
        double dz = z - centerZ;
        double radius = localRadius(x, z, settings);
        return dx * dx + dz * dz <= radius * radius;
    }

    public static int depth(
            int centerX, int centerZ, int x, int z,
            MudTerrainGenerationSettings settings) {
        if (!contains(centerX, centerZ, x, z, settings)) {
            return 0;
        }
        if (settings.thickness() == 1) {
            return 1;
        }
        double distance = Math.hypot(x - centerX, z - centerZ);
        double normalized = Mth.clamp(
                distance / Math.max(1.0D, localRadius(x, z, settings)),
                0.0D, 1.0D);
        if (normalized <= 0.08D) {
            return settings.thickness();
        }
        double bowl = Math.pow(1.0D - normalized, 0.62D);
        double variation = (smoothNoise(
                settings.seed() ^ 0x61C88647,
                x * DEPTH_FREQUENCY,
                z * DEPTH_FREQUENCY) - 0.5D) * 0.22D;
        double scaled = Mth.clamp(bowl + variation, 0.0D, 1.0D);
        return 1 + Mth.floor(scaled * (settings.thickness() - 1) + 1.0E-9D);
    }

    private static double localRadius(
            int x, int z, MudTerrainGenerationSettings settings) {
        if (settings.edgeRoughness() <= 1.0E-9D) {
            return settings.radius();
        }
        double noise = smoothNoise(
                settings.seed(), x * EDGE_FREQUENCY, z * EDGE_FREQUENCY);
        double erosion = settings.edgeRoughness() * settings.radius()
                * (0.08D + noise * 0.20D);
        return Math.max(1.0D, settings.radius() - erosion);
    }

    static double smoothNoise(long seed, double x, double z) {
        int x0 = Mth.floor(x);
        int z0 = Mth.floor(z);
        double tx = smoothStep(x - x0);
        double tz = smoothStep(z - z0);
        double a = Mth.lerp(tx, unit(seed, x0, z0), unit(seed, x0 + 1, z0));
        double b = Mth.lerp(tx, unit(seed, x0, z0 + 1), unit(seed, x0 + 1, z0 + 1));
        return Mth.lerp(tz, a, b);
    }

    private static double smoothStep(double value) {
        return value * value * (3.0D - 2.0D * value);
    }

    private static double unit(long seed, int x, int z) {
        long value = seed;
        value ^= (long) x * 0x632BE59BD9B4E019L;
        value ^= (long) z * 0x9E3779B97F4A7C15L;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (value >>> 11) * 0x1.0p-53;
    }
}

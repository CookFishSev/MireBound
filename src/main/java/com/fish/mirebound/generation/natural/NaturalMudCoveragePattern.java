package com.fish.mirebound.generation.natural;

import com.fish.mirebound.mud.SinkingMedium;

/** Stateless world-coordinate sampling shared by generation and the editor preview. */
public final class NaturalMudCoveragePattern {
    private static final SinkingMedium[] MEDIA = SinkingMedium.values();
    private NaturalMudCoveragePattern() {}

    public static boolean covered(NaturalMudCoverageRule rule, long seed, int x, int z) {
        if (!rule.enabled() || rule.weights().isEmpty() || rule.coverage() <= 0
                || !regionGenerated(rule, seed, x, z)) return false;
        if (rule.coverage() >= 1) return true;
        double threshold = Math.clamp(rule.coverage()
                + (noise(seed + 19, x / 48.0, z / 48.0) - .5) * 2 * rule.coverageVariation(), 0, 1);
        long cell = nearestCell(seed + 7, x, z, 16);
        // Region ranks are uniform; interpolated noise is not, and saturated
        // high coverage values into almost complete biome coverage.
        return unit(seed + 11, (int) (cell >> 32), (int) cell) < threshold;
    }

    public static boolean regionGenerated(NaturalMudCoverageRule rule, long seed, int x, int z) {
        if (rule.generationChance() <= 0) return false;
        if (rule.generationChance() >= 1) return true;
        long cell = nearestCell(seed + 61, x, z, 64);
        return unit(seed + 67, (int) (cell >> 32), (int) cell) < rule.generationChance();
    }

    private static long nearestCell(long seed, int x, int z, int size) {
        double wx = x + (noise(seed + 79, x / 12.0, z / 12.0) - .5) * 6;
        double wz = z + (noise(seed + 83, x / 12.0, z / 12.0) - .5) * 6;
        int gx = (int) Math.floor(wx / size), gz = (int) Math.floor(wz / size);
        double nearest = Double.POSITIVE_INFINITY;
        long key = 0;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            int sx = gx + dx, sz = gz + dz;
            double px = (sx + .2 + .6 * unit(seed + 41, sx, sz)) * size;
            double pz = (sz + .2 + .6 * unit(seed + 43, sx, sz)) * size;
            double distance = (wx - px) * (wx - px) + (wz - pz) * (wz - pz);
            if (distance < nearest) { nearest = distance; key = (long) sx << 32 | sz & 0xFFFFFFFFL; }
        }
        return key;
    }

    public static double depth(NaturalMudCoverageRule rule, long seed, int x, int z, double treeDistance) {
        double influence = rule.deeperNearTrees()
                ? Math.clamp(1 - treeDistance / rule.treeRadius(), 0, 1) : 0;
        influence = influence * influence * (3 - 2 * influence);
        double value = rule.depth() + (rule.treeDepth() - rule.depth()) * influence
                + (noise(seed + 31, x / 5.0, z / 5.0) - .5) * 2 * rule.depthVariation();
        return Math.clamp(Math.round(value * 16) / 16.0, 1.0 / 16, Math.min(6, rule.replacementLayers()));
    }

    public static SinkingMedium medium(NaturalMudCoverageRule rule, long seed, int x, int z) {
        if (rule.mixMode() == NaturalMudCoverageRule.MixMode.RANDOM)
            return weighted(rule, seed, x, z, unit(seed, x, z));
        int size = rule.patchSize();
        int gx = Math.floorDiv(x, size), gz = Math.floorDiv(z, size);
        double first = Double.POSITIVE_INFINITY, second = first;
        int ax = gx, az = gz, bx = gx, bz = gz;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            int sx = gx + dx, sz = gz + dz;
            double px = (sx + .2 + .6 * unit(seed + 41, sx, sz)) * size;
            double pz = (sz + .2 + .6 * unit(seed + 43, sx, sz)) * size;
            double distance = Math.hypot(x - px, z - pz);
            if (distance < first) {
                second = first; bx = ax; bz = az;
                first = distance; ax = sx; az = sz;
            } else if (distance < second) { second = distance; bx = sx; bz = sz; }
        }
        double edgeWidth = .5 + rule.mixVariation() * Math.min(6, size * .2);
        boolean neighbor = second - first < edgeWidth
                && unit(seed + 47, x, z) < .5 * (1 - (second - first) / edgeWidth);
        int sx = neighbor ? bx : ax, sz = neighbor ? bz : az;
        return weighted(rule, seed, sx * size, sz * size, unit(seed + 53, sx, sz));
    }

    private static SinkingMedium weighted(NaturalMudCoverageRule rule, long seed,
            int x, int z, double sample) {
        double total = 0;
        for (SinkingMedium medium : MEDIA) total += weight(rule, medium, seed, x, z);
        double target = sample * total;
        for (SinkingMedium medium : MEDIA) {
            target -= weight(rule, medium, seed, x, z);
            if (target < 0) return medium;
        }
        return null;
    }

    private static double weight(NaturalMudCoverageRule rule, SinkingMedium medium,
            long seed, int x, int z) {
        int weight = rule.weights().getOrDefault(medium, 0);
        return weight == 0 ? 0 : weight * (1 + rule.mixVariation()
                * (noise(seed + medium.ordinal() * 71L, x / 48.0, z / 48.0) - .5));
    }

    private static double noise(long seed, double x, double z) {
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        double tx = x - ix, tz = z - iz;
        tx = tx * tx * (3 - 2 * tx); tz = tz * tz * (3 - 2 * tz);
        double a = unit(seed, ix, iz), b = unit(seed, ix + 1, iz);
        double c = unit(seed, ix, iz + 1), d = unit(seed, ix + 1, iz + 1);
        return a + (b - a) * tx + (c + (d - c) * tx - a - (b - a) * tx) * tz;
    }

    private static double unit(long seed, int x, int z) {
        long hash = seed ^ x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL;
        hash = (hash ^ hash >>> 30) * 0xBF58476D1CE4E5B9L;
        hash = (hash ^ hash >>> 27) * 0x94D049BB133111EBL;
        return ((hash ^ hash >>> 31) >>> 11) * 0x1.0p-53;
    }
}

package com.fish.mirebound.generation.natural;

import com.fish.mirebound.generation.MudTerrainLakeSettings;
import com.fish.mirebound.generation.MudTerrainLakeShape;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** Pure deterministic top-down shape builder shared by worldgen and GUI previews. */
public final class NaturalMudDepositShape {
    private NaturalMudDepositShape() {
    }

    public static List<Cell> build(
            NaturalMudDepositForm form, long seed, int radius) {
        return buildBounded(form, seed, Mth.clamp(radius, 2, 12));
    }

    public static List<Cell> buildForWand(
            NaturalMudDepositForm form, long seed, int radius) {
        if (form.lake()) {
            throw new IllegalArgumentException(
                    "Wand lake types use MudTerrainLakeShape directly");
        }
        return buildBounded(form, seed, Mth.clamp(radius, 2, 48));
    }

    private static List<Cell> buildBounded(
            NaturalMudDepositForm form, long seed, int boundedRadius) {
        RandomSource random = RandomSource.create(seed);
        List<Cell> cells = new ArrayList<>(switch (form) {
            case RIVERBANK_CRESCENT -> crescent(random, boundedRadius);
            case RIVERBED_RIBBON -> ribbon(random, boundedRadius, false);
            case DUNE_BLOWOUT -> blowout(random, boundedRadius);
            case MARSH_MOSAIC -> mosaic(random, boundedRadius);
            case CAVE_SEEP -> seep(random, boundedRadius);
            case VOLCANIC_FISSURE -> ribbon(random, boundedRadius, true);
            case END_IMPACT_RING -> impactRing(random, boundedRadius);
            case ORGANIC_NEST -> organicNest(random, boundedRadius);
            case SURFACE_LAKE, UNDERGROUND_LAKE ->
                    lakeFootprint(seed, boundedRadius);
        });
        cells.sort(Comparator.comparingDouble(Cell::distanceSquared));
        return List.copyOf(cells);
    }

    public static int columnDepth(
            NaturalMudGenerationProfile.Rule rule, Cell cell) {
        return columnDepth(rule.minimumDepth(), rule.maximumDepth(), cell);
    }

    public static int columnDepth(
            int minimumDepth, int maximumDepth, Cell cell) {
        int boundedMinimum = Mth.clamp(minimumDepth, 1, 8);
        int boundedMaximum = Mth.clamp(
                maximumDepth, boundedMinimum, 8);
        int depthRange = boundedMaximum - boundedMinimum;
        int depth = boundedMinimum
                + Mth.floor(cell.strength() * (depthRange + 0.999D));
        return Mth.clamp(depth, boundedMinimum, boundedMaximum);
    }

    public static MudTerrainLakeSettings lakeSettings(long seed, int radius) {
        int horizontal = Mth.clamp(radius, 2, 12);
        int vertical = Mth.clamp((horizontal + 1) / 2, 1, 6);
        return new MudTerrainLakeSettings(
                horizontal, vertical, (int) (seed ^ seed >>> 32),
                MudTerrainLakeSettings.AIR, MudTerrainLakeSettings.AIR);
    }

    static boolean acceptsLandHeights(
            int siteY, int centerY,
            int westY, int eastY, int northY, int southY) {
        if (Math.abs(centerY - siteY) > 1) {
            return false;
        }
        int minimum = Math.min(centerY,
                Math.min(Math.min(westY, eastY), Math.min(northY, southY)));
        int maximum = Math.max(centerY,
                Math.max(Math.max(westY, eastY), Math.max(northY, southY)));
        return maximum - minimum <= 1;
    }

    private static List<Cell> crescent(RandomSource random, int radius) {
        List<Cell> result = new ArrayList<>();
        double squash = 0.68D + random.nextDouble() * 0.16D;
        double cutShift = 0.34D + random.nextDouble() * 0.16D;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double nx = x / (double) radius;
                double nz = z / (radius * squash);
                double outer = nx * nx + nz * nz;
                double cutX = nx - cutShift;
                double cut = cutX * cutX + nz * nz * 1.08D;
                double rough = noise(randomSeed(x, z, random.nextLong())) * 0.13D;
                if (outer <= 1.0D + rough && cut >= 0.42D + rough * 0.45D) {
                    result.add(cell(x, z, 1.0D - Math.sqrt(Math.max(0.0D, outer)) * 0.58D));
                }
            }
        }
        return result;
    }

    private static List<Cell> ribbon(
            RandomSource random, int radius, boolean branching) {
        List<Cell> result = new ArrayList<>();
        double angle = random.nextDouble() * Math.PI;
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double halfLength = radius * (branching ? 1.35D : 1.65D);
        double baseWidth = Math.max(1.15D, radius * (branching ? 0.24D : 0.34D));
        int bound = Mth.ceil(halfLength + baseWidth + 2.0D);
        long salt = random.nextLong();
        for (int x = -bound; x <= bound; x++) {
            for (int z = -bound; z <= bound; z++) {
                double along = x * cos + z * sin;
                double across = -x * sin + z * cos;
                if (Math.abs(along) > halfLength) {
                    continue;
                }
                double curve = Math.sin(along * 0.42D + salt * 1.0E-5D)
                        * radius * (branching ? 0.14D : 0.22D);
                double taper = 1.0D - Math.pow(Math.abs(along) / halfLength, 1.7D);
                double width = Math.max(0.72D, baseWidth * (0.55D + taper * 0.62D));
                double mainDistance = Math.abs(across - curve);
                boolean inside = mainDistance <= width;
                if (branching && along > -radius * 0.1D) {
                    double branchAcross = across + along * 0.52D - radius * 0.18D;
                    inside |= Math.abs(branchAcross) <= width * 0.54D
                            && along < halfLength * 0.72D;
                }
                if (inside && noise(randomSeed(x, z, salt)) > -0.68D) {
                    result.add(cell(x, z,
                            1.0D - Math.min(1.0D, mainDistance / Math.max(1.0D, width)) * 0.42D));
                }
            }
        }
        return result;
    }

    private static List<Cell> blowout(RandomSource random, int radius) {
        List<Cell> result = new ArrayList<>();
        double angle = random.nextDouble() * Math.PI;
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        long salt = random.nextLong();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double rx = (x * cos + z * sin) / radius;
                double rz = (-x * sin + z * cos) / (radius * 0.64D);
                double distance = Math.sqrt(rx * rx + rz * rz);
                double windCut = Math.max(0.0D, rx) * 0.18D;
                double edge = 1.0D + noise(randomSeed(x, z, salt)) * 0.16D - windCut;
                if (distance <= edge) {
                    result.add(cell(x, z, 1.0D - distance * 0.48D));
                }
            }
        }
        return result;
    }

    private static List<Cell> mosaic(RandomSource random, int radius) {
        int blobCount = 3 + random.nextInt(3);
        Blob[] blobs = new Blob[blobCount];
        for (int index = 0; index < blobCount; index++) {
            blobs[index] = new Blob(
                    random.nextInt(radius + 1) - radius / 2,
                    random.nextInt(radius + 1) - radius / 2,
                    radius * (0.34D + random.nextDouble() * 0.31D));
        }
        List<Cell> result = new ArrayList<>();
        long salt = random.nextLong();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double best = 0.0D;
                for (Blob blob : blobs) {
                    double distance = Math.hypot(x - blob.x, z - blob.z) / blob.radius;
                    best = Math.max(best, 1.0D - distance);
                }
                if (best + noise(randomSeed(x, z, salt)) * 0.16D > 0.0D) {
                    result.add(cell(x, z, 0.52D + Math.max(0.0D, best) * 0.48D));
                }
            }
        }
        return result;
    }

    private static List<Cell> seep(RandomSource random, int radius) {
        List<Cell> result = new ArrayList<>();
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double dx = Math.cos(angle);
        double dz = Math.sin(angle);
        long salt = random.nextLong();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double downhill = (x * dx + z * dz) / radius;
                double cross = (-x * dz + z * dx) / (radius * 0.72D);
                double shifted = downhill + 0.22D;
                double distance = Math.sqrt(shifted * shifted + cross * cross);
                boolean tail = downhill > 0.15D
                        && Math.abs(cross) < (1.0D - downhill) * 0.36D;
                if (distance <= 0.92D + noise(randomSeed(x, z, salt)) * 0.12D || tail) {
                    result.add(cell(x, z, 1.0D - Math.min(1.0D, distance) * 0.52D));
                }
            }
        }
        return result;
    }

    private static List<Cell> impactRing(RandomSource random, int radius) {
        List<Cell> result = new ArrayList<>();
        double inner = 0.43D + random.nextDouble() * 0.10D;
        long salt = random.nextLong();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double distance = Math.hypot(x, z) / radius;
                double rough = noise(randomSeed(x, z, salt)) * 0.09D;
                boolean ring = distance >= inner + rough && distance <= 1.0D + rough;
                boolean core = distance <= inner * 0.36D;
                double angle = Math.atan2(z, x);
                boolean spoke = distance < inner && Math.cos(angle * 5.0D + salt) > 0.91D;
                if (ring || core || spoke) {
                    double strength = core ? 1.0D
                            : 0.58D + (1.0D - Math.min(1.0D, distance)) * 0.34D;
                    result.add(cell(x, z, strength));
                }
            }
        }
        return result;
    }

    private static List<Cell> organicNest(RandomSource random, int radius) {
        List<Cell> result = new ArrayList<>();
        int arms = 4 + random.nextInt(4);
        double turn = random.nextDouble() * Math.PI * 2.0D;
        long salt = random.nextLong();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double distance = Math.hypot(x, z);
                boolean core = distance <= radius * 0.48D
                        + noise(randomSeed(x, z, salt)) * radius * 0.10D;
                double angle = Math.atan2(z, x) - turn - distance * 0.18D;
                double armWave = Math.cos(angle * arms);
                boolean tendril = distance > radius * 0.30D && distance <= radius
                        && armWave > 0.82D + distance / radius * 0.10D;
                if (core || tendril) {
                    result.add(cell(x, z,
                            1.0D - Math.min(1.0D, distance / radius) * 0.44D));
                }
            }
        }
        return result;
    }

    private static List<Cell> lakeFootprint(long seed, int radius) {
        MudTerrainLakeShape.Shape shape = MudTerrainLakeShape.build(
                lakeSettings(seed, radius));
        Map<Long, Integer> lowestByColumn = new HashMap<>();
        for (net.minecraft.core.BlockPos pos : shape.interior()) {
            long key = (long) pos.getX() << 32
                    ^ pos.getZ() & 0xFFFFFFFFL;
            lowestByColumn.merge(key, pos.getY(), Math::min);
        }
        int vertical = Math.max(1, lakeSettings(seed, radius).verticalRadius());
        List<Cell> result = new ArrayList<>(lowestByColumn.size());
        lowestByColumn.forEach((key, lowestY) -> {
            int x = (int) (key >> 32);
            int z = (int) (long) key;
            double strength = Mth.clamp(
                    (1.0D - lowestY) / (vertical * 2.0D), 0.15D, 1.0D);
            result.add(cell(x, z, strength));
        });
        return result;
    }

    private static Cell cell(int x, int z, double strength) {
        return new Cell(x, z, Mth.clamp(strength, 0.15D, 1.0D));
    }

    private static long randomSeed(int x, int z, long salt) {
        long value = salt ^ (long) x * 0x9E3779B97F4A7C15L
                ^ (long) z * 0xC2B2AE3D27D4EB4FL;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    private static double noise(long value) {
        return ((value >>> 11) * 0x1.0p-53) * 2.0D - 1.0D;
    }

    public record Cell(int dx, int dz, double strength) {
        private double distanceSquared() {
            return (double) dx * dx + (double) dz * dz;
        }
    }

    private record Blob(int x, int z, double radius) {
    }
}

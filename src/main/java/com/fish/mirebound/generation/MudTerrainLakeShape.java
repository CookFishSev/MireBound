package com.fish.mirebound.generation;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;

/** Deterministic lake volume using vanilla LakeFeature's ellipsoid mask. */
public final class MudTerrainLakeShape {
    public static final int LIQUID_SURFACE_Y = 0;

    private MudTerrainLakeShape() {
    }

    public static Shape build(MudTerrainLakeSettings settings) {
        int horizontalRadius = settings.horizontalRadius();
        int verticalRadius = settings.verticalRadius();
        int width = horizontalRadius * 2;
        int height = verticalRadius * 2;
        boolean[] volume = new boolean[width * width * height];
        RandomSource random = RandomSource.create(settings.seed());

        int ellipsoidCount = random.nextInt(4) + 4;
        for (int index = 0; index < ellipsoidCount; index++) {
            addEllipsoid(volume, width, height, random);
        }
        if (!containsInterior(volume, width, height, verticalRadius)) {
            volume[index(width, height, horizontalRadius,
                    Math.max(0, verticalRadius - 1), horizontalRadius)] = true;
        }

        Set<Long> interior = new HashSet<>();
        Set<Long> cavity = new HashSet<>();
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < width; z++) {
                for (int y = 0; y < height; y++) {
                    if (!volume[index(width, height, x, y, z)]) {
                        continue;
                    }
                    long packed = offset(
                            x, y, z, horizontalRadius, verticalRadius).asLong();
                    (y < verticalRadius ? interior : cavity).add(packed);
                }
            }
        }

        Set<Long> shell = new HashSet<>();
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < width; z++) {
                for (int y = 0; y < height; y++) {
                    if (!volume[index(width, height, x, y, z)]
                            && bordersVolume(volume, width, height, x, y, z)
                            && (y < verticalRadius || random.nextInt(2) != 0)) {
                        shell.add(offset(
                                x, y, z, horizontalRadius, verticalRadius)
                                .asLong());
                    }
                }
            }
        }
        return new Shape(sorted(interior), sorted(cavity), sorted(shell));
    }

    /** Keeps the underground basin enclosed while opening the surface variant. */
    public static boolean includesShell(
            MudTerrainGenerationType type, BlockPos offset) {
        return type != MudTerrainGenerationType.LAKE_SURFACE
                || offset.getY() <= LIQUID_SURFACE_Y;
    }

    /** Finds the uppermost mud cell in every local-Y pool column. */
    public static Set<Long> surfaceInterior(Shape shape) {
        Map<Long, BlockPos> highestByColumn = new HashMap<>();
        for (BlockPos offset : shape.interior()) {
            long column = ChunkPos.asLong(offset.getX(), offset.getZ());
            highestByColumn.merge(column, offset, (current, candidate) ->
                    candidate.getY() > current.getY() ? candidate : current);
        }
        Set<Long> surface = new HashSet<>(highestByColumn.size());
        highestByColumn.values().forEach(pos -> surface.add(pos.asLong()));
        return Set.copyOf(surface);
    }

    private static void addEllipsoid(
            boolean[] volume, int width, int height, RandomSource random) {
        // LakeFeature's 16x8 constants scaled to the selected dimensions.
        double diameterX = random.nextDouble() * width * 3.0D / 8.0D
                + width * 3.0D / 16.0D;
        double diameterY = random.nextDouble() * height / 2.0D
                + height / 4.0D;
        double diameterZ = random.nextDouble() * width * 3.0D / 8.0D
                + width * 3.0D / 16.0D;
        double centerX = random.nextDouble()
                * (width - diameterX - width / 8.0D)
                + width / 16.0D + diameterX / 2.0D;
        double centerY = random.nextDouble()
                * (height - diameterY - height / 2.0D)
                + height / 4.0D + diameterY / 2.0D;
        double centerZ = random.nextDouble()
                * (width - diameterZ - width / 8.0D)
                + width / 16.0D + diameterZ / 2.0D;

        int horizontalMargin = (int) Math.round(width / 16.0D);
        int verticalMargin = (int) Math.round(height / 8.0D);
        for (int x = horizontalMargin; x < width - horizontalMargin; x++) {
            for (int z = horizontalMargin; z < width - horizontalMargin; z++) {
                for (int y = verticalMargin; y < height - verticalMargin; y++) {
                    double normalizedX = (x - centerX) / (diameterX / 2.0D);
                    double normalizedY = (y - centerY) / (diameterY / 2.0D);
                    double normalizedZ = (z - centerZ) / (diameterZ / 2.0D);
                    if (normalizedX * normalizedX
                            + normalizedY * normalizedY
                            + normalizedZ * normalizedZ < 1.0D) {
                        volume[index(width, height, x, y, z)] = true;
                    }
                }
            }
        }
    }

    private static boolean bordersVolume(
            boolean[] volume, int width, int height, int x, int y, int z) {
        return x < width - 1 && volume[index(width, height, x + 1, y, z)]
                || x > 0 && volume[index(width, height, x - 1, y, z)]
                || z < width - 1 && volume[index(width, height, x, y, z + 1)]
                || z > 0 && volume[index(width, height, x, y, z - 1)]
                || y < height - 1 && volume[index(width, height, x, y + 1, z)]
                || y > 0 && volume[index(width, height, x, y - 1, z)];
    }

    private static boolean containsInterior(
            boolean[] volume, int width, int height, int liquidLevel) {
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < width; z++) {
                for (int y = 0; y < liquidLevel; y++) {
                    if (volume[index(width, height, x, y, z)]) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static int index(
            int width, int height, int x, int y, int z) {
        return (x * width + z) * height + y;
    }

    private static BlockPos offset(
            int x, int y, int z, int horizontalRadius, int verticalRadius) {
        return new BlockPos(
                x - horizontalRadius,
                y - verticalRadius + 1,
                z - horizontalRadius);
    }

    private static List<BlockPos> sorted(Set<Long> packedPositions) {
        return packedPositions.stream()
                .map(value -> BlockPos.of(value.longValue()))
                .sorted(Comparator.comparingInt((BlockPos pos) -> pos.getY())
                        .thenComparingInt(BlockPos::getZ)
                        .thenComparingInt(BlockPos::getX))
                .map(BlockPos::immutable)
                .toList();
    }

    public record Shape(
            List<BlockPos> interior,
            List<BlockPos> cavity,
            List<BlockPos> shell) {
        public Shape {
            interior = List.copyOf(interior);
            cavity = List.copyOf(cavity);
            shell = List.copyOf(shell);
        }
    }
}

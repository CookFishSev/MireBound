package com.fish.mirebound.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

class MudTerrainLakeShapeTest {
    @Test
    void shapeIsDeterministicAndBounded() {
        MudTerrainLakeSettings settings = new MudTerrainLakeSettings(
                11, 5, 771923,
                MudTerrainLakeSettings.AIR, MudTerrainLakeSettings.AIR);

        MudTerrainLakeShape.Shape first = MudTerrainLakeShape.build(settings);
        MudTerrainLakeShape.Shape second = MudTerrainLakeShape.build(settings);

        assertEquals(first, second);
        Set<BlockPos> volume = new HashSet<>(first.interior());
        volume.addAll(first.cavity());
        assertFalse(volume.isEmpty());
        for (BlockPos pos : volume) {
            assertTrue(Math.abs(pos.getX()) <= settings.horizontalRadius());
            assertTrue(Math.abs(pos.getY()) <= settings.verticalRadius());
            assertTrue(Math.abs(pos.getZ()) <= settings.horizontalRadius());
        }
    }

    @Test
    void defaultSizeMatchesVanillaLakeFeatureMaskExactly() {
        int seed = 991;
        MudTerrainLakeShape.Shape shape = MudTerrainLakeShape.build(
                new MudTerrainLakeSettings(8, 4, seed,
                        MudTerrainLakeSettings.AIR, MudTerrainLakeSettings.AIR));

        assertEquals(vanillaDefaultShape(seed), shape);
    }

    @Test
    void upperCavityAndRandomBarrierStaySeparateFromTheFill() {
        MudTerrainLakeShape.Shape shape = MudTerrainLakeShape.build(
                new MudTerrainLakeSettings(8, 4, 991,
                        MudTerrainLakeSettings.AIR, MudTerrainLakeSettings.AIR));
        Set<BlockPos> interior = new HashSet<>(shape.interior());
        Set<BlockPos> cavity = new HashSet<>(shape.cavity());
        Set<BlockPos> volume = new HashSet<>(interior);
        volume.addAll(cavity);

        assertFalse(cavity.isEmpty());
        assertTrue(interior.stream().allMatch(pos -> pos.getY()
                <= MudTerrainLakeShape.LIQUID_SURFACE_Y));
        assertTrue(cavity.stream().allMatch(pos -> pos.getY()
                > MudTerrainLakeShape.LIQUID_SURFACE_Y));
        for (BlockPos shell : shape.shell()) {
            assertFalse(interior.contains(shell));
            assertFalse(cavity.contains(shell));
            assertTrue(java.util.Arrays.stream(Direction.values())
                    .map(shell::relative)
                    .anyMatch(volume::contains));
        }
    }

    @Test
    void seedChangesTheIrregularBasinWithoutChangingItsBounds() {
        MudTerrainLakeShape.Shape first = MudTerrainLakeShape.build(
                new MudTerrainLakeSettings(10, 5, 1181,
                        MudTerrainLakeSettings.AIR, MudTerrainLakeSettings.AIR));
        MudTerrainLakeShape.Shape second = MudTerrainLakeShape.build(
                new MudTerrainLakeSettings(10, 5, 1182,
                        MudTerrainLakeSettings.AIR, MudTerrainLakeSettings.AIR));

        assertNotEquals(first, second);
        assertTrue(first.interior().size() > 100);
        assertTrue(second.interior().size() > 100);
    }

    @Test
    void surfaceLakeOpensTheShellAboveItsLiquidPlane() {
        MudTerrainLakeShape.Shape shape = MudTerrainLakeShape.build(
                new MudTerrainLakeSettings(8, 4, 1181,
                        MudTerrainLakeSettings.AIR,
                        MudTerrainLakeSettings.AIR));
        List<BlockPos> undergroundShell = shape.shell().stream()
                .filter(pos -> MudTerrainLakeShape.includesShell(
                        MudTerrainGenerationType.LAKE_POOL, pos))
                .toList();
        List<BlockPos> surfaceShell = shape.shell().stream()
                .filter(pos -> MudTerrainLakeShape.includesShell(
                        MudTerrainGenerationType.LAKE_SURFACE, pos))
                .toList();

        assertEquals(shape.shell(), undergroundShell);
        assertFalse(surfaceShell.isEmpty());
        assertTrue(undergroundShell.size() > surfaceShell.size());
        assertTrue(undergroundShell.containsAll(surfaceShell));
        assertTrue(surfaceShell.stream().allMatch(pos ->
                pos.getY() <= MudTerrainLakeShape.LIQUID_SURFACE_Y));
    }

    @Test
    void everyExposedInteriorColumnHasOneSurfaceCell() {
        MudTerrainLakeShape.Shape shape = MudTerrainLakeShape.build(
                new MudTerrainLakeSettings(8, 4, 1181,
                        MudTerrainLakeSettings.AIR,
                        MudTerrainLakeSettings.AIR));
        Set<Long> interior = shape.interior().stream()
                .map(BlockPos::asLong).collect(java.util.stream.Collectors.toSet());
        Set<Long> surface = MudTerrainLakeShape.surfaceInterior(shape);

        assertFalse(surface.isEmpty());
        assertTrue(interior.containsAll(surface));
        assertTrue(surface.stream().map(BlockPos::of).allMatch(pos ->
                !interior.contains(pos.above().asLong())));
        assertEquals(14, MudTerrainLakeSettings.DEFAULT_SURFACE_HEIGHT_PIXELS);
    }

    @Test
    void disconnectedInteriorSegmentsStillProduceOneTopCellPerColumn() {
        MudTerrainLakeShape.Shape shape = new MudTerrainLakeShape.Shape(
                List.of(BlockPos.ZERO, BlockPos.ZERO.above(2)),
                List.of(), List.of());

        assertEquals(Set.of(BlockPos.ZERO.above(2).asLong()),
                MudTerrainLakeShape.surfaceInterior(shape));
    }

    @Test
    void minimumAndMaximumSizesStillProduceBoundedVolumes() {
        for (MudTerrainLakeSettings settings : List.of(
                new MudTerrainLakeSettings(2, 1, 37,
                        MudTerrainLakeSettings.AIR, MudTerrainLakeSettings.AIR),
                new MudTerrainLakeSettings(24, 12, 37,
                        MudTerrainLakeSettings.AIR, MudTerrainLakeSettings.AIR))) {
            MudTerrainLakeShape.Shape shape = MudTerrainLakeShape.build(settings);
            assertFalse(shape.interior().isEmpty());
            assertTrue(shape.interior().stream().allMatch(pos ->
                    Math.abs(pos.getX()) <= settings.horizontalRadius()
                            && Math.abs(pos.getY()) <= settings.verticalRadius()
                            && Math.abs(pos.getZ()) <= settings.horizontalRadius()));
        }
    }

    @Test
    void generationTypeCyclesInBothDirections() {
        assertEquals(List.of(
                        MudTerrainGenerationType.LAKE_POOL,
                        MudTerrainGenerationType.LAKE_SURFACE,
                        MudTerrainGenerationType.RIVERBANK_CRESCENT,
                        MudTerrainGenerationType.RIVERBED_RIBBON,
                        MudTerrainGenerationType.DUNE_BLOWOUT,
                        MudTerrainGenerationType.MARSH_MOSAIC,
                        MudTerrainGenerationType.CAVE_SEEP,
                        MudTerrainGenerationType.VOLCANIC_FISSURE,
                        MudTerrainGenerationType.END_IMPACT_RING,
                        MudTerrainGenerationType.ORGANIC_NEST),
                MudTerrainGenerationType.selectableValues());
        assertEquals(MudTerrainGenerationType.LAKE_POOL,
                MudTerrainGenerationType.SURFACE_DEPOSIT.cycle(1));
        assertEquals(MudTerrainGenerationType.LAKE_POOL,
                MudTerrainGenerationType.SURFACE_DEPOSIT.cycle(-1));
        assertEquals(MudTerrainGenerationType.LAKE_SURFACE,
                MudTerrainGenerationType.LAKE_POOL.cycle(1));
        assertEquals(MudTerrainGenerationType.RIVERBANK_CRESCENT,
                MudTerrainGenerationType.LAKE_SURFACE.cycle(1));
        assertEquals(MudTerrainGenerationType.ORGANIC_NEST,
                MudTerrainGenerationType.LAKE_POOL.cycle(-1));
        assertEquals(MudTerrainGenerationType.LAKE_SURFACE,
                MudTerrainGenerationType.byId(
                        MudTerrainGenerationType.LAKE_SURFACE.ordinal()));
        assertEquals(MudTerrainGenerationType.LAKE_POOL,
                MudTerrainGenerationType.byId(
                        MudTerrainGenerationType.SURFACE_DEPOSIT.ordinal()));
    }

    @Test
    void upperCavityClearingCannotBeDisabledByLegacyRequests() {
        MudTerrainLakeSettings defaults = new MudTerrainLakeSettings(
                8, 4, 991,
                MudTerrainLakeSettings.AIR, MudTerrainLakeSettings.AIR);
        MudTerrainLakeSettings legacyDisabled = new MudTerrainLakeSettings(
                8, 4, 991,
                MudTerrainLakeSettings.AIR, MudTerrainLakeSettings.AIR, false);

        assertTrue(defaults.clearUpperCavity());
        assertTrue(legacyDisabled.clearUpperCavity());
    }

    @Test
    void invalidWireValuesAreNotSilentlyClamped() {
        MudTerrainGenerationRequest request = new MudTerrainGenerationRequest(
                MudTerrainGenerationType.LAKE_POOL, BlockPos.ZERO, true,
                new MudTerrainGenerationSettings(99, 0, 2.0D, 99, -1, false),
                new MudTerrainLakeSettings(99, 0, -1,
                        MudTerrainLakeSettings.AIR, MudTerrainLakeSettings.AIR),
                false);

        assertFalse(request.validWireValues());
        assertEquals(48, request.depositSettings().radius());
        assertEquals(24, request.lakeSettings().horizontalRadius());
    }

    private static MudTerrainLakeShape.Shape vanillaDefaultShape(int seed) {
        boolean[] volume = new boolean[16 * 16 * 8];
        RandomSource random = RandomSource.create(seed);
        int ellipsoidCount = random.nextInt(4) + 4;
        for (int index = 0; index < ellipsoidCount; index++) {
            double diameterX = random.nextDouble() * 6.0D + 3.0D;
            double diameterY = random.nextDouble() * 4.0D + 2.0D;
            double diameterZ = random.nextDouble() * 6.0D + 3.0D;
            double centerX = random.nextDouble() * (16.0D - diameterX - 2.0D)
                    + 1.0D + diameterX / 2.0D;
            double centerY = random.nextDouble() * (8.0D - diameterY - 4.0D)
                    + 2.0D + diameterY / 2.0D;
            double centerZ = random.nextDouble() * (16.0D - diameterZ - 2.0D)
                    + 1.0D + diameterZ / 2.0D;
            for (int x = 1; x < 15; x++) {
                for (int z = 1; z < 15; z++) {
                    for (int y = 1; y < 7; y++) {
                        double dx = (x - centerX) / (diameterX / 2.0D);
                        double dy = (y - centerY) / (diameterY / 2.0D);
                        double dz = (z - centerZ) / (diameterZ / 2.0D);
                        if (dx * dx + dy * dy + dz * dz < 1.0D) {
                            volume[vanillaIndex(x, y, z)] = true;
                        }
                    }
                }
            }
        }

        Set<BlockPos> interior = new HashSet<>();
        Set<BlockPos> cavity = new HashSet<>();
        Set<BlockPos> shell = new HashSet<>();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < 8; y++) {
                    BlockPos pos = new BlockPos(x - 8, y - 3, z - 8);
                    if (volume[vanillaIndex(x, y, z)]) {
                        (y < 4 ? interior : cavity).add(pos);
                    } else if (vanillaBorder(volume, x, y, z)
                            && (y < 4 || random.nextInt(2) != 0)) {
                        shell.add(pos);
                    }
                }
            }
        }
        return new MudTerrainLakeShape.Shape(
                sorted(interior), sorted(cavity), sorted(shell));
    }

    private static boolean vanillaBorder(
            boolean[] volume, int x, int y, int z) {
        return x < 15 && volume[vanillaIndex(x + 1, y, z)]
                || x > 0 && volume[vanillaIndex(x - 1, y, z)]
                || z < 15 && volume[vanillaIndex(x, y, z + 1)]
                || z > 0 && volume[vanillaIndex(x, y, z - 1)]
                || y < 7 && volume[vanillaIndex(x, y + 1, z)]
                || y > 0 && volume[vanillaIndex(x, y - 1, z)];
    }

    private static int vanillaIndex(int x, int y, int z) {
        return (x * 16 + z) * 8 + y;
    }

    private static List<BlockPos> sorted(Set<BlockPos> positions) {
        return positions.stream()
                .sorted(java.util.Comparator
                        .comparingInt((BlockPos pos) -> pos.getY())
                        .thenComparingInt(BlockPos::getZ)
                        .thenComparingInt(BlockPos::getX))
                .toList();
    }
}

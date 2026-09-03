package com.fish.mirebound.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fish.mirebound.network.payload.MudTuningSelectionPayload;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class MudTuningSectionHighlightCacheTest {
    @Test
    void floatNetworkRoundTripDoesNotCreateAMixedHighlight() {
        double baseline = 0.055D;

        assertTrue(MudTuningSectionHighlightCache.sameSyncedValue(
                (double) (float) baseline, baseline));
        assertFalse(MudTuningSectionHighlightCache.sameSyncedValue(0.065D, baseline));
    }

    @Test
    void dirtySectionKeepsOldGeometryUntilReplacementIsBuilt() throws Exception {
        MudTuningSectionHighlightCache.SectionKey key =
                new MudTuningSectionHighlightCache.SectionKey(2, 3, 4);
        MudTuningSectionHighlightGeometry.SectionGeometry original =
                MudTuningSectionHighlightGeometry.empty(key);
        Map<MudTuningSectionHighlightCache.SectionKey,
                MudTuningSectionHighlightGeometry.SectionGeometry> geometry =
                staticField("GEOMETRY");
        Set<MudTuningSectionHighlightCache.SectionKey> planned = staticField("PLANNED");
        Set<MudTuningSectionHighlightCache.SectionKey> dirty = staticField("DIRTY");
        Set<MudTuningSectionHighlightCache.SectionKey> queued = staticField("QUEUED");
        Method markDirty = MudTuningSectionHighlightCache.class
                .getDeclaredMethod("markDirty", MudTuningSectionHighlightCache.SectionKey.class);
        markDirty.setAccessible(true);

        try {
            geometry.put(key, original);
            planned.add(key);

            markDirty.invoke(null, key);

            assertSame(original, geometry.get(key));
            assertTrue(dirty.contains(key));
            assertTrue(queued.contains(key));
        } finally {
            MudTuningSectionHighlightCache.reset();
        }
    }

    @Test
    void largeSelectionIntersectsEveryCoveredSectionWithoutAHighlightRadiusCap() {
        MudTuningSectionHighlightCache.SelectionBounds selection =
                new MudTuningSectionHighlightCache.SelectionBounds(
                        true, new BlockPos(-1_000, -32, -1_000),
                        new BlockPos(1_000, 255, 1_000));

        assertTrue(selection.intersects(
                new MudTuningSectionHighlightCache.SectionKey(-62, -2, -62)));
        assertTrue(selection.intersects(
                new MudTuningSectionHighlightCache.SectionKey(62, 15, 62)));
        assertFalse(selection.intersects(
                new MudTuningSectionHighlightCache.SectionKey(63, 0, 0)));
    }

    @Test
    void negativeSectionOwnershipUsesFloorBasedSectionCoordinates() {
        MudTuningSectionHighlightCache.SectionKey section =
                new MudTuningSectionHighlightCache.SectionKey(-1, -1, -1);

        assertTrue(section.contains(new BlockPos(-1, -1, -1)));
        assertTrue(section.contains(new BlockPos(-16, -16, -16)));
        assertFalse(section.contains(BlockPos.ZERO));
    }

    @Test
    void incompatibleFacesCullAcrossSectionBoundaries() {
        BlockPos left = new BlockPos(15, 0, 0);
        BlockPos right = new BlockPos(16, 0, 0);
        Map<Long, MudTuningSelectionPayload.HighlightKind> world = classified(left, right);

        MudTuningSectionHighlightGeometry.KindGeometry leftGeometry = compile(
                new MudTuningSectionHighlightCache.SectionKey(0, 0, 0), left, world);
        MudTuningSectionHighlightGeometry.KindGeometry rightGeometry = compile(
                new MudTuningSectionHighlightCache.SectionKey(1, 0, 0), right, world);

        assertEquals(5, leftGeometry.faces().size());
        assertEquals(5, rightGeometry.faces().size());
        assertFalse(leftGeometry.faces().contains(
                new MudTuningConnectedHighlightRenderer.FaceKey(left, Direction.EAST)));
        assertFalse(rightGeometry.faces().contains(
                new MudTuningConnectedHighlightRenderer.FaceKey(right, Direction.WEST)));
    }

    @Test
    void diagonalEdgesHaveOneOwnerAcrossEverySectionAxis() {
        assertUniqueEdgeOwner(
                new BlockPos(15, 0, 0), new BlockPos(16, 1, 0),
                new MudTuningSectionHighlightCache.SectionKey(0, 0, 0),
                new MudTuningSectionHighlightCache.SectionKey(1, 0, 0),
                new MudTuningConnectedHighlightRenderer.EdgeRun(2, 16, 1, 0, 1));
        assertUniqueEdgeOwner(
                new BlockPos(0, 15, 0), new BlockPos(1, 16, 0),
                new MudTuningSectionHighlightCache.SectionKey(0, 0, 0),
                new MudTuningSectionHighlightCache.SectionKey(0, 1, 0),
                new MudTuningConnectedHighlightRenderer.EdgeRun(2, 1, 16, 0, 1));
        assertUniqueEdgeOwner(
                new BlockPos(0, 0, 15), new BlockPos(1, 0, 16),
                new MudTuningSectionHighlightCache.SectionKey(0, 0, 0),
                new MudTuningSectionHighlightCache.SectionKey(0, 0, 1),
                new MudTuningConnectedHighlightRenderer.EdgeRun(1, 1, 0, 16, 1));
    }

    private static void assertUniqueEdgeOwner(BlockPos first, BlockPos second,
            MudTuningSectionHighlightCache.SectionKey firstSection,
            MudTuningSectionHighlightCache.SectionKey secondSection,
            MudTuningConnectedHighlightRenderer.EdgeRun expected) {
        Map<Long, MudTuningSelectionPayload.HighlightKind> world = classified(first, second);
        MudTuningSectionHighlightGeometry.KindGeometry firstGeometry =
                compile(firstSection, first, world);
        MudTuningSectionHighlightGeometry.KindGeometry secondGeometry =
                compile(secondSection, second, world);

        long occurrences = firstGeometry.edges().stream().filter(expected::equals).count()
                + secondGeometry.edges().stream().filter(expected::equals).count();
        assertEquals(1, occurrences);
        assertTrue(firstGeometry.edges().contains(expected));
        assertFalse(secondGeometry.edges().contains(expected));
    }

    private static MudTuningSectionHighlightGeometry.KindGeometry compile(
            MudTuningSectionHighlightCache.SectionKey section, BlockPos local,
            Map<Long, MudTuningSelectionPayload.HighlightKind> world) {
        EnumMap<MudTuningSelectionPayload.HighlightKind, Set<Long>> positions =
                new EnumMap<>(MudTuningSelectionPayload.HighlightKind.class);
        positions.put(MudTuningSelectionPayload.HighlightKind.INCOMPATIBLE,
                Set.of(local.asLong()));
        return MudTuningSectionHighlightGeometry.compile(
                section, positions, pos -> world.get(pos.asLong())).kinds().getFirst();
    }

    private static Map<Long, MudTuningSelectionPayload.HighlightKind> classified(
            BlockPos... positions) {
        Map<Long, MudTuningSelectionPayload.HighlightKind> world = new HashMap<>();
        for (BlockPos pos : positions) {
            world.put(pos.asLong(), MudTuningSelectionPayload.HighlightKind.INCOMPATIBLE);
        }
        return world;
    }

    @SuppressWarnings("unchecked")
    private static <T> T staticField(String name) throws ReflectiveOperationException {
        Field field = MudTuningSectionHighlightCache.class.getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(null);
    }
}

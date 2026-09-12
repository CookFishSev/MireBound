package com.fish.mirebound.stain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fish.mirebound.mud.SinkingMedium;
import java.util.List;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class WallStainCornerWrapTest {
    @Test
    void wrappingPreservesBothMediaAndTheirMixAndCreationTime() {
        long first = MudFootprintBlockEntity.packWallPixel(0, 7, 1, SinkingMedium.MUD, 80);
        long second = MudFootprintBlockEntity.packWallPixel(0, 7, 0.7F, SinkingMedium.TAR, 90);
        long mixed = MudFootprintBlockEntity.mergeWallPixels(new long[] {first}, new long[] {second})[0];
        WallStainCornerWrap.WrappedFace west = face(WallStainCornerWrap.build(
                Direction.NORTH, new long[] {mixed}, 2, 0, 0.8F, 0, 99, 0), Direction.WEST);
        long wrapped = pixel(west, 0, 7);
        assertEquals(MudFootprintBlockEntity.wallPixelMedium(mixed), MudFootprintBlockEntity.wallPixelMedium(wrapped));
        assertEquals(MudFootprintBlockEntity.wallPixelSecondaryMedium(mixed), MudFootprintBlockEntity.wallPixelSecondaryMedium(wrapped));
        assertEquals(MudFootprintBlockEntity.wallPixelSecondaryWeight(mixed), MudFootprintBlockEntity.wallPixelSecondaryWeight(wrapped));
        assertEquals(MudFootprintBlockEntity.wallPixelCreatedAt(mixed), MudFootprintBlockEntity.wallPixelCreatedAt(wrapped));
    }

    @Test
    void everyFaceMapsItsFourEdgesOntoAdjacentFacesWithoutMirroringAlongTheEdge() {
        for (Direction source : Direction.values()) {
            for (boolean horizontal : new boolean[] {false, true}) {
                for (int boundary : new int[] {0, 15}) {
                    int u = horizontal ? boundary : 7;
                    int v = horizontal ? 7 : boundary;
                    var wrapped = WallStainCornerWrap.build(source,
                            new long[] {MudFootprintBlockEntity.packWallPixel(u, v, 1, SinkingMedium.MUD, 40)},
                            1, 0, 0.8F, 0, 40, 0);
                    assertEquals(1, wrapped.size());
                    Direction target = wrapped.getFirst().face();
                    assertTrue(source.getAxis() != target.getAxis());
                    assertEquals(boundary == 0 ? -1 : 1, target.getAxisDirection().getStep());
                    long pixel = wrapped.getFirst().pixels()[0];
                    int[] point = new int[3];
                    point[target.getAxis().ordinal()] = boundary;
                    point[target.getAxis() == Direction.Axis.X ? 2 : 0] = MudFootprintBlockEntity.wallPixelHorizontal(pixel);
                    point[target.getAxis() == Direction.Axis.Y ? 2 : 1] = MudFootprintBlockEntity.wallPixelVertical(pixel);
                    assertEquals(source.getAxisDirection().getStep() > 0 ? 15 : 0, point[source.getAxis().ordinal()]);
                    for (int axis = 0; axis < 3; axis++) {
                        if (axis != target.getAxis().ordinal() && axis != source.getAxis().ordinal()) assertEquals(7, point[axis]);
                    }
                }
            }
        }
    }

    @Test
    void northFaceWrapsAroundItsWestEdgeInLocalCoordinates() {
        WallStainCornerWrap.WrappedFace west = face(
                WallStainCornerWrap.build(
                        Direction.NORTH, verticalEdgePixels(0, 6, 8),
                        3, 0.18F, 0.80F, 0.0F, 40L, 12L),
                Direction.WEST);

        assertTrue(pixel(west, 0, 7) != 0L);
        assertTrue(pixel(west, 1, 7) != 0L);
        assertTrue(pixel(west, 2, 7) != 0L);
        assertTrue(MudFootprintBlockEntity.wallPixelStrength(pixel(west, 0, 7))
                > MudFootprintBlockEntity.wallPixelStrength(pixel(west, 1, 7)));
    }

    @Test
    void southFaceUsesTheOppositeSideOfTheSameTargetFace() {
        WallStainCornerWrap.WrappedFace west = face(
                WallStainCornerWrap.build(
                        Direction.SOUTH, verticalEdgePixels(0, 6, 8),
                        3, 0.18F, 0.80F, 0.0F, 40L, 12L),
                Direction.WEST);

        assertTrue(pixel(west, 15, 7) != 0L);
        assertTrue(pixel(west, 14, 7) != 0L);
        assertTrue(pixel(west, 13, 7) != 0L);
        assertEquals(0L, pixel(west, 0, 7));
    }

    @Test
    void upperEdgeKeepsItsAlongEdgeCoordinate() {
        long[] source = new long[3];
        for (int x = 6; x <= 8; x++) {
            source[x - 6] = MudFootprintBlockEntity.packWallPixel(
                    x, 15, 1.0F, SinkingMedium.MUD, 80L);
        }

        WallStainCornerWrap.WrappedFace top = face(
                WallStainCornerWrap.build(
                        Direction.NORTH, source,
                        3, 0.18F, 0.80F, 0.0F, 80L, 24L),
                Direction.UP);

        assertTrue(pixel(top, 7, 0) != 0L);
        assertTrue(pixel(top, 7, 1) != 0L);
        assertTrue(pixel(top, 7, 2) != 0L);
    }

    @Test
    void interiorPixelsDoNotCreateAnotherFace() {
        long interior = MudFootprintBlockEntity.packWallPixel(
                7, 7, 1.0F, SinkingMedium.MUD, 120L);

        List<WallStainCornerWrap.WrappedFace> result = WallStainCornerWrap.build(
                Direction.NORTH, new long[] {interior},
                3, 0.18F, 0.80F, 0.0F, 120L, 36L);

        assertTrue(result.isEmpty());
    }

    private static long[] verticalEdgePixels(int x, int minY, int maxY) {
        long[] pixels = new long[maxY - minY + 1];
        for (int y = minY; y <= maxY; y++) {
            pixels[y - minY] = MudFootprintBlockEntity.packWallPixel(
                    x, y, 1.0F, SinkingMedium.MUD, 40L);
        }
        return pixels;
    }

    private static WallStainCornerWrap.WrappedFace face(
            List<WallStainCornerWrap.WrappedFace> faces, Direction direction) {
        return faces.stream()
                .filter(face -> face.face() == direction)
                .findFirst()
                .orElseThrow();
    }

    private static long pixel(WallStainCornerWrap.WrappedFace face, int x, int y) {
        for (long pixel : face.pixels()) {
            if (MudFootprintBlockEntity.wallPixelHorizontal(pixel) == x
                    && MudFootprintBlockEntity.wallPixelVertical(pixel) == y) {
                return pixel;
            }
        }
        return 0L;
    }
}

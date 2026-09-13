package com.fish.mirebound.mud;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * A bounded, server-authored horizontal pressure footprint.
 *
 * Coordinates are relative to the player's feet. Keeping the shape relative
 * lets clients apply it to their interpolated copy of the player without
 * reconstructing the pose locally.
 */
public record MudCompressionShape(
        double originX, double originY, double originZ,
        int surfaceOffsetMicros, List<Polygon> polygons) {
    public static final MudCompressionShape EMPTY =
            new MudCompressionShape(0.0D, 0.0D, 0.0D, 0, List.of());
    public static final int MAX_POLYGONS = 6;
    public static final int MAX_POINTS = 8;
    private static final int MAX_COORDINATE_MICROS = 100_000;

    public MudCompressionShape {
        originX = finiteOrZero(originX);
        originY = finiteOrZero(originY);
        originZ = finiteOrZero(originZ);
        surfaceOffsetMicros = Mth.clamp(surfaceOffsetMicros,
                -MAX_COORDINATE_MICROS, MAX_COORDINATE_MICROS);
        polygons = sanitizePolygons(polygons);
    }

    public boolean empty() {
        return polygons.isEmpty();
    }

    public Vec3 originOr(Vec3 fallback) {
        return empty() ? fallback : new Vec3(originX, originY, originZ);
    }

    public static MudCompressionShape from(
            MudEntityGeometry.PlaneSlice slice, Vec3 origin) {
        if (slice == null || slice.empty() || origin == null) {
            return EMPTY;
        }
        int surfaceOffset = Mth.clamp((int) Math.round(
                (slice.surfaceY() - origin.y) * 10_000.0D),
                -MAX_COORDINATE_MICROS, MAX_COORDINATE_MICROS);
        List<Polygon> result = new ArrayList<>(Math.min(MAX_POLYGONS, slice.polygons().size()));
        for (MudEntityGeometry.SlicePolygon polygon : slice.polygons()) {
            if (result.size() >= MAX_POLYGONS) {
                break;
            }
            List<Point> points = new ArrayList<>(polygon.vertices().size());
            for (Vec3 vertex : polygon.vertices()) {
                points.add(new Point(
                        quantize(vertex.x - origin.x),
                        quantize(vertex.z - origin.z)));
            }
            points = limitPoints(points);
            if (points.size() >= 3) {
                result.add(new Polygon(
                        polygon.part().ordinal(),
                        quantize(polygon.armorOffset()),
                        points));
            }
        }
        return result.isEmpty() ? EMPTY : new MudCompressionShape(
                origin.x, origin.y, origin.z, surfaceOffset, result);
    }

    /** Combines consecutive server slices before they cross the network. */
    public static MudCompressionShape swept(
            MudEntityGeometry.PlaneSlice previous, Vec3 previousOrigin,
            MudEntityGeometry.PlaneSlice current, Vec3 currentOrigin) {
        if (current == null || current.empty() || currentOrigin == null) {
            return EMPTY;
        }
        if (previous == null || previous.empty() || previousOrigin == null) {
            return from(current, currentOrigin);
        }
        List<MudEntityGeometry.SlicePolygon> merged = new ArrayList<>(MAX_POLYGONS);
        boolean[] usedPrevious = new boolean[previous.polygons().size()];
        for (MudEntityGeometry.SlicePolygon currentPolygon : current.polygons()) {
            List<Vec3> points = new ArrayList<>(currentPolygon.vertices().size() + MAX_POINTS);
            addAtHeight(points, currentPolygon.vertices(), current.surfaceY());
            int previousIndex = findPart(previous.polygons(), currentPolygon.part());
            if (previousIndex >= 0) {
                usedPrevious[previousIndex] = true;
                addAtHeight(points, previous.polygons().get(previousIndex).vertices(), current.surfaceY());
            }
            List<Vec3> hull = MudEntityGeometry.convexHull(points);
            if (hull.size() >= 3) {
                merged.add(new MudEntityGeometry.SlicePolygon(
                        currentPolygon.part(), hull,
                        Math.max(currentPolygon.armorOffset(), previousIndex < 0
                                ? 0.0D : previous.polygons().get(previousIndex).armorOffset())));
            }
        }
        for (int index = 0; index < previous.polygons().size(); index++) {
            if (usedPrevious[index] || merged.size() >= MAX_POLYGONS) {
                continue;
            }
            MudEntityGeometry.SlicePolygon polygon = previous.polygons().get(index);
            List<Vec3> points = new ArrayList<>(polygon.vertices().size());
            addAtHeight(points, polygon.vertices(), current.surfaceY());
            List<Vec3> hull = MudEntityGeometry.convexHull(points);
            if (hull.size() >= 3) {
                merged.add(new MudEntityGeometry.SlicePolygon(
                        polygon.part(), hull, polygon.armorOffset()));
            }
        }
        return from(new MudEntityGeometry.PlaneSlice(current.surfaceY(), merged), currentOrigin);
    }

    public MudEntityGeometry.PlaneSlice toPlaneSlice(Vec3 origin) {
        if (empty() || origin == null) {
            return new MudEntityGeometry.PlaneSlice(0.0D, List.of());
        }
        Vec3 authoritativeOrigin = new Vec3(originX, originY, originZ);
        double surfaceY = authoritativeOrigin.y + surfaceOffsetMicros / 10_000.0D;
        List<MudEntityGeometry.SlicePolygon> result = new ArrayList<>(polygons.size());
        for (Polygon polygon : polygons) {
            MudBodyPart[] parts = MudBodyPart.values();
            int partId = Mth.clamp(polygon.partId(), 0, parts.length - 1);
            List<Vec3> vertices = new ArrayList<>(polygon.points().size());
            for (Point point : polygon.points()) {
                vertices.add(new Vec3(
                        authoritativeOrigin.x + point.xMicros() / 10_000.0D,
                        surfaceY,
                        authoritativeOrigin.z + point.zMicros() / 10_000.0D));
            }
            result.add(new MudEntityGeometry.SlicePolygon(
                    parts[partId], List.copyOf(vertices),
                    polygon.armorOffsetMicros() / 10_000.0D));
        }
        return new MudEntityGeometry.PlaneSlice(surfaceY, List.copyOf(result));
    }

    public void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeDouble(originX);
        buffer.writeDouble(originY);
        buffer.writeDouble(originZ);
        buffer.writeVarInt(surfaceOffsetMicros);
        buffer.writeVarInt(polygons.size());
        for (Polygon polygon : polygons) {
            buffer.writeVarInt(polygon.partId());
            buffer.writeVarInt(polygon.armorOffsetMicros());
            buffer.writeVarInt(polygon.points().size());
            for (Point point : polygon.points()) {
                buffer.writeVarInt(point.xMicros());
                buffer.writeVarInt(point.zMicros());
            }
        }
    }

    public static MudCompressionShape read(RegistryFriendlyByteBuf buffer) {
        double originX = buffer.readDouble();
        double originY = buffer.readDouble();
        double originZ = buffer.readDouble();
        int surfaceOffset = boundedCoordinate(buffer.readVarInt());
        int polygonCount = buffer.readVarInt();
        if (polygonCount < 0 || polygonCount > MAX_POLYGONS) {
            throw new IllegalArgumentException("Invalid mud compression polygon count: " + polygonCount);
        }
        List<Polygon> polygons = new ArrayList<>(polygonCount);
        for (int polygonIndex = 0; polygonIndex < polygonCount; polygonIndex++) {
            int partId = buffer.readVarInt();
            int armorOffset = boundedCoordinate(buffer.readVarInt());
            int pointCount = buffer.readVarInt();
            if (pointCount < 3 || pointCount > MAX_POINTS) {
                throw new IllegalArgumentException("Invalid mud compression point count: " + pointCount);
            }
            List<Point> points = new ArrayList<>(pointCount);
            for (int pointIndex = 0; pointIndex < pointCount; pointIndex++) {
                points.add(new Point(
                        boundedCoordinate(buffer.readVarInt()),
                        boundedCoordinate(buffer.readVarInt())));
            }
            polygons.add(new Polygon(partId, armorOffset, points));
        }
        return new MudCompressionShape(
                originX, originY, originZ,
                surfaceOffset, polygons);
    }

    private static List<Polygon> sanitizePolygons(List<Polygon> input) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }
        List<Polygon> result = new ArrayList<>(Math.min(MAX_POLYGONS, input.size()));
        for (Polygon polygon : input) {
            if (polygon == null || result.size() >= MAX_POLYGONS) {
                break;
            }
            List<Point> points = polygon.points() == null
                    ? List.of() : polygon.points().stream()
                            .filter(point -> point != null)
                            .toList();
            points = limitPoints(points);
            if (points.size() >= 3) {
                result.add(new Polygon(
                        Mth.clamp(polygon.partId(), 0, MudBodyPart.COUNT - 1),
                        Mth.clamp(polygon.armorOffsetMicros(),
                                -MAX_COORDINATE_MICROS, MAX_COORDINATE_MICROS),
                        points));
            }
        }
        return List.copyOf(result);
    }

    private static int quantize(double value) {
        return boundedCoordinate((int) Math.round(value * 10_000.0D));
    }

    private static List<Point> limitPoints(List<Point> input) {
        if (input.size() <= MAX_POINTS) {
            return List.copyOf(input);
        }
        List<Point> result = new ArrayList<>(MAX_POINTS);
        for (int index = 0; index < MAX_POINTS; index++) {
            int source = (int) ((long) index * input.size() / MAX_POINTS);
            result.add(input.get(source));
        }
        return List.copyOf(result);
    }

    private static void addAtHeight(List<Vec3> target, List<Vec3> source, double y) {
        for (Vec3 point : source) {
            target.add(new Vec3(point.x, y, point.z));
        }
    }

    private static int findPart(
            List<MudEntityGeometry.SlicePolygon> polygons, MudBodyPart part) {
        for (int index = 0; index < polygons.size(); index++) {
            if (polygons.get(index).part() == part) {
                return index;
            }
        }
        return -1;
    }

    private static int boundedCoordinate(int value) {
        return Mth.clamp(value, -MAX_COORDINATE_MICROS, MAX_COORDINATE_MICROS);
    }

    private static double finiteOrZero(double value) {
        return Double.isFinite(value) ? value : 0.0D;
    }

    public record Polygon(int partId, int armorOffsetMicros, List<Point> points) {
    }

    public record Point(int xMicros, int zMicros) {
    }
}

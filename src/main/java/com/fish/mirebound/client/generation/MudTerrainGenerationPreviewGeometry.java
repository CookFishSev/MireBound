package com.fish.mirebound.client.generation;

import com.fish.mirebound.mud.tuning.MudTuningHighlightGeometry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/** Cached exposed faces and merged boundary edges for a voxel preview. */
final class MudTerrainGenerationPreviewGeometry {
    private static final int MAXIMUM_EDGE_COUNT = 240_000;
    private static final int INTERIOR = 1;
    private static final int SHELL = 1 << 1;
    private static final int CAVITY = 1 << 2;

    private MudTerrainGenerationPreviewGeometry() {
    }

    static Geometry build(Set<Long> occupied) {
        return build(occupied, Set.of(), Set.of());
    }

    static Geometry build(
            Set<Long> interior, Set<Long> cavity, Set<Long> shell) {
        Set<Long> occupied = new HashSet<>(
                interior.size() + cavity.size() + shell.size());
        occupied.addAll(interior);
        occupied.addAll(cavity);
        occupied.addAll(shell);
        if (occupied.isEmpty()) {
            return Geometry.EMPTY;
        }
        List<Face> faces = new ArrayList<>();
        for (long packed : occupied) {
            BlockPos pos = BlockPos.of(packed);
            for (Direction direction : Direction.values()) {
                if (!occupied.contains(pos.relative(direction).asLong())) {
                    faces.add(new Face(pos, direction));
                }
            }
        }
        MudTuningHighlightGeometry.Result visible =
                MudTuningHighlightGeometry.visibleEdges(
                        occupied, MAXIMUM_EDGE_COUNT);
        List<EdgeRun> edges = visible.complete()
                ? classifiedEdges(visible.edges(), faces, cavity, shell)
                : List.of();
        return new Geometry(List.copyOf(faces), edges, bounds(occupied));
    }

    private static List<EdgeRun> classifiedEdges(
            List<MudTuningHighlightGeometry.Edge> visible,
            List<Face> faces, Set<Long> cavity, Set<Long> shell) {
        Map<MudTuningHighlightGeometry.Edge, Integer> faceCategories =
                new HashMap<>(Math.min(MAXIMUM_EDGE_COUNT, faces.size() * 2));
        for (Face face : faces) {
            long packed = face.pos().asLong();
            int category = cavity.contains(packed)
                    ? CAVITY : shell.contains(packed) ? SHELL : INTERIOR;
            addFaceEdges(faceCategories, face, category);
        }

        Set<MudTuningHighlightGeometry.Edge> selected = new HashSet<>();
        for (MudTuningHighlightGeometry.Edge edge : visible) {
            if ((faceCategories.getOrDefault(edge, 0) & CAVITY) == 0) {
                selected.add(edge);
            }
        }
        for (Map.Entry<MudTuningHighlightGeometry.Edge, Integer> entry
                : faceCategories.entrySet()) {
            int categories = entry.getValue();
            if ((categories & CAVITY) == 0
                    && (categories & INTERIOR) != 0
                    && (categories & SHELL) != 0) {
                selected.add(entry.getKey());
            }
        }
        return selected.size() <= MAXIMUM_EDGE_COUNT
                ? mergeEdges(List.copyOf(selected)) : List.of();
    }

    private static void addFaceEdges(
            Map<MudTuningHighlightGeometry.Edge, Integer> categories,
            Face face, int category) {
        BlockPos pos = face.pos();
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        switch (face.direction()) {
            case DOWN, UP -> {
                int planeY = y + (face.direction() == Direction.UP ? 1 : 0);
                addEdge(categories, new MudTuningHighlightGeometry.Edge(
                        0, x, planeY, z), category);
                addEdge(categories, new MudTuningHighlightGeometry.Edge(
                        0, x, planeY, z + 1), category);
                addEdge(categories, new MudTuningHighlightGeometry.Edge(
                        2, x, planeY, z), category);
                addEdge(categories, new MudTuningHighlightGeometry.Edge(
                        2, x + 1, planeY, z), category);
            }
            case NORTH, SOUTH -> {
                int planeZ = z + (face.direction() == Direction.SOUTH ? 1 : 0);
                addEdge(categories, new MudTuningHighlightGeometry.Edge(
                        0, x, y, planeZ), category);
                addEdge(categories, new MudTuningHighlightGeometry.Edge(
                        0, x, y + 1, planeZ), category);
                addEdge(categories, new MudTuningHighlightGeometry.Edge(
                        1, x, y, planeZ), category);
                addEdge(categories, new MudTuningHighlightGeometry.Edge(
                        1, x + 1, y, planeZ), category);
            }
            case WEST, EAST -> {
                int planeX = x + (face.direction() == Direction.EAST ? 1 : 0);
                addEdge(categories, new MudTuningHighlightGeometry.Edge(
                        2, planeX, y, z), category);
                addEdge(categories, new MudTuningHighlightGeometry.Edge(
                        2, planeX, y + 1, z), category);
                addEdge(categories, new MudTuningHighlightGeometry.Edge(
                        1, planeX, y, z), category);
                addEdge(categories, new MudTuningHighlightGeometry.Edge(
                        1, planeX, y, z + 1), category);
            }
        }
    }

    private static void addEdge(
            Map<MudTuningHighlightGeometry.Edge, Integer> categories,
            MudTuningHighlightGeometry.Edge edge, int category) {
        categories.merge(edge, category, (first, second) -> first | second);
    }

    static List<EdgeRun> mergeEdges(List<MudTuningHighlightGeometry.Edge> source) {
        if (source.isEmpty()) {
            return List.of();
        }
        List<MudTuningHighlightGeometry.Edge> edges = new ArrayList<>(source);
        edges.sort(Comparator.comparingInt(MudTuningHighlightGeometry.Edge::axis)
                .thenComparingInt(MudTerrainGenerationPreviewGeometry::fixedFirst)
                .thenComparingInt(MudTerrainGenerationPreviewGeometry::fixedSecond)
                .thenComparingInt(MudTerrainGenerationPreviewGeometry::coordinate));
        List<EdgeRun> merged = new ArrayList<>();
        MudTuningHighlightGeometry.Edge first = edges.getFirst();
        int start = coordinate(first);
        int end = start + 1;
        for (int index = 1; index < edges.size(); index++) {
            MudTuningHighlightGeometry.Edge next = edges.get(index);
            if (sameLine(first, next) && coordinate(next) <= end) {
                end = Math.max(end, coordinate(next) + 1);
                continue;
            }
            merged.add(run(first, start, end));
            first = next;
            start = coordinate(next);
            end = start + 1;
        }
        merged.add(run(first, start, end));
        return List.copyOf(merged);
    }

    private static boolean sameLine(
            MudTuningHighlightGeometry.Edge first,
            MudTuningHighlightGeometry.Edge second) {
        return first.axis() == second.axis()
                && fixedFirst(first) == fixedFirst(second)
                && fixedSecond(first) == fixedSecond(second);
    }

    private static int coordinate(MudTuningHighlightGeometry.Edge edge) {
        return switch (edge.axis()) {
            case 0 -> edge.x();
            case 1 -> edge.y();
            default -> edge.z();
        };
    }

    private static int fixedFirst(MudTuningHighlightGeometry.Edge edge) {
        return edge.axis() == 0 ? edge.y() : edge.x();
    }

    private static int fixedSecond(MudTuningHighlightGeometry.Edge edge) {
        return edge.axis() == 2 ? edge.y() : edge.z();
    }

    private static EdgeRun run(
            MudTuningHighlightGeometry.Edge edge, int start, int end) {
        return switch (edge.axis()) {
            case 0 -> new EdgeRun(0, start, edge.y(), edge.z(), end - start);
            case 1 -> new EdgeRun(1, edge.x(), start, edge.z(), end - start);
            default -> new EdgeRun(2, edge.x(), edge.y(), start, end - start);
        };
    }

    private static AABB bounds(Set<Long> occupied) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (long packed : occupied) {
            BlockPos pos = BlockPos.of(packed);
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        return new AABB(minX, minY, minZ,
                maxX + 1.0D, maxY + 1.0D, maxZ + 1.0D);
    }

    record Geometry(List<Face> faces, List<EdgeRun> edges, AABB bounds) {
        private static final Geometry EMPTY = new Geometry(
                List.of(), List.of(), new AABB(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D));
    }

    record Face(BlockPos pos, Direction direction) {
    }

    record EdgeRun(int axis, int x, int y, int z, int length) {
    }
}

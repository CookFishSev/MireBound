package com.fish.mirebound.mud.tuning;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import net.minecraft.core.BlockPos;

/** Builds the visible wire boundary of a voxel set without retaining interior edges. */
public final class MudTuningHighlightGeometry {
    private MudTuningHighlightGeometry() {
    }

    public static Result visibleEdges(Set<Long> positions, int limit) {
        if (positions.isEmpty() || limit <= 0) {
            return new Result(List.of(), positions.isEmpty());
        }
        List<Edge> visible = new ArrayList<>();
        Set<Edge> emitted = new HashSet<>();
        for (long packed : positions) {
            BlockPos pos = BlockPos.of(packed);
            if (isInterior(positions, pos)) {
                continue;
            }
            for (int first = 0; first <= 1; first++) {
                for (int second = 0; second <= 1; second++) {
                    if (!addEdge(positions, emitted, visible,
                            new Edge(0, pos.getX(), pos.getY() + first,
                                    pos.getZ() + second), limit)
                            || !addEdge(positions, emitted, visible,
                                    new Edge(1, pos.getX() + first, pos.getY(),
                                            pos.getZ() + second), limit)
                            || !addEdge(positions, emitted, visible,
                                    new Edge(2, pos.getX() + first, pos.getY() + second,
                                            pos.getZ()), limit)) {
                        return new Result(List.of(), false);
                    }
                }
            }
        }
        return new Result(List.copyOf(visible), true);
    }

    public static BudgetedResult fitToBudget(Set<Long> positions, BlockPos priorityCenter,
            int primitiveLimit, boolean includePositions) {
        if (positions.isEmpty() || primitiveLimit <= 0) {
            return BudgetedResult.EMPTY;
        }
        if (!includePositions) {
            Result full = visibleEdges(positions, primitiveLimit);
            if (full.complete()) {
                return new BudgetedResult(new long[0], full.edges());
            }
        }

        NearestPositions nearest = new NearestPositions(
                Math.min(positions.size(), primitiveLimit), priorityCenter);
        for (long packed : positions) {
            nearest.offer(BlockPos.of(packed));
        }
        long[] ordered = nearest.finish();
        int count = ordered.length;
        while (count > 0) {
            Set<Long> subset = new HashSet<>(count * 2);
            for (int index = 0; index < count; index++) {
                subset.add(ordered[index]);
            }
            int positionCount = includePositions ? count : 0;
            Result geometry = visibleEdges(subset, primitiveLimit - positionCount);
            if (geometry.complete()) {
                long[] kept = includePositions
                        ? java.util.Arrays.copyOf(ordered, count) : new long[0];
                return new BudgetedResult(kept, geometry.edges());
            }
            count = Math.min(count - 1, count * 3 / 4);
        }
        return BudgetedResult.EMPTY;
    }

    public static boolean edgeVisible(Set<Long> positions, Edge edge) {
        int count = 0;
        int first = -1;
        int second = -1;
        for (int a = 0; a < 2; a++) {
            for (int b = 0; b < 2; b++) {
                int index = a << 1 | b;
                BlockPos cell = switch (edge.axis) {
                    case 0 -> new BlockPos(edge.x, edge.y - 1 + a, edge.z - 1 + b);
                    case 1 -> new BlockPos(edge.x - 1 + a, edge.y, edge.z - 1 + b);
                    default -> new BlockPos(edge.x - 1 + a, edge.y - 1 + b, edge.z);
                };
                if (positions.contains(cell.asLong())) {
                    if (first < 0) {
                        first = index;
                    } else {
                        second = index;
                    }
                    count++;
                }
            }
        }
        if (count == 0 || count == 4) {
            return false;
        }
        return count != 2 || (first ^ second) == 3;
    }

    private static boolean addEdge(Set<Long> positions, Set<Edge> emitted,
            List<Edge> visible, Edge edge, int limit) {
        if (!edgeVisible(positions, edge) || !emitted.add(edge)) {
            return true;
        }
        if (visible.size() >= limit) {
            return false;
        }
        visible.add(edge);
        return true;
    }

    private static boolean isInterior(Set<Long> positions, BlockPos pos) {
        return positions.contains(pos.offset(-1, 0, 0).asLong())
                && positions.contains(pos.offset(1, 0, 0).asLong())
                && positions.contains(pos.offset(0, -1, 0).asLong())
                && positions.contains(pos.offset(0, 1, 0).asLong())
                && positions.contains(pos.offset(0, 0, -1).asLong())
                && positions.contains(pos.offset(0, 0, 1).asLong());
    }

    public record Edge(int axis, int x, int y, int z) {
        public Edge {
            if (axis < 0 || axis > 2) {
                throw new IllegalArgumentException("Highlight edge axis must be in [0, 2]");
            }
        }

        public long corner() {
            return BlockPos.asLong(x, y, z);
        }
    }

    public record Result(List<Edge> edges, boolean complete) {
    }

    public record BudgetedResult(long[] positions, List<Edge> edges) {
        private static final BudgetedResult EMPTY =
                new BudgetedResult(new long[0], List.of());

        public int primitiveCount() {
            return positions.length + edges.size();
        }
    }

    static final class NearestPositions {
        private static final Comparator<Candidate> FARTHEST_FIRST =
                Comparator.comparingLong(Candidate::distanceSquared).reversed()
                        .thenComparing(Comparator.comparingLong(Candidate::packed).reversed());
        private static final Comparator<Candidate> NEAREST_FIRST =
                Comparator.comparingLong(Candidate::distanceSquared)
                        .thenComparingLong(Candidate::packed);

        private final int limit;
        private final BlockPos center;
        private final PriorityQueue<Candidate> candidates = new PriorityQueue<>(FARTHEST_FIRST);

        NearestPositions(int limit, BlockPos center) {
            this.limit = Math.max(0, limit);
            this.center = center;
        }

        void offer(BlockPos pos) {
            if (limit == 0) {
                return;
            }
            Candidate candidate = new Candidate(pos.asLong(), distanceSquared(pos, center));
            if (candidates.size() < limit) {
                candidates.add(candidate);
                return;
            }
            Candidate farthest = candidates.peek();
            if (farthest != null && NEAREST_FIRST.compare(candidate, farthest) < 0) {
                candidates.poll();
                candidates.add(candidate);
            }
        }

        long[] finish() {
            return candidates.stream()
                    .sorted(NEAREST_FIRST)
                    .mapToLong(Candidate::packed)
                    .toArray();
        }

        private static long distanceSquared(BlockPos pos, BlockPos center) {
            if (center == null) {
                return 0L;
            }
            long dx = (long) pos.getX() - center.getX();
            long dy = (long) pos.getY() - center.getY();
            long dz = (long) pos.getZ() - center.getZ();
            return dx * dx + dy * dy + dz * dz;
        }

        private record Candidate(long packed, long distanceSquared) {
        }
    }
}

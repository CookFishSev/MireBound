package com.fish.mirebound.client;

import com.fish.mirebound.network.payload.MudTuningSelectionPayload;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/** Compiles one section's classified cells into seam-free faces and edge runs. */
final class MudTuningSectionHighlightGeometry {
    private MudTuningSectionHighlightGeometry() {
    }

    static SectionGeometry compile(MudTuningSectionHighlightCache.SectionKey section,
            EnumMap<MudTuningSelectionPayload.HighlightKind, Set<Long>> local,
            Function<BlockPos, MudTuningSelectionPayload.HighlightKind> classifier) {
        ClassificationLookup lookup = new ClassificationLookup(local, classifier);
        List<KindGeometry> kinds = new ArrayList<>();
        for (Map.Entry<MudTuningSelectionPayload.HighlightKind, Set<Long>> entry
                : local.entrySet()) {
            List<MudTuningConnectedHighlightRenderer.FaceKey> faces = new ArrayList<>();
            List<MudTuningConnectedHighlightRenderer.EdgeKey> edges = new ArrayList<>();
            Set<MudTuningConnectedHighlightRenderer.EdgeKey> emitted = new HashSet<>();
            for (long packed : entry.getValue()) {
                BlockPos pos = BlockPos.of(packed);
                if (entry.getKey() == MudTuningSelectionPayload.HighlightKind.INCOMPATIBLE) {
                    for (Direction direction : Direction.values()) {
                        if (lookup.kindAt(pos.relative(direction)) != entry.getKey()) {
                            faces.add(new MudTuningConnectedHighlightRenderer.FaceKey(
                                    pos, direction));
                        }
                    }
                }
                for (int first = 0; first <= 1; first++) {
                    for (int second = 0; second <= 1; second++) {
                        offerEdge(section, entry.getKey(), lookup, emitted, edges,
                                new MudTuningConnectedHighlightRenderer.EdgeKey(
                                        0, pos.getX(), pos.getY() + first,
                                        pos.getZ() + second));
                        offerEdge(section, entry.getKey(), lookup, emitted, edges,
                                new MudTuningConnectedHighlightRenderer.EdgeKey(
                                        1, pos.getX() + first, pos.getY(),
                                        pos.getZ() + second));
                        offerEdge(section, entry.getKey(), lookup, emitted, edges,
                                new MudTuningConnectedHighlightRenderer.EdgeKey(
                                        2, pos.getX() + first, pos.getY() + second,
                                        pos.getZ()));
                    }
                }
            }
            kinds.add(new KindGeometry(entry.getKey(), List.copyOf(faces),
                    MudTuningConnectedHighlightRenderer.mergeEdges(edges)));
        }
        return new SectionGeometry(section, List.copyOf(kinds), section.bounds());
    }

    static SectionGeometry empty(MudTuningSectionHighlightCache.SectionKey section) {
        return new SectionGeometry(section, List.of(), section.bounds());
    }

    private static void offerEdge(MudTuningSectionHighlightCache.SectionKey section,
            MudTuningSelectionPayload.HighlightKind kind, ClassificationLookup lookup,
            Set<MudTuningConnectedHighlightRenderer.EdgeKey> emitted,
            List<MudTuningConnectedHighlightRenderer.EdgeKey> edges,
            MudTuningConnectedHighlightRenderer.EdgeKey edge) {
        if (emitted.add(edge) && edgeVisible(lookup, kind, edge)
                && ownsEdge(section, lookup, kind, edge)) {
            edges.add(edge);
        }
    }

    private static boolean edgeVisible(ClassificationLookup lookup,
            MudTuningSelectionPayload.HighlightKind kind,
            MudTuningConnectedHighlightRenderer.EdgeKey edge) {
        int count = 0;
        int first = -1;
        int second = -1;
        for (int a = 0; a < 2; a++) {
            for (int b = 0; b < 2; b++) {
                int index = a << 1 | b;
                if (lookup.kindAt(edgeCell(edge, a, b)) == kind) {
                    if (first < 0) {
                        first = index;
                    } else {
                        second = index;
                    }
                    count++;
                }
            }
        }
        return count != 0 && count != 4 && (count != 2 || (first ^ second) == 3);
    }

    private static boolean ownsEdge(MudTuningSectionHighlightCache.SectionKey section,
            ClassificationLookup lookup, MudTuningSelectionPayload.HighlightKind kind,
            MudTuningConnectedHighlightRenderer.EdgeKey edge) {
        BlockPos owner = null;
        for (int a = 0; a < 2; a++) {
            for (int b = 0; b < 2; b++) {
                BlockPos cell = edgeCell(edge, a, b);
                if (lookup.kindAt(cell) == kind && (owner == null || compare(cell, owner) < 0)) {
                    owner = cell;
                }
            }
        }
        return owner != null && section.contains(owner);
    }

    private static BlockPos edgeCell(
            MudTuningConnectedHighlightRenderer.EdgeKey edge, int a, int b) {
        return switch (edge.axis()) {
            case 0 -> new BlockPos(edge.x(), edge.y() - 1 + a, edge.z() - 1 + b);
            case 1 -> new BlockPos(edge.x() - 1 + a, edge.y(), edge.z() - 1 + b);
            default -> new BlockPos(edge.x() - 1 + a, edge.y() - 1 + b, edge.z());
        };
    }

    private static int compare(BlockPos first, BlockPos second) {
        int x = Integer.compare(first.getX(), second.getX());
        int y = x == 0 ? Integer.compare(first.getY(), second.getY()) : x;
        return y == 0 ? Integer.compare(first.getZ(), second.getZ()) : y;
    }

    private static final class ClassificationLookup {
        private final Map<Long, MudTuningSelectionPayload.HighlightKind> kinds = new HashMap<>();
        private final Set<Long> classified = new HashSet<>();
        private final Function<BlockPos, MudTuningSelectionPayload.HighlightKind> classifier;

        private ClassificationLookup(
                EnumMap<MudTuningSelectionPayload.HighlightKind, Set<Long>> local,
                Function<BlockPos, MudTuningSelectionPayload.HighlightKind> classifier) {
            this.classifier = classifier;
            for (Map.Entry<MudTuningSelectionPayload.HighlightKind, Set<Long>> entry
                    : local.entrySet()) {
                for (long packed : entry.getValue()) {
                    classified.add(packed);
                    kinds.put(packed, entry.getKey());
                }
            }
        }

        private MudTuningSelectionPayload.HighlightKind kindAt(BlockPos pos) {
            long packed = pos.asLong();
            if (!classified.add(packed)) {
                return kinds.get(packed);
            }
            MudTuningSelectionPayload.HighlightKind kind = classifier.apply(pos);
            if (kind != null) {
                kinds.put(packed, kind);
            }
            return kind;
        }
    }

    record SectionGeometry(MudTuningSectionHighlightCache.SectionKey key,
            List<KindGeometry> kinds, AABB bounds) {
    }

    record KindGeometry(MudTuningSelectionPayload.HighlightKind kind,
            List<MudTuningConnectedHighlightRenderer.FaceKey> faces,
            List<MudTuningConnectedHighlightRenderer.EdgeRun> edges) {
    }
}

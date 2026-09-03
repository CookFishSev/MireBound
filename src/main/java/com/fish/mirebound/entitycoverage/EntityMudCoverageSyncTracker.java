package com.fish.mirebound.entitycoverage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Maintains the last broadcast baseline for sparse spot synchronization. */
final class EntityMudCoverageSyncTracker {
    private long lastSignature = Long.MIN_VALUE;
    private final Map<Integer, Long> spotSignatures = new HashMap<>();
    private final Set<Integer> currentIds = new HashSet<>();
    private final List<EntityMudCoverageSpot> changedScratch = new ArrayList<>();
    private final List<Integer> removedScratch = new ArrayList<>();

    long lastSignature() {
        return lastSignature;
    }

    Delta delta(List<EntityMudCoverageSpot> spots) {
        currentIds.clear();
        changedScratch.clear();
        removedScratch.clear();
        for (EntityMudCoverageSpot spot : spots) {
            currentIds.add(spot.id());
            long signature = EntityMudCoverageEncoding.spotSignature(spot);
            Long previous = spotSignatures.get(spot.id());
            if (previous == null || previous.longValue() != signature) {
                changedScratch.add(spot);
            }
        }
        for (int id : spotSignatures.keySet()) {
            if (!currentIds.contains(id)) {
                removedScratch.add(id);
            }
        }
        return new Delta(snapshot(changedScratch), snapshot(removedScratch));
    }

    private static <T> List<T> snapshot(List<T> values) {
        return values.isEmpty() ? List.of() : List.copyOf(values);
    }

    void mark(long signature, List<EntityMudCoverageSpot> spots) {
        lastSignature = signature;
        spotSignatures.clear();
        for (EntityMudCoverageSpot spot : spots) {
            spotSignatures.put(
                    spot.id(), EntityMudCoverageEncoding.spotSignature(spot));
        }
    }

    record Delta(
            List<EntityMudCoverageSpot> changed,
            List<Integer> removedIds) {
        int size() {
            return changed.size() + removedIds.size();
        }
    }
}

package com.fish.mirebound.assimilation;

import java.util.Arrays;

/** Deterministic self-avoiding cardinal routes over a 4x4 QTE grid. */
public final class AssimilationTracePattern {
    public static final int GRID_SIZE = 4;
    public static final int NODE_COUNT = GRID_SIZE * GRID_SIZE;

    private AssimilationTracePattern() {
    }

    public static int[] build(int patternSeed, int sequence, int requestedLength) {
        int length = Math.max(2, Math.min(NODE_COUNT, requestedLength));
        int[] path = new int[length];
        Arrays.fill(path, -1);
        int seed = mix(patternSeed ^ sequence * 0x6D2B79F5);
        for (int attempt = 0; attempt < NODE_COUNT; attempt++) {
            boolean[] used = new boolean[NODE_COUNT];
            int start = Math.floorMod(mix(seed + attempt * 0x9E3779B9), NODE_COUNT);
            path[0] = start;
            used[start] = true;
            if (extend(path, used, 1, seed ^ attempt * 0x632BE5AB)) {
                return path;
            }
        }
        throw new IllegalStateException("Unable to build a 4x4 trace path of length " + length);
    }

    public static boolean adjacent(int first, int second) {
        if (first < 0 || first >= NODE_COUNT || second < 0 || second >= NODE_COUNT
                || first == second) {
            return false;
        }
        int rowDistance = Math.abs(first / GRID_SIZE - second / GRID_SIZE);
        int columnDistance = Math.abs(first % GRID_SIZE - second % GRID_SIZE);
        return rowDistance + columnDistance == 1;
    }

    private static boolean extend(int[] path, boolean[] used, int index, int seed) {
        if (index >= path.length) {
            return true;
        }
        int current = path[index - 1];
        int[] candidates = new int[NODE_COUNT];
        int count = 0;
        for (int node = 0; node < NODE_COUNT; node++) {
            if (!used[node] && adjacent(current, node)) {
                candidates[count++] = node;
            }
        }
        for (int i = count - 1; i > 0; i--) {
            int swap = Math.floorMod(mix(seed ^ index * 0x27D4EB2D ^ i), i + 1);
            int value = candidates[i];
            candidates[i] = candidates[swap];
            candidates[swap] = value;
        }
        for (int i = 0; i < count; i++) {
            int node = candidates[i];
            path[index] = node;
            used[node] = true;
            if (extend(path, used, index + 1, mix(seed + node * 0x165667B1))) {
                return true;
            }
            used[node] = false;
            path[index] = -1;
        }
        return false;
    }

    private static int mix(int value) {
        value ^= value >>> 16;
        value *= 0x7FEB352D;
        value ^= value >>> 15;
        value *= 0x846CA68B;
        return value ^ value >>> 16;
    }
}

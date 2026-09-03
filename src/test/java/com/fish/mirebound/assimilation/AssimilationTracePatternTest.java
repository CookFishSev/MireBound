package com.fish.mirebound.assimilation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AssimilationTracePatternTest {
    @Test
    void routesAreDeterministicAdjacentAndSelfAvoiding() {
        for (int seed = -64; seed <= 64; seed++) {
            for (int sequence = 1; sequence <= 12; sequence++) {
                int[] path = AssimilationTracePattern.build(seed, sequence, 10);
                assertArrayEquals(path, AssimilationTracePattern.build(seed, sequence, 10));
                assertEquals(10, path.length);
                Set<Integer> visited = new HashSet<>();
                for (int index = 0; index < path.length; index++) {
                    assertTrue(path[index] >= 0
                            && path[index] < AssimilationTracePattern.NODE_COUNT);
                    assertTrue(visited.add(path[index]));
                    if (index > 0) {
                        assertTrue(AssimilationTracePattern.adjacent(
                                path[index - 1], path[index]));
                    }
                }
            }
        }
    }

    @Test
    void requestedLengthIsClampedToGridCapacity() {
        assertEquals(2, AssimilationTracePattern.build(1, 1, 0).length);
        assertEquals(16, AssimilationTracePattern.build(1, 1, 99).length);
        assertTrue(AssimilationTracePattern.adjacent(0, 1));
        assertTrue(AssimilationTracePattern.adjacent(0, 4));
        assertFalse(AssimilationTracePattern.adjacent(0, 5));
        assertFalse(AssimilationTracePattern.adjacent(5, 5));
    }

    @Test
    void fullGridRoutesRemainAvailableAcrossSeeds() {
        for (int seed = -96; seed <= 96; seed++) {
            int[] path = AssimilationTracePattern.build(seed, seed * 17 + 3,
                    AssimilationTracePattern.NODE_COUNT);
            assertEquals(AssimilationTracePattern.NODE_COUNT, path.length);
            Set<Integer> visited = new HashSet<>();
            for (int index = 0; index < path.length; index++) {
                assertTrue(visited.add(path[index]));
                if (index > 0) {
                    assertTrue(AssimilationTracePattern.adjacent(
                            path[index - 1], path[index]));
                }
            }
        }
    }
}

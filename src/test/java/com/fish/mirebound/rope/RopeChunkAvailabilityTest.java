package com.fish.mirebound.rope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class RopeChunkAvailabilityTest {
    @Test
    void loadedRopeNeedsOnlyOneLookupPerChunk() {
        AtomicInteger calls = new AtomicInteger();
        List<Vec3> nodes = List.of(new Vec3(4, 70, 4), new Vec3(5, 70, 4), new Vec3(6, 70, 4));
        assertTrue(RopeChunkAvailability.loaded(nodes, 0.75D, (x, z) -> {
            calls.incrementAndGet();
            return true;
        }));
        assertEquals(1, calls.get());
    }

    @Test
    void unloadedEndOrCollisionPaddingPausesSimulation() {
        assertFalse(RopeChunkAvailability.loaded(
                List.of(new Vec3(14, 70, 4), new Vec3(16.2, 70, 4)), 0.75D,
                (x, z) -> x == 0));
        assertFalse(RopeChunkAvailability.loaded(
                List.of(new Vec3(14, 70, 4), new Vec3(15.7, 70, 4)), 0.75D,
                (x, z) -> x == 0));
    }

    @Test
    void checksCorridorBetweenLoadedEndpoints() {
        assertFalse(RopeChunkAvailability.loaded(
                List.of(new Vec3(4, 70, 4), new Vec3(36, 70, 4)), 0.75D,
                (x, z) -> x != 1));
    }

    @Test
    void negativeCoordinatesUseFloorAndResumeWithoutChangingThePose() {
        List<Vec3> nodes = new ArrayList<>(List.of(new Vec3(-0.1, 70, 4), new Vec3(0.2, 70, 4)));
        List<Vec3> original = List.copyOf(nodes);
        assertFalse(RopeChunkAvailability.loaded(nodes, 0.75D, (x, z) -> x >= 0));
        assertTrue(RopeChunkAvailability.loaded(nodes, 0.75D, (x, z) -> true));
        assertEquals(original, nodes);
    }

    @Test
    void invalidOrExcessiveGeometryCannotTriggerAnUnboundedScan() {
        AtomicInteger calls = new AtomicInteger();
        RopeChunkAvailability.ChunkLookup chunks = (x, z) -> {
            calls.incrementAndGet();
            return true;
        };
        assertFalse(RopeChunkAvailability.loaded(List.of(new Vec3(Double.NaN, 0, 0)), 0.75D, chunks));
        assertFalse(RopeChunkAvailability.loaded(
                List.of(new Vec3(4, 70, 4), new Vec3(30_000_000, 70, 30_000_000)), 0.75D, chunks));
        assertEquals(0, calls.get());
    }
}

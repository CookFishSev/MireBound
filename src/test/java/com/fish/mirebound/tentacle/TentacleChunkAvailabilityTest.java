package com.fish.mirebound.tentacle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class TentacleChunkAvailabilityTest {
    @Test
    void sharedChunksAreLookedUpOnlyOnce() {
        AtomicInteger calls = new AtomicInteger();
        assertTrue(TentacleChunkAvailability.loaded(
                List.of(List.of(new Vec3(1, 64, 1), new Vec3(8, 64, 1)),
                        List.of(new Vec3(2, 64, 2), new Vec3(7, 64, 2))),
                0.5D, (x, z) -> {
                    calls.incrementAndGet();
                    return true;
                }));
        assertEquals(1, calls.get());
    }

    @Test
    void missingChunkUnderThePathPausesTheTentacle() {
        assertFalse(TentacleChunkAvailability.loaded(
                List.of(List.of(new Vec3(4, 64, 4), new Vec3(36, 64, 4))),
                0.5D, (x, z) -> x != 1));
    }

    @Test
    void negativeCoordinatesUseFloor() {
        assertFalse(TentacleChunkAvailability.loaded(
                List.of(List.of(new Vec3(-0.1, 64, 4), new Vec3(0.1, 64, 4))),
                0.5D, (x, z) -> x >= 0));
        assertTrue(TentacleChunkAvailability.loaded(
                List.of(List.of(new Vec3(-0.1, 64, 4), new Vec3(0.1, 64, 4))),
                0.5D, (x, z) -> true));
    }

    @Test
    void invalidAndUnboundedGeometryIsRejectedBeforeScanning() {
        AtomicInteger calls = new AtomicInteger();
        assertFalse(TentacleChunkAvailability.loaded(
                List.of(List.of(new Vec3(Double.NaN, 64, 0))),
                0.5D, (x, z) -> {
                    calls.incrementAndGet();
                    return true;
                }));
        assertFalse(TentacleChunkAvailability.loaded(
                List.of(List.of(new Vec3(0, 64, 0), new Vec3(30_000_000, 64, 30_000_000))),
                0.5D, (x, z) -> {
                    calls.incrementAndGet();
                    return true;
                }));
        assertEquals(0, calls.get());
    }
}

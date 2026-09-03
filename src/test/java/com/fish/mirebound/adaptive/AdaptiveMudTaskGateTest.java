package com.fish.mirebound.adaptive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdaptiveMudTaskGateTest {
    @Test
    void onePlayerCannotOwnOverlappingWorldMutationTasks() {
        UUID player = UUID.randomUUID();
        try {
            assertTrue(AdaptiveMudTaskGate.tryAcquire(player));
            assertFalse(AdaptiveMudTaskGate.tryAcquire(player));
            assertEquals(1, AdaptiveMudTaskGate.activeCount());
        } finally {
            AdaptiveMudTaskGate.release(player);
        }
        assertEquals(0, AdaptiveMudTaskGate.activeCount());
        assertTrue(AdaptiveMudTaskGate.tryAcquire(player));
        AdaptiveMudTaskGate.release(player);
    }
}

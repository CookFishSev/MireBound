package com.fish.mirebound.rope;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class RopeDragInputOrderTest {
    private final UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void anotherPlayersFirstSessionIsNotBlockedByALargerCounter() {
        RopeDragInputOrder order = new RopeDragInputOrder();
        order.record(first, 800, 100, true);
        order.finish(first);
        assertTrue(order.accepts(second, 1, 1, true));
        order.record(second, 1, 1, true);
        assertFalse(order.accepts(first, 799, 1000, true));
        assertTrue(order.accepts(first, 801, 1, true));
    }

    @Test
    void duplicateAndOutOfOrderPacketsCannotReopenAReleasedSession() {
        RopeDragInputOrder order = new RopeDragInputOrder();
        order.record(first, 3, 5, true);
        assertFalse(order.accepts(first, 3, 5, true));
        assertFalse(order.accepts(first, 2, 100, true));
        assertTrue(order.accepts(first, 3, 6, false));
        order.record(first, 3, 6, false);
        assertFalse(order.accepts(first, 3, 7, true));
        assertFalse(order.accepts(first, 3, 7, false));
        assertTrue(order.accepts(first, 4, 1, true));
    }

    @Test
    void releaseCannotCreateOrReplaceASession() {
        RopeDragInputOrder order = new RopeDragInputOrder();
        assertFalse(order.accepts(first, 1, 1, false));
        order.record(first, 3, 5, true);
        assertFalse(order.accepts(first, 4, 6, false));
        assertFalse(order.accepts(second, 3, 6, false));
    }

    @Test
    void logoutAllowsTheSamePlayerToRestartClientCounters() {
        RopeDragInputOrder order = new RopeDragInputOrder();
        order.record(first, 800, 100, true);
        order.record(second, 5, 20, true);
        order.forget(first);
        assertTrue(order.accepts(first, 1, 1, true));
        assertFalse(order.accepts(second, 1, 1, true));
        assertTrue(order.accepts(second, 5, 21, true));
    }

    @Test
    void serverForcedReleaseClosesTheSessionWithoutResettingItsWatermark() {
        RopeDragInputOrder order = new RopeDragInputOrder();
        order.record(first, 3, 5, true);
        order.finish(first);
        assertFalse(order.accepts(first, 3, 100, true));
        assertFalse(order.accepts(first, 2, 100, true));
        assertTrue(order.accepts(first, 4, 1, true));
    }
}

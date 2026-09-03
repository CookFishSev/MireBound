package com.fish.mirebound.client.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class SableAdaptiveMudInvalidationQueueTest {
    private static final Predicate<String> ALL = ignored -> true;

    @Test
    void retriesAtTheConfiguredTicks() {
        var queue = new SableAdaptiveMudInvalidationQueue.RetryQueue<String>(
                new int[]{0, 1, 3, 7}, 16);
        queue.schedule("section", 10);

        assertEquals(List.of(), queue.pollDue(9, 16, ALL));
        assertEquals(List.of("section"), queue.pollDue(10, 16, ALL));
        assertEquals(List.of("section"), queue.pollDue(11, 16, ALL));
        assertEquals(List.of(), queue.pollDue(12, 16, ALL));
        assertEquals(List.of("section"), queue.pollDue(13, 16, ALL));
        assertEquals(List.of(), queue.pollDue(16, 16, ALL));
        assertEquals(List.of("section"), queue.pollDue(17, 16, ALL));
        assertEquals(0, queue.size());
    }

    @Test
    void reschedulingDeduplicatesAndRestartsRetries() {
        var queue = new SableAdaptiveMudInvalidationQueue.RetryQueue<String>(
                new int[]{0, 2}, 16);
        queue.schedule("section", 4);
        assertEquals(List.of("section"), queue.pollDue(4, 16, ALL));

        queue.schedule("section", 5);

        assertEquals(1, queue.size());
        assertEquals(List.of("section"), queue.pollDue(5, 16, ALL));
        assertEquals(List.of(), queue.pollDue(6, 16, ALL));
        assertEquals(List.of("section"), queue.pollDue(7, 16, ALL));
    }

    @Test
    void successfulAttemptCancelsRemainingRetries() {
        var queue = new SableAdaptiveMudInvalidationQueue.RetryQueue<String>(
                new int[]{0, 1, 3}, 16);
        queue.schedule("section", 10);

        assertEquals(List.of("section"), queue.pollDue(10, 16, ALL));
        queue.cancel("section");

        assertEquals(List.of(), queue.pollDue(11, 16, ALL));
        assertEquals(List.of(), queue.pollDue(13, 16, ALL));
        assertEquals(0, queue.size());
    }

    @Test
    void budgetDefersRemainingSectionsWithoutDroppingThem() {
        var queue = new SableAdaptiveMudInvalidationQueue.RetryQueue<String>(
                new int[]{0}, 16);
        queue.schedule("first", 0);
        queue.schedule("second", 0);
        queue.schedule("third", 0);

        assertEquals(List.of("first", "second"), queue.pollDue(0, 2, ALL));
        assertEquals(List.of("third"), queue.pollDue(0, 2, ALL));
    }

    @Test
    void retriedSectionsRotateBehindUnprocessedSections() {
        var queue = new SableAdaptiveMudInvalidationQueue.RetryQueue<String>(
                new int[]{0, 0}, 16);
        queue.schedule("first", 0);
        queue.schedule("second", 0);

        assertEquals(List.of("first"), queue.pollDue(0, 1, ALL));
        assertEquals(List.of("second"), queue.pollDue(0, 1, ALL));
        assertEquals(List.of("first"), queue.pollDue(0, 1, ALL));
        assertEquals(List.of("second"), queue.pollDue(0, 1, ALL));
    }

    @Test
    void capacityEvictsTheOldestSection() {
        var queue = new SableAdaptiveMudInvalidationQueue.RetryQueue<String>(
                new int[]{0}, 2);
        queue.schedule("first", 0);
        queue.schedule("second", 0);
        queue.schedule("third", 0);

        assertEquals(List.of("second", "third"), queue.pollDue(0, 3, ALL));
    }
}

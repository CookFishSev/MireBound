package com.fish.mirebound.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class AdaptiveMudClientCacheTest {
    @Test
    void revisionIndexChangesAndRemovesSources() {
        AdaptiveMudClientCache.AppearanceRevisionIndex<String> revisions =
                new AdaptiveMudClientCache.AppearanceRevisionIndex<>();

        int first = revisions.update("source");
        assertTrue(first > 0);
        assertEquals(first, revisions.getOrDefault("source"));

        int changed = revisions.update("source");
        assertNotEquals(first, changed);
        assertEquals(changed, revisions.getOrDefault("source"));

        revisions.remove("source");
        assertEquals(0, revisions.getOrDefault("source"));
    }

    @Test
    void pendingRemovalWaitsUntilTheProxyBlockIsGone() {
        AdaptiveMudClientCache.PendingRemovalQueue<String> pending =
                new AdaptiveMudClientCache.PendingRemovalQueue<>();
        pending.stage("source");

        assertEquals(List.of(), pending.pollRemovable(256, key -> true));
        assertEquals(1, pending.size());
        assertEquals(List.of("source"), pending.pollRemovable(256, key -> false));
        assertEquals(0, pending.size());
    }

    @Test
    void aNewSourceCancelsAnOlderPendingRemoval() {
        AdaptiveMudClientCache.PendingRemovalQueue<String> pending =
                new AdaptiveMudClientCache.PendingRemovalQueue<>();
        pending.stage("source");

        pending.cancel("source");

        assertEquals(List.of(), pending.pollRemovable(256, key -> false));
    }

    @Test
    void retainedEntriesRotateBehindUninspectedLargeSelectionEntries() {
        AdaptiveMudClientCache.PendingRemovalQueue<Integer> pending =
                new AdaptiveMudClientCache.PendingRemovalQueue<>();
        pending.stage(0);
        pending.stage(1);
        pending.stage(2);

        assertEquals(List.of(), pending.pollRemovable(1, key -> true));
        assertEquals(List.of(1), pending.pollRemovable(1, key -> false));
        assertEquals(List.of(2), pending.pollRemovable(1, key -> false));
        assertEquals(List.of(0), pending.pollRemovable(1, key -> false));
    }
}

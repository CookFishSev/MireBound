package com.fish.mirebound.adaptive;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Prevents one operator from running overlapping adaptive world mutations. */
public final class AdaptiveMudTaskGate {
    private static final Set<UUID> ACTIVE_PLAYERS = new HashSet<>();

    private AdaptiveMudTaskGate() {
    }

    public static synchronized boolean tryAcquire(UUID playerId) {
        return ACTIVE_PLAYERS.add(playerId);
    }

    public static synchronized void release(UUID playerId) {
        ACTIVE_PLAYERS.remove(playerId);
    }

    static synchronized int activeCount() {
        return ACTIVE_PLAYERS.size();
    }
}

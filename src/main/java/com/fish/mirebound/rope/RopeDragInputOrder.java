package com.fish.mirebound.rope;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Client counters belong to a player connection, never to a shared rope. */
final class RopeDragInputOrder {
    private final Map<UUID, Sequence> players = new HashMap<>();

    boolean accepts(UUID player, long session, long sequence, boolean dragging) {
        if (player == null || session <= 0 || sequence <= 0) {
            return false;
        }
        Sequence previous = players.get(player);
        if (previous == null) {
            return dragging;
        }
        if (session > previous.session()) {
            return dragging;
        }
        return session == previous.session() && !previous.ended()
                && sequence > previous.sequence();
    }

    void record(UUID player, long session, long sequence, boolean dragging) {
        players.put(player, new Sequence(session, sequence, !dragging));
    }

    boolean matchesSession(UUID player, long session) {
        Sequence previous = players.get(player);
        return previous != null && previous.session() == session;
    }

    void finish(UUID player) {
        Sequence previous = players.get(player);
        if (previous != null && !previous.ended()) {
            players.put(player, new Sequence(previous.session(), previous.sequence(), true));
        }
    }

    void forget(UUID player) {
        players.remove(player);
    }

    private record Sequence(long session, long sequence, boolean ended) {
    }
}

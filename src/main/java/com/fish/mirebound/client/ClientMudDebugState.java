package com.fish.mirebound.client;

import com.fish.mirebound.network.payload.MudDebugSyncPayload;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

final class ClientMudDebugState {
    private static final Map<Integer, Entry> BY_ENTITY = new HashMap<>();

    private ClientMudDebugState() {
    }

    static void set(MudDebugSyncPayload payload) {
        if (payload.active()) {
            BY_ENTITY.put(payload.entityId(), new Entry(payload));
        } else {
            BY_ENTITY.remove(payload.entityId());
        }
    }

    static void tick() {
        Iterator<Entry> iterator = BY_ENTITY.values().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            entry.ageTicks++;
            if (entry.ageTicks > 40) {
                iterator.remove();
            }
        }
    }

    static MudDebugSyncPayload currentFor(int entityId) {
        Entry entry = BY_ENTITY.get(entityId);
        return entry == null ? null : entry.payload;
    }

    static void reset() {
        BY_ENTITY.clear();
    }

    private static final class Entry {
        private final MudDebugSyncPayload payload;
        private int ageTicks;

        private Entry(MudDebugSyncPayload payload) {
            this.payload = payload;
        }
    }
}

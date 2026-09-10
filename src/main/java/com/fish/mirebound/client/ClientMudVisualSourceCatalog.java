package com.fish.mirebound.client;

import com.fish.mirebound.adaptive.MudVisualSource;
import com.fish.mirebound.network.payload.MudVisualSourceCatalogPayload;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Bounded client copy of persistent adaptive source states. */
final class ClientMudVisualSourceCatalog {
    private static final int MAX_ENTRIES = 4096;
    private static final LinkedHashMap<Long, BlockState> STATES =
            new LinkedHashMap<>(256, 0.75F, true);
    private static final Map<Integer, HashSet<Long>> BY_ENTITY = new HashMap<>();
    private static final Map<Long, Integer> REFERENCES = new HashMap<>();

    private ClientMudVisualSourceCatalog() {
    }

    static synchronized void accept(MudVisualSourceCatalogPayload payload) {
        if (payload == null) {
            return;
        }
        clearEntity(payload.entityId());
        HashSet<Long> entitySources = new HashSet<>();
        int count = Math.min(payload.sources().length, payload.stateIds().length);
        for (int index = 0; index < count; index++) {
            long source = payload.sources()[index];
            int stateId = payload.stateIds()[index];
            if (!MudVisualSource.positionBacked(source) || stateId <= 0) {
                continue;
            }
            BlockState state = Block.stateById(stateId);
            if (state != null && !state.isAir()) {
                STATES.put(source, state);
                entitySources.add(source);
                REFERENCES.merge(source, 1, Integer::sum);
            }
        }
        if (!entitySources.isEmpty()) {
            BY_ENTITY.put(payload.entityId(), entitySources);
        }
        while (STATES.size() > MAX_ENTRIES) {
            STATES.remove(STATES.keySet().iterator().next());
        }
    }

    static synchronized BlockState state(long source) {
        return STATES.get(source);
    }

    static synchronized void clearEntity(int entityId) {
        HashSet<Long> sources = BY_ENTITY.remove(entityId);
        if (sources == null) {
            return;
        }
        for (long source : sources) {
            int references = REFERENCES.getOrDefault(source, 0) - 1;
            if (references <= 0) {
                REFERENCES.remove(source);
                STATES.remove(source);
            } else {
                REFERENCES.put(source, references);
            }
        }
    }

    static synchronized void reset() {
        STATES.clear();
        BY_ENTITY.clear();
        REFERENCES.clear();
    }
}

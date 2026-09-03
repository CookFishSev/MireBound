package com.fish.mirebound.assimilation;

import com.fish.mirebound.Mirebound;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;

/** Owns assimilation runtime identity and player-persistent state. */
final class AssimilationStateStore {
    private static final String PERSISTENT_KEY = Mirebound.MOD_ID + ":assimilation";
    private static final int SAVE_INTERVAL_TICKS = 20;
    private static final Map<ServerPlayer, AssimilationState> STATES =
            new WeakHashMap<>();

    private AssimilationStateStore() {
    }

    static AssimilationState state(ServerPlayer player) {
        return STATES.computeIfAbsent(player, ignored -> new AssimilationState());
    }

    static AssimilationState get(ServerPlayer player) {
        return STATES.get(player);
    }

    static AssimilationState remove(ServerPlayer player) {
        return STATES.remove(player);
    }

    static Iterable<Map.Entry<ServerPlayer, AssimilationState>> entries() {
        return STATES.entrySet();
    }

    static void clear() {
        STATES.clear();
    }

    static void load(ServerPlayer player, AssimilationState state) {
        CompoundTag persistent = player.getPersistentData();
        if (persistent.contains(PERSISTENT_KEY, Tag.TAG_COMPOUND)) {
            state.load(persistent.getCompound(PERSISTENT_KEY));
        }
    }

    static void saveIfNeeded(ServerPlayer player, AssimilationState state) {
        if (state.dirty
                && (long) player.tickCount - state.lastSaveTick
                        >= SAVE_INTERVAL_TICKS) {
            save(player, state);
        }
    }

    static void save(ServerPlayer player, AssimilationState state) {
        if (state.active()) {
            player.getPersistentData().put(PERSISTENT_KEY, state.save());
        } else {
            player.getPersistentData().remove(PERSISTENT_KEY);
        }
        state.lastSaveTick = player.tickCount;
        state.dirty = false;
    }

    static void clearPersistent(ServerPlayer player) {
        player.getPersistentData().remove(PERSISTENT_KEY);
    }
}

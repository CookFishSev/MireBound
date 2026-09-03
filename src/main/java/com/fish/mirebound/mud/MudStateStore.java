package com.fish.mirebound.mud;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;

public final class MudStateStore {
    private static final Map<ServerPlayer, MudPlayerData> SERVER_DATA = new WeakHashMap<>();

    private MudStateStore() {
    }

    public static MudPlayerData get(ServerPlayer player) {
        return SERVER_DATA.computeIfAbsent(player, ignored -> new MudPlayerData());
    }

    static void remove(ServerPlayer player) {
        SERVER_DATA.remove(player);
    }

    static void clear() {
        SERVER_DATA.clear();
    }
}

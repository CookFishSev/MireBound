package com.fish.mirebound.network;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/** Per-tick safety budgets for client-authored inputs that trigger server work. */
public final class ServerInputBudget {
    private static final Map<UUID, TickBudget> BY_PLAYER = new HashMap<>();

    private ServerInputBudget() {
    }

    public static synchronized boolean allow(ServerPlayer player, Channel channel) {
        TickBudget budget = BY_PLAYER.computeIfAbsent(
                player.getUUID(), ignored -> new TickBudget());
        return budget.allow(channel, player.serverLevel().getGameTime());
    }

    public static synchronized void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        BY_PLAYER.remove(event.getEntity().getUUID());
    }

    public static synchronized void onServerStopping(ServerStoppingEvent event) {
        BY_PLAYER.clear();
    }

    public enum Channel {
        DEVELOPER_OPTIONS(2),
        VIEW_MODE(4),
        STRUGGLE(8),
        WATER_GUN_INPUT(4),
        SCULK_MIRE_INPUT(8),
        TENDER_FLESH_STRIKE(8),
        ASSIMILATION_PURGE(4),
        PLAYER_GEOMETRY(4),
        ARMOR_TEXTURE_CONTACT(12),
        ASSIMILATION_QTE(24),
        ASSIMILATION_TRACE(12),
        ASSIMILATION_SOUL_POSITION(4),
        ROPE_DRAG(8),
        ROPE_ANCHOR(2),
        ROPE_BREAK(4),
        ROPE_EXTEND(2),
        ROPE_CLIMB(4),
        MUD_TUNING_REQUEST(4),
        MUD_TUNING_APPLY(4);

        private final int maximumPerTick;

        Channel(int maximumPerTick) {
            this.maximumPerTick = maximumPerTick;
        }
    }

    static final class TickBudget {
        private long tick = Long.MIN_VALUE;
        private final int[] counts = new int[Channel.values().length];

        boolean allow(Channel channel, long currentTick) {
            if (tick != currentTick) {
                tick = currentTick;
                java.util.Arrays.fill(counts, 0);
            }
            int index = channel.ordinal();
            if (counts[index] >= channel.maximumPerTick) {
                return false;
            }
            counts[index]++;
            return true;
        }
    }
}

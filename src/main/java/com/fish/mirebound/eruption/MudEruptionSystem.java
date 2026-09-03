package com.fish.mirebound.eruption;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** NeoForge event facade for the bounded, non-entity mud eruption lifecycle. */
public final class MudEruptionSystem {
    private static final Map<ServerLevel, MudEruptionLevelState> STATES =
            new IdentityHashMap<>();
    private static int nextVentId = 1;

    private MudEruptionSystem() {
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            STATES.computeIfAbsent(level, MudEruptionLevelState::new).tick(level);
        }
        STATES.keySet().removeIf(level -> level.getServer() != event.getServer());
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncLevelVents(player);
        }
    }

    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncLevelVents(player);
        }
    }

    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            STATES.remove(level);
        }
    }

    private static void syncLevelVents(ServerPlayer player) {
        MudEruptionLevelState state = STATES.get(player.serverLevel());
        if (state != null) {
            state.syncTo(player);
        }
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        STATES.clear();
        nextVentId = 1;
    }

    static boolean intersectsPlayerFootprint(AABB bounds, Vec3 origin, double radius) {
        if (bounds.maxY < origin.y - 0.08D || bounds.minY > origin.y + 0.16D) {
            return false;
        }
        double closestX = Mth.clamp(origin.x, bounds.minX, bounds.maxX);
        double closestZ = Mth.clamp(origin.z, bounds.minZ, bounds.maxZ);
        double dx = origin.x - closestX;
        double dz = origin.z - closestZ;
        return dx * dx + dz * dz <= radius * radius;
    }

    static boolean intersectsPlayerSurface(
            AABB bounds, Vec3 origin, Vec3 normal, double radius) {
        if (normal != null && normal.y > 0.90D) {
            return intersectsPlayerFootprint(bounds, origin, radius);
        }
        double closestX = Mth.clamp(origin.x, bounds.minX, bounds.maxX);
        double closestY = Mth.clamp(origin.y, bounds.minY, bounds.maxY);
        double closestZ = Mth.clamp(origin.z, bounds.minZ, bounds.maxZ);
        return origin.distanceToSqr(closestX, closestY, closestZ)
                <= Mth.square(radius + 0.08D);
    }

    static int nextId() {
        int id = nextVentId++;
        if (nextVentId <= 0 || nextVentId > 0x1FFFFFFF) {
            nextVentId = 1;
        }
        return id;
    }
}

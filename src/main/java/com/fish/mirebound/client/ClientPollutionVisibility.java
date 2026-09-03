package com.fish.mirebound.client;

import com.fish.mirebound.client.compat.freecam.FreecamCompat;
import com.fish.mirebound.mud.MudPhysics;
import com.fish.mirebound.network.payload.MudViewModePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

/** Separates persistent pollution rendering from camera-driven contact sampling. */
public final class ClientPollutionVisibility {
    private static Boolean lastSentExternalCamera;
    private static Player lastSentPlayer;
    private static int lastSentTick = Integer.MIN_VALUE;

    private ClientPollutionVisibility() {
    }

    public static boolean isSuppressed(Entity entity) {
        return entity instanceof Player player
                && renderSuppressed(player.isSpectator());
    }

    public static boolean isContactSamplingSuppressed(Entity entity) {
        if (isSuppressed(entity)) {
            return true;
        }
        Minecraft minecraft = Minecraft.getInstance();
        return entity == minecraft.player
                && FreecamCompat.isExternalCameraActive(minecraft);
    }

    public static boolean isEntityIdSuppressed(int entityId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return false;
        }
        Entity entity = minecraft.level.getEntity(entityId);
        return entity != null && isSuppressed(entity);
    }

    public static boolean isLocalSuppressed(Minecraft minecraft) {
        return minecraft.player != null && suppressed(
                minecraft.player.isSpectator(),
                FreecamCompat.isExternalCameraActive(minecraft));
    }

    static boolean suppressed(boolean spectator, boolean externalCamera) {
        return spectator || externalCamera;
    }

    static boolean renderSuppressed(boolean spectator) {
        return spectator;
    }

    static void syncViewMode(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.getConnection() == null) {
            lastSentExternalCamera = null;
            lastSentPlayer = null;
            return;
        }
        boolean externalCamera = FreecamCompat.isExternalCameraActive(minecraft);
        boolean samePlayerInstance = lastSentPlayer == minecraft.player;
        if (samePlayerInstance
                && lastSentExternalCamera != null
                && lastSentExternalCamera.booleanValue() == externalCamera
                && (!externalCamera || minecraft.player.tickCount - lastSentTick < 20)) {
            return;
        }
        PacketDistributor.sendToServer(new MudViewModePayload(externalCamera));
        lastSentPlayer = minecraft.player;
        lastSentExternalCamera = externalCamera;
        lastSentTick = minecraft.player.tickCount;
    }

    static void updateExternalCameraPhysics(Minecraft minecraft) {
        if (minecraft.player == null) {
            return;
        }
        MudPhysics.setClientExternalCameraPhysicsSuspended(
                minecraft.player,
                FreecamCompat.isExternalCameraActive(minecraft));
    }

    static void reset() {
        if (lastSentPlayer != null) {
            MudPhysics.setClientExternalCameraPhysicsSuspended(
                    lastSentPlayer, false);
        }
        lastSentExternalCamera = null;
        lastSentPlayer = null;
        lastSentTick = Integer.MIN_VALUE;
    }
}

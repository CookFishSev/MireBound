package com.fish.mirebound.client;

import com.fish.mirebound.mud.SinkingMedium;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Narrow client-domain bridge for persistent procedural surface sources. */
public final class MudSurfaceDeformationApi {
    private MudSurfaceDeformationApi() {
    }

    public static boolean openEruptionVent(int ventId, SinkingMedium medium,
            Vec3 origin, double radiusPixels, long seed, long visualSource) {
        return MudSurfaceEffectManager.openEruptionVent(
                ventId, medium, origin, radiusPixels, seed, visualSource);
    }

    public static boolean openOrientedEruptionVent(int ventId, SinkingMedium medium,
            Object subLevel, BlockPos supportPos, Direction face, Vec3 localOrigin,
            double radiusPixels, long seed, long visualSource) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null && MudSideSurfaceEffectManager.openEruptionVent(
                minecraft.level, ventId, subLevel, supportPos, face, localOrigin,
                medium, radiusPixels, seed, visualSource);
    }

    public static void closeEruptionVent(int ventId, int mergeEntityId) {
        MudSurfaceEffectManager.closeEruptionVent(ventId, mergeEntityId);
        MudSideSurfaceEffectManager.closeEruptionVent(ventId);
    }

    public static void forgetEruptionVent(int ventId) {
        MudSurfaceEffectManager.forgetEruptionVent(ventId);
        MudSideSurfaceEffectManager.forgetEruptionVent(ventId);
    }

    public static boolean shouldPresentEruption(Vec3 origin) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.getCameraEntity() == null) {
            return false;
        }
        double range = MudSurfaceClientSettings.renderDistance() + 3.0D;
        if (minecraft.getCameraEntity().position().distanceToSqr(origin) > range * range) {
            return false;
        }
        return minecraft.level.getChunkSource().hasChunk(
                (int) Math.floor(origin.x) >> 4,
                (int) Math.floor(origin.z) >> 4);
    }
}

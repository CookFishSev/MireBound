package com.fish.mirebound.client.eruption;

import com.fish.mirebound.client.MudSurfaceDeformationApi;
import com.fish.mirebound.client.config.MireboundClientSettings;
import com.fish.mirebound.client.config.MireboundClientSettings.ClientOption;
import com.fish.mirebound.compat.sable.SableCompat;
import com.fish.mirebound.network.payload.MudEruptionVentPayload;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Client ownership of synchronized vent identities; geometry stays in the shared surface field. */
public final class ClientMudEruptionManager {
    private static final Map<Integer, MudEruptionVentPayload> ACTIVE = new HashMap<>();
    private static final Set<Integer> PRESENTED = new HashSet<>();
    private static long lastRetryTick = Long.MIN_VALUE;
    private static ResourceLocation activeDimension;

    private ClientMudEruptionManager() {
    }

    public static void accept(MudEruptionVentPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            switchDimension(minecraft.level.dimension().location());
        }
        if (payload.active()) {
            ACTIVE.put(payload.ventId(), payload);
            if (effectsEnabled()) {
                updatePresentation(payload);
            }
        } else {
            ACTIVE.remove(payload.ventId());
            if (PRESENTED.remove(payload.ventId())) {
                MudSurfaceDeformationApi.closeEruptionVent(
                        payload.ventId(), payload.mergeEntityId());
            }
        }
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        ResourceLocation currentDimension = minecraft.level.dimension().location();
        boolean dimensionChanged = switchDimension(currentDimension);
        if (!effectsEnabled()) {
            hidePresentation();
            return;
        }
        if (ACTIVE.isEmpty()) {
            return;
        }
        long now = minecraft.level.getGameTime();
        if (!dimensionChanged && (now == lastRetryTick || now % 20L != 0L)) {
            return;
        }
        lastRetryTick = now;
        for (MudEruptionVentPayload payload : ACTIVE.values()) {
            updatePresentation(payload);
        }
    }

    private static void updatePresentation(MudEruptionVentPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        Object subLevel = payload.physicalized() && minecraft.level != null
                ? SableCompat.subLevelById(minecraft.level, payload.subLevelId()) : null;
        Vec3 worldOrigin = payload.physicalized()
                ? (subLevel == null ? null : SableCompat.toRenderWorld(subLevel, payload.origin()))
                : payload.origin();
        if (minecraft.level != null
                && payload.dimension().equals(minecraft.level.dimension().location())
                && worldOrigin != null
                && MudSurfaceDeformationApi.shouldPresentEruption(worldOrigin)) {
            boolean presented = !payload.physicalized() && payload.face() == Direction.UP
                    ? MudSurfaceDeformationApi.openEruptionVent(
                            payload.ventId(), payload.medium(), payload.origin(),
                            payload.radiusPixels(), payload.seed(), payload.visualSource())
                    : MudSurfaceDeformationApi.openOrientedEruptionVent(
                            payload.ventId(), payload.medium(), subLevel,
                            payload.supportBlockPos(), payload.face(), payload.origin(),
                            payload.radiusPixels(), payload.seed(), payload.visualSource());
            if (presented) {
                PRESENTED.add(payload.ventId());
            } else {
                PRESENTED.remove(payload.ventId());
            }
        } else if (PRESENTED.remove(payload.ventId())) {
            MudSurfaceDeformationApi.forgetEruptionVent(payload.ventId());
        }
    }

    private static boolean switchDimension(ResourceLocation currentDimension) {
        if (currentDimension.equals(activeDimension)) {
            return false;
        }
        for (int ventId : PRESENTED) {
            MudSurfaceDeformationApi.forgetEruptionVent(ventId);
        }
        PRESENTED.clear();
        ACTIVE.entrySet().removeIf(
                entry -> !entry.getValue().dimension().equals(currentDimension));
        activeDimension = currentDimension;
        lastRetryTick = Long.MIN_VALUE;
        return true;
    }

    private static boolean effectsEnabled() {
        return MireboundClientSettings.clientOptionEnabled(
                ClientOption.ERUPTION_EFFECTS);
    }

    private static void hidePresentation() {
        for (int ventId : PRESENTED) {
            MudSurfaceDeformationApi.forgetEruptionVent(ventId);
        }
        PRESENTED.clear();
        lastRetryTick = Long.MIN_VALUE;
    }

    public static void reset() {
        for (int ventId : PRESENTED) {
            MudSurfaceDeformationApi.forgetEruptionVent(ventId);
        }
        ACTIVE.clear();
        PRESENTED.clear();
        lastRetryTick = Long.MIN_VALUE;
        activeDimension = null;
    }
}

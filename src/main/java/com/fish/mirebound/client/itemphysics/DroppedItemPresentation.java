package com.fish.mirebound.client.itemphysics;

import com.fish.mirebound.compat.sable.SableCompat;
import com.fish.mirebound.mud.MudBlock;
import com.fish.mirebound.network.payload.DroppedItemMudStatePayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;

/** Client-only lifecycle cache for stable, non-bobbing mud item poses. */
public final class DroppedItemPresentation {
    private static final int CLEANUP_INTERVAL_TICKS = 100;
    private static final int LIGHT_SCAN_LIMIT = 64;
    private static final Map<Integer, Entry> ENTRIES = new HashMap<>();
    private static final Map<Integer, LightSample> LIGHTS = new HashMap<>();
    private static int cleanupTicks;

    private DroppedItemPresentation() {
    }

    public static void accept(DroppedItemMudStatePayload payload) {
        if (!payload.anchored()) {
            ENTRIES.remove(payload.entityId());
            LIGHTS.remove(payload.entityId());
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        long gameTime = minecraft.level == null ? 0L : minecraft.level.getGameTime();
        ItemEntity trackedItem = null;
        if (minecraft.level != null) {
            Entity entity = minecraft.level.getEntity(payload.entityId());
            if (entity instanceof ItemEntity item
                    && payload.entityUuid().equals(item.getUUID())) {
                trackedItem = item;
            }
        }
        Entry previous = ENTRIES.get(payload.entityId());
        long startTick = previous != null
                && previous.uuid.equals(payload.entityUuid())
                && previous.stablePresentation == payload.stablePresentation()
                ? previous.startTick : gameTime;
        ENTRIES.put(payload.entityId(), new Entry(
                payload.entityUuid(),
                DroppedItemVisualPose.create(
                        payload.entityUuid(),
                        payload.maximumTiltDegrees(),
                        trackedItem == null ? 0.0F : trackedItem.getSpin(0.0F)),
                startTick,
                payload.settleTicks(),
                payload.stablePresentation(),
                payload.sableAnchored()));
        if (payload.sableAnchored() && minecraft.level != null) {
            if (trackedItem != null) {
                SableCompat.clearEntityTracking(trackedItem);
            }
        }
    }

    public static float settleBobSample(
            ItemEntity item, float partialTick, float originalSample) {
        Entry entry = visualEntry(item);
        if (entry == null) {
            return originalSample;
        }
        return Mth.lerp(amount(entry, partialTick), originalSample, -1.0F);
    }

    public static float settleYaw(
            ItemEntity item, float partialTick, float originalRadians) {
        Entry entry = visualEntry(item);
        return entry == null
                ? originalRadians
                : entry.pose.settleYaw(originalRadians, amount(entry, partialTick));
    }

    /** Uses the light at the exposed top of an anchored mud column, not inside its volume. */
    public static int light(ItemEntity item, int original) {
        Entry entry = visualEntry(item);
        if (entry == null || !item.level().isClientSide()) {
            return original;
        }
        long gameTime = item.level().getGameTime();
        LightSample cached = LIGHTS.get(item.getId());
        if (cached == null || !cached.uuid.equals(item.getUUID())
                || cached.gameTime != gameTime) {
            cached = new LightSample(item.getUUID(), gameTime, visibleLight(item));
            LIGHTS.put(item.getId(), cached);
        }
        return Math.max(original, cached.light);
    }

    public static void applyTilt(
            ItemEntity item, float partialTick, PoseStack poseStack) {
        Entry entry = visualEntry(item);
        if (entry == null) {
            return;
        }
        float amount = amount(entry, partialTick);
        float collisionRadius = (float) (Math.max(item.getBbWidth(), item.getBbHeight()) * 0.5D);
        poseStack.translate(0.0F, entry.pose.bottomAlignment(collisionRadius, amount), 0.0F);
        poseStack.mulPose(Axis.XP.rotationDegrees(entry.pose.tiltXDegrees() * amount));
        poseStack.mulPose(Axis.ZP.rotationDegrees(entry.pose.tiltZDegrees() * amount));
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft.level == null) {
            reset();
            return;
        }
        if (++cleanupTicks < CLEANUP_INTERVAL_TICKS) {
            return;
        }
        cleanupTicks = 0;
        ENTRIES.entrySet().removeIf(stored -> {
            Entity entity = minecraft.level.getEntity(stored.getKey());
            return !(entity instanceof ItemEntity item)
                    || item.isRemoved()
                    || !stored.getValue().uuid.equals(item.getUUID());
        });
        LIGHTS.keySet().removeIf(id -> !ENTRIES.containsKey(id));
    }

    public static void reset() {
        ENTRIES.clear();
        LIGHTS.clear();
        cleanupTicks = 0;
    }

    private static int visibleLight(ItemEntity item) {
        BlockPos.MutableBlockPos cursor = item.blockPosition().mutable();
        for (int scanned = 0; scanned < LIGHT_SCAN_LIMIT; scanned++) {
            if (!(item.level().getBlockState(cursor).getBlock() instanceof MudBlock)) {
                return LevelRenderer.getLightColor(item.level(), cursor);
            }
            cursor.move(Direction.UP);
        }
        return 0;
    }

    static int activeCount() {
        return ENTRIES.size();
    }

    public static boolean isSableAnchored(ItemEntity item) {
        Entry entry = entry(item);
        return entry != null && entry.sableAnchored;
    }

    public static boolean isAnchored(ItemEntity item) {
        return entry(item) != null;
    }

    private static Entry entry(ItemEntity item) {
        Entry entry = ENTRIES.get(item.getId());
        return entry != null && entry.uuid.equals(item.getUUID()) ? entry : null;
    }

    private static Entry visualEntry(ItemEntity item) {
        Entry entry = entry(item);
        return entry != null && entry.stablePresentation ? entry : null;
    }

    private static float amount(Entry entry, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        long gameTime = minecraft.level == null ? entry.startTick : minecraft.level.getGameTime();
        return DroppedItemVisualPose.easedAmount(
                gameTime - entry.startTick + partialTick, entry.settleTicks);
    }

    private record Entry(
            UUID uuid, DroppedItemVisualPose pose, long startTick, int settleTicks,
            boolean stablePresentation, boolean sableAnchored) {
    }

    private record LightSample(UUID uuid, long gameTime, int light) {
    }
}

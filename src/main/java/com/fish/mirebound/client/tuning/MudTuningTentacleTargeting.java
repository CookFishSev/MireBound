package com.fish.mirebound.client.tuning;

import com.fish.mirebound.client.tentacle.ClientTentacleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** One-frame cached crosshair target for the tuning wand's tentacle mode. */
public final class MudTuningTentacleTargeting {
    private static final double MAXIMUM_DISTANCE = 64.0D;
    private static Object cachedLevel;
    private static long cachedTick = Long.MIN_VALUE;
    private static int cachedPartialBits;
    private static Vec3 cachedOrigin;
    private static Vec3 cachedDirection;
    private static ClientTentacleManager.TentacleTarget cachedTarget;

    private MudTuningTentacleTargeting() {
    }

    public static ClientTentacleManager.TentacleTarget target(Minecraft minecraft) {
        if (minecraft == null || minecraft.screen != null || minecraft.player == null
                || minecraft.level == null || !MudTuningClientSettings.tentacleAutoSnap()
                || MudTuningClientState.mode() != MudTuningWandMode.SUMMON
                || MudTuningClientState.summonType() != MudTuningSummonType.TENTACLE
                || MudTuningInputController.heldWandHand(minecraft.player) == null) {
            return null;
        }
        MudTuningSpatialPlacement.PlacementRay ray =
                MudTuningSpatialPlacement.ray(minecraft);
        if (ray == null) {
            return null;
        }
        Vec3 origin = ray.origin();
        Vec3 direction = ray.direction();
        float partialTick = ray.partialTick();
        long tick = minecraft.level.getGameTime();
        int partialBits = Float.floatToIntBits(partialTick);
        if (cachedLevel == minecraft.level && cachedTick == tick
                && cachedPartialBits == partialBits && origin.equals(cachedOrigin)
                && direction.equals(cachedDirection)) {
            return cachedTarget;
        }
        cachedLevel = minecraft.level;
        cachedTick = tick;
        cachedPartialBits = partialBits;
        cachedOrigin = origin;
        cachedDirection = direction;
        double visibleDistance = MAXIMUM_DISTANCE;
        HitResult blockHit = minecraft.level.clip(new ClipContext(
                origin, origin.add(direction.scale(MAXIMUM_DISTANCE)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                minecraft.player));
        if (blockHit.getType() == HitResult.Type.BLOCK) {
            visibleDistance = Math.min(visibleDistance,
                    origin.distanceTo(blockHit.getLocation()));
        }
        cachedTarget = ClientTentacleManager.target(
                origin, direction, visibleDistance, partialTick);
        return cachedTarget;
    }

    public static void toggle(Minecraft minecraft) {
        boolean enabled = !MudTuningClientSettings.tentacleAutoSnap();
        MudTuningClientSettings.setTentacleAutoSnap(enabled);
        invalidate();
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    enabled ? "message.mirebound.tuning.tentacle_snap_enabled"
                            : "message.mirebound.tuning.tentacle_snap_disabled"), true);
        }
    }

    public static void invalidate() {
        cachedLevel = null;
        cachedTick = Long.MIN_VALUE;
        cachedOrigin = null;
        cachedDirection = null;
        cachedTarget = null;
    }
}

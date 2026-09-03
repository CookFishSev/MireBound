package com.fish.mirebound.client.tuning;

import com.fish.mirebound.tool.MudTuningWandReach;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Cached block targeting that extends the wand past vanilla's 64-block attribute cap. */
public final class MudTuningWandTargeting {
    private static Object cachedLevel;
    private static Entity cachedCameraEntity;
    private static HitResult cachedVanillaHit;
    private static long cachedGameTime = Long.MIN_VALUE;
    private static int cachedPartialBits;
    private static long cachedRangeBits;
    private static Vec3 cachedOrigin;
    private static Vec3 cachedDirection;
    private static BlockHitResult cachedBlockHit;

    private MudTuningWandTargeting() {
    }

    public static BlockHitResult blockHit(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null || minecraft.level == null
                || MudTuningInputController.heldWandHand(minecraft.player) == null) {
            return null;
        }
        HitResult vanilla = minecraft.hitResult;
        if (vanilla != null && vanilla.getType() == HitResult.Type.ENTITY) {
            return null;
        }
        BlockHitResult vanillaBlock = vanilla instanceof BlockHitResult hit
                && hit.getType() == HitResult.Type.BLOCK ? hit : null;
        double range = MudTuningWandReach.configuredInteractionRange(minecraft.player);
        if (range <= minecraft.player.blockInteractionRange() + 1.0E-6D) {
            return vanillaBlock;
        }

        Entity cameraEntity = minecraft.getCameraEntity();
        if (cameraEntity == null) {
            return vanillaBlock;
        }
        float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        Vec3 origin = cameraEntity.getEyePosition(partialTick);
        Vec3 direction = cameraEntity.getViewVector(partialTick);
        long gameTime = minecraft.level.getGameTime();
        int partialBits = Float.floatToIntBits(partialTick);
        long rangeBits = Double.doubleToLongBits(range);
        if (cachedLevel == minecraft.level
                && cachedCameraEntity == cameraEntity
                && cachedVanillaHit == vanilla
                && cachedGameTime == gameTime
                && cachedPartialBits == partialBits
                && cachedRangeBits == rangeBits
                && origin.equals(cachedOrigin)
                && direction.equals(cachedDirection)) {
            return cachedBlockHit;
        }

        HitResult extended = cameraEntity.pick(range, partialTick, false);
        BlockHitResult extendedBlock = extended instanceof BlockHitResult hit
                && hit.getType() == HitResult.Type.BLOCK ? hit : null;
        cachedLevel = minecraft.level;
        cachedCameraEntity = cameraEntity;
        cachedVanillaHit = vanilla;
        cachedGameTime = gameTime;
        cachedPartialBits = partialBits;
        cachedRangeBits = rangeBits;
        cachedOrigin = origin;
        cachedDirection = direction;
        cachedBlockHit = nearer(origin, vanillaBlock, extendedBlock);
        return cachedBlockHit;
    }

    static BlockHitResult nearer(
            Vec3 origin, BlockHitResult first, BlockHitResult second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first.getLocation().distanceToSqr(origin)
                        <= second.getLocation().distanceToSqr(origin)
                ? first : second;
    }

    public static void invalidate() {
        cachedLevel = null;
        cachedCameraEntity = null;
        cachedVanillaHit = null;
        cachedGameTime = Long.MIN_VALUE;
        cachedOrigin = null;
        cachedDirection = null;
        cachedBlockHit = null;
    }
}

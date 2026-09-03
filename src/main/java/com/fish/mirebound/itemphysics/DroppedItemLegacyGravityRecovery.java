package com.fish.mirebound.itemphysics;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.compat.sable.SableCompat;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

/** Recovers items saved while older mod builds owned persistent NoGravity. */
final class DroppedItemLegacyGravityRecovery {
    private static final double STATIONARY_SPEED_SQUARED = 1.0E-8D;

    private DroppedItemLegacyGravityRecovery() {
    }

    static boolean recoverIfStale(ItemEntity item) {
        if (!item.isNoGravity()
                || item.onGround()
                || item.getDeltaMovement().lengthSqr() > STATIONARY_SPEED_SQUARED
                || item.tickCount < 5) {
            return false;
        }
        SableCompat.SinkingVolumeProbe probe = SableCompat.sinkingVolumeProbe(
                item.level(), item.getBoundingBox().inflate(0.08D), item);
        if (probe.candidateCount() == 0
                || probe.sampleIntersecting(item.getBoundingBox()) != null) {
            return false;
        }
        item.setNoGravity(false);
        item.setDeltaMovement(new Vec3(0.0D, -0.04D, 0.0D));
        item.hasImpulse = true;
        Mirebound.LOGGER.info(
                "Recovered legacy Sable dropped item {} from stale NoGravity state",
                item.getId());
        return true;
    }
}

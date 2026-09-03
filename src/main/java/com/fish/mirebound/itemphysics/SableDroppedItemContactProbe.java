package com.fish.mirebound.itemphysics;

import com.fish.mirebound.compat.sable.SableCompat;
import com.fish.mirebound.compat.sable.SableCompat.SinkingSample;
import com.fish.mirebound.itemphysics.DroppedItemContactResolver.Contact;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Bounded Sable-only swept-volume probe for dropped items before mud physics owns them.
 *
 * <p>Sable invokes {@code entityInside} after an item has completed its normal move. A thin
 * rotating mud surface can therefore be crossed in one tick without leaving a current-point
 * contact to resolve. This probe asks Sable for only the structures intersecting the item's
 * swept AABB, then samples that short segment against those already-bounded candidates.</p>
 */
final class SableDroppedItemContactProbe {
    private static final double MINIMUM_PROBE_SPEED_SQUARED = 4.0E-4D;
    private static final double TRACE_SAMPLE_SPACING = 1.0D / 24.0D;
    private static final int MAXIMUM_TRACE_SEGMENTS = 48;
    private static final double SWEEP_PADDING = 0.018D;

    private SableDroppedItemContactProbe() {
    }

    /**
     * Resolves an item that is already intersecting a physicalized mud volume.
     *
     * <p>This path intentionally has no velocity gate. Vanilla checks whether an item is inside
     * collision before it performs the tick's move and can otherwise apply its generic
     * move-towards-free-space impulse before the swept post-tick probe gets a chance to anchor
     * the item.</p>
     */
    static Contact findCurrentVolume(ItemEntity item) {
        if (!SableCompat.isLoaded()) {
            return null;
        }

        Vec3 itemPosition = item.position();
        AABB bounds = item.getBoundingBox();
        SableCompat.SinkingVolumeProbe probe = SableCompat.sinkingVolumeProbe(
                item.level(), bounds.inflate(SWEEP_PADDING), item);
        if (probe.candidateCount() == 0) {
            return null;
        }
        SinkingSample sample = probe.sampleIntersecting(bounds);
        return sample == null ? null : DroppedItemContactResolver.resolveSableSample(
                item, sample, itemPosition);
    }

    static Contact find(ItemEntity item) {
        if (!SableCompat.isLoaded()) {
            return null;
        }

        Vec3 current = item.position();
        Vec3 previous = new Vec3(item.xo, item.yo, item.zo);
        Vec3 displacement = current.subtract(previous);
        if (displacement.lengthSqr() < MINIMUM_PROBE_SPEED_SQUARED
                && item.getDeltaMovement().lengthSqr() < MINIMUM_PROBE_SPEED_SQUARED) {
            return null;
        }

        AABB currentBounds = item.getBoundingBox();
        AABB sweptBounds = currentBounds.minmax(
                currentBounds.move(previous.subtract(current))).inflate(SWEEP_PADDING);
        SableCompat.SinkingVolumeProbe probe = SableCompat.sinkingVolumeProbe(
                item.level(), sweptBounds, item);
        if (probe.candidateCount() == 0) {
            return null;
        }

        AABB previousBounds = currentBounds.move(previous.subtract(current));
        int segments = traceSegments(displacement.length());
        for (int segment = 0; segment <= segments; segment++) {
            double progress = segment / (double) segments;
            Vec3 itemPosition = previous.lerp(current, progress);
            AABB itemBounds = previousBounds.move(displacement.scale(progress));
            SinkingSample sample = probe.sampleIntersecting(itemBounds);
            if (sample == null) {
                continue;
            }
            Contact contact = DroppedItemContactResolver.resolveSableSample(
                    item, sample, itemPosition);
            if (contact != null) {
                return contact;
            }
        }
        return null;
    }

    static int traceSegments(double distance) {
        return Mth.clamp(
                (int) Math.ceil(Math.max(0.0D, distance) / TRACE_SAMPLE_SPACING),
                1, MAXIMUM_TRACE_SEGMENTS);
    }

}

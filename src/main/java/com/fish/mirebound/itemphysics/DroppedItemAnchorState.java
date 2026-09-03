package com.fish.mirebound.itemphysics;

import com.fish.mirebound.compat.sable.SableCompat;
import com.fish.mirebound.compat.sable.SableGravityColumn;
import com.fish.mirebound.itemphysics.DroppedItemContactResolver.Contact;
import com.fish.mirebound.itemphysics.DroppedItemContactResolver.Frame;
import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.network.payload.DroppedItemMudStatePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Mutable movement and presentation state for one server-owned dropped-item anchor. */
final class DroppedItemAnchorState {
    private Contact contact;
    private Vec3 localPosition;
    private Vec3 tangentialVelocity;
    private double depthVelocity;
    private final boolean sableAnchor;
    private SableGravityColumn.Span sableSpan;
    private int sableGeometryAge;
    private boolean stateSynchronized;
    private boolean presentationActive;
    private int presentationSettleTicks;
    private float presentationMaximumTiltDegrees;

    private DroppedItemAnchorState(Contact contact, Vec3 localPosition,
            Vec3 tangentialVelocity, double depthVelocity,
            SableGravityColumn.Span sableSpan) {
        this.contact = contact;
        this.localPosition = localPosition;
        this.tangentialVelocity = tangentialVelocity;
        this.depthVelocity = depthVelocity;
        this.sableAnchor = contact.subLevel() != null;
        this.sableSpan = sableSpan;
    }

    static DroppedItemAnchorState enter(ItemEntity item, Contact contact,
            DroppedItemPhysicsProfile profile, SableGravityColumn.Span sableSpan) {
        Frame frame = contact.frame();
        if (sableSpan != null) {
            double maximumDepth = DroppedItemDynamics.maximumDepth(
                    profile, sableSpan.availableDepth());
            double inwardSpeed = Math.max(0.0D, -item.getDeltaMovement().y);
            double entryDepth = DroppedItemDynamics.anchoredEntryDepth(
                    sableSpan.initialDepth(), inwardSpeed,
                    maximumDepth, sableSpan.availableDepth(), profile);
            return new DroppedItemAnchorState(
                    contact,
                    sableSpan.positionAtDepth(entryDepth),
                    sableSpan.tangential(frame.motion()),
                    DroppedItemDynamics.entryDepthSpeed(inwardSpeed, profile),
                    sableSpan);
        }
        double maximumDepth = DroppedItemDynamics.maximumDepth(
                profile, contact.availableDepth());
        double entryDepth = DroppedItemDynamics.anchoredEntryDepth(
                contact.itemDepth(frame.position(), item.getBbWidth(), item.getBbHeight()),
                contact.inwardSpeed(frame.motion()),
                maximumDepth, contact.availableDepth(), profile);
        Vec3 localPosition = contact.positionAtItemDepth(
                frame.position(), item.getBbWidth(), item.getBbHeight(), entryDepth);
        double entryVelocity = DroppedItemDynamics.entryDepthSpeed(
                contact.inwardSpeed(frame.motion()), profile);
        return new DroppedItemAnchorState(
                contact, localPosition,
                contact.tangentialMotion(frame.motion()), entryVelocity, null);
    }

    static SableGravityColumn.Span resolveSableSpan(
            ItemEntity item, Contact contact, Vec3 localPosition) {
        Vec3 worldPosition = contact.frame().toWorldPosition(localPosition);
        if (worldPosition == null || !finite(worldPosition)) {
            return null;
        }
        return SableGravityColumn.resolve(
                contact.level(),
                contact.frame().transform(),
                item.getBoundingBox().move(worldPosition.subtract(item.position())),
                worldPosition);
    }

    Contact contact() {
        return contact;
    }

    void setContact(Contact contact) {
        this.contact = contact;
    }

    boolean isSableAnchor() {
        return sableAnchor;
    }

    boolean isStateSynchronized() {
        return stateSynchronized;
    }

    Vec3 localPosition() {
        return localPosition;
    }

    boolean prepareGeometry(ItemEntity item) {
        if (!sableAnchor) {
            return true;
        }
        sableGeometryAge++;
        if (sableSpan != null
                && sableGeometryAge < 10
                && sableSpan.matches(contact.frame().transform())) {
            return true;
        }
        SableGravityColumn.Span refreshed = resolveSableSpan(item, contact, localPosition);
        if (refreshed == null) {
            return false;
        }
        sableSpan = refreshed;
        sableGeometryAge = 0;
        return true;
    }

    BlockPos profilePos() {
        return sableSpan == null ? contact.topPos() : sableSpan.surfacePos();
    }

    SinkingMedium profileMedium() {
        return sableSpan == null ? contact.medium() : sableSpan.surfaceMedium();
    }

    boolean advance(ItemEntity item, DroppedItemPhysicsProfile profile) {
        return sableAnchor ? advanceSable(item, profile) : advanceOrdinary(item, profile);
    }

    private boolean advanceOrdinary(ItemEntity item, DroppedItemPhysicsProfile profile) {
        Frame frame = contact.frame();
        Vec3 centerOffset = frame.center().subtract(frame.position());
        if (!contact.withinSurfaceFootprint(
                localPosition, centerOffset, item.getBbWidth(), item.getBbHeight())) {
            return false;
        }

        double maximumDepth = DroppedItemDynamics.maximumDepth(
                profile, contact.availableDepth());
        double currentDepth = contact.itemDepth(
                localPosition, centerOffset, item.getBbWidth(), item.getBbHeight());
        double retention = DroppedItemDynamics.horizontalRetention(
                Math.max(0.0D, Math.min(currentDepth, maximumDepth)),
                item.getBbHeight(), profile);
        tangentialVelocity = tangentialVelocity.scale(retention);
        Vec3 movedPosition = localPosition.add(tangentialVelocity);
        if (!contact.withinSurfaceFootprint(
                movedPosition, centerOffset, item.getBbWidth(), item.getBbHeight())) {
            return false;
        }
        double depth = contact.itemDepth(
                movedPosition, centerOffset, item.getBbWidth(), item.getBbHeight());
        double nextDepth;
        if (depth > maximumDepth) {
            double returnSpeed = DroppedItemDynamics.buoyantReturnDepthSpeed(
                    depthVelocity, depth - maximumDepth, profile);
            nextDepth = Math.max(maximumDepth, depth + returnSpeed);
            depthVelocity = returnSpeed;
        } else {
            double sinkSpeed = DroppedItemDynamics.settlingDepthSpeed(
                    depthVelocity, maximumDepth - depth, profile);
            nextDepth = Math.min(maximumDepth, depth + sinkSpeed);
            depthVelocity = sinkSpeed;
        }
        localPosition = contact.positionAtItemDepth(
                movedPosition, centerOffset, item.getBbWidth(), item.getBbHeight(), nextDepth);
        return place(item);
    }

    private boolean advanceSable(ItemEntity item, DroppedItemPhysicsProfile profile) {
        SableGravityColumn.Span span = sableSpan;
        if (span == null) {
            return false;
        }
        double maximumDepth = DroppedItemDynamics.maximumDepth(
                profile, span.availableDepth());
        double depth = span.depthAt(localPosition);
        double retention = DroppedItemDynamics.horizontalRetention(
                Math.max(0.0D, Math.min(depth, maximumDepth)),
                item.getBbHeight(), profile);
        tangentialVelocity = tangentialVelocity.scale(retention);
        Vec3 movedPosition = localPosition.add(tangentialVelocity);
        if (tangentialVelocity.lengthSqr() > 1.0E-10D) {
            SableGravityColumn.Span movedSpan =
                    resolveSableSpan(item, contact, movedPosition);
            if (movedSpan == null) {
                tangentialVelocity = Vec3.ZERO;
                movedPosition = localPosition;
            } else {
                span = movedSpan;
                sableSpan = movedSpan;
                sableGeometryAge = 0;
                maximumDepth = DroppedItemDynamics.maximumDepth(
                        profile, span.availableDepth());
            }
        }

        depth = span.depthAt(movedPosition);
        double nextDepth;
        if (depth > maximumDepth) {
            double returnSpeed = DroppedItemDynamics.buoyantReturnDepthSpeed(
                    depthVelocity, depth - maximumDepth, profile);
            nextDepth = Math.max(maximumDepth, depth + returnSpeed);
            depthVelocity = returnSpeed;
        } else {
            double sinkSpeed = DroppedItemDynamics.settlingDepthSpeed(
                    depthVelocity, maximumDepth - depth, profile);
            nextDepth = Math.min(maximumDepth, depth + sinkSpeed);
            depthVelocity = sinkSpeed;
        }
        localPosition = span.positionAtDepth(nextDepth);
        return place(item);
    }

    boolean place(ItemEntity item) {
        Vec3 worldPosition = contact.frame().toWorldPosition(localPosition);
        if (worldPosition == null || !finite(worldPosition)) {
            return false;
        }
        item.setPos(worldPosition.x, worldPosition.y, worldPosition.z);
        item.setDeltaMovement(Vec3.ZERO);
        item.setOnGround(false);
        item.hasImpulse = true;
        return true;
    }

    void syncPresentation(ItemEntity item, DroppedItemPhysicsProfile profile) {
        boolean active = profile.stablePresentationEnabled();
        int settleTicks = profile.presentationSettleTicks();
        float maximumTilt = (float) profile.presentationMaximumTiltDegrees();
        if (stateSynchronized
                && presentationActive == active
                && presentationSettleTicks == settleTicks
                && Math.abs(presentationMaximumTiltDegrees - maximumTilt) <= 1.0E-6F) {
            return;
        }
        stateSynchronized = true;
        presentationActive = active;
        presentationSettleTicks = settleTicks;
        presentationMaximumTiltDegrees = maximumTilt;
        PacketDistributor.sendToPlayersTrackingEntity(
                item, presentationPayload(item, true, active));
    }

    void clearClientState(ItemEntity item) {
        if (!stateSynchronized) {
            return;
        }
        stateSynchronized = false;
        presentationActive = false;
        PacketDistributor.sendToPlayersTrackingEntity(
                item, presentationPayload(item, false, false));
    }

    DroppedItemMudStatePayload presentationPayload(
            ItemEntity item, boolean anchored, boolean stablePresentation) {
        return new DroppedItemMudStatePayload(
                item.getId(), item.getUUID(), anchored, stablePresentation,
                presentationSettleTicks, presentationMaximumTiltDegrees, sableAnchor);
    }

    boolean presentationActive() {
        return presentationActive;
    }

    private static boolean finite(Vec3 vector) {
        return Double.isFinite(vector.x)
                && Double.isFinite(vector.y)
                && Double.isFinite(vector.z);
    }
}

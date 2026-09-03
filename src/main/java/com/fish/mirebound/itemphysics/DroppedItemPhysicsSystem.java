package com.fish.mirebound.itemphysics;

import com.fish.mirebound.compat.sable.SableCompat;
import com.fish.mirebound.compat.sable.SableGravityColumn;
import com.fish.mirebound.itemphysics.DroppedItemContactResolver.Contact;
import com.fish.mirebound.itemphysics.DroppedItemContactResolver.ContactHint;
import com.fish.mirebound.mud.MudMediumRuntime;
import com.fish.mirebound.mud.SinkingMedium;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-authoritative attachment of dropped items to sinking-medium columns. */
public final class DroppedItemPhysicsSystem {
    private static final Map<ItemEntity, ContactHint> CONTACT_HINTS = new WeakHashMap<>();
    private static final Map<ItemEntity, DroppedItemAnchorState> ACTIVE = new WeakHashMap<>();

    private DroppedItemPhysicsSystem() {
    }

    public static void captureContact(Level level, BlockPos pos, BlockState state,
            ItemEntity item, SinkingMedium medium) {
        if (level.isClientSide() || item.isRemoved()
                || !MudMediumRuntime.enabled(level, pos, medium)) {
            return;
        }
        long gameTime = level.getGameTime();
        ContactHint previous = CONTACT_HINTS.get(item);
        if (previous == null || previous.gameTime() != gameTime) {
            CONTACT_HINTS.put(item, new ContactHint(level, pos.immutable(), gameTime));
        }
    }

    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof ItemEntity item) || item.level().isClientSide()) {
            return;
        }
        if (item.isRemoved() || item.getItem().isEmpty()) {
            release(item);
            return;
        }

        ContactHint hint = CONTACT_HINTS.remove(item);
        DroppedItemAnchorState active = ACTIVE.get(item);
        if (active != null) {
            if (!advanceAnchored(item, active)) {
                release(item);
            }
            return;
        }

        Contact contact = findEntryContact(item, hint);
        if (contact == null || (!contact.exactVolumeHit() && !contact.hasEntered(
                contact.frame().position(), item.getBbWidth(), item.getBbHeight()))) {
            DroppedItemLegacyGravityRecovery.recoverIfStale(item);
            return;
        }
        enter(item, contact);
    }

    /** Captures an item before vanilla applies its generic inside-block escape impulse. */
    public static boolean suppressSableEscape(ItemEntity item) {
        if (item == null || item.isRemoved()) {
            return false;
        }
        if (item.level().isClientSide()) {
            if (item instanceof DroppedItemSableAnchorView view
                    && view.mirebound$isSableMudAnchored()) {
                return true;
            }
            // Before the anchor packet arrives, exact volume contact still suppresses the
            // client's vanilla move-towards-free-space impulse.
            return SableDroppedItemContactProbe.findCurrentVolume(item) != null;
        }
        DroppedItemAnchorState active = ACTIVE.get(item);
        if (active != null) {
            return active.isSableAnchor();
        }
        Contact contact = SableDroppedItemContactProbe.findCurrentVolume(item);
        if (contact == null) {
            return false;
        }
        enter(item, contact);
        return true;
    }

    private static boolean advanceAnchored(ItemEntity item, DroppedItemAnchorState active) {
        Contact contact = DroppedItemContactResolver.refreshAnchor(item, active.contact());
        if (contact == null) {
            return false;
        }
        active.setContact(contact);
        if (!active.prepareGeometry(item)) {
            return false;
        }
        DroppedItemPhysicsProfile profile = MudMediumRuntime.droppedItemProfile(
                contact.level(), active.profilePos(), active.profileMedium());
        if (!profile.enabled() || !active.advance(item, profile)) {
            return false;
        }
        if (active.isSableAnchor()) {
            SableCompat.clearEntityTracking(item);
        }
        active.syncPresentation(item, profile);
        return true;
    }

    private static Contact findEntryContact(ItemEntity item, ContactHint hint) {
        long currentTick = item.level().getGameTime();
        if (hint != null && hint.gameTime() >= currentTick - 1L) {
            Contact hinted = DroppedItemContactResolver.resolve(item, hint);
            if (hinted != null) {
                return hinted;
            }
        }

        Contact sable = SableDroppedItemContactProbe.find(item);
        if (sable != null) {
            return sable;
        }
        Contact current = DroppedItemContactResolver.findCurrentWorldContact(item);
        if (current != null) {
            return current;
        }
        return DroppedItemContactResolver.needsSweptWorldContact(item)
                ? DroppedItemContactResolver.findSweptWorldContact(item) : null;
    }

    private static boolean enter(ItemEntity item, Contact contact) {
        SableGravityColumn.Span sableSpan = contact.subLevel() == null
                ? null : DroppedItemAnchorState.resolveSableSpan(
                        item, contact, contact.frame().position());
        if (contact.subLevel() != null && sableSpan == null) {
            return false;
        }
        BlockPos profilePos = sableSpan == null ? contact.topPos() : sableSpan.surfacePos();
        SinkingMedium profileMedium = sableSpan == null
                ? contact.medium() : sableSpan.surfaceMedium();
        DroppedItemPhysicsProfile profile = MudMediumRuntime.droppedItemProfile(
                contact.level(), profilePos, profileMedium);
        if (!profile.enabled()) {
            return false;
        }

        DroppedItemAnchorState entered = DroppedItemAnchorState.enter(
                item, contact, profile, sableSpan);
        if (!entered.place(item)) {
            return false;
        }
        ACTIVE.put(item, entered);
        if (entered.isSableAnchor()) {
            SableCompat.clearEntityTracking(item);
        }
        entered.syncPresentation(item, profile);
        return true;
    }

    private static void release(ItemEntity item) {
        CONTACT_HINTS.remove(item);
        DroppedItemAnchorState active = ACTIVE.remove(item);
        if (active == null || item.isRemoved()) {
            return;
        }
        active.clearClientState(item);
        item.setOnGround(false);
        item.hasImpulse = true;
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        CONTACT_HINTS.clear();
        ACTIVE.clear();
    }

    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer observer)
                || !(event.getTarget() instanceof ItemEntity item)) {
            return;
        }
        DroppedItemAnchorState active = ACTIVE.get(item);
        if (active != null && active.isStateSynchronized()) {
            PacketDistributor.sendToPlayer(
                    observer, active.presentationPayload(
                            item, true, active.presentationActive()));
        }
    }

    static int activeCount() {
        return ACTIVE.size();
    }

    /** True while the anchor solver is the sole movement owner for a Sable item. */
    public static boolean ownsSableMovement(Entity entity) {
        if (!(entity instanceof ItemEntity item) || entity.level().isClientSide()) {
            return false;
        }
        DroppedItemAnchorState active = ACTIVE.get(item);
        return active != null && active.isSableAnchor();
    }
}

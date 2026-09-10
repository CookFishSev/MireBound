package com.fish.mirebound.coverage.skin;

import com.fish.mirebound.network.payload.SelfSkinStainPayload;
import com.fish.mirebound.network.payload.SkinStainSyncPayload;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Relays only validated self-authored masks to viewers; gameplay coverage is untouched. */
public final class SkinStainSharing {
    private static final State STATE=new State();
    private SkinStainSharing() {}

    public static void accept(ServerPlayer sender, SelfSkinStainPayload payload) {
        if(!com.fish.mirebound.network.ServerInputBudget.allow(sender,
                com.fish.mirebound.network.ServerInputBudget.Channel.SELF_SKIN_STAIN))return;
        long tick=sender.getServer().getTickCount();
        if(!STATE.update(sender.getUUID(),tick,payload.mask())) {
            if(payload.mask().equals(STATE.get(sender.getUUID())))
                PacketDistributor.sendToPlayer(sender,new SkinStainSyncPayload(sender.getUUID(),payload.mask()));
            return;
        }
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(sender,new SkinStainSyncPayload(sender.getUUID(),payload.mask()));
    }

    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if(event.getEntity() instanceof ServerPlayer viewer && event.getTarget() instanceof ServerPlayer owner) {
            SkinStainMaskWire mask=STATE.get(owner.getUUID());
            if(mask!=null)PacketDistributor.sendToPlayer(viewer,new SkinStainSyncPayload(owner.getUUID(),mask));
        }
    }
    public static void onStopTracking(PlayerEvent.StopTracking event) {
        if(event.getEntity() instanceof ServerPlayer viewer && event.getTarget() instanceof ServerPlayer owner)
            PacketDistributor.sendToPlayer(viewer,new SkinStainSyncPayload(owner.getUUID(),null));
    }
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id=event.getEntity().getUUID();
        STATE.remove(id);
    }
    public static void onStop(ServerStoppingEvent event) { STATE.clear(); }

    static final class State {
        private final Map<UUID,SkinStainMaskWire> masks=new HashMap<>();
        private final Map<UUID,Long> updates=new HashMap<>();
        boolean update(UUID authenticatedSender,long tick,SkinStainMaskWire mask) {
            Long last=updates.get(authenticatedSender);
            if(last!=null&&tick-last>=0&&tick-last<40)return false;
            updates.put(authenticatedSender,tick);
            if(mask.equals(masks.get(authenticatedSender)))return false;
            try { mask.decode(); }
            catch(IllegalArgumentException ignored) { return false; }
            masks.put(authenticatedSender,mask);
            return true;
        }
        SkinStainMaskWire get(UUID owner) { return masks.get(owner); }
        void remove(UUID owner) { masks.remove(owner); updates.remove(owner); }
        void clear() { masks.clear(); updates.clear(); }
    }
}

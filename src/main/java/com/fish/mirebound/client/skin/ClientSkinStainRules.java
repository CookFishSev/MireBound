package com.fish.mirebound.client.skin;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.coverage.skin.SkinStainMask;
import com.fish.mirebound.coverage.skin.SkinStainMaskWire;
import com.fish.mirebound.network.payload.SelfSkinStainPayload;
import com.fish.mirebound.network.payload.SkinStainSyncPayload;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

/** Only the local mask is editable. Remote masks come exclusively from the server. */
public final class ClientSkinStainRules {
    private static SkinStainMask local;
    private static SkinStainMaskWire pending;
    private static final Map<UUID,SkinStainMask> REMOTE=new LinkedHashMap<>();
    private static long revision, ownRevision, acknowledgedRevision=-1;
    private static int sendTicks;
    private static String loadError;

    private ClientSkinStainRules() {}

    public static SkinStainStorage storage() {
        return new SkinStainStorage(Minecraft.getInstance().gameDirectory.toPath().resolve("mirebound/skin-coverage"));
    }

    public static SkinStainMask ownMask() {
        if(local==null) {
            try {
                local=storage().loadMask();
                pending=SkinStainMaskWire.encode(local);
            } catch(IOException | IllegalArgumentException error) {
                Mirebound.LOGGER.warn("Unable to load a shareable self skin mask",error);
                local=SkinStainMask.empty(64,64);
                pending=SkinStainMaskWire.encode(local);
                loadError="gui.mirebound.skin.io_error";
            }
            revision++; ownRevision++;
        }
        return local;
    }

    public static String loadError() { ownMask(); return loadError; }
    public static long revision() { ownMask(); return revision; }

    public static SkinStainMask forEntity(int entityId) {
        Minecraft minecraft=Minecraft.getInstance();
        var entity=minecraft.level==null?null:minecraft.level.getEntity(entityId);
        if(entity==null)return null;
        SkinStainMask mask=entity.getUUID().equals(minecraft.getGameProfile().getId())?ownMask():REMOTE.get(entity.getUUID());
        return mask==null||mask.isEmpty()?null:mask;
    }

    public static void save(SkinStainMask next) throws IOException {
        SkinStainMaskWire encoded=SkinStainMaskWire.encode(next);
        storage().saveMask(next);
        local=next; pending=encoded; loadError=null;
        revision++; ownRevision++;
        SkinStainMaskTextures.reset();
    }

    public static void tick() {
        Minecraft minecraft=Minecraft.getInstance();
        if(minecraft.player==null||minecraft.getConnection()==null)return;
        ownMask();
        sendTicks++;
        if(acknowledgedRevision==ownRevision || sendTicks<60)return;
        PacketDistributor.sendToServer(new SelfSkinStainPayload(pending));
        sendTicks=0;
    }

    public static void accept(SkinStainSyncPayload payload) {
        if(payload.owner().equals(Minecraft.getInstance().getGameProfile().getId())) {
            ownMask();
            if(pending.equals(payload.mask()))acknowledgedRevision=ownRevision;
            return;
        }
        if(payload.mask()==null) { if(REMOTE.remove(payload.owner())!=null)revision++; return; }
        try {
            SkinStainMask mask=payload.mask().decode();
            if(mask.isEmpty()) REMOTE.remove(payload.owner());
            else {
                if(!REMOTE.containsKey(payload.owner())&&REMOTE.size()>=256)return;
                long bytes=REMOTE.values().stream().mapToLong(SkinStainMask::storageBytes).sum();
                SkinStainMask previous=REMOTE.get(payload.owner());
                if(bytes-(previous==null?0:previous.storageBytes())+mask.storageBytes()>32L*1024*1024)return;
                REMOTE.put(payload.owner(),mask);
            }
            revision++;
        } catch(IllegalArgumentException error) { Mirebound.LOGGER.warn("Ignoring invalid shared skin mask"); }
    }

    public static void resetSession() {
        REMOTE.clear(); acknowledgedRevision=-1; sendTicks=60; revision++;
        SkinStainMaskTextures.reset();
    }
}

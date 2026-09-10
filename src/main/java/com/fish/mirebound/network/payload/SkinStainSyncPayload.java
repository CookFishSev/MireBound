package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.coverage.skin.SkinStainMaskWire;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-owned identity; a null mask forgets a player leaving the tracking set. */
public record SkinStainSyncPayload(UUID owner, SkinStainMaskWire mask) implements CustomPacketPayload {
    public static final Type<SkinStainSyncPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID,"skin_stain_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf,SkinStainSyncPayload> STREAM_CODEC=new StreamCodec<>() {
        public SkinStainSyncPayload decode(RegistryFriendlyByteBuf buffer) {
            UUID owner=buffer.readUUID();
            return new SkinStainSyncPayload(owner,buffer.readBoolean()?SkinStainMaskWire.read(buffer):null);
        }
        public void encode(RegistryFriendlyByteBuf buffer,SkinStainSyncPayload payload) {
            buffer.writeUUID(payload.owner); buffer.writeBoolean(payload.mask!=null);
            if(payload.mask!=null)payload.mask.write(buffer);
        }
    };
    public Type<SkinStainSyncPayload> type() { return TYPE; }
}

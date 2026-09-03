package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client intent to add one fixed-length segment at a rope endpoint. */
public record RopeExtendPayload(int ropeId, int endpointSegment) implements CustomPacketPayload {
    public static final Type<RopeExtendPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "rope_extend"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RopeExtendPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public RopeExtendPayload decode(RegistryFriendlyByteBuf buffer) {
                    return new RopeExtendPayload(buffer.readVarInt(), buffer.readVarInt());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, RopeExtendPayload payload) {
                    buffer.writeVarInt(payload.ropeId());
                    buffer.writeVarInt(payload.endpointSegment());
                }
            };

    @Override
    public Type<RopeExtendPayload> type() {
        return TYPE;
    }
}

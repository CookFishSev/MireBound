package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-validated request to permanently anchor one rope segment. */
public record RopeAnchorPayload(int ropeId, int segmentIndex)
        implements CustomPacketPayload {
    public static final Type<RopeAnchorPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "rope_anchor"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RopeAnchorPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public RopeAnchorPayload decode(RegistryFriendlyByteBuf buffer) {
                    return new RopeAnchorPayload(buffer.readVarInt(), buffer.readVarInt());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer,
                        RopeAnchorPayload payload) {
                    buffer.writeVarInt(payload.ropeId());
                    buffer.writeVarInt(payload.segmentIndex());
                }
            };

    @Override
    public Type<RopeAnchorPayload> type() {
        return TYPE;
    }
}

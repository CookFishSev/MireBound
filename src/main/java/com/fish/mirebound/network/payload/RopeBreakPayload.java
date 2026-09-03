package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client intent to keep mining one rope segment. */
public record RopeBreakPayload(boolean breaking, int ropeId, int segmentIndex,
        boolean allConnected) implements CustomPacketPayload {
    public static final Type<RopeBreakPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "rope_break"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RopeBreakPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public RopeBreakPayload decode(RegistryFriendlyByteBuf buffer) {
                    return new RopeBreakPayload(buffer.readBoolean(), buffer.readVarInt(),
                            buffer.readVarInt(), buffer.readBoolean());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, RopeBreakPayload payload) {
                    buffer.writeBoolean(payload.breaking());
                    buffer.writeVarInt(payload.ropeId());
                    buffer.writeVarInt(payload.segmentIndex());
                    buffer.writeBoolean(payload.allConnected());
                }
            };

    @Override
    public Type<RopeBreakPayload> type() {
        return TYPE;
    }
}

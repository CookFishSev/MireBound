package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Owner-side press/release intent; hold duration is measured by the server. */
public record AssimilationQteInputPayload(
        int sequence, int cell, int button, int phase) implements CustomPacketPayload {
    public static final int PRESS = 1;
    public static final int RELEASE = 2;
    public static final Type<AssimilationQteInputPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "assimilation_qte_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AssimilationQteInputPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public AssimilationQteInputPayload decode(RegistryFriendlyByteBuf buffer) {
                    return new AssimilationQteInputPayload(
                            buffer.readVarInt(), buffer.readVarInt(),
                            buffer.readUnsignedByte(), buffer.readUnsignedByte());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer,
                        AssimilationQteInputPayload payload) {
                    buffer.writeVarInt(payload.sequence());
                    buffer.writeVarInt(payload.cell());
                    buffer.writeByte(payload.button());
                    buffer.writeByte(payload.phase());
                }
            };

    @Override
    public Type<AssimilationQteInputPayload> type() {
        return TYPE;
    }
}

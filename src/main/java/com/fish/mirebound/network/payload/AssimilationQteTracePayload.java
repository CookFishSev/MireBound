package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Sparse start/node/release events for the server-authoritative 4x4 trace QTE. */
public record AssimilationQteTracePayload(
        int sequence, int cell, int button, int action, int node) implements CustomPacketPayload {
    public static final int START = 1;
    public static final int NODE = 2;
    public static final int RELEASE = 3;
    public static final Type<AssimilationQteTracePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "assimilation_qte_trace"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AssimilationQteTracePayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public AssimilationQteTracePayload decode(RegistryFriendlyByteBuf buffer) {
                    return new AssimilationQteTracePayload(
                            buffer.readVarInt(), buffer.readVarInt(), buffer.readUnsignedByte(),
                            buffer.readUnsignedByte(), buffer.readUnsignedByte());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer,
                        AssimilationQteTracePayload payload) {
                    buffer.writeVarInt(payload.sequence());
                    buffer.writeVarInt(payload.cell());
                    buffer.writeByte(payload.button());
                    buffer.writeByte(payload.action());
                    buffer.writeByte(payload.node());
                }
            };

    @Override
    public Type<AssimilationQteTracePayload> type() {
        return TYPE;
    }
}

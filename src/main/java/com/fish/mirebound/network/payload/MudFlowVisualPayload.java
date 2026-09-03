package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.mud.SinkingMedium;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** One bounded client-side bridge between two committed finite-volume updates. */
public record MudFlowVisualPayload(
        long sourcePos,
        long targetPos,
        SinkingMedium medium,
        int sourcePixelsAfter,
        int targetPixelsAfter,
        int transferredPixels,
        int durationTicks) implements CustomPacketPayload {
    public static final Type<MudFlowVisualPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "mud_flow_visual"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MudFlowVisualPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public MudFlowVisualPayload decode(RegistryFriendlyByteBuf buffer) {
                    return new MudFlowVisualPayload(
                            buffer.readLong(),
                            buffer.readLong(),
                            SinkingMedium.byId(buffer.readUnsignedByte()),
                            buffer.readUnsignedByte(),
                            buffer.readUnsignedByte(),
                            buffer.readUnsignedByte(),
                            buffer.readUnsignedByte());
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer, MudFlowVisualPayload payload) {
                    buffer.writeLong(payload.sourcePos());
                    buffer.writeLong(payload.targetPos());
                    buffer.writeByte(payload.medium().id());
                    buffer.writeByte(payload.sourcePixelsAfter());
                    buffer.writeByte(payload.targetPixelsAfter());
                    buffer.writeByte(payload.transferredPixels());
                    buffer.writeByte(payload.durationTicks());
                }
            };

    public MudFlowVisualPayload {
        sourcePixelsAfter = Mth.clamp(sourcePixelsAfter, 0, 16);
        targetPixelsAfter = Mth.clamp(targetPixelsAfter, 1, 16);
        transferredPixels = Mth.clamp(transferredPixels, 1, 16);
        durationTicks = Mth.clamp(durationTicks, 4, 12);
    }

    public BlockPos source() {
        return BlockPos.of(sourcePos);
    }

    public BlockPos target() {
        return BlockPos.of(targetPos);
    }

    @Override
    public Type<MudFlowVisualPayload> type() {
        return TYPE;
    }
}

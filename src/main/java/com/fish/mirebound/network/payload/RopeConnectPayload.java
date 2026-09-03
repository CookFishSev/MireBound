package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client intent to join the dragged endpoint to another rope endpoint. */
public record RopeConnectPayload(
        int sourceRopeId, int sourceSegment, int targetRopeId, int targetSegment)
        implements CustomPacketPayload {
    public static final Type<RopeConnectPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "rope_connect"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RopeConnectPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    RopeConnectPayload::sourceRopeId,
                    ByteBufCodecs.VAR_INT,
                    RopeConnectPayload::sourceSegment,
                    ByteBufCodecs.VAR_INT,
                    RopeConnectPayload::targetRopeId,
                    ByteBufCodecs.VAR_INT,
                    RopeConnectPayload::targetSegment,
                    RopeConnectPayload::new);

    @Override
    public Type<RopeConnectPayload> type() {
        return TYPE;
    }
}

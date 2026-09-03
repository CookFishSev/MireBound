package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Keeps one visible rescue-rope segment engaged while right click is held. */
public record RopeRescueHaulPayload(
        boolean active, int ropeId, int segmentIndex) implements CustomPacketPayload {
    public static final Type<RopeRescueHaulPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "rope_rescue_haul"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RopeRescueHaulPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL,
                    RopeRescueHaulPayload::active,
                    ByteBufCodecs.VAR_INT,
                    RopeRescueHaulPayload::ropeId,
                    ByteBufCodecs.VAR_INT,
                    RopeRescueHaulPayload::segmentIndex,
                    RopeRescueHaulPayload::new);

    @Override
    public Type<RopeRescueHaulPayload> type() {
        return TYPE;
    }
}

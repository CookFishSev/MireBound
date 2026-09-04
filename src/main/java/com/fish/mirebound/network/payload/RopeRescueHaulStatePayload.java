package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server confirmation for one rescue-haul input session. */
public record RopeRescueHaulStatePayload(
        int ropeId, int segmentIndex, long sessionId, boolean active)
        implements CustomPacketPayload {
    public static final Type<RopeRescueHaulStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID,
                    "rope_rescue_haul_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RopeRescueHaulStatePayload>
            STREAM_CODEC = StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    RopeRescueHaulStatePayload::ropeId,
                    ByteBufCodecs.VAR_INT,
                    RopeRescueHaulStatePayload::segmentIndex,
                    ByteBufCodecs.VAR_LONG,
                    RopeRescueHaulStatePayload::sessionId,
                    ByteBufCodecs.BOOL,
                    RopeRescueHaulStatePayload::active,
                    RopeRescueHaulStatePayload::new);

    @Override
    public Type<RopeRescueHaulStatePayload> type() {
        return TYPE;
    }
}

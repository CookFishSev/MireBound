package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Marks the currently charged rope throw as a single-player rescue cast. */
public record RopeRescueCastPayload(boolean armed) implements CustomPacketPayload {
    public static final Type<RopeRescueCastPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "rope_rescue_cast"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RopeRescueCastPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL,
                    RopeRescueCastPayload::armed,
                    RopeRescueCastPayload::new);

    @Override
    public Type<RopeRescueCastPayload> type() {
        return TYPE;
    }
}

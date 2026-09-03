package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client hit intent; the server validates enclosure state and pillar ownership. */
public record TenderFleshStrikePayload(int pillarIndex) implements CustomPacketPayload {
    public static final Type<TenderFleshStrikePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "tender_flesh_strike"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TenderFleshStrikePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    TenderFleshStrikePayload::pillarIndex,
                    TenderFleshStrikePayload::new);

    @Override
    public Type<TenderFleshStrikePayload> type() {
        return TYPE;
    }
}

package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client intent to toggle active rejection or report a movement attempt. */
public record AssimilationPurgeInputPayload(boolean movementAttempt)
        implements CustomPacketPayload {
    public static final Type<AssimilationPurgeInputPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "assimilation_purge_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AssimilationPurgeInputPayload>
            STREAM_CODEC = StreamCodec.composite(
                    ByteBufCodecs.BOOL,
                    AssimilationPurgeInputPayload::movementAttempt,
                    AssimilationPurgeInputPayload::new);

    @Override
    public Type<AssimilationPurgeInputPayload> type() {
        return TYPE;
    }
}

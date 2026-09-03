package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Requests acknowledgement of the adaptive-conversion compatibility warning. */
public record MudTuningConversionUnlockPayload(boolean confirmed)
        implements CustomPacketPayload {
    public static final Type<MudTuningConversionUnlockPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    Mirebound.MOD_ID, "mud_tuning_conversion_unlock"));
    public static final StreamCodec<RegistryFriendlyByteBuf,
            MudTuningConversionUnlockPayload> STREAM_CODEC = StreamCodec.composite(
                    ByteBufCodecs.BOOL,
                    MudTuningConversionUnlockPayload::confirmed,
                    MudTuningConversionUnlockPayload::new);

    @Override
    public Type<MudTuningConversionUnlockPayload> type() {
        return TYPE;
    }
}

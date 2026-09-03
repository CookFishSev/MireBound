package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Synchronizes the current save's server-authoritative conversion safety state. */
public record MudTuningConversionSafetyPayload(boolean unlocked,
        boolean unrestrictedUnlocked, boolean unrestrictedEnabled)
        implements CustomPacketPayload {
    public static final Type<MudTuningConversionSafetyPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    Mirebound.MOD_ID, "mud_tuning_conversion_safety"));
    public static final StreamCodec<RegistryFriendlyByteBuf,
            MudTuningConversionSafetyPayload> STREAM_CODEC = StreamCodec.composite(
                    ByteBufCodecs.BOOL,
                    MudTuningConversionSafetyPayload::unlocked,
                    ByteBufCodecs.BOOL,
                    MudTuningConversionSafetyPayload::unrestrictedUnlocked,
                    ByteBufCodecs.BOOL,
                    MudTuningConversionSafetyPayload::unrestrictedEnabled,
                    MudTuningConversionSafetyPayload::new);

    @Override
    public Type<MudTuningConversionSafetyPayload> type() {
        return TYPE;
    }
}

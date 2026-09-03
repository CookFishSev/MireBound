package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-authoritative values displayed by the tuning-wand settings screen. */
public record MudTuningGlobalSettingsPayload(
        int eruptionMaximumActivePerLevel,
        boolean entityCoverageEnabled,
        int entityCoverageAutomaticFadeSeconds,
        double interactionRange,
        boolean editable) implements CustomPacketPayload {
    public static final Type<MudTuningGlobalSettingsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "mud_tuning_global_settings"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MudTuningGlobalSettingsPayload>
            STREAM_CODEC = new StreamCodec<>() {
                @Override
                public MudTuningGlobalSettingsPayload decode(RegistryFriendlyByteBuf buffer) {
                    return new MudTuningGlobalSettingsPayload(
                            buffer.readVarInt(), buffer.readBoolean(), buffer.readVarInt(),
                            buffer.readDouble(), buffer.readBoolean());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer,
                        MudTuningGlobalSettingsPayload payload) {
                    buffer.writeVarInt(payload.eruptionMaximumActivePerLevel);
                    buffer.writeBoolean(payload.entityCoverageEnabled);
                    buffer.writeVarInt(payload.entityCoverageAutomaticFadeSeconds);
                    buffer.writeDouble(payload.interactionRange);
                    buffer.writeBoolean(payload.editable);
                }
            };

    @Override
    public Type<MudTuningGlobalSettingsPayload> type() {
        return TYPE;
    }

}

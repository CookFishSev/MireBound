package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Requests or applies the small set of world-wide tuning-wand settings. */
public record MudTuningGlobalRequestPayload(
        boolean apply,
        int eruptionMaximumActivePerLevel,
        boolean entityCoverageEnabled,
        int entityCoverageAutomaticFadeSeconds,
        double interactionRange) implements CustomPacketPayload {
    public static final Type<MudTuningGlobalRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "mud_tuning_global_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MudTuningGlobalRequestPayload>
            STREAM_CODEC = new StreamCodec<>() {
                @Override
                public MudTuningGlobalRequestPayload decode(RegistryFriendlyByteBuf buffer) {
                    return new MudTuningGlobalRequestPayload(
                            buffer.readBoolean(), buffer.readVarInt(), buffer.readBoolean(),
                            buffer.readVarInt(), buffer.readDouble());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer,
                        MudTuningGlobalRequestPayload payload) {
                    buffer.writeBoolean(payload.apply);
                    buffer.writeVarInt(payload.eruptionMaximumActivePerLevel);
                    buffer.writeBoolean(payload.entityCoverageEnabled);
                    buffer.writeVarInt(payload.entityCoverageAutomaticFadeSeconds);
                    buffer.writeDouble(payload.interactionRange);
                }
            };

    public static MudTuningGlobalRequestPayload open() {
        return new MudTuningGlobalRequestPayload(false, 0, false, 0, 0.0D);
    }

    public boolean hasFiniteInteractionRange() {
        return Double.isFinite(interactionRange);
    }

    @Override
    public Type<MudTuningGlobalRequestPayload> type() {
        return TYPE;
    }
}

package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Synchronized cage-only activation for a tuning wand used without a target. */
public record MudTuningWandPulsePayload(int playerEntityId,
        boolean mainHand, long coreGameTime) implements CustomPacketPayload {
    public static final Type<MudTuningWandPulsePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    Mirebound.MOD_ID, "mud_tuning_wand_pulse"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MudTuningWandPulsePayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public MudTuningWandPulsePayload decode(RegistryFriendlyByteBuf buffer) {
                    return new MudTuningWandPulsePayload(
                            buffer.readVarInt(), buffer.readBoolean(), buffer.readVarLong());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer,
                        MudTuningWandPulsePayload payload) {
                    buffer.writeVarInt(payload.playerEntityId);
                    buffer.writeBoolean(payload.mainHand);
                    buffer.writeVarLong(payload.coreGameTime);
                }
            };

    @Override
    public Type<MudTuningWandPulsePayload> type() {
        return TYPE;
    }
}

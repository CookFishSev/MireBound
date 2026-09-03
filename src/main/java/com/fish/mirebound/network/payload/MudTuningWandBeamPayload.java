package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.mud.MudTuningAnchor;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** One server-authoritative tuning-wand selection pulse. */
public record MudTuningWandBeamPayload(int playerEntityId, MudTuningAnchor target,
        boolean mainHand, long coreGameTime) implements CustomPacketPayload {
    public static final Type<MudTuningWandBeamPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "mud_tuning_wand_beam"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MudTuningWandBeamPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public MudTuningWandBeamPayload decode(RegistryFriendlyByteBuf buffer) {
                    return new MudTuningWandBeamPayload(
                            buffer.readVarInt(), MudTuningAnchor.read(buffer),
                            buffer.readBoolean(), buffer.readVarLong());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer,
                        MudTuningWandBeamPayload payload) {
                    buffer.writeVarInt(payload.playerEntityId);
                    MudTuningAnchor.write(buffer, payload.target);
                    buffer.writeBoolean(payload.mainHand);
                    buffer.writeVarLong(payload.coreGameTime);
                }
            };

    @Override
    public Type<MudTuningWandBeamPayload> type() {
        return TYPE;
    }
}

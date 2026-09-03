package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.mud.tuning.MudTuningSelectionElement;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MudTuningSelectionNudgePayload(
        MudTuningSelectionElement element, Direction direction)
        implements CustomPacketPayload {
    public static final Type<MudTuningSelectionNudgePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    Mirebound.MOD_ID, "mud_tuning_selection_nudge"));
    public static final StreamCodec<RegistryFriendlyByteBuf,
            MudTuningSelectionNudgePayload> STREAM_CODEC = new StreamCodec<>() {
                @Override
                public MudTuningSelectionNudgePayload decode(RegistryFriendlyByteBuf buffer) {
                    int elementId = buffer.readVarInt();
                    int directionId = buffer.readVarInt();
                    return new MudTuningSelectionNudgePayload(
                            MudTuningSelectionElement.byId(elementId),
                            safeDirection(directionId));
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer,
                        MudTuningSelectionNudgePayload payload) {
                    buffer.writeVarInt(payload.element().ordinal());
                    buffer.writeVarInt(payload.direction().get3DDataValue());
                }
            };

    private static Direction safeDirection(int id) {
        return id >= 0 && id < Direction.values().length
                ? Direction.from3DDataValue(id) : Direction.UP;
    }

    @Override
    public Type<MudTuningSelectionNudgePayload> type() {
        return TYPE;
    }
}

package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.mud.MudTuningAnchor;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MudTuningRequestPayload(Action action, MudTuningAnchor anchor) implements CustomPacketPayload {
    public static final Type<MudTuningRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "mud_tuning_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MudTuningRequestPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public MudTuningRequestPayload decode(RegistryFriendlyByteBuf buffer) {
                    return new MudTuningRequestPayload(
                            Action.byId(buffer.readVarInt()), MudTuningAnchor.read(buffer));
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, MudTuningRequestPayload payload) {
                    buffer.writeVarInt(payload.action.ordinal());
                    MudTuningAnchor.write(buffer, payload.anchor);
                }
            };

    @Override
    public Type<MudTuningRequestPayload> type() {
        return TYPE;
    }

    public enum Action {
        SELECT_FIRST,
        SELECT_SECOND,
        OPEN_RANGE,
        OPEN_SINGLE,
        OPEN_WORLD,
        REFRESH,
        REFRESH_SESSION,
        ACTIVATE_WAND,
        CLEAR_SELECTION,
        LOCK_TARGET,
        CONVERT_SINGLE,
        RESTORE_SINGLE,
        CONVERT_RANGE,
        RESTORE_RANGE;

        private static Action byId(int id) {
            Action[] values = values();
            return id >= 0 && id < values.length ? values[id] : REFRESH;
        }
    }
}

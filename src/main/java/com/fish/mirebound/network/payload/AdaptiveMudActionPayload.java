package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.mud.MudTuningAnchor;
import com.fish.mirebound.mud.MudTuningScope;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-authoritative conversion or restoration request from the tuning screen. */
public record AdaptiveMudActionPayload(Action action, MudTuningScope scope,
        MudTuningAnchor first, MudTuningAnchor second, int mediumId,
        ResourceLocation sourceBlockId)
        implements CustomPacketPayload {
    public static final ResourceLocation ALL_SOURCE_BLOCKS =
            ResourceLocation.withDefaultNamespace("air");
    public static final Type<AdaptiveMudActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "adaptive_mud_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AdaptiveMudActionPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public AdaptiveMudActionPayload decode(RegistryFriendlyByteBuf buffer) {
                    return new AdaptiveMudActionPayload(
                            Action.byId(buffer.readVarInt()),
                            MudTuningScope.byId(buffer.readVarInt()),
                            MudTuningAnchor.read(buffer),
                            MudTuningAnchor.read(buffer),
                            buffer.readVarInt(),
                            buffer.readResourceLocation());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer,
                        AdaptiveMudActionPayload payload) {
                    buffer.writeVarInt(payload.action.ordinal());
                    buffer.writeVarInt(payload.scope.ordinal());
                    MudTuningAnchor.write(buffer, payload.first);
                    MudTuningAnchor.write(buffer, payload.second);
                    buffer.writeVarInt(payload.mediumId);
                    buffer.writeResourceLocation(payload.sourceBlockId);
                }
            };

    @Override
    public Type<AdaptiveMudActionPayload> type() {
        return TYPE;
    }

    public enum Action {
        CONVERT,
        RESTORE,
        INVALID;

        private static Action byId(int id) {
            Action[] values = values();
            return id >= 0 && id < INVALID.ordinal() ? values[id] : INVALID;
        }
    }
}

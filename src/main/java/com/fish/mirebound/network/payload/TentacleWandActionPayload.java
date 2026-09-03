package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Operator wand intent for spawning a tentacle or opening its root configuration. */
public record TentacleWandActionPayload(
        Action action, int instanceId, double x, double y, double z, int volume)
        implements CustomPacketPayload {
    public static final int MINIMUM_SUMMON_VOLUME = 1;
    public static final int MAXIMUM_SUMMON_VOLUME = 50;
    public static final Type<TentacleWandActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "tentacle_wand_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TentacleWandActionPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public TentacleWandActionPayload decode(RegistryFriendlyByteBuf buffer) {
                    return new TentacleWandActionPayload(Action.byId(buffer.readVarInt()),
                            buffer.readVarInt(), buffer.readDouble(), buffer.readDouble(),
                            buffer.readDouble(), buffer.readVarInt());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer,
                        TentacleWandActionPayload payload) {
                    buffer.writeVarInt(payload.action.ordinal());
                    buffer.writeVarInt(payload.instanceId);
                    buffer.writeDouble(payload.x);
                    buffer.writeDouble(payload.y);
                    buffer.writeDouble(payload.z);
                    buffer.writeVarInt(payload.volume);
                }
            };

    @Override
    public Type<TentacleWandActionPayload> type() {
        return TYPE;
    }

    public enum Action {
        SUMMON,
        CONFIGURE,
        REMOVE,
        INVALID;

        private static Action byId(int id) {
            Action[] values = values();
            return id >= 0 && id < INVALID.ordinal() ? values[id] : INVALID;
        }
    }
}

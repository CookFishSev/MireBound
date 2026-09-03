package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Starts, refreshes, or stops one server-authoritative continuous spray session. */
public record WaterGunFirePayload(boolean firing) implements CustomPacketPayload {
    public static final Type<WaterGunFirePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "water_gun_fire"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WaterGunFirePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public WaterGunFirePayload decode(RegistryFriendlyByteBuf buffer) {
            return new WaterGunFirePayload(buffer.readBoolean());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, WaterGunFirePayload value) {
            buffer.writeBoolean(value.firing());
        }
    };

    @Override
    public Type<WaterGunFirePayload> type() {
        return TYPE;
    }
}

package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MudDeveloperOptionsPayload(boolean physicsLog) implements CustomPacketPayload {
    public static final Type<MudDeveloperOptionsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "developer_options"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MudDeveloperOptionsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            MudDeveloperOptionsPayload::physicsLog,
            MudDeveloperOptionsPayload::new);

    @Override
    public Type<MudDeveloperOptionsPayload> type() {
        return TYPE;
    }
}

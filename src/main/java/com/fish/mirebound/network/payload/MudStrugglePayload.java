package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MudStrugglePayload(boolean pressed, int chargeTicks) implements CustomPacketPayload {
    public static final Type<MudStrugglePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "mud_struggle"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MudStrugglePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            MudStrugglePayload::pressed,
            ByteBufCodecs.VAR_INT,
            MudStrugglePayload::chargeTicks,
            MudStrugglePayload::new);

    @Override
    public Type<MudStrugglePayload> type() {
        return TYPE;
    }
}

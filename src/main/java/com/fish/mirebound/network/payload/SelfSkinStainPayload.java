package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.coverage.skin.SkinStainMaskWire;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** No owner field: only the authenticated sender can change their own skin mask. */
public record SelfSkinStainPayload(SkinStainMaskWire mask) implements CustomPacketPayload {
    public static final Type<SelfSkinStainPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID,"self_skin_stain"));
    public static final StreamCodec<RegistryFriendlyByteBuf,SelfSkinStainPayload> STREAM_CODEC = new StreamCodec<>() {
        public SelfSkinStainPayload decode(RegistryFriendlyByteBuf buffer) { return new SelfSkinStainPayload(SkinStainMaskWire.read(buffer)); }
        public void encode(RegistryFriendlyByteBuf buffer, SelfSkinStainPayload payload) { payload.mask.write(buffer); }
    };
    public Type<SelfSkinStainPayload> type() { return TYPE; }
}

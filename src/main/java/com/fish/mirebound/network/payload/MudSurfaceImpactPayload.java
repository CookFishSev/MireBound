package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.mud.SinkingMedium;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** One compact event that lets the client widen the displaced rim after impact. */
public record MudSurfaceImpactPayload(
        int entityId,
        SinkingMedium medium,
        double originX,
        double originY,
        double originZ,
        float impactStrength,
        float volumeFraction) implements CustomPacketPayload {
    public static final Type<MudSurfaceImpactPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "mud_surface_impact"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MudSurfaceImpactPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public MudSurfaceImpactPayload decode(RegistryFriendlyByteBuf buffer) {
                    return new MudSurfaceImpactPayload(
                            buffer.readVarInt(),
                            SinkingMedium.byId(buffer.readUnsignedByte()),
                            buffer.readDouble(),
                            buffer.readDouble(),
                            buffer.readDouble(),
                            buffer.readFloat(),
                            buffer.readFloat());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, MudSurfaceImpactPayload payload) {
                    buffer.writeVarInt(payload.entityId());
                    buffer.writeByte(payload.medium().id());
                    buffer.writeDouble(payload.originX());
                    buffer.writeDouble(payload.originY());
                    buffer.writeDouble(payload.originZ());
                    buffer.writeFloat(payload.impactStrength());
                    buffer.writeFloat(payload.volumeFraction());
                }
            };

    public MudSurfaceImpactPayload {
        impactStrength = (float) Mth.clamp(impactStrength, 0.0D, 1.0D);
        volumeFraction = (float) Mth.clamp(volumeFraction, 0.0D, 1.0D);
    }

    public net.minecraft.world.phys.Vec3 origin() {
        return new net.minecraft.world.phys.Vec3(originX, originY, originZ);
    }

    @Override
    public Type<MudSurfaceImpactPayload> type() {
        return TYPE;
    }
}

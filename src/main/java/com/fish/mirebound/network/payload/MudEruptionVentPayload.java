package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.mud.SinkingMedium;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Spawn/close state for one server-owned, non-entity mud vent. */
public record MudEruptionVentPayload(
        ResourceLocation dimension,
        int ventId,
        boolean active,
        SinkingMedium medium,
        double x,
        double y,
        double z,
        float radiusPixels,
        long seed,
        int mergeEntityId,
        long visualSource,
        UUID subLevelId,
        long supportPos,
        Direction face) implements CustomPacketPayload {
    public static final Type<MudEruptionVentPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "mud_eruption_vent"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MudEruptionVentPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public MudEruptionVentPayload decode(RegistryFriendlyByteBuf buffer) {
                    return new MudEruptionVentPayload(
                            buffer.readResourceLocation(),
                            buffer.readVarInt(),
                            buffer.readBoolean(),
                            SinkingMedium.byId(buffer.readUnsignedByte()),
                            buffer.readDouble(),
                            buffer.readDouble(),
                            buffer.readDouble(),
                            buffer.readFloat(),
                            buffer.readLong(),
                            buffer.readVarInt() - 1,
                            buffer.readLong(),
                            buffer.readBoolean() ? buffer.readUUID() : null,
                            buffer.readLong(),
                            safeDirection(buffer.readUnsignedByte()));
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, MudEruptionVentPayload payload) {
                    buffer.writeResourceLocation(payload.dimension());
                    buffer.writeVarInt(payload.ventId());
                    buffer.writeBoolean(payload.active());
                    buffer.writeByte(payload.medium().id());
                    buffer.writeDouble(payload.x());
                    buffer.writeDouble(payload.y());
                    buffer.writeDouble(payload.z());
                    buffer.writeFloat(payload.radiusPixels());
                    buffer.writeLong(payload.seed());
                    buffer.writeVarInt(payload.mergeEntityId() + 1);
                    buffer.writeLong(payload.visualSource());
                    buffer.writeBoolean(payload.subLevelId() != null);
                    if (payload.subLevelId() != null) {
                        buffer.writeUUID(payload.subLevelId());
                    }
                    buffer.writeLong(payload.supportPos());
                    buffer.writeByte(payload.face().get3DDataValue());
                }
            };

    private static Direction safeDirection(int id) {
        return id >= 0 && id < Direction.values().length
                ? Direction.from3DDataValue(id) : Direction.UP;
    }

    public MudEruptionVentPayload {
        ventId = Math.max(1, ventId);
        radiusPixels = (float) Mth.clamp(radiusPixels, 1.0D, 24.0D);
        mergeEntityId = Math.max(-1, mergeEntityId);
        face = face == null ? Direction.UP : face;
    }

    public Vec3 origin() {
        return new Vec3(x, y, z);
    }

    public BlockPos supportBlockPos() {
        return BlockPos.of(supportPos);
    }

    public boolean physicalized() {
        return subLevelId != null;
    }

    @Override
    public Type<MudEruptionVentPayload> type() {
        return TYPE;
    }
}

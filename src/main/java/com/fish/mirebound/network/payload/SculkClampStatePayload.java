package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public record SculkClampStatePayload(
        int entityId,
        boolean active,
        Vec3 surfacePoint,
        Vec3 surfaceNormal,
        Vec3 surfaceAxisX,
        Vec3 surfaceAxisZ,
        long visualSource,
        float radius,
        float height,
        float renderDistance,
        int emergeTicks,
        int retractTicks,
        int closeTicks,
        int openTicks,
        int remainingTicks,
        int maximumTicks) implements CustomPacketPayload {

    public static final Type<SculkClampStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "sculk_clamp_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SculkClampStatePayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public SculkClampStatePayload decode(RegistryFriendlyByteBuf buffer) {
                    return new SculkClampStatePayload(
                            buffer.readVarInt(),
                            buffer.readBoolean(),
                            readDoubleVector(buffer),
                            readFloatVector(buffer),
                            readFloatVector(buffer),
                            readFloatVector(buffer),
                            buffer.readLong(),
                            buffer.readFloat(),
                            buffer.readFloat(),
                            buffer.readFloat(),
                            buffer.readVarInt(),
                            buffer.readVarInt(),
                            buffer.readVarInt(),
                            buffer.readVarInt(),
                            buffer.readVarInt(),
                            buffer.readVarInt());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, SculkClampStatePayload payload) {
                    buffer.writeVarInt(payload.entityId());
                    buffer.writeBoolean(payload.active());
                    writeDoubleVector(buffer, payload.surfacePoint());
                    writeFloatVector(buffer, payload.surfaceNormal());
                    writeFloatVector(buffer, payload.surfaceAxisX());
                    writeFloatVector(buffer, payload.surfaceAxisZ());
                    buffer.writeLong(payload.visualSource());
                    buffer.writeFloat(payload.radius());
                    buffer.writeFloat(payload.height());
                    buffer.writeFloat(payload.renderDistance());
                    buffer.writeVarInt(payload.emergeTicks());
                    buffer.writeVarInt(payload.retractTicks());
                    buffer.writeVarInt(payload.closeTicks());
                    buffer.writeVarInt(payload.openTicks());
                    buffer.writeVarInt(payload.remainingTicks());
                    buffer.writeVarInt(payload.maximumTicks());
                }
            };

    public SculkClampStatePayload {
        surfacePoint = surfacePoint == null ? Vec3.ZERO : surfacePoint;
        surfaceNormal = normalized(surfaceNormal, new Vec3(0.0D, 1.0D, 0.0D));
        surfaceAxisX = normalized(surfaceAxisX, new Vec3(1.0D, 0.0D, 0.0D));
        surfaceAxisZ = normalized(surfaceAxisZ, new Vec3(0.0D, 0.0D, 1.0D));
        radius = Math.max(0.05F, radius);
        height = Math.max(0.05F, height);
        renderDistance = Math.max(8.0F, renderDistance);
        emergeTicks = Math.max(1, emergeTicks);
        retractTicks = Math.max(1, retractTicks);
        closeTicks = Math.max(1, closeTicks);
        openTicks = Math.max(1, openTicks);
        remainingTicks = Math.max(0, remainingTicks);
        maximumTicks = Math.max(1, maximumTicks);
    }

    private static Vec3 normalized(Vec3 value, Vec3 fallback) {
        return value == null || value.lengthSqr() < 1.0E-8D ? fallback : value.normalize();
    }

    private static Vec3 readDoubleVector(RegistryFriendlyByteBuf buffer) {
        return new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }

    private static Vec3 readFloatVector(RegistryFriendlyByteBuf buffer) {
        return new Vec3(buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
    }

    private static void writeDoubleVector(RegistryFriendlyByteBuf buffer, Vec3 vector) {
        buffer.writeDouble(vector.x);
        buffer.writeDouble(vector.y);
        buffer.writeDouble(vector.z);
    }

    private static void writeFloatVector(RegistryFriendlyByteBuf buffer, Vec3 vector) {
        buffer.writeFloat((float) vector.x);
        buffer.writeFloat((float) vector.y);
        buffer.writeFloat((float) vector.z);
    }

    @Override
    public Type<SculkClampStatePayload> type() {
        return TYPE;
    }
}

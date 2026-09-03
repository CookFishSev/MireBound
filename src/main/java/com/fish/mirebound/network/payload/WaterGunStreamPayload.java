package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Compact visual snapshot of one authoritative pressure-water stream. */
public record WaterGunStreamPayload(
        int shooterId,
        boolean active,
        int targetEntityId,
        float washRadius,
        List<Vec3> points) implements CustomPacketPayload {
    private static final int MAX_POINTS = 48;
    public static final Type<WaterGunStreamPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "water_gun_stream"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WaterGunStreamPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public WaterGunStreamPayload decode(RegistryFriendlyByteBuf buffer) {
                    int shooterId = buffer.readVarInt();
                    boolean active = buffer.readBoolean();
                    if (!active) {
                        return stopped(shooterId);
                    }
                    int targetEntityId = buffer.readVarInt() - 1;
                    float washRadius = buffer.readFloat();
                    int count = buffer.readUnsignedByte();
                    if (count < 2 || count > MAX_POINTS) {
                        throw new IllegalArgumentException("Invalid water stream point count: " + count);
                    }
                    Vec3 origin = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
                    List<Vec3> points = new ArrayList<>(count);
                    points.add(origin);
                    for (int index = 1; index < count; index++) {
                        points.add(origin.add(readFloatVector(buffer)));
                    }
                    return new WaterGunStreamPayload(
                            shooterId, true, targetEntityId, washRadius, points);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, WaterGunStreamPayload payload) {
                    buffer.writeVarInt(payload.shooterId());
                    buffer.writeBoolean(payload.active());
                    if (!payload.active()) {
                        return;
                    }
                    buffer.writeVarInt(payload.targetEntityId() + 1);
                    buffer.writeFloat(payload.washRadius());
                    buffer.writeByte(payload.points().size());
                    Vec3 origin = payload.points().getFirst();
                    buffer.writeDouble(origin.x);
                    buffer.writeDouble(origin.y);
                    buffer.writeDouble(origin.z);
                    for (int index = 1; index < payload.points().size(); index++) {
                        writeFloatVector(buffer, payload.points().get(index).subtract(origin));
                    }
                }
            };

    public WaterGunStreamPayload {
        points = List.copyOf(points);
        washRadius = (float) Mth.clamp(washRadius, 0.0D, 4.0D);
        if (active && (points.size() < 2 || points.size() > MAX_POINTS)) {
            throw new IllegalArgumentException("Active water streams require 2.." + MAX_POINTS + " points");
        }
        if (!active) {
            points = List.of();
            targetEntityId = -1;
            washRadius = 0.0F;
        }
    }

    public static WaterGunStreamPayload stopped(int shooterId) {
        return new WaterGunStreamPayload(shooterId, false, -1, 0.0F, List.of());
    }

    @Override
    public Type<WaterGunStreamPayload> type() {
        return TYPE;
    }

    private static Vec3 readFloatVector(RegistryFriendlyByteBuf buffer) {
        return new Vec3(buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
    }

    private static void writeFloatVector(RegistryFriendlyByteBuf buffer, Vec3 vector) {
        buffer.writeFloat((float) vector.x);
        buffer.writeFloat((float) vector.y);
        buffer.writeFloat((float) vector.z);
    }
}

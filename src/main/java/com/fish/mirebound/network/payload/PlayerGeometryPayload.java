package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.mud.AnimatedPlayerGeometry;
import com.fish.mirebound.mud.MudBodyPart;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Compact post-animation body/cape geometry; all coordinates are relative to origin. */
public record PlayerGeometryPayload(Vec3 origin, List<Part> parts, Cape cape)
        implements CustomPacketPayload {
    public static final Type<PlayerGeometryPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "player_geometry"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerGeometryPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public PlayerGeometryPayload decode(RegistryFriendlyByteBuf buffer) {
                    Vec3 origin = readVec3d(buffer);
                    int count = buffer.readVarInt();
                    if (count < 0 || count > MudBodyPart.COUNT) {
                        throw new IllegalArgumentException("Invalid player geometry part count " + count);
                    }
                    List<Part> parts = new ArrayList<>(count);
                    for (int index = 0; index < count; index++) {
                        parts.add(new Part(buffer.readVarInt(), readVec3f(buffer),
                                readVec3f(buffer), readVec3f(buffer), readVec3f(buffer)));
                    }
                    Cape cape = buffer.readBoolean()
                            ? new Cape(readVec3f(buffer), readVec3f(buffer), readVec3f(buffer),
                                    readVec3f(buffer), buffer.readFloat())
                            : null;
                    return new PlayerGeometryPayload(origin, List.copyOf(parts), cape);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, PlayerGeometryPayload payload) {
                    writeVec3d(buffer, payload.origin);
                    buffer.writeVarInt(payload.parts.size());
                    for (Part part : payload.parts) {
                        buffer.writeVarInt(part.partId);
                        writeVec3f(buffer, part.centerOffset);
                        writeVec3f(buffer, part.halfSide);
                        writeVec3f(buffer, part.halfUp);
                        writeVec3f(buffer, part.halfForward);
                    }
                    buffer.writeBoolean(payload.cape != null);
                    if (payload.cape != null) {
                        writeVec3f(buffer, payload.cape.rootOffset);
                        writeVec3f(buffer, payload.cape.side);
                        writeVec3f(buffer, payload.cape.down);
                        writeVec3f(buffer, payload.cape.normal);
                        buffer.writeFloat(payload.cape.scale);
                    }
                }
            };

    public PlayerGeometryPayload {
        parts = parts == null ? List.of() : List.copyOf(parts);
    }

    public static PlayerGeometryPayload body(Vec3 origin,
            AnimatedPlayerGeometry.PartPose[] poses) {
        List<Part> parts = new ArrayList<>(MudBodyPart.COUNT);
        for (MudBodyPart part : MudBodyPart.values()) {
            AnimatedPlayerGeometry.PartPose pose = poses[part.ordinal()];
            parts.add(new Part(part.ordinal(), pose.center().subtract(origin),
                    pose.halfSide(), pose.halfUp(), pose.halfForward()));
        }
        return new PlayerGeometryPayload(origin, parts, null);
    }

    public static PlayerGeometryPayload cape(Vec3 origin,
            AnimatedPlayerGeometry.CapePose pose) {
        return new PlayerGeometryPayload(origin, List.of(), new Cape(
                pose.root().subtract(origin), pose.side(), pose.down(), pose.normal(), (float) pose.scale()));
    }

    @Override
    public Type<PlayerGeometryPayload> type() {
        return TYPE;
    }

    private static Vec3 readVec3d(RegistryFriendlyByteBuf buffer) {
        return new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }

    private static Vec3 readVec3f(RegistryFriendlyByteBuf buffer) {
        return new Vec3(buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
    }

    private static void writeVec3d(RegistryFriendlyByteBuf buffer, Vec3 value) {
        buffer.writeDouble(value.x);
        buffer.writeDouble(value.y);
        buffer.writeDouble(value.z);
    }

    private static void writeVec3f(RegistryFriendlyByteBuf buffer, Vec3 value) {
        buffer.writeFloat((float) value.x);
        buffer.writeFloat((float) value.y);
        buffer.writeFloat((float) value.z);
    }

    public record Part(int partId, Vec3 centerOffset,
            Vec3 halfSide, Vec3 halfUp, Vec3 halfForward) {
    }

    public record Cape(Vec3 rootOffset, Vec3 side, Vec3 down, Vec3 normal, float scale) {
    }
}

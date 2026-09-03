package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.tentacle.TentacleGrabMode;
import com.fish.mirebound.tentacle.TentacleGrabTarget;
import com.fish.mirebound.tentacle.TentaclePhase;
import com.fish.mirebound.tentacle.TentacleRagdollPose;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public record TentacleStateSyncPayload(
        int instanceId,
        boolean removed,
        TentaclePhase phase,
        int age,
        int syncIntervalTicks,
        long visualSeed,
        double rootX,
        double rootY,
        double rootZ,
        float rootRadius,
        float tipRadius,
        int grabbedEntityId,
        TentacleGrabMode grabMode,
        float grabIntensity,
        TentacleRagdollPose grabPose,
        List<Vec3> points) implements CustomPacketPayload {
    private static final int MAXIMUM_POINTS = 32;
    public static final Type<TentacleStateSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "tentacle_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TentacleStateSyncPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public TentacleStateSyncPayload decode(RegistryFriendlyByteBuf buffer) {
                    int instanceId = buffer.readVarInt();
                    boolean removed = buffer.readBoolean();
                    if (removed) {
                        return removed(instanceId);
                    }
                    int phaseId = buffer.readUnsignedByte();
                    TentaclePhase[] phases = TentaclePhase.values();
                    TentaclePhase phase = phases[Mth.clamp(phaseId, 0, phases.length - 1)];
                    int age = buffer.readVarInt();
                    int syncInterval = Math.max(1, buffer.readUnsignedByte());
                    long seed = buffer.readLong();
                    double rootX = buffer.readDouble();
                    double rootY = buffer.readDouble();
                    double rootZ = buffer.readDouble();
                    float rootRadius = buffer.readFloat();
                    float tipRadius = buffer.readFloat();
                    int grabbedEntityId = buffer.readVarInt();
                    int grabModeId = buffer.readUnsignedByte();
                    TentacleGrabMode[] grabModes = TentacleGrabMode.values();
                    TentacleGrabMode grabMode = grabModes[Mth.clamp(grabModeId, 0, grabModes.length - 1)];
                    float grabIntensity = buffer.readFloat();
                    TentacleRagdollPose grabPose = readPose(buffer);
                    int pointCount = buffer.readUnsignedByte();
                    if (pointCount < 2 || pointCount > MAXIMUM_POINTS) {
                        throw new IllegalArgumentException("Invalid tentacle point count: " + pointCount);
                    }
                    List<Vec3> points = new ArrayList<>(pointCount);
                    for (int index = 0; index < pointCount; index++) {
                        points.add(new Vec3(
                                rootX + buffer.readFloat(),
                                rootY + buffer.readFloat(),
                                rootZ + buffer.readFloat()));
                    }
                    return new TentacleStateSyncPayload(instanceId, false, phase, age, syncInterval, seed,
                            rootX, rootY, rootZ, rootRadius, tipRadius,
                            grabbedEntityId, grabMode, grabIntensity, grabPose, points);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, TentacleStateSyncPayload payload) {
                    buffer.writeVarInt(payload.instanceId());
                    buffer.writeBoolean(payload.removed());
                    if (payload.removed()) {
                        return;
                    }
                    buffer.writeByte(payload.phase().ordinal());
                    buffer.writeVarInt(payload.age());
                    buffer.writeByte(payload.syncIntervalTicks());
                    buffer.writeLong(payload.visualSeed());
                    buffer.writeDouble(payload.rootX());
                    buffer.writeDouble(payload.rootY());
                    buffer.writeDouble(payload.rootZ());
                    buffer.writeFloat(payload.rootRadius());
                    buffer.writeFloat(payload.tipRadius());
                    buffer.writeVarInt(payload.grabbedEntityId());
                    buffer.writeByte(payload.grabMode().ordinal());
                    buffer.writeFloat(payload.grabIntensity());
                    writePose(buffer, payload.grabPose());
                    buffer.writeByte(payload.points().size());
                    for (Vec3 point : payload.points()) {
                        buffer.writeFloat((float) (point.x - payload.rootX()));
                        buffer.writeFloat((float) (point.y - payload.rootY()));
                        buffer.writeFloat((float) (point.z - payload.rootZ()));
                    }
                }
            };

    public TentacleStateSyncPayload {
        points = List.copyOf(points);
        grabMode = grabMode == null ? TentacleGrabMode.THRASH : grabMode;
        grabPose = grabPose == null ? TentacleRagdollPose.IDENTITY : grabPose;
    }

    public static TentacleStateSyncPayload removed(int instanceId) {
        return new TentacleStateSyncPayload(instanceId, true, TentaclePhase.RETRACTING,
                0, 1, 0L, 0.0D, 0.0D, 0.0D, 0.0F, 0.0F,
                -1, TentacleGrabMode.THRASH, 0.0F, TentacleRagdollPose.IDENTITY, List.of());
    }

    private static TentacleRagdollPose readPose(RegistryFriendlyByteBuf buffer) {
        org.joml.Quaterniond body = new org.joml.Quaterniond(
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
        org.joml.Quaterniond head = new org.joml.Quaterniond(
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
        org.joml.Quaterniond reference = new org.joml.Quaterniond(
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
        Vec3 headOffset = readVector(buffer);
        TentacleGrabTarget[] targets = TentacleGrabTarget.values();
        TentacleGrabTarget target = targets[Mth.clamp(buffer.readUnsignedByte(), 0, targets.length - 1)];
        return new TentacleRagdollPose(body, head, reference,
                headOffset, target, readVector(buffer),
                readVector(buffer), readVector(buffer),
                readVector(buffer), readVector(buffer));
    }

    private static void writePose(RegistryFriendlyByteBuf buffer, TentacleRagdollPose pose) {
        buffer.writeFloat((float) pose.bodyOrientation().x);
        buffer.writeFloat((float) pose.bodyOrientation().y);
        buffer.writeFloat((float) pose.bodyOrientation().z);
        buffer.writeFloat((float) pose.bodyOrientation().w);
        buffer.writeFloat((float) pose.headOrientation().x);
        buffer.writeFloat((float) pose.headOrientation().y);
        buffer.writeFloat((float) pose.headOrientation().z);
        buffer.writeFloat((float) pose.headOrientation().w);
        buffer.writeFloat((float) pose.referenceOrientation().x);
        buffer.writeFloat((float) pose.referenceOrientation().y);
        buffer.writeFloat((float) pose.referenceOrientation().z);
        buffer.writeFloat((float) pose.referenceOrientation().w);
        writeVector(buffer, pose.headOffset());
        buffer.writeByte(pose.grabTarget().ordinal());
        writeVector(buffer, pose.gripOffset());
        writeVector(buffer, pose.leftArmDirection());
        writeVector(buffer, pose.rightArmDirection());
        writeVector(buffer, pose.leftLegDirection());
        writeVector(buffer, pose.rightLegDirection());
    }

    private static Vec3 readVector(RegistryFriendlyByteBuf buffer) {
        return new Vec3(buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
    }

    private static void writeVector(RegistryFriendlyByteBuf buffer, Vec3 vector) {
        buffer.writeFloat((float) vector.x);
        buffer.writeFloat((float) vector.y);
        buffer.writeFloat((float) vector.z);
    }

    public Vec3 root() {
        return new Vec3(rootX, rootY, rootZ);
    }

    @Override
    public Type<TentacleStateSyncPayload> type() {
        return TYPE;
    }
}

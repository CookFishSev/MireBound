package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.mud.SinkingMedium;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** One bounded, time-staggered batch of bubbles around a probed surface. */
public record MudProbeBubblePayload(
        List<BubbleSpawn> bubbles,
        Vec3 normal,
        Vec3 tangent,
        SinkingMedium medium,
        BlockPos profilePos) implements CustomPacketPayload {
    private static final int MAX_POINTS = 8;
    public static final Type<MudProbeBubblePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "mud_probe_bubble"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MudProbeBubblePayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public MudProbeBubblePayload decode(RegistryFriendlyByteBuf buffer) {
                    Vec3 normal = new Vec3(
                            buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
                    Vec3 tangent = new Vec3(
                            buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
                    SinkingMedium medium = SinkingMedium.byId(buffer.readUnsignedByte());
                    BlockPos profilePos = buffer.readBlockPos();
                    int count = buffer.readUnsignedByte();
                    if (count < 1 || count > MAX_POINTS) {
                        throw new IllegalArgumentException("Invalid mud probe bubble count: " + count);
                    }
                    List<BubbleSpawn> bubbles = new ArrayList<>(count);
                    for (int index = 0; index < count; index++) {
                        bubbles.add(new BubbleSpawn(
                                buffer.readDouble(),
                                buffer.readDouble(),
                                buffer.readDouble(),
                                buffer.readVarInt()));
                    }
                    return new MudProbeBubblePayload(
                            bubbles, normal, tangent, medium, profilePos);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, MudProbeBubblePayload payload) {
                    buffer.writeDouble(payload.normal().x);
                    buffer.writeDouble(payload.normal().y);
                    buffer.writeDouble(payload.normal().z);
                    buffer.writeDouble(payload.tangent().x);
                    buffer.writeDouble(payload.tangent().y);
                    buffer.writeDouble(payload.tangent().z);
                    buffer.writeByte(payload.medium().id());
                    buffer.writeBlockPos(payload.profilePos());
                    buffer.writeByte(payload.bubbles().size());
                    for (BubbleSpawn bubble : payload.bubbles()) {
                        buffer.writeDouble(bubble.x());
                        buffer.writeDouble(bubble.y());
                        buffer.writeDouble(bubble.z());
                        buffer.writeVarInt(bubble.delayTicks());
                    }
                }
            };

    public MudProbeBubblePayload(
            List<BubbleSpawn> bubbles, Direction normal, SinkingMedium medium) {
        this(bubbles, directionVector(normal),
                normal != null && normal.getAxis() == Direction.Axis.X
                        ? new Vec3(0.0D, 1.0D, 0.0D)
                        : new Vec3(1.0D, 0.0D, 0.0D), medium, BlockPos.ZERO);
    }

    public MudProbeBubblePayload(
            List<BubbleSpawn> bubbles, Direction normal,
            Vec3 tangent, SinkingMedium medium) {
        this(bubbles, directionVector(normal), tangent, medium, BlockPos.ZERO);
    }

    public MudProbeBubblePayload(
            List<BubbleSpawn> bubbles, Direction normal,
            Vec3 tangent, SinkingMedium medium, BlockPos profilePos) {
        this(bubbles, directionVector(normal), tangent, medium, profilePos);
    }

    public MudProbeBubblePayload {
        bubbles = List.copyOf(bubbles);
        profilePos = profilePos == null ? BlockPos.ZERO : profilePos;
        normal = normal == null || !finite(normal)
                ? new Vec3(0.0D, 1.0D, 0.0D) : normal.normalize();
        tangent = tangent == null || !finite(tangent)
                ? new Vec3(1.0D, 0.0D, 0.0D) : tangent.normalize();
        if (bubbles.isEmpty() || bubbles.size() > MAX_POINTS) {
            throw new IllegalArgumentException("Mud probe bubble count must be 1.." + MAX_POINTS);
        }
        for (BubbleSpawn bubble : bubbles) {
            if (bubble.delayTicks() < 0 || bubble.delayTicks() > 200) {
                throw new IllegalArgumentException("Mud probe bubble delay must be 0..200 ticks");
            }
        }
    }

    private static Vec3 directionVector(Direction direction) {
        return direction == null
                ? new Vec3(0.0D, 1.0D, 0.0D)
                : Vec3.atLowerCornerOf(direction.getNormal());
    }

    private static boolean finite(Vec3 vector) {
        return Double.isFinite(vector.x)
                && Double.isFinite(vector.y)
                && Double.isFinite(vector.z)
                && vector.lengthSqr() >= 1.0E-8D;
    }

    @Override
    public Type<MudProbeBubblePayload> type() {
        return TYPE;
    }

    public record BubbleSpawn(double x, double y, double z, int delayTicks) {
        public static BubbleSpawn at(Vec3 point, int delayTicks) {
            return new BubbleSpawn(point.x, point.y, point.z, delayTicks);
        }

        public Vec3 point() {
            return new Vec3(x, y, z);
        }
    }
}

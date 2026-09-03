package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import com.fish.mirebound.rope.RopeFrame;
import com.fish.mirebound.rope.RopeProperties;
import com.fish.mirebound.rope.RopeSegmentOrientation;

/** Compact server snapshot for one non-entity rope chain. */
public record RopeSnapshotPayload(
        int ropeId,
        boolean removed,
        int age,
        int snapshotSequence,
        int interval,
        List<RopeSegmentOrientation> anchoredOrientations,
        List<RopeSegmentOrientation> rescueAnchoredOrientations,
        RopeSegmentOrientation draggedOrientation,
        double originX,
        double originY,
        double originZ,
        List<Vec3> nodes) implements CustomPacketPayload {
    private static final int MAX_NODES = RopeProperties.MAX_SEGMENTS + 1;
    public static final Type<RopeSnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "rope_snapshot"));
    private static final int MAX_ANCHORED_SEGMENTS = RopeProperties.MAX_SEGMENTS;
    public static final StreamCodec<RegistryFriendlyByteBuf, RopeSnapshotPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public RopeSnapshotPayload decode(RegistryFriendlyByteBuf buffer) {
                    int id = buffer.readVarInt();
                    boolean removed = buffer.readBoolean();
                    if (removed) {
                        return removed(id, buffer.readVarInt());
                    }
                    int age = buffer.readVarInt();
                    int snapshotSequence = buffer.readVarInt();
                    int interval = Math.max(1, buffer.readUnsignedByte());
                    int anchorCount = buffer.readUnsignedByte();
                    if (anchorCount > MAX_ANCHORED_SEGMENTS) {
                        throw new IllegalArgumentException("Invalid rope anchor count: "
                                + anchorCount);
                    }
                    List<RopeSegmentOrientation> anchored = new ArrayList<>(anchorCount);
                    for (int index = 0; index < anchorCount; index++) {
                        anchored.add(new RopeSegmentOrientation(buffer.readByte(),
                                readFrame(buffer)));
                    }
                    int rescueAnchorCount = buffer.readUnsignedByte();
                    if (rescueAnchorCount > MAX_ANCHORED_SEGMENTS) {
                        throw new IllegalArgumentException("Invalid rope rescue anchor count: "
                                + rescueAnchorCount);
                    }
                    List<RopeSegmentOrientation> rescueAnchored = new ArrayList<>(
                            rescueAnchorCount);
                    for (int index = 0; index < rescueAnchorCount; index++) {
                        rescueAnchored.add(new RopeSegmentOrientation(buffer.readByte(),
                                readFrame(buffer)));
                    }
                    int draggedSegment = buffer.readByte();
                    RopeSegmentOrientation dragged = draggedSegment < 0 ? null
                            : new RopeSegmentOrientation(draggedSegment, readFrame(buffer));
                    double x = buffer.readDouble();
                    double y = buffer.readDouble();
                    double z = buffer.readDouble();
                    int count = buffer.readUnsignedByte();
                    if (count < 2 || count > MAX_NODES) {
                        throw new IllegalArgumentException("Invalid rope node count: " + count);
                    }
                    List<Vec3> nodes = new ArrayList<>(count);
                    for (int index = 0; index < count; index++) {
                        nodes.add(new Vec3(
                                x + buffer.readFloat(),
                                y + buffer.readFloat(),
                                z + buffer.readFloat()));
                    }
                    return new RopeSnapshotPayload(
                            id, false, age, snapshotSequence, interval,
                            anchored, rescueAnchored, dragged, x, y, z, nodes);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer,
                        RopeSnapshotPayload payload) {
                    buffer.writeVarInt(payload.ropeId());
                    buffer.writeBoolean(payload.removed());
                    if (payload.removed()) {
                        buffer.writeVarInt(payload.snapshotSequence());
                        return;
                    }
                    buffer.writeVarInt(payload.age());
                    buffer.writeVarInt(payload.snapshotSequence());
                    buffer.writeByte(payload.interval());
                    buffer.writeByte(payload.anchoredOrientations().size());
                    for (RopeSegmentOrientation orientation : payload.anchoredOrientations()) {
                        buffer.writeByte(orientation.segment());
                        writeFrame(buffer, orientation.frame());
                    }
                    buffer.writeByte(payload.rescueAnchoredOrientations().size());
                    for (RopeSegmentOrientation orientation : payload.rescueAnchoredOrientations()) {
                        buffer.writeByte(orientation.segment());
                        writeFrame(buffer, orientation.frame());
                    }
                    int draggedSegment = payload.draggedOrientation() == null
                            ? -1 : payload.draggedOrientation().segment();
                    buffer.writeByte(draggedSegment);
                    if (payload.draggedOrientation() != null) {
                        writeFrame(buffer, payload.draggedOrientation().frame());
                    }
                    buffer.writeDouble(payload.originX());
                    buffer.writeDouble(payload.originY());
                    buffer.writeDouble(payload.originZ());
                    buffer.writeByte(payload.nodes().size());
                    for (Vec3 node : payload.nodes()) {
                        buffer.writeFloat((float) (node.x - payload.originX()));
                        buffer.writeFloat((float) (node.y - payload.originY()));
                        buffer.writeFloat((float) (node.z - payload.originZ()));
                    }
                }
            };

    public RopeSnapshotPayload {
        nodes = List.copyOf(nodes);
        anchoredOrientations = List.copyOf(anchoredOrientations);
        rescueAnchoredOrientations = List.copyOf(rescueAnchoredOrientations);
        if (!removed) {
            if (anchoredOrientations.size() > MAX_ANCHORED_SEGMENTS) {
                throw new IllegalArgumentException("Too many anchored rope segments");
            }
            Set<Integer> anchoredSegments = new HashSet<>();
            for (RopeSegmentOrientation orientation : anchoredOrientations) {
                if (!validOrientation(orientation, nodes.size())) {
                    throw new IllegalArgumentException("Invalid anchored rope orientation");
                }
                if (!anchoredSegments.add(orientation.segment())) {
                    throw new IllegalArgumentException("Duplicate anchored rope segment");
                }
            }
            Set<Integer> rescueSegments = new HashSet<>();
            for (RopeSegmentOrientation orientation : rescueAnchoredOrientations) {
                if (!validOrientation(orientation, nodes.size())) {
                    throw new IllegalArgumentException("Invalid rescue anchored rope orientation");
                }
                if (!rescueSegments.add(orientation.segment())) {
                    throw new IllegalArgumentException("Duplicate rescue anchored rope segment");
                }
                if (anchoredSegments.contains(orientation.segment())) {
                    throw new IllegalArgumentException(
                            "Rope segment cannot be both ordinary and rescue anchored");
                }
            }
            if (rescueAnchoredOrientations.size() > MAX_ANCHORED_SEGMENTS) {
                throw new IllegalArgumentException("Too many rescue anchored rope segments");
            }
            if (draggedOrientation != null
                    && !validOrientation(draggedOrientation, nodes.size())) {
                throw new IllegalArgumentException("Invalid dragged rope orientation");
            }
            if (draggedOrientation != null
                    && (anchoredSegments.contains(draggedOrientation.segment())
                            || rescueSegments.contains(draggedOrientation.segment()))) {
                throw new IllegalArgumentException("Rope segment cannot be dragged and anchored");
            }
        }
        if (!removed && (nodes.size() < 2 || nodes.size() > MAX_NODES)) {
            throw new IllegalArgumentException("Active rope requires 2.." + MAX_NODES + " nodes");
        }
    }

    /** Compatibility constructor for snapshots created before sequence ordering was added. */
    public RopeSnapshotPayload(int ropeId, boolean removed, int age, int interval,
            List<RopeSegmentOrientation> anchoredOrientations,
            RopeSegmentOrientation draggedOrientation,
            double originX, double originY, double originZ, List<Vec3> nodes) {
        this(ropeId, removed, age, 0, interval, anchoredOrientations,
                List.of(), draggedOrientation, originX, originY, originZ, nodes);
    }

    public static RopeSnapshotPayload removed(int id) {
        return removed(id, 0);
    }

    public static RopeSnapshotPayload removed(int id, int snapshotSequence) {
        return new RopeSnapshotPayload(
                id, true, 0, snapshotSequence, 1, List.of(), List.of(), null,
                0.0D, 0.0D, 0.0D, List.of());
    }

    private static boolean validOrientation(RopeSegmentOrientation orientation, int nodeCount) {
        return orientation != null && orientation.frame() != null
                && orientation.segment() >= 0
                && orientation.segment() < nodeCount - 1;
    }

    private static RopeFrame readFrame(RegistryFriendlyByteBuf buffer) {
        return RopeFrame.from(
                new Vec3(buffer.readFloat(), buffer.readFloat(), buffer.readFloat()),
                new Vec3(buffer.readFloat(), buffer.readFloat(), buffer.readFloat()),
                new Vec3(buffer.readFloat(), buffer.readFloat(), buffer.readFloat()));
    }

    private static void writeFrame(RegistryFriendlyByteBuf buffer, RopeFrame frame) {
        RopeFrame used = frame == null ? RopeFrame.IDENTITY : frame;
        buffer.writeFloat((float) used.x().x);
        buffer.writeFloat((float) used.x().y);
        buffer.writeFloat((float) used.x().z);
        buffer.writeFloat((float) used.y().x);
        buffer.writeFloat((float) used.y().y);
        buffer.writeFloat((float) used.y().z);
        buffer.writeFloat((float) used.z().x);
        buffer.writeFloat((float) used.z().y);
        buffer.writeFloat((float) used.z().z);
    }

    @Override
    public Type<RopeSnapshotPayload> type() {
        return TYPE;
    }
}

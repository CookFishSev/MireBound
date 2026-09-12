package com.fish.mirebound.network.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.connection.ConnectionType;
import com.fish.mirebound.rope.RopeFrame;
import com.fish.mirebound.rope.RopeSegmentOrientation;
import org.junit.jupiter.api.Test;

class RopeSnapshotPayloadTest {
    @Test
    void codecPreservesSelfAndRescueEndpointLinks() {
        RopeSnapshotPayload expected = new RopeSnapshotPayload(7, false, 0, 4, 1,
                List.of(), List.of(), null, 0, 0, 0,
                List.of(Vec3.ZERO, new Vec3(1, 0, 0)),
                new com.fish.mirebound.rope.RopeEndpoint(7, false),
                new com.fish.mirebound.rope.RopeEndpoint(9, true));
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.OTHER);
        try {
            RopeSnapshotPayload.STREAM_CODEC.encode(buffer, expected);
            assertEquals(expected, RopeSnapshotPayload.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void codecPreservesHeldAndAnchoredState() {
        List<Vec3> nodes = new ArrayList<>();
        for (int index = 0; index < 21; index++) {
            nodes.add(new Vec3(index, 4.0D, 0.0D));
        }
        RopeFrame frame = RopeFrame.fromTangent(new Vec3(0.0D, 1.0D, 0.0D));
        RopeSnapshotPayload expected = new RopeSnapshotPayload(
                7, false, 18, 1,
                List.of(new RopeSegmentOrientation(5, frame)),
                new RopeSegmentOrientation(8, frame),
                10.0D, 4.0D, 0.0D, nodes);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.OTHER);

        RopeSnapshotPayload.STREAM_CODEC.encode(buffer, expected);
        assertEquals(expected, RopeSnapshotPayload.STREAM_CODEC.decode(buffer));
        assertEquals(0, buffer.readableBytes());
        buffer.release();
    }

    @Test
    void removedPacketHasNoActiveNodeValidationRequirements() {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.OTHER);

        RopeSnapshotPayload.STREAM_CODEC.encode(buffer, RopeSnapshotPayload.removed(9));
        assertEquals(RopeSnapshotPayload.removed(9),
                RopeSnapshotPayload.STREAM_CODEC.decode(buffer));
        assertEquals(0, buffer.readableBytes());
        buffer.release();
    }
}

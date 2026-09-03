package com.fish.mirebound.network.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fish.mirebound.tentacle.TentacleGrabMode;
import com.fish.mirebound.tentacle.TentacleGrabTarget;
import com.fish.mirebound.tentacle.TentaclePhase;
import com.fish.mirebound.tentacle.TentacleRagdollPose;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

class TentacleStateSyncPayloadTest {
    @Test
    void ragdollPoseCodecKeepsEveryFieldAligned() {
        TentacleRagdollPose pose = new TentacleRagdollPose(
                new Quaterniond(0.11D, -0.22D, 0.33D, 0.88D),
                new Quaterniond(-0.15D, 0.25D, -0.35D, 0.82D),
                new Quaterniond(0.19D, 0.29D, -0.09D, 0.91D),
                new Vec3(0.12345D, 0.67891D, -0.24680D),
                TentacleGrabTarget.RIGHT_FOOT,
                new Vec3(-0.31D, 0.47D, 0.59D),
                new Vec3(0.61D, -0.71D, 0.81D),
                new Vec3(-0.62D, -0.72D, -0.82D),
                new Vec3(0.63D, -0.73D, 0.83D),
                new Vec3(-0.64D, -0.74D, -0.84D));
        TentacleStateSyncPayload expected = new TentacleStateSyncPayload(
                17, false, TentaclePhase.IDLE, 123, 2, 0x123456789ABCDEFL,
                10.25D, 64.5D, -8.75D, 0.30F, 0.085F,
                42, TentacleGrabMode.WRAP, 0.875F, pose,
                List.of(new Vec3(10.25D, 64.5D, -8.75D),
                        new Vec3(11.75D, 65.25D, -7.50D)));
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.OTHER);

        TentacleStateSyncPayload.STREAM_CODEC.encode(buffer, expected);
        TentacleStateSyncPayload actual =
                TentacleStateSyncPayload.STREAM_CODEC.decode(buffer);

        assertEquals(expected.instanceId(), actual.instanceId());
        assertEquals(expected.grabMode(), actual.grabMode());
        assertEquals(expected.grabIntensity(), actual.grabIntensity());
        assertEquals(pose.grabTarget(), actual.grabPose().grabTarget());
        assertVectorEquals(pose.headOffset(), actual.grabPose().headOffset());
        assertVectorEquals(pose.gripOffset(), actual.grabPose().gripOffset());
        assertVectorEquals(pose.leftArmDirection(), actual.grabPose().leftArmDirection());
        assertVectorEquals(pose.rightArmDirection(), actual.grabPose().rightArmDirection());
        assertVectorEquals(pose.leftLegDirection(), actual.grabPose().leftLegDirection());
        assertVectorEquals(pose.rightLegDirection(), actual.grabPose().rightLegDirection());
        assertQuaternionEquals(pose.bodyOrientation(), actual.grabPose().bodyOrientation());
        assertQuaternionEquals(pose.headOrientation(), actual.grabPose().headOrientation());
        assertQuaternionEquals(pose.referenceOrientation(),
                actual.grabPose().referenceOrientation());
        assertEquals(expected.points().size(), actual.points().size());
        for (int index = 0; index < expected.points().size(); index++) {
            assertVectorEquals(expected.points().get(index), actual.points().get(index));
        }
        assertEquals(0, buffer.readableBytes(), "codec left unread pose bytes");
        buffer.release();
    }

    private static void assertVectorEquals(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, 1.0E-5D);
        assertEquals(expected.y, actual.y, 1.0E-5D);
        assertEquals(expected.z, actual.z, 1.0E-5D);
    }

    private static void assertQuaternionEquals(Quaterniond expected, Quaterniond actual) {
        assertEquals(expected.x, actual.x, 1.0E-5D);
        assertEquals(expected.y, actual.y, 1.0E-5D);
        assertEquals(expected.z, actual.z, 1.0E-5D);
        assertEquals(expected.w, actual.w, 1.0E-5D);
    }
}

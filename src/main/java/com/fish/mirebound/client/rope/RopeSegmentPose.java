package com.fish.mirebound.client.rope;

import com.fish.mirebound.rope.RopeFrame;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/** World-stable per-segment frames for rendering and section picking. */
final class RopeSegmentPose {
    private static final double EPSILON = 1.0E-10D;

    private RopeSegmentPose() {
    }

    /** Builds the same independent Householder frame used by the reference renderer. */
    static Frame initial(Vec3 tangent) {
        Vec3 normalizedTangent = tangent == null || tangent.lengthSqr() <= EPSILON
                ? new Vec3(0.0D, 1.0D, 0.0D) : tangent.normalize();
        double tx = normalizedTangent.x;
        double ty = normalizedTangent.y;
        double tz = normalizedTangent.z;
        Vec3 side;
        Vec3 up;
        if (tz < -0.9999D) {
            side = new Vec3(0.0D, -1.0D, 0.0D);
            up = new Vec3(-1.0D, 0.0D, 0.0D);
        } else {
            double inverse = 1.0D / (1.0D + tz);
            double cross = -tx * ty * inverse;
            side = new Vec3(1.0D - tx * tx * inverse, cross, -tx);
            up = new Vec3(cross, 1.0D - ty * ty * inverse, -ty);
        }
        RopeFrame frame = RopeFrame.from(side, normalizedTangent, up);
        return frame == null
                ? new Frame(new Vec3(1.0D, 0.0D, 0.0D), normalizedTangent,
                        new Vec3(0.0D, 0.0D, 1.0D))
                : fromRopeFrame(frame);
    }

    static Frame fromRopeFrame(RopeFrame frame) {
        return frame == null ? null : new Frame(frame.x(), frame.y(), frame.z());
    }

    static RopeFrame toRopeFrame(Frame frame) {
        return frame == null ? null : RopeFrame.from(frame.x(), frame.y(), frame.z());
    }

    static Frame[] frames(List<Vec3> nodes) {
        if (nodes == null || nodes.size() < 2) {
            return new Frame[0];
        }
        Frame[] result = new Frame[nodes.size() - 1];
        for (int segment = 0; segment < result.length; segment++) {
            result[segment] = initial(nodes.get(segment + 1).subtract(nodes.get(segment)));
        }
        return result;
    }

    static List<Vec3> withRigidSegment(
            List<Vec3> nodes, int segment, RopeFrame frame, double length) {
        if (nodes == null || frame == null || segment < 0
                || segment + 1 >= nodes.size() || !Double.isFinite(length)
                || length <= 0.0D) {
            return nodes;
        }
        Vec3 center = nodes.get(segment).lerp(nodes.get(segment + 1), 0.5D);
        Vec3 halfAxis = frame.y().normalize().scale(length * 0.5D);
        ArrayList<Vec3> adjusted = new ArrayList<>(nodes);
        adjusted.set(segment, center.subtract(halfAxis));
        adjusted.set(segment + 1, center.add(halfAxis));
        return List.copyOf(adjusted);
    }

    record Frame(Vec3 x, Vec3 y, Vec3 z) {
    }
}

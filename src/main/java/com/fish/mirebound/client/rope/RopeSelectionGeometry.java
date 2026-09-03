package com.fish.mirebound.client.rope;

import java.util.List;
import net.minecraft.world.phys.Vec3;

/** Fixed oriented box geometry for one selectable rope segment. */
final class RopeSelectionGeometry {
    private static final int[][] FACES = {
            {0, 2, 3, 1}, {4, 5, 7, 6},
            {0, 4, 6, 2}, {1, 3, 7, 5},
            {0, 1, 5, 4}, {2, 6, 7, 3}
    };
    private static final int[][] EDGES = {
            {0, 1}, {0, 2}, {0, 4}, {1, 3}, {1, 5}, {2, 3},
            {2, 6}, {3, 7}, {4, 5}, {4, 6}, {5, 7}, {6, 7}
    };

    private RopeSelectionGeometry() {
    }

    static Box of(Vec3 start, Vec3 end, double halfWidth, double halfLength) {
        Vec3 delta = end.subtract(start);
        Vec3 tangent = delta.lengthSqr() <= 1.0E-10D
                ? new Vec3(0.0D, 1.0D, 0.0D) : delta.normalize();
        return of(start, end, RopeSegmentPose.initial(tangent), halfWidth, halfLength);
    }

    static Box of(Vec3 start, Vec3 end, RopeSegmentPose.Frame frame,
            double halfWidth, double halfLength) {
        Vec3 delta = end.subtract(start);
        return new Box(start.lerp(end, 0.5D), frame,
                Math.max(0.0D, halfWidth), Math.max(0.0D, halfLength));
    }

    static double rayHitDistance(Vec3 origin, Vec3 direction, Box box,
            double padding, double maximumDistance) {
        Vec3 relative = origin.subtract(box.center());
        double[] originLocal = {
                relative.dot(box.frame().x()), relative.dot(box.frame().y()),
                relative.dot(box.frame().z())
        };
        double[] directionLocal = {
                direction.dot(box.frame().x()), direction.dot(box.frame().y()),
                direction.dot(box.frame().z())
        };
        double[] halfSize = {
                box.halfWidth() + padding, box.halfLength() + padding,
                box.halfWidth() + padding
        };
        double near = 0.0D;
        double far = maximumDistance;
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(directionLocal[axis]) <= 1.0E-10D) {
                if (Math.abs(originLocal[axis]) > halfSize[axis]) {
                    return Double.POSITIVE_INFINITY;
                }
                continue;
            }
            double axisNear = (-halfSize[axis] - originLocal[axis])
                    / directionLocal[axis];
            double axisFar = (halfSize[axis] - originLocal[axis])
                    / directionLocal[axis];
            if (axisNear > axisFar) {
                double swap = axisNear;
                axisNear = axisFar;
                axisFar = swap;
            }
            near = Math.max(near, axisNear);
            far = Math.min(far, axisFar);
            if (near > far) {
                return Double.POSITIVE_INFINITY;
            }
        }
        return near <= maximumDistance && far >= 0.0D
                ? Math.max(0.0D, near) : Double.POSITIVE_INFINITY;
    }

    static int[][] faces() {
        return FACES;
    }

    static int[][] edges() {
        return EDGES;
    }

    record Box(Vec3 center, RopeSegmentPose.Frame frame,
            double halfWidth, double halfLength) {
        List<Vec3> corners() {
            return List.of(
                    point(-1.0D, -1.0D, -1.0D), point(-1.0D, -1.0D, 1.0D),
                    point(-1.0D, 1.0D, -1.0D), point(-1.0D, 1.0D, 1.0D),
                    point(1.0D, -1.0D, -1.0D), point(1.0D, -1.0D, 1.0D),
                    point(1.0D, 1.0D, -1.0D), point(1.0D, 1.0D, 1.0D));
        }

        private Vec3 point(double xSign, double ySign, double zSign) {
            return center.add(frame.x().scale(xSign * halfWidth))
                    .add(frame.y().scale(ySign * halfLength))
                    .add(frame.z().scale(zSign * halfWidth));
        }
    }
}

package com.fish.mirebound.mud;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Shared canonical player geometry used by pollution contact and mud-surface
 * displacement. The geometry deliberately follows the same 32-pixel player
 * proportions as the authoritative coverage sampler.
 */
public final class MudEntityGeometry {
    public static final double PLAYER_MODEL_HEIGHT_PIXELS = 32.0D;
    private static final double SURFACE_PIXEL_OUTSET = 0.006D;
    private static final double PLANE_EPSILON = 1.0E-6D;
    private static final int[][] BOX_EDGES = {
            {0, 1}, {0, 2}, {0, 4},
            {1, 3}, {1, 5},
            {2, 3}, {2, 6},
            {3, 7},
            {4, 5}, {4, 6},
            {5, 7},
            {6, 7}
    };

    private MudEntityGeometry() {
    }

    /** Snapshot of the exact canonical body and outer armor volumes used by contact code. */
    public static List<DebugBox> debugBoxes(Player player) {
        List<DebugBox> boxes = new ArrayList<>(MudBodyPart.values().length * 2);
        for (MudBodyPart part : MudBodyPart.values()) {
            OrientedBox skin = bodyBox(player, part, 0.0D);
            boxes.add(new DebugBox(part, false, List.of(skin.corners())));
            double armorOffset = outerArmorOffset(player, part);
            if (armorOffset > 0.0D) {
                OrientedBox armor = bodyBox(player, part, armorOffset);
                boxes.add(new DebugBox(part, true, List.of(armor.corners())));
            }
        }
        return List.copyOf(boxes);
    }

    public static SamplingBasis bodyBasis(Player player) {
        return orientedBasis(
                player.position(),
                player.getBbHeight() / PLAYER_MODEL_HEIGHT_PIXELS,
                player.yBodyRot,
                0.0F,
                0.0D);
    }

    public static SamplingBasis headBasis(Player player) {
        return orientedBasis(
                player.position(),
                player.getBbHeight() / PLAYER_MODEL_HEIGHT_PIXELS,
                player.yHeadRot,
                player.getXRot(),
                modelBottomPixels(MudBodyPart.HEAD));
    }

    public static SamplingBasis orientedBasis(Vec3 feet, double scale,
            float yawDegrees, float pitchDegrees, double pivotPixels) {
        float yaw = yawDegrees * ((float) Math.PI / 180.0F);
        float pitch = pitchDegrees * ((float) Math.PI / 180.0F);
        double sinYaw = Mth.sin(yaw);
        double cosYaw = Mth.cos(yaw);
        double sinPitch = Mth.sin(pitch);
        double cosPitch = Mth.cos(pitch);
        Vec3 side = new Vec3(cosYaw, 0.0D, sinYaw);
        Vec3 levelForward = new Vec3(-sinYaw, 0.0D, cosYaw);
        Vec3 forward = levelForward.scale(cosPitch).add(0.0D, -sinPitch, 0.0D);
        Vec3 up = new Vec3(0.0D, cosPitch, 0.0D).add(levelForward.scale(sinPitch));
        return new SamplingBasis(
                feet.add(0.0D, pivotPixels * scale, 0.0D),
                side,
                up,
                forward,
                scale,
                pivotPixels);
    }

    public static Vec3 surfacePixelPoint(SamplingBasis basis, MudBodyPart part,
            MudSurface surface, int row, int column) {
        MudSurfaceLayout.Face face = MudSurfaceLayout.face(part, surface);
        double scale = basis.scale();
        double width = modelWidthPixels(part) * scale;
        double depth = modelDepthPixels(part) * scale;
        double height = MudSurfaceLayout.modelHeight(part) * scale;
        double side = modelCenterSidePixels(part) * scale;
        double forward;
        double y = modelBottomPixels(part) * scale;

        if (face.vertical()) {
            y += (row + 0.5D) * height / face.height();
            if (surface == MudSurface.FRONT || surface == MudSurface.BACK) {
                side += -width * 0.5D + (column + 0.5D) * width / face.width();
                forward = surface == MudSurface.FRONT
                        ? depth * 0.5D + SURFACE_PIXEL_OUTSET
                        : -depth * 0.5D - SURFACE_PIXEL_OUTSET;
            } else {
                side += surface == MudSurface.LEFT
                        ? width * 0.5D + SURFACE_PIXEL_OUTSET
                        : -width * 0.5D - SURFACE_PIXEL_OUTSET;
                forward = -depth * 0.5D + (column + 0.5D) * depth / face.width();
            }
        } else {
            side += -width * 0.5D + (column + 0.5D) * width / face.width();
            forward = -depth * 0.5D + (row + 0.5D) * depth / face.height();
            y += surface == MudSurface.TOP ? height + SURFACE_PIXEL_OUTSET : -SURFACE_PIXEL_OUTSET;
        }

        double relativeY = y - basis.pivotPixels() * scale;
        return basis.pivot()
                .add(basis.side().scale(side))
                .add(basis.up().scale(relativeY))
                .add(basis.forward().scale(forward));
    }

    static Vec3 nearestSurfacePoint(
            SamplingBasis basis, MudBodyPart part, Vec3 point) {
        return bodyBox(basis, part, 0.0D).nearestSurfacePoint(point);
    }

    /** Uses the latest post-animation part transform, with the canonical model as fallback. */
    public static Vec3 surfacePixelPoint(Player player, MudBodyPart part,
            MudSurface surface, int row, int column) {
        return surfacePixelPoint(bodyBox(player, part, 0.0D), part, surface, row, column);
    }

    public static Vec3 surfacePixelPoint(Player player, MudBodyPart part,
            MudSurface surface, int row, int column, double shellOffset) {
        return surfacePixelPoint(bodyBox(player, part, shellOffset), part, surface, row, column);
    }

    /** Captures one animated part pose for repeated pixel sampling in a single client tick. */
    public static SurfacePixelSampler surfacePixelSampler(Player player, MudBodyPart part) {
        return new SurfacePixelSampler(bodyBox(player, part, 0.0D));
    }

    /** Captures every canonical body-part pose once for one exact-pixel pass. */
    public static SurfacePixelSampler[] surfacePixelSamplers(Player player) {
        SurfacePixelSampler[] result = new SurfacePixelSampler[MudBodyPart.COUNT];
        for (MudBodyPart part : MudBodyPart.values()) {
            result[part.ordinal()] = surfacePixelSampler(player, part);
        }
        return result;
    }

    public static Vec3 surfacePixelOutwardNormal(SamplingBasis basis, MudSurface surface) {
        return switch (surface) {
            case FRONT -> basis.forward();
            case BACK -> basis.forward().scale(-1.0D);
            case LEFT -> basis.side();
            case RIGHT -> basis.side().scale(-1.0D);
            case TOP -> basis.up();
            case BOTTOM -> basis.up().scale(-1.0D);
        };
    }

    public static Vec3 surfacePixelOutwardNormal(Player player,
            MudBodyPart part, MudSurface surface) {
        OrientedBox box = bodyBox(player, part, 0.0D);
        return switch (surface) {
            case FRONT -> box.forward;
            case BACK -> box.forward.scale(-1.0D);
            case LEFT -> box.side;
            case RIGHT -> box.side.scale(-1.0D);
            case TOP -> box.up;
            case BOTTOM -> box.up.scale(-1.0D);
        };
    }

    public static MudCapeGeometry.CapeBasis capeBasis(Player player) {
        AnimatedPlayerGeometry.CapePose animated = AnimatedPlayerGeometry.cape(player);
        return animated == null ? MudCapeGeometry.basis(player) : animated.basis();
    }

    public static Vec3 capeProbe(MudCapeGeometry.CapeBasis basis,
            MudCapeLayout.Side side, int row, int column) {
        return side == MudCapeLayout.Side.OUTER
                ? MudCapeGeometry.frontProbe(basis, row, column)
                : MudCapeGeometry.backProbe(basis, row, column);
    }

    /**
     * Intersects the canonical player and the outermost equipped armor shells
     * with a horizontal mud plane.
     */
    public static PlaneSlice horizontalSlice(Player player, double surfaceY) {
        Map<MudBodyPart, SlicePolygon> polygons = new EnumMap<>(MudBodyPart.class);
        for (MudBodyPart part : MudBodyPart.values()) {
            double armorOffset = outerArmorOffset(player, part, surfaceY);
            OrientedBox box = bodyBox(player, part, armorOffset);
            List<Vec3> vertices = intersectHorizontalPlane(box, surfaceY);
            if (vertices.size() >= 3) {
                polygons.put(part, new SlicePolygon(part, List.copyOf(vertices), armorOffset));
            }
        }
        return new PlaneSlice(surfaceY, List.copyOf(polygons.values()));
    }

    /**
     * Intersects the canonical player and equipped armor shells with an
     * arbitrary world-space plane. The returned vertices are ordered in the
     * supplied plane basis, so callers can rasterize vertical mud faces without
     * depending on the camera.
     */
    public static OrientedPlaneSlice planeSlice(Player player, Vec3 planePoint,
            Vec3 planeNormal, Vec3 planeAxisU, Vec3 planeAxisV) {
        Vec3 normal = planeNormal.normalize();
        Vec3 axisU = planeAxisU.subtract(normal.scale(planeAxisU.dot(normal))).normalize();
        Vec3 axisV = planeAxisV.subtract(normal.scale(planeAxisV.dot(normal)))
                .subtract(axisU.scale(planeAxisV.dot(axisU))).normalize();
        Map<MudBodyPart, SlicePolygon> polygons = new EnumMap<>(MudBodyPart.class);
        for (MudBodyPart part : MudBodyPart.values()) {
            double armorOffset = outerArmorOffset(player, part);
            OrientedBox box = bodyBox(player, part, armorOffset);
            List<Vec3> vertices = intersectPlane(
                    box, planePoint, normal, axisU, axisV);
            if (vertices.size() >= 3) {
                polygons.put(part, new SlicePolygon(
                        part, List.copyOf(vertices), armorOffset));
            }
        }
        return new OrientedPlaneSlice(
                planePoint, normal, axisU, axisV, List.copyOf(polygons.values()));
    }

    private static OrientedBox bodyBox(SamplingBasis basis, MudBodyPart part,
            double armorOffset) {
        double scale = basis.scale();
        double width = modelWidthPixels(part) * scale;
        double depth = modelDepthPixels(part) * scale;
        double height = MudSurfaceLayout.modelHeight(part) * scale;
        double side = modelCenterSidePixels(part) * scale;
        double centerPixels = modelBottomPixels(part)
                + MudSurfaceLayout.modelHeight(part) * 0.5D;
        double relativeY = (centerPixels - basis.pivotPixels()) * scale;
        Vec3 center = basis.pivot()
                .add(basis.side().scale(side))
                .add(basis.up().scale(relativeY));
        return new OrientedBox(
                center,
                basis.side(),
                basis.up(),
                basis.forward(),
                width * 0.5D + armorOffset,
                height * 0.5D + armorOffset,
                depth * 0.5D + armorOffset);
    }

    private static OrientedBox bodyBox(Player player, MudBodyPart part,
            double armorOffset) {
        AnimatedPlayerGeometry.PartPose animated = AnimatedPlayerGeometry.part(player, part);
        if (animated != null) {
            return new OrientedBox(
                    animated.center(),
                    animated.side(),
                    animated.up(),
                    animated.forward(),
                    animated.halfWidth() + armorOffset,
                    animated.halfHeight() + armorOffset,
                    animated.halfDepth() + armorOffset);
        }
        SamplingBasis basis = part == MudBodyPart.HEAD ? headBasis(player) : bodyBasis(player);
        return bodyBox(basis, part, armorOffset);
    }

    private static Vec3 surfacePixelPoint(OrientedBox box, MudBodyPart part,
            MudSurface surface, int row, int column) {
        MudSurfaceLayout.Face face = MudSurfaceLayout.face(part, surface);
        double side;
        double up;
        double forward;
        if (face.vertical()) {
            up = -box.halfHeight + (row + 0.5D) * box.halfHeight * 2.0D / face.height();
            if (surface == MudSurface.FRONT || surface == MudSurface.BACK) {
                side = -box.halfWidth + (column + 0.5D) * box.halfWidth * 2.0D / face.width();
                forward = surface == MudSurface.FRONT
                        ? box.halfDepth + SURFACE_PIXEL_OUTSET
                        : -box.halfDepth - SURFACE_PIXEL_OUTSET;
            } else {
                side = surface == MudSurface.LEFT
                        ? box.halfWidth + SURFACE_PIXEL_OUTSET
                        : -box.halfWidth - SURFACE_PIXEL_OUTSET;
                forward = -box.halfDepth
                        + (column + 0.5D) * box.halfDepth * 2.0D / face.width();
            }
        } else {
            side = -box.halfWidth + (column + 0.5D) * box.halfWidth * 2.0D / face.width();
            forward = -box.halfDepth + (row + 0.5D) * box.halfDepth * 2.0D / face.height();
            up = surface == MudSurface.TOP
                    ? box.halfHeight + SURFACE_PIXEL_OUTSET
                    : -box.halfHeight - SURFACE_PIXEL_OUTSET;
        }
        return box.center
                .add(box.side.scale(side))
                .add(box.up.scale(up))
                .add(box.forward.scale(forward));
    }

    private static double outerArmorOffset(Player player, MudBodyPart part,
            double surfaceY, SamplingBasis basis) {
        double localHeight = worldPlaneLocalHeight(surfaceY, basis);
        int modelRow = Mth.floor(localHeight - modelBottomPixels(part));
        if (modelRow < 0 || modelRow >= MudSurfaceLayout.modelHeight(part)) {
            return 0.0D;
        }
        double best = 0.0D;
        for (EquipmentSlot slot : ArmorMudManager.armorSlots()) {
            if (!ArmorMudManager.validArmor(player.getItemBySlot(slot), slot)
                    || !ArmorMudManager.slotOwnsSurface(
                            slot, part, MudSurface.FRONT, modelRow)) {
                continue;
            }
            best = Math.max(best, ArmorMudManager.surfaceOffset(slot));
        }
        return best;
    }

    private static double outerArmorOffset(Player player, MudBodyPart part,
            double surfaceY) {
        AnimatedPlayerGeometry.PartPose animated = AnimatedPlayerGeometry.part(player, part);
        if (animated == null) {
            SamplingBasis basis = part == MudBodyPart.HEAD ? headBasis(player) : bodyBasis(player);
            return outerArmorOffset(player, part, surfaceY, basis);
        }
        Vec3 planePoint = new Vec3(animated.center().x, surfaceY, animated.center().z);
        double localHeight = planePoint.subtract(animated.center()).dot(animated.up());
        double normalized = (localHeight + animated.halfHeight())
                / Math.max(1.0E-6D, animated.halfHeight() * 2.0D);
        int modelRow = Mth.floor(normalized * MudSurfaceLayout.modelHeight(part));
        if (modelRow < 0 || modelRow >= MudSurfaceLayout.modelHeight(part)) {
            return 0.0D;
        }
        double best = 0.0D;
        for (EquipmentSlot slot : ArmorMudManager.armorSlots()) {
            if (ArmorMudManager.validArmor(player.getItemBySlot(slot), slot)
                    && ArmorMudManager.slotOwnsSurface(slot, part, MudSurface.FRONT, modelRow)) {
                best = Math.max(best, ArmorMudManager.surfaceOffset(slot));
            }
        }
        return best;
    }

    private static double outerArmorOffset(Player player, MudBodyPart part) {
        double best = 0.0D;
        for (EquipmentSlot slot : ArmorMudManager.armorSlots()) {
            if (!ArmorMudManager.validArmor(player.getItemBySlot(slot), slot)) {
                continue;
            }
            for (int row = 0; row < MudSurfaceLayout.modelHeight(part); row++) {
                if (ArmorMudManager.slotOwnsSurface(
                        slot, part, MudSurface.FRONT, row)) {
                    best = Math.max(best, ArmorMudManager.surfaceOffset(slot));
                    break;
                }
            }
        }
        return best;
    }

    private static double worldPlaneLocalHeight(double surfaceY, SamplingBasis basis) {
        if (Math.abs(basis.up().y) <= PLANE_EPSILON) {
            return Double.NEGATIVE_INFINITY;
        }
        double alongUp = (surfaceY - basis.pivot().y) / basis.up().y;
        return alongUp / basis.scale() + basis.pivotPixels();
    }

    private static List<Vec3> intersectHorizontalPlane(OrientedBox box, double surfaceY) {
        Vec3[] corners = box.corners();
        List<Vec3> intersections = new ArrayList<>(8);
        for (int[] edge : BOX_EDGES) {
            Vec3 first = corners[edge[0]];
            Vec3 second = corners[edge[1]];
            double firstDistance = first.y - surfaceY;
            double secondDistance = second.y - surfaceY;
            if (Math.abs(firstDistance) <= PLANE_EPSILON) {
                addUnique(intersections, new Vec3(first.x, surfaceY, first.z));
            }
            if (Math.abs(secondDistance) <= PLANE_EPSILON) {
                addUnique(intersections, new Vec3(second.x, surfaceY, second.z));
            }
            if (firstDistance * secondDistance >= -PLANE_EPSILON * PLANE_EPSILON
                    || Math.abs(first.y - second.y) <= PLANE_EPSILON) {
                continue;
            }
            double t = (surfaceY - first.y) / (second.y - first.y);
            if (t >= -PLANE_EPSILON && t <= 1.0D + PLANE_EPSILON) {
                addUnique(intersections, new Vec3(
                        Mth.lerp(t, first.x, second.x),
                        surfaceY,
                        Mth.lerp(t, first.z, second.z)));
            }
        }
        return convexHull(intersections);
    }

    private static List<Vec3> intersectPlane(OrientedBox box, Vec3 planePoint,
            Vec3 normal, Vec3 axisU, Vec3 axisV) {
        Vec3[] corners = box.corners();
        List<Vec3> intersections = new ArrayList<>(8);
        for (int[] edge : BOX_EDGES) {
            Vec3 first = corners[edge[0]];
            Vec3 second = corners[edge[1]];
            double firstDistance = first.subtract(planePoint).dot(normal);
            double secondDistance = second.subtract(planePoint).dot(normal);
            if (Math.abs(firstDistance) <= PLANE_EPSILON) {
                addUniqueOnPlane(intersections, first, planePoint, axisU, axisV);
            }
            if (Math.abs(secondDistance) <= PLANE_EPSILON) {
                addUniqueOnPlane(intersections, second, planePoint, axisU, axisV);
            }
            if (firstDistance * secondDistance >= -PLANE_EPSILON * PLANE_EPSILON
                    || Math.abs(firstDistance - secondDistance) <= PLANE_EPSILON) {
                continue;
            }
            double t = firstDistance / (firstDistance - secondDistance);
            if (t >= -PLANE_EPSILON && t <= 1.0D + PLANE_EPSILON) {
                addUniqueOnPlane(
                        intersections,
                        first.add(second.subtract(first).scale(t)),
                        planePoint,
                        axisU,
                        axisV);
            }
        }
        if (intersections.size() < 3) {
            return List.of();
        }
        double centerU = 0.0D;
        double centerV = 0.0D;
        for (Vec3 point : intersections) {
            Vec3 relative = point.subtract(planePoint);
            centerU += relative.dot(axisU);
            centerV += relative.dot(axisV);
        }
        final double originU = centerU / intersections.size();
        final double originV = centerV / intersections.size();
        intersections.sort(Comparator.comparingDouble(point -> {
            Vec3 relative = point.subtract(planePoint);
            return Math.atan2(
                    relative.dot(axisV) - originV,
                    relative.dot(axisU) - originU);
        }));
        return intersections;
    }

    private static void addUnique(List<Vec3> points, Vec3 candidate) {
        for (Vec3 point : points) {
            double dx = point.x - candidate.x;
            double dz = point.z - candidate.z;
            if (dx * dx + dz * dz <= 1.0E-10D) {
                return;
            }
        }
        points.add(candidate);
    }

    private static void addUniqueOnPlane(List<Vec3> points, Vec3 candidate,
            Vec3 planePoint, Vec3 axisU, Vec3 axisV) {
        Vec3 relative = candidate.subtract(planePoint);
        double candidateU = relative.dot(axisU);
        double candidateV = relative.dot(axisV);
        for (Vec3 point : points) {
            Vec3 existing = point.subtract(planePoint);
            double du = existing.dot(axisU) - candidateU;
            double dv = existing.dot(axisV) - candidateV;
            if (du * du + dv * dv <= 1.0E-10D) {
                return;
            }
        }
        points.add(candidate);
    }

    public static List<Vec3> convexHull(List<Vec3> input) {
        if (input.size() <= 2) {
            return List.copyOf(input);
        }
        List<Vec3> points = new ArrayList<>(input);
        points.sort(Comparator.comparingDouble((Vec3 point) -> point.x)
                .thenComparingDouble(point -> point.z));
        List<Vec3> hull = new ArrayList<>(points.size() * 2);
        for (Vec3 point : points) {
            while (hull.size() >= 2
                    && cross(hull.get(hull.size() - 2), hull.get(hull.size() - 1), point) <= 1.0E-10D) {
                hull.remove(hull.size() - 1);
            }
            hull.add(point);
        }
        int lowerSize = hull.size();
        for (int index = points.size() - 2; index >= 0; index--) {
            Vec3 point = points.get(index);
            while (hull.size() > lowerSize
                    && cross(hull.get(hull.size() - 2), hull.get(hull.size() - 1), point) <= 1.0E-10D) {
                hull.remove(hull.size() - 1);
            }
            hull.add(point);
        }
        hull.remove(hull.size() - 1);
        return hull;
    }

    private static double cross(Vec3 first, Vec3 second, Vec3 third) {
        return (second.x - first.x) * (third.z - first.z)
                - (second.z - first.z) * (third.x - first.x);
    }

    public static boolean containsXZ(List<Vec3> polygon, double x, double z) {
        if (polygon.size() < 3) {
            return false;
        }
        boolean inside = false;
        for (int current = 0, previous = polygon.size() - 1;
                current < polygon.size();
                previous = current++) {
            Vec3 a = polygon.get(current);
            Vec3 b = polygon.get(previous);
            boolean crosses = (a.z > z) != (b.z > z)
                    && x < (b.x - a.x) * (z - a.z)
                            / (b.z - a.z) + a.x;
            if (crosses) {
                inside = !inside;
            }
        }
        return inside;
    }

    public static boolean containsPlane(List<Vec3> polygon, Vec3 planePoint,
            Vec3 axisU, Vec3 axisV, double u, double v) {
        if (polygon.size() < 3) {
            return false;
        }
        boolean inside = false;
        for (int current = 0, previous = polygon.size() - 1;
                current < polygon.size();
                previous = current++) {
            Vec3 currentRelative = polygon.get(current).subtract(planePoint);
            Vec3 previousRelative = polygon.get(previous).subtract(planePoint);
            double currentU = currentRelative.dot(axisU);
            double currentV = currentRelative.dot(axisV);
            double previousU = previousRelative.dot(axisU);
            double previousV = previousRelative.dot(axisV);
            boolean crosses = (currentV > v) != (previousV > v)
                    && u < (previousU - currentU) * (v - currentV)
                            / (previousV - currentV) + currentU;
            if (crosses) {
                inside = !inside;
            }
        }
        return inside;
    }

    private static double modelWidthPixels(MudBodyPart part) {
        return part == MudBodyPart.HEAD || part == MudBodyPart.BODY ? 8.0D : 4.0D;
    }

    private static double modelDepthPixels(MudBodyPart part) {
        return part == MudBodyPart.HEAD ? 8.0D : 4.0D;
    }

    private static double modelCenterSidePixels(MudBodyPart part) {
        return switch (part) {
            case LEFT_LEG -> 2.0D;
            case RIGHT_LEG -> -2.0D;
            case LEFT_ARM -> 6.0D;
            case RIGHT_ARM -> -6.0D;
            case BODY, HEAD -> 0.0D;
        };
    }

    private static double modelBottomPixels(MudBodyPart part) {
        return switch (part) {
            case LEFT_LEG, RIGHT_LEG -> 0.0D;
            case BODY, LEFT_ARM, RIGHT_ARM -> 12.0D;
            case HEAD -> 24.0D;
        };
    }

    public record SamplingBasis(Vec3 pivot, Vec3 side, Vec3 up, Vec3 forward,
            double scale, double pivotPixels) {
    }

    public record PlaneSlice(double surfaceY, List<SlicePolygon> polygons) {
        public boolean empty() {
            return polygons.isEmpty();
        }
    }

    public record OrientedPlaneSlice(Vec3 planePoint, Vec3 normal,
            Vec3 axisU, Vec3 axisV, List<SlicePolygon> polygons) {
        public boolean empty() {
            return polygons.isEmpty();
        }
    }

    public record SlicePolygon(MudBodyPart part, List<Vec3> vertices,
            double armorOffset) {
    }

    public record DebugBox(MudBodyPart part, boolean armor, List<Vec3> corners) {
    }

    public static final class SurfacePixelSampler {
        private final OrientedBox box;

        private SurfacePixelSampler(OrientedBox box) {
            this.box = box;
        }

        public Vec3 point(MudBodyPart part, MudSurface surface, int row, int column) {
            return surfacePixelPoint(box, part, surface, row, column);
        }

        public Vec3 side() {
            return box.side;
        }

        public Vec3 up() {
            return box.up;
        }

        public Vec3 forward() {
            return box.forward;
        }

        public Vec3 outwardNormal(MudSurface surface) {
            return switch (surface) {
                case FRONT -> box.forward;
                case BACK -> box.forward.scale(-1.0D);
                case LEFT -> box.side;
                case RIGHT -> box.side.scale(-1.0D);
                case TOP -> box.up;
                case BOTTOM -> box.up.scale(-1.0D);
            };
        }

        public Vec3 nearestSurfacePoint(Vec3 point) {
            return box.nearestSurfacePoint(point);
        }
    }

    private record OrientedBox(Vec3 center, Vec3 side, Vec3 up, Vec3 forward,
            double halfWidth, double halfHeight, double halfDepth) {
        Vec3 nearestSurfacePoint(Vec3 point) {
            Vec3 relative = point.subtract(center);
            double localSide = relative.dot(side);
            double localUp = relative.dot(up);
            double localForward = relative.dot(forward);
            double nearestSide = Mth.clamp(localSide, -halfWidth, halfWidth);
            double nearestUp = Mth.clamp(localUp, -halfHeight, halfHeight);
            double nearestForward = Mth.clamp(localForward, -halfDepth, halfDepth);
            boolean inside = Math.abs(localSide) <= halfWidth
                    && Math.abs(localUp) <= halfHeight
                    && Math.abs(localForward) <= halfDepth;
            if (inside) {
                double sideGap = halfWidth - Math.abs(localSide);
                double upGap = halfHeight - Math.abs(localUp);
                double forwardGap = halfDepth - Math.abs(localForward);
                if (sideGap <= upGap && sideGap <= forwardGap) {
                    nearestSide = signedExtent(localSide, halfWidth);
                } else if (upGap <= forwardGap) {
                    nearestUp = signedExtent(localUp, halfHeight);
                } else {
                    nearestForward = signedExtent(localForward, halfDepth);
                }
            }
            return center
                    .add(side.scale(nearestSide))
                    .add(up.scale(nearestUp))
                    .add(forward.scale(nearestForward));
        }

        private static double signedExtent(double value, double extent) {
            return Math.copySign(extent, value == 0.0D ? 1.0D : value);
        }

        Vec3[] corners() {
            Vec3[] result = new Vec3[8];
            for (int bits = 0; bits < 8; bits++) {
                result[bits] = center
                        .add(side.scale((bits & 1) == 0 ? -halfWidth : halfWidth))
                        .add(up.scale((bits & 2) == 0 ? -halfHeight : halfHeight))
                        .add(forward.scale((bits & 4) == 0 ? -halfDepth : halfDepth));
            }
            return result;
        }
    }
}

package com.fish.mirebound.client;

import com.fish.mirebound.client.tuning.MudTuningClientState;
import com.fish.mirebound.client.tuning.MudTuningClientSettings;
import com.fish.mirebound.client.tuning.MudTuningWandMode;
import com.fish.mirebound.compat.sable.SableCompat;
import com.fish.mirebound.mud.MudTuningAnchor;
import com.fish.mirebound.mud.tuning.MudTuningHighlightGeometry;
import com.fish.mirebound.network.payload.MudTuningSelectionPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Renders connected voxel-union shells for server-classified tuning positions. */
final class MudTuningConnectedHighlightRenderer {
    private static final double FACE_OFFSET = 0.004D;
    private static final double MIXED_FLOW_PERIOD_TICKS = 240.0D;
    private static long cachedRevision = Long.MIN_VALUE;
    private static List<CachedGroup> cachedGroups = List.of();

    private MudTuningConnectedHighlightRenderer() {
    }

    static void render(Minecraft minecraft, PoseStack.Pose pose,
            MultiBufferSource.BufferSource buffers, Vec3 camera, Vec3 playerPosition,
            Frustum frustum) {
        double maxDistanceSquared = maxDistanceSquared(minecraft);
        MudTuningWandMode mode = MudTuningClientState.mode();
        MudTuningSectionHighlightGpuCache.render(
                MudTuningSectionHighlightCache.sections(), pose, camera,
                playerPosition, maxDistanceSquared, frustum,
                rendersHighlightKind(mode,
                        MudTuningSelectionPayload.HighlightKind.INCOMPATIBLE));
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        VertexConsumer faces = buffers.getBuffer(MudTuningTargetRenderTypes.face());
        refreshTopology();
        for (CachedGroup group : cachedGroups) {
            if (!rendersHighlightKind(mode, group.kind)) {
                continue;
            }
            if (MudTuningAnchor.WORLD_SUB_LEVEL_ID.equals(group.subLevelId)) {
                continue;
            }
            Object subLevel = resolveSubLevel(minecraft, group.subLevelId);
            if (!MudTuningAnchor.WORLD_SUB_LEVEL_ID.equals(group.subLevelId)
                    && subLevel == null) {
                continue;
            }
            AABB worldBounds = worldBounds(group.bounds, subLevel);
            if (worldBounds == null
                    || distanceToSqr(worldBounds, playerPosition) > maxDistanceSquared
                    || frustum != null && !frustum.isVisible(worldBounds)) {
                continue;
            }
            Color color = Color.forKind(group.kind);
            if (!group.faces.isEmpty()) {
                renderFaces(pose, faces, group.faces,
                        subLevel, camera, playerPosition, maxDistanceSquared, color);
            }
            renderEdges(pose, lines, group.edges,
                    subLevel, camera, playerPosition, maxDistanceSquared, color);
        }
    }

    static boolean rendersHighlightKind(MudTuningWandMode mode,
            MudTuningSelectionPayload.HighlightKind kind) {
        return kind != MudTuningSelectionPayload.HighlightKind.INCOMPATIBLE
                || mode == MudTuningWandMode.RANGE;
    }

    static boolean isAnimated(MudTuningSelectionPayload.HighlightKind kind) {
        return kind == MudTuningSelectionPayload.HighlightKind.MODIFIED_NATIVE_FLOW_MIXED;
    }

    private static double maxDistanceSquared(Minecraft minecraft) {
        double distance = Math.max(128.0D,
                (minecraft.options.renderDistance().get() + 1.0D) * 16.0D);
        return distance * distance;
    }

    private static void refreshTopology() {
        long revision = MudTuningClientState.highlightRevision();
        if (cachedRevision == revision) {
            return;
        }
        List<CachedGroup> rebuilt = new ArrayList<>();
        for (MudTuningSelectionPayload.HighlightGroup group
                : MudTuningClientState.highlightGroups()) {
            rebuilt.add(CachedGroup.build(group));
        }
        cachedGroups = List.copyOf(rebuilt);
        cachedRevision = revision;
    }

    private static Object resolveSubLevel(Minecraft minecraft, java.util.UUID subLevelId) {
        return MudTuningAnchor.WORLD_SUB_LEVEL_ID.equals(subLevelId)
                ? null : SableCompat.subLevelById(minecraft.level, subLevelId);
    }

    private static void renderFaces(PoseStack.Pose pose, VertexConsumer faces,
            List<FaceKey> visibleFaces, Object subLevel, Vec3 camera,
            Vec3 playerPosition, double maxDistanceSquared, Color color) {
        for (FaceKey face : visibleFaces) {
            BlockPos pos = face.pos;
            Vec3 center = worldPoint(subLevel, Vec3.atCenterOf(pos));
            if (center == null || playerPosition.distanceToSqr(center) > maxDistanceSquared) {
                continue;
            }
            Vec3[] corners = faceCorners(pos, face.direction, subLevel);
            if (corners != null) {
                quad(pose, faces, corners, camera,
                        color.red, color.green, color.blue, 0.12F);
            }
        }
    }

    private static void renderEdges(PoseStack.Pose pose, VertexConsumer lines,
            List<EdgeRun> visibleEdges, Object subLevel, Vec3 camera,
            Vec3 playerPosition, double maxDistanceSquared, Color color) {
        for (EdgeRun edge : visibleEdges) {
            Vec3 start = worldPoint(subLevel, edge.start());
            Vec3 end = worldPoint(subLevel, edge.end());
            if (start == null || end == null
                    || distanceToSqr(new AABB(start, end), playerPosition)
                            > maxDistanceSquared) {
                continue;
            }
            line(pose, lines, start, end, camera,
                    color.red, color.green, color.blue, 0.98F);
        }
    }

    static boolean edgeVisible(Set<Long> positions, EdgeKey edge) {
        return MudTuningHighlightGeometry.edgeVisible(positions,
                new MudTuningHighlightGeometry.Edge(
                        edge.axis, edge.x, edge.y, edge.z));
    }

    private static Vec3[] faceCorners(BlockPos pos, Direction direction, Object subLevel) {
        double minX = pos.getX() - FACE_OFFSET;
        double minY = pos.getY() - FACE_OFFSET;
        double minZ = pos.getZ() - FACE_OFFSET;
        double maxX = pos.getX() + 1.0D + FACE_OFFSET;
        double maxY = pos.getY() + 1.0D + FACE_OFFSET;
        double maxZ = pos.getZ() + 1.0D + FACE_OFFSET;
        Vec3[] local = switch (direction) {
            case DOWN -> new Vec3[] {new Vec3(minX, minY, minZ), new Vec3(maxX, minY, minZ),
                    new Vec3(maxX, minY, maxZ), new Vec3(minX, minY, maxZ)};
            case UP -> new Vec3[] {new Vec3(minX, maxY, minZ), new Vec3(minX, maxY, maxZ),
                    new Vec3(maxX, maxY, maxZ), new Vec3(maxX, maxY, minZ)};
            case NORTH -> new Vec3[] {new Vec3(minX, minY, minZ), new Vec3(minX, maxY, minZ),
                    new Vec3(maxX, maxY, minZ), new Vec3(maxX, minY, minZ)};
            case SOUTH -> new Vec3[] {new Vec3(minX, minY, maxZ), new Vec3(maxX, minY, maxZ),
                    new Vec3(maxX, maxY, maxZ), new Vec3(minX, maxY, maxZ)};
            case WEST -> new Vec3[] {new Vec3(minX, minY, minZ), new Vec3(minX, minY, maxZ),
                    new Vec3(minX, maxY, maxZ), new Vec3(minX, maxY, minZ)};
            case EAST -> new Vec3[] {new Vec3(maxX, minY, minZ), new Vec3(maxX, maxY, minZ),
                    new Vec3(maxX, maxY, maxZ), new Vec3(maxX, minY, maxZ)};
        };
        Vec3[] world = new Vec3[4];
        for (int index = 0; index < 4; index++) {
            world[index] = worldPoint(subLevel, local[index]);
            if (world[index] == null) {
                return null;
            }
        }
        return world;
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer vertices,
            double x0, double y0, double z0, double x1, double y1, double z1,
            double x2, double y2, double z2, double x3, double y3, double z3,
            double normalX, double normalY, double normalZ,
            float red, float green, float blue, float alpha) {
        vertex(pose, vertices, x0, y0, z0, 0.0F, 0.0F,
                normalX, normalY, normalZ, red, green, blue, alpha);
        vertex(pose, vertices, x1, y1, z1, 0.0F, 1.0F,
                normalX, normalY, normalZ, red, green, blue, alpha);
        vertex(pose, vertices, x2, y2, z2, 1.0F, 1.0F,
                normalX, normalY, normalZ, red, green, blue, alpha);
        vertex(pose, vertices, x3, y3, z3, 1.0F, 0.0F,
                normalX, normalY, normalZ, red, green, blue, alpha);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer vertices,
            Vec3[] world, Vec3 camera,
            float red, float green, float blue, float alpha) {
        Vec3 first = world[0].subtract(camera);
        Vec3 second = world[1].subtract(camera);
        Vec3 third = world[2].subtract(camera);
        Vec3 fourth = world[3].subtract(camera);
        Vec3 normal = second.subtract(first).cross(fourth.subtract(first)).normalize();
        vertex(pose, vertices, first, 0.0F, 0.0F, normal, red, green, blue, alpha);
        vertex(pose, vertices, second, 0.0F, 1.0F, normal, red, green, blue, alpha);
        vertex(pose, vertices, third, 1.0F, 1.0F, normal, red, green, blue, alpha);
        vertex(pose, vertices, fourth, 1.0F, 0.0F, normal, red, green, blue, alpha);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vertices,
            Vec3 point, float u, float v, Vec3 normal,
            float red, float green, float blue, float alpha) {
        vertex(pose, vertices, point.x, point.y, point.z, u, v,
                normal.x, normal.y, normal.z, red, green, blue, alpha);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vertices,
            double x, double y, double z, float u, float v,
            double normalX, double normalY, double normalZ,
            float red, float green, float blue, float alpha) {
        vertices.addVertex(pose, (float) x, (float) y, (float) z)
                .setColor(red, green, blue, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, (float) normalX, (float) normalY, (float) normalZ);
    }

    private static void line(PoseStack.Pose pose, VertexConsumer lines,
            Vec3 start, Vec3 end, Vec3 camera,
            float red, float green, float blue, float alpha) {
        Vec3 normal = end.subtract(start).normalize();
        lines.addVertex(pose, (float) (start.x - camera.x),
                        (float) (start.y - camera.y), (float) (start.z - camera.z))
                .setColor(red, green, blue, alpha)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
        lines.addVertex(pose, (float) (end.x - camera.x),
                        (float) (end.y - camera.y), (float) (end.z - camera.z))
                .setColor(red, green, blue, alpha)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static Vec3 worldPoint(Object subLevel, Vec3 point) {
        return subLevel == null ? point : SableCompat.toRenderWorld(subLevel, point);
    }

    record EdgeKey(int axis, int x, int y, int z) {
        private int coordinate() {
            return switch (axis) {
                case 0 -> x;
                case 1 -> y;
                default -> z;
            };
        }

        private int fixedFirst() {
            return axis == 0 ? y : x;
        }

        private int fixedSecond() {
            return axis == 2 ? y : z;
        }
    }

    record EdgeRun(int axis, int x, int y, int z, int length) {
        private Vec3 start() {
            return new Vec3(x, y, z);
        }

        private Vec3 end() {
            return switch (axis) {
                case 0 -> new Vec3(x + length, y, z);
                case 1 -> new Vec3(x, y + length, z);
                default -> new Vec3(x, y, z + length);
            };
        }
    }

    record FaceKey(BlockPos pos, Direction direction) {
    }

    private record CachedGroup(MudTuningSelectionPayload.HighlightKind kind,
            java.util.UUID subLevelId, List<FaceKey> faces, List<EdgeRun> edges,
            AABB bounds) {
        private static CachedGroup build(MudTuningSelectionPayload.HighlightGroup group) {
            Set<Long> positions = new HashSet<>(group.positions().length * 2);
            for (long packed : group.positions()) {
                positions.add(packed);
            }
            List<FaceKey> faces = new ArrayList<>();
            List<EdgeKey> edges = new ArrayList<>();
            for (long packed : group.positions()) {
                BlockPos pos = BlockPos.of(packed);
                if (group.kind() == MudTuningSelectionPayload.HighlightKind.INCOMPATIBLE) {
                    for (Direction direction : Direction.values()) {
                        if (!positions.contains(pos.relative(direction).asLong())) {
                            faces.add(new FaceKey(pos, direction));
                        }
                    }
                }
            }
            for (int index = 0; index < group.edgeCorners().length; index++) {
                BlockPos corner = BlockPos.of(group.edgeCorners()[index]);
                edges.add(new EdgeKey(group.edgeAxes()[index],
                        corner.getX(), corner.getY(), corner.getZ()));
            }
            return new CachedGroup(group.kind(), group.subLevelId(),
                    List.copyOf(faces), mergeEdges(edges), geometryBounds(group));
        }
    }

    static List<EdgeRun> mergeEdges(List<EdgeKey> edges) {
        if (edges.isEmpty()) {
            return List.of();
        }
        List<EdgeKey> sorted = new ArrayList<>(edges);
        sorted.sort(Comparator.comparingInt(EdgeKey::axis)
                .thenComparingInt(EdgeKey::fixedFirst)
                .thenComparingInt(EdgeKey::fixedSecond)
                .thenComparingInt(EdgeKey::coordinate));
        List<EdgeRun> merged = new ArrayList<>();
        EdgeKey first = sorted.getFirst();
        int runStart = first.coordinate();
        int runEnd = runStart + 1;
        for (int index = 1; index < sorted.size(); index++) {
            EdgeKey next = sorted.get(index);
            if (sameLine(first, next) && next.coordinate() <= runEnd) {
                runEnd = Math.max(runEnd, next.coordinate() + 1);
                continue;
            }
            merged.add(edgeRun(first, runStart, runEnd));
            first = next;
            runStart = next.coordinate();
            runEnd = runStart + 1;
        }
        merged.add(edgeRun(first, runStart, runEnd));
        return List.copyOf(merged);
    }

    private static boolean sameLine(EdgeKey first, EdgeKey second) {
        return first.axis == second.axis
                && first.fixedFirst() == second.fixedFirst()
                && first.fixedSecond() == second.fixedSecond();
    }

    private static EdgeRun edgeRun(EdgeKey edge, int start, int end) {
        return switch (edge.axis) {
            case 0 -> new EdgeRun(0, start, edge.y, edge.z, end - start);
            case 1 -> new EdgeRun(1, edge.x, start, edge.z, end - start);
            default -> new EdgeRun(2, edge.x, edge.y, start, end - start);
        };
    }

    private static AABB geometryBounds(MudTuningSelectionPayload.HighlightGroup group) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (long packed : group.positions()) {
            BlockPos pos = BlockPos.of(packed);
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX() + 1.0D);
            maxY = Math.max(maxY, pos.getY() + 1.0D);
            maxZ = Math.max(maxZ, pos.getZ() + 1.0D);
        }
        for (int index = 0; index < group.edgeCorners().length; index++) {
            BlockPos corner = BlockPos.of(group.edgeCorners()[index]);
            int axis = group.edgeAxes()[index];
            minX = Math.min(minX, corner.getX());
            minY = Math.min(minY, corner.getY());
            minZ = Math.min(minZ, corner.getZ());
            maxX = Math.max(maxX, corner.getX() + (axis == 0 ? 1.0D : 0.0D));
            maxY = Math.max(maxY, corner.getY() + (axis == 1 ? 1.0D : 0.0D));
            maxZ = Math.max(maxZ, corner.getZ() + (axis == 2 ? 1.0D : 0.0D));
        }
        if (!Double.isFinite(minX)) {
            return null;
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(0.01D);
    }

    private static AABB worldBounds(AABB localBounds, Object subLevel) {
        if (localBounds == null || subLevel == null) {
            return localBounds;
        }
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (int index = 0; index < 8; index++) {
            Vec3 world = worldPoint(subLevel, new Vec3(
                    (index & 1) == 0 ? localBounds.minX : localBounds.maxX,
                    (index & 2) == 0 ? localBounds.minY : localBounds.maxY,
                    (index & 4) == 0 ? localBounds.minZ : localBounds.maxZ));
            if (world == null) {
                return null;
            }
            minX = Math.min(minX, world.x);
            minY = Math.min(minY, world.y);
            minZ = Math.min(minZ, world.z);
            maxX = Math.max(maxX, world.x);
            maxY = Math.max(maxY, world.y);
            maxZ = Math.max(maxZ, world.z);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static double distanceToSqr(AABB bounds, Vec3 point) {
        return distanceToSqr(bounds.minX, bounds.minY, bounds.minZ,
                bounds.maxX, bounds.maxY, bounds.maxZ, point);
    }

    private static double distanceToSqr(
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ, Vec3 point) {
        double dx = Math.max(Math.max(minX - point.x, 0.0D), point.x - maxX);
        double dy = Math.max(Math.max(minY - point.y, 0.0D), point.y - maxY);
        double dz = Math.max(Math.max(minZ - point.z, 0.0D), point.z - maxZ);
        return dx * dx + dy * dy + dz * dz;
    }

    record Color(float red, float green, float blue) {
        static final Color WHITE = new Color(1.0F, 1.0F, 1.0F);

        private static Color mixedFlowColor() {
            Minecraft minecraft = Minecraft.getInstance();
            double tick = minecraft.level == null ? 0.0D
                    : minecraft.level.getGameTime()
                            + minecraft.getTimer().getGameTimeDeltaPartialTick(false);
            float wave = (float) (0.5D + 0.5D
                    * Math.sin(tick * (Math.PI * 2.0D / MIXED_FLOW_PERIOD_TICKS)));
            Color modified = configured(MudTuningClientSettings.HudColor.MODIFIED);
            Color flow = configured(MudTuningClientSettings.HudColor.FLOW);
            return new Color(
                    lerp(modified.red, flow.red, wave),
                    lerp(modified.green, flow.green, wave),
                    lerp(modified.blue, flow.blue, wave));
        }

        private static Color configured(MudTuningClientSettings.HudColor color) {
            int rgb = color.color();
            return new Color((rgb >> 16 & 0xFF) / 255.0F,
                    (rgb >> 8 & 0xFF) / 255.0F,
                    (rgb & 0xFF) / 255.0F);
        }

        private static float lerp(float first, float second, float amount) {
            return first + (second - first) * amount;
        }

        static Color forKind(MudTuningSelectionPayload.HighlightKind kind) {
            return switch (kind) {
                case MODIFIED_NATIVE -> configured(MudTuningClientSettings.HudColor.MODIFIED);
                case INCOMPATIBLE -> configured(
                        MudTuningClientSettings.HudColor.INCOMPATIBLE);
                case CONVERTED_DEFAULT -> configured(
                        MudTuningClientSettings.HudColor.CONVERTED_DEFAULT);
                case CONVERTED_MODIFIED -> configured(
                        MudTuningClientSettings.HudColor.CONVERTED_MODIFIED);
                case MODIFIED_NATIVE_FLOW -> configured(
                        MudTuningClientSettings.HudColor.FLOW);
                case MODIFIED_NATIVE_FLOW_MIXED -> mixedFlowColor();
            };
        }
    }
}

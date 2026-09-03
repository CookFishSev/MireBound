package com.fish.mirebound.client;

import com.fish.mirebound.mud.MudBodyPart;
import com.fish.mirebound.mud.MudSurface;
import com.fish.mirebound.mud.MudSurfaceLayout;
import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Maps custom humanoid armor UV pixels back to the nearest canonical body surface cell. */
final class ArmorModelUvProjector {
    private static final Field CUBE_POLYGONS = findField(ModelPart.Cube.class, "polygons");
    private static final Field POLYGON_VERTICES = findField(findNestedClass("Polygon"), "vertices");
    private static final Field POLYGON_NORMAL = findField(findNestedClass("Polygon"), "normal");
    private static final Field VERTEX_POS = findField(findNestedClass("Vertex"), "pos");
    private static final Field VERTEX_U = findField(findNestedClass("Vertex"), "u");
    private static final Field VERTEX_V = findField(findNestedClass("Vertex"), "v");
    private static final Map<ModelPart, Map<Key, Plan>> CACHE = new WeakHashMap<>();

    private ArmorModelUvProjector() {
    }

    static Plan plan(ModelPart modelPart, MudBodyPart bodyPart, int textureWidth, int textureHeight) {
        if (!available()) {
            return Plan.EMPTY;
        }
        Key key = new Key(bodyPart, textureWidth, textureHeight);
        Map<Key, Plan> byShape = CACHE.computeIfAbsent(modelPart, ignored -> new HashMap<>());
        return byShape.computeIfAbsent(key, ignored -> build(modelPart, bodyPart, textureWidth, textureHeight));
    }

    static void reset() {
        CACHE.clear();
    }

    private static Plan build(ModelPart modelPart, MudBodyPart bodyPart, int textureWidth, int textureHeight) {
        Map<Integer, int[]> candidates = new HashMap<>();
        PoseStack rootPose = new PoseStack();
        modelPart.translateAndRotate(rootPose);
        Matrix4f inverseRoot = new Matrix4f(rootPose.last().pose()).invert();
        try {
            modelPart.visit(new PoseStack(), (pose, path, cubeIndex, cube) -> {
                try {
                    Object[] polygons = (Object[]) CUBE_POLYGONS.get(cube);
                    for (Object polygon : polygons) {
                        addPolygon(candidates, pose, inverseRoot, polygon, bodyPart, textureWidth, textureHeight);
                    }
                } catch (ReflectiveOperationException | ClassCastException ignored) {
                }
            });
        } catch (RuntimeException exception) {
            return Plan.EMPTY;
        }
        return candidates.isEmpty() ? Plan.EMPTY : new Plan(Map.copyOf(candidates));
    }

    private static void addPolygon(Map<Integer, int[]> candidates, PoseStack.Pose pose, Matrix4f inverseRoot,
            Object polygon, MudBodyPart bodyPart, int textureWidth, int textureHeight)
            throws ReflectiveOperationException {
        Object[] rawVertices = (Object[]) POLYGON_VERTICES.get(polygon);
        if (rawVertices.length != 4) {
            return;
        }
        Vertex[] vertices = new Vertex[4];
        for (int index = 0; index < rawVertices.length; index++) {
            Object raw = rawVertices[index];
            Vector3f source = (Vector3f) VERTEX_POS.get(raw);
            Vector3f modelPoint = pose.pose().transformPosition(
                    source.x() / 16.0F, source.y() / 16.0F, source.z() / 16.0F, new Vector3f());
            Vector3f local = inverseRoot.transformPosition(modelPoint).mul(16.0F);
            vertices[index] = new Vertex(local, VERTEX_U.getFloat(raw), VERTEX_V.getFloat(raw));
        }

        Vector3f rawNormal = (Vector3f) POLYGON_NORMAL.get(polygon);
        Vector3f modelNormal = pose.transformNormal(rawNormal.x(), rawNormal.y(), rawNormal.z(), new Vector3f());
        Vector3f localNormal = inverseRoot.transformDirection(modelNormal);
        if (localNormal.lengthSquared() < 1.0E-6F) {
            return;
        }
        localNormal.normalize();
        Corners corners = Corners.of(vertices);
        if (corners == null) {
            return;
        }
        float minU = minU(vertices);
        float maxU = maxU(vertices);
        float minV = minV(vertices);
        float maxV = maxV(vertices);
        if (maxU - minU <= 1.0E-7F || maxV - minV <= 1.0E-7F) {
            return;
        }
        int minX = Mth.clamp(Mth.floor(minU * textureWidth + 0.0001F), 0, textureWidth);
        int maxX = Mth.clamp(Mth.ceil(maxU * textureWidth - 0.0001F), 0, textureWidth);
        int minY = Mth.clamp(Mth.floor(minV * textureHeight + 0.0001F), 0, textureHeight);
        int maxY = Mth.clamp(Mth.ceil(maxV * textureHeight - 0.0001F), 0, textureHeight);
        MudSurface surface = surface(localNormal);
        for (int y = minY; y < maxY; y++) {
            float t = Mth.clamp(((y + 0.5F) / textureHeight - minV) / (maxV - minV), 0.0F, 1.0F);
            for (int x = minX; x < maxX; x++) {
                float s = Mth.clamp(((x + 0.5F) / textureWidth - minU) / (maxU - minU), 0.0F, 1.0F);
                Vector3f point = corners.point(s, t);
                int cell = nearestCell(bodyPart, surface, point);
                int pixel = y * textureWidth + x;
                candidates.put(pixel, addUnique(candidates.get(pixel), cell));
            }
        }
    }

    private static int nearestCell(MudBodyPart part, MudSurface surface, Vector3f point) {
        Bounds bounds = Bounds.of(part);
        MudSurfaceLayout.Face face = MudSurfaceLayout.face(part, surface);
        float vertical = Mth.clamp((bounds.maxY - point.y()) / bounds.height(), 0.0F, 0.999999F);
        float side = Mth.clamp((point.x() - bounds.minX) / bounds.width(), 0.0F, 0.999999F);
        float backToFront = Mth.clamp((bounds.maxZ - point.z()) / bounds.depth(), 0.0F, 0.999999F);
        int row;
        int column;
        if (face.vertical()) {
            row = Mth.clamp((int) (vertical * face.height()), 0, face.height() - 1);
            column = surface == MudSurface.FRONT || surface == MudSurface.BACK
                    ? Mth.clamp((int) (side * face.width()), 0, face.width() - 1)
                    : Mth.clamp((int) (backToFront * face.width()), 0, face.width() - 1);
        } else {
            row = Mth.clamp((int) (backToFront * face.height()), 0, face.height() - 1);
            column = Mth.clamp((int) (side * face.width()), 0, face.width() - 1);
        }
        return MudSurfaceLayout.cellIndex(part, surface, row, column);
    }

    private static MudSurface surface(Vector3f normal) {
        float x = Math.abs(normal.x());
        float y = Math.abs(normal.y());
        float z = Math.abs(normal.z());
        if (y >= x && y >= z) {
            return normal.y() < 0.0F ? MudSurface.TOP : MudSurface.BOTTOM;
        }
        if (x >= z) {
            return normal.x() < 0.0F ? MudSurface.RIGHT : MudSurface.LEFT;
        }
        return normal.z() < 0.0F ? MudSurface.FRONT : MudSurface.BACK;
    }

    private static int[] addUnique(int[] values, int value) {
        if (values == null) {
            return new int[] {value};
        }
        for (int current : values) {
            if (current == value) {
                return values;
            }
        }
        int[] result = Arrays.copyOf(values, values.length + 1);
        result[values.length] = value;
        return result;
    }

    private static boolean available() {
        return CUBE_POLYGONS != null && POLYGON_VERTICES != null && POLYGON_NORMAL != null
                && VERTEX_POS != null && VERTEX_U != null && VERTEX_V != null;
    }

    private static Class<?> findNestedClass(String simpleName) {
        for (Class<?> nested : ModelPart.class.getDeclaredClasses()) {
            if (nested.getSimpleName().equals(simpleName)) {
                return nested;
            }
        }
        return null;
    }

    private static Field findField(Class<?> type, String name) {
        if (type == null) {
            return null;
        }
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    private static float minU(Vertex[] vertices) {
        float value = Float.POSITIVE_INFINITY;
        for (Vertex vertex : vertices) value = Math.min(value, vertex.u);
        return value;
    }

    private static float maxU(Vertex[] vertices) {
        float value = Float.NEGATIVE_INFINITY;
        for (Vertex vertex : vertices) value = Math.max(value, vertex.u);
        return value;
    }

    private static float minV(Vertex[] vertices) {
        float value = Float.POSITIVE_INFINITY;
        for (Vertex vertex : vertices) value = Math.min(value, vertex.v);
        return value;
    }

    private static float maxV(Vertex[] vertices) {
        float value = Float.NEGATIVE_INFINITY;
        for (Vertex vertex : vertices) value = Math.max(value, vertex.v);
        return value;
    }

    record Plan(Map<Integer, int[]> candidates) {
        private static final Plan EMPTY = new Plan(Map.of());
    }

    private record Key(MudBodyPart bodyPart, int width, int height) {
    }

    private record Vertex(Vector3f position, float u, float v) {
    }

    private record Corners(Vertex p00, Vertex p10, Vertex p11, Vertex p01) {
        private static Corners of(Vertex[] vertices) {
            Vertex p00 = nearest(vertices, true, true);
            Vertex p10 = nearest(vertices, false, true);
            Vertex p11 = nearest(vertices, false, false);
            Vertex p01 = nearest(vertices, true, false);
            return p00 == null || p10 == null || p11 == null || p01 == null
                    ? null : new Corners(p00, p10, p11, p01);
        }

        private Vector3f point(float s, float t) {
            Vector3f top = lerp(p00.position, p10.position, s);
            Vector3f bottom = lerp(p01.position, p11.position, s);
            return lerp(top, bottom, t);
        }

        private static Vertex nearest(Vertex[] vertices, boolean lowU, boolean lowV) {
            float targetU = lowU ? minU(vertices) : maxU(vertices);
            float targetV = lowV ? minV(vertices) : maxV(vertices);
            Vertex best = null;
            float bestDistance = Float.POSITIVE_INFINITY;
            for (Vertex vertex : vertices) {
                float distance = Math.abs(vertex.u - targetU) + Math.abs(vertex.v - targetV);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = vertex;
                }
            }
            return best;
        }

        private static Vector3f lerp(Vector3f a, Vector3f b, float amount) {
            return new Vector3f(
                    Mth.lerp(amount, a.x(), b.x()),
                    Mth.lerp(amount, a.y(), b.y()),
                    Mth.lerp(amount, a.z(), b.z()));
        }
    }

    private record Bounds(float minX, float maxX, float minY, float maxY, float minZ, float maxZ) {
        private static Bounds of(MudBodyPart part) {
            return switch (part) {
                case HEAD -> new Bounds(-4.0F, 4.0F, -8.0F, 0.0F, -4.0F, 4.0F);
                case BODY -> new Bounds(-4.0F, 4.0F, 0.0F, 12.0F, -2.0F, 2.0F);
                case LEFT_ARM -> new Bounds(-1.0F, 3.0F, -2.0F, 10.0F, -2.0F, 2.0F);
                case RIGHT_ARM -> new Bounds(-3.0F, 1.0F, -2.0F, 10.0F, -2.0F, 2.0F);
                case LEFT_LEG, RIGHT_LEG -> new Bounds(-2.0F, 2.0F, 0.0F, 12.0F, -2.0F, 2.0F);
            };
        }

        private float width() {
            return maxX - minX;
        }

        private float height() {
            return maxY - minY;
        }

        private float depth() {
            return maxZ - minZ;
        }
    }
}

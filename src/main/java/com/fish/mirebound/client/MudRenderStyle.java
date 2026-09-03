package com.fish.mirebound.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.reflect.Field;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

final class MudRenderStyle {
    private static final int ALPHA_THRESHOLD = 4;
    private static final float SURFACE_OFFSET = 0.0036F;
    private static final float PIXEL_THICKNESS = 0.0065F;
    private static final float CLOTHING_INNER_THICKNESS = 0.0210F;
    private static final float CLOTHING_OUTER_BONUS = 0.0012F;
    private static final Field CUBE_POLYGONS = findField(ModelPart.Cube.class, "polygons");
    private static final Field POLYGON_VERTICES = findField(findNestedClass("Polygon"), "vertices");
    private static final Field POLYGON_NORMAL = findField(findNestedClass("Polygon"), "normal");
    private static final Field VERTEX_POS = findField(findNestedClass("Vertex"), "pos");
    private static final Field VERTEX_U = findField(findNestedClass("Vertex"), "u");
    private static final Field VERTEX_V = findField(findNestedClass("Vertex"), "v");

    private MudRenderStyle() {
    }

    static void renderPart(ModelPart part, PoseStack poseStack, MultiBufferSource bufferSource,
            int packedLight, int overlay, ResourceLocation mudTexture) {
        renderFlatPart(part, poseStack, bufferSource, packedLight, overlay, mudTexture, false);
    }

    static void renderPart(ModelPart part, PoseStack poseStack, MultiBufferSource bufferSource,
            int packedLight, int overlay, ResourceLocation mudTexture, boolean fillClothingGap) {
        renderFlatPart(part, poseStack, bufferSource, packedLight, overlay, mudTexture, false);
    }

    static void renderFlatPart(ModelPart part, PoseStack poseStack, MultiBufferSource bufferSource,
            int packedLight, int overlay, ResourceLocation mudTexture, boolean cutout) {
        if (!part.visible || part.skipDraw) {
            return;
        }

        VertexConsumer consumer = bufferSource.getBuffer(cutout ? RenderType.entityCutoutNoCull(mudTexture) : RenderType.entityTranslucent(mudTexture));
        part.render(poseStack, consumer, packedLight, overlay, -1);
    }

    static void renderArmorPart(ModelPart part, PoseStack poseStack, MultiBufferSource bufferSource,
            int packedLight, int overlay, ResourceLocation mudTexture) {
        if (!part.visible || part.skipDraw) {
            return;
        }
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityTranslucent(mudTexture));
        if (!hasModelUvAccess()) {
            part.render(poseStack, consumer, packedLight, overlay, -1);
            return;
        }
        part.visit(poseStack, (pose, path, index, cube) -> renderCube(
                pose, cube, consumer, packedLight, overlay, mudTexture, 64, 32, false));
    }

    private static void renderClothingPart(ModelPart part, PoseStack poseStack, MultiBufferSource bufferSource,
            int packedLight, int overlay, ResourceLocation mudTexture) {
        if (!part.visible || part.skipDraw) {
            return;
        }

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(mudTexture));
        if (!hasModelUvAccess()) {
            part.render(poseStack, consumer, packedLight, overlay, -1);
            return;
        }

        int textureWidth = MudSkinTextureCache.textureWidth(mudTexture);
        int textureHeight = MudSkinTextureCache.textureHeight(mudTexture);
        part.visit(poseStack, (pose, path, index, cube) -> renderCube(
                pose,
                cube,
                consumer,
                packedLight,
                overlay,
                mudTexture,
                textureWidth,
                textureHeight,
                true));
    }

    private static void renderCube(PoseStack.Pose pose, ModelPart.Cube cube, VertexConsumer consumer, int packedLight,
            int overlay, ResourceLocation mudTexture, int textureWidth, int textureHeight, boolean fillClothingGap) {
        try {
            Object[] polygons = (Object[]) CUBE_POLYGONS.get(cube);
            for (Object polygon : polygons) {
                SourceVertex[] vertices = readVertices(polygon);
                Vector3f normal = new Vector3f((Vector3f) POLYGON_NORMAL.get(polygon));
                renderPixelFace(pose, consumer, packedLight, overlay, mudTexture, textureWidth, textureHeight, normal, vertices, fillClothingGap);
            }
        } catch (IllegalAccessException | ClassCastException exception) {
            cube.compile(pose, consumer, packedLight, overlay, -1);
        }
    }

    private static SourceVertex[] readVertices(Object polygon) throws IllegalAccessException {
        Object[] rawVertices = (Object[]) POLYGON_VERTICES.get(polygon);
        SourceVertex[] vertices = new SourceVertex[rawVertices.length];
        for (int i = 0; i < rawVertices.length; i++) {
            Object rawVertex = rawVertices[i];
            Vector3f position = (Vector3f) VERTEX_POS.get(rawVertex);
            vertices[i] = new SourceVertex(position.x(), position.y(), position.z(), VERTEX_U.getFloat(rawVertex), VERTEX_V.getFloat(rawVertex));
        }
        return vertices;
    }

    private static void renderPixelFace(PoseStack.Pose pose, VertexConsumer consumer, int packedLight, int overlay,
            ResourceLocation mudTexture, int textureWidth, int textureHeight, Vector3f normal, SourceVertex[] vertices,
            boolean fillClothingGap) {
        if (vertices.length != 4) {
            return;
        }

        UvBounds uvBounds = UvBounds.of(vertices, textureWidth, textureHeight);
        if (uvBounds.isEmpty()) {
            return;
        }

        UvCorners corners = UvCorners.of(vertices);
        if (corners == null) {
            return;
        }

        Matrix4f matrix = pose.pose();
        Vector3f transformedNormal = pose.transformNormal(normal.x(), normal.y(), normal.z(), new Vector3f());
        if (transformedNormal.lengthSquared() > 0.000001F) {
            transformedNormal.normalize();
        }

        float topOffsetBase = SURFACE_OFFSET + (fillClothingGap ? CLOTHING_OUTER_BONUS : 0.0F);
        float bottomOffset = fillClothingGap ? -CLOTHING_INNER_THICKNESS : -PIXEL_THICKNESS;
        for (int py = uvBounds.minY; py < uvBounds.maxY; py++) {
            for (int px = uvBounds.minX; px < uvBounds.maxX; px++) {
                int pixel = texturePixel(mudTexture, px, py);
                if (FastColor.ABGR32.alpha(pixel) <= ALPHA_THRESHOLD) {
                    continue;
                }

                float jitter = pixelJitter(px, py) * 0.0018F;
                float topOffset = topOffsetBase + jitter;
                PixelQuad quad = pixelQuad(corners, uvBounds, textureWidth, textureHeight, px, py);
                emitFace(pose, consumer, packedLight, overlay, matrix, transformedNormal, quad, normal, topOffset, false);
                if (fillClothingGap) {
                    emitFace(pose, consumer, packedLight, overlay, matrix, new Vector3f(transformedNormal).negate(), quad, normal, bottomOffset, true);
                }
                emitOpenEdges(pose, consumer, packedLight, overlay, mudTexture, textureWidth, textureHeight, matrix,
                        transformedNormal, quad, normal, topOffset, bottomOffset, px, py, fillClothingGap);
            }
        }
    }

    private static PixelQuad pixelQuad(UvCorners corners, UvBounds bounds, int textureWidth, int textureHeight, int px, int py) {
        float u0 = px / (float) textureWidth;
        float u1 = (px + 1) / (float) textureWidth;
        float v0 = py / (float) textureHeight;
        float v1 = (py + 1) / (float) textureHeight;
        float s0 = bounds.toU(u0);
        float s1 = bounds.toU(u1);
        float t0 = bounds.toV(v0);
        float t1 = bounds.toV(v1);
        return new PixelQuad(
                corners.point(s0, t0), corners.point(s1, t0), corners.point(s1, t1), corners.point(s0, t1),
                u0, v0, u1, v1);
    }

    private static void emitOpenEdges(PoseStack.Pose pose, VertexConsumer consumer, int packedLight, int overlay,
            ResourceLocation mudTexture, int textureWidth, int textureHeight, Matrix4f matrix, Vector3f normal,
            PixelQuad quad, Vector3f sourceNormal, float topOffset, float bottomOffset, int px, int py, boolean fillClothingGap) {
        float edgeBottom = fillClothingGap ? bottomOffset : topOffset - PIXEL_THICKNESS;
        if (!covered(mudTexture, px - 1, py)) {
            emitSide(pose, consumer, packedLight, overlay, matrix, normal, sourceNormal, quad.p00, quad.p01, quad.u0, quad.v0, quad.u0, quad.v1, topOffset, edgeBottom);
        }
        if (!covered(mudTexture, px + 1, py)) {
            emitSide(pose, consumer, packedLight, overlay, matrix, normal, sourceNormal, quad.p10, quad.p11, quad.u1, quad.v0, quad.u1, quad.v1, topOffset, edgeBottom);
        }
        if (!covered(mudTexture, px, py - 1)) {
            emitSide(pose, consumer, packedLight, overlay, matrix, normal, sourceNormal, quad.p00, quad.p10, quad.u0, quad.v0, quad.u1, quad.v0, topOffset, edgeBottom);
        }
        if (!covered(mudTexture, px, py + 1)) {
            emitSide(pose, consumer, packedLight, overlay, matrix, normal, sourceNormal, quad.p01, quad.p11, quad.u0, quad.v1, quad.u1, quad.v1, topOffset, edgeBottom);
        }
    }

    private static boolean covered(ResourceLocation mudTexture, int px, int py) {
        return FastColor.ABGR32.alpha(texturePixel(mudTexture, px, py)) > ALPHA_THRESHOLD;
    }

    private static int texturePixel(ResourceLocation texture, int x, int y) {
        return ArmorMudTextureCache.owns(texture)
                ? ArmorMudTextureCache.pixel(texture, x, y)
                : MudSkinTextureCache.pixel(texture, x, y);
    }

    private static void emitFace(PoseStack.Pose pose, VertexConsumer consumer, int packedLight, int overlay, Matrix4f matrix,
            Vector3f transformedNormal, PixelQuad quad, Vector3f sourceNormal, float offset, boolean reversed) {
        if (reversed) {
            emitVertex(pose, consumer, packedLight, overlay, matrix, transformedNormal, sourceNormal, quad.p01, quad.u0, quad.v1, offset);
            emitVertex(pose, consumer, packedLight, overlay, matrix, transformedNormal, sourceNormal, quad.p11, quad.u1, quad.v1, offset);
            emitVertex(pose, consumer, packedLight, overlay, matrix, transformedNormal, sourceNormal, quad.p10, quad.u1, quad.v0, offset);
            emitVertex(pose, consumer, packedLight, overlay, matrix, transformedNormal, sourceNormal, quad.p00, quad.u0, quad.v0, offset);
            return;
        }

        emitVertex(pose, consumer, packedLight, overlay, matrix, transformedNormal, sourceNormal, quad.p00, quad.u0, quad.v0, offset);
        emitVertex(pose, consumer, packedLight, overlay, matrix, transformedNormal, sourceNormal, quad.p10, quad.u1, quad.v0, offset);
        emitVertex(pose, consumer, packedLight, overlay, matrix, transformedNormal, sourceNormal, quad.p11, quad.u1, quad.v1, offset);
        emitVertex(pose, consumer, packedLight, overlay, matrix, transformedNormal, sourceNormal, quad.p01, quad.u0, quad.v1, offset);
    }

    private static void emitSide(PoseStack.Pose pose, VertexConsumer consumer, int packedLight, int overlay, Matrix4f matrix,
            Vector3f transformedNormal, Vector3f sourceNormal, Vector3f a, Vector3f b, float ua, float va, float ub, float vb,
            float topOffset, float bottomOffset) {
        emitVertex(pose, consumer, packedLight, overlay, matrix, transformedNormal, sourceNormal, a, ua, va, topOffset);
        emitVertex(pose, consumer, packedLight, overlay, matrix, transformedNormal, sourceNormal, b, ub, vb, topOffset);
        emitVertex(pose, consumer, packedLight, overlay, matrix, transformedNormal, sourceNormal, b, ub, vb, bottomOffset);
        emitVertex(pose, consumer, packedLight, overlay, matrix, transformedNormal, sourceNormal, a, ua, va, bottomOffset);
    }

    private static void emitVertex(PoseStack.Pose pose, VertexConsumer consumer, int packedLight, int overlay, Matrix4f matrix,
            Vector3f transformedNormal, Vector3f sourceNormal, Vector3f localPosition, float u, float v, float offset) {
        Vector3f position = matrix.transformPosition(localPosition.x() / 16.0F, localPosition.y() / 16.0F, localPosition.z() / 16.0F, new Vector3f());
        Vector3f offsetNormal = pose.transformNormal(sourceNormal.x(), sourceNormal.y(), sourceNormal.z(), new Vector3f());
        if (offsetNormal.lengthSquared() > 0.000001F) {
            offsetNormal.normalize();
        }
        position.add(offsetNormal.x() * offset, offsetNormal.y() * offset, offsetNormal.z() * offset);
        consumer.addVertex(position.x(), position.y(), position.z(), -1, u, v, overlay, packedLight,
                transformedNormal.x(), transformedNormal.y(), transformedNormal.z());
    }

    private static float pixelJitter(int x, int y) {
        int hash = x * 73428767 ^ y * 9122719;
        hash ^= hash >>> 13;
        hash *= 1274126177;
        hash ^= hash >>> 16;
        return ((hash & 3) + 1) / 4.0F;
    }

    private static boolean hasModelUvAccess() {
        return CUBE_POLYGONS != null && POLYGON_VERTICES != null && POLYGON_NORMAL != null && VERTEX_POS != null && VERTEX_U != null && VERTEX_V != null;
    }

    private static Class<?> findNestedClass(String simpleName) {
        for (Class<?> nestedClass : ModelPart.class.getDeclaredClasses()) {
            if (nestedClass.getSimpleName().equals(simpleName)) {
                return nestedClass;
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

    private record SourceVertex(float x, float y, float z, float u, float v) {
        private Vector3f position() {
            return new Vector3f(x, y, z);
        }
    }

    private record PixelQuad(Vector3f p00, Vector3f p10, Vector3f p11, Vector3f p01, float u0, float v0, float u1, float v1) {
    }

    private record UvBounds(float minU, float maxU, float minV, float maxV, int minX, int maxX, int minY, int maxY) {
        private static UvBounds of(SourceVertex[] vertices, int textureWidth, int textureHeight) {
            float minU = Float.POSITIVE_INFINITY;
            float maxU = Float.NEGATIVE_INFINITY;
            float minV = Float.POSITIVE_INFINITY;
            float maxV = Float.NEGATIVE_INFINITY;
            for (SourceVertex vertex : vertices) {
                minU = Math.min(minU, vertex.u);
                maxU = Math.max(maxU, vertex.u);
                minV = Math.min(minV, vertex.v);
                maxV = Math.max(maxV, vertex.v);
            }
            int minX = Mth.clamp(Mth.floor(minU * textureWidth + 0.0001F), 0, textureWidth);
            int maxX = Mth.clamp(Mth.ceil(maxU * textureWidth - 0.0001F), 0, textureWidth);
            int minY = Mth.clamp(Mth.floor(minV * textureHeight + 0.0001F), 0, textureHeight);
            int maxY = Mth.clamp(Mth.ceil(maxV * textureHeight - 0.0001F), 0, textureHeight);
            return new UvBounds(minU, maxU, minV, maxV, minX, maxX, minY, maxY);
        }

        private boolean isEmpty() {
            return maxX <= minX || maxY <= minY || maxU - minU <= 0.000001F || maxV - minV <= 0.000001F;
        }

        private float toU(float u) {
            return Mth.clamp((u - minU) / (maxU - minU), 0.0F, 1.0F);
        }

        private float toV(float v) {
            return Mth.clamp((v - minV) / (maxV - minV), 0.0F, 1.0F);
        }
    }

    private record UvCorners(SourceVertex p00, SourceVertex p10, SourceVertex p11, SourceVertex p01) {
        private static UvCorners of(SourceVertex[] vertices) {
            SourceVertex p00 = nearest(vertices, true, true);
            SourceVertex p10 = nearest(vertices, false, true);
            SourceVertex p11 = nearest(vertices, false, false);
            SourceVertex p01 = nearest(vertices, true, false);
            if (p00 == null || p10 == null || p11 == null || p01 == null) {
                return null;
            }
            return new UvCorners(p00, p10, p11, p01);
        }

        private Vector3f point(float s, float t) {
            Vector3f top = lerp(p00.position(), p10.position(), s);
            Vector3f bottom = lerp(p01.position(), p11.position(), s);
            return lerp(top, bottom, t);
        }

        private static SourceVertex nearest(SourceVertex[] vertices, boolean lowU, boolean lowV) {
            float targetU = lowU ? minU(vertices) : maxU(vertices);
            float targetV = lowV ? minV(vertices) : maxV(vertices);
            SourceVertex best = null;
            float bestDistance = Float.POSITIVE_INFINITY;
            for (SourceVertex vertex : vertices) {
                float distance = Math.abs(vertex.u - targetU) + Math.abs(vertex.v - targetV);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = vertex;
                }
            }
            return best;
        }

        private static float minU(SourceVertex[] vertices) {
            float value = Float.POSITIVE_INFINITY;
            for (SourceVertex vertex : vertices) {
                value = Math.min(value, vertex.u);
            }
            return value;
        }

        private static float maxU(SourceVertex[] vertices) {
            float value = Float.NEGATIVE_INFINITY;
            for (SourceVertex vertex : vertices) {
                value = Math.max(value, vertex.u);
            }
            return value;
        }

        private static float minV(SourceVertex[] vertices) {
            float value = Float.POSITIVE_INFINITY;
            for (SourceVertex vertex : vertices) {
                value = Math.min(value, vertex.v);
            }
            return value;
        }

        private static float maxV(SourceVertex[] vertices) {
            float value = Float.NEGATIVE_INFINITY;
            for (SourceVertex vertex : vertices) {
                value = Math.max(value, vertex.v);
            }
            return value;
        }

        private static Vector3f lerp(Vector3f a, Vector3f b, float value) {
            return new Vector3f(
                    Mth.lerp(value, a.x(), b.x()),
                    Mth.lerp(value, a.y(), b.y()),
                    Mth.lerp(value, a.z(), b.z()));
        }
    }
}

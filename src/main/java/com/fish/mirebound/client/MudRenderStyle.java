package com.fish.mirebound.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.reflect.Field;
import com.fish.mirebound.mud.MudBodyPart;
import com.fish.mirebound.mud.MudCoverageRules;
import com.fish.mirebound.mud.MudCoveragePatternSeed;
import com.fish.mirebound.mud.MudSurface;
import com.fish.mirebound.mud.MudSurfaceLayout;
import com.fish.mirebound.mud.SinkingMedium;
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

    /** Renders coverage from the model face geometry instead of a shared UV texture. */
    static void renderCoveredSkinPart(ModelPart part, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, int overlay,
            int entityId, MudBodyPart targetPart, ResourceLocation skinTexture,
            boolean slimModel) {
        if (!com.fish.mirebound.client.config.MireboundClientSettings.independentSurfaceCoverage()) {
            ResourceLocation texture = MudSkinTextureCache.textureFor(entityId, skinTexture, slimModel);
            if (texture != null) renderPart(part, poseStack, bufferSource, packedLight, overlay, texture);
            return;
        }
        if (!part.visible || part.skipDraw) {
            return;
        }
        if (!hasModelUvAccess()) {
            ResourceLocation fallback = MudSkinTextureCache.partTextureFor(
                    entityId, skinTexture, slimModel, targetPart);
            if (fallback != null) {
                renderPart(part, poseStack, bufferSource, packedLight, overlay, fallback);
            }
            return;
        }
        ResourceLocation stencil = com.fish.mirebound.client.skin.SkinStainMaskTextures.texture(
                entityId, SkinPixelCache.width(skinTexture), SkinPixelCache.height(skinTexture));
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityTranslucent(stencil));
        part.visit(poseStack, (pose, path, index, cube) -> renderCoveredCube(
                pose, cube, consumer, packedLight, overlay, entityId, targetPart,
                skinTexture, slimModel));
    }


    private static void renderCoveredCube(PoseStack.Pose pose, ModelPart.Cube cube,
            VertexConsumer consumer, int packedLight, int overlay, int entityId,
            MudBodyPart targetPart, ResourceLocation skinTexture, boolean slimModel) {
        try {
            Object[] polygons = (Object[]) CUBE_POLYGONS.get(cube);
            for (Object polygon : polygons) {
                SourceVertex[] vertices = readVertices(polygon);
                Vector3f normal = new Vector3f((Vector3f) POLYGON_NORMAL.get(polygon));
                MudSurface surface = surfaceForNormal(normal);
                if (surface == null) {
                    continue;
                }
                renderCoveredFace(pose, consumer, packedLight, overlay, entityId,
                        targetPart, surface, skinTexture, vertices, normal, slimModel);
            }
        } catch (IllegalAccessException | ClassCastException ignored) {
            // The ordinary per-part texture path remains available when a renderer changes ModelPart internals.
        }
    }

    private static void renderCoveredFace(PoseStack.Pose pose, VertexConsumer consumer,
            int packedLight, int overlay, int entityId, MudBodyPart part,
            MudSurface surface, ResourceLocation skinTexture,
            SourceVertex[] vertices, Vector3f normal, boolean slimModel) {
        if (vertices.length != 4) {
            return;
        }
        float minU = Float.POSITIVE_INFINITY;
        float maxU = Float.NEGATIVE_INFINITY;
        float minV = Float.POSITIVE_INFINITY;
        float maxV = Float.NEGATIVE_INFINITY;
        for (SourceVertex vertex : vertices) {
            float u = surfaceAxisU(surface, vertex);
            float v = surfaceAxisV(surface, vertex);
            minU = Math.min(minU, u);
            maxU = Math.max(maxU, u);
            minV = Math.min(minV, v);
            maxV = Math.max(maxV, v);
        }
        if (maxU - minU < 0.0001F || maxV - minV < 0.0001F) {
            return;
        }
        Vector3f[] corners = faceCorners(surface, vertices, minU, maxU, minV, maxV);
        if (corners == null) {
            return;
        }
        SourceVertex[] uvCorners = new SourceVertex[4];
        for (int i = 0; i < 4; i++) {
            for (SourceVertex vertex : vertices)
                if (corners[i].distanceSquared(vertex.x, vertex.y, vertex.z) < 1e-8F) { uvCorners[i] = vertex; break; }
            if (uvCorners[i] == null) return;
        }
        MudSurfaceLayout.Face face = MudSurfaceLayout.face(part, surface);
        Vector3f transformedNormal = pose.transformNormal(normal.x(), normal.y(), normal.z(), new Vector3f());
        if (transformedNormal.lengthSquared() > 0.000001F) {
            transformedNormal.normalize();
        }
        for (int row = 0; row < face.height(); row++) {
            for (int column = 0; column < face.width(); column++) {
                int cell = MudSurfaceLayout.cellIndex(part, surface, row, column);
                ClientMudState.CoverageState display = ClientMudState.displaySnapshot(entityId);
                float coverage = display.surfacePixelCoverage(part, surface, row, column);
                int original = MudSkinTextureCache.skinSurfacePixel(
                        skinTexture, part, surface, row, column, slimModel);
                boolean skinPixelUsable = MudSkinTextureCache.skinSurfacePixelUsable(
                        skinTexture, part, surface, row, column, slimModel);
                int color;
                if (coverage <= 0.004F
                        || !MudCoverageAppearance.allowsCoveragePixel(
                                display.surfacePixelMedium(part, surface, row, column),
                                MudCoverageRules.DOMAIN_SKIN, cell,
                                MudSurfaceLayout.CELL_COUNT)
                        || !skinPixelUsable) {
                    float assimilationCoverage = ClientAssimilationState.coverage(entityId, cell);
                    if (assimilationCoverage <= 0.004F
                            || !skinPixelUsable) {
                        continue;
                    }
                    color = MudSkinTextureCache.blendedAssimilationOverlayPixel(
                            entityId, cell, original, column, row,
                            assimilationCoverage, cell * 31 + 0x41A55A17, false);
                } else {
                    SinkingMedium medium = display.surfacePixelMedium(part, surface, row, column);
                    long visualSource = display.surfacePixelVisualSource(part, surface, row, column);
                    int salt = MudCoveragePatternSeed.mix(
                            cell * 31 + part.ordinal() * 101,
                            ClientMudState.coveragePatternSeed(entityId));
                    color = MudSkinTextureCache.skinCoverageTextureAbgr(
                            medium, visualSource, column, row, salt,
                            Math.round(255.0F * Mth.clamp(coverage, 0.0F, 1.0F)));
                }
                int argb = FastColor.ARGB32.color(
                        FastColor.ABGR32.alpha(color), FastColor.ABGR32.red(color),
                        FastColor.ABGR32.green(color), FastColor.ABGR32.blue(color));
                float u0 = column / (float) face.width();
                float u1 = (column + 1) / (float) face.width();
                float v0 = row / (float) face.height();
                float v1 = (row + 1) / (float) face.height();
                PixelQuad quad = new PixelQuad(
                        interpolate(corners, u0, v0), interpolate(corners, u1, v0),
                        interpolate(corners, u1, v1), interpolate(corners, u0, v1),
                        0.0F, 0.0F, 1.0F, 1.0F);
                emitMaskedSkinFace(pose, consumer, packedLight, overlay,
                        transformedNormal, quad, normal, argb, uvCorners, u0, v0, u1, v1);
            }
        }
    }

    private static void emitMaskedSkinFace(PoseStack.Pose pose, VertexConsumer consumer,
            int packedLight, int overlay, Vector3f transformedNormal, PixelQuad quad,
            Vector3f sourceNormal, int color, SourceVertex[] uv,
            float s0, float t0, float s1, float t1) {
        Matrix4f matrix = pose.pose();
        emitColoredVertex(pose, consumer, packedLight, overlay, matrix,
                transformedNormal, sourceNormal, quad.p00, skinUv(uv,s0,t0,true), skinUv(uv,s0,t0,false), .004F, color);
        emitColoredVertex(pose, consumer, packedLight, overlay, matrix,
                transformedNormal, sourceNormal, quad.p10, skinUv(uv,s1,t0,true), skinUv(uv,s1,t0,false), .004F, color);
        emitColoredVertex(pose, consumer, packedLight, overlay, matrix,
                transformedNormal, sourceNormal, quad.p11, skinUv(uv,s1,t1,true), skinUv(uv,s1,t1,false), .004F, color);
        emitColoredVertex(pose, consumer, packedLight, overlay, matrix,
                transformedNormal, sourceNormal, quad.p01, skinUv(uv,s0,t1,true), skinUv(uv,s0,t1,false), .004F, color);
    }

    private static float skinUv(SourceVertex[] corners, float s, float t, boolean horizontal) {
        float value = 0;
        for (int i = 0; i < 4; i++) {
            float weight = (i == 0 || i == 3 ? 1 - s : s) * (i < 2 ? 1 - t : t);
            value += (horizontal ? corners[i].u : corners[i].v) * weight;
        }
        return value;
    }

    private static void emitColoredVertex(PoseStack.Pose pose, VertexConsumer consumer,
            int packedLight, int overlay, Matrix4f matrix, Vector3f transformedNormal,
            Vector3f sourceNormal, Vector3f localPosition, float u, float v,
            float offset, int color) {
        Vector3f position = matrix.transformPosition(localPosition.x() / 16.0F,
                localPosition.y() / 16.0F, localPosition.z() / 16.0F, new Vector3f());
        Vector3f offsetNormal = pose.transformNormal(
                sourceNormal.x(), sourceNormal.y(), sourceNormal.z(), new Vector3f());
        if (offsetNormal.lengthSquared() > 0.000001F) {
            offsetNormal.normalize();
        }
        position.add(offsetNormal.x() * offset, offsetNormal.y() * offset,
                offsetNormal.z() * offset);
        consumer.addVertex(position.x(), position.y(), position.z(), color,
                u, v, overlay, packedLight, transformedNormal.x(),
                transformedNormal.y(), transformedNormal.z());
    }

    private static MudSurface surfaceForNormal(Vector3f normal) {
        float x = Math.abs(normal.x());
        float y = Math.abs(normal.y());
        float z = Math.abs(normal.z());
        if (y >= x && y >= z) {
            return normal.y() >= 0.0F ? MudSurface.TOP : MudSurface.BOTTOM;
        }
        if (x >= z) {
            return normal.x() >= 0.0F ? MudSurface.LEFT : MudSurface.RIGHT;
        }
        return normal.z() >= 0.0F ? MudSurface.FRONT : MudSurface.BACK;
    }

    private static float surfaceAxisU(MudSurface surface, SourceVertex vertex) {
        return surface == MudSurface.LEFT || surface == MudSurface.RIGHT
                ? vertex.z : vertex.x;
    }

    private static float surfaceAxisV(MudSurface surface, SourceVertex vertex) {
        return surface == MudSurface.TOP || surface == MudSurface.BOTTOM
                ? vertex.z : vertex.y;
    }

    private static Vector3f[] faceCorners(MudSurface surface, SourceVertex[] vertices,
            float minU, float maxU, float minV, float maxV) {
        Vector3f[] result = new Vector3f[4];
        float centerU = (minU + maxU) * 0.5F;
        float centerV = (minV + maxV) * 0.5F;
        for (SourceVertex vertex : vertices) {
            int u = surfaceAxisU(surface, vertex) > centerU ? 1 : 0;
            int v = surfaceAxisV(surface, vertex) > centerV ? 1 : 0;
            int index = v * 2 + u;
            result[index] = vertex.position();
        }
        return result[0] == null || result[1] == null
                || result[2] == null || result[3] == null ? null : result;
    }

    private static Vector3f interpolate(Vector3f[] corners, float u, float v) {
        Vector3f bottom = new Vector3f(corners[0]).lerp(corners[1], u);
        Vector3f top = new Vector3f(corners[2]).lerp(corners[3], u);
        return bottom.lerp(top, v);
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

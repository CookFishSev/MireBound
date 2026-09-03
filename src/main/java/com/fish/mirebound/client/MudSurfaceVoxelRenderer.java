package com.fish.mirebound.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Draws raised mud-surface voxels while omitting walls buried by neighbors. */
final class MudSurfaceVoxelRenderer {
    static final Vec3 WORLD_UP = new Vec3(0.0D, 1.0D, 0.0D);
    static final Vec3 WORLD_X = new Vec3(1.0D, 0.0D, 0.0D);
    static final Vec3 WORLD_Z = new Vec3(0.0D, 0.0D, 1.0D);

    private static final double PIXEL = 1.0D / 16.0D;
    private static final double PILE_SURFACE_INSET = 0.00075D;
    private static final double MAXIMUM_PARTIAL_HEIGHT_SCALE = 1.20D;

    private MudSurfaceVoxelRenderer() {
    }

    static void renderTopPile(PoseStack.Pose pose,
            MultiBufferSource.BufferSource buffers,
            MudSurfaceEffectManager.Hole hole,
            MudSurfaceEffectManager.SurfaceCell cell,
            float partialTick,
            Long2BooleanOpenHashMap supportValidity,
            double pileHeight, double visualHeightEpsilon,
            float u0, float v0, float u1, float v1,
            MudSurfaceAppearance.Appearance appearance) {
        VertexConsumer vertices = MudSurfaceRenderBatchCache.pile(
                buffers, appearance, cell.medium);
        double centerX = (cell.pixelX + 0.5D) * PIXEL;
        double centerZ = (cell.pixelZ + 0.5D) * PIXEL;
        MudRenderedSurfaceGeometry.SurfaceHit renderedHit = cell.renderedHit;
        Vec3 normal = renderedHit == null ? WORLD_UP : renderedHit.normal();
        Vec3 axisX = renderedHit == null ? WORLD_X : renderedHit.axisX();
        Vec3 axisZ = renderedHit == null ? WORLD_Z : renderedHit.axisZ();
        double currentBase = renderedHit == null
                ? cell.surfaceY - PILE_SURFACE_INSET : 0.0D;
        double renderedHeight = visiblePileHeight(pileHeight, cell.renderedPatch);
        renderVoxel(pose, vertices,
                centerX - normal.x * PILE_SURFACE_INSET,
                cell.surfaceY - normal.y * PILE_SURFACE_INSET,
                centerZ - normal.z * PILE_SURFACE_INSET,
                axisX, axisZ, normal,
                PIXEL * 1.018D, renderedHeight, visualHeightEpsilon,
                wallStart(currentBase, renderedHeight,
                        topNeighbor(hole, cell, cell.pixelX, cell.pixelZ - 1,
                                partialTick, supportValidity,
                                visualHeightEpsilon)),
                wallStart(currentBase, renderedHeight,
                        topNeighbor(hole, cell, cell.pixelX + 1, cell.pixelZ,
                                partialTick, supportValidity,
                                visualHeightEpsilon)),
                wallStart(currentBase, renderedHeight,
                        topNeighbor(hole, cell, cell.pixelX, cell.pixelZ + 1,
                                partialTick, supportValidity,
                                visualHeightEpsilon)),
                wallStart(currentBase, renderedHeight,
                        topNeighbor(hole, cell, cell.pixelX - 1, cell.pixelZ,
                                partialTick, supportValidity,
                                visualHeightEpsilon)),
                cell.renderedPatch,
                appearance.u(u0), appearance.v(v0),
                appearance.u(u1), appearance.v(v1),
                255, cell.packedLight, appearance);
    }

    static void renderSidePile(PoseStack.Pose pose,
            MultiBufferSource.BufferSource buffers,
            MudSideSurfaceEffectManager.SideImprint imprint,
            MudSideSurfaceEffectManager.SideCell cell,
            MudSideSurfaceEffectManager.FaceBasis basis,
            float partialTick,
            double centerX, double centerY, double centerZ,
            double pileSize, double pileHeight, double visualHeightEpsilon,
            float u0, float v0, float u1, float v1,
            MudSurfaceAppearance.Appearance appearance) {
        Vec3 normal = basis.normal;
        VertexConsumer vertices = MudSurfaceRenderBatchCache.pile(
                buffers, appearance, imprint.medium);
        double renderedHeight = visiblePileHeight(pileHeight, cell.renderedPatch);
        renderVoxel(pose, vertices,
                centerX - normal.x * PILE_SURFACE_INSET,
                centerY - normal.y * PILE_SURFACE_INSET,
                centerZ - normal.z * PILE_SURFACE_INSET,
                basis.axisU, basis.axisV, normal,
                pileSize, renderedHeight, visualHeightEpsilon,
                wallStart(0.0D, renderedHeight,
                        sideNeighbor(imprint, cell.u, cell.v - 1, partialTick,
                                visualHeightEpsilon)),
                wallStart(0.0D, renderedHeight,
                        sideNeighbor(imprint, cell.u + 1, cell.v, partialTick,
                                visualHeightEpsilon)),
                wallStart(0.0D, renderedHeight,
                        sideNeighbor(imprint, cell.u, cell.v + 1, partialTick,
                                visualHeightEpsilon)),
                wallStart(0.0D, renderedHeight,
                        sideNeighbor(imprint, cell.u - 1, cell.v, partialTick,
                                visualHeightEpsilon)),
                cell.renderedPatch,
                appearance.u(u0), appearance.v(v0),
                appearance.u(u1), appearance.v(v1),
                255, cell.packedLight, appearance);
    }

    private static double topNeighbor(MudSurfaceEffectManager.Hole hole,
            MudSurfaceEffectManager.SurfaceCell current,
            int pixelX, int pixelZ, float partialTick,
            Long2BooleanOpenHashMap supportValidity,
            double visualHeightEpsilon) {
        MudSurfaceEffectManager.SurfaceCell neighbor = hole.cells.get(
                MudSurfaceEffectManager.cellKey(pixelX, pixelZ));
        if (neighbor == null) {
            return MudSurfaceVoxelGeometry.NO_NEIGHBOR;
        }
        long supportKey = MudSurfaceEffectManager.surfaceSupportKey(neighbor);
        if (!supportValidity.containsKey(supportKey)) {
            supportValidity.put(supportKey,
                    MudSurfaceEffectManager.hasCurrentSupport(neighbor));
        }
        if (!supportValidity.get(supportKey)) {
            return MudSurfaceVoxelGeometry.NO_NEIGHBOR;
        }
        double height = Mth.lerp(
                partialTick, neighbor.previousPileHeight, neighbor.pileHeight);
        double depression = Mth.lerp(
                partialTick, neighbor.previousDepression, neighbor.depression);
        if (!MudSurfaceVoxelGeometry.rendersAsPile(
                depression, height, visualHeightEpsilon)) {
            return MudSurfaceVoxelGeometry.NO_NEIGHBOR;
        }
        height = visiblePileHeight(height, neighbor.renderedPatch);
        if (current.renderedHit != null) {
            if (neighbor.renderedHit == null
                    || current.renderedHit.normal().dot(neighbor.renderedHit.normal()) < 0.999D) {
                return MudSurfaceVoxelGeometry.NO_NEIGHBOR;
            }
            double dx = (neighbor.pixelX - current.pixelX) * PIXEL;
            double dz = (neighbor.pixelZ - current.pixelZ) * PIXEL;
            double expectedY = current.surfaceY
                    + current.renderedHit.axisX().y * dx
                    + current.renderedHit.axisZ().y * dz;
            return Math.abs(neighbor.surfaceY - expectedY) <= PIXEL * 0.12D
                    ? height : MudSurfaceVoxelGeometry.NO_NEIGHBOR;
        }
        return neighbor.surfaceY - PILE_SURFACE_INSET + height;
    }

    private static double sideNeighbor(MudSideSurfaceEffectManager.SideImprint imprint,
            int u, int v, float partialTick, double visualHeightEpsilon) {
        if (u < 0 || u > 15 || v < 0 || v > 15) {
            return MudSurfaceVoxelGeometry.NO_NEIGHBOR;
        }
        MudSideSurfaceEffectManager.SideCell neighbor = imprint.cells.get(u | v << 4);
        if (neighbor == null) {
            return MudSurfaceVoxelGeometry.NO_NEIGHBOR;
        }
        double height = Mth.lerp(
                partialTick, neighbor.previousPileHeight, neighbor.pileHeight);
        double depression = Mth.lerp(
                partialTick, neighbor.previousDepression, neighbor.depression);
        return MudSurfaceVoxelGeometry.rendersAsPile(
                depression, height, visualHeightEpsilon)
                ? visiblePileHeight(height, neighbor.renderedPatch)
                : MudSurfaceVoxelGeometry.NO_NEIGHBOR;
    }

    static double visiblePileHeight(double height,
            MudRenderedSurfaceGeometry.SurfacePatch patch) {
        if (patch == null || patch.full() || height <= 0.0D) {
            return height;
        }
        double coverage = Mth.clamp(patch.coverage(), 0.0D, 1.0D);
        double scale = Mth.lerp(coverage, MAXIMUM_PARTIAL_HEIGHT_SCALE, 1.0D);
        return height * scale;
    }

    private static double wallStart(
            double currentBase, double currentHeight, double neighborTop) {
        return MudSurfaceVoxelGeometry.visibleWallStart(
                currentBase, currentHeight, neighborTop);
    }

    private static void renderVoxel(PoseStack.Pose pose, VertexConsumer vertices,
            double centerX, double centerY, double centerZ,
            Vec3 axisX, Vec3 axisZ, Vec3 normal,
            double size, double height, double visualHeightEpsilon,
            double negativeZStart, double positiveXStart,
            double positiveZStart, double negativeXStart,
            MudRenderedSurfaceGeometry.SurfacePatch renderedPatch,
            float u0, float v0, float u1, float v1,
            int alpha, int packedLight,
            MudSurfaceAppearance.Appearance appearance) {
        boolean reverseWinding = MudSurfaceVoxelGeometry.reverseWinding(
                axisX, axisZ, normal);
        if (renderedPatch != null && !renderedPatch.full()) {
            renderPartialVoxel(pose, vertices,
                    centerX, centerY, centerZ,
                    axisX, axisZ, normal, Math.min(size, PIXEL),
                    height, visualHeightEpsilon,
                    negativeZStart, positiveXStart,
                    positiveZStart, negativeXStart,
                    renderedPatch, u0, v0, u1, v1,
                    alpha, packedLight, reverseWinding, appearance);
            return;
        }
        renderTop(pose, vertices,
                centerX + normal.x * height,
                centerY + normal.y * height,
                centerZ + normal.z * height,
                axisX, axisZ, normal, size,
                u0, v0, u1, v1, alpha, packedLight,
                reverseWinding, appearance);
        double half = size * 0.5D;
        renderWall(pose, vertices, centerX, centerY, centerZ,
                axisX, axisZ, normal,
                -half, -half, half, -half, negativeZStart, height,
                u0, v0, u1, v1, 224, alpha, packedLight,
                -axisZ.x, -axisZ.y, -axisZ.z,
                visualHeightEpsilon, reverseWinding, appearance);
        renderWall(pose, vertices, centerX, centerY, centerZ,
                axisX, axisZ, normal,
                half, -half, half, half, positiveXStart, height,
                u0, v0, u1, v1, 210, alpha, packedLight,
                axisX.x, axisX.y, axisX.z,
                visualHeightEpsilon, reverseWinding, appearance);
        renderWall(pose, vertices, centerX, centerY, centerZ,
                axisX, axisZ, normal,
                half, half, -half, half, positiveZStart, height,
                u0, v0, u1, v1, 196, alpha, packedLight,
                axisZ.x, axisZ.y, axisZ.z,
                visualHeightEpsilon, reverseWinding, appearance);
        renderWall(pose, vertices, centerX, centerY, centerZ,
                axisX, axisZ, normal,
                -half, half, -half, -half, negativeXStart, height,
                u0, v0, u1, v1, 214, alpha, packedLight,
                -axisX.x, -axisX.y, -axisX.z,
                visualHeightEpsilon, reverseWinding, appearance);
    }

    private static void renderPartialVoxel(PoseStack.Pose pose,
            VertexConsumer vertices,
            double centerX, double centerY, double centerZ,
            Vec3 axisX, Vec3 axisZ, Vec3 normal,
            double size, double height, double visualHeightEpsilon,
            double negativeZStart, double positiveXStart,
            double positiveZStart, double negativeXStart,
            MudRenderedSurfaceGeometry.SurfacePatch renderedPatch,
            float u0, float v0, float u1, float v1,
            int alpha, int packedLight, boolean reverseWinding,
            MudSurfaceAppearance.Appearance appearance) {
        for (MudRenderedSurfaceGeometry.PatchPolygon polygon
                : renderedPatch.polygons()) {
            renderPatchTop(pose, vertices,
                    centerX, centerY, centerZ, axisX, axisZ, normal,
                    size, height, polygon.vertices(), u0, v0, u1, v1,
                    alpha, packedLight, reverseWinding, appearance);
        }
        for (MudRenderedSurfaceGeometry.PatchEdge edge
                : renderedPatch.boundaryEdges()) {
            MudRenderedSurfaceGeometry.PatchVertex first = edge.first();
            MudRenderedSurfaceGeometry.PatchVertex second = edge.second();
            double deltaU = second.u() - first.u();
            double deltaV = second.v() - first.v();
            double edgeLength = Math.sqrt(deltaU * deltaU + deltaV * deltaV);
            if (edgeLength <= 1.0E-6D) {
                continue;
            }
            Vec3 wallNormal = axisX.scale(deltaV / edgeLength)
                    .add(axisZ.scale(-deltaU / edgeLength))
                    .normalize();
            double wallStart = patchWallStart(
                    first, second, negativeZStart, positiveXStart,
                    positiveZStart, negativeXStart);
            float edgeU0 = Mth.lerp(
                    (float) (first.u() + 0.5D), u0, u1);
            float edgeU1 = Mth.lerp(
                    (float) (second.u() + 0.5D), u0, u1);
            renderWall(pose, vertices,
                    centerX, centerY, centerZ, axisX, axisZ, normal,
                    first.u() * size, first.v() * size,
                    second.u() * size, second.v() * size,
                    wallStart, height,
                    edgeU0, v0, edgeU1, v1,
                    214, alpha, packedLight,
                    wallNormal.x, wallNormal.y, wallNormal.z,
                    visualHeightEpsilon, reverseWinding, appearance);
        }
    }

    private static void renderPatchTop(PoseStack.Pose pose,
            VertexConsumer vertices,
            double centerX, double centerY, double centerZ,
            Vec3 axisX, Vec3 axisZ, Vec3 normal,
            double size, double height,
            java.util.List<MudRenderedSurfaceGeometry.PatchVertex> polygon,
            float u0, float v0, float u1, float v1,
            int alpha, int packedLight, boolean reverseWinding,
            MudSurfaceAppearance.Appearance appearance) {
        if (polygon.size() < 3) {
            return;
        }
        MudRenderedSurfaceGeometry.PatchVertex first = polygon.getFirst();
        for (int index = 1; index + 1 < polygon.size(); index++) {
            MudRenderedSurfaceGeometry.PatchVertex second = polygon.get(index);
            MudRenderedSurfaceGeometry.PatchVertex third = polygon.get(index + 1);
            if (reverseWinding) {
                renderPatchTopTriangle(pose, vertices,
                        centerX, centerY, centerZ, axisX, axisZ, normal,
                        size, height, first, third, second,
                        u0, v0, u1, v1, alpha, packedLight, appearance);
            } else {
                renderPatchTopTriangle(pose, vertices,
                        centerX, centerY, centerZ, axisX, axisZ, normal,
                        size, height, first, second, third,
                        u0, v0, u1, v1, alpha, packedLight, appearance);
            }
        }
    }

    private static void renderPatchTopTriangle(PoseStack.Pose pose,
            VertexConsumer vertices,
            double centerX, double centerY, double centerZ,
            Vec3 axisX, Vec3 axisZ, Vec3 normal,
            double size, double height,
            MudRenderedSurfaceGeometry.PatchVertex first,
            MudRenderedSurfaceGeometry.PatchVertex second,
            MudRenderedSurfaceGeometry.PatchVertex third,
            float u0, float v0, float u1, float v1,
            int alpha, int packedLight,
            MudSurfaceAppearance.Appearance appearance) {
        for (MudRenderedSurfaceGeometry.PatchPolygon quad
                : MudRenderedSurfaceGeometry.triangleQuads(first, second, third)) {
            for (MudRenderedSurfaceGeometry.PatchVertex point : quad.vertices()) {
                patchTopVertex(pose, vertices,
                        centerX, centerY, centerZ, axisX, axisZ, normal,
                        size, height, point, u0, v0, u1, v1,
                        alpha, packedLight, appearance);
            }
        }
    }

    private static void patchTopVertex(PoseStack.Pose pose,
            VertexConsumer vertices,
            double centerX, double centerY, double centerZ,
            Vec3 axisX, Vec3 axisZ, Vec3 normal,
            double size, double height,
            MudRenderedSurfaceGeometry.PatchVertex point,
            float u0, float v0, float u1, float v1,
            int alpha, int packedLight,
            MudSurfaceAppearance.Appearance appearance) {
        vertex(pose, vertices,
                centerX, centerY, centerZ, axisX, axisZ, normal,
                point.u() * size, point.v() * size, height,
                Mth.lerp((float) (point.u() + 0.5D), u0, u1),
                Mth.lerp((float) (point.v() + 0.5D), v0, v1),
                255, alpha, packedLight,
                normal.x, normal.y, normal.z, appearance);
    }

    private static double patchWallStart(
            MudRenderedSurfaceGeometry.PatchVertex first,
            MudRenderedSurfaceGeometry.PatchVertex second,
            double negativeZStart, double positiveXStart,
            double positiveZStart, double negativeXStart) {
        double epsilon = 1.0E-5D;
        if (Math.abs(first.v() + 0.5D) <= epsilon
                && Math.abs(second.v() + 0.5D) <= epsilon) {
            return negativeZStart;
        }
        if (Math.abs(first.u() - 0.5D) <= epsilon
                && Math.abs(second.u() - 0.5D) <= epsilon) {
            return positiveXStart;
        }
        if (Math.abs(first.v() - 0.5D) <= epsilon
                && Math.abs(second.v() - 0.5D) <= epsilon) {
            return positiveZStart;
        }
        if (Math.abs(first.u() + 0.5D) <= epsilon
                && Math.abs(second.u() + 0.5D) <= epsilon) {
            return negativeXStart;
        }
        return 0.0D;
    }

    private static void renderTop(PoseStack.Pose pose, VertexConsumer vertices,
            double centerX, double centerY, double centerZ,
            Vec3 axisX, Vec3 axisZ, Vec3 normal, double size,
            float u0, float v0, float u1, float v1,
            int alpha, int packedLight,
            boolean reverseWinding,
            MudSurfaceAppearance.Appearance appearance) {
        double half = size * 0.5D;
        if (reverseWinding) {
            vertex(pose, vertices, centerX, centerY, centerZ,
                    axisX, axisZ, normal, -half, -half, 0.0D,
                    u0, v0, 255, alpha, packedLight, normal.x, normal.y, normal.z,
                    appearance);
            vertex(pose, vertices, centerX, centerY, centerZ,
                    axisX, axisZ, normal, -half, half, 0.0D,
                    u0, v1, 255, alpha, packedLight, normal.x, normal.y, normal.z,
                    appearance);
            vertex(pose, vertices, centerX, centerY, centerZ,
                    axisX, axisZ, normal, half, half, 0.0D,
                    u1, v1, 255, alpha, packedLight, normal.x, normal.y, normal.z,
                    appearance);
            vertex(pose, vertices, centerX, centerY, centerZ,
                    axisX, axisZ, normal, half, -half, 0.0D,
                    u1, v0, 255, alpha, packedLight, normal.x, normal.y, normal.z,
                    appearance);
            return;
        }
        vertex(pose, vertices, centerX, centerY, centerZ,
                axisX, axisZ, normal, -half, -half, 0.0D,
                u0, v0, 255, alpha, packedLight, normal.x, normal.y, normal.z,
                appearance);
        vertex(pose, vertices, centerX, centerY, centerZ,
                axisX, axisZ, normal, half, -half, 0.0D,
                u1, v0, 255, alpha, packedLight, normal.x, normal.y, normal.z,
                appearance);
        vertex(pose, vertices, centerX, centerY, centerZ,
                axisX, axisZ, normal, half, half, 0.0D,
                u1, v1, 255, alpha, packedLight, normal.x, normal.y, normal.z,
                appearance);
        vertex(pose, vertices, centerX, centerY, centerZ,
                axisX, axisZ, normal, -half, half, 0.0D,
                u0, v1, 255, alpha, packedLight, normal.x, normal.y, normal.z,
                appearance);
    }

    private static void renderWall(PoseStack.Pose pose, VertexConsumer vertices,
            double centerX, double centerY, double centerZ,
            Vec3 axisX, Vec3 axisZ, Vec3 normal,
            double ax, double az, double bx, double bz,
            double startHeight, double topHeight,
            float u0, float v0, float u1, float v1,
            int shade, int alpha, int packedLight,
            double normalX, double normalY, double normalZ,
            double visualHeightEpsilon, boolean reverseWinding,
            MudSurfaceAppearance.Appearance appearance) {
        if (!MudSurfaceVoxelGeometry.wallVisible(
                startHeight, topHeight, visualHeightEpsilon)) {
            return;
        }
        double fraction = topHeight <= 1.0E-8D
                ? 0.0D : Mth.clamp(startHeight / topHeight, 0.0D, 1.0D);
        float wallV0 = (float) Mth.lerp(fraction, v0, v1);
        if (reverseWinding) {
            vertex(pose, vertices, centerX, centerY, centerZ,
                    axisX, axisZ, normal, ax, az, startHeight,
                    u0, wallV0, shade, alpha, packedLight, normalX, normalY, normalZ,
                    appearance);
            vertex(pose, vertices, centerX, centerY, centerZ,
                    axisX, axisZ, normal, ax, az, topHeight,
                    u0, v1, shade, alpha, packedLight, normalX, normalY, normalZ,
                    appearance);
            vertex(pose, vertices, centerX, centerY, centerZ,
                    axisX, axisZ, normal, bx, bz, topHeight,
                    u1, v1, shade, alpha, packedLight, normalX, normalY, normalZ,
                    appearance);
            vertex(pose, vertices, centerX, centerY, centerZ,
                    axisX, axisZ, normal, bx, bz, startHeight,
                    u1, wallV0, shade, alpha, packedLight, normalX, normalY, normalZ,
                    appearance);
        } else {
            vertex(pose, vertices, centerX, centerY, centerZ,
                    axisX, axisZ, normal, ax, az, startHeight,
                    u0, wallV0, shade, alpha, packedLight, normalX, normalY, normalZ,
                    appearance);
            vertex(pose, vertices, centerX, centerY, centerZ,
                    axisX, axisZ, normal, bx, bz, startHeight,
                    u1, wallV0, shade, alpha, packedLight, normalX, normalY, normalZ,
                    appearance);
            vertex(pose, vertices, centerX, centerY, centerZ,
                    axisX, axisZ, normal, bx, bz, topHeight,
                    u1, v1, shade, alpha, packedLight, normalX, normalY, normalZ,
                    appearance);
            vertex(pose, vertices, centerX, centerY, centerZ,
                    axisX, axisZ, normal, ax, az, topHeight,
                    u0, v1, shade, alpha, packedLight, normalX, normalY, normalZ,
                    appearance);
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vertices,
            double centerX, double centerY, double centerZ,
            Vec3 axisX, Vec3 axisZ, Vec3 normal,
            double localX, double localZ, double localHeight,
            float u, float v, int shade, int alpha, int packedLight,
            double normalX, double normalY, double normalZ,
            MudSurfaceAppearance.Appearance appearance) {
        double x = centerX + axisX.x * localX + axisZ.x * localZ + normal.x * localHeight;
        double y = centerY + axisX.y * localX + axisZ.y * localZ + normal.y * localHeight;
        double z = centerZ + axisX.z * localX + axisZ.z * localZ + normal.z * localHeight;
        vertices.addVertex(pose, (float) x, (float) y, (float) z)
                .setColor(
                        appearance.shadedRed(shade),
                        appearance.shadedGreen(shade),
                        appearance.shadedBlue(shade),
                        alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose, (float) normalX, (float) normalY, (float) normalZ);
    }
}

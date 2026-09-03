package com.fish.mirebound.client;

import com.fish.mirebound.Mirebound;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Renders exact fixed-angle wand geometry unsupported by vanilla model JSON. */
final class MudTuningWandGeometryRenderer {
    private static final float LEFT_CAGE_HINGE_X = 4.67F;
    private static final float RIGHT_CAGE_HINGE_X = 11.33F;
    private static final float CAGE_HINGE_Y = 23.36F;
    private static final float CAGE_OPEN_ANGLE = 12.0F;
    private static final ResourceLocation WAND_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(
                    Mirebound.MOD_ID, "textures/item/mud_tuning_wand.png");
    private static final FaceUvs POST_UVS = new FaceUvs(
            uv(8, 0, 12, 4), uv(8, 8, 12, 12),
            uv(8, 0, 12, 4), uv(8, 8, 12, 12),
            uv(8, 4, 12, 8), uv(8, 8, 12, 12));
    private static final FaceUvs SHOULDER_UVS = new FaceUvs(
            uv(12, 0, 16, 4), uv(8, 0, 12, 4),
            uv(12, 0, 16, 4), uv(8, 0, 12, 4),
            uv(8, 4, 12, 8), uv(8, 8, 12, 12));
    private static final FaceUvs EMITTER_UVS = new FaceUvs(
            uv(4, 4, 8, 8), uv(4, 4, 8, 8),
            uv(4, 4, 8, 8), uv(4, 4, 8, 8),
            uv(4, 4, 8, 8), uv(0, 4, 4, 8));

    private MudTuningWandGeometryRenderer() {
    }

    static void renderFixedHead(PoseStack poseStack, MultiBufferSource buffers,
            int packedLight, int packedOverlay, float opening) {
        VertexConsumer vertices = buffers.getBuffer(
                RenderType.entityCutoutNoCull(WAND_TEXTURE));
        renderHeadSide(poseStack, vertices, true, opening, packedLight, packedOverlay);
        renderHeadSide(poseStack, vertices, false, opening, packedLight, packedOverlay);
    }

    private static void renderHeadSide(PoseStack poseStack,
            VertexConsumer vertices, boolean left, float opening,
            int packedLight, int packedOverlay) {
        poseStack.pushPose();
        rotateAround(poseStack,
                left ? LEFT_CAGE_HINGE_X : RIGHT_CAGE_HINGE_X,
                CAGE_HINGE_Y, 8.0F,
                (left ? 1.0F : -1.0F) * CAGE_OPEN_ANGLE * opening);
        renderCageSide(poseStack, vertices, left, packedLight, packedOverlay);
        renderShoulder(poseStack, vertices, left, packedLight, packedOverlay);
        poseStack.popPose();
    }

    static void renderCoreCube(PoseStack.Pose pose, VertexConsumer vertices,
            float halfSize, int packedLight, int packedOverlay) {
        float n = -halfSize;
        float p = halfSize;
        quad(pose, vertices,
                n, n, n, n, p, n, p, p, n, p, n, n,
                Uv.FULL, 0.0F, 0.0F, -1.0F, 255,
                packedLight, packedOverlay);
        quad(pose, vertices,
                n, n, p, p, n, p, p, p, p, n, p, p,
                Uv.FULL, 0.0F, 0.0F, 1.0F, 255,
                packedLight, packedOverlay);
        quad(pose, vertices,
                n, n, n, n, n, p, n, p, p, n, p, n,
                Uv.FULL, -1.0F, 0.0F, 0.0F, 255,
                packedLight, packedOverlay);
        quad(pose, vertices,
                p, n, n, p, p, n, p, p, p, p, n, p,
                Uv.FULL, 1.0F, 0.0F, 0.0F, 255,
                packedLight, packedOverlay);
        quad(pose, vertices,
                n, n, n, p, n, n, p, n, p, n, n, p,
                Uv.FULL, 0.0F, -1.0F, 0.0F, 255,
                packedLight, packedOverlay);
        quad(pose, vertices,
                n, p, n, n, p, p, p, p, p, p, p, n,
                Uv.FULL, 0.0F, 1.0F, 0.0F, 255,
                packedLight, packedOverlay);
    }

    private static void renderCageSide(PoseStack poseStack,
            VertexConsumer vertices, boolean left,
            int packedLight, int packedOverlay) {
        poseStack.pushPose();
        rotateAround(poseStack, 8.0F, 19.0F, 8.0F, left ? 8.5F : -8.5F);
        if (left) {
            cuboid(poseStack.last(), vertices,
                    4.5F, 23.8F, 6.3F, 6.2F, 31.2F, 9.7F,
                    POST_UVS, true, packedLight, packedOverlay);
            cuboid(poseStack.last(), vertices,
                    6.2F, 25.0F, 7.0F, 6.55F, 30.15F, 9.0F,
                    EMITTER_UVS, false, packedLight, packedOverlay);
        } else {
            cuboid(poseStack.last(), vertices,
                    9.8F, 23.8F, 6.3F, 11.5F, 31.2F, 9.7F,
                    POST_UVS, true, packedLight, packedOverlay);
            cuboid(poseStack.last(), vertices,
                    9.45F, 25.0F, 7.0F, 9.8F, 30.15F, 9.0F,
                    EMITTER_UVS, false, packedLight, packedOverlay);
        }
        poseStack.popPose();
    }

    private static void renderShoulder(PoseStack poseStack,
            VertexConsumer vertices, boolean left,
            int packedLight, int packedOverlay) {
        poseStack.pushPose();
        if (left) {
            rotateAround(poseStack, 4.15F, 30.8F, 8.0F, 40.0F);
            cuboid(poseStack.last(), vertices,
                    3.05F, 29.9F, 6.0F, 6.45F, 32.0F, 10.0F,
                    SHOULDER_UVS, true, packedLight, packedOverlay);
        } else {
            rotateAround(poseStack, 11.85F, 30.8F, 8.0F, -40.0F);
            cuboid(poseStack.last(), vertices,
                    9.55F, 29.9F, 6.0F, 12.95F, 32.0F, 10.0F,
                    SHOULDER_UVS, true, packedLight, packedOverlay);
        }
        poseStack.popPose();
    }

    private static void rotateAround(PoseStack poseStack,
            float pivotX, float pivotY, float pivotZ, float angle) {
        poseStack.translate(pivotX / 16.0F, pivotY / 16.0F, pivotZ / 16.0F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(angle));
        poseStack.translate(-pivotX / 16.0F, -pivotY / 16.0F, -pivotZ / 16.0F);
    }

    private static void cuboid(PoseStack.Pose pose, VertexConsumer vertices,
            float minX, float minY, float minZ,
            float maxX, float maxY, float maxZ,
            FaceUvs uv, boolean shade, int packedLight, int packedOverlay) {
        float x0 = minX / 16.0F;
        float y0 = minY / 16.0F;
        float z0 = minZ / 16.0F;
        float x1 = maxX / 16.0F;
        float y1 = maxY / 16.0F;
        float z1 = maxZ / 16.0F;
        quad(pose, vertices, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0,
                uv.north, 0, 0, -1, shade ? 204 : 255, packedLight, packedOverlay);
        quad(pose, vertices, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1,
                uv.south, 0, 0, 1, shade ? 204 : 255, packedLight, packedOverlay);
        quad(pose, vertices, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0,
                uv.west, -1, 0, 0, shade ? 153 : 255, packedLight, packedOverlay);
        quad(pose, vertices, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1,
                uv.east, 1, 0, 0, shade ? 153 : 255, packedLight, packedOverlay);
        quad(pose, vertices, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1,
                uv.down, 0, -1, 0, shade ? 128 : 255, packedLight, packedOverlay);
        quad(pose, vertices, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0,
                uv.up, 0, 1, 0, 255, packedLight, packedOverlay);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer vertices,
            float ax, float ay, float az, float bx, float by, float bz,
            float cx, float cy, float cz, float dx, float dy, float dz,
            Uv uv, float normalX, float normalY, float normalZ, int shade,
            int packedLight, int packedOverlay) {
        vertex(pose, vertices, ax, ay, az, uv.u0, uv.v1,
                normalX, normalY, normalZ, shade, packedLight, packedOverlay);
        vertex(pose, vertices, bx, by, bz, uv.u0, uv.v0,
                normalX, normalY, normalZ, shade, packedLight, packedOverlay);
        vertex(pose, vertices, cx, cy, cz, uv.u1, uv.v0,
                normalX, normalY, normalZ, shade, packedLight, packedOverlay);
        vertex(pose, vertices, dx, dy, dz, uv.u1, uv.v1,
                normalX, normalY, normalZ, shade, packedLight, packedOverlay);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vertices,
            float x, float y, float z, float u, float v,
            float normalX, float normalY, float normalZ, int shade,
            int packedLight, int packedOverlay) {
        vertices.addVertex(pose, x, y, z)
                .setColor(shade, shade, shade, 255)
                .setUv(u, v)
                .setOverlay(packedOverlay == 0
                        ? OverlayTexture.NO_OVERLAY : packedOverlay)
                .setLight(packedLight)
                .setNormal(pose, normalX, normalY, normalZ);
    }

    private static Uv uv(float u0, float v0, float u1, float v1) {
        return new Uv(u0 / 16.0F, v0 / 16.0F, u1 / 16.0F, v1 / 16.0F);
    }

    private record FaceUvs(Uv north, Uv east, Uv south,
            Uv west, Uv up, Uv down) {
    }

    private record Uv(float u0, float v0, float u1, float v1) {
        private static final Uv FULL = new Uv(0.0F, 0.0F, 1.0F, 1.0F);
    }
}

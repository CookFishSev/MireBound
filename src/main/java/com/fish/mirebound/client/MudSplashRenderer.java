package com.fish.mirebound.client;

import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.client.config.MireboundClientSettings;
import com.fish.mirebound.client.config.MireboundClientSettings.ClientOption;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Batched crossed mud ribbons that preserve a blocky style without particle entities. */
final class MudSplashRenderer {
    private static final int MAXIMUM_COLUMN_SEGMENTS = 28;
    private static final double MAXIMUM_COLUMN_TRAIL_LENGTH = 1.75D;

    private MudSplashRenderer() {
    }

    static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES
                || !MireboundClientSettings.clientOptionEnabled(
                        ClientOption.SPLASH_EFFECTS)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        PoseStack poseStack = event.getPoseStack();
        Set<ResourceLocation> used = new HashSet<>();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        PoseStack.Pose pose = poseStack.last();
        for (MudSplashClientManager.Droplet droplet : MudSplashClientManager.droplets()) {
            if (!droplet.active) {
                continue;
            }
            Vec3 center = droplet.previousPosition.lerp(droplet.position, partialTick);
            if (center.distanceToSqr(camera) > 16384.0D) {
                continue;
            }
            AABB bounds = new AABB(center, center)
                    .inflate(droplet.columnActive() ? 1.18D : 0.36D);
            if (event.getFrustum() != null && !event.getFrustum().isVisible(bounds)) {
                continue;
            }
            MudSurfaceAppearance.Appearance appearance = MudSurfaceAppearance.resolve(
                    minecraft.level, droplet.visualSource,
                    droplet.medium.skinCoverageTexture());
            renderDroplet(minecraft, pose, buffers, droplet, center, camera,
                    appearance);
            used.add(appearance.texture());
        }
        poseStack.popPose();
        for (ResourceLocation texture : used) {
            buffers.endBatch(RenderType.entityTranslucent(texture));
        }
    }

    private static void renderDroplet(Minecraft minecraft, PoseStack.Pose pose,
            MultiBufferSource.BufferSource buffers,
            MudSplashClientManager.Droplet droplet, Vec3 center, Vec3 camera,
            MudSurfaceAppearance.Appearance appearance) {
        double speed = droplet.velocity.length();
        int light = LevelRenderer.getLightColor(minecraft.level, BlockPos.containing(center));
        int alpha = droplet.medium == SinkingMedium.LIVING_SLIME ? 154
                : droplet.medium.opaqueCoverage() ? 238 : 196;
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityTranslucent(
                appearance.texture()));
        if (droplet.fountain) {
            renderFountainDroplet(
                    pose, vertices, droplet, center, camera, speed,
                    appearance, alpha, light);
            return;
        }
        Vec3 tangent = speed > 1.0E-5D
                ? droplet.velocity.scale(1.0D / speed) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 side = tangent.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (side.lengthSqr() < 1.0E-5D) {
            side = tangent.cross(new Vec3(1.0D, 0.0D, 0.0D));
        }
        side = side.normalize();
        Vec3 binormal = tangent.cross(side).normalize();
        double length = droplet.size * Mth.clamp(1.25D + speed * 2.2D, 1.25D, 4.0D);
        double width = droplet.size * Mth.clamp(1.05D - speed * 0.22D, 0.58D, 1.0D);
        Vec3 along = tangent.scale(length * 0.5D);
        MudTextureUv.Region texture = MudTextureUv.sample(droplet.seed, 5);
        renderDoubleSidedRibbon(pose, vertices, center, along, side.scale(width * 0.5D),
                binormal, appearance.u(texture.u0()), appearance.v(texture.v0()),
                appearance.u(texture.u1()), appearance.v(texture.v1()),
                appearance, alpha, light);
        renderDoubleSidedRibbon(pose, vertices, center, along, binormal.scale(width * 0.5D),
                side, appearance.u(texture.u0()), appearance.v(texture.v0()),
                appearance.u(texture.u1()), appearance.v(texture.v1()),
                appearance, alpha, light);
    }

    private static void renderFountainDroplet(PoseStack.Pose pose, VertexConsumer vertices,
            MudSplashClientManager.Droplet droplet, Vec3 center, Vec3 camera,
            double speed, MudSurfaceAppearance.Appearance appearance,
            int alpha, int light) {
        double requestedTrailLength = droplet.columnActive()
                ? Math.min(MAXIMUM_COLUMN_TRAIL_LENGTH,
                        speed * Math.max(1, droplet.columnTrailTicks) * 0.94D
                                * droplet.columnTrailFactor())
                : 0.0D;
        double trailLength = droplet.trail.availableLength(center, requestedTrailLength);
        double spacing = Math.max(droplet.size * 0.82D, 0.032D);
        int segmentCount = Mth.clamp(
                1 + Mth.ceil(trailLength / spacing), 1, MAXIMUM_COLUMN_SEGMENTS);
        double usedTrail = segmentCount <= 1 ? 0.0D : trailLength;

        Vec3 facing = camera.subtract(center);
        if (facing.lengthSqr() < 1.0E-8D) {
            facing = new Vec3(0.0D, 0.0D, 1.0D);
        } else {
            facing = facing.normalize();
        }
        Vec3 right = new Vec3(0.0D, 1.0D, 0.0D).cross(facing);
        if (right.lengthSqr() < 1.0E-8D) {
            right = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            right = right.normalize();
        }
        Vec3 up = facing.cross(right).normalize();

        for (int index = 0; index < segmentCount; index++) {
            double distance = segmentCount <= 1
                    ? 0.0D : usedTrail * index / (segmentCount - 1.0D);
            long segmentSeed = droplet.seed + index * 0x9E3779B97F4A7C15L;
            double scaleVariation = 0.88D
                    + ((segmentSeed >>> 25) & 0xFFL) / 255.0D * 0.24D;
            Vec3 segmentCenter = droplet.trail.sampleBack(center, distance);
            double width = droplet.size * 1.62D * scaleVariation;
            double height = width * (1.08D
                    + ((segmentSeed >>> 41) & 0x7FL) / 127.0D * 0.18D);
            int segmentAlpha = Mth.clamp((int) Math.round(alpha
                    * (1.0D - index / (double) Math.max(1, segmentCount) * 0.18D)),
                    0, 255);
            MudTextureUv.Region texture = MudTextureUv.sample(segmentSeed, 4);
            renderBillboardSprite(pose, vertices, segmentCenter, right, up, facing,
                    width, height, texture, appearance,
                    segmentAlpha, light);
        }
    }

    private static void renderBillboardSprite(PoseStack.Pose pose, VertexConsumer vertices,
            Vec3 center, Vec3 right, Vec3 up, Vec3 normal,
            double width, double height, MudTextureUv.Region texture,
            MudSurfaceAppearance.Appearance appearance, int alpha, int light) {
        double halfWidth = width * 0.5D;
        double halfHeight = height * 0.5D;
        Vec3 horizontal = right.scale(halfWidth);
        Vec3 vertical = up.scale(halfHeight);
        Vec3 a = center.subtract(horizontal).subtract(vertical);
        Vec3 b = center.add(horizontal).subtract(vertical);
        Vec3 c = center.add(horizontal).add(vertical);
        Vec3 d = center.subtract(horizontal).add(vertical);
        quad(pose, vertices, a, b, c, d, normal,
                appearance.u(texture.u0()), appearance.v(texture.v0()),
                appearance.u(texture.u1()), appearance.v(texture.v1()),
                appearance, alpha, light);
    }

    private static void renderDoubleSidedRibbon(PoseStack.Pose pose, VertexConsumer vertices,
            Vec3 center, Vec3 along, Vec3 halfWidth, Vec3 normal,
            float u0, float v0, float u1, float v1,
            MudSurfaceAppearance.Appearance appearance,
            int alpha, int light) {
        Vec3 a = center.subtract(along).subtract(halfWidth);
        Vec3 b = center.add(along).subtract(halfWidth);
        Vec3 c = center.add(along).add(halfWidth);
        Vec3 d = center.subtract(along).add(halfWidth);
        quad(pose, vertices, a, b, c, d, normal,
                u0, v0, u1, v1, appearance, alpha, light);
        quad(pose, vertices, d, c, b, a, normal.scale(-1.0D),
                u0, v0, u1, v1, appearance, alpha, light);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer vertices,
            Vec3 a, Vec3 b, Vec3 c, Vec3 d, Vec3 normal,
            float u0, float v0, float u1, float v1,
            MudSurfaceAppearance.Appearance appearance,
            int alpha, int light) {
        vertex(pose, vertices, a, u0, v0, normal, appearance, alpha, light);
        vertex(pose, vertices, b, u1, v0, normal, appearance, alpha, light);
        vertex(pose, vertices, c, u1, v1, normal, appearance, alpha, light);
        vertex(pose, vertices, d, u0, v1, normal, appearance, alpha, light);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vertices,
            Vec3 point, float u, float v, Vec3 normal,
            MudSurfaceAppearance.Appearance appearance,
            int alpha, int light) {
        vertices.addVertex(pose, (float) point.x, (float) point.y, (float) point.z)
                .setColor(appearance.red(), appearance.green(),
                        appearance.blue(), alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }
}

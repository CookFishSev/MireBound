package com.fish.mirebound.client;

import com.fish.mirebound.mud.SinkingMedium;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Renders one fixed four-jaw mesh per restrained player without entities or dynamic textures. */
final class ClientSculkClampRenderer {
    private ClientSculkClampRenderer() {
    }

    static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        List<ClientSculkClampManager.View> views = ClientSculkClampManager.views(partialTick);
        if (views.isEmpty()) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        Set<RenderType> usedRenderTypes = new LinkedHashSet<>();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        for (ClientSculkClampManager.View view : views) {
            double reach = view.radius() + view.height() + 0.35D;
            if (view.point().distanceToSqr(camera) > view.renderDistance() * view.renderDistance()
                    || event.getFrustum() != null
                    && !event.getFrustum().isVisible(new AABB(view.point(), view.point()).inflate(reach))) {
                continue;
            }
            MudSurfaceAppearance.Appearance appearance = MudSurfaceAppearance.resolve(
                    minecraft.level, view.visualSource(), SinkingMedium.SCULK_MIRE.coverTexture());
            RenderType renderType = appearance.baseOpacity() < 0.995F
                    ? RenderType.entityTranslucent(appearance.texture())
                    : RenderType.entityCutoutNoCull(appearance.texture());
            usedRenderTypes.add(renderType);
            renderClamp(minecraft, poseStack.last(), buffers.getBuffer(renderType), view, appearance);
        }
        poseStack.popPose();
        usedRenderTypes.forEach(buffers::endBatch);
    }

    private static void renderClamp(Minecraft minecraft, PoseStack.Pose pose,
            VertexConsumer vertices, ClientSculkClampManager.View view,
            MudSurfaceAppearance.Appearance appearance) {
        double emergence = view.emergence();
        double grip = view.grip();
        int light = LevelRenderer.getLightColor(minecraft.level, BlockPos.containing(view.point()));
        for (int index = 0; index < 4; index++) {
            double sx = index == 0 || index == 3 ? 1.0D : -1.0D;
            double sz = index < 2 ? 1.0D : -1.0D;
            Vec3 direction = view.axisX().scale(sx).add(view.axisZ().scale(sz)).normalize();
            Vec3 side = view.normal().cross(direction).normalize();
            double emergeDelay = index * 0.035D;
            double jawProgress = smoother(Mth.clamp(
                    (emergence - emergeDelay) / (1.0D - emergeDelay), 0.0D, 1.0D));
            double gripDelay = index * 0.045D;
            double jawGrip = smoother(Mth.clamp(
                    (grip - gripDelay) / (1.0D - gripDelay), 0.0D, 1.0D));
            Vec3 base = view.point().add(direction.scale(view.radius() * 1.10D))
                    .add(view.normal().scale(-view.height() * (1.0D - jawProgress) - 0.025D));
            double tipRadius = Mth.lerp(jawGrip,
                    view.radius() * 0.78D, view.radius() * 0.28D);
            Vec3 tip = view.point().add(direction.scale(tipRadius))
                    .add(view.normal().scale(view.height() * jawProgress));
            double baseHalfWidth = view.radius() * 0.22D;
            double tipHalfWidth = view.radius() * 0.105D;
            double baseHalfThickness = Math.max(0.035D, view.radius() * 0.105D);
            double tipHalfThickness = Math.max(0.022D, view.radius() * 0.055D);
            renderTaperedPrism(pose, vertices, base, tip, side, view.normal(),
                    baseHalfWidth, tipHalfWidth, baseHalfThickness, tipHalfThickness,
                    light, appearance);
        }
    }

    private static void renderTaperedPrism(PoseStack.Pose pose, VertexConsumer vertices,
            Vec3 base, Vec3 tip, Vec3 side, Vec3 up,
            double baseWidth, double tipWidth, double baseThickness, double tipThickness,
            int packedLight, MudSurfaceAppearance.Appearance appearance) {
        Vec3[] b = ring(base, side, up, baseWidth, baseThickness);
        Vec3[] t = ring(tip, side, up, tipWidth, tipThickness);
        for (int face = 0; face < 4; face++) {
            int next = (face + 1) & 3;
            quad(pose, vertices, b[face], b[next], t[next], t[face], packedLight, appearance);
        }
        quad(pose, vertices, b[3], b[2], b[1], b[0], packedLight, appearance);
        quad(pose, vertices, t[0], t[1], t[2], t[3], packedLight, appearance);
    }

    private static Vec3[] ring(Vec3 center, Vec3 side, Vec3 up,
            double halfWidth, double halfThickness) {
        Vec3 across = side.scale(halfWidth);
        Vec3 vertical = up.scale(halfThickness);
        return new Vec3[] {
                center.add(across).add(vertical),
                center.subtract(across).add(vertical),
                center.subtract(across).subtract(vertical),
                center.add(across).subtract(vertical)
        };
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer vertices,
            Vec3 a, Vec3 b, Vec3 c, Vec3 d, int packedLight,
            MudSurfaceAppearance.Appearance appearance) {
        Vec3 normal = b.subtract(a).cross(d.subtract(a));
        normal = normal.lengthSqr() < 1.0E-8D ? new Vec3(0.0D, 1.0D, 0.0D) : normal.normalize();
        vertex(pose, vertices, a, 0.0F, 0.0F, normal, packedLight, appearance);
        vertex(pose, vertices, b, 1.0F, 0.0F, normal, packedLight, appearance);
        vertex(pose, vertices, c, 1.0F, 1.0F, normal, packedLight, appearance);
        vertex(pose, vertices, d, 0.0F, 1.0F, normal, packedLight, appearance);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vertices, Vec3 point,
            float u, float v, Vec3 normal, int packedLight,
            MudSurfaceAppearance.Appearance appearance) {
        vertices.addVertex(pose, (float) point.x, (float) point.y, (float) point.z)
                .setColor(appearance.red(), appearance.green(), appearance.blue(), 255)
                .setUv(Mth.lerp(u, appearance.minimumU(), appearance.maximumU()),
                        Mth.lerp(v, appearance.minimumV(), appearance.maximumV()))
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static double smoother(double value) {
        double clamped = Mth.clamp(value, 0.0D, 1.0D);
        return clamped * clamped * clamped
                * (clamped * (clamped * 6.0D - 15.0D) + 10.0D);
    }
}

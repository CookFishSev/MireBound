package com.fish.mirebound.client;

import com.fish.mirebound.client.compat.ClientRenderCompat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Captures custom equipment vertices when the local player is not visibly rendered,
 * such as in first person. The render target is a no-op, so this only feeds contact data.
 */
final class ArmorMudProxyRenderer {
    private static final int CAPTURE_INTERVAL_TICKS = 2;
    private static final MultiBufferSource NOOP_BUFFERS = renderType ->
            SodiumGeometryCaptureBridge.noopConsumer(NoopVertexConsumer.INSTANCE);
    private static int lastCaptureTick = Integer.MIN_VALUE;
    private static boolean capturePass;

    private ArmorMudProxyRenderer() {
    }

    static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        if (ClientRenderCompat.isRenderingShaderShadowPass()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        boolean debugGeometryCapture = ClientMudDebugOptions.contactGeometry()
                && AnimatedPlayerGeometryCapture.needsLocalOffscreenCapture(player);
        if (player == null || minecraft.level == null
                || (!minecraft.options.getCameraType().isFirstPerson()
                        && !player.isInvisible() && !debugGeometryCapture)
                || lastCaptureTick != Integer.MIN_VALUE
                        && player.tickCount - lastCaptureTick < CAPTURE_INTERVAL_TICKS
                || !ArmorVertexContactCapture.needsLocalOffscreenCapture(player)
                        && !debugGeometryCapture
                        && !AnimatedPlayerGeometryCapture.needsLocalOffscreenCapture(player)) {
            return;
        }

        lastCaptureTick = player.tickCount;
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cameraPosition = event.getCamera().getPosition();
        double x = Mth.lerp(partialTick, player.xOld, player.getX()) - cameraPosition.x;
        double y = Mth.lerp(partialTick, player.yOld, player.getY()) - cameraPosition.y;
        double z = Mth.lerp(partialTick, player.zOld, player.getZ()) - cameraPosition.z;
        int packedLight = minecraft.getEntityRenderDispatcher().getPackedLightCoords(player, partialTick);
        boolean wasInvisible = player.isInvisible();
        CameraType previousCameraType = minecraft.options.getCameraType();
        try {
            capturePass = true;
            if (previousCameraType.isFirstPerson()) {
                minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
            if (wasInvisible) {
                player.setInvisible(false);
            }
            minecraft.getEntityRenderDispatcher().render(player, x, y, z,
                    Mth.lerp(partialTick, player.yRotO, player.getYRot()), partialTick,
                    new PoseStack(), NOOP_BUFFERS, packedLight);
        } catch (RuntimeException ignored) {
            // A third-party renderer must not be able to break the world render pass.
        } finally {
            if (wasInvisible) {
                player.setInvisible(true);
            }
            if (minecraft.options.getCameraType() != previousCameraType) {
                minecraft.options.setCameraType(previousCameraType);
            }
            capturePass = false;
        }
    }

    static boolean isCapturePass() {
        return capturePass;
    }

    static void reset() {
        lastCaptureTick = Integer.MIN_VALUE;
        capturePass = false;
        AnimatedPlayerGeometryCapture.reset();
    }

    private enum NoopVertexConsumer implements VertexConsumer, NoopGeometryVertexSink {
        INSTANCE;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }
    }
}

package com.fish.mirebound.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.Pose;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Owns rendering of sealed players so the live entity is never the visible statue. */
public final class AssimilationFrozenBodyProxy {
    private static final ThreadLocal<Integer> ACTIVE_PROXY = new ThreadLocal<>();

    private AssimilationFrozenBodyProxy() {
    }

    public static boolean suppressesOriginal(int entityId) {
        ClientAssimilationState.View view = ClientAssimilationState.view(entityId);
        return view != null && view.stage().frozen() && !isProxyPass(entityId);
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cameraPosition = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        boolean rendered = false;
        for (AbstractClientPlayer player : minecraft.level.players()) {
            ClientAssimilationState.View view = ClientAssimilationState.view(player.getId());
            if (view == null || !view.stage().frozen()
                    || !isVisible(event, minecraft, player, view, cameraPosition)) {
                continue;
            }
            rendered |= renderOne(minecraft, player, view, partialTick, cameraPosition, buffers);
        }
        if (rendered) {
            buffers.endBatch();
        }
    }

    private static boolean isProxyPass(int entityId) {
        Integer active = ACTIVE_PROXY.get();
        return active != null && active == entityId;
    }

    static void reset() {
        ACTIVE_PROXY.remove();
    }

    private static boolean isVisible(RenderLevelStageEvent event, Minecraft minecraft,
            AbstractClientPlayer player, ClientAssimilationState.View view,
            Vec3 cameraPosition) {
        Vec3 anchor = view.anchor();
        if (!Double.isFinite(anchor.x) || !Double.isFinite(anchor.y)
                || !Double.isFinite(anchor.z)) {
            return false;
        }
        double maximumDistance = minecraft.options.renderDistance().get() * 16.0D + 32.0D;
        if (cameraPosition.distanceToSqr(anchor) > maximumDistance * maximumDistance) {
            return false;
        }
        double playerWidth = player.getDimensions(Pose.STANDING).width();
        double playerHeight = player.getDimensions(Pose.STANDING).height();
        double halfWidth = Math.max(0.35D, playerWidth * 0.5D);
        double maximumReach = Math.max(halfWidth, playerHeight * 0.5D) + 0.5D;
        AABB bounds = new AABB(
                anchor.x - maximumReach, anchor.y - 0.25D, anchor.z - maximumReach,
                anchor.x + maximumReach, anchor.y + playerHeight + 0.5D,
                anchor.z + maximumReach).inflate(0.35D);
        return event.getFrustum().isVisible(bounds);
    }

    private static boolean renderOne(Minecraft minecraft, AbstractClientPlayer player,
            ClientAssimilationState.View view, float partialTick, Vec3 cameraPosition,
            MultiBufferSource.BufferSource buffers) {
        Vec3 anchor = view.renderAnchor(partialTick);
        double x = anchor.x - cameraPosition.x;
        double y = anchor.y - cameraPosition.y;
        double z = anchor.z - cameraPosition.z;
        int packedLight = minecraft.getEntityRenderDispatcher()
                .getPackedLightCoords(player, partialTick);

        FrozenEntityState saved = FrozenEntityState.capture(player);
        CameraType savedCamera = minecraft.options.getCameraType();
        boolean changedCamera = player == minecraft.player && savedCamera.isFirstPerson();
        try {
            ACTIVE_PROXY.set(player.getId());
            saved.applyFrozen(player, view);
            if (changedCamera) {
                minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
            PoseStack poseStack = new PoseStack();
            poseStack.translate(x, y, z);
            double playerHeight = player.getDimensions(Pose.STANDING).height();
            double pivot = Math.max(0.5D, playerHeight * 0.5D);
            poseStack.translate(0.0D, pivot, 0.0D);
            poseStack.mulPose(Axis.XP.rotationDegrees(view.renderBodyPitch(partialTick)));
            poseStack.mulPose(Axis.ZP.rotationDegrees(view.renderBodyRoll(partialTick)));
            poseStack.translate(0.0D, -pivot, 0.0D);
            minecraft.getEntityRenderDispatcher().render(
                    player, 0.0D, 0.0D, 0.0D, view.frozenYaw(), partialTick,
                    poseStack, buffers, packedLight);
            return true;
        } catch (RuntimeException ignored) {
            // Optional player renderers must not be able to break the level render pass.
            return false;
        } finally {
            if (changedCamera) {
                minecraft.options.setCameraType(savedCamera);
            }
            saved.restore(player);
            ACTIVE_PROXY.remove();
        }
    }

    private record FrozenEntityState(
            float yaw, float previousYaw,
            float pitch, float previousPitch,
            float bodyYaw, float previousBodyYaw,
            float headYaw, float previousHeadYaw,
            boolean invisible) {
        static FrozenEntityState capture(AbstractClientPlayer player) {
            return new FrozenEntityState(
                    player.getYRot(), player.yRotO,
                    player.getXRot(), player.xRotO,
                    player.yBodyRot, player.yBodyRotO,
                    player.yHeadRot, player.yHeadRotO,
                    player.isInvisible());
        }

        void applyFrozen(AbstractClientPlayer player, ClientAssimilationState.View view) {
            player.setYRot(view.frozenYaw());
            player.yRotO = view.frozenYaw();
            player.setXRot(view.frozenPitch());
            player.xRotO = view.frozenPitch();
            player.yBodyRot = view.frozenYaw();
            player.yBodyRotO = view.frozenYaw();
            player.yHeadRot = view.frozenYaw();
            player.yHeadRotO = view.frozenYaw();
            if (invisible) {
                player.setInvisible(false);
            }
        }

        void restore(AbstractClientPlayer player) {
            player.setYRot(yaw);
            player.yRotO = previousYaw;
            player.setXRot(pitch);
            player.xRotO = previousPitch;
            player.yBodyRot = bodyYaw;
            player.yBodyRotO = previousBodyYaw;
            player.yHeadRot = headYaw;
            player.yHeadRotO = previousHeadYaw;
            player.setInvisible(invisible);
        }
    }
}

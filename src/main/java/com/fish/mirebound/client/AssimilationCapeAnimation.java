package com.fish.mirebound.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.player.AbstractClientPlayer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Freezes the cape layer's model-local inertia transform without pinning it to the camera. */
public final class AssimilationCapeAnimation {
    private static final ThreadLocal<RenderContext> CONTEXT = new ThreadLocal<>();
    private static final Map<Integer, CapeTransform> LAST_TRANSFORMS = new ConcurrentHashMap<>();
    private static final Map<Integer, CapeTransform> FROZEN_TRANSFORMS = new ConcurrentHashMap<>();

    private AssimilationCapeAnimation() {
    }

    public static void begin(AbstractClientPlayer player, PoseStack poseStack) {
        ClientAssimilationState.View view = ClientAssimilationState.view(player.getId());
        if (view == null || view.progress() <= 0.0001F) {
            return;
        }
        CONTEXT.set(new RenderContext(player.getId(),
                new Matrix4f(poseStack.last().pose()),
                new Matrix3f(poseStack.last().normal())));
    }

    public static void prepareCloak(AbstractClientPlayer player, PoseStack poseStack) {
        RenderContext context = CONTEXT.get();
        if (context == null || context.entityId != player.getId()) {
            return;
        }
        ClientAssimilationState.View view = ClientAssimilationState.view(player.getId());
        if (view == null) {
            return;
        }
        if (!view.stage().frozen()) {
            LAST_TRANSFORMS.put(player.getId(), CapeTransform.capture(context, poseStack));
            return;
        }

        CapeTransform frozen = FROZEN_TRANSFORMS.get(player.getId());
        if (frozen == null) {
            frozen = LAST_TRANSFORMS.get(player.getId());
            if (frozen == null) {
                frozen = CapeTransform.capture(context, poseStack);
            }
            FROZEN_TRANSFORMS.put(player.getId(), frozen);
        }
        frozen.apply(context, poseStack);
    }

    public static void end(AbstractClientPlayer player) {
        RenderContext context = CONTEXT.get();
        if (context == null || context.entityId != player.getId()) {
            return;
        }
        CONTEXT.remove();
    }

    static void clearEntity(int entityId) {
        LAST_TRANSFORMS.remove(entityId);
        FROZEN_TRANSFORMS.remove(entityId);
    }

    static void reset() {
        LAST_TRANSFORMS.clear();
        FROZEN_TRANSFORMS.clear();
        CONTEXT.remove();
    }

    private record RenderContext(int entityId, Matrix4f basePose, Matrix3f baseNormal) {
    }

    private record CapeTransform(Matrix4f localPose, Matrix3f localNormal) {
        static CapeTransform capture(RenderContext context, PoseStack poseStack) {
            Matrix4f localPose = new Matrix4f(context.basePose).invert()
                    .mul(poseStack.last().pose());
            Matrix3f localNormal = new Matrix3f(context.baseNormal).invert()
                    .mul(poseStack.last().normal());
            return new CapeTransform(localPose, localNormal);
        }

        void apply(RenderContext context, PoseStack poseStack) {
            poseStack.last().pose().set(context.basePose).mul(localPose);
            poseStack.last().normal().set(context.baseNormal).mul(localNormal);
        }
    }
}

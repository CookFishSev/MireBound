package com.fish.mirebound.client;

import com.fish.mirebound.mixin.client.mud.PlayerModelGeometryAccessor;
import com.fish.mirebound.mixin.client.tentacle.WalkAnimationStateAccessor;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.WalkAnimationState;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/** Slows ordinary animation and locks the visible body pose after stasis. */
public final class AssimilationPlayerAnimation {
    private static final ThreadLocal<Deque<SavedWalk>> STACK =
            ThreadLocal.withInitial(ArrayDeque::new);
    private static final Map<Integer, Map<PlayerModel<?>, ModelPose>> LAST_RENDERED_POSES =
            new ConcurrentHashMap<>();
    private static final Map<Integer, Map<PlayerModel<?>, ModelPose>> FROZEN_POSES =
            new ConcurrentHashMap<>();
    private static final Map<Integer, Map<ModelPart, PartTransform>> LAST_DETACHED_PARTS =
            new ConcurrentHashMap<>();
    private static final Map<Integer, Map<ModelPart, PartTransform>> FROZEN_DETACHED_PARTS =
            new ConcurrentHashMap<>();
    private static final Map<Integer, AnimationClock> ANIMATION_CLOCKS = new ConcurrentHashMap<>();

    private AssimilationPlayerAnimation() {
    }

    static void onPre(RenderPlayerEvent.Pre event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer player)) {
            return;
        }
        ClientAssimilationState.View view = ClientAssimilationState.view(player.getId());
        if (view == null || view.progress() <= 0.0001F) {
            return;
        }
        WalkAnimationStateAccessor accessor = (WalkAnimationStateAccessor) player.walkAnimation;
        SavedWalk saved = new SavedWalk(player.getId(), accessor.mirebound$getSpeedOld(),
                accessor.mirebound$getSpeed(), accessor.mirebound$getPosition());
        STACK.get().push(saved);
        if (view.stage().frozen()) {
            accessor.mirebound$setSpeedOld(view.frozenWalkSpeed());
            accessor.mirebound$setSpeed(view.frozenWalkSpeed());
            accessor.mirebound$setPosition(view.frozenWalkPosition());
        } else {
            float scale = view.profile().animationScale(view.progress());
            accessor.mirebound$setSpeedOld(saved.speedOld * scale);
            accessor.mirebound$setSpeed(saved.speed * scale);
        }
    }

    /** Called after vanilla and compatibility renderers have produced the final model pose. */
    public static void applyAfterSetup(AbstractClientPlayer player,
            PlayerModel<AbstractClientPlayer> model) {
        Deque<SavedWalk> stack = STACK.get();
        if (stack.isEmpty() || stack.peek().entityId != player.getId()) {
            return;
        }
        ClientAssimilationState.View view = ClientAssimilationState.view(player.getId());
        if (view == null || !view.stage().frozen()) {
            return;
        }
        SavedWalk frame = stack.peek();
        if (frame.savedPose == null) {
            frame.savedPose = ModelPose.capture(model);
        }
        Map<PlayerModel<?>, ModelPose> frozenByModel = FROZEN_POSES.computeIfAbsent(
                player.getId(), ignored -> new IdentityHashMap<>());
        ModelPose frozen = frozenByModel.get(model);
        if (frozen == null) {
            Map<PlayerModel<?>, ModelPose> lastByModel = LAST_RENDERED_POSES.get(player.getId());
            frozen = lastByModel == null ? null : lastByModel.get(model);
            if (frozen == null) {
                frozen = ModelPose.capture(model);
            }
            frozenByModel.put(model, frozen);
        }
        frame.frozenPose = frozen;
        frozen.apply();
    }

    /** Restores one EMF/Fresh Moves part after its late animation pass and before geometry submission. */
    public static void applyFrozenTransform(ModelPart part) {
        Deque<SavedWalk> stack = STACK.get();
        if (stack.isEmpty()) {
            return;
        }
        SavedWalk frame = stack.peek();
        ClientAssimilationState.View view = ClientAssimilationState.view(frame.entityId);
        if (view == null) {
            return;
        }
        if (!view.stage().frozen()) {
            LAST_DETACHED_PARTS.computeIfAbsent(frame.entityId,
                    ignored -> new IdentityHashMap<>()).put(part, PartTransform.capture(part));
            return;
        }
        if (frame.frozenPose != null && frame.frozenPose.apply(part)) {
            return;
        }

        Map<ModelPart, PartTransform> frozenParts = FROZEN_DETACHED_PARTS.computeIfAbsent(
                frame.entityId, ignored -> new IdentityHashMap<>());
        PartTransform transform = frozenParts.get(part);
        if (transform == null) {
            Map<ModelPart, PartTransform> lastParts = LAST_DETACHED_PARTS.get(frame.entityId);
            transform = lastParts == null ? null : lastParts.get(part);
            if (transform == null) {
                transform = PartTransform.capture(part);
            }
            frozenParts.put(part, transform);
        }
        transform.apply(part);
    }

    /** Returns a continuous, per-player animation clock slowed by assimilation. */
    public static float animationAge(int entityId, float vanillaAge) {
        ClientAssimilationState.View view = ClientAssimilationState.view(entityId);
        if (view == null || view.progress() <= 0.0001F) {
            ANIMATION_CLOCKS.remove(entityId);
            return vanillaAge;
        }
        float scale = view.stage().frozen()
                ? 0.0F : view.profile().animationScale(view.progress());
        return ANIMATION_CLOCKS.computeIfAbsent(entityId,
                ignored -> new AnimationClock(vanillaAge)).advance(vanillaAge, scale);
    }

    static void onPost(RenderPlayerEvent.Post event) {
        Deque<SavedWalk> stack = STACK.get();
        if (stack.isEmpty() || stack.peek().entityId != event.getEntity().getId()) {
            return;
        }
        SavedWalk saved = stack.pop();
        ClientAssimilationState.View view = ClientAssimilationState.view(event.getEntity().getId());
        if (view != null && !view.stage().frozen()) {
            PlayerModel<?> model = event.getRenderer().getModel();
            LAST_RENDERED_POSES.computeIfAbsent(event.getEntity().getId(),
                    ignored -> new IdentityHashMap<>()).put(model, ModelPose.capture(model));
        }
        if (saved.savedPose != null) {
            saved.savedPose.apply();
        }
        WalkAnimationStateAccessor accessor = (WalkAnimationStateAccessor) event.getEntity().walkAnimation;
        accessor.mirebound$setSpeedOld(saved.speedOld);
        accessor.mirebound$setSpeed(saved.speed);
        accessor.mirebound$setPosition(saved.position);
        if (stack.isEmpty()) {
            STACK.remove();
        }
    }

    static void clearEntity(int entityId) {
        AssimilationCapeAnimation.clearEntity(entityId);
        LAST_RENDERED_POSES.remove(entityId);
        FROZEN_POSES.remove(entityId);
        LAST_DETACHED_PARTS.remove(entityId);
        FROZEN_DETACHED_PARTS.remove(entityId);
        ANIMATION_CLOCKS.remove(entityId);
    }

    static void reset() {
        AssimilationCapeAnimation.reset();
        LAST_RENDERED_POSES.clear();
        FROZEN_POSES.clear();
        LAST_DETACHED_PARTS.clear();
        FROZEN_DETACHED_PARTS.clear();
        ANIMATION_CLOCKS.clear();
        STACK.remove();
    }

    private static final class SavedWalk {
        private final int entityId;
        private final float speedOld;
        private final float speed;
        private final float position;
        private ModelPose savedPose;
        private ModelPose frozenPose;

        private SavedWalk(int entityId, float speedOld, float speed, float position) {
            this.entityId = entityId;
            this.speedOld = speedOld;
            this.speed = speed;
            this.position = position;
        }
    }

    private static final class AnimationClock {
        private float lastVanillaAge;
        private float displayAge;

        private AnimationClock(float vanillaAge) {
            lastVanillaAge = vanillaAge;
            displayAge = vanillaAge;
        }

        private float advance(float vanillaAge, float scale) {
            float delta = vanillaAge - lastVanillaAge;
            if (delta < -0.001F || delta > 5.0F) {
                displayAge = vanillaAge;
            } else if (delta > 0.0F) {
                displayAge += delta * scale;
            }
            lastVanillaAge = vanillaAge;
            return displayAge;
        }
    }

    private record PartTransform(float x, float y, float z,
            float xRot, float yRot, float zRot,
            float xScale, float yScale, float zScale,
            boolean visible, boolean skipDraw) {
        static PartTransform capture(ModelPart part) {
            return new PartTransform(part.x, part.y, part.z,
                    part.xRot, part.yRot, part.zRot,
                    part.xScale, part.yScale, part.zScale,
                    part.visible, part.skipDraw);
        }

        void apply(ModelPart part) {
            part.x = x;
            part.y = y;
            part.z = z;
            part.xRot = xRot;
            part.yRot = yRot;
            part.zRot = zRot;
            part.xScale = xScale;
            part.yScale = yScale;
            part.zScale = zScale;
            part.visible = visible;
            part.skipDraw = skipDraw;
        }
    }

    private static final class ModelPose {
        private final IdentityHashMap<ModelPart, PartTransform> transforms;

        private ModelPose(IdentityHashMap<ModelPart, PartTransform> transforms) {
            this.transforms = transforms;
        }

        static ModelPose capture(PlayerModel<?> model) {
            IdentityHashMap<ModelPart, PartTransform> captured = new IdentityHashMap<>();
            captureTree(model.head, captured);
            captureTree(model.hat, captured);
            captureTree(model.body, captured);
            captureTree(model.jacket, captured);
            captureTree(model.leftArm, captured);
            captureTree(model.leftSleeve, captured);
            captureTree(model.rightArm, captured);
            captureTree(model.rightSleeve, captured);
            captureTree(model.leftLeg, captured);
            captureTree(model.leftPants, captured);
            captureTree(model.rightLeg, captured);
            captureTree(model.rightPants, captured);
            PlayerModelGeometryAccessor accessor = (PlayerModelGeometryAccessor) model;
            captureTree(accessor.mirebound$getCloak(), captured);
            captureTree(accessor.mirebound$getEar(), captured);
            return new ModelPose(captured);
        }

        private static void captureTree(ModelPart root,
                IdentityHashMap<ModelPart, PartTransform> captured) {
            root.getAllParts().forEach(part ->
                    captured.putIfAbsent(part, PartTransform.capture(part)));
        }

        void apply() {
            transforms.forEach((part, transform) -> transform.apply(part));
        }

        boolean apply(ModelPart part) {
            PartTransform transform = transforms.get(part);
            if (transform != null) {
                transform.apply(part);
                return true;
            }
            return false;
        }
    }
}

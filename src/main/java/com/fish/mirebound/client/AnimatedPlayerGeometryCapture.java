package com.fish.mirebound.client;

import com.fish.mirebound.client.compat.ClientRenderCompat;
import com.fish.mirebound.client.config.MireboundClientSettings;
import com.fish.mirebound.mixin.client.mud.PlayerModelGeometryAccessor;
import com.fish.mirebound.mud.AnimatedPlayerGeometry;
import com.fish.mirebound.mud.MudBodyPart;
import com.fish.mirebound.network.payload.PlayerGeometryPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Locale;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Captures rigid player-part transforms after renderer animation has run. */
public final class AnimatedPlayerGeometryCapture {
    private static final int CAPTURE_INTERVAL_TICKS = 2;
    private static int lastBodyAttemptTick = Integer.MIN_VALUE;
    private static int lastCapeAttemptTick = Integer.MIN_VALUE;
    private static int lastReliableBodyTick = Integer.MIN_VALUE;
    private static int lastReliableCapeTick = Integer.MIN_VALUE;
    private static int pendingBodyModelTick = Integer.MIN_VALUE;
    private static int pendingCapeModelTick = Integer.MIN_VALUE;
    private static int dirtyProbeTick = Integer.MIN_VALUE;
    private static int bodyAttempts;
    private static int capeAttempts;
    private static int bodySuccesses;
    private static int capeSuccesses;
    private static boolean autoBodyUsesVertices;
    private static boolean autoCapeUsesVertices;
    private static boolean dirtyProbeResult;
    private static String bodyStatus = "not_attempted";
    private static String capeStatus = "not_attempted";
    private static String bodyDetail = "none";
    private static String capeDetail = "none";
    private static String bodyPipeline = "none";
    private static String capePipeline = "none";

    private AnimatedPlayerGeometryCapture() {
    }

    public static VertexConsumer wrapBody(VertexConsumer delegate, LivingEntity entity,
            EntityModel<?> model, PoseStack poseStack) {
        if (!(entity instanceof LocalPlayer player) || !isCurrentLocalPlayer(player)
                || incompleteFirstPersonPass()
                || !shouldCapture(player)
                || !captureAttemptDue(
                        player.tickCount, lastBodyAttemptTick,
                        ClientRenderCompat.isRenderingShaderShadowPass())
                || delegate instanceof AnimatedGeometryConsumer) {
            return delegate;
        }
        lastBodyAttemptTick = player.tickCount;
        bodyAttempts++;
        bodyPipeline = pipelineDescription(player, model);
        ContactGeometryMode mode = MireboundClientSettings.contactGeometryMode();
        boolean useVertices = mode == ContactGeometryMode.SODIUM_VERTICES
                || mode == ContactGeometryMode.AUTO && autoBodyUsesVertices;
        if (useVertices && SodiumGeometryCaptureBridge.available()) {
            VertexConsumer wrapped = SodiumGeometryCaptureBridge.wrapBody(delegate, player);
            if (wrapped != null) {
                return wrapped;
            }
        }
        if (useVertices) {
            recordBodyFailure("sodium:wrapper_unavailable");
            if (mode == ContactGeometryMode.AUTO) {
                autoBodyUsesVertices = false;
            }
            return delegate;
        }
        pendingBodyModelTick = player.tickCount;
        return delegate;
    }

    public static void finishBody(VertexConsumer consumer, LivingEntity entity,
            EntityModel<?> model, PoseStack poseStack) {
        if (consumer instanceof AnimatedGeometryConsumer capture) {
            capture.finishGeometry();
        }
        if (entity instanceof LocalPlayer player && isCurrentLocalPlayer(player)
                && pendingBodyModelTick == player.tickCount) {
            pendingBodyModelTick = Integer.MIN_VALUE;
            boolean success = captureModelBody(player, model, poseStack);
            if (MireboundClientSettings.contactGeometryMode() == ContactGeometryMode.AUTO) {
                autoBodyUsesVertices = !success;
            }
        }
    }

    public static VertexConsumer wrapCape(VertexConsumer delegate,
            AbstractClientPlayer entity, PlayerModel<?> model, PoseStack poseStack) {
        if (!(entity instanceof LocalPlayer player) || !isCurrentLocalPlayer(player)
                || incompleteFirstPersonPass()
                || !shouldCapture(player)
                || !captureAttemptDue(
                        player.tickCount, lastCapeAttemptTick,
                        ClientRenderCompat.isRenderingShaderShadowPass())
                || delegate instanceof AnimatedGeometryConsumer) {
            return delegate;
        }
        lastCapeAttemptTick = player.tickCount;
        capeAttempts++;
        capePipeline = pipelineDescription(player, model);
        ContactGeometryMode mode = MireboundClientSettings.contactGeometryMode();
        boolean useVertices = mode == ContactGeometryMode.SODIUM_VERTICES
                || mode == ContactGeometryMode.AUTO && autoCapeUsesVertices;
        if (useVertices && SodiumGeometryCaptureBridge.available()) {
            VertexConsumer wrapped = SodiumGeometryCaptureBridge.wrapCape(delegate, player);
            if (wrapped != null) {
                return wrapped;
            }
        }
        if (useVertices) {
            recordCapeFailure("sodium:wrapper_unavailable");
            if (mode == ContactGeometryMode.AUTO) {
                autoCapeUsesVertices = false;
            }
            return delegate;
        }
        pendingCapeModelTick = player.tickCount;
        return delegate;
    }

    public static void finishCape(VertexConsumer consumer, AbstractClientPlayer entity,
            PlayerModel<?> model, PoseStack poseStack) {
        if (consumer instanceof AnimatedGeometryConsumer capture) {
            capture.finishGeometry();
        }
        if (entity instanceof LocalPlayer player && isCurrentLocalPlayer(player)
                && pendingCapeModelTick == player.tickCount) {
            pendingCapeModelTick = Integer.MIN_VALUE;
            boolean success = captureModelCape(player, model, poseStack);
            if (MireboundClientSettings.contactGeometryMode() == ContactGeometryMode.AUTO) {
                autoCapeUsesVertices = !success;
            }
        }
    }

    public static boolean needsLocalOffscreenCapture(LocalPlayer player) {
        return shouldCapture(player) && attemptDue(player.tickCount, lastBodyAttemptTick);
    }

    public static String status(LocalPlayer player) {
        AnimatedPlayerGeometry.Source source = player == null
                ? AnimatedPlayerGeometry.Source.NONE
                : AnimatedPlayerGeometry.bodySource(player);
        return "contact_geometry_mode="
                + MireboundClientSettings.contactGeometryMode().serializedName()
                + ", active_source=" + source.name().toLowerCase(Locale.ROOT)
                + ", sodium_available=" + SodiumGeometryCaptureBridge.available()
                + ", capture_gate=" + captureGate(player)
                + ", body_age_ticks=" + ageTicks(player, lastReliableBodyTick)
                + ", cape_age_ticks=" + ageTicks(player, lastReliableCapeTick)
                + ", body_attempts=" + bodyAttempts + '/' + bodySuccesses
                + ", cape_attempts=" + capeAttempts + '/' + capeSuccesses
                + ", body_status=" + bodyStatus
                + ", body_detail=" + bodyDetail
                + ", body_pipeline=" + bodyPipeline
                + ", cape_status=" + capeStatus
                + ", cape_detail=" + capeDetail
                + ", cape_pipeline=" + capePipeline;
    }

    public static void reset() {
        lastBodyAttemptTick = Integer.MIN_VALUE;
        lastCapeAttemptTick = Integer.MIN_VALUE;
        lastReliableBodyTick = Integer.MIN_VALUE;
        lastReliableCapeTick = Integer.MIN_VALUE;
        pendingBodyModelTick = Integer.MIN_VALUE;
        pendingCapeModelTick = Integer.MIN_VALUE;
        dirtyProbeTick = Integer.MIN_VALUE;
        bodyAttempts = 0;
        capeAttempts = 0;
        bodySuccesses = 0;
        capeSuccesses = 0;
        autoBodyUsesVertices = false;
        autoCapeUsesVertices = false;
        dirtyProbeResult = false;
        bodyStatus = "not_attempted";
        capeStatus = "not_attempted";
        bodyDetail = "none";
        capeDetail = "none";
        bodyPipeline = "none";
        capePipeline = "none";
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            AnimatedPlayerGeometry.clear(minecraft.player);
        }
    }

    static void submitBody(LocalPlayer player, AnimatedPlayerGeometry.PartPose[] poses,
            AnimatedPlayerGeometry.Source source) {
        lastReliableBodyTick = player.tickCount;
        bodySuccesses++;
        bodyStatus = "ok:" + source.name().toLowerCase(Locale.ROOT);
        AnimatedPlayerGeometry.updateBody(player, poses, source);
        if (MireboundClientSettings.animatedContactGeometry()
                && !ClientPollutionVisibility.isContactSamplingSuppressed(player)) {
            PacketDistributor.sendToServer(PlayerGeometryPayload.body(player.position(), poses));
        }
    }

    static void submitCape(LocalPlayer player, AnimatedPlayerGeometry.CapePose cape,
            AnimatedPlayerGeometry.Source source) {
        lastReliableCapeTick = player.tickCount;
        capeSuccesses++;
        capeStatus = "ok:" + source.name().toLowerCase(Locale.ROOT);
        AnimatedPlayerGeometry.updateCape(player, cape, source);
        if (MireboundClientSettings.animatedContactGeometry()
                && !ClientPollutionVisibility.isContactSamplingSuppressed(player)) {
            PacketDistributor.sendToServer(PlayerGeometryPayload.cape(player.position(), cape));
        }
    }

    static boolean validPartPose(MudBodyPart part,
            Vec3 side, Vec3 up, Vec3 forward) {
        double width = side.length();
        double height = up.length();
        double depth = forward.length();
        if (width < 0.035D || height < 0.12D || depth < 0.035D
                || width > (part == MudBodyPart.HEAD || part == MudBodyPart.BODY ? 0.42D : 0.28D)
                || height > (part == MudBodyPart.HEAD ? 0.42D : 0.62D)
                || depth > (part == MudBodyPart.HEAD ? 0.42D : 0.28D)) {
            return false;
        }
        Vec3 s = side.normalize();
        Vec3 u = up.normalize();
        Vec3 f = forward.normalize();
        return Math.abs(s.dot(u)) <= 0.36D
                && Math.abs(s.dot(f)) <= 0.36D
                && Math.abs(u.dot(f)) <= 0.36D;
    }

    static void recordBodyFailure(String reason) {
        bodyStatus = reason;
        if (MireboundClientSettings.contactGeometryMode() == ContactGeometryMode.AUTO
                && reason.startsWith("sodium:")) {
            autoBodyUsesVertices = false;
        }
    }

    static void recordCapeFailure(String reason) {
        capeStatus = reason;
        if (MireboundClientSettings.contactGeometryMode() == ContactGeometryMode.AUTO
                && reason.startsWith("sodium:")) {
            autoCapeUsesVertices = false;
        }
    }

    static void recordBodyDetail(String detail) {
        bodyDetail = detail;
    }

    static void recordCapeDetail(String detail) {
        capeDetail = detail;
    }

    private static boolean captureModelBody(LocalPlayer player,
            EntityModel<?> model, PoseStack poseStack) {
        if (!(model instanceof PlayerModel<?> playerModel)) {
            bodyStatus = "model_part:not_player_model:" + model.getClass().getSimpleName();
            return false;
        }
        AnimatedPlayerGeometry.PartPose[] poses =
                new AnimatedPlayerGeometry.PartPose[MudBodyPart.COUNT];
        StringBuilder selected = new StringBuilder();
        boolean slim = player.getSkin().model()
                == net.minecraft.client.resources.PlayerSkin.Model.SLIM;
        captureBodyPart(poses, selected, MudBodyPart.LEFT_LEG,
                playerModel.leftLeg, poseStack, 4.0D, 12.0D, 4.0D);
        captureBodyPart(poses, selected, MudBodyPart.RIGHT_LEG,
                playerModel.rightLeg, poseStack, 4.0D, 12.0D, 4.0D);
        captureBodyPart(poses, selected, MudBodyPart.BODY,
                playerModel.body, poseStack, 8.0D, 12.0D, 4.0D);
        captureBodyPart(poses, selected, MudBodyPart.LEFT_ARM,
                playerModel.leftArm, poseStack, slim ? 3.0D : 4.0D, 12.0D, 4.0D);
        captureBodyPart(poses, selected, MudBodyPart.RIGHT_ARM,
                playerModel.rightArm, poseStack, slim ? 3.0D : 4.0D, 12.0D, 4.0D);
        captureBodyPart(poses, selected, MudBodyPart.HEAD,
                playerModel.head, poseStack, 8.0D, 8.0D, 8.0D);
        for (MudBodyPart part : MudBodyPart.values()) {
            AnimatedPlayerGeometry.PartPose pose = poses[part.ordinal()];
            if (pose == null || !validPartPose(part,
                    pose.halfSide(), pose.halfUp(), pose.halfForward())) {
                bodyStatus = "model_part:invalid_" + part.name().toLowerCase(Locale.ROOT)
                        + (pose == null ? ":missing" : String.format(Locale.ROOT,
                                ":%.3f/%.3f/%.3f", pose.halfWidth(),
                                pose.halfHeight(), pose.halfDepth()));
                return false;
            }
        }
        bodyDetail = "selected_cubes[" + selected + ']';
        submitBody(player, poses, AnimatedPlayerGeometry.Source.MODEL_PART);
        return true;
    }

    private static boolean captureModelCape(LocalPlayer player,
            PlayerModel<?> model, PoseStack poseStack) {
        if (!(model instanceof PlayerModelGeometryAccessor accessor)) {
            capeStatus = "model_part:no_cloak_accessor";
            return false;
        }
        PartCapture captured = capturePart(
                accessor.mirebound$getCloak(), poseStack, 10.0D, 16.0D, 1.0D);
        AnimatedPlayerGeometry.PartPose box = captured == null ? null : captured.pose;
        AnimatedPlayerGeometry.PartPose body =
                AnimatedPlayerGeometry.part(player, MudBodyPart.BODY);
        if (box == null || body == null) {
            capeStatus = "model_part:missing_box_or_body";
            return false;
        }
        Vec3 side = box.side();
        Vec3 down = box.up().scale(-1.0D);
        Vec3 normal = box.forward();
        Vec3 expectedBack = body.forward().scale(-1.0D);
        if (normal.dot(expectedBack) < 0.0D) {
            normal = normal.scale(-1.0D);
        }
        double scale = (box.halfWidth() * 2.0D / 10.0D
                + box.halfHeight() * 2.0D / 16.0D) * 0.5D;
        if (scale < 0.025D || scale > 0.12D) {
            capeStatus = String.format(Locale.ROOT, "model_part:invalid_scale:%.3f", scale);
            return false;
        }
        AnimatedPlayerGeometry.CapePose cape = new AnimatedPlayerGeometry.CapePose(
                box.center().subtract(down.scale(box.halfHeight())),
                side, down, normal, scale);
        capeDetail = captured.diagnostic();
        submitCape(player, cape, AnimatedPlayerGeometry.Source.MODEL_PART);
        return true;
    }

    private static void captureBodyPart(AnimatedPlayerGeometry.PartPose[] poses,
            StringBuilder selected, MudBodyPart bodyPart, ModelPart modelPart,
            PoseStack poseStack, double width, double height, double depth) {
        PartCapture capture = capturePart(modelPart, poseStack, width, height, depth);
        poses[bodyPart.ordinal()] = capture == null ? null : capture.pose;
        if (!selected.isEmpty()) {
            selected.append(',');
        }
        selected.append(bodyPart.name().toLowerCase(Locale.ROOT)).append('=')
                .append(capture == null ? "none" : capture.diagnostic());
    }

    private static PartCapture capturePart(ModelPart part, PoseStack poseStack,
            double targetWidth, double targetHeight, double targetDepth) {
        if (part == null || !part.visible) {
            return null;
        }
        CubeSelector selector = new CubeSelector(
                targetWidth, targetHeight, targetDepth, worldCameraPosition());
        part.visit(poseStack, (pose, path, index, cube) -> {
            selector.offer(pose.pose(), path, index, cube);
        });
        return selector.best();
    }

    static Vec3 worldCameraPosition() {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        return camera == null ? Vec3.ZERO : camera.getPosition();
    }

    private static String pipelineDescription(LocalPlayer player, EntityModel<?> model) {
        String renderer = "unknown";
        try {
            renderer = Minecraft.getInstance().getEntityRenderDispatcher()
                    .getRenderer(player).getClass().getName();
        } catch (RuntimeException ignored) {
            // Diagnostics must never disturb rendering.
        }
        return "renderer=" + renderer + ",model=" + model.getClass().getName();
    }

    private static boolean shouldCapture(LocalPlayer player) {
        if (!isCurrentLocalPlayer(player) || player.level() == null || !player.isAlive()
                || ClientPollutionVisibility.isSuppressed(player)
                || ClientPollutionVisibility.isContactSamplingSuppressed(player)
                        && !ClientMudDebugOptions.contactGeometry()
                || !MireboundClientSettings.animatedContactGeometry()
                        && !ClientMudDebugOptions.contactGeometry()) {
            return false;
        }
        return ClientMudDebugOptions.contactGeometry()
                || ArmorVertexContactCapture.needsLocalOffscreenCapture(player)
                || player.clientLevel.isRaining() && hasDirtyBodyOrCape(player);
    }

    private static boolean isCurrentLocalPlayer(LocalPlayer player) {
        return player != null && player == Minecraft.getInstance().player;
    }

    private static boolean incompleteFirstPersonPass() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.options.getCameraType().isFirstPerson()
                && !ArmorMudProxyRenderer.isCapturePass();
    }

    private static String captureGate(LocalPlayer player) {
        if (player == null) {
            return "no_player";
        }
        if (player.level() == null) {
            return "no_level";
        }
        if (!player.isAlive()) {
            return "dead";
        }
        if (ClientPollutionVisibility.isSuppressed(player)) {
            return "spectator";
        }
        if (ClientPollutionVisibility.isContactSamplingSuppressed(player)
                && !ClientMudDebugOptions.contactGeometry()) {
            return "external_camera";
        }
        if (!MireboundClientSettings.animatedContactGeometry()
                && !ClientMudDebugOptions.contactGeometry()) {
            return "disabled";
        }
        return "ready";
    }

    private static String ageTicks(LocalPlayer player, int capturedTick) {
        if (player == null || capturedTick == Integer.MIN_VALUE) {
            return "none";
        }
        int age = player.tickCount - capturedTick;
        return age < 0 ? "reset" : Integer.toString(age);
    }

    private static boolean attemptDue(int tick, int lastTick) {
        int elapsed = tick - lastTick;
        return lastTick == Integer.MIN_VALUE || elapsed < 0
                || elapsed >= CAPTURE_INTERVAL_TICKS;
    }

    static boolean captureAttemptDue(int tick, int lastTick, boolean shadowPass) {
        return !shadowPass && attemptDue(tick, lastTick);
    }

    private static boolean hasDirtyBodyOrCape(LocalPlayer player) {
        if (dirtyProbeTick == player.tickCount) {
            return dirtyProbeResult;
        }
        dirtyProbeTick = player.tickCount;
        dirtyProbeResult = scanDirtyBodyOrCape(player);
        return dirtyProbeResult;
    }

    private static boolean scanDirtyBodyOrCape(LocalPlayer player) {
        return ClientMudState.hasVisibleBodyOrCape(player.getId());
    }

    private static final class CubeSelector {
        private static final double MAX_SCORE = 0.8D;
        private final double targetWidth;
        private final double targetHeight;
        private final double targetDepth;
        private final Vec3 camera;
        private PartCapture best;

        CubeSelector(double targetWidth, double targetHeight,
                double targetDepth, Vec3 camera) {
            this.targetWidth = targetWidth;
            this.targetHeight = targetHeight;
            this.targetDepth = targetDepth;
            this.camera = camera;
        }

        void offer(Matrix4f matrix, String path, int index, ModelPart.Cube cube) {
            double width = Math.abs(cube.maxX - cube.minX);
            double height = Math.abs(cube.maxY - cube.minY);
            double depth = Math.abs(cube.maxZ - cube.minZ);
            if (width < 0.01D || height < 0.01D || depth < 0.01D) {
                return;
            }
            double score = Math.abs(width - targetWidth) / targetWidth
                    + Math.abs(height - targetHeight) / targetHeight
                    + Math.abs(depth - targetDepth) / targetDepth;
            if (score > MAX_SCORE || best != null && score >= best.score) {
                return;
            }
            AnimatedPlayerGeometry.PartPose pose = cubePose(matrix, cube, camera);
            if (pose != null) {
                best = new PartCapture(pose, score, path, index, width, height, depth);
            }
        }

        PartCapture best() {
            return best;
        }
    }

    private record PartCapture(AnimatedPlayerGeometry.PartPose pose, double score,
            String path, int index, double width, double height, double depth) {
        String diagnostic() {
            return String.format(Locale.ROOT, "%s#%d:%.1fx%.1fx%.1f@%.3f",
                    path, index, width, height, depth, score);
        }
    }

    private static AnimatedPlayerGeometry.PartPose cubePose(
            Matrix4f matrix, ModelPart.Cube cube, Vec3 camera) {
        float centerX = (cube.minX + cube.maxX) / 32.0F;
        float centerY = (cube.minY + cube.maxY) / 32.0F;
        float centerZ = (cube.minZ + cube.maxZ) / 32.0F;
        Vec3 localCenter = transform(matrix, centerX, centerY, centerZ);
        Vec3 side = transform(matrix, cube.maxX / 16.0F, centerY, centerZ)
                .subtract(localCenter);
        Vec3 up = transform(matrix, centerX, cube.minY / 16.0F, centerZ)
                .subtract(localCenter);
        Vec3 forward = transform(matrix, centerX, centerY, cube.minZ / 16.0F)
                .subtract(localCenter);
        if (side.lengthSqr() < 1.0E-8D || up.lengthSqr() < 1.0E-8D
                || forward.lengthSqr() < 1.0E-8D) {
            return null;
        }
        return new AnimatedPlayerGeometry.PartPose(
                localCenter.add(camera), side, up, forward);
    }

    private static Vec3 transform(Matrix4f matrix, float x, float y, float z) {
        Vector3f transformed = matrix.transformPosition(x, y, z, new Vector3f());
        return new Vec3(transformed.x, transformed.y, transformed.z);
    }
}

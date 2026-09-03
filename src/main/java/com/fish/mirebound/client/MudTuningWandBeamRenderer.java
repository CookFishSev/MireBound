package com.fish.mirebound.client;

import com.fish.mirebound.client.compat.freecam.FreecamCompat;
import com.fish.mirebound.client.generation.MudTerrainGenerationController;
import com.fish.mirebound.mud.MudTuningAnchor;
import com.fish.mirebound.client.tuning.MudTuningInputController;
import com.fish.mirebound.client.tuning.MudTuningSpatialPlacement;
import com.fish.mirebound.client.tuning.MudTuningTentacleTargeting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Draws short shader-friendly selection beams from synchronized wand activations. */
final class MudTuningWandBeamRenderer {
    private static final RenderType BEAM_RENDER_TYPE = RenderType.lightning();
    private static final double MAX_RENDER_DISTANCE_SQUARED = 128.0D * 128.0D;
    private static final double TARGET_NODE_SPACING = 1.5D;
    private static final int MIN_NODE_COUNT = 8;
    private static final int MAX_NODE_COUNT = 32;
    private static final double LINE_HALF_WIDTH = 0.3D / 16.0D;

    private MudTuningWandBeamRenderer() {
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
        double time = minecraft.level.getGameTime() + partialTick;
        List<MudTuningWandClientEffects.BeamView> beams =
                MudTuningWandClientEffects.beams(time);
        Vec3 previewTarget = MudTerrainGenerationController.coreTarget(minecraft);
        var tentacleTarget = MudTuningTentacleTargeting.target(minecraft);
        if (previewTarget == null) {
            previewTarget = tentacleTarget == null
                    ? MudTuningSpatialPlacement.target(minecraft)
                    : tentacleTarget.position();
        }
        if (beams.isEmpty() && previewTarget == null) {
            return;
        }

        Vec3 cameraPosition = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer vertices = buffers.getBuffer(BEAM_RENDER_TYPE);
        poseStack.pushPose();
        poseStack.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);
        PoseStack.Pose pose = poseStack.last();

        if (previewTarget != null) {
            renderPlacementPreview(minecraft, event, pose, vertices,
                    cameraPosition, partialTick, time, previewTarget);
        }

        for (MudTuningWandClientEffects.BeamView beam : beams) {
            Entity entity = minecraft.level.getEntity(beam.playerEntityId());
            if (!(entity instanceof Player player)) {
                continue;
            }
            Vec3 target = targetPosition(minecraft, beam.target());
            Vec3 origin = beamOrigin(minecraft, event, player, beam.mainHand(), partialTick);
            if (target == null || origin == null
                    || cameraPosition.distanceToSqr(origin) > MAX_RENDER_DISTANCE_SQUARED) {
                continue;
            }
            Vec3 visibleEnd = origin.add(target.subtract(origin).scale(beam.extension()));
            if (origin.distanceToSqr(visibleEnd) <= 1.0E-6D) {
                continue;
            }
            AABB bounds = new AABB(origin, visibleEnd).inflate(0.12D);
            if (event.getFrustum() != null && !event.getFrustum().isVisible(bounds)) {
                continue;
            }
            renderBeam(pose, vertices, origin, visibleEnd, cameraPosition, beam);
        }

        poseStack.popPose();
        buffers.endBatch(BEAM_RENDER_TYPE);
    }

    private static void renderPlacementPreview(Minecraft minecraft,
            RenderLevelStageEvent event, PoseStack.Pose pose, VertexConsumer vertices,
            Vec3 cameraPosition, float partialTick, double time, Vec3 target) {
        Player player = minecraft.player;
        InteractionHand hand = player == null ? null
                : MudTuningInputController.heldWandHand(player);
        if (player == null || hand == null || target == null) {
            return;
        }
        boolean mainHand = hand == InteractionHand.MAIN_HAND;
        Vec3 origin = beamOrigin(minecraft, event, player, mainHand, partialTick);
        if (origin == null || origin.distanceToSqr(target) <= 1.0E-6D) {
            return;
        }
        AABB bounds = new AABB(origin, target).inflate(0.12D);
        if (event.getFrustum() != null && !event.getFrustum().isVisible(bounds)) {
            return;
        }
        MudTuningWandClientEffects.BeamView preview =
                new MudTuningWandClientEffects.BeamView(
                        player.getId(), null, mainHand,
                        MudTuningWandClientEffects.activeColor(time),
                        1.0F, 0.96F, time, 0.0D);
        renderBeam(pose, vertices, origin, target, cameraPosition, preview);
    }

    private static Vec3 targetPosition(Minecraft minecraft, MudTuningAnchor anchor) {
        return MudTuningWandClientEffects.targetPosition(minecraft, anchor);
    }

    private static Vec3 beamOrigin(Minecraft minecraft, RenderLevelStageEvent event,
            Player player, boolean mainHand, float partialTick) {
        Vec3 captured = MudTuningWandCoreFocus.resolve(
                player, mainHand, partialTick,
                event.getModelViewMatrix(), event.getProjectionMatrix());
        if (captured != null) {
            return captured;
        }
        HumanoidArm arm = mainHand ? player.getMainArm() : player.getMainArm().getOpposite();
        double side = arm == HumanoidArm.RIGHT ? 1.0D : -1.0D;
        boolean localFirstPerson = player == minecraft.player
                && minecraft.options.getCameraType().isFirstPerson()
                && event.getCamera().getEntity() == player
                && !FreecamCompat.isExternalCameraActive(minecraft);
        if (localFirstPerson) {
            Vec3 forward = player.getViewVector(partialTick).normalize();
            Vec3 right = forward.cross(new Vec3(0.0D, 1.0D, 0.0D));
            if (right.lengthSqr() <= 1.0E-6D) {
                right = bodyRight(player, partialTick);
            } else {
                right = right.normalize();
            }
            Vec3 up = right.cross(forward);
            if (up.lengthSqr() <= 1.0E-6D) {
                up = new Vec3(0.0D, 1.0D, 0.0D);
            } else {
                up = up.normalize();
            }
            return event.getCamera().getPosition()
                    .add(forward.scale(0.76D))
                    .add(right.scale(0.32D * side))
                    .subtract(up.scale(0.22D));
        }

        Vec3 bodyForward = bodyForward(player, partialTick);
        Vec3 bodyRight = bodyRight(player, partialTick);
        return player.getEyePosition(partialTick)
                .add(0.0D, -0.35D, 0.0D)
                .add(bodyForward.scale(1.275D))
                .add(bodyRight.scale(0.325D * side));
    }

    private static Vec3 bodyForward(Player player, float partialTick) {
        double yaw = Math.toRadians(Mth.rotLerp(
                partialTick, player.yBodyRotO, player.yBodyRot));
        return new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
    }

    private static Vec3 bodyRight(Player player, float partialTick) {
        Vec3 forward = bodyForward(player, partialTick);
        return forward.cross(new Vec3(0.0D, 1.0D, 0.0D)).normalize();
    }

    private static void renderBeam(PoseStack.Pose pose, VertexConsumer vertices,
            Vec3 origin, Vec3 end, Vec3 camera,
            MudTuningWandClientEffects.BeamView beam) {
        Vec3 delta = end.subtract(origin);
        Vec3[] points = beamPoints(origin, delta, beam);
        int red = beam.color() >> 16 & 0xFF;
        int green = beam.color() >> 8 & 0xFF;
        int blue = beam.color() & 0xFF;
        int alpha = Mth.clamp(Math.round(beam.alpha() * 245.0F), 0, 255);
        renderRibbon(pose, vertices, points, camera,
                LINE_HALF_WIDTH, red, green, blue, alpha);
    }

    private static Vec3[] beamPoints(Vec3 origin, Vec3 delta,
            MudTuningWandClientEffects.BeamView beam) {
        double length = delta.length();
        double scaledLength = length / TARGET_NODE_SPACING;
        int nodeCount = Mth.clamp((int) Math.round(
                MIN_NODE_COUNT * (double) MIN_NODE_COUNT
                        / (scaledLength + MIN_NODE_COUNT) + scaledLength),
                MIN_NODE_COUNT, MAX_NODE_COUNT);
        Vec3 direction = delta.normalize();
        Vec3 side = direction.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (side.lengthSqr() <= 1.0E-8D) {
            side = direction.cross(new Vec3(1.0D, 0.0D, 0.0D));
        }
        side = side.normalize();
        Vec3 cross = direction.cross(side).normalize();
        double nodeRadius = 0.2D * Math.sqrt(
                Math.max(0.0D, scaledLength) / Math.max(1, nodeCount));
        long tick = (long) Math.floor(beam.age());
        float partialTick = (float) (beam.age() - tick);
        long seed = Double.doubleToLongBits(beam.startTime())
                ^ (long) beam.playerEntityId() * 0x9E3779B97F4A7C15L;

        Vec3[] points = new Vec3[nodeCount];
        for (int index = 0; index < nodeCount; index++) {
            double along = index / (double) (nodeCount - 1);
            Vec3 point = origin.add(delta.scale(along));
            if (index > 0 && index < nodeCount - 1) {
                Vec3 previous = nodeOffset(seed, index, tick,
                        direction, side, cross, nodeRadius);
                Vec3 next = nodeOffset(seed, index, tick + 1L,
                        direction, side, cross, nodeRadius);
                point = point.add(previous.lerp(next, partialTick));
            }
            points[index] = point;
        }
        return points;
    }

    private static Vec3 nodeOffset(long seed, int node, long tick,
            Vec3 direction, Vec3 side, Vec3 cross, double radius) {
        long nodeSeed = seed ^ (long) node * 0xD1B54A32D192ED03L
                ^ tick * 0x94D049BB133111EBL;
        double along = signedUnit(nodeSeed ^ 0x632BE59BD9B4E019L) * radius * 0.3D;
        double horizontal = signedUnit(nodeSeed ^ 0xC6BC279692B5CC83L) * radius;
        double vertical = signedUnit(nodeSeed ^ 0xDB4F0B9175AE2165L) * radius;
        return direction.scale(along)
                .add(side.scale(horizontal))
                .add(cross.scale(vertical));
    }

    private static double signedUnit(long seed) {
        long mixed = mix64(seed);
        return (mixed >>> 11) * 0x1.0p-53 - 0.5D;
    }

    private static long mix64(long value) {
        value += 0x9E3779B97F4A7C15L;
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    private static void renderRibbon(PoseStack.Pose pose, VertexConsumer vertices,
            Vec3[] points, Vec3 camera, double halfWidth,
            int red, int green, int blue, int alpha) {
        Vec3[] widths = new Vec3[points.length];
        for (int index = 0; index < points.length; index++) {
            Vec3 previous = points[Math.max(0, index - 1)];
            Vec3 next = points[Math.min(points.length - 1, index + 1)];
            Vec3 tangent = next.subtract(previous);
            Vec3 view = points[index].subtract(camera);
            Vec3 width = tangent.cross(view);
            if (width.lengthSqr() <= 1.0E-8D) {
                width = tangent.cross(new Vec3(0.0D, 1.0D, 0.0D));
            }
            if (width.lengthSqr() <= 1.0E-8D) {
                width = new Vec3(1.0D, 0.0D, 0.0D);
            } else {
                width = width.normalize();
            }
            if (index > 0 && width.dot(widths[index - 1]) < 0.0D) {
                width = width.scale(-1.0D);
            }
            widths[index] = width.scale(halfWidth);
        }
        for (int index = 0; index < points.length - 1; index++) {
            ribbonQuad(pose, vertices,
                    points[index], points[index + 1],
                    widths[index], widths[index + 1],
                    red, green, blue, alpha);
        }
    }

    private static void ribbonQuad(PoseStack.Pose pose, VertexConsumer vertices,
            Vec3 from, Vec3 to, Vec3 fromHalfWidth, Vec3 toHalfWidth,
            int red, int green, int blue, int alpha) {
        vertex(pose, vertices, from.subtract(fromHalfWidth), red, green, blue, alpha);
        vertex(pose, vertices, to.subtract(toHalfWidth), red, green, blue, alpha);
        vertex(pose, vertices, to.add(toHalfWidth), red, green, blue, alpha);
        vertex(pose, vertices, from.add(fromHalfWidth), red, green, blue, alpha);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vertices,
            Vec3 point, int red, int green, int blue, int alpha) {
        vertices.addVertex(pose, (float) point.x, (float) point.y, (float) point.z)
                .setColor(red, green, blue, alpha);
    }
}

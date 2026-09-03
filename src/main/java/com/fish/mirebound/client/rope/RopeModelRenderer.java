package com.fish.mirebound.client.rope;

import com.fish.mirebound.Mirebound;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Two-layer square rope mesh adapted from Memento In Abyss' MIT renderer.
 * Mirebound keeps its independently authored inner and outer textures.
 */
public final class RopeModelRenderer {
    private static final ResourceLocation INNER_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            Mirebound.MOD_ID, "textures/entity/rope.png");
    private static final ResourceLocation OUTER_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            Mirebound.MOD_ID, "textures/entity/rope_w.png");
    private static final RenderType INNER_TYPE = RenderType.entityCutoutNoCull(INNER_TEXTURE);
    private static final RenderType OUTER_TYPE = RenderType.entityCutoutNoCull(OUTER_TEXTURE);
    private static final String BREAK_TEXTURE_PATH = "textures/block/destroy_stage_";

    private RopeModelRenderer() {
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        List<ClientRopes.View> ropes = ClientRopes.views(partialTick);
        if (!ropes.isEmpty()) {
            Vec3 camera = event.getCamera().getPosition();
            MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
            VertexConsumer inner = buffers.getBuffer(INNER_TYPE);
            VertexConsumer outer = buffers.getBuffer(OUTER_TYPE);
            LightSampler lights = LightSampler.forLevel(minecraft.level);
            PoseStack poseStack = event.getPoseStack();
            poseStack.pushPose();
            poseStack.translate(-camera.x, -camera.y, -camera.z);
            for (ClientRopes.View rope : ropes) {
                if (event.getFrustum() != null && !event.getFrustum().isVisible(rope.bounds())) {
                    continue;
                }
                renderChain(poseStack.last(), inner, outer, lights, rope.nodes(), rope.frames());
            }
            poseStack.popPose();
            buffers.endBatch(INNER_TYPE);
            buffers.endBatch(OUTER_TYPE);
        }
        ClientRopes.BreakSelection breaking = ClientRopes.breakSelection(partialTick);
        if (breaking != null) {
            renderBreakingSelection(event, breaking);
        }
    }

    private static void renderBreakingSelection(RenderLevelStageEvent event,
            ClientRopes.BreakSelection breaking) {
        Minecraft minecraft = Minecraft.getInstance();
        ResourceLocation texture = ResourceLocation.withDefaultNamespace(
                BREAK_TEXTURE_PATH + breaking.stage() + ".png");
        RenderType renderType = RenderType.entityCutoutNoCullZOffset(texture, false);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        ClientRopes.Selection selection = breaking.selection();
        RopeSegmentPose.Frame frame = selection.frame();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        VertexConsumer vertices = buffers.getBuffer(renderType);
        renderBreakingCuboid(poseStack.last(), vertices,
                selection.start().lerp(selection.end(), 0.5D), frame);
        poseStack.popPose();
        buffers.endBatch(renderType);
    }

    private static void renderBreakingCuboid(PoseStack.Pose pose, VertexConsumer vertices,
            Vec3 center, RopeSegmentPose.Frame frame) {
        double halfWidth = RopeSegmentSpec.OUTER.halfWidth() + 0.018D;
        double halfLength = RopeSegmentSpec.OUTER.halfLength() + 0.018D;
        Vec3 p000 = point(center, frame, -halfWidth, -halfLength, -halfWidth);
        Vec3 p001 = point(center, frame, -halfWidth, -halfLength, halfWidth);
        Vec3 p010 = point(center, frame, -halfWidth, halfLength, -halfWidth);
        Vec3 p011 = point(center, frame, -halfWidth, halfLength, halfWidth);
        Vec3 p100 = point(center, frame, halfWidth, -halfLength, -halfWidth);
        Vec3 p101 = point(center, frame, halfWidth, -halfLength, halfWidth);
        Vec3 p110 = point(center, frame, halfWidth, halfLength, -halfWidth);
        Vec3 p111 = point(center, frame, halfWidth, halfLength, halfWidth);
        breakingQuad(pose, vertices, p000, p010, p011, p001);
        breakingQuad(pose, vertices, p101, p111, p110, p100);
        breakingQuad(pose, vertices, p100, p110, p010, p000);
        breakingQuad(pose, vertices, p001, p011, p111, p101);
        breakingQuad(pose, vertices, p001, p101, p100, p000);
        breakingQuad(pose, vertices, p010, p110, p111, p011);
    }

    private static void breakingQuad(PoseStack.Pose pose, VertexConsumer vertices,
            Vec3 a, Vec3 b, Vec3 c, Vec3 d) {
        Vec3 normal = RopeSegmentSpec.faceNormal(a, b, c);
        if (normal.lengthSqr() <= 1.0E-10D) {
            return;
        }
        // Keep each sampled region centered on the corresponding rope face.
        float uSpan = crackTextureSpan(a.distanceTo(d));
        float vSpan = crackTextureSpan(a.distanceTo(b));
        float u0 = (1.0F - uSpan) * 0.5F;
        float v0 = (1.0F - vSpan) * 0.5F;
        float u1 = u0 + uSpan;
        float v1 = v0 + vSpan;
        breakingVertex(pose, vertices, a, u0, v1, normal);
        breakingVertex(pose, vertices, b, u0, v0, normal);
        breakingVertex(pose, vertices, c, u1, v0, normal);
        breakingVertex(pose, vertices, d, u1, v1, normal);
    }

    private static float crackTextureSpan(double edgeLength) {
        return (float) Math.max(0.0D, Math.min(1.0D, edgeLength));
    }

    private static void breakingVertex(PoseStack.Pose pose, VertexConsumer vertices,
            Vec3 point, float u, float v, Vec3 normal) {
        vertices.addVertex(pose, (float) point.x, (float) point.y, (float) point.z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static void renderChain(PoseStack.Pose pose, VertexConsumer inner,
            VertexConsumer outer, LightSampler lights, List<Vec3> nodes,
            RopeSegmentPose.Frame[] frames) {
        Vec3[] startRing = new Vec3[4];
        Vec3[] endRing = new Vec3[4];
        Vec3[] jointFirstRing = new Vec3[4];
        Vec3[] jointSecondRing = new Vec3[4];
        for (int segment = 0; segment < nodes.size() - 1; segment++) {
            Vec3 start = nodes.get(segment);
            Vec3 end = nodes.get(segment + 1);
            RopeSegmentPose.Frame frame = frames[segment];
            int light = lights.sample(start.lerp(end, 0.5D));
            renderSegmentLayer(pose, inner, start, end, frame,
                    RopeSegmentSpec.INNER, light, startRing, endRing);
            renderSegmentLayer(pose, outer, start, end, frame,
                    RopeSegmentSpec.OUTER, light, startRing, endRing);
            if (segment > 0 && RopeSegmentSpec.shouldRenderJoint(
                    frames[segment - 1], frame)) {
                renderJoint(pose, inner, start, frames[segment - 1], frame,
                        RopeSegmentSpec.OUTER.halfWidth(), lights.sample(start),
                        jointFirstRing, jointSecondRing);
            }
        }
        int startLight = lights.sample(nodes.getFirst());
        renderCap(pose, inner, nodes.getFirst(), frames[0], true,
                RopeSegmentSpec.INNER, startLight, startRing);
        renderCap(pose, outer, nodes.getFirst(), frames[0], true,
                RopeSegmentSpec.OUTER, startLight, startRing);
        int last = frames.length - 1;
        int endLight = lights.sample(nodes.getLast());
        renderCap(pose, inner, nodes.getLast(), frames[last], false,
                RopeSegmentSpec.INNER, endLight, startRing);
        renderCap(pose, outer, nodes.getLast(), frames[last], false,
                RopeSegmentSpec.OUTER, endLight, startRing);
    }

    private static void renderSegmentLayer(PoseStack.Pose pose,
            VertexConsumer vertices, Vec3 start, Vec3 end,
            RopeSegmentPose.Frame frame, RopeSegmentSpec.Cuboid cuboid, int light,
            Vec3[] startRing, Vec3[] endRing) {
        fillRing(startRing, start, frame, cuboid.halfWidth());
        fillRing(endRing, end, frame, cuboid.halfWidth());
        RopeSegmentSpec.Uv uv = cuboid.side();
        for (int side = 0; side < 4; side++) {
            int next = (side + 1) & 3;
            Vec3 outward = sideOutward(frame, side);
            jointQuad(pose, vertices, outward,
                    startRing[side], endRing[side], endRing[next], startRing[next],
                    uv.u0(), uv.v0(), uv.u1(), uv.v1(), light);
        }
    }

    /** Bridges adjacent square rings directly; no separate knot primitive is created. */
    private static void renderJoint(PoseStack.Pose pose, VertexConsumer vertices,
            Vec3 center, RopeSegmentPose.Frame incoming,
            RopeSegmentPose.Frame outgoing, double halfWidth, int light,
            Vec3[] firstRing, Vec3[] secondRing) {
        fillRing(firstRing, center, incoming, halfWidth);
        fillRing(secondRing, center, outgoing, halfWidth);
        for (int side = 0; side < 4; side++) {
            int next = (side + 1) & 3;
            Vec3 outward = jointOutward(incoming, outgoing, side);
            jointQuad(pose, vertices, outward,
                    firstRing[side], secondRing[side], secondRing[next],
                    firstRing[next],
                    0.0F, 0.0F, 4.0F / 32.0F, 4.0F / 32.0F, light);
        }
    }

    private static void jointQuad(PoseStack.Pose pose, VertexConsumer vertices,
            Vec3 expectedOutward, Vec3 a, Vec3 b, Vec3 c, Vec3 d,
            float u0, float v0, float u1, float v1, int light) {
        if (RopeSegmentSpec.facePointsAwayFrom(
                expectedOutward, a, b, c)) {
            quad(pose, vertices, d, c, b, a, u0, v0, u1, v1, light,
                    expectedOutward);
        } else {
            quad(pose, vertices, a, b, c, d, u0, v0, u1, v1, light,
                    expectedOutward);
        }
    }

    private static Vec3 jointOutward(RopeSegmentPose.Frame incoming,
            RopeSegmentPose.Frame outgoing, int side) {
        Vec3 first = sideOutward(incoming, side);
        Vec3 second = sideOutward(outgoing, side);
        Vec3 blended = first.add(second);
        return blended.lengthSqr() <= 1.0E-10D ? first : blended.normalize();
    }

    private static Vec3 sideOutward(RopeSegmentPose.Frame frame, int side) {
        return switch (side) {
            case 0 -> frame.x().scale(-1.0D);
            case 1 -> frame.z();
            case 2 -> frame.x();
            default -> frame.z().scale(-1.0D);
        };
    }

    private static void renderCap(PoseStack.Pose pose, VertexConsumer vertices,
            Vec3 center, RopeSegmentPose.Frame frame, boolean start,
            RopeSegmentSpec.Cuboid cuboid, int light, Vec3[] ring) {
        fillRing(ring, center, frame, cuboid.halfWidth());
        RopeSegmentSpec.Uv cap = cuboid.cap();
        Vec3 normal = frame.y().scale(start ? -1.0D : 1.0D);
        if (start) {
            jointQuad(pose, vertices, normal,
                    ring[3], ring[2], ring[1], ring[0],
                    cap.u0(), cap.v0(), cap.u1(), cap.v1(), light);
        } else {
            jointQuad(pose, vertices, normal,
                    ring[0], ring[1], ring[2], ring[3],
                    cap.u0(), cap.v0(), cap.u1(), cap.v1(), light);
        }
    }

    private static void fillRing(Vec3[] ring, Vec3 center,
            RopeSegmentPose.Frame frame, double halfWidth) {
        ring[0] = point(center, frame, -halfWidth, 0.0D, -halfWidth);
        ring[1] = point(center, frame, -halfWidth, 0.0D, halfWidth);
        ring[2] = point(center, frame, halfWidth, 0.0D, halfWidth);
        ring[3] = point(center, frame, halfWidth, 0.0D, -halfWidth);
    }

    private static Vec3 point(Vec3 center, RopeSegmentPose.Frame frame,
            double x, double y, double z) {
        return center.add(frame.x().scale(x))
                .add(frame.y().scale(y))
                .add(frame.z().scale(z));
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer vertices,
            Vec3 a, Vec3 b, Vec3 c, Vec3 d,
            float u0, float v0, float u1, float v1, int light) {
        quad(pose, vertices, a, b, c, d, u0, v0, u1, v1, light, null);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer vertices,
            Vec3 a, Vec3 b, Vec3 c, Vec3 d,
            float u0, float v0, float u1, float v1, int light,
            Vec3 normalOverride) {
        Vec3 normal = RopeSegmentSpec.faceNormal(a, b, c);
        if (normal.lengthSqr() <= 1.0E-10D) {
            return;
        }
        if (normalOverride != null && normalOverride.lengthSqr() > 1.0E-10D) {
            normal = normalOverride.normalize();
        }
        vertex(pose, vertices, a, u0, v1, normal, light);
        vertex(pose, vertices, b, u0, v0, normal, light);
        vertex(pose, vertices, c, u1, v0, normal, light);
        vertex(pose, vertices, d, u1, v1, normal, light);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vertices,
            Vec3 point, float u, float v, Vec3 normal, int light) {
        vertices.addVertex(pose, (float) point.x, (float) point.y, (float) point.z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static final class LightSampler {
        private static final double OUTSIDE_OFFSET = 0.08D;
        private static LightSampler shared;
        private final ClientLevel level;
        private final Map<Long, Integer> cache = new HashMap<>();
        private final BlockPos.MutableBlockPos lookup = new BlockPos.MutableBlockPos();
        private long cachedGameTime;

        private LightSampler(ClientLevel level) {
            this.level = level;
            this.cachedGameTime = level.getGameTime();
        }

        private static LightSampler forLevel(ClientLevel level) {
            if (shared == null || shared.level != level) {
                shared = new LightSampler(level);
            } else if (shared.cachedGameTime != level.getGameTime()) {
                shared.cache.clear();
                shared.cachedGameTime = level.getGameTime();
            }
            return shared;
        }

        private int sample(Vec3 point) {
            int best = light(point.x, point.y, point.z);
            for (Direction direction : Direction.values()) {
                best = brighter(best, light(
                        point.x + direction.getStepX() * OUTSIDE_OFFSET,
                        point.y + direction.getStepY() * OUTSIDE_OFFSET,
                        point.z + direction.getStepZ() * OUTSIDE_OFFSET));
            }
            return best;
        }

        private static int brighter(int first, int second) {
            return LightTexture.pack(
                    Math.max(LightTexture.block(first), LightTexture.block(second)),
                    Math.max(LightTexture.sky(first), LightTexture.sky(second)));
        }

        private int light(double x, double y, double z) {
            int blockX = net.minecraft.util.Mth.floor(x);
            int blockY = net.minecraft.util.Mth.floor(y);
            int blockZ = net.minecraft.util.Mth.floor(z);
            long key = BlockPos.asLong(blockX, blockY, blockZ);
            Integer cached = cache.get(key);
            if (cached != null) {
                return cached;
            }
            lookup.set(blockX, blockY, blockZ);
            int sampled = net.minecraft.client.renderer.LevelRenderer.getLightColor(
                    level, lookup);
            cache.put(key, sampled);
            return sampled;
        }
    }
}

package com.fish.mirebound.client.tentacle;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.client.MudTuningWandCoreTexture;
import com.fish.mirebound.client.config.MireboundClientSettings;
import com.fish.mirebound.client.config.MireboundClientSettings.ClientOption;
import com.fish.mirebound.client.tuning.MudTuningTentacleTargeting;
import com.fish.mirebound.tentacle.TentacleGrabTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class ProceduralTentacleRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            Mirebound.MOD_ID, "textures/entity/procedural_tentacle.png");
    private static final RenderType RENDER_TYPE = RenderType.entityCutoutNoCull(TEXTURE);
    private static final RenderType HIGHLIGHT_RENDER_TYPE = RenderType.lightning();
    private static final double EPSILON = 1.0E-10D;
    private static final Map<Integer, Vec3> ROOT_FRAME_NORMALS = new HashMap<>();
    private static final Map<GeometryKey, PreparedTube> FRAME_GEOMETRY = new HashMap<>();
    private static List<ClientTentacleManager.View> frameViews = List.of();
    private static ClientLevel frameLevel;
    private static long frameViewTick = Long.MIN_VALUE;
    private static int framePartialBits;
    private static float lastPartialTick;
    private static boolean renderingEnabled = true;

    private ProceduralTentacleRenderer() {
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        if (!MireboundClientSettings.clientOptionEnabled(ClientOption.TENTACLES)) {
            clearDisabledGeometry();
            return;
        }
        renderingEnabled = true;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        if (frameLevel != minecraft.level) {
            ROOT_FRAME_NORMALS.clear();
            FRAME_GEOMETRY.clear();
            frameViews = List.of();
            frameLevel = minecraft.level;
            frameViewTick = Long.MIN_VALUE;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        lastPartialTick = partialTick;
        List<ClientTentacleManager.View> views = viewsForFrame(minecraft, partialTick);
        if (views.isEmpty()) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        double renderDistanceSquared = TentacleClientSettings.renderDistance()
                * TentacleClientSettings.renderDistance();
        double lodDistanceSquared = TentacleClientSettings.lodDistance()
                * TentacleClientSettings.lodDistance();
        int sides = TentacleClientSettings.crossSectionSides();
        var target = MudTuningTentacleTargeting.target(minecraft);
        int highlightedId = target == null ? -1 : target.instanceId();
        int highlightColor = 0xFF000000 | MudTuningWandCoreTexture.hudColor(
                minecraft.level.getGameTime() + partialTick);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer vertices = buffers.getBuffer(RENDER_TYPE);
        VertexConsumer highlightVertices = null;
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        for (ClientTentacleManager.View view : views) {
            double distanceSquared = distanceSquaredToBounds(camera, view.bounds());
            if (distanceSquared > renderDistanceSquared
                    || !isVisible(minecraft, event.getFrustum(), view)) {
                continue;
            }
            int renderedSides = distanceSquared > lodDistanceSquared
                    ? Math.max(4, sides / TentacleClientSettings.lodStride()) : sides;
            renderTube(minecraft, poseStack.last(), vertices, view, renderedSides);
            if (view.id() == highlightedId) {
                PreparedTube prepared = FRAME_GEOMETRY.get(
                        new GeometryKey(view.id(), renderedSides));
                if (prepared != null) {
                    if (highlightVertices == null) {
                        highlightVertices = buffers.getBuffer(HIGHLIGHT_RENDER_TYPE);
                    }
                    renderHighlight(poseStack.last(), highlightVertices,
                            prepared.body(), highlightColor, view.age());
                }
            }
        }
        poseStack.popPose();
        buffers.endBatch(RENDER_TYPE);
        if (highlightVertices != null) {
            buffers.endBatch(HIGHLIGHT_RENDER_TYPE);
        }
    }

    public static void renderIrisShadowPass(MultiBufferSource.BufferSource buffers,
            Matrix4f modelView, Frustum shadowFrustum) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!MireboundClientSettings.clientOptionEnabled(ClientOption.TENTACLES)
                || !TentacleClientSettings.castShaderShadows()
                || minecraft.level == null) {
            return;
        }
        List<ClientTentacleManager.View> views = viewsForFrame(minecraft, lastPartialTick);
        if (views.isEmpty()) {
            return;
        }
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        double renderDistanceSquared = TentacleClientSettings.renderDistance()
                * TentacleClientSettings.renderDistance();
        double lodDistanceSquared = TentacleClientSettings.lodDistance()
                * TentacleClientSettings.lodDistance();
        int sides = TentacleClientSettings.crossSectionSides();
        VertexConsumer vertices = buffers.getBuffer(RENDER_TYPE);
        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(modelView);
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        for (ClientTentacleManager.View view : views) {
            double distanceSquared = distanceSquaredToBounds(camera, view.bounds());
            if (distanceSquared > renderDistanceSquared
                    || !isVisible(minecraft, shadowFrustum, view)) {
                continue;
            }
            int renderedSides = distanceSquared > lodDistanceSquared
                    ? Math.max(4, sides / TentacleClientSettings.lodStride()) : sides;
            renderTube(minecraft, poseStack.last(), vertices, view, renderedSides);
        }
    }

    static double distanceSquaredToBounds(Vec3 point, AABB bounds) {
        double x = Math.max(bounds.minX - point.x, Math.max(0.0D, point.x - bounds.maxX));
        double y = Math.max(bounds.minY - point.y, Math.max(0.0D, point.y - bounds.maxY));
        double z = Math.max(bounds.minZ - point.z, Math.max(0.0D, point.z - bounds.maxZ));
        return x * x + y * y + z * z;
    }

    private static void clearDisabledGeometry() {
        if (!renderingEnabled) {
            return;
        }
        renderingEnabled = false;
        ROOT_FRAME_NORMALS.clear();
        FRAME_GEOMETRY.clear();
        frameViews = List.of();
        frameLevel = null;
        frameViewTick = Long.MIN_VALUE;
    }

    private static List<ClientTentacleManager.View> viewsForFrame(
            Minecraft minecraft, float partialTick) {
        long tick = minecraft.level == null ? Long.MIN_VALUE : minecraft.level.getGameTime();
        int partialBits = Float.floatToIntBits(partialTick);
        if (frameLevel != minecraft.level || frameViewTick != tick
                || framePartialBits != partialBits) {
            if (frameLevel != minecraft.level) {
                ROOT_FRAME_NORMALS.clear();
                frameLevel = minecraft.level;
            }
            frameViewTick = tick;
            framePartialBits = partialBits;
            FRAME_GEOMETRY.clear();
            frameViews = ClientTentacleManager.views(partialTick);
            if (ROOT_FRAME_NORMALS.size() > Math.max(64, frameViews.size() * 2)) {
                HashSet<Integer> activeIds = new HashSet<>(frameViews.size());
                for (ClientTentacleManager.View view : frameViews) {
                    activeIds.add(view.id());
                }
                ROOT_FRAME_NORMALS.keySet().retainAll(activeIds);
            }
        }
        return frameViews;
    }

    private static boolean isVisible(Minecraft minecraft, Frustum frustum,
            ClientTentacleManager.View view) {
        if (frustum == null) {
            return true;
        }
        List<Vec3> points = view.points();
        double radius = Math.max(view.rootRadius(), view.tipRadius())
                * (1.0D + TentacleClientSettings.surfaceVariation()
                        + TentacleClientSettings.pulseAmplitude())
                + 0.10D;
        for (int index = 1; index < points.size(); index++) {
            if (frustum.isVisible(new AABB(points.get(index - 1), points.get(index)).inflate(radius))) {
                return true;
            }
        }
        if (view.grabbedEntityId() >= 0 && minecraft.level != null) {
            net.minecraft.world.entity.Entity grabbed = minecraft.level.getEntity(view.grabbedEntityId());
            if (grabbed != null && frustum.isVisible(grabbed.getBoundingBoxForCulling()
                    .inflate(radius + grabbed.getBbWidth()))) {
                return true;
            }
        }
        return false;
    }

    private static void renderTube(Minecraft minecraft, PoseStack.Pose pose, VertexConsumer vertices,
            ClientTentacleManager.View view, int sides) {
        GeometryKey key = new GeometryKey(view.id(), sides);
        PreparedTube prepared = FRAME_GEOMETRY.get(key);
        if (prepared == null) {
            prepared = prepareTube(minecraft, view, sides);
            if (prepared == null) {
                return;
            }
            FRAME_GEOMETRY.put(key, prepared);
        }
        TubeGeometry body = prepared.body();
        List<Vec3> points = body.points();
        Frame[] frames = body.frames();
        Vec3[][] rings = body.rings();

        renderRootCap(pose, vertices, points.getFirst(), rings[0], frames[0], sides,
                LevelRenderer.getLightColor(minecraft.level, BlockPos.containing(points.getFirst())));
        renderTubeSides(minecraft, pose, vertices, body);
        if (prepared.capBodyTip()) {
            renderTip(pose, vertices, points.getLast(), rings[rings.length - 1],
                    frames[frames.length - 1].tangent, sides,
                    LevelRenderer.getLightColor(minecraft.level, BlockPos.containing(points.getLast())),
                    prepared.tipLength());
        }
        if (!prepared.wraps().isEmpty()) {
            renderGrabWrap(minecraft, pose, vertices, prepared.wraps());
        }
    }

    private static PreparedTube prepareTube(Minecraft minecraft,
            ClientTentacleManager.View view, int sides) {
        GrabWrapGeometry grabWrap = buildGrabWrapGeometry(minecraft, view);
        List<Vec3> points = view.points();
        if (points.size() < 2) {
            return null;
        }
        Frame[] frames = frames(points,
                stableRootNormal(view.id(), tangent(points, 0), view.visualSeed()));
        Vec3[][] rings = new Vec3[points.size()][sides];
        double[] distance = new double[points.size()];
        for (int index = 1; index < points.size(); index++) {
            distance[index] = distance[index - 1] + points.get(index - 1).distanceTo(points.get(index));
        }
        double taperStart = TentacleClientSettings.tipTaperStart();
        double tipRingScale = TentacleClientSettings.tipRingScale();
        double surfaceVariation = TentacleClientSettings.surfaceVariation();
        double pulseSpeed = TentacleClientSettings.pulseSpeed();
        double pulseAmplitude = TentacleClientSettings.pulseAmplitude();
        double seedPhase = (view.visualSeed() & 0xFFFFL) * (Math.PI * 2.0D / 65536.0D);
        for (int index = 0; index < points.size(); index++) {
            double fraction = index / (double) (points.size() - 1);
            double shapedFraction = Math.pow(fraction, 0.82D);
            double radius = view.rootRadius()
                    + (view.tipRadius() - view.rootRadius()) * shapedFraction;
            if (fraction > taperStart) {
                double taper = (fraction - taperStart) / Math.max(0.001D, 1.0D - taperStart);
                taper = taper * taper * (3.0D - 2.0D * taper);
                radius *= 1.0D + (tipRingScale - 1.0D) * taper;
            }
            double stableShape = Math.sin(fraction * Math.PI * 6.0D + seedPhase)
                    * surfaceVariation;
            double pulse = Math.sin(view.age() * pulseSpeed
                    - fraction * Math.PI * 2.5D + seedPhase)
                    * pulseAmplitude;
            radius *= Math.max(0.72D, 1.0D + stableShape + pulse);
            for (int side = 0; side < sides; side++) {
                double angle = (side + 0.5D) * Math.PI * 2.0D / sides;
                Vec3 radial = frames[index].normal.scale(Math.cos(angle))
                        .add(frames[index].binormal.scale(Math.sin(angle)));
                rings[index][side] = points.get(index).add(radial.scale(radius));
            }
        }
        TubeGeometry body = new TubeGeometry(
                List.copyOf(points), frames, rings, distance, sides, 0.0D);
        List<TubeGeometry> wraps = new ArrayList<>();
        boolean directWrapAttached = false;
        if (grabWrap != null) {
            int wrapSides = Math.max(6, sides);
            for (int strand = 0; strand < grabWrap.strands().size(); strand++) {
                GrabStrand coil = grabWrap.strands().get(strand);
                long seed = view.visualSeed() ^ 0x657261705F746970L
                        ^ (strand * 0x9E3779B97F4A7C15L);
                TubeGeometry geometry = prepareAuxiliaryTube(
                        coil.points(), coil.radii(), wrapSides, seed, true);
                if (geometry != null) {
                    wraps.add(geometry);
                    directWrapAttached |= strand == 0
                            && geometry.points().getFirst().equals(points.getLast());
                }
            }
        }
        return new PreparedTube(body, List.copyOf(wraps),
                view.tipRadius() * TentacleClientSettings.tipLengthScale(),
                grabWrap == null || !directWrapAttached);
    }

    private static void renderTubeSides(Minecraft minecraft, PoseStack.Pose pose,
            VertexConsumer vertices, TubeGeometry geometry) {
        List<Vec3> points = geometry.points();
        Vec3[][] rings = geometry.rings();
        double[] distance = geometry.distance();
        int sides = geometry.sides();
        for (int index = 0; index < points.size() - 1; index++) {
            Vec3 middle = points.get(index).add(points.get(index + 1)).scale(0.5D);
            int light = LevelRenderer.getLightColor(minecraft.level, BlockPos.containing(middle));
            float v0 = (float) (distance[index] * 0.75D);
            float v1 = (float) (distance[index + 1] * 0.75D);
            for (int side = 0; side < sides; side++) {
                int next = (side + 1) % sides;
                Vec3 normal = rings[index][side].subtract(points.get(index))
                        .add(rings[index][next].subtract(points.get(index))).normalize();
                quad(pose, vertices,
                        rings[index][side], rings[index + 1][side],
                        rings[index + 1][next], rings[index][next],
                        side / (float) sides, (side + 1) / (float) sides,
                        v0, v1, light, normal);
            }
        }
    }

    private static void renderHighlight(PoseStack.Pose pose, VertexConsumer vertices,
            TubeGeometry geometry, int color, double age) {
        renderHighlightLayer(pose, vertices, geometry, color,
                1.10D + Math.sin(age * 0.18D) * 0.012D,
                Mth.clamp(Math.round(176.0F
                        + 34.0F * (float) Math.sin(age * 0.24D)), 138, 216));
        renderHighlightLayer(pose, vertices, geometry, color,
                1.19D + Math.sin(age * 0.18D) * 0.018D,
                Mth.clamp(Math.round(92.0F
                        + 22.0F * (float) Math.sin(age * 0.24D)), 64, 120));
    }

    private static void renderHighlightLayer(PoseStack.Pose pose, VertexConsumer vertices,
            TubeGeometry geometry, int color, double scale, int alpha) {
        List<Vec3> centers = geometry.points();
        Vec3[][] sourceRings = geometry.rings();
        int sides = geometry.sides();
        Vec3[][] rings = new Vec3[sourceRings.length][sides];
        for (int index = 0; index < sourceRings.length; index++) {
            Vec3 center = centers.get(index);
            for (int side = 0; side < sides; side++) {
                rings[index][side] = center.add(
                        sourceRings[index][side].subtract(center).scale(scale));
            }
        }
        int red = color >> 16 & 0xFF;
        int green = color >> 8 & 0xFF;
        int blue = color & 0xFF;
        for (int index = 0; index < rings.length - 1; index++) {
            for (int side = 0; side < sides; side++) {
                int next = (side + 1) % sides;
                highlightQuad(pose, vertices,
                        rings[index][side], rings[index + 1][side],
                        rings[index + 1][next], rings[index][next],
                        red, green, blue, alpha);
            }
        }
    }

    private static void highlightQuad(PoseStack.Pose pose, VertexConsumer vertices,
            Vec3 first, Vec3 second, Vec3 third, Vec3 fourth,
            int red, int green, int blue, int alpha) {
        highlightVertex(pose, vertices, first, red, green, blue, alpha);
        highlightVertex(pose, vertices, second, red, green, blue, alpha);
        highlightVertex(pose, vertices, third, red, green, blue, alpha);
        highlightVertex(pose, vertices, fourth, red, green, blue, alpha);
    }

    private static void highlightVertex(PoseStack.Pose pose, VertexConsumer vertices,
            Vec3 point, int red, int green, int blue, int alpha) {
        vertices.addVertex(pose, (float) point.x, (float) point.y, (float) point.z)
                .setColor(red, green, blue, alpha);
    }

    private static GrabWrapGeometry buildGrabWrapGeometry(
            Minecraft minecraft, ClientTentacleManager.View view) {
        ClientTentacleManager.RagdollPose pose = view.grabPose();
        if (!TentacleClientSettings.grabWrapEnabled() || view.grabbedEntityId() < 0
                || view.grabIntensity() <= 0.05F || pose == null
                || pose.grabTarget() != TentacleGrabTarget.WHOLE_BODY
                || minecraft.level == null) {
            return null;
        }
        net.minecraft.world.entity.Entity entity = minecraft.level.getEntity(view.grabbedEntityId());
        if (entity == null || view.points().isEmpty()) {
            return null;
        }
        Vec3 center = entity.getPosition(lastPartialTick)
                .add(0.0D, entity.getBbHeight() * 0.5D, 0.0D);
        Vec3 axis = TentaclePoseTransforms.bodyAxis(pose);
        if (axis.lengthSqr() <= EPSILON) {
            return null;
        }
        axis = axis.normalize();
        Vec3 wrapCenter = center;
        double targetRadius = entity.getBbWidth() * 0.50D;
        int strandCount = TentacleClientSettings.grabWrapStrands();
        double wrapProgress = Mth.clamp(view.grabIntensity(), 0.0F, 1.0F);
        wrapProgress = wrapProgress * wrapProgress * (3.0D - 2.0D * wrapProgress);
        double strandRadius = Math.min(view.tipRadius() * 0.62D, targetRadius * 0.44D)
                * TentacleClientSettings.grabWrapStrandRadiusScale()
                / Math.sqrt(strandCount);
        double wrapRadius = Mth.lerp(wrapProgress, strandRadius * 1.2D,
                (targetRadius + strandRadius * 0.72D)
                        * TentacleClientSettings.grabWrapRadiusScale());
        double axialLength = entity.getBbHeight() * 0.70D
                * TentacleClientSettings.grabWrapLengthScale() * wrapProgress;
        int segments = TentacleClientSettings.grabWrapSegments();
        double turns = TentacleClientSettings.grabWrapTurns()
                * TentacleClientSettings.grabWrapWholeBodyTurnsScale()
                * wrapProgress;
        double halfLength = axialLength * 0.5D;
        TentacleWrapStability.AxialSpan span =
                TentacleWrapStability.fullBodySpan(halfLength);
        double primaryEntryAxial = Mth.clamp(
                view.points().getLast().subtract(wrapCenter).dot(axis),
                span.start(), span.end());
        double direction = (view.visualSeed() & 1L) == 0L ? 1.0D : -1.0D;
        Vec3 stableNormal = stableWrapNormal(pose, axis);
        List<GrabStrand> strands = new ArrayList<>(strandCount);
        for (int strand = 0; strand < strandCount; strand++) {
            double phase = Math.PI * 2.0D * strand / strandCount;
            Vec3 strandNormal = TentacleWrapStability.strandNormal(
                    stableNormal, axis, phase);
            Vec3 strandBinormal = axis.cross(strandNormal).normalize();
            double axialOffset = strandCount <= 1 ? 0.0D
                    : axialLength * 0.08D * (strand - (strandCount - 1) * 0.5D)
                            / (strandCount - 1);
            GrabStrand coil = strand == 0
                    ? buildGrabStrandFromEntry(
                            wrapCenter, axis, strandNormal, strandBinormal,
                            strandRadius, wrapRadius, primaryEntryAxial,
                            span.start(), span.end(), turns, direction, segments)
                    : buildGrabStrand(
                            wrapCenter, axis, strandNormal, strandBinormal,
                            strandRadius, wrapRadius, span.start() + axialOffset,
                            span.end() + axialOffset, turns, direction, segments);
            double joinRadius = view.tipRadius() * TentacleClientSettings.tipRingScale()
                    / Math.sqrt(Math.max(1, strandCount));
            if (strand == 0) {
                GrabWrapDirectGeometry.Strand attached = GrabWrapDirectGeometry.attach(
                        view.points().getLast(), coil.points(), coil.radii(),
                        wrapCenter, axis, span.start(), span.end(), joinRadius);
                strands.add(new GrabStrand(attached.points(), attached.radii()));
            } else {
                strands.add(coil);
            }
        }
        return strands.isEmpty() ? null
                : new GrabWrapGeometry(List.copyOf(strands));
    }

    private static void renderGrabWrap(Minecraft minecraft, PoseStack.Pose renderPose,
            VertexConsumer vertices, List<TubeGeometry> geometries) {
        for (TubeGeometry geometry : geometries) {
            renderAuxiliaryTube(minecraft, renderPose, vertices, geometry);
        }
    }

    private static GrabStrand buildGrabStrand(
            Vec3 wrapCenter, Vec3 axis, Vec3 firstBasis, Vec3 secondBasis,
            double strandRadius, double wrapRadius, double startAxial, double endAxial,
            double turns, double direction, int segments) {
        List<Vec3> points = new ArrayList<>(segments + 1);
        List<Double> radii = new ArrayList<>(segments + 1);
        for (int index = 0; index <= segments; index++) {
            double amount = index / (double) segments;
            double smooth = amount * amount * (3.0D - 2.0D * amount);
            double angle = direction * smooth * turns * Math.PI * 2.0D;
            double axial = Mth.lerp(smooth, startAxial, endAxial);
            double coilRadius = wrapRadius * (1.0D - amount * 0.035D);
            Vec3 ringCenter = wrapCenter.add(axis.scale(axial));
            points.add(ringCenter
                    .add(firstBasis.scale(Math.cos(angle) * coilRadius))
                    .add(secondBasis.scale(Math.sin(angle) * coilRadius)));
            double endTaper = Math.min(1.0D, Math.min(amount, 1.0D - amount) * 5.0D);
            radii.add(strandRadius * Mth.lerp(endTaper, 0.72D, 1.0D));
        }
        return new GrabStrand(List.copyOf(points), List.copyOf(radii));
    }

    private static GrabStrand buildGrabStrandFromEntry(
            Vec3 wrapCenter, Vec3 axis, Vec3 firstBasis, Vec3 secondBasis,
            double strandRadius, double wrapRadius, double entryAxial,
            double startAxial, double endAxial,
            double turns, double direction, int segments) {
        List<Vec3> points = new ArrayList<>(segments + 1);
        List<Double> radii = new ArrayList<>(segments + 1);
        double entry = Mth.clamp(entryAxial,
                Math.min(startAxial, endAxial), Math.max(startAxial, endAxial));
        double toBottom = Math.abs(entry - startAxial);
        double fullSpan = Math.abs(endAxial - startAxial);
        double turnAmount = fullSpan <= EPSILON ? 0.0D
                : toBottom / Math.max(EPSILON, toBottom + fullSpan);
        for (int index = 0; index <= segments; index++) {
            double amount = index / (double) segments;
            double smooth = amount * amount * (3.0D - 2.0D * amount);
            double axial;
            if (turnAmount > EPSILON && amount < turnAmount) {
                double local = amount / turnAmount;
                local = local * local * (3.0D - 2.0D * local);
                axial = Mth.lerp(local, entry, startAxial);
            } else {
                double local = turnAmount >= 1.0D - EPSILON ? 1.0D
                        : (amount - turnAmount) / (1.0D - turnAmount);
                local = Mth.clamp(local, 0.0D, 1.0D);
                local = local * local * (3.0D - 2.0D * local);
                axial = Mth.lerp(local, startAxial, endAxial);
            }
            double angle = direction * smooth * turns * Math.PI * 2.0D;
            double coilRadius = wrapRadius * (1.0D - amount * 0.035D);
            Vec3 ringCenter = wrapCenter.add(axis.scale(axial));
            points.add(ringCenter
                    .add(firstBasis.scale(Math.cos(angle) * coilRadius))
                    .add(secondBasis.scale(Math.sin(angle) * coilRadius)));
            double endTaper = Math.min(1.0D, Math.min(amount, 1.0D - amount) * 5.0D);
            radii.add(strandRadius * Mth.lerp(endTaper, 0.72D, 1.0D));
        }
        return new GrabStrand(List.copyOf(points), List.copyOf(radii));
    }

    private static Vec3 stableWrapNormal(ClientTentacleManager.RagdollPose pose, Vec3 axis) {
        var worldBody = TentaclePoseTransforms.worldBodyOrientation(pose);
        Vector3f forwardVector = worldBody.transform(new Vector3f(0.0F, 0.0F, 1.0F));
        Vec3 forward = new Vec3(forwardVector.x, forwardVector.y, forwardVector.z);
        Vec3 projected = forward.subtract(axis.scale(forward.dot(axis)));
        if (projected.lengthSqr() <= EPSILON) {
            Vector3f rightVector = worldBody.transform(new Vector3f(1.0F, 0.0F, 0.0F));
            Vec3 right = new Vec3(rightVector.x, rightVector.y, rightVector.z);
            projected = right.subtract(axis.scale(right.dot(axis)));
        }
        return projected.lengthSqr() <= EPSILON ? new Vec3(1.0D, 0.0D, 0.0D) : projected.normalize();
    }

    private static TubeGeometry prepareAuxiliaryTube(
            List<Vec3> points, List<Double> radii,
            int sides, long seed, boolean capEnd) {
        if (points.size() < 2 || points.size() != radii.size()) {
            return null;
        }
        Frame[] frames = frames(points, seededNormal(tangent(points, 0), seed));
        Vec3[][] rings = new Vec3[points.size()][sides];
        double[] distance = new double[points.size()];
        for (int index = 1; index < points.size(); index++) {
            distance[index] = distance[index - 1] + points.get(index - 1).distanceTo(points.get(index));
        }
        for (int index = 0; index < points.size(); index++) {
            double radius = Math.max(0.006D, radii.get(index));
            for (int side = 0; side < sides; side++) {
                double angle = (side + 0.5D) * Math.PI * 2.0D / sides;
                Vec3 radial = frames[index].normal.scale(Math.cos(angle))
                        .add(frames[index].binormal.scale(Math.sin(angle)));
                rings[index][side] = points.get(index).add(radial.scale(radius));
            }
        }
        double capLength = capEnd
                ? Math.max(0.015D, radii.getLast() * 1.7D) : 0.0D;
        return new TubeGeometry(List.copyOf(points), frames, rings, distance, sides, capLength);
    }

    private static void renderAuxiliaryTube(Minecraft minecraft, PoseStack.Pose pose,
            VertexConsumer vertices, TubeGeometry geometry) {
        renderTubeSides(minecraft, pose, vertices, geometry);
        if (geometry.capLength() > 0.0D) {
            int last = geometry.points().size() - 1;
            renderTip(pose, vertices, geometry.points().get(last),
                    geometry.rings()[last], geometry.frames()[last].tangent,
                    geometry.sides(), LevelRenderer.getLightColor(minecraft.level,
                            BlockPos.containing(geometry.points().get(last))),
                    geometry.capLength());
        }
    }

    private static void renderRootCap(PoseStack.Pose pose, VertexConsumer vertices,
            Vec3 center, Vec3[] equator, Frame frame, int sides, int light) {
        int latitudeRings = TentacleClientSettings.rootCapRings();
        double lengthScale = TentacleClientSettings.rootCapLengthScale();
        double radius = equator[0].distanceTo(center);
        Vec3[] previous = equator;
        for (int latitude = 1; latitude <= latitudeRings; latitude++) {
            double angle = latitude * Math.PI * 0.5D / (latitudeRings + 1.0D);
            double axial = -Math.sin(angle) * radius * lengthScale;
            double ringRadius = Math.cos(angle) * radius;
            Vec3 ringCenter = center.add(frame.tangent.scale(axial));
            Vec3[] current = new Vec3[sides];
            for (int side = 0; side < sides; side++) {
                double sideAngle = (side + 0.5D) * Math.PI * 2.0D / sides;
                Vec3 radial = frame.normal.scale(Math.cos(sideAngle))
                        .add(frame.binormal.scale(Math.sin(sideAngle)));
                current[side] = ringCenter.add(radial.scale(ringRadius));
            }
            float v0 = (latitude - 1) / (float) (latitudeRings + 1);
            float v1 = latitude / (float) (latitudeRings + 1);
            for (int side = 0; side < sides; side++) {
                int next = (side + 1) % sides;
                float u0 = side / (float) sides;
                float u1 = (side + 1) / (float) sides;
                rootQuad(pose, vertices,
                        previous[side], previous[next], current[next], current[side],
                        u0, u1, v0, v1, light, center, frame.tangent, lengthScale);
            }
            previous = current;
        }

        Vec3 pole = center.add(frame.tangent.scale(-radius * lengthScale));
        for (int side = 0; side < sides; side++) {
            int next = (side + 1) % sides;
            float u0 = side / (float) sides;
            float u1 = (side + 1) / (float) sides;
            float v = latitudeRings / (float) (latitudeRings + 1);
            rootVertex(pose, vertices, previous[side], u0, v, light,
                    center, frame.tangent, lengthScale);
            rootVertex(pose, vertices, previous[next], u1, v, light,
                    center, frame.tangent, lengthScale);
            vertex(pose, vertices, pole, (u0 + u1) * 0.5F, 1.0F, light,
                    frame.tangent.scale(-1.0D));
            rootVertex(pose, vertices, previous[side], u0, v, light,
                    center, frame.tangent, lengthScale);
        }
    }

    private static void rootQuad(PoseStack.Pose pose, VertexConsumer vertices,
            Vec3 a, Vec3 b, Vec3 c, Vec3 d, float u0, float u1, float v0, float v1,
            int light, Vec3 center, Vec3 tangent, double lengthScale) {
        rootVertex(pose, vertices, a, u0, v0, light, center, tangent, lengthScale);
        rootVertex(pose, vertices, b, u1, v0, light, center, tangent, lengthScale);
        rootVertex(pose, vertices, c, u1, v1, light, center, tangent, lengthScale);
        rootVertex(pose, vertices, d, u0, v1, light, center, tangent, lengthScale);
    }

    private static void rootVertex(PoseStack.Pose pose, VertexConsumer vertices, Vec3 point,
            float u, float v, int light, Vec3 center, Vec3 tangent, double lengthScale) {
        Vec3 offset = point.subtract(center);
        double axial = offset.dot(tangent);
        Vec3 radial = offset.subtract(tangent.scale(axial));
        Vec3 normal = radial.add(tangent.scale(axial / (lengthScale * lengthScale))).normalize();
        vertex(pose, vertices, point, u, v, light, normal);
    }

    private static void renderTip(PoseStack.Pose pose, VertexConsumer vertices, Vec3 center,
            Vec3[] ring, Vec3 tangent, int sides, int light, double tipLength) {
        Vec3 apex = center.add(tangent.scale(Math.max(EPSILON, tipLength)));
        for (int side = 0; side < sides; side++) {
            int next = (side + 1) % sides;
            Vec3 faceNormal = ring[next].subtract(apex).cross(ring[side].subtract(apex));
            faceNormal = faceNormal.lengthSqr() < EPSILON ? tangent : faceNormal.normalize();
            vertex(pose, vertices, apex, 0.5F, 0.0F, light, faceNormal);
            vertex(pose, vertices, ring[side], side / (float) sides, 1.0F, light, faceNormal);
            vertex(pose, vertices, ring[next], (side + 1) / (float) sides, 1.0F, light, faceNormal);
            vertex(pose, vertices, apex, 0.5F, 0.0F, light, faceNormal);
        }
    }

    private static Frame[] frames(List<Vec3> points, Vec3 initialNormal) {
        Frame[] frames = new Frame[points.size()];
        Vec3 previousNormal = null;
        for (int index = 0; index < points.size(); index++) {
            Vec3 tangent = tangent(points, index);
            Vec3 normal;
            if (previousNormal == null) {
                normal = initialNormal.subtract(tangent.scale(initialNormal.dot(tangent)));
                normal = normal.lengthSqr() < EPSILON
                        ? seededNormal(tangent, 0L) : normal.normalize();
            } else {
                normal = previousNormal.subtract(tangent.scale(previousNormal.dot(tangent)));
                if (normal.lengthSqr() < EPSILON) {
                    normal = seededNormal(tangent, index);
                }
                normal = normal.normalize();
            }
            Vec3 binormal = tangent.cross(normal).normalize();
            frames[index] = new Frame(tangent, normal, binormal);
            previousNormal = normal;
        }
        return frames;
    }

    private static Vec3 stableRootNormal(int id, Vec3 tangent, long seed) {
        Vec3 previous = ROOT_FRAME_NORMALS.get(id);
        Vec3 normal = seededNormal(tangent, seed);
        if (previous != null && previous.dot(normal) < 0.0D) {
            normal = normal.scale(-1.0D);
        }
        ROOT_FRAME_NORMALS.put(id, normal);
        return normal;
    }

    private static Vec3 seededNormal(Vec3 tangent, long seed) {
        double phase = (seed & 0xFFFFL) * (Math.PI * 2.0D / 65536.0D);
        Vec3 reference = new Vec3(
                Math.cos(phase) * 0.72D,
                0.45D,
                Math.sin(phase) * 0.72D).normalize();
        Vec3 normal = reference.subtract(tangent.scale(reference.dot(tangent)));
        if (normal.lengthSqr() < EPSILON) {
            reference = new Vec3(-reference.z, reference.x, reference.y);
            normal = reference.subtract(tangent.scale(reference.dot(tangent)));
        }
        if (normal.lengthSqr() < EPSILON) {
            reference = Math.abs(tangent.x) < 0.8D
                    ? new Vec3(1.0D, 0.0D, 0.0D)
                    : new Vec3(0.0D, 0.0D, 1.0D);
            normal = reference.subtract(tangent.scale(reference.dot(tangent)));
        }
        return normal.normalize();
    }

    private static Vec3 tangent(List<Vec3> points, int index) {
        Vec3 tangent = index == 0 ? points.get(1).subtract(points.getFirst())
                : index == points.size() - 1 ? points.getLast().subtract(points.get(index - 1))
                : points.get(index + 1).subtract(points.get(index - 1));
        return tangent.lengthSqr() < EPSILON ? new Vec3(0.0D, 1.0D, 0.0D) : tangent.normalize();
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer vertices,
            Vec3 a, Vec3 b, Vec3 c, Vec3 d,
            float u0, float u1, float v0, float v1, int light, Vec3 normal) {
        vertex(pose, vertices, a, u0, v0, light, normal);
        vertex(pose, vertices, b, u0, v1, light, normal);
        vertex(pose, vertices, c, u1, v1, light, normal);
        vertex(pose, vertices, d, u1, v0, light, normal);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vertices,
            Vec3 point, float u, float v, int light, Vec3 normal) {
        vertices.addVertex(pose, (float) point.x, (float) point.y, (float) point.z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private record GrabStrand(List<Vec3> points, List<Double> radii) {
    }

    private record GrabWrapGeometry(List<GrabStrand> strands) {
    }

    private record GeometryKey(int instanceId, int sides) {
    }

    private record PreparedTube(TubeGeometry body, List<TubeGeometry> wraps,
            double tipLength, boolean capBodyTip) {
    }

    private record TubeGeometry(List<Vec3> points, Frame[] frames, Vec3[][] rings,
            double[] distance, int sides, double capLength) {
    }

    private record Frame(Vec3 tangent, Vec3 normal, Vec3 binormal) {
    }
}

package com.fish.mirebound.client;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.adaptive.MudVisualSource;
import com.fish.mirebound.client.compat.ClientRenderCompat;
import com.fish.mirebound.mud.MudBehaviorContext;
import com.fish.mirebound.mud.MudMediumRuntime;
import com.fish.mirebound.mud.MudPhysicsParameter;
import com.fish.mirebound.mud.AdhesionStrandProfile;
import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.mud.TenderFleshMechanics;
import com.fish.mirebound.mud.TenderFleshProfile;
import com.fish.mirebound.mud.TenderFleshPoolRules;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Batched 2.5D holes, raised pixel rims, and low-poly mud bubbles. */
final class MudSurfaceEffectRenderer {
    private static final double PIXEL = 1.0D / 16.0D;
    private static final double SURFACE_DECAL_NORMAL_OFFSET = PIXEL * 0.10D;
    private static final int TENDER_FLESH_PILLAR_COUNT = 4;
    private static final int TENDER_FLESH_MEMBRANE_SLICES = 4;
    private static final int TENDER_FLESH_MAX_SEGMENTS = 10;
    private static final ResourceLocation TENDER_FLESH_BROKEN_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(
                    Mirebound.MOD_ID, "textures/block/tender_flesh_broken.png");
    private static final Vec3[] BRIDGE_CENTERS = bridgeScratch();
    private static final Vec3[] BRIDGE_SIDE_A = bridgeScratch();
    private static final Vec3[] BRIDGE_SIDE_B = bridgeScratch();
    private static final MudSurfaceEffectManager.AdhesionStrand[] SHEET_RIBS =
            new MudSurfaceEffectManager.AdhesionStrand[
                    MudSurfaceEffectManager.MAX_ADHESION_STRANDS_PER_PLAYER];
    private static final Set<RenderType> STRAND_RENDER_TYPES = new HashSet<>();
    private static final Set<ResourceLocation> FLESH_TEXTURES = new HashSet<>();
    private static final Set<ResourceLocation> FLESH_MEMBRANE_TEXTURES = new HashSet<>();
    private static final List<MudSurfaceEffectManager.Hole> VISIBLE_SURFACE_HOLES =
            new ArrayList<>();
    private static final IdentityHashMap<Object,
            com.fish.mirebound.compat.sable.SableCompat.AffineTransform> SABLE_TRANSFORMS =
            new IdentityHashMap<>();
    private static final Long2BooleanOpenHashMap SUPPORT_VALIDITY = new Long2BooleanOpenHashMap();
    private static final Long2DoubleOpenHashMap SURFACE_DARKENING =
            new Long2DoubleOpenHashMap();
    private static final Long2BooleanOpenHashMap SURFACE_FLESH =
            new Long2BooleanOpenHashMap();
    private static final Long2BooleanOpenHashMap SURFACE_TILE_VISIBILITY =
            new Long2BooleanOpenHashMap();
    private static final Long2IntOpenHashMap SURFACE_LIGHT =
            new Long2IntOpenHashMap();
    private static final Long2ObjectOpenHashMap<MudSurfaceAppearance.Appearance>[]
            SURFACE_DECAL_APPEARANCES = appearanceMaps();
    private static final Long2ObjectOpenHashMap<MudSurfaceAppearance.Appearance>[]
            SURFACE_PILE_APPEARANCES = appearanceMaps();
    private static final Vec3[][] TENDER_FLESH_PILLAR_POINTS =
            new Vec3[TENDER_FLESH_PILLAR_COUNT][TENDER_FLESH_MAX_SEGMENTS + 1];
    private static final Vec3[][] TENDER_FLESH_RING_POINTS =
            new Vec3[TENDER_FLESH_MAX_SEGMENTS + 1][4];

    private MudSurfaceEffectRenderer() {
    }

    static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES
                || ClientRenderCompat.isRenderingShaderShadowPass()
                || !MudSurfaceClientSettings.enabled()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        double renderDistanceSquared = Mth.square(MudSurfaceClientSettings.renderDistance());
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        PoseStack poseStack = event.getPoseStack();
        PoseStack.Pose pose = poseStack.last();
        Set<RenderType> strandRenderTypes = STRAND_RENDER_TYPES;
        Set<ResourceLocation> fleshTextures = FLESH_TEXTURES;
        Set<ResourceLocation> fleshMembraneTextures = FLESH_MEMBRANE_TEXTURES;
        IdentityHashMap<Object, com.fish.mirebound.compat.sable.SableCompat.AffineTransform>
                sableTransforms = SABLE_TRANSFORMS;
        strandRenderTypes.clear();
        fleshTextures.clear();
        fleshMembraneTextures.clear();
        sableTransforms.clear();
        SURFACE_LIGHT.clear();
        List<MudSurfaceEffectManager.Hole> visibleSurfaceHoles = VISIBLE_SURFACE_HOLES;
        visibleSurfaceHoles.clear();
        MudSurfaceRenderBatchCache.beginFrame();

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        pose = poseStack.last();
        for (MudSurfaceEffectManager.Hole hole : MudSurfaceEffectManager.holes()) {
            Vec3 center = hole.center;
            double visibility = Mth.lerp(partialTick, hole.previousVisibility, hole.visibility);
            if (!hole.cells.isEmpty()) {
                AABB bounds = hole.surfaceBounds();
                if (distanceToSqr(bounds, camera) <= renderDistanceSquared
                        && (event.getFrustum() == null
                                || event.getFrustum().isVisible(bounds))) {
                    visibleSurfaceHoles.add(hole);
                }
            }
            if (hole.fleshTemplateEnabled
                    && (visibility > 0.003D || hole.fleshPillarProgress > 0.003D)
                    && center.distanceToSqr(camera) <= renderDistanceSquared
                    && renderTenderFleshTentacles(
                            minecraft, pose, buffers, hole, partialTick,
                            fleshTextures, fleshMembraneTextures)) {
                // Texture batches are collected by the renderer above.
            }
        }
        int localPlayerId = minecraft.player == null
                ? Integer.MIN_VALUE : minecraft.player.getId();
        visibleSurfaceHoles.sort((first, second) -> {
            boolean firstLocal = first.entityId == localPlayerId;
            boolean secondLocal = second.entityId == localPlayerId;
            if (firstLocal != secondLocal) {
                return firstLocal ? -1 : 1;
            }
            return Double.compare(
                    distanceToSqr(first.surfaceBounds(), camera),
                    distanceToSqr(second.surfaceBounds(), camera));
        });
        int remainingSurfaceCells = MudSurfaceCellBudget.globalRenderLimit(
                MudSurfaceClientSettings.maxSurfaceCells(),
                MudSurfaceClientSettings.maxHoles());
        int perHoleSurfaceCells = MudSurfaceCellBudget.renderCellLimitPerHole(
                MudSurfaceClientSettings.maxSurfaceCells());
        for (MudSurfaceEffectManager.Hole hole : visibleSurfaceHoles) {
            int availableCells = Math.min(
                    remainingSurfaceCells, perHoleSurfaceCells);
            if (availableCells <= 0) {
                break;
            }
            remainingSurfaceCells -= renderSurfaceField(
                    minecraft, pose, buffers, hole, partialTick,
                    camera, renderDistanceSquared, event.getFrustum(), availableCells);
        }
        for (MudSideSurfaceEffectManager.SideImprint imprint
                : MudSideSurfaceEffectManager.imprints()) {
            MudSideSurfaceEffectManager.FaceBasis basis =
                    MudSideSurfaceEffectManager.renderBasis(
                            imprint, sableTransforms);
            Vec3 center = basis.origin
                    .add(basis.axisU.scale(0.5D))
                    .add(basis.axisV.scale(0.5D));
            if (center.distanceToSqr(camera) > renderDistanceSquared) {
                continue;
            }
            AABB bounds = new AABB(center, center).inflate(0.78D);
            if (event.getFrustum() != null && !event.getFrustum().isVisible(bounds)) {
                continue;
            }
            renderSideField(minecraft, pose, buffers, imprint, basis, partialTick,
                    camera, renderDistanceSquared);
        }

        for (MudSurfaceEffectManager.Bubble bubble : MudSurfaceEffectManager.bubbles()) {
            if (!bubble.active || bubble.center.distanceToSqr(camera) > renderDistanceSquared) {
                continue;
            }
            AABB bounds = new AABB(bubble.center, bubble.center).inflate(bubble.radius * 2.8D + 0.04D);
            if (event.getFrustum() != null && !event.getFrustum().isVisible(bounds)) {
                continue;
            }
            renderBubble(minecraft, pose, buffers, bubble, partialTick);
        }
        for (MudSurfaceEffectManager.Hole hole : MudSurfaceEffectManager.holes()) {
            AdhesionStrandProfile currentProfile = MudMediumRuntime.adhesionStrands(
                    minecraft.level, hole.profilePos, hole.medium);
            AdhesionStrandProfile profile = MudSurfaceEffectManager.adhesionLifecycleProfile(
                    hole.adhesionProfile, currentProfile,
                    hasActiveAdhesionStrand(hole));
            if (!profile.enabled()) {
                continue;
            }
            if (profile.sheetEnabled()
                    && hole.center.distanceToSqr(camera) <= renderDistanceSquared) {
                renderAdhesionSheet(minecraft, pose, buffers, hole, profile, partialTick);
            }
            for (MudSurfaceEffectManager.AdhesionStrand strand : hole.adhesionStrands) {
                if (!strand.active) {
                    continue;
                }
                Vec3 start = lerp(partialTick, strand.previousSurfacePoint, strand.surfacePoint);
                Vec3 end = lerp(partialTick, strand.previousBodyPoint, strand.bodyPoint);
                AABB bounds = new AABB(start, end).inflate(0.42D);
                if (start.distanceToSqr(camera) > renderDistanceSquared
                        && end.distanceToSqr(camera) > renderDistanceSquared
                        || event.getFrustum() != null && !event.getFrustum().isVisible(bounds)) {
                    continue;
                }
                renderAdhesionStrand(minecraft, pose, buffers, hole, strand,
                        profile, start, end, partialTick);
            }
        }
        poseStack.popPose();

        MudSurfaceRenderBatchCache.endFrame(buffers);
        for (RenderType renderType : strandRenderTypes) {
            buffers.endBatch(renderType);
        }
        for (ResourceLocation texture : fleshTextures) {
            buffers.endBatch(RenderType.entityCutoutNoCull(texture));
        }
        for (ResourceLocation texture : fleshMembraneTextures) {
            buffers.endBatch(MudSurfaceDecalRenderTypes.opaqueMembrane(texture));
            buffers.endBatch(MudSurfaceDecalRenderTypes.membrane(texture));
        }
    }

    private static boolean hasActiveAdhesionStrand(MudSurfaceEffectManager.Hole hole) {
        for (MudSurfaceEffectManager.AdhesionStrand strand : hole.adhesionStrands) {
            if (strand.active) {
                return true;
            }
        }
        return false;
    }

    private static boolean renderAdhesionSheet(
            Minecraft minecraft, PoseStack.Pose pose,
            MultiBufferSource.BufferSource buffers,
            MudSurfaceEffectManager.Hole hole,
            AdhesionStrandProfile profile,
            float partialTick) {
        int ribCount = collectSheetRibs(hole, partialTick);
        if (ribCount < 2) {
            return false;
        }
        int alpha = adhesionAlpha(hole.medium);
        int packedLight = LevelRenderer.getLightColor(
                minecraft.level, BlockPos.containing(hole.center));
        int pairCount = ribCount >= 3 ? ribCount : 1;
        boolean rendered = false;
        for (int pair = 0; pair < pairCount; pair++) {
            MudSurfaceEffectManager.AdhesionStrand first = SHEET_RIBS[pair];
            MudSurfaceEffectManager.AdhesionStrand second = SHEET_RIBS[(pair + 1) % ribCount];
            Vec3 firstSurface = lerp(partialTick,
                    first.previousSurfacePoint, first.surfacePoint);
            Vec3 secondSurface = lerp(partialTick,
                    second.previousSurfacePoint, second.surfacePoint);
            Vec3 firstBody = lerp(partialTick, first.previousBodyPoint, first.bodyPoint);
            Vec3 secondBody = lerp(partialTick, second.previousBodyPoint, second.bodyPoint);
            if (firstSurface.distanceTo(secondSurface) > profile.sheetMaximumSpan()
                    || firstBody.distanceTo(secondBody) > profile.sheetMaximumSpan() * 1.65D) {
                continue;
            }
            double firstStretch = firstSurface.distanceTo(firstBody)
                    / Math.max(0.15D, profile.breakLength());
            double secondStretch = secondSurface.distanceTo(secondBody)
                    / Math.max(0.15D, profile.breakLength());
            long pairSeed = MudSurfaceEffectManager.mix(
                    first.seed ^ Long.rotateLeft(second.seed, 23));
            MudSurfaceAppearance.Appearance appearance = MudSurfaceAppearance.resolve(
                    minecraft.level, first.visualSource, hole.medium.coverTexture());
            RenderType renderType = adhesionRenderType(hole.medium, appearance.texture());
            STRAND_RENDER_TYPES.add(renderType);
            VertexConsumer vertices = buffers.getBuffer(renderType);
            double variation = unit(pairSeed) - 0.5D;
            double fingeringStart = Mth.clamp(
                    profile.sheetFingeringStart()
                            + variation * profile.sheetIrregularity() * 0.30D,
                    0.10D, 0.92D);
            double stretch = (firstStretch + secondStretch) * 0.5D;
            double fingering = smooth(Mth.clamp(
                    (stretch - fingeringStart) / Math.max(0.05D, 1.0D - fingeringStart),
                    0.0D, 1.0D));
            double firstBreak = Mth.lerp(partialTick,
                    first.previousBreakProgress, first.breakProgress);
            double secondBreak = Mth.lerp(partialTick,
                    second.previousBreakProgress, second.breakProgress);
            double growth = smooth(Math.min(
                    Mth.lerp(partialTick, first.previousAttachProgress, first.attachProgress),
                    Mth.lerp(partialTick, second.previousAttachProgress, second.attachProgress)));
            if (growth <= 0.006D) {
                continue;
            }
            double breakRetention = 1.0D - smooth(Math.max(firstBreak, secondBreak));
            double baseRetention = 0.5D * (1.0D - fingering) * breakRetention * growth;
            if (baseRetention <= 0.006D) {
                continue;
            }
            MudTextureUv.Region uv = appearanceUv(
                    MudTextureUv.sample(pairSeed, 10), appearance);
            for (int segment = 0;
                    segment < MudSurfaceEffectManager.ADHESION_BRIDGE_NODE_COUNT - 1;
                    segment++) {
                int segmentCount = MudSurfaceEffectManager.ADHESION_BRIDGE_NODE_COUNT - 1;
                double segmentFrom = (double) segment / segmentCount;
                if (segmentFrom >= growth) {
                    continue;
                }
                double localGrowth = Mth.clamp(
                        (growth - segmentFrom) * segmentCount, 0.0D, 1.0D);
                double segmentNoise = unit(MudSurfaceEffectManager.mix(
                        pairSeed ^ segment * 0x9e3779b97f4a7c15L)) - 0.5D;
                double retained = Mth.clamp(baseRetention * (1.0D
                        + segmentNoise * profile.sheetIrregularity() * 0.72D),
                        0.0D, 0.5D);
                if (retained <= 0.006D) {
                    continue;
                }
                Vec3 firstLow = lerp(partialTick,
                        first.previousBridgeNodes[segment], first.bridgeNodes[segment]);
                Vec3 firstHigh = lerp(partialTick,
                        first.previousBridgeNodes[segment + 1], first.bridgeNodes[segment + 1]);
                Vec3 secondLow = lerp(partialTick,
                        second.previousBridgeNodes[segment], second.bridgeNodes[segment]);
                Vec3 secondHigh = lerp(partialTick,
                        second.previousBridgeNodes[segment + 1], second.bridgeNodes[segment + 1]);
                firstHigh = lerp(firstLow, firstHigh, localGrowth);
                secondHigh = lerp(secondLow, secondHigh, localGrowth);
                if (retained >= 0.495D) {
                    renderAdhesionSheetQuad(pose, vertices,
                            firstLow, secondLow, secondHigh, firstHigh,
                            uv, alpha, packedLight, appearance);
                } else {
                    Vec3 firstFingerLow = lerp(firstLow, secondLow, retained);
                    Vec3 firstFingerHigh = lerp(firstHigh, secondHigh, retained);
                    Vec3 secondFingerLow = lerp(firstLow, secondLow, 1.0D - retained);
                    Vec3 secondFingerHigh = lerp(firstHigh, secondHigh, 1.0D - retained);
                    renderAdhesionSheetQuad(pose, vertices,
                            firstLow, firstFingerLow, firstFingerHigh, firstHigh,
                            uv, alpha, packedLight, appearance);
                    renderAdhesionSheetQuad(pose, vertices,
                            secondFingerLow, secondLow, secondHigh, secondFingerHigh,
                            uv, alpha, packedLight, appearance);
                }
                rendered = true;
            }
        }
        java.util.Arrays.fill(SHEET_RIBS, 0, ribCount, null);
        return rendered;
    }

    private static int collectSheetRibs(
            MudSurfaceEffectManager.Hole hole, float partialTick) {
        int count = 0;
        for (MudSurfaceEffectManager.AdhesionStrand strand : hole.adhesionStrands) {
            if (!strand.active || !strand.bridgeInitialized
                    || strand.attachProgress <= 0.006D) {
                continue;
            }
            if (count >= SHEET_RIBS.length) {
                break;
            }
            int insertion = count;
            double angle = sheetAngle(hole, strand, partialTick);
            while (insertion > 0
                    && sheetAngle(hole, SHEET_RIBS[insertion - 1], partialTick) > angle) {
                SHEET_RIBS[insertion] = SHEET_RIBS[insertion - 1];
                insertion--;
            }
            SHEET_RIBS[insertion] = strand;
            count++;
        }
        return count;
    }

    static int sheetRibCapacity() {
        return SHEET_RIBS.length;
    }

    private static double sheetAngle(
            MudSurfaceEffectManager.Hole hole,
            MudSurfaceEffectManager.AdhesionStrand strand,
            float partialTick) {
        Vec3 point = lerp(partialTick, strand.previousSurfacePoint, strand.surfacePoint);
        Vec3 relative = point.subtract(hole.center);
        return Math.atan2(relative.dot(hole.axisZ), relative.dot(hole.axisX));
    }

    private static void renderAdhesionSheetQuad(
            PoseStack.Pose pose, VertexConsumer vertices,
            Vec3 a, Vec3 b, Vec3 c, Vec3 d,
            MudTextureUv.Region uv, int alpha, int packedLight) {
        renderAdhesionSheetQuad(pose, vertices, a, b, c, d,
                uv, alpha, packedLight, 255);
    }

    private static void renderAdhesionSheetQuad(
            PoseStack.Pose pose, VertexConsumer vertices,
            Vec3 a, Vec3 b, Vec3 c, Vec3 d,
            MudTextureUv.Region uv, int alpha, int packedLight,
            MudSurfaceAppearance.Appearance appearance) {
        Vec3 normal = b.subtract(a).cross(d.subtract(a));
        if (normal.lengthSqr() <= 1.0E-9D) {
            return;
        }
        quad(pose, vertices, a, b, c, d,
                uv.u0(), uv.v0(), uv.u1(), uv.v1(),
                appearance.red(), appearance.green(), appearance.blue(),
                alpha, packedLight, normal.normalize());
    }

    private static void renderAdhesionSheetQuad(
            PoseStack.Pose pose, VertexConsumer vertices,
            Vec3 a, Vec3 b, Vec3 c, Vec3 d,
            MudTextureUv.Region uv, int alpha, int packedLight, int shade,
            MudSurfaceAppearance.Appearance appearance) {
        Vec3 normal = b.subtract(a).cross(d.subtract(a));
        if (normal.lengthSqr() <= 1.0E-9D) {
            return;
        }
        quad(pose, vertices, a, b, c, d,
                uv.u0(), uv.v0(), uv.u1(), uv.v1(),
                appearance.shadedRed(shade), appearance.shadedGreen(shade),
                appearance.shadedBlue(shade), alpha, packedLight, normal.normalize());
    }

    private static void renderAdhesionSheetQuad(
            PoseStack.Pose pose, VertexConsumer vertices,
            Vec3 a, Vec3 b, Vec3 c, Vec3 d,
            MudTextureUv.Region uv, int alpha, int packedLight, int shade) {
        Vec3 normal = b.subtract(a).cross(d.subtract(a));
        if (normal.lengthSqr() <= 1.0E-9D) {
            return;
        }
        quad(pose, vertices, a, b, c, d,
                uv.u0(), uv.v0(), uv.u1(), uv.v1(),
                shade, shade, shade, alpha, packedLight, normal.normalize());
    }

    private static void renderMembraneQuad(
            PoseStack.Pose pose, VertexConsumer vertices,
            Vec3 a, Vec3 b, Vec3 c, Vec3 d,
            MudTextureUv.Region uv, int alpha, int packedLight,
            MudSurfaceAppearance.Appearance appearance) {
        Vec3 first = b.subtract(a).cross(c.subtract(a));
        Vec3 second = c.subtract(a).cross(d.subtract(a));
        Vec3 normal = first.add(second);
        if (normal.lengthSqr() <= 1.0E-9D) {
            return;
        }
        normal = normal.normalize();
        MudTextureUv.Region mappedUv = appearanceUv(uv, appearance);
        triangle(pose, vertices, a, b, c,
                mappedUv.u0(), mappedUv.v0(), mappedUv.u1(), mappedUv.v0(),
                mappedUv.u1(), mappedUv.v1(),
                appearance.red(), appearance.green(), appearance.blue(),
                alpha, packedLight, normal);
        triangle(pose, vertices, a, c, d,
                mappedUv.u0(), mappedUv.v0(), mappedUv.u1(), mappedUv.v1(),
                mappedUv.u0(), mappedUv.v1(),
                appearance.red(), appearance.green(), appearance.blue(),
                alpha, packedLight, normal);
    }

    private static void renderAdhesionStrand(Minecraft minecraft, PoseStack.Pose pose,
            MultiBufferSource.BufferSource buffers, MudSurfaceEffectManager.Hole hole,
            MudSurfaceEffectManager.AdhesionStrand strand, AdhesionStrandProfile profile,
            Vec3 start, Vec3 end,
            float partialTick) {
        double length = start.distanceTo(end);
        if (length <= 0.012D || !strand.bridgeInitialized) {
            return;
        }
        double growth = smooth(Mth.lerp(partialTick,
                strand.previousAttachProgress, strand.attachProgress));
        if (growth <= 0.006D) {
            return;
        }
        double breakLength = Math.max(0.15D, profile.breakLength());
        double stretch = Mth.clamp(length / breakLength, 0.0D, 1.0D);
        Vec3 midpoint = start.add(end).scale(0.5D);
        int packedLight = LevelRenderer.getLightColor(
                minecraft.level, BlockPos.containing(midpoint));
        MudSurfaceAppearance.Appearance appearance = MudSurfaceAppearance.resolve(
                minecraft.level, strand.visualSource, hole.medium.coverTexture());
        RenderType renderType = adhesionRenderType(hole.medium, appearance.texture());
        STRAND_RENDER_TYPES.add(renderType);
        VertexConsumer vertices = buffers.getBuffer(renderType);
        int alpha = adhesionAlpha(hole.medium);
        MudTextureUv.Region uv = appearanceUv(
                MudTextureUv.sample(strand.seed, 6), appearance);
        prepareAdhesionFrames(strand, hole, partialTick);
        double breakProgress = Mth.lerp(partialTick,
                strand.previousBreakProgress, strand.breakProgress);
        double lobeVisibility = strand.breaking
                ? growth * (1.0D - smooth(breakProgress)) : growth;
        renderAdhesionSurfaceLobe(pose, vertices, hole, strand, profile,
                start, stretch, lobeVisibility, uv, alpha, packedLight, appearance);
        if (strand.breaking && breakProgress > 0.0D) {
            double retained = 0.5D * (1.0D - smooth(breakProgress));
            renderAdhesionBridgePiece(pose, vertices, profile,
                    0.0D, Math.min(retained, growth), stretch, uv, alpha, packedLight,
                    appearance);
            renderAdhesionBridgePiece(pose, vertices, profile,
                    1.0D - retained, growth, stretch, uv, alpha, packedLight, appearance);
        } else {
            renderAdhesionBridgePiece(pose, vertices, profile,
                    0.0D, growth, stretch, uv, alpha, packedLight, appearance);
        }
    }

    private static void prepareAdhesionFrames(
            MudSurfaceEffectManager.AdhesionStrand strand,
            MudSurfaceEffectManager.Hole hole,
            float partialTick) {
        int count = MudSurfaceEffectManager.ADHESION_BRIDGE_NODE_COUNT;
        for (int node = 0; node < count; node++) {
            BRIDGE_CENTERS[node] = lerp(partialTick,
                    strand.previousBridgeNodes[node], strand.bridgeNodes[node]);
        }
        for (int node = 0; node < count; node++) {
            Vec3 before = BRIDGE_CENTERS[Math.max(0, node - 1)];
            Vec3 after = BRIDGE_CENTERS[Math.min(count - 1, node + 1)];
            Vec3 tangent = after.subtract(before);
            if (tangent.lengthSqr() <= 1.0E-8D) {
                tangent = new Vec3(0.0D, 1.0D, 0.0D);
            } else {
                tangent = tangent.normalize();
            }
            Vec3 reference = node == 0 ? hole.axisX : BRIDGE_SIDE_A[node - 1];
            Vec3 sideA = reference.subtract(tangent.scale(reference.dot(tangent)));
            if (sideA.lengthSqr() <= 1.0E-7D) {
                reference = node == 0 ? hole.axisZ : BRIDGE_SIDE_B[node - 1];
                sideA = reference.subtract(tangent.scale(reference.dot(tangent)));
            }
            if (sideA.lengthSqr() <= 1.0E-7D) {
                sideA = tangent.cross(new Vec3(0.0D, 1.0D, 0.0D));
            }
            if (sideA.lengthSqr() <= 1.0E-7D) {
                sideA = tangent.cross(new Vec3(1.0D, 0.0D, 0.0D));
            }
            sideA = sideA.normalize();
            BRIDGE_SIDE_A[node] = sideA;
            BRIDGE_SIDE_B[node] = tangent.cross(sideA).normalize();
        }
    }

    private static void renderAdhesionSurfaceLobe(
            PoseStack.Pose pose, VertexConsumer vertices,
            MudSurfaceEffectManager.Hole hole,
            MudSurfaceEffectManager.AdhesionStrand strand,
            AdhesionStrandProfile profile,
            Vec3 start, double stretch, double visibility,
            MudTextureUv.Region uv, int alpha, int packedLight,
            MudSurfaceAppearance.Appearance appearance) {
        if (visibility <= 0.01D) {
            return;
        }
        double endpointWidth = adhesionWidth(profile, 0.0D, stretch);
        double lobeWidth = Math.max(PIXEL * 1.50D, endpointWidth * 1.18D)
                * Math.sqrt(visibility);
        double lobeHeight = Math.max(PIXEL * 0.16D, endpointWidth * 0.10D)
                * visibility;
        double offsetSign = (strand.seed & 1L) == 0L ? -1.0D : 1.0D;
        Vec3 offset = hole.axisX.scale(offsetSign * PIXEL * 0.16D);
        renderOrientedCuboid(pose, vertices, start, hole.axisX, hole.axisZ, hole.normal,
                lobeWidth * 1.10D, lobeWidth * 0.64D, lobeHeight,
                uv.u0(), uv.v0(), uv.u1(), uv.v1(), alpha, packedLight, appearance);
        renderOrientedCuboid(pose, vertices, start.add(offset),
                hole.axisX, hole.axisZ, hole.normal,
                lobeWidth * 0.62D, lobeWidth, lobeHeight * 0.86D,
                uv.u0(), uv.v0(), uv.u1(), uv.v1(), alpha, packedLight, appearance);
    }

    private static void renderAdhesionBridgePiece(PoseStack.Pose pose, VertexConsumer vertices,
            AdhesionStrandProfile profile, double from, double to, double stretch,
            MudTextureUv.Region uv, int alpha, int packedLight,
            MudSurfaceAppearance.Appearance appearance) {
        if (to - from <= 0.008D) {
            return;
        }
        int segmentCount = MudSurfaceEffectManager.ADHESION_BRIDGE_NODE_COUNT - 1;
        for (int segment = 0; segment < segmentCount; segment++) {
            double segmentFrom = (double) segment / segmentCount;
            double segmentTo = (double) (segment + 1) / segmentCount;
            double clippedFrom = Math.max(from, segmentFrom);
            double clippedTo = Math.min(to, segmentTo);
            if (clippedTo - clippedFrom <= 1.0E-5D) {
                continue;
            }
            Vec3 nodeStart = BRIDGE_CENTERS[segment];
            Vec3 nodeEnd = BRIDGE_CENTERS[segment + 1];
            double localFrom = (clippedFrom - segmentFrom) * segmentCount;
            double localTo = (clippedTo - segmentFrom) * segmentCount;
            Vec3 pieceStart = nodeStart.add(nodeEnd.subtract(nodeStart).scale(localFrom));
            Vec3 pieceEnd = nodeStart.add(nodeEnd.subtract(nodeStart).scale(localTo));
            Vec3 startSideA = directionLerp(
                    BRIDGE_SIDE_A[segment], BRIDGE_SIDE_A[segment + 1], localFrom);
            Vec3 startSideB = directionLerp(
                    BRIDGE_SIDE_B[segment], BRIDGE_SIDE_B[segment + 1], localFrom);
            Vec3 endSideA = directionLerp(
                    BRIDGE_SIDE_A[segment], BRIDGE_SIDE_A[segment + 1], localTo);
            Vec3 endSideB = directionLerp(
                    BRIDGE_SIDE_B[segment], BRIDGE_SIDE_B[segment + 1], localTo);
            renderAdhesionPrism(pose, vertices, pieceStart, pieceEnd,
                    adhesionWidth(profile, clippedFrom, stretch),
                    adhesionWidth(profile, clippedTo, stretch),
                    startSideA, startSideB, endSideA, endSideB,
                    uv, alpha, packedLight, appearance);
        }
    }

    private static double adhesionWidth(
            AdhesionStrandProfile profile, double t, double stretch) {
        double endpoint = Math.pow(Math.abs(t * 2.0D - 1.0D), 0.72D);
        double shape = Mth.lerp(endpoint, profile.neckScale(), profile.endWidthScale());
        double stretched = Mth.lerp(stretch, 1.0D, 0.60D);
        double pixels = Math.max(0.50D, profile.widthPixels() * shape * stretched);
        return Math.max(0.50D, Math.rint(pixels * 4.0D) / 4.0D) * PIXEL;
    }

    private static RenderType adhesionRenderType(
            SinkingMedium medium, ResourceLocation texture) {
        return medium == SinkingMedium.TENDER_FLESH
                ? RenderType.entityTranslucent(texture)
                : RenderType.entityCutoutNoCull(texture);
    }

    private static RenderType tenderFleshMembraneRenderType(
            TenderFleshProfile profile, ResourceLocation texture) {
        return profile.membraneOpaque()
                ? MudSurfaceDecalRenderTypes.opaqueMembrane(texture)
                : MudSurfaceDecalRenderTypes.membrane(texture);
    }

    private static int adhesionAlpha(SinkingMedium medium) {
        return medium == SinkingMedium.TENDER_FLESH ? 184 : 255;
    }

    private static void renderAdhesionPrism(PoseStack.Pose pose, VertexConsumer vertices,
            Vec3 start, Vec3 end, double startWidth, double endWidth,
            Vec3 startSideA, Vec3 startSideB, Vec3 endSideA, Vec3 endSideB,
            MudTextureUv.Region uv, int alpha, int packedLight,
            MudSurfaceAppearance.Appearance appearance) {
        if (end.distanceToSqr(start) <= 1.0E-8D) {
            return;
        }
        Vec3 startA = startSideA.scale(startWidth * 0.5D);
        Vec3 startB = startSideB.scale(startWidth * 0.5D);
        Vec3 endA = endSideA.scale(endWidth * 0.5D);
        Vec3 endB = endSideB.scale(endWidth * 0.5D);
        Vec3 near0 = start.subtract(startA).subtract(startB);
        Vec3 near1 = start.add(startA).subtract(startB);
        Vec3 near2 = start.add(startA).add(startB);
        Vec3 near3 = start.subtract(startA).add(startB);
        Vec3 far0 = end.subtract(endA).subtract(endB);
        Vec3 far1 = end.add(endA).subtract(endB);
        Vec3 far2 = end.add(endA).add(endB);
        Vec3 far3 = end.subtract(endA).add(endB);
        renderAdhesionPrismFace(pose, vertices, near0, near1, far1, far0,
                uv, alpha, packedLight, appearance);
        renderAdhesionPrismFace(pose, vertices, near1, near2, far2, far1,
                uv, alpha, packedLight, appearance);
        renderAdhesionPrismFace(pose, vertices, near2, near3, far3, far2,
                uv, alpha, packedLight, appearance);
        renderAdhesionPrismFace(pose, vertices, near3, near0, far0, far3,
                uv, alpha, packedLight, appearance);
    }

    private static void renderAdhesionPrismFace(
            PoseStack.Pose pose, VertexConsumer vertices,
            Vec3 nearA, Vec3 nearB, Vec3 farB, Vec3 farA,
            MudTextureUv.Region uv, int alpha, int packedLight,
            MudSurfaceAppearance.Appearance appearance) {
        Vec3 normal = nearB.subtract(nearA).cross(farA.subtract(nearA)).normalize();
        quad(pose, vertices, nearA, nearB, farB, farA,
                uv.u0(), uv.v0(), uv.u1(), uv.v1(),
                appearance.red(), appearance.green(), appearance.blue(),
                alpha, packedLight, normal);
    }

    private static MudTextureUv.Region appearanceUv(
            MudTextureUv.Region uv, MudSurfaceAppearance.Appearance appearance) {
        return new MudTextureUv.Region(
                appearance.u(uv.u0()), appearance.v(uv.v0()),
                appearance.u(uv.u1()), appearance.v(uv.v1()));
    }

    private static Vec3 lerp(float partialTick, Vec3 previous, Vec3 current) {
        return new Vec3(
                Mth.lerp(partialTick, previous.x, current.x),
                Mth.lerp(partialTick, previous.y, current.y),
                Mth.lerp(partialTick, previous.z, current.z));
    }

    private static Vec3 lerp(Vec3 from, Vec3 to, double amount) {
        return from.add(to.subtract(from).scale(amount));
    }

    private static double unit(long value) {
        return ((value >>> 11) & 4095L) / 4095.0D;
    }

    private static Vec3 directionLerp(Vec3 from, Vec3 to, double amount) {
        Vec3 value = from.scale(1.0D - amount).add(to.scale(amount));
        return value.lengthSqr() <= 1.0E-8D ? from : value.normalize();
    }

    private static Vec3[] bridgeScratch() {
        Vec3[] values = new Vec3[MudSurfaceEffectManager.ADHESION_BRIDGE_NODE_COUNT];
        java.util.Arrays.fill(values, Vec3.ZERO);
        return values;
    }

    private static int renderSurfaceField(Minecraft minecraft, PoseStack.Pose pose,
            MultiBufferSource.BufferSource buffers, MudSurfaceEffectManager.Hole hole,
            float partialTick, Vec3 camera, double renderDistanceSquared,
            Frustum frustum, int cellLimit) {
        partialTick = hole.surfacePartialTick(
                minecraft.level.getGameTime(), partialTick);
        SUPPORT_VALIDITY.clear();
        SURFACE_DARKENING.clear();
        SURFACE_FLESH.clear();
        SURFACE_TILE_VISIBILITY.clear();
        clearAppearanceMaps(SURFACE_DECAL_APPEARANCES);
        clearAppearanceMaps(SURFACE_PILE_APPEARANCES);
        if (hole.cells.size() <= cellLimit) {
            return renderSurfaceFieldPass(
                    minecraft, pose, buffers, hole, partialTick,
                    camera, renderDistanceSquared, frustum, cellLimit, 0);
        }
        int rendered = renderSurfaceFieldPass(
                minecraft, pose, buffers, hole, partialTick,
                camera, renderDistanceSquared, frustum, cellLimit, 1);
        if (rendered < cellLimit) {
            rendered += renderSurfaceFieldPass(
                    minecraft, pose, buffers, hole, partialTick,
                    camera, renderDistanceSquared, frustum,
                    cellLimit - rendered, 2);
        }
        return rendered;
    }

    private static int renderSurfaceFieldPass(
            Minecraft minecraft, PoseStack.Pose pose,
            MultiBufferSource.BufferSource buffers, MudSurfaceEffectManager.Hole hole,
            float partialTick, Vec3 camera, double renderDistanceSquared,
            Frustum frustum, int cellLimit, int distancePass) {
        Long2BooleanOpenHashMap supportValidity = SUPPORT_VALIDITY;
        Long2DoubleOpenHashMap darkeningBySupport = SURFACE_DARKENING;
        Long2BooleanOpenHashMap fleshBySupport = SURFACE_FLESH;
        Long2BooleanOpenHashMap tileVisibility = SURFACE_TILE_VISIBILITY;
        int rendered = 0;
        for (MudSurfaceEffectManager.SurfaceCell cell : hole.cells.values()) {
            long supportKey = MudSurfaceEffectManager.surfaceSupportKey(cell);
            if (!supportValidity.containsKey(supportKey)) {
                supportValidity.put(
                        supportKey,
                        MudSurfaceEffectManager.hasCurrentSupport(cell));
            }
            if (!supportValidity.get(supportKey)) {
                continue;
            }
            double centerX = (cell.pixelX + 0.5D) * PIXEL;
            double centerZ = (cell.pixelZ + 0.5D) * PIXEL;
            double dx = centerX - camera.x;
            double dy = cell.surfaceY - camera.y;
            double dz = centerZ - camera.z;
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared > renderDistanceSquared) {
                continue;
            }
            boolean near = MudSurfaceCellBudget.prioritizeNear(distanceSquared);
            if (distancePass == 1 && !near || distancePass == 2 && near) {
                continue;
            }
            long tileKey = surfaceTileKey(cell);
            if (!tileVisibility.containsKey(tileKey)) {
                tileVisibility.put(tileKey,
                        frustum == null || frustum.isVisible(surfaceTileBounds(tileKey)));
            }
            if (!tileVisibility.get(tileKey)) {
                continue;
            }
            double visualHeightEpsilon = MudSurfaceCellBudget.visualHeightEpsilon(
                    distanceSquared, renderDistanceSquared);
            double depression = Mth.lerp(
                    partialTick, cell.previousDepression, cell.depression);
            double closure = Mth.lerp(
                    partialTick, cell.previousClosureProgress, cell.closureProgress);
            double pileHeight = Mth.lerp(
                    partialTick, cell.previousPileHeight, cell.pileHeight);
            SinkingMedium medium = cell.medium;
            if (!fleshBySupport.containsKey(cell.supportBlock)) {
                fleshBySupport.put(cell.supportBlock, MudBehaviorContext.tenderFlesh(
                        minecraft.level, BlockPos.of(cell.supportBlock), medium));
            }
            boolean fleshTemplate = fleshBySupport.get(cell.supportBlock);
            float u0 = tileU(cell.seed);
            float v0 = tileV(cell.seed);
            float u1 = u0 + 1.0F / 16.0F;
            float v1 = v0 + 1.0F / 16.0F;
            MudRenderedSurfaceGeometry.SurfaceHit renderedHit = cell.renderedHit;
            Vec3 surfaceNormal = renderedHit == null
                    ? MudSurfaceVoxelRenderer.WORLD_UP : renderedHit.normal();
            Vec3 surfaceAxisX = renderedHit == null
                    ? MudSurfaceVoxelRenderer.WORLD_X : renderedHit.axisX();
            Vec3 surfaceAxisZ = renderedHit == null
                    ? MudSurfaceVoxelRenderer.WORLD_Z : renderedHit.axisZ();
            int packedLight = surfaceLight(
                    minecraft, centerX, cell.surfaceY, centerZ, surfaceNormal);
            cell.packedLight = packedLight;
            Direction surfaceFace = Direction.getNearest(
                    surfaceNormal.x, surfaceNormal.y, surfaceNormal.z);
            if (depression > 0.003D) {
                if (rendered >= cellLimit) {
                    break;
                }
                double darkening;
                if (fleshTemplate) {
                    darkening = 0.0D;
                } else if (darkeningBySupport.containsKey(cell.supportBlock)) {
                    darkening = darkeningBySupport.get(cell.supportBlock);
                } else {
                    darkening = MudMediumRuntime.value(
                            minecraft.level, BlockPos.of(cell.supportBlock), medium,
                            MudPhysicsParameter.SURFACE_HOLE_DARKENING);
                    darkeningBySupport.put(cell.supportBlock, darkening);
                }
                int darkness = Mth.clamp(
                        (int) Math.round((1.0D - darkening) * 255.0D), 24, 255);
                int shade = Mth.clamp(
                        (int) Math.round(Mth.lerp(depression, 255.0D, darkness)),
                        darkness, 255);
                MudSurfaceAppearance.Appearance appearance = appearanceFor(
                        SURFACE_DECAL_APPEARANCES, minecraft, hole, cell, surfaceFace,
                        surfaceCompressionTexture(medium, fleshTemplate));
                VertexConsumer core = MudSurfaceRenderBatchCache.decal(
                        buffers, appearance, medium);
                renderClosingPlaneCell(pose, core,
                        centerX + surfaceNormal.x * SURFACE_DECAL_NORMAL_OFFSET,
                        cell.surfaceY + surfaceNormal.y * SURFACE_DECAL_NORMAL_OFFSET,
                        centerZ + surfaceNormal.z * SURFACE_DECAL_NORMAL_OFFSET,
                        surfaceAxisX,
                        surfaceAxisZ,
                        surfaceNormal,
                        PIXEL * 1.012D, closure, cell.closureMask,
                        cell.renderedPatch,
                        u0, v0, u1, v1,
                        shade, 255, packedLight, appearance);
                rendered++;
            } else if (pileHeight > visualHeightEpsilon) {
                if (rendered >= cellLimit) {
                    break;
                }
                MudSurfaceAppearance.Appearance appearance = appearanceFor(
                        SURFACE_PILE_APPEARANCES, minecraft, hole, cell, surfaceFace,
                        medium.coverTexture());
                MudSurfaceVoxelRenderer.renderTopPile(
                        pose, buffers, hole, cell, partialTick,
                        supportValidity, pileHeight, visualHeightEpsilon,
                        u0, v0, u1, v1, appearance);
                rendered++;
            }
        }
        return rendered;
    }

    private static long surfaceTileKey(MudSurfaceEffectManager.SurfaceCell cell) {
        return BlockPos.asLong(
                Math.floorDiv(cell.pixelX, 16),
                Mth.floor(cell.surfaceY),
                Math.floorDiv(cell.pixelZ, 16));
    }

    private static int surfaceLight(Minecraft minecraft,
            double centerX, double centerY, double centerZ, Vec3 normal) {
        BlockPos lightPos = MudSurfaceEffectManager.exposedSurfaceLightPosition(
                new Vec3(centerX, centerY, centerZ), normal);
        long key = lightPos.asLong();
        if (!SURFACE_LIGHT.containsKey(key)) {
            SURFACE_LIGHT.put(key, LevelRenderer.getLightColor(minecraft.level, lightPos));
        }
        return SURFACE_LIGHT.get(key);
    }

    private static AABB surfaceTileBounds(long tileKey) {
        BlockPos pos = BlockPos.of(tileKey);
        return new AABB(
                pos.getX() - 0.04D,
                pos.getY() - 0.08D,
                pos.getZ() - 0.04D,
                pos.getX() + 1.04D,
                pos.getY() + 1.50D,
                pos.getZ() + 1.04D);
    }

    private static double distanceToSqr(AABB bounds, Vec3 point) {
        double dx = Math.max(Math.max(bounds.minX - point.x, 0.0D), point.x - bounds.maxX);
        double dy = Math.max(Math.max(bounds.minY - point.y, 0.0D), point.y - bounds.maxY);
        double dz = Math.max(Math.max(bounds.minZ - point.z, 0.0D), point.z - bounds.maxZ);
        return dx * dx + dy * dy + dz * dz;
    }

    private static MudSurfaceAppearance.Appearance appearanceFor(
            Long2ObjectOpenHashMap<MudSurfaceAppearance.Appearance>[] appearances,
            Minecraft minecraft, MudSurfaceEffectManager.Hole hole,
            MudSurfaceEffectManager.SurfaceCell cell,
            Direction face, ResourceLocation fallbackTexture) {
        Long2ObjectOpenHashMap<MudSurfaceAppearance.Appearance> bySupport =
                appearances[cell.medium.ordinal()];
        MudSurfaceAppearance.Appearance appearance = bySupport.get(cell.supportBlock);
        if (appearance == null) {
            appearance = MudSurfaceAppearance.resolve(
                    minecraft.level, BlockPos.of(cell.supportBlock), face,
                    hole.visualSource, fallbackTexture);
            bySupport.put(cell.supportBlock, appearance);
        }
        return appearance;
    }

    @SuppressWarnings("unchecked")
    private static Long2ObjectOpenHashMap<MudSurfaceAppearance.Appearance>[] appearanceMaps() {
        Long2ObjectOpenHashMap<MudSurfaceAppearance.Appearance>[] maps =
                new Long2ObjectOpenHashMap[SinkingMedium.values().length];
        for (int index = 0; index < maps.length; index++) {
            maps[index] = new Long2ObjectOpenHashMap<>();
        }
        return maps;
    }

    private static void clearAppearanceMaps(
            Long2ObjectOpenHashMap<MudSurfaceAppearance.Appearance>[] maps) {
        for (Long2ObjectOpenHashMap<MudSurfaceAppearance.Appearance> map : maps) {
            map.clear();
        }
    }

    private static boolean renderTenderFleshTentacles(
            Minecraft minecraft,
            PoseStack.Pose pose,
            MultiBufferSource.BufferSource buffers,
            MudSurfaceEffectManager.Hole hole,
            float partialTick,
            Set<ResourceLocation> fleshTextures,
            Set<ResourceLocation> fleshMembraneTextures) {
        TenderFleshProfile profile = MudMediumRuntime.tenderFleshProfile(
                minecraft.level, hole.profilePos);
        int segments = Mth.clamp(profile.tentacleSegments() + 4, 7, 10);
        double contraction = Mth.clamp(Mth.lerp(
                partialTick, hole.previousFleshContraction, hole.fleshContraction), 0.0D, 1.0D);
        double pillarProgress = Mth.clamp(Mth.lerp(
                partialTick, hole.previousFleshPillarProgress, hole.fleshPillarProgress), 0.0D, 1.0D);
        double visibility = Math.max(
                Mth.clamp(Mth.lerp(partialTick, hole.previousVisibility, hole.visibility), 0.0D, 1.0D),
                pillarProgress);
        boolean withdrawing = hole.fleshPillarWithdrawing;
        double emergence = withdrawing
                ? smooth(Mth.clamp(pillarProgress / 0.46D, 0.0D, 1.0D))
                : smooth(pillarProgress);
        double closure = withdrawing
                ? smooth(Mth.clamp((pillarProgress - 0.46D) / 0.26D, 0.0D, 1.0D))
                : smooth(Mth.clamp((pillarProgress - 0.18D) / 0.82D, 0.0D, 1.0D));
        double membraneProgress = withdrawing
                ? 1.0D
                : smooth(Mth.clamp((pillarProgress - 0.50D) / 0.50D, 0.0D, 1.0D));
        double membraneRetention = withdrawing
                ? smooth(smooth(Mth.clamp(
                        (pillarProgress - 0.48D) / 0.52D, 0.0D, 1.0D)))
                : 1.0D;
        if (pillarProgress <= 0.003D) {
            return false;
        }

        Vec3 anchoredCenter = hole.fleshEnclosureAnchorSet
                ? hole.fleshEnclosureCenter : hole.center;
        Vec3 enclosureCenter = withdrawing && hole.fleshRetreatFrameCaptured
                ? hole.fleshRetreatCenter : anchoredCenter;
        Vec3 enclosureNormal = withdrawing && hole.fleshRetreatFrameCaptured
                ? hole.fleshRetreatNormal : hole.normal;
        Vec3 enclosureAxisX = withdrawing && hole.fleshRetreatFrameCaptured
                ? hole.fleshRetreatAxisX : hole.axisX;
        Vec3 enclosureAxisZ = withdrawing && hole.fleshRetreatFrameCaptured
                ? hole.fleshRetreatAxisZ : hole.axisZ;

        net.minecraft.world.entity.Entity trapped = minecraft.level.getEntity(hole.entityId);
        double bodyHalfWidth = trapped == null ? 0.30D : Math.max(0.20D, trapped.getBbWidth() * 0.5D);
        double bodyCornerRadius = Math.sqrt(2.0D) * (bodyHalfWidth + PIXEL * 2.0D);
        double innerRadius = bodyCornerRadius;
        double outerRadius = TenderFleshPoolRules.pillarOuterRadius(
                profile, bodyHalfWidth);
        double exposedHeight = withdrawing && hole.fleshRetreatFrameCaptured
                ? hole.fleshRetreatHeight
                : Mth.lerp(partialTick,
                        hole.previousFleshExposedHeight, hole.fleshExposedHeight);
        double pillarHeight = TenderFleshMechanics.enclosureHeight(
                profile, exposedHeight);
        if (pillarHeight <= PIXEL * 0.20D) {
            return false;
        }
        double baseWidth = Math.max(PIXEL * 2.0D, profile.foldWidthPixels() * PIXEL)
                * (0.96D + closure * 0.12D);
        double breathContraction = membraneProgress * Mth.clamp(
                profile.contractionStrength() * contraction * 0.32D,
                0.0D, 0.30D);

        MudSurfaceAppearance.Appearance intactAppearance = MudSurfaceAppearance.resolve(
                minecraft.level, hole.profilePos, Direction.UP,
                hole.visualSource, hole.medium.coverTexture());
        boolean nativeTenderAppearance = hole.medium == SinkingMedium.TENDER_FLESH
                && hole.visualSource == MudVisualSource.NONE;
        MudSurfaceAppearance.Appearance brokenAppearance = nativeTenderAppearance
                ? MudSurfaceAppearance.resolve(
                        minecraft.level, MudVisualSource.NONE, TENDER_FLESH_BROKEN_TEXTURE)
                : intactAppearance;
        int brokenShade = nativeTenderAppearance ? 255 : 176;
        VertexConsumer intactVertices = buffers.getBuffer(
                RenderType.entityCutoutNoCull(intactAppearance.texture()));
        VertexConsumer brokenVertices = buffers.getBuffer(
                RenderType.entityCutoutNoCull(brokenAppearance.texture()));
        fleshTextures.add(intactAppearance.texture());
        fleshTextures.add(brokenAppearance.texture());
        int packedLight = LevelRenderer.getLightColor(
                minecraft.level, BlockPos.containing(enclosureCenter));
        Vec3 normal = enclosureNormal.lengthSqr() <= 1.0E-8D
                ? new Vec3(0.0D, 1.0D, 0.0D) : enclosureNormal.normalize();
        Vec3[][] pillarPoints = TENDER_FLESH_PILLAR_POINTS;
        for (int index = 0; index < TENDER_FLESH_PILLAR_COUNT; index++) {
            long seed = MudSurfaceEffectManager.mix(
                    hole.seed ^ (long) index * 0x9e3779b97f4a7c15L);
            double angle = Math.PI * 0.25D + Math.PI * 0.50D * index;
            Vec3 radial = enclosureAxisX.scale(Math.cos(angle))
                    .add(enclosureAxisZ.scale(Math.sin(angle))).normalize();
            Vec3 tangent = normal.cross(radial).normalize();
            double stalkHeight = pillarHeight;
            for (int node = 0; node <= segments; node++) {
                double progress = (double) node / segments;
                pillarPoints[index][node] = tenderFleshPillarPoint(
                        enclosureCenter, radial, normal,
                        outerRadius, innerRadius, stalkHeight,
                        emergence, closure, breathContraction,
                        profile.tentacleSwayPixels() * PIXEL, progress);
            }
            boolean broken = (hole.fleshBrokenMask & (1 << index)) != 0;
            int requiredHits = TenderFleshMechanics.pillarPackedValue(
                    hole.fleshPillarRequiredHitsPacked, index);
            int damage = broken ? requiredHits : TenderFleshMechanics.pillarPackedValue(
                    hole.fleshPillarDamagePacked, index);
            renderTenderFleshPillarTube(
                    pose, intactVertices, brokenVertices,
                    pillarPoints[index], baseWidth,
                     segments, normal, tangent, seed, packedLight,
                     255, brokenShade, breathContraction, damage, requiredHits,
                     intactAppearance, brokenAppearance);
        }

        if (membraneProgress > 0.003D && membraneRetention > 0.003D) {
            VertexConsumer membrane = buffers.getBuffer(
                    tenderFleshMembraneRenderType(profile, intactAppearance.texture()));
            boolean opaque = profile.membraneOpaque();
            int alpha = opaque ? 255 : Mth.clamp((int) Math.round(
                    255.0D * profile.membraneOpacity()
                            * membraneProgress * visibility), 0, 255);
            Vec3 membraneOffset = Vec3.ZERO;
            double membraneCurve = Math.max(PIXEL,
                    profile.enclosureOpenRadius() * 0.22D
                            * membraneProgress * membraneRetention)
                    * (1.0D - breathContraction * 0.80D);
            double filledRows = membraneProgress * segments;
            for (int index = 0; index < TENDER_FLESH_PILLAR_COUNT; index++) {
                int next = (index + 1) % TENDER_FLESH_PILLAR_COUNT;
                for (int row = 0; row < segments; row++) {
                    double rowFill = Mth.clamp(filledRows - row, 0.0D, 1.0D);
                    if (rowFill <= 0.003D) {
                        continue;
                    }
                    Vec3 lowerA = pillarPoints[index][row];
                    Vec3 lowerB = pillarPoints[next][row];
                    Vec3 upperA = lerp(
                            pillarPoints[index][row], pillarPoints[index][row + 1], rowFill);
                    Vec3 upperB = lerp(
                            pillarPoints[next][row], pillarPoints[next][row + 1], rowFill);
                    for (int slice = 0; slice < TENDER_FLESH_MEMBRANE_SLICES; slice++) {
                        double sliceStart = (double) slice / TENDER_FLESH_MEMBRANE_SLICES;
                        double sliceEnd = (double) (slice + 1) / TENDER_FLESH_MEMBRANE_SLICES;
                        boolean firstHalf = slice < TENDER_FLESH_MEMBRANE_SLICES / 2;
                        double retainedStart = firstHalf
                                ? sliceStart * membraneRetention
                                : 1.0D - (1.0D - sliceStart) * membraneRetention;
                        double retainedEnd = firstHalf
                                ? sliceEnd * membraneRetention
                                : 1.0D - (1.0D - sliceEnd) * membraneRetention;
                        Vec3 sliceLowerA = curvedMembranePoint(
                                lowerA, lowerB, enclosureCenter, normal, retainedStart,
                                membraneOffset, membraneCurve);
                        Vec3 sliceLowerB = curvedMembranePoint(
                                lowerA, lowerB, enclosureCenter, normal, retainedEnd,
                                membraneOffset, membraneCurve);
                        Vec3 sliceUpperA = curvedMembranePoint(
                                upperA, upperB, enclosureCenter, normal, retainedStart,
                                membraneOffset, membraneCurve);
                        Vec3 sliceUpperB = curvedMembranePoint(
                                upperA, upperB, enclosureCenter, normal, retainedEnd,
                                membraneOffset, membraneCurve);
                        MudTextureUv.Region uv = MudTextureUv.sample(
                                hole.seed + index * 67L + row * 131L + slice * 17L
                                        + 0x5f3759dfL, 8);
                        renderMembraneQuad(
                                 pose, membrane, sliceLowerA, sliceLowerB,
                                 sliceUpperB, sliceUpperA, uv, alpha, packedLight,
                                 intactAppearance);
                    }
                }
            }
            fleshMembraneTextures.add(intactAppearance.texture());
        }
        return true;
    }

    static int raycastTenderFleshPillar(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null
                || minecraft.player.isSpectator()) {
            return -1;
        }
        MudSurfaceEffectManager.Hole hole = MudSurfaceEffectManager.holeFor(
                minecraft.player.getId());
        if (hole == null || !hole.fleshTemplateEnabled
                || !hole.fleshEnclosureActive
                || hole.fleshPillarWithdrawing
                || hole.fleshPillarProgress < 0.20D) {
            return -1;
        }
        TenderFleshProfile profile = MudMediumRuntime.tenderFleshProfile(
                minecraft.level, hole.profilePos);
        double progress = Mth.clamp(hole.fleshPillarProgress, 0.0D, 1.0D);
        double pillarHeight = TenderFleshMechanics.enclosureHeight(
                profile, hole.fleshExposedHeight);
        if (pillarHeight <= PIXEL * 0.25D) {
            return -1;
        }
        net.minecraft.world.entity.Entity trapped = minecraft.player;
        double bodyHalfWidth = Math.max(0.20D, trapped.getBbWidth() * 0.5D);
        double bodyCornerRadius = Math.sqrt(2.0D) * (bodyHalfWidth + PIXEL * 2.0D);
        double outerRadius = TenderFleshPoolRules.pillarOuterRadius(
                profile, bodyHalfWidth);
        double innerRadius = bodyCornerRadius;
        double baseWidth = Math.max(PIXEL * 2.0D,
                profile.foldWidthPixels() * PIXEL);
        Vec3 normal = hole.normal.lengthSqr() <= 1.0E-8D
                ? new Vec3(0.0D, 1.0D, 0.0D) : hole.normal.normalize();
        Vec3 axisX = hole.axisX.lengthSqr() <= 1.0E-8D
                ? new Vec3(1.0D, 0.0D, 0.0D) : hole.axisX.normalize();
        Vec3 axisZ = hole.axisZ.lengthSqr() <= 1.0E-8D
                ? new Vec3(0.0D, 0.0D, 1.0D) : hole.axisZ.normalize();
        Vec3 start = minecraft.gameRenderer.getMainCamera().getPosition();
        Vec3 direction = minecraft.player.getViewVector(1.0F).normalize();
        double reach = 8.0D;
        int segments = Mth.clamp(profile.tentacleSegments() + 4, 7, 10);
        double contraction = Mth.clamp(
                Mth.lerp(1.0F, hole.previousFleshContraction, hole.fleshContraction),
                0.0D, 1.0D);
        double closure = smooth(Mth.clamp((progress - 0.18D) / 0.82D, 0.0D, 1.0D));
        double breathContraction = Mth.clamp(
                profile.contractionStrength() * contraction * 0.32D,
                0.0D, 0.30D);
        double bestDistance = Double.POSITIVE_INFINITY;
        int best = -1;
        Vec3 enclosureCenter = hole.fleshEnclosureAnchorSet
                ? hole.fleshEnclosureCenter : hole.center;
        for (int index = 0; index < TENDER_FLESH_PILLAR_COUNT; index++) {
            if ((hole.fleshBrokenMask & (1 << index)) != 0) {
                continue;
            }
            double angle = Math.PI * 0.25D + Math.PI * 0.50D * index;
            Vec3 radial = axisX.scale(Math.cos(angle))
                    .add(axisZ.scale(Math.sin(angle))).normalize();
            Vec3 tangent = normal.cross(radial).normalize();
            Vec3 previous = null;
            for (int node = 0; node <= segments; node++) {
                double nodeProgress = (double) node / segments;
                Vec3 point = tenderFleshPillarPoint(
                        enclosureCenter, radial, normal, outerRadius, innerRadius,
                        pillarHeight, smooth(progress), closure, breathContraction,
                        profile.tentacleSwayPixels() * PIXEL, nodeProgress);
                if (previous != null) {
                    double half = tenderFleshPillarHalfWidth(
                            baseWidth, nodeProgress, breathContraction) * 1.18D;
                    AABB bounds = new AABB(previous, point).inflate(half);
                    double hit = rayAabbDistance(start, direction, reach, bounds);
                    if (hit < bestDistance) {
                        bestDistance = hit;
                        best = index;
                    }
                }
                previous = point;
            }
        }
        return best;
    }

    private static double rayAabbDistance(Vec3 origin, Vec3 direction,
            double reach, AABB box) {
        double minimum = 0.0D;
        double maximum = reach;
        double[] starts = {origin.x, origin.y, origin.z};
        double[] directions = {direction.x, direction.y, direction.z};
        double[] minimums = {box.minX, box.minY, box.minZ};
        double[] maximums = {box.maxX, box.maxY, box.maxZ};
        for (int axis = 0; axis < 3; axis++) {
            double start = starts[axis];
            double delta = directions[axis];
            if (Math.abs(delta) <= 1.0E-8D) {
                if (start < minimums[axis] || start > maximums[axis]) {
                    return Double.POSITIVE_INFINITY;
                }
                continue;
            }
            double first = (minimums[axis] - start) / delta;
            double second = (maximums[axis] - start) / delta;
            if (first > second) {
                double swap = first;
                first = second;
                second = swap;
            }
            minimum = Math.max(minimum, first);
            maximum = Math.min(maximum, second);
            if (minimum > maximum) {
                return Double.POSITIVE_INFINITY;
            }
        }
        return minimum;
    }

    private static Vec3 tenderFleshPillarPoint(
            Vec3 center, Vec3 radial, Vec3 normal,
            double outerRadius, double innerRadius, double height,
            double emergence, double closure, double breathContraction,
            double outwardBulge, double progress) {
        double eased = progress * progress * (3.0D - 2.0D * progress);
        double enclosureProgress = smooth(Mth.clamp(progress / 0.72D, 0.0D, 1.0D));
        double enclosureRadius = Mth.lerp(
                closure * enclosureProgress, outerRadius, innerRadius);
        double middleWeight = Math.pow(Math.sin(progress * Math.PI), 2.0D);
        enclosureRadius *= 1.0D - breathContraction * middleWeight;
        double topJoin = closure
                * smooth(Mth.clamp((progress - 0.70D) / 0.30D, 0.0D, 1.0D));
        // All four centerlines share one exact endpoint. The membrane strips
        // therefore close as part of their own final row instead of requiring
        // a separate roof patch.
        double radius = Mth.lerp(topJoin, enclosureRadius, 0.0D);
        radius += Math.sin(progress * Math.PI) * outwardBulge;
        double heightProgress = height * eased - height * (1.0D - emergence)
                - PIXEL * 0.45D;
        return center.add(radial.scale(radius))
                .add(normal.scale(heightProgress + 0.006D));
    }

    private static Vec3 curvedMembranePoint(
            Vec3 from, Vec3 to, Vec3 center, Vec3 normal,
            double progress, Vec3 offset, double curve) {
        Vec3 point = lerp(from, to, progress);
        Vec3 radial = point.subtract(center);
        Vec3 planar = radial.subtract(normal.scale(radial.dot(normal)));
        if (planar.lengthSqr() > 1.0E-8D) {
            point = point.add(planar.normalize().scale(
                    Math.sin(progress * Math.PI) * curve));
        }
        return point.add(offset);
    }

    /** Renders one shared-ring tube so adjacent bends have no open gaps. */
    private static void renderTenderFleshPillarTube(
            PoseStack.Pose pose,
            VertexConsumer intactVertices,
            VertexConsumer brokenVertices,
            Vec3[] points,
            double width,
            int segments,
            Vec3 referenceNormal,
            Vec3 initialSide,
            long seed,
            int packedLight,
            int shade,
            int brokenShade,
            double breathContraction,
            int damage,
            int requiredHits,
            MudSurfaceAppearance.Appearance intactAppearance,
            MudSurfaceAppearance.Appearance brokenAppearance) {
        Vec3[][] rings = TENDER_FLESH_RING_POINTS;
        Vec3 transportedSide = initialSide;
        for (int node = 0; node <= segments; node++) {
            Vec3 previous = points[Math.max(0, node - 1)];
            Vec3 next = points[Math.min(segments, node + 1)];
            Vec3 direction = next.subtract(previous);
            if (direction.lengthSqr() <= 1.0E-8D) {
                direction = referenceNormal;
            } else {
                direction = direction.normalize();
            }
            Vec3 side = transportedSide.subtract(
                    direction.scale(transportedSide.dot(direction)));
            if (side.lengthSqr() <= 1.0E-8D) {
                side = referenceNormal.cross(direction);
            }
            if (side.lengthSqr() <= 1.0E-8D) {
                side = new Vec3(1.0D, 0.0D, 0.0D).cross(direction);
            }
            if (side.lengthSqr() <= 1.0E-8D) {
                side = new Vec3(0.0D, 0.0D, 1.0D);
            }
            side = side.normalize();
            if (side.dot(transportedSide) < 0.0D) {
                side = side.scale(-1.0D);
            }
            transportedSide = side;
            Vec3 depth = direction.cross(side);
            if (depth.lengthSqr() <= 1.0E-8D) {
                depth = referenceNormal;
            } else {
                depth = depth.normalize();
            }
            double progress = (double) node / segments;
            double half = tenderFleshPillarHalfWidth(
                    width, progress, breathContraction);
            rings[node][0] = points[node].subtract(side.scale(half)).subtract(depth.scale(half));
            rings[node][1] = points[node].add(side.scale(half)).subtract(depth.scale(half));
            rings[node][2] = points[node].add(side.scale(half)).add(depth.scale(half));
            rings[node][3] = points[node].subtract(side.scale(half)).add(depth.scale(half));
        }
        for (int segment = 0; segment < segments; segment++) {
            MudTextureUv.Region uv = MudTextureUv.sample(seed + segment * 31L, 8);
            for (int face = 0; face < 4; face++) {
                int nextFace = (face + 1) % 4;
                boolean damaged = tenderFleshPatchDamaged(
                        seed, segment, face, segments, damage, requiredHits);
                VertexConsumer vertices = damaged ? brokenVertices : intactVertices;
                MudSurfaceAppearance.Appearance appearance = damaged
                        ? brokenAppearance : intactAppearance;
                MudTextureUv.Region mappedUv = appearanceUv(uv, appearance);
                renderAdhesionSheetQuad(
                        pose, vertices,
                        rings[segment][face], rings[segment][nextFace],
                        rings[segment + 1][nextFace], rings[segment + 1][face],
                        mappedUv, 255, packedLight,
                        damaged ? brokenShade : shade, appearance);
            }
        }
        MudTextureUv.Region capUv = MudTextureUv.sample(seed + 0x1234L, 8);
        VertexConsumer capVertices = requiredHits > 0 && damage >= requiredHits
                ? brokenVertices : intactVertices;
        MudSurfaceAppearance.Appearance capAppearance = requiredHits > 0 && damage >= requiredHits
                ? brokenAppearance : intactAppearance;
        renderAdhesionSheetQuad(
                pose, capVertices,
                rings[segments][0], rings[segments][1],
                rings[segments][2], rings[segments][3],
                appearanceUv(capUv, capAppearance), 255, packedLight,
                requiredHits > 0 && damage >= requiredHits ? brokenShade : shade,
                capAppearance);
    }

    private static boolean tenderFleshPatchDamaged(
            long seed, int segment, int face, int segments,
            int damage, int requiredHits) {
        if (damage <= 0 || requiredHits <= 0) {
            return false;
        }
        int groupCount = Math.max(1, segments * 2);
        int damagedGroups = Math.min(groupCount,
                (damage * groupCount + requiredHits - 1) / requiredHits);
        int group = segment * 2 + (face & 1);
        int offset = Math.floorMod((int) (seed ^ seed >>> 32), groupCount);
        int rank = Math.floorMod(offset - group, groupCount);
        return rank < damagedGroups;
    }

    private static double tenderFleshPillarHalfWidth(
            double width, double progress, double breathContraction) {
        double middleWeight = Math.pow(Math.sin(progress * Math.PI), 2.0D);
        double tipTaper = 1.0D - smooth(Mth.clamp(
                (progress - 0.82D) / 0.18D, 0.0D, 1.0D));
        return width * 0.5D * Mth.lerp(progress, 1.0D, 0.48D)
                * (1.0D + breathContraction * middleWeight * 0.65D)
                * tipTaper;
    }

    private static ResourceLocation surfaceCompressionTexture(
            SinkingMedium medium, boolean fleshTemplate) {
        return fleshTemplate
                ? medium.skinCoverageTexture() : medium.coverTexture();
    }

    private static void renderSideField(Minecraft minecraft, PoseStack.Pose pose,
            MultiBufferSource.BufferSource buffers,
            MudSideSurfaceEffectManager.SideImprint imprint,
            MudSideSurfaceEffectManager.FaceBasis basis,
            float partialTick, Vec3 camera, double renderDistanceSquared) {
        Vec3 normal = basis.normal;
        Vec3 axisU = basis.axisU;
        Vec3 axisV = basis.axisV;
        SinkingMedium medium = imprint.medium;
        boolean fleshTemplate = MudBehaviorContext.tenderFlesh(
                minecraft.level, imprint.pos, medium);
        double darkening = fleshTemplate ? 0.0D
                : MudMediumRuntime.value(
                        minecraft.level,
                        imprint.pos,
                        medium,
                        MudPhysicsParameter.SURFACE_HOLE_DARKENING);
        int darkness = Mth.clamp(
                (int) Math.round((1.0D - darkening * 0.82D) * 255.0D),
                32,
                255);
        double decalSize = imprint.physicalized()
                ? PIXEL : PIXEL * 1.012D;
        double pileSize = imprint.physicalized()
                ? PIXEL : PIXEL * 1.018D;
        MudSurfaceAppearance.Appearance decalAppearance = MudSurfaceAppearance.resolve(
                minecraft.level, imprint.pos,
                imprint.face, imprint.visualSource,
                surfaceCompressionTexture(medium, fleshTemplate));
        MudSurfaceAppearance.Appearance pileAppearance = MudSurfaceAppearance.resolve(
                minecraft.level, imprint.pos,
                imprint.face, imprint.visualSource, medium.coverTexture());
        for (MudSideSurfaceEffectManager.SideCell cell : imprint.cells.values()) {
            double localU = (cell.u + 0.5D) * PIXEL;
            double localV = (cell.v + 0.5D) * PIXEL;
            double centerX = basis.origin.x + axisU.x * localU + axisV.x * localV;
            double centerY = basis.origin.y + axisU.y * localU + axisV.y * localV;
            double centerZ = basis.origin.z + axisU.z * localU + axisV.z * localV;
            cell.packedLight = surfaceLight(
                    minecraft, centerX, centerY, centerZ, normal);
            double dx = centerX - camera.x;
            double dy = centerY - camera.y;
            double dz = centerZ - camera.z;
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared > renderDistanceSquared) {
                continue;
            }
            double visualHeightEpsilon = MudSurfaceCellBudget.visualHeightEpsilon(
                    distanceSquared, renderDistanceSquared);
            double depression = Mth.lerp(
                    partialTick, cell.previousDepression, cell.depression);
            double closure = Mth.lerp(
                    partialTick,
                    cell.previousClosureProgress,
                    cell.closureProgress);
            double pileHeight = Mth.lerp(
                    partialTick, cell.previousPileHeight, cell.pileHeight);
            float u0 = tileU(cell.seed);
            float v0 = tileV(cell.seed);
            float u1 = u0 + 1.0F / 16.0F;
            float v1 = v0 + 1.0F / 16.0F;
            if (depression > 0.003D) {
                int shade = Mth.clamp(
                        (int) Math.round(Mth.lerp(depression, 255.0D, darkness)),
                        darkness, 255);
                VertexConsumer vertices = MudSurfaceRenderBatchCache.decal(
                        buffers, decalAppearance, medium);
                renderClosingPlaneCell(
                        pose,
                        vertices,
                        centerX + normal.x * SURFACE_DECAL_NORMAL_OFFSET,
                        centerY + normal.y * SURFACE_DECAL_NORMAL_OFFSET,
                        centerZ + normal.z * SURFACE_DECAL_NORMAL_OFFSET,
                        axisU,
                        axisV,
                        normal,
                        decalSize,
                        closure,
                        cell.closureMask,
                        cell.renderedPatch,
                        u0,
                        v0,
                        u1,
                        v1,
                        shade,
                        255,
                        cell.packedLight,
                        decalAppearance);
            } else if (pileHeight > visualHeightEpsilon) {
                MudSurfaceVoxelRenderer.renderSidePile(
                        pose, buffers, imprint, cell, basis, partialTick,
                        centerX, centerY, centerZ, pileSize, pileHeight,
                        visualHeightEpsilon,
                        u0, v0, u1, v1, pileAppearance);
            }
        }
    }

    private static void renderClosingPlaneCell(PoseStack.Pose pose,
            VertexConsumer vertices, double centerX, double centerY, double centerZ,
            Vec3 axisX, Vec3 axisZ, Vec3 normal, double size,
            double closure, int closureMask,
            MudRenderedSurfaceGeometry.SurfacePatch renderedPatch,
            float u0, float v0, float u1, float v1,
            int shade, int alpha, int packedLight,
            MudSurfaceAppearance.Appearance appearance) {
        double progress = Mth.clamp(closure, 0.0D, 1.0D);
        boolean negativeX = (closureMask & 1) != 0;
        boolean positiveX = (closureMask & 2) != 0;
        boolean negativeZ = (closureMask & 4) != 0;
        boolean positiveZ = (closureMask & 8) != 0;
        double minX = -0.5D + (negativeX
                ? progress * (positiveX ? 0.5D : 1.0D) : 0.0D);
        double maxX = 0.5D - (positiveX
                ? progress * (negativeX ? 0.5D : 1.0D) : 0.0D);
        double minZ = -0.5D + (negativeZ
                ? progress * (positiveZ ? 0.5D : 1.0D) : 0.0D);
        double maxZ = 0.5D - (positiveZ
                ? progress * (negativeZ ? 0.5D : 1.0D) : 0.0D);
        if (maxX - minX <= 0.002D || maxZ - minZ <= 0.002D) {
            return;
        }
        if (renderedPatch != null && !renderedPatch.full()) {
            renderClosingPlanePatch(
                    pose, vertices, centerX, centerY, centerZ,
                    axisX, axisZ, normal, Math.min(size, PIXEL),
                    renderedPatch, minX, minZ, maxX, maxZ,
                    u0, v0, u1, v1,
                    appearance.shadedRed(shade),
                    appearance.shadedGreen(shade),
                    appearance.shadedBlue(shade),
                    alpha, packedLight, appearance);
            return;
        }
        renderPlaneRectRaw(
                pose,
                vertices,
                centerX,
                centerY,
                centerZ,
                axisX,
                axisZ,
                normal,
                minX * size,
                minZ * size,
                maxX * size,
                maxZ * size,
                appearance.u(Mth.lerp((float) (minX + 0.5D), u0, u1)),
                appearance.v(Mth.lerp((float) (minZ + 0.5D), v0, v1)),
                appearance.u(Mth.lerp((float) (maxX + 0.5D), u0, u1)),
                appearance.v(Mth.lerp((float) (maxZ + 0.5D), v0, v1)),
                appearance.shadedRed(shade),
                appearance.shadedGreen(shade),
                appearance.shadedBlue(shade),
                alpha,
                packedLight);
    }

    private static void renderClosingPlanePatch(PoseStack.Pose pose,
            VertexConsumer vertices, double centerX, double centerY, double centerZ,
            Vec3 axisX, Vec3 axisZ, Vec3 normal, double size,
            MudRenderedSurfaceGeometry.SurfacePatch renderedPatch,
            double minX, double minZ, double maxX, double maxZ,
            float u0, float v0, float u1, float v1,
            int red, int green, int blue, int alpha, int packedLight,
            MudSurfaceAppearance.Appearance appearance) {
        boolean reverseWinding = MudSurfaceVoxelGeometry.reverseWinding(
                axisX, axisZ, normal);
        for (MudRenderedSurfaceGeometry.PatchPolygon patch
                : renderedPatch.polygons()) {
            List<MudRenderedSurfaceGeometry.PatchVertex> clipped =
                    MudRenderedSurfaceGeometry.clipPolygon(
                            patch.vertices(), minX, minZ, maxX, maxZ);
            if (clipped.size() < 3) {
                continue;
            }
            MudRenderedSurfaceGeometry.PatchVertex first = clipped.getFirst();
            for (int index = 1; index + 1 < clipped.size(); index++) {
                MudRenderedSurfaceGeometry.PatchVertex second = clipped.get(index);
                MudRenderedSurfaceGeometry.PatchVertex third = clipped.get(index + 1);
                if (reverseWinding) {
                    renderPatchTriangle(pose, vertices,
                            centerX, centerY, centerZ, axisX, axisZ, normal, size,
                            first, third, second,
                            u0, v0, u1, v1, red, green, blue, alpha,
                            packedLight, appearance);
                } else {
                    renderPatchTriangle(pose, vertices,
                            centerX, centerY, centerZ, axisX, axisZ, normal, size,
                            first, second, third,
                            u0, v0, u1, v1, red, green, blue, alpha,
                            packedLight, appearance);
                }
            }
        }
    }

    private static void renderPatchTriangle(PoseStack.Pose pose,
            VertexConsumer vertices, double centerX, double centerY, double centerZ,
            Vec3 axisX, Vec3 axisZ, Vec3 normal, double size,
            MudRenderedSurfaceGeometry.PatchVertex first,
            MudRenderedSurfaceGeometry.PatchVertex second,
            MudRenderedSurfaceGeometry.PatchVertex third,
            float u0, float v0, float u1, float v1,
            int red, int green, int blue, int alpha, int packedLight,
            MudSurfaceAppearance.Appearance appearance) {
        for (MudRenderedSurfaceGeometry.PatchPolygon quad
                : MudRenderedSurfaceGeometry.triangleQuads(first, second, third)) {
            for (MudRenderedSurfaceGeometry.PatchVertex point : quad.vertices()) {
                renderPatchVertex(pose, vertices,
                        centerX, centerY, centerZ, axisX, axisZ, normal, size,
                        point, u0, v0, u1, v1, red, green, blue, alpha,
                        packedLight, appearance);
            }
        }
    }

    private static void renderPatchVertex(PoseStack.Pose pose,
            VertexConsumer vertices, double centerX, double centerY, double centerZ,
            Vec3 axisX, Vec3 axisZ, Vec3 normal, double size,
            MudRenderedSurfaceGeometry.PatchVertex point,
            float u0, float v0, float u1, float v1,
            int red, int green, int blue, int alpha, int packedLight,
            MudSurfaceAppearance.Appearance appearance) {
        planeVertex(pose, vertices,
                centerX, centerY, centerZ, axisX, axisZ,
                point.u() * size, point.v() * size,
                appearance.u(Mth.lerp((float) (point.u() + 0.5D), u0, u1)),
                appearance.v(Mth.lerp((float) (point.v() + 0.5D), v0, v1)),
                red, green, blue, alpha, packedLight, normal);
    }

    private static void renderPlaneRectRaw(PoseStack.Pose pose,
            VertexConsumer vertices, double centerX, double centerY, double centerZ,
            Vec3 axisX, Vec3 axisZ, Vec3 normal,
            double minX, double minZ, double maxX, double maxZ,
            float u0, float v0, float u1, float v1,
            int red, int green, int blue, int alpha, int packedLight) {
        if (MudSurfaceVoxelGeometry.reverseWinding(axisX, axisZ, normal)) {
            planeVertex(pose, vertices, centerX, centerY, centerZ, axisX, axisZ,
                    minX, minZ, u0, v0, red, green, blue, alpha, packedLight, normal);
            planeVertex(pose, vertices, centerX, centerY, centerZ, axisX, axisZ,
                    minX, maxZ, u0, v1, red, green, blue, alpha, packedLight, normal);
            planeVertex(pose, vertices, centerX, centerY, centerZ, axisX, axisZ,
                    maxX, maxZ, u1, v1, red, green, blue, alpha, packedLight, normal);
            planeVertex(pose, vertices, centerX, centerY, centerZ, axisX, axisZ,
                    maxX, minZ, u1, v0, red, green, blue, alpha, packedLight, normal);
            return;
        }
        planeVertex(pose, vertices, centerX, centerY, centerZ, axisX, axisZ,
                minX, minZ, u0, v0, red, green, blue, alpha, packedLight, normal);
        planeVertex(pose, vertices, centerX, centerY, centerZ, axisX, axisZ,
                maxX, minZ, u1, v0, red, green, blue, alpha, packedLight, normal);
        planeVertex(pose, vertices, centerX, centerY, centerZ, axisX, axisZ,
                maxX, maxZ, u1, v1, red, green, blue, alpha, packedLight, normal);
        planeVertex(pose, vertices, centerX, centerY, centerZ, axisX, axisZ,
                minX, maxZ, u0, v1, red, green, blue, alpha, packedLight, normal);
    }

    private static void renderBubble(Minecraft minecraft, PoseStack.Pose pose,
            MultiBufferSource.BufferSource buffers, MudSurfaceEffectManager.Bubble bubble,
            float partialTick) {
        double age = bubble.ageTicks + partialTick;
        double progress = Mth.clamp(age / Math.max(1.0D, bubble.lifeTicks), 0.0D, 1.0D);
        Vec3 axisX = bubble.tangent;
        Vec3 axisZ = bubble.bitangent;
        int packedLight = MudSurfaceEffectManager.exposedSurfaceLight(
                minecraft.level, bubble.center, bubble.normal);
        Direction face = Direction.getNearest(
                bubble.normal.x, bubble.normal.y, bubble.normal.z);
        MudSurfaceAppearance.Appearance appearance = MudSurfaceAppearance.resolve(
                minecraft.level, bubble.profilePos, face, bubble.medium.coverTexture());
        VertexConsumer vertices = MudSurfaceRenderBatchCache.bubble(buffers, appearance);
        int baseAlpha = bubble.medium == SinkingMedium.LIVING_SLIME
                ? 148 : bubble.medium.opaqueCoverage() ? 236 : 194;
        double rotation = (((bubble.seed >>> 17) & 1023L) / 1024.0D)
                * Math.PI * 2.0D;
        double cosine = Math.cos(rotation);
        double sine = Math.sin(rotation);
        Vec3 rotatedX = axisX.scale(cosine).add(axisZ.scale(sine));
        Vec3 rotatedZ = axisZ.scale(cosine).subtract(axisX.scale(sine));

        if (progress < 0.88D) {
            double growthProgress = Mth.clamp(progress / 0.88D, 0.0D, 1.0D);
            double growth = (1.0D - Math.pow(2.0D, -4.5D * growthProgress))
                    / (1.0D - Math.pow(2.0D, -4.5D));
            double radius = bubble.radius * growth;
            double heightScale = switch (bubble.medium) {
                case TAR -> 0.82D;
                case LIVING_SLIME -> 1.10D;
                default -> 0.94D;
            };
            double height = radius * heightScale;
            Vec3 emergingCenter = bubble.center.add(
                    bubble.normal.scale(-height * (1.0D - growth) * 0.72D));
            renderBlockBubble(pose, vertices, emergingCenter, bubble.normal,
                    rotatedX, rotatedZ, radius, height,
                    bubble.seed, baseAlpha, packedLight, appearance);
        }

        if (progress >= 0.86D) {
            double burst = smooth(Mth.clamp(
                    (progress - 0.86D) / 0.14D, 0.0D, 1.0D));
            int alpha = Mth.clamp((int) Math.round(
                    baseAlpha * (1.0D - burst) * 0.72D), 0, 255);
            renderPixelBurst(pose, vertices, bubble.center.add(bubble.normal.scale(0.003D)),
                    bubble.normal, rotatedX, rotatedZ, bubble.radius, burst,
                    bubble.seed, alpha, packedLight, appearance);
        }
    }

    private static void renderBlockBubble(PoseStack.Pose pose, VertexConsumer vertices,
            Vec3 center, Vec3 normal, Vec3 axisX, Vec3 axisZ,
            double radius, double height, long seed, int alpha, int packedLight,
            MudSurfaceAppearance.Appearance appearance) {
        if (radius <= 0.001D || alpha <= 1) {
            return;
        }
        MudTextureUv.Region texture = MudTextureUv.sample(seed, 8);
        float u0 = appearance.u(texture.u0());
        float v0 = appearance.v(texture.v0());
        float u1 = appearance.u(texture.u1());
        float v1 = appearance.v(texture.v1());
        double asymmetryX = 0.94D + ((seed >>> 19) & 15L) / 250.0D;
        double asymmetryZ = 0.94D + ((seed >>> 27) & 15L) / 250.0D;
        renderOrientedCuboid(pose, vertices, center, axisX, axisZ, normal,
                radius * 1.58D, radius * 1.58D, height * 0.82D,
                u0, v0, u1, v1,
                alpha, packedLight, appearance);
        renderOrientedCuboid(pose, vertices,
                center.add(normal.scale(height * 0.10D)),
                axisX, axisZ, normal,
                radius * 2.0D * asymmetryX, radius * 1.12D, height * 0.58D,
                u0, v0, u1, v1,
                alpha, packedLight, appearance);
        renderOrientedCuboid(pose, vertices,
                center.add(normal.scale(height * 0.12D)),
                axisX, axisZ, normal,
                radius * 1.12D, radius * 2.0D * asymmetryZ, height * 0.62D,
                u0, v0, u1, v1,
                alpha, packedLight, appearance);
        renderOrientedCuboid(pose, vertices,
                center.add(normal.scale(height * 0.02D)),
                axisX, axisZ, normal,
                radius * 1.05D, radius * 1.05D, height * 1.18D,
                u0, v0, u1, v1,
                alpha, packedLight, appearance);
    }

    private static void renderPixelBurst(PoseStack.Pose pose, VertexConsumer vertices,
            Vec3 center, Vec3 normal, Vec3 axisX, Vec3 axisZ,
            double radius, double progress, long seed, int alpha, int packedLight,
            MudSurfaceAppearance.Appearance appearance) {
        if (alpha <= 1) {
            return;
        }
        MudTextureUv.Region texture = MudTextureUv.sample(
                seed ^ 0x6a09e667f3bcc909L, 3);
        float u0 = appearance.u(texture.u0());
        float v0 = appearance.v(texture.v0());
        float u1 = appearance.u(texture.u1());
        float v1 = appearance.v(texture.v1());
        for (int piece = 0; piece < 6; piece++) {
            long hash = bubbleHash(seed + piece * 0x9E3779B97F4A7C15L);
            double angle = bubbleUnit(hash) * Math.PI * 2.0D;
            double reach = radius * progress
                    * (0.42D + bubbleUnit(hash >>> 11) * 0.82D);
            double lift = radius * progress
                    * (0.10D + bubbleUnit(hash >>> 23) * 0.52D);
            double size = Math.max(
                    PIXEL * 0.62D,
                    radius * (0.18D + bubbleUnit(hash >>> 37) * 0.16D)
                            * (1.0D - progress * 0.48D));
            Vec3 point = center
                    .add(axisX.scale(Math.cos(angle) * reach))
                    .add(axisZ.scale(Math.sin(angle) * reach))
                    .add(normal.scale(lift));
            renderPlaneCell(pose, vertices, point, axisX, axisZ, size, normal,
                    u0, v0, u1, v1,
                    appearance.red(), appearance.green(), appearance.blue(),
                    alpha, packedLight);
        }
    }

    private static void renderOrientedCuboid(PoseStack.Pose pose,
            VertexConsumer vertices, Vec3 base,
            Vec3 axisX, Vec3 axisZ, Vec3 normal,
            double width, double depth, double height,
            float u0, float v0, float u1, float v1,
            int alpha, int packedLight) {
        renderOrientedCuboid(pose, vertices, base,
                axisX, axisZ, normal, width, depth, height,
                u0, v0, u1, v1, alpha, packedLight,
                MudSurfaceAppearance.Appearance.untinted());
    }

    private static void renderOrientedCuboid(PoseStack.Pose pose,
            VertexConsumer vertices, Vec3 base,
            Vec3 axisX, Vec3 axisZ, Vec3 normal,
            double width, double depth, double height,
            float u0, float v0, float u1, float v1,
            int alpha, int packedLight,
            MudSurfaceAppearance.Appearance appearance) {
        Vec3 hx = axisX.scale(width * 0.5D);
        Vec3 hz = axisZ.scale(depth * 0.5D);
        Vec3 lift = normal.scale(height);
        Vec3 a = base.subtract(hx).subtract(hz);
        Vec3 b = base.add(hx).subtract(hz);
        Vec3 c = base.add(hx).add(hz);
        Vec3 d = base.subtract(hx).add(hz);
        quad(pose, vertices, a.add(lift), b.add(lift), c.add(lift), d.add(lift),
                u0, v0, u1, v1,
                appearance.red(), appearance.green(), appearance.blue(),
                alpha, packedLight, normal);
        quad(pose, vertices, a, b, b.add(lift), a.add(lift),
                u0, v0, u1, v1,
                appearance.shadedRed(224), appearance.shadedGreen(224),
                appearance.shadedBlue(224), alpha, packedLight,
                axisZ.scale(-1.0D));
        quad(pose, vertices, b, c, c.add(lift), b.add(lift),
                u0, v0, u1, v1,
                appearance.shadedRed(210), appearance.shadedGreen(210),
                appearance.shadedBlue(210), alpha, packedLight, axisX);
        quad(pose, vertices, c, d, d.add(lift), c.add(lift),
                u0, v0, u1, v1,
                appearance.shadedRed(196), appearance.shadedGreen(196),
                appearance.shadedBlue(196), alpha, packedLight, axisZ);
        quad(pose, vertices, d, a, a.add(lift), d.add(lift),
                u0, v0, u1, v1,
                appearance.shadedRed(214), appearance.shadedGreen(214),
                appearance.shadedBlue(214), alpha, packedLight,
                axisX.scale(-1.0D));
    }

    private static long bubbleHash(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        return value ^ value >>> 33;
    }

    private static double bubbleUnit(long value) {
        return (value & 0xFFFFL) / 65535.0D;
    }

    private static void renderPlaneCellRaw(PoseStack.Pose pose, VertexConsumer vertices,
            double centerX, double centerY, double centerZ,
            Vec3 axisX, Vec3 axisZ, double size, Vec3 normal,
            float u0, float v0, float u1, float v1,
            int red, int green, int blue, int alpha, int packedLight) {
        double half = size * 0.5D;
        renderPlaneRectRaw(
                pose, vertices, centerX, centerY, centerZ,
                axisX, axisZ, normal,
                -half, -half, half, half,
                u0, v0, u1, v1,
                red, green, blue, alpha, packedLight);
    }

    private static void planeVertex(PoseStack.Pose pose, VertexConsumer vertices,
            double centerX, double centerY, double centerZ,
            Vec3 axisX, Vec3 axisZ, double localX, double localZ,
            float u, float v, int red, int green, int blue, int alpha,
            int packedLight, Vec3 normal) {
        double x = centerX + axisX.x * localX + axisZ.x * localZ;
        double y = centerY + axisX.y * localX + axisZ.y * localZ;
        double z = centerZ + axisX.z * localX + axisZ.z * localZ;
        vertices.addVertex(pose, (float) x, (float) y, (float) z)
                .setColor(red, green, blue, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static void renderPlaneCell(PoseStack.Pose pose, VertexConsumer vertices,
            Vec3 center, Vec3 axisX, Vec3 axisZ, double size, Vec3 normal,
            float u0, float v0, float u1, float v1,
            int red, int green, int blue, int alpha, int packedLight) {
        Vec3 hx = axisX.scale(size * 0.5D);
        Vec3 hz = axisZ.scale(size * 0.5D);
        quad(pose, vertices,
                center.subtract(hx).subtract(hz),
                center.add(hx).subtract(hz),
                center.add(hx).add(hz),
                center.subtract(hx).add(hz),
                u0, v0, u1, v1,
                red, green, blue, alpha, packedLight, normal);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer vertices,
            Vec3 a, Vec3 b, Vec3 c, Vec3 d,
            float u0, float v0, float u1, float v1,
            int red, int green, int blue, int alpha, int packedLight, Vec3 normal) {
        Vec3 geometricNormal = b.subtract(a).cross(c.subtract(a));
        if (geometricNormal.dot(normal) < 0.0D) {
            vertex(pose, vertices, a, u0, v0,
                    red, green, blue, alpha, packedLight, normal);
            vertex(pose, vertices, d, u0, v1,
                    red, green, blue, alpha, packedLight, normal);
            vertex(pose, vertices, c, u1, v1,
                    red, green, blue, alpha, packedLight, normal);
            vertex(pose, vertices, b, u1, v0,
                    red, green, blue, alpha, packedLight, normal);
            return;
        }
        vertex(pose, vertices, a, u0, v0, red, green, blue, alpha, packedLight, normal);
        vertex(pose, vertices, b, u1, v0, red, green, blue, alpha, packedLight, normal);
        vertex(pose, vertices, c, u1, v1, red, green, blue, alpha, packedLight, normal);
        vertex(pose, vertices, d, u0, v1, red, green, blue, alpha, packedLight, normal);
    }

    private static void triangle(PoseStack.Pose pose, VertexConsumer vertices,
            Vec3 a, Vec3 b, Vec3 c,
            float u0, float v0, float u1, float v1, float u2, float v2,
            int red, int green, int blue, int alpha, int packedLight, Vec3 normal) {
        vertex(pose, vertices, a, u0, v0, red, green, blue, alpha, packedLight, normal);
        vertex(pose, vertices, b, u1, v1, red, green, blue, alpha, packedLight, normal);
        vertex(pose, vertices, c, u2, v2, red, green, blue, alpha, packedLight, normal);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vertices,
            Vec3 point, float u, float v, int red, int green, int blue, int alpha,
            int packedLight, Vec3 normal) {
        vertices.addVertex(pose, (float) point.x, (float) point.y, (float) point.z)
                .setColor(red, green, blue, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static float tileU(long hash) {
        return ((hash >>> 8) & 15L) / 16.0F;
    }

    private static float tileV(long hash) {
        return ((hash >>> 12) & 15L) / 16.0F;
    }

    private static double smooth(double value) {
        double clamped = Mth.clamp(value, 0.0D, 1.0D);
        return clamped * clamped * (3.0D - clamped * 2.0D);
    }
}

package com.fish.mirebound.client;

import com.fish.mirebound.stain.MudFootprintBlock;
import com.fish.mirebound.stain.MudFootprintBlockEntity;
import com.fish.mirebound.mud.MudPhysicsSettings;
import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.compat.sable.SableCompat;
import com.fish.mirebound.client.config.MireboundClientSettings;
import com.fish.mirebound.client.config.MireboundClientSettings.ClientOption;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class MudFootprintBlockEntityRenderer implements BlockEntityRenderer<MudFootprintBlockEntity> {
    private static final float FLOW_SURFACE_OFFSET = 0.0008F;
    private static final float CORE_SIZE = 0.25F;
    private static final float MIN_SPREAD = 0.030F;
    private static final float SPREAD_VARIATION = 0.025F;
    private static final float UV_TILE = 0.25F;
    private static final float PIXEL_UV_TILE = 1.0F / 16.0F;
    private static final int CEILING_ZONE_COLUMNS = 4;
    private static final int CEILING_ZONE_ROWS = 3;
    private static final int CEILING_ZONE_COUNT = CEILING_ZONE_COLUMNS * CEILING_ZONE_ROWS;
    private static final Direction[] DIRECTIONS = Direction.values();
    private final long[] ceilingZonePixels = new long[CEILING_ZONE_COUNT];
    private final float[] ceilingZoneScores = new float[CEILING_ZONE_COUNT];
    private final long[] wallCells = new long[16 * 16];
    private final long[] wallFlowPixels = new long[MudWallFlowLayout.MAX_FLOWS_PER_FACE];
    private final long[] wallFlowHashes = new long[MudWallFlowLayout.MAX_FLOWS_PER_FACE];
    private final float[] wallFlowScores = new float[MudWallFlowLayout.MAX_FLOWS_PER_FACE];

    public MudFootprintBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(MudFootprintBlockEntity blockEntity, float partialTick, PoseStack poseStack,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (!MireboundClientSettings.clientOptionEnabled(
                ClientOption.SURFACE_DECALS)) {
            return;
        }
        PoseStack.Pose pose = poseStack.last();
        List<MudFootprintBlockEntity.Entry> entries = blockEntity.entries();
        int footprintFaceMask = 0;
        int preciseWallFaceMask = 0;
        for (MudFootprintBlockEntity.Entry entry : entries) {
            if (!entry.wallStain()) {
                footprintFaceMask |= 1 << entry.face().get3DDataValue();
            } else if (entry.wallPixels().length > 0) {
                preciseWallFaceMask |= 1 << entry.face().get3DDataValue();
            }
        }
        for (MudFootprintBlockEntity.Entry entry : entries) {
            if (!entry.wallStain() || entry.wallPixels().length > 0) {
                continue;
            }
            boolean preciseWallOnFace = (preciseWallFaceMask
                    & 1 << entry.face().get3DDataValue()) != 0;
            if (!Float.isFinite(surfacePlane(blockEntity, entry))) {
                continue;
            }
            SinkingMedium medium = entry.medium();
            float ageVisibility = ageVisibility(blockEntity, entry, partialTick);
            float stainStrength = entry.wallStain()
                    ? Mth.lerp(entry.strength(), 0.52F, 1.0F)
                    : smooth(Mth.clamp(entry.strength(), 0.0F, 1.0F));
            int alpha = Math.round(baseAlpha(medium)
                    * Mth.clamp(entry.fade(), 0.0F, 1.0F)
                    * ageVisibility
                    * stainStrength);
            if (alpha <= 1) {
                continue;
            }

            long hash = mix(entry.id());
            float coreWidth = entry.width();
            float coreHeight = entry.height();
            float radians = entry.yawDegrees() * Mth.DEG_TO_RAD;
            float cos = Mth.cos(radians);
            float sin = Mth.sin(radians);
            int tileX = (int) (hash & 3L);
            int tileY = (int) ((hash >>> 2) & 3L);
            float u0 = tileX * UV_TILE;
            float v0 = tileY * UV_TILE;
            float u1 = u0 + UV_TILE;
            float v1 = v0 + UV_TILE;

            VertexConsumer vertices = buffers.getBuffer(
                    preciseWallOnFace
                            ? MudSurfaceDecalRenderTypes.wallTranslucent(
                                    MudSkinTextureCache.renderTexture(medium.skinCoverageTexture()))
                            : RenderType.entityTranslucent(
                                    MudSkinTextureCache.renderTexture(medium.skinCoverageTexture())));
            if (!preciseWallOnFace) {
                renderRect(blockEntity, pose, vertices, entry,
                        0.0F, 0.0F, coreWidth * 0.5F, coreHeight * 0.5F,
                        cos, sin, u0, v0, u1, v1, alpha, packedLight);
            }
            renderSpread(blockEntity, pose, vertices, entry, hash,
                    coreWidth, coreHeight, cos, sin, alpha, packedLight);
        }
        for (Direction face : DIRECTIONS) {
            if ((preciseWallFaceMask & 1 << face.get3DDataValue()) != 0) {
                renderPreciseWallStain(
                        blockEntity, face, entries, partialTick, pose, buffers, packedLight);
            }
            if ((footprintFaceMask & 1 << face.get3DDataValue()) != 0) {
                renderFusedFootprints(blockEntity, face, entries, pose, buffers, packedLight);
            }
        }
    }

    private static void renderFusedFootprints(MudFootprintBlockEntity blockEntity, Direction face,
            List<MudFootprintBlockEntity.Entry> entries, PoseStack.Pose pose,
            MultiBufferSource buffers, int packedLight) {
        MudFootprintBlockEntity.Entry anchor = null;
        for (MudFootprintBlockEntity.Entry entry : entries) {
            if (!entry.wallStain() && entry.face() == face) {
                anchor = entry;
                break;
            }
        }
        if (anchor == null) {
            return;
        }
        float plane = surfacePlane(blockEntity, anchor);
        if (!Float.isFinite(plane)) {
            return;
        }
        MudFootprintTextureCache.TextureView texture = MudFootprintTextureCache.textureFor(blockEntity, face, entries);
        if (texture == null) {
            return;
        }
        if (texture.hasStablePixels()) {
            VertexConsumer stable = buffers.getBuffer(
                    MudSurfaceDecalRenderTypes.cutout(texture.stableLocation()));
            renderFullSurface(blockEntity, anchor,
                    pose, stable, face, plane, packedLight);
        }
        if (texture.hasTranslucentPixels()) {
            VertexConsumer translucent = buffers.getBuffer(
                    MudSurfaceDecalRenderTypes.translucent(texture.translucentLocation()));
            renderFullSurface(blockEntity, anchor,
                    pose, translucent, face, plane, packedLight);
        }
    }

    private static void renderFullSurface(MudFootprintBlockEntity blockEntity,
            MudFootprintBlockEntity.Entry anchor,
            PoseStack.Pose pose, VertexConsumer vertices,
            Direction face, float surfacePlane, int packedLight) {
        float minimum = MudFootprintTextureCache.CANVAS_MIN;
        float maximum = MudFootprintTextureCache.CANVAS_MAX;
        if (preciseProjectionAvailable(blockEntity, face)) {
            int first = Mth.floor(minimum);
            int last = Mth.ceil(maximum);
            float span = maximum - minimum;
            for (int vertical = first; vertical < last; vertical++) {
                float minimumVertical = Math.max(minimum, vertical);
                float maximumVertical = Math.min(maximum, vertical + 1.0F);
                for (int horizontal = first; horizontal < last; horizontal++) {
                    float minimumHorizontal = Math.max(minimum, horizontal);
                    float maximumHorizontal = Math.min(maximum, horizontal + 1.0F);
                    BlockPos supportPos = supportPos(
                            blockEntity.getBlockPos(), face, horizontal, vertical);
                    MudRenderedSurfaceGeometry.RenderedSurface rendered =
                            renderedSupportSurface(blockEntity, supportPos, face);
                    if (rendered != null) {
                        renderRenderedSurface(pose, vertices, rendered,
                                supportPos, blockEntity.getBlockPos(), face,
                                minimum, span, 255, packedLight);
                        continue;
                    }
                    float u0 = (minimumHorizontal - minimum) / span;
                    float u1 = (maximumHorizontal - minimum) / span;
                    float v0 = (minimumVertical - minimum) / span;
                    float v1 = (maximumVertical - minimum) / span;
                    projectedGridVertex(blockEntity, anchor, pose, vertices,
                            face, surfacePlane, minimumHorizontal, minimumVertical,
                            u0, v0, 255, packedLight);
                    projectedGridVertex(blockEntity, anchor, pose, vertices,
                            face, surfacePlane, minimumHorizontal, maximumVertical,
                            u0, v1, 255, packedLight);
                    projectedGridVertex(blockEntity, anchor, pose, vertices,
                            face, surfacePlane, maximumHorizontal, maximumVertical,
                            u1, v1, 255, packedLight);
                    projectedGridVertex(blockEntity, anchor, pose, vertices,
                            face, surfacePlane, maximumHorizontal, minimumVertical,
                            u1, v0, 255, packedLight);
                }
            }
            return;
        }
        projectedGridVertex(blockEntity, anchor, pose, vertices,
                face, surfacePlane, minimum, minimum,
                0.0F, 0.0F, 255, packedLight);
        projectedGridVertex(blockEntity, anchor, pose, vertices,
                face, surfacePlane, minimum, maximum,
                0.0F, 1.0F, 255, packedLight);
        projectedGridVertex(blockEntity, anchor, pose, vertices,
                face, surfacePlane, maximum, maximum,
                1.0F, 1.0F, 255, packedLight);
        projectedGridVertex(blockEntity, anchor, pose, vertices,
                face, surfacePlane, maximum, minimum,
                1.0F, 0.0F, 255, packedLight);
    }

    private void renderPreciseWallStain(MudFootprintBlockEntity blockEntity, Direction face,
            List<MudFootprintBlockEntity.Entry> entries, float partialTick, PoseStack.Pose pose,
            MultiBufferSource buffers, int packedLight) {
        MudFootprintBlockEntity.Entry anchor = null;
        for (MudFootprintBlockEntity.Entry entry : entries) {
            if (entry.wallStain() && entry.face() == face
                    && entry.wallPixels().length > 0) {
                anchor = entry;
                break;
            }
        }
        if (anchor == null) {
            return;
        }
        float surfacePlane = surfacePlane(blockEntity, anchor);
        if (!Float.isFinite(surfacePlane)) {
            return;
        }
        MudWallTextureCache.TextureView texture =
                MudWallTextureCache.textureFor(blockEntity, face, entries);
        if (texture == null) {
            return;
        }
        Object subLevel = SableCompat.containingSubLevel(blockEntity);
        boolean physicalized = subLevel != null;
        Vec3 localDown = localGravity(subLevel);
        Vec3 faceNormal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
        double normalGravity = faceNormal.dot(localDown);
        Vec3 surfaceGravity = localDown.subtract(faceNormal.scale(normalGravity));
        boolean lowerFaceOwnsFlow = !physicalized
                && face.getAxis().isHorizontal()
                && blockEntity.getLevel() != null
                && blockEntity.getLevel().getBlockEntity(blockEntity.getBlockPos().below())
                        instanceof MudFootprintBlockEntity lower
                && lower.hasPreciseWallStain(face);
        float anchorHorizontal = face == Direction.NORTH || face == Direction.SOUTH
                || face.getAxis() == Direction.Axis.Y
                ? anchor.localX()
                : anchor.localZ();
        float centerX = 0.5F - anchorHorizontal;
        float centerZ = face.getAxis() == Direction.Axis.Y
                ? 0.5F - anchor.localZ()
                : 0.5F - anchor.localY();
        BlockPos supportPos = blockEntity.getBlockPos()
                .relative(face.getOpposite());
        MudRenderedSurfaceGeometry.RenderedSurface rendered =
                renderedSupportSurface(blockEntity, supportPos, face);
        if (texture.hasStablePixels()) {
            VertexConsumer stable = buffers.getBuffer(
                    MudSurfaceDecalRenderTypes.wallCutout(texture.stableLocation()));
            if (rendered != null) {
                renderRenderedSurface(pose, stable, rendered,
                        supportPos, blockEntity.getBlockPos(), face,
                        0.0F, 1.0F, 255, packedLight);
            } else {
                renderSurfaceRect(blockEntity, pose, stable, anchor,
                        surfacePlane, centerX, centerZ, 0.5F, 0.5F,
                        1.0F, 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, 255, packedLight);
            }
        }
        if (texture.hasTranslucentPixels()) {
            VertexConsumer translucent = buffers.getBuffer(
                    MudSurfaceDecalRenderTypes.wallTranslucent(texture.translucentLocation()));
            if (rendered != null) {
                renderRenderedSurface(pose, translucent, rendered,
                        supportPos, blockEntity.getBlockPos(), face,
                        0.0F, 1.0F, 255, packedLight);
            } else {
                renderSurfaceRect(blockEntity, pose, translucent, anchor,
                        surfacePlane, centerX, centerZ, 0.5F, 0.5F,
                        1.0F, 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, 255, packedLight);
            }
        }
        for (MudFootprintBlockEntity.Entry entry : entries) {
            if (!entry.wallStain() || entry.face() != face
                    || entry.wallPixels().length == 0) {
                continue;
            }
            // A tilted physical cube can have two or three underside faces. Each face whose
            // outward normal has a meaningful downward component owns its own hanging drips.
            if (normalGravity > 0.30D) {
                renderCeilingDrips(blockEntity, entry, surfacePlane, localDown,
                        partialTick, pose, buffers, packedLight, 1.0F);
            } else if (surfaceGravity.lengthSqr() > 0.025D) {
                Vec3 gridGravity = gridDirection(face, surfaceGravity.normalize());
                renderWallFlows(blockEntity, entry, surfacePlane, partialTick,
                        pose, buffers, packedLight,
                        (float) gridGravity.x, (float) gridGravity.y,
                        physicalized, lowerFaceOwnsFlow);
            }
        }
    }

    private static Vec3 localGravity(Object subLevel) {
        Vec3 local = subLevel == null
                ? new Vec3(0.0D, -1.0D, 0.0D)
                : SableCompat.toLocalDirection(subLevel, new Vec3(0.0D, -1.0D, 0.0D));
        return local == null || local.lengthSqr() <= 1.0E-8D
                ? new Vec3(0.0D, -1.0D, 0.0D)
                : local.normalize();
    }

    private static Vec3 gridDirection(Direction face, Vec3 localDirection) {
        return switch (face.getAxis()) {
            case X -> new Vec3(localDirection.z, localDirection.y, 0.0D);
            case Y -> new Vec3(localDirection.x, localDirection.z, 0.0D);
            case Z -> new Vec3(localDirection.x, localDirection.y, 0.0D);
        };
    }

    private void renderWallFlows(MudFootprintBlockEntity blockEntity, MudFootprintBlockEntity.Entry entry,
            float surfacePlane, float partialTick, PoseStack.Pose pose, MultiBufferSource buffers, int packedLight,
            float gravityHorizontal, float gravityVertical, boolean physicalized, boolean lowerFaceOwnsFlow) {
        if (blockEntity.getLevel() == null) {
            return;
        }
        Arrays.fill(wallCells, 0L);
        for (long pixel : entry.wallPixels()) {
            int cell = MudFootprintBlockEntity.wallPixelHorizontal(pixel)
                    | MudFootprintBlockEntity.wallPixelVertical(pixel) << 4;
            wallCells[cell] = pixel;
        }
        float gravityLength = Mth.sqrt(gravityHorizontal * gravityHorizontal + gravityVertical * gravityVertical);
        if (gravityLength <= 0.001F) {
            return;
        }
        gravityHorizontal /= gravityLength;
        gravityVertical /= gravityLength;
        int downstreamX = Math.abs(gravityHorizontal) >= Math.abs(gravityVertical)
                ? Integer.signum(Math.round(gravityHorizontal)) : 0;
        int downstreamY = Math.abs(gravityVertical) >= Math.abs(gravityHorizontal)
                ? Integer.signum(Math.round(gravityVertical)) : 0;
        boolean allowOutside = physicalized || downstreamX != 0 || downstreamY >= 0 || !lowerFaceOwnsFlow;
        int flowCount = MudWallFlowLayout.select(
                wallCells, blockEntity.getBlockPos(), entry.face(), downstreamX, downstreamY,
                allowOutside, MudPhysicsSettings.wallStainDripChance(),
                wallFlowPixels, wallFlowHashes, wallFlowScores);
        for (int index = 0; index < flowCount; index++) {
            renderWallFlow(blockEntity, entry, surfacePlane,
                    wallFlowPixels[index], wallFlowHashes[index], partialTick, pose, buffers, packedLight,
                    gravityHorizontal, gravityVertical, physicalized);
        }
    }

    private static void renderWallFlow(MudFootprintBlockEntity blockEntity, MudFootprintBlockEntity.Entry entry,
            float surfacePlane, long pixel, long hash, float partialTick, PoseStack.Pose pose,
            MultiBufferSource buffers,
            int packedLight, float gravityHorizontal, float gravityVertical, boolean physicalized) {
        float age = pixelAge(blockEntity, pixel) + partialTick;
        float elapsed = Math.max(0.0F, age - MudPhysicsSettings.wallStainFadeInTicks());
        float timeConstant = MudPhysicsSettings.wallStainFlowDurationTicks()
                * Mth.lerp(unitNoise(hash ^ 0x510e527fade682d1L), 0.72F, 1.38F);
        float progress = (float) Math.pow(1.0F - Math.exp(-elapsed / Math.max(1.0F, timeConstant)), 1.32D);
        if (progress <= 0.002F) {
            return;
        }
        float strength = MudFootprintBlockEntity.wallPixelStrength(pixel);
        float visibility = pixelLifetimeVisibility(pixel, age);
        int alpha = MudWallTextureCache.effectiveWallAlpha(strength, visibility);
        if (alpha <= 1) {
            return;
        }
        int x = MudFootprintBlockEntity.wallPixelHorizontal(pixel);
        int y = MudFootprintBlockEntity.wallPixelVertical(pixel);
        float widthPixels = Mth.lerp(unitNoise(hash ^ 0x6a09e667f3bcc909L), 0.72F, 1.38F)
                * (0.88F + strength * 0.18F);
        float halfWidth = widthPixels / 32.0F;
        float horizontal = (x + 0.5F) / 16.0F
                + (unitNoise(hash ^ 0x3c6ef372fe94f82bL) - 0.5F) * (1.0F / 96.0F);
        float vertical = (y + 0.5F) / 16.0F;
        horizontal += gravityHorizontal * (0.5F / 16.0F);
        vertical += gravityVertical * (0.5F / 16.0F);
        float lengthNoise = (float) Math.pow(unitNoise(hash ^ 0x1f83d9abfb41bd6bL), 1.28D);
        float maximumLength = MudPhysicsSettings.wallStainFlowPreviewCells()
                * Mth.lerp(lengthNoise, 0.45F, 1.12F)
                / 16.0F;
        float length = progress * maximumLength;
        if (physicalized) {
            length = Math.min(length, distanceToSurfaceEdge(
                    horizontal, vertical, gravityHorizontal, gravityVertical));
        }
        if (length <= 0.001F) {
            return;
        }
        SinkingMedium medium = MudFootprintBlockEntity.wallPixelMedium(pixel);
        MudSurfaceAppearance.Appearance appearance = MudSurfaceAppearance.resolve(
                blockEntity.getLevel(), entry.visualSource(), medium.skinCoverageTexture());
        int tileX = (int) ((hash >>> 8) & 15L);
        int tileY = (int) ((hash >>> 12) & 15L);
        float u0 = appearance.u(tileX * PIXEL_UV_TILE);
        float v0 = appearance.v(tileY * PIXEL_UV_TILE);
        float u1 = appearance.u((tileX + 1) * PIXEL_UV_TILE);
        float v1 = appearance.v((tileY + 1) * PIXEL_UV_TILE);
        int brightnessShade = brightnessShade(medium, x, y);
        VertexConsumer vertices = buffers.getBuffer(
                MudSurfaceRenderBatchCache.wallFlowRenderType(appearance.texture()));
        surfaceRibbonQuad(pose, vertices, entry.face(), surfacePlane,
                horizontal, vertical,
                horizontal + gravityHorizontal * length,
                vertical + gravityVertical * length,
                halfWidth,
                u0, v0, u1, v1,
                appearance.shadedRed(brightnessShade),
                appearance.shadedGreen(brightnessShade),
                appearance.shadedBlue(brightnessShade),
                Math.round(alpha * (0.18F + strength * 0.16F)),
                Math.round(alpha * (0.45F + strength * 0.18F)), packedLight);
    }

    private static float distanceToSurfaceEdge(float horizontal, float vertical,
            float directionHorizontal, float directionVertical) {
        float distance = Float.POSITIVE_INFINITY;
        if (directionHorizontal > 0.0001F) {
            distance = Math.min(distance, (1.0F - horizontal) / directionHorizontal);
        } else if (directionHorizontal < -0.0001F) {
            distance = Math.min(distance, -horizontal / directionHorizontal);
        }
        if (directionVertical > 0.0001F) {
            distance = Math.min(distance, (1.0F - vertical) / directionVertical);
        } else if (directionVertical < -0.0001F) {
            distance = Math.min(distance, -vertical / directionVertical);
        }
        return Math.max(0.0F, distance - 0.002F);
    }

    private static void surfaceRibbonQuad(PoseStack.Pose pose, VertexConsumer vertices,
            Direction face, float surfacePlane,
            float startHorizontal, float startVertical, float endHorizontal, float endVertical,
            float halfWidth,
            float u0, float v0, float u1, float v1,
            int red, int green, int blue,
            int topAlpha, int bottomAlpha, int packedLight) {
        float deltaH = endHorizontal - startHorizontal;
        float deltaV = endVertical - startVertical;
        float inverseLength = Mth.invSqrt(deltaH * deltaH + deltaV * deltaV);
        float sideH = -deltaV * inverseLength * halfWidth;
        float sideV = deltaH * inverseLength * halfWidth;
        surfaceGridVertex(pose, vertices, face, surfacePlane, startHorizontal + sideH, startVertical + sideV,
                u0, v0, red, green, blue, topAlpha, packedLight);
        surfaceGridVertex(pose, vertices, face, surfacePlane, endHorizontal + sideH, endVertical + sideV,
                u0, v1, red, green, blue, bottomAlpha, packedLight);
        surfaceGridVertex(pose, vertices, face, surfacePlane, endHorizontal - sideH, endVertical - sideV,
                u1, v1, red, green, blue, bottomAlpha, packedLight);
        surfaceGridVertex(pose, vertices, face, surfacePlane, startHorizontal - sideH, startVertical - sideV,
                u1, v0, red, green, blue, topAlpha, packedLight);
    }

    private static void surfaceGridVertex(PoseStack.Pose pose, VertexConsumer vertices, Direction face,
            float surfacePlane, float horizontal, float vertical,
            float u, float v, int alpha, int packedLight) {
        surfaceGridVertex(pose, vertices, face, surfacePlane, horizontal, vertical,
                u, v, 255, 255, 255, alpha, packedLight);
    }

    private static void surfaceGridVertex(PoseStack.Pose pose, VertexConsumer vertices, Direction face,
            float surfacePlane, float horizontal, float vertical,
            float u, float v, int red, int green, int blue, int alpha, int packedLight) {
        float renderedPlane = surfacePlane
                + face.getAxisDirection().getStep() * FLOW_SURFACE_OFFSET;
        switch (face.getAxis()) {
            case X -> rawVertex(pose, vertices, renderedPlane, vertical, horizontal,
                    u, v, red, green, blue, alpha, packedLight, face.getStepX(), 0.0F, 0.0F);
            case Y -> rawVertex(pose, vertices, horizontal, renderedPlane, vertical,
                    u, v, red, green, blue, alpha, packedLight, 0.0F, face.getStepY(), 0.0F);
            case Z -> rawVertex(pose, vertices, horizontal, vertical, renderedPlane,
                    u, v, red, green, blue, alpha, packedLight, 0.0F, 0.0F, face.getStepZ());
        }
    }

    private static void projectedGridVertex(MudFootprintBlockEntity blockEntity,
            MudFootprintBlockEntity.Entry entry,
            PoseStack.Pose pose, VertexConsumer vertices, Direction face,
            float surfacePlane, float horizontal, float vertical,
            float u, float v, int alpha, int packedLight) {
        float renderedPlane = surfacePlane
                + face.getAxisDirection().getStep() * FLOW_SURFACE_OFFSET;
        Vec3 point = switch (face.getAxis()) {
            case X -> new Vec3(renderedPlane, vertical, horizontal);
            case Y -> new Vec3(horizontal, renderedPlane, vertical);
            case Z -> new Vec3(horizontal, vertical, renderedPlane);
        };
        ProjectedSurface projected = projectSurface(
                blockEntity, entry.face(), point.x, point.y, point.z);
        rawVertex(pose, vertices,
                (float) projected.point().x,
                (float) projected.point().y,
                (float) projected.point().z,
                u, v, 255, 255, 255, alpha, packedLight,
                (float) projected.normal().x,
                (float) projected.normal().y,
                (float) projected.normal().z);
    }

    private static ProjectedSurface projectSurface(
            MudFootprintBlockEntity blockEntity, Direction face,
            double localX, double localY, double localZ) {
        Vec3 fallbackPoint = new Vec3(localX, localY, localZ);
        Vec3 fallbackNormal = new Vec3(
                face.getStepX(), face.getStepY(), face.getStepZ());
        if (blockEntity.getLevel() == null
                || !MudSurfaceClientSettings.preciseModelGeometry()) {
            return new ProjectedSurface(fallbackPoint, fallbackNormal);
        }
        BlockPos containerPos = blockEntity.getBlockPos();
        Vec3 worldPoint = fallbackPoint.add(
                containerPos.getX(), containerPos.getY(), containerPos.getZ());
        BlockPos supportPos = BlockPos.containing(
                worldPoint.subtract(fallbackNormal.scale(0.01D)));
        BlockState support = blockEntity.getLevel().getBlockState(supportPos);
        if (!MudFootprintBlock.isValidSupport(
                support, blockEntity.getLevel(), supportPos)) {
            return new ProjectedSurface(fallbackPoint, fallbackNormal);
        }
        double sourceX = worldPoint.x - supportPos.getX();
        double sourceY = worldPoint.y - supportPos.getY();
        double sourceZ = worldPoint.z - supportPos.getZ();
        MudRenderedSurfaceGeometry.SurfaceHit hit =
                MudRenderedSurfaceGeometry.surfaceHit(
                        blockEntity.getLevel(), supportPos, support, face,
                        sourceX, sourceY, sourceZ);
        if (hit == null) {
            return new ProjectedSurface(fallbackPoint, fallbackNormal);
        }
        double worldCoordinate = switch (face.getAxis()) {
            case X -> supportPos.getX() + hit.coordinate();
            case Y -> supportPos.getY() + hit.coordinate();
            case Z -> supportPos.getZ() + hit.coordinate();
        };
        Vec3 projected = switch (face.getAxis()) {
            case X -> new Vec3(
                    worldCoordinate - containerPos.getX(), localY, localZ);
            case Y -> new Vec3(
                    localX, worldCoordinate - containerPos.getY(), localZ);
            case Z -> new Vec3(
                    localX, localY, worldCoordinate - containerPos.getZ());
        };
        return new ProjectedSurface(
                projected.add(hit.normal().scale(FLOW_SURFACE_OFFSET)),
                hit.normal());
    }

    private static boolean preciseProjectionAvailable(
            MudFootprintBlockEntity blockEntity, Direction face) {
        if (blockEntity.getLevel() == null
                || !MudSurfaceClientSettings.preciseModelGeometry()
                || SableCompat.containingSubLevel(blockEntity) != null) {
            return false;
        }
        BlockPos container = blockEntity.getBlockPos();
        for (int vertical = -1; vertical <= 1; vertical++) {
            for (int horizontal = -1; horizontal <= 1; horizontal++) {
                BlockPos support = supportPos(
                        container, face, horizontal, vertical);
                if (renderedSupportSurface(blockEntity, support, face) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    private static MudRenderedSurfaceGeometry.RenderedSurface renderedSupportSurface(
            MudFootprintBlockEntity blockEntity, BlockPos supportPos,
            Direction face) {
        if (blockEntity.getLevel() == null
                || !MudSurfaceClientSettings.preciseModelGeometry()
                || SableCompat.containingSubLevel(blockEntity) != null) {
            return null;
        }
        BlockState support = blockEntity.getLevel().getBlockState(supportPos);
        if (!MudFootprintBlock.isValidSupport(
                support, blockEntity.getLevel(), supportPos)) {
            return null;
        }
        return MudRenderedSurfaceGeometry.renderedSurface(
                blockEntity.getLevel(), supportPos, support, face);
    }

    private static BlockPos supportPos(BlockPos container, Direction face,
            int horizontal, int vertical) {
        BlockPos base = container.relative(face.getOpposite());
        return switch (face.getAxis()) {
            case X -> base.offset(0, vertical, horizontal);
            case Y -> base.offset(horizontal, 0, vertical);
            case Z -> base.offset(horizontal, vertical, 0);
        };
    }

    private static void renderRenderedSurface(PoseStack.Pose pose,
            VertexConsumer vertices,
            MudRenderedSurfaceGeometry.RenderedSurface surface,
            BlockPos supportPos, BlockPos containerPos, Direction face,
            float canvasMinimum, float canvasSpan,
            int alpha, int packedLight) {
        for (MudRenderedSurfaceGeometry.RenderedQuad quad : surface.quads()) {
            renderedSurfaceVertex(pose, vertices, quad.first(), quad.normal(),
                    supportPos, containerPos, face,
                    canvasMinimum, canvasSpan, alpha, packedLight);
            renderedSurfaceVertex(pose, vertices, quad.second(), quad.normal(),
                    supportPos, containerPos, face,
                    canvasMinimum, canvasSpan, alpha, packedLight);
            renderedSurfaceVertex(pose, vertices, quad.third(), quad.normal(),
                    supportPos, containerPos, face,
                    canvasMinimum, canvasSpan, alpha, packedLight);
            renderedSurfaceVertex(pose, vertices, quad.fourth(), quad.normal(),
                    supportPos, containerPos, face,
                    canvasMinimum, canvasSpan, alpha, packedLight);
        }
    }

    private static void renderedSurfaceVertex(PoseStack.Pose pose,
            VertexConsumer vertices, Vec3 sourcePoint, Vec3 normal,
            BlockPos supportPos, BlockPos containerPos, Direction face,
            float canvasMinimum, float canvasSpan,
            int alpha, int packedLight) {
        Vec3 point = sourcePoint.add(
                supportPos.getX() - containerPos.getX(),
                supportPos.getY() - containerPos.getY(),
                supportPos.getZ() - containerPos.getZ())
                .add(normal.scale(FLOW_SURFACE_OFFSET));
        float horizontal = switch (face.getAxis()) {
            case X -> (float) point.z;
            case Y, Z -> (float) point.x;
        };
        float vertical = switch (face.getAxis()) {
            case Y -> (float) point.z;
            case X, Z -> (float) point.y;
        };
        float u = (horizontal - canvasMinimum) / canvasSpan;
        float v = (vertical - canvasMinimum) / canvasSpan;
        rawVertex(pose, vertices,
                (float) point.x, (float) point.y, (float) point.z,
                u, v, 255, 255, 255, alpha, packedLight,
                (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private void renderCeilingDrips(MudFootprintBlockEntity blockEntity,
            MudFootprintBlockEntity.Entry entry, float surfacePlane, Vec3 localDown,
            float partialTick, PoseStack.Pose pose,
            MultiBufferSource buffers, int packedLight, float entryFade) {
        if (blockEntity.getLevel() == null) {
            return;
        }
        Arrays.fill(ceilingZonePixels, 0L);
        Arrays.fill(ceilingZoneScores, -1.0F);
        float selectionChance = Mth.clamp(MudPhysicsSettings.wallStainDripChance() * 1.15F, 0.10F, 0.68F);
        long fallback = 0L;
        float fallbackScore = -1.0F;
        for (long pixel : entry.wallPixels()) {
            float strength = MudFootprintBlockEntity.wallPixelStrength(pixel);
            if (strength <= 0.07F) {
                continue;
            }
            int x = MudFootprintBlockEntity.wallPixelHorizontal(pixel);
            int z = MudFootprintBlockEntity.wallPixelVertical(pixel);
            int cell = x | z << 4;
            long hash = mix(entry.id() ^ cell * 0x9e3779b97f4a7c15L);
            float random = unitNoise(hash);
            float score = random * (0.55F + strength * 0.45F);
            if (score > fallbackScore) {
                fallback = pixel;
                fallbackScore = score;
            }
            if (random > selectionChance) {
                continue;
            }
            int zoneX = Math.min(CEILING_ZONE_COLUMNS - 1, x * CEILING_ZONE_COLUMNS / 16);
            int zoneZ = Math.min(CEILING_ZONE_ROWS - 1, z * CEILING_ZONE_ROWS / 16);
            int zone = zoneX + zoneZ * CEILING_ZONE_COLUMNS;
            if (score > ceilingZoneScores[zone]) {
                ceilingZoneScores[zone] = score;
                ceilingZonePixels[zone] = pixel;
            }
        }

        int limit = Math.min(CEILING_ZONE_COUNT, MudPhysicsSettings.wallStainCeilingDripCount());
        int rendered = 0;
        int zoneOffset = (int) (mix(entry.id()) & 0x7FFFFFFFL) % CEILING_ZONE_COUNT;
        for (int index = 0; index < CEILING_ZONE_COUNT && rendered < limit; index++) {
            int zone = (zoneOffset + index) % CEILING_ZONE_COUNT;
            long pixel = ceilingZonePixels[zone];
            if (pixel == 0L) {
                continue;
            }
            int cell = MudFootprintBlockEntity.wallPixelHorizontal(pixel)
                    | MudFootprintBlockEntity.wallPixelVertical(pixel) << 4;
            renderCeilingDrip(blockEntity, entry, surfacePlane, localDown, pixel,
                    mix(entry.id() ^ cell * 0x9e3779b97f4a7c15L), partialTick,
                    pose, buffers, packedLight, entryFade);
            rendered++;
        }
        if (rendered == 0 && fallback != 0L && limit > 0) {
            int cell = MudFootprintBlockEntity.wallPixelHorizontal(fallback)
                    | MudFootprintBlockEntity.wallPixelVertical(fallback) << 4;
            renderCeilingDrip(blockEntity, entry, surfacePlane, localDown, fallback,
                    mix(entry.id() ^ cell * 0x9e3779b97f4a7c15L),
                    partialTick, pose, buffers, packedLight, entryFade);
        }
    }

    private static void renderCeilingDrip(MudFootprintBlockEntity blockEntity,
            MudFootprintBlockEntity.Entry entry, float surfacePlane, Vec3 localDown,
            long pixel, long hash, float partialTick,
            PoseStack.Pose pose, MultiBufferSource buffers, int packedLight, float entryFade) {
        float age = pixelAge(blockEntity, pixel) + partialTick;
        float maturity = Mth.clamp(
                (age - MudPhysicsSettings.wallStainFadeInTicks())
                        / MudPhysicsSettings.wallStainFlowDurationTicks(),
                0.0F,
                1.0F);
        maturity = smooth(maturity);
        if (maturity <= 0.001F) {
            return;
        }
        float strength = MudFootprintBlockEntity.wallPixelStrength(pixel);
        float minimumLength = MudPhysicsSettings.wallStainCeilingDripMinLengthFactor();
        float lengthNoise = (float) Math.pow(unitNoise(hash ^ 0x243f6a8885a308d3L), 1.35D);
        float variation = Mth.lerp(lengthNoise, minimumLength, 1.0F);
        float configuredMaxLength = MudPhysicsSettings.wallStainCeilingDripMaxLength()
                * MudPhysicsSettings.wallStainCeilingDripLengthMultiplier();
        float targetLength = configuredMaxLength * variation;
        float maximumLength = targetLength * maturity;
        float normalizedLength = Mth.clamp(
                (variation - minimumLength) / Math.max(0.001F, 1.0F - minimumLength),
                0.0F,
                1.0F);
        float lengthWidthScale = Mth.lerp(smooth(normalizedLength), 1.16F, 0.48F);
        float halfWidth = MudPhysicsSettings.wallStainCeilingDripWidth()
                * (0.34F + strength * 0.34F)
                * Mth.lerp(unitNoise(hash ^ 0x13198a2e03707344L), 0.82F, 1.14F)
                * lengthWidthScale;
        float jitterX = (unitNoise(hash ^ 0xa4093822299f31d0L) - 0.5F) * 0.050F;
        float jitterZ = (unitNoise(hash ^ 0x082efa98ec4e6c89L) - 0.5F) * 0.050F;
        float horizontal = (MudFootprintBlockEntity.wallPixelHorizontal(pixel) + 0.5F) / 16.0F + jitterX;
        float vertical = (MudFootprintBlockEntity.wallPixelVertical(pixel) + 0.5F) / 16.0F + jitterZ;
        Vec3 anchor = surfacePoint(entry.face(), surfacePlane, horizontal, vertical).add(localDown.scale(0.002D));
        SinkingMedium medium = MudFootprintBlockEntity.wallPixelMedium(pixel);
        MudSurfaceAppearance.Appearance appearance = MudSurfaceAppearance.resolve(
                blockEntity.getLevel(), entry.visualSource(), medium.skinCoverageTexture());
        int tileX = (int) ((hash >>> 8) & 15L);
        int tileY = (int) ((hash >>> 12) & 15L);
        float u0 = appearance.u(tileX * PIXEL_UV_TILE);
        float v0 = appearance.v(tileY * PIXEL_UV_TILE);
        float u1 = appearance.u((tileX + 1) * PIXEL_UV_TILE);
        float v1 = appearance.v((tileY + 1) * PIXEL_UV_TILE);
        int brightnessShade = brightnessShade(
                medium,
                MudFootprintBlockEntity.wallPixelHorizontal(pixel),
                MudFootprintBlockEntity.wallPixelVertical(pixel));
        int red = appearance.shadedRed(brightnessShade);
        int green = appearance.shadedGreen(brightnessShade);
        int blue = appearance.shadedBlue(brightnessShade);

        float gameTime = blockEntity.getLevel().getGameTime() + partialTick;
        int baseCycle = MudPhysicsSettings.wallStainCeilingDripCycleTicks();
        float cycle = baseCycle * Mth.lerp(unitNoise(hash ^ 0x452821e638d01377L), 0.72F, 1.32F);
        float cycleOffset = unitNoise(hash ^ 0xbe5466cf34e90c6cL) * cycle;
        float phase = (gameTime + cycleOffset) % cycle / cycle;
        float detachThreshold = Mth.clamp(
                1.0F - MudPhysicsSettings.wallStainCeilingDripDetachChance() * 1.45F,
                0.42F,
                1.01F);
        boolean detaches = variation >= detachThreshold;
        float lifetimeVisibility = pixelLifetimeVisibility(pixel, age);
        int fullAlpha = MudWallTextureCache.effectiveWallAlpha(
                entryFade * Mth.lerp(strength, 0.62F, 1.0F), lifetimeVisibility);
        if (fullAlpha <= 1) {
            return;
        }
        VertexConsumer vertices = buffers.getBuffer(
                RenderType.entityTranslucent(appearance.texture()));
        if (!detaches) {
            renderCrossStrand(pose, vertices, anchor, anchor.add(localDown.scale(maximumLength)), localDown, halfWidth,
                    u0, v0, u1, v1, fullAlpha,
                    Math.round(fullAlpha * (0.52F + strength * 0.20F)), packedLight,
                    red, green, blue);
            return;
        }

        // Animate detachment with geometry only. Alpha cycling makes some shader packs
        // threshold the complete strand away in one frame.
        float detachStart = 0.72F;
        if (phase <= detachStart) {
            float strandLength = maximumLength
                    * smooth(Mth.clamp(phase / (detachStart - 0.05F), 0.0F, 1.0F));
            renderCrossStrand(pose, vertices, anchor, anchor.add(localDown.scale(strandLength)), localDown, halfWidth,
                    u0, v0, u1, v1, fullAlpha,
                    Math.round(fullAlpha * (0.52F + strength * 0.20F)), packedLight,
                    red, green, blue);
            return;
        }

        float dropProgress = Mth.clamp((phase - detachStart) / (1.0F - detachStart), 0.0F, 1.0F);
        float splitFraction = Mth.lerp(unitNoise(hash ^ 0x3f84d5b5b5470917L), 0.36F, 0.56F);
        float upperSplitLength = maximumLength * splitFraction;
        float lowerSplitLength = maximumLength - upperSplitLength;

        float retractTime = Mth.clamp(dropProgress / 0.34F, 0.0F, 1.0F);
        float retractProgress = 1.0F - (float) Math.pow(1.0F - retractTime, 3.0D);
        float upperLength = upperSplitLength * (1.0F - retractProgress);
        if (upperLength > 0.0015F) {
            renderCrossStrand(pose, vertices, anchor, anchor.add(localDown.scale(upperLength)), localDown,
                    halfWidth * (1.0F - retractProgress * 0.24F),
                    u0, v0, u1, v1, fullAlpha,
                    Math.round(fullAlpha * (0.62F + strength * 0.16F)), packedLight,
                    red, green, blue);
        }

        float contraction = smooth(dropProgress);
        float finalCollapse = 1.0F - smooth(Mth.clamp((dropProgress - 0.86F) / 0.14F, 0.0F, 1.0F));
        float compactLength = Math.max(halfWidth * 2.25F, 0.024F);
        float lowerLength = Mth.lerp(contraction, lowerSplitLength, compactLength)
                * Math.max(0.025F, finalCollapse);
        float lowerWidth = halfWidth
                * Mth.lerp(contraction, 0.86F, 0.42F)
                * Math.max(0.06F, finalCollapse);
        if (lowerLength > 0.0012F && lowerWidth > 0.0008F) {
            float initialCenter = upperSplitLength + lowerSplitLength * 0.5F;
            float fallDistance = maximumLength * 0.16F * dropProgress
                    + 1.05F * dropProgress * dropProgress;
            float lowerCenter = initialCenter + fallDistance;
            Vec3 center = anchor.add(localDown.scale(lowerCenter));
            renderCrossStrand(pose, vertices,
                    center.add(localDown.scale(-lowerLength * 0.5F)),
                    center.add(localDown.scale(lowerLength * 0.5F)), localDown,
                    lowerWidth,
                    u0, v0, u1, v1, fullAlpha, Math.round(fullAlpha * 0.82F), packedLight,
                    red, green, blue);
        }
    }

    private static Vec3 surfacePoint(Direction face, float surfacePlane, float horizontal, float vertical) {
        return switch (face.getAxis()) {
            case X -> new Vec3(surfacePlane, vertical, horizontal);
            case Y -> new Vec3(horizontal, surfacePlane, vertical);
            case Z -> new Vec3(horizontal, vertical, surfacePlane);
        };
    }

    private static int brightnessShade(SinkingMedium medium, int x, int y) {
        return Mth.clamp(Math.round(
                255.0F * MudCoverageAppearance.brightnessScale(medium, x, y)), 0, 255);
    }

    private static void renderCrossStrand(PoseStack.Pose pose, VertexConsumer vertices,
            Vec3 top, Vec3 bottom, Vec3 direction, float halfWidth,
            float u0, float v0, float u1, float v1, int topAlpha, int bottomAlpha, int packedLight,
            int red, int green, int blue) {
        Vec3 helper = Math.abs(direction.y) < 0.82D ? new Vec3(0.0D, 1.0D, 0.0D) : new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 sideA = direction.cross(helper).normalize();
        Vec3 sideB = direction.cross(sideA).normalize();
        strandPlane(pose, vertices, top, bottom, sideA, sideB, halfWidth,
                u0, v0, u1, v1, topAlpha, bottomAlpha, packedLight, red, green, blue);
        strandPlane(pose, vertices, top, bottom, sideB, sideA, halfWidth,
                u0, v0, u1, v1, topAlpha, bottomAlpha, packedLight, red, green, blue);
    }

    private static void strandPlane(PoseStack.Pose pose, VertexConsumer vertices,
            Vec3 top, Vec3 bottom, Vec3 side, Vec3 normal, float halfWidth,
            float u0, float v0, float u1, float v1, int topAlpha, int bottomAlpha, int packedLight,
            int red, int green, int blue) {
        Vec3 offset = side.scale(halfWidth);
        rawVertex(pose, vertices, top.subtract(offset), u0, v0,
                red, green, blue, topAlpha, packedLight, normal);
        rawVertex(pose, vertices, bottom.subtract(offset), u0, v1,
                red, green, blue, bottomAlpha, packedLight, normal);
        rawVertex(pose, vertices, bottom.add(offset), u1, v1,
                red, green, blue, bottomAlpha, packedLight, normal);
        rawVertex(pose, vertices, top.add(offset), u1, v0,
                red, green, blue, topAlpha, packedLight, normal);
    }

    private static void rawVertex(PoseStack.Pose pose, VertexConsumer vertices, Vec3 point,
            float u, float v, int red, int green, int blue,
            int alpha, int packedLight, Vec3 normal) {
        rawVertex(pose, vertices, (float) point.x, (float) point.y, (float) point.z,
                u, v, red, green, blue, alpha, packedLight,
                (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static void rawVertex(PoseStack.Pose pose, VertexConsumer vertices, float x, float y, float z,
            float u, float v, int alpha, int packedLight, float normalX, float normalY, float normalZ) {
        rawVertex(pose, vertices, x, y, z, u, v,
                255, 255, 255, alpha, packedLight, normalX, normalY, normalZ);
    }

    private static void rawVertex(PoseStack.Pose pose, VertexConsumer vertices, float x, float y, float z,
            float u, float v, int red, int green, int blue,
            int alpha, int packedLight, float normalX, float normalY, float normalZ) {
        vertices.addVertex(pose, x, y, z)
                .setColor(red, green, blue, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose, normalX, normalY, normalZ);
    }

    @Override
    public int getViewDistance() {
        return 48;
    }

    @Override
    public AABB getRenderBoundingBox(MudFootprintBlockEntity blockEntity) {
        var pos = blockEntity.getBlockPos();
        return new AABB(
                pos.getX() - 2.75D,
                pos.getY() - 2.75D,
                pos.getZ() - 2.75D,
                pos.getX() + 3.75D,
                pos.getY() + 3.75D,
                pos.getZ() + 3.75D);
    }

    private static void renderSpread(MudFootprintBlockEntity blockEntity,
            PoseStack.Pose pose, VertexConsumer vertices,
            MudFootprintBlockEntity.Entry entry, long hash, float coreWidth, float coreHeight,
            float cos, float sin, int alpha, int packedLight) {
        float halfWidth = coreWidth * 0.5F;
        float halfHeight = coreHeight * 0.5F;
        for (int side = 0; side < 4; side++) {
            long sideHash = mix(hash + side * 0x9e3779b97f4a7c15L);
            if (side == 3 && (sideHash & 3L) == 0L) {
                continue;
            }
            float sizeScale = Mth.clamp(Math.max(coreWidth, coreHeight) / CORE_SIZE, 0.85F, 1.45F);
            float extension = (MIN_SPREAD + ((sideHash >>> 4) & 15L) / 15.0F * SPREAD_VARIATION)
                    * sizeScale;
            float sideLength = side < 2 ? coreWidth : coreHeight;
            float length = sideLength * (0.42F + ((sideHash >>> 8) & 15L) / 15.0F * 0.34F);
            float travel = Math.max(0.0F, (sideLength - length) * 0.5F);
            float tangent = (((sideHash >>> 12) & 31L) / 31.0F * 2.0F - 1.0F) * travel;
            float centerX;
            float centerZ;
            float halfX;
            float halfZ;
            if (side == 0 || side == 1) {
                centerX = tangent;
                centerZ = (side == 0 ? -1.0F : 1.0F) * (halfHeight + extension * 0.5F);
                halfX = length * 0.5F;
                halfZ = extension * 0.5F;
            } else {
                centerX = (side == 2 ? -1.0F : 1.0F) * (halfWidth + extension * 0.5F);
                centerZ = tangent;
                halfX = extension * 0.5F;
                halfZ = length * 0.5F;
            }
            int fringeAlpha = Math.round(alpha * (0.48F + ((sideHash >>> 17) & 7L) / 7.0F * 0.18F));
            int tileX = (int) ((sideHash >>> 20) & 3L);
            int tileY = (int) ((sideHash >>> 22) & 3L);
            float u0 = tileX * UV_TILE;
            float v0 = tileY * UV_TILE;
            renderRect(blockEntity, pose, vertices, entry,
                    centerX, centerZ, halfX, halfZ, cos, sin,
                    u0, v0, u0 + UV_TILE, v0 + UV_TILE, fringeAlpha, packedLight);
        }
    }

    private static void renderRect(MudFootprintBlockEntity blockEntity,
            PoseStack.Pose pose, VertexConsumer vertices,
            MudFootprintBlockEntity.Entry entry, float centerX, float centerZ, float halfX, float halfZ,
            float cos, float sin, float u0, float v0, float u1, float v1, int alpha, int packedLight) {
        vertex(blockEntity, pose, vertices, entry,
                centerX - halfX, centerZ - halfZ, cos, sin, u0, v0, alpha, packedLight);
        vertex(blockEntity, pose, vertices, entry,
                centerX - halfX, centerZ + halfZ, cos, sin, u0, v1, alpha, packedLight);
        vertex(blockEntity, pose, vertices, entry,
                centerX + halfX, centerZ + halfZ, cos, sin, u1, v1, alpha, packedLight);
        vertex(blockEntity, pose, vertices, entry,
                centerX + halfX, centerZ - halfZ, cos, sin, u1, v0, alpha, packedLight);
    }

    private static void renderSurfaceRect(MudFootprintBlockEntity blockEntity,
            PoseStack.Pose pose, VertexConsumer vertices,
            MudFootprintBlockEntity.Entry entry, float surfacePlane,
            float centerX, float centerZ, float halfX, float halfZ,
            float cos, float sin, float u0, float v0, float u1, float v1, int alpha, int packedLight) {
        surfaceVertex(blockEntity, pose, vertices, entry, surfacePlane,
                centerX - halfX, centerZ - halfZ, cos, sin, u0, v0, alpha, packedLight);
        surfaceVertex(blockEntity, pose, vertices, entry, surfacePlane,
                centerX - halfX, centerZ + halfZ, cos, sin, u0, v1, alpha, packedLight);
        surfaceVertex(blockEntity, pose, vertices, entry, surfacePlane,
                centerX + halfX, centerZ + halfZ, cos, sin, u1, v1, alpha, packedLight);
        surfaceVertex(blockEntity, pose, vertices, entry, surfacePlane,
                centerX + halfX, centerZ - halfZ, cos, sin, u1, v0, alpha, packedLight);
    }

    private static void surfaceVertex(MudFootprintBlockEntity blockEntity,
            PoseStack.Pose pose, VertexConsumer vertices,
            MudFootprintBlockEntity.Entry entry, float surfacePlane,
            float offsetX, float offsetZ, float cos, float sin, float u, float v, int alpha, int packedLight) {
        float planeX = offsetX * cos - offsetZ * sin;
        float planeY = offsetX * sin + offsetZ * cos;
        Direction face = entry.face();
        float x = entry.localX();
        float y = entry.localY();
        float z = entry.localZ();
        if (face == Direction.UP || face == Direction.DOWN) {
            x += planeX;
            y = surfacePlane;
            z += planeY;
        } else if (face == Direction.NORTH || face == Direction.SOUTH) {
            x += planeX;
            y += planeY;
            z = surfacePlane;
        } else {
            x = surfacePlane;
            y += planeY;
            z += planeX;
        }
        ProjectedSurface projected = projectSurface(
                blockEntity, face, x, y, z);
        vertices.addVertex(pose,
                        (float) projected.point().x,
                        (float) projected.point().y,
                        (float) projected.point().z)
                .setColor(255, 255, 255, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose,
                        (float) projected.normal().x,
                        (float) projected.normal().y,
                        (float) projected.normal().z);
    }

    private static float surfacePlane(MudFootprintBlockEntity blockEntity,
            MudFootprintBlockEntity.Entry entry) {
        Direction face = entry.face();
        float stored = switch (face.getAxis()) {
            case X -> entry.localX();
            case Y -> entry.localY();
            case Z -> entry.localZ();
        };
        if (blockEntity.getLevel() == null) {
            return stored - face.getAxisDirection().getStep() * 0.006F;
        }
        BlockPos containerPos = blockEntity.getBlockPos();
        BlockPos supportPos = containerPos.relative(face.getOpposite());
        var support = blockEntity.getLevel().getBlockState(supportPos);
        if (!MudFootprintBlock.isValidSupport(
                support, blockEntity.getLevel(), supportPos)) {
            return Float.NaN;
        }
        float closest = Float.NaN;
        float closestDistance = Float.MAX_VALUE;
        for (AABB box : support.getCollisionShape(blockEntity.getLevel(), supportPos).toAabbs()) {
            double worldPlane = switch (face) {
                case WEST -> supportPos.getX() + box.minX;
                case EAST -> supportPos.getX() + box.maxX;
                case DOWN -> supportPos.getY() + box.minY;
                case UP -> supportPos.getY() + box.maxY;
                case NORTH -> supportPos.getZ() + box.minZ;
                case SOUTH -> supportPos.getZ() + box.maxZ;
            };
            float localPlane = (float) (worldPlane - switch (face.getAxis()) {
                case X -> containerPos.getX();
                case Y -> containerPos.getY();
                case Z -> containerPos.getZ();
            });
            float distance = Math.abs(localPlane - stored);
            if (distance < closestDistance) {
                closest = localPlane;
                closestDistance = distance;
            }
        }
        return closest;
    }

    private record ProjectedSurface(Vec3 point, Vec3 normal) {
    }

    private static void vertex(MudFootprintBlockEntity blockEntity,
            PoseStack.Pose pose, VertexConsumer vertices, MudFootprintBlockEntity.Entry entry,
            float offsetX, float offsetZ, float cos, float sin, float u, float v, int alpha, int packedLight) {
        float planeX = offsetX * cos - offsetZ * sin;
        float planeY = offsetX * sin + offsetZ * cos;
        Direction face = entry.face();
        float x = entry.localX();
        float y = entry.localY();
        float z = entry.localZ();
        if (face == Direction.UP || face == Direction.DOWN) {
            x += planeX;
            z += planeY;
        } else if (face == Direction.NORTH || face == Direction.SOUTH) {
            x += planeX;
            y += planeY;
        } else {
            z += planeX;
            y += planeY;
        }
        ProjectedSurface projected = projectSurface(
                blockEntity, face, x, y, z);
        vertices.addVertex(pose,
                        (float) projected.point().x,
                        (float) projected.point().y,
                        (float) projected.point().z)
                .setColor(255, 255, 255, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose,
                        (float) projected.normal().x,
                        (float) projected.normal().y,
                        (float) projected.normal().z);
    }

    private static float ageVisibility(MudFootprintBlockEntity blockEntity,
            MudFootprintBlockEntity.Entry entry, float partialTick) {
        if (MudPhysicsSettings.footprintPermanent() || blockEntity.getLevel() == null) {
            return 1.0F;
        }
        long duration = Math.max(1L, entry.expiresAt() - entry.createdAt());
        double fadeStart = entry.createdAt() + duration * 0.55D;
        double now = blockEntity.getLevel().getGameTime() + partialTick;
        if (now <= fadeStart) {
            return 1.0F;
        }
        return Mth.clamp((float) ((entry.expiresAt() - now) / Math.max(1.0D, entry.expiresAt() - fadeStart)), 0.0F, 1.0F);
    }

    private static int pixelAge(MudFootprintBlockEntity blockEntity, long pixel) {
        if (!MudFootprintBlockEntity.wallPixelHasCreationTime(pixel) || blockEntity.getLevel() == null) {
            return Integer.MAX_VALUE;
        }
        return MudFootprintBlockEntity.wallPixelAge(pixel, blockEntity.getLevel().getGameTime());
    }

    private static float pixelLifetimeVisibility(long pixel, float age) {
        if (MudPhysicsSettings.footprintPermanent()
                || !MudFootprintBlockEntity.wallPixelHasCreationTime(pixel)) {
            return 1.0F;
        }
        int lifetime = Math.max(1, MudPhysicsSettings.footprintLifetimeTicks());
        float fadeStart = lifetime * 0.55F;
        if (age <= fadeStart) {
            return 1.0F;
        }
        return Mth.clamp((lifetime - age) / Math.max(1.0F, lifetime - fadeStart), 0.0F, 1.0F);
    }

    private static int baseAlpha(SinkingMedium medium) {
        if (medium == SinkingMedium.LIVING_SLIME) {
            return 150;
        }
        return medium.translucentSkinCoverage() ? 178 : 255;
    }

    private static float smooth(float value) {
        float clamped = Mth.clamp(value, 0.0F, 1.0F);
        return clamped * clamped * (3.0F - clamped * 2.0F);
    }

    private static float unitNoise(long value) {
        return (mix(value) & 0xFFFFL) / 65535.0F;
    }

    private static long mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        return value ^ value >>> 33;
    }
}

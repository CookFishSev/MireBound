package com.fish.mirebound.mud;

import com.fish.mirebound.adaptive.MudVisualSource;
import static com.fish.mirebound.mud.MudContactRules.qualifiesSableFeetContact;
import static com.fish.mirebound.mud.MudContactRules.qualifiesSculkSurfaceContact;
import static com.fish.mirebound.mud.MudContactRules.qualifiesZeroDepthSurfaceContact;
import static com.fish.mirebound.mud.MudContactRules.qualifiesWorldContact;
import static com.fish.mirebound.mud.MudContactRules.qualifiesWorldVerticalContact;

import com.fish.mirebound.compat.sable.SableCompat;
import com.fish.mirebound.compat.sable.SableCompat.SinkingSample;
import com.fish.mirebound.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Resolves one immutable ordinary-world or Sable contact frame per player pass. */
final class MudContactResolver {
    static final double CONTACT_ENTER_SURFACE_GRACE = 0.030D;

    private MudContactResolver() {
    }

    static MudContact contactFromBlock(
            Level level, BlockPos pos, BlockState state,
            Player player, SinkingMedium medium) {
        if (!MudMediumRuntime.enabled(level, pos, medium)
                || !MudBlock.supportsVerticalSinking(state, medium)) {
            return null;
        }
        BlockPos topPos = MudColumnResolver.findTop(level, pos);
        BlockState topState = level.getBlockState(topPos);
        SinkingMedium topMedium = ModBlocks.mediumOf(topState.getBlock());
        if (topMedium != null) {
            state = topState;
            medium = topMedium;
        }

        BlockPos bottomPos = MudColumnResolver.findBottom(level, topPos);
        double localSurfaceHeight = MudMediumRuntime.surfaceHeightOver(
                level, topPos, topState, medium,
                player.getBoundingBox().deflate(0.020D, 0.0D, 0.020D));
        if (!Double.isFinite(localSurfaceHeight)) {
            return null;
        }
        double surfaceY = topPos.getY() + localSurfaceHeight;
        double availableDepth = MudColumnResolver.availableDepth(surfaceY, bottomPos);
        double depth = depthFromSurface(player, surfaceY);
        double depthFactor = verticalImmersion(player, depth);
        double horizontalCoverage = horizontalCoverage(
                depthFactor,
                MudVolumeContactResolver.worldBodyImmersion(
                        level, player.getBoundingBox()));
        PhysicsLayer activeLayer = activeWorldPhysicsLayer(
                level, player, topPos, bottomPos, state, medium);
        LayerDepth layerDepth = worldLayerDepth(
                surfaceY, availableDepth, activeLayer.pos());
        return new MudContact(
                state, medium, activeLayer.medium(), activeLayer.pos(), topPos,
                surfaceY, depth, depthFactor, horizontalCoverage, availableDepth,
                layerDepth.topDepth(), layerDepth.depth(), layerDepth.hasDeeperLayer(), null,
                new Vec3(player.getX(), surfaceY, player.getZ()),
                new Vec3(0.0D, 1.0D, 0.0D),
                new Vec3(1.0D, 0.0D, 0.0D),
                new Vec3(0.0D, 0.0D, 1.0D),
                Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, true);
    }

    static MudContact findPlayerContact(Player player) {
        return findPlayerContact(player, true);
    }

    static MudContact findPlayerContact(Player player, boolean includeSable) {
        Level level = player.level();
        AABB box = player.getBoundingBox().deflate(0.020D, 0.0D, 0.020D);
        Vec3 feet = player.position();
        int minX = Mth.floor(box.minX);
        int minY = Mth.floor(box.minY - 0.12D);
        int minZ = Mth.floor(box.minZ);
        int maxX = Mth.floor(box.maxX);
        int maxY = Mth.floor(box.maxY);
        int maxZ = Mth.floor(box.maxZ);

        MudContact best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        double bodyImmersion = Double.NaN;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    double horizontalOverlap = horizontalOverlapArea(box, x, z);
                    if (!qualifiesWorldContact(horizontalOverlap)) {
                        continue;
                    }

                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    SinkingMedium medium = ModBlocks.mediumOf(state.getBlock());
                    if (medium == null || !MudMediumRuntime.enabled(level, pos, medium)
                            || !MudBlock.supportsVerticalSinking(state, medium)) {
                        continue;
                    }

                    BlockPos topPos = MudColumnResolver.findTop(level, pos);
                    BlockState topState = level.getBlockState(topPos);
                    SinkingMedium topMedium = ModBlocks.mediumOf(topState.getBlock());
                    if (topMedium != null) {
                        medium = topMedium;
                        state = topState;
                    }

                    BlockPos bottomPos = MudColumnResolver.findBottom(level, topPos);
                    double localSurfaceHeight = MudMediumRuntime.surfaceHeightOver(
                            level, topPos, topState, medium, box);
                    if (!Double.isFinite(localSurfaceHeight)) {
                        continue;
                    }
                    double surfaceY = topPos.getY() + localSurfaceHeight;
                    double feetDepth = surfaceY - feet.y;
                    PhysicsLayer activeLayer = activeWorldPhysicsLayer(
                            level, player, topPos, bottomPos, state, medium);
                    SinkingPhysicsProfile profile = MudMediumRuntime.ordinaryProfile(
                            level, activeLayer.pos(), activeLayer.medium(),
                            MudPhysicsProfiles.ordinary(player, activeLayer.medium()));
                    boolean zeroDepth = SinkingPhysicsSolver.configuredDepth(profile)
                            <= 1.0E-6D;
                    boolean immersedContact = zeroDepth
                            ? qualifiesZeroDepthSurfaceContact(
                                    feetDepth, CONTACT_ENTER_SURFACE_GRACE)
                            : qualifiesWorldVerticalContact(feet.y, surfaceY);
                    boolean sculkSurfaceContact = MudBehaviorContext.sculk(
                            level, topPos, medium)
                            && qualifiesSculkSurfaceContact(
                                    feetDepth, player.isShiftKeyDown());
                    if ((!immersedContact && !sculkSurfaceContact)
                            || box.maxY < bottomPos.getY() + 0.025D) {
                        continue;
                    }

                    double availableDepth =
                            MudColumnResolver.availableDepth(surfaceY, bottomPos);
                    double depth = depthFromSurface(player, surfaceY);
                    double depthFactor = verticalImmersion(player, depth);
                    if (Double.isNaN(bodyImmersion)) {
                        bodyImmersion = MudVolumeContactResolver.worldBodyImmersion(
                                level, player.getBoundingBox());
                    }
                    double horizontalCoverage = horizontalCoverage(
                            depthFactor, bodyImmersion);
                    double score = depth + horizontalOverlap * 0.12D
                            + topPos.getY() * 0.001D
                            + medium.coverageScale() * 0.01D;
                    if (score > bestScore) {
                        bestScore = score;
                        LayerDepth layerDepth = worldLayerDepth(
                                surfaceY, availableDepth, activeLayer.pos());
                        best = new MudContact(
                                state, medium, activeLayer.medium(), activeLayer.pos(),
                                topPos, surfaceY, depth, depthFactor,
                                horizontalCoverage, availableDepth,
                                layerDepth.topDepth(), layerDepth.depth(),
                                layerDepth.hasDeeperLayer(), null,
                                new Vec3(player.getX(), surfaceY, player.getZ()),
                                new Vec3(0.0D, 1.0D, 0.0D),
                                new Vec3(1.0D, 0.0D, 0.0D),
                                new Vec3(0.0D, 0.0D, 1.0D),
                                Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                                Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                                immersedContact);
                    }
                }
            }
        }

        return best != null || !includeSable
                ? best : findSablePlayerContact(player, box);
    }

    static MudContact sableContactFromSample(
            Level level, Player player, SinkingSample sample, double surfaceGrace) {
        SableGravityColumn column = sableGravityColumn(level, sample);
        if (column == null) {
            return null;
        }
        double sampleDepth = column.depth(sample.localPoint());
        if (sampleDepth < -surfaceGrace
                || sampleDepth > column.availableDepth() + 0.035D) {
            return null;
        }
        Vec3 localFeet = SableCompat.toLocal(sample.subLevel(), player.position());
        if (localFeet == null) {
            return null;
        }
        double feetDepth = column.depth(localFeet);
        SableLayer activeLayer = column.layerAt(localFeet);
        SinkingMedium physicsMedium = activeLayer == null
                ? column.surfaceMedium() : activeLayer.medium();
        BlockPos physicsProfilePos = activeLayer == null
                ? column.surfacePos() : activeLayer.pos();
        SinkingPhysicsProfile profile = MudMediumRuntime.ordinaryProfile(
                level, physicsProfilePos, physicsMedium,
                MudPhysicsProfiles.ordinary(player, physicsMedium));
        boolean zeroDepth = SinkingPhysicsSolver.configuredDepth(profile)
                <= 1.0E-6D;
        boolean immersedContact = zeroDepth
                ? qualifiesZeroDepthSurfaceContact(feetDepth, surfaceGrace)
                : qualifiesSableFeetContact(feetDepth);
        boolean sculkSurfaceContact = MudBehaviorContext.sculk(
                level, column.surfacePos(), column.surfaceMedium())
                && qualifiesSculkSurfaceContact(feetDepth, player.isShiftKeyDown());
        if (!immersedContact && !sculkSurfaceContact) {
            return null;
        }
        Vec3 localSurface = clampSurfacePoint(
                level, column, column.surfacePoint(localFeet));
        Vec3 surfaceWorld = SableCompat.toWorld(sample.subLevel(), localSurface);
        if (surfaceWorld == null) {
            return null;
        }
        Vec3 surfaceNormal =
                SableCompat.toWorldDirection(sample.subLevel(), column.localUp());
        Vec3 surfaceAxisX =
                SableCompat.toWorldDirection(sample.subLevel(), column.localAxisX());
        Vec3 surfaceAxisZ =
                SableCompat.toWorldDirection(sample.subLevel(), column.localAxisZ());
        if (surfaceNormal == null || surfaceAxisX == null || surfaceAxisZ == null
                || surfaceNormal.lengthSqr() < 1.0E-8D
                || surfaceAxisX.lengthSqr() < 1.0E-8D
                || surfaceAxisZ.lengthSqr() < 1.0E-8D) {
            return null;
        }
        surfaceNormal = surfaceNormal.normalize();
        surfaceAxisX = surfaceAxisX.normalize();
        surfaceAxisZ = surfaceAxisZ.normalize();
        double availableDepth = Math.max(1.0D / 16.0D, column.availableDepth());
        double depth = Mth.clamp(
                feetDepth, 0.0D,
                Math.max(2.8D, player.getBbHeight() + 0.8D));
        LayerDepth layerDepth = activeLayer == null
                ? new LayerDepth(0.0D, availableDepth, false)
                : layerDepth(
                        column.surfaceCoordinate(), availableDepth,
                        activeLayer.topCoordinate(), activeLayer.bottomCoordinate());
        SurfaceClip clip = surfaceClip(level, column, localSurface);
        BodyImmersion immersion = sableBodyImmersion(
                player, sample.subLevel(), column, clip);
        return new MudContact(
                column.surfaceState(), column.surfaceMedium(), physicsMedium,
                physicsProfilePos, column.surfacePos(), surfaceWorld.y, depth,
                immersion.depthShare(), immersion.horizontalShare(), availableDepth,
                layerDepth.topDepth(), layerDepth.depth(), layerDepth.hasDeeperLayer(),
                new SableCoverageContext(
                        sample.subLevel(), column.surfaceMedium(), surfaceWorld.y,
                        availableDepth, column.localUp(), column.surfacePos(),
                        column.layers()),
                surfaceWorld, surfaceNormal, surfaceAxisX, surfaceAxisZ,
                clip.negativeX(), clip.positiveX(),
                clip.negativeZ(), clip.positiveZ(), immersedContact);
    }

    private static MudContact findSablePlayerContact(Player player, AABB box) {
        if (!SableCompat.isLoaded()) {
            return null;
        }
        Level level = player.level();
        AABB probeBounds = box.inflate(0.06D, 0.025D, 0.06D)
                .expandTowards(0.0D, -0.12D, 0.0D);
        SableCompat.SinkingVolumeProbe probe =
                SableCompat.sinkingVolumeProbe(level, probeBounds, player);
        double halfWidth = Math.max(0.05D, player.getBbWidth() * 0.5D - 0.035D);
        double[] xOffsets = {0.0D, -halfWidth * 0.72D, halfWidth * 0.72D};
        double[] zOffsets = {0.0D, -halfWidth * 0.72D, halfWidth * 0.72D};
        double height = Math.max(player.getBbHeight(), 0.1D);
        Vec3 feet = player.position();
        double eyeOffset = player.getEyePosition().y - feet.y;
        double[] yOffsets = {
                -0.065D, 0.055D,
                Math.min(height - 0.03D, height * 0.25D),
                Math.min(height - 0.03D, height * 0.50D),
                Math.min(height - 0.03D, height * 0.75D),
                Math.min(height - 0.03D, eyeOffset)
        };

        MudContact best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        int mudPointHits = 0;
        for (double yOffset : yOffsets) {
            double y = feet.y + Mth.clamp(yOffset, -0.09D, height - 0.02D);
            for (double xOffset : xOffsets) {
                for (double zOffset : zOffsets) {
                    SinkingSample sample = probe.sample(new Vec3(
                            feet.x + xOffset, y, feet.z + zOffset));
                    if (sample == null) {
                        continue;
                    }
                    mudPointHits++;
                    MudContact contact = sableContactFromSample(
                            level, player, sample, CONTACT_ENTER_SURFACE_GRACE);
                    if (contact == null) {
                        continue;
                    }
                    double upwardAlignment = contact.surfaceNormal()
                            .dot(new Vec3(0.0D, 1.0D, 0.0D));
                    double score = contact.depth() + upwardAlignment * 0.08D
                            + yOffset * 0.002D
                            + contact.medium().coverageScale() * 0.01D;
                    if (score > bestScore) {
                        bestScore = score;
                        best = contact;
                    }
                }
            }
        }
        if (player instanceof ServerPlayer serverPlayer
                && PhysicsTraceLog.enabled(serverPlayer)
                && Math.floorMod(player.tickCount - player.getId(), 10) == 0) {
            PhysicsTraceLog.traceSableProbe(
                    serverPlayer, probe.candidateCount(),
                    yOffsets.length * xOffsets.length * zOffsets.length,
                    mudPointHits, best == null ? null : best.medium(),
                    best == null ? 0.0D : best.depth());
        }
        return best;
    }

    static double depthFromSurface(Player player, double surfaceY) {
        return Mth.clamp(
                surfaceY - player.position().y, 0.0D,
                Math.max(2.8D, player.getBbHeight() + 0.8D));
    }

    private static PhysicsLayer activeWorldPhysicsLayer(
            Level level, Player player, BlockPos topPos, BlockPos bottomPos,
            BlockState topState, SinkingMedium topMedium) {
        int feetY = Mth.clamp(
                Mth.floor(player.position().y + 0.018D),
                bottomPos.getY(), topPos.getY());
        BlockPos activePos = new BlockPos(topPos.getX(), feetY, topPos.getZ());
        BlockState activeState = level.getBlockState(activePos);
        SinkingMedium activeMedium = ModBlocks.mediumOf(activeState.getBlock());
        return activeMedium == null
                ? new PhysicsLayer(topState, topMedium, topPos)
                : new PhysicsLayer(activeState, activeMedium, activePos);
    }

    private static LayerDepth worldLayerDepth(
            double surfaceY, double availableDepth, BlockPos activePos) {
        double layerTop = Math.min(surfaceY, activePos.getY() + 1.0D);
        return layerDepth(surfaceY, availableDepth, layerTop, activePos.getY());
    }

    static LayerDepth layerDepth(
            double surfaceCoordinate, double availableDepth,
            double layerTopCoordinate, double layerBottomCoordinate) {
        double topDepth = Math.max(0.0D, surfaceCoordinate - layerTopCoordinate);
        double depth = Math.max(
                1.0D / 16.0D, layerTopCoordinate - layerBottomCoordinate);
        boolean hasDeeperLayer = topDepth + depth
                < availableDepth - 1.0D / 64.0D;
        return new LayerDepth(topDepth, depth, hasDeeperLayer);
    }

    private static BodyImmersion sableBodyImmersion(
            Player player, Object subLevel,
            SableGravityColumn column, SurfaceClip clip) {
        AABB box = player.getBoundingBox();
        double minimumX = Double.POSITIVE_INFINITY;
        double maximumX = Double.NEGATIVE_INFINITY;
        double minimumZ = Double.POSITIVE_INFINITY;
        double maximumZ = Double.NEGATIVE_INFINITY;
        double minimumDepth = Double.POSITIVE_INFINITY;
        double maximumDepth = Double.NEGATIVE_INFINITY;
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 2; y++) {
                for (int z = 0; z < 2; z++) {
                    Vec3 world = new Vec3(
                            x == 0 ? box.minX : box.maxX,
                            y == 0 ? box.minY : box.maxY,
                            z == 0 ? box.minZ : box.maxZ);
                    Vec3 local = SableCompat.toLocal(subLevel, world);
                    if (local == null) {
                        return BodyImmersion.NONE;
                    }
                    double xCoordinate = local.dot(column.localAxisX());
                    double zCoordinate = local.dot(column.localAxisZ());
                    double depthCoordinate = local.dot(column.localUp());
                    minimumX = Math.min(minimumX, xCoordinate);
                    maximumX = Math.max(maximumX, xCoordinate);
                    minimumZ = Math.min(minimumZ, zCoordinate);
                    maximumZ = Math.max(maximumZ, zCoordinate);
                    minimumDepth = Math.min(minimumDepth, depthCoordinate);
                    maximumDepth = Math.max(maximumDepth, depthCoordinate);
                }
            }
        }
        double bodyX = Math.max(0.01D, maximumX - minimumX);
        double bodyZ = Math.max(0.01D, maximumZ - minimumZ);
        double bodyDepth = Math.max(0.01D, maximumDepth - minimumDepth);
        double mudDepthMinimum =
                column.surfaceCoordinate() - column.availableDepth();
        double depthOverlap = Math.max(0.0D,
                Math.min(maximumDepth, column.surfaceCoordinate())
                        - Math.max(minimumDepth, mudDepthMinimum));
        double depthShare = Mth.clamp(depthOverlap / bodyDepth, 0.0D, 1.0D);
        double tangentShareX = Mth.clamp(
                (clip.negativeX() + clip.positiveX()) / bodyX, 0.0D, 1.0D);
        double tangentShareZ = Mth.clamp(
                (clip.negativeZ() + clip.positiveZ()) / bodyZ, 0.0D, 1.0D);
        return new BodyImmersion(
                depthShare,
                Mth.clamp(tangentShareX * tangentShareZ, 0.0D, 1.0D));
    }

    private static double verticalImmersion(Player player, double depth) {
        return Mth.clamp(
                depth / Math.max(player.getBbHeight(), 0.1D), 0.0D, 1.0D);
    }

    private static double horizontalCoverage(
            double verticalImmersion, double bodyImmersion) {
        if (verticalImmersion <= 1.0E-6D) {
            return bodyImmersion > 0.0D ? 1.0D : 0.0D;
        }
        return Mth.clamp(bodyImmersion / verticalImmersion, 0.0D, 1.0D);
    }

    private static SurfaceClip surfaceClip(
            Level level, SableGravityColumn column, Vec3 localSurface) {
        AABB bounds = MudBlock.localBounds(
                level, column.surfacePos(),
                column.surfaceState(), column.surfaceMedium())
                .move(column.surfacePos());
        Vec3 minimum = new Vec3(bounds.minX, bounds.minY, bounds.minZ);
        Vec3 maximum = new Vec3(bounds.maxX, bounds.maxY, bounds.maxZ);
        double coordinateX = localSurface.dot(column.localAxisX());
        double coordinateZ = localSurface.dot(column.localAxisZ());
        double minimumX = minimumProjection(minimum, maximum, column.localAxisX());
        double maximumX = maximumProjection(minimum, maximum, column.localAxisX());
        double minimumZ = minimumProjection(minimum, maximum, column.localAxisZ());
        double maximumZ = maximumProjection(minimum, maximum, column.localAxisZ());
        return new SurfaceClip(
                Math.max(0.0D, coordinateX - minimumX),
                Math.max(0.0D, maximumX - coordinateX),
                Math.max(0.0D, coordinateZ - minimumZ),
                Math.max(0.0D, maximumZ - coordinateZ));
    }

    private static Vec3 clampSurfacePoint(
            Level level, SableGravityColumn column, Vec3 point) {
        AABB bounds = MudBlock.localBounds(
                level, column.surfacePos(),
                column.surfaceState(), column.surfaceMedium())
                .move(column.surfacePos());
        return switch (column.localDown().getAxis()) {
            case X -> new Vec3(point.x,
                    Mth.clamp(point.y, bounds.minY, bounds.maxY),
                    Mth.clamp(point.z, bounds.minZ, bounds.maxZ));
            case Y -> new Vec3(
                    Mth.clamp(point.x, bounds.minX, bounds.maxX), point.y,
                    Mth.clamp(point.z, bounds.minZ, bounds.maxZ));
            case Z -> new Vec3(
                    Mth.clamp(point.x, bounds.minX, bounds.maxX),
                    Mth.clamp(point.y, bounds.minY, bounds.maxY), point.z);
        };
    }

    private static double minimumProjection(Vec3 min, Vec3 max, Vec3 axis) {
        return (axis.x >= 0.0D ? min.x : max.x) * axis.x
                + (axis.y >= 0.0D ? min.y : max.y) * axis.y
                + (axis.z >= 0.0D ? min.z : max.z) * axis.z;
    }

    private static double maximumProjection(Vec3 min, Vec3 max, Vec3 axis) {
        return (axis.x >= 0.0D ? max.x : min.x) * axis.x
                + (axis.y >= 0.0D ? max.y : min.y) * axis.y
                + (axis.z >= 0.0D ? max.z : min.z) * axis.z;
    }

    private static SableGravityColumn sableGravityColumn(
            Level level, SinkingSample sample) {
        Vec3 localDownVector = SableCompat.toLocalDirection(
                sample.subLevel(), new Vec3(0.0D, -1.0D, 0.0D));
        if (localDownVector == null || localDownVector.lengthSqr() < 1.0E-8D) {
            return null;
        }
        Direction localDown = dominantDirection(localDownVector);
        Direction localUpDirection = localDown.getOpposite();
        if (MudBlock.heightPixels(sample.state(), sample.medium()) < 16
                && MudBlock.surfaceDirection(sample.state(), sample.medium())
                        != localUpDirection) {
            return null;
        }
        Vec3 localUp = directionVector(localUpDirection);
        Vec3 localAxisX = tangentAxis(localUpDirection);
        Vec3 localAxisZ = localUp.cross(localAxisX).normalize();

        BlockPos surfacePos = sample.pos();
        for (int guard = 0; guard < 16; guard++) {
            BlockPos next = surfacePos.relative(localUpDirection);
            if (ModBlocks.mediumOf(SableCompat.subLevelBlockState(
                    level, sample.subLevel(), next).getBlock()) == null) {
                break;
            }
            surfacePos = next.immutable();
        }
        BlockPos bottomPos = sample.pos();
        for (int guard = 0; guard < 16; guard++) {
            BlockPos next = bottomPos.relative(localDown);
            if (ModBlocks.mediumOf(SableCompat.subLevelBlockState(
                    level, sample.subLevel(), next).getBlock()) == null) {
                break;
            }
            bottomPos = next.immutable();
        }
        BlockState surfaceState = SableCompat.subLevelBlockState(
                level, sample.subLevel(), surfacePos);
        SinkingMedium surfaceMedium = ModBlocks.mediumOf(surfaceState.getBlock());
        if (surfaceMedium == null) {
            return null;
        }
        double surfaceCoordinate = materialFaceCoordinate(
                level, surfacePos, surfaceState, surfaceMedium,
                localUpDirection, localUp);
        BlockState bottomState = SableCompat.subLevelBlockState(
                level, sample.subLevel(), bottomPos);
        SinkingMedium bottomMedium = ModBlocks.mediumOf(bottomState.getBlock());
        double bottomCoordinate = materialFaceCoordinate(
                level, bottomPos, bottomState,
                bottomMedium == null ? surfaceMedium : bottomMedium,
                localDown, localUp);
        double availableDepth = Math.max(
                0.0D, surfaceCoordinate - bottomCoordinate);

        List<SableLayer> layers = new ArrayList<>();
        BlockPos cursor = surfacePos;
        double layerTop = surfaceCoordinate;
        for (int guard = 0; guard < 16; guard++) {
            BlockState state = SableCompat.subLevelBlockState(
                    level, sample.subLevel(), cursor);
            SinkingMedium medium = ModBlocks.mediumOf(state.getBlock());
            if (medium == null) {
                break;
            }
            double layerBottom = materialFaceCoordinate(
                    level, cursor, state, medium, localDown, localUp);
            var localShape = MudBlock.localShape(level, cursor, state, medium);
            layers.add(new SableLayer(
                    medium, cursor.immutable(), state,
                    MudVisualSource.capture(level, cursor),
                    localShape.isEmpty() ? 0.0D : localShape.bounds().maxY,
                    layerTop, layerBottom, localShape));
            if (cursor.equals(bottomPos)) {
                break;
            }
            cursor = cursor.relative(localDown);
            BlockState nextState = SableCompat.subLevelBlockState(
                    level, sample.subLevel(), cursor);
            SinkingMedium nextMedium = ModBlocks.mediumOf(nextState.getBlock());
            layerTop = materialFaceCoordinate(
                    level, cursor, nextState,
                    nextMedium == null ? medium : nextMedium,
                    localUpDirection, localUp);
        }
        return new SableGravityColumn(
                sample.subLevel(), localDown, localUp, localAxisX, localAxisZ,
                surfacePos, surfaceState, surfaceMedium, surfaceCoordinate,
                availableDepth, layers.toArray(SableLayer[]::new));
    }

    private static Direction dominantDirection(Vec3 vector) {
        double x = Math.abs(vector.x);
        double y = Math.abs(vector.y);
        double z = Math.abs(vector.z);
        if (x >= y && x >= z) {
            return vector.x >= 0.0D ? Direction.EAST : Direction.WEST;
        }
        if (y >= z) {
            return vector.y >= 0.0D ? Direction.UP : Direction.DOWN;
        }
        return vector.z >= 0.0D ? Direction.SOUTH : Direction.NORTH;
    }

    private static Vec3 directionVector(Direction direction) {
        return new Vec3(
                direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }

    private static Vec3 tangentAxis(Direction localUp) {
        return localUp.getAxis() == Direction.Axis.X
                ? new Vec3(0.0D, 0.0D, 1.0D)
                : new Vec3(1.0D, 0.0D, 0.0D);
    }

    private static double materialFaceCoordinate(
            Level level, BlockPos pos, BlockState state, SinkingMedium medium,
            Direction face, Vec3 axis) {
        AABB bounds = MudBlock.localBounds(
                level, pos, state, medium).move(pos);
        double x = face == Direction.EAST ? bounds.maxX : bounds.minX;
        double y = face == Direction.UP ? bounds.maxY : bounds.minY;
        double z = face == Direction.SOUTH ? bounds.maxZ : bounds.minZ;
        return x * axis.x + y * axis.y + z * axis.z;
    }

    private static double horizontalOverlapArea(
            AABB box, int blockX, int blockZ) {
        double overlapX = Math.min(box.maxX, blockX + 1.0D)
                - Math.max(box.minX, blockX);
        double overlapZ = Math.min(box.maxZ, blockZ + 1.0D)
                - Math.max(box.minZ, blockZ);
        return overlapX <= 0.0D || overlapZ <= 0.0D
                ? 0.0D : overlapX * overlapZ;
    }

    private record BodyImmersion(double depthShare, double horizontalShare) {
        private static final BodyImmersion NONE = new BodyImmersion(0.0D, 0.0D);
    }
}

package com.fish.mirebound.client;

import com.fish.mirebound.adaptive.AdaptiveMudBlock;
import com.fish.mirebound.compat.sable.SableCompat;
import com.fish.mirebound.mud.MudEntityGeometry;
import com.fish.mirebound.mud.MudBlock;
import com.fish.mirebound.mud.MudMediumRuntime;
import com.fish.mirebound.mud.MudPhysicsParameter;
import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.registry.ModBlocks;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Directional deformation on ordinary and physicalized mud faces.
 */
final class MudSideSurfaceEffectManager {
    private static final double PIXEL = 1.0D / 16.0D;
    private static final Direction[] DEFORMABLE_FACES = {
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST,
            Direction.DOWN
    };
    private static final Map<SideKey, SideImprint> IMPRINTS = new HashMap<>();
    private static final Map<Long, List<AABB>> OCCUPANCY_BOXES = new HashMap<>();
    private static final Int2ObjectOpenHashMap<EruptionFaceGroup> ERUPTION_GROUPS =
            new Int2ObjectOpenHashMap<>();
    private static ClientLevel level;
    private static int retainedCells;

    private MudSideSurfaceEffectManager() {
    }

    static void tick(ClientLevel clientLevel) {
        if (clientLevel == null || !MudSurfaceClientSettings.enabled()
                || MudSurfaceClientSettings.maxSideImprints() <= 0
                || MudSurfaceClientSettings.maxSideCells() <= 0) {
            reset();
            return;
        }
        if (level != clientLevel) {
            reset();
            level = clientLevel;
        }
        for (SideImprint imprint : IMPRINTS.values()) {
            imprint.beginTick();
        }
        OCCUPANCY_BOXES.clear();
        for (Player player : clientLevel.players()) {
            if (ClientPollutionVisibility.isSuppressed(player)
                    || ClientAssimilationState.isFrozen(player.getId())
                    || ClientPollutionVisibility.isContactSamplingSuppressed(player)) {
                // A detached view cannot refresh contact, but existing side and
                // underside deformation remains world-owned and closes normally.
                continue;
            }
            scanPlayer(player);
            scanSablePlayer(player);
        }
        finishTick();
    }

    static Iterable<SideImprint> imprints() {
        return IMPRINTS.values();
    }

    static boolean openEruptionVent(ClientLevel clientLevel, int ventId,
            Object subLevel, BlockPos pos, Direction face, Vec3 localOrigin,
            SinkingMedium medium, double radiusPixels, long seed, long visualSource) {
        if (clientLevel == null || ventId <= 0 || pos == null || face == null
                || localOrigin == null || medium == null) {
            return false;
        }
        if (level != clientLevel) {
            reset();
            level = clientLevel;
        }
        double plane = switch (face.getAxis()) {
            case X -> localOrigin.x;
            case Y -> localOrigin.y;
            case Z -> localOrigin.z;
        };
        int eruptionKey = eruptionKey(ventId);
        EruptionFaceGroup group = eruptionGroup(
                eruptionKey, subLevel, face, plane);
        boolean presented = false;
        int searchRadius = eruptionBlockSearchRadius(radiusPixels);
        for (int ring = 0; ring <= searchRadius; ring++) {
            for (int offsetU = -ring; offsetU <= ring; offsetU++) {
                for (int offsetV = -ring; offsetV <= ring; offsetV++) {
                    if (Math.max(Math.abs(offsetU), Math.abs(offsetV)) != ring) {
                        continue;
                    }
                    BlockPos candidate = eruptionNeighborPos(
                            pos, face, offsetU, offsetV);
                    presented |= openEruptionFace(
                            clientLevel, eruptionKey, group, subLevel,
                            candidate, face, plane, localOrigin,
                            radiusPixels, seed, visualSource);
                }
            }
        }
        if (presented) {
            group.stamp(localOrigin, radiusPixels);
        }
        return presented;
    }

    static void closeEruptionVent(int ventId) {
        int key = eruptionKey(ventId);
        for (SideImprint imprint : IMPRINTS.values()) {
            if (imprint.key.entityId() == key) {
                imprint.persistent = false;
                imprint.seenThisTick = false;
            }
        }
    }

    static void forgetEruptionVent(int ventId) {
        int key = eruptionKey(ventId);
        ERUPTION_GROUPS.remove(key);
        removeEruptionImprints(key);
    }

    static void reset() {
        IMPRINTS.clear();
        ERUPTION_GROUPS.clear();
        OCCUPANCY_BOXES.clear();
        MudSurfaceShapeGeometry.reset();
        MudRenderedSurfaceGeometry.reset();
        retainedCells = 0;
        level = null;
    }

    private static boolean openEruptionFace(ClientLevel clientLevel,
            int eruptionKey, EruptionFaceGroup group, Object subLevel,
            BlockPos pos, Direction face, double plane, Vec3 localOrigin,
            double radiusPixels, long seed, long fallbackVisualSource) {
        BlockState state = blockState(clientLevel, subLevel, pos);
        SinkingMedium medium = ModBlocks.mediumOf(state.getBlock());
        if (medium == null) {
            return false;
        }
        boolean presented = false;
        List<AABB> shapeBoxes = MudBlock.localShape(
                clientLevel, pos, state, medium).toAabbs();
        for (AABB localBox : shapeBoxes) {
            AABB faceBox = localBox.move(pos);
            if (Math.abs(facePlane(faceBox, face) - plane) > 0.002D) {
                continue;
            }
            MudSurfaceShapeGeometry.FaceMask localMask =
                    MudSurfaceShapeGeometry.faceMask(shapeBoxes, localBox, face);
            if (!localMask.any()) {
                continue;
            }
            FaceBasis localBasis = faceBasis(pos, face, plane);
            ProjectionRange range = projectionRange(localBasis, faceBox);
            if (range.maximumU() - range.minimumU() <= 0.002D
                    || range.maximumV() - range.minimumV() <= 0.002D
                    || !eruptionIntersects(
                            localBasis, range, localOrigin, radiusPixels)) {
                continue;
            }
            FaceBasis worldBasis = subLevel == null
                    ? localBasis : transformBasis(subLevel, localBasis);
            if (worldBasis == null) {
                continue;
            }
            boolean[] exposedCells = subLevel == null
                    ? worldExposedCells(localBasis, localMask.cells())
                    : localMask.cells();
            if (!any(exposedCells)
                    || subLevel != null && !eruptionFaceExposed(
                            clientLevel, subLevel, pos, state, medium, faceBox, face)) {
                continue;
            }
            SideKey key = new SideKey(eruptionKey, subLevel,
                    pos.asLong(), face, localMask.geometryKey());
            SideImprint imprint = IMPRINTS.get(key);
            if (imprint == null) {
                if (IMPRINTS.size() >= MudSurfaceClientSettings.maxSideImprints()) {
                    pruneClosedImprints();
                }
                if (IMPRINTS.size() >= MudSurfaceClientSettings.maxSideImprints()) {
                    continue;
                }
                imprint = new SideImprint(
                        key, subLevel, pos, face, localBasis,
                        worldBasis, medium, seed);
                IMPRINTS.put(key, imprint);
            }
            imprint.minimumU = range.minimumU();
            imprint.maximumU = range.maximumU();
            imprint.minimumV = range.minimumV();
            imprint.maximumV = range.maximumV();
            updateExposedCells(imprint, exposedCells);
            imprint.medium = medium;
            long localVisualSource = MudSurfaceAppearance.captureVisualSource(
                    clientLevel, pos, face);
            imprint.visualSource = localVisualSource == 0L
                    ? fallbackVisualSource : localVisualSource;
            imprint.persistent = true;
            imprint.seenThisTick = true;
            imprint.eruptionGroup = group;
            group.add(imprint);
            presented = true;
        }
        return presented;
    }

    private static EruptionFaceGroup eruptionGroup(
            int eruptionKey, Object subLevel, Direction face, double plane) {
        EruptionFaceGroup current = ERUPTION_GROUPS.get(eruptionKey);
        if (current != null && current.matches(subLevel, face, plane)) {
            return current;
        }
        if (current != null) {
            removeEruptionImprints(eruptionKey);
        }
        EruptionFaceGroup created = new EruptionFaceGroup(
                subLevel, face, plane);
        ERUPTION_GROUPS.put(eruptionKey, created);
        return created;
    }

    private static void removeEruptionImprints(int eruptionKey) {
        Iterator<SideImprint> iterator = IMPRINTS.values().iterator();
        while (iterator.hasNext()) {
            SideImprint imprint = iterator.next();
            if (imprint.key.entityId() != eruptionKey) {
                continue;
            }
            retainedCells -= imprint.cells.size();
            iterator.remove();
        }
        retainedCells = Math.max(0, retainedCells);
    }

    private static BlockState blockState(
            ClientLevel clientLevel, Object subLevel, BlockPos pos) {
        return subLevel == null
                ? clientLevel.getBlockState(pos)
                : SableCompat.subLevelBlockState(clientLevel, subLevel, pos);
    }

    private static boolean eruptionFaceExposed(ClientLevel clientLevel,
            Object subLevel, BlockPos pos, BlockState state,
            SinkingMedium medium, AABB faceBox, Direction face) {
        Vec3 outside = faceCenter(faceBox, face).add(
                face.getStepX() * 0.003D,
                face.getStepY() * 0.003D,
                face.getStepZ() * 0.003D);
        BlockPos outsidePos = BlockPos.containing(outside);
        if (outsidePos.equals(pos)) {
            return !MudBlock.containsLocalPoint(
                    clientLevel, pos, state, medium,
                    outside.subtract(pos.getX(), pos.getY(), pos.getZ()),
                    0.0005D);
        }
        BlockState outsideState = blockState(clientLevel, subLevel, outsidePos);
        return outsideState.isAir()
                || outsideState.getBlock() == ModBlocks.MUD_FOOTPRINT.get();
    }

    private static boolean eruptionIntersects(FaceBasis basis,
            ProjectionRange range, Vec3 localOrigin, double radiusPixels) {
        Vec3 relative = localOrigin.subtract(basis.origin);
        double centerU = relative.dot(basis.axisU) / PIXEL;
        double centerV = relative.dot(basis.axisV) / PIXEL;
        double minimumU = range.minimumU() / PIXEL;
        double maximumU = range.maximumU() / PIXEL;
        double minimumV = range.minimumV() / PIXEL;
        double maximumV = range.maximumV() / PIXEL;
        double nearestU = Mth.clamp(centerU, minimumU, maximumU);
        double nearestV = Mth.clamp(centerV, minimumV, maximumV);
        double deltaU = centerU - nearestU;
        double deltaV = centerV - nearestV;
        double haloRadius = Mth.clamp(radiusPixels, 1.0D, 18.0D) + 1.0D;
        return deltaU * deltaU + deltaV * deltaV <= haloRadius * haloRadius;
    }

    static int eruptionBlockSearchRadius(double radiusPixels) {
        double boundedRadius = Mth.clamp(radiusPixels, 1.0D, 18.0D) + 1.0D;
        return Mth.clamp(Mth.ceil(boundedRadius / 16.0D), 1, 2);
    }

    static BlockPos eruptionNeighborPos(
            BlockPos origin, Direction face, int offsetU, int offsetV) {
        return switch (face.getAxis()) {
            case X -> origin.offset(0, offsetU, offsetV);
            case Y -> origin.offset(offsetU, 0, offsetV);
            case Z -> origin.offset(offsetU, offsetV, 0);
        };
    }

    private static void scanPlayer(Player player) {
        AABB bounds = player.getBoundingBox().inflate(0.24D);
        BlockPos minimum = BlockPos.containing(
                bounds.minX, bounds.minY - 0.02D, bounds.minZ);
        BlockPos maximum = BlockPos.containing(
                bounds.maxX, bounds.maxY + 0.02D, bounds.maxZ);
        for (BlockPos cursor : BlockPos.betweenClosed(minimum, maximum)) {
            BlockState state = level.getBlockState(cursor);
            SinkingMedium medium = ModBlocks.mediumOf(state.getBlock());
            if (medium == null
                    || !MudMediumRuntime.enabled(level, cursor, medium)
                    || MudMediumRuntime.value(
                            level, cursor, medium,
                            MudPhysicsParameter.SURFACE_EFFECTS_ENABLED) < 0.5D) {
                continue;
            }
            double surfaceHeight = MudMediumRuntime.surfaceHeight(
                    level, cursor, state, medium);
            if (surfaceHeight <= 0.001D) {
                continue;
            }
            if (state.getBlock() instanceof AdaptiveMudBlock
                    && MudSurfaceClientSettings.preciseModelGeometry()) {
                List<MudRenderedSurfaceGeometry.RenderedFace> renderedFaces =
                        MudRenderedSurfaceGeometry.axisFaces(
                                level, cursor, state);
                if (!renderedFaces.isEmpty()) {
                    scanRenderedWorldMud(
                            player, cursor.immutable(), medium, renderedFaces);
                    continue;
                }
            }
            if (!MudBlock.supportsVerticalSinking(state, medium)) {
                scanShapedWorldMud(
                        player, cursor.immutable(), medium,
                        MudBlock.localShape(level, cursor, state, medium).toAabbs(),
                        false);
                continue;
            }
            if (state.getBlock() instanceof AdaptiveMudBlock) {
                List<AABB> shapeBoxes = MudBlock.localShape(
                        level, cursor, state, medium).toAabbs();
                if (!isFullCube(shapeBoxes)) {
                    scanShapedWorldMud(
                            player, cursor.immutable(), medium,
                            shapeBoxes, true);
                    continue;
                }
            }
            for (Direction face : DEFORMABLE_FACES) {
                FaceExposure exposure = faceExposure(
                        cursor, face, surfaceHeight);
                if (exposure.empty()) {
                    continue;
                }
                stampFace(player, cursor.immutable(), face, medium,
                        exposure.minimumV(), exposure.maximumV());
            }
        }
    }

    private static void scanShapedWorldMud(Player player, BlockPos pos,
            SinkingMedium medium, List<AABB> shapeBoxes,
            boolean omitTopFace) {
        for (AABB localBox : shapeBoxes) {
            AABB worldBox = localBox.move(pos);
            for (Direction face : Direction.values()) {
                if (omitTopFace && face == Direction.UP) {
                    continue;
                }
                MudSurfaceShapeGeometry.FaceMask localMask =
                        MudSurfaceShapeGeometry.faceMask(shapeBoxes, localBox, face);
                if (!localMask.any()) {
                    continue;
                }
                double plane = facePlane(worldBox, face);
                FaceBasis basis = faceBasis(pos, face, plane);
                boolean[] exposedCells = worldExposedCells(
                        basis, localMask.cells());
                if (!any(exposedCells)) {
                    continue;
                }
                ProjectionRange range = projectionRange(basis, worldBox);
                stampFace(
                        player,
                        null,
                        pos,
                        face,
                        medium,
                        basis,
                        basis,
                        range.minimumU(),
                        range.maximumU(),
                        range.minimumV(),
                        range.maximumV(),
                        localMask.geometryKey(),
                        exposedCells);
            }
        }
    }

    private static void scanRenderedWorldMud(Player player, BlockPos pos,
            SinkingMedium medium,
            List<MudRenderedSurfaceGeometry.RenderedFace> renderedFaces) {
        for (MudRenderedSurfaceGeometry.RenderedFace rendered : renderedFaces) {
            Direction face = rendered.face();
            if (face == Direction.UP) {
                continue;
            }
            double plane = worldPlane(pos, face, rendered.plane());
            FaceBasis basis = faceBasis(pos, face, plane);
            boolean[] exposedCells = worldExposedCells(
                    basis, rendered.cells());
            if (!any(exposedCells)) {
                continue;
            }
            stampFace(
                    player,
                    null,
                    pos,
                    face,
                    medium,
                    basis,
                    basis,
                    0.0D,
                    1.0D,
                    0.0D,
                    1.0D,
                    rendered.geometryKey(),
                    exposedCells);
        }
    }

    private static double worldPlane(
            BlockPos pos, Direction face, double localPlane) {
        return switch (face.getAxis()) {
            case X -> pos.getX() + localPlane;
            case Y -> pos.getY() + localPlane;
            case Z -> pos.getZ() + localPlane;
        };
    }

    private static boolean occupiedAt(Vec3 point) {
        BlockPos pos = BlockPos.containing(point);
        List<AABB> boxes = OCCUPANCY_BOXES.get(pos.asLong());
        if (boxes == null) {
            BlockState state = level.getBlockState(pos);
            SinkingMedium medium = ModBlocks.mediumOf(state.getBlock());
            var shape = medium == null
                    ? state.getCollisionShape(level, pos, CollisionContext.empty())
                    : MudBlock.localShape(level, pos, state, medium);
            if (shape.isEmpty()) {
                shape = state.getShape(level, pos, CollisionContext.empty());
            }
            boxes = shape.toAabbs();
            OCCUPANCY_BOXES.put(pos.asLong(), boxes);
        }
        Vec3 local = point.subtract(pos.getX(), pos.getY(), pos.getZ());
        for (AABB box : boxes) {
            if (local.x >= box.minX - 1.0E-6D && local.x <= box.maxX + 1.0E-6D
                    && local.y >= box.minY - 1.0E-6D && local.y <= box.maxY + 1.0E-6D
                    && local.z >= box.minZ - 1.0E-6D && local.z <= box.maxZ + 1.0E-6D) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFullCube(List<AABB> boxes) {
        if (boxes.size() != 1) {
            return false;
        }
        AABB box = boxes.getFirst();
        return box.minX <= 1.0E-7D && box.minY <= 1.0E-7D
                && box.minZ <= 1.0E-7D
                && box.maxX >= 1.0D - 1.0E-7D
                && box.maxY >= 1.0D - 1.0E-7D
                && box.maxZ >= 1.0D - 1.0E-7D;
    }

    private static double facePlane(AABB box, Direction face) {
        return switch (face) {
            case WEST -> box.minX;
            case EAST -> box.maxX;
            case DOWN -> box.minY;
            case UP -> box.maxY;
            case NORTH -> box.minZ;
            case SOUTH -> box.maxZ;
        };
    }

    private static Vec3 faceCenter(AABB box, Direction face) {
        return switch (face.getAxis()) {
            case X -> new Vec3(
                    facePlane(box, face),
                    (box.minY + box.maxY) * 0.5D,
                    (box.minZ + box.maxZ) * 0.5D);
            case Y -> new Vec3(
                    (box.minX + box.maxX) * 0.5D,
                    facePlane(box, face),
                    (box.minZ + box.maxZ) * 0.5D);
            case Z -> new Vec3(
                    (box.minX + box.maxX) * 0.5D,
                    (box.minY + box.maxY) * 0.5D,
                    facePlane(box, face));
        };
    }

    private static void scanSablePlayer(Player player) {
        if (!SableCompat.isLoaded()) {
            return;
        }
        AABB bounds = player.getBoundingBox().inflate(0.24D);
        for (SableCompat.MudSurfaceFace surface
                : SableCompat.mudSurfaceFaces(level, bounds, player)) {
            if (!MudMediumRuntime.enabled(level, surface.pos(), surface.medium())
                    || MudMediumRuntime.value(
                            level,
                            surface.pos(),
                            surface.medium(),
                            MudPhysicsParameter.SURFACE_EFFECTS_ENABLED) < 0.5D) {
                continue;
            }
            FaceBasis localBasis = faceBasis(
                    surface.pos(), surface.face(), surface.plane());
            FaceBasis worldBasis = transformBasis(
                    surface.subLevel(), localBasis);
            if (worldBasis == null) {
                continue;
            }
            ProjectionRange range = projectionRange(
                    localBasis, surface.localBox());
            stampFace(
                    player,
                    surface.subLevel(),
                    surface.pos(),
                    surface.face(),
                    surface.medium(),
                    localBasis,
                    worldBasis,
                    range.minimumU(),
                    range.maximumU(),
                    range.minimumV(),
                    range.maximumV(),
                    surfaceGeometryKey(surface.localBox()),
                    null);
        }
    }

    private static FaceExposure faceExposure(BlockPos pos, Direction face,
            double surfaceHeight) {
        BlockPos neighborPos = pos.relative(face);
        BlockState neighbor = level.getBlockState(neighborPos);
        SinkingMedium neighborMedium = ModBlocks.mediumOf(neighbor.getBlock());
        if (face == Direction.DOWN) {
            return neighborMedium != null
                    || neighbor.isSolidRender(level, neighborPos)
                    ? FaceExposure.EMPTY
                    : new FaceExposure(0.0D, 1.0D);
        }
        if (neighborMedium != null) {
            double neighborTop = neighborPos.getY()
                    + MudMediumRuntime.surfaceHeight(
                            level, neighborPos, neighbor, neighborMedium);
            double minimum = Mth.clamp(
                    neighborTop - pos.getY(), 0.0D, surfaceHeight);
            return new FaceExposure(minimum, surfaceHeight);
        }
        return neighbor.isSolidRender(level, neighborPos)
                ? FaceExposure.EMPTY
                : new FaceExposure(0.0D, surfaceHeight);
    }

    private static void stampFace(Player player, BlockPos pos, Direction face,
            SinkingMedium medium, double minimumV, double maximumV) {
        FaceBasis basis = faceBasis(pos, face);
        stampFace(
                player,
                null,
                pos,
                face,
                medium,
                basis,
                basis,
                0.0D,
                1.0D,
                minimumV,
                maximumV,
                0,
                null);
    }

    private static void stampFace(Player player, Object subLevel,
            BlockPos pos, Direction face, SinkingMedium medium,
            FaceBasis localBasis, FaceBasis basis,
            double minimumU, double maximumU,
            double minimumV, double maximumV, int geometryKey,
            boolean[] exposedCells) {
        MudEntityGeometry.OrientedPlaneSlice slice = MudEntityGeometry.planeSlice(
                player, basis.origin, basis.normal, basis.axisU, basis.axisV);
        if (slice.empty()) {
            return;
        }
        SideKey key = new SideKey(
                player.getId(), subLevel, pos.asLong(), face, geometryKey);
        SideImprint imprint = IMPRINTS.get(key);
        if (imprint == null) {
            if (IMPRINTS.size() >= MudSurfaceClientSettings.maxSideImprints()) {
                pruneClosedImprints();
            }
            if (IMPRINTS.size() >= MudSurfaceClientSettings.maxSideImprints()) {
                return;
            }
            imprint = new SideImprint(
                    key, subLevel, pos, face, localBasis, basis, medium,
                    MudSurfaceEffectManager.mix(
                            player.getUUID().getLeastSignificantBits()
                                    ^ pos.asLong()
                                    ^ face.ordinal() * 0x9e3779b97f4a7c15L));
            IMPRINTS.put(key, imprint);
        }
        imprint.visualSource = MudSurfaceAppearance.captureVisualSource(
                level, pos, face);
        imprint.seenThisTick = true;
        imprint.medium = medium;
        imprint.basis = basis;
        imprint.minimumU = minimumU;
        imprint.maximumU = maximumU;
        imprint.minimumV = minimumV;
        imprint.maximumV = maximumV;
        updateExposedCells(imprint, exposedCells);
        boolean[] direct = imprint.directCells;
        Arrays.fill(direct, false);
        for (MudEntityGeometry.SlicePolygon polygon : slice.polygons()) {
            rasterize(imprint, polygon.vertices(), direct);
        }
        addDownwardDrag(imprint, direct, player.getDeltaMovement());
    }

    private static void rasterize(SideImprint imprint, List<Vec3> polygon,
            boolean[] direct) {
        if (polygon.size() < 3) {
            return;
        }
        double minimumU = Double.POSITIVE_INFINITY;
        double maximumU = Double.NEGATIVE_INFINITY;
        double minimumV = Double.POSITIVE_INFINITY;
        double maximumV = Double.NEGATIVE_INFINITY;
        for (Vec3 point : polygon) {
            Vec3 relative = point.subtract(imprint.basis.origin);
            double u = relative.dot(imprint.basis.axisU);
            double v = relative.dot(imprint.basis.axisV);
            minimumU = Math.min(minimumU, u);
            maximumU = Math.max(maximumU, u);
            minimumV = Math.min(minimumV, v);
            maximumV = Math.max(maximumV, v);
        }
        int minimumCellU = Mth.clamp(Mth.floor(minimumU * 16.0D) - 1, 0, 15);
        int maximumCellU = Mth.clamp(Mth.floor(maximumU * 16.0D) + 1, 0, 15);
        int minimumCellV = Mth.clamp(Mth.floor(minimumV * 16.0D) - 1, 0, 15);
        int maximumCellV = Mth.clamp(Mth.floor(maximumV * 16.0D) + 1, 0, 15);
        for (int v = minimumCellV; v <= maximumCellV; v++) {
            double sampleV = (v + 0.5D) * PIXEL;
            if (sampleV < imprint.minimumV - 1.0E-5D
                    || sampleV > imprint.maximumV + 1.0E-5D) {
                continue;
            }
            for (int u = minimumCellU; u <= maximumCellU; u++) {
                double sampleU = (u + 0.5D) * PIXEL;
                if (sampleU < imprint.minimumU - 1.0E-5D
                        || sampleU > imprint.maximumU + 1.0E-5D) {
                    continue;
                }
                if (!MudEntityGeometry.containsPlane(
                        polygon,
                        imprint.basis.origin,
                        imprint.basis.axisU,
                        imprint.basis.axisV,
                        sampleU,
                        sampleV)) {
                    continue;
                }
                direct[cellIndex(u, v)] = true;
                refreshCell(imprint, u, v, 1.0D);
            }
        }
    }

    private static void addDownwardDrag(SideImprint imprint, boolean[] direct,
            Vec3 motion) {
        if (imprint.subLevel != null || imprint.face == Direction.DOWN) {
            return;
        }
        double tangentialSpeed = Math.abs(motion.dot(imprint.basis.axisU));
        double downwardSpeed = Math.max(0.0D, -motion.y);
        double trailScale = MudMediumRuntime.value(
                level, imprint.subLevel == null ? imprint.pos : null,
                imprint.medium, MudPhysicsParameter.SURFACE_MOVEMENT_TRAIL);
        int maximumDrag = Mth.clamp(
                1 + Mth.floor((tangentialSpeed * 7.0D + downwardSpeed * 10.0D)
                        * trailScale),
                1,
                3);
        for (int cell = 0; cell < direct.length; cell++) {
            if (!direct[cell]) {
                continue;
            }
            int u = cell & 15;
            int v = cell >>> 4;
            if (v > 0 && direct[cellIndex(u, v - 1)]) {
                continue;
            }
            long hash = MudSurfaceEffectManager.mix(
                    imprint.seed ^ cell * 0xc2b2ae3d27d4eb4fL);
            int length = 1 + (int) ((hash >>> 19) % maximumDrag);
            for (int offset = 1; offset <= length; offset++) {
                int targetV = v - offset;
                if (targetV < 0) {
                    break;
                }
                double sampleV = (targetV + 0.5D) * PIXEL;
                if (sampleV < imprint.minimumV - 1.0E-5D) {
                    break;
                }
                double strength = (0.62D - offset * 0.10D)
                        * (0.88D + ((hash >>> (25 + offset * 5)) & 15L) / 100.0D);
                refreshCell(imprint, u, targetV, strength);
            }
        }
    }

    private static void refreshCell(SideImprint imprint, int u, int v,
            double strength) {
        SideCell cell = ensureCell(imprint, u, v);
        if (cell == null) {
            return;
        }
        imprint.includeRefreshedCell(u, v);
        cell.refreshed = true;
        cell.depression = Math.max(cell.depression, strength);
        cell.closureProgress = 0.0D;
        cell.closureMask = 0;
        for (int dv = -1; dv <= 1; dv++) {
            for (int du = -1; du <= 1; du++) {
                if (du == 0 && dv == 0) {
                    continue;
                }
                ensureCellAt(imprint, u + du, v + dv);
            }
        }
    }

    private static void stampEruption(
            SideImprint imprint, Vec3 localOrigin, double radiusPixels) {
        Vec3 relative = localOrigin.subtract(imprint.localBasis.origin);
        double centerU = relative.dot(imprint.localBasis.axisU) / PIXEL;
        double centerV = relative.dot(imprint.localBasis.axisV) / PIXEL;
        double radius = Mth.clamp(radiusPixels, 1.0D, 18.0D);
        int minimumU = Mth.floor(centerU - radius - 1.0D);
        int maximumU = Mth.ceil(centerU + radius + 1.0D);
        int minimumV = Mth.floor(centerV - radius - 1.0D);
        int maximumV = Mth.ceil(centerV + radius + 1.0D);
        for (int v = minimumV; v <= maximumV; v++) {
            for (int u = minimumU; u <= maximumU; u++) {
                double du = u + 0.5D - centerU;
                double dv = v + 0.5D - centerV;
                long hash = eruptionCellSeed(imprint, u, v,
                        0x9e3779b97f4a7c15L);
                double jitter = (((hash >>> 25) & 255L) / 255.0D - 0.5D) * 0.62D;
                double depression = MudSurfaceHeightField.impactDepression(
                        du, dv, radius, jitter, 0.94D);
                if (depression > 0.003D) {
                    refreshCell(imprint, u, v, depression);
                }
            }
        }
    }

    private static SideCell ensureCell(SideImprint imprint, int u, int v) {
        if (!insideImprintBounds(imprint, u, v)) {
            return null;
        }
        int index = cellIndex(u, v);
        SideCell cell = imprint.cells.get(index);
        if (cell != null) {
            return cell;
        }
        if (retainedCells >= MudSurfaceClientSettings.maxSideCells()) {
            return null;
        }
        long hash = eruptionCellSeed(imprint, u, v,
                0x632be59bd9b4e019L);
        MudRenderedSurfaceGeometry.SurfacePatch renderedPatch = null;
        if (imprint.subLevel == null
                && MudSurfaceClientSettings.preciseModelGeometry()) {
            BlockState state = level.getBlockState(imprint.pos);
            if (state.getBlock() instanceof AdaptiveMudBlock) {
                renderedPatch = MudRenderedSurfaceGeometry.surfacePatch(
                        level, imprint.pos, state, imprint.face, u, v);
            }
        }
        cell = new SideCell(u, v, hash, renderedPatch);
        cell.packedLight = MudSurfaceEffectManager.exposedSurfaceLight(
                level, cellCenter(imprint, cell), imprint.basis.normal);
        imprint.cells.put(index, cell);
        retainedCells++;
        return cell;
    }

    private static boolean insideImprintBounds(
            SideImprint imprint, int u, int v) {
        if (u < 0 || u > 15 || v < 0 || v > 15) {
            return false;
        }
        double centerU = (u + 0.5D) * PIXEL;
        double centerV = (v + 0.5D) * PIXEL;
        return centerU >= imprint.minimumU - 1.0E-5D
                && centerU <= imprint.maximumU + 1.0E-5D
                && centerV >= imprint.minimumV - 1.0E-5D
                && centerV <= imprint.maximumV + 1.0E-5D
                && (imprint.exposedCells == null
                        || imprint.exposedCells[cellIndex(u, v)]);
    }

    private static void finishTick() {
        Iterator<SideImprint> iterator = IMPRINTS.values().iterator();
        while (iterator.hasNext()) {
            SideImprint imprint = iterator.next();
            if (!stillValid(imprint)) {
                retainedCells -= imprint.cells.size();
                detachFromEruptionGroup(imprint);
                iterator.remove();
                continue;
            }
            updateCells(imprint);
            if (imprint.cells.isEmpty()) {
                detachFromEruptionGroup(imprint);
                iterator.remove();
            }
        }
    }

    private static boolean stillValid(SideImprint imprint) {
        BlockState state = imprint.subLevel == null
                ? level.getBlockState(imprint.pos)
                : SableCompat.subLevelBlockState(
                        level, imprint.subLevel, imprint.pos);
        SinkingMedium medium = ModBlocks.mediumOf(state.getBlock());
        if (medium != imprint.medium) {
            return false;
        }
        // Sable side effects keep their frozen local face snapshot. Ordinary-world
        // faces can cheaply revalidate neighboring occlusion without another scan.
        if (imprint.subLevel != null) {
            return true;
        }
        boolean shapedSurface = state.getBlock() instanceof AdaptiveMudBlock
                || !MudBlock.supportsVerticalSinking(state, medium);
        if (!shapedSurface) {
            FaceExposure exposure = faceExposure(
                    imprint.pos,
                    imprint.face,
                    MudMediumRuntime.surfaceHeight(
                            level, imprint.pos, state, medium));
            if (exposure.empty()) {
                return false;
            }
            imprint.minimumV = exposure.minimumV();
            imprint.maximumV = exposure.maximumV();
            return true;
        }
        if (state.getBlock() instanceof AdaptiveMudBlock
                && MudSurfaceClientSettings.preciseModelGeometry()) {
            for (MudRenderedSurfaceGeometry.RenderedFace rendered
                    : MudRenderedSurfaceGeometry.axisFaces(
                            level, imprint.pos, state)) {
                if (rendered.face() != imprint.face
                        || rendered.geometryKey() != imprint.key.geometryKey()) {
                    continue;
                }
                FaceBasis basis = faceBasis(
                        imprint.pos, imprint.face,
                        worldPlane(imprint.pos, imprint.face, rendered.plane()));
                boolean[] exposedCells = worldExposedCells(
                        basis, rendered.cells());
                updateExposedCells(imprint, exposedCells);
                return any(exposedCells);
            }
            return false;
        }
        List<AABB> shapeBoxes = MudBlock.localShape(
                level, imprint.pos, state, medium).toAabbs();
        for (AABB localBox : shapeBoxes) {
            MudSurfaceShapeGeometry.FaceMask mask =
                    MudSurfaceShapeGeometry.faceMask(
                            shapeBoxes, localBox, imprint.face);
            if (mask.geometryKey() != imprint.key.geometryKey()) {
                continue;
            }
            return mask.any();
        }
        return false;
    }

    private static void updateCells(SideImprint imprint) {
        double closeTicks = effectiveCloseTicks(imprint);
        double closeRate = closureRate(imprint.maximumClosureLayers, closeTicks);
        for (SideCell cell : imprint.cells.values()) {
            if (cell.refreshed || cell.depression <= 0.003D) {
                continue;
            }
            int mask = boundaryMask(imprint, cell);
            if (mask != 0) {
                double variation = 0.80D
                        + ((cell.seed >>> 27) & 255L) / 255.0D * 0.40D;
                cell.closureProgress = Math.min(
                        1.0D,
                        cell.closureProgress + closeRate * variation);
                cell.closureMask = mask;
                if (cell.closureProgress >= 1.0D) {
                    cell.depression = 0.0D;
                    cell.closureMask = 0;
                }
            }
        }
        double rimHeight = MudMediumRuntime.value(
                level, imprint.pos,
                imprint.medium, MudPhysicsParameter.SURFACE_RIM_HEIGHT_PIXELS)
                * PIXEL * (imprint.basis.normal.y < -0.70D ? 1.08D : 0.72D);
        double response = MudMediumRuntime.value(
                level, imprint.pos,
                imprint.medium, MudPhysicsParameter.SURFACE_HEIGHT_RESPONSE);
        double settleResponse = Math.min(
                response * 0.62D,
                Math.max(0.015D, 4.0D / closeTicks));
        Iterator<SideCell> iterator = imprint.cells.values().iterator();
        while (iterator.hasNext()) {
            SideCell cell = iterator.next();
            if (!insideImprintBounds(imprint, cell.u, cell.v)) {
                iterator.remove();
                retainedCells--;
                continue;
            }
            double targetPile = cell.depression > 0.003D
                    ? 0.0D
                    : adjacentDepression(imprint, cell) * rimHeight
                            * (0.82D + ((cell.seed >>> 35) & 63L) / 315.0D);
            cell.pileHeight += (targetPile - cell.pileHeight)
                    * (targetPile >= cell.pileHeight ? response : settleResponse);
            if (cell.depression <= 0.003D
                    && cell.pileHeight <= MudSurfaceEffectManager.SURFACE_CELL_VISUAL_HEIGHT_EPSILON
                    && targetPile <= MudSurfaceEffectManager.SURFACE_CELL_VISUAL_HEIGHT_EPSILON) {
                cell.pileHeight = 0.0D;
                cell.previousPileHeight = 0.0D;
                iterator.remove();
                retainedCells--;
            }
        }
    }

    private static int boundaryMask(SideImprint imprint, SideCell cell) {
        int mask = 0;
        if (!connected(imprint, cell.u - 1, cell.v)) {
            mask |= 1;
        }
        if (!connected(imprint, cell.u + 1, cell.v)) {
            mask |= 2;
        }
        if (!connected(imprint, cell.u, cell.v - 1)) {
            mask |= 4;
        }
        if (!connected(imprint, cell.u, cell.v + 1)) {
            mask |= 8;
        }
        return mask;
    }

    private static boolean connected(SideImprint imprint, int u, int v) {
        SideCell cell = sideCellAt(imprint, u, v);
        return cell != null && effectiveDepression(cell) > 0.003D;
    }

    private static double adjacentDepression(SideImprint imprint, SideCell cell) {
        double strongest = 0.0D;
        for (int dv = -1; dv <= 1; dv++) {
            for (int du = -1; du <= 1; du++) {
                if (du == 0 && dv == 0) {
                    continue;
                }
                SideCell neighbor = sideCellAt(
                        imprint, cell.u + du, cell.v + dv);
                if (neighbor != null) {
                    strongest = Math.max(strongest, effectiveDepression(neighbor));
                }
            }
        }
        return strongest;
    }

    private static SideCell sideCellAt(SideImprint imprint, int u, int v) {
        if (u >= 0 && u <= 15 && v >= 0 && v <= 15) {
            return imprint.cells.get(cellIndex(u, v));
        }
        ImprintCell mapped = adjacentImprintCell(imprint, u, v);
        return mapped == null ? null : mapped.imprint.cells.get(
                cellIndex(mapped.u, mapped.v));
    }

    private static SideCell ensureCellAt(SideImprint imprint, int u, int v) {
        if (u >= 0 && u <= 15 && v >= 0 && v <= 15) {
            return ensureCell(imprint, u, v);
        }
        ImprintCell mapped = adjacentImprintCell(imprint, u, v);
        return mapped == null ? null
                : ensureCell(mapped.imprint, mapped.u, mapped.v);
    }

    private static ImprintCell adjacentImprintCell(
            SideImprint imprint, int u, int v) {
        EruptionFaceGroup group = imprint.eruptionGroup;
        if (group == null) {
            return null;
        }
        Vec3 sample = cellCenter(imprint.localBasis, u, v);
        Vec3 inside = sample.subtract(imprint.localBasis.normal.scale(0.002D));
        List<SideImprint> candidates = group.imprints.get(
                BlockPos.containing(inside).asLong());
        if (candidates == null) {
            return null;
        }
        for (SideImprint candidate : candidates) {
            Vec3 relative = sample.subtract(candidate.localBasis.origin);
            int candidateU = Mth.floor(
                    relative.dot(candidate.localBasis.axisU) / PIXEL);
            int candidateV = Mth.floor(
                    relative.dot(candidate.localBasis.axisV) / PIXEL);
            if (candidateU < 0 || candidateU > 15
                    || candidateV < 0 || candidateV > 15) {
                continue;
            }
            return new ImprintCell(candidate, candidateU, candidateV);
        }
        return null;
    }

    private static void detachFromEruptionGroup(SideImprint imprint) {
        EruptionFaceGroup group = imprint.eruptionGroup;
        if (group != null) {
            group.remove(imprint);
            if (group.isEmpty()
                    && ERUPTION_GROUPS.get(imprint.key.entityId()) == group) {
                ERUPTION_GROUPS.remove(imprint.key.entityId());
            }
            imprint.eruptionGroup = null;
        }
    }

    private static long eruptionCellSeed(
            SideImprint imprint, int u, int v, long salt) {
        if (imprint.eruptionGroup == null) {
            return MudSurfaceEffectManager.mix(
                    imprint.seed ^ u * 0x632be59bd9b4e019L
                            ^ v * 0x94d049bb133111ebL ^ salt);
        }
        Vec3 center = cellCenter(imprint.localBasis, u, v);
        long pixelX = Mth.floor(center.x / PIXEL);
        long pixelY = Mth.floor(center.y / PIXEL);
        long pixelZ = Mth.floor(center.z / PIXEL);
        return MudSurfaceEffectManager.mix(
                imprint.seed
                        ^ pixelX * 0x9e3779b97f4a7c15L
                        ^ pixelY * 0xc2b2ae3d27d4eb4fL
                        ^ pixelZ * 0x165667b19e3779f9L
                        ^ salt);
    }

    static double effectiveDepression(SideCell cell) {
        return cell.depression
                * (1.0D - Mth.clamp(cell.closureProgress, 0.0D, 1.0D));
    }

    private static double effectiveCloseTicks(SideImprint imprint) {
        BlockPos profilePos = imprint.pos;
        double configured = Math.max(1.0D, MudMediumRuntime.value(
                level, profilePos, imprint.medium, MudPhysicsParameter.SURFACE_CLOSE_TICKS));
        double viscosity = MudMediumRuntime.value(
                level, profilePos, imprint.medium, MudPhysicsParameter.VISCOSITY_SURFACE);
        return configured * Mth.clamp(2.10D + viscosity * 0.65D, 2.25D, 6.0D);
    }

    static double closureRate(double maximumClosureLayers, double closeTicks) {
        return Math.min(
                1.0D,
                Math.max(1.0D, maximumClosureLayers) / Math.max(1.0D, closeTicks));
    }

    private static void pruneClosedImprints() {
        Iterator<SideImprint> iterator = IMPRINTS.values().iterator();
        while (iterator.hasNext()) {
            SideImprint imprint = iterator.next();
            if (!imprint.seenThisTick && imprint.cells.isEmpty()) {
                detachFromEruptionGroup(imprint);
                iterator.remove();
                return;
            }
        }
    }

    static Vec3 cellCenter(SideImprint imprint, SideCell cell) {
        return cellCenter(imprint.basis, cell);
    }

    static Vec3 cellCenter(FaceBasis basis, SideCell cell) {
        return cellCenter(basis, cell.u, cell.v);
    }

    private static Vec3 cellCenter(FaceBasis basis, int u, int v) {
        return basis.origin
                .add(basis.axisU.scale((u + 0.5D) * PIXEL))
                .add(basis.axisV.scale((v + 0.5D) * PIXEL));
    }

    static FaceBasis renderBasis(SideImprint imprint,
            IdentityHashMap<Object, SableCompat.AffineTransform> transforms) {
        if (imprint.subLevel == null) {
            return imprint.basis;
        }
        SableCompat.AffineTransform transform =
                transforms.get(imprint.subLevel);
        if (transform == null && !transforms.containsKey(imprint.subLevel)) {
            SableCompat.RigidTransform rigid =
                    SableCompat.rigidTransform(imprint.subLevel);
            transform = rigid == null ? null : rigid.resolveWorld();
            transforms.put(imprint.subLevel, transform);
        }
        FaceBasis transformed = transformBasis(
                transform, imprint.localBasis);
        return transformed == null ? imprint.basis : transformed;
    }

    private static FaceBasis faceBasis(BlockPos pos, Direction face) {
        double plane = switch (face) {
            case NORTH -> pos.getZ();
            case SOUTH -> pos.getZ() + 1.0D;
            case WEST -> pos.getX();
            case EAST -> pos.getX() + 1.0D;
            case DOWN -> pos.getY();
            case UP -> pos.getY() + 1.0D;
        };
        return faceBasis(pos, face, plane);
    }

    private static FaceBasis faceBasis(
            BlockPos pos, Direction face, double plane) {
        Vec3 normal = new Vec3(
                face.getStepX(), face.getStepY(), face.getStepZ());
        if (face.getAxis() == Direction.Axis.Y) {
            return new FaceBasis(
                    new Vec3(pos.getX(), plane, pos.getZ()),
                    normal,
                    new Vec3(1.0D, 0.0D, 0.0D),
                    new Vec3(0.0D, 0.0D, 1.0D));
        }
        Vec3 axisV = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 axisU = axisV.cross(normal);
        Vec3 origin = switch (face) {
            case NORTH -> new Vec3(pos.getX() + 1.0D, pos.getY(), plane);
            case SOUTH -> new Vec3(pos.getX(), pos.getY(), plane);
            case WEST -> new Vec3(plane, pos.getY(), pos.getZ());
            case EAST -> new Vec3(plane, pos.getY(), pos.getZ() + 1.0D);
            default -> throw new IllegalArgumentException(
                    "Side or underside face required");
        };
        return new FaceBasis(origin, normal, axisU, axisV);
    }

    private static FaceBasis transformBasis(
            Object subLevel, FaceBasis localBasis) {
        Vec3 origin = SableCompat.toWorld(subLevel, localBasis.origin);
        Vec3 normal = SableCompat.toWorldDirection(
                subLevel, localBasis.normal);
        Vec3 axisU = SableCompat.toWorldDirection(
                subLevel, localBasis.axisU);
        Vec3 axisV = SableCompat.toWorldDirection(
                subLevel, localBasis.axisV);
        if (origin == null || normal == null || axisU == null || axisV == null
                || normal.lengthSqr() <= 1.0E-8D
                || axisU.lengthSqr() <= 1.0E-8D
                || axisV.lengthSqr() <= 1.0E-8D) {
            return null;
        }
        return new FaceBasis(
                origin,
                normal.normalize(),
                axisU.normalize(),
                axisV.normalize());
    }

    private static FaceBasis transformBasis(
            SableCompat.AffineTransform transform, FaceBasis localBasis) {
        if (transform == null) {
            return null;
        }
        Vec3 origin = transform.toWorld(localBasis.origin);
        Vec3 normal = transform.toWorldDirection(localBasis.normal);
        Vec3 axisU = transform.toWorldDirection(localBasis.axisU);
        Vec3 axisV = transform.toWorldDirection(localBasis.axisV);
        if (normal.lengthSqr() <= 1.0E-8D
                || axisU.lengthSqr() <= 1.0E-8D
                || axisV.lengthSqr() <= 1.0E-8D) {
            return null;
        }
        return new FaceBasis(
                origin,
                normal.normalize(),
                axisU.normalize(),
                axisV.normalize());
    }

    private static ProjectionRange projectionRange(
            FaceBasis basis, AABB box) {
        double minimumU = Double.POSITIVE_INFINITY;
        double maximumU = Double.NEGATIVE_INFINITY;
        double minimumV = Double.POSITIVE_INFINITY;
        double maximumV = Double.NEGATIVE_INFINITY;
        for (int corner = 0; corner < 8; corner++) {
            Vec3 point = new Vec3(
                    (corner & 1) == 0 ? box.minX : box.maxX,
                    (corner & 2) == 0 ? box.minY : box.maxY,
                    (corner & 4) == 0 ? box.minZ : box.maxZ);
            Vec3 relative = point.subtract(basis.origin);
            double u = relative.dot(basis.axisU);
            double v = relative.dot(basis.axisV);
            minimumU = Math.min(minimumU, u);
            maximumU = Math.max(maximumU, u);
            minimumV = Math.min(minimumV, v);
            maximumV = Math.max(maximumV, v);
        }
        return new ProjectionRange(
                Mth.clamp(minimumU, 0.0D, 1.0D),
                Mth.clamp(maximumU, 0.0D, 1.0D),
                Mth.clamp(minimumV, 0.0D, 1.0D),
                Mth.clamp(maximumV, 0.0D, 1.0D));
    }

    private static int surfaceGeometryKey(AABB box) {
        long bits = Double.doubleToLongBits(box.minX);
        bits = bits * 31L + Double.doubleToLongBits(box.minY);
        bits = bits * 31L + Double.doubleToLongBits(box.minZ);
        bits = bits * 31L + Double.doubleToLongBits(box.maxX);
        bits = bits * 31L + Double.doubleToLongBits(box.maxY);
        bits = bits * 31L + Double.doubleToLongBits(box.maxZ);
        return (int) (bits ^ bits >>> 32);
    }

    private static boolean[] worldExposedCells(
            FaceBasis basis, boolean[] localMask) {
        boolean[] exposed = localMask.clone();
        for (int v = 0; v < 16; v++) {
            for (int u = 0; u < 16; u++) {
                int index = cellIndex(u, v);
                if (!exposed[index]) {
                    continue;
                }
                Vec3 point = basis.origin
                        .add(basis.axisU.scale((u + 0.5D) * PIXEL))
                        .add(basis.axisV.scale((v + 0.5D) * PIXEL));
                if (occupiedAt(point.add(basis.normal.scale(0.0025D)))) {
                    exposed[index] = false;
                }
            }
        }
        return exposed;
    }

    private static boolean any(boolean[] cells) {
        for (boolean cell : cells) {
            if (cell) {
                return true;
            }
        }
        return false;
    }

    private static void updateExposedCells(
            SideImprint imprint, boolean[] exposedCells) {
        imprint.exposedCells = exposedCells;
        if (exposedCells == null || imprint.cells.isEmpty()) {
            return;
        }
        Iterator<SideCell> iterator = imprint.cells.values().iterator();
        while (iterator.hasNext()) {
            SideCell cell = iterator.next();
            if (!exposedCells[cellIndex(cell.u, cell.v)]) {
                iterator.remove();
                retainedCells--;
            }
        }
    }

    private static int cellIndex(int u, int v) {
        return u | v << 4;
    }

    private static int eruptionKey(int ventId) {
        return Integer.MIN_VALUE + Math.max(1, ventId);
    }

    private record SideKey(int entityId, Object subLevel, long blockPos,
            Direction face, int geometryKey) {
    }

    private record ProjectionRange(double minimumU, double maximumU,
            double minimumV, double maximumV) {
    }

    private record FaceExposure(double minimumV, double maximumV) {
        private static final FaceExposure EMPTY =
                new FaceExposure(0.0D, 0.0D);

        private boolean empty() {
            return maximumV - minimumV <= 0.002D;
        }
    }

    private static final class EruptionFaceGroup {
        private final Object subLevel;
        private final Direction face;
        private final long plane;
        private final Map<Long, List<SideImprint>> imprints = new HashMap<>();

        private EruptionFaceGroup(Object subLevel, Direction face, double plane) {
            this.subLevel = subLevel;
            this.face = face;
            this.plane = Math.round(plane * 4096.0D);
        }

        private boolean matches(Object candidateSubLevel,
                Direction candidateFace, double candidatePlane) {
            return subLevel == candidateSubLevel
                    && face == candidateFace
                    && plane == Math.round(candidatePlane * 4096.0D);
        }

        private void add(SideImprint imprint) {
            List<SideImprint> atBlock = imprints.computeIfAbsent(
                    imprint.pos.asLong(), ignored -> new java.util.ArrayList<>(1));
            if (!atBlock.contains(imprint)) {
                atBlock.add(imprint);
            }
        }

        private void remove(SideImprint imprint) {
            List<SideImprint> atBlock = imprints.get(imprint.pos.asLong());
            if (atBlock == null) {
                return;
            }
            atBlock.remove(imprint);
            if (atBlock.isEmpty()) {
                imprints.remove(imprint.pos.asLong());
            }
        }

        private void stamp(Vec3 localOrigin, double radiusPixels) {
            for (List<SideImprint> atBlock : imprints.values()) {
                for (SideImprint imprint : atBlock) {
                    stampEruption(imprint, localOrigin, radiusPixels);
                }
            }
        }

        private boolean isEmpty() {
            return imprints.isEmpty();
        }
    }

    private record ImprintCell(SideImprint imprint, int u, int v) {
    }

    static final class SideImprint {
        private final boolean[] directCells = new boolean[256];
        final SideKey key;
        final Object subLevel;
        final BlockPos pos;
        final Direction face;
        final FaceBasis localBasis;
        FaceBasis basis;
        final long seed;
        final Int2ObjectOpenHashMap<SideCell> cells = new Int2ObjectOpenHashMap<>();
        SinkingMedium medium;
        long visualSource;
        double minimumU;
        double maximumU;
        double minimumV;
        double maximumV;
        int minimumRefreshedU = 16;
        int maximumRefreshedU = -1;
        int minimumRefreshedV = 16;
        int maximumRefreshedV = -1;
        double maximumClosureLayers = 1.0D;
        boolean[] exposedCells;
        boolean seenThisTick;
        boolean persistent;
        EruptionFaceGroup eruptionGroup;

        boolean physicalized() {
            return subLevel != null;
        }

        SideImprint(SideKey key, Object subLevel, BlockPos pos,
                Direction face, FaceBasis localBasis, FaceBasis basis,
                SinkingMedium medium, long seed) {
            this.key = key;
            this.subLevel = subLevel;
            this.pos = pos;
            this.face = face;
            this.localBasis = localBasis;
            this.basis = basis;
            this.medium = medium;
            this.seed = seed;
        }

        void beginTick() {
            seenThisTick = persistent;
            for (SideCell cell : cells.values()) {
                cell.previousDepression = cell.depression;
                cell.previousClosureProgress = cell.closureProgress;
                cell.previousPileHeight = cell.pileHeight;
                cell.refreshed = persistent;
            }
        }

        void includeRefreshedCell(int u, int v) {
            minimumRefreshedU = Math.min(minimumRefreshedU, u);
            maximumRefreshedU = Math.max(maximumRefreshedU, u);
            minimumRefreshedV = Math.min(minimumRefreshedV, v);
            maximumRefreshedV = Math.max(maximumRefreshedV, v);
            int width = maximumRefreshedU - minimumRefreshedU + 1;
            int height = maximumRefreshedV - minimumRefreshedV + 1;
            maximumClosureLayers = Math.max(
                    maximumClosureLayers,
                    Math.max(1.0D, Math.min(width, height) * 0.5D));
        }
    }

    static final class SideCell {
        final int u;
        final int v;
        final long seed;
        final MudRenderedSurfaceGeometry.SurfacePatch renderedPatch;
        double previousDepression;
        double depression;
        double previousClosureProgress;
        double closureProgress;
        int closureMask;
        double previousPileHeight;
        double pileHeight;
        boolean refreshed;
        int packedLight;

        SideCell(int u, int v, long seed,
                MudRenderedSurfaceGeometry.SurfacePatch renderedPatch) {
            this.u = u;
            this.v = v;
            this.seed = seed;
            this.renderedPatch = renderedPatch;
        }
    }

    static final class FaceBasis {
        final Vec3 origin;
        final Vec3 normal;
        final Vec3 axisU;
        final Vec3 axisV;

        FaceBasis(Vec3 origin, Vec3 normal, Vec3 axisU, Vec3 axisV) {
            this.origin = origin;
            this.normal = normal;
            this.axisU = axisU;
            this.axisV = axisV;
        }
    }
}

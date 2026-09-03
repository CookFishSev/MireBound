package com.fish.mirebound.eruption;

import com.fish.mirebound.adaptive.MudVisualPalette;
import com.fish.mirebound.adaptive.MudVisualSource;
import com.fish.mirebound.compat.sable.SableCompat;
import com.fish.mirebound.mud.MudBlock;
import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.registry.ModBlocks;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Bounded exposed-face lookup and seam palette sampling for mud vents. */
final class MudEruptionSurfaceSampler {
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final int MAXIMUM_CANDIDATES_PER_PROBE = 48;
    private static final int PALETTE_GRID = 5;
    private static final double PIXEL = 1.0D / 16.0D;

    private MudEruptionSurfaceSampler() {
    }

    static List<Surface> findSurfaces(ServerLevel level, ServerPlayer trackingPlayer,
            int x, int z, int topY, int bottomY) {
        List<Surface> result = new ArrayList<>();
        for (int y = topY; y >= bottomY
                && result.size() < MAXIMUM_CANDIDATES_PER_PROBE; y--) {
            addExposedSurfaces(level, null, new BlockPos(x, y, z), result);
        }
        if (result.size() >= MAXIMUM_CANDIDATES_PER_PROBE || !SableCompat.isLoaded()) {
            return List.copyOf(result);
        }
        AABB bounds = new AABB(
                x - 0.08D, bottomY - 0.08D, z - 0.08D,
                x + 1.08D, topY + 1.08D, z + 1.08D);
        for (SableCompat.MudSurfaceFace face
                : SableCompat.mudSurfaceFaces(level, bounds, trackingPlayer)) {
            if (result.size() >= MAXIMUM_CANDIDATES_PER_PROBE) {
                break;
            }
            UUID subLevelId = SableCompat.subLevelId(face.subLevel());
            if (subLevelId == null) {
                continue;
            }
            result.add(new Surface(
                    face.subLevel(), subLevelId, face.pos(), face.medium(),
                    face.face(), face.localBox(), face.plane(),
                    faceCenter(face.localBox(), face.face()),
                    MudVisualSource.capture(level, face.pos(), face.face())));
        }
        return List.copyOf(result);
    }

    static List<Surface> exposedSurfaces(ServerLevel level, BlockPos pos) {
        List<Surface> result = new ArrayList<>(6);
        addExposedSurfaces(level, SableCompat.subLevelAtStorage(level, pos), pos, result);
        return List.copyOf(result);
    }

    static boolean stillSupports(ServerLevel level, Surface surface) {
        if (surface.physicalized()
                && SableCompat.subLevelById(level, surface.subLevelId()) == null) {
            return false;
        }
        BlockState state = level.getBlockState(surface.pos());
        SinkingMedium medium = ModBlocks.mediumOf(state.getBlock());
        if (medium != surface.medium()
                || MudVisualSource.capture(level, surface.pos(), surface.face())
                        != surface.visualSource()) {
            return false;
        }
        Vec3 normal = surface.localNormal();
        Vec3 inside = surface.localOrigin().subtract(normal.scale(0.003D));
        if (!MudBlock.containsLocalPoint(
                level, surface.pos(), state, medium,
                inside.subtract(surface.pos().getX(), surface.pos().getY(), surface.pos().getZ()),
                0.004D)) {
            return false;
        }
        return isAirOutside(level, surface.pos(), state, medium,
                surface.localOrigin().add(normal.scale(0.003D)));
    }

    static MudVisualPalette visualPalette(ServerLevel level, Surface surface,
            double radiusBlocks) {
        MudVisualPalette palette = new MudVisualPalette();
        double span = Math.max(PIXEL, radiusBlocks * 0.92D);
        Vec3 axisU = surface.localAxisU();
        Vec3 axisV = surface.localAxisV();
        Vec3 normal = surface.localNormal();
        for (int gridU = 0; gridU < PALETTE_GRID; gridU++) {
            for (int gridV = 0; gridV < PALETTE_GRID; gridV++) {
                double offsetU = (gridU / (double) (PALETTE_GRID - 1) * 2.0D - 1.0D) * span;
                double offsetV = (gridV / (double) (PALETTE_GRID - 1) * 2.0D - 1.0D) * span;
                if (offsetU * offsetU + offsetV * offsetV > span * span * 1.12D) {
                    continue;
                }
                Vec3 sample = surface.localOrigin()
                        .add(axisU.scale(offsetU))
                        .add(axisV.scale(offsetV))
                        .subtract(normal.scale(0.025D));
                addPaletteSample(level, palette, sample, surface.face());
            }
        }
        if (palette.isEmpty()) {
            palette.add(surface.medium(), surface.visualSource(), 1.0F);
        }
        return palette;
    }

    private static void addExposedSurfaces(ServerLevel level, Object subLevel,
            BlockPos pos, List<Surface> result) {
        BlockState state = level.getBlockState(pos);
        SinkingMedium medium = ModBlocks.mediumOf(state.getBlock());
        if (medium == null || !(state.getBlock() instanceof MudBlock)) {
            return;
        }
        UUID subLevelId = SableCompat.subLevelId(subLevel);
        Set<FaceKey> visited = new HashSet<>();
        for (AABB local : MudBlock.localShape(
                level, pos, state, medium).toAabbs()) {
            AABB box = local.move(pos);
            for (Direction face : DIRECTIONS) {
                double plane = facePlane(box, face);
                FaceKey key = new FaceKey(face, quantize(plane),
                        quantize(minimumU(box, face)), quantize(maximumU(box, face)),
                        quantize(minimumV(box, face)), quantize(maximumV(box, face)));
                Vec3 center = faceCenter(box, face);
                if (!visited.add(key)
                        || !isAirOutside(level, pos, state, medium,
                                center.add(direction(face).scale(0.003D)))) {
                    continue;
                }
                result.add(new Surface(
                        subLevel, subLevelId, pos.immutable(), medium, face,
                        box, plane, center,
                        MudVisualSource.capture(level, pos, face)));
            }
        }
    }

    private static boolean isAirOutside(ServerLevel level, BlockPos support,
            BlockState supportState, SinkingMedium supportMedium, Vec3 outside) {
        BlockPos outsidePos = BlockPos.containing(outside);
        if (outsidePos.equals(support)) {
            return !MudBlock.containsLocalPoint(
                    level, support, supportState, supportMedium,
                    outside.subtract(support.getX(), support.getY(), support.getZ()),
                    0.0005D);
        }
        BlockState outsideState = level.getBlockState(outsidePos);
        return outsideState.isAir()
                || outsideState.getBlock() == ModBlocks.MUD_FOOTPRINT.get();
    }

    private static void addPaletteSample(ServerLevel level, MudVisualPalette palette,
            Vec3 localPoint, Direction face) {
        BlockPos pos = BlockPos.containing(localPoint);
        BlockState state = level.getBlockState(pos);
        SinkingMedium medium = ModBlocks.mediumOf(state.getBlock());
        if (medium == null || !(state.getBlock() instanceof MudBlock)
                || !MudBlock.containsLocalPoint(
                        level, pos, state, medium,
                        localPoint.subtract(pos.getX(), pos.getY(), pos.getZ()),
                        0.004D)) {
            return;
        }
        palette.add(medium, MudVisualSource.capture(level, pos, face), 1.0F);
    }

    private static Vec3 faceCenter(AABB box, Direction face) {
        return switch (face.getAxis()) {
            case X -> new Vec3(facePlane(box, face),
                    (box.minY + box.maxY) * 0.5D, (box.minZ + box.maxZ) * 0.5D);
            case Y -> new Vec3((box.minX + box.maxX) * 0.5D,
                    facePlane(box, face), (box.minZ + box.maxZ) * 0.5D);
            case Z -> new Vec3((box.minX + box.maxX) * 0.5D,
                    (box.minY + box.maxY) * 0.5D, facePlane(box, face));
        };
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

    private static double minimumU(AABB box, Direction face) {
        return face.getAxis() == Direction.Axis.X ? box.minZ : box.minX;
    }

    private static double maximumU(AABB box, Direction face) {
        return face.getAxis() == Direction.Axis.X ? box.maxZ : box.maxX;
    }

    private static double minimumV(AABB box, Direction face) {
        return face.getAxis() == Direction.Axis.Y ? box.minZ : box.minY;
    }

    private static double maximumV(AABB box, Direction face) {
        return face.getAxis() == Direction.Axis.Y ? box.maxZ : box.maxY;
    }

    private static long quantize(double value) {
        return Math.round(value * 4096.0D);
    }

    private static Vec3 direction(Direction face) {
        return new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
    }

    private static Vec3 axisU(Direction face) {
        if (face.getAxis() == Direction.Axis.Y) {
            return new Vec3(1.0D, 0.0D, 0.0D);
        }
        return new Vec3(0.0D, 1.0D, 0.0D).cross(direction(face));
    }

    private static Vec3 axisV(Direction face) {
        return face.getAxis() == Direction.Axis.Y
                ? new Vec3(0.0D, 0.0D, 1.0D)
                : new Vec3(0.0D, 1.0D, 0.0D);
    }

    private static double randomPixelCoordinate(
            double minimum, double maximum, RandomSource random) {
        int first = Mth.ceil(minimum / PIXEL - 0.5D - 1.0E-6D);
        int last = Mth.floor(maximum / PIXEL - 0.5D + 1.0E-6D);
        if (first > last) {
            return (minimum + maximum) * 0.5D;
        }
        return (first + (first == last ? 0 : random.nextInt(last - first + 1)) + 0.5D) * PIXEL;
    }

    record Surface(Object subLevel, UUID subLevelId, BlockPos pos,
            SinkingMedium medium, Direction face, AABB localBox, double plane,
            Vec3 localOrigin, long visualSource) {
        boolean physicalized() {
            return subLevelId != null;
        }

        Surface withRandomOrigin(RandomSource random) {
            double u = randomPixelCoordinate(
                    minimumU(localBox, face), maximumU(localBox, face), random);
            double v = randomPixelCoordinate(
                    minimumV(localBox, face), maximumV(localBox, face), random);
            Vec3 origin = switch (face.getAxis()) {
                case X -> new Vec3(plane, v, u);
                case Y -> new Vec3(u, plane, v);
                case Z -> new Vec3(u, v, plane);
            };
            return new Surface(subLevel, subLevelId, pos, medium, face,
                    localBox, plane, origin, visualSource);
        }

        Vec3 worldOrigin() {
            return physicalized() ? SableCompat.toWorld(subLevel, localOrigin) : localOrigin;
        }

        Vec3 worldNormal() {
            Vec3 normal = localNormal();
            Vec3 transformed = physicalized()
                    ? SableCompat.toWorldDirection(subLevel, normal) : normal;
            return transformed == null || transformed.lengthSqr() <= 1.0E-8D
                    ? normal : transformed.normalize();
        }

        Vec3 localNormal() {
            return direction(face);
        }

        Vec3 localAxisU() {
            return axisU(face);
        }

        Vec3 localAxisV() {
            return axisV(face);
        }

    }

    private record FaceKey(Direction face, long plane, long minimumU,
            long maximumU, long minimumV, long maximumV) {
    }
}

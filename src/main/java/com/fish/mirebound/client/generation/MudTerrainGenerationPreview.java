package com.fish.mirebound.client.generation;

import com.fish.mirebound.adaptive.AdaptiveMudBlock;
import com.fish.mirebound.adaptive.AdaptiveMudEligibility;
import com.fish.mirebound.client.AdaptiveMudClientCache;
import com.fish.mirebound.generation.MudTerrainDepositPlanner;
import com.fish.mirebound.generation.MudTerrainDepositShape;
import com.fish.mirebound.generation.MudTerrainGenerationRequest;
import com.fish.mirebound.generation.MudTerrainGenerationSettings;
import com.fish.mirebound.generation.MudTerrainGenerationType;
import com.fish.mirebound.generation.MudTerrainLakeShape;
import com.fish.mirebound.generation.MudTerrainLakeSettings;
import com.fish.mirebound.generation.natural.NaturalMudDepositShape;
import com.fish.mirebound.generation.natural.NaturalMudDepositShape.Cell;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Cached client-only voxel volume shared by the preview renderer and wand beam. */
public final class MudTerrainGenerationPreview {
    private static final long WORLD_REFRESH_INTERVAL = 20L;
    private static Preview active;
    private static MudTerrainLakeSettings cachedLakeSettings;
    private static MudTerrainLakeShape.Shape cachedLakeShape;

    private MudTerrainGenerationPreview() {
    }

    public static Preview refresh(
            Minecraft minecraft, MudTerrainGenerationRequest request) {
        ClientLevel level = minecraft.level;
        if (level == null || request == null) {
            reset();
            return null;
        }
        long gameTime = level.getGameTime();
        if (active != null
                && active.dimension.equals(level.dimension().location())
                && active.request.equals(request)
                && gameTime - active.builtAt < WORLD_REFRESH_INTERVAL) {
            return active;
        }
        Set<Long> interior = new HashSet<>();
        Set<Long> cavity = new HashSet<>();
        Set<Long> shell = new HashSet<>();
        if (request.type() == MudTerrainGenerationType.SURFACE_DEPOSIT) {
            buildSurface(level, request, interior);
        } else if (request.type().isNaturalDeposit()) {
            buildNatural(request, level, interior);
        } else {
            buildLake(level, request, interior, cavity, shell);
        }
        Set<Long> occupied = new HashSet<>(
                interior.size() + cavity.size() + shell.size());
        occupied.addAll(interior);
        occupied.addAll(cavity);
        occupied.addAll(shell);
        active = occupied.isEmpty() ? null : new Preview(
                level.dimension().location(), request,
                Set.copyOf(interior), Set.copyOf(cavity), Set.copyOf(shell),
                Set.copyOf(occupied),
                MudTerrainGenerationPreviewGeometry.build(
                        interior, Set.of(), shell), gameTime);
        return active;
    }

    public static Preview active(ClientLevel level) {
        if (active == null || level == null
                || !active.dimension.equals(level.dimension().location())) {
            return null;
        }
        return active;
    }

    public static void reset() {
        active = null;
    }

    private static void buildSurface(
            ClientLevel level, MudTerrainGenerationRequest request,
            Set<Long> interior) {
        BlockPos center = request.center();
        MudTerrainGenerationSettings settings = request.depositSettings();
        ResourceLocation sourceFilter = settings.sameSourceOnly()
                ? sourceId(level, center, settings) : null;
        if (settings.sameSourceOnly() && sourceFilter == null) {
            return;
        }
        for (int z = center.getZ() - settings.radius();
                z <= center.getZ() + settings.radius(); z++) {
            for (int x = center.getX() - settings.radius();
                    x <= center.getX() + settings.radius(); x++) {
                if (!MudTerrainDepositShape.contains(
                        center.getX(), center.getZ(), x, z, settings)) {
                    continue;
                }
                int surfaceY = MudTerrainDepositPlanner.findSurfaceY(
                        level, center, x, z, settings);
                if (surfaceY == Integer.MIN_VALUE) {
                    continue;
                }
                int depth = MudTerrainDepositShape.depth(
                        center.getX(), center.getZ(), x, z, settings);
                addConvertibleColumn(level, x, surfaceY, z,
                        depth, sourceFilter, interior);
            }
        }
    }

    private static void buildLake(
            ClientLevel level, MudTerrainGenerationRequest request,
            Set<Long> interior, Set<Long> cavity, Set<Long> shell) {
        MudTerrainLakeShape.Shape shape = lakeShape(request.lakeSettings());
        addLoadedOffsets(level, request, shape.interior(), interior);
        addLoadedOffsets(level, request, shape.cavity(), cavity);
        for (BlockPos offset : shape.shell()) {
            if (MudTerrainLakeShape.includesShell(request.type(), offset)) {
                addLoadedOffset(level, request, offset, shell);
            }
        }
    }

    private static void buildNatural(
            MudTerrainGenerationRequest request, ClientLevel level,
            Set<Long> interior) {
        MudTerrainGenerationSettings settings = request.depositSettings();
        for (Cell cell : NaturalMudDepositShape.buildForWand(
                request.type().naturalForm(), settings.seed(), settings.radius())) {
            int depth = NaturalMudDepositShape.columnDepth(
                    1, settings.thickness(), cell);
            for (int layer = 0; layer < depth; layer++) {
                addLoadedOffset(level, request,
                        new BlockPos(cell.dx(), -layer, cell.dz()), interior);
            }
        }
    }

    private static void addLoadedOffsets(
            ClientLevel level, MudTerrainGenerationRequest request,
            java.util.List<BlockPos> offsets, Set<Long> output) {
        for (BlockPos offset : offsets) {
            addLoadedOffset(level, request, offset, output);
        }
    }

    private static void addLoadedOffset(
            ClientLevel level, MudTerrainGenerationRequest request,
            BlockPos offset, Set<Long> output) {
        BlockPos pos = request.center().offset(
                request.rotation().apply(offset));
        if (level.isInWorldBounds(pos)
                && level.getChunkSource().hasChunk(
                        pos.getX() >> 4, pos.getZ() >> 4)) {
            output.add(pos.asLong());
        }
    }

    private static MudTerrainLakeShape.Shape lakeShape(
            MudTerrainLakeSettings settings) {
        if (!settings.equals(cachedLakeSettings) || cachedLakeShape == null) {
            cachedLakeSettings = settings;
            cachedLakeShape = MudTerrainLakeShape.build(settings);
        }
        return cachedLakeShape;
    }

    private static void addConvertibleColumn(
            ClientLevel level, int x, int surfaceY, int z, int requestedDepth,
            ResourceLocation sourceFilter, Set<Long> output) {
        for (int offset = 0; offset < requestedDepth; offset++) {
            BlockPos pos = new BlockPos(x, surfaceY - offset, z);
            if (!level.isInWorldBounds(pos)) {
                break;
            }
            BlockState state = level.getBlockState(pos);
            BlockState source = state.getBlock() instanceof AdaptiveMudBlock
                    ? AdaptiveMudClientCache.sourceState(level, pos) : state;
            if (source == null || source.isAir()
                    || sourceFilter != null && !sourceFilter.equals(
                            BuiltInRegistries.BLOCK.getKey(source.getBlock()))) {
                break;
            }
            if (state.getBlock() instanceof AdaptiveMudBlock) {
                continue;
            }
            if (!AdaptiveMudEligibility.check(level, pos, state).supported()) {
                break;
            }
            output.add(pos.asLong());
        }
    }

    private static ResourceLocation sourceId(
            ClientLevel level, BlockPos center,
            MudTerrainGenerationSettings settings) {
        BlockPos sourcePos = center;
        if (level.getBlockState(sourcePos).isAir()) {
            int surfaceY = MudTerrainDepositPlanner.findSurfaceY(
                    level, center, center.getX(), center.getZ(), settings);
            if (surfaceY == Integer.MIN_VALUE) {
                return null;
            }
            sourcePos = new BlockPos(center.getX(), surfaceY, center.getZ());
        }
        BlockState state = level.getBlockState(sourcePos);
        BlockState source = state.getBlock() instanceof AdaptiveMudBlock
                ? AdaptiveMudClientCache.sourceState(level, sourcePos) : state;
        return source == null || source.isAir() ? null
                : BuiltInRegistries.BLOCK.getKey(source.getBlock());
    }

    public record Preview(
            ResourceLocation dimension,
            MudTerrainGenerationRequest request,
            Set<Long> interior,
            Set<Long> cavity,
            Set<Long> shell,
            Set<Long> occupied,
            MudTerrainGenerationPreviewGeometry.Geometry geometry,
            long builtAt) {
        public Vec3 coreTarget() {
            return Vec3.atCenterOf(request.center());
        }

        public int cellCount() {
            return interior.size() + cavity.size() + shell.size();
        }
    }
}

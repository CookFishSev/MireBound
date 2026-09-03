package com.fish.mirebound.generation.natural;

import com.fish.mirebound.generation.natural.NaturalMudDepositShape.Cell;
import com.fish.mirebound.generation.natural.NaturalMudGenerationProfile.Rule;
import com.fish.mirebound.generation.MudTerrainGenerationType;
import com.fish.mirebound.generation.MudTerrainLakeSettings;
import com.fish.mirebound.generation.MudTerrainLakeShape;
import com.fish.mirebound.mud.MudBlock;
import com.fish.mirebound.mud.MudBlockVariant;
import com.fish.mirebound.registry.ModBlocks;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Sparse natural terrain feature that emits at most one bounded deposit per chunk. */
public final class NaturalMudDepositFeature
        extends Feature<NoneFeatureConfiguration> {
    private static final int GENERATED_TOP_HEIGHT_PIXELS = 14;
    private static final int MAXIMUM_LAND_HEIGHT_DELTA = 1;
    private static final int MAXIMUM_OTHER_HEIGHT_DELTA = 3;

    public NaturalMudDepositFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(
            FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (context.chunkGenerator() instanceof FlatLevelSource) {
            return false;
        }
        WorldGenLevel level = context.level();
        Level dimensionLevel = level.getLevel();
        if (dimensionLevel.dimension() != Level.OVERWORLD
                && dimensionLevel.dimension() != Level.NETHER
                && dimensionLevel.dimension() != Level.END) {
            return false;
        }

        RandomSource random = context.random();
        int candidateX = context.origin().getX() + random.nextInt(16);
        int candidateZ = context.origin().getZ() + random.nextInt(16);
        int surfaceY = surfaceY(level, candidateX, candidateZ, false);
        int undergroundY = undergroundProbeY(level, surfaceY, random);
        Holder<Biome> surfaceBiome = level.getBiome(
                new BlockPos(candidateX, surfaceY, candidateZ));
        Holder<Biome> undergroundBiome = level.getBiome(
                new BlockPos(candidateX, undergroundY, candidateZ));
        NaturalMudGenerationProfile profile =
                NaturalMudGenerationSettings.active(level.getLevel());
        List<EligibleRule> eligible = eligibleRules(
                profile, dimensionLevel.dimension(), surfaceBiome,
                undergroundBiome);
        EligibleRule selected = selectRule(eligible, random);
        if (selected == null) {
            return false;
        }

        int radius = randomBetween(random,
                selected.rule.minimumRadius(), selected.rule.maximumRadius());
        List<NaturalMudDepositForm> forms = selected.forms;
        int firstForm = random.nextInt(forms.size());
        for (int attempt = 0; attempt < forms.size(); attempt++) {
            NaturalMudDepositForm form = forms.get(
                    (firstForm + attempt) % forms.size());
            Site site = findSite(level, dimensionLevel.dimension(), form,
                    candidateX, undergroundY, candidateZ, radius, random);
            if (site == null) {
                continue;
            }
            int placed = placeShape(level, selected.rule, form, site,
                    radius, random.nextLong());
            if (placed > 0) {
                return true;
            }
        }
        return false;
    }

    private static List<EligibleRule> eligibleRules(
            NaturalMudGenerationProfile profile,
            ResourceKey<Level> dimension,
            Holder<Biome> surfaceBiome,
            Holder<Biome> undergroundBiome) {
        List<EligibleRule> result = new ArrayList<>();
        for (Rule rule : profile.rules()) {
            if (!rule.enabled() || rule.chancePerHundredThousandChunks() <= 0) {
                continue;
            }
            List<NaturalMudDepositForm> matchingForms = new ArrayList<>();
            for (NaturalMudDepositForm form : rule.forms()) {
                Holder<Biome> probe = form.underground()
                        ? undergroundBiome : surfaceBiome;
                if (rule.matches(probe, dimension)) {
                    matchingForms.add(form);
                }
            }
            if (!matchingForms.isEmpty()) {
                result.add(new EligibleRule(rule, List.copyOf(matchingForms)));
            }
        }
        return result;
    }

    private static EligibleRule selectRule(
            List<EligibleRule> eligible, RandomSource random) {
        int totalChance = 0;
        for (EligibleRule candidate : eligible) {
            totalChance += candidate.rule.chancePerHundredThousandChunks();
        }
        if (totalChance <= 0
                || random.nextInt(NaturalMudGenerationProfile.PROBABILITY_SCALE)
                        >= Math.min(NaturalMudGenerationProfile.PROBABILITY_SCALE,
                                totalChance)) {
            return null;
        }
        int selected = random.nextInt(totalChance);
        for (EligibleRule candidate : eligible) {
            selected -= candidate.rule.chancePerHundredThousandChunks();
            if (selected < 0) {
                return candidate;
            }
        }
        return eligible.isEmpty() ? null : eligible.getLast();
    }

    private static Site findSite(
            WorldGenLevel level, ResourceKey<Level> dimension,
            NaturalMudDepositForm form, int x, int probeY, int z,
            int radius, RandomSource random) {
        return switch (form) {
            case RIVERBANK_CRESCENT -> findRiverbank(level, x, z, radius);
            case RIVERBED_RIBBON -> findRiverbed(level, x, z);
            case CAVE_SEEP -> findCaveFloor(level, x, probeY, z, 24);
            case VOLCANIC_FISSURE -> findCaveFloor(level, x,
                    dimension == Level.NETHER ? randomBetween(random, 20, 110) : probeY,
                    z, 32);
            case ORGANIC_NEST -> dimension == Level.NETHER
                    ? findCaveFloor(level, x, randomBetween(random, 20, 110), z, 32)
                    : findSurface(level, x, z);
            case DUNE_BLOWOUT, MARSH_MOSAIC, END_IMPACT_RING ->
                    findSurface(level, x, z);
            case SURFACE_LAKE -> findSurface(level, x, z);
            case UNDERGROUND_LAKE -> findUndergroundLake(
                    level, x, probeY, z, 12);
        };
    }

    private static Site findSurface(WorldGenLevel level, int x, int z) {
        int y = surfaceY(level, x, z, false);
        BlockPos pos = new BlockPos(x, y, z);
        return canReplaceTerrain(level.getBlockState(pos))
                ? new Site(x, y, z, SurfaceMode.LAND) : null;
    }

    private static Site findRiverbank(
            WorldGenLevel level, int centerX, int centerZ, int radius) {
        int search = Math.min(7, radius + 2);
        for (int ring = 0; ring <= search; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    int x = centerX + dx;
                    int z = centerZ + dz;
                    int y = surfaceY(level, x, z, false);
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!canReplaceTerrain(level.getBlockState(pos))
                            || !waterNear(level, x, y, z)) {
                        continue;
                    }
                    return new Site(x, y, z, SurfaceMode.LAND);
                }
            }
        }
        return null;
    }

    private static Site findRiverbed(WorldGenLevel level, int x, int z) {
        int y = surfaceY(level, x, z, true);
        BlockPos pos = new BlockPos(x, y, z);
        if (!canReplaceTerrain(level.getBlockState(pos))) {
            return null;
        }
        for (int offset = 1; offset <= 5; offset++) {
            if (!level.getFluidState(pos.above(offset)).isEmpty()) {
                return new Site(x, y, z, SurfaceMode.UNDERWATER);
            }
        }
        return null;
    }

    private static Site findCaveFloor(
            WorldGenLevel level, int x, int probeY, int z, int range) {
        int maximum = Math.min(level.getMaxBuildHeight() - 3, probeY + range);
        int minimum = Math.max(level.getMinBuildHeight() + 3, probeY - range);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, maximum, z);
        for (int y = maximum; y >= minimum; y--) {
            cursor.setY(y);
            if (!level.getBlockState(cursor).isAir()) {
                continue;
            }
            cursor.setY(y - 1);
            if (canReplaceTerrain(level.getBlockState(cursor))) {
                return new Site(x, y - 1, z, SurfaceMode.CAVE);
            }
        }
        return null;
    }

    private static Site findUndergroundLake(
            WorldGenLevel level, int x, int probeY, int z, int range) {
        int minimum = Math.max(level.getMinBuildHeight() + 8, probeY - range);
        int maximum = Math.min(level.getMaxBuildHeight() - 9, probeY + range);
        for (int distance = 0; distance <= range; distance++) {
            int below = probeY - distance;
            if (below >= minimum && canReplaceTerrain(
                    level.getBlockState(new BlockPos(x, below, z)))) {
                return new Site(x, below, z, SurfaceMode.CAVE);
            }
            int above = probeY + distance;
            if (distance > 0 && above <= maximum && canReplaceTerrain(
                    level.getBlockState(new BlockPos(x, above, z)))) {
                return new Site(x, above, z, SurfaceMode.CAVE);
            }
        }
        return null;
    }

    private static int placeShape(
            WorldGenLevel level, Rule rule, NaturalMudDepositForm form,
            Site site, int radius, long seed) {
        if (form.lake()) {
            return placeLake(level, rule, form, site, radius, seed);
        }
        List<Cell> cells = NaturalMudDepositShape.build(form, seed, radius);
        BlockState full = ModBlocks.blockFor(rule.medium()).defaultBlockState();
        BlockState top = rule.fullHeightTop() ? full
                : full.setValue(MudBlock.VARIANT, MudBlockVariant.HEIGHT)
                        .setValue(MudBlock.HEIGHT, GENERATED_TOP_HEIGHT_PIXELS);
        List<PlacementColumn> columns = collectPlacementColumns(
                level, site, cells);
        if (site.mode == SurfaceMode.LAND
                && columns.size() < minimumLandColumns(form, cells.size())) {
            return 0;
        }
        int placements = 0;
        for (PlacementColumn column : columns) {
            Cell cell = column.cell;
            BlockPos topPos = column.top;
            int depth = NaturalMudDepositShape.columnDepth(rule, cell);
            boolean columnPlaced = false;
            for (int offset = depth - 1; offset >= 1; offset--) {
                BlockPos buried = topPos.below(offset);
                if (!canReplaceForMode(level.getBlockState(buried), site.mode)) {
                    continue;
                }
                columnPlaced |= level.setBlock(buried, full, 2);
            }
            columnPlaced |= level.setBlock(topPos, top, 2);
            if (columnPlaced) {
                placements++;
            }
        }
        return placements;
    }

    private static int placeLake(
            WorldGenLevel level, Rule rule, NaturalMudDepositForm form,
            Site site, int radius, long seed) {
        MudTerrainLakeSettings settings =
                NaturalMudDepositShape.lakeSettings(seed, radius);
        MudTerrainLakeShape.Shape shape = MudTerrainLakeShape.build(settings);
        MudTerrainGenerationType type = form == NaturalMudDepositForm.SURFACE_LAKE
                ? MudTerrainGenerationType.LAKE_SURFACE
                : MudTerrainGenerationType.LAKE_POOL;
        if (!validLakeSite(level, site, shape, type)) {
            return 0;
        }

        BlockState full = ModBlocks.blockFor(rule.medium()).defaultBlockState();
        BlockState top = rule.fullHeightTop() ? full
                : full.setValue(MudBlock.VARIANT, MudBlockVariant.HEIGHT)
                        .setValue(MudBlock.HEIGHT, GENERATED_TOP_HEIGHT_PIXELS);
        Set<Long> surfaces = MudTerrainLakeShape.surfaceInterior(shape);
        int placements = 0;
        for (BlockPos offset : shape.cavity()) {
            BlockPos pos = new BlockPos(site.x, site.y, site.z).offset(offset);
            if (!level.getBlockState(pos).isAir()
                    && level.setBlock(pos, Blocks.CAVE_AIR.defaultBlockState(), 2)) {
                placements++;
            }
        }
        for (BlockPos offset : shape.interior()) {
            BlockPos pos = new BlockPos(site.x, site.y, site.z).offset(offset);
            BlockState replacement = surfaces.contains(offset.asLong()) ? top : full;
            if (level.setBlock(pos, replacement, 2)) {
                placements++;
            }
        }
        return placements;
    }

    private static boolean validLakeSite(
            WorldGenLevel level, Site site, MudTerrainLakeShape.Shape shape,
            MudTerrainGenerationType type) {
        BlockPos center = new BlockPos(site.x, site.y, site.z);
        for (BlockPos offset : shape.interior()) {
            if (!validLakeTerrain(level, center.offset(offset))) {
                return false;
            }
        }
        for (BlockPos offset : shape.cavity()) {
            BlockPos pos = center.offset(offset);
            if (type == MudTerrainGenerationType.LAKE_POOL) {
                if (!validLakeTerrain(level, pos)) {
                    return false;
                }
            } else if (!validSurfaceCavity(level, pos)) {
                return false;
            }
        }
        for (BlockPos offset : shape.shell()) {
            if (MudTerrainLakeShape.includesShell(type, offset)
                    && !validLakeTerrain(level, center.offset(offset))) {
                return false;
            }
        }
        return true;
    }

    private static boolean validLakeTerrain(
            WorldGenLevel level, BlockPos pos) {
        if (!validLakePosition(level, pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return !state.hasBlockEntity() && level.getBlockEntity(pos) == null
                && state.getFluidState().isEmpty()
                && canReplaceTerrain(state);
    }

    private static boolean validSurfaceCavity(
            WorldGenLevel level, BlockPos pos) {
        if (!validLakePosition(level, pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return !state.hasBlockEntity() && level.getBlockEntity(pos) == null
                && state.getFluidState().isEmpty()
                && (state.isAir() || state.canBeReplaced());
    }

    private static boolean validLakePosition(
            WorldGenLevel level, BlockPos pos) {
        return level.getLevel().isInWorldBounds(pos)
                && level.getLevel().getWorldBorder().isWithinBounds(pos)
                && chunkAvailable(level, pos.getX(), pos.getZ());
    }

    private static List<PlacementColumn> collectPlacementColumns(
            WorldGenLevel level, Site site, List<Cell> cells) {
        List<PlacementColumn> columns = new ArrayList<>(cells.size());
        int maximumHeightDelta = site.mode == SurfaceMode.LAND
                ? MAXIMUM_LAND_HEIGHT_DELTA : MAXIMUM_OTHER_HEIGHT_DELTA;
        for (Cell cell : cells) {
            int x = site.x + cell.dx();
            int z = site.z + cell.dz();
            if (!chunkAvailable(level, x, z)) {
                continue;
            }
            Integer y = resolveColumnY(level, site, x, z);
            if (y == null || Math.abs(y - site.y) > maximumHeightDelta
                    || site.mode == SurfaceMode.LAND
                            && !isGentleLandColumn(level, site.y, x, y, z)) {
                continue;
            }
            BlockPos top = new BlockPos(x, y, z);
            if (canPlaceColumn(level, top, site.mode)) {
                columns.add(new PlacementColumn(cell, top));
            }
        }
        return columns;
    }

    private static int minimumLandColumns(
            NaturalMudDepositForm form, int cellCount) {
        double requiredShare = form == NaturalMudDepositForm.RIVERBANK_CRESCENT
                ? 0.45D : 0.68D;
        return Math.max(4, Mth.ceil(cellCount * requiredShare));
    }

    private static boolean isGentleLandColumn(
            WorldGenLevel level, int siteY, int x, int y, int z) {
        if (!chunkAvailable(level, x - 1, z)
                || !chunkAvailable(level, x + 1, z)
                || !chunkAvailable(level, x, z - 1)
                || !chunkAvailable(level, x, z + 1)) {
            return false;
        }
        return NaturalMudDepositShape.acceptsLandHeights(siteY, y,
                surfaceY(level, x - 1, z, false),
                surfaceY(level, x + 1, z, false),
                surfaceY(level, x, z - 1, false),
                surfaceY(level, x, z + 1, false));
    }

    private static Integer resolveColumnY(
            WorldGenLevel level, Site site, int x, int z) {
        return switch (site.mode) {
            case LAND -> surfaceY(level, x, z, false);
            case UNDERWATER -> surfaceY(level, x, z, true);
            case CAVE -> {
                Site floor = findCaveFloor(level, x, site.y + 1, z, 5);
                yield floor == null ? null : floor.y;
            }
        };
    }

    private static boolean canPlaceColumn(
            WorldGenLevel level, BlockPos top, SurfaceMode mode) {
        BlockState state = level.getBlockState(top);
        if (!canReplaceForMode(state, mode) || state.hasBlockEntity()) {
            return false;
        }
        BlockState above = level.getBlockState(top.above());
        return above.isAir() || !above.getFluidState().isEmpty()
                || above.getCollisionShape(level, top.above()).isEmpty();
    }

    private static boolean canReplaceForMode(
            BlockState state, SurfaceMode mode) {
        return canReplaceTerrain(state);
    }

    private static boolean canReplaceTerrain(BlockState state) {
        return state.is(BlockTags.DIRT)
                || state.is(BlockTags.SAND)
                || state.is(BlockTags.TERRACOTTA)
                || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.BASE_STONE_NETHER)
                || state.is(BlockTags.NYLIUM)
                || state.is(Blocks.END_STONE)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.CLAY)
                || state.is(Blocks.MUD)
                || state.is(Blocks.SOUL_SAND)
                || state.is(Blocks.SOUL_SOIL);
    }

    private static boolean waterNear(
            WorldGenLevel level, int x, int y, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (Math.abs(dx) + Math.abs(dz) != 1) {
                    continue;
                }
                BlockPos.MutableBlockPos cursor =
                        new BlockPos.MutableBlockPos(x + dx, y + 2, z + dz);
                for (int offset = 2; offset >= -2; offset--) {
                    cursor.setY(y + offset);
                    if (!level.getFluidState(cursor).isEmpty()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static int surfaceY(
            WorldGenLevel level, int x, int z, boolean oceanFloor) {
        if (!chunkAvailable(level, x, z)) {
            return level.getMinBuildHeight();
        }
        return level.getHeight(oceanFloor
                        ? Heightmap.Types.OCEAN_FLOOR_WG
                        : Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
    }

    private static int undergroundProbeY(
            WorldGenLevel level, int surfaceY, RandomSource random) {
        int minimum = level.getMinBuildHeight() + 12;
        int maximum = Math.max(minimum,
                Math.min(surfaceY - 8, level.getMaxBuildHeight() - 16));
        return randomBetween(random, minimum, maximum);
    }

    private static boolean chunkAvailable(
            WorldGenLevel level, int x, int z) {
        return level.hasChunk(SectionPos.blockToSectionCoord(x),
                SectionPos.blockToSectionCoord(z));
    }

    private static int randomBetween(
            RandomSource random, int minimum, int maximum) {
        return maximum <= minimum ? minimum
                : minimum + random.nextInt(maximum - minimum + 1);
    }

    private record EligibleRule(Rule rule, List<NaturalMudDepositForm> forms) {
    }

    private record PlacementColumn(Cell cell, BlockPos top) {
    }

    private record Site(int x, int y, int z, SurfaceMode mode) {
    }

    private enum SurfaceMode {
        LAND,
        UNDERWATER,
        CAVE
    }
}

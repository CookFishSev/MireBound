package com.fish.mirebound.generation.natural;

import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.mud.MudBlock;
import com.fish.mirebound.mud.MudBlockVariant;
import com.fish.mirebound.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** Writes only the owning chunk; one bounded terrain/tree snapshot per feature call. */
final class NaturalMudCoveragePlacement {
    private NaturalMudCoveragePlacement() {}

    static boolean place(WorldGenLevel level, BlockPos origin, NaturalMudGenerationProfile profile) {
        int baseX = Math.floorDiv(origin.getX(), 16) * 16;
        int baseZ = Math.floorDiv(origin.getZ(), 16) * 16;
        long seed = level.getSeed() ^ level.getLevel().dimension().location().hashCode();
        Map<String, boolean[]> coverage = new HashMap<>();
        for (var section : level.getChunk(baseX >> 4, baseZ >> 4).getSections()) {
            section.getBiomes().getAll(biome -> biome.unwrapKey().ifPresent(key -> {
                String id = key.location().toString();
                var rule = profile.coverageRules().get(id);
                if (rule != null && rule.enabled() && rule.generationChance() > 0
                        && rule.coverage() > 0 && !rule.weights().isEmpty())
                    coverage.computeIfAbsent(id, ignored -> new boolean[256]);
            }));
        }
        coverage.entrySet().removeIf(entry -> {
            var rule = profile.coverageRules().get(entry.getKey());
            boolean any = false;
            for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
                boolean covered = NaturalMudCoveragePattern.covered(rule, seed, baseX + dx, baseZ + dz);
                entry.getValue()[dx * 16 + dz] = covered;
                any |= covered;
            }
            return !any;
        });
        if (coverage.isEmpty()) return false;
        int border = 0;
        for (String biome : coverage.keySet()) {
            var rule = profile.coverageRules().get(biome);
            if (rule.deeperNearTrees()) border = Math.max(border, rule.treeRadius());
        }
        int width = 16 + border * 2;
        int[] heights = new int[width * width];
        boolean[] trunks = new boolean[width * width];
        List<BlockPos> trees = new ArrayList<>();
        Set<String> surfaceBiomes = new HashSet<>();
        boolean ceiling = level.getLevel().dimensionType().hasCeiling();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int ix = 0; ix < width; ix++) for (int iz = 0; iz < width; iz++) {
            int x = baseX + ix - border, z = baseZ + iz - border;
            int index = ix * width + iz;
            heights[index] = level.getMinBuildHeight();
            if (!level.hasChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16))) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            // Feature-stage heightmaps include newly placed trees; the _WG map is stale here.
            if (!ceiling) y = Math.min(y, level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 1);
            boolean open = !ceiling;
            int maximumScan = ceiling ? Math.min(256, level.getLevel().dimensionType().logicalHeight()) : 40;
            if (ceiling) y = Math.min(y, level.getMinBuildHeight() + maximumScan - 8);
            for (int step = 0; step < maximumScan && y > level.getMinBuildHeight(); step++, y--) {
                BlockState state = level.getBlockState(cursor.set(x, y, z));
                if (!open) {
                    open = state.isAir() || !state.getFluidState().isEmpty();
                    continue;
                }
                if (state.is(BlockTags.LOGS)) { trunks[index] = true; continue; }
                if (state.isAir() || state.is(BlockTags.LEAVES) || state.canBeReplaced()
                        || !state.getFluidState().isEmpty()) continue;
                heights[index] = y;
                if (trunks[index] && border > 0) trees.add(new BlockPos(x, y, z));
                break;
            }
        }
        boolean placed = false;
        for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
            int index = (dx + border) * width + dz + border;
            int x = baseX + dx, z = baseZ + dz, y = heights[index];
            if (y <= level.getMinBuildHeight() || trunks[index]) continue;
            BlockPos top = new BlockPos(x, y, z);
            String biome = level.getBiome(top).unwrapKey().map(key -> key.location().toString()).orElse("");
            surfaceBiomes.add(biome);
            NaturalMudCoverageRule rule = profile.coverageRules().get(biome);
            boolean[] mask = coverage.get(biome);
            if (mask == null || !mask[dx * 16 + dz]) continue;
            double distanceSquared = Double.POSITIVE_INFINITY;
            if (rule.deeperNearTrees()) for (BlockPos tree : trees) {
                if (Math.abs(tree.getY() - y) <= 3) {
                    int treeX = tree.getX() - x, treeZ = tree.getZ() - z;
                    distanceSquared = Math.min(distanceSquared, treeX * treeX + treeZ * treeZ);
                }
            }
            placed |= placeColumn(level, top, rule, seed, Math.sqrt(distanceSquared));
        }
        placed |= placeCaveBiomes(level, baseX, baseZ, heights, border, surfaceBiomes, profile, coverage, seed);
        return placed;
    }

    private static boolean placeColumn(WorldGenLevel level, BlockPos top,
            NaturalMudCoverageRule rule, long seed, double treeDistance) {
        double sinkingDepth = NaturalMudCoveragePattern.depth(rule, seed, top.getX(), top.getZ(), treeDistance);
        int depth = rule.replacementLayers();
        SinkingMedium medium = NaturalMudCoveragePattern.medium(rule, seed, top.getX(), top.getZ());
        if (medium == null) return false;
        BlockState mud = ModBlocks.blockFor(medium).defaultBlockState()
                .setValue(MudBlock.VARIANT, MudBlockVariant.NATURAL_DEPTH);
        int valid = 0;
        while (valid < depth && top.getY() - valid > level.getMinBuildHeight()
                && replaceable(level.getBlockState(top.below(valid)))) valid++;
        NaturalMudColumnPlan plan = NaturalMudColumnPlan.create(depth, valid, sinkingDepth);
        boolean placed = false;
        for (int offset = plan.layerCount() - 1; offset >= 0; offset--) {
            placed |= level.setBlock(top.below(offset), mud
                    .setValue(MudBlock.VARIANT, plan.terminalLayer(offset)
                            ? MudBlockVariant.NATURAL_DEPTH_END : MudBlockVariant.NATURAL_DEPTH)
                    .setValue(MudBlock.HEIGHT, plan.depthPixels(offset)), 2);
        }
        return placed;
    }

    private static boolean placeCaveBiomes(WorldGenLevel level, int baseX, int baseZ, int[] heights, int border,
            Set<String> surfaceBiomes, NaturalMudGenerationProfile profile,
            Map<String, boolean[]> coverage, long seed) {
        Set<String> underground = new HashSet<>(coverage.keySet());
        underground.removeAll(surfaceBiomes);
        if (underground.isEmpty()) return false;
        var chunk = level.getChunk(baseX >> 4, baseZ >> 4);
        int width = 16 + border * 2;
        var sections = chunk.getSections();
        int[] columnCounts = new int[256];
        boolean placed = false;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int section = sections.length - 1; section >= 0; section--) {
            if (!sections[section].getBiomes().maybeHas(biome -> biome.unwrapKey()
                    .map(key -> underground.contains(key.location().toString())).orElse(false))) continue;
            int sectionY = (chunk.getMinSection() + section) * 16;
            for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
                int column = dx * 16 + dz;
                int maxY = Math.min(sectionY + 15, heights[(dx + border) * width + dz + border] - 2);
                int x = baseX + dx, z = baseZ + dz;
                if (maxY < sectionY || columnCounts[column] >= 4) continue;
                boolean open = level.getBlockState(cursor.set(x, maxY + 1, z)).isAir();
                for (int y = maxY; y >= sectionY && columnCounts[column] < 4; y--) {
                    BlockState state = level.getBlockState(cursor.set(x, y, z));
                    if (open && replaceable(state)) {
                        String biome = level.getBiome(cursor).unwrapKey().map(key -> key.location().toString()).orElse("");
                        var rule = underground.contains(biome) ? profile.coverageRules().get(biome) : null;
                        if (rule != null && coverage.get(biome)[column]) {
                            placed |= placeColumn(level, cursor.immutable(), rule, seed, Double.POSITIVE_INFINITY);
                            columnCounts[column]++;
                        }
                    }
                    open = state.isAir();
                }
            }
        }
        return placed;
    }

    private static boolean replaceable(BlockState state) {
        return !state.hasBlockEntity() && state.getFluidState().isEmpty()
                && (state.is(BlockTags.DIRT) || state.is(BlockTags.SAND)
                || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.BASE_STONE_NETHER)
                || state.is(net.minecraft.world.level.block.Blocks.GRAVEL)
                || state.is(net.minecraft.world.level.block.Blocks.CLAY)
                || state.is(net.minecraft.world.level.block.Blocks.END_STONE)
                || state.is(net.minecraft.world.level.block.Blocks.SOUL_SAND)
                || state.is(net.minecraft.world.level.block.Blocks.SOUL_SOIL));
    }
}

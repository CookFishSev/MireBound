package com.fish.mirebound.mud.tuning;

import com.fish.mirebound.adaptive.AdaptiveMudBehaviorSettings;
import com.fish.mirebound.adaptive.AdaptiveMudBlock;
import com.fish.mirebound.adaptive.AdaptiveMudEligibility;
import com.fish.mirebound.adaptive.AdaptiveMudSourceStore;
import com.fish.mirebound.mud.MudBlock;
import com.fish.mirebound.mud.MudBlockProfileStore;
import com.fish.mirebound.mud.MudBlockVariant;
import com.fish.mirebound.mud.MudPhysicsParameter;
import com.fish.mirebound.mud.MudPhysicsSettings;
import com.fish.mirebound.mud.MudShapeProfile;
import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.network.payload.MudTuningSelectionPayload;
import com.fish.mirebound.network.payload.MudTuningSessionPayload;
import com.fish.mirebound.registry.ModBlocks;
import com.fish.mirebound.stain.MudFootprintBlock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** One bounded selection pass that produces all tuning object groups. */
public final class MudTuningObjectScanner {
    private MudTuningObjectScanner() {
    }

    public static ScanResult scan(ServerLevel level, BlockPos minimum, BlockPos maximum,
            boolean sableScope) {
        return scan(level, minimum, maximum, sableScope, 0, true, null, false);
    }

    public static ScanResult scan(ServerLevel level, BlockPos minimum, BlockPos maximum,
            boolean sableScope, boolean forceAllBlocks) {
        return scan(level, minimum, maximum, sableScope,
                0, true, null, forceAllBlocks);
    }

    public static ScanResult scan(ServerLevel level, BlockPos minimum, BlockPos maximum,
            boolean sableScope, int incompatiblePositionLimit) {
        return scan(level, minimum, maximum, sableScope,
                incompatiblePositionLimit, true, null, false);
    }

    public static ScanResult scan(ServerLevel level, BlockPos minimum, BlockPos maximum,
            boolean sableScope, int incompatiblePositionLimit, boolean forceAllBlocks) {
        return scan(level, minimum, maximum, sableScope,
                incompatiblePositionLimit, true, null, forceAllBlocks);
    }

    public static ScanResult summarize(ServerLevel level, BlockPos minimum, BlockPos maximum,
            boolean sableScope, int incompatiblePositionLimit) {
        return summarize(level, minimum, maximum, sableScope,
                incompatiblePositionLimit, null);
    }

    public static ScanResult summarize(ServerLevel level, BlockPos minimum, BlockPos maximum,
            boolean sableScope, int incompatiblePositionLimit, BlockPos priorityCenter) {
        return scan(level, minimum, maximum, sableScope,
                incompatiblePositionLimit, false, priorityCenter, false);
    }

    public static ScanResult summarize(ServerLevel level, BlockPos minimum, BlockPos maximum,
            boolean sableScope, int incompatiblePositionLimit, BlockPos priorityCenter,
            boolean forceAllBlocks) {
        return scan(level, minimum, maximum, sableScope,
                incompatiblePositionLimit, false, priorityCenter, forceAllBlocks);
    }

    private static ScanResult scan(ServerLevel level, BlockPos minimum, BlockPos maximum,
            boolean sableScope, int incompatiblePositionLimit, boolean collectGroups,
            BlockPos priorityCenter, boolean forceAllBlocks) {
        Map<MudTuningObjectId, Accumulator> groups = collectGroups
                ? new LinkedHashMap<>() : null;
        MudBlockProfileStore profiles = collectGroups ? MudBlockProfileStore.get(level) : null;
        AdaptiveMudSourceStore sources = collectGroups ? AdaptiveMudSourceStore.get(level) : null;
        double[] adaptiveBaseline = collectGroups
                ? AdaptiveMudBehaviorSettings.get(level).values() : null;
        int convertible = 0;
        int adaptiveCount = 0;
        int mud = 0;
        int unsupported = 0;
        int unloaded = 0;
        int verticalSize = maximum.getY() - minimum.getY() + 1;
        MudTuningHighlightGeometry.NearestPositions incompatible = incompatiblePositionLimit > 0
                ? new MudTuningHighlightGeometry.NearestPositions(
                        incompatiblePositionLimit, priorityCenter)
                : null;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minimum.getX(); x <= maximum.getX(); x++) {
            for (int z = minimum.getZ(); z <= maximum.getZ(); z++) {
                if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) {
                    unloaded += verticalSize;
                    continue;
                }
                for (int y = minimum.getY(); y <= maximum.getY(); y++) {
                    pos.set(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.getBlock() instanceof AdaptiveMudBlock adaptive) {
                        adaptiveCount++;
                        if (!collectGroups) {
                            continue;
                        }
                        BlockState source = sources.sourceState(pos);
                        if (source == null) {
                            continue;
                        }
                        ResourceLocation sourceId = BuiltInRegistries.BLOCK.getKey(source.getBlock());
                        MudTuningObjectId id = MudTuningObjectId.convertedBlock(sourceId);
                        SinkingMedium storedMedium = adaptive.medium();
                        MudBlockProfileStore.Profile local = profiles.profile(level, pos, storedMedium);
                        boolean legacy = storedMedium != SinkingMedium.MUD;
                        double[] values = local != null
                                ? local.values()
                                : legacy
                                        ? MudPhysicsSettings.values(storedMedium)
                                        : adaptiveBaseline;
                        groups.computeIfAbsent(id, ignored -> new Accumulator(
                                id, adaptiveBaseline, capabilities(id, sableScope, null)))
                                .offer(pos, source, state, storedMedium, values,
                                        local != null || legacy, false);
                        continue;
                    }
                    if (state.getBlock() instanceof MudBlock mudBlock) {
                        mud++;
                        if (!collectGroups) {
                            continue;
                        }
                        SinkingMedium medium = mudBlock.medium();
                        MudTuningObjectId id = MudTuningObjectId.nativeMedium(medium);
                        MudBlockProfileStore.Profile local = profiles.profile(level, pos, medium);
                        double[] baseline = MudPhysicsSettings.values(medium);
                        double[] values = local == null ? baseline : local.values();
                        profiles.trackShapeState(level, pos, state);
                        groups.computeIfAbsent(id, ignored -> new Accumulator(
                                id, baseline, capabilities(id, sableScope, medium)))
                                .offer(pos, state, state, medium, values, local != null, true);
                        continue;
                    }
                    AdaptiveMudEligibility.Result eligibility =
                            AdaptiveMudEligibility.check(level, pos, state);
                    if (AdaptiveMudEligibility.canConvert(eligibility, forceAllBlocks)) {
                        convertible++;
                        if (collectGroups) {
                            ResourceLocation sourceId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                            MudTuningObjectId id = MudTuningObjectId.sourceBlock(sourceId);
                            groups.computeIfAbsent(id, ignored -> new Accumulator(
                                    id, adaptiveBaseline, capabilities(id, sableScope, null)))
                                    .offer(pos, state, state, SinkingMedium.MUD,
                                            adaptiveBaseline, false, false);
                        }
                    } else if (!isIgnoredState(state)) {
                        unsupported++;
                        if (incompatible != null) {
                            incompatible.offer(pos);
                        }
                        if (collectGroups) {
                            ResourceLocation sourceId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                            MudTuningObjectId id = MudTuningObjectId.incompatibleBlock(sourceId);
                            groups.computeIfAbsent(id, ignored -> new Accumulator(
                                    id, adaptiveBaseline, capabilities(id, sableScope, null)))
                                    .offer(pos, state, state, SinkingMedium.MUD,
                                            adaptiveBaseline, false, false);
                        }
                    }
                }
            }
        }
        List<ObjectGroup> result = collectGroups
                ? groups.values().stream()
                        .map(Accumulator::finish)
                        .sorted(Comparator.comparingInt(
                                        (ObjectGroup group) -> group.id.kind().ordinal())
                                .thenComparing(group -> group.id.sourceBlockId().toString())
                                .thenComparingInt(group -> group.id.mediumId()))
                        .limit(MudTuningSessionPayload.MAX_OBJECTS)
                        .toList()
                : List.of();
        long[] incompatiblePositions = incompatible == null
                ? new long[0] : incompatible.finish();
        return new ScanResult(result, new MudTuningSelectionPayload.SelectionSummary(
                volume(minimum, maximum), convertible, adaptiveCount, mud, unsupported, unloaded),
                incompatiblePositions);
    }

    private static long volume(BlockPos minimum, BlockPos maximum) {
        long x = (long) maximum.getX() - minimum.getX() + 1L;
        long y = (long) maximum.getY() - minimum.getY() + 1L;
        long z = (long) maximum.getZ() - minimum.getZ() + 1L;
        if (x <= 0L || y <= 0L || z <= 0L || x > Long.MAX_VALUE / y) {
            return Long.MAX_VALUE;
        }
        long xy = x * y;
        return xy > Long.MAX_VALUE / z ? Long.MAX_VALUE : xy * z;
    }

    static boolean isIgnoredState(BlockState state) {
        return isIgnoredState(
                state.isAir(), state.getBlock() instanceof MudFootprintBlock);
    }

    static boolean isIgnoredState(boolean air, boolean stainContainer) {
        return air || stainContainer;
    }

    public static ObjectGroup worldNative(SinkingMedium medium) {
        MudTuningObjectId id = MudTuningObjectId.nativeMedium(medium);
        double[] values = MudPhysicsSettings.values(medium);
        return ObjectGroup.world(id, Block.getId(ModBlocks.blockFor(medium).defaultBlockState()),
                capabilities(id, false, medium), values,
                com.fish.mirebound.mud.MudPhysicsProfiles.defaultValues(medium));
    }

    public static ObjectGroup worldAdaptive(ServerLevel level) {
        MudTuningObjectId id = MudTuningObjectId.adaptiveDefault();
        double[] values = AdaptiveMudBehaviorSettings.get(level).values();
        return ObjectGroup.world(id,
                Block.getId(ModBlocks.adaptiveBlockFor(SinkingMedium.MUD).defaultBlockState()),
                capabilities(id, false, null), values, AdaptiveMudBehaviorSettings.defaults());
    }

    public static ObjectGroup worldTentacle() {
        MudTuningObjectId id = MudTuningObjectId.tentacle();
        double[] values = MudPhysicsSettings.tentacleValues();
        return ObjectGroup.world(id,
                Block.getId(ModBlocks.blockFor(SinkingMedium.MUD).defaultBlockState()),
                capabilities(id, false, null), values,
                com.fish.mirebound.mud.MudPhysicsProfiles.tentacleDefaultValues());
    }

    /** Pure capability projection used by the scanner and protocol tests. */
    public static MudTuningCapabilities capabilitiesFor(
            MudTuningObjectId id, boolean sableScope, SinkingMedium medium) {
        return new MudTuningCapabilities(capabilities(id, sableScope, medium));
    }

    private static int capabilities(MudTuningObjectId id, boolean sableScope, SinkingMedium medium) {
        int bits = sableScope ? MudTuningCapabilities.SABLE_SCOPE : 0;
        switch (id.kind()) {
            case NATIVE_MEDIUM -> {
                bits |= MudTuningCapabilities.EDIT_PARAMETERS
                        | MudTuningCapabilities.EDIT_SHAPE;
                if (!sableScope) {
                    bits |= MudTuningCapabilities.FINITE_FLOW;
                }
                if (medium == SinkingMedium.LIVING_SLIME) {
                    bits |= MudTuningCapabilities.LIVING_SLIME;
                }
            }
            case SOURCE_BLOCK -> bits |= MudTuningCapabilities.CONVERT
                    | MudTuningCapabilities.SOURCE_APPEARANCE;
            case INCOMPATIBLE_BLOCK -> {
            }
            case CONVERTED_BLOCK -> {
                bits |= MudTuningCapabilities.EDIT_PARAMETERS
                        | MudTuningCapabilities.RESTORE
                        | MudTuningCapabilities.SOURCE_APPEARANCE
                        | MudTuningCapabilities.HARVEST_OVERRIDE;
            }
            case ADAPTIVE_DEFAULT -> bits |= MudTuningCapabilities.EDIT_PARAMETERS
                    | MudTuningCapabilities.HARVEST_OVERRIDE;
            case TENTACLE -> bits |= MudTuningCapabilities.EDIT_PARAMETERS;
        }
        return bits;
    }

    public record ScanResult(List<ObjectGroup> groups,
            MudTuningSelectionPayload.SelectionSummary summary,
            long[] incompatiblePositions) {
        public ObjectGroup group(MudTuningObjectId id) {
            for (ObjectGroup group : groups) {
                if (group.id.equals(id)) {
                    return group;
                }
            }
            return null;
        }
    }

    public record ObjectGroup(MudTuningObjectId id, List<BlockPos> positions,
            MudTuningSessionPayload.MediumProfile profile) {
        private static ObjectGroup world(MudTuningObjectId id, int representativeStateId,
                int capabilities, double[] values, double[] resetValues) {
            return new ObjectGroup(id, List.of(), new MudTuningSessionPayload.MediumProfile(
                    id, 0, false, false, MudBlockVariant.DEFAULT.ordinal(), 16, false,
                    representativeStateId, capabilities,
                    Arrays.copyOf(values, values.length),
                    Arrays.copyOf(resetValues, resetValues.length)));
        }
    }

    private static final class Accumulator {
        private final MudTuningObjectId id;
        private final double[] baseline;
        private final int capabilities;
        private final List<BlockPos> positions = new ArrayList<>();
        private double[] values;
        private boolean[] mixed;
        private int localCount;
        private int representativeStateId;
        private int variant;
        private int height;
        private boolean shapeMixed;

        private Accumulator(MudTuningObjectId id, double[] baseline, int capabilities) {
            this.id = id;
            this.baseline = Arrays.copyOf(baseline, baseline.length);
            this.capabilities = capabilities;
        }

        private void offer(BlockPos pos, BlockState representative, BlockState mudState,
                SinkingMedium medium, double[] offered, boolean local, boolean shape) {
            positions.add(pos.immutable());
            int offeredVariant = MudBlockVariant.DEFAULT.ordinal();
            int offeredHeight = 16;
            if (shape) {
                MudBlockVariant actual = MudBlock.variant(mudState);
                offeredVariant = (actual == MudBlockVariant.SPECIAL
                        ? MudBlockVariant.HEIGHT : actual).ordinal();
                offeredHeight = actual == MudBlockVariant.SPECIAL
                        ? MudShapeProfile.special(medium).heightPixels()
                        : MudBlock.storedHeight(mudState);
            }
            if (values == null) {
                values = Arrays.copyOf(offered, MudPhysicsParameter.COUNT);
                mixed = new boolean[MudPhysicsParameter.COUNT];
                representativeStateId = Block.getId(representative);
                variant = offeredVariant;
                height = offeredHeight;
            } else {
                for (MudPhysicsParameter parameter : MudPhysicsParameter.values()) {
                    int index = parameter.ordinal();
                    mixed[index] |= !parameter.displayEquivalent(values[index], offered[index]);
                }
                shapeMixed |= shape && (variant != offeredVariant || height != offeredHeight);
            }
            if (local) {
                localCount++;
            }
        }

        private ObjectGroup finish() {
            double[] displayed = Arrays.copyOf(values, values.length);
            for (MudPhysicsParameter parameter : MudPhysicsParameter.values()) {
                if (mixed[parameter.ordinal()]) {
                    displayed[parameter.ordinal()] = baseline[parameter.ordinal()];
                }
            }
            MudTuningSessionPayload.MediumProfile profile =
                    new MudTuningSessionPayload.MediumProfile(
                            id, positions.size(), localCount > 0,
                            localCount == positions.size(),
                            shapeMixed ? MudBlockVariant.DEFAULT.ordinal() : variant,
                            shapeMixed ? 16 : height, shapeMixed,
                            representativeStateId, capabilities,
                            displayed, Arrays.copyOf(baseline, baseline.length));
            return new ObjectGroup(id, List.copyOf(positions), profile);
        }
    }
}

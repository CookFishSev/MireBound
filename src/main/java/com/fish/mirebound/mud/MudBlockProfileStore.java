package com.fish.mirebound.mud;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.adaptive.AdaptiveMudBehaviorSettings;
import com.fish.mirebound.adaptive.AdaptiveMudBlock;
import com.fish.mirebound.assimilation.AssimilationProfile;
import com.fish.mirebound.eruption.MudEruptionProfile;
import com.fish.mirebound.itemphysics.DroppedItemPhysicsProfile;
import com.fish.mirebound.mud.flow.MudBlockMotionMode;
import com.fish.mirebound.mud.flow.MudFlowProfile;
import com.fish.mirebound.mud.harvest.MudHarvestProfile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

/** Persistent, per-dimension profile snapshots for individually tuned mud blocks. */
public final class MudBlockProfileStore extends SavedData {
    private static final String DATA_NAME = "mirebound_mud_block_profiles";
    private static final int DATA_VERSION = 10;
    private static final int MAX_PERSISTED_PROFILES = 262_144;
    private static final int MAX_PERSISTED_SHAPE_MARKERS = 262_144;
    private static final Factory<MudBlockProfileStore> FACTORY =
            new Factory<>(MudBlockProfileStore::new, MudBlockProfileStore::load);

    private final Map<Long, Profile> profiles = new HashMap<>();
    private final Map<Long, Set<Long>> profilesByChunk = new HashMap<>();
    private final Map<Long, List<Long>> eruptionProfilesByChunk = new HashMap<>();
    private final Set<Long> shapeModified = new HashSet<>();
    private final Map<Long, Set<Long>> shapesByChunk = new HashMap<>();

    public static MudBlockProfileStore get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    private static MudBlockProfileStore load(CompoundTag tag, HolderLookup.Provider registries) {
        MudBlockProfileStore store = new MudBlockProfileStore();
        int version = tag.getInt("Version");
        ListTag entries = tag.getList("Entries", Tag.TAG_COMPOUND);
        int entryCount = Math.min(MAX_PERSISTED_PROFILES, entries.size());
        if (entries.size() > entryCount) {
            Mirebound.LOGGER.warn("Ignoring {} mud block profiles beyond the limit of {}",
                    entries.size() - entryCount, MAX_PERSISTED_PROFILES);
        }
        for (int index = 0; index < entryCount; index++) {
            CompoundTag entry = entries.getCompound(index);
            int mediumId = entry.getInt("Medium");
            long[] packed = entry.getLongArray("Values");
            if (mediumId < 0 || mediumId >= SinkingMedium.COUNT
                    || packed.length == 0 || packed.length > MudPhysicsParameter.COUNT) {
                continue;
            }
            SinkingMedium medium = SinkingMedium.byId(mediumId);
            double[] values = MudPhysicsProfiles.defaultValues(medium);
            for (int valueIndex = 0; valueIndex < packed.length; valueIndex++) {
                values[valueIndex] = Double.longBitsToDouble(packed[valueIndex]);
            }
            migrateLoadedValues(version, medium, values);
            long pos = entry.getLong("Pos");
            Profile profile = loadProfile(version, entry, medium, values);
            store.profiles.put(pos, profile);
            store.addToChunkIndex(pos);
            store.updateEruptionIndex(pos, null, profile);
        }
        long[] savedShapes = tag.getLongArray("ShapeModified");
        int shapeCount = Math.min(MAX_PERSISTED_SHAPE_MARKERS, savedShapes.length);
        if (savedShapes.length > shapeCount) {
            Mirebound.LOGGER.warn("Ignoring {} mud shape markers beyond the limit of {}",
                    savedShapes.length - shapeCount, MAX_PERSISTED_SHAPE_MARKERS);
        }
        for (int index = 0; index < shapeCount; index++) {
            long pos = savedShapes[index];
            store.shapeModified.add(pos);
            store.addToIndex(store.shapesByChunk, pos);
        }
        return store;
    }

    static Profile loadProfile(
            int version, CompoundTag entry, SinkingMedium medium, double[] values) {
        boolean adaptive = version >= 10 && entry.getBoolean("Adaptive");
        return adaptive ? Profile.createAdaptive(values) : Profile.create(medium, values);
    }

    static void migrateLoadedValues(int version, SinkingMedium medium, double[] values) {
        if (version < 1 && medium == SinkingMedium.TAR) {
            migrateLoadedDefault(values, MudPhysicsParameter.ADHESION_STRAND_MIN_COUNT, 2.0D, 6.0D);
            migrateLoadedDefault(values, MudPhysicsParameter.ADHESION_STRAND_MAX_COUNT, 5.0D, 8.0D);
            migrateLoadedDefault(values, MudPhysicsParameter.ADHESION_STRAND_SPAWN_HEIGHT, 0.62D, 1.45D);
            migrateLoadedDefault(values, MudPhysicsParameter.ADHESION_SHEET_MIN_RIBS, 4.0D, 6.0D);
            migrateLoadedDefault(values, MudPhysicsParameter.ADHESION_SHEET_MAX_SPAN, 0.92D, 1.20D);
        }
        if (version < 2) {
            values[MudPhysicsParameter.ADHESION_BODY_ANCHOR_LIFT.ordinal()] =
                    AdhesionStrandProfile.defaultsFor(medium).bodyAnchorLift();
        }
        if (version < 3 && medium == SinkingMedium.TAR) {
            migrateLoadedDefault(
                    values, MudPhysicsParameter.ADHESION_STRAND_BREAK_LENGTH, 2.90D, 4.20D);
        }
        if (version < 4 && medium == SinkingMedium.TAR) {
            migrateLoadedDefault(values, MudPhysicsParameter.ADHESION_STRAND_MIN_COUNT, 6.0D, 10.0D);
            migrateLoadedDefault(values, MudPhysicsParameter.ADHESION_STRAND_MAX_COUNT, 8.0D, 16.0D);
            migrateLoadedDefault(
                    values, MudPhysicsParameter.ADHESION_STRAND_BREAK_LENGTH, 4.20D, 5.50D);
            migrateLoadedDefault(values, MudPhysicsParameter.ADHESION_SPAWN_INTERVAL_TICKS, 3.0D, 2.0D);
            migrateLoadedDefault(values, MudPhysicsParameter.ADHESION_BREAK_CONFIRM_TICKS, 6.0D, 10.0D);
        }
        if (version < 5 && medium == SinkingMedium.TAR) {
            migrateLoadedDefault(
                    values, MudPhysicsParameter.ADHESION_STRAND_BREAK_LENGTH, 5.50D, 2.00D);
        }
        if (version < 6 && medium != SinkingMedium.TENDER_FLESH) {
            double[] previousDefaults = new double[MudPhysicsParameter.COUNT];
            double[] templateDefaults = new double[MudPhysicsParameter.COUNT];
            AdhesionStrandProfile.defaultsBeforeSharedTarTemplate(medium)
                    .writeTo(previousDefaults);
            AdhesionStrandProfile.defaultsFor(medium).writeTo(templateDefaults);
            for (MudPhysicsParameter parameter : MudPhysicsParameter.values()) {
                if (parameter.category() != MudPhysicsParameter.Category.ADHESION_STRANDS
                        || AdhesionStrandProfile.isFeatureSwitch(parameter)) {
                    continue;
                }
                migrateLoadedDefault(
                        values,
                        parameter,
                        previousDefaults[parameter.ordinal()],
                        templateDefaults[parameter.ordinal()]);
            }
        }
        if (version < 7) {
            DroppedItemPhysicsProfile previous =
                    DroppedItemPhysicsProfile.defaultsBeforeVisibleSettling(medium);
            DroppedItemPhysicsProfile current = DroppedItemPhysicsProfile.defaultsFor(medium);
            migrateLoadedDefault(
                    values,
                    MudPhysicsParameter.ITEM_MAXIMUM_SINK_DEPTH,
                    previous.maximumSinkDepth(),
                    current.maximumSinkDepth());
            migrateLoadedDefault(
                    values,
                    MudPhysicsParameter.ITEM_MAXIMUM_IMPACT_PENETRATION,
                    previous.maximumImpactPenetration(),
                    current.maximumImpactPenetration());
        }
        if (version < 8
                && MudSinkingDepthControl.mode(values) == MudSinkingDepthControl.Mode.SIMPLE) {
            values[MudPhysicsParameter.SIMPLE_MAXIMUM_SINKING_DEPTH.ordinal()] =
                    MudSinkingDepthControl.maximumDepth(
                            values[MudPhysicsParameter.MAX_DEPTH_FACTOR.ordinal()],
                            values[MudPhysicsParameter.COLUMN_MARGIN.ordinal()]);
        }
    }

    private static void migrateLoadedDefault(double[] values, MudPhysicsParameter parameter,
            double previousDefault, double nextDefault) {
        int index = parameter.ordinal();
        if (index < values.length && Math.abs(values[index] - previousDefault) <= 1.0E-9D) {
            values[index] = nextDefault;
        }
    }

    public Profile profile(ServerLevel level, BlockPos pos, SinkingMedium medium) {
        Profile profile = profiles.get(pos.asLong());
        if (profile == null) {
            return null;
        }
        SinkingMedium current = ModBlocksAccess.mediumAt(level, pos);
        boolean adaptive = level.getBlockState(pos).getBlock() instanceof AdaptiveMudBlock;
        if (current != medium || profile.medium() != medium || profile.adaptive() != adaptive) {
            removeStored(pos.asLong());
            setDirty();
            return null;
        }
        return profile;
    }

    public boolean isModified(ServerLevel level, BlockPos pos) {
        SinkingMedium medium = ModBlocksAccess.mediumAt(level, pos);
        if (medium == null) {
            long packed = pos.asLong();
            boolean removed = removeStored(packed) != null;
            removed |= removeShapeMarker(packed);
            if (removed) {
                setDirty();
            }
            return false;
        }
        trackShapeState(level, pos, level.getBlockState(pos));
        return profile(level, pos, medium) != null || shapeModified.contains(pos.asLong());
    }

    /** Returns whether a local tuning record changes anything outside finite-volume flow. */
    public boolean hasNonFlowChanges(ServerLevel level, BlockPos pos, SinkingMedium medium) {
        Profile profile = profile(level, pos, medium);
        if (profile != null) {
            double[] values = profile.values();
            double[] inherited = MudPhysicsSettings.values(medium);
            for (MudPhysicsParameter parameter : MudPhysicsParameter.values()) {
                if (parameter.appliesTo(medium)
                        && !parameter.isFiniteVolumeFlowParameter()
                        && !sameSyncedValue(
                                values[parameter.ordinal()], inherited[parameter.ordinal()])) {
                    return true;
                }
            }
        }
        // Clear stale shape markers from older saves before classifying this state.
        trackShapeState(level, pos, level.getBlockState(pos));
        return shapeModified.contains(pos.asLong());
    }

    /** Copies a source block's local tuning only when the target has no local record. */
    public boolean copyIfAbsent(
            ServerLevel level, BlockPos source, BlockPos target, SinkingMedium medium) {
        if (ModBlocksAccess.mediumAt(level, source) != medium
                || ModBlocksAccess.mediumAt(level, target) != medium) {
            return false;
        }
        return copyIfAbsent(level, profile(level, source, medium), target, medium);
    }

    public boolean copyIfAbsent(
            ServerLevel level, Profile sourceProfile, BlockPos target, SinkingMedium medium) {
        if (ModBlocksAccess.mediumAt(level, target) != medium
                || sourceProfile == null || sourceProfile.medium() != medium) {
            return false;
        }
        long targetPacked = target.asLong();
        Profile existing = profiles.get(targetPacked);
        if (existing != null && existing.medium() == medium) {
            return false;
        }
        if (existing != null) {
            removeStored(targetPacked);
        }
        addToChunkIndex(targetPacked);
        profiles.put(targetPacked, sourceProfile);
        updateEruptionIndex(targetPacked, null, sourceProfile);
        setDirty();
        return true;
    }

    public void put(ServerLevel level, BlockPos pos, SinkingMedium medium, double[] requestedValues) {
        if (ModBlocksAccess.mediumAt(level, pos) != medium) {
            return;
        }
        putCompiled(level, pos, medium, requestedValues);
    }

    public void putOrRemoveInherited(
            ServerLevel level, BlockPos pos,
            SinkingMedium medium, double[] requestedValues) {
        if (ModBlocksAccess.mediumAt(level, pos) != medium) {
            return;
        }
        boolean adaptive = level.getBlockState(pos).getBlock() instanceof AdaptiveMudBlock;
        Profile replacement = adaptive
                ? Profile.createAdaptive(requestedValues)
                : Profile.create(medium, requestedValues);
        double[] inheritedValues = adaptive
                ? AdaptiveMudBehaviorSettings.get(level).values()
                : MudPhysicsSettings.values(medium);
        Profile inherited = adaptive
                ? Profile.createAdaptive(inheritedValues)
                : Profile.create(medium, inheritedValues);
        if (sameValues(replacement.values, inherited.values)) {
            remove(pos);
            return;
        }
        putCompiled(pos, replacement);
    }

    static boolean sameValues(double[] first, double[] second) {
        return Arrays.equals(first, second);
    }

    static boolean sameSyncedValue(double first, double second) {
        return Float.floatToIntBits((float) first) == Float.floatToIntBits((float) second);
    }

    private void putCompiled(
            ServerLevel level, BlockPos pos,
            SinkingMedium medium, double[] requestedValues) {
        Profile replacement = level.getBlockState(pos).getBlock() instanceof AdaptiveMudBlock
                ? Profile.createAdaptive(requestedValues)
                : Profile.create(medium, requestedValues);
        putCompiled(pos, replacement);
    }

    private void putCompiled(BlockPos pos, Profile replacement) {
        long packed = pos.asLong();
        if (!profiles.containsKey(packed)) {
            addToChunkIndex(packed);
        }
        Profile previous = profiles.get(packed);
        profiles.put(packed, replacement);
        updateEruptionIndex(packed, previous, replacement);
        setDirty();
    }

    public boolean remove(BlockPos pos) {
        if (removeStored(pos.asLong()) == null) {
            return false;
        }
        setDirty();
        return true;
    }

    public boolean removeAll(BlockPos pos) {
        long packed = pos.asLong();
        boolean removed = removeStored(packed) != null;
        removed |= removeShapeMarker(packed);
        if (removed) {
            setDirty();
        }
        return removed;
    }

    public List<BlockPos> modifiedIn(BlockPos minimum, BlockPos maximum, int limit) {
        List<BlockPos> result = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        int minimumChunkX = minimum.getX() >> 4;
        int maximumChunkX = maximum.getX() >> 4;
        int minimumChunkZ = minimum.getZ() >> 4;
        int maximumChunkZ = maximum.getZ() >> 4;
        for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
            for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
                long chunkKey = ChunkPos.asLong(chunkX, chunkZ);
                if (appendModified(profilesByChunk.get(chunkKey), minimum, maximum, limit, seen, result)
                        || appendModified(shapesByChunk.get(chunkKey), minimum, maximum, limit, seen, result)) {
                    return result;
                }
            }
        }
        return result;
    }

    public List<StoredProfile> profilesInChunk(ServerLevel level, ChunkPos chunk) {
        Set<Long> indexed = profilesByChunk.get(chunk.toLong());
        if (indexed == null || indexed.isEmpty()) {
            return List.of();
        }
        List<StoredProfile> result = new ArrayList<>(indexed.size());
        List<Long> stale = new ArrayList<>();
        for (long packed : indexed) {
            Profile profile = profiles.get(packed);
            BlockPos pos = BlockPos.of(packed);
            if (profile == null || ModBlocksAccess.mediumAt(level, pos) != profile.medium()) {
                stale.add(packed);
                continue;
            }
            result.add(new StoredProfile(pos, profile));
        }
        if (!stale.isEmpty()) {
            for (long packed : stale) {
                removeStored(packed);
            }
            setDirty();
        }
        return List.copyOf(result);
    }

    /** Selects one locally enabled vent profile without scanning stored blocks. */
    public EruptionCandidate randomEruptionCandidate(ServerLevel level,
            double centerX, double centerZ, double radius, RandomSource random) {
        if (eruptionProfilesByChunk.isEmpty()) {
            return null;
        }
        int minimumChunkX = ((int) Math.floor(centerX - radius)) >> 4;
        int maximumChunkX = ((int) Math.floor(centerX + radius)) >> 4;
        int minimumChunkZ = ((int) Math.floor(centerZ - radius)) >> 4;
        int maximumChunkZ = ((int) Math.floor(centerZ + radius)) >> 4;
        long selectedChunk = Long.MIN_VALUE;
        int chunkChoices = 0;
        for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
            for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
                long chunkKey = ChunkPos.asLong(chunkX, chunkZ);
                List<Long> candidates = eruptionProfilesByChunk.get(chunkKey);
                if (candidates != null && !candidates.isEmpty()
                        && random.nextInt(++chunkChoices) == 0) {
                    selectedChunk = chunkKey;
                }
            }
        }
        if (selectedChunk == Long.MIN_VALUE) {
            return null;
        }
        List<Long> candidates = eruptionProfilesByChunk.get(selectedChunk);
        int attempts = Math.min(candidates.size(), 8);
        double radiusSquared = radius * radius;
        for (int attempt = 0; attempt < attempts; attempt++) {
            if (candidates.isEmpty()) {
                return null;
            }
            long packed = candidates.get(random.nextInt(candidates.size()));
            BlockPos pos = BlockPos.of(packed);
            double dx = pos.getX() + 0.5D - centerX;
            double dz = pos.getZ() + 0.5D - centerZ;
            if (dx * dx + dz * dz > radiusSquared
                    || !level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                continue;
            }
            Profile profile = profiles.get(packed);
            SinkingMedium current = ModBlocksAccess.mediumAt(level, pos);
            if (profile == null || current != profile.medium()) {
                removeStored(packed);
                setDirty();
                return null;
            }
            double localRadius = Math.min(
                    radius, profile.eruption().spawning().searchRadius());
            if (profile.eruption().spawning().enabled()
                    && dx * dx + dz * dz <= localRadius * localRadius) {
                return new EruptionCandidate(pos, profile.medium(), profile.eruption());
            }
        }
        return null;
    }

    private void addToChunkIndex(long packed) {
        addToIndex(profilesByChunk, packed);
    }

    public void trackShapeState(ServerLevel level, BlockPos pos, BlockState state) {
        long packed = pos.asLong();
        boolean modified = ModBlocksAccess.mediumAt(level, pos) != null
                && shapeCountsAsModified(MudBlock.variant(state));
        if (modified) {
            if (shapeModified.add(packed)) {
                addToIndex(shapesByChunk, packed);
                setDirty();
            }
        } else if (removeShapeMarker(packed)) {
            setDirty();
        }
    }

    static boolean shapeCountsAsModified(MudBlockVariant variant) {
        return variant == MudBlockVariant.SPECIAL;
    }

    private static boolean appendModified(Set<Long> source, BlockPos minimum, BlockPos maximum,
            int limit, Set<Long> seen, List<BlockPos> result) {
        if (source == null) {
            return false;
        }
        for (long packed : source) {
            BlockPos pos = BlockPos.of(packed);
            if (pos.getX() < minimum.getX() || pos.getX() > maximum.getX()
                    || pos.getY() < minimum.getY() || pos.getY() > maximum.getY()
                    || pos.getZ() < minimum.getZ() || pos.getZ() > maximum.getZ()
                    || !seen.add(packed)) {
                continue;
            }
            result.add(pos);
            if (result.size() >= limit) {
                return true;
            }
        }
        return false;
    }

    private static void addToIndex(Map<Long, Set<Long>> index, long packed) {
        BlockPos pos = BlockPos.of(packed);
        index.computeIfAbsent(ChunkPos.asLong(pos), ignored -> new HashSet<>()).add(packed);
    }

    private Profile removeStored(long packed) {
        Profile removed = profiles.remove(packed);
        if (removed == null) {
            return null;
        }
        BlockPos pos = BlockPos.of(packed);
        long chunkKey = ChunkPos.asLong(pos);
        Set<Long> chunkProfiles = profilesByChunk.get(chunkKey);
        if (chunkProfiles != null) {
            chunkProfiles.remove(packed);
            if (chunkProfiles.isEmpty()) {
                profilesByChunk.remove(chunkKey);
            }
        }
        updateEruptionIndex(packed, removed, null);
        return removed;
    }

    private void updateEruptionIndex(long packed, Profile previous, Profile replacement) {
        boolean wasEnabled = previous != null && previous.eruption().spawning().enabled();
        boolean isEnabled = replacement != null && replacement.eruption().spawning().enabled();
        if (wasEnabled == isEnabled) {
            return;
        }
        long chunkKey = ChunkPos.asLong(BlockPos.of(packed));
        if (isEnabled) {
            eruptionProfilesByChunk.computeIfAbsent(chunkKey, ignored -> new ArrayList<>())
                    .add(packed);
            return;
        }
        List<Long> positions = eruptionProfilesByChunk.get(chunkKey);
        if (positions != null) {
            positions.remove(packed);
            if (positions.isEmpty()) {
                eruptionProfilesByChunk.remove(chunkKey);
            }
        }
    }

    private boolean removeShapeMarker(long packed) {
        if (!shapeModified.remove(packed)) {
            return false;
        }
        BlockPos pos = BlockPos.of(packed);
        long chunkKey = ChunkPos.asLong(pos);
        Set<Long> chunkShapes = shapesByChunk.get(chunkKey);
        if (chunkShapes != null) {
            chunkShapes.remove(packed);
            if (chunkShapes.isEmpty()) {
                shapesByChunk.remove(chunkKey);
            }
        }
        return true;
    }

    public static double value(ServerLevel level, BlockPos pos, SinkingMedium medium,
            MudPhysicsParameter parameter) {
        Profile profile = get(level).profile(level, pos, medium);
        if (profile != null) {
            return profile.value(parameter);
        }
        Profile adaptive = adaptiveBaseProfile(level, pos);
        return adaptive == null
                ? MudPhysicsSettings.value(medium, parameter)
                : adaptive.value(parameter);
    }

    static SinkingPhysicsProfile ordinary(ServerLevel level, BlockPos pos, SinkingMedium medium) {
        Profile profile = get(level).profile(level, pos, medium);
        if (profile != null) {
            return profile.ordinary();
        }
        Profile adaptive = adaptiveBaseProfile(level, pos);
        return adaptive == null ? MudPhysicsSettings.ordinaryProfile(medium) : adaptive.ordinary();
    }

    static LivingSlimePhysicsProfile livingSlime(ServerLevel level, BlockPos pos) {
        Profile profile = get(level).profile(level, pos, SinkingMedium.LIVING_SLIME);
        return profile == null ? MudPhysicsSettings.livingSlimeProfile() : profile.livingSlime();
    }

    static SculkMireProfile sculkMire(
            ServerLevel level, BlockPos pos, SinkingMedium medium) {
        Profile profile = get(level).profile(level, pos, medium);
        if (profile != null) {
            return profile.sculkMire();
        }
        Profile adaptive = adaptiveBaseProfile(level, pos);
        return adaptive == null
                ? MudPhysicsSettings.sculkMireProfile(medium)
                : adaptive.sculkMire();
    }

    static TenderFleshProfile tenderFlesh(
            ServerLevel level, BlockPos pos, SinkingMedium medium) {
        Profile profile = get(level).profile(level, pos, medium);
        if (profile != null) {
            return profile.tenderFlesh();
        }
        Profile adaptive = adaptiveBaseProfile(level, pos);
        return adaptive == null
                ? MudPhysicsSettings.tenderFleshProfile(medium)
                : adaptive.tenderFlesh();
    }

    static AssimilationProfile assimilation(
            ServerLevel level, BlockPos pos, SinkingMedium medium) {
        Profile profile = get(level).profile(level, pos, medium);
        if (profile != null) {
            return profile.assimilation();
        }
        Profile adaptive = adaptiveBaseProfile(level, pos);
        return adaptive == null
                ? MudPhysicsSettings.assimilationProfile(medium)
                : adaptive.assimilation();
    }

    public static MudEruptionProfile eruption(
            ServerLevel level, BlockPos pos, SinkingMedium medium) {
        Profile profile = get(level).profile(level, pos, medium);
        if (profile != null) {
            return profile.eruption();
        }
        Profile adaptive = adaptiveBaseProfile(level, pos);
        return adaptive == null ? MudPhysicsSettings.eruptionProfile(medium) : adaptive.eruption();
    }

    static MudHarvestProfile harvest(
            ServerLevel level, BlockPos pos, SinkingMedium medium) {
        Profile profile = get(level).profile(level, pos, medium);
        if (profile != null) {
            return profile.harvest();
        }
        Profile adaptive = adaptiveBaseProfile(level, pos);
        return adaptive == null ? MudPhysicsSettings.harvestProfile(medium) : adaptive.harvest();
    }

    static DroppedItemPhysicsProfile droppedItems(
            ServerLevel level, BlockPos pos, SinkingMedium medium) {
        Profile profile = get(level).profile(level, pos, medium);
        if (profile != null) {
            return profile.droppedItems();
        }
        Profile adaptive = adaptiveBaseProfile(level, pos);
        return adaptive == null
                ? MudPhysicsSettings.droppedItemProfile(medium)
                : adaptive.droppedItems();
    }

    static AdhesionStrandProfile adhesion(
            ServerLevel level, BlockPos pos, SinkingMedium medium) {
        Profile profile = get(level).profile(level, pos, medium);
        if (profile != null) {
            return profile.adhesionStrands();
        }
        Profile adaptive = adaptiveBaseProfile(level, pos);
        return adaptive == null
                ? MudPhysicsSettings.adhesionStrandProfile(medium)
                : adaptive.adhesionStrands();
    }

    static MudFlowProfile flow(ServerLevel level, BlockPos pos, SinkingMedium medium) {
        Profile profile = get(level).profile(level, pos, medium);
        if (profile != null) {
            return profile.flow();
        }
        Profile adaptive = adaptiveBaseProfile(level, pos);
        return adaptive == null ? MudPhysicsSettings.flowProfile(medium) : adaptive.flow();
    }

    private static Profile adaptiveBaseProfile(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof AdaptiveMudBlock adaptive
                && adaptive.medium() == SinkingMedium.MUD) {
            return AdaptiveMudBehaviorSettings.get(level).profile();
        }
        return null;
    }

    private static double[] sanitize(SinkingMedium medium, double[] requestedValues) {
        double[] defaults = MudPhysicsProfiles.defaultValues(medium);
        double[] values = Arrays.copyOf(defaults, MudPhysicsParameter.COUNT);
        for (MudPhysicsParameter parameter : MudPhysicsParameter.values()) {
            if (parameter.ordinal() < requestedValues.length && parameter.appliesTo(medium)) {
                values[parameter.ordinal()] = parameter.sanitize(requestedValues[parameter.ordinal()]);
            }
        }
        MudBlockMotionMode.enforceExclusive(values);
        MudSinkingDepthControl.enforceSimpleBounds(values);
        return values;
    }

    private static double[] sanitizeAdaptive(double[] requestedValues) {
        double[] values = MudPhysicsProfiles.defaultValues(SinkingMedium.MUD);
        for (MudPhysicsParameter parameter : MudPhysicsParameter.values()) {
            int index = parameter.ordinal();
            if (index < requestedValues.length && parameter.appliesToAdaptive()) {
                values[index] = parameter.sanitize(requestedValues[index]);
            }
        }
        MudBlockMotionMode.enforceExclusive(values);
        MudSinkingDepthControl.enforceSimpleBounds(values);
        return values;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Version", DATA_VERSION);
        ListTag entries = new ListTag();
        for (Map.Entry<Long, Profile> stored : profiles.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Pos", stored.getKey());
            entry.putInt("Medium", stored.getValue().medium().id());
            entry.putBoolean("Adaptive", stored.getValue().adaptive());
            double[] values = stored.getValue().values();
            long[] packed = new long[values.length];
            for (int index = 0; index < values.length; index++) {
                packed[index] = Double.doubleToRawLongBits(values[index]);
            }
            entry.putLongArray("Values", packed);
            entries.add(entry);
        }
        tag.put("Entries", entries);
        tag.putLongArray("ShapeModified", shapeModified.stream().mapToLong(Long::longValue).toArray());
        return tag;
    }

    public record Profile(SinkingMedium medium, boolean adaptive,
            double[] values, SinkingPhysicsProfile ordinary,
            LivingSlimePhysicsProfile livingSlime, SculkMireProfile sculkMire,
            TenderFleshProfile tenderFlesh, AdhesionStrandProfile adhesionStrands,
            AssimilationProfile assimilation,
            MudEruptionProfile eruption, MudHarvestProfile harvest,
            DroppedItemPhysicsProfile droppedItems, MudFlowProfile flow) {
        public static Profile create(SinkingMedium medium, double[] requestedValues) {
            double[] values = sanitize(medium, requestedValues);
            return compile(medium, false, values);
        }

        public static Profile createAdaptive(double[] requestedValues) {
            return compile(SinkingMedium.MUD, true, sanitizeAdaptive(requestedValues));
        }

        private static Profile compile(
                SinkingMedium medium, boolean adaptive, double[] values) {
            return new Profile(medium, adaptive, values, SinkingPhysicsProfile.fromValues(values),
                    LivingSlimePhysicsProfile.fromValues(values), SculkMireProfile.fromValues(values),
                    TenderFleshProfile.fromValues(values), AdhesionStrandProfile.fromValues(values),
                    AssimilationProfile.fromValues(values),
                    MudEruptionProfile.fromValues(values), MudHarvestProfile.fromValues(values),
                    DroppedItemPhysicsProfile.fromValues(values), MudFlowProfile.fromValues(values));
        }

        public Profile {
            values = Arrays.copyOf(values, values.length);
        }

        @Override
        public double[] values() {
            return Arrays.copyOf(values, values.length);
        }

        public double value(MudPhysicsParameter parameter) {
            return values[parameter.ordinal()];
        }
    }

    public record StoredProfile(BlockPos pos, Profile profile) {
    }

    public record EruptionCandidate(
            BlockPos pos, SinkingMedium medium, MudEruptionProfile profile) {
    }

    /** Keeps registry ownership out of SavedData serialization code. */
    private static final class ModBlocksAccess {
        private static SinkingMedium mediumAt(ServerLevel level, BlockPos pos) {
            return com.fish.mirebound.registry.ModBlocks.mediumOf(level.getBlockState(pos).getBlock());
        }
    }
}

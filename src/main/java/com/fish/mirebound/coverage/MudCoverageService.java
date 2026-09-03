package com.fish.mirebound.coverage;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.mud.MudBodyPart;
import com.fish.mirebound.mud.MudCapeLayout;
import com.fish.mirebound.mud.MudPlayerData;
import com.fish.mirebound.mud.MudSurfaceLayout;
import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.network.payload.MudCoverageDeltaPayload;
import com.fish.mirebound.network.payload.MudCoverageSyncPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Owns persistent storage and sparse network synchronization of player mud coverage. */
public final class MudCoverageService {
    private static final String PERSISTENT_DATA_KEY = Mirebound.MOD_ID + ":mud_coverage";
    private static final int TAG_COMPOUND_ID = 10;
    private static final int ACTIVE_PACK_INTERVAL_TICKS = 2;
    private static final int PERSISTENCE_SAVE_INTERVAL_TICKS = 20;

    private MudCoverageService() {
    }

    /**
     * Synchronizes immediately when requested, otherwise at most every two ticks.
     * A full snapshot is sent only while initializing the subject's sync cache;
     * normal updates contain changed cells only.
     */
    public static void sync(ServerPlayer player, MudPlayerData data, boolean immediate) {
        if (!immediate && ticksSince(player, data.lastCoveragePackTick) < ACTIVE_PACK_INTERVAL_TICKS) {
            return;
        }
        data.lastCoveragePackTick = player.tickCount;
        if (!hasFullSyncCache(data)) {
            sendFullTracking(player, data);
            return;
        }

        int surfaceChanges = countCellChanges(
                data.surfaceCoverage, data.surfaceMedium, data.surfaceAppearance,
                data.surfaceVisualSource,
                data.lastSyncedSurfaceCoverage, data.lastSyncedSurfaceMedium,
                data.lastSyncedSurfaceAppearance, data.lastSyncedSurfaceVisualSource,
                MudSurfaceLayout.CELL_COUNT);
        int capeChanges = countCellChanges(
                data.capeCoverage, data.capeMedium, data.capeAppearance,
                data.capeVisualSource,
                data.lastSyncedCapeCoverage, data.lastSyncedCapeMedium,
                data.lastSyncedCapeAppearance, data.lastSyncedCapeVisualSource,
                MudCapeLayout.CELL_COUNT);
        int visionChanges = countVisionChanges(data);
        int coveragePermille = permille(data.coverage);
        int visionPermille = permille(data.visionObstruction);
        boolean coverageChanged = coveragePermille != permille(data.lastSyncedCoverage);
        boolean visionChanged = visionPermille != permille(data.lastSyncedVisionObstruction);
        boolean mediumChanged = data.medium.id() != data.lastSyncedMediumId;
        boolean patternSeedChanged = data.coveragePatternSeed
                != data.lastSyncedCoveragePatternSeed;
        boolean persistentChanged = coverageChanged || surfaceChanges > 0
                || capeChanges > 0 || mediumChanged || patternSeedChanged;
        if (persistentChanged) {
            data.coveragePersistenceDirty = true;
        }
        if (data.coveragePersistenceDirty
                && ticksSince(player, data.lastPersistentSaveTick)
                        >= PERSISTENCE_SAVE_INTERVAL_TICKS) {
            save(player, data);
        }
        if (!coverageChanged && !visionChanged && !mediumChanged && !patternSeedChanged
                && surfaceChanges == 0 && capeChanges == 0 && visionChanges == 0) {
            return;
        }

        CellDelta surfaces = buildCellDelta(
                data.surfaceCoverage, data.surfaceMedium, data.surfaceAppearance,
                data.surfaceVisualSource,
                data.lastSyncedSurfaceCoverage, data.lastSyncedSurfaceMedium,
                data.lastSyncedSurfaceAppearance, data.lastSyncedSurfaceVisualSource,
                MudSurfaceLayout.CELL_COUNT, surfaceChanges);
        CellDelta capes = buildCellDelta(
                data.capeCoverage, data.capeMedium, data.capeAppearance,
                data.capeVisualSource,
                data.lastSyncedCapeCoverage, data.lastSyncedCapeMedium,
                data.lastSyncedCapeAppearance, data.lastSyncedCapeVisualSource,
                MudCapeLayout.CELL_COUNT, capeChanges);
        VisionDelta vision = buildVisionDelta(data, visionChanges);
        data.lastSyncTick = player.tickCount;
        data.lastSyncedCoverage = data.coverage;
        data.lastSyncedVisionObstruction = data.visionObstruction;
        data.lastSyncedMediumId = data.medium.id();
        data.lastSyncedCoveragePatternSeed = data.coveragePatternSeed;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                player,
                new MudCoverageDeltaPayload(
                        player.getId(), data.coveragePatternSeed,
                        coveragePermille, data.medium.id(), visionPermille,
                        surfaces.indices, surfaces.coverage, surfaces.medium,
                        surfaces.appearance, surfaces.visualSource,
                        capes.indices, capes.coverage, capes.medium,
                        capes.appearance, capes.visualSource,
                        vision.indices, vision.coverage, vision.medium,
                        vision.visualSource));
    }

    /** Sends a complete state to a newly tracking observer without disturbing delta history. */
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer observer
                && event.getTarget() instanceof ServerPlayer subject) {
            sendFullTo(observer, subject, com.fish.mirebound.mud.MudStateStore.get(subject));
        }
    }

    public static void sendFullTo(ServerPlayer recipient, ServerPlayer subject, MudPlayerData data) {
        PacketDistributor.sendToPlayer(recipient, fullPayload(subject, data));
    }

    public static void load(ServerPlayer player, MudPlayerData data) {
        CompoundTag persistentData = player.getPersistentData();
        if (persistentData.contains(PERSISTENT_DATA_KEY, TAG_COMPOUND_ID)) {
            data.loadPersistent(persistentData.getCompound(PERSISTENT_DATA_KEY));
        } else {
            data.clearSyncedParts();
            data.resetSyncCache();
        }
        data.coveragePersistenceDirty = false;
    }

    public static void save(ServerPlayer player, MudPlayerData data) {
        CompoundTag persistentData = player.getPersistentData();
        if (data.hasPersistentCoverage()) {
            persistentData.put(PERSISTENT_DATA_KEY, data.savePersistent());
        } else {
            persistentData.remove(PERSISTENT_DATA_KEY);
        }
        data.lastPersistentSaveTick = player.tickCount;
        data.coveragePersistenceDirty = false;
    }

    private static void sendFullTracking(ServerPlayer player, MudPlayerData data) {
        MudCoverageSyncPayload payload = fullPayload(player, data);
        data.lastSyncTick = player.tickCount;
        data.lastSyncedCoverage = data.coverage;
        data.lastSyncedVisionObstruction = data.visionObstruction;
        data.lastSyncedMediumId = data.medium.id();
        data.lastSyncedCoveragePatternSeed = data.coveragePatternSeed;
        data.lastSyncedVisionCoverage = payload.packedVisionCoverage();
        data.lastSyncedVisionMedium = payload.packedVisionMedium();
        data.lastSyncedSurfaceCoverage = payload.packedSurfaceCoverage();
        data.lastSyncedSurfaceMedium = payload.packedSurfaceMedium();
        data.lastSyncedSurfaceAppearance = payload.packedSurfaceAppearance();
        data.lastSyncedSurfaceVisualSource = payload.packedSurfaceVisualSource();
        data.lastSyncedCapeCoverage = payload.packedCapeCoverage();
        data.lastSyncedCapeMedium = payload.packedCapeMedium();
        data.lastSyncedCapeAppearance = payload.packedCapeAppearance();
        data.lastSyncedCapeVisualSource = payload.packedCapeVisualSource();
        data.lastSyncedVisionVisualSource = payload.packedVisionVisualSource();
        save(player, data);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, payload);
    }

    private static MudCoverageSyncPayload fullPayload(ServerPlayer player, MudPlayerData data) {
        return new MudCoverageSyncPayload(
                player.getId(), data.coveragePatternSeed,
                permille(data.coverage), data.medium.id(),
                permille(data.visionObstruction),
                MudCoverageSyncPayload.packVision(data.visionCoverage),
                MudCoverageSyncPayload.packVisionMedium(data.visionMedium),
                data.visionVisualSource.clone(),
                MudCoverageSyncPayload.packSurfaces(data.surfaceCoverage),
                MudCoverageSyncPayload.packSurfaceMedium(data.surfaceMedium),
                data.surfaceAppearance.clone(),
                data.surfaceVisualSource.clone(),
                MudCoverageSyncPayload.packCape(data.capeCoverage),
                MudCoverageSyncPayload.packCapeMedium(data.capeMedium),
                data.capeAppearance.clone(),
                data.capeVisualSource.clone());
    }

    private static boolean hasFullSyncCache(MudPlayerData data) {
        return data.lastSyncedSurfaceCoverage.length == MudSurfaceLayout.CELL_COUNT
                && data.lastSyncedSurfaceMedium.length == MudSurfaceLayout.CELL_COUNT
                && data.lastSyncedSurfaceAppearance.length == MudSurfaceLayout.CELL_COUNT
                && data.lastSyncedSurfaceVisualSource.length == MudSurfaceLayout.CELL_COUNT
                && data.lastSyncedCapeCoverage.length == MudCapeLayout.CELL_COUNT
                && data.lastSyncedCapeMedium.length == MudCapeLayout.CELL_COUNT
                && data.lastSyncedCapeAppearance.length == MudCapeLayout.CELL_COUNT
                && data.lastSyncedCapeVisualSource.length == MudCapeLayout.CELL_COUNT
                && data.lastSyncedVisionCoverage.length == MudBodyPart.VISION_COUNT
                && data.lastSyncedVisionMedium.length == MudBodyPart.VISION_COUNT
                && data.lastSyncedVisionVisualSource.length == MudBodyPart.VISION_COUNT;
    }

    private static int countCellChanges(
            float[] coverage, byte[] medium, int[] appearance, long[] visualSource,
            byte[] previousCoverage, byte[] previousMedium, int[] previousAppearance,
            long[] previousVisualSource,
            int count) {
        int changed = 0;
        for (int index = 0; index < count; index++) {
            int packedCoverage = MudCoverageSyncPayload.packCoverageLevel(coverage[index]);
            if ((previousCoverage[index] & 0xFF) != packedCoverage
                    || previousMedium[index] != validMedium(medium[index])
                    || previousAppearance[index] != appearance[index]
                    || previousVisualSource[index] != visualSource[index]) {
                changed++;
            }
        }
        return changed;
    }

    private static CellDelta buildCellDelta(
            float[] coverage, byte[] medium, int[] appearance, long[] visualSource,
            byte[] previousCoverage, byte[] previousMedium, int[] previousAppearance,
            long[] previousVisualSource,
            int count, int changedCount) {
        if (changedCount == 0) {
            return CellDelta.EMPTY;
        }
        int[] indices = new int[changedCount];
        byte[] packedCoverage = new byte[changedCount];
        byte[] packedMedium = new byte[changedCount];
        int[] packedAppearance = new int[changedCount];
        long[] packedVisualSource = new long[changedCount];
        int offset = 0;
        for (int index = 0; index < count; index++) {
            byte nextCoverage = (byte) MudCoverageSyncPayload.packCoverageLevel(coverage[index]);
            byte nextMedium = validMedium(medium[index]);
            int nextAppearance = appearance[index];
            long nextVisualSource = visualSource[index];
            if (previousCoverage[index] == nextCoverage
                    && previousMedium[index] == nextMedium
                    && previousAppearance[index] == nextAppearance
                    && previousVisualSource[index] == nextVisualSource) {
                continue;
            }
            indices[offset] = index;
            packedCoverage[offset] = nextCoverage;
            packedMedium[offset] = nextMedium;
            packedAppearance[offset] = nextAppearance;
            packedVisualSource[offset] = nextVisualSource;
            previousCoverage[index] = nextCoverage;
            previousMedium[index] = nextMedium;
            previousAppearance[index] = nextAppearance;
            previousVisualSource[index] = nextVisualSource;
            offset++;
        }
        if (offset != changedCount) {
            throw new IllegalStateException("Mud coverage delta changed during one server tick");
        }
        return new CellDelta(indices, packedCoverage, packedMedium,
                packedAppearance, packedVisualSource);
    }

    private static int countVisionChanges(MudPlayerData data) {
        int changed = 0;
        for (int index = 0; index < MudBodyPart.VISION_COUNT; index++) {
            int coverage = MudCoverageSyncPayload.packCoverageLevel(data.visionCoverage[index]);
            if ((data.lastSyncedVisionCoverage[index] & 0xFF) != coverage
                    || data.lastSyncedVisionMedium[index] != validMedium(data.visionMedium[index])
                    || data.lastSyncedVisionVisualSource[index]
                            != data.visionVisualSource[index]) {
                changed++;
            }
        }
        return changed;
    }

    private static VisionDelta buildVisionDelta(MudPlayerData data, int changedCount) {
        if (changedCount == 0) {
            return VisionDelta.EMPTY;
        }
        int[] indices = new int[changedCount];
        byte[] coverage = new byte[changedCount];
        byte[] medium = new byte[changedCount];
        long[] visualSource = new long[changedCount];
        int offset = 0;
        for (int index = 0; index < MudBodyPart.VISION_COUNT; index++) {
            byte nextCoverage = (byte) MudCoverageSyncPayload.packCoverageLevel(data.visionCoverage[index]);
            byte nextMedium = validMedium(data.visionMedium[index]);
            long nextVisualSource = data.visionVisualSource[index];
            if (data.lastSyncedVisionCoverage[index] == nextCoverage
                    && data.lastSyncedVisionMedium[index] == nextMedium
                    && data.lastSyncedVisionVisualSource[index] == nextVisualSource) {
                continue;
            }
            indices[offset] = index;
            coverage[offset] = nextCoverage;
            medium[offset] = nextMedium;
            visualSource[offset] = nextVisualSource;
            data.lastSyncedVisionCoverage[index] = nextCoverage;
            data.lastSyncedVisionMedium[index] = nextMedium;
            data.lastSyncedVisionVisualSource[index] = nextVisualSource;
            offset++;
        }
        if (offset != changedCount) {
            throw new IllegalStateException("Mud vision delta changed during one server tick");
        }
        return new VisionDelta(indices, coverage, medium, visualSource);
    }

    private static byte validMedium(byte medium) {
        int id = medium & 0xFF;
        return (byte) (id < SinkingMedium.COUNT ? id : SinkingMedium.MUD.id());
    }

    private static int permille(float value) {
        return Mth.clamp(Mth.floor(Mth.clamp(value, 0.0F, 1.0F) * 1000.0F), 0, 1000);
    }

    private static long ticksSince(ServerPlayer player, int lastTick) {
        return (long) player.tickCount - (long) lastTick;
    }

    private record CellDelta(int[] indices, byte[] coverage, byte[] medium,
            int[] appearance, long[] visualSource) {
        private static final CellDelta EMPTY = new CellDelta(
                new int[0], new byte[0], new byte[0], new int[0], new long[0]);
    }

    private record VisionDelta(int[] indices, byte[] coverage, byte[] medium,
            long[] visualSource) {
        private static final VisionDelta EMPTY = new VisionDelta(
                new int[0], new byte[0], new byte[0], new long[0]);
    }
}

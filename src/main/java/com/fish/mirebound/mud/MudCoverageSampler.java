package com.fish.mirebound.mud;

import static com.fish.mirebound.coverage.MudSkinCoverageOperations.blendSkinSurfaceEdges;
import static com.fish.mirebound.coverage.MudSkinCoverageOperations.innerCleanlinessMask;
import static com.fish.mirebound.coverage.MudSkinCoverageOperations.innerSkinProtected;
import static com.fish.mirebound.mud.MudContactRules.requiresSoleEntry;

import com.fish.mirebound.compat.sable.SableCompat;
import com.fish.mirebound.compat.sable.SableCompat.SinkingSample;
import com.fish.mirebound.adaptive.MudVisualSource;
import com.fish.mirebound.coverage.armor.ArmorTextureMudManager;
import com.fish.mirebound.coverage.MudCoverageService;
import com.fish.mirebound.coverage.MudVisionSamplingLayout;
import com.fish.mirebound.registry.ModBlocks;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Exact body/cape coverage, active face vision, splash paint, and diagnostics. */
final class MudCoverageSampler {
    private static final float VISION_CLEAR_THRESHOLD = 0.0005F;
    private static final double VISION_FACE_EDGE_PROBE_FACTOR = 0.62D;
    private static final float VISION_FACE_EDGE_PROBE_COVERAGE = 0.82F;
    private static final float SURFACE_MEDIUM_REFRESH_THRESHOLD = 0.025F;
    private static final float SABLE_STRONG_SWITCH_THRESHOLD = 0.62F;
    private static final float SABLE_STRONG_SWITCH_MIN_STRENGTH = 0.68F;
    private static final double SABLE_LAYER_ENTRY_PAINT_DEPTH = 0.24D;
    private static final int SABLE_NEW_SURFACE_CONFIRM_TICKS = 2;
    private static final int SABLE_MEDIUM_SWITCH_CONFIRM_TICKS = 3;
    private static final MudBodyPart[] MUD_BODY_PARTS = MudBodyPart.values();
    private static final MudSurface[] MUD_SURFACES = MudSurface.values();
    private static final SinkingMedium[] SINKING_MEDIA = SinkingMedium.values();
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final ThreadLocal<CoverageThreadState> COVERAGE_THREAD_STATE =
            ThreadLocal.withInitial(CoverageThreadState::new);

    private MudCoverageSampler() {
    }

    static void resetThreadState() {
        COVERAGE_THREAD_STATE.remove();
        MudSplashPlayerPainter.resetThreadState();
    }

    private static double smoothStep(double value) {
        double clamped = Mth.clamp(value, 0.0D, 1.0D);
        return clamped * clamped * (3.0D - 2.0D * clamped);
    }

    private static long ticksSince(ServerPlayer player, int lastTick) {
        return (long) player.tickCount - (long) lastTick;
    }

    static boolean updateContactOnlyMudCoverage(
            ServerPlayer player, MudPlayerData data,
            MudVolumeSnapshot snapshot) {
        WorldMudVolumeProbe worldProbe = snapshot.worldProbe();
        SableCompat.MudVolumeProbe sableProbe = snapshot.sableProbe();
        if (worldProbe.isEmpty()
                && (sableProbe == null || sableProbe.isEmpty())) {
            return false;
        }

        CoverageThreadState threadState = COVERAGE_THREAD_STATE.get();
        SableCompat.MudVolumeProbe previousProbe =
                threadState.sableVolumeCoverageProbe;
        WorldMudVolumeProbe previousWorldProbe =
                threadState.worldVolumeCoverageProbe;
        threadState.sableVolumeCoverageProbe = sableProbe;
        threadState.worldVolumeCoverageProbe = worldProbe;
        try {
            updatePartCoverage(player, player.level(), data);
            ArmorMudManager.applyContacts(player, data);
            float visionFill = updateVisionCoverage(
                    player, player.level(), data);
            MudSample eye = sampleSubmergedMudPoint(
                    player,
                    player.level(),
                    playerEyePosition(player),
                    1.0F);
            data.eyeSubmerged = eye.strength() > 0.01F;
            data.visionObstruction = visionFill;
            if (data.eyeSubmerged) {
                tickMudSuffocation(player);
            } else {
                recoverAir(player);
            }
            return true;
        } finally {
            threadState.sableVolumeCoverageProbe = previousProbe;
            threadState.worldVolumeCoverageProbe = previousWorldProbe;
        }
    }

    /**
     * Paints the sealed body without treating its detached soul camera as an
     * active mud contact. Persistent skin, cape and armor dirt still update,
     * while vision, breathing, movement and struggle state remain untouched.
     */
    static boolean updateFrozenBodyMudCoverage(
            ServerPlayer player, MudPlayerData data,
            MudVolumeSnapshot snapshot) {
        WorldMudVolumeProbe worldProbe = snapshot.worldProbe();
        SableCompat.MudVolumeProbe sableProbe = snapshot.sableProbe();
        data.eyeSubmerged = false;
        data.visionObstruction = 0.0F;
        data.clearVisionCoverage();
        if (worldProbe.isEmpty()
                && (sableProbe == null || sableProbe.isEmpty())) {
            data.clearArmorContacts();
            data.clearSurfaceContacts();
            return false;
        }

        CoverageThreadState threadState = COVERAGE_THREAD_STATE.get();
        SableCompat.MudVolumeProbe previousProbe =
                threadState.sableVolumeCoverageProbe;
        WorldMudVolumeProbe previousWorldProbe =
                threadState.worldVolumeCoverageProbe;
        threadState.sableVolumeCoverageProbe = sableProbe;
        threadState.worldVolumeCoverageProbe = worldProbe;
        try {
            updatePartCoverage(player, player.level(), data);
            ArmorMudManager.applyContacts(player, data);
            return true;
        } finally {
            threadState.sableVolumeCoverageProbe = previousProbe;
            threadState.worldVolumeCoverageProbe = previousWorldProbe;
        }
    }


    static void updateServerMudState(ServerPlayer player, Level level, MudContact contact) {
        MudPlayerData data = MudStateStore.get(player);
        if (data.lastProcessedTick == player.tickCount) {
            return;
        }

        double surfaceY = contact.surfaceY();
        SinkingMedium medium = contact.medium();
        double currentDepth = contact.sableContext() == null
                ? MudContactResolver.depthFromSurface(player, surfaceY)
                : contact.depth();
        double currentDepthFactor = Mth.clamp(currentDepth / Math.max(player.getBbHeight(), 0.1D), 0.0D, 1.35D);
        data.lastProcessedTick = player.tickCount;
        data.lastMudTick = player.tickCount;
        data.inMud = true;
        if (contact.physicsMedium() != SinkingMedium.LIVING_SLIME) {
            data.resetLivingSlimeState();
        }
        data.medium = medium;
        data.depth = currentDepth;
        data.stuckTicks++;
        CoverageThreadState threadState = COVERAGE_THREAD_STATE.get();
        CoverageDebugContext previousDebugContext = threadState.coverageDebugContext;
        SableCoverageContext previousSableContext = threadState.sableCoverageContext;
        SableCompat.MudVolumeProbe previousSableProbe = threadState.sableVolumeCoverageProbe;
        WorldMudVolumeProbe previousWorldProbe = threadState.worldVolumeCoverageProbe;
        CoverageDebugContext debugContext = CoverageDebugLog.enabled()
                ? new CoverageDebugContext(player, medium, surfaceY, currentDepth)
                : null;
        threadState.coverageDebugContext = debugContext;
        threadState.sableCoverageContext = contact.sableContext();
        if (threadState.sableCoverageContext == null) {
            // Freeze nearby ordinary and physicalized volumes once. The exact skin,
            // armor, cape and vision probes then share this snapshot instead of
            // issuing a reflected Sable broadphase query for every missed pixel.
            MudVolumeSnapshot snapshot = MudVolumeContactResolver.nearbySnapshot(player, true);
            threadState.worldVolumeCoverageProbe = snapshot.worldProbe();
            threadState.sableVolumeCoverageProbe = snapshot.sableProbe();
        }
        if (threadState.sableCoverageContext != null
                && threadState.sableCoverageContext.layers().length > 1
                && CoverageDebugLog.reserveSableDiagnostic(player, "column", 20)) {
            CoverageDebugLog.sableDiagnostic(
                    player, "column", MudCoverageDiagnostics.sableColumn(player, threadState.sableCoverageContext));
        }
        if (debugContext != null && CoverageDebugLog.reserve(player, "server-state", 20)) {
            CoverageDebugLog.event(player, "server-state",
                    MudCoverageDiagnostics.serverState(player, level, data, medium, surfaceY, currentDepth));
        }
        try {
            SableLayerPoint eyeLayer = threadState.sableCoverageContext == null
                    ? null
                    : threadState.sableCoverageContext.layerPoint(playerEyePosition(player));
            data.eyeSubmerged = threadState.sableCoverageContext == null
                    ? playerEyePosition(player).y < surfaceY - 0.02D || isEntityEyeInSinking(player)
                    : eyeLayer != null && eyeLayer.depth() > 0.02D;
            float visionFill = updateVisionCoverage(player, level, data);
            data.visionObstruction = threadState.sableCoverageContext == null
                    ? Math.max(calculateVisionObstruction(player, surfaceY, data), visionFill)
                    : visionFill;

            float targetCoverage = threadState.sableCoverageContext == null
                    ? calculateCoverage(player, surfaceY, currentDepth, medium)
                    : calculateSableCoverage(player, currentDepth, medium);
            if (data.eyeSubmerged) {
                targetCoverage = Math.max(targetCoverage, 0.92F);
                tickMudSuffocation(player);
            } else {
                recoverAir(player);
            }

            updatePartCoverage(player, level, data);
            ArmorMudManager.applyContacts(player, data);
            if (ordinaryCoverageEnabled(level, contact.surfaceProfilePos(), medium)) {
                data.setCoverage(data.coverage + (targetCoverage - data.coverage) * 0.16F);
            }
            if (debugContext != null
                    && (debugContext.changedCells > 0 || debugContext.sablePendingCells > 0 || debugContext.sableRejectedCells > 0)
                    && CoverageDebugLog.reserve(player, "surface-summary", 10)) {
                CoverageDebugLog.event(player, "surface-summary",
                        "changed=" + debugContext.changedCells
                                + " logged=" + debugContext.loggedCells
                                + " mediumMismatch=" + debugContext.mediumMismatchCells
                                + " sableHits=" + debugContext.sableHitCells
                                + " sablePending=" + debugContext.sablePendingCells
                                + " sablePendingMismatch=" + debugContext.sablePendingMismatchCells
                                + " sableRejected=" + debugContext.sableRejectedCells
                                + " sableRejectedMismatch=" + debugContext.sableRejectedMismatchCells
                                + " coverage=" + MudCoverageDiagnostics.round3(data.coverage)
                                + " cells=" + MudCoverageDiagnostics.coverageSummary(data));
            }
        } finally {
            threadState.coverageDebugContext = previousDebugContext;
            threadState.sableCoverageContext = previousSableContext;
            threadState.sableVolumeCoverageProbe = previousSableProbe;
            threadState.worldVolumeCoverageProbe = previousWorldProbe;
        }
        MudCoverageService.sync(player, data, data.eyeSubmerged);
    }

    public static boolean applyMudSplashToPlayer(ServerPlayer player, Vec3 impact,
            float radius, float strength, SinkingMedium medium) {
        return MudSplashPlayerPainter.apply(
                player, impact, radius, strength, medium, 0L, false);
    }

    public static boolean applyMudSplashToPlayer(ServerPlayer player, Vec3 impact,
            float radius, float strength, SinkingMedium medium,
            boolean forceOrdinaryCoverage) {
        return applyMudSplashToPlayer(player, impact, radius, strength,
                medium, 0L, forceOrdinaryCoverage);
    }

    public static boolean applyMudSplashToPlayer(ServerPlayer player, Vec3 impact,
            float radius, float strength, SinkingMedium medium,
            long visualSource, boolean forceOrdinaryCoverage) {
        return MudSplashPlayerPainter.apply(
                player, impact, radius, strength, medium, visualSource,
                forceOrdinaryCoverage);
    }

    public static boolean applyMudClodToPlayer(ServerPlayer player, Vec3 impact,
            float radius, float strength, SinkingMedium medium,
            long visualSource, boolean forceOrdinaryCoverage) {
        return MudSplashPlayerPainter.applyClod(
                player, impact, radius, strength, medium, visualSource,
                forceOrdinaryCoverage);
    }

    private static void updatePartCoverage(ServerPlayer player, Level level, MudPlayerData data) {
        data.clearArmorContacts();
        data.clearSurfaceContacts();
        int activeArmorMask = activeArmorMask(player);
        int innerCleanlinessMask = innerCleanlinessMask(player);
        CoverageThreadState threadState = COVERAGE_THREAD_STATE.get();
        SurfaceSampleFrame previousFrame = threadState.activeSurfaceSampleFrame;
        SurfaceSampleFrame frame = threadState.sableCoverageContext == null
                ? null : threadState.sableSurfaceSampleFrame;
        if (frame != null) {
            frame.begin();
            threadState.activeSurfaceSampleFrame = frame;
        }
        try {
            SurfaceWorldColumnCache worldColumns = threadState.sableCoverageContext == null
                    ? threadState.worldSurfaceColumnCache : null;
            if (worldColumns != null) {
                worldColumns.begin();
            }
            for (MudBodyPart part : MUD_BODY_PARTS) {
                sampleSurfacePixels(
                        player,
                        level,
                        data,
                        worldColumns,
                        threadState,
                        part,
                        activeArmorMask,
                        innerCleanlinessMask);
            }
            sampleCapePixels(player, level, data, worldColumns, threadState);
            if (frame != null) {
                frame.commit(data);
                if (threadState.sableCoverageContext.layers().length > 1
                        && CoverageDebugLog.reserveSableDiagnostic(player, "frame", 20)) {
                    CoverageDebugLog.sableDiagnostic(player, "frame",
                        frame.describe() + " state=" + debugCoverageSummary(data));
                }
            }
            blendSkinSurfaceEdges(player, data, innerCleanlinessMask);
            data.refreshCoverageAfterSurfaceUpdate();
        } finally {
            if (frame != null) {
                frame.clear();
            }
            threadState.activeSurfaceSampleFrame = previousFrame;
        }
    }

    private static void sampleSurfacePixels(Player player, Level level, MudPlayerData data,
            SurfaceWorldColumnCache worldColumns, CoverageThreadState threadState,
            MudBodyPart part, int activeArmorMask, int innerCleanlinessMask) {
        MudEntityGeometry.SurfacePixelSampler geometry =
                MudEntityGeometry.surfacePixelSampler(player, part);
        for (MudSurface surface : MUD_SURFACES) {
            MudSurfaceLayout.Face face = MudSurfaceLayout.face(part, surface);
            Vec3 normal = geometry.outwardNormal(surface);
            for (int row = 0; row < face.height(); row++) {
                boolean soleEntryRequired = requiresSoleEntry(part, surface, row);
                boolean skinProtected = innerSkinProtected(
                        innerCleanlinessMask, part, surface, row);
                int rowArmorMask = armorSurfaceMask(
                        activeArmorMask, part, surface, row);
                for (int column = 0; column < face.width(); column++) {
                    int cell = MudSurfaceLayout.cellIndex(part, surface, row, column);
                    boolean fracture = com.fish.mirebound.assimilation.AssimilationSystem
                            .keepsCrackClear(player, cell);
                    Vec3 point = geometry.point(part, surface, row, column);
                    if (soleEntryRequired
                            && sampleSurfacePixel(
                                    player,
                                    level,
                                    soleEntryProbePoint(
                                            player,
                                            part,
                                            surface,
                                            row,
                                            column,
                                            point,
                                            normal,
                                            geometry),
                                    worldColumns,
                                    threadState).strength() <= 0.0F) {
                        continue;
                    }
                    MudSample sample = sampleSurfacePixel(
                            player, level, point, worldColumns, threadState);
                    if (sample.strength() > 0.0F) {
                        data.markSurfaceContact(cell);
                    }
                    if (sample != MudSample.NONE
                            && MudMediumRuntime.assimilationProfile(
                                    level, sample.profilePos(), sample.medium()).enabled()) {
                        data.markAssimilationContact(
                                sample.medium(), sample.profilePos(),
                                sample.visualSource(), 1.0F);
                    }
                    if (!fracture && !skinProtected
                            && sample.strength() > 0.0F
                            && ordinaryCoverageEnabled(level, sample.profilePos(), sample.medium())
                            && MudCoverageRules.allowsPixel(
                                    sample.medium(), sample.appearance(), MudCoverageRules.DOMAIN_SKIN,
                                    cell, MudSurfaceLayout.CELL_COUNT)) {
                        markSurfaceTouched(
                                data, part, row, surface, column, sample, threadState);
                    }
                    if (fracture || rowArmorMask == 0) {
                        continue;
                    }
                    for (var armorSlot : ArmorMudManager.armorSlots()) {
                        int slotBit = 1 << ArmorMudManager.armorSlotIndex(armorSlot);
                        if ((rowArmorMask & slotBit) == 0) {
                            continue;
                        }
                        Vec3 armorPoint = point.add(normal.scale(ArmorMudManager.surfaceOffset(armorSlot)));
                        MudSample armorSample = sampleSurfacePixel(
                                player, level, armorPoint, worldColumns, threadState);
                        if (armorSample.strength() > 0.0F
                                && ordinaryCoverageEnabled(
                                        level, armorSample.profilePos(), armorSample.medium())
                                && ArmorMudManager.allowsCoveragePixel(
                                        player, armorSlot, cell, armorSample.medium())) {
                            data.setArmorContact(armorSlot, cell, armorSample.strength(),
                                    armorSample.medium(), armorSample.visualSource());
                        }
                    }
                }
            }
        }
    }

    private static int activeArmorMask(Player player) {
        int mask = 0;
        for (EquipmentSlot slot : ArmorMudManager.armorSlots()) {
            if (ArmorMudManager.validArmor(player.getItemBySlot(slot), slot)) {
                mask |= 1 << ArmorMudManager.armorSlotIndex(slot);
            }
        }
        return mask;
    }

    private static int armorSurfaceMask(int activeArmorMask,
            MudBodyPart part, MudSurface surface, int row) {
        int mask = 0;
        for (EquipmentSlot slot : ArmorMudManager.armorSlots()) {
            int bit = 1 << ArmorMudManager.armorSlotIndex(slot);
            if ((activeArmorMask & bit) != 0
                    && ArmorMudManager.slotOwnsSurface(slot, part, surface, row)) {
                mask |= bit;
            }
        }
        return mask;
    }

    private static void sampleCapePixels(Player player, Level level, MudPlayerData data,
            SurfaceWorldColumnCache worldColumns, CoverageThreadState threadState) {
        MudCapeGeometry.CapeBasis basis = MudEntityGeometry.capeBasis(player);
        for (int row = 0; row < MudCapeLayout.ROWS; row++) {
            for (int column = 0; column < MudCapeLayout.COLUMNS; column++) {
                MudSample front = sampleSurfacePixel(
                        player, level, MudCapeGeometry.frontProbe(basis, row, column),
                        worldColumns, threadState);
                MudSample back = sampleSurfacePixel(
                        player, level, MudCapeGeometry.backProbe(basis, row, column),
                        worldColumns, threadState);
                int outerCell = MudCapeLayout.index(MudCapeLayout.Side.OUTER, row, column);
                if (front.strength() > 0.0F
                        && ordinaryCoverageEnabled(level, front.profilePos(), front.medium())
                        && MudCoverageRules.allowsPixel(
                                front.medium(), front.appearance(), MudCoverageRules.DOMAIN_CAPE,
                                outerCell, MudCapeLayout.CELL_COUNT)) {
                    data.applyCapeSample(
                            MudCapeLayout.Side.OUTER, row, column, front.strength(),
                            front.medium(), front.appearance(), front.visualSource());
                }
                int innerCell = MudCapeLayout.index(MudCapeLayout.Side.INNER, row, column);
                if (back.strength() > 0.0F
                        && ordinaryCoverageEnabled(level, back.profilePos(), back.medium())
                        && MudCoverageRules.allowsPixel(
                                back.medium(), back.appearance(), MudCoverageRules.DOMAIN_CAPE,
                                innerCell, MudCapeLayout.CELL_COUNT)) {
                    data.applyCapeSample(
                            MudCapeLayout.Side.INNER, row, column, back.strength(),
                            back.medium(), back.appearance(), back.visualSource());
                }
            }
        }
    }

    private static Vec3 soleEntryProbePoint(Player player, MudBodyPart part,
            MudSurface surface, int row, int column, Vec3 surfacePoint,
            Vec3 outwardNormal, MudEntityGeometry.SurfacePixelSampler geometry) {
        Vec3 solePoint = surfacePoint;
        Vec3 soleNormal = outwardNormal;
        if (surface != MudSurface.BOTTOM) {
            MudSurfaceLayout.AdjacentCell adjacent =
                    MudSurfaceLayout.neighborAcrossEdge(
                            part,
                            surface,
                            row,
                            column,
                            MudSurfaceLayout.Edge.ROW_MIN);
            solePoint = geometry.point(
                    part, adjacent.surface(), adjacent.row(), adjacent.column());
            soleNormal = geometry.outwardNormal(adjacent.surface());
        }
        return MudContactRules.soleEntryProbePoint(
                playerFeetPosition(player).y,
                solePoint,
                soleNormal);
    }

    private static MudSample sampleSurfacePixel(Player player, Level level, Vec3 point,
            SurfaceWorldColumnCache worldColumns, CoverageThreadState threadState) {
        SableCoverageContext context = threadState.sableCoverageContext;
        if (context != null) {
            SableLayerPoint layerPoint = context.layerPoint(point);
            if (layerPoint == null || layerPoint.depth() < 0.0D
                    || layerPoint.depth() > context.availableDepth() + 0.001D
                    || !producesMudPollution(
                            level, layerPoint.layer().pos(), layerPoint.layer().medium())) {
                return MudSample.NONE;
            }
            return MudSample.sableConfirmed(
                    MudCoverageRules.contactTarget(level, layerPoint.layer().medium(), 1.0F),
                    layerPoint.layer().medium(),
                    layerPoint.layer().pos(),
                    MudCoverageAppearanceSnapshot.at(
                            level, layerPoint.layer().pos(), layerPoint.layer().medium()),
                    layerPoint.layer().visualSource(),
                    layerPoint.depth(),
                    debugSableContextTrace("sable-surface-pixel", point, context, layerPoint));
        }

        BlockPos pos = BlockPos.containing(point);
        BlockState state = level.getBlockState(pos);
        SinkingMedium medium = ModBlocks.mediumOf(state.getBlock());
        if (medium == null || !MudMediumRuntime.enabled(level, pos, medium)) {
            WorldVolumeSample worldSample =
                    sampleWorldMudVolume(level, point, 0.004D, threadState);
            if (worldSample != null
                    && producesMudPollution(level, worldSample.pos(), worldSample.medium())) {
                return new MudSample(
                        MudCoverageRules.contactTarget(level, worldSample.pos(), worldSample.medium(), 1.0F),
                        worldSample.medium(),
                        worldSample.pos(),
                        MudCoverageAppearanceSnapshot.at(level, worldSample.pos(), worldSample.medium()),
                        MudVisualSource.capture(level, worldSample.pos()),
                        debugWorldTrace(
                                "surface-pixel-adjacent",
                                level,
                                point,
                                worldSample.pos(),
                                worldSample.medium()));
            }
            SableCompat.MudVolumeProbe probe =
                    threadState.sableVolumeCoverageProbe;
            SableCompat.MudVolumeSample sample = probe == null
                    ? null
                    : probe.sample(point, 0.004D);
            return sample == null
                    || !producesMudPollution(level, sample.pos(), sample.medium())
                    ? MudSample.NONE
                    : MudSample.sableConfirmed(
                            MudCoverageRules.contactTarget(level, sample.medium(), 1.0F),
                            sample.medium(),
                            sample.pos(),
                            MudCoverageAppearanceSnapshot.at(
                                    level, sample.pos(), sample.medium()),
                            sample.visualSource(),
                            0.0D,
                            null);
        }
        if (!producesMudPollution(level, pos, medium)) {
            return MudSample.NONE;
        }
        if (!MudBlock.supportsVerticalSinking(state, medium)) {
            return MudBlock.containsLocalPoint(
                    level, pos, state, medium,
                    point.subtract(pos.getX(), pos.getY(), pos.getZ()),
                    0.004D)
                    ? new MudSample(
                            MudCoverageRules.contactTarget(level, pos, medium, 1.0F),
                            medium,
                            pos,
                            MudCoverageAppearanceSnapshot.at(level, pos, medium),
                            MudVisualSource.capture(level, pos),
                            debugWorldTrace(
                                    "surface-pixel-oriented",
                                    level,
                                    point,
                                    pos,
                                    medium))
                    : MudSample.NONE;
        }
        SurfaceWorldColumn column = worldColumns.column(level, pos, medium);
        BlockState columnTopState = level.getBlockState(column.topPos());
        double columnSurfaceY = column.topPos().getY()
                + MudMediumRuntime.surfaceHeightAt(
                        level, column.topPos(), columnTopState,
                        column.surfaceMedium(), point.x, point.z);
        if (!Double.isFinite(columnSurfaceY)
                || point.y > columnSurfaceY + 0.001D
                || point.y < column.bottomY() - 0.001D) {
            return MudSample.NONE;
        }
        return new MudSample(MudCoverageRules.contactTarget(level, pos, medium, 1.0F), medium, pos,
                MudCoverageAppearanceSnapshot.at(level, pos, medium),
                MudVisualSource.capture(level, pos),
                debugWorldTrace("surface-pixel", level, point, pos, medium,
                        column.surfaceMedium(), column.topPos(), column.bottomPos()));
    }

    private static boolean ordinaryCoverageEnabled(
            Level level, BlockPos profilePos, SinkingMedium medium) {
        var profile = MudMediumRuntime.assimilationProfile(level, profilePos, medium);
        return !profile.enabled() || profile.ordinaryCoverageEnabled();
    }

    private static float updateVisionCoverage(ServerPlayer player, Level level, MudPlayerData data) {
        data.clearVisionCoverage();
        float total = 0.0F;
        double height = player.getBbHeight();
        double frontOffset = MudVisionSamplingLayout.frontOffset();
        int visionRows = MudBodyPart.VISION_BANDS;
        int visionLanes = MudBodyPart.VISION_LANES;
        double minHeightFactor = MudVisionSamplingLayout.minHeightFactor();
        double maxHeightFactor = MudVisionSamplingLayout.maxHeightFactor();
        double faceRadius = MudVisionSamplingLayout.faceRadius();
        double bandSpan = height * (maxHeightFactor - minHeightFactor) / visionRows;
        double laneSpan = faceRadius * 2.0D / visionLanes;
        Vec3 feet = playerFeetPosition(player);
        Vec3 eye = playerEyePosition(player);
        double eyeOffset = eye.y - feet.y;
        VisionBasis basis = visionBasis(player);
        for (int band = 0; band < visionRows; band++) {
            double t = (band + 0.5D) / visionRows;
            double centerY = height * Mth.lerp(t, minHeightFactor, maxHeightFactor);
            double verticalOffset = centerY - eyeOffset;
            for (int lane = 0; lane < visionLanes; lane++) {
                double sideLane = laneOffset(faceRadius, lane, visionLanes);
                MudSample sample = sampleVisionCell(player, level, basis, sideLane, verticalOffset, laneSpan * 0.48D, bandSpan * 0.48D, frontOffset);
                if (sample.strength() <= 0.0F) {
                    continue;
                }

                data.setVisionCoverage(band, lane, sample.strength(),
                        sample.medium(), sample.visualSource());
                total += sample.strength();
            }
        }
        return total / (visionRows * visionLanes);
    }

    private static void markSurfaceTouched(MudPlayerData data, MudBodyPart part, int row, MudSurface surface, int column,
            MudSample sample, CoverageThreadState threadState) {
        float strength = sample.strength();
        if (strength <= 0.0F) {
            return;
        }

        SurfaceSampleFrame frame = threadState.activeSurfaceSampleFrame;
        if (frame != null) {
            frame.offer(part, surface, row, column, sample);
            return;
        }

        applySurfaceSample(data, part, surface, row, column, sample);
    }

    private static void applySurfaceSample(MudPlayerData data, MudBodyPart part, MudSurface surface, int row, int column,
            MudSample sample) {
        float strength = sample.strength();

        float current = data.surfacePixelCoverage(part, surface, row, column);
        SinkingMedium currentMedium = data.surfacePixelMedium(part, surface, row, column);
        if (!acceptSurfaceSample(data, part, surface, row, column, current, currentMedium, sample)) {
            return;
        }
        int currentAppearance = data.surfacePixelAppearance(part, surface, row, column);
        long currentVisualSource = data.surfacePixelVisualSource(
                part, surface, row, column);
        if (strength <= current && currentMedium == sample.medium()
                && currentAppearance == sample.appearance()
                && currentVisualSource == sample.visualSource()) {
            return;
        }

        float response = part == MudBodyPart.LEFT_ARM || part == MudBodyPart.RIGHT_ARM ? 0.56F : 0.58F;
        float nextCoverage = strength > current ? current + (strength - current) * response : current;
        data.setSurfacePixelCoverage(part, surface, row, column, nextCoverage,
                sample.medium(), sample.appearance(), sample.visualSource());
        debugSurfaceTouch(part, surface, row, column, current, currentMedium, nextCoverage, sample);
    }

    private static boolean acceptSurfaceSample(MudPlayerData data, MudBodyPart part, MudSurface surface, int row, int column,
            float currentCoverage, SinkingMedium currentMedium, MudSample sample) {
        if (!sample.sable()) {
            data.clearSableSurfaceCandidate(part, surface, row, column);
            return true;
        }

        if (sample.confirmedSableLayer()) {
            data.clearSableSurfaceCandidate(part, surface, row, column);
            return true;
        }

        // Physicalized Sable blocks can report adjacent local layers for the same
        // visual body pixel while an assembly/player is moving. Keep normal world
        // contact immediate, but require Sable-created/switching stains to be
        // stable for a couple of ticks before mutating persistent skin coverage.
        if (sample.medium() != data.medium
                && currentMedium != sample.medium()
                && sample.layerDepth() > SABLE_LAYER_ENTRY_PAINT_DEPTH) {
            data.clearSableSurfaceCandidate(part, surface, row, column);
            debugSableRejectedSurface(part, surface, row, column, currentCoverage, currentMedium, "deep-layer-entry", sample);
            return false;
        }

        if (currentCoverage > SURFACE_MEDIUM_REFRESH_THRESHOLD && currentMedium == sample.medium()) {
            data.clearSableSurfaceCandidate(part, surface, row, column);
            return true;
        }

        if (currentCoverage > SABLE_STRONG_SWITCH_THRESHOLD
                && currentMedium != sample.medium()
                && sample.strength() < SABLE_STRONG_SWITCH_MIN_STRENGTH) {
            data.clearSableSurfaceCandidate(part, surface, row, column);
            debugSableRejectedSurface(part, surface, row, column, currentCoverage, currentMedium, "weak-switch", sample);
            return false;
        }

        int requiredTicks = currentCoverage > SABLE_STRONG_SWITCH_THRESHOLD && currentMedium != sample.medium()
                ? SABLE_MEDIUM_SWITCH_CONFIRM_TICKS
                : SABLE_NEW_SURFACE_CONFIRM_TICKS;
        int pendingTicks = data.noteSableSurfaceCandidate(part, surface, row, column, sample.medium(), data.lastProcessedTick);
        if (pendingTicks >= requiredTicks) {
            data.clearSableSurfaceCandidate(part, surface, row, column);
            return true;
        }

        debugSablePendingSurface(part, surface, row, column, currentCoverage, currentMedium, pendingTicks, requiredTicks, sample);
        return false;
    }

    private static void debugSableRejectedSurface(MudBodyPart part, MudSurface surface, int row, int column,
            float previousCoverage, SinkingMedium previousMedium, String reason, MudSample sample) {
        CoverageDebugContext context = COVERAGE_THREAD_STATE.get().coverageDebugContext;
        if (context == null || !CoverageDebugLog.enabled()) {
            return;
        }

        context.sableRejectedCells++;
        boolean mediumMismatch = sample.medium() != context.contactMedium;
        if (mediumMismatch) {
            context.sableRejectedMismatchCells++;
        }
        if (context.rejectedLoggedCells >= 4 && !mediumMismatch) {
            return;
        }
        if (context.rejectedLoggedCells >= 10) {
            return;
        }

        context.rejectedLoggedCells++;
        CoverageDebugLog.event(context.player, "sable-rejected",
                "reason=" + reason
                        + " contactMedium=" + context.contactMedium.serializedName()
                        + " contactSurfaceY=" + round3(context.surfaceY)
                        + " contactDepth=" + round3(context.depth)
                        + " cell=" + part.name() + "/" + surface.name() + "/row" + row + "/column" + column
                        + " prev=" + previousMedium.serializedName() + ":" + round3(previousCoverage)
                        + " candidate=" + sample.medium().serializedName() + ":" + round3(sample.strength())
                        + " layerDepth=" + round3(sample.layerDepth())
                        + (sample.trace() == null ? " trace=none" : " trace={" + sample.trace().describe() + "}"));
    }

    private static void debugSablePendingSurface(MudBodyPart part, MudSurface surface, int row, int column,
            float previousCoverage, SinkingMedium previousMedium, int pendingTicks, int requiredTicks, MudSample sample) {
        CoverageDebugContext context = COVERAGE_THREAD_STATE.get().coverageDebugContext;
        if (context == null || !CoverageDebugLog.enabled()) {
            return;
        }

        context.sablePendingCells++;
        boolean mediumMismatch = sample.medium() != context.contactMedium;
        if (mediumMismatch) {
            context.sablePendingMismatchCells++;
        }
        if (context.pendingLoggedCells >= 4 && !mediumMismatch) {
            return;
        }
        if (context.pendingLoggedCells >= 10) {
            return;
        }

        context.pendingLoggedCells++;
        CoverageDebugLog.event(context.player, "sable-pending",
                "contactMedium=" + context.contactMedium.serializedName()
                        + " contactSurfaceY=" + round3(context.surfaceY)
                        + " contactDepth=" + round3(context.depth)
                        + " cell=" + part.name() + "/" + surface.name() + "/row" + row + "/column" + column
                        + " prev=" + previousMedium.serializedName() + ":" + round3(previousCoverage)
                        + " candidate=" + sample.medium().serializedName() + ":" + round3(sample.strength())
                        + " pending=" + pendingTicks + "/" + requiredTicks
                        + (sample.trace() == null ? " trace=none" : " trace={" + sample.trace().describe() + "}"));
    }

    private static void debugSurfaceTouch(MudBodyPart part, MudSurface surface, int row, int column,
            float previousCoverage, SinkingMedium previousMedium, float nextCoverage, MudSample sample) {
        CoverageDebugContext context = COVERAGE_THREAD_STATE.get().coverageDebugContext;
        if (context == null || !CoverageDebugLog.enabled()) {
            return;
        }

        context.changedCells++;
        if (sample.trace() != null && sample.trace().kind().startsWith("sable")) {
            context.sableHitCells++;
        }
        boolean mediumMismatch = sample.medium() != context.contactMedium;
        if (mediumMismatch) {
            context.mediumMismatchCells++;
        }

        boolean shouldLog = context.loggedCells < 6 || mediumMismatch;
        if (!shouldLog || context.loggedCells >= 16) {
            return;
        }

        context.loggedCells++;
        CoverageDebugLog.event(context.player, "surface-touch",
                "contactMedium=" + context.contactMedium.serializedName()
                        + " contactSurfaceY=" + round3(context.surfaceY)
                        + " contactDepth=" + round3(context.depth)
                        + " cell=" + part.name() + "/" + surface.name() + "/row" + row + "/column" + column
                        + " prev=" + previousMedium.serializedName() + ":" + round3(previousCoverage)
                        + " next=" + sample.medium().serializedName() + ":" + round3(nextCoverage)
                        + " sampleStrength=" + round3(sample.strength())
                        + (sample.trace() == null ? " trace=none" : " trace={" + sample.trace().describe() + "}"));
    }

    private static double laneOffset(double radius, int lane, int laneCount) {
        return -radius + radius * 2.0D * ((lane + 0.5D) / laneCount);
    }

    private static Vec3 movementPoint(Player player, Vec3 movementDirection,
            double sideOffset, double yOffset, double forwardOffset) {
        Vec3 feet = playerFeetPosition(player);
        double rightX = movementDirection.z;
        double rightZ = -movementDirection.x;
        return new Vec3(
                feet.x + rightX * sideOffset + movementDirection.x * forwardOffset,
                feet.y + yOffset,
                feet.z + rightZ * sideOffset + movementDirection.z * forwardOffset);
    }

    private static Vec3 playerFeetPosition(Player player) {
        return player.position();
    }

    private static Vec3 playerEyePosition(Player player) {
        return player.getEyePosition();
    }

    private static MudSample sampleSubmergedMud(Player player, Level level, Vec3 point, float coverage, double radius) {
        MudSample center = sampleSubmergedMudPoint(player, level, point, coverage);
        if (center.strength() > 0.0F) {
            return center;
        }

        MudSample best = MudSample.NONE;
        best = strongest(best, sampleSubmergedMudPoint(player, level, point.add(radius, 0.0D, 0.0D), coverage * 0.86F));
        best = strongest(best, sampleSubmergedMudPoint(player, level, point.add(-radius, 0.0D, 0.0D), coverage * 0.86F));
        best = strongest(best, sampleSubmergedMudPoint(player, level, point.add(0.0D, radius, 0.0D), coverage * 0.72F));
        best = strongest(best, sampleSubmergedMudPoint(player, level, point.add(0.0D, -radius, 0.0D), coverage * 0.92F));
        best = strongest(best, sampleSubmergedMudPoint(player, level, point.add(0.0D, 0.0D, radius), coverage * 0.86F));
        best = strongest(best, sampleSubmergedMudPoint(player, level, point.add(0.0D, 0.0D, -radius), coverage * 0.86F));
        return best;
    }

    private static MudSample sampleVisionCell(Player player, Level level, VisionBasis basis, double sideOffset, double verticalOffset,
            double halfWidth, double halfHeight, double frontOffset) {
        MudSample best = sampleVisionCellPoint(player, level, basis, sideOffset, verticalOffset, frontOffset, 1.0F);
        best = strongest(best, sampleVisionCellPoint(player, level, basis, sideOffset - halfWidth, verticalOffset, frontOffset, 0.92F));
        best = strongest(best, sampleVisionCellPoint(player, level, basis, sideOffset + halfWidth, verticalOffset, frontOffset, 0.92F));
        best = strongest(best, sampleVisionCellPoint(player, level, basis, sideOffset, verticalOffset - halfHeight, frontOffset, VISION_FACE_EDGE_PROBE_COVERAGE));
        best = strongest(best, sampleVisionCellPoint(player, level, basis, sideOffset, verticalOffset + halfHeight, frontOffset, 0.88F));
        best = strongest(best, sampleVisionCellPoint(player, level, basis, sideOffset - halfWidth, verticalOffset - halfHeight, frontOffset, 0.82F));
        best = strongest(best, sampleVisionCellPoint(player, level, basis, sideOffset + halfWidth, verticalOffset - halfHeight, frontOffset, 0.82F));
        best = strongest(best, sampleVisionCellPoint(player, level, basis, sideOffset - halfWidth, verticalOffset + halfHeight, frontOffset, 0.80F));
        best = strongest(best, sampleVisionCellPoint(player, level, basis, sideOffset + halfWidth, verticalOffset + halfHeight, frontOffset, 0.80F));
        return best;
    }

    private static MudSample sampleVisionCellPoint(Player player, Level level, VisionBasis basis, double sideOffset, double verticalOffset,
            double frontOffset, float coverage) {
        Vec3 facePoint = basis.origin()
                .add(basis.right().scale(sideOffset))
                .add(basis.up().scale(verticalOffset))
                .add(basis.forward().scale(frontOffset));
        return sampleSubmergedMud(player, level, facePoint, coverage, 0.010D);
    }

    private static VisionBasis visionBasis(Player player) {
        float yaw = player.getYRot() * ((float) Math.PI / 180.0F);
        float pitch = player.getXRot() * ((float) Math.PI / 180.0F);
        double sinYaw = Mth.sin(yaw);
        double cosYaw = Mth.cos(yaw);
        double sinPitch = Mth.sin(pitch);
        double cosPitch = Mth.cos(pitch);
        Vec3 forward = new Vec3(-sinYaw * cosPitch, -sinPitch, cosYaw * cosPitch).normalize();
        Vec3 right = new Vec3(cosYaw, 0.0D, sinYaw).normalize();
        Vec3 up = forward.cross(right).normalize();
        return new VisionBasis(playerEyePosition(player), forward, right, up);
    }

    private static MudSample sampleSubmergedMudPoint(Player player, Level level, Vec3 point, float coverage) {
        BlockPos pos = BlockPos.containing(point);
        BlockState state = level.getBlockState(pos);
        SinkingMedium medium = ModBlocks.mediumOf(state.getBlock());
        if (medium == null) {
            WorldVolumeSample worldSample =
                    sampleWorldMudVolume(level, point, 0.010D);
            if (worldSample != null
                    && producesMudPollution(level, worldSample.pos(), worldSample.medium())) {
                return new MudSample(
                        coverage,
                        worldSample.medium(),
                        worldSample.pos(),
                        MudCoverageAppearanceSnapshot.at(level, worldSample.pos(), worldSample.medium()),
                        MudVisualSource.capture(level, worldSample.pos()),
                        debugWorldTrace(
                                "submerged-world-adjacent",
                                level,
                                point,
                                worldSample.pos(),
                                worldSample.medium()));
            }
            return sampleSableSubmergedMudPoint(player, level, point, coverage);
        }
        if (!producesMudPollution(level, pos, medium)) {
            return MudSample.NONE;
        }
        if (!MudBlock.supportsVerticalSinking(state, medium)) {
            return MudBlock.containsLocalPoint(
                    level, pos, state, medium,
                    point.subtract(pos.getX(), pos.getY(), pos.getZ()),
                    0.010D)
                    ? new MudSample(
                            coverage,
                            medium,
                            pos,
                            MudCoverageAppearanceSnapshot.at(level, pos, medium),
                            MudVisualSource.capture(level, pos),
                            debugWorldTrace(
                                    "submerged-world-oriented",
                                    level,
                                    point,
                                    pos,
                                    medium))
                    : MudSample.NONE;
        }

        BlockPos topPos = findTopSinking(level, pos);
        BlockPos bottomPos = findBottomSinking(level, topPos);
        BlockState topState = level.getBlockState(topPos);
        SinkingMedium topMedium = ModBlocks.mediumOf(topState.getBlock());
        SinkingMedium surfaceMedium = topMedium == null ? medium : topMedium;
        double surfaceY = topPos.getY() + MudMediumRuntime.surfaceHeightAt(
                level, topPos, topState, surfaceMedium, point.x, point.z);
        if (!Double.isFinite(surfaceY)
                || point.y < bottomPos.getY() - 0.030D) {
            return MudSample.NONE;
        }

        double pointDepth = surfaceY - point.y;
        if (pointDepth <= -0.040D) {
            return MudSample.NONE;
        }

        float strength = (float) (coverage * smoothStep(Mth.clamp((pointDepth + 0.045D) / 0.135D, 0.0D, 1.0D)));
        return strength <= 0.0F ? MudSample.NONE
                : new MudSample(strength, medium, pos,
                        MudCoverageAppearanceSnapshot.at(level, pos, medium),
                        MudVisualSource.capture(level, pos),
                        debugWorldTrace("submerged-world", level, point, pos, medium, surfaceMedium, topPos, bottomPos));
    }

    private static MudSample sampleSableSubmergedMudPoint(Entity entity, Level level, Vec3 point, float coverage) {
        SableCompat.MudVolumeProbe volumeProbe =
                COVERAGE_THREAD_STATE.get().sableVolumeCoverageProbe;
        if (volumeProbe != null) {
            SableCompat.MudVolumeSample volume =
                    volumeProbe.sample(point, 0.010D);
            if (volume != null
                    && producesMudPollution(level, volume.pos(), volume.medium())) {
                return MudSample.sableConfirmed(
                        coverage,
                        volume.medium(),
                        volume.pos(),
                        MudCoverageAppearanceSnapshot.at(
                                level, volume.pos(), volume.medium()),
                        volume.visualSource(),
                        0.0D,
                        null);
            }
            // A frozen probe is authoritative for the whole coverage pass. A miss
            // means this point is outside every nearby physicalized mud volume;
            // falling through would repeat Sable's reflected broadphase per pixel.
            return MudSample.NONE;
        }
        SableCoverageContext context = COVERAGE_THREAD_STATE.get().sableCoverageContext;
        if (context != null && entity instanceof Player) {
            return sampleSableContextSubmergedMudPoint(level, point, coverage, context);
        }

        SinkingSample sample = SableCompat.sampleSinking(level, point, entity);
        if (sample == null
                || !producesMudPollution(level, sample.pos(), sample.medium())) {
            return MudSample.NONE;
        }

        double surfaceLocalY = sample.pos().getY()
                + MudMediumRuntime.surfaceHeightAt(
                        level, sample.pos(), sample.state(), sample.medium(),
                        sample.localPoint().x, sample.localPoint().z);
        if (!Double.isFinite(surfaceLocalY)
                || SableCompat.isWorldPointAboveLocalSurface(sample, surfaceLocalY, 0.040D)
                || sample.localPoint().y < sample.pos().getY() - 0.030D) {
            return MudSample.NONE;
        }

        double pointDepth = surfaceLocalY - sample.localPoint().y;
        if (pointDepth <= -0.040D) {
            return MudSample.NONE;
        }

        float strength = (float) (coverage * smoothStep(Mth.clamp((pointDepth + 0.045D) / 0.135D, 0.0D, 1.0D)));
        SinkingMedium medium = sableColumnMedium(sample, 0.08D);
        return strength <= 0.0F ? MudSample.NONE
                : MudSample.sable(strength, medium, sample.pos(),
                        MudCoverageAppearanceSnapshot.at(level, sample.pos(), medium),
                        medium == sample.topMedium()
                                ? sample.topVisualSource() : sample.visualSource(),
                        pointDepth,
                        debugSableTrace("sable-submerged", sample, medium, surfaceLocalY));
    }

    private static MudSample sampleSableContextSubmergedMudPoint(Level level, Vec3 point, float coverage, SableCoverageContext context) {
        SableLayerPoint layerPoint = context.layerPoint(point);
        if (layerPoint == null
                || !producesMudPollution(
                        level, layerPoint.layer().pos(), layerPoint.layer().medium())) {
            return MudSample.NONE;
        }

        double pointDepth = layerPoint.depth();
        if (pointDepth <= -0.040D || pointDepth > context.availableDepth() + 0.030D) {
            return MudSample.NONE;
        }

        float strength = (float) (coverage * smoothStep(Mth.clamp((pointDepth + 0.045D) / 0.135D, 0.0D, 1.0D)));
        return strength <= 0.0F ? MudSample.NONE
                : MudSample.sableConfirmed(strength, layerPoint.layer().medium(),
                        layerPoint.layer().pos(),
                        MudCoverageAppearanceSnapshot.at(
                                level, layerPoint.layer().pos(), layerPoint.layer().medium()),
                        layerPoint.layer().visualSource(), pointDepth,
                        debugSableContextTrace("sable-session-submerged", point, context, layerPoint));
    }

    private static boolean producesMudPollution(
            Level level, BlockPos profilePos, SinkingMedium medium) {
        return medium != null
                && (MudCoverageRules.contactTarget(level, profilePos, medium, 1.0F) > 0.001F
                || MudMediumRuntime.assimilationProfile(level, profilePos, medium).enabled());
    }

    private static CoverageDebugLog.SampleTrace debugWorldTrace(String kind, Level level, Vec3 point, BlockPos pos,
            SinkingMedium sampledMedium) {
        if (!CoverageDebugLog.enabled()) {
            return null;
        }

        BlockPos topPos = findTopSinking(level, pos);
        BlockPos bottomPos = findBottomSinking(level, topPos);
        SinkingMedium topMedium = ModBlocks.mediumOf(level.getBlockState(topPos).getBlock());
        SinkingMedium surfaceMedium = topMedium == null ? sampledMedium : topMedium;
        return debugWorldTrace(kind, level, point, pos, sampledMedium, surfaceMedium, topPos, bottomPos);
    }

    private static CoverageDebugLog.SampleTrace debugWorldTrace(String kind, Level level, Vec3 point, BlockPos pos,
            SinkingMedium sampledMedium, SinkingMedium surfaceMedium, BlockPos topPos, BlockPos bottomPos) {
        if (!CoverageDebugLog.enabled()) {
            return null;
        }

        double surfaceY = topPos.getY() + MudMediumRuntime.surfaceHeightAt(
                level, topPos, level.getBlockState(topPos), surfaceMedium,
                point.x, point.z);
        return CoverageDebugLog.trace(kind, point,
                "block=" + blockPosString(pos)
                        + " raw=" + sampledMedium.serializedName()
                        + " surface=" + surfaceMedium.serializedName()
                        + " top=" + blockPosString(topPos)
                        + " bottom=" + blockPosString(bottomPos)
                        + " depth=" + round3(surfaceY - point.y));
    }

    private static CoverageDebugLog.SampleTrace debugSableTrace(String kind, SinkingSample sample,
            SinkingMedium finalMedium, double surfaceLocalY) {
        if (!CoverageDebugLog.enabled()) {
            return null;
        }

        SinkingMedium topMedium = sample.topMedium() == null ? sample.medium() : sample.topMedium();
        boolean aboveSurface = SableCompat.isWorldPointAboveLocalSurface(sample, surfaceLocalY, 0.0D);
        return CoverageDebugLog.trace(kind, sample.worldPoint(),
                "pos=" + blockPosString(sample.pos())
                        + " raw=" + sample.medium().serializedName()
                        + " final=" + finalMedium.serializedName()
                        + " local=" + CoverageDebugLog.vec(sample.localPoint())
                        + " layerSurfaceY=" + round3(surfaceLocalY)
                        + " top=" + blockPosString(sample.topPos()) + ':' + topMedium.serializedName()
                        + " bottom=" + blockPosString(sample.bottomPos())
                        + " localDepth=" + round3(surfaceLocalY - sample.localPoint().y)
                        + " aboveWorldSurface=" + aboveSurface);
    }

    private static CoverageDebugLog.SampleTrace debugSableContextTrace(String kind, Vec3 worldPoint,
            SableCoverageContext context, SableLayerPoint layerPoint) {
        if (!CoverageDebugLog.enabled()) {
            return null;
        }

        return CoverageDebugLog.trace(kind, worldPoint,
                "contact=" + context.contactMedium().serializedName()
                        + " final=" + layerPoint.layer().medium().serializedName()
                        + " surfaceY=" + round3(context.surfaceY())
                        + " availableDepth=" + round3(context.availableDepth())
                        + " local=" + CoverageDebugLog.vec(layerPoint.localPoint())
                        + " layerTop=" + round3(layerPoint.layer().topCoordinate())
                        + " layerBottom=" + round3(layerPoint.layer().bottomCoordinate())
                        + " depth=" + round3(layerPoint.depth()));
    }

    private static SinkingMedium sableColumnMedium(SinkingSample sample, double topLayerBias) {
        return sample.medium();
    }

    private static MudSample strongest(MudSample first, MudSample second) {
        return second.strength() > first.strength() ? second : first;
    }

    private static float calculateVisionObstruction(ServerPlayer player, double surfaceY, MudPlayerData data) {
        double eyeDepth = surfaceY - playerEyePosition(player).y;
        return (float) smoothStep(Mth.clamp((eyeDepth - 0.02D) / 0.74D, 0.0D, 1.0D));
    }

    private static float calculateCoverage(Player player, double surfaceY, double depth, SinkingMedium medium) {
        double bodyProgress = (depth - player.getBbHeight() * 0.36D) / (player.getBbHeight() * 0.74D);
        double eyeProgress = (surfaceY - playerEyePosition(player).y + 0.34D) / 0.58D;
        double target = Math.max(
                Mth.clamp(bodyProgress, 0.0D, 0.66D),
                Mth.clamp(eyeProgress, 0.0D, 1.0D));
        return MudCoverageRules.contactTarget(
                player.level(), medium, (float) (target * medium.coverageScale()));
    }

    private static float calculateSableCoverage(Player player, double depth, SinkingMedium medium) {
        double bodyProgress = depth / Math.max(0.1D, player.getBbHeight());
        return MudCoverageRules.contactTarget(
                player.level(), medium, (float) (bodyProgress * medium.coverageScale()));
    }

    private static void tickMudSuffocation(LivingEntity entity) {
        int air = entity.getAirSupply() - 1;
        entity.setAirSupply(air);
        if (air <= -20) {
            entity.setAirSupply(0);
            entity.hurt(entity.damageSources().drown(), 2.0F);
        }
    }

    static void recoverAir(ServerPlayer player) {
        if (player.getAirSupply() < player.getMaxAirSupply()) {
            player.setAirSupply(Math.min(player.getAirSupply() + 4, player.getMaxAirSupply()));
        }
    }

    private static BlockPos findTopSinking(Level level, BlockPos start) {
        return MudColumnResolver.findTop(level, start);
    }

    private static BlockPos findBottomSinking(Level level, BlockPos start) {
        return MudColumnResolver.findBottom(level, start);
    }

    private static double availableDepth(double surfaceY, BlockPos bottomPos) {
        return MudColumnResolver.availableDepth(surfaceY, bottomPos);
    }

    private static WorldVolumeSample ordinaryMudVolumeAt(
            Level level, Vec3 point, double tolerance) {
        BlockPos center = BlockPos.containing(point);
        WorldVolumeSample sample = ordinaryMudVolumeAt(
                level, point, center, tolerance);
        if (sample != null) {
            return sample;
        }
        for (Direction direction : DIRECTIONS) {
            sample = ordinaryMudVolumeAt(
                    level,
                    point,
                    center.relative(direction),
                    tolerance);
            if (sample != null) {
                return sample;
            }
        }
        return null;
    }

    private static WorldVolumeSample sampleWorldMudVolume(
            Level level, Vec3 point, double tolerance) {
        return sampleWorldMudVolume(
                level, point, tolerance, COVERAGE_THREAD_STATE.get());
    }

    private static WorldVolumeSample sampleWorldMudVolume(
            Level level, Vec3 point, double tolerance,
            CoverageThreadState threadState) {
        WorldMudVolumeProbe probe = threadState.worldVolumeCoverageProbe;
        return probe == null
                ? ordinaryMudVolumeAt(level, point, tolerance)
                : probe.sample(point, tolerance);
    }

    private static WorldVolumeSample ordinaryMudVolumeAt(
            Level level, Vec3 point, BlockPos pos, double tolerance) {
        BlockState state = level.getBlockState(pos);
        SinkingMedium medium = ModBlocks.mediumOf(state.getBlock());
        if (medium == null
                || !MudMediumRuntime.enabled(level, pos, medium)
                || !MudBlock.containsLocalPoint(
                        level, pos, state, medium,
                        point.subtract(
                                pos.getX(), pos.getY(), pos.getZ()),
                        tolerance)) {
            return null;
        }
        return new WorldVolumeSample(
                pos.immutable(), state, medium);
    }

    static boolean isEntityEyeInSinking(LivingEntity entity) {
        Level level = entity.level();
        Vec3 eyePoint = entity instanceof Player player ? playerEyePosition(player) : new Vec3(entity.getX(), entity.getEyeY(), entity.getZ());
        BlockPos eyePos = BlockPos.containing(eyePoint);
        SinkingMedium medium = ModBlocks.mediumOf(level.getBlockState(eyePos).getBlock());
        if (medium == null) {
            return sampleSableSubmergedMudPoint(entity, level, eyePoint, 1.0F).strength() > 0.0F;
        }
        BlockState eyeState = level.getBlockState(eyePos);
        if (!MudBlock.supportsVerticalSinking(eyeState, medium)) {
            return MudBlock.containsLocalPoint(
                    level, eyePos, eyeState, medium,
                    eyePoint.subtract(
                            eyePos.getX(), eyePos.getY(), eyePos.getZ()),
                    0.004D);
        }

        BlockPos topPos = findTopSinking(level, eyePos);
        BlockState topState = level.getBlockState(topPos);
        SinkingMedium topMedium = ModBlocks.mediumOf(topState.getBlock());
        double surfaceY = topPos.getY()
                + MudMediumRuntime.surfaceHeightAt(
                        level, topPos, topState,
                        topMedium == null ? medium : topMedium,
                        eyePoint.x, eyePoint.z);
        return Double.isFinite(surfaceY) && eyePoint.y < surfaceY - 0.02D;
    }

    static void syncDebug(ServerPlayer player, MudPlayerData data, boolean active) {
        MudDebugSynchronizer.sync(player, data, active);
    }

    private static double round3(double value) {
        return MudCoverageDiagnostics.round3(value);
    }

    private static String debugCoverageSummary(MudPlayerData data) {
        return MudCoverageDiagnostics.coverageSummary(data);
    }

    private static String blockPosString(BlockPos pos) {
        return MudCoverageDiagnostics.blockPos(pos);
    }

    private record MudSample(float strength, SinkingMedium medium, BlockPos profilePos,
            int appearance, long visualSource,
            CoverageDebugLog.SampleTrace trace, boolean sable,
            double layerDepth, boolean confirmedSableLayer) {
        private static final MudSample NONE = new MudSample(0.0F, SinkingMedium.MUD);

        private MudSample(float strength, SinkingMedium medium) {
            this(strength, medium, null, MudCoverageAppearanceSnapshot.GLOBAL_FALLBACK,
                    0L, null, false, 0.0D, false);
        }

        private MudSample(float strength, SinkingMedium medium, CoverageDebugLog.SampleTrace trace) {
            this(strength, medium, null, MudCoverageAppearanceSnapshot.GLOBAL_FALLBACK,
                    0L, trace, false, 0.0D, false);
        }

        private MudSample(float strength, SinkingMedium medium, BlockPos profilePos, int appearance,
                CoverageDebugLog.SampleTrace trace) {
            this(strength, medium, profilePos, appearance, 0L,
                    trace, false, 0.0D, false);
        }

        private MudSample(float strength, SinkingMedium medium, BlockPos profilePos,
                int appearance, long visualSource,
                CoverageDebugLog.SampleTrace trace) {
            this(strength, medium, profilePos, appearance, visualSource,
                    trace, false, 0.0D, false);
        }

        private static MudSample sable(float strength, SinkingMedium medium, BlockPos profilePos,
                int appearance, long visualSource,
                double layerDepth, CoverageDebugLog.SampleTrace trace) {
            return new MudSample(
                    strength, medium, profilePos, appearance, visualSource,
                    trace, true, layerDepth, false);
        }

        private static MudSample sableConfirmed(float strength, SinkingMedium medium,
                BlockPos profilePos, int appearance, long visualSource,
                double layerDepth, CoverageDebugLog.SampleTrace trace) {
            return new MudSample(
                    strength, medium, profilePos, appearance, visualSource,
                    trace, true, layerDepth, true);
        }
    }

    private static final class CoverageDebugContext {
        private final ServerPlayer player;
        private final SinkingMedium contactMedium;
        private final double surfaceY;
        private final double depth;
        private int changedCells;
        private int loggedCells;
        private int pendingLoggedCells;
        private int rejectedLoggedCells;
        private int mediumMismatchCells;
        private int sableHitCells;
        private int sablePendingCells;
        private int sablePendingMismatchCells;
        private int sableRejectedCells;
        private int sableRejectedMismatchCells;

        private CoverageDebugContext(ServerPlayer player, SinkingMedium contactMedium, double surfaceY, double depth) {
            this.player = player;
            this.contactMedium = contactMedium;
            this.surfaceY = surfaceY;
            this.depth = depth;
        }
    }

    private record VisionBasis(Vec3 origin, Vec3 forward, Vec3 right, Vec3 up) {
    }

    private static final class SurfaceWorldColumnCache {
        private static final int CAPACITY = 16;
        private final SurfaceWorldColumn[] columns = new SurfaceWorldColumn[CAPACITY];
        private int size;

        private void begin() {
            size = 0;
        }

        private SurfaceWorldColumn column(Level level, BlockPos pos, SinkingMedium sampledMedium) {
            for (int i = 0; i < size; i++) {
                SurfaceWorldColumn column = columns[i];
                if (column.x() == pos.getX() && column.z() == pos.getZ()
                        && pos.getY() >= column.bottomY() && pos.getY() <= column.topPos().getY()) {
                    return column;
                }
            }

            BlockPos topPos = findTopSinking(level, pos);
            BlockPos bottomPos = findBottomSinking(level, topPos);
            BlockState topState = level.getBlockState(topPos);
            SinkingMedium topMedium = ModBlocks.mediumOf(topState.getBlock());
            SinkingMedium surfaceMedium = topMedium == null ? sampledMedium : topMedium;
            SurfaceWorldColumn column = new SurfaceWorldColumn(
                    pos.getX(),
                    pos.getZ(),
                    topPos,
                    bottomPos,
                    bottomPos.getY(),
                    topPos.getY() + MudMediumRuntime.surfaceHeight(
                            level, topPos, topState, surfaceMedium),
                    surfaceMedium);
            if (size < columns.length) {
                columns[size++] = column;
            }
            return column;
        }
    }

    private record SurfaceWorldColumn(int x, int z, BlockPos topPos, BlockPos bottomPos, double bottomY,
            double surfaceY, SinkingMedium surfaceMedium) {
    }

    private static final class CoverageThreadState {
        private CoverageDebugContext coverageDebugContext;
        private SableCoverageContext sableCoverageContext;
        private SableCompat.MudVolumeProbe sableVolumeCoverageProbe;
        private WorldMudVolumeProbe worldVolumeCoverageProbe;
        private final SurfaceSampleFrame sableSurfaceSampleFrame = new SurfaceSampleFrame();
        private final SurfaceWorldColumnCache worldSurfaceColumnCache = new SurfaceWorldColumnCache();
        private SurfaceSampleFrame activeSurfaceSampleFrame;
    }

    private static final class SurfaceSampleFrame {
        private final MudSample[] samples = new MudSample[MudBodyPart.SURFACE_COUNT];
        private final int[] touched = new int[MudBodyPart.SURFACE_COUNT];
        private final int[] offeredByMedium = new int[SinkingMedium.COUNT];
        private final int[] selectedByMedium = new int[SinkingMedium.COUNT];
        private final int[] selectedPartMask = new int[SinkingMedium.COUNT];
        private final int[] selectedBySurface = new int[MudSurface.COUNT];
        private final int[] selectedMinBand = new int[SinkingMedium.COUNT];
        private final int[] selectedMaxBand = new int[SinkingMedium.COUNT];
        private final int[] conflictPairs = new int[SinkingMedium.COUNT * SinkingMedium.COUNT];
        private int touchedCount;

        private void begin() {
            clear();
            Arrays.fill(offeredByMedium, 0);
            Arrays.fill(selectedByMedium, 0);
            Arrays.fill(selectedPartMask, 0);
            Arrays.fill(selectedBySurface, 0);
            Arrays.fill(selectedMinBand, Integer.MAX_VALUE);
            Arrays.fill(selectedMaxBand, -1);
            Arrays.fill(conflictPairs, 0);
        }

        private void offer(MudBodyPart part, MudSurface surface, int row, int column, MudSample sample) {
            offeredByMedium[sample.medium().id()]++;
            int index = MudSurfaceLayout.cellIndex(part, surface, row, column);
            MudSample current = samples[index];
            if (current == null) {
                samples[index] = sample;
                touched[touchedCount++] = index;
                return;
            }

            // The center probe is offered first. Keep its medium as the cell's
            // authority; later edge probes may only strengthen that same medium.
            if (current.medium() == sample.medium() && sample.strength() > current.strength()) {
                samples[index] = sample;
            } else if (current.medium() != sample.medium()) {
                int mediumCount = offeredByMedium.length;
                conflictPairs[current.medium().id() * mediumCount + sample.medium().id()]++;
            }
        }

        private void commit(MudPlayerData data) {
            for (int i = 0; i < touchedCount; i++) {
                int index = touched[i];
                MudSample sample = samples[index];
                MudBodyPart part = MudSurfaceLayout.part(index);
                MudSurface surface = MudSurfaceLayout.surface(index);
                int row = MudSurfaceLayout.row(index);
                int column = MudSurfaceLayout.column(index);
                int band = MudSurfaceLayout.legacyBand(part, surface, row);
                int mediumId = sample.medium().id();
                selectedByMedium[mediumId]++;
                selectedBySurface[surface.ordinal()]++;
                selectedPartMask[mediumId] |= 1 << part.ordinal();
                selectedMinBand[mediumId] = Math.min(selectedMinBand[mediumId], band);
                selectedMaxBand[mediumId] = Math.max(selectedMaxBand[mediumId], band);
                applySurfaceSample(data, part, surface, row, column, sample);
            }
        }

        private String describe() {
            StringBuilder builder = new StringBuilder(512);
            builder.append("cells=").append(touchedCount).append(" media=[");
            boolean wroteMedium = false;
            for (SinkingMedium medium : SinkingMedium.values()) {
                int id = medium.id();
                if (offeredByMedium[id] == 0 && selectedByMedium[id] == 0) {
                    continue;
                }
                if (wroteMedium) {
                    builder.append(';');
                }
                wroteMedium = true;
                builder.append(medium.serializedName())
                        .append(" offered=").append(offeredByMedium[id])
                        .append(" selected=").append(selectedByMedium[id]);
                if (selectedByMedium[id] > 0) {
                    builder.append(" bands=").append(selectedMinBand[id]).append('-').append(selectedMaxBand[id])
                            .append(" parts=");
                    int partMask = selectedPartMask[id];
                    boolean wrotePart = false;
                    for (MudBodyPart part : MUD_BODY_PARTS) {
                        if ((partMask & (1 << part.ordinal())) == 0) {
                            continue;
                        }
                        if (wrotePart) {
                            builder.append(',');
                        }
                        wrotePart = true;
                        builder.append(part.name());
                    }
                }
            }
            builder.append("] surfaces=[");
            for (int i = 0; i < MUD_SURFACES.length; i++) {
                if (i > 0) {
                    builder.append(',');
                }
                builder.append(MUD_SURFACES[i].name()).append(':').append(selectedBySurface[i]);
            }
            builder.append("] conflicts=[");
            boolean wroteConflict = false;
            int mediumCount = offeredByMedium.length;
            for (SinkingMedium selected : SinkingMedium.values()) {
                for (SinkingMedium rejected : SinkingMedium.values()) {
                    int count = conflictPairs[selected.id() * mediumCount + rejected.id()];
                    if (count == 0) {
                        continue;
                    }
                    if (wroteConflict) {
                        builder.append(';');
                    }
                    wroteConflict = true;
                    builder.append(selected.serializedName()).append('>')
                            .append(rejected.serializedName()).append(':').append(count);
                }
            }
            return builder.append(']').toString();
        }

        private void clear() {
            for (int i = 0; i < touchedCount; i++) {
                samples[touched[i]] = null;
            }
            touchedCount = 0;
        }
    }

}

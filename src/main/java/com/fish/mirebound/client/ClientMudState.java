package com.fish.mirebound.client;

import com.fish.mirebound.mud.MudBodyPart;
import com.fish.mirebound.mud.MudCapeLayout;
import com.fish.mirebound.mud.MudSurface;
import com.fish.mirebound.mud.MudSurfaceLayout;
import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.network.payload.MudCoverageDeltaPayload;
import com.fish.mirebound.network.payload.MudCoverageSyncPayload;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public final class ClientMudState {
    private static final float COVERAGE_EPSILON = 0.00025F;
    private static final float DISPLAY_SETTLE_EPSILON = 1.0F / 1024.0F;
    private static final int UNTRACKED_ENTITY_TTL_TICKS = 200;
    private static final long EMPTY_SIGNATURE_SEED = 1125899906842597L;
    private static final MudBodyPart[] BODY_PARTS = MudBodyPart.values();
    private static final MudSurface[] SURFACES = MudSurface.values();
    private static final MudCapeLayout.Side[] CAPE_SIDES = MudCapeLayout.Side.values();
    private static final CoverageState EMPTY_DISPLAY = new CoverageState();
    private static final Map<Integer, CoverageState> COVERAGE_BY_ENTITY = new ConcurrentHashMap<>();
    private static int struggleSendCooldown;

    private ClientMudState() {
    }

    public static void setCoverageFromServer(MudCoverageSyncPayload payload) {
        CoverageState state = COVERAGE_BY_ENTITY.computeIfAbsent(payload.entityId(), ignored -> new CoverageState());
        boolean targetChanged = false;
        float overallTargetCoverage = Mth.clamp(payload.coveragePermille() / 1000.0F, 0.0F, 1.0F);
        float overallTargetVision = Mth.clamp(payload.visionPermille() / 1000.0F, 0.0F, 1.0F);
        SinkingMedium overallTargetMedium = payload.medium();
        targetChanged |= state.targetCoverage != overallTargetCoverage
                || state.targetVisionObstruction != overallTargetVision
                || state.medium != overallTargetMedium
                || state.coveragePatternSeed != payload.coveragePatternSeed();
        state.targetCoverage = overallTargetCoverage;
        state.targetVisionObstruction = overallTargetVision;
        state.medium = overallTargetMedium;
        state.coveragePatternSeed = payload.coveragePatternSeed();
        for (MudBodyPart part : BODY_PARTS) {
            for (MudSurface surface : SURFACES) {
                MudSurfaceLayout.Face face = MudSurfaceLayout.face(part, surface);
                for (int row = 0; row < face.height(); row++) {
                    for (int column = 0; column < face.width(); column++) {
                        int index = MudSurfaceLayout.cellIndex(part, surface, row, column);
                        float targetCoverage = Mth.clamp(payload.surfacePixelCoveragePermille(part, surface, row, column) / 1000.0F,
                                0.0F, 1.0F);
                        byte targetMedium = (byte) payload.surfacePixelMedium(part, surface, row, column).id();
                        int targetAppearance = payload.surfacePixelAppearance(part, surface, row, column);
                        long targetVisualSource = payload.surfacePixelVisualSource(
                                part, surface, row, column);
                        targetChanged |= state.targetSurfaceCoverage[index] != targetCoverage
                                || state.targetSurfaceMedium[index] != targetMedium
                                || state.targetSurfaceAppearance[index] != targetAppearance
                                || state.targetSurfaceVisualSource[index] != targetVisualSource;
                        state.targetSurfaceCoverage[index] = targetCoverage;
                        state.targetSurfaceMedium[index] = targetMedium;
                        state.targetSurfaceAppearance[index] = targetAppearance;
                        state.targetSurfaceVisualSource[index] = targetVisualSource;
                        if (targetCoverage > COVERAGE_EPSILON) {
                            state.displaySurfaceMedium[index] = targetMedium;
                            state.displaySurfaceAppearance[index] = targetAppearance;
                            state.displaySurfaceVisualSource[index] = targetVisualSource;
                        }
                    }
                }
            }
        }
        for (MudCapeLayout.Side side : CAPE_SIDES) {
            for (int row = 0; row < MudCapeLayout.ROWS; row++) {
                for (int column = 0; column < MudCapeLayout.COLUMNS; column++) {
                    int index = MudCapeLayout.index(side, row, column);
                    float targetCoverage = Mth.clamp(
                            payload.capePixelCoveragePermille(side, row, column) / 1000.0F, 0.0F, 1.0F);
                    byte targetMedium = (byte) payload.capePixelMedium(side, row, column).id();
                    int targetAppearance = payload.capePixelAppearance(side, row, column);
                    long targetVisualSource = payload.capePixelVisualSource(side, row, column);
                    targetChanged |= state.targetCapeCoverage[index] != targetCoverage
                            || state.targetCapeMedium[index] != targetMedium
                            || state.targetCapeAppearance[index] != targetAppearance
                            || state.targetCapeVisualSource[index] != targetVisualSource;
                    state.targetCapeCoverage[index] = targetCoverage;
                    state.targetCapeMedium[index] = targetMedium;
                    state.targetCapeAppearance[index] = targetAppearance;
                    state.targetCapeVisualSource[index] = targetVisualSource;
                    if (targetCoverage > COVERAGE_EPSILON) {
                        state.displayCapeMedium[index] = targetMedium;
                        state.displayCapeAppearance[index] = targetAppearance;
                        state.displayCapeVisualSource[index] = targetVisualSource;
                    }
                }
            }
        }
        for (int band = 0; band < MudBodyPart.VISION_BANDS; band++) {
            for (int lane = 0; lane < MudBodyPart.VISION_LANES; lane++) {
                int visionIndex = visionIndex(band, lane);
                float targetCoverage = Mth.clamp(payload.visionCoveragePermille(band, lane) / 1000.0F, 0.0F, 1.0F);
                byte targetMedium = (byte) payload.visionMedium(band, lane).id();
                long targetVisualSource = payload.visionVisualSource(band, lane);
                targetChanged |= state.targetVisionCoverage[visionIndex] != targetCoverage
                        || state.targetVisionMedium[visionIndex] != targetMedium
                        || state.targetVisionVisualSource[visionIndex] != targetVisualSource;
                state.targetVisionCoverage[visionIndex] = targetCoverage;
                state.targetVisionMedium[visionIndex] = targetMedium;
                state.targetVisionVisualSource[visionIndex] = targetVisualSource;
                if (targetCoverage > COVERAGE_EPSILON) {
                    state.displayVisionMedium[visionIndex] = targetMedium;
                    state.displayVisionVisualSource[visionIndex] = targetVisualSource;
                }
            }
        }
        if (targetChanged) {
            state.animationSettled = false;
        }
        state.ticksSinceSync = 0;
    }

    public static void applyCoverageDeltaFromServer(MudCoverageDeltaPayload payload) {
        CoverageState state = COVERAGE_BY_ENTITY.computeIfAbsent(
                payload.entityId(), ignored -> new CoverageState());
        boolean targetChanged = false;
        float overallTargetCoverage = Mth.clamp(payload.coveragePermille() / 1000.0F, 0.0F, 1.0F);
        float overallTargetVision = Mth.clamp(payload.visionPermille() / 1000.0F, 0.0F, 1.0F);
        SinkingMedium overallTargetMedium = payload.medium();
        targetChanged |= state.targetCoverage != overallTargetCoverage
                || state.targetVisionObstruction != overallTargetVision
                || state.medium != overallTargetMedium
                || state.coveragePatternSeed != payload.coveragePatternSeed();
        state.targetCoverage = overallTargetCoverage;
        state.targetVisionObstruction = overallTargetVision;
        state.medium = overallTargetMedium;
        state.coveragePatternSeed = payload.coveragePatternSeed();

        int surfaceCount = minimumLength(
                payload.surfaceIndices(), payload.surfaceCoverage(),
                payload.surfaceMedium(), payload.surfaceAppearance(),
                payload.surfaceVisualSource());
        for (int offset = 0; offset < surfaceCount; offset++) {
            int index = payload.surfaceIndices()[offset];
            if (index < 0 || index >= MudSurfaceLayout.CELL_COUNT) {
                continue;
            }
            float targetCoverage = MudCoverageSyncPayload.unpackCoverageLevel(
                    payload.surfaceCoverage()[offset] & 0xFF);
            byte targetMedium = validMedium(payload.surfaceMedium()[offset]);
            int targetAppearance = payload.surfaceAppearance()[offset];
            long targetVisualSource = payload.surfaceVisualSource()[offset];
            targetChanged |= state.targetSurfaceCoverage[index] != targetCoverage
                    || state.targetSurfaceMedium[index] != targetMedium
                    || state.targetSurfaceAppearance[index] != targetAppearance
                    || state.targetSurfaceVisualSource[index] != targetVisualSource;
            state.targetSurfaceCoverage[index] = targetCoverage;
            state.targetSurfaceMedium[index] = targetMedium;
            state.targetSurfaceAppearance[index] = targetAppearance;
            state.targetSurfaceVisualSource[index] = targetVisualSource;
            if (targetCoverage > COVERAGE_EPSILON) {
                state.displaySurfaceMedium[index] = targetMedium;
                state.displaySurfaceAppearance[index] = targetAppearance;
                state.displaySurfaceVisualSource[index] = targetVisualSource;
            }
        }

        int capeCount = minimumLength(
                payload.capeIndices(), payload.capeCoverage(),
                payload.capeMedium(), payload.capeAppearance(),
                payload.capeVisualSource());
        for (int offset = 0; offset < capeCount; offset++) {
            int index = payload.capeIndices()[offset];
            if (index < 0 || index >= MudCapeLayout.CELL_COUNT) {
                continue;
            }
            float targetCoverage = MudCoverageSyncPayload.unpackCoverageLevel(
                    payload.capeCoverage()[offset] & 0xFF);
            byte targetMedium = validMedium(payload.capeMedium()[offset]);
            int targetAppearance = payload.capeAppearance()[offset];
            long targetVisualSource = payload.capeVisualSource()[offset];
            targetChanged |= state.targetCapeCoverage[index] != targetCoverage
                    || state.targetCapeMedium[index] != targetMedium
                    || state.targetCapeAppearance[index] != targetAppearance
                    || state.targetCapeVisualSource[index] != targetVisualSource;
            state.targetCapeCoverage[index] = targetCoverage;
            state.targetCapeMedium[index] = targetMedium;
            state.targetCapeAppearance[index] = targetAppearance;
            state.targetCapeVisualSource[index] = targetVisualSource;
            if (targetCoverage > COVERAGE_EPSILON) {
                state.displayCapeMedium[index] = targetMedium;
                state.displayCapeAppearance[index] = targetAppearance;
                state.displayCapeVisualSource[index] = targetVisualSource;
            }
        }

        int visionCount = Math.min(payload.visionIndices().length,
                Math.min(payload.visionCoverage().length,
                        Math.min(payload.visionMedium().length,
                                payload.visionVisualSource().length)));
        for (int offset = 0; offset < visionCount; offset++) {
            int index = payload.visionIndices()[offset];
            if (index < 0 || index >= MudBodyPart.VISION_COUNT) {
                continue;
            }
            float targetCoverage = MudCoverageSyncPayload.unpackCoverageLevel(
                    payload.visionCoverage()[offset] & 0xFF);
            byte targetMedium = validMedium(payload.visionMedium()[offset]);
            long targetVisualSource = payload.visionVisualSource()[offset];
            targetChanged |= state.targetVisionCoverage[index] != targetCoverage
                    || state.targetVisionMedium[index] != targetMedium
                    || state.targetVisionVisualSource[index] != targetVisualSource;
            state.targetVisionCoverage[index] = targetCoverage;
            state.targetVisionMedium[index] = targetMedium;
            state.targetVisionVisualSource[index] = targetVisualSource;
            if (targetCoverage > COVERAGE_EPSILON) {
                state.displayVisionMedium[index] = targetMedium;
                state.displayVisionVisualSource[index] = targetVisualSource;
            }
        }

        if (targetChanged) {
            state.animationSettled = false;
        }
        state.ticksSinceSync = 0;
    }

    private static int minimumLength(int[] indices, byte[] coverage, byte[] medium,
            int[] appearance, long[] visualSource) {
        return Math.min(Math.min(indices.length, coverage.length),
                Math.min(Math.min(medium.length, appearance.length), visualSource.length));
    }

    private static byte validMedium(byte medium) {
        int id = medium & 0xFF;
        return (byte) (id < SinkingMedium.COUNT ? id : SinkingMedium.MUD.id());
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        for (Map.Entry<Integer, CoverageState> tracked : COVERAGE_BY_ENTITY.entrySet()) {
            CoverageState state = tracked.getValue();
            Entity trackedEntity = minecraft.level == null ? null : minecraft.level.getEntity(tracked.getKey());
            if (trackedEntity instanceof Player player && player.isDeadOrDying()) {
                removeTrackedEntity(tracked.getKey(), state);
                continue;
            }
            state.renderSuppressed = trackedEntity instanceof Player player && player.isSpectator();
            state.ticksSinceSync++;
            if (minecraft.level != null && trackedEntity == null
                    && state.ticksSinceSync > UNTRACKED_ENTITY_TTL_TICKS) {
                removeTrackedEntity(tracked.getKey(), state);
                continue;
            }
            if (state.animationSettled) {
                continue;
            }

            boolean animationSettled = true;
            float coverageDelta = state.targetCoverage - state.displayCoverage;
            if (Math.abs(coverageDelta) <= DISPLAY_SETTLE_EPSILON) {
                state.displayCoverage = state.targetCoverage;
            } else {
                state.displayCoverage += coverageDelta * 0.20F;
                animationSettled = false;
            }
            float visionDelta = state.targetVisionObstruction - state.displayVisionObstruction;
            if (Math.abs(visionDelta) <= DISPLAY_SETTLE_EPSILON) {
                state.displayVisionObstruction = state.targetVisionObstruction;
            } else {
                state.displayVisionObstruction += visionDelta * 0.32F;
                animationSettled = false;
            }
            boolean hasVisiblePart = false;
            Arrays.fill(state.displayPartCoverage, 0.0F);
            Arrays.fill(state.displayBandCoverage, 0.0F);
            Arrays.fill(state.displayLegacySurfaceCoverage, 0.0F);
            Arrays.fill(state.displayLegacySurfaceMedium, (byte) SinkingMedium.MUD.id());
            long surfaceSignature = EMPTY_SIGNATURE_SEED;
            boolean hasQuantizedSurface = false;
            long surfaceMediumMask = 0L;
            for (int surfaceIndex = 0; surfaceIndex < MudSurfaceLayout.CELL_COUNT; surfaceIndex++) {
                float delta = state.targetSurfaceCoverage[surfaceIndex] - state.displaySurfaceCoverage[surfaceIndex];
                if (Math.abs(delta) <= DISPLAY_SETTLE_EPSILON) {
                    state.displaySurfaceCoverage[surfaceIndex] = state.targetSurfaceCoverage[surfaceIndex];
                } else {
                    state.displaySurfaceCoverage[surfaceIndex] += delta * 0.30F;
                    animationSettled = false;
                }
                if (state.targetSurfaceCoverage[surfaceIndex] > COVERAGE_EPSILON) {
                    state.displaySurfaceMedium[surfaceIndex] = state.targetSurfaceMedium[surfaceIndex];
                    state.displaySurfaceAppearance[surfaceIndex] = state.targetSurfaceAppearance[surfaceIndex];
                    state.displaySurfaceVisualSource[surfaceIndex] =
                            state.targetSurfaceVisualSource[surfaceIndex];
                } else if (state.displaySurfaceCoverage[surfaceIndex] <= COVERAGE_EPSILON) {
                    state.displaySurfaceCoverage[surfaceIndex] = 0.0F;
                    state.displaySurfaceMedium[surfaceIndex] = (byte) SinkingMedium.MUD.id();
                    state.displaySurfaceAppearance[surfaceIndex] = 0;
                    state.displaySurfaceVisualSource[surfaceIndex] = 0L;
                }
                float displayed = state.displaySurfaceCoverage[surfaceIndex];
                hasVisiblePart |= state.targetSurfaceCoverage[surfaceIndex] > COVERAGE_EPSILON || displayed > COVERAGE_EPSILON;
                int displayLevel = Mth.clamp(Math.round(displayed * 255.0F), 0, 255);
                hasQuantizedSurface |= displayLevel > 0;
                surfaceSignature = surfaceSignature * 31L + displayLevel;
                if (displayLevel > 0) {
                    surfaceSignature = surfaceSignature * 31L + (state.displaySurfaceMedium[surfaceIndex] & 0xFF);
                    surfaceSignature = surfaceSignature * 31L + state.displaySurfaceAppearance[surfaceIndex];
                    surfaceSignature = surfaceSignature * 31L
                            + Long.hashCode(state.displaySurfaceVisualSource[surfaceIndex]);
                    surfaceMediumMask |= 1L << (state.displaySurfaceMedium[surfaceIndex] & 0xFF);
                }
                if (displayed <= COVERAGE_EPSILON) {
                    continue;
                }
                MudBodyPart part = MudSurfaceLayout.part(surfaceIndex);
                MudSurface surface = MudSurfaceLayout.surface(surfaceIndex);
                int row = MudSurfaceLayout.row(surfaceIndex);
                int column = MudSurfaceLayout.column(surfaceIndex);
                int band = MudSurfaceLayout.legacyBand(part, surface, row);
                state.displayPartCoverage[part.ordinal()] = Math.max(state.displayPartCoverage[part.ordinal()], displayed);
                int bandIndex = bandIndex(part, band);
                state.displayBandCoverage[bandIndex] = Math.max(state.displayBandCoverage[bandIndex], displayed);
                int legacyIndex = MudSurfaceLayout.legacySurfaceIndex(part, band, surface,
                        MudSurfaceLayout.legacyLane(part, surface, column));
                if (displayed > state.displayLegacySurfaceCoverage[legacyIndex]) {
                    state.displayLegacySurfaceCoverage[legacyIndex] = displayed;
                    state.displayLegacySurfaceMedium[legacyIndex] = state.displaySurfaceMedium[surfaceIndex];
                }
            }
            state.displaySurfaceSignature = hasQuantizedSurface ? surfaceSignature : 0L;
            state.displaySurfaceMediumMask = hasQuantizedSurface ? surfaceMediumMask : 0L;

            boolean hasVisibleVision = state.targetVisionObstruction > COVERAGE_EPSILON || state.displayVisionObstruction > COVERAGE_EPSILON;
            for (int i = 0; i < state.displayVisionCoverage.length; i++) {
                float delta = state.targetVisionCoverage[i] - state.displayVisionCoverage[i];
                if (Math.abs(delta) <= DISPLAY_SETTLE_EPSILON) {
                    state.displayVisionCoverage[i] = state.targetVisionCoverage[i];
                } else {
                    state.displayVisionCoverage[i] += delta * 0.42F;
                    animationSettled = false;
                }
                if (state.targetVisionCoverage[i] > COVERAGE_EPSILON) {
                    state.displayVisionMedium[i] = state.targetVisionMedium[i];
                    state.displayVisionVisualSource[i] = state.targetVisionVisualSource[i];
                } else if (state.displayVisionCoverage[i] <= COVERAGE_EPSILON) {
                    state.displayVisionCoverage[i] = 0.0F;
                    state.displayVisionMedium[i] = (byte) SinkingMedium.MUD.id();
                    state.displayVisionVisualSource[i] = 0L;
                }
                hasVisibleVision |= state.targetVisionCoverage[i] > COVERAGE_EPSILON || state.displayVisionCoverage[i] > COVERAGE_EPSILON;
            }
            boolean hasVisibleCape = false;
            long capeSignature = EMPTY_SIGNATURE_SEED;
            boolean hasQuantizedCape = false;
            long capeMediumMask = 0L;
            for (int i = 0; i < state.displayCapeCoverage.length; i++) {
                float delta = state.targetCapeCoverage[i] - state.displayCapeCoverage[i];
                if (Math.abs(delta) <= DISPLAY_SETTLE_EPSILON) {
                    state.displayCapeCoverage[i] = state.targetCapeCoverage[i];
                } else {
                    state.displayCapeCoverage[i] += delta * 0.30F;
                    animationSettled = false;
                }
                if (state.targetCapeCoverage[i] > COVERAGE_EPSILON) {
                    state.displayCapeMedium[i] = state.targetCapeMedium[i];
                    state.displayCapeAppearance[i] = state.targetCapeAppearance[i];
                    state.displayCapeVisualSource[i] = state.targetCapeVisualSource[i];
                } else if (state.displayCapeCoverage[i] <= COVERAGE_EPSILON) {
                    state.displayCapeCoverage[i] = 0.0F;
                    state.displayCapeMedium[i] = (byte) SinkingMedium.MUD.id();
                    state.displayCapeAppearance[i] = 0;
                    state.displayCapeVisualSource[i] = 0L;
                }
                hasVisibleCape |= state.targetCapeCoverage[i] > COVERAGE_EPSILON
                        || state.displayCapeCoverage[i] > COVERAGE_EPSILON;
                int displayLevel = Mth.clamp(Math.round(state.displayCapeCoverage[i] * 255.0F), 0, 255);
                hasQuantizedCape |= displayLevel > 0;
                capeSignature = capeSignature * 31L + displayLevel;
                if (displayLevel > 0) {
                    capeSignature = capeSignature * 31L + (state.displayCapeMedium[i] & 0xFF);
                    capeSignature = capeSignature * 31L + state.displayCapeAppearance[i];
                    capeSignature = capeSignature * 31L
                            + Long.hashCode(state.displayCapeVisualSource[i]);
                    capeMediumMask |= 1L << (state.displayCapeMedium[i] & 0xFF);
                }
            }
            state.displayCapeSignature = hasQuantizedCape ? capeSignature : 0L;
            state.displayCapeMediumMask = hasQuantizedCape ? capeMediumMask : 0L;
            state.hasVisibleBodyOrCape = hasVisiblePart || hasVisibleCape;
            state.animationSettled = animationSettled;
            if (state.targetCoverage <= COVERAGE_EPSILON && state.displayCoverage <= COVERAGE_EPSILON
                    && !hasVisiblePart && !hasVisibleVision && !hasVisibleCape) {
                removeTrackedEntity(tracked.getKey(), state);
            }
        }

        if (struggleSendCooldown > 0) {
            struggleSendCooldown--;
        }
    }

    public static boolean canSendStruggle(int entityId) {
        if (struggleSendCooldown > 0) {
            return false;
        }

        struggleSendCooldown = displayCoverage(entityId) < 0.05F ? 6 : 3;
        return true;
    }

    public static float displayCoverage(int entityId) {
        CoverageState state = displayState(entityId);
        return state == null ? 0.0F : Mth.clamp(state.displayCoverage, 0.0F, 1.0F);
    }

    public static float displayPartCoverage(int entityId, MudBodyPart part) {
        CoverageState state = displayState(entityId);
        return state == null ? 0.0F : Mth.clamp(state.displayPartCoverage[part.ordinal()], 0.0F, 1.0F);
    }

    public static float displayBandCoverage(int entityId, MudBodyPart part, int band) {
        CoverageState state = displayState(entityId);
        return state == null ? 0.0F : Mth.clamp(state.displayBandCoverage[bandIndex(part, band)], 0.0F, 1.0F);
    }

    public static float displaySurfaceCoverage(int entityId, MudBodyPart part, int band, MudSurface surface) {
        CoverageState state = displayState(entityId);
        if (state == null) {
            return 0.0F;
        }

        float max = 0.0F;
        for (int lane = 0; lane < MudBodyPart.SURFACE_LANES; lane++) {
            max = Math.max(max, state.displayLegacySurfaceCoverage[
                    MudSurfaceLayout.legacySurfaceIndex(part, band, surface, lane)]);
        }
        return Mth.clamp(max, 0.0F, 1.0F);
    }

    public static float displaySurfaceCoverage(int entityId, MudBodyPart part, int band, MudSurface surface, int lane) {
        CoverageState state = displayState(entityId);
        return state == null ? 0.0F : Mth.clamp(
                state.displayLegacySurfaceCoverage[MudSurfaceLayout.legacySurfaceIndex(part, band, surface, lane)], 0.0F, 1.0F);
    }

    public static SinkingMedium displayMedium(int entityId) {
        CoverageState state = displayState(entityId);
        return state == null ? SinkingMedium.MUD : state.medium;
    }

    public static float displayVisionObstruction(int entityId) {
        CoverageState state = displayState(entityId);
        return state == null ? 0.0F : Mth.clamp(state.displayVisionObstruction, 0.0F, 1.0F);
    }

    public static float displayVisionCoverage(int entityId, int band, int lane) {
        CoverageState state = displayState(entityId);
        return state == null ? 0.0F : Mth.clamp(state.displayVisionCoverage[visionIndex(band, lane)], 0.0F, 1.0F);
    }

    public static SinkingMedium displaySurfaceMedium(int entityId, MudBodyPart part, int band, MudSurface surface, int lane) {
        CoverageState state = displayState(entityId);
        return state == null ? SinkingMedium.MUD : SinkingMedium.byId(
                state.displayLegacySurfaceMedium[MudSurfaceLayout.legacySurfaceIndex(part, band, surface, lane)] & 0xFF);
    }

    public static float displaySurfacePixelCoverage(int entityId, MudBodyPart part, MudSurface surface, int row, int column) {
        CoverageState state = displayState(entityId);
        return state == null ? 0.0F : Mth.clamp(
                state.displaySurfaceCoverage[MudSurfaceLayout.cellIndex(part, surface, row, column)], 0.0F, 1.0F);
    }

    public static SinkingMedium displaySurfacePixelMedium(int entityId, MudBodyPart part, MudSurface surface, int row,
            int column) {
        CoverageState state = displayState(entityId);
        return state == null ? SinkingMedium.MUD : SinkingMedium.byId(
                state.displaySurfaceMedium[MudSurfaceLayout.cellIndex(part, surface, row, column)] & 0xFF);
    }

    public static int displaySurfacePixelAppearance(int entityId, MudBodyPart part, MudSurface surface, int row,
            int column) {
        CoverageState state = displayState(entityId);
        return state == null ? 0 : state.displaySurfaceAppearance[
                MudSurfaceLayout.cellIndex(part, surface, row, column)];
    }

    public static long displaySurfacePixelVisualSource(int entityId,
            MudBodyPart part, MudSurface surface, int row, int column) {
        CoverageState state = displayState(entityId);
        return state == null ? 0L : state.displaySurfaceVisualSource[
                MudSurfaceLayout.cellIndex(part, surface, row, column)];
    }

    public static SinkingMedium displayVisionMedium(int entityId, int band, int lane) {
        CoverageState state = displayState(entityId);
        return state == null ? SinkingMedium.MUD : SinkingMedium.byId(state.displayVisionMedium[visionIndex(band, lane)] & 0xFF);
    }

    public static long displayVisionVisualSource(int entityId, int band, int lane) {
        CoverageState state = displayState(entityId);
        return state == null ? 0L
                : state.displayVisionVisualSource[visionIndex(band, lane)];
    }

    public static float displayCapePixelCoverage(int entityId, int row, int column) {
        float front = displayCapePixelCoverage(entityId, MudCapeLayout.Side.OUTER, row, column);
        float back = displayCapePixelCoverage(entityId, MudCapeLayout.Side.INNER, row, column);
        return Math.max(front, back);
    }

    public static float displayCapePixelCoverage(
            int entityId, MudCapeLayout.Side side, int row, int column) {
        CoverageState state = displayState(entityId);
        return state == null ? 0.0F : Mth.clamp(
                state.displayCapeCoverage[MudCapeLayout.index(side, row, column)], 0.0F, 1.0F);
    }

    public static SinkingMedium displayCapePixelMedium(int entityId, int row, int column) {
        MudCapeLayout.Side side = displayCapePixelCoverage(
                entityId, MudCapeLayout.Side.INNER, row, column)
                > displayCapePixelCoverage(entityId, MudCapeLayout.Side.OUTER, row, column)
                        ? MudCapeLayout.Side.INNER
                        : MudCapeLayout.Side.OUTER;
        return displayCapePixelMedium(entityId, side, row, column);
    }

    public static SinkingMedium displayCapePixelMedium(
            int entityId, MudCapeLayout.Side side, int row, int column) {
        CoverageState state = displayState(entityId);
        return state == null ? SinkingMedium.MUD : SinkingMedium.byId(
                state.displayCapeMedium[MudCapeLayout.index(side, row, column)] & 0xFF);
    }

    public static int displayCapePixelAppearance(
            int entityId, MudCapeLayout.Side side, int row, int column) {
        CoverageState state = displayState(entityId);
        return state == null ? 0 : state.displayCapeAppearance[MudCapeLayout.index(side, row, column)];
    }

    public static long displayCapePixelVisualSource(
            int entityId, MudCapeLayout.Side side, int row, int column) {
        CoverageState state = displayState(entityId);
        return state == null ? 0L : state.displayCapeVisualSource[
                MudCapeLayout.index(side, row, column)];
    }

    public static void reset() {
        COVERAGE_BY_ENTITY.clear();
        struggleSendCooldown = 0;
        MudSkinTextureCache.reset();
        MudCapeTextureCache.reset();
    }

    public static void clearEntity(int entityId) {
        COVERAGE_BY_ENTITY.remove(entityId);
        MudSkinTextureCache.invalidateOrdinaryEntity(entityId);
        MudCapeTextureCache.clearEntity(entityId);
    }

    private static void removeTrackedEntity(int entityId, CoverageState state) {
        if (COVERAGE_BY_ENTITY.remove(entityId, state)) {
            MudSkinTextureCache.invalidateOrdinaryEntity(entityId);
            MudCapeTextureCache.clearEntity(entityId);
        }
    }

    public static CoverageState displaySnapshot(int entityId) {
        CoverageState state = displayState(entityId);
        return state == null ? EMPTY_DISPLAY : state;
    }

    public static long displaySurfaceSignature(int entityId) {
        CoverageState state = displaySnapshot(entityId);
        if (state.displaySurfaceSignature == 0L) {
            return 0L;
        }
        long signature = state.displaySurfaceSignature * 31L
                + Integer.toUnsignedLong(state.coveragePatternSeed);
        long appearance = state.surfaceAppearanceRevisionSignature();
        return appearance == 0L ? signature : signature * 31L + appearance;
    }

    public static long displayCapeSignature(int entityId) {
        CoverageState state = displaySnapshot(entityId);
        if (state.displayCapeSignature == 0L) {
            return 0L;
        }
        long signature = state.displayCapeSignature * 31L
                + Integer.toUnsignedLong(state.coveragePatternSeed);
        long appearance = state.capeAppearanceRevisionSignature();
        return appearance == 0L ? signature : signature * 31L + appearance;
    }

    public static int coveragePatternSeed(int entityId) {
        return displaySnapshot(entityId).coveragePatternSeed;
    }

    public static long displaySurfaceMediumMask(int entityId) {
        return displaySnapshot(entityId).displaySurfaceMediumMask;
    }

    public static long displayCapeMediumMask(int entityId) {
        return displaySnapshot(entityId).displayCapeMediumMask;
    }

    public static boolean hasVisibleBodyOrCape(int entityId) {
        return displaySnapshot(entityId).hasVisibleBodyOrCape;
    }

    private static CoverageState displayState(int entityId) {
        CoverageState state = COVERAGE_BY_ENTITY.get(entityId);
        return state == null || state.renderSuppressed ? null : state;
    }

    public static final class CoverageState {
        private SinkingMedium medium = SinkingMedium.MUD;
        private int coveragePatternSeed;
        private float targetCoverage;
        private float displayCoverage;
        private float targetVisionObstruction;
        private float displayVisionObstruction;
        private final float[] targetVisionCoverage = new float[MudBodyPart.VISION_COUNT];
        private final float[] displayVisionCoverage = new float[MudBodyPart.VISION_COUNT];
        private final float[] displayPartCoverage = new float[MudBodyPart.COUNT];
        private final float[] displayBandCoverage = new float[MudBodyPart.BAND_COUNT];
        private final float[] targetSurfaceCoverage = new float[MudSurfaceLayout.CELL_COUNT];
        private final float[] displaySurfaceCoverage = new float[MudSurfaceLayout.CELL_COUNT];
        private final float[] displayLegacySurfaceCoverage = new float[MudBodyPart.COUNT * MudBodyPart.BANDS
                * MudSurface.COUNT * MudBodyPart.SURFACE_LANES];
        private final byte[] targetVisionMedium = new byte[MudBodyPart.VISION_COUNT];
        private final byte[] displayVisionMedium = new byte[MudBodyPart.VISION_COUNT];
        private final long[] targetVisionVisualSource = new long[MudBodyPart.VISION_COUNT];
        private final long[] displayVisionVisualSource = new long[MudBodyPart.VISION_COUNT];
        private final byte[] targetSurfaceMedium = new byte[MudSurfaceLayout.CELL_COUNT];
        private final byte[] displaySurfaceMedium = new byte[MudSurfaceLayout.CELL_COUNT];
        private final int[] targetSurfaceAppearance = new int[MudSurfaceLayout.CELL_COUNT];
        private final int[] displaySurfaceAppearance = new int[MudSurfaceLayout.CELL_COUNT];
        private final long[] targetSurfaceVisualSource = new long[MudSurfaceLayout.CELL_COUNT];
        private final long[] displaySurfaceVisualSource = new long[MudSurfaceLayout.CELL_COUNT];
        private final float[] targetCapeCoverage = new float[MudCapeLayout.CELL_COUNT];
        private final float[] displayCapeCoverage = new float[MudCapeLayout.CELL_COUNT];
        private final byte[] targetCapeMedium = new byte[MudCapeLayout.CELL_COUNT];
        private final byte[] displayCapeMedium = new byte[MudCapeLayout.CELL_COUNT];
        private final int[] targetCapeAppearance = new int[MudCapeLayout.CELL_COUNT];
        private final int[] displayCapeAppearance = new int[MudCapeLayout.CELL_COUNT];
        private final long[] targetCapeVisualSource = new long[MudCapeLayout.CELL_COUNT];
        private final long[] displayCapeVisualSource = new long[MudCapeLayout.CELL_COUNT];
        private final byte[] displayLegacySurfaceMedium = new byte[MudBodyPart.COUNT * MudBodyPart.BANDS
                * MudSurface.COUNT * MudBodyPart.SURFACE_LANES];
        private long displaySurfaceSignature;
        private long displayCapeSignature;
        private long displaySurfaceMediumMask;
        private long displayCapeMediumMask;
        private long adaptiveAppearanceEpoch = Long.MIN_VALUE;
        private long surfaceAppearanceRevisionSignature;
        private long capeAppearanceRevisionSignature;
        private boolean hasVisibleBodyOrCape;
        private boolean renderSuppressed;
        private boolean animationSettled;
        private int ticksSinceSync = 100;

        public float coverage() {
            return Mth.clamp(displayCoverage, 0.0F, 1.0F);
        }

        public SinkingMedium medium() {
            return medium;
        }

        public float visionObstruction() {
            return Mth.clamp(displayVisionObstruction, 0.0F, 1.0F);
        }

        public float visionCoverage(int band, int lane) {
            return Mth.clamp(displayVisionCoverage[visionIndex(band, lane)], 0.0F, 1.0F);
        }

        public SinkingMedium visionMedium(int band, int lane) {
            return SinkingMedium.byId(displayVisionMedium[visionIndex(band, lane)] & 0xFF);
        }

        public long visionVisualSource(int band, int lane) {
            return displayVisionVisualSource[visionIndex(band, lane)];
        }

        public float legacySurfaceCoverage(MudBodyPart part, int band, MudSurface surface, int lane) {
            return Mth.clamp(displayLegacySurfaceCoverage[
                    MudSurfaceLayout.legacySurfaceIndex(part, band, surface, lane)], 0.0F, 1.0F);
        }

        public SinkingMedium legacySurfaceMedium(MudBodyPart part, int band, MudSurface surface, int lane) {
            return SinkingMedium.byId(displayLegacySurfaceMedium[
                    MudSurfaceLayout.legacySurfaceIndex(part, band, surface, lane)] & 0xFF);
        }

        public float surfacePixelCoverage(MudBodyPart part, MudSurface surface, int row, int column) {
            return Mth.clamp(
                    displaySurfaceCoverage[MudSurfaceLayout.cellIndex(part, surface, row, column)], 0.0F, 1.0F);
        }

        public SinkingMedium surfacePixelMedium(MudBodyPart part, MudSurface surface, int row, int column) {
            return SinkingMedium.byId(
                    displaySurfaceMedium[MudSurfaceLayout.cellIndex(part, surface, row, column)] & 0xFF);
        }

        public int surfacePixelAppearance(MudBodyPart part, MudSurface surface, int row, int column) {
            return displaySurfaceAppearance[MudSurfaceLayout.cellIndex(part, surface, row, column)];
        }

        public long surfacePixelVisualSource(
                MudBodyPart part, MudSurface surface, int row, int column) {
            return displaySurfaceVisualSource[MudSurfaceLayout.cellIndex(
                    part, surface, row, column)];
        }

        public float capePixelCoverage(MudCapeLayout.Side side, int row, int column) {
            return Mth.clamp(displayCapeCoverage[MudCapeLayout.index(side, row, column)], 0.0F, 1.0F);
        }

        public int coveragePatternSeed() {
            return coveragePatternSeed;
        }

        public SinkingMedium capePixelMedium(MudCapeLayout.Side side, int row, int column) {
            return SinkingMedium.byId(displayCapeMedium[MudCapeLayout.index(side, row, column)] & 0xFF);
        }

        public int capePixelAppearance(MudCapeLayout.Side side, int row, int column) {
            return displayCapeAppearance[MudCapeLayout.index(side, row, column)];
        }

        public long capePixelVisualSource(
                MudCapeLayout.Side side, int row, int column) {
            return displayCapeVisualSource[MudCapeLayout.index(side, row, column)];
        }

        private long surfaceAppearanceRevisionSignature() {
            refreshAppearanceRevisionSignatures();
            return surfaceAppearanceRevisionSignature;
        }

        private long capeAppearanceRevisionSignature() {
            refreshAppearanceRevisionSignatures();
            return capeAppearanceRevisionSignature;
        }

        private void refreshAppearanceRevisionSignatures() {
            long epoch = AdaptiveMudClientCache.appearanceEpoch();
            if (adaptiveAppearanceEpoch == epoch) {
                return;
            }
            adaptiveAppearanceEpoch = epoch;
            var level = Minecraft.getInstance().level;
            surfaceAppearanceRevisionSignature = appearanceRevisionSignature(
                    level, displaySurfaceCoverage, displaySurfaceVisualSource);
            capeAppearanceRevisionSignature = appearanceRevisionSignature(
                    level, displayCapeCoverage, displayCapeVisualSource);
        }
    }

    private static long appearanceRevisionSignature(
            net.minecraft.world.level.Level level,
            float[] coverage, long[] visualSources) {
        long signature = 0L;
        for (int index = 0; index < coverage.length; index++) {
            if (coverage[index] <= COVERAGE_EPSILON) {
                continue;
            }
            long visualSource = visualSources[index];
            int revision = AdaptiveMudClientCache.appearanceRevision(
                    level, visualSource);
            if (revision == 0) {
                continue;
            }
            signature = (signature * 31L + Long.hashCode(visualSource)) * 31L
                    + Integer.toUnsignedLong(revision);
        }
        return signature;
    }

    private static int bandIndex(MudBodyPart part, int band) {
        return part.ordinal() * MudBodyPart.BANDS + Mth.clamp(band, 0, MudBodyPart.BANDS - 1);
    }

    private static int visionIndex(int band, int lane) {
        return Mth.clamp(band, 0, MudBodyPart.VISION_BANDS - 1) * MudBodyPart.VISION_LANES
                + Mth.clamp(lane, 0, MudBodyPart.VISION_LANES - 1);
    }
}

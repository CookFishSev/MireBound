package com.fish.mirebound.client;

import com.fish.mirebound.adaptive.MudVisualPalette;
import com.fish.mirebound.assimilation.AssimilationProfile;
import com.fish.mirebound.assimilation.AssimilationContributions;
import com.fish.mirebound.assimilation.AssimilationCoveragePattern;
import com.fish.mirebound.assimilation.AssimilationQteAction;
import com.fish.mirebound.assimilation.AssimilationPartialPurge;
import com.fish.mirebound.assimilation.AssimilationStage;
import com.fish.mirebound.mud.MudBodyPart;
import com.fish.mirebound.mud.MudSurface;
import com.fish.mirebound.mud.MudSurfaceLayout;
import com.fish.mirebound.mud.MudPhysics;
import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.network.payload.AssimilationStatePayload;
import java.util.BitSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.Entity;

/** Client interpolation and deterministic per-pixel assimilation reveal. */
public final class ClientAssimilationState {
    private static final Map<Integer, View> BY_ENTITY = new ConcurrentHashMap<>();
    private static final int UNTRACKED_TTL_TICKS = 200;

    private ClientAssimilationState() {
    }

    public static void accept(AssimilationStatePayload payload) {
        View view = BY_ENTITY.computeIfAbsent(payload.entityId(), ignored -> new View());
        AssimilationStage previousStage = view.stage;
        boolean completedPartialPurge = view.partialPurgeActive
                && payload.stage() == AssimilationStage.NORMAL
                && payload.progress() <= 0.0001F;
        view.stage = payload.stage();
        view.targetProgress = payload.progress();
        view.targetShellIntegrity = payload.shellIntegrity();
        view.restoringTicks = payload.restoringTicks();
        Vec3 nextAnchor = new Vec3(payload.anchorX(), payload.anchorY(), payload.anchorZ());
        if (!view.anchorInitialized || !view.stage.frozen()
                || previousStage == AssimilationStage.NORMAL) {
            view.displayAnchor = nextAnchor;
            view.previousDisplayAnchor = nextAnchor;
            view.anchorInitialized = true;
        }
        view.targetAnchor = nextAnchor;
        view.frozenYaw = payload.frozenYaw();
        view.frozenPitch = payload.frozenPitch();
        if (previousStage == AssimilationStage.NORMAL) {
            view.displayBodyPitch = payload.bodyPitch();
            view.displayBodyRoll = payload.bodyRoll();
            view.previousBodyPitch = payload.bodyPitch();
            view.previousBodyRoll = payload.bodyRoll();
        }
        view.targetBodyPitch = payload.bodyPitch();
        view.targetBodyRoll = payload.bodyRoll();
        view.frozenWalkPosition = payload.frozenWalkPosition();
        view.frozenWalkSpeed = payload.frozenWalkSpeed();
        AssimilationContributions.unpackNetwork(
                payload.contributions(), view.targetContributions);
        view.targetVisualPalette.unpackNetwork(
                payload.visualPaletteEntries(), payload.visualSources(), payload.progress());
        view.medium = AssimilationContributions.dominant(
                view.targetContributions, SinkingMedium.byId(payload.mediumId()));
        if (view.patternSeed != payload.patternSeed() || view.activationThresholds == null) {
            view.patternSeed = payload.patternSeed();
            view.activationThresholds = AssimilationCoveragePattern.buildThresholds(
                    view.patternSeed == 0 ? payload.entityId() : view.patternSeed);
        }
        BitSet nextRevealed = BitSet.valueOf(payload.revealedCells());
        BitSet newlyRevealed = (BitSet) nextRevealed.clone();
        newlyRevealed.andNot(view.revealedCells);
        view.revealedCells.clear();
        view.revealedCells.or(nextRevealed);
        view.qteCell = payload.qteCell();
        view.qteButton = payload.qteButton();
        view.qteAction = payload.qteAction();
        view.qteRapidClicks = payload.qteRapidClicks();
        view.qteTraceProgress = payload.qteTraceProgress();
        view.qteTicksRemaining = payload.qteTicksRemaining();
        view.qteStreak = payload.qteStreak();
        view.qteSequence = payload.qteSequence();
        boolean newPurgeSession = !view.partialPurgeActive && payload.partialPurgeActive();
        boolean newPurgeRound = view.partialPurgeRound != payload.partialPurgeRound();
        view.partialPurgeActive = payload.partialPurgeActive();
        view.partialPurgeZoneStart = payload.partialPurgeZoneStart();
        view.partialPurgeZoneEnd = payload.partialPurgeZoneEnd();
        view.partialPurgeCursorForward = payload.partialPurgeCursorForward();
        view.partialPurgeCursorOneWayTicks = payload.partialPurgeCursorOneWayTicks();
        view.partialPurgeCooldownTicks = payload.partialPurgeCooldownTicks();
        view.partialPurgeResult = payload.partialPurgeResult();
        view.partialPurgeResultTicks = payload.partialPurgeResultTicks();
        view.partialPurgeRound = payload.partialPurgeRound();
        float cursorError = payload.partialPurgeCursor() - view.partialPurgeCursor;
        if (newPurgeSession || newPurgeRound || Math.abs(cursorError) > 0.16F) {
            view.partialPurgeCursor = payload.partialPurgeCursor();
            view.previousPartialPurgeCursor = view.partialPurgeCursor;
        } else {
            view.partialPurgeCursor += cursorError * 0.35F;
        }
        if (payload.profile() != null) {
            view.profile = payload.profile();
        }
        if (previousStage == AssimilationStage.RESTORING
                && view.stage == AssimilationStage.NORMAL) {
            view.restoreBlackoutTailTicks = view.profile.restoreBlackoutFadeTicks();
        }
        rebuildOpenTargets(view);
        if (view.stage.frozen() && !newlyRevealed.isEmpty()) {
            view.lastRescueCell = newlyRevealed.nextSetBit(0);
            view.rescuePulseTicks = view.profile.rescuePulseTicks();
        }
        if (view.stage == AssimilationStage.SEALED && previousStage != AssimilationStage.SEALED) {
            view.soulTransitionTicks = view.profile.soulTransitionTicks();
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.player.getId() == payload.entityId()) {
            MudPhysics.setClientPlayerPhysicsSuspended(minecraft.player, view.stage.frozen());
        }
        if (view.stage == AssimilationStage.NORMAL && minecraft.level != null) {
            Entity entity = minecraft.level.getEntity(payload.entityId());
            if (entity != null) {
                entity.refreshDimensions();
            }
        }
        view.ticksSinceSync = 0;
        if (view.stage == AssimilationStage.NORMAL && view.targetProgress <= 0.0001F) {
            view.targetShellIntegrity = 0.0F;
            view.displayShellIntegrity = 0.0F;
            view.revealedCells.clear();
            view.targetOpenCells.clear();
            java.util.Arrays.fill(view.openVisibility, 0.0F);
            view.qteCell = -1;
            view.qteButton = 0;
            view.qteAction = AssimilationQteAction.NONE;
            view.qteRapidClicks = 0;
            view.qteTraceProgress = 0;
            view.qteTicksRemaining = 0;
            view.qteStreak = 0;
            view.clearPartialPurge();
            view.soulTransitionTicks = 0;
            if (!completedPartialPurge) {
                view.displayProgress = 0.0F;
                java.util.Arrays.fill(view.displayContributions, 0.0F);
                view.displayVisualPalette.clear();
            }
            AssimilationPlayerAnimation.clearEntity(payload.entityId());
        }
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        int localId = minecraft.player == null ? Integer.MIN_VALUE : minecraft.player.getId();
        BY_ENTITY.entrySet().removeIf(entry -> {
            View view = entry.getValue();
            view.ticksSinceSync++;
            if (view.soulTransitionTicks > 0) {
                view.soulTransitionTicks--;
            }
            if (view.stage == AssimilationStage.RESTORING && view.restoringTicks > 0) {
                view.restoringTicks--;
            }
            if (view.stage == AssimilationStage.NORMAL && view.restoreBlackoutTailTicks > 0) {
                view.restoreBlackoutTailTicks--;
            }
            if (view.rescuePulseTicks > 0) {
                view.rescuePulseTicks--;
            }
            view.previousPartialPurgeCursor = view.partialPurgeCursor;
            if (view.partialPurgeActive && view.partialPurgeCooldownTicks <= 0) {
                AssimilationPartialPurge.Cursor cursor = AssimilationPartialPurge.advance(
                        view.partialPurgeCursor, view.partialPurgeCursorForward,
                        view.partialPurgeCursorOneWayTicks);
                view.partialPurgeCursor = cursor.position();
                view.partialPurgeCursorForward = cursor.forward();
            }
            if (view.partialPurgeCooldownTicks > 0) {
                view.partialPurgeCooldownTicks--;
            }
            if (view.partialPurgeResultTicks > 0) {
                view.partialPurgeResultTicks--;
            }
            boolean localQtePaused = entry.getKey() == localId
                    && view.qteCell >= 0 && !AssimilationQteClient.inRange(view);
            if (view.qteTicksRemaining > 0 && !localQtePaused) {
                view.qteTicksRemaining--;
            }
            animateCracks(view);
            float progressRate = view.targetProgress > view.displayProgress ? 0.18F : 0.12F;
            view.displayProgress += (view.targetProgress - view.displayProgress) * progressRate;
            for (int mediumId = 0; mediumId < SinkingMedium.COUNT; mediumId++) {
                float target = view.targetContributions[mediumId];
                float current = view.displayContributions[mediumId];
                float rate = target > current ? 0.18F : 0.12F;
                float next = current + (target - current) * rate;
                view.displayContributions[mediumId] = Math.abs(next - target) < 0.0005F
                        ? target : next;
            }
            animateVisualPalette(view);
            view.medium = AssimilationContributions.dominant(
                    view.displayContributions, view.medium);
            view.displayShellIntegrity +=
                    (view.targetShellIntegrity - view.displayShellIntegrity) * 0.24F;
            view.previousDisplayAnchor = view.displayAnchor;
            view.previousBodyPitch = view.displayBodyPitch;
            view.previousBodyRoll = view.displayBodyRoll;
            view.displayAnchor = view.displayAnchor.lerp(view.targetAnchor, 0.48D);
            view.displayBodyPitch += (view.targetBodyPitch - view.displayBodyPitch) * 0.42F;
            view.displayBodyRoll += (view.targetBodyRoll - view.displayBodyRoll) * 0.42F;
            if (Math.abs(view.displayProgress - view.targetProgress) < 0.0005F) {
                view.displayProgress = view.targetProgress;
            }
            if (Math.abs(view.displayShellIntegrity - view.targetShellIntegrity) < 0.0005F) {
                view.displayShellIntegrity = view.targetShellIntegrity;
            }
            boolean expired = entry.getKey() != localId
                    && view.ticksSinceSync > UNTRACKED_TTL_TICKS;
            if (expired) {
                AssimilationPlayerAnimation.clearEntity(entry.getKey());
                MudSkinTextureCache.clearEntity(entry.getKey());
            }
            return expired;
        });
    }

    public static View view(int entityId) {
        return BY_ENTITY.get(entityId);
    }

    public static boolean isFrozen(int entityId) {
        View view = BY_ENTITY.get(entityId);
        return view != null && view.stage.frozen();
    }

    public static float coverage(int entityId, int cell) {
        View view = BY_ENTITY.get(entityId);
        if (view == null || cell < 0 || cell >= MudSurfaceLayout.CELL_COUNT) {
            return 0.0F;
        }
        float progress = view.stage == AssimilationStage.RESTORING
                ? restorationRemaining(view) : view.displayProgress;
        if (progress <= 0.0001F) {
            return 0.0F;
        }
        if (view.activationThresholds == null) {
            view.activationThresholds = AssimilationCoveragePattern.buildThresholds(
                    view.patternSeed == 0 ? entityId : view.patternSeed);
        }
        float strength = AssimilationCoveragePattern.strength(
                progress, view.activationThresholds[cell]);
        if (view.stage == AssimilationStage.SEALED || view.stage == AssimilationStage.RESTORING) {
            strength *= 1.0F - view.openVisibility[cell];
        }
        return strength;
    }

    public static SinkingMedium medium(int entityId, int cell) {
        View view = BY_ENTITY.get(entityId);
        if (view == null || cell < 0 || cell >= MudSurfaceLayout.CELL_COUNT) {
            return SinkingMedium.ASSIMILATION_SLIME;
        }
        return mediumForView(view, cell);
    }

    public static long visualSource(int entityId, int cell) {
        View view = BY_ENTITY.get(entityId);
        if (view == null || cell < 0 || cell >= MudSurfaceLayout.CELL_COUNT) {
            return 0L;
        }
        resolveCellVisuals(view);
        return view.resolvedVisualSourceByCell[cell];
    }

    public static boolean hasMultipleMedia(int entityId) {
        View view = BY_ENTITY.get(entityId);
        if (view == null) {
            return false;
        }
        return view.displayVisualPalette.size() > 1;
    }

    private static SinkingMedium mediumForView(View view, int cell) {
        resolveCellVisuals(view);
        return SinkingMedium.byId(view.resolvedMediumByCell[cell] & 0xFF);
    }

    private static void resolveCellVisuals(View view) {
        long signature = view.patternSeed;
        for (int mediumId = 0; mediumId < SinkingMedium.COUNT; mediumId++) {
            signature = signature * 131L
                    + Math.round(view.displayContributions[mediumId] * 64.0F);
        }
        for (int index = 0; index < view.displayVisualPalette.size(); index++) {
            signature = signature * 131L + view.displayVisualPalette.mediumAt(index).id();
            signature = signature * 131L + view.displayVisualPalette.visualSourceAt(index);
            signature = signature * 131L
                    + Math.round(view.displayVisualPalette.weightAt(index) * 64.0F);
        }
        if (view.resolvedMediumSignature != signature) {
            view.activeMediumCount = 0;
            float cumulative = 0.0F;
            for (int mediumId = 0; mediumId < SinkingMedium.COUNT; mediumId++) {
                float weight = view.displayContributions[mediumId];
                if (weight <= 0.0001F) {
                    continue;
                }
                cumulative += weight;
                int active = view.activeMediumCount++;
                view.activeMediumIds[active] = mediumId;
                view.activeCumulativeWeights[active] = cumulative;
            }
            for (int index = 0; index < MudSurfaceLayout.CELL_COUNT; index++) {
                view.resolvedMediumByCell[index] = (byte) AssimilationContributions.mediumForCell(
                        view.patternSeed, index, view.activeMediumIds,
                        view.activeCumulativeWeights, view.activeMediumCount, view.medium).id();
                SinkingMedium cellMedium = SinkingMedium.byId(
                        view.resolvedMediumByCell[index] & 0xFF);
                view.resolvedVisualSourceByCell[index] = view.displayVisualPalette
                        .selectForMedium(view.patternSeed ^ 0x5A17C9E3L,
                                index, cellMedium)
                        .visualSource();
            }
            view.resolvedMediumSignature = signature;
        }
    }

    private static void animateVisualPalette(View view) {
        MudVisualPalette next = new MudVisualPalette();
        for (int index = 0; index < view.targetVisualPalette.size(); index++) {
            SinkingMedium medium = view.targetVisualPalette.mediumAt(index);
            long visualSource = view.targetVisualPalette.visualSourceAt(index);
            float target = view.targetVisualPalette.weightAt(index);
            float current = view.displayVisualPalette.weight(medium, visualSource);
            float value = current + (target - current) * (target > current ? 0.18F : 0.12F);
            if (value > 0.0001F) {
                next.add(medium, visualSource, value);
            }
        }
        for (int index = 0; index < view.displayVisualPalette.size(); index++) {
            SinkingMedium medium = view.displayVisualPalette.mediumAt(index);
            long visualSource = view.displayVisualPalette.visualSourceAt(index);
            if (view.targetVisualPalette.weight(medium, visualSource) > 0.0F) {
                continue;
            }
            float value = view.displayVisualPalette.weightAt(index) * 0.88F;
            if (value > 0.0001F) {
                next.add(medium, visualSource, value);
            }
        }
        view.displayVisualPalette.copyFrom(next);
    }

    public static long signature(int entityId) {
        View view = BY_ENTITY.get(entityId);
        if (view == null || view.displayProgress <= 0.0001F) {
            return 0L;
        }
        long value = Math.round(view.displayProgress * 64.0F);
        value = value * 131L + Math.round(view.displayShellIntegrity * 64.0F);
        value = value * 131L + view.stage.ordinal();
        if (view.stage == AssimilationStage.RESTORING) {
            value = value * 131L + Math.round(restorationRemaining(view) * 64.0F);
        }
        value = value * 131L + view.patternSeed;
        for (int mediumId = 0; mediumId < SinkingMedium.COUNT; mediumId++) {
            value = value * 131L + Math.round(view.displayContributions[mediumId] * 64.0F);
        }
        for (int index = 0; index < view.displayVisualPalette.size(); index++) {
            value = value * 131L + view.displayVisualPalette.mediumAt(index).id();
            value = value * 131L + view.displayVisualPalette.visualSourceAt(index);
            value = value * 131L
                    + Math.round(view.displayVisualPalette.weightAt(index) * 64.0F);
        }
        value = value * 131L + view.revealedCells.hashCode();
        value = value * 131L + view.qteCell;
        value = value * 131L + view.qteSequence;
        value = value * 131L + view.crackAnimationRevision;
        return value;
    }

    public static boolean localSoulActive(Minecraft minecraft) {
        if (minecraft.player == null) {
            return false;
        }
        View view = BY_ENTITY.get(minecraft.player.getId());
        return view != null && view.stage.frozen()
                && (view.stage != AssimilationStage.SEALED || view.soulTransitionTicks <= 0);
    }

    public static boolean localStasisActive(Minecraft minecraft) {
        if (minecraft.player == null) {
            return false;
        }
        View view = BY_ENTITY.get(minecraft.player.getId());
        return view != null && view.stage.frozen();
    }

    public static boolean localPartialPurgeActive(Minecraft minecraft) {
        if (minecraft.player == null) {
            return false;
        }
        View view = BY_ENTITY.get(minecraft.player.getId());
        return view != null && view.partialPurgeActive;
    }

    public static boolean rescueFractureEdge(int entityId, int cell) {
        View view = BY_ENTITY.get(entityId);
        if (view == null || !view.stage.frozen()
                || cell < 0 || cell >= MudSurfaceLayout.CELL_COUNT
                || view.openVisibility[cell] > 0.92F) {
            return false;
        }
        MudBodyPart part = MudSurfaceLayout.part(cell);
        MudSurface surface = MudSurfaceLayout.surface(cell);
        MudSurfaceLayout.Face face = MudSurfaceLayout.face(part, surface);
        int row = MudSurfaceLayout.row(cell);
        int column = MudSurfaceLayout.column(cell);
        return revealed(view, part, surface, face, row - 1, column)
                || revealed(view, part, surface, face, row + 1, column)
                || revealed(view, part, surface, face, row, column - 1)
                || revealed(view, part, surface, face, row, column + 1);
    }

    public static float localLookScale(Minecraft minecraft) {
        if (minecraft.player == null) {
            return 1.0F;
        }
        View view = BY_ENTITY.get(minecraft.player.getId());
        return view == null || view.stage.frozen()
                ? 1.0F : view.profile.lookScale(view.displayProgress);
    }

    public static float animationScale(int entityId) {
        View view = BY_ENTITY.get(entityId);
        if (view == null) {
            return 1.0F;
        }
        return view.stage.frozen() ? 0.0F : view.profile.animationScale(view.displayProgress);
    }

    public static void clearEntity(int entityId) {
        BY_ENTITY.remove(entityId);
        AssimilationPlayerAnimation.clearEntity(entityId);
        MudSkinTextureCache.clearEntity(entityId);
    }

    public static void reset() {
        BY_ENTITY.clear();
        AssimilationQteClient.reset();
        AssimilationPlayerAnimation.reset();
        AssimilationScreenOverlay.reset();
        AssimilationSoulCamera.reset();
        AssimilationSoulPresentation.reset();
    }

    private static boolean revealed(View view, MudBodyPart part, MudSurface surface,
            MudSurfaceLayout.Face face, int row, int column) {
        if (row >= 0 && row < face.height() && column >= 0 && column < face.width()) {
            return view.openVisibility[MudSurfaceLayout.cellIndex(
                    part, surface, row, column)] > 0.08F;
        }
        MudSurfaceLayout.Edge edge = row < 0 ? MudSurfaceLayout.Edge.ROW_MIN
                : row >= face.height() ? MudSurfaceLayout.Edge.ROW_MAX
                : column < 0 ? MudSurfaceLayout.Edge.COLUMN_MIN
                : MudSurfaceLayout.Edge.COLUMN_MAX;
        MudSurfaceLayout.AdjacentCell adjacent = MudSurfaceLayout.neighborAcrossEdge(
                part, surface, Mth.clamp(row, 0, face.height() - 1),
                Mth.clamp(column, 0, face.width() - 1), edge);
        return view.openVisibility[MudSurfaceLayout.cellIndex(
                part, adjacent.surface(), adjacent.row(), adjacent.column())] > 0.08F;
    }

    private static void rebuildOpenTargets(View view) {
        BitSet previous = (BitSet) view.targetOpenCells.clone();
        view.targetOpenCells.clear();
        view.targetOpenCells.or(view.revealedCells);
        int target = view.qteCell;
        if (target < 0) {
            view.crackAnimating |= !previous.equals(view.targetOpenCells);
            return;
        }
        MudBodyPart part = MudSurfaceLayout.part(target);
        MudSurface surface = MudSurfaceLayout.surface(target);
        MudSurfaceLayout.Face face = MudSurfaceLayout.face(part, surface);
        int targetRow = MudSurfaceLayout.row(target);
        int targetColumn = MudSurfaceLayout.column(target);
        int radius = view.profile.selfRescueQteRevealRadius();
        for (int row = Math.max(0, targetRow - radius);
                row <= Math.min(face.height() - 1, targetRow + radius); row++) {
            for (int column = Math.max(0, targetColumn - radius);
                    column <= Math.min(face.width() - 1, targetColumn + radius); column++) {
                if (Math.abs(row - targetRow) + Math.abs(column - targetColumn) <= radius) {
                    view.targetOpenCells.set(MudSurfaceLayout.cellIndex(part, surface, row, column));
                }
            }
        }
        view.crackAnimating |= !previous.equals(view.targetOpenCells);
    }

    private static void animateCracks(View view) {
        if (!view.crackAnimating) {
            return;
        }
        float step = 1.0F / Math.max(1, view.profile.selfRescueQteFadeTicks());
        boolean changed = false;
        boolean pending = false;
        for (int cell = 0; cell < MudSurfaceLayout.CELL_COUNT; cell++) {
            float target = view.targetOpenCells.get(cell) ? 1.0F : 0.0F;
            float current = view.openVisibility[cell];
            float next = target > current
                    ? Math.min(target, current + step)
                    : Math.max(target, current - step);
            if (Math.abs(next - current) > 1.0E-5F) {
                view.openVisibility[cell] = next;
                changed = true;
            }
            pending |= Math.abs(next - target) > 1.0E-5F;
        }
        if (changed) {
            view.crackAnimationRevision++;
        }
        view.crackAnimating = pending;
    }

    private static float restorationRemaining(View view) {
        return Mth.clamp(view.restoringTicks
                / (float) Math.max(1, view.profile.restoreTicks()), 0.0F, 1.0F);
    }

    public static final class View {
        private AssimilationStage stage = AssimilationStage.NORMAL;
        private float targetProgress;
        private float displayProgress;
        private float targetShellIntegrity;
        private float displayShellIntegrity;
        private int restoringTicks;
        private int restoreBlackoutTailTicks;
        private Vec3 targetAnchor = Vec3.ZERO;
        private Vec3 displayAnchor = Vec3.ZERO;
        private Vec3 previousDisplayAnchor = Vec3.ZERO;
        private boolean anchorInitialized;
        private float frozenYaw;
        private float frozenPitch;
        private float targetBodyPitch;
        private float displayBodyPitch;
        private float previousBodyPitch;
        private float targetBodyRoll;
        private float displayBodyRoll;
        private float previousBodyRoll;
        private float frozenWalkPosition;
        private float frozenWalkSpeed;
        private int patternSeed;
        private SinkingMedium medium = SinkingMedium.ASSIMILATION_SLIME;
        private final float[] targetContributions = new float[SinkingMedium.COUNT];
        private final float[] displayContributions = new float[SinkingMedium.COUNT];
        private final MudVisualPalette targetVisualPalette = new MudVisualPalette();
        private final MudVisualPalette displayVisualPalette = new MudVisualPalette();
        private final byte[] resolvedMediumByCell = new byte[MudSurfaceLayout.CELL_COUNT];
        private final long[] resolvedVisualSourceByCell = new long[MudSurfaceLayout.CELL_COUNT];
        private final int[] activeMediumIds = new int[SinkingMedium.COUNT];
        private final float[] activeCumulativeWeights = new float[SinkingMedium.COUNT];
        private int activeMediumCount;
        private long resolvedMediumSignature = Long.MIN_VALUE;
        private float[] activationThresholds;
        private final BitSet revealedCells = new BitSet(MudSurfaceLayout.CELL_COUNT);
        private final BitSet targetOpenCells = new BitSet(MudSurfaceLayout.CELL_COUNT);
        private final float[] openVisibility = new float[MudSurfaceLayout.CELL_COUNT];
        private int crackAnimationRevision;
        private boolean crackAnimating;
        private AssimilationProfile profile = AssimilationProfile.DEFAULT;
        private int ticksSinceSync;
        private int soulTransitionTicks;
        private int rescuePulseTicks;
        private int lastRescueCell = -1;
        private int qteCell = -1;
        private int qteButton;
        private AssimilationQteAction qteAction = AssimilationQteAction.NONE;
        private int qteRapidClicks;
        private int qteTraceProgress;
        private int qteTicksRemaining;
        private int qteStreak;
        private int qteSequence;
        private boolean partialPurgeActive;
        private float partialPurgeZoneStart;
        private float partialPurgeZoneEnd;
        private float previousPartialPurgeCursor;
        private float partialPurgeCursor;
        private boolean partialPurgeCursorForward = true;
        private int partialPurgeCursorOneWayTicks = 24;
        private int partialPurgeCooldownTicks;
        private byte partialPurgeResult;
        private int partialPurgeResultTicks;
        private int partialPurgeRound;

        public AssimilationStage stage() {
            return stage;
        }

        public float progress() {
            return displayProgress;
        }

        public float shellIntegrity() {
            return displayShellIntegrity;
        }

        public int restoringTicks() {
            return restoringTicks;
        }

        public int restoreBlackoutTailTicks() {
            return restoreBlackoutTailTicks;
        }

        public Vec3 anchor() {
            return displayAnchor;
        }

        public Vec3 renderAnchor(float partialTick) {
            return previousDisplayAnchor.lerp(
                    displayAnchor, Mth.clamp(partialTick, 0.0F, 1.0F));
        }

        public float frozenYaw() {
            return frozenYaw;
        }

        public float frozenPitch() {
            return frozenPitch;
        }

        public float bodyPitch() {
            return displayBodyPitch;
        }

        public float bodyRoll() {
            return displayBodyRoll;
        }

        public float renderBodyPitch(float partialTick) {
            return Mth.lerp(Mth.clamp(partialTick, 0.0F, 1.0F),
                    previousBodyPitch, displayBodyPitch);
        }

        public float renderBodyRoll(float partialTick) {
            return Mth.lerp(Mth.clamp(partialTick, 0.0F, 1.0F),
                    previousBodyRoll, displayBodyRoll);
        }

        public float frozenWalkPosition() {
            return frozenWalkPosition;
        }

        public float frozenWalkSpeed() {
            return frozenWalkSpeed;
        }

        public AssimilationProfile profile() {
            return profile;
        }

        public SinkingMedium medium() {
            return medium;
        }

        public SinkingMedium medium(int cell) {
            return ClientAssimilationState.mediumForView(this, cell);
        }

        public float mediumContribution(SinkingMedium source) {
            return source == null ? 0.0F : displayContributions[source.id()];
        }

        public int soulTransitionTicks() {
            return soulTransitionTicks;
        }

        public int rescuePulseTicks() {
            return rescuePulseTicks;
        }

        public int lastRescueCell() {
            return lastRescueCell;
        }

        public int qteCell() {
            return qteCell;
        }

        public int qteButton() {
            return qteButton;
        }

        public AssimilationQteAction qteAction() {
            return qteAction;
        }

        public int qteRapidClicks() {
            return qteRapidClicks;
        }

        public int qteTraceProgress() {
            return qteTraceProgress;
        }

        public int patternSeed() {
            return patternSeed;
        }

        public int qteTicksRemaining() {
            return qteTicksRemaining;
        }

        public int qteStreak() {
            return qteStreak;
        }

        public int qteSequence() {
            return qteSequence;
        }

        public boolean partialPurgeActive() {
            return partialPurgeActive;
        }

        public float partialPurgeZoneStart() {
            return partialPurgeZoneStart;
        }

        public float partialPurgeZoneEnd() {
            return partialPurgeZoneEnd;
        }

        public float partialPurgeCursor(float partialTick) {
            return Mth.lerp(Mth.clamp(partialTick, 0.0F, 1.0F),
                    previousPartialPurgeCursor, partialPurgeCursor);
        }

        public int partialPurgeCooldownTicks() {
            return partialPurgeCooldownTicks;
        }

        public byte partialPurgeResult() {
            return partialPurgeResult;
        }

        public int partialPurgeResultTicks() {
            return partialPurgeResultTicks;
        }

        private void clearPartialPurge() {
            partialPurgeActive = false;
            partialPurgeZoneStart = 0.0F;
            partialPurgeZoneEnd = 0.0F;
            previousPartialPurgeCursor = 0.0F;
            partialPurgeCursor = 0.0F;
            partialPurgeCursorForward = true;
            partialPurgeCursorOneWayTicks = 24;
            partialPurgeCooldownTicks = 0;
            partialPurgeResult = AssimilationPartialPurge.RESULT_NONE;
            partialPurgeResultTicks = 0;
            partialPurgeRound = 0;
        }
    }
}

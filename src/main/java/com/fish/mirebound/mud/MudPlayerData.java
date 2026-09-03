package com.fish.mirebound.mud;

import com.fish.mirebound.adaptive.MudVisualPalette;
import com.fish.mirebound.coverage.MudCoveragePaintPredicate;
import java.util.Arrays;
import java.util.BitSet;
import java.util.function.IntPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class MudPlayerData {
    private static final int PERSISTENCE_VERSION = 9;
    private static final float COVERAGE_CLEAR_THRESHOLD = 0.00025F;
    private static final float OLD_PERSISTENCE_COVERAGE_SCALE = 100.0F;
    private static final float PERSISTENCE_COVERAGE_SCALE = 255.0F;
    private static final String TAG_VERSION = "Version";
    private static final String TAG_COVERAGE = "Coverage";
    private static final String TAG_MEDIUM = "Medium";
    private static final String TAG_SURFACE_COVERAGE = "SurfaceCoverage";
    private static final String TAG_SURFACE_MEDIUM = "SurfaceMedium";
    private static final String TAG_SURFACE_APPEARANCE = "SurfaceAppearance";
    private static final String TAG_SURFACE_VISUAL_SOURCE = "SurfaceVisualSource";
    private static final String TAG_CAPE_COVERAGE = "CapeCoverage";
    private static final String TAG_CAPE_MEDIUM = "CapeMedium";
    private static final String TAG_CAPE_APPEARANCE = "CapeAppearance";
    private static final String TAG_CAPE_VISUAL_SOURCE = "CapeVisualSource";
    private static final String TAG_COVERAGE_PATTERN_SEED = "CoveragePatternSeed";

    public int lastMudTick = Integer.MIN_VALUE;
    public int lastPhysicsTick = Integer.MIN_VALUE;
    public int lastFootprintTick = Integer.MIN_VALUE;
    public int lastProcessedTick = Integer.MIN_VALUE;
    public int lastSyncTick = Integer.MIN_VALUE;
    public int lastCoveragePackTick = Integer.MIN_VALUE;
    public int lastPersistentSaveTick = Integer.MIN_VALUE;
    public int lastDebugSyncTick = Integer.MIN_VALUE;
    public int lastTenderFleshEnclosureSyncTick = Integer.MIN_VALUE;
    public int lastDebugLogTick = Integer.MIN_VALUE;
    public int lastWallStainTick = Integer.MIN_VALUE;
    public int lastMudSplashTick = Integer.MIN_VALUE;
    public Vec3 mudImpactPreviousFeet = Vec3.ZERO;
    public Vec3 mudImpactPreviousVelocity = Vec3.ZERO;
    public int struggleCooldown;
    public int strugglePower;
    public int struggleHold;
    public int liftTicks;
    public int stuckTicks;
    public float struggleCharge;
    public float pendingStruggleCharge = -1.0F;
    public float coverage;
    public float visionObstruction;
    public float lastSyncedCoverage = -1.0F;
    public float lastSyncedVisionObstruction = -1.0F;
    public int lastSyncedMediumId = -1;
    public int coveragePatternSeed;
    public int lastSyncedCoveragePatternSeed = -1;
    public int lastSyncedTenderFleshBrokenMask = -1;
    public int lastSyncedTenderFleshPillarDamagePacked = -1;
    public int lastSyncedTenderFleshPillarRequiredHitsPacked = -1;
    public int lastSyncedTenderFleshCooldownTicks = -1;
    public float lastSyncedTenderFleshProgress = -1.0F;
    public double lastSyncedTenderFleshAnchorX = Double.NaN;
    public double lastSyncedTenderFleshAnchorY = Double.NaN;
    public double lastSyncedTenderFleshAnchorZ = Double.NaN;
    public double lastSyncedTenderFleshPlayerX = Double.NaN;
    public double lastSyncedTenderFleshPlayerZ = Double.NaN;
    public boolean lastSyncedTenderFleshActive;
    public boolean lastSyncedTenderFleshRetreating;
    public final float[] partCoverage = new float[MudBodyPart.COUNT];
    public final float[] bandCoverage = new float[MudBodyPart.BAND_COUNT];
    public final float[] surfaceCoverage = new float[MudSurfaceLayout.CELL_COUNT];
    public final byte[] surfaceMedium = new byte[MudSurfaceLayout.CELL_COUNT];
    public final int[] surfaceAppearance = new int[MudSurfaceLayout.CELL_COUNT];
    public final long[] surfaceVisualSource = new long[MudSurfaceLayout.CELL_COUNT];
    private final boolean[] surfaceContactThisTick = new boolean[MudSurfaceLayout.CELL_COUNT];
    private final float[] assimilationContactWeights = new float[SinkingMedium.COUNT];
    private final MudVisualPalette assimilationContactVisuals = new MudVisualPalette();
    private final long[] assimilationContactPositions = new long[SinkingMedium.COUNT];
    private final boolean[] assimilationContactHasPosition = new boolean[SinkingMedium.COUNT];
    private int assimilationContactTick = Integer.MIN_VALUE;
    public final float[] capeCoverage = new float[MudCapeLayout.CELL_COUNT];
    public final byte[] capeMedium = new byte[MudCapeLayout.CELL_COUNT];
    public final int[] capeAppearance = new int[MudCapeLayout.CELL_COUNT];
    public final long[] capeVisualSource = new long[MudCapeLayout.CELL_COUNT];
    private final byte[] armorContactCoverage =
            new byte[ArmorMudManager.ARMOR_SLOT_COUNT * MudSurfaceLayout.CELL_COUNT];
    private final byte[] armorContactMedium =
            new byte[ArmorMudManager.ARMOR_SLOT_COUNT * MudSurfaceLayout.CELL_COUNT];
    private final long[] armorContactVisualSource =
            new long[ArmorMudManager.ARMOR_SLOT_COUNT * MudSurfaceLayout.CELL_COUNT];
    final float[] footprintMediumWeightsScratch = new float[SinkingMedium.COUNT];
    private final byte[] pendingSableSurfaceMedium = new byte[MudSurfaceLayout.CELL_COUNT];
    private final byte[] pendingSableSurfaceTicks = new byte[MudSurfaceLayout.CELL_COUNT];
    private final int[] pendingSableSurfaceLastTick = new int[MudSurfaceLayout.CELL_COUNT];
    public final float[] visionCoverage = new float[MudBodyPart.VISION_COUNT];
    public final byte[] visionMedium = new byte[MudBodyPart.VISION_COUNT];
    public final long[] visionVisualSource = new long[MudBodyPart.VISION_COUNT];
    public byte[] lastSyncedSurfaceCoverage = new byte[0];
    public byte[] lastSyncedSurfaceMedium = new byte[0];
    public int[] lastSyncedSurfaceAppearance = new int[0];
    public long[] lastSyncedSurfaceVisualSource = new long[0];
    public byte[] lastSyncedCapeCoverage = new byte[0];
    public byte[] lastSyncedCapeMedium = new byte[0];
    public int[] lastSyncedCapeAppearance = new int[0];
    public long[] lastSyncedCapeVisualSource = new long[0];
    public byte[] lastSyncedVisionCoverage = new byte[0];
    public byte[] lastSyncedVisionMedium = new byte[0];
    public long[] lastSyncedVisionVisualSource = new long[0];
    public double depth;
    public double debugColumnDepth;
    public double debugSinkLimit;
    public double debugRemainingDepth;
    public double debugYBefore;
    public double debugYAfter;
    public double debugHorizontalSpeed;
    public double debugSinkStep;
    public double debugWalkScale;
    public double debugVerticalScale;
    public double settlingVelocity;
    final LivingSlimeRuntimeState livingSlimeState = new LivingSlimeRuntimeState();
    final SculkMireRuntimeState sculkMireState = new SculkMireRuntimeState();
    final TenderFleshRuntimeState tenderFleshState = new TenderFleshRuntimeState();
    float sculkMovementIntent;
    boolean sculkJumpIntent;
    boolean sculkCrouchIntent;
    int sculkInputAge = 20;
    public float lastLookYaw;
    public float lastLookPitch;
    public float agitation;
    public float leftFootprintResidue;
    public float rightFootprintResidue;
    public float wallStainResidue;
    public float wallStainObservedCoverage;
    public double footprintTravel;
    public double footprintLastX;
    public double footprintLastZ;
    public double footprintDirectionX;
    public double footprintDirectionZ;
    public boolean inMud;
    public boolean eyeSubmerged;
    public boolean debugPhysicalized;
    public boolean holdingStruggle;
    public boolean hasLookSample;
    public boolean nextFootprintLeft = true;
    public boolean footprintTrackingInitialized;
    public boolean footprintDirectionInitialized;
    public boolean wallStainTrackingInitialized;
    public boolean wallStainWasInMud;
    public boolean mudImpactTrackingInitialized;
    public boolean mudImpactWasInside;
    public boolean gravityOverrideActive;
    public boolean previousNoGravity;
    public boolean coveragePersistenceDirty;
    private boolean coverageBatchActive;
    public boolean clientPollutionSuppressed;
    public boolean tenderFleshHintShown;
    public boolean tenderFleshEffectiveHintShown;
    public int tenderFleshAbsorbedFeedbacks;
    public int clientPollutionSuppressedUntilTick = Integer.MIN_VALUE;
    public SinkingMedium medium = SinkingMedium.MUD;
    public SinkingMedium physicsMedium = SinkingMedium.MUD;
    public SinkingMedium physicsMediumFrom = SinkingMedium.MUD;
    public float physicsMediumBlend = 1.0F;
    public BlockPos physicsProfilePos;
    public BlockPos lastClientPhysicsProfilePos;
    public int lastClientPhysicsProfileSyncTick = Integer.MIN_VALUE;

    public void tickCooldowns() {
        if (struggleCooldown > 0) {
            struggleCooldown--;
        } else if (!holdingStruggle && strugglePower > 0) {
            strugglePower--;
        }
        if (holdingStruggle) {
            struggleHold = Math.min(20, struggleHold + 1);
        }
        if (liftTicks > 0) {
            liftTicks--;
        } else if (struggleCharge > 0.0F) {
            struggleCharge = Math.max(0.0F, struggleCharge - 0.08F);
        }
        if (agitation > 0.0F) {
            agitation = Math.max(0.0F, agitation - physicsMedium.agitationDecay());
        }
    }

    public void updateMudImpactTracking(
            Vec3 feet, Vec3 velocity, boolean insideMud) {
        mudImpactPreviousFeet = feet;
        mudImpactPreviousVelocity = velocity;
        mudImpactWasInside = insideMud;
        mudImpactTrackingInitialized = true;
    }

    public void resetMudImpactTracking() {
        mudImpactPreviousFeet = Vec3.ZERO;
        mudImpactPreviousVelocity = Vec3.ZERO;
        mudImpactWasInside = false;
        mudImpactTrackingInitialized = false;
    }

    public float[] footprintMediumWeightsScratch() {
        return footprintMediumWeightsScratch;
    }

    public void resetLivingSlimeState() {
        livingSlimeState.reset();
    }

    public void resetSculkMireState() {
        sculkMireState.reset();
        sculkMovementIntent = 0.0F;
        sculkJumpIntent = false;
        sculkCrouchIntent = false;
        sculkInputAge = 20;
    }

    public void resetTenderFleshState() {
        tenderFleshState.reset();
    }

    public void resetPhysicsState() {
        pendingStruggleCharge = -1.0F;
        liftTicks = 0;
        settlingVelocity = 0.0D;
        resetSculkMireState();
        resetTenderFleshState();
        physicsMedium = SinkingMedium.MUD;
        physicsMediumFrom = SinkingMedium.MUD;
        physicsMediumBlend = 1.0F;
        physicsProfilePos = null;
        lastClientPhysicsProfilePos = null;
        lastClientPhysicsProfileSyncTick = Integer.MIN_VALUE;
        debugPhysicalized = false;
    }

    public void resetFootprintTracking() {
        lastFootprintTick = Integer.MIN_VALUE;
        leftFootprintResidue = 0.0F;
        rightFootprintResidue = 0.0F;
        wallStainResidue = 0.0F;
        wallStainObservedCoverage = 0.0F;
        lastWallStainTick = Integer.MIN_VALUE;
        footprintTravel = 0.0D;
        footprintLastX = 0.0D;
        footprintLastZ = 0.0D;
        footprintDirectionX = 0.0D;
        footprintDirectionZ = 0.0D;
        nextFootprintLeft = true;
        footprintTrackingInitialized = false;
        footprintDirectionInitialized = false;
        wallStainTrackingInitialized = false;
        wallStainWasInMud = false;
    }

    public void setCoverage(float value) {
        coverage = Mth.clamp(value, 0.0F, 1.0F);
    }

    public void setPartCoverage(MudBodyPart part, float value) {
        partCoverage[part.ordinal()] = Mth.clamp(value, 0.0F, 1.0F);
    }

    public float partCoverage(MudBodyPart part) {
        return partCoverage[part.ordinal()];
    }

    public void setBandCoverage(MudBodyPart part, int band, float value) {
        bandCoverage[bandIndex(part, band)] = Mth.clamp(value, 0.0F, 1.0F);
        refreshPartCoverage(part);
    }

    public float bandCoverage(MudBodyPart part, int band) {
        return bandCoverage[bandIndex(part, band)];
    }

    public void setSurfaceCoverage(MudBodyPart part, int band, MudSurface surface, float value) {
        setSurfaceCoverage(part, band, surface, value, medium);
    }

    public void setSurfaceCoverage(MudBodyPart part, int band, MudSurface surface, float value, SinkingMedium medium) {
        MudSurfaceLayout.Face face = MudSurfaceLayout.face(part, surface);
        for (int row = 0; row < face.height(); row++) {
            if (MudSurfaceLayout.legacyBand(part, surface, row) != band) {
                continue;
            }
            for (int column = 0; column < face.width(); column++) {
                setSurfacePixelCoverageValue(part, surface, row, column, value, medium);
            }
        }
        refreshAllCoverage();
    }

    public void setSurfaceCoverage(MudBodyPart part, int band, MudSurface surface, int lane, float value) {
        setSurfaceCoverage(part, band, surface, lane, value, medium);
    }

    public void setSurfaceCoverage(MudBodyPart part, int band, MudSurface surface, int lane, float value, SinkingMedium medium) {
        MudSurfaceLayout.Face face = MudSurfaceLayout.face(part, surface);
        for (int row = 0; row < face.height(); row++) {
            if (MudSurfaceLayout.legacyBand(part, surface, row) != band) {
                continue;
            }
            for (int column = 0; column < face.width(); column++) {
                if (MudSurfaceLayout.legacyLane(part, surface, column) == lane) {
                    setSurfacePixelCoverageValue(part, surface, row, column, value, medium);
                }
            }
        }
        refreshAllCoverage();
    }

    public float surfaceCoverage(MudBodyPart part, int band, MudSurface surface) {
        float max = 0.0F;
        for (int lane = 0; lane < MudBodyPart.SURFACE_LANES; lane++) {
            max = Math.max(max, surfaceCoverage(part, band, surface, lane));
        }
        return max;
    }

    public float surfaceCoverage(MudBodyPart part, int band, MudSurface surface, int lane) {
        float max = 0.0F;
        MudSurfaceLayout.Face face = MudSurfaceLayout.face(part, surface);
        for (int row = 0; row < face.height(); row++) {
            if (MudSurfaceLayout.legacyBand(part, surface, row) != band) {
                continue;
            }
            for (int column = 0; column < face.width(); column++) {
                if (MudSurfaceLayout.legacyLane(part, surface, column) == lane) {
                    max = Math.max(max, surfacePixelCoverage(part, surface, row, column));
                }
            }
        }
        return max;
    }

    public SinkingMedium surfaceMedium(MudBodyPart part, int band, MudSurface surface, int lane) {
        float best = 0.0F;
        SinkingMedium medium = SinkingMedium.MUD;
        MudSurfaceLayout.Face face = MudSurfaceLayout.face(part, surface);
        for (int row = 0; row < face.height(); row++) {
            if (MudSurfaceLayout.legacyBand(part, surface, row) != band) {
                continue;
            }
            for (int column = 0; column < face.width(); column++) {
                if (MudSurfaceLayout.legacyLane(part, surface, column) != lane) {
                    continue;
                }
                int index = MudSurfaceLayout.cellIndex(part, surface, row, column);
                if (surfaceCoverage[index] > best) {
                    best = surfaceCoverage[index];
                    medium = SinkingMedium.byId(surfaceMedium[index] & 0xFF);
                }
            }
        }
        return medium;
    }

    public void setSurfacePixelCoverage(MudBodyPart part, MudSurface surface, int row, int column, float value,
            SinkingMedium medium) {
        setSurfacePixelCoverageValue(part, surface, row, column, value, medium,
                MudCoverageAppearanceSnapshot.GLOBAL_FALLBACK);
    }

    public void setSurfacePixelCoverage(MudBodyPart part, MudSurface surface, int row, int column, float value,
            SinkingMedium medium, int appearance) {
        setSurfacePixelCoverageValue(part, surface, row, column, value, medium, appearance, 0L);
    }

    public void setSurfacePixelCoverage(MudBodyPart part, MudSurface surface,
            int row, int column, float value, SinkingMedium medium,
            int appearance, long visualSource) {
        setSurfacePixelCoverageValue(part, surface, row, column, value, medium,
                appearance, visualSource);
    }

    public float surfacePixelCoverage(MudBodyPart part, MudSurface surface, int row, int column) {
        return surfaceCoverage[MudSurfaceLayout.cellIndex(part, surface, row, column)];
    }

    public SinkingMedium surfacePixelMedium(MudBodyPart part, MudSurface surface, int row, int column) {
        return SinkingMedium.byId(surfaceMedium[MudSurfaceLayout.cellIndex(part, surface, row, column)] & 0xFF);
    }

    public int surfacePixelAppearance(MudBodyPart part, MudSurface surface, int row, int column) {
        return surfaceAppearance[MudSurfaceLayout.cellIndex(part, surface, row, column)];
    }

    public long surfacePixelVisualSource(
            MudBodyPart part, MudSurface surface, int row, int column) {
        return surfaceVisualSource[MudSurfaceLayout.cellIndex(
                part, surface, row, column)];
    }

    public void applyCapeSample(int row, int column, float strength, SinkingMedium medium) {
        applyCapeSample(MudCapeLayout.Side.OUTER, row, column, strength, medium);
    }

    public void applyCapeSample(MudCapeLayout.Side side, int row, int column,
            float strength, SinkingMedium medium) {
        applyCapeSample(side, row, column, strength, medium,
                MudCoverageAppearanceSnapshot.GLOBAL_FALLBACK);
    }

    public void applyCapeSample(MudCapeLayout.Side side, int row, int column,
            float strength, SinkingMedium medium, int appearance) {
        applyCapeSample(side, row, column, strength, medium, appearance, 0L);
    }

    public void applyCapeSample(MudCapeLayout.Side side, int row, int column,
            float strength, SinkingMedium medium, int appearance,
            long visualSource) {
        int index = MudCapeLayout.index(side, row, column);
        if (strength > COVERAGE_CLEAR_THRESHOLD) {
            ensureCoveragePatternSeed();
        }
        float current = capeCoverage[index];
        boolean sameMedium = capeMedium[index] == (byte) medium.id();
        if (strength <= current && sameMedium && capeAppearance[index] == appearance
                && capeVisualSource[index] == visualSource) {
            return;
        }
        if (strength > current || !sameMedium) {
            capeCoverage[index] = current + (Mth.clamp(strength, 0.0F, 1.0F) - current) * 0.58F;
        }
        capeMedium[index] = (byte) medium.id();
        capeAppearance[index] = appearance;
        capeVisualSource[index] = visualSource;
    }

    public int ensureCoveragePatternSeed() {
        if (!coverageBatchActive) {
            coveragePatternSeed = MudCoveragePatternSeed.next();
            coverageBatchActive = true;
            coveragePersistenceDirty = true;
        } else if (coveragePatternSeed == 0) {
            coveragePatternSeed = MudCoveragePatternSeed.next();
        }
        return coveragePatternSeed;
    }

    public SinkingMedium capePixelMedium(int row, int column) {
        return capePixelMedium(MudCapeLayout.Side.OUTER, row, column);
    }

    public SinkingMedium capePixelMedium(MudCapeLayout.Side side, int row, int column) {
        return SinkingMedium.byId(capeMedium[MudCapeLayout.index(side, row, column)] & 0xFF);
    }

    public int capePixelAppearance(MudCapeLayout.Side side, int row, int column) {
        return capeAppearance[MudCapeLayout.index(side, row, column)];
    }

    public long capePixelVisualSource(MudCapeLayout.Side side, int row, int column) {
        return capeVisualSource[MudCapeLayout.index(side, row, column)];
    }

    public int noteSableSurfaceCandidate(MudBodyPart part, MudSurface surface, int row, int column,
            SinkingMedium medium, int tick) {
        int index = MudSurfaceLayout.cellIndex(part, surface, row, column);
        byte mediumId = (byte) medium.id();
        int previousTick = pendingSableSurfaceLastTick[index];
        if (pendingSableSurfaceMedium[index] == mediumId && tick == previousTick) {
            return pendingSableSurfaceTicks[index] & 0xFF;
        }
        if (pendingSableSurfaceMedium[index] == mediumId && tick == previousTick + 1) {
            pendingSableSurfaceTicks[index] = (byte) Math.min(127, (pendingSableSurfaceTicks[index] & 0xFF) + 1);
        } else {
            pendingSableSurfaceMedium[index] = mediumId;
            pendingSableSurfaceTicks[index] = 1;
        }
        pendingSableSurfaceLastTick[index] = tick;
        return pendingSableSurfaceTicks[index] & 0xFF;
    }

    public void clearSableSurfaceCandidate(MudBodyPart part, MudSurface surface, int row, int column) {
        int index = MudSurfaceLayout.cellIndex(part, surface, row, column);
        pendingSableSurfaceMedium[index] = (byte) SinkingMedium.MUD.id();
        pendingSableSurfaceTicks[index] = 0;
        pendingSableSurfaceLastTick[index] = Integer.MIN_VALUE;
    }

    private void clearSableSurfaceCandidates() {
        Arrays.fill(pendingSableSurfaceMedium, (byte) SinkingMedium.MUD.id());
        Arrays.fill(pendingSableSurfaceTicks, (byte) 0);
        Arrays.fill(pendingSableSurfaceLastTick, Integer.MIN_VALUE);
    }

    public void refreshCoverageAfterSurfaceUpdate() {
        refreshAllCoverage();
    }

    /** Removes washable mud from exact canonical body cells opened by another system. */
    public boolean clearSurfaceCoverage(BitSet cells) {
        if (cells == null || cells.isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (int cell = cells.nextSetBit(0);
                cell >= 0 && cell < surfaceCoverage.length;
                cell = cells.nextSetBit(cell + 1)) {
            if (surfaceCoverage[cell] <= COVERAGE_CLEAR_THRESHOLD) {
                continue;
            }
            surfaceCoverage[cell] = 0.0F;
            surfaceMedium[cell] = (byte) SinkingMedium.MUD.id();
            surfaceAppearance[cell] = MudCoverageAppearanceSnapshot.GLOBAL_FALLBACK;
            surfaceVisualSource[cell] = 0L;
            changed = true;
        }
        if (changed) {
            refreshAllCoverage();
            coveragePersistenceDirty = true;
        }
        return changed;
    }

    public boolean blendSurfaceEdges() {
        return MudSurfaceEdgeBlend.blend(surfaceCoverage, surfaceMedium, ignored -> true);
    }

    public boolean blendSurfaceEdges(IntPredicate paintableCell) {
        return MudSurfaceEdgeBlend.blend(surfaceCoverage, surfaceMedium, paintableCell);
    }

    public boolean blendSurfaceEdges(IntPredicate paintableCell,
            MudCoveragePaintPredicate allowsPaint) {
        return MudSurfaceEdgeBlend.blend(
                surfaceCoverage, surfaceMedium, surfaceAppearance, surfaceVisualSource,
                paintableCell, allowsPaint::test);
    }

    public boolean fadeTransferredSurfaceEdges(BitSet transferredCells,
            IntPredicate visibleCell) {
        boolean changed = MudSurfaceFadeBlend.fade(
                surfaceCoverage, surfaceMedium, surfaceAppearance, surfaceVisualSource,
                transferredCells, visibleCell);
        coveragePersistenceDirty |= changed;
        return changed;
    }

    public void clearVisionCoverage() {
        Arrays.fill(visionCoverage, 0.0F);
        Arrays.fill(visionMedium, (byte) SinkingMedium.MUD.id());
        Arrays.fill(visionVisualSource, 0L);
    }

    public void clearArmorContacts() {
        Arrays.fill(armorContactCoverage, (byte) 0);
        Arrays.fill(armorContactMedium, (byte) SinkingMedium.MUD.id());
        Arrays.fill(armorContactVisualSource, 0L);
    }

    void clearSurfaceContacts() {
        Arrays.fill(surfaceContactThisTick, false);
    }

    void markSurfaceContact(int cell) {
        if (cell >= 0 && cell < surfaceContactThisTick.length) {
            surfaceContactThisTick[cell] = true;
        }
    }

    public void beginAssimilationContactFrame(int tick) {
        if (assimilationContactTick != tick) {
            Arrays.fill(assimilationContactWeights, 0.0F);
            assimilationContactVisuals.clear();
            Arrays.fill(assimilationContactHasPosition, false);
            assimilationContactTick = tick;
        }
    }

    void markAssimilationContact(SinkingMedium medium, BlockPos profilePos,
            long visualSource, float strength) {
        if (medium != null && strength > 0.0F) {
            float bounded = Mth.clamp(strength, 0.0F, 1.0F);
            assimilationContactWeights[medium.id()] += bounded;
            assimilationContactVisuals.add(medium, visualSource, bounded);
            if (profilePos != null && !assimilationContactHasPosition[medium.id()]) {
                assimilationContactPositions[medium.id()] = profilePos.asLong();
                assimilationContactHasPosition[medium.id()] = true;
            }
        }
    }

    void markAssimilationContact(SinkingMedium medium, BlockPos profilePos, float strength) {
        markAssimilationContact(medium, profilePos, 0L, strength);
    }

    void markAssimilationContact(SinkingMedium medium, float strength) {
        markAssimilationContact(medium, null, strength);
    }

    public float assimilationContactWeight(SinkingMedium medium, int tick) {
        return medium == null || assimilationContactTick != tick
                ? 0.0F : assimilationContactWeights[medium.id()];
    }

    public BlockPos assimilationContactPosition(SinkingMedium medium, int tick) {
        return medium == null || assimilationContactTick != tick
                || !assimilationContactHasPosition[medium.id()]
                ? null : BlockPos.of(assimilationContactPositions[medium.id()]);
    }

    public MudVisualPalette assimilationContactVisuals(int tick) {
        return assimilationContactTick == tick ? assimilationContactVisuals : null;
    }

    public boolean surfaceContactThisTick(int cell) {
        return cell >= 0 && cell < surfaceContactThisTick.length
                && surfaceContactThisTick[cell];
    }

    public void setArmorContact(net.minecraft.world.entity.EquipmentSlot slot, int cell, float strength,
            SinkingMedium medium) {
        setArmorContact(slot, cell, strength, medium, 0L);
    }

    public void setArmorContact(net.minecraft.world.entity.EquipmentSlot slot, int cell,
            float strength, SinkingMedium medium, long visualSource) {
        int index = armorContactIndex(slot, cell);
        int level = Mth.clamp(Math.round(strength * 255.0F), 0, 255);
        if (level > (armorContactCoverage[index] & 0xFF)) {
            armorContactCoverage[index] = (byte) level;
            armorContactMedium[index] = (byte) medium.id();
            armorContactVisualSource[index] = visualSource;
        }
    }

    public int armorContactCoverage(net.minecraft.world.entity.EquipmentSlot slot, int cell) {
        return armorContactCoverage[armorContactIndex(slot, cell)] & 0xFF;
    }

    public SinkingMedium armorContactMedium(net.minecraft.world.entity.EquipmentSlot slot, int cell) {
        return SinkingMedium.byId(armorContactMedium[armorContactIndex(slot, cell)] & 0xFF);
    }

    public long armorContactVisualSource(
            net.minecraft.world.entity.EquipmentSlot slot, int cell) {
        return armorContactVisualSource[armorContactIndex(slot, cell)];
    }

    private static int armorContactIndex(net.minecraft.world.entity.EquipmentSlot slot, int cell) {
        return ArmorMudManager.armorSlotIndex(slot) * MudSurfaceLayout.CELL_COUNT + cell;
    }

    public void setVisionCoverage(int band, int lane, float value, SinkingMedium medium) {
        setVisionCoverage(band, lane, value, medium, 0L);
    }

    public void setVisionCoverage(int band, int lane, float value,
            SinkingMedium medium, long visualSource) {
        int index = visionIndex(band, lane);
        float clamped = Mth.clamp(value, 0.0F, 1.0F);
        visionCoverage[index] = clamped;
        visionMedium[index] = (byte) (clamped <= COVERAGE_CLEAR_THRESHOLD ? SinkingMedium.MUD.id() : medium.id());
        visionVisualSource[index] = clamped <= COVERAGE_CLEAR_THRESHOLD ? 0L : visualSource;
    }

    public float visionCoverage(int band, int lane) {
        return visionCoverage[visionIndex(band, lane)];
    }

    public SinkingMedium visionMedium(int band, int lane) {
        return SinkingMedium.byId(visionMedium[visionIndex(band, lane)] & 0xFF);
    }

    public long visionVisualSource(int band, int lane) {
        return visionVisualSource[visionIndex(band, lane)];
    }

    public void decayPartCoverage(float amount) {
        for (int i = 0; i < surfaceCoverage.length; i++) {
            surfaceCoverage[i] = Math.max(0.0F, surfaceCoverage[i] - amount);
            if (surfaceCoverage[i] <= COVERAGE_CLEAR_THRESHOLD) {
                surfaceCoverage[i] = 0.0F;
                surfaceMedium[i] = (byte) SinkingMedium.MUD.id();
                surfaceAppearance[i] = MudCoverageAppearanceSnapshot.GLOBAL_FALLBACK;
                surfaceVisualSource[i] = 0L;
            }
        }
        for (int i = 0; i < capeCoverage.length; i++) {
            capeCoverage[i] = Math.max(0.0F, capeCoverage[i] - amount);
            if (capeCoverage[i] <= COVERAGE_CLEAR_THRESHOLD) {
                capeCoverage[i] = 0.0F;
                capeMedium[i] = (byte) SinkingMedium.MUD.id();
                capeAppearance[i] = MudCoverageAppearanceSnapshot.GLOBAL_FALLBACK;
                capeVisualSource[i] = 0L;
            }
        }
        refreshAllCoverage();
    }

    /** Fades persistent skin/cape coverage only after the player leaves mud. */
    public boolean fadeNaturalCoverage(net.minecraft.world.level.Level level) {
        boolean changed = false;
        for (int i = 0; i < surfaceCoverage.length; i++) {
            float current = surfaceCoverage[i];
            if (current <= COVERAGE_CLEAR_THRESHOLD) {
                continue;
            }
            SinkingMedium medium = SinkingMedium.byId(surfaceMedium[i] & 0xFF);
            int fadeTicks = MudMediumRuntime.coverageNaturalFadeTicks(level, medium);
            if (fadeTicks <= 0) {
                continue;
            }
            surfaceCoverage[i] = Math.max(0.0F, current - 1.0F / fadeTicks);
            if (surfaceCoverage[i] <= COVERAGE_CLEAR_THRESHOLD) {
                surfaceCoverage[i] = 0.0F;
                surfaceMedium[i] = (byte) SinkingMedium.MUD.id();
                surfaceAppearance[i] = MudCoverageAppearanceSnapshot.GLOBAL_FALLBACK;
                surfaceVisualSource[i] = 0L;
            }
            changed = true;
        }
        for (int i = 0; i < capeCoverage.length; i++) {
            float current = capeCoverage[i];
            if (current <= COVERAGE_CLEAR_THRESHOLD) {
                continue;
            }
            SinkingMedium medium = SinkingMedium.byId(capeMedium[i] & 0xFF);
            int fadeTicks = MudMediumRuntime.coverageNaturalFadeTicks(level, medium);
            if (fadeTicks <= 0) {
                continue;
            }
            capeCoverage[i] = Math.max(0.0F, current - 1.0F / fadeTicks);
            if (capeCoverage[i] <= COVERAGE_CLEAR_THRESHOLD) {
                capeCoverage[i] = 0.0F;
                capeMedium[i] = (byte) SinkingMedium.MUD.id();
                capeAppearance[i] = MudCoverageAppearanceSnapshot.GLOBAL_FALLBACK;
                capeVisualSource[i] = 0L;
            }
            changed = true;
        }
        if (changed) {
            refreshAllCoverage();
        }
        return changed;
    }

    public void fadeAllCoverage(float multiplier, float clearThreshold) {
        coverage = fadeCoverageValue(coverage, multiplier, clearThreshold);
        for (int i = 0; i < surfaceCoverage.length; i++) {
            surfaceCoverage[i] = fadeCoverageValue(surfaceCoverage[i], multiplier, clearThreshold);
            if (surfaceCoverage[i] <= 0.0F) {
                surfaceMedium[i] = (byte) SinkingMedium.MUD.id();
                surfaceAppearance[i] = MudCoverageAppearanceSnapshot.GLOBAL_FALLBACK;
                surfaceVisualSource[i] = 0L;
            }
        }
        for (int i = 0; i < capeCoverage.length; i++) {
            capeCoverage[i] = fadeCoverageValue(capeCoverage[i], multiplier, clearThreshold);
            if (capeCoverage[i] <= 0.0F) {
                capeMedium[i] = (byte) SinkingMedium.MUD.id();
                capeAppearance[i] = MudCoverageAppearanceSnapshot.GLOBAL_FALLBACK;
                capeVisualSource[i] = 0L;
            }
        }
        refreshAllCoverage();
    }

    public void refreshCoverageSummary() {
        refreshAllCoverage();
    }

    private static float fadeCoverageValue(float value, float multiplier, float clearThreshold) {
        if (value <= clearThreshold) {
            return 0.0F;
        }
        float faded = value * multiplier;
        return faded <= clearThreshold ? 0.0F : faded;
    }

    public void clearSyncedParts() {
        Arrays.fill(partCoverage, 0.0F);
        Arrays.fill(bandCoverage, 0.0F);
        Arrays.fill(surfaceCoverage, 0.0F);
        Arrays.fill(surfaceMedium, (byte) SinkingMedium.MUD.id());
        Arrays.fill(surfaceAppearance, MudCoverageAppearanceSnapshot.GLOBAL_FALLBACK);
        Arrays.fill(surfaceVisualSource, 0L);
        Arrays.fill(capeCoverage, 0.0F);
        Arrays.fill(capeMedium, (byte) SinkingMedium.MUD.id());
        Arrays.fill(capeAppearance, MudCoverageAppearanceSnapshot.GLOBAL_FALLBACK);
        Arrays.fill(capeVisualSource, 0L);
        clearSableSurfaceCandidates();
        clearSurfaceContacts();
        clearVisionCoverage();
        lastSyncedSurfaceCoverage = new byte[0];
        lastSyncedSurfaceMedium = new byte[0];
        lastSyncedSurfaceAppearance = new int[0];
        lastSyncedSurfaceVisualSource = new long[0];
        lastSyncedCapeCoverage = new byte[0];
        lastSyncedCapeMedium = new byte[0];
        lastSyncedCapeAppearance = new int[0];
        lastSyncedCapeVisualSource = new long[0];
        lastSyncedVisionCoverage = new byte[0];
        lastSyncedVisionMedium = new byte[0];
        lastSyncedVisionVisualSource = new long[0];
        lastSyncedMediumId = -1;
        coveragePatternSeed = 0;
        coverageBatchActive = false;
        agitation = 0.0F;
        struggleCharge = 0.0F;
        resetPhysicsState();
        visionObstruction = 0.0F;
        lastSyncedVisionObstruction = -1.0F;
        hasLookSample = false;
        resetLivingSlimeState();
        resetFootprintTracking();
        medium = SinkingMedium.MUD;
    }

    public void clearAfterDeath() {
        clearSyncedParts();
        coverage = 0.0F;
        inMud = false;
        eyeSubmerged = false;
        depth = 0.0D;
        stuckTicks = 0;
        holdingStruggle = false;
        struggleHold = 0;
        pendingStruggleCharge = -1.0F;
        gravityOverrideActive = false;
        clientPollutionSuppressed = false;
        clientPollutionSuppressedUntilTick = Integer.MIN_VALUE;
        tenderFleshHintShown = false;
        tenderFleshEffectiveHintShown = false;
        tenderFleshAbsorbedFeedbacks = 0;
        resetMudImpactTracking();
        resetSyncCache();
    }

    public CompoundTag savePersistent() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG_VERSION, PERSISTENCE_VERSION);
        tag.putFloat(TAG_COVERAGE, coverage);
        tag.putInt(TAG_MEDIUM, medium.id());
        tag.putByteArray(TAG_SURFACE_COVERAGE, packCoverage(surfaceCoverage));
        tag.putByteArray(TAG_SURFACE_MEDIUM, surfaceMedium.clone());
        tag.putIntArray(TAG_SURFACE_APPEARANCE, surfaceAppearance.clone());
        tag.putLongArray(TAG_SURFACE_VISUAL_SOURCE, surfaceVisualSource.clone());
        tag.putByteArray(TAG_CAPE_COVERAGE, packCoverage(capeCoverage));
        tag.putByteArray(TAG_CAPE_MEDIUM, capeMedium.clone());
        tag.putIntArray(TAG_CAPE_APPEARANCE, capeAppearance.clone());
        tag.putLongArray(TAG_CAPE_VISUAL_SOURCE, capeVisualSource.clone());
        tag.putInt(TAG_COVERAGE_PATTERN_SEED, coveragePatternSeed);
        return tag;
    }

    public void loadPersistent(CompoundTag tag) {
        clearSyncedParts();
        coverage = Mth.clamp(tag.getFloat(TAG_COVERAGE), 0.0F, 1.0F);
        medium = SinkingMedium.byId(tag.getInt(TAG_MEDIUM));

        byte[] packedCoverage = tag.getByteArray(TAG_SURFACE_COVERAGE);
        byte[] packedMedium = tag.getByteArray(TAG_SURFACE_MEDIUM);
        int version = tag.getInt(TAG_VERSION);
        float coverageScale = version >= 2 ? PERSISTENCE_COVERAGE_SCALE : OLD_PERSISTENCE_COVERAGE_SCALE;
        if (version >= 3 && packedCoverage.length == MudSurfaceLayout.CELL_COUNT) {
            loadExactSurfaceData(packedCoverage, packedMedium, coverageScale);
        } else {
            migrateLegacySurfaceData(packedCoverage, packedMedium, coverageScale);
        }
        if (version >= 4) {
            loadCapeData(tag.getByteArray(TAG_CAPE_COVERAGE), tag.getByteArray(TAG_CAPE_MEDIUM), version);
        }
        if (version >= 7) {
            loadAppearance(tag.getIntArray(TAG_SURFACE_APPEARANCE), surfaceAppearance);
            loadAppearance(tag.getIntArray(TAG_CAPE_APPEARANCE), capeAppearance);
        }
        if (version >= 8) {
            loadVisualSource(tag.getLongArray(TAG_SURFACE_VISUAL_SOURCE), surfaceVisualSource);
            loadVisualSource(tag.getLongArray(TAG_CAPE_VISUAL_SOURCE), capeVisualSource);
        }
        if (version >= 9) {
            coveragePatternSeed = tag.getInt(TAG_COVERAGE_PATTERN_SEED);
        }

        refreshAllCoverage();
        if (coverageBatchActive && coveragePatternSeed == 0) {
            coveragePatternSeed = MudCoveragePatternSeed.next();
        }
        resetSyncCache();
    }

    public boolean hasPersistentCoverage() {
        if (coverage > COVERAGE_CLEAR_THRESHOLD) {
            return true;
        }
        for (float value : surfaceCoverage) {
            if (value > COVERAGE_CLEAR_THRESHOLD) {
                return true;
            }
        }
        for (float value : capeCoverage) {
            if (value > COVERAGE_CLEAR_THRESHOLD) {
                return true;
            }
        }
        return false;
    }

    public void copyPersistentFrom(MudPlayerData other) {
        loadPersistent(other.savePersistent());
    }

    public void resetSyncCache() {
        lastCoveragePackTick = Integer.MIN_VALUE;
        lastSyncedCoverage = -1.0F;
        lastSyncedVisionObstruction = -1.0F;
        lastSyncedMediumId = -1;
        lastSyncedCoveragePatternSeed = -1;
        lastTenderFleshEnclosureSyncTick = Integer.MIN_VALUE;
        lastSyncedTenderFleshBrokenMask = -1;
        lastSyncedTenderFleshPillarDamagePacked = -1;
        lastSyncedTenderFleshPillarRequiredHitsPacked = -1;
        lastSyncedTenderFleshCooldownTicks = -1;
        lastSyncedTenderFleshProgress = -1.0F;
        lastSyncedTenderFleshAnchorX = Double.NaN;
        lastSyncedTenderFleshAnchorY = Double.NaN;
        lastSyncedTenderFleshAnchorZ = Double.NaN;
        lastSyncedTenderFleshPlayerX = Double.NaN;
        lastSyncedTenderFleshPlayerZ = Double.NaN;
        lastSyncedTenderFleshActive = false;
        lastSyncedTenderFleshRetreating = false;
        lastSyncedSurfaceCoverage = new byte[0];
        lastSyncedSurfaceMedium = new byte[0];
        lastSyncedSurfaceAppearance = new int[0];
        lastSyncedSurfaceVisualSource = new long[0];
        lastSyncedCapeCoverage = new byte[0];
        lastSyncedCapeMedium = new byte[0];
        lastSyncedCapeAppearance = new int[0];
        lastSyncedCapeVisualSource = new long[0];
        lastSyncedVisionCoverage = new byte[0];
        lastSyncedVisionMedium = new byte[0];
        lastSyncedVisionVisualSource = new long[0];
    }

    private void setSurfacePixelCoverageValue(MudBodyPart part, MudSurface surface, int row, int column, float value,
            SinkingMedium medium) {
        setSurfacePixelCoverageValue(part, surface, row, column, value, medium,
                MudCoverageAppearanceSnapshot.GLOBAL_FALLBACK, 0L);
    }

    private void setSurfacePixelCoverageValue(MudBodyPart part, MudSurface surface, int row, int column, float value,
            SinkingMedium medium, int appearance) {
        setSurfacePixelCoverageValue(part, surface, row, column, value, medium,
                appearance, 0L);
    }

    private void setSurfacePixelCoverageValue(MudBodyPart part, MudSurface surface,
            int row, int column, float value, SinkingMedium medium,
            int appearance, long visualSource) {
        int index = MudSurfaceLayout.cellIndex(part, surface, row, column);
        float clamped = Mth.clamp(value, 0.0F, 1.0F);
        if (clamped > COVERAGE_CLEAR_THRESHOLD) {
            ensureCoveragePatternSeed();
        }
        surfaceCoverage[index] = clamped;
        surfaceMedium[index] = (byte) (clamped <= COVERAGE_CLEAR_THRESHOLD ? SinkingMedium.MUD.id() : medium.id());
        surfaceAppearance[index] = clamped <= COVERAGE_CLEAR_THRESHOLD
                ? MudCoverageAppearanceSnapshot.GLOBAL_FALLBACK : appearance;
        surfaceVisualSource[index] = clamped <= COVERAGE_CLEAR_THRESHOLD ? 0L : visualSource;
    }

    private static void loadAppearance(int[] source, int[] target) {
        System.arraycopy(source, 0, target, 0, Math.min(source.length, target.length));
    }

    private static void loadVisualSource(long[] source, long[] target) {
        System.arraycopy(source, 0, target, 0, Math.min(source.length, target.length));
    }

    private void loadExactSurfaceData(byte[] packedCoverage, byte[] packedMedium, float coverageScale) {
        int count = Math.min(surfaceCoverage.length, packedCoverage.length);
        for (int i = 0; i < count; i++) {
            float value = Mth.clamp((packedCoverage[i] & 0xFF) / coverageScale, 0.0F, 1.0F);
            surfaceCoverage[i] = value;
            if (i < packedMedium.length && value > COVERAGE_CLEAR_THRESHOLD) {
                surfaceMedium[i] = packedMedium[i];
            }
        }
    }

    private void loadCapeData(byte[] packedCoverage, byte[] packedMedium, int version) {
        boolean legacySingleFace = version < 6 || packedCoverage.length == MudCapeLayout.FACE_CELL_COUNT;
        int sourceCount = Math.min(
                legacySingleFace ? MudCapeLayout.FACE_CELL_COUNT : capeCoverage.length,
                packedCoverage.length);
        for (int i = 0; i < sourceCount; i++) {
            float value = Mth.clamp(
                    (packedCoverage[i] & 0xFF) / PERSISTENCE_COVERAGE_SCALE, 0.0F, 1.0F);
            byte medium = i < packedMedium.length && value > COVERAGE_CLEAR_THRESHOLD
                    ? packedMedium[i]
                    : (byte) SinkingMedium.MUD.id();
            capeCoverage[i] = value;
            capeMedium[i] = medium;
        }
    }

    private void migrateLegacySurfaceData(byte[] packedCoverage, byte[] packedMedium, float coverageScale) {
        int legacyCount = MudBodyPart.COUNT * MudBodyPart.BANDS * MudSurface.COUNT * MudBodyPart.SURFACE_LANES;
        if (packedCoverage.length < legacyCount) {
            return;
        }
        for (MudBodyPart part : MudBodyPart.values()) {
            for (MudSurface surface : MudSurface.values()) {
                MudSurfaceLayout.Face face = MudSurfaceLayout.face(part, surface);
                for (int row = 0; row < face.height(); row++) {
                    int band = MudSurfaceLayout.legacyBand(part, surface, row);
                    for (int column = 0; column < face.width(); column++) {
                        int lane = MudSurfaceLayout.legacyLane(part, surface, column);
                        int oldIndex = MudSurfaceLayout.legacySurfaceIndex(part, band, surface, lane);
                        float value = Mth.clamp((packedCoverage[oldIndex] & 0xFF) / coverageScale, 0.0F, 1.0F);
                        if (value <= COVERAGE_CLEAR_THRESHOLD) {
                            continue;
                        }
                        int index = MudSurfaceLayout.cellIndex(part, surface, row, column);
                        surfaceCoverage[index] = value;
                        if (oldIndex < packedMedium.length) {
                            surfaceMedium[index] = packedMedium[oldIndex];
                        }
                    }
                }
            }
        }
    }

    private void refreshAllCoverage() {
        for (MudBodyPart part : MudBodyPart.values()) {
            for (int band = 0; band < MudBodyPart.BANDS; band++) {
                refreshBandCoverage(part, band);
            }
            refreshPartCoverage(part);
        }
        refreshOverallCoverageFromParts();
        coverageBatchActive = coverage > COVERAGE_CLEAR_THRESHOLD;
        if (!coverageBatchActive) {
            for (float value : capeCoverage) {
                if (value > COVERAGE_CLEAR_THRESHOLD) {
                    coverageBatchActive = true;
                    break;
                }
            }
        }
    }

    private void refreshBandCoverage(MudBodyPart part, int band) {
        float max = 0.0F;
        for (MudSurface surface : MudSurface.values()) {
            MudSurfaceLayout.Face face = MudSurfaceLayout.face(part, surface);
            for (int row = 0; row < face.height(); row++) {
                if (MudSurfaceLayout.legacyBand(part, surface, row) != band) {
                    continue;
                }
                for (int column = 0; column < face.width(); column++) {
                    max = Math.max(max, surfacePixelCoverage(part, surface, row, column));
                }
            }
        }
        bandCoverage[bandIndex(part, band)] = max;
    }

    private void refreshPartCoverage(MudBodyPart part) {
        float max = 0.0F;
        for (int band = 0; band < MudBodyPart.BANDS; band++) {
            max = Math.max(max, bandCoverage(part, band));
        }
        partCoverage[part.ordinal()] = max;
    }

    private void refreshOverallCoverageFromParts() {
        float max = 0.0F;
        for (MudBodyPart part : MudBodyPart.values()) {
            max = Math.max(max, partCoverage(part));
        }
        coverage = max;
    }

    private static int bandIndex(MudBodyPart part, int band) {
        return part.ordinal() * MudBodyPart.BANDS + Mth.clamp(band, 0, MudBodyPart.BANDS - 1);
    }

    private static int visionIndex(int band, int lane) {
        return Mth.clamp(band, 0, MudBodyPart.VISION_BANDS - 1) * MudBodyPart.VISION_LANES
                + Mth.clamp(lane, 0, MudBodyPart.VISION_LANES - 1);
    }

    private static byte[] packCoverage(float[] values) {
        byte[] packed = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            packed[i] = (byte) Mth.clamp(Math.round(values[i] * PERSISTENCE_COVERAGE_SCALE), 0, 255);
        }
        return packed;
    }

    private static int globalLevel(MudBodyPart part, int band) {
        return switch (part) {
            case HEAD -> MudBodyPart.BANDS * 2 + band;
            case BODY, LEFT_ARM, RIGHT_ARM -> MudBodyPart.BANDS + band;
            case LEFT_LEG, RIGHT_LEG -> band;
        };
    }
}

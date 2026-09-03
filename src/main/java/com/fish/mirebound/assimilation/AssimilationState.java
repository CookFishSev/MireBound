package com.fish.mirebound.assimilation;

import com.fish.mirebound.adaptive.MudVisualPalette;
import com.fish.mirebound.adaptive.MudVisualSource;
import com.fish.mirebound.mud.MudSurfaceLayout;
import com.fish.mirebound.mud.MudPhysicsParameter;
import com.fish.mirebound.mud.MudPhysicsProfiles;
import com.fish.mirebound.mud.SinkingMedium;
import java.util.Arrays;
import java.util.BitSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Persistent server state. Ordinary washable mud never reads or mutates this object. */
final class AssimilationState {
    private static final int VERSION = 13;
    private static final int REVEAL_BYTES = (MudSurfaceLayout.CELL_COUNT + 7) / 8;
    private static final MudPhysicsParameter[] PROFILE_PARAMETERS = Arrays.stream(
                    MudPhysicsParameter.values())
            .filter(parameter -> parameter.category() == MudPhysicsParameter.Category.ASSIMILATION)
            .toArray(MudPhysicsParameter[]::new);
    private static final int MAX_CONTRIBUTION_ENTRIES = SinkingMedium.COUNT;
    private static final int MAX_VISUAL_PALETTE_ENTRIES = MudVisualPalette.MAX_ENTRIES;
    private static final int MAX_RUNTIME_PROFILES = SinkingMedium.COUNT;
    private static final int MAX_RUNTIME_PROFILE_VALUES = PROFILE_PARAMETERS.length;

    AssimilationStage stage = AssimilationStage.NORMAL;
    float progress;
    float shellIntegrity;
    int restoringTicks;
    int rescueGraceTicks;
    Vec3 anchor = Vec3.ZERO;
    Vec3 rigidVelocity = Vec3.ZERO;
    float bodyPitch;
    float bodyRoll;
    boolean carriedLastTick;
    int transportHandoffTicks;
    float frozenYaw;
    float frozenPitch;
    float frozenWalkPosition;
    float frozenWalkSpeed;
    int patternSeed;
    SinkingMedium medium = SinkingMedium.ASSIMILATION_SLIME;
    String templateId = AssimilationBehaviorTemplate.DEFAULT_ID;
    final transient AssimilationProfile[] runtimeProfiles =
            new AssimilationProfile[SinkingMedium.COUNT];
    final transient BlockPos[] runtimeProfilePositions =
            new BlockPos[SinkingMedium.COUNT];
    private final transient double[] mixedProfileValues =
            new double[MudPhysicsParameter.COUNT];
    private final transient double[] profileScratchValues =
            new double[MudPhysicsParameter.COUNT];
    private transient AssimilationProfile mixedRuntimeProfile;
    private transient long mixedRuntimeProfileSignature = Long.MIN_VALUE;
    transient SinkingMedium behaviorMedium;
    transient BlockPos behaviorProfilePosition;
    transient AssimilationProfile behaviorProfile;
    final float[] contributions = new float[SinkingMedium.COUNT];
    final MudVisualPalette visualPalette = new MudVisualPalette();
    String dimension = "";
    final BitSet revealedCells = new BitSet(MudSurfaceLayout.CELL_COUNT);
    final BitSet selfRescueOpenedCells = new BitSet(MudSurfaceLayout.CELL_COUNT);
    int qteCell = -1;
    int qteButton;
    AssimilationQteAction qteAction = AssimilationQteAction.NONE;
    boolean qteHoldActive;
    int qteHoldTicks;
    int qteRapidClicks;
    boolean qteRapidPressed;
    int qteTraceProgress;
    boolean qteTraceActive;
    int qteTicksRemaining;
    int qteStreak;
    int qteCooldownTicks;
    int qteSequence;
    boolean partialPurgeActive;
    float partialPurgeZoneStart;
    float partialPurgeZoneEnd;
    float partialPurgeCursor;
    boolean partialPurgeCursorForward = true;
    int partialPurgeCursorOneWayTicks = 24;
    int partialPurgeCooldownTicks;
    boolean partialPurgeWasCrouching;
    byte partialPurgeResult;
    int partialPurgeResultTicks;
    int partialPurgeRound;
    Vec3 partialPurgeOrigin = Vec3.ZERO;
    int lastPartialPurgeToggleTick = Integer.MIN_VALUE;
    Vec3 soulPosition = Vec3.ZERO;
    int soulPositionTick = Integer.MIN_VALUE;
    long lastSyncSignature = Long.MIN_VALUE;
    int lastSyncTick = Integer.MIN_VALUE;
    int lastSaveTick = Integer.MIN_VALUE;
    String lastSyncedTemplateId = "";
    transient AssimilationProfile lastSyncedProfile;
    boolean dirty;

    boolean active() {
        return stage != AssimilationStage.NORMAL || progress > 0.0001F;
    }

    boolean frozen() {
        return stage.frozen();
    }

    void beginAssimilating(int seed) {
        ensurePatternSeed(seed);
        if (stage == AssimilationStage.NORMAL) {
            stage = AssimilationStage.ASSIMILATING;
            dirty = true;
        }
    }

    boolean addContribution(SinkingMedium activeMedium, float amount) {
        return addContribution(activeMedium,
                visualPalette.dominantVisualSource(activeMedium), amount);
    }

    boolean addContribution(SinkingMedium activeMedium, long visualSource, float amount) {
        float accepted = AssimilationContributions.add(contributions, activeMedium, amount);
        if (accepted <= 0.0F) {
            return false;
        }
        visualPalette.add(activeMedium, visualSource, accepted);
        refreshContributionSummary(activeMedium);
        dirty = true;
        return true;
    }

    void setProgress(SinkingMedium activeMedium, float value) {
        SinkingMedium selected = activeMedium == null ? medium : activeMedium;
        long visualSource = visualPalette.dominantVisualSource(selected);
        AssimilationContributions.setSingle(contributions, selected, value);
        visualPalette.setSingle(selected, visualSource, Mth.clamp(value, 0.0F, 1.0F));
        refreshContributionSummary(selected);
        dirty = true;
    }

    float contribution(SinkingMedium activeMedium) {
        return activeMedium == null ? 0.0F : contributions[activeMedium.id()];
    }

    float removeContributions(float amount) {
        SinkingMedium fallback = medium;
        float removed = AssimilationContributions.removeProportional(contributions, amount);
        if (removed > 0.0F) {
            visualPalette.removeProportional(removed);
            refreshContributionSummary(fallback);
            dirty = true;
        }
        return removed;
    }

    byte[] packedContributions() {
        return AssimilationContributions.packNetwork(contributions);
    }

    void ensurePatternSeed(int seed) {
        if (patternSeed != 0) {
            return;
        }
        patternSeed = seed == 0 ? 0x4F1BBCDD : seed;
        dirty = true;
    }

    void seal(Vec3 position, String dimensionId, float yaw, float pitch,
            float walkPosition, float walkSpeed) {
        if (progress < 1.0F) {
            float remaining = AssimilationContributions.add(
                    contributions, medium, 1.0F - progress);
            visualPalette.add(medium,
                    visualPalette.dominantVisualSource(medium), remaining);
        }
        stage = AssimilationStage.SEALED;
        refreshContributionSummary(medium);
        shellIntegrity = 1.0F;
        restoringTicks = 0;
        anchor = position;
        rigidVelocity = Vec3.ZERO;
        bodyPitch = 0.0F;
        bodyRoll = 0.0F;
        carriedLastTick = false;
        transportHandoffTicks = 0;
        dimension = dimensionId;
        frozenYaw = yaw;
        frozenPitch = pitch;
        frozenWalkPosition = walkPosition;
        frozenWalkSpeed = walkSpeed;
        revealedCells.clear();
        selfRescueOpenedCells.clear();
        clearQte();
        clearPartialPurge();
        soulPosition = Vec3.ZERO;
        soulPositionTick = Integer.MIN_VALUE;
        dirty = true;
    }

    void beginRestoring(int ticks) {
        stage = AssimilationStage.RESTORING;
        restoringTicks = Math.max(1, ticks);
        qteCell = -1;
        qteButton = 0;
        qteAction = AssimilationQteAction.NONE;
        qteHoldActive = false;
        qteHoldTicks = 0;
        qteRapidClicks = 0;
        qteRapidPressed = false;
        qteTraceProgress = 0;
        qteTraceActive = false;
        qteTicksRemaining = 0;
        dirty = true;
    }

    void reset(int graceTicks) {
        stage = AssimilationStage.NORMAL;
        progress = 0.0F;
        shellIntegrity = 0.0F;
        restoringTicks = 0;
        rescueGraceTicks = Math.max(0, graceTicks);
        anchor = Vec3.ZERO;
        rigidVelocity = Vec3.ZERO;
        bodyPitch = 0.0F;
        bodyRoll = 0.0F;
        carriedLastTick = false;
        transportHandoffTicks = 0;
        dimension = "";
        patternSeed = 0;
        medium = SinkingMedium.ASSIMILATION_SLIME;
        templateId = AssimilationBehaviorTemplate.DEFAULT_ID;
        Arrays.fill(runtimeProfiles, null);
        Arrays.fill(runtimeProfilePositions, null);
        invalidateRuntimeProfileMix();
        clearBehaviorContext();
        lastSyncedProfile = null;
        Arrays.fill(contributions, 0.0F);
        visualPalette.clear();
        revealedCells.clear();
        selfRescueOpenedCells.clear();
        clearQte();
        clearPartialPurge();
        soulPosition = Vec3.ZERO;
        soulPositionTick = Integer.MIN_VALUE;
        dirty = true;
    }

    void clearQte() {
        qteCell = -1;
        qteButton = 0;
        qteAction = AssimilationQteAction.NONE;
        qteHoldActive = false;
        qteHoldTicks = 0;
        qteRapidClicks = 0;
        qteRapidPressed = false;
        qteTraceProgress = 0;
        qteTraceActive = false;
        qteTicksRemaining = 0;
        qteStreak = 0;
        qteCooldownTicks = 0;
        qteSequence = 0;
        selfRescueOpenedCells.clear();
    }

    void rememberRuntimeProfile(
            SinkingMedium activeMedium, BlockPos pos, AssimilationProfile profile) {
        if (activeMedium == null || profile == null) {
            return;
        }
        int index = activeMedium.id();
        BlockPos immutablePos = pos == null ? null : pos.immutable();
        if (profile.equals(runtimeProfiles[index])
                && java.util.Objects.equals(immutablePos, runtimeProfilePositions[index])) {
            return;
        }
        runtimeProfiles[index] = profile;
        runtimeProfilePositions[index] = immutablePos;
        invalidateRuntimeProfileMix();
    }

    AssimilationProfile runtimeProfile() {
        float totalWeight = 0.0F;
        for (SinkingMedium activeMedium : SinkingMedium.values()) {
            float weight = contributions[activeMedium.id()];
            AssimilationProfile profile = runtimeProfiles[activeMedium.id()];
            if (weight <= 0.0001F || profile == null) {
                continue;
            }
            totalWeight += weight;
        }
        if (totalWeight <= 0.0001F) {
            return medium == null ? null : runtimeProfiles[medium.id()];
        }
        long signature = 1L;
        for (SinkingMedium activeMedium : SinkingMedium.values()) {
            float weight = contributions[activeMedium.id()];
            AssimilationProfile profile = runtimeProfiles[activeMedium.id()];
            if (weight <= 0.0001F || profile == null) {
                continue;
            }
            signature = signature * 31L + activeMedium.id();
            signature = signature * 31L + Math.round(weight / totalWeight * 64.0F);
            signature = signature * 31L + profile.hashCode();
        }
        if (mixedRuntimeProfile != null && signature == mixedRuntimeProfileSignature) {
            return mixedRuntimeProfile;
        }
        Arrays.fill(mixedProfileValues, 0.0D);
        for (SinkingMedium activeMedium : SinkingMedium.values()) {
            float weight = contributions[activeMedium.id()];
            AssimilationProfile profile = runtimeProfiles[activeMedium.id()];
            if (weight <= 0.0001F || profile == null) {
                continue;
            }
            Arrays.fill(profileScratchValues, 0.0D);
            profile.writeTo(profileScratchValues);
            float share = weight / totalWeight;
            for (MudPhysicsParameter parameter : PROFILE_PARAMETERS) {
                int index = parameter.ordinal();
                mixedProfileValues[index] += profileScratchValues[index] * share;
            }
        }
        mixedRuntimeProfile = AssimilationProfile.fromValues(mixedProfileValues);
        mixedRuntimeProfileSignature = signature;
        return mixedRuntimeProfile;
    }

    private void invalidateRuntimeProfileMix() {
        mixedRuntimeProfile = null;
        mixedRuntimeProfileSignature = Long.MIN_VALUE;
    }

    void setBehaviorContext(SinkingMedium activeMedium, BlockPos profilePosition,
            AssimilationProfile profile) {
        behaviorMedium = activeMedium;
        behaviorProfilePosition = profilePosition == null ? null : profilePosition.immutable();
        behaviorProfile = profile;
    }

    void clearBehaviorContext() {
        behaviorMedium = null;
        behaviorProfilePosition = null;
        behaviorProfile = null;
    }

    void clearPartialPurge() {
        partialPurgeActive = false;
        partialPurgeZoneStart = 0.0F;
        partialPurgeZoneEnd = 0.0F;
        partialPurgeCursor = 0.0F;
        partialPurgeCursorForward = true;
        partialPurgeCursorOneWayTicks = 24;
        partialPurgeCooldownTicks = 0;
        partialPurgeWasCrouching = false;
        partialPurgeResult = AssimilationPartialPurge.RESULT_NONE;
        partialPurgeResultTicks = 0;
        partialPurgeRound = 0;
        partialPurgeOrigin = Vec3.ZERO;
    }

    void applySelfRescueSuccess(BitSet opened, AssimilationProfile profile) {
        revealedCells.or(opened);
        selfRescueOpenedCells.or(opened);
        qteStreak++;
        shellIntegrity = Mth.clamp(shellIntegrity
                - 1.0F / Math.max(1, profile.selfRescueQteRequiredStreak()), 0.0F, 1.0F);
        dirty = true;
    }

    void makeCrackExternal(BitSet opened) {
        selfRescueOpenedCells.andNot(opened);
    }

    void rollbackSelfRescue(AssimilationProfile profile) {
        revealedCells.andNot(selfRescueOpenedCells);
        selfRescueOpenedCells.clear();
        shellIntegrity = Mth.clamp(shellIntegrity
                + qteStreak / (float) Math.max(1, profile.selfRescueQteRequiredStreak()),
                0.0F, 1.0F);
        dirty = true;
    }

    byte[] revealBytes() {
        byte[] source = revealedCells.toByteArray();
        if (source.length <= REVEAL_BYTES) {
            return source;
        }
        byte[] bounded = new byte[REVEAL_BYTES];
        System.arraycopy(source, 0, bounded, 0, bounded.length);
        return bounded;
    }

    long syncSignature() {
        long value = stage.ordinal();
        value = value * 31L + Math.round(progress * 1000.0F);
        value = value * 31L + Math.round(shellIntegrity * 1000.0F);
        value = value * 31L + restoringTicks;
        value = value * 31L + revealedCells.hashCode();
        value = value * 31L + qteCell;
        value = value * 31L + qteButton;
        value = value * 31L + qteAction.ordinal();
        value = value * 31L + qteRapidClicks;
        value = value * 31L + qteTraceProgress;
        value = value * 31L + qteStreak;
        value = value * 31L + qteSequence;
        value = value * 31L + (partialPurgeActive ? 1L : 0L);
        value = value * 31L + Math.round(partialPurgeZoneStart * 1000.0F);
        value = value * 31L + Math.round(partialPurgeZoneEnd * 1000.0F);
        value = value * 31L + Math.round(partialPurgeCursor * 1000.0F);
        value = value * 31L + (partialPurgeCursorForward ? 1L : 0L);
        value = value * 31L + partialPurgeCursorOneWayTicks;
        value = value * 31L + partialPurgeCooldownTicks;
        value = value * 31L + partialPurgeResult;
        value = value * 31L + partialPurgeResultTicks;
        value = value * 31L + partialPurgeRound;
        value = value * 31L + patternSeed;
        for (int mediumId = 0; mediumId < SinkingMedium.COUNT; mediumId++) {
            value = value * 31L + Math.round(contributions[mediumId] * 1000.0F);
        }
        for (int index = 0; index < visualPalette.size(); index++) {
            value = value * 31L + visualPalette.mediumAt(index).id();
            value = value * 31L + visualPalette.visualSourceAt(index);
            value = value * 31L + Math.round(visualPalette.weightAt(index) * 1000.0F);
        }
        value = value * 31L + anchor.hashCode();
        value = value * 31L + Math.round(bodyPitch * 20.0F);
        value = value * 31L + Math.round(bodyRoll * 20.0F);
        return value;
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", VERSION);
        tag.putInt("Stage", stage.ordinal());
        tag.putFloat("Progress", progress);
        tag.putFloat("ShellIntegrity", shellIntegrity);
        tag.putInt("RestoringTicks", restoringTicks);
        tag.putInt("RescueGraceTicks", rescueGraceTicks);
        tag.putDouble("AnchorX", anchor.x);
        tag.putDouble("AnchorY", anchor.y);
        tag.putDouble("AnchorZ", anchor.z);
        tag.putDouble("RigidVelocityX", rigidVelocity.x);
        tag.putDouble("RigidVelocityY", rigidVelocity.y);
        tag.putDouble("RigidVelocityZ", rigidVelocity.z);
        tag.putFloat("BodyPitch", bodyPitch);
        tag.putFloat("BodyRoll", bodyRoll);
        tag.putFloat("FrozenYaw", frozenYaw);
        tag.putFloat("FrozenPitch", frozenPitch);
        tag.putFloat("FrozenWalkPosition", frozenWalkPosition);
        tag.putFloat("FrozenWalkSpeed", frozenWalkSpeed);
        tag.putInt("PatternSeed", patternSeed);
        tag.putInt("Medium", medium.id());
        tag.putString("Template", templateId);
        tag.putIntArray("Contributions",
                AssimilationContributions.packPersistent(contributions));
        tag.putIntArray("VisualPalette", visualPalette.packPersistentEntries());
        tag.putLongArray("VisualSources", visualPalette.packVisualSources());
        tag.put("RuntimeProfiles", saveRuntimeProfiles());
        tag.putString("Dimension", dimension);
        tag.putByteArray("RevealedCells", revealBytes());
        tag.putByteArray("SelfRescueOpenedCells", boundedBytes(selfRescueOpenedCells));
        tag.putInt("QteCell", qteCell);
        tag.putInt("QteButton", qteButton);
        tag.putInt("QteAction", qteAction.ordinal());
        tag.putInt("QteRapidClicks", qteRapidClicks);
        tag.putInt("QteTicksRemaining", qteTicksRemaining);
        tag.putInt("QteStreak", qteStreak);
        tag.putInt("QteCooldownTicks", qteCooldownTicks);
        tag.putInt("QteSequence", qteSequence);
        return tag;
    }

    void load(CompoundTag tag) {
        Arrays.fill(runtimeProfiles, null);
        Arrays.fill(runtimeProfilePositions, null);
        invalidateRuntimeProfileMix();
        stage = AssimilationStage.byId(tag.getInt("Stage"));
        float legacyProgress = Mth.clamp(tag.getFloat("Progress"), 0.0F, 1.0F);
        shellIntegrity = Mth.clamp(tag.getFloat("ShellIntegrity"), 0.0F, 1.0F);
        restoringTicks = Math.max(0, tag.getInt("RestoringTicks"));
        rescueGraceTicks = Math.max(0, tag.getInt("RescueGraceTicks"));
        anchor = new Vec3(tag.getDouble("AnchorX"), tag.getDouble("AnchorY"), tag.getDouble("AnchorZ"));
        rigidVelocity = new Vec3(tag.getDouble("RigidVelocityX"),
                tag.getDouble("RigidVelocityY"), tag.getDouble("RigidVelocityZ"));
        bodyPitch = tag.getFloat("BodyPitch");
        bodyRoll = tag.getFloat("BodyRoll");
        carriedLastTick = false;
        transportHandoffTicks = 0;
        frozenYaw = tag.getFloat("FrozenYaw");
        frozenPitch = tag.getFloat("FrozenPitch");
        frozenWalkPosition = tag.getFloat("FrozenWalkPosition");
        frozenWalkSpeed = tag.getFloat("FrozenWalkSpeed");
        patternSeed = tag.getInt("PatternSeed");
        medium = tag.contains("Medium")
                ? SinkingMedium.byId(tag.getInt("Medium"))
                : SinkingMedium.ASSIMILATION_SLIME;
        templateId = tag.contains("Template")
                ? tag.getString("Template") : AssimilationBehaviorTemplate.DEFAULT_ID;
        int[] packedContributions = boundedIntArray(
                tag, "Contributions", MAX_CONTRIBUTION_ENTRIES);
        if (packedContributions != null) {
            AssimilationContributions.unpackPersistent(packedContributions, contributions);
        } else {
            AssimilationContributions.setSingle(contributions, medium, legacyProgress);
        }
        int[] packedPalette = boundedIntArray(
                tag, "VisualPalette", MAX_VISUAL_PALETTE_ENTRIES);
        long[] packedSources = boundedLongArray(
                tag, "VisualSources", MAX_VISUAL_PALETTE_ENTRIES);
        if (packedPalette != null && packedSources != null) {
            visualPalette.unpackPersistent(packedPalette, packedSources);
            visualPalette.scaleTo(AssimilationContributions.total(contributions));
        } else {
            restoreDefaultVisualPalette();
        }
        if (tag.contains("RuntimeProfiles", Tag.TAG_LIST)) {
            loadRuntimeProfiles(tag.getList("RuntimeProfiles", Tag.TAG_COMPOUND));
        }
        refreshContributionSummary(medium);
        dimension = tag.getString("Dimension");
        revealedCells.clear();
        loadBoundedBitSet(revealedCells,
                boundedByteArray(tag, "RevealedCells", REVEAL_BYTES));
        selfRescueOpenedCells.clear();
        loadBoundedBitSet(selfRescueOpenedCells,
                boundedByteArray(tag, "SelfRescueOpenedCells", REVEAL_BYTES));
        selfRescueOpenedCells.and(revealedCells);
        qteCell = tag.contains("QteCell") ? tag.getInt("QteCell") : -1;
        qteButton = Mth.clamp(tag.getInt("QteButton"), 0, 2);
        qteAction = tag.contains("QteAction")
                ? AssimilationQteAction.byId(tag.getInt("QteAction"))
                : qteCell >= 0 ? AssimilationQteAction.CLICK : AssimilationQteAction.NONE;
        qteHoldActive = false;
        qteHoldTicks = 0;
        qteRapidClicks = Math.max(0, tag.getInt("QteRapidClicks"));
        qteRapidPressed = false;
        qteTraceProgress = 0;
        qteTraceActive = false;
        qteTicksRemaining = Math.max(0, tag.getInt("QteTicksRemaining"));
        qteStreak = Math.max(0, tag.getInt("QteStreak"));
        qteCooldownTicks = Math.max(0, tag.getInt("QteCooldownTicks"));
        qteSequence = Math.max(0, tag.getInt("QteSequence"));
        soulPosition = anchor;
        soulPositionTick = Integer.MIN_VALUE;
        clearPartialPurge();
        if (!stage.equals(AssimilationStage.SEALED)
                || qteCell < 0 || qteCell >= MudSurfaceLayout.CELL_COUNT) {
            qteCell = -1;
            qteButton = 0;
            qteAction = AssimilationQteAction.NONE;
            qteRapidClicks = 0;
            qteTraceProgress = 0;
            qteTicksRemaining = 0;
        } else if (qteAction == AssimilationQteAction.NONE) {
            qteAction = AssimilationQteAction.CLICK;
        }
        if (!active()) {
            reset(0);
        }
        dirty = false;
        lastSyncSignature = Long.MIN_VALUE;
        lastSyncedTemplateId = "";
        lastSyncedProfile = null;
    }

    private ListTag saveRuntimeProfiles() {
        ListTag list = new ListTag();
        for (SinkingMedium activeMedium : SinkingMedium.values()) {
            AssimilationProfile profile = runtimeProfiles[activeMedium.id()];
            if (profile == null) {
                continue;
            }
            double[] values = new double[MudPhysicsParameter.COUNT];
            profile.writeTo(values);
            long[] packed = new long[PROFILE_PARAMETERS.length];
            for (int index = 0; index < PROFILE_PARAMETERS.length; index++) {
                packed[index] = Double.doubleToRawLongBits(
                        values[PROFILE_PARAMETERS[index].ordinal()]);
            }
            CompoundTag entry = new CompoundTag();
            entry.putInt("Medium", activeMedium.id());
            entry.putLongArray("Values", packed);
            BlockPos pos = runtimeProfilePositions[activeMedium.id()];
            if (pos != null) {
                entry.putLong("Pos", pos.asLong());
            }
            list.add(entry);
        }
        return list;
    }

    private void loadRuntimeProfiles(ListTag list) {
        int entryCount = Math.min(list.size(), MAX_RUNTIME_PROFILES);
        for (int entryIndex = 0; entryIndex < entryCount; entryIndex++) {
            CompoundTag entry = list.getCompound(entryIndex);
            int mediumId = entry.getInt("Medium");
            long[] packed = boundedLongArray(
                    entry, "Values", MAX_RUNTIME_PROFILE_VALUES);
            if (mediumId < 0 || mediumId >= SinkingMedium.COUNT
                    || packed == null || packed.length == 0) {
                continue;
            }
            SinkingMedium activeMedium = SinkingMedium.byId(mediumId);
            double[] values = MudPhysicsProfiles.defaultValues(activeMedium);
            int count = Math.min(packed.length, PROFILE_PARAMETERS.length);
            for (int index = 0; index < count; index++) {
                values[PROFILE_PARAMETERS[index].ordinal()] = Double.longBitsToDouble(packed[index]);
            }
            runtimeProfiles[mediumId] = AssimilationProfile.fromValues(values);
            if (entry.contains("Pos", Tag.TAG_LONG)) {
                runtimeProfilePositions[mediumId] = BlockPos.of(entry.getLong("Pos"));
            }
        }
    }

    private void restoreDefaultVisualPalette() {
        visualPalette.clear();
        for (SinkingMedium activeMedium : SinkingMedium.values()) {
            float weight = contributions[activeMedium.id()];
            if (weight > 0.0F) {
                visualPalette.add(activeMedium, MudVisualSource.NONE, weight);
            }
        }
    }

    private static void loadBoundedBitSet(BitSet target, byte[] packed) {
        if (packed == null || packed.length == 0) {
            return;
        }
        target.or(BitSet.valueOf(packed));
        if (target.length() > MudSurfaceLayout.CELL_COUNT) {
            target.clear(MudSurfaceLayout.CELL_COUNT, target.length());
        }
    }

    private static int[] boundedIntArray(CompoundTag tag, String key, int maximumLength) {
        Tag value = tag.get(key);
        if (!(value instanceof net.minecraft.nbt.IntArrayTag array)
                || array.size() > maximumLength) {
            return null;
        }
        return array.getAsIntArray();
    }

    private static long[] boundedLongArray(CompoundTag tag, String key, int maximumLength) {
        Tag value = tag.get(key);
        if (!(value instanceof net.minecraft.nbt.LongArrayTag array)
                || array.size() > maximumLength) {
            return null;
        }
        return array.getAsLongArray();
    }

    private static byte[] boundedByteArray(CompoundTag tag, String key, int maximumLength) {
        Tag value = tag.get(key);
        if (!(value instanceof net.minecraft.nbt.ByteArrayTag array)
                || array.size() > maximumLength) {
            return null;
        }
        return array.getAsByteArray();
    }

    private void refreshContributionSummary(SinkingMedium fallback) {
        progress = AssimilationContributions.total(contributions);
        medium = AssimilationContributions.dominant(contributions, fallback);
    }

    private static byte[] boundedBytes(BitSet cells) {
        byte[] source = cells.toByteArray();
        if (source.length <= REVEAL_BYTES) {
            return source;
        }
        byte[] bounded = new byte[REVEAL_BYTES];
        System.arraycopy(source, 0, bounded, 0, bounded.length);
        return bounded;
    }
}

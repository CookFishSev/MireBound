package com.fish.mirebound.assimilation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fish.mirebound.adaptive.MudVisualPalette;
import com.fish.mirebound.mud.MudSurfaceLayout;
import com.fish.mirebound.mud.MudSurface;
import com.fish.mirebound.mud.MudPhysicsParameter;
import com.fish.mirebound.mud.MudPhysicsProfiles;
import com.fish.mirebound.mud.SinkingMedium;
import java.util.BitSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class AssimilationMechanicsTest {
    private static final AssimilationProfile PROFILE = AssimilationProfile.DEFAULT;

    @Test
    void immersionGainIsBoundedAndMonotonic() {
        assertEquals(0.0F, PROFILE.gainForImmersion(-1.0F), 1.0E-7F);
        float shallow = PROFILE.gainForImmersion(0.25F);
        float deep = PROFILE.gainForImmersion(0.75F);
        assertTrue(shallow > 0.0F);
        assertTrue(deep > shallow);
        assertEquals(PROFILE.gainPerTick(), PROFILE.gainForImmersion(2.0F), 1.0E-7F);
        assertEquals(250.0F, 1.0F / PROFILE.gainPerTick(), 0.01F);
        assertTrue(PROFILE.ordinaryCoverageEnabled());
        assertTrue(PROFILE.soulEmergenceBackOffset() > 0.0F);
        assertTrue(PROFILE.rescueCrackDarkness() > 0.0F);
        assertTrue(PROFILE.soulBaseEffect() > 0.0F);
        assertTrue(PROFILE.soulFogOpacity() > 0.0F);
        assertEquals(16.0F, PROFILE.soulRadius(), 1.0E-7F);
        assertEquals(3.75F, PROFILE.soulBlurRadius(), 1.0E-7F);
        assertEquals(0.26F, PROFILE.soulBaseFogStrength(), 1.0E-7F);
        assertEquals(6.0F, PROFILE.soulFogDistance(), 1.0E-7F);
        assertEquals(6, PROFILE.shellTransportHandoffTicks());
        assertTrue(PROFILE.selfRescueQteEnabled());
        assertEquals(6, PROFILE.selfRescueQteRequiredStreak());
        assertTrue(PROFILE.selfRescueQteTimeoutTicks() > PROFILE.selfRescueQteNextDelayTicks());
        assertTrue(PROFILE.selfRescueQteHoldChance() > 0.0F);
        assertTrue(PROFILE.selfRescueQteHoldTicks() > 0);
        assertTrue(PROFILE.selfRescueQteRapidChance() > 0.0F);
        assertEquals(3, PROFILE.selfRescueQteRapidClicks());
        assertTrue(PROFILE.selfRescueQteTraceChance() > 0.0F);
        assertEquals(240, PROFILE.selfRescueQteTraceTimeoutTicks());
        assertEquals(10, PROFILE.selfRescueQteTraceNodes());
        assertEquals(18, PROFILE.selfRescueQteTraceSpacing());
        assertEquals(8.0F, PROFILE.selfRescueQteTraceHitRadius(), 1.0E-7F);
        assertEquals(4.5F, PROFILE.selfRescueQteRange(), 1.0E-7F);
        assertEquals(32, PROFILE.restoreTicks());
        assertEquals(6, PROFILE.restoreBlackoutFadeTicks());
        assertTrue(PROFILE.restoreBlackoutFadeTicks() * 2 <= PROFILE.restoreTicks());
    }

    @Test
    void movementLookAndAnimationSlowContinuously() {
        assertEquals(1.0F, PROFILE.movementScale(0.0F), 1.0E-7F);
        assertTrue(PROFILE.movementScale(0.5F) < PROFILE.movementScale(0.25F));
        assertEquals(PROFILE.minimumMoveScale(), PROFILE.movementScale(1.0F), 1.0E-7F);
        assertEquals(PROFILE.minimumLookScale(), PROFILE.lookScale(1.0F), 1.0E-7F);
        assertEquals(PROFILE.minimumAnimationScale(), PROFILE.animationScale(1.0F), 1.0E-7F);
    }

    @Test
    void sealedStateRoundTripsWithoutOrdinaryMudData() {
        AssimilationState state = new AssimilationState();
        state.ensurePatternSeed(0x24681357);
        state.setProgress(SinkingMedium.RED_QUICKSAND, 0.64F);
        state.seal(new Vec3(12.5D, 64.0D, -8.25D), "minecraft:overworld",
                37.0F, -12.0F, 4.5F, 0.2F);
        state.rigidVelocity = new Vec3(0.25D, -0.5D, 0.125D);
        state.bodyPitch = 12.0F;
        state.bodyRoll = -7.5F;
        state.revealedCells.set(MudSurfaceLayout.CELL_COUNT - 1);
        state.qteCell = 417;
        state.qteButton = 2;
        state.qteAction = AssimilationQteAction.HOLD;
        state.qteRapidClicks = 2;
        state.qteTraceProgress = 3;
        state.qteTicksRemaining = 31;
        state.qteStreak = 3;
        state.qteCooldownTicks = 7;
        state.qteSequence = 9;

        AssimilationState loaded = new AssimilationState();
        loaded.load(state.save());

        assertEquals(AssimilationStage.SEALED, loaded.stage);
        assertEquals(state.anchor, loaded.anchor);
        assertEquals(state.rigidVelocity, loaded.rigidVelocity);
        assertEquals(state.bodyPitch, loaded.bodyPitch);
        assertEquals(state.bodyRoll, loaded.bodyRoll);
        assertEquals(state.dimension, loaded.dimension);
        assertEquals(state.patternSeed, loaded.patternSeed);
        assertEquals(state.medium, loaded.medium);
        assertEquals(1.0F, loaded.contribution(SinkingMedium.RED_QUICKSAND), 0.0002F);
        assertTrue(loaded.revealedCells.get(MudSurfaceLayout.CELL_COUNT - 1));
        assertEquals(state.qteCell, loaded.qteCell);
        assertEquals(state.qteButton, loaded.qteButton);
        assertEquals(state.qteAction, loaded.qteAction);
        assertEquals(state.qteRapidClicks, loaded.qteRapidClicks);
        assertEquals(0, loaded.qteTraceProgress);
        assertEquals(state.qteTicksRemaining, loaded.qteTicksRemaining);
        assertEquals(state.qteStreak, loaded.qteStreak);
        assertEquals(state.qteCooldownTicks, loaded.qteCooldownTicks);
        assertEquals(state.qteSequence, loaded.qteSequence);
        assertTrue(loaded.frozen());
    }

    @Test
    void coverageGrowsInStableSoftEdgedPatches() {
        float[] first = AssimilationCoveragePattern.buildThresholds(0x12345678);
        float[] second = AssimilationCoveragePattern.buildThresholds(0x12345678);
        float[] anotherSession = AssimilationCoveragePattern.buildThresholds(0x6A09E667);
        assertEquals(MudSurfaceLayout.CELL_COUNT, first.length);
        boolean sessionPatternChanged = false;
        for (int cell = 0; cell < first.length; cell++) {
            assertEquals(first[cell], second[cell], 0.0F);
            sessionPatternChanged |= first[cell] != anotherSession[cell];
            assertEquals(1.0F,
                    AssimilationCoveragePattern.strength(1.0F, first[cell]), 1.0E-6F);
        }
        assertTrue(sessionPatternChanged);

        for (var part : com.fish.mirebound.mud.MudBodyPart.values()) {
            for (var surface : com.fish.mirebound.mud.MudSurface.values()) {
                MudSurfaceLayout.Face face = MudSurfaceLayout.face(part, surface);
                for (int row = 0; row < face.height(); row++) {
                    for (int column = 0; column < face.width(); column++) {
                        int cell = MudSurfaceLayout.cellIndex(part, surface, row, column);
                        if (column + 1 < face.width()) {
                            int right = MudSurfaceLayout.cellIndex(part, surface, row, column + 1);
                            assertTrue(Math.abs(first[cell] - first[right]) < 0.13F);
                        }
                        if (row + 1 < face.height()) {
                            int above = MudSurfaceLayout.cellIndex(part, surface, row + 1, column);
                            assertTrue(Math.abs(first[cell] - first[above]) < 0.13F);
                        }
                    }
                }
            }
        }

        float threshold = first[0];
        assertEquals(0.0F,
                AssimilationCoveragePattern.strength(threshold, threshold), 1.0E-6F);
        assertEquals(0.5F,
                AssimilationCoveragePattern.strength(threshold + 0.08F, threshold), 1.0E-5F);
        assertEquals(1.0F,
                AssimilationCoveragePattern.strength(threshold + 0.16F, threshold), 1.0E-6F);
    }

    @Test
    void seventyPercentAssimilationDoesNotPaintTheWholeBody() {
        float[] thresholds = AssimilationCoveragePattern.buildThresholds(0x7A31C5E9);
        int untouched = 0;
        float totalStrength = 0.0F;
        for (float threshold : thresholds) {
            float strength = AssimilationCoveragePattern.strength(0.70F, threshold);
            totalStrength += strength;
            untouched += strength <= 0.001F ? 1 : 0;
        }
        float average = totalStrength / thresholds.length;
        assertTrue(average > 0.66F && average < 0.78F,
                "visual coverage should track assimilation progress: " + average);
        assertTrue(untouched > thresholds.length / 10,
                "0.7 assimilation must retain visibly clean cells");
    }

    @Test
    void lookControlBecomesHeavyBeforeCompleteAssimilation() {
        assertTrue(PROFILE.lookScale(0.70F) < 0.32F);
        assertTrue(PROFILE.lookScale(0.90F) < 0.14F);
    }

    @Test
    void everyBodyFaceStartsWithScatteredPatchesInsteadOfWaitingItsTurn() {
        float[] thresholds = AssimilationCoveragePattern.buildThresholds(0x41A55A17);
        for (var part : com.fish.mirebound.mud.MudBodyPart.values()) {
            for (var surface : com.fish.mirebound.mud.MudSurface.values()) {
                MudSurfaceLayout.Face face = MudSurfaceLayout.face(part, surface);
                float earliest = 1.0F;
                float latest = 0.0F;
                for (int cell = face.offset(); cell < face.offset() + face.cellCount(); cell++) {
                    earliest = Math.min(earliest, thresholds[cell]);
                    latest = Math.max(latest, thresholds[cell]);
                }
                assertTrue(earliest < 0.16F, part + "/" + surface + " starts too late");
                assertTrue(latest - earliest > 0.12F,
                        part + "/" + surface + " lacks staggered growth");
            }
        }
    }

    @Test
    void resetClearsShellAndLeavesOnlyConfiguredGrace() {
        AssimilationState state = new AssimilationState();
        state.beginAssimilating(0x12345678);
        state.seal(Vec3.ZERO, "minecraft:overworld", 0.0F, 0.0F, 0.0F, 0.0F);
        state.reset(80);

        assertEquals(AssimilationStage.NORMAL, state.stage);
        assertEquals(0.0F, state.progress, 1.0E-7F);
        assertEquals(80, state.rescueGraceTicks);
        assertEquals(-1, state.qteCell);
        assertEquals(0, state.qteStreak);
        assertEquals(0, state.patternSeed);
        assertFalse(state.active());
    }

    @Test
    void assimilationPatternIsStableWithinOneSessionAndReplaceableAfterReset() {
        AssimilationState state = new AssimilationState();
        state.beginAssimilating(0x13572468);
        assertEquals(0x13572468, state.patternSeed);

        state.beginAssimilating(0x24681357);
        assertEquals(0x13572468, state.patternSeed);

        state.reset(0);
        state.beginAssimilating(0x24681357);
        assertEquals(0x24681357, state.patternSeed);
    }

    @Test
    void multipleMediaContributeWithoutReplacingEachOther() {
        AssimilationState state = new AssimilationState();
        state.beginAssimilating(0x714B3A29);
        assertTrue(state.addContribution(SinkingMedium.ASSIMILATION_SLIME, 0.30F));
        assertTrue(state.addContribution(SinkingMedium.RED_QUICKSAND, 0.20F));

        assertEquals(0.50F, state.progress, 1.0E-6F);
        assertEquals(0.30F, state.contribution(SinkingMedium.ASSIMILATION_SLIME), 1.0E-6F);
        assertEquals(0.20F, state.contribution(SinkingMedium.RED_QUICKSAND), 1.0E-6F);
        assertEquals(SinkingMedium.ASSIMILATION_SLIME, state.medium);

        AssimilationState loaded = new AssimilationState();
        loaded.load(state.save());
        assertEquals(state.progress, loaded.progress, 0.0002F);
        assertEquals(state.contribution(SinkingMedium.ASSIMILATION_SLIME),
                loaded.contribution(SinkingMedium.ASSIMILATION_SLIME), 0.0002F);
        assertEquals(state.contribution(SinkingMedium.RED_QUICKSAND),
                loaded.contribution(SinkingMedium.RED_QUICKSAND), 0.0002F);
    }

    @Test
    void sameMediumAdaptiveSourcesSurviveSaveAndProportionalPurge() {
        AssimilationState state = new AssimilationState();
        state.addContribution(SinkingMedium.ASSIMILATION_SLIME, 101L, 0.30F);
        state.addContribution(SinkingMedium.ASSIMILATION_SLIME, 202L, 0.50F);

        AssimilationState loaded = new AssimilationState();
        loaded.load(state.save());
        assertEquals(2, loaded.visualPalette.size());
        assertEquals(0.30F, loaded.visualPalette.weight(
                SinkingMedium.ASSIMILATION_SLIME, 101L), 0.0002F);
        assertEquals(0.50F, loaded.visualPalette.weight(
                SinkingMedium.ASSIMILATION_SLIME, 202L), 0.0002F);

        loaded.removeContributions(0.40F);
        assertEquals(2, loaded.visualPalette.size());
        assertEquals(0.15F, loaded.visualPalette.weight(
                SinkingMedium.ASSIMILATION_SLIME, 101L), 0.0002F);
        assertEquals(0.25F, loaded.visualPalette.weight(
                SinkingMedium.ASSIMILATION_SLIME, 202L), 0.0002F);
    }

    @Test
    void multipleMediaBlendTheirRuntimeBehaviorInsteadOfSwitchingAtDominance() {
        double[] slowValues = MudPhysicsProfiles.defaultValues(SinkingMedium.ASSIMILATION_SLIME);
        slowValues[MudPhysicsParameter.ASSIMILATION_GAIN_PER_TICK.ordinal()] = 0.002D;
        slowValues[MudPhysicsParameter.ASSIMILATION_MINIMUM_MOVE_SCALE.ordinal()] = 0.20D;
        AssimilationProfile slow = AssimilationProfile.fromValues(slowValues);

        double[] fastValues = MudPhysicsProfiles.defaultValues(SinkingMedium.RED_QUICKSAND);
        fastValues[MudPhysicsParameter.ASSIMILATION_ENABLED.ordinal()] = 1.0D;
        fastValues[MudPhysicsParameter.ASSIMILATION_GAIN_PER_TICK.ordinal()] = 0.010D;
        fastValues[MudPhysicsParameter.ASSIMILATION_MINIMUM_MOVE_SCALE.ordinal()] = 0.60D;
        AssimilationProfile fast = AssimilationProfile.fromValues(fastValues);

        AssimilationState state = new AssimilationState();
        state.rememberRuntimeProfile(SinkingMedium.ASSIMILATION_SLIME, BlockPos.ZERO, slow);
        state.rememberRuntimeProfile(SinkingMedium.RED_QUICKSAND, BlockPos.ZERO.above(), fast);
        state.addContribution(SinkingMedium.ASSIMILATION_SLIME, 0.25F);
        state.addContribution(SinkingMedium.RED_QUICKSAND, 0.75F);

        AssimilationProfile mixed = state.runtimeProfile();
        assertEquals(0.008F, mixed.gainPerTick(), 1.0E-6F);
        assertEquals(0.50F, mixed.minimumMoveScale(), 1.0E-6F);
        assertSame(mixed, state.runtimeProfile(), "unchanged mixtures should reuse the cached profile");
    }

    @Test
    void playerBehaviorPrefersPhysicalContactWhenItHasAssimilationEnabled() {
        float[] weights = new float[SinkingMedium.COUNT];
        boolean[] enabled = new boolean[SinkingMedium.COUNT];
        weights[SinkingMedium.RED_QUICKSAND.id()] = 0.15F;
        weights[SinkingMedium.ASSIMILATION_SLIME.id()] = 0.85F;
        enabled[SinkingMedium.RED_QUICKSAND.id()] = true;
        enabled[SinkingMedium.ASSIMILATION_SLIME.id()] = true;

        assertEquals(SinkingMedium.RED_QUICKSAND,
                AssimilationSystem.chooseBehaviorMedium(
                        SinkingMedium.RED_QUICKSAND, weights, enabled,
                        SinkingMedium.MUD));
    }

    @Test
    void playerBehaviorUsesStrongestEnabledContactAndStableTieBreak() {
        float[] weights = new float[SinkingMedium.COUNT];
        boolean[] enabled = new boolean[SinkingMedium.COUNT];
        weights[SinkingMedium.RED_QUICKSAND.id()] = 0.40F;
        weights[SinkingMedium.MUD.id()] = 0.40F;
        weights[SinkingMedium.TAR.id()] = 0.70F;
        enabled[SinkingMedium.RED_QUICKSAND.id()] = true;
        enabled[SinkingMedium.MUD.id()] = true;
        enabled[SinkingMedium.TAR.id()] = true;

        assertEquals(SinkingMedium.TAR,
                AssimilationSystem.chooseBehaviorMedium(null, weights, enabled,
                        SinkingMedium.MUD));
        weights[SinkingMedium.TAR.id()] = 0.40F;
        assertEquals(SinkingMedium.MUD,
                AssimilationSystem.chooseBehaviorMedium(null, weights, enabled,
                        SinkingMedium.MUD));
    }

    @Test
    void playerBehaviorFallsBackWhenNoEnabledContactExists() {
        float[] weights = new float[SinkingMedium.COUNT];
        boolean[] enabled = new boolean[SinkingMedium.COUNT];
        weights[SinkingMedium.RED_QUICKSAND.id()] = 1.0F;

        assertEquals(SinkingMedium.TAR,
                AssimilationSystem.chooseBehaviorMedium(null, weights, enabled,
                        SinkingMedium.TAR));
    }

    @Test
    void localRuntimeTemplateSnapshotSurvivesSaveAndReload() {
        double[] values = MudPhysicsProfiles.defaultValues(SinkingMedium.RED_QUICKSAND);
        values[MudPhysicsParameter.ASSIMILATION_ENABLED.ordinal()] = 1.0D;
        values[MudPhysicsParameter.ASSIMILATION_GAIN_PER_TICK.ordinal()] = 0.013D;
        values[MudPhysicsParameter.ASSIMILATION_PARTIAL_PURGE_SPLASH_DROPLETS.ordinal()] = 11.0D;
        AssimilationProfile localProfile = AssimilationProfile.fromValues(values);

        AssimilationState state = new AssimilationState();
        state.beginAssimilating(0x62394A11);
        state.rememberRuntimeProfile(
                SinkingMedium.RED_QUICKSAND, new BlockPos(17, 63, -9), localProfile);
        state.addContribution(SinkingMedium.RED_QUICKSAND, 0.35F);

        AssimilationState loaded = new AssimilationState();
        loaded.load(state.save());

        assertEquals(localProfile, loaded.runtimeProfile());
        assertEquals(new BlockPos(17, 63, -9),
                loaded.runtimeProfilePositions[SinkingMedium.RED_QUICKSAND.id()]);
    }

    @Test
    void legacySingleMediumSaveMigratesIntoContributionPalette() {
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("Version", 10);
        legacy.putInt("Stage", AssimilationStage.ASSIMILATING.ordinal());
        legacy.putFloat("Progress", 0.42F);
        legacy.putInt("Medium", SinkingMedium.RED_QUICKSAND.id());
        legacy.putInt("QteCell", -1);

        AssimilationState loaded = new AssimilationState();
        loaded.load(legacy);

        assertEquals(0.42F, loaded.progress, 1.0E-6F);
        assertEquals(0.42F, loaded.contribution(SinkingMedium.RED_QUICKSAND), 1.0E-6F);
        assertEquals(SinkingMedium.RED_QUICKSAND, loaded.medium);
        assertTrue(loaded.active());
    }

    @Test
    void oversizedPersistenceArraysUseBoundedFallbacks() {
        CompoundTag malformed = new CompoundTag();
        malformed.putInt("Stage", AssimilationStage.ASSIMILATING.ordinal());
        malformed.putFloat("Progress", 0.42F);
        malformed.putInt("Medium", SinkingMedium.RED_QUICKSAND.id());
        malformed.putIntArray("Contributions", new int[SinkingMedium.COUNT + 1]);
        malformed.putIntArray("VisualPalette", new int[MudVisualPalette.MAX_ENTRIES + 1]);
        malformed.putLongArray("VisualSources", new long[MudVisualPalette.MAX_ENTRIES + 1]);
        int revealBytes = (MudSurfaceLayout.CELL_COUNT + 7) / 8;
        malformed.putByteArray("RevealedCells", new byte[revealBytes + 1]);
        malformed.putByteArray("SelfRescueOpenedCells", new byte[revealBytes + 1]);

        AssimilationState loaded = new AssimilationState();
        loaded.load(malformed);

        assertEquals(0.42F, loaded.progress, 1.0E-6F);
        assertEquals(0.42F, loaded.contribution(SinkingMedium.RED_QUICKSAND), 1.0E-6F);
        assertEquals(1, loaded.visualPalette.size());
        assertEquals(SinkingMedium.RED_QUICKSAND, loaded.visualPalette.mediumAt(0));
        assertTrue(loaded.revealedCells.isEmpty());
        assertTrue(loaded.selfRescueOpenedCells.isEmpty());
    }

    @Test
    void oversizedRuntimeProfileValuesAndEntriesAreIgnored() {
        CompoundTag malformed = new CompoundTag();
        malformed.putInt("Stage", AssimilationStage.ASSIMILATING.ordinal());
        malformed.putInt("Medium", SinkingMedium.ASSIMILATION_SLIME.id());
        malformed.putIntArray("Contributions", new int[] {4200});
        ListTag profiles = new ListTag();
        CompoundTag oversizedValues = new CompoundTag();
        oversizedValues.putInt("Medium", SinkingMedium.ASSIMILATION_SLIME.id());
        oversizedValues.putLongArray("Values", new long[1024]);
        profiles.add(oversizedValues);
        for (int index = 1; index < SinkingMedium.COUNT; index++) {
            CompoundTag invalidMedium = new CompoundTag();
            invalidMedium.putInt("Medium", -1);
            invalidMedium.putLongArray("Values", new long[] {0L});
            profiles.add(invalidMedium);
        }
        CompoundTag beyondEntryLimit = new CompoundTag();
        beyondEntryLimit.putInt("Medium", SinkingMedium.ASSIMILATION_SLIME.id());
        beyondEntryLimit.putLongArray("Values", new long[] {0L});
        profiles.add(beyondEntryLimit);
        malformed.put("RuntimeProfiles", profiles);

        AssimilationState loaded = new AssimilationState();
        loaded.load(malformed);

        assertEquals(null, loaded.runtimeProfiles[SinkingMedium.ASSIMILATION_SLIME.id()]);
        assertEquals(0.42F, loaded.progress, 1.0E-6F);
    }

    @Test
    void mixedPixelOwnershipIsStableAndWeighted() {
        float[] contributions = new float[SinkingMedium.COUNT];
        contributions[SinkingMedium.ASSIMILATION_SLIME.id()] = 0.75F;
        contributions[SinkingMedium.RED_QUICKSAND.id()] = 0.25F;
        int slime = 0;
        int sand = 0;
        for (int cell = 0; cell < MudSurfaceLayout.CELL_COUNT; cell++) {
            SinkingMedium first = AssimilationContributions.mediumForCell(
                    0x41A55A17, cell, contributions, SinkingMedium.ASSIMILATION_SLIME);
            SinkingMedium second = AssimilationContributions.mediumForCell(
                    0x41A55A17, cell, contributions, SinkingMedium.ASSIMILATION_SLIME);
            assertEquals(first, second);
            slime += first == SinkingMedium.ASSIMILATION_SLIME ? 1 : 0;
            sand += first == SinkingMedium.RED_QUICKSAND ? 1 : 0;
        }
        assertEquals(MudSurfaceLayout.CELL_COUNT, slime + sand);
        assertTrue(slime > sand * 2);
        assertTrue(slime < sand * 4);
    }

    @Test
    void rescueHitUsesCanonicalModelFaceDirections() {
        assertEquals(MudSurface.FRONT, AssimilationSystem.hitSurface(0.5D, 0.1D, 1.0D));
        assertEquals(MudSurface.BACK, AssimilationSystem.hitSurface(0.5D, 0.1D, -1.0D));
        assertEquals(MudSurface.LEFT, AssimilationSystem.hitSurface(0.5D, 1.0D, 0.1D));
        assertEquals(MudSurface.RIGHT, AssimilationSystem.hitSurface(0.5D, -1.0D, 0.1D));
        assertEquals(MudSurface.TOP, AssimilationSystem.hitSurface(0.99D, 0.0D, 0.0D));
        assertEquals(MudSurface.BOTTOM, AssimilationSystem.hitSurface(0.01D, 0.0D, 0.0D));
    }

    @Test
    void failedSelfRescueClosesOnlySelfOpenedCracks() {
        AssimilationState state = new AssimilationState();
        state.seal(Vec3.ZERO, "minecraft:overworld", 0.0F, 0.0F, 0.0F, 0.0F);
        BitSet first = new BitSet(MudSurfaceLayout.CELL_COUNT);
        first.set(100);
        first.set(101);
        state.applySelfRescueSuccess(first, PROFILE);
        float damagedIntegrity = state.shellIntegrity;

        BitSet externallyClaimed = new BitSet(MudSurfaceLayout.CELL_COUNT);
        externallyClaimed.set(101);
        state.makeCrackExternal(externallyClaimed);
        state.rollbackSelfRescue(PROFILE);

        assertFalse(state.revealedCells.get(100));
        assertTrue(state.revealedCells.get(101));
        assertEquals(1.0F, state.shellIntegrity, 1.0E-6F);
        assertTrue(damagedIntegrity < state.shellIntegrity);
        assertTrue(state.selfRescueOpenedCells.isEmpty());
    }
}

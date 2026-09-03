package com.fish.mirebound.entitycoverage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fish.mirebound.adaptive.MudVisualSource;
import com.fish.mirebound.mud.SinkingMedium;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EntityMudCoverageStateTest {
    @Test
    void accumulationCanCoverEveryModelPixel() {
        EntityMudCoverageState state = new EntityMudCoverageState(17);

        for (int index = 0; index < 40; index++) {
            state.add(SinkingMedium.MUD, MudVisualSource.NONE, 0.05F, false);
        }

        assertEquals(EntityMudCoverageState.MAXIMUM_COVERAGE,
                state.totalCoverage(), 0.0001F);
    }

    @Test
    void twoMediaRemainIndependentAndSurvivePersistence() {
        EntityMudCoverageState state = new EntityMudCoverageState(91);
        state.add(SinkingMedium.MUD, MudVisualSource.NONE, 0.25F, false);
        state.add(SinkingMedium.SOFT_QUICKSAND, 42L, 0.20F, false);

        EntityMudCoverageState restored = EntityMudCoverageState.load(
                state.save(), 0);
        EntityMudCoverageState.Snapshot snapshot = restored.snapshot();

        assertEquals(SinkingMedium.MUD, snapshot.primaryMedium());
        assertEquals(0.25F, snapshot.primaryStrength(), 0.0001F);
        assertEquals(SinkingMedium.SOFT_QUICKSAND, snapshot.secondaryMedium());
        assertEquals(42L, snapshot.secondaryVisualSource());
        assertEquals(0.20F, snapshot.secondaryStrength(), 0.0001F);
        assertEquals(91, snapshot.patternSeed());
    }

    @Test
    void washingIsMonotonicAndClearsBothMedia() {
        EntityMudCoverageState state = new EntityMudCoverageState(1);
        state.add(SinkingMedium.MUD, 0L, 0.20F, false);
        state.add(SinkingMedium.TAR, 0L, 0.10F, false);

        assertTrue(state.wash(0.12F));
        assertEquals(0.18F, state.totalCoverage(), 0.0001F);
        assertTrue(state.wash(1.0F));
        assertEquals(0.0F, state.totalCoverage(), 0.0001F);
    }

    @Test
    void automaticFadeIsLinearAndClearsAtConfiguredDuration() {
        EntityMudCoverageState state = new EntityMudCoverageState(3);
        state.add(SinkingMedium.MUD, 0L, 0.40F, false);
        state.addSpot(0.0F, 0.5F, -1.0F,
                0.2F, 0.80F, SinkingMedium.MUD, 0L, true);
        state.refreshAutomaticFade(100L);

        assertTrue(state.advanceAutomaticFade(150L, 100));
        assertEquals(0.40F, state.totalCoverage(), 0.0001F);
        assertEquals(0.50F, state.snapshot().automaticFadeScale(), 0.0001F);
        assertEquals(0.80F,
                state.snapshot().spots().getFirst().strength(), 0.0001F);

        assertTrue(state.advanceAutomaticFade(200L, 100));
        assertFalse(state.dirty());
    }

    @Test
    void newContactRestartsAutomaticFadeFromCurrentCoverage() {
        EntityMudCoverageState state = new EntityMudCoverageState(4);
        state.add(SinkingMedium.MUD, 0L, 0.80F, false);
        state.refreshAutomaticFade(100L);
        state.advanceAutomaticFade(150L, 100);
        state.refreshAutomaticFade(150L);

        state.advanceAutomaticFade(200L, 100);

        assertEquals(0.40F, state.totalCoverage(), 0.0001F);
        assertEquals(0.50F, state.snapshot().automaticFadeScale(), 0.0001F);
    }

    @Test
    void maximumConfiguredDurationStillBeginsFadingImmediately() {
        EntityMudCoverageState state = new EntityMudCoverageState(6);
        state.add(SinkingMedium.MUD, 0L, 1.0F, false);
        state.refreshAutomaticFade(100L);

        assertTrue(state.advanceAutomaticFade(110L, 3600 * 20));
        assertTrue(state.snapshot().automaticFadeScale() < 1.0F);
    }

    @Test
    void automaticFadeDoesNotResendUnchangedSpotGeometry() {
        EntityMudCoverageState state = new EntityMudCoverageState(7);
        state.add(SinkingMedium.MUD, 0L, 0.40F, false);
        state.addSpot(0.0F, 0.5F, -1.0F,
                0.2F, 0.80F, SinkingMedium.MUD, 0L, true);
        state.markBroadcast(state.synchronizationSignature());
        state.refreshAutomaticFade(100L);

        state.advanceAutomaticFade(110L, 600);
        EntityMudCoverageSyncTracker.Delta delta =
                state.synchronizationDelta();

        assertTrue(delta.changed().isEmpty());
        assertTrue(delta.removedIds().isEmpty());
    }

    @Test
    void mudClodCanReplacePartOfFullyCoveredSingleMedium() {
        EntityMudCoverageState state = new EntityMudCoverageState(5);
        state.add(SinkingMedium.MUD, 0L,
                EntityMudCoverageState.MAXIMUM_COVERAGE, false);

        assertTrue(state.add(SinkingMedium.TAR, 0L, 0.10F, true));

        EntityMudCoverageState.Snapshot snapshot = state.snapshot();
        assertEquals(EntityMudCoverageState.MAXIMUM_COVERAGE,
                state.totalCoverage(), 0.0001F);
        assertEquals(SinkingMedium.TAR, snapshot.secondaryMedium());
        assertEquals(0.10F, snapshot.secondaryStrength(), 0.0001F);
    }

    @Test
    void localizedSpotsMergeStayBoundedAndSurvivePersistence() {
        EntityMudCoverageState state = new EntityMudCoverageState(27);
        state.add(SinkingMedium.MUD, 41L, 0.25F, false);
        state.addSpot(0.10F, 0.80F, -0.75F,
                0.16F, 0.72F, SinkingMedium.MUD, 41L, true);
        state.addSpot(0.12F, 0.79F, -0.74F,
                0.14F, 0.55F, SinkingMedium.MUD, 41L, true);
        for (int index = 0; index < 24; index++) {
            state.addSpot(-0.95F + index * 0.08F,
                    (index % 6) / 5.0F, (index & 1) == 0 ? -0.9F : 0.9F,
                    0.05F, 0.45F, SinkingMedium.SOFT_QUICKSAND,
                    index, true);
        }

        EntityMudCoverageState restored = EntityMudCoverageState.load(
                state.save(), 0);
        EntityMudCoverageState.Snapshot snapshot = restored.snapshot();

        assertTrue(snapshot.spots().size() <= EntityMudCoverageState.MAXIMUM_SPOTS);
        assertFalse(snapshot.spots().isEmpty());
        assertEquals(27, snapshot.patternSeed());
        assertEquals(state.synchronizationSignature(),
                restored.synchronizationSignature());
    }

    @Test
    void washingAlsoFadesLocalizedSpotsMonotonically() {
        EntityMudCoverageState state = new EntityMudCoverageState(9);
        state.add(SinkingMedium.MUD, 0L, 0.40F, false);
        state.addSpot(0.0F, 0.5F, -1.0F,
                0.2F, 0.8F, SinkingMedium.MUD, 0L, true);
        EntityMudCoverageSpot before = state.snapshot().spots().getFirst();

        state.wash(0.10F);

        EntityMudCoverageSpot after = state.snapshot().spots().getFirst();
        assertEquals(before.id(), after.id());
        assertEquals(before.localX(), after.localX());
        assertEquals(before.localY(), after.localY());
        assertEquals(before.localZ(), after.localZ());
        assertEquals(before.radius(), after.radius());
        assertTrue(after.strength() < before.strength());
    }

    @Test
    void visibleWashingKeepsItsStrengthDuringAutomaticFade() {
        EntityMudCoverageState state = new EntityMudCoverageState(10);
        state.add(SinkingMedium.MUD, 0L, 1.0F, false);
        state.advanceAutomaticFade(100L, 20);
        state.advanceAutomaticFade(110L, 20);
        EntityMudCoverageState.Snapshot before = state.snapshot();
        float beforeVisible = before.primaryStrength()
                * before.automaticFadeScale();

        state.washVisible(0.10F);

        EntityMudCoverageState.Snapshot after = state.snapshot();
        float afterVisible = after.primaryStrength()
                * after.automaticFadeScale();
        assertEquals(beforeVisible - 0.10F, afterVisible, 0.0001F);
    }

    @Test
    void repeatedContactStrengthensWithoutMovingExistingSpot() {
        EntityMudCoverageState state = new EntityMudCoverageState(31);
        state.addSpot(0.10F, 0.70F, -0.30F,
                0.18F, 0.45F, SinkingMedium.MUD, 4L, false);
        EntityMudCoverageSpot before = state.snapshot().spots().getFirst();

        state.addSpot(0.105F, 0.696F, -0.296F,
                0.24F, 0.55F, SinkingMedium.MUD, 4L, false);

        EntityMudCoverageSpot after = state.snapshot().spots().getFirst();
        assertEquals(1, state.snapshot().spots().size());
        assertEquals(before.id(), after.id());
        assertEquals(before.localX(), after.localX());
        assertEquals(before.localY(), after.localY());
        assertEquals(before.localZ(), after.localZ());
        assertEquals(before.radius(), after.radius());
        assertTrue(after.strength() > before.strength());
    }

    @Test
    void nearbyDistinctHitsRemainSeparateAndCanFillTheirOwnPixels() {
        EntityMudCoverageState state = new EntityMudCoverageState(32);
        state.addSpot(0.0F, 0.50F, -1.0F,
                0.18F, 0.55F, SinkingMedium.MUD, 4L, true);

        assertTrue(state.addSpot(0.07F, 0.50F, -1.0F,
                0.18F, 0.55F, SinkingMedium.MUD, 4L, true));

        assertEquals(2, state.snapshot().spots().size());
    }

    @Test
    void verticalContactExpandsOneContinuousVolumeWithoutLosingIdentity() {
        EntityMudCoverageState state = new EntityMudCoverageState(33);
        assertTrue(state.addVerticalVolume(
                0.18F, true, 0.55F, SinkingMedium.MUD, 4L));
        EntityMudCoverageSpot shallow = state.snapshot().spots().getFirst();

        assertTrue(state.addVerticalVolume(
                0.46F, true, 0.55F, SinkingMedium.MUD, 4L));

        EntityMudCoverageSpot deep = state.snapshot().spots().getFirst();
        assertEquals(1, state.snapshot().spots().size());
        assertEquals(shallow.id(), deep.id());
        assertEquals(EntityMudCoverageSpot.Shape.LOWER_VOLUME, deep.shape());
        assertEquals(0.46F, deep.localY(), 0.0001F);
    }

    @Test
    void visualSpotsPreserveMoreThanTwoMediaThroughWashAndPersistence() {
        EntityMudCoverageState state = new EntityMudCoverageState(34);
        state.addSpot(-0.5F, 0.2F, -1.0F,
                0.12F, 0.70F, SinkingMedium.MUD, 1L, true);
        state.addSpot(0.0F, 0.4F, -1.0F,
                0.12F, 0.65F, SinkingMedium.TAR, 2L, true);
        state.addSpot(0.5F, 0.6F, -1.0F,
                0.12F, 0.60F, SinkingMedium.SOFT_QUICKSAND, 3L, true);

        assertTrue(state.dirty());
        assertTrue(state.wash(0.05F));
        EntityMudCoverageState restored = EntityMudCoverageState.load(
                state.save(), 0);

        assertEquals(Set.of(
                SinkingMedium.MUD,
                SinkingMedium.TAR,
                SinkingMedium.SOFT_QUICKSAND),
                restored.snapshot().spots().stream()
                        .map(EntityMudCoverageSpot::medium)
                        .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void verticalVolumesPreserveThirdMediumInSparseSynchronization() {
        EntityMudCoverageState state = new EntityMudCoverageState(37);
        state.addVerticalVolume(
                0.20F, true, 0.60F, SinkingMedium.MUD, 1L);
        state.addVerticalVolume(
                0.35F, true, 0.60F, SinkingMedium.TAR, 2L);
        state.markBroadcast(state.synchronizationSignature());

        assertTrue(state.addVerticalVolume(
                0.28F, true, 0.60F, SinkingMedium.SOFT_QUICKSAND, 3L));
        EntityMudCoverageSyncTracker.Delta delta =
                state.synchronizationDelta();

        assertEquals(1, delta.changed().size());
        assertEquals(SinkingMedium.SOFT_QUICKSAND,
                delta.changed().getFirst().medium());
        assertEquals(Set.of(
                SinkingMedium.MUD,
                SinkingMedium.TAR,
                SinkingMedium.SOFT_QUICKSAND),
                state.snapshot().spots().stream()
                        .map(EntityMudCoverageSpot::medium)
                        .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void localizedContactVolumesKeepMediaOnIndependentBodyRegions() {
        EntityMudCoverageState state = new EntityMudCoverageState(38);

        assertTrue(state.addContactVolume(
                -0.72F, 0.55F, 0.0F, 0.34F, true,
                0.70F, SinkingMedium.MUD, 1L));
        assertTrue(state.addContactVolume(
                0.72F, 0.55F, 0.0F, 0.34F, true,
                0.70F, SinkingMedium.TAR, 2L));

        EntityMudCoverageState.Snapshot snapshot = state.snapshot();
        assertEquals(2, snapshot.spots().size());
        assertEquals(Set.of(SinkingMedium.MUD, SinkingMedium.TAR),
                snapshot.spots().stream()
                        .map(EntityMudCoverageSpot::medium)
                        .collect(java.util.stream.Collectors.toSet()));
        assertTrue(snapshot.spots().stream().allMatch(spot ->
                spot.shape() == EntityMudCoverageSpot.Shape.LOWER_CONTACT_VOLUME));
        assertTrue(snapshot.spots().get(0).localX()
                < snapshot.spots().get(1).localX());
    }

    @Test
    void contactThrottleSeparatesBlocksOfTheSameNativeMedium() {
        EntityMudCoverageState state = new EntityMudCoverageState(39);

        assertTrue(state.contactUpdateDue(
                SinkingMedium.MUD, 0L, 41L, 100L, 5));
        assertFalse(state.contactUpdateDue(
                SinkingMedium.MUD, 0L, 41L, 100L, 5));
        assertTrue(state.contactUpdateDue(
                SinkingMedium.MUD, 0L, 42L, 100L, 5));
    }

    @Test
    void localizedContactMigratesLegacyDirectionlessVolumes() {
        EntityMudCoverageState state = new EntityMudCoverageState(40);
        state.addVerticalVolume(
                0.65F, true, 0.80F, SinkingMedium.MUD, 0L);

        assertTrue(state.addContactVolume(
                0.35F, 0.65F, -0.20F, 0.32F, true,
                0.80F, SinkingMedium.MUD, 0L));

        assertEquals(1, state.snapshot().spots().size());
        EntityMudCoverageSpot spot = state.snapshot().spots().getFirst();
        assertEquals(EntityMudCoverageSpot.Shape.LOWER_CONTACT_VOLUME,
                spot.shape());
        assertEquals(0.35F, spot.localX(), 0.0001F);
        assertEquals(-0.20F, spot.localZ(), 0.0001F);
    }

    @Test
    void preLocalizedTemporaryCoverageDoesNotSurviveUpgrade() {
        net.minecraft.nbt.CompoundTag legacy = new net.minecraft.nbt.CompoundTag();
        legacy.putInt("Seed", 45);
        net.minecraft.nbt.CompoundTag primary = new net.minecraft.nbt.CompoundTag();
        primary.putInt("Medium", SinkingMedium.MUD.id());
        primary.putFloat("Strength", 0.75F);
        legacy.put("Primary", primary);

        EntityMudCoverageState loaded = EntityMudCoverageState.load(legacy, 0);

        assertFalse(loaded.dirty());
        assertTrue(loaded.snapshot().spots().isEmpty());
        assertTrue(loaded.persistencePending());
    }

    @Test
    void contactThrottleIsIndependentForEachMediumAndVisualSource() {
        EntityMudCoverageState state = new EntityMudCoverageState(35);

        assertTrue(state.contactUpdateDue(
                SinkingMedium.MUD, 1L, 100L, 5));
        assertFalse(state.contactUpdateDue(
                SinkingMedium.MUD, 1L, 100L, 5));
        assertTrue(state.contactUpdateDue(
                SinkingMedium.TAR, 1L, 100L, 5));
        assertTrue(state.contactUpdateDue(
                SinkingMedium.MUD, 2L, 100L, 5));
        assertTrue(state.contactUpdateDue(
                SinkingMedium.MUD, 1L, 105L, 5));
    }

    @Test
    void synchronizationDeltaContainsOnlyChangedAndRemovedSpots() {
        EntityMudCoverageState state = new EntityMudCoverageState(36);
        state.addSpot(-0.4F, 0.3F, -1.0F,
                0.12F, 0.40F, SinkingMedium.MUD, 1L, true);
        state.addSpot(0.4F, 0.7F, -1.0F,
                0.12F, 0.45F, SinkingMedium.TAR, 2L, true);
        state.markBroadcast(state.synchronizationSignature());
        int unchangedId = state.snapshot().spots().get(1).id();

        state.addSpot(-0.4F, 0.3F, -1.0F,
                0.12F, 0.70F, SinkingMedium.MUD, 1L, true);
        EntityMudCoverageSyncTracker.Delta changed =
                state.synchronizationDelta();

        assertEquals(1, changed.changed().size());
        assertFalse(changed.changed().stream()
                .anyMatch(spot -> spot.id() == unchangedId));
        assertTrue(changed.removedIds().isEmpty());

        state.wash(1.0F);
        EntityMudCoverageSyncTracker.Delta removed =
                state.synchronizationDelta();
        assertTrue(removed.changed().isEmpty());
        assertEquals(2, removed.removedIds().size());
    }

    @Test
    void fullSpotBudgetNeverEvictsUnrelatedStains() {
        EntityMudCoverageState state = new EntityMudCoverageState(44);
        for (int index = 0; index < EntityMudCoverageState.MAXIMUM_SPOTS; index++) {
            assertTrue(state.addSpot(-1.0F, 0.0F, -1.0F,
                    0.01F, 0.50F, SinkingMedium.MUD, index, false));
        }
        var beforeIds = state.snapshot().spots().stream()
                .map(EntityMudCoverageSpot::id).toList();

        assertFalse(state.addSpot(1.0F, 1.0F, 1.0F,
                0.01F, 1.0F, SinkingMedium.TAR, 999L, true));

        assertEquals(beforeIds, state.snapshot().spots().stream()
                .map(EntityMudCoverageSpot::id).toList());
    }
}

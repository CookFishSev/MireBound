package com.fish.mirebound.mud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TenderFleshMechanicsTest {
    private static final double EPSILON = 1.0E-9D;

    @Test
    void contractionIsPeriodicAndDeterministic() {
        TenderFleshProfile profile = TenderFleshProfile.DEFAULT;
        assertEquals(TenderFleshMechanics.contraction(profile, 0),
                TenderFleshMechanics.contraction(profile, profile.pulsePeriodTicks()), EPSILON);
        assertTrue(TenderFleshMechanics.contraction(profile, profile.pulsePeriodTicks() / 2)
                > TenderFleshMechanics.contraction(profile, 0));
    }

    @Test
    void activityBuildsWrapWhileStillnessReleasesIt() {
        TenderFleshRuntimeState state = new TenderFleshRuntimeState();
        for (int tick = 0; tick < 30; tick++) {
            TenderFleshMechanics.step(TenderFleshProfile.DEFAULT, state,
                    input(tick, 0.62D, 0.20D, false, false, -0.01D, 0.45D));
        }
        double wrapped = state.wrap;
        assertTrue(wrapped > 0.10D);
        for (int tick = 30; tick < 90; tick++) {
            TenderFleshMechanics.step(TenderFleshProfile.DEFAULT, state,
                    input(tick, 0.62D, 0.0D, false, false, -0.01D, 0.45D));
        }
        assertTrue(state.wrap < wrapped);
    }

    @Test
    void movementBuildsPressureAndStillnessReleasesIt() {
        TenderFleshRuntimeState state = new TenderFleshRuntimeState();
        for (int tick = 0; tick < 40; tick++) {
            TenderFleshMechanics.step(TenderFleshProfile.DEFAULT, state,
                    input(tick, 0.62D, 0.62D, false, false, -0.01D, 0.45D));
        }
        double loaded = state.pressure;
        assertTrue(loaded > 0.45D);
        assertTrue(state.calmness < 0.60D);
        for (int tick = 40; tick < 120; tick++) {
            TenderFleshMechanics.step(TenderFleshProfile.DEFAULT, state,
                    input(tick, 0.62D, 0.0D, false, false, -0.01D, 0.45D));
        }
        assertTrue(state.pressure < loaded * 0.35D);
        assertTrue(state.calmness > 0.60D);
    }

    @Test
    void contractionAddsOnlyDownwardMotion() {
        TenderFleshRuntimeState state = new TenderFleshRuntimeState();
        int peak = TenderFleshProfile.DEFAULT.pulsePeriodTicks() / 2;
        TenderFleshMechanics.StepResult falling = TenderFleshMechanics.step(
                TenderFleshProfile.DEFAULT, state,
                input(peak, 0.70D, 0.25D, true, false, -0.006D, 0.45D));
        assertTrue(falling.motionY() < -0.006D);

        TenderFleshMechanics.StepResult rising = TenderFleshMechanics.step(
                TenderFleshProfile.DEFAULT, state,
                input(peak, 0.70D, 0.25D, true, false, 0.035D, 0.45D));
        assertEquals(0.035D, rising.motionY(), EPSILON);
    }

    @Test
    void relaxedReleaseEscapesBetterThanContractedRelease() {
        TenderFleshMechanics.StepResult relaxed = TenderFleshMechanics.step(
                TenderFleshProfile.DEFAULT, new TenderFleshRuntimeState(),
                input(0, 0.55D, 0.30D, false, true, 0.08D, 0.50D));
        TenderFleshMechanics.StepResult contracted = TenderFleshMechanics.step(
                TenderFleshProfile.DEFAULT, new TenderFleshRuntimeState(),
                input(TenderFleshProfile.DEFAULT.pulsePeriodTicks() / 2,
                        0.55D, 0.30D, false, true, 0.08D, 0.50D));
        assertTrue(relaxed.motionY() > contracted.motionY());
    }

    @Test
    void wrappingReducesTheOrdinaryMovementResult() {
        TenderFleshRuntimeState state = new TenderFleshRuntimeState();
        state.wrap = 0.90D;
        TenderFleshMechanics.StepResult result = TenderFleshMechanics.step(
                TenderFleshProfile.DEFAULT, state,
                input(TenderFleshProfile.DEFAULT.pulsePeriodTicks() / 2,
                        0.75D, 0.20D, true, false, -0.01D, 0.60D));
        assertTrue(result.walkScale() < 0.60D);
        assertTrue(Math.abs(result.motionX()) < 0.12D);
    }

    @Test
    void relaxedReleaseLoosensWrapping() {
        TenderFleshRuntimeState state = new TenderFleshRuntimeState();
        state.wrap = 0.80D;
        TenderFleshMechanics.StepResult result = TenderFleshMechanics.step(
                TenderFleshProfile.DEFAULT, state,
                input(0, 0.65D, 0.0D, false, true, 0.08D, 0.50D));
        assertEquals(TenderFleshMechanics.ReleaseOutcome.EFFECTIVE, result.releaseOutcome());
        assertTrue(result.wrap() < 0.70D);
    }

    @Test
    void contractedReleaseIsAbsorbedAndTightensWrapping() {
        TenderFleshRuntimeState state = new TenderFleshRuntimeState();
        state.wrap = 0.20D;
        TenderFleshMechanics.StepResult result = TenderFleshMechanics.step(
                TenderFleshProfile.DEFAULT, state,
                input(TenderFleshProfile.DEFAULT.pulsePeriodTicks() / 2,
                        0.65D, 0.0D, false, true, 0.08D, 0.50D));
        assertEquals(TenderFleshMechanics.ReleaseOutcome.ABSORBED, result.releaseOutcome());
        assertTrue(result.wrap() > 0.20D);
    }

    @Test
    void effectiveReleaseCreatesOnlyAShortEscapePulse() {
        TenderFleshRuntimeState state = new TenderFleshRuntimeState();
        TenderFleshMechanics.StepResult release = TenderFleshMechanics.step(
                TenderFleshProfile.DEFAULT, state,
                input(0, 0.75D, 0.0D, false, true, 0.0D, 0.50D));
        assertTrue(release.motionY() >= TenderFleshProfile.DEFAULT.escapePulseSpeed() * 0.85D);
        double first = release.motionY();
        TenderFleshMechanics.StepResult next = TenderFleshMechanics.step(
                TenderFleshProfile.DEFAULT, state,
                input(1, 0.75D, 0.0D, false, false, 0.0D, 0.50D));
        assertTrue(next.motionY() < first);
        for (int tick = 2; tick < 12; tick++) {
            TenderFleshMechanics.step(TenderFleshProfile.DEFAULT, state,
                    input(tick, 0.75D, 0.0D, false, false, 0.0D, 0.50D));
        }
        assertEquals(0, state.escapeTicks);
    }

    @Test
    void completedEnclosureStaysLockedUntilPillarsAreBroken() {
        TenderFleshProfile profile = TenderFleshProfile.DEFAULT;
        TenderFleshRuntimeState state = new TenderFleshRuntimeState();
        TenderFleshMechanics.StepResult result = null;
        for (int tick = 0; tick < 260; tick++) {
            result = TenderFleshMechanics.step(profile, state,
                    enclosureInput(tick, true));
        }

        assertTrue(state.enclosureActive);
        assertTrue(state.enclosureProgress > 0.98D);
        assertTrue(result != null
                && result.motionX() <= profile.enclosureClosedRadius() + 0.01D);

        TenderFleshMechanics.StepResult released = TenderFleshMechanics.step(
                profile, state, enclosureInput(261, false));
        assertTrue(state.enclosureActive);
        assertEquals(0.0D, released.motionX(), EPSILON);
        assertEquals(0.0D, released.motionZ(), EPSILON);
    }

    @Test
    void enclosureKeepsItsActivationAnchorAndSharedCenterline() {
        TenderFleshRuntimeState state = new TenderFleshRuntimeState();
        TenderFleshMechanics.step(TenderFleshProfile.DEFAULT, state,
                enclosureInput(0, true, 10.25D, 64.0D, -3.75D));

        assertTrue(state.enclosureActive);
        assertEquals(10.25D, state.enclosureCenterX, EPSILON);
        assertEquals(64.0D, state.enclosureCenterY, EPSILON);
        assertEquals(-3.75D, state.enclosureCenterZ, EPSILON);
        assertEquals(state.enclosureCenterX, state.enclosurePlayerX, EPSILON);
        assertEquals(state.enclosureCenterZ, state.enclosurePlayerZ, EPSILON);

        TenderFleshMechanics.step(TenderFleshProfile.DEFAULT, state,
                enclosureInput(1, true, 12.0D, 68.0D, -1.0D));
        assertEquals(10.25D, state.enclosureCenterX, EPSILON);
        assertEquals(64.0D, state.enclosureCenterY, EPSILON);
        assertEquals(-3.75D, state.enclosureCenterZ, EPSILON);
    }

    @Test
    void allFourDistinctPillarsMustBeHitBeforeRetreat() {
        TenderFleshProfile profile = TenderFleshProfile.DEFAULT;
        TenderFleshRuntimeState state = new TenderFleshRuntimeState();
        state.enclosureActive = true;
        state.enclosureProgress = 1.0D;
        TenderFleshMechanics.initializePillarDurability(profile, state, 0x12345678L);

        int firstRequired = TenderFleshMechanics.pillarPackedValue(
                state.enclosurePillarRequiredHitsPacked, 0);
        boolean hasDifferentDurability = false;
        for (int pillar = 0; pillar < 4; pillar++) {
            int required = TenderFleshMechanics.pillarPackedValue(
                    state.enclosurePillarRequiredHitsPacked, pillar);
            assertTrue(required >= 3 && required <= 6);
            hasDifferentDurability |= required != firstRequired;
            for (int hit = 1; hit <= required; hit++) {
                assertTrue(TenderFleshMechanics.strikePillar(profile, state, pillar));
                assertEquals(hit, TenderFleshMechanics.pillarPackedValue(
                        state.enclosurePillarDamagePacked, pillar));
                assertEquals(hit == required,
                        (state.enclosureBrokenMask & (1 << pillar)) != 0);
                state.enclosureStrikeCooldownTicks = 0;
            }
            if (pillar == 0) {
                assertFalse(TenderFleshMechanics.strikePillar(profile, state, pillar));
            }
        }

        assertTrue(hasDifferentDurability);
        assertEquals(0x0F, state.enclosureBrokenMask);
        assertTrue(state.enclosureRetreating);
        assertFalse(state.enclosureActive);
    }

    @Test
    void attackRetreatStartsCooldownOnlyAfterPillarsFinishSinking() {
        TenderFleshProfile profile = TenderFleshProfile.DEFAULT;
        TenderFleshRuntimeState state = new TenderFleshRuntimeState();
        state.enclosureProgress = 1.0D;
        state.enclosureRetreating = true;
        state.enclosureBrokenMask = 0x0F;

        int ticks = 0;
        while (state.enclosureRetreating && ticks++ < 500) {
            TenderFleshMechanics.step(profile, state, enclosureInput(ticks, true));
        }

        assertFalse(state.enclosureRetreating);
        assertEquals(0.0D, state.enclosureProgress, EPSILON);
        assertEquals(profile.enclosureCooldownTicks(), state.enclosureCooldownTicks);
        assertEquals(0, state.enclosureBrokenMask);
    }

    @Test
    void forcedDisplacementStartsRetreatWithoutRequiringPillarHits() {
        TenderFleshRuntimeState state = new TenderFleshRuntimeState();
        state.enclosureActive = true;
        state.enclosureProgress = 0.85D;

        assertTrue(TenderFleshMechanics.beginForcedRetreat(state));
        assertFalse(state.enclosureActive);
        assertTrue(state.enclosureRetreating);
        assertEquals(0, state.enclosureBrokenMask);
        assertFalse(TenderFleshMechanics.beginForcedRetreat(state));
    }

    @Test
    void adaptiveEnclosureHeightRespectsConfiguredBounds() {
        TenderFleshProfile profile = TenderFleshProfile.DEFAULT;
        double minimum = profile.enclosureMinHeightPixels() / 16.0D;
        double maximum = profile.enclosureMaxHeightPixels() / 16.0D;

        assertEquals(minimum,
                TenderFleshMechanics.enclosureHeight(profile, 0.25D), EPSILON);
        assertEquals(1.75D,
                TenderFleshMechanics.enclosureHeight(profile, 1.75D), EPSILON);
        assertEquals(maximum,
                TenderFleshMechanics.enclosureHeight(profile, 4.0D), EPSILON);
    }

    private static TenderFleshMechanics.Input enclosureInput(long tick, boolean allowed) {
        return enclosureInput(tick, allowed, 0.0D, 0.0D, 0.0D);
    }

    private static TenderFleshMechanics.Input enclosureInput(
            long tick, boolean allowed, double x, double y, double z) {
        return new TenderFleshMechanics.Input(
                tick, 0.80D, 0.35D, 0.0D, 0.0D,
                false, -1.0D,
                0.25D, 0.0D, 0.0D, 0.05D,
                allowed, x, y, z, x, z);
    }

    private static TenderFleshMechanics.Input input(long tick, double depth,
            double horizontalSpeed, boolean holding, boolean released,
            double motionY, double walkScale) {
        return new TenderFleshMechanics.Input(
                tick, depth, 0.35D, horizontalSpeed, 0.0D,
                holding, released ? 0.80D : -1.0D,
                0.12D, motionY, -0.08D, walkScale);
    }
}

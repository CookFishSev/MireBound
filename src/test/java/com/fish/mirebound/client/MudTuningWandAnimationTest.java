package com.fish.mirebound.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class MudTuningWandAnimationTest {
    private static final float EPSILON = 1.0E-5F;

    @Test
    void cageTickStateOpensThenImmediatelyCloses() {
        float opening = 0.0F;
        for (int tick = 0; tick < 6; tick++) {
            float next = MudTuningWandAnimation.nextCageOpening(opening, true);
            assertTrue(next > opening);
            opening = next;
        }
        assertTrue(MudTuningWandAnimation.cageReachedOpen(opening));

        float firstClosingStep = MudTuningWandAnimation.nextCageOpening(opening, false);
        assertTrue(firstClosingStep < opening);
        opening = firstClosingStep;
        for (int tick = 0; tick < 8 && opening > 0.0F; tick++) {
            float next = MudTuningWandAnimation.nextCageOpening(opening, false);
            assertTrue(next <= opening);
            opening = next;
        }
        assertEquals(0.0F, opening, EPSILON);
    }

    @Test
    void cageRenderValueInterpolatesBetweenClientTicks() {
        assertEquals(0.25F, MudTuningWandAnimation.cageOpening(
                0.25F, 0.75F, 0.0F), EPSILON);
        assertEquals(0.50F, MudTuningWandAnimation.cageOpening(
                0.25F, 0.75F, 0.5F), EPSILON);
        assertEquals(0.75F, MudTuningWandAnimation.cageOpening(
                0.25F, 0.75F, 1.0F), EPSILON);
    }

    @Test
    void interruptedPreviewResumesWithoutJumpingFullyOpen() {
        MudTuningWandClientEffects.PreviewTransition preview =
                new MudTuningWandClientEffects.PreviewTransition();
        Vec3 target = new Vec3(3.0D, 4.0D, 5.0D);
        preview.tick(target, true);
        preview.tick(null, false);
        float interrupted = preview.amount(1.0F);

        preview.tick(target, true);
        float resumed = preview.amount(1.0F);

        assertTrue(resumed > interrupted);
        assertTrue(resumed < 1.0F);
        assertEquals(target, preview.target());
    }

    @Test
    void targetedWandHoldsAimUntilBeamHasFullyFaded() {
        assertTrue(MudTuningWandAnimation.beamKeepsWandAimed(true, 0.0D));
        assertTrue(MudTuningWandAnimation.beamKeepsWandAimed(
                true, MudTuningWandAnimation.BEAM_END_TICKS - 0.01D));
        assertTrue(!MudTuningWandAnimation.beamKeepsWandAimed(
                true, MudTuningWandAnimation.BEAM_END_TICKS));
        assertTrue(!MudTuningWandAnimation.beamKeepsWandAimed(false, 1.0D));
    }

    @Test
    void beamExtendsAndFadesWithinActivationLifetime() {
        assertEquals(0.0F, MudTuningWandAnimation.beamExtension(0.0D), EPSILON);
        assertEquals(1.0F, MudTuningWandAnimation.beamExtension(1.0D), EPSILON);
        assertEquals(0.0F, MudTuningWandAnimation.beamAlpha(-0.1D), EPSILON);
        assertTrue(MudTuningWandAnimation.beamAlpha(1.0D) > 0.95F);
        assertTrue(MudTuningWandAnimation.beamAlpha(10.0D) > 0.0F);
        assertEquals(0.0F, MudTuningWandAnimation.beamAlpha(13.0D), EPSILON);
        assertEquals(0.0F, MudTuningWandAnimation.beamAlpha(
                MudTuningWandAnimation.BEAM_END_TICKS), EPSILON);
    }

    @Test
    void coreMotionResponseIsSmoothAndFrameRateIndependent() {
        assertEquals(0.0F, MudTuningWandCoreMotion.response(0.0D), EPSILON);
        float shortStep = MudTuningWandCoreMotion.response(0.25D);
        float fullTick = MudTuningWandCoreMotion.response(1.0D);
        assertTrue(shortStep > 0.0F && shortStep < fullTick);
        float fourShortSteps = 1.0F - (float) Math.pow(1.0F - shortStep, 4.0D);
        assertEquals(fullTick, fourShortSteps, 1.0E-4F);
    }

}

package com.fish.mirebound.mud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.util.Mth;
import org.junit.jupiter.api.Test;

class SinkingPhysicsSolverTest {
    private static final double STANDING_HEIGHT = 1.8D;

    @Test
    void tautRescueRopeRelievesSettlingInsideTheSolver() {
        SinkingPhysicsProfile profile =
                SinkingPhysicsProfile.forMedium(SinkingMedium.SOFT_QUICKSAND);
        SinkingPhysicsSolver.Input baseline = new SinkingPhysicsSolver.Input(
                0.9D, 2.0D, STANDING_HEIGHT,
                0.0D, -0.03D, 0.0D,
                0.03D, 0.25D, 0.0D,
                false, true, -1.0D, false,
                0.0D, 0.0D, 0.0D,
                0.5D, 1.0D, 1.0D, 0.0D,
                0.0D, 2.0D, false);
        SinkingPhysicsSolver.Input assisted = new SinkingPhysicsSolver.Input(
                0.9D, 2.0D, STANDING_HEIGHT,
                0.0D, -0.03D, 0.0D,
                0.03D, 0.25D, 0.0D,
                false, true, -1.0D, false,
                0.0D, 0.0D, 0.0D,
                0.5D, 1.0D, 1.0D, 0.0D,
                0.0D, 2.0D, false,
                0.025D, 0.045D, 0.0D, 0.025D);

        SinkingPhysicsSolver.Result normal = SinkingPhysicsSolver.solve(profile, baseline);
        SinkingPhysicsSolver.Result rescued = SinkingPhysicsSolver.solve(profile, assisted);

        assertTrue(rescued.motionY() > normal.motionY());
        assertTrue(rescued.settlingVelocity() < normal.settlingVelocity());
        assertEquals(normal.motionX() + 0.025D, rescued.motionX(), 1.0E-9D);
    }

    @Test
    void horizontalBodyCoverageBlendsDeepResistanceContinuously() {
        SinkingPhysicsProfile profile =
                SinkingPhysicsProfile.forMedium(SinkingMedium.SOFT_QUICKSAND);
        SinkingPhysicsSolver.Result full = SinkingPhysicsSolver.solve(
                profile, coverageInput(1.0D));
        SinkingPhysicsSolver.Result half = SinkingPhysicsSolver.solve(
                profile, coverageInput(0.5D));
        SinkingPhysicsSolver.Result edge = SinkingPhysicsSolver.solve(
                profile, coverageInput(0.1D));

        assertTrue(full.walkScale() < half.walkScale());
        assertTrue(half.walkScale() < edge.walkScale());
        assertEquals(
                1.0D + (full.walkScale() - 1.0D) * 0.5D,
                half.walkScale(),
                1.0E-9D);
    }

    @Test
    void mudWalkerReducesTheCompressionLimitAndRestoresMovement() {
        SinkingPhysicsProfile profile = SinkingPhysicsProfile.forMedium(SinkingMedium.PEAT_BOG);
        SinkingPhysicsSolver.Input normalInput = enchantedInput(1.0D, 0.0D);
        SinkingPhysicsSolver.Input enchantedInput = enchantedInput(0.55D, 0.48D);

        SinkingPhysicsSolver.Result normal = SinkingPhysicsSolver.solve(profile, normalInput);
        SinkingPhysicsSolver.Result enchanted = SinkingPhysicsSolver.solve(profile, enchantedInput);

        assertTrue(enchanted.sinkLimit() < normal.sinkLimit());
        assertEquals(normal.sinkLimit() * 0.55D, enchanted.sinkLimit(), 1.0E-9D);
        assertTrue(enchanted.walkScale() > normal.walkScale());
        assertTrue(enchanted.walkScale() < 1.0D);
    }

    @Test
    void settlingAcceleratesThenBrakesSmoothlyAtTheCompressionLimit() {
        SinkingPhysicsProfile profile = SinkingPhysicsProfile.forMedium(SinkingMedium.SOFT_QUICKSAND);
        double depth = 0.0D;
        double motionY = 0.0D;
        double settling = 0.0D;
        double firstSpeed = 0.0D;
        double peakSpeed = 0.0D;
        double nearLimitSpeed = Double.NaN;
        SinkingPhysicsSolver.Result result = null;

        for (int tick = 0; tick < 2400; tick++) {
            result = SinkingPhysicsSolver.solve(profile, input(
                    depth, motionY, settling, 0.0D, -1.0D, false, 2.0D,
                    0.65D, 0.0D, false, false));
            double speed = -result.motionY();
            if (tick == 0) {
                firstSpeed = speed;
            }
            peakSpeed = Math.max(peakSpeed, speed);
            if (result.remainingDepth() < profile.brakeDistance * 0.35D && Double.isNaN(nearLimitSpeed)) {
                nearLimitSpeed = speed;
            }
            depth += speed;
            motionY = result.motionY();
            settling = result.settlingVelocity();
            assertTrue(depth <= result.sinkLimit() + 1.0E-9D);
        }

        assertTrue(peakSpeed > firstSpeed * 1.25D);
        assertTrue(nearLimitSpeed < peakSpeed);
        assertTrue(result.remainingDepth() < profile.brakeDistance * 0.10D);
        assertTrue(solve(profile, depth, motionY, settling, 0.0D, -1.0D, false).motionY() <= 0.0D);
    }

    @Test
    void advancedCalmSettlingCanFindAYieldEquilibriumBeforeTheHardLimit() {
        double[] values = MudPhysicsProfiles.defaultValues(SinkingMedium.SOFT_QUICKSAND);
        values[MudPhysicsParameter.SINKING_DEPTH_CONTROL_MODE.ordinal()] =
                MudSinkingDepthControl.Mode.ADVANCED.parameterValue();
        SinkingPhysicsProfile profile = SinkingPhysicsProfile.fromValues(values);
        double depth = 0.0D;
        double motionY = 0.0D;
        double settling = 0.0D;
        SinkingPhysicsSolver.Result result = null;

        for (int tick = 0; tick < 2400; tick++) {
            result = solve(profile, depth, motionY, settling, 0.0D, -1.0D, false);
            depth -= result.motionY();
            motionY = result.motionY();
            settling = result.settlingVelocity();
        }

        assertTrue(depth > 0.15D);
        assertTrue(depth < result.sinkLimit() - profile.brakeDistance * 0.10D);
        assertTrue(result.settlingVelocity() < 1.0E-4D);
    }

    @Test
    void simpleCalmSettlingApproachesTheConfiguredDepth() {
        SinkingPhysicsProfile profile = profileWithDepths(0.60D, 0.60D);
        double depth = 0.0D;
        double motionY = 0.0D;
        double settling = 0.0D;
        SinkingPhysicsSolver.Result result = null;

        for (int tick = 0; tick < 2400; tick++) {
            result = solve(profile, depth, motionY, settling, 0.0D, -1.0D, false);
            depth -= result.motionY();
            motionY = result.motionY();
            settling = result.settlingVelocity();
        }

        double remainingDepth = result.remainingDepth();
        assertTrue(remainingDepth < 0.02D,
                () -> "remaining depth was " + remainingDepth);
        assertTrue(depth <= result.sinkLimit() + 1.0E-9D);
        assertTrue(result.motionY() <= 0.0D);
    }

    @Test
    void simpleCalmSettlingReachesDeepRequestedDepthsInReasonableTime() {
        for (double requestedDepth : new double[] {0.70D, 0.99D}) {
            SinkingPhysicsProfile profile = profileWithDepths(
                    requestedDepth, requestedDepth);
            double depth = simulateCalmSettling(profile, 1.0D, false, 800);

            assertTrue(depth >= requestedDepth - 0.03D,
                    () -> "requested " + requestedDepth + " but reached " + depth);
        }
    }

    @Test
    void fullSimpleDepthContinuesNaturallyIntoTheNextLayer() {
        SinkingPhysicsProfile profile = profileWithDepths(1.0D, 1.0D);
        double depth = simulateCalmSettling(profile, 2.0D, true, 1200);

        assertTrue(depth > 1.10D,
                () -> "settling stalled at the layer boundary: " + depth);
    }

    @Test
    void adaptiveMudMaximumDoesNotReplaceItsNaturalDepth() {
        SinkingPhysicsProfile profile = profileWithDepths(
                SinkingMedium.MUD, 0.90D, 0.432D);
        double depth = simulateCalmSettling(profile, 1.0D, false, 1000);

        assertTrue(Math.abs(depth - 0.432D) < 0.02D,
                () -> "adaptive mud reached only " + depth);
    }

    @Test
    void sustainedDisturbanceBreaksNaturalDepthButStopsAtMaximum() {
        SinkingPhysicsProfile profile = profileWithDepths(0.80D, 0.42D);
        double depth = 0.0D;
        double motionY = 0.0D;
        double settling = 0.0D;
        SinkingPhysicsSolver.Result result = null;

        for (int tick = 0; tick < 1600; tick++) {
            double agitation = tick < 700 ? 0.0D : 0.85D;
            double look = tick < 700 ? 0.0D : 18.0D;
            result = SinkingPhysicsSolver.solve(profile, input(
                    depth, motionY, settling, agitation, -1.0D, false,
                    1.0D, 0.85D, look, false, false));
            depth -= result.motionY();
            motionY = result.motionY();
            settling = result.settlingVelocity();
        }

        assertTrue(depth > 0.55D, "disturbance stopped at " + depth);
        assertTrue(depth <= 0.80D + 1.0E-9D, "maximum exceeded: " + depth);
        assertEquals(0.42D, result.naturalSinkLimit(), 1.0E-9D);
        assertEquals(0.80D, result.sinkLimit(), 1.0E-9D);
    }

    @Test
    void beingBelowTheLimitStopsCompressionWithoutCreatingBuoyancy() {
        SinkingPhysicsProfile profile = SinkingPhysicsProfile.forMedium(SinkingMedium.SOFT_QUICKSAND);
        SinkingPhysicsSolver.Result result = solve(profile, 1.99D, -0.04D, 0.04D, 0.0D, -1.0D, false);
        assertEquals(0.0D, result.motionY(), 1.0E-9D);
        assertEquals(0.0D, result.settlingVelocity(), 1.0E-9D);
    }

    @Test
    void disturbanceBreaksYieldAndIncreasesSettling() {
        SinkingPhysicsProfile profile = SinkingPhysicsProfile.forMedium(SinkingMedium.SOFT_QUICKSAND);
        SinkingPhysicsSolver.Result calm = solve(profile, 0.55D, 0.0D, 0.0D, 0.0D, -1.0D, false);
        SinkingPhysicsSolver.Result disturbed = SinkingPhysicsSolver.solve(profile, input(
                0.55D, 0.0D, 0.0D, 0.18D, -1.0D, false, 2.0D,
                0.85D, 9.0D, true, true));

        assertTrue(disturbed.targetSinkSpeed() > calm.targetSinkSpeed());
        assertTrue(disturbed.settlingVelocity() > calm.settlingVelocity());
        assertTrue(disturbed.yieldResistance() < calm.yieldResistance());
    }

    @Test
    void struggleProtectionReducesTheDownwardChargeContribution() {
        double[] protectedValues = MudPhysicsProfiles.defaultValues(SinkingMedium.SOFT_QUICKSAND);
        double[] unprotectedValues = protectedValues.clone();
        unprotectedValues[MudPhysicsParameter.STRUGGLE_SINK_SUPPRESSION.ordinal()] = 0.0D;
        SinkingPhysicsSolver.Input charging = input(
                0.45D, 0.0D, 0.0D, 0.0D, -1.0D, false, 2.0D,
                0.0D, 0.0D, false, true);

        SinkingPhysicsSolver.Result protectedResult = SinkingPhysicsSolver.solve(
                SinkingPhysicsProfile.fromValues(protectedValues), charging);
        SinkingPhysicsSolver.Result unprotectedResult = SinkingPhysicsSolver.solve(
                SinkingPhysicsProfile.fromValues(unprotectedValues), charging);

        assertTrue(protectedResult.disturbanceSink() < unprotectedResult.disturbanceSink());
        assertTrue(protectedResult.targetSinkSpeed() < unprotectedResult.targetSinkSpeed());
    }

    @Test
    void struggleIsMonotonicAndOnlyExplicitStruggleCanMoveUpward() {
        SinkingPhysicsProfile profile = SinkingPhysicsProfile.forMedium(SinkingMedium.SOFT_QUICKSAND);
        SinkingPhysicsSolver.Result weak = solve(profile, 0.45D, 0.0D, 0.02D, 0.0D, 0.20D, false);
        SinkingPhysicsSolver.Result strong = solve(profile, 0.45D, 0.0D, 0.02D, 0.0D, 0.90D, false);
        SinkingPhysicsSolver.Result noStruggle = solve(profile, 0.45D, 0.08D, 0.0D, 0.0D, -1.0D, false);

        assertTrue(strong.motionY() > weak.motionY());
        assertTrue(weak.motionY() > 0.0D);
        assertTrue(noStruggle.motionY() <= 0.0D);
        assertTrue(strong.settlingVelocity() < weak.settlingVelocity());
    }

    @Test
    void horizontalMovementLocksSmoothlyAroundWaistDepth() {
        SinkingPhysicsProfile profile = SinkingPhysicsProfile.forMedium(SinkingMedium.SOFT_QUICKSAND);
        SinkingPhysicsSolver.Result shallow = solve(profile, 0.12D, 0.0D, 0.0D, 0.12D, -1.0D, false);
        SinkingPhysicsSolver.Result thigh = solve(profile, 0.55D, 0.0D, 0.0D, 0.12D, -1.0D, false);
        SinkingPhysicsSolver.Result waist = solve(profile, 0.92D, 0.0D, 0.0D, 0.12D, -1.0D, false);

        assertTrue(shallow.walkScale() > thigh.walkScale());
        assertTrue(thigh.walkScale() > waist.walkScale());
        assertTrue(shallow.walkScale() > 0.80D);
        assertTrue(waist.walkScale() < 0.01D);
    }

    @Test
    void movementResistanceUsesImmersedBodyShareInsteadOfContactDepth() {
        SinkingPhysicsProfile profile = SinkingPhysicsProfile.forMedium(SinkingMedium.SOFT_QUICKSAND);
        SinkingPhysicsSolver.Input thinSideContact = input(
                0.92D, 0.0D, 0.0D, 0.12D, -1.0D, false, 2.0D,
                0.0D, 0.0D, false, false, 0.06D);
        SinkingPhysicsSolver.Input waistImmersion = input(
                0.92D, 0.0D, 0.0D, 0.12D, -1.0D, false, 2.0D,
                0.0D, 0.0D, false, false, 0.52D);

        SinkingPhysicsSolver.Result thin = SinkingPhysicsSolver.solve(profile, thinSideContact);
        SinkingPhysicsSolver.Result waist = SinkingPhysicsSolver.solve(profile, waistImmersion);
        assertTrue(thin.walkScale() > 0.80D);
        assertTrue(waist.walkScale() < 0.02D);
    }

    @Test
    void movementCurveHitsConfiguredBodyDepthAnchorsWithoutOvershoot() {
        SinkingPhysicsProfile profile = SinkingPhysicsProfile.forMedium(SinkingMedium.SOFT_QUICKSAND);
        assertEquals(profile.walkSurface, SinkingPhysicsSolver.walkScale(profile, 0.0D), 1.0E-12D);
        assertEquals(profile.walkKnee, SinkingPhysicsSolver.walkScale(profile, profile.walkKneeDepth), 1.0E-12D);
        assertEquals(profile.walkThigh, SinkingPhysicsSolver.walkScale(profile, profile.walkThighDepth), 1.0E-12D);
        assertEquals(profile.walkWaist, SinkingPhysicsSolver.walkScale(profile, profile.walkWaistDepth), 1.0E-12D);

        double previous = profile.walkSurface;
        for (int step = 1; step <= 200; step++) {
            double depth = profile.walkWaistDepth * step / 200.0D;
            double current = SinkingPhysicsSolver.walkScale(profile, depth);
            assertTrue(current <= previous + 1.0E-12D);
            assertTrue(current >= profile.walkWaist - 1.0E-12D);
            previous = current;
        }
    }

    @Test
    void movementCurveHasContinuousSlopeAtBodyDepthAnchors() {
        SinkingPhysicsProfile profile = SinkingPhysicsProfile.forMedium(SinkingMedium.SOFT_QUICKSAND);
        assertContinuousSlope(profile, profile.walkKneeDepth);
        assertContinuousSlope(profile, profile.walkThighDepth);
    }

    @Test
    void verticalCarryGuiParametersDriveTheSolver() {
        double[] values = MudPhysicsProfiles.defaultValues(SinkingMedium.SOFT_QUICKSAND);
        values[MudPhysicsParameter.VERTICAL_SURFACE.ordinal()] = 0.73D;
        values[MudPhysicsParameter.VERTICAL_DEEP.ordinal()] = 0.11D;
        values[MudPhysicsParameter.GRANULAR_COLLAPSE.ordinal()] = 0.0D;
        values[MudPhysicsParameter.COHESIVE_SUCTION.ordinal()] = 0.0D;
        values[MudPhysicsParameter.ADHESIVE_GRIP.ordinal()] = 0.0D;
        SinkingPhysicsProfile profile = SinkingPhysicsProfile.fromValues(values);

        SinkingPhysicsSolver.Result shallow = solve(profile, 0.0D, 0.0D, 0.0D, 0.0D, -1.0D, false);
        SinkingPhysicsSolver.Result deep = solve(profile, 2.0D, 0.0D, 0.0D, 0.0D, -1.0D, false);
        assertEquals(0.73D, shallow.verticalScale(), 1.0E-12D);
        assertEquals(0.11D, deep.verticalScale(), 1.0E-12D);
    }

    @Test
    void shallowColumnWinsOverMaterialDepthLimit() {
        SinkingPhysicsProfile profile = SinkingPhysicsProfile.forMedium(SinkingMedium.SOFT_QUICKSAND);
        SinkingPhysicsSolver.Result result = solve(profile, 0.0D, 0.0D, 0.0D, 0.0D, -1.0D, false, 0.50D);
        assertEquals(0.50D * MudSinkingDepthControl.maximumDepth(
                profile.maxDepthFactor, profile.columnMargin),
                result.sinkLimit(), 1.0E-9D);
    }

    @Test
    void zeroMaximumDepthPreventsSinkingIntoTheCurrentLayer() {
        SinkingPhysicsProfile profile = profileWithMaximumDepth(0.0D);

        assertEquals(0.0D, SinkingPhysicsSolver.sinkLimit(
                profile, 2.0D, 0.0D, 1.0D, true, 1.0D), 1.0E-9D);
    }

    @Test
    void halfMaximumDepthStopsAtTheMiddleOfTheCurrentLayer() {
        SinkingPhysicsProfile profile = profileWithMaximumDepth(0.5D);

        assertEquals(0.5D, SinkingPhysicsSolver.sinkLimit(
                profile, 2.0D, 0.0D, 1.0D, true, 1.0D), 1.0E-9D);
    }

    @Test
    void fullMaximumDepthAddsOnlyTheLayerTransitionPenetration() {
        SinkingPhysicsProfile profile = profileWithMaximumDepth(1.0D);

        assertEquals(1.025D, SinkingPhysicsSolver.sinkLimit(
                profile, 2.0D, 0.0D, 1.0D, true, 1.0D), 1.0E-9D);
        assertEquals(1.0D, SinkingPhysicsSolver.sinkLimit(
                profile, 1.0D, 0.0D, 1.0D, false, 1.0D), 1.0E-9D);
    }

    @Test
    void deeperLayerUsesItsOwnMaximumDepth() {
        SinkingPhysicsProfile lowerLayer = profileWithMaximumDepth(0.35D);

        assertEquals(1.35D, SinkingPhysicsSolver.sinkLimit(
                lowerLayer, 2.0D, 1.0D, 1.0D, false, 1.0D), 1.0E-9D);
    }

    @Test
    void granularComponentTurnsMovementIntoAdditionalCollapse() {
        double[] calmValues = MudPhysicsProfiles.defaultValues(SinkingMedium.SOFT_QUICKSAND);
        calmValues[MudPhysicsParameter.GRANULAR_COLLAPSE.ordinal()] = 0.0D;
        calmValues[MudPhysicsParameter.COHESIVE_SUCTION.ordinal()] = 0.0D;
        calmValues[MudPhysicsParameter.ADHESIVE_GRIP.ordinal()] = 0.0D;
        double[] granularValues = calmValues.clone();
        granularValues[MudPhysicsParameter.GRANULAR_COLLAPSE.ordinal()] = 1.0D;

        SinkingPhysicsSolver.Input moving = input(
                0.35D, 0.0D, 0.0D, 0.16D, -1.0D, false, 2.0D,
                0.55D, 0.0D, false, false);
        SinkingPhysicsSolver.Result baseline = SinkingPhysicsSolver.solve(
                SinkingPhysicsProfile.fromValues(calmValues), moving);
        SinkingPhysicsSolver.Result granular = SinkingPhysicsSolver.solve(
                SinkingPhysicsProfile.fromValues(granularValues), moving);

        assertTrue(granular.disturbanceSink() > baseline.disturbanceSink());
        assertTrue(granular.targetSinkSpeed() > baseline.targetSinkSpeed());
    }

    @Test
    void adhesiveComponentGripsWithoutAddingASeparateDownwardForce() {
        double[] baseValues = MudPhysicsProfiles.defaultValues(SinkingMedium.TAR);
        baseValues[MudPhysicsParameter.GRANULAR_COLLAPSE.ordinal()] = 0.0D;
        baseValues[MudPhysicsParameter.COHESIVE_SUCTION.ordinal()] = 0.0D;
        baseValues[MudPhysicsParameter.ADHESIVE_GRIP.ordinal()] = 0.0D;
        double[] adhesiveValues = baseValues.clone();
        adhesiveValues[MudPhysicsParameter.ADHESIVE_GRIP.ordinal()] = 1.0D;
        SinkingPhysicsSolver.Input input = input(
                0.65D, 0.0D, 0.0D, 0.14D, 0.75D, false, 2.0D,
                0.0D, 0.0D, false, false, 0.48D);

        SinkingPhysicsSolver.Result baseline = SinkingPhysicsSolver.solve(
                SinkingPhysicsProfile.fromValues(baseValues), input);
        SinkingPhysicsSolver.Result adhesive = SinkingPhysicsSolver.solve(
                SinkingPhysicsProfile.fromValues(adhesiveValues), input);

        assertTrue(adhesive.walkScale() < baseline.walkScale());
        assertTrue(adhesive.verticalScale() < baseline.verticalScale());
        assertTrue(adhesive.struggleImpulse() < baseline.struggleImpulse());
        assertEquals(baseline.disturbanceSink(), adhesive.disturbanceSink(), 1.0E-12D);
    }

    private static void assertContinuousSlope(SinkingPhysicsProfile profile, double depth) {
        double epsilon = 1.0E-5D;
        double center = SinkingPhysicsSolver.walkScale(profile, depth);
        double leftSlope = (center - SinkingPhysicsSolver.walkScale(profile, depth - epsilon)) / epsilon;
        double rightSlope = (SinkingPhysicsSolver.walkScale(profile, depth + epsilon) - center) / epsilon;
        assertEquals(leftSlope, rightSlope, 0.01D);
    }

    private static SinkingPhysicsProfile profileWithMaximumDepth(double maximumDepth) {
        return profileWithMaximumDepth(SinkingMedium.SOFT_QUICKSAND, maximumDepth);
    }

    private static SinkingPhysicsProfile profileWithMaximumDepth(
            SinkingMedium medium, double maximumDepth) {
        return profileWithDepths(medium, maximumDepth, maximumDepth);
    }

    private static SinkingPhysicsProfile profileWithDepths(
            double maximumDepth, double naturalDepth) {
        return profileWithDepths(SinkingMedium.SOFT_QUICKSAND, maximumDepth, naturalDepth);
    }

    private static SinkingPhysicsProfile profileWithDepths(
            SinkingMedium medium, double maximumDepth, double naturalDepth) {
        double[] values = MudPhysicsProfiles.defaultValues(medium);
        values[MudPhysicsParameter.SIMPLE_MAXIMUM_SINKING_DEPTH.ordinal()] = maximumDepth;
        values[MudPhysicsParameter.SIMPLE_NATURAL_SINKING_DEPTH.ordinal()] = naturalDepth;
        return SinkingPhysicsProfile.fromValues(values);
    }

    private static double simulateCalmSettling(SinkingPhysicsProfile profile,
            double columnDepth, boolean layered, int ticks) {
        double depth = 0.0D;
        double motionY = 0.0D;
        double settling = 0.0D;
        for (int tick = 0; tick < ticks; tick++) {
            boolean lowerLayer = layered && depth > 1.018D;
            SinkingPhysicsSolver.Input input = new SinkingPhysicsSolver.Input(
                    depth,
                    columnDepth,
                    STANDING_HEIGHT,
                    0.0D,
                    motionY,
                    0.0D,
                    settling,
                    0.0D,
                    0.0D,
                    false,
                    false,
                    -1.0D,
                    false,
                    0.0D,
                    0.0D,
                    0.0D,
                    Mth.clamp(depth / STANDING_HEIGHT, 0.0D, 1.0D),
                    1.0D,
                    1.0D,
                    0.0D,
                    lowerLayer ? 1.0D : 0.0D,
                    1.0D,
                    layered && !lowerLayer);
            SinkingPhysicsSolver.Result result = SinkingPhysicsSolver.solve(profile, input);
            depth -= result.motionY();
            motionY = result.motionY();
            settling = result.settlingVelocity();
        }
        return depth;
    }

    private static SinkingPhysicsSolver.Result solve(SinkingPhysicsProfile profile, double depth, double motionY,
            double settling, double horizontalMotion, double charge, boolean carry) {
        return solve(profile, depth, motionY, settling, horizontalMotion, charge, carry, 2.0D);
    }

    private static SinkingPhysicsSolver.Result solve(SinkingPhysicsProfile profile, double depth, double motionY,
            double settling, double horizontalMotion, double charge, boolean carry, double columnDepth) {
        return SinkingPhysicsSolver.solve(profile, input(
                depth, motionY, settling, horizontalMotion, charge, carry, columnDepth,
                0.0D, 0.0D, false, false));
    }

    private static SinkingPhysicsSolver.Input input(double depth, double motionY, double settling,
            double horizontalMotion, double charge, boolean carry, double columnDepth,
            double agitation, double lookDelta, boolean crouching, boolean holding) {
        return new SinkingPhysicsSolver.Input(
                depth,
                columnDepth,
                STANDING_HEIGHT,
                horizontalMotion,
                motionY,
                0.0D,
                settling,
                agitation,
                lookDelta,
                crouching,
                holding,
                charge,
                carry,
                0.0D,
                0.0D,
                0.0D);
    }

    private static SinkingPhysicsSolver.Input input(double depth, double motionY, double settling,
            double horizontalMotion, double charge, boolean carry, double columnDepth,
            double agitation, double lookDelta, boolean crouching, boolean holding,
            double immersionFraction) {
        return new SinkingPhysicsSolver.Input(
                depth,
                columnDepth,
                STANDING_HEIGHT,
                horizontalMotion,
                motionY,
                0.0D,
                settling,
                agitation,
                lookDelta,
                crouching,
                holding,
                charge,
                carry,
                0.0D,
                0.0D,
                0.0D,
                immersionFraction);
    }

    private static SinkingPhysicsSolver.Input enchantedInput(
            double depthLimitScale,
            double walkRestoration) {
        return new SinkingPhysicsSolver.Input(
                0.62D,
                3.0D,
                STANDING_HEIGHT,
                0.10D,
                0.0D,
                0.0D,
                0.0D,
                0.35D,
                0.0D,
                false,
                false,
                -1.0D,
                false,
                0.0D,
                0.0D,
                0.0D,
                0.55D,
                depthLimitScale,
                walkRestoration);
    }

    private static SinkingPhysicsSolver.Input coverageInput(
            double horizontalCoverage) {
        return new SinkingPhysicsSolver.Input(
                0.90D,
                2.0D,
                STANDING_HEIGHT,
                0.10D,
                0.0D,
                0.0D,
                0.0D,
                0.0D,
                0.0D,
                false,
                false,
                -1.0D,
                false,
                0.0D,
                0.0D,
                0.0D,
                0.50D,
                horizontalCoverage,
                1.0D,
                0.0D);
    }
}

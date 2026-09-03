package com.fish.mirebound.mud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.world.entity.LivingEntity;
import org.junit.jupiter.api.Test;

class MudPlayerMovementTest {
    @Test
    void subthresholdSinkIsAppliedImmediatelyInsteadOfBeingLost() {
        MudPlayerMovement.VerticalMotionPlan plan =
                MudPlayerMovement.verticalMotionPlan(-0.00269D);

        assertEquals(-0.00269D, plan.immediateY(), 1.0E-12D);
        assertEquals(0.0D, plan.retainedY(), 1.0E-12D);
    }

    @Test
    void vanillaSizedSinkRemainsVelocityDriven() {
        MudPlayerMovement.VerticalMotionPlan plan =
                MudPlayerMovement.verticalMotionPlan(
                        -LivingEntity.MIN_MOVEMENT_DISTANCE);

        assertEquals(0.0D, plan.immediateY(), 1.0E-12D);
        assertEquals(
                -LivingEntity.MIN_MOVEMENT_DISTANCE,
                plan.retainedY(),
                1.0E-12D);
    }

    @Test
    void upwardStruggleMotionIsNotChanged() {
        MudPlayerMovement.VerticalMotionPlan plan =
                MudPlayerMovement.verticalMotionPlan(0.002D);

        assertEquals(0.0D, plan.immediateY(), 1.0E-12D);
        assertEquals(0.002D, plan.retainedY(), 1.0E-12D);
    }

    @Test
    void zeroMaximumDepthCorrectsContactPenetrationBackToTheLayerSurface() {
        double[] values = MudPhysicsProfiles.defaultValues(SinkingMedium.MUD);
        values[MudPhysicsParameter.SINKING_DEPTH_CONTROL_MODE.ordinal()] =
                MudSinkingDepthControl.Mode.SIMPLE.parameterValue();
        values[MudPhysicsParameter.SIMPLE_MAXIMUM_SINKING_DEPTH.ordinal()] = 0.0D;
        SinkingPhysicsProfile profile = SinkingPhysicsProfile.fromValues(values);

        assertEquals(0.018D, MudPlayerMovement.zeroDepthCorrection(
                profile, 0.018D, 1.0D, 0.0D, 1.0D, false), 1.0E-12D);
        assertEquals(0.0D, MudPlayerMovement.zeroDepthCorrection(
                profile, 0.0D, 1.0D, 0.0D, 1.0D, false), 1.0E-12D);
    }
}

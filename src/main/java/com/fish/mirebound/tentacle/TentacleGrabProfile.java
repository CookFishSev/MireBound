package com.fish.mirebound.tentacle;

import com.fish.mirebound.mud.MudPhysicsParameter;
import net.minecraft.util.Mth;

public record TentacleGrabProfile(
        int tipSegments,
        double contactPadding,
        double targetPaddingScale,
        int attachTicks,
        double spring,
        double damping,
        double maximumAcceleration,
        double maximumSpeed,
        double breakDistance,
        int maximumTicks,
        double wholeBodyTipRatio,
        double surfaceClearanceScale,
        double flightControlScale,
        int holdWrapTicks,
        int holdLiftTicks,
        double holdLiftHeight,
        double holdControlScale,
        double holdPositionRadius,
        double holdHeightVariation,
        int holdPositionTicks,
        int holdMoveTicks,
        double ragdollGravity,
        double ragdollArmGravityScale,
        double ragdollArmTorsoClearanceScale,
        double ragdollDamping,
        double ragdollInertia,
        double ragdollGripStiffness,
        double ragdollGripOrientationStiffness,
        double ragdollJointStiffness,
        int ragdollSolverIterations,
        boolean ragdollCollisionEnabled,
        double ragdollCollisionRadius,
        double ragdollMaximumNodeSpeed,
        double thrashRadius,
        double thrashVerticalRange,
        double thrashSpeed,
        double thrashJitter,
        double wrapRadius,
        double wrapSpeed) {

    public static TentacleGrabProfile fromValues(double[] values) {
        return new TentacleGrabProfile(
                integer(values, MudPhysicsParameter.TENTACLE_GRAB_TIP_SEGMENTS),
                value(values, MudPhysicsParameter.TENTACLE_GRAB_CONTACT_PADDING),
                value(values, MudPhysicsParameter.TENTACLE_GRAB_TARGET_PADDING_SCALE),
                integer(values, MudPhysicsParameter.TENTACLE_GRAB_ATTACH_TICKS),
                value(values, MudPhysicsParameter.TENTACLE_GRAB_SPRING),
                value(values, MudPhysicsParameter.TENTACLE_GRAB_DAMPING),
                value(values, MudPhysicsParameter.TENTACLE_GRAB_MAX_ACCELERATION),
                value(values, MudPhysicsParameter.TENTACLE_GRAB_MAX_SPEED),
                value(values, MudPhysicsParameter.TENTACLE_GRAB_BREAK_DISTANCE),
                integer(values, MudPhysicsParameter.TENTACLE_GRAB_MAX_TICKS),
                value(values, MudPhysicsParameter.TENTACLE_GRAB_WHOLE_BODY_TIP_RATIO),
                value(values, MudPhysicsParameter.TENTACLE_GRAB_SURFACE_CLEARANCE_SCALE),
                value(values, MudPhysicsParameter.TENTACLE_GRAB_FLIGHT_CONTROL_SCALE),
                integer(values, MudPhysicsParameter.TENTACLE_HOLD_WRAP_TICKS),
                integer(values, MudPhysicsParameter.TENTACLE_HOLD_LIFT_TICKS),
                value(values, MudPhysicsParameter.TENTACLE_HOLD_LIFT_HEIGHT),
                value(values, MudPhysicsParameter.TENTACLE_HOLD_CONTROL_SCALE),
                value(values, MudPhysicsParameter.TENTACLE_HOLD_POSITION_RADIUS),
                value(values, MudPhysicsParameter.TENTACLE_HOLD_HEIGHT_VARIATION),
                integer(values, MudPhysicsParameter.TENTACLE_HOLD_POSITION_TICKS),
                integer(values, MudPhysicsParameter.TENTACLE_HOLD_MOVE_TICKS),
                value(values, MudPhysicsParameter.TENTACLE_RAGDOLL_GRAVITY),
                value(values, MudPhysicsParameter.TENTACLE_RAGDOLL_ARM_GRAVITY_SCALE),
                value(values, MudPhysicsParameter.TENTACLE_RAGDOLL_ARM_TORSO_CLEARANCE_SCALE),
                value(values, MudPhysicsParameter.TENTACLE_RAGDOLL_DAMPING),
                value(values, MudPhysicsParameter.TENTACLE_RAGDOLL_INERTIA),
                value(values, MudPhysicsParameter.TENTACLE_RAGDOLL_GRIP_STIFFNESS),
                value(values, MudPhysicsParameter.TENTACLE_RAGDOLL_GRIP_ORIENTATION_STIFFNESS),
                value(values, MudPhysicsParameter.TENTACLE_RAGDOLL_JOINT_STIFFNESS),
                integer(values, MudPhysicsParameter.TENTACLE_RAGDOLL_SOLVER_ITERATIONS),
                value(values, MudPhysicsParameter.TENTACLE_RAGDOLL_COLLISION_ENABLED) >= 0.5D,
                value(values, MudPhysicsParameter.TENTACLE_RAGDOLL_COLLISION_RADIUS),
                value(values, MudPhysicsParameter.TENTACLE_RAGDOLL_MAX_NODE_SPEED),
                value(values, MudPhysicsParameter.TENTACLE_THRASH_RADIUS),
                value(values, MudPhysicsParameter.TENTACLE_THRASH_VERTICAL_RANGE),
                value(values, MudPhysicsParameter.TENTACLE_THRASH_SPEED),
                value(values, MudPhysicsParameter.TENTACLE_THRASH_JITTER),
                value(values, MudPhysicsParameter.TENTACLE_WRAP_RADIUS),
                value(values, MudPhysicsParameter.TENTACLE_WRAP_SPEED));
    }

    private static int integer(double[] values, MudPhysicsParameter parameter) {
        return Math.max(0, Mth.floor(value(values, parameter) + 0.5D));
    }

    private static double value(double[] values, MudPhysicsParameter parameter) {
        return parameter.sanitize(values[parameter.ordinal()]);
    }
}

package com.fish.mirebound.itemphysics;

import com.fish.mirebound.mud.MudPhysicsParameter;
import com.fish.mirebound.mud.SinkingMedium;
import net.minecraft.util.Mth;

/** Immutable dropped-item settings consumed by the server item-physics hot path. */
public record DroppedItemPhysicsProfile(
        boolean enabled,
        double maximumSinkDepth,
        double settlingSpeed,
        double settlingResponse,
        double surfaceHorizontalRetention,
        double submergedHorizontalRetention,
        double impactPenetrationScale,
        double maximumImpactPenetration,
        double impactVelocityRetention,
        double releaseUpwardSpeed,
        boolean stablePresentationEnabled,
        int presentationSettleTicks,
        double presentationMaximumTiltDegrees,
        double buoyantReturnSpeed,
        double buoyantReturnResponse,
        double buoyantReturnEasingDistance,
        double buoyantReturnMaximumStepFraction) {

    public static DroppedItemPhysicsProfile fromValues(double[] values) {
        return new DroppedItemPhysicsProfile(
                value(values, MudPhysicsParameter.ITEM_PHYSICS_ENABLED) >= 0.5D,
                value(values, MudPhysicsParameter.ITEM_MAXIMUM_SINK_DEPTH),
                value(values, MudPhysicsParameter.ITEM_SETTLING_SPEED),
                value(values, MudPhysicsParameter.ITEM_SETTLING_RESPONSE),
                value(values, MudPhysicsParameter.ITEM_SURFACE_HORIZONTAL_RETENTION),
                value(values, MudPhysicsParameter.ITEM_SUBMERGED_HORIZONTAL_RETENTION),
                value(values, MudPhysicsParameter.ITEM_IMPACT_PENETRATION_SCALE),
                value(values, MudPhysicsParameter.ITEM_MAXIMUM_IMPACT_PENETRATION),
                value(values, MudPhysicsParameter.ITEM_IMPACT_VELOCITY_RETENTION),
                value(values, MudPhysicsParameter.ITEM_RELEASE_UPWARD_SPEED),
                value(values, MudPhysicsParameter.ITEM_STABLE_PRESENTATION_ENABLED) >= 0.5D,
                Mth.floor(value(values, MudPhysicsParameter.ITEM_PRESENTATION_SETTLE_TICKS)),
                value(values, MudPhysicsParameter.ITEM_PRESENTATION_MAXIMUM_TILT_DEGREES),
                value(values, MudPhysicsParameter.ITEM_BUOYANT_RETURN_SPEED),
                value(values, MudPhysicsParameter.ITEM_BUOYANT_RETURN_RESPONSE),
                value(values, MudPhysicsParameter.ITEM_BUOYANT_RETURN_EASING_DISTANCE),
                value(values, MudPhysicsParameter.ITEM_BUOYANT_RETURN_MAXIMUM_STEP_FRACTION));
    }

    public static DroppedItemPhysicsProfile defaultsFor(SinkingMedium medium) {
        double sinkDepth = Mth.clamp(
                0.10D + medium.maxSinkDepthFactor() * 0.34D, 0.14D, 0.55D);
        double settlingSpeed = Mth.clamp(
                Math.abs(medium.entitySinkSpeed()) * 0.20D, 0.0015D, 0.012D);
        double surfaceRetention = Mth.clamp(
                medium.entityHorizontalScale() + 0.42D, 0.44D, 0.92D);
        double submergedRetention = Mth.clamp(
                medium.entityHorizontalScale() + 0.08D, 0.12D, 0.72D);
        double impactDepth = Mth.clamp(sinkDepth * 0.42D, 0.055D, 0.22D);
        return new DroppedItemPhysicsProfile(
                true,
                sinkDepth,
                settlingSpeed,
                0.24D,
                surfaceRetention,
                submergedRetention,
                0.16D,
                impactDepth,
                0.18D,
                0.08D,
                true,
                6,
                8.0D,
                0.018D,
                0.18D,
                0.30D,
                0.65D);
    }

    public static DroppedItemPhysicsProfile defaultsBeforeVisibleSettling(
            SinkingMedium medium) {
        double sinkDepth = Mth.clamp(
                medium.maxSinkDepthFactor() * 0.22D, 0.04D, 0.48D);
        double settlingSpeed = Mth.clamp(
                Math.abs(medium.entitySinkSpeed()) * 0.20D, 0.0015D, 0.012D);
        double surfaceRetention = Mth.clamp(
                medium.entityHorizontalScale() + 0.42D, 0.44D, 0.92D);
        double submergedRetention = Mth.clamp(
                medium.entityHorizontalScale() + 0.08D, 0.12D, 0.72D);
        double impactDepth = Mth.clamp(sinkDepth * 0.70D, 0.035D, 0.24D);
        return new DroppedItemPhysicsProfile(
                true,
                sinkDepth,
                settlingSpeed,
                0.24D,
                surfaceRetention,
                submergedRetention,
                0.16D,
                impactDepth,
                0.18D,
                0.08D,
                true,
                6,
                8.0D,
                0.018D,
                0.18D,
                0.30D,
                0.65D);
    }

    public void writeTo(double[] values) {
        put(values, MudPhysicsParameter.ITEM_PHYSICS_ENABLED, enabled ? 1.0D : 0.0D);
        put(values, MudPhysicsParameter.ITEM_MAXIMUM_SINK_DEPTH, maximumSinkDepth);
        put(values, MudPhysicsParameter.ITEM_SETTLING_SPEED, settlingSpeed);
        put(values, MudPhysicsParameter.ITEM_SETTLING_RESPONSE, settlingResponse);
        put(values, MudPhysicsParameter.ITEM_SURFACE_HORIZONTAL_RETENTION,
                surfaceHorizontalRetention);
        put(values, MudPhysicsParameter.ITEM_SUBMERGED_HORIZONTAL_RETENTION,
                submergedHorizontalRetention);
        put(values, MudPhysicsParameter.ITEM_IMPACT_PENETRATION_SCALE,
                impactPenetrationScale);
        put(values, MudPhysicsParameter.ITEM_MAXIMUM_IMPACT_PENETRATION,
                maximumImpactPenetration);
        put(values, MudPhysicsParameter.ITEM_IMPACT_VELOCITY_RETENTION,
                impactVelocityRetention);
        put(values, MudPhysicsParameter.ITEM_RELEASE_UPWARD_SPEED, releaseUpwardSpeed);
        put(values, MudPhysicsParameter.ITEM_STABLE_PRESENTATION_ENABLED,
                stablePresentationEnabled ? 1.0D : 0.0D);
        put(values, MudPhysicsParameter.ITEM_PRESENTATION_SETTLE_TICKS,
                presentationSettleTicks);
        put(values, MudPhysicsParameter.ITEM_PRESENTATION_MAXIMUM_TILT_DEGREES,
                presentationMaximumTiltDegrees);
        put(values, MudPhysicsParameter.ITEM_BUOYANT_RETURN_SPEED, buoyantReturnSpeed);
        put(values, MudPhysicsParameter.ITEM_BUOYANT_RETURN_RESPONSE, buoyantReturnResponse);
        put(values, MudPhysicsParameter.ITEM_BUOYANT_RETURN_EASING_DISTANCE,
                buoyantReturnEasingDistance);
        put(values, MudPhysicsParameter.ITEM_BUOYANT_RETURN_MAXIMUM_STEP_FRACTION,
                buoyantReturnMaximumStepFraction);
    }

    private static double value(double[] values, MudPhysicsParameter parameter) {
        return parameter.sanitize(values[parameter.ordinal()]);
    }

    private static void put(double[] values, MudPhysicsParameter parameter, double value) {
        values[parameter.ordinal()] = parameter.sanitize(value);
    }
}

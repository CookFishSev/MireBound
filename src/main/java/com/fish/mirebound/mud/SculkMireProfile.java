package com.fish.mirebound.mud;

import com.fish.mirebound.mud.behavior.HiddenThreatTemplate;
import com.fish.mirebound.mud.behavior.MudActivityTemplate;
import com.fish.mirebound.mud.behavior.QuietCrouchEscapeTemplate;
import com.fish.mirebound.mud.behavior.ResonancePulseTemplate;
import com.fish.mirebound.mud.behavior.TimedRestraintTemplate;

/** Cached server/client tuning for the sculk mire state machine. */
public record SculkMireProfile(
        double sneakWalkScale,
        int quietCrouchDelayTicks,
        double quietRiseSpeed,
        double actionSinkBoost,
        double actionThreshold,
        double lookActivityThreshold,
        double hiddenValueGain,
        double hiddenValueDecay,
        double triggerDepth,
        double hiddenTriggerThreshold,
        int clampDurationTicks,
        int clampMaximumTicks,
        int clampExtensionTicks,
        double clampWalkScale,
        int resonanceIntervalTicks,
        int clampSyncIntervalTicks,
        int clampEmergeTicks,
        int clampRetractTicks,
        double clampRadius,
        double clampHeight,
        double clampRenderDistance,
        int clampCloseTicks,
        int clampOpenTicks) implements MudActivityTemplate.Settings,
        QuietCrouchEscapeTemplate.Settings,
        HiddenThreatTemplate.Settings,
        TimedRestraintTemplate.Settings,
        ResonancePulseTemplate.Settings {

    public static final SculkMireProfile DEFAULT = new SculkMireProfile(
            0.92D,
            100,
            0.024D,
            0.032D,
            0.08D,
            1.10D,
            0.014D,
            0.006D,
            0.12D,
            1.0D,
            80,
            220,
            2,
            0.025D,
            4,
            4,
            24,
            18,
            0.68D,
            0.58D,
            96.0D,
            10,
            8);

    public static SculkMireProfile fromValues(double[] values) {
        return new SculkMireProfile(
                value(values, MudPhysicsParameter.SCULK_SNEAK_WALK_SCALE),
                integer(values, MudPhysicsParameter.SCULK_QUIET_CROUCH_DELAY_TICKS),
                value(values, MudPhysicsParameter.SCULK_QUIET_RISE_SPEED),
                value(values, MudPhysicsParameter.SCULK_ACTION_SINK_BOOST),
                value(values, MudPhysicsParameter.SCULK_ACTION_THRESHOLD),
                value(values, MudPhysicsParameter.SCULK_LOOK_ACTIVITY_THRESHOLD),
                value(values, MudPhysicsParameter.SCULK_HIDDEN_VALUE_GAIN),
                value(values, MudPhysicsParameter.SCULK_HIDDEN_VALUE_DECAY),
                value(values, MudPhysicsParameter.SCULK_TRIGGER_DEPTH),
                value(values, MudPhysicsParameter.SCULK_HIDDEN_TRIGGER_THRESHOLD),
                integer(values, MudPhysicsParameter.SCULK_CLAMP_DURATION_TICKS),
                integer(values, MudPhysicsParameter.SCULK_CLAMP_MAXIMUM_TICKS),
                integer(values, MudPhysicsParameter.SCULK_CLAMP_EXTENSION_TICKS),
                value(values, MudPhysicsParameter.SCULK_CLAMP_WALK_SCALE),
                integer(values, MudPhysicsParameter.SCULK_RESONANCE_INTERVAL_TICKS),
                integer(values, MudPhysicsParameter.SCULK_CLAMP_SYNC_INTERVAL_TICKS),
                integer(values, MudPhysicsParameter.SCULK_CLAMP_EMERGE_TICKS),
                integer(values, MudPhysicsParameter.SCULK_CLAMP_RETRACT_TICKS),
                value(values, MudPhysicsParameter.SCULK_CLAMP_RADIUS),
                value(values, MudPhysicsParameter.SCULK_CLAMP_HEIGHT),
                value(values, MudPhysicsParameter.SCULK_CLAMP_RENDER_DISTANCE),
                integer(values, MudPhysicsParameter.SCULK_CLAMP_CLOSE_TICKS),
                integer(values, MudPhysicsParameter.SCULK_CLAMP_OPEN_TICKS));
    }

    public void writeTo(double[] values) {
        put(values, MudPhysicsParameter.SCULK_SNEAK_WALK_SCALE, sneakWalkScale);
        put(values, MudPhysicsParameter.SCULK_QUIET_CROUCH_DELAY_TICKS, quietCrouchDelayTicks);
        put(values, MudPhysicsParameter.SCULK_QUIET_RISE_SPEED, quietRiseSpeed);
        put(values, MudPhysicsParameter.SCULK_ACTION_SINK_BOOST, actionSinkBoost);
        put(values, MudPhysicsParameter.SCULK_ACTION_THRESHOLD, actionThreshold);
        put(values, MudPhysicsParameter.SCULK_LOOK_ACTIVITY_THRESHOLD, lookActivityThreshold);
        put(values, MudPhysicsParameter.SCULK_HIDDEN_VALUE_GAIN, hiddenValueGain);
        put(values, MudPhysicsParameter.SCULK_HIDDEN_VALUE_DECAY, hiddenValueDecay);
        put(values, MudPhysicsParameter.SCULK_TRIGGER_DEPTH, triggerDepth);
        put(values, MudPhysicsParameter.SCULK_HIDDEN_TRIGGER_THRESHOLD, hiddenTriggerThreshold);
        put(values, MudPhysicsParameter.SCULK_CLAMP_DURATION_TICKS, clampDurationTicks);
        put(values, MudPhysicsParameter.SCULK_CLAMP_MAXIMUM_TICKS, clampMaximumTicks);
        put(values, MudPhysicsParameter.SCULK_CLAMP_EXTENSION_TICKS, clampExtensionTicks);
        put(values, MudPhysicsParameter.SCULK_CLAMP_WALK_SCALE, clampWalkScale);
        put(values, MudPhysicsParameter.SCULK_RESONANCE_INTERVAL_TICKS, resonanceIntervalTicks);
        put(values, MudPhysicsParameter.SCULK_CLAMP_SYNC_INTERVAL_TICKS, clampSyncIntervalTicks);
        put(values, MudPhysicsParameter.SCULK_CLAMP_EMERGE_TICKS, clampEmergeTicks);
        put(values, MudPhysicsParameter.SCULK_CLAMP_RETRACT_TICKS, clampRetractTicks);
        put(values, MudPhysicsParameter.SCULK_CLAMP_RADIUS, clampRadius);
        put(values, MudPhysicsParameter.SCULK_CLAMP_HEIGHT, clampHeight);
        put(values, MudPhysicsParameter.SCULK_CLAMP_RENDER_DISTANCE, clampRenderDistance);
        put(values, MudPhysicsParameter.SCULK_CLAMP_CLOSE_TICKS, clampCloseTicks);
        put(values, MudPhysicsParameter.SCULK_CLAMP_OPEN_TICKS, clampOpenTicks);
    }

    @Override
    public int restraintDurationTicks() {
        return clampDurationTicks;
    }

    @Override
    public int restraintMaximumTicks() {
        return clampMaximumTicks;
    }

    @Override
    public int restraintExtensionTicks() {
        return clampExtensionTicks;
    }

    private static double value(double[] values, MudPhysicsParameter parameter) {
        return values[parameter.ordinal()];
    }

    private static int integer(double[] values, MudPhysicsParameter parameter) {
        return Math.max(1, (int) Math.round(value(values, parameter)));
    }

    private static void put(double[] values, MudPhysicsParameter parameter, double value) {
        values[parameter.ordinal()] = parameter.sanitize(value);
    }
}

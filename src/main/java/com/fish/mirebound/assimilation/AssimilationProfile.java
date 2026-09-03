package com.fish.mirebound.assimilation;

import com.fish.mirebound.mud.MudPhysicsParameter;
import net.minecraft.util.Mth;

/** Immutable server profile shared with clients in assimilation state packets. */
public record AssimilationProfile(
        boolean enabled,
        float gainPerTick,
        float immersionExponent,
        float minimumMoveScale,
        float minimumLookScale,
        float minimumAnimationScale,
        float screenOpacity,
        float blurStrength,
        boolean armorEnabled,
        boolean ordinaryCoverageEnabled,
        boolean finalStasisEnabled,
        boolean shellPhysicsEnabled,
        float shellGravity,
        float shellAirDrag,
        float shellGroundFriction,
        float shellRestitution,
        float shellMaximumSpeed,
        float shellMaximumTilt,
        float shellTiltResponse,
        float shellTeleportReleaseDistance,
        int shellTransportHandoffTicks,
        float soulRadius,
        float soulMoveSpeed,
        float soulSprintMultiplier,
        float soulAcceleration,
        float soulDrag,
        float soulEmergenceBackOffset,
        float soulEmergenceUpOffset,
        float soulBaseEffect,
        float soulEffectStart,
        float soulColorStrength,
        float soulPixelSize,
        float soulBlurRadius,
        float soulBaseFogStrength,
        float soulFogStart,
        float soulFogDistance,
        float soulFogOpacity,
        float soulBoundarySoftness,
        float soulFloatAmplitude,
        float soulFloatSpeed,
        float soulSoundDamping,
        float rescuePulseStrength,
        float rescueCrackDarkness,
        float rescueDamagePerHit,
        int rescueRevealRadius,
        int rescuePulseTicks,
        boolean selfRescueQteEnabled,
        int selfRescueQteTimeoutTicks,
        int selfRescueQteNextDelayTicks,
        int selfRescueQteFailureDelayTicks,
        int selfRescueQteRequiredStreak,
        int selfRescueQteRevealRadius,
        int selfRescueQteFadeTicks,
        float selfRescueQteAimDegrees,
        float selfRescueQteHoldChance,
        int selfRescueQteHoldTicks,
        float selfRescueQteRapidChance,
        int selfRescueQteRapidClicks,
        float selfRescueQteTraceChance,
        int selfRescueQteTraceTimeoutTicks,
        int selfRescueQteTraceNodes,
        int selfRescueQteTraceSpacing,
        float selfRescueQteTraceHitRadius,
        float selfRescueQteRange,
        int soulTransitionTicks,
        int restoreTicks,
        int restoreBlackoutFadeTicks,
        int rescueGraceTicks,
        boolean partialPurgeEnabled,
        boolean partialPurgeCancelOnMove,
        int partialPurgeCursorMinOneWayTicks,
        int partialPurgeCursorMaxOneWayTicks,
        float partialPurgeZoneMinWidth,
        float partialPurgeZoneMaxWidth,
        float partialPurgeSuccessAmount,
        float partialPurgeFailureAmount,
        int partialPurgeSuccessWeaknessTicks,
        int partialPurgeFailureWeaknessTicks,
        float partialPurgeFailureDamage,
        int partialPurgeRoundCooldownTicks,
        int partialPurgeSplashDroplets,
        float partialPurgeSplashSpeed,
        float screenCrackStartProgress,
        int screenCrackMinIntervalTicks,
        int screenCrackMaxIntervalTicks,
        int screenCrackFadeInTicks,
        int screenCrackHoldTicks,
        int screenCrackFadeOutTicks,
        float screenCrackMinLength,
        float screenCrackMaxLength,
        float screenCrackMinWidth,
        float screenCrackMaxWidth) {
    public static final AssimilationProfile DEFAULT = new AssimilationProfile(
            true, 0.0040F, 1.20F,
            0.10F, 0.04F, 0.14F,
            0.94F, 0.68F,
            true, true, true,
            true, 0.08F, 0.98F, 0.72F, 0.08F, 3.0F, 18.0F, 0.18F,
            48.0F, 6,
            16.0F,
            0.32F, 1.90F, 0.36F, 0.72F,
            1.35F, 0.75F,
            0.16F, 0.18F, 0.62F, 5.0F, 3.75F, 0.26F,
            0.42F, 6.0F, 0.68F, 0.20F, 0.035F, 0.075F,
            0.62F, 0.72F, 0.48F, 0.06F, 2, 8,
            true, 100, 6, 14, 6, 1, 12, 8.0F, 0.34F, 24,
            0.32F, 3,
            0.28F, 240, 10, 18, 8.0F,
            4.5F,
            10, 32, 6, 100,
            true, true, 18, 34, 0.08F, 0.14F,
            0.08F, 0.04F, 60, 100, 1.0F, 10, 6, 0.12F,
            0.58F, 44, 72, 10, 34, 18,
            0.40F, 0.72F, 0.018F, 0.036F);

    public AssimilationProfile {
        gainPerTick = Mth.clamp(gainPerTick, 0.00001F, 0.05F);
        immersionExponent = Mth.clamp(immersionExponent, 0.10F, 4.0F);
        minimumMoveScale = Mth.clamp(minimumMoveScale, 0.0F, 1.0F);
        minimumLookScale = Mth.clamp(minimumLookScale, 0.02F, 1.0F);
        minimumAnimationScale = Mth.clamp(minimumAnimationScale, 0.0F, 1.0F);
        screenOpacity = Mth.clamp(screenOpacity, 0.0F, 1.0F);
        blurStrength = Mth.clamp(blurStrength, 0.0F, 1.0F);
        shellGravity = Mth.clamp(shellGravity, 0.0F, 0.30F);
        shellAirDrag = Mth.clamp(shellAirDrag, 0.0F, 1.0F);
        shellGroundFriction = Mth.clamp(shellGroundFriction, 0.0F, 1.0F);
        shellRestitution = Mth.clamp(shellRestitution, 0.0F, 1.0F);
        shellMaximumSpeed = Mth.clamp(shellMaximumSpeed, 0.05F, 20.0F);
        shellMaximumTilt = Mth.clamp(shellMaximumTilt, 0.0F, 75.0F);
        shellTiltResponse = Mth.clamp(shellTiltResponse, 0.01F, 1.0F);
        shellTeleportReleaseDistance = Mth.clamp(shellTeleportReleaseDistance, 4.0F, 1024.0F);
        shellTransportHandoffTicks = Mth.clamp(shellTransportHandoffTicks, 0, 40);
        soulRadius = Mth.clamp(soulRadius, 4.0F, 128.0F);
        soulMoveSpeed = Mth.clamp(soulMoveSpeed, 0.02F, 2.0F);
        soulSprintMultiplier = Mth.clamp(soulSprintMultiplier, 1.0F, 4.0F);
        soulAcceleration = Mth.clamp(soulAcceleration, 0.05F, 1.0F);
        soulDrag = Mth.clamp(soulDrag, 0.0F, 0.99F);
        soulEmergenceBackOffset = Mth.clamp(soulEmergenceBackOffset, 0.0F, 6.0F);
        soulEmergenceUpOffset = Mth.clamp(soulEmergenceUpOffset, -2.0F, 6.0F);
        soulBaseEffect = Mth.clamp(soulBaseEffect, 0.0F, 1.0F);
        soulEffectStart = Mth.clamp(soulEffectStart, 0.0F, 0.95F);
        soulColorStrength = Mth.clamp(soulColorStrength, 0.0F, 1.0F);
        soulPixelSize = Mth.clamp(soulPixelSize, 1.0F, 16.0F);
        soulBlurRadius = Mth.clamp(soulBlurRadius, 0.0F, 8.0F);
        soulBaseFogStrength = Mth.clamp(soulBaseFogStrength, 0.0F, 1.0F);
        soulFogStart = Mth.clamp(soulFogStart, 0.0F, 0.95F);
        soulFogDistance = Mth.clamp(soulFogDistance, 1.0F, 64.0F);
        soulFogOpacity = Mth.clamp(soulFogOpacity, 0.0F, 1.0F);
        soulBoundarySoftness = Mth.clamp(soulBoundarySoftness, 0.0F, 0.75F);
        soulFloatAmplitude = Mth.clamp(soulFloatAmplitude, 0.0F, 0.25F);
        soulFloatSpeed = Mth.clamp(soulFloatSpeed, 0.005F, 0.5F);
        soulSoundDamping = Mth.clamp(soulSoundDamping, 0.0F, 1.0F);
        rescuePulseStrength = Mth.clamp(rescuePulseStrength, 0.0F, 1.0F);
        rescueCrackDarkness = Mth.clamp(rescueCrackDarkness, 0.0F, 1.0F);
        rescueDamagePerHit = Mth.clamp(rescueDamagePerHit, 0.005F, 1.0F);
        rescueRevealRadius = Mth.clamp(rescueRevealRadius, 0, 6);
        rescuePulseTicks = Mth.clamp(rescuePulseTicks, 1, 40);
        selfRescueQteTimeoutTicks = Mth.clamp(selfRescueQteTimeoutTicks, 10, 200);
        selfRescueQteNextDelayTicks = Mth.clamp(selfRescueQteNextDelayTicks, 0, 100);
        selfRescueQteFailureDelayTicks = Mth.clamp(selfRescueQteFailureDelayTicks, 0, 200);
        selfRescueQteRequiredStreak = Mth.clamp(selfRescueQteRequiredStreak, 1, 32);
        selfRescueQteRevealRadius = Mth.clamp(selfRescueQteRevealRadius, 0, 6);
        selfRescueQteFadeTicks = Mth.clamp(selfRescueQteFadeTicks, 1, 60);
        selfRescueQteAimDegrees = Mth.clamp(selfRescueQteAimDegrees, 2.0F, 30.0F);
        selfRescueQteHoldChance = Mth.clamp(selfRescueQteHoldChance, 0.0F, 1.0F);
        selfRescueQteHoldTicks = Mth.clamp(selfRescueQteHoldTicks, 4, 100);
        selfRescueQteRapidChance = Mth.clamp(selfRescueQteRapidChance, 0.0F, 1.0F);
        selfRescueQteRapidClicks = Mth.clamp(selfRescueQteRapidClicks, 2, 8);
        selfRescueQteTraceChance = Mth.clamp(selfRescueQteTraceChance, 0.0F, 1.0F);
        selfRescueQteTraceTimeoutTicks = Mth.clamp(selfRescueQteTraceTimeoutTicks, 20, 400);
        selfRescueQteTraceNodes = Mth.clamp(selfRescueQteTraceNodes, 2,
                AssimilationTracePattern.NODE_COUNT);
        selfRescueQteTraceSpacing = Mth.clamp(selfRescueQteTraceSpacing, 10, 32);
        selfRescueQteTraceHitRadius = Mth.clamp(selfRescueQteTraceHitRadius, 3.0F, 16.0F);
        selfRescueQteRange = Mth.clamp(selfRescueQteRange, 2.0F, 16.0F);
        soulTransitionTicks = Mth.clamp(soulTransitionTicks, 0, 100);
        restoreTicks = Mth.clamp(restoreTicks, 4, 200);
        restoreBlackoutFadeTicks = Mth.clamp(restoreBlackoutFadeTicks, 1, 100);
        rescueGraceTicks = Mth.clamp(rescueGraceTicks, 0, 1200);
        partialPurgeCursorMinOneWayTicks = Mth.clamp(
                partialPurgeCursorMinOneWayTicks, 6, 200);
        partialPurgeCursorMaxOneWayTicks = Mth.clamp(
                partialPurgeCursorMaxOneWayTicks,
                partialPurgeCursorMinOneWayTicks, 240);
        partialPurgeZoneMinWidth = Mth.clamp(partialPurgeZoneMinWidth, 0.04F, 0.80F);
        partialPurgeZoneMaxWidth = Mth.clamp(
                partialPurgeZoneMaxWidth, partialPurgeZoneMinWidth, 0.90F);
        partialPurgeSuccessAmount = Mth.clamp(partialPurgeSuccessAmount, 0.001F, 1.0F);
        partialPurgeFailureAmount = Mth.clamp(partialPurgeFailureAmount, 0.0F, 1.0F);
        partialPurgeSuccessWeaknessTicks = Mth.clamp(
                partialPurgeSuccessWeaknessTicks, 0, 1200);
        partialPurgeFailureWeaknessTicks = Mth.clamp(
                partialPurgeFailureWeaknessTicks, 0, 1200);
        partialPurgeFailureDamage = Mth.clamp(partialPurgeFailureDamage, 0.0F, 20.0F);
        partialPurgeRoundCooldownTicks = Mth.clamp(
                partialPurgeRoundCooldownTicks, 0, 100);
        partialPurgeSplashDroplets = Mth.clamp(partialPurgeSplashDroplets, 0, 24);
        partialPurgeSplashSpeed = Mth.clamp(partialPurgeSplashSpeed, 0.01F, 0.50F);
        screenCrackStartProgress = Mth.clamp(screenCrackStartProgress, 0.05F, 1.0F);
        screenCrackMinIntervalTicks = Mth.clamp(screenCrackMinIntervalTicks, 4, 600);
        screenCrackMaxIntervalTicks = Mth.clamp(
                screenCrackMaxIntervalTicks, screenCrackMinIntervalTicks, 1200);
        screenCrackFadeInTicks = Mth.clamp(screenCrackFadeInTicks, 1, 200);
        screenCrackHoldTicks = Mth.clamp(screenCrackHoldTicks, 1, 1200);
        screenCrackFadeOutTicks = Mth.clamp(screenCrackFadeOutTicks, 1, 400);
        screenCrackMinLength = Mth.clamp(screenCrackMinLength, 0.04F, 0.90F);
        screenCrackMaxLength = Mth.clamp(
                screenCrackMaxLength, screenCrackMinLength, 1.10F);
        screenCrackMinWidth = Mth.clamp(screenCrackMinWidth, 0.002F, 0.09F);
        screenCrackMaxWidth = Mth.clamp(
                screenCrackMaxWidth, screenCrackMinWidth, 0.12F);
    }

    public float movementScale(float assimilation) {
        return easedScale(assimilation, minimumMoveScale, 1.55F);
    }

    public float gainForImmersion(float immersion) {
        return gainPerTick * (float) Math.pow(
                Mth.clamp(immersion, 0.0F, 1.0F), immersionExponent);
    }

    public float lookScale(float assimilation) {
        return easedScale(assimilation, minimumLookScale, 0.78F);
    }

    public float animationScale(float assimilation) {
        return easedScale(assimilation, minimumAnimationScale, 1.45F);
    }

    public static AssimilationProfile fromValues(double[] values) {
        AssimilationProfile base = DEFAULT;
        return new AssimilationProfile(
                bool(values, MudPhysicsParameter.ASSIMILATION_ENABLED, base.enabled),
                value(values, MudPhysicsParameter.ASSIMILATION_GAIN_PER_TICK, base.gainPerTick),
                value(values, MudPhysicsParameter.ASSIMILATION_IMMERSION_EXPONENT, base.immersionExponent),
                value(values, MudPhysicsParameter.ASSIMILATION_MINIMUM_MOVE_SCALE, base.minimumMoveScale),
                value(values, MudPhysicsParameter.ASSIMILATION_MINIMUM_LOOK_SCALE, base.minimumLookScale),
                value(values, MudPhysicsParameter.ASSIMILATION_MINIMUM_ANIMATION_SCALE, base.minimumAnimationScale),
                value(values, MudPhysicsParameter.ASSIMILATION_SCREEN_OPACITY, base.screenOpacity),
                value(values, MudPhysicsParameter.ASSIMILATION_BLUR_STRENGTH, base.blurStrength),
                bool(values, MudPhysicsParameter.ASSIMILATION_ARMOR_ENABLED, base.armorEnabled),
                bool(values, MudPhysicsParameter.ASSIMILATION_ORDINARY_COVERAGE_ENABLED,
                        base.ordinaryCoverageEnabled),
                bool(values, MudPhysicsParameter.ASSIMILATION_FINAL_STASIS_ENABLED, base.finalStasisEnabled),
                bool(values, MudPhysicsParameter.ASSIMILATION_SHELL_PHYSICS_ENABLED, base.shellPhysicsEnabled),
                value(values, MudPhysicsParameter.ASSIMILATION_SHELL_GRAVITY, base.shellGravity),
                value(values, MudPhysicsParameter.ASSIMILATION_SHELL_AIR_DRAG, base.shellAirDrag),
                value(values, MudPhysicsParameter.ASSIMILATION_SHELL_GROUND_FRICTION, base.shellGroundFriction),
                value(values, MudPhysicsParameter.ASSIMILATION_SHELL_RESTITUTION, base.shellRestitution),
                value(values, MudPhysicsParameter.ASSIMILATION_SHELL_MAXIMUM_SPEED, base.shellMaximumSpeed),
                value(values, MudPhysicsParameter.ASSIMILATION_SHELL_MAXIMUM_TILT, base.shellMaximumTilt),
                value(values, MudPhysicsParameter.ASSIMILATION_SHELL_TILT_RESPONSE, base.shellTiltResponse),
                value(values, MudPhysicsParameter.ASSIMILATION_SHELL_TELEPORT_RELEASE_DISTANCE,
                        base.shellTeleportReleaseDistance),
                integer(values, MudPhysicsParameter.ASSIMILATION_SHELL_TRANSPORT_HANDOFF_TICKS,
                        base.shellTransportHandoffTicks),
                value(values, MudPhysicsParameter.ASSIMILATION_SOUL_RADIUS, base.soulRadius),
                value(values, MudPhysicsParameter.ASSIMILATION_SOUL_MOVE_SPEED, base.soulMoveSpeed),
                value(values, MudPhysicsParameter.ASSIMILATION_SOUL_SPRINT_MULTIPLIER,
                        base.soulSprintMultiplier),
                value(values, MudPhysicsParameter.ASSIMILATION_SOUL_ACCELERATION, base.soulAcceleration),
                value(values, MudPhysicsParameter.ASSIMILATION_SOUL_DRAG, base.soulDrag),
                value(values, MudPhysicsParameter.ASSIMILATION_SOUL_EMERGENCE_BACK_OFFSET,
                        base.soulEmergenceBackOffset),
                value(values, MudPhysicsParameter.ASSIMILATION_SOUL_EMERGENCE_UP_OFFSET,
                        base.soulEmergenceUpOffset),
                value(values, MudPhysicsParameter.ASSIMILATION_SOUL_BASE_EFFECT, base.soulBaseEffect),
                value(values, MudPhysicsParameter.ASSIMILATION_SOUL_EFFECT_START, base.soulEffectStart),
                value(values, MudPhysicsParameter.ASSIMILATION_SOUL_COLOR_STRENGTH, base.soulColorStrength),
                value(values, MudPhysicsParameter.ASSIMILATION_SOUL_PIXEL_SIZE, base.soulPixelSize),
                value(values, MudPhysicsParameter.ASSIMILATION_SOUL_BLUR_RADIUS, base.soulBlurRadius),
                value(values, MudPhysicsParameter.ASSIMILATION_SOUL_BASE_FOG_STRENGTH,
                        base.soulBaseFogStrength),
                value(values, MudPhysicsParameter.ASSIMILATION_SOUL_FOG_START, base.soulFogStart),
                value(values, MudPhysicsParameter.ASSIMILATION_SOUL_FOG_DISTANCE, base.soulFogDistance),
                value(values, MudPhysicsParameter.ASSIMILATION_SOUL_FOG_OPACITY, base.soulFogOpacity),
                value(values, MudPhysicsParameter.ASSIMILATION_SOUL_BOUNDARY_SOFTNESS,
                        base.soulBoundarySoftness),
                value(values, MudPhysicsParameter.ASSIMILATION_SOUL_FLOAT_AMPLITUDE,
                        base.soulFloatAmplitude),
                value(values, MudPhysicsParameter.ASSIMILATION_SOUL_FLOAT_SPEED, base.soulFloatSpeed),
                value(values, MudPhysicsParameter.ASSIMILATION_SOUL_SOUND_DAMPING, base.soulSoundDamping),
                value(values, MudPhysicsParameter.ASSIMILATION_RESCUE_PULSE_STRENGTH,
                        base.rescuePulseStrength),
                value(values, MudPhysicsParameter.ASSIMILATION_RESCUE_CRACK_DARKNESS,
                        base.rescueCrackDarkness),
                value(values, MudPhysicsParameter.ASSIMILATION_RESCUE_DAMAGE_PER_HIT,
                        base.rescueDamagePerHit),
                integer(values, MudPhysicsParameter.ASSIMILATION_RESCUE_REVEAL_RADIUS,
                        base.rescueRevealRadius),
                integer(values, MudPhysicsParameter.ASSIMILATION_RESCUE_PULSE_TICKS,
                        base.rescuePulseTicks),
                bool(values, MudPhysicsParameter.ASSIMILATION_SELF_RESCUE_QTE_ENABLED,
                        base.selfRescueQteEnabled),
                integer(values, MudPhysicsParameter.ASSIMILATION_SELF_RESCUE_QTE_TIMEOUT_TICKS,
                        base.selfRescueQteTimeoutTicks),
                integer(values, MudPhysicsParameter.ASSIMILATION_SELF_RESCUE_QTE_NEXT_DELAY_TICKS,
                        base.selfRescueQteNextDelayTicks),
                integer(values, MudPhysicsParameter.ASSIMILATION_SELF_RESCUE_QTE_FAILURE_DELAY_TICKS,
                        base.selfRescueQteFailureDelayTicks),
                integer(values, MudPhysicsParameter.ASSIMILATION_SELF_RESCUE_QTE_REQUIRED_STREAK,
                        base.selfRescueQteRequiredStreak),
                integer(values, MudPhysicsParameter.ASSIMILATION_SELF_RESCUE_QTE_REVEAL_RADIUS,
                        base.selfRescueQteRevealRadius),
                integer(values, MudPhysicsParameter.ASSIMILATION_SELF_RESCUE_QTE_FADE_TICKS,
                        base.selfRescueQteFadeTicks),
                value(values, MudPhysicsParameter.ASSIMILATION_SELF_RESCUE_QTE_AIM_DEGREES,
                        base.selfRescueQteAimDegrees),
                value(values, MudPhysicsParameter.ASSIMILATION_SELF_RESCUE_QTE_HOLD_CHANCE,
                        base.selfRescueQteHoldChance),
                integer(values, MudPhysicsParameter.ASSIMILATION_SELF_RESCUE_QTE_HOLD_TICKS,
                        base.selfRescueQteHoldTicks),
                value(values, MudPhysicsParameter.ASSIMILATION_SELF_RESCUE_QTE_RAPID_CHANCE,
                        base.selfRescueQteRapidChance),
                integer(values, MudPhysicsParameter.ASSIMILATION_SELF_RESCUE_QTE_RAPID_CLICKS,
                        base.selfRescueQteRapidClicks),
                value(values, MudPhysicsParameter.ASSIMILATION_SELF_RESCUE_QTE_TRACE_CHANCE,
                        base.selfRescueQteTraceChance),
                integer(values, MudPhysicsParameter.ASSIMILATION_SELF_RESCUE_QTE_TRACE_TIMEOUT_TICKS,
                        base.selfRescueQteTraceTimeoutTicks),
                integer(values, MudPhysicsParameter.ASSIMILATION_SELF_RESCUE_QTE_TRACE_NODES,
                        base.selfRescueQteTraceNodes),
                integer(values, MudPhysicsParameter.ASSIMILATION_SELF_RESCUE_QTE_TRACE_SPACING,
                        base.selfRescueQteTraceSpacing),
                value(values, MudPhysicsParameter.ASSIMILATION_SELF_RESCUE_QTE_TRACE_HIT_RADIUS,
                        base.selfRescueQteTraceHitRadius),
                value(values, MudPhysicsParameter.ASSIMILATION_SELF_RESCUE_QTE_RANGE,
                        base.selfRescueQteRange),
                integer(values, MudPhysicsParameter.ASSIMILATION_SOUL_TRANSITION_TICKS,
                        base.soulTransitionTicks),
                integer(values, MudPhysicsParameter.ASSIMILATION_RESTORE_TICKS, base.restoreTicks),
                integer(values, MudPhysicsParameter.ASSIMILATION_RESTORE_BLACKOUT_FADE_TICKS,
                        base.restoreBlackoutFadeTicks),
                integer(values, MudPhysicsParameter.ASSIMILATION_RESCUE_GRACE_TICKS,
                        base.rescueGraceTicks),
                bool(values, MudPhysicsParameter.ASSIMILATION_PARTIAL_PURGE_ENABLED,
                        base.partialPurgeEnabled),
                bool(values, MudPhysicsParameter.ASSIMILATION_PARTIAL_PURGE_CANCEL_ON_MOVE,
                        base.partialPurgeCancelOnMove),
                integer(values, MudPhysicsParameter.ASSIMILATION_PARTIAL_PURGE_CURSOR_MIN_ONE_WAY_TICKS,
                        base.partialPurgeCursorMinOneWayTicks),
                integer(values, MudPhysicsParameter.ASSIMILATION_PARTIAL_PURGE_CURSOR_MAX_ONE_WAY_TICKS,
                        base.partialPurgeCursorMaxOneWayTicks),
                value(values, MudPhysicsParameter.ASSIMILATION_PARTIAL_PURGE_ZONE_MIN_WIDTH,
                        base.partialPurgeZoneMinWidth),
                value(values, MudPhysicsParameter.ASSIMILATION_PARTIAL_PURGE_ZONE_MAX_WIDTH,
                        base.partialPurgeZoneMaxWidth),
                value(values, MudPhysicsParameter.ASSIMILATION_PARTIAL_PURGE_SUCCESS_AMOUNT,
                        base.partialPurgeSuccessAmount),
                value(values, MudPhysicsParameter.ASSIMILATION_PARTIAL_PURGE_FAILURE_AMOUNT,
                        base.partialPurgeFailureAmount),
                integer(values, MudPhysicsParameter.ASSIMILATION_PARTIAL_PURGE_SUCCESS_WEAKNESS_TICKS,
                        base.partialPurgeSuccessWeaknessTicks),
                integer(values, MudPhysicsParameter.ASSIMILATION_PARTIAL_PURGE_FAILURE_WEAKNESS_TICKS,
                        base.partialPurgeFailureWeaknessTicks),
                value(values, MudPhysicsParameter.ASSIMILATION_PARTIAL_PURGE_FAILURE_DAMAGE,
                        base.partialPurgeFailureDamage),
                integer(values, MudPhysicsParameter.ASSIMILATION_PARTIAL_PURGE_ROUND_COOLDOWN_TICKS,
                        base.partialPurgeRoundCooldownTicks),
                integer(values, MudPhysicsParameter.ASSIMILATION_PARTIAL_PURGE_SPLASH_DROPLETS,
                        base.partialPurgeSplashDroplets),
                value(values, MudPhysicsParameter.ASSIMILATION_PARTIAL_PURGE_SPLASH_SPEED,
                        base.partialPurgeSplashSpeed),
                value(values, MudPhysicsParameter.ASSIMILATION_SCREEN_CRACK_START_PROGRESS,
                        base.screenCrackStartProgress),
                integer(values, MudPhysicsParameter.ASSIMILATION_SCREEN_CRACK_MIN_INTERVAL_TICKS,
                        base.screenCrackMinIntervalTicks),
                integer(values, MudPhysicsParameter.ASSIMILATION_SCREEN_CRACK_MAX_INTERVAL_TICKS,
                        base.screenCrackMaxIntervalTicks),
                integer(values, MudPhysicsParameter.ASSIMILATION_SCREEN_CRACK_FADE_IN_TICKS,
                        base.screenCrackFadeInTicks),
                integer(values, MudPhysicsParameter.ASSIMILATION_SCREEN_CRACK_HOLD_TICKS,
                        base.screenCrackHoldTicks),
                integer(values, MudPhysicsParameter.ASSIMILATION_SCREEN_CRACK_FADE_OUT_TICKS,
                        base.screenCrackFadeOutTicks),
                value(values, MudPhysicsParameter.ASSIMILATION_SCREEN_CRACK_MIN_LENGTH,
                        base.screenCrackMinLength),
                value(values, MudPhysicsParameter.ASSIMILATION_SCREEN_CRACK_MAX_LENGTH,
                        base.screenCrackMaxLength),
                value(values, MudPhysicsParameter.ASSIMILATION_SCREEN_CRACK_MIN_WIDTH,
                        base.screenCrackMinWidth),
                value(values, MudPhysicsParameter.ASSIMILATION_SCREEN_CRACK_MAX_WIDTH,
                        base.screenCrackMaxWidth));
    }

    public void writeTo(double[] values) {
        put(values, MudPhysicsParameter.ASSIMILATION_ENABLED, enabled ? 1.0D : 0.0D);
        put(values, MudPhysicsParameter.ASSIMILATION_GAIN_PER_TICK, gainPerTick);
        put(values, MudPhysicsParameter.ASSIMILATION_IMMERSION_EXPONENT, immersionExponent);
        put(values, MudPhysicsParameter.ASSIMILATION_MINIMUM_MOVE_SCALE, minimumMoveScale);
        put(values, MudPhysicsParameter.ASSIMILATION_MINIMUM_LOOK_SCALE, minimumLookScale);
        put(values, MudPhysicsParameter.ASSIMILATION_MINIMUM_ANIMATION_SCALE, minimumAnimationScale);
        put(values, MudPhysicsParameter.ASSIMILATION_SCREEN_OPACITY, screenOpacity);
        put(values, MudPhysicsParameter.ASSIMILATION_BLUR_STRENGTH, blurStrength);
        put(values, MudPhysicsParameter.ASSIMILATION_ARMOR_ENABLED, armorEnabled ? 1.0D : 0.0D);
        put(values, MudPhysicsParameter.ASSIMILATION_ORDINARY_COVERAGE_ENABLED,
                ordinaryCoverageEnabled ? 1.0D : 0.0D);
        put(values, MudPhysicsParameter.ASSIMILATION_FINAL_STASIS_ENABLED,
                finalStasisEnabled ? 1.0D : 0.0D);
        put(values, MudPhysicsParameter.ASSIMILATION_SHELL_PHYSICS_ENABLED,
                shellPhysicsEnabled ? 1.0D : 0.0D);
        put(values, MudPhysicsParameter.ASSIMILATION_SHELL_GRAVITY, shellGravity);
        put(values, MudPhysicsParameter.ASSIMILATION_SHELL_AIR_DRAG, shellAirDrag);
        put(values, MudPhysicsParameter.ASSIMILATION_SHELL_GROUND_FRICTION, shellGroundFriction);
        put(values, MudPhysicsParameter.ASSIMILATION_SHELL_RESTITUTION, shellRestitution);
        put(values, MudPhysicsParameter.ASSIMILATION_SHELL_MAXIMUM_SPEED, shellMaximumSpeed);
        put(values, MudPhysicsParameter.ASSIMILATION_SHELL_MAXIMUM_TILT, shellMaximumTilt);
        put(values, MudPhysicsParameter.ASSIMILATION_SHELL_TILT_RESPONSE, shellTiltResponse);
        put(values, MudPhysicsParameter.ASSIMILATION_SHELL_TELEPORT_RELEASE_DISTANCE,
                shellTeleportReleaseDistance);
        put(values, MudPhysicsParameter.ASSIMILATION_SHELL_TRANSPORT_HANDOFF_TICKS,
                shellTransportHandoffTicks);
        put(values, MudPhysicsParameter.ASSIMILATION_SOUL_RADIUS, soulRadius);
        put(values, MudPhysicsParameter.ASSIMILATION_SOUL_MOVE_SPEED, soulMoveSpeed);
        put(values, MudPhysicsParameter.ASSIMILATION_SOUL_SPRINT_MULTIPLIER, soulSprintMultiplier);
        put(values, MudPhysicsParameter.ASSIMILATION_SOUL_ACCELERATION, soulAcceleration);
        put(values, MudPhysicsParameter.ASSIMILATION_SOUL_DRAG, soulDrag);
        put(values, MudPhysicsParameter.ASSIMILATION_SOUL_EMERGENCE_BACK_OFFSET,
                soulEmergenceBackOffset);
        put(values, MudPhysicsParameter.ASSIMILATION_SOUL_EMERGENCE_UP_OFFSET, soulEmergenceUpOffset);
        put(values, MudPhysicsParameter.ASSIMILATION_SOUL_BASE_EFFECT, soulBaseEffect);
        put(values, MudPhysicsParameter.ASSIMILATION_SOUL_EFFECT_START, soulEffectStart);
        put(values, MudPhysicsParameter.ASSIMILATION_SOUL_COLOR_STRENGTH, soulColorStrength);
        put(values, MudPhysicsParameter.ASSIMILATION_SOUL_PIXEL_SIZE, soulPixelSize);
        put(values, MudPhysicsParameter.ASSIMILATION_SOUL_BLUR_RADIUS, soulBlurRadius);
        put(values, MudPhysicsParameter.ASSIMILATION_SOUL_BASE_FOG_STRENGTH, soulBaseFogStrength);
        put(values, MudPhysicsParameter.ASSIMILATION_SOUL_FOG_START, soulFogStart);
        put(values, MudPhysicsParameter.ASSIMILATION_SOUL_FOG_DISTANCE, soulFogDistance);
        put(values, MudPhysicsParameter.ASSIMILATION_SOUL_FOG_OPACITY, soulFogOpacity);
        put(values, MudPhysicsParameter.ASSIMILATION_SOUL_BOUNDARY_SOFTNESS, soulBoundarySoftness);
        put(values, MudPhysicsParameter.ASSIMILATION_SOUL_FLOAT_AMPLITUDE, soulFloatAmplitude);
        put(values, MudPhysicsParameter.ASSIMILATION_SOUL_FLOAT_SPEED, soulFloatSpeed);
        put(values, MudPhysicsParameter.ASSIMILATION_SOUL_SOUND_DAMPING, soulSoundDamping);
        put(values, MudPhysicsParameter.ASSIMILATION_RESCUE_PULSE_STRENGTH, rescuePulseStrength);
        put(values, MudPhysicsParameter.ASSIMILATION_RESCUE_CRACK_DARKNESS, rescueCrackDarkness);
        put(values, MudPhysicsParameter.ASSIMILATION_RESCUE_DAMAGE_PER_HIT, rescueDamagePerHit);
        put(values, MudPhysicsParameter.ASSIMILATION_RESCUE_REVEAL_RADIUS, rescueRevealRadius);
        put(values, MudPhysicsParameter.ASSIMILATION_RESCUE_PULSE_TICKS, rescuePulseTicks);
        put(values, MudPhysicsParameter.ASSIMILATION_SELF_RESCUE_QTE_ENABLED,
                selfRescueQteEnabled ? 1.0D : 0.0D);
        put(values, MudPhysicsParameter.ASSIMILATION_SELF_RESCUE_QTE_TIMEOUT_TICKS,
                selfRescueQteTimeoutTicks);
        put(values, MudPhysicsParameter.ASSIMILATION_SELF_RESCUE_QTE_NEXT_DELAY_TICKS,
                selfRescueQteNextDelayTicks);
        put(values, MudPhysicsParameter.ASSIMILATION_SELF_RESCUE_QTE_FAILURE_DELAY_TICKS,
                selfRescueQteFailureDelayTicks);
        put(values, MudPhysicsParameter.ASSIMILATION_SELF_RESCUE_QTE_REQUIRED_STREAK,
                selfRescueQteRequiredStreak);
        put(values, MudPhysicsParameter.ASSIMILATION_SELF_RESCUE_QTE_REVEAL_RADIUS,
                selfRescueQteRevealRadius);
        put(values, MudPhysicsParameter.ASSIMILATION_SELF_RESCUE_QTE_FADE_TICKS,
                selfRescueQteFadeTicks);
        put(values, MudPhysicsParameter.ASSIMILATION_SELF_RESCUE_QTE_AIM_DEGREES,
                selfRescueQteAimDegrees);
        put(values, MudPhysicsParameter.ASSIMILATION_SELF_RESCUE_QTE_HOLD_CHANCE,
                selfRescueQteHoldChance);
        put(values, MudPhysicsParameter.ASSIMILATION_SELF_RESCUE_QTE_HOLD_TICKS,
                selfRescueQteHoldTicks);
        put(values, MudPhysicsParameter.ASSIMILATION_SELF_RESCUE_QTE_RAPID_CHANCE,
                selfRescueQteRapidChance);
        put(values, MudPhysicsParameter.ASSIMILATION_SELF_RESCUE_QTE_RAPID_CLICKS,
                selfRescueQteRapidClicks);
        put(values, MudPhysicsParameter.ASSIMILATION_SELF_RESCUE_QTE_TRACE_CHANCE,
                selfRescueQteTraceChance);
        put(values, MudPhysicsParameter.ASSIMILATION_SELF_RESCUE_QTE_TRACE_TIMEOUT_TICKS,
                selfRescueQteTraceTimeoutTicks);
        put(values, MudPhysicsParameter.ASSIMILATION_SELF_RESCUE_QTE_TRACE_NODES,
                selfRescueQteTraceNodes);
        put(values, MudPhysicsParameter.ASSIMILATION_SELF_RESCUE_QTE_TRACE_SPACING,
                selfRescueQteTraceSpacing);
        put(values, MudPhysicsParameter.ASSIMILATION_SELF_RESCUE_QTE_TRACE_HIT_RADIUS,
                selfRescueQteTraceHitRadius);
        put(values, MudPhysicsParameter.ASSIMILATION_SELF_RESCUE_QTE_RANGE,
                selfRescueQteRange);
        put(values, MudPhysicsParameter.ASSIMILATION_SOUL_TRANSITION_TICKS, soulTransitionTicks);
        put(values, MudPhysicsParameter.ASSIMILATION_RESTORE_TICKS, restoreTicks);
        put(values, MudPhysicsParameter.ASSIMILATION_RESTORE_BLACKOUT_FADE_TICKS,
                restoreBlackoutFadeTicks);
        put(values, MudPhysicsParameter.ASSIMILATION_RESCUE_GRACE_TICKS, rescueGraceTicks);
        put(values, MudPhysicsParameter.ASSIMILATION_PARTIAL_PURGE_ENABLED,
                partialPurgeEnabled ? 1.0D : 0.0D);
        put(values, MudPhysicsParameter.ASSIMILATION_PARTIAL_PURGE_CANCEL_ON_MOVE,
                partialPurgeCancelOnMove ? 1.0D : 0.0D);
        put(values, MudPhysicsParameter.ASSIMILATION_PARTIAL_PURGE_CURSOR_MIN_ONE_WAY_TICKS,
                partialPurgeCursorMinOneWayTicks);
        put(values, MudPhysicsParameter.ASSIMILATION_PARTIAL_PURGE_CURSOR_MAX_ONE_WAY_TICKS,
                partialPurgeCursorMaxOneWayTicks);
        put(values, MudPhysicsParameter.ASSIMILATION_PARTIAL_PURGE_ZONE_MIN_WIDTH,
                partialPurgeZoneMinWidth);
        put(values, MudPhysicsParameter.ASSIMILATION_PARTIAL_PURGE_ZONE_MAX_WIDTH,
                partialPurgeZoneMaxWidth);
        put(values, MudPhysicsParameter.ASSIMILATION_PARTIAL_PURGE_SUCCESS_AMOUNT,
                partialPurgeSuccessAmount);
        put(values, MudPhysicsParameter.ASSIMILATION_PARTIAL_PURGE_FAILURE_AMOUNT,
                partialPurgeFailureAmount);
        put(values, MudPhysicsParameter.ASSIMILATION_PARTIAL_PURGE_SUCCESS_WEAKNESS_TICKS,
                partialPurgeSuccessWeaknessTicks);
        put(values, MudPhysicsParameter.ASSIMILATION_PARTIAL_PURGE_FAILURE_WEAKNESS_TICKS,
                partialPurgeFailureWeaknessTicks);
        put(values, MudPhysicsParameter.ASSIMILATION_PARTIAL_PURGE_FAILURE_DAMAGE,
                partialPurgeFailureDamage);
        put(values, MudPhysicsParameter.ASSIMILATION_PARTIAL_PURGE_ROUND_COOLDOWN_TICKS,
                partialPurgeRoundCooldownTicks);
        put(values, MudPhysicsParameter.ASSIMILATION_PARTIAL_PURGE_SPLASH_DROPLETS,
                partialPurgeSplashDroplets);
        put(values, MudPhysicsParameter.ASSIMILATION_PARTIAL_PURGE_SPLASH_SPEED,
                partialPurgeSplashSpeed);
        put(values, MudPhysicsParameter.ASSIMILATION_SCREEN_CRACK_START_PROGRESS,
                screenCrackStartProgress);
        put(values, MudPhysicsParameter.ASSIMILATION_SCREEN_CRACK_MIN_INTERVAL_TICKS,
                screenCrackMinIntervalTicks);
        put(values, MudPhysicsParameter.ASSIMILATION_SCREEN_CRACK_MAX_INTERVAL_TICKS,
                screenCrackMaxIntervalTicks);
        put(values, MudPhysicsParameter.ASSIMILATION_SCREEN_CRACK_FADE_IN_TICKS,
                screenCrackFadeInTicks);
        put(values, MudPhysicsParameter.ASSIMILATION_SCREEN_CRACK_HOLD_TICKS,
                screenCrackHoldTicks);
        put(values, MudPhysicsParameter.ASSIMILATION_SCREEN_CRACK_FADE_OUT_TICKS,
                screenCrackFadeOutTicks);
        put(values, MudPhysicsParameter.ASSIMILATION_SCREEN_CRACK_MIN_LENGTH,
                screenCrackMinLength);
        put(values, MudPhysicsParameter.ASSIMILATION_SCREEN_CRACK_MAX_LENGTH,
                screenCrackMaxLength);
        put(values, MudPhysicsParameter.ASSIMILATION_SCREEN_CRACK_MIN_WIDTH,
                screenCrackMinWidth);
        put(values, MudPhysicsParameter.ASSIMILATION_SCREEN_CRACK_MAX_WIDTH,
                screenCrackMaxWidth);
    }

    private static float value(double[] values, MudPhysicsParameter parameter, float fallback) {
        return values != null && parameter.ordinal() < values.length
                ? (float) values[parameter.ordinal()] : fallback;
    }

    private static int integer(double[] values, MudPhysicsParameter parameter, int fallback) {
        return values != null && parameter.ordinal() < values.length
                ? (int) Math.round(values[parameter.ordinal()]) : fallback;
    }

    private static boolean bool(double[] values, MudPhysicsParameter parameter, boolean fallback) {
        return values != null && parameter.ordinal() < values.length
                ? values[parameter.ordinal()] >= 0.5D : fallback;
    }

    private static void put(double[] values, MudPhysicsParameter parameter, double value) {
        if (values != null && parameter.ordinal() < values.length) {
            values[parameter.ordinal()] = parameter.sanitize(value);
        }
    }

    private static float easedScale(float assimilation, float minimum, float exponent) {
        float amount = (float) Math.pow(Mth.clamp(assimilation, 0.0F, 1.0F), exponent);
        return Mth.lerp(amount, 1.0F, minimum);
    }
}

package com.fish.mirebound.assimilation;

import static com.fish.mirebound.physics.MudMovementControl.clearAssimilationMovementSpeed;

import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.adaptive.MudVisualPalette;
import com.fish.mirebound.splash.MudSplashSystem;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

/** Owns the optional partial-assimilation rejection timing challenge. */
final class AssimilationPurgeSystem {
    private static final double MOVEMENT_CANCEL_DISTANCE_SQUARED = 0.0001D;

    private AssimilationPurgeSystem() {
    }

    static void toggle(ServerPlayer player) {
        AssimilationState state = AssimilationStateStore.state(player);
        AssimilationProfile profile = AssimilationSystem.profileFor(state);
        if ((long) player.tickCount - state.lastPartialPurgeToggleTick < 4L) {
            return;
        }
        state.lastPartialPurgeToggleTick = player.tickCount;
        if (state.frozen() || state.progress >= 0.9999F) {
            state.clearPartialPurge();
            player.displayClientMessage(Component.translatable(
                    "message.mirebound.assimilation.purge.sealed"), true);
            AssimilationSystem.sync(player, state, profile, true);
            return;
        }
        if (AssimilationSystem.hasActiveAssimilationMudContact(player)) {
            state.clearPartialPurge();
            player.displayClientMessage(Component.translatable(
                    "message.mirebound.assimilation.purge.blocked"), true);
            AssimilationSystem.sync(player, state, profile, true);
            return;
        }
        if (!profile.partialPurgeEnabled()
                || state.stage != AssimilationStage.ASSIMILATING
                || state.progress <= 0.0001F) {
            state.clearPartialPurge();
            player.displayClientMessage(Component.translatable(
                    "message.mirebound.assimilation.purge.none"), true);
            AssimilationSystem.sync(player, state, profile, true);
            return;
        }
        if (state.partialPurgeActive) {
            state.clearPartialPurge();
        } else {
            state.partialPurgeActive = true;
            state.partialPurgeOrigin = player.position();
            state.partialPurgeWasCrouching = player.isShiftKeyDown();
            rollRound(player, state, profile, true);
            player.displayClientMessage(Component.translatable(
                    "message.mirebound.assimilation.purge.start"), true);
        }
        AssimilationSystem.sync(player, state, profile, true);
    }

    static void update(ServerPlayer player, AssimilationState state,
            AssimilationProfile profile) {
        if (!state.partialPurgeActive) {
            return;
        }
        if (stopIfBlocked(player, state)) {
            return;
        }
        if (profile.partialPurgeCancelOnMove()
                && movedFromOrigin(state.partialPurgeOrigin, player.position())) {
            state.clearPartialPurge();
            return;
        }
        if (!profile.partialPurgeEnabled()
                || state.stage != AssimilationStage.ASSIMILATING
                || state.progress <= 0.0001F || player.isDeadOrDying()) {
            state.clearPartialPurge();
            return;
        }
        if (state.partialPurgeResultTicks > 0) {
            state.partialPurgeResultTicks--;
        } else {
            state.partialPurgeResult = AssimilationPartialPurge.RESULT_NONE;
        }
        boolean crouching = player.isShiftKeyDown();
        if (state.partialPurgeCooldownTicks > 0) {
            state.partialPurgeCooldownTicks--;
            if (state.partialPurgeCooldownTicks == 0) {
                rollRound(player, state, profile, false);
            }
        } else {
            AssimilationPartialPurge.Cursor cursor = AssimilationPartialPurge.advance(
                    state.partialPurgeCursor, state.partialPurgeCursorForward,
                    state.partialPurgeCursorOneWayTicks);
            state.partialPurgeCursor = cursor.position();
            state.partialPurgeCursorForward = cursor.forward();
            if (crouching && !state.partialPurgeWasCrouching) {
                judge(player, state, profile);
            }
        }
        state.partialPurgeWasCrouching = crouching;
    }

    static boolean stopIfBlocked(ServerPlayer player, AssimilationState state) {
        if (!state.partialPurgeActive
                || !AssimilationSystem.hasActiveAssimilationMudContact(player)) {
            return false;
        }
        state.clearPartialPurge();
        player.displayClientMessage(Component.translatable(
                "message.mirebound.assimilation.purge.blocked"), true);
        return true;
    }

    static boolean cancelForMovement(
            AssimilationState state, AssimilationProfile profile) {
        if (!state.partialPurgeActive || !profile.partialPurgeCancelOnMove()) {
            return false;
        }
        state.clearPartialPurge();
        return true;
    }

    static boolean movedFromOrigin(Vec3 origin, Vec3 position) {
        return origin != null && position != null
                && origin.distanceToSqr(position) > MOVEMENT_CANCEL_DISTANCE_SQUARED;
    }

    private static void judge(ServerPlayer player, AssimilationState state,
            AssimilationProfile profile) {
        boolean success = AssimilationPartialPurge.succeeds(
                state.partialPurgeCursor, state.partialPurgeZoneStart,
                state.partialPurgeZoneEnd);
        state.partialPurgeCooldownTicks = profile.partialPurgeRoundCooldownTicks();
        state.partialPurgeResultTicks = Math.max(
                6, profile.partialPurgeRoundCooldownTicks());
        if (success) {
            state.partialPurgeResult = AssimilationPartialPurge.RESULT_SUCCESS;
            MudVisualPalette expelledVisuals = state.visualPalette.copy();
            SinkingMedium expelledFallback = state.medium;
            state.removeContributions(profile.partialPurgeSuccessAmount());
            applyWeakness(player, profile.partialPurgeSuccessWeaknessTicks());
            MudSplashSystem.spawnPurge(
                    player, expelledVisuals, expelledFallback,
                    profile.partialPurgeSplashDroplets(),
                    profile.partialPurgeSplashSpeed());
            player.serverLevel().playSound(null, player.blockPosition(),
                    SoundEvents.SLIME_BLOCK_BREAK, SoundSource.PLAYERS,
                    0.45F, 1.18F);
            if (state.progress <= 0.0001F) {
                state.reset(0);
                clearAssimilationMovementSpeed(player);
                player.displayClientMessage(Component.translatable(
                        "message.mirebound.assimilation.purge.clear"), true);
            }
            return;
        }

        state.partialPurgeResult = AssimilationPartialPurge.RESULT_FAILURE;
        state.addContribution(state.medium, profile.partialPurgeFailureAmount());
        if (!player.getAbilities().invulnerable) {
            player.setHealth(AssimilationPartialPurge.nonLethalHealth(
                    player.getHealth(), profile.partialPurgeFailureDamage()));
        }
        applyWeakness(player, profile.partialPurgeFailureWeaknessTicks());
        player.serverLevel().playSound(null, player.blockPosition(),
                SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 0.50F, 0.82F);
        if (state.progress >= 0.9999F && profile.finalStasisEnabled()) {
            state.seal(player.position(), AssimilationSystem.dimensionId(player),
                    player.getYRot(), player.getXRot(),
                    player.walkAnimation.position(1.0F),
                    player.walkAnimation.speed(1.0F));
            state.qteCooldownTicks = profile.soulTransitionTicks()
                    + profile.selfRescueQteNextDelayTicks();
            AssimilationSystem.initializeFrozenBody(player, state);
            state.soulPosition = player.getEyePosition();
            state.soulPositionTick = player.tickCount;
            AssimilationSystem.playSealSound(player);
            AssimilationSystem.sync(player, state, profile, true);
        }
    }

    private static void rollRound(ServerPlayer player, AssimilationState state,
            AssimilationProfile profile, boolean resetCursor) {
        state.partialPurgeRound++;
        float width = Mth.lerp(
                player.getRandom().nextFloat(),
                profile.partialPurgeZoneMinWidth(),
                profile.partialPurgeZoneMaxWidth());
        float start = player.getRandom().nextFloat()
                * Math.max(0.0F, 1.0F - width);
        state.partialPurgeZoneStart = start;
        state.partialPurgeZoneEnd = start + width;
        state.partialPurgeCursorOneWayTicks = Mth.nextInt(
                player.getRandom(),
                profile.partialPurgeCursorMinOneWayTicks(),
                profile.partialPurgeCursorMaxOneWayTicks());
        if (resetCursor) {
            state.partialPurgeCursor =
                    player.getRandom().nextBoolean() ? 0.0F : 1.0F;
            state.partialPurgeCursorForward = state.partialPurgeCursor <= 0.0F;
        }
        state.partialPurgeResult = AssimilationPartialPurge.RESULT_NONE;
        state.partialPurgeResultTicks = 0;
    }

    private static void applyWeakness(ServerPlayer player, int ticks) {
        if (ticks > 0) {
            player.addEffect(new MobEffectInstance(
                    MobEffects.WEAKNESS, ticks, 0, false, true, true));
        }
    }
}

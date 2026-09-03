package com.fish.mirebound.mud;

import com.fish.mirebound.network.payload.SculkClampStatePayload;
import com.fish.mirebound.mud.behavior.DisturbanceSinkTemplate;
import com.fish.mirebound.mud.behavior.HiddenThreatTemplate;
import com.fish.mirebound.mud.behavior.MudActivityTemplate;
import com.fish.mirebound.mud.behavior.QuietCrouchEscapeTemplate;
import com.fish.mirebound.mud.behavior.ResonancePulseTemplate;
import com.fish.mirebound.mud.behavior.TimedRestraintTemplate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Owns the input-sensitive sculk mire rules and their server-side presentation. */
public final class SculkMireMechanics {
    private SculkMireMechanics() {
    }

    public static StepResult step(SculkMireProfile profile, SculkMireRuntimeState state, Input input) {
        MudActivityTemplate.Sample activity = MudActivityTemplate.sample(
                profile, input.movementStrength(), input.lookDelta(),
                input.jumpIntent(), input.crouching());
        if (!state.sunk() && activity.active() && !input.crouching()) {
            state.escape().enter();
        }
        QuietCrouchEscapeTemplate.Decision quiet = QuietCrouchEscapeTemplate.step(
                profile, state.escape(), input.depth(), input.crouching(), activity.active());

        if (state.clampActive()) {
            TimedRestraintTemplate.Result restraint = TimedRestraintTemplate.step(
                    profile, state.restraint(), activity.resisting());
            boolean emitNoise = ResonancePulseTemplate.step(
                    profile, state.resonance(), activity.resisting());
            if (restraint.released()) {
                return new StepResult(0.0D, 0.0D, 0.0D, 0.0D,
                        false, false, true, false, true);
            }
            return new StepResult(0.0D, 0.0D, 0.0D, 0.0D,
                    activity.resisting(), false, false, emitNoise, true);
        }

        // A crouching player who has not crossed the entry depth can pass over the
        // surface. Once sunk, crouch is only safe while completely still.
        boolean calming = input.crouching() && !activity.active();
        HiddenThreatTemplate.step(profile, state.threat(), false, 0.0D, calming);
        if (quiet.mode() == QuietCrouchEscapeTemplate.Mode.SURFACE_PASS) {
            return new StepResult(
                    input.originalMotionX() * profile.sneakWalkScale(),
                    0.0D,
                    input.originalMotionZ() * profile.sneakWalkScale(),
                    profile.sneakWalkScale(), false, false, false, false, true);
        }

        if (quiet.mode() == QuietCrouchEscapeTemplate.Mode.WAITING
                || quiet.mode() == QuietCrouchEscapeTemplate.Mode.RISING) {
            return new StepResult(0.0D, quiet.upwardSpeed(), 0.0D,
                    profile.sneakWalkScale(), false, false, false, false, true);
        }

        boolean disturbed = activity.active() && state.sunk();
        boolean emitNoise = ResonancePulseTemplate.step(profile, state.resonance(), disturbed);
        boolean threshold = HiddenThreatTemplate.step(
                profile, state.threat(), disturbed, activity.strength(), false);
        if (threshold && state.sunk()) {
            state.threat().clear();
            TimedRestraintTemplate.start(profile, state.restraint());
            return new StepResult(0.0D, 0.0D, 0.0D, 0.0D,
                    true, true, false, emitNoise, true);
        }

        double y = input.baselineMotionY();
        if (disturbed) {
            y = DisturbanceSinkTemplate.apply(
                    input.baselineMotionY(), input.remainingDepth(),
                    activity.strength(), profile.actionSinkBoost());
        }
        return new StepResult(input.baselineMotionX(), y, input.baselineMotionZ(),
                input.baselineWalkScale(), disturbed, false,
                false, emitNoise, false);
    }

    public static void updateFrame(SculkMireRuntimeState state, MudPhysics.ClientSurfaceContact contact) {
        if (contact != null) {
            state.setSurfaceFrame(contact.surfacePoint(), contact.surfaceNormal(),
                    contact.surfaceAxisX(), contact.surfaceAxisZ());
        }
    }

    public static void updateFrame(SculkMireRuntimeState state, Vec3 point, Vec3 normal,
            Vec3 axisX, Vec3 axisZ) {
        state.setSurfaceFrame(point, normal, axisX, axisZ);
    }

    public static void emitResonance(ServerPlayer player, SculkMireProfile profile,
            SculkMireRuntimeState state) {
        player.serverLevel().gameEvent(null, GameEvent.ENTITY_ACTION, player.position());
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.SCULK_BLOCK_CHARGE,
                SoundSource.BLOCKS, 0.42F, 0.72F + player.getRandom().nextFloat() * 0.20F);
    }

    public static void syncClamp(ServerPlayer player, SculkMireProfile profile,
            SculkMireRuntimeState state, boolean active, long visualSource) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                new SculkClampStatePayload(
                        player.getId(), active,
                        state.surfacePoint(), state.surfaceNormal(), state.surfaceAxisX(), state.surfaceAxisZ(),
                        visualSource,
                        (float) profile.clampRadius(), (float) profile.clampHeight(),
                        (float) profile.clampRenderDistance(),
                        profile.clampEmergeTicks(), profile.clampRetractTicks(),
                        profile.clampCloseTicks(), profile.clampOpenTicks(),
                        state.clampTicks(), profile.clampMaximumTicks()));
    }

    public record Input(
            double depth,
            double remainingDepth,
            double originalMotionX,
            double originalMotionY,
            double originalMotionZ,
            double baselineMotionX,
            double baselineMotionY,
            double baselineMotionZ,
            double baselineWalkScale,
            double movementStrength,
            double lookDelta,
            boolean jumpIntent,
            boolean crouching) {
    }

    public record StepResult(
            double motionX,
            double motionY,
            double motionZ,
            double walkScale,
            boolean action,
            boolean clampStarted,
            boolean clampReleased,
            boolean emitNoise,
            boolean resetSettlingVelocity) {
    }
}

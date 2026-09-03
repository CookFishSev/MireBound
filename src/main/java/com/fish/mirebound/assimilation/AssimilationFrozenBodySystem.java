package com.fish.mirebound.assimilation;

import static com.fish.mirebound.physics.MudMovementControl.clearAssimilationMovementSpeed;

import com.fish.mirebound.compat.sable.SableCompat;
import com.fish.mirebound.mud.MudPhysics;
import com.fish.mirebound.physics.PlayerGravityControl;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

/** Owns sealed-body transport, rigid motion, gravity, and release handoff. */
final class AssimilationFrozenBodySystem {
    private static final double POSITION_EPSILON_SQR = 1.0E-8D;

    private AssimilationFrozenBodySystem() {
    }

    static void initialize(ServerPlayer player, AssimilationState state) {
        PlayerGravityControl.acquire(player, PlayerGravityControl.Owner.ASSIMILATION);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        player.setPos(state.anchor.x, state.anchor.y, state.anchor.z);
        player.setYRot(state.frozenYaw);
        player.setXRot(state.frozenPitch);
        player.setShiftKeyDown(false);
        player.setSprinting(false);
    }

    static boolean update(ServerPlayer player, AssimilationState state,
            AssimilationProfile profile) {
        PlayerGravityControl.acquire(player, PlayerGravityControl.Owner.ASSIMILATION);
        player.setShiftKeyDown(false);
        player.setSprinting(false);
        player.setYRot(state.frozenYaw);
        player.setXRot(state.frozenPitch);
        player.fallDistance = 0.0F;

        Vec3 current = player.position();
        if (player.isPassenger()) {
            Vec3 carriedMotion = current.subtract(state.anchor);
            state.anchor = current;
            state.rigidVelocity = Vec3.ZERO;
            state.carriedLastTick = true;
            state.transportHandoffTicks = profile.shellTransportHandoffTicks();
            updateTilt(state, profile, carriedMotion, false);
            state.dimension = AssimilationSystem.dimensionId(player);
            state.dirty |= carriedMotion.lengthSqr() > POSITION_EPSILON_SQR;
            player.setDeltaMovement(Vec3.ZERO);
            return true;
        }

        if (state.carriedLastTick) {
            state.anchor = current;
            state.rigidVelocity = Vec3.ZERO;
            state.carriedLastTick = false;
            state.transportHandoffTicks = profile.shellTransportHandoffTicks();
            state.dirty = true;
        }
        if (state.transportHandoffTicks > 0) {
            Vec3 inheritedMotion = current.subtract(state.anchor);
            state.anchor = current;
            state.rigidVelocity = Vec3.ZERO;
            state.transportHandoffTicks--;
            updateTilt(state, profile, inheritedMotion, false);
            state.dirty |= inheritedMotion.lengthSqr() > POSITION_EPSILON_SQR;
            player.setDeltaMovement(Vec3.ZERO);
            return true;
        }

        if (SableCompat.isTracking(player)) {
            Vec3 inheritedMotion = current.subtract(state.anchor);
            state.anchor = current;
            updateTilt(state, profile, inheritedMotion, false);
            state.dirty |= inheritedMotion.lengthSqr() > POSITION_EPSILON_SQR;
        } else {
            double limit = profile.shellTeleportReleaseDistance();
            if (current.distanceToSqr(state.anchor) > limit * limit) {
                return false;
            }
            if (current.distanceToSqr(state.anchor) > POSITION_EPSILON_SQR) {
                player.setPos(state.anchor.x, state.anchor.y, state.anchor.z);
            }
        }

        if (!profile.shellPhysicsEnabled()) {
            boolean changed = state.rigidVelocity.lengthSqr() > POSITION_EPSILON_SQR
                    || Math.abs(state.bodyPitch) > 1.0E-4F
                    || Math.abs(state.bodyRoll) > 1.0E-4F;
            state.rigidVelocity = Vec3.ZERO;
            state.bodyPitch = 0.0F;
            state.bodyRoll = 0.0F;
            state.dirty |= changed;
            player.refreshDimensions();
            player.setDeltaMovement(Vec3.ZERO);
            return true;
        }

        if (MudPhysics.hasSinkingContact(player)) {
            state.rigidVelocity = Vec3.ZERO;
            state.anchor = player.position();
            player.setDeltaMovement(Vec3.ZERO);
            return true;
        }

        Vec3 requested = AssimilationRigidBodyMotion.integrate(
                state.rigidVelocity, profile.shellGravity(),
                profile.shellAirDrag(), profile.shellMaximumSpeed());
        Vec3 beforeMove = player.position();
        player.move(MoverType.SELF, requested);
        Vec3 actual = player.position().subtract(beforeMove);
        AssimilationRigidBodyMotion.CollisionResult collision =
                AssimilationRigidBodyMotion.resolveCollision(
                        requested, actual, profile.shellRestitution(),
                        profile.shellGroundFriction());
        state.rigidVelocity = collision.velocity();
        state.anchor = player.position();
        updateTilt(state, profile, state.rigidVelocity,
                collision.blockedY() && requested.y < 0.0D);
        state.dirty |= actual.lengthSqr() > POSITION_EPSILON_SQR
                || state.rigidVelocity.lengthSqr() > POSITION_EPSILON_SQR;
        player.setDeltaMovement(Vec3.ZERO);
        return true;
    }

    static void release(ServerPlayer player, AssimilationState state,
            int graceTicks, boolean returnToBody) {
        boolean hadFrozenBody = state.frozen();
        Vec3 bodyAnchor = state.anchor;
        float bodyYaw = state.frozenYaw;
        float bodyPitch = state.frozenPitch;
        PlayerGravityControl.release(player, PlayerGravityControl.Owner.ASSIMILATION);
        clearAssimilationMovementSpeed(player);
        player.refreshDimensions();
        if (returnToBody && hadFrozenBody
                && AssimilationSystem.sameDimension(player, state)
                && finite(bodyAnchor)) {
            SableCompat.clearEntityTracking(player);
            player.connection.teleport(
                    bodyAnchor.x, bodyAnchor.y, bodyAnchor.z,
                    bodyYaw, bodyPitch);
            player.setDeltaMovement(Vec3.ZERO);
        }
        state.reset(graceTicks);
        state.lastSyncSignature = Long.MIN_VALUE;
        player.serverLevel().playSound(null, player.blockPosition(),
                SoundEvents.HONEY_BLOCK_BREAK, SoundSource.PLAYERS,
                0.72F, 1.18F);
    }

    private static void updateTilt(AssimilationState state,
            AssimilationProfile profile, Vec3 motion, boolean grounded) {
        AssimilationRigidBodyMotion.Tilt tilt =
                AssimilationRigidBodyMotion.updateTilt(
                        state.bodyPitch, state.bodyRoll, motion, state.frozenYaw,
                        profile.shellMaximumSpeed(), profile.shellMaximumTilt(),
                        profile.shellTiltResponse(), grounded);
        state.bodyPitch = tilt.pitch();
        state.bodyRoll = tilt.roll();
    }

    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x)
                && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
}

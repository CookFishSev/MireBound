package com.fish.mirebound.mud;

import static com.fish.mirebound.physics.MudMovementControl.clearMudMovement;
import static com.fish.mirebound.physics.MudMovementControl.updateMudMovementSpeed;

import com.fish.mirebound.compat.sable.SableCompat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Client prediction state, HUD projections, and Sable wake bookkeeping. */
final class MudClientPhysics {
    private static final Map<Integer, State> STATES = new HashMap<>();
    private static final Map<Integer, Integer> SABLE_WAKE_TICKS = new HashMap<>();
    private static final Set<Integer> ASSIMILATION_SUSPENDED_PLAYERS = new HashSet<>();
    private static final Set<Integer> EXTERNAL_CAMERA_SUSPENDED_PLAYERS = new HashSet<>();

    private MudClientPhysics() {
    }

    static void wakeSable(Player player) {
        SABLE_WAKE_TICKS.put(player.getId(), player.tickCount);
    }

    static int queueStruggle(Player player, int chargeTicks) {
        if (player.isSpectator() || isSuspended(player)) {
            clear(player);
            return 0;
        }
        State state = state(player);
        state.pendingStruggleCharge = Mth.clamp(
                chargeTicks / (double) MudStruggleTiming.MAX_CHARGE_TICKS, 0.0D, 1.0D);
        return MudStruggleTiming.cooldownTicks(
                chargeTicks, MudStruggleTiming.configuredMaximumCooldown(
                        player.level(), state.physicsProfilePos, state.physicsMedium));
    }

    static void updateInput(Player player, boolean jumping) {
        if (player.isSpectator() || isSuspended(player)) {
            return;
        }
        state(player).jumpingInput = jumping;
    }

    static void reset() {
        STATES.clear();
        SABLE_WAKE_TICKS.clear();
        ASSIMILATION_SUSPENDED_PLAYERS.clear();
        EXTERNAL_CAMERA_SUSPENDED_PLAYERS.clear();
    }

    static boolean isSinking(Player player) {
        if (player.isSpectator() || isSuspended(player)) {
            return false;
        }
        State state = STATES.get(player.getId());
        return isFresh(player, state) && state.inContact;
    }

    static boolean isInMedium(Player player, SinkingMedium medium) {
        if (isSuspended(player)) {
            return false;
        }
        State state = STATES.get(player.getId());
        return isFresh(player, state) && state.inContact
                && state.physicsMedium == medium;
    }

    static boolean isInSculk(Player player) {
        State state = STATES.get(player.getId());
        return isFresh(player, state) && state.inContact
                && MudBehaviorContext.sculk(
                        player.level(), state.physicsProfilePos, state.physicsMedium);
    }

    static boolean enclosureActive(Player player) {
        State state = STATES.get(player.getId());
        return state != null && state.tenderFleshState.enclosureActive
                && !state.tenderFleshState.enclosureRetreating;
    }

    static float tenderEscapeOpportunity(Player player) {
        State state = STATES.get(player.getId());
        return !isInTenderFlesh(player, state)
                ? 0.0F : (float) state.tenderFleshState.escapeOpportunity;
    }

    static float tenderReleaseThreshold(Player player) {
        State state = STATES.get(player.getId());
        if (!isInTenderFlesh(player, state)) {
            return (float) MudPhysicsProfiles.tenderFlesh(player)
                    .releaseOpportunityThreshold();
        }
        return (float) MudPlayerMovement.resolveTenderFleshProfile(
                player, state.physicsProfilePos, state.physicsMedium)
                .releaseOpportunityThreshold();
    }

    static float tenderContraction(Player player) {
        State state = STATES.get(player.getId());
        return !isInTenderFlesh(player, state)
                ? 0.0F : (float) state.tenderFleshState.contraction;
    }

    static float tenderWrap(Player player) {
        State state = STATES.get(player.getId());
        return !isInTenderFlesh(player, state)
                ? 0.0F : (float) state.tenderFleshState.wrap;
    }

    static float tenderPressure(Player player) {
        State state = STATES.get(player.getId());
        return !isInTenderFlesh(player, state)
                ? 0.0F : (float) state.tenderFleshState.pressure;
    }

    static float tenderCalmness(Player player) {
        State state = STATES.get(player.getId());
        return !isInTenderFlesh(player, state)
                ? 0.0F : (float) state.tenderFleshState.calmness;
    }

    static float sculkEscapeProgress(Player player) {
        State state = STATES.get(player.getId());
        if (!isFresh(player, state) || !state.inContact
                || !MudBehaviorContext.sculk(
                        player.level(), state.physicsProfilePos, state.physicsMedium)
                || !state.sculkMireState.sunk()
                || state.sculkMireState.clampActive()) {
            return -1.0F;
        }
        SculkMireProfile profile = MudPlayerMovement.resolveSculkMireProfile(
                player, state.physicsProfilePos, state.physicsMedium);
        return SculkMireHudProgress.escape(
                state.sculkMireState.quietCrouchTicks(),
                profile.quietCrouchDelayTicks());
    }

    static boolean sculkClampLocked(Player player) {
        State state = STATES.get(player.getId());
        return state != null && state.sculkMireState.clampActive();
    }

    static void updateSculkClamp(
            int entityId, boolean active, int remainingTicks) {
        STATES.computeIfAbsent(entityId, ignored -> new State())
                .sculkMireState.forceClamp(active, remainingTicks);
    }

    static void updateTenderEnclosure(
            int entityId, boolean active, boolean retreating,
            int brokenMask, int pillarDamagePacked, int pillarRequiredHitsPacked,
            int cooldownTicks, float progress,
            double anchorX, double anchorY, double anchorZ,
            double playerX, double playerZ) {
        TenderFleshRuntimeState state = STATES
                .computeIfAbsent(entityId, ignored -> new State())
                .tenderFleshState;
        state.enclosureActive = active;
        state.enclosureRetreating = retreating;
        state.enclosureBrokenMask = brokenMask & 0x0F;
        state.enclosurePillarDamagePacked = pillarDamagePacked & 0x0FFF;
        state.enclosurePillarRequiredHitsPacked = pillarRequiredHitsPacked & 0x0FFF;
        state.enclosureCooldownTicks = Math.max(0, cooldownTicks);
        state.enclosureCenterX = anchorX;
        state.enclosureCenterY = anchorY;
        state.enclosureCenterZ = anchorZ;
        state.enclosureCenterSet = active || retreating;
        state.enclosurePlayerX = playerX;
        state.enclosurePlayerZ = playerZ;
        state.enclosurePlayerCenterSet = active || retreating;
        double targetProgress = Mth.clamp(progress, 0.0F, 1.0F);
        state.enclosureProgress =
                Math.abs(state.enclosureProgress - targetProgress) > 0.18D
                        ? targetProgress
                        : Mth.lerp(0.45D, state.enclosureProgress, targetProgress);
        if (!active && !retreating && targetProgress <= 1.0E-4D) {
            state.enclosureCenterSet = false;
            state.enclosurePlayerCenterSet = false;
        }
    }

    static MudPhysics.ClientSurfaceContact surfaceContact(Player player) {
        if (player.isSpectator() || isSuspended(player)) {
            return null;
        }
        State state = STATES.get(player.getId());
        if (!isFresh(player, state) || !state.inContact
                || state.surfacePoint == null || state.surfaceNormal == null) {
            return null;
        }
        Vec3 motion = player.getDeltaMovement();
        double horizontalSpeed = Math.sqrt(
                motion.x * motion.x + motion.z * motion.z);
        return new MudPhysics.ClientSurfaceContact(
                state.medium, state.surfacePoint, state.surfaceNormal,
                state.surfaceAxisX, state.surfaceAxisZ,
                state.surfaceProfilePos, state.depth, state.availableDepth,
                state.agitation, horizontalSpeed, state.walkScale,
                state.sableContact, state.clipNegativeX, state.clipPositiveX,
                state.clipNegativeZ, state.clipPositiveZ);
    }

    static void tick(Player player) {
        if (isSuspended(player)) {
            clear(player);
            return;
        }
        if (player.isSpectator()) {
            clear(player);
            return;
        }
        State state = state(player);
        if (state.tenderFleshState.enclosureActive
                && !state.tenderFleshState.enclosureRetreating) {
            TenderFleshEnclosureSystem.suppressFlight(player);
            TenderFleshEnclosureSystem.anchorPlayer(
                    player, state.tenderFleshState);
        }
        int sableWakeTick = SABLE_WAKE_TICKS.getOrDefault(
                player.getId(), Integer.MIN_VALUE / 2);
        boolean includeSable = state.sableContact
                || SableCompat.isTracking(player)
                || player.tickCount - sableWakeTick <= 2;
        MudContact contact = MudContactResolver.findPlayerContact(
                player, includeSable);
        if (contact != null
                && state.tenderFleshState.enclosureActive
                && !state.tenderFleshState.enclosureRetreating
                && !MudBehaviorContext.tenderFlesh(
                        player.level(), contact.physicsProfilePos(),
                        contact.physicsMedium())) {
            anchorClientEnclosure(player, state);
            return;
        }
        if (contact == null) {
            if (state.tenderFleshState.enclosureActive
                    && !state.tenderFleshState.enclosureRetreating
                    && state.tenderFleshState.enclosurePlayerCenterSet) {
                TenderFleshEnclosureSystem.suppressFlight(player);
                anchorClientEnclosure(player, state);
                state.inContact = false;
                return;
            }
            MudVolumeSnapshot snapshot =
                    MudVolumeContactResolver.nearbySnapshot(player, includeSable);
            VolumePhysicsContact volumeContact =
                    MudVolumeContactResolver.findPhysicsContact(player, snapshot);
            if (volumeContact == null || player.isSpectator()) {
                clearMudMovement(player);
            } else {
                MudVolumeContactResolver.applyResistance(player, volumeContact);
                state.lastContactTick = player.tickCount;
            }
            boolean wasInContact = state.inContact;
            state.inContact = false;
            state.sableContact = false;
            state.pendingStruggleCharge = -1.0D;
            state.liftTicks = 0;
            state.settlingVelocity = 0.0D;
            state.physicsProfilePos = null;
            state.sculkMireState.reset();
            state.tenderFleshState.reset();
            state.livingSlimeState.detach();
            if (wasInContact && player.level().noCollision(
                    player, player.getBoundingBox().move(0.0D, -0.045D, 0.0D))) {
                player.setOnGround(false);
            }
            if (player.tickCount - state.lastContactTick > 20) {
                STATES.remove(player.getId());
                SABLE_WAKE_TICKS.remove(player.getId());
            }
            return;
        }

        state.inContact = true;
        state.sableContact = contact.sableContext() != null;
        state.lastContactTick = player.tickCount;
        state.medium = contact.medium();
        state.physicsMedium = contact.physicsMedium();
        state.depth = contact.depth();
        state.availableDepth = contact.availableDepth();
        state.surfacePoint = contact.surfacePoint();
        state.surfaceNormal = contact.surfaceNormal();
        state.surfaceAxisX = contact.surfaceAxisX();
        state.surfaceAxisZ = contact.surfaceAxisZ();
        state.surfaceProfilePos = contact.surfaceProfilePos();
        state.physicsProfilePos = contact.physicsProfilePos();
        boolean sculkEnabled = MudBehaviorContext.sculk(
                player.level(), contact.physicsProfilePos(), contact.physicsMedium());
        boolean fleshEnabled = MudBehaviorContext.tenderFlesh(
                player.level(), contact.physicsProfilePos(), contact.physicsMedium());
        if (sculkEnabled) {
            SculkMireMechanics.updateFrame(
                    state.sculkMireState, contact.surfacePoint(),
                    contact.surfaceNormal(), contact.surfaceAxisX(),
                    contact.surfaceAxisZ());
        } else {
            state.sculkMireState.reset();
        }
        if (!fleshEnabled
                && !state.tenderFleshState.enclosureActive
                && !state.tenderFleshState.enclosureRetreating) {
            state.tenderFleshState.reset();
        }
        state.clipNegativeX = contact.clipNegativeX();
        state.clipPositiveX = contact.clipPositiveX();
        state.clipNegativeZ = contact.clipNegativeZ();
        state.clipPositiveZ = contact.clipPositiveZ();
        if (MudPlayerMovement.correctZeroDepthPenetration(player, contact)) {
            state.settlingVelocity = 0.0D;
        }
        if (contact.sableContext() == null) {
            MudPlayerMovement.applyClientPlayerMovement(
                    player, contact.state(), contact.depth(),
                    contact.depthFactor(), contact.horizontalCoverage(),
                    contact.availableDepth(),
                    contact.layerTopDepth(), contact.layerDepth(), contact.hasDeeperLayer(),
                    contact.physicsMedium(), contact.physicsProfilePos(), state);
        } else {
            MudPlayerMovement.applyClientSablePlayerMovement(player, contact, state);
        }
        if (contact.physicsMedium() != SinkingMedium.LIVING_SLIME
                && player.getDeltaMovement().y <= 0.0D) {
            player.setOnGround(true);
        }
    }

    static void clear(Player player) {
        clearMudMovement(player);
        STATES.remove(player.getId());
        SABLE_WAKE_TICKS.remove(player.getId());
    }

    static void setSuspended(Player player, boolean suspended) {
        setSuspended(ASSIMILATION_SUSPENDED_PLAYERS, player, suspended);
    }

    static void setExternalCameraSuspended(Player player, boolean suspended) {
        setSuspended(EXTERNAL_CAMERA_SUSPENDED_PLAYERS, player, suspended);
    }

    private static void setSuspended(
            Set<Integer> owner, Player player, boolean suspended) {
        if (suspended) {
            owner.add(player.getId());
            clear(player);
        } else {
            owner.remove(player.getId());
        }
    }

    private static boolean isSuspended(Player player) {
        int entityId = player.getId();
        return suspendedBy(
                ASSIMILATION_SUSPENDED_PLAYERS.contains(entityId),
                EXTERNAL_CAMERA_SUSPENDED_PLAYERS.contains(entityId));
    }

    static boolean suspendedBy(boolean assimilation, boolean externalCamera) {
        return assimilation || externalCamera;
    }

    static State state(Player player) {
        return STATES.computeIfAbsent(player.getId(), ignored -> new State());
    }

    private static void anchorClientEnclosure(Player player, State state) {
        Vec3 motion = player.getDeltaMovement();
        TenderFleshEnclosureSystem.anchorPlayer(
                player, state.tenderFleshState);
        player.setDeltaMovement(
                0.0D,
                TenderFleshEnclosureSystem.clampVerticalMotion(
                        player, state.tenderFleshState, motion.y),
                0.0D);
        updateMudMovementSpeed(player, 0.0D);
        state.lastContactTick = player.tickCount;
    }

    private static boolean isFresh(Player player, State state) {
        return state != null && player.tickCount - state.lastContactTick <= 2;
    }

    static boolean isInTenderFlesh(Player player) {
        return isInTenderFlesh(player, STATES.get(player.getId()));
    }

    private static boolean isInTenderFlesh(Player player, State state) {
        return isFresh(player, state) && state.inContact
                && MudBehaviorContext.tenderFlesh(
                        player.level(), state.physicsProfilePos, state.physicsMedium);
    }

    static final class State {
        final LivingSlimeRuntimeState livingSlimeState =
                new LivingSlimeRuntimeState();
        final SculkMireRuntimeState sculkMireState =
                new SculkMireRuntimeState();
        final TenderFleshRuntimeState tenderFleshState =
                new TenderFleshRuntimeState();
        double pendingStruggleCharge = -1.0D;
        double settlingVelocity;
        float agitation;
        float lastYaw;
        float lastPitch;
        int liftTicks;
        int lastContactTick;
        boolean hasLookSample;
        boolean inContact;
        boolean sableContact;
        boolean jumpingInput;
        SinkingMedium medium = SinkingMedium.MUD;
        SinkingMedium physicsMedium = SinkingMedium.MUD;
        double depth;
        double availableDepth;
        double walkScale = 1.0D;
        Vec3 surfacePoint;
        Vec3 surfaceNormal;
        Vec3 surfaceAxisX;
        Vec3 surfaceAxisZ;
        BlockPos surfaceProfilePos;
        BlockPos physicsProfilePos;
        double clipNegativeX = Double.POSITIVE_INFINITY;
        double clipPositiveX = Double.POSITIVE_INFINITY;
        double clipNegativeZ = Double.POSITIVE_INFINITY;
        double clipPositiveZ = Double.POSITIVE_INFINITY;

        double lookDelta(Player player) {
            float yaw = player.getYRot();
            float pitch = player.getXRot();
            if (!hasLookSample) {
                lastYaw = yaw;
                lastPitch = pitch;
                hasLookSample = true;
                return 0.0D;
            }
            float yawDelta = Mth.wrapDegrees(yaw - lastYaw);
            float pitchDelta = pitch - lastPitch;
            lastYaw = yaw;
            lastPitch = pitch;
            return Math.sqrt(yawDelta * yawDelta + pitchDelta * pitchDelta);
        }
    }
}

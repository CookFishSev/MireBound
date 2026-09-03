package com.fish.mirebound.mud;

import static com.fish.mirebound.physics.MudMovementControl.clearMudMovement;
import static com.fish.mirebound.physics.MudMovementControl.restoreMudGravity;

import com.fish.mirebound.coverage.MudCoverageService;
import net.minecraft.server.level.ServerPlayer;

/** Freecam/spectator pollution leases and contact-state suppression. */
final class MudPollutionSuppression {
    private MudPollutionSuppression() {
    }

    static void set(ServerPlayer player, boolean suppressed) {
        MudPlayerData data = MudStateStore.get(player);
        data.clientPollutionSuppressed = suppressed;
        data.clientPollutionSuppressedUntilTick = suppressed
                ? player.tickCount + 60
                : Integer.MIN_VALUE;
        if (suppressed) {
            suspend(player, data);
        }
    }

    static boolean isSuppressed(ServerPlayer player) {
        return isSuppressed(player, MudStateStore.get(player));
    }

    static boolean isSuppressed(
            ServerPlayer player, MudPlayerData data) {
        if (player.isSpectator()) {
            return true;
        }
        if (data.clientPollutionSuppressed
                && leaseExpired(player.tickCount, data.clientPollutionSuppressedUntilTick)) {
            data.clientPollutionSuppressed = false;
            data.clientPollutionSuppressedUntilTick = Integer.MIN_VALUE;
        }
        return data.clientPollutionSuppressed;
    }

    static boolean leaseExpired(int currentTick, int deadlineTick) {
        return (long) currentTick - (long) deadlineTick > 0L;
    }

    static void suspend(ServerPlayer player, MudPlayerData data) {
        boolean leavingTenderFlesh = data.tenderFleshState.enclosureActive
                || data.tenderFleshState.enclosureRetreating
                || data.physicsProfilePos != null && MudBehaviorContext.tenderFlesh(
                        player.level(), data.physicsProfilePos, data.physicsMedium);
        if (data.sculkMireState.sunk() || data.sculkMireState.clampActive()) {
            MudPlayerPhysicsController.leaveSculkMire(player, data);
        }
        clearMudMovement(player);
        if (data.inMud || data.gravityOverrideActive) {
            restoreMudGravity(player, data);
        }
        data.inMud = false;
        data.eyeSubmerged = false;
        data.depth = 0.0D;
        data.visionObstruction = 0.0F;
        data.clearVisionCoverage();
        data.stuckTicks = 0;
        MudPlayerPhysicsController.clearStruggleState(data);
        data.hasLookSample = false;
        data.clearArmorContacts();
        data.resetPhysicsState();
        data.resetFootprintTracking();
        data.resetLivingSlimeState();
        if (leavingTenderFlesh) {
            TenderFleshEnclosureSystem.sync(player, data, true);
        }
        MudCoverageSampler.syncDebug(player, data, false);
        MudCoverageService.sync(player, data, false);
    }

    static void maintainSurfacePass(ServerPlayer player, MudPlayerData data) {
        data.lastProcessedTick = player.tickCount;
        data.lastMudTick = player.tickCount;
        data.inMud = true;
        data.eyeSubmerged = false;
        data.visionObstruction = 0.0F;
        data.clearVisionCoverage();
        data.clearArmorContacts();
        data.stuckTicks = 0;
        data.resetFootprintTracking();
        MudCoverageSampler.recoverAir(player);
    }

}

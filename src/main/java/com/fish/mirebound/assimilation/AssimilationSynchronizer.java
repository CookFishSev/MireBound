package com.fish.mirebound.assimilation;

import com.fish.mirebound.network.payload.AssimilationStatePayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Owns assimilation state diffing, throttling, and payload encoding. */
final class AssimilationSynchronizer {
    private static final int SYNC_INTERVAL_TICKS = 2;

    private AssimilationSynchronizer() {
    }

    static void sync(ServerPlayer player, AssimilationState state,
            AssimilationProfile profile, boolean immediate) {
        boolean templateChanged =
                !state.templateId.equals(state.lastSyncedTemplateId);
        boolean profileChanged = !profile.equals(state.lastSyncedProfile);
        long signature = state.syncSignature();
        if (!immediate && !profileChanged && signature == state.lastSyncSignature
                && (long) player.tickCount - state.lastSyncTick < 20L) {
            return;
        }
        if (!immediate
                && (long) player.tickCount - state.lastSyncTick
                        < SYNC_INTERVAL_TICKS) {
            return;
        }
        state.lastSyncTick = player.tickCount;
        state.lastSyncSignature = signature;
        state.lastSyncedTemplateId = state.templateId;
        state.lastSyncedProfile = profile;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                player,
                payload(player, state, profile,
                        immediate || templateChanged || profileChanged));
    }

    static AssimilationStatePayload payload(ServerPlayer player,
            AssimilationState state, AssimilationProfile profile,
            boolean includeProfile) {
        return new AssimilationStatePayload(
                player.getId(), state.stage, state.progress, state.shellIntegrity,
                state.restoringTicks, state.anchor.x, state.anchor.y, state.anchor.z,
                state.frozenYaw, state.frozenPitch, state.bodyPitch, state.bodyRoll,
                state.frozenWalkPosition, state.frozenWalkSpeed,
                state.patternSeed, state.medium.id(), state.packedContributions(),
                state.visualPalette.packNetworkEntries(),
                state.visualPalette.packVisualSources(),
                state.revealBytes(), state.qteCell, state.qteButton,
                state.qteAction, state.qteRapidClicks, state.qteTraceProgress,
                state.qteTicksRemaining, state.qteStreak, state.qteSequence,
                state.partialPurgeActive, state.partialPurgeZoneStart,
                state.partialPurgeZoneEnd, state.partialPurgeCursor,
                state.partialPurgeCursorForward,
                state.partialPurgeCursorOneWayTicks,
                state.partialPurgeCooldownTicks, state.partialPurgeResult,
                state.partialPurgeResultTicks, state.partialPurgeRound,
                includeProfile ? profile : null);
    }
}

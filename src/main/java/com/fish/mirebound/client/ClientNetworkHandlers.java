package com.fish.mirebound.client;

import com.fish.mirebound.client.swarm.ClientSwarmState;
import com.fish.mirebound.client.tentacle.ClientTentacleManager;
import com.fish.mirebound.network.payload.MudCoverageDeltaPayload;
import com.fish.mirebound.network.payload.MudCoverageSyncPayload;
import com.fish.mirebound.network.payload.MudDebugSyncPayload;
import com.fish.mirebound.mud.MudPhysicsProfiles;
import com.fish.mirebound.network.payload.MudPhysicsProfileSyncPayload;
import com.fish.mirebound.network.payload.MudProbeBubblePayload;
import com.fish.mirebound.network.payload.SwarmStateSyncPayload;
import com.fish.mirebound.network.payload.SculkClampStatePayload;
import com.fish.mirebound.network.payload.TentacleStateSyncPayload;
import com.fish.mirebound.network.payload.RopeSnapshotPayload;
import com.fish.mirebound.network.payload.RopeInteractionReleasePayload;
import com.fish.mirebound.network.payload.MudSplashPayload;
import com.fish.mirebound.network.payload.MudClodScreenImpactPayload;
import com.fish.mirebound.network.payload.DroppedItemMudStatePayload;
import com.fish.mirebound.network.payload.EntityMudCoveragePayload;
import com.fish.mirebound.client.itemphysics.DroppedItemPresentation;
import com.fish.mirebound.client.entitycoverage.ClientEntityMudCoverage;
import com.fish.mirebound.client.entitycoverage.EntityMudTextureCache;
import com.fish.mirebound.client.compat.SableAdaptiveMudInvalidationQueue;
import com.fish.mirebound.client.tuning.MudTuningClientState;
import com.fish.mirebound.client.tuning.MudTuningInputController;
import com.fish.mirebound.network.payload.MudSurfaceImpactPayload;
import com.fish.mirebound.network.payload.MudEruptionVentPayload;
import com.fish.mirebound.client.eruption.ClientMudEruptionManager;
import com.fish.mirebound.network.payload.MudTuningSelectionPayload;
import com.fish.mirebound.network.payload.MudTuningSessionPayload;
import com.fish.mirebound.network.payload.MudTuningGlobalSettingsPayload;
import com.fish.mirebound.network.payload.MudTuningConversionSafetyPayload;
import com.fish.mirebound.client.tuning.MudTuningWandSettingsScreen;
import com.fish.mirebound.client.generation.MudTerrainGenerationScreen;
import com.fish.mirebound.network.payload.MudTuningWandBeamPayload;
import com.fish.mirebound.network.payload.MudTuningWandPulsePayload;
import com.fish.mirebound.network.payload.MudLocalProfilesPayload;
import com.fish.mirebound.network.payload.MudFlowVisualPayload;
import com.fish.mirebound.network.payload.AdaptiveMudSourcesPayload;
import com.fish.mirebound.network.payload.AdaptiveMudProfileSyncPayload;
import com.fish.mirebound.adaptive.AdaptiveMudBehaviorSettings;
import com.fish.mirebound.network.payload.WaterGunStreamPayload;
import com.fish.mirebound.network.payload.WaterGunProfileSyncPayload;
import com.fish.mirebound.network.payload.TenderFleshEnclosurePayload;
import com.fish.mirebound.network.payload.AssimilationStatePayload;
import com.fish.mirebound.mud.MudLocalProfileCache;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;
import java.util.HashSet;
import java.util.Set;

public final class ClientNetworkHandlers {
    private ClientNetworkHandlers() {
    }

    public static void handleCoverageSync(MudCoverageSyncPayload payload) {
        ClientMudState.setCoverageFromServer(payload);
    }

    public static void handleAssimilationState(AssimilationStatePayload payload) {
        ClientAssimilationState.accept(payload);
    }

    public static void handleCoverageDelta(MudCoverageDeltaPayload payload) {
        ClientMudState.applyCoverageDeltaFromServer(payload);
    }

    public static void handleDebugSync(MudDebugSyncPayload payload) {
        ClientMudDebugState.set(payload);
    }

    public static void handlePhysicsProfileSync(MudPhysicsProfileSyncPayload payload) {
        boolean coverageAppearanceChanged = MudPhysicsProfiles.acceptClientProfile(
                payload.medium(), payload.values());
        if (coverageAppearanceChanged) {
            MudSurfaceAppearance.reset();
            MudSkinTextureCache.reset();
            MudCapeTextureCache.reset();
            ArmorMudTextureCache.reset();
            ArmorMudCompositeTextureCache.reset();
        }
    }

    public static void handleMudTuningSelection(MudTuningSelectionPayload payload) {
        MudTuningClientState.accept(payload);
    }

    public static void handleMudTuningWandBeam(MudTuningWandBeamPayload payload) {
        MudTuningWandClientEffects.accept(payload);
    }

    public static void handleMudTuningWandPulse(MudTuningWandPulsePayload payload) {
        MudTuningWandClientEffects.accept(payload);
    }

    public static void handleMudTuningSession(MudTuningSessionPayload payload) {
        MudPhysicsTuningScreen.open(payload);
    }

    public static void handleMudTuningGlobalSettings(MudTuningGlobalSettingsPayload payload) {
        switch (MudTuningClientState.consumeGlobalScreen()) {
            case SETTINGS -> MudTuningWandSettingsScreen.open(payload);
            case GENERATION -> MudTerrainGenerationScreen.open(payload);
        }
    }

    public static void handleMudTuningConversionSafety(
            MudTuningConversionSafetyPayload payload) {
        MudTuningInputController.acceptConversionSafety(payload);
    }

    public static void handleMudLocalProfiles(MudLocalProfilesPayload payload) {
        MudLocalProfileCache.accept(payload);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null
                && minecraft.level.dimension().location().equals(payload.dimension())) {
            MudTuningSectionHighlightCache.invalidateChunk(
                    payload.chunkX(), payload.chunkZ());
        }
    }

    public static void handleMudFlowVisual(MudFlowVisualPayload payload) {
        MudFlowClientManager.accept(payload);
    }

    public static void handleAdaptiveMudSources(AdaptiveMudSourcesPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        var update = AdaptiveMudClientCache.accept(payload, minecraft.level);
        MudRenderedSurfaceGeometry.invalidate(update.dirtyPositions());
        SableAdaptiveMudInvalidationQueue.schedule(
                payload.dimension(), update.dirtyPositions());
        if (minecraft.level != null
                && minecraft.level.dimension().location().equals(payload.dimension())) {
            Set<Long> dirtySections = new HashSet<>();
            for (BlockPos pos : update.dirtyPositions()) {
                MudTuningSectionHighlightCache.invalidate(pos);
                dirtySections.add(SectionPos.asLong(pos));
                minecraft.level.getLightEngine().checkBlock(pos);
            }
            for (long section : dirtySections) {
                minecraft.levelRenderer.setSectionDirtyWithNeighbors(
                        SectionPos.x(section), SectionPos.y(section), SectionPos.z(section));
            }
        }
    }

    public static void handleAdaptiveMudProfile(AdaptiveMudProfileSyncPayload payload) {
        AdaptiveMudBehaviorSettings.acceptClient(payload.values());
        invalidateAdaptiveAppearanceCaches();
    }

    private static void invalidateAdaptiveAppearanceCaches() {
        MudSurfaceAppearance.reset();
        MudSkinTextureCache.reset();
        MudCapeTextureCache.reset();
        ArmorMudTextureCache.reset();
        ArmorMudCompositeTextureCache.reset();
        MudFootprintTextureCache.reset();
        MudWallTextureCache.reset();
        ScreenMudOverlay.reset();
        EntityMudTextureCache.reset();
    }

    public static void handleSwarmStateSync(SwarmStateSyncPayload payload) {
        ClientSwarmState.setTarget(
                payload.strength(),
                payload.hasProfilePos() ? BlockPos.of(payload.profilePos()) : null,
                com.fish.mirebound.mud.SinkingMedium.byId(payload.mediumId()));
    }

    public static void handleSculkClampState(SculkClampStatePayload payload) {
        ClientSculkClampManager.accept(payload);
        com.fish.mirebound.mud.MudPhysics.updateClientSculkClampState(
                payload.entityId(), payload.active(), payload.remainingTicks());
    }

    public static void handleTentacleStateSync(TentacleStateSyncPayload payload) {
        ClientTentacleManager.accept(payload);
    }

    public static void handleRopeSnapshot(RopeSnapshotPayload payload) {
        com.fish.mirebound.client.rope.ClientRopes.accept(payload);
    }

    public static void handleRopeInteractionRelease(
            RopeInteractionReleasePayload payload) {
        com.fish.mirebound.client.rope.ClientRopes.releaseFromServer(payload);
    }

    public static void handleTenderFleshEnclosure(TenderFleshEnclosurePayload payload) {
        MudSurfaceEffectManager.acceptTenderFleshEnclosure(payload);
        com.fish.mirebound.mud.MudPhysics.updateClientTenderFleshEnclosureState(
                payload.entityId(), payload.active(), payload.retreating(),
                payload.brokenMask(), payload.pillarDamagePacked(),
                payload.pillarRequiredHitsPacked(), payload.cooldownTicks(), payload.progress(),
                payload.anchorX(), payload.anchorY(), payload.anchorZ(),
                payload.playerX(), payload.playerZ());
    }

    public static void handleMudSplash(MudSplashPayload payload) {
        MudSplashClientManager.accept(payload);
    }

    public static void handleMudClodScreenImpact(
            MudClodScreenImpactPayload payload) {
        ScreenMudOverlay.acceptMudClodImpact(payload);
    }

    public static void handleDroppedItemMudState(DroppedItemMudStatePayload payload) {
        DroppedItemPresentation.accept(payload);
    }

    public static void handleEntityMudCoverage(EntityMudCoveragePayload payload) {
        ClientEntityMudCoverage.accept(payload);
    }

    public static void handleMudSurfaceImpact(MudSurfaceImpactPayload payload) {
        MudSurfaceEffectManager.acceptImpact(payload);
    }

    public static void handleMudEruptionVent(MudEruptionVentPayload payload) {
        ClientMudEruptionManager.accept(payload);
    }

    public static void handleMudProbeBubble(MudProbeBubblePayload payload) {
        for (MudProbeBubblePayload.BubbleSpawn bubble : payload.bubbles()) {
            MudSurfaceEffectManager.scheduleProbeBubble(
                    bubble.point(),
                    payload.normal(),
                    payload.tangent(),
                    payload.medium(),
                    payload.profilePos(),
                    bubble.delayTicks());
        }
    }

    public static void handleWaterGunStream(WaterGunStreamPayload payload) {
        WaterGunStreamClientManager.accept(payload);
    }

    public static void handleWaterGunProfile(WaterGunProfileSyncPayload payload) {
        WaterGunStreamClientManager.acceptProfile(payload);
    }
}

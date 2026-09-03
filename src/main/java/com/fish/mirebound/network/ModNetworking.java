package com.fish.mirebound.network;

import com.fish.mirebound.client.ClientNetworkHandlers;
import com.fish.mirebound.mud.MudPhysics;
import com.fish.mirebound.mud.PhysicsTraceLog;
import com.fish.mirebound.network.payload.MudCoverageDeltaPayload;
import com.fish.mirebound.network.payload.MudCoverageSyncPayload;
import com.fish.mirebound.network.payload.MudDebugSyncPayload;
import com.fish.mirebound.network.payload.MudDeveloperOptionsPayload;
import com.fish.mirebound.network.payload.MudStrugglePayload;
import com.fish.mirebound.network.payload.MudSplashPayload;
import com.fish.mirebound.network.payload.MudClodScreenImpactPayload;
import com.fish.mirebound.network.payload.DroppedItemMudStatePayload;
import com.fish.mirebound.network.payload.EntityMudCoveragePayload;
import com.fish.mirebound.network.payload.MudSurfaceImpactPayload;
import com.fish.mirebound.network.payload.MudEruptionVentPayload;
import com.fish.mirebound.network.payload.MudProbeBubblePayload;
import com.fish.mirebound.network.payload.MudViewModePayload;
import com.fish.mirebound.network.payload.MudPhysicsProfileSyncPayload;
import com.fish.mirebound.network.payload.ArmorTextureContactPayload;
import com.fish.mirebound.network.payload.PlayerGeometryPayload;
import com.fish.mirebound.network.payload.WaterGunFirePayload;
import com.fish.mirebound.network.payload.WaterGunStreamPayload;
import com.fish.mirebound.network.payload.WaterGunProfileSyncPayload;
import com.fish.mirebound.network.payload.SwarmStateSyncPayload;
import com.fish.mirebound.network.payload.SculkClampStatePayload;
import com.fish.mirebound.network.payload.SculkMireInputPayload;
import com.fish.mirebound.network.payload.TentacleStateSyncPayload;
import com.fish.mirebound.network.payload.RopeSnapshotPayload;
import com.fish.mirebound.network.payload.RopeDragPayload;
import com.fish.mirebound.network.payload.RopeAnchorPayload;
import com.fish.mirebound.network.payload.RopeBreakPayload;
import com.fish.mirebound.network.payload.RopeExtendPayload;
import com.fish.mirebound.network.payload.RopeConnectPayload;
import com.fish.mirebound.network.payload.RopeRescueCastPayload;
import com.fish.mirebound.network.payload.RopeRescueHaulPayload;
import com.fish.mirebound.network.payload.RopeClimbInputPayload;
import com.fish.mirebound.network.payload.RopeInteractionReleasePayload;
import com.fish.mirebound.network.payload.TenderFleshEnclosurePayload;
import com.fish.mirebound.network.payload.TenderFleshStrikePayload;
import com.fish.mirebound.network.payload.AssimilationStatePayload;
import com.fish.mirebound.network.payload.AssimilationQteInputPayload;
import com.fish.mirebound.network.payload.AssimilationQteTracePayload;
import com.fish.mirebound.network.payload.AssimilationSoulPositionPayload;
import com.fish.mirebound.network.payload.AssimilationPurgeInputPayload;
import com.fish.mirebound.assimilation.AssimilationSystem;
import com.fish.mirebound.network.payload.MudTuningApplyPayload;
import com.fish.mirebound.network.payload.MudTuningRequestPayload;
import com.fish.mirebound.network.payload.MudTuningSelectionNudgePayload;
import com.fish.mirebound.network.payload.MudTuningSelectionPayload;
import com.fish.mirebound.network.payload.MudTuningSessionPayload;
import com.fish.mirebound.network.payload.MudTuningWandBeamPayload;
import com.fish.mirebound.network.payload.MudTuningWandPulsePayload;
import com.fish.mirebound.network.payload.MudTuningGlobalRequestPayload;
import com.fish.mirebound.network.payload.MudTuningGlobalSettingsPayload;
import com.fish.mirebound.network.payload.MudTerrainGenerationPayload;
import com.fish.mirebound.network.payload.MudTuningConversionSafetyPayload;
import com.fish.mirebound.network.payload.MudTuningConversionUnlockPayload;
import com.fish.mirebound.network.payload.MudLocalProfilesPayload;
import com.fish.mirebound.network.payload.MudFlowVisualPayload;
import com.fish.mirebound.network.payload.AdaptiveMudSourcesPayload;
import com.fish.mirebound.network.payload.AdaptiveMudProfileSyncPayload;
import com.fish.mirebound.network.payload.AdaptiveMudActionPayload;
import com.fish.mirebound.network.payload.TentacleWandActionPayload;
import com.fish.mirebound.water.WaterGunSystem;
import com.fish.mirebound.coverage.armor.ArmorTextureMudManager;
import com.fish.mirebound.mud.AnimatedPlayerGeometryManager;
import com.fish.mirebound.mud.tuning.MudTuningManager;
import com.fish.mirebound.mud.tuning.MudTuningConversionSafety;
import com.fish.mirebound.generation.MudTerrainGenerationManager;
import com.fish.mirebound.registry.ModBlocks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetworking {
    private static final String PROTOCOL_VERSION = "171";

    private ModNetworking() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToServer(MudStrugglePayload.TYPE, MudStrugglePayload.STREAM_CODEC, ModNetworking::handleStruggle);
        registrar.playToServer(MudDeveloperOptionsPayload.TYPE, MudDeveloperOptionsPayload.STREAM_CODEC, ModNetworking::handleDeveloperOptions);
        registrar.playToServer(MudViewModePayload.TYPE, MudViewModePayload.STREAM_CODEC,
                ModNetworking::handleViewMode);
        registrar.playToServer(MudTuningRequestPayload.TYPE, MudTuningRequestPayload.STREAM_CODEC,
                ModNetworking::handleMudTuningRequest);
        registrar.playToServer(MudTuningGlobalRequestPayload.TYPE,
                MudTuningGlobalRequestPayload.STREAM_CODEC,
                ModNetworking::handleMudTuningGlobalRequest);
        registrar.playToServer(MudTerrainGenerationPayload.TYPE,
                MudTerrainGenerationPayload.STREAM_CODEC,
                ModNetworking::handleMudTerrainGeneration);
        registrar.playToServer(MudTuningSelectionNudgePayload.TYPE,
                MudTuningSelectionNudgePayload.STREAM_CODEC,
                ModNetworking::handleMudTuningSelectionNudge);
        registrar.playToServer(MudTuningApplyPayload.TYPE, MudTuningApplyPayload.STREAM_CODEC,
                ModNetworking::handleMudTuningApply);
        registrar.playToServer(AdaptiveMudActionPayload.TYPE,
                AdaptiveMudActionPayload.STREAM_CODEC,
                ModNetworking::handleAdaptiveMudAction);
        registrar.playToServer(MudTuningConversionUnlockPayload.TYPE,
                MudTuningConversionUnlockPayload.STREAM_CODEC,
                ModNetworking::handleMudTuningConversionUnlock);
        registrar.playToServer(TentacleWandActionPayload.TYPE,
                TentacleWandActionPayload.STREAM_CODEC,
                ModNetworking::handleTentacleWandAction);
        registrar.playToServer(ArmorTextureContactPayload.TYPE, ArmorTextureContactPayload.STREAM_CODEC,
                ModNetworking::handleArmorTextureContact);
        registrar.playToServer(PlayerGeometryPayload.TYPE, PlayerGeometryPayload.STREAM_CODEC,
                ModNetworking::handlePlayerGeometry);
        registrar.playToServer(WaterGunFirePayload.TYPE, WaterGunFirePayload.STREAM_CODEC,
                ModNetworking::handleWaterGunFire);
        registrar.playToServer(RopeDragPayload.TYPE, RopeDragPayload.STREAM_CODEC,
                ModNetworking::handleRopeDrag);
        registrar.playToServer(RopeAnchorPayload.TYPE, RopeAnchorPayload.STREAM_CODEC,
                ModNetworking::handleRopeAnchor);
        registrar.playToServer(RopeBreakPayload.TYPE, RopeBreakPayload.STREAM_CODEC,
                ModNetworking::handleRopeBreak);
        registrar.playToServer(RopeExtendPayload.TYPE, RopeExtendPayload.STREAM_CODEC,
                ModNetworking::handleRopeExtend);
        registrar.playToServer(RopeConnectPayload.TYPE, RopeConnectPayload.STREAM_CODEC,
                ModNetworking::handleRopeConnect);
        registrar.playToServer(RopeRescueCastPayload.TYPE, RopeRescueCastPayload.STREAM_CODEC,
                ModNetworking::handleRopeRescueCast);
        registrar.playToServer(RopeRescueHaulPayload.TYPE, RopeRescueHaulPayload.STREAM_CODEC,
                ModNetworking::handleRopeRescueHaul);
        registrar.playToServer(RopeClimbInputPayload.TYPE, RopeClimbInputPayload.STREAM_CODEC,
                ModNetworking::handleRopeClimbInput);
        registrar.playToClient(RopeInteractionReleasePayload.TYPE,
                RopeInteractionReleasePayload.STREAM_CODEC,
                ModNetworking::handleRopeInteractionRelease);
        registrar.playToServer(SculkMireInputPayload.TYPE, SculkMireInputPayload.STREAM_CODEC,
                ModNetworking::handleSculkMireInput);
        registrar.playToServer(TenderFleshStrikePayload.TYPE, TenderFleshStrikePayload.STREAM_CODEC,
                ModNetworking::handleTenderFleshStrike);
        registrar.playToServer(AssimilationQteInputPayload.TYPE,
                AssimilationQteInputPayload.STREAM_CODEC,
                ModNetworking::handleAssimilationQteInput);
        registrar.playToServer(AssimilationQteTracePayload.TYPE,
                AssimilationQteTracePayload.STREAM_CODEC,
                ModNetworking::handleAssimilationQteTrace);
        registrar.playToServer(AssimilationSoulPositionPayload.TYPE,
                AssimilationSoulPositionPayload.STREAM_CODEC,
                ModNetworking::handleAssimilationSoulPosition);
        registrar.playToServer(AssimilationPurgeInputPayload.TYPE,
                AssimilationPurgeInputPayload.STREAM_CODEC,
                ModNetworking::handleAssimilationPurgeInput);
        registrar.playToClient(MudCoverageSyncPayload.TYPE, MudCoverageSyncPayload.STREAM_CODEC, ModNetworking::handleCoverageSync);
        registrar.playToClient(MudCoverageDeltaPayload.TYPE, MudCoverageDeltaPayload.STREAM_CODEC,
                ModNetworking::handleCoverageDelta);
        registrar.playToClient(MudDebugSyncPayload.TYPE, MudDebugSyncPayload.STREAM_CODEC, ModNetworking::handleDebugSync);
        registrar.playToClient(MudPhysicsProfileSyncPayload.TYPE, MudPhysicsProfileSyncPayload.STREAM_CODEC,
                ModNetworking::handlePhysicsProfileSync);
        registrar.playToClient(SwarmStateSyncPayload.TYPE, SwarmStateSyncPayload.STREAM_CODEC,
                ModNetworking::handleSwarmStateSync);
        registrar.playToClient(SculkClampStatePayload.TYPE, SculkClampStatePayload.STREAM_CODEC,
                ModNetworking::handleSculkClampState);
        registrar.playToClient(TentacleStateSyncPayload.TYPE, TentacleStateSyncPayload.STREAM_CODEC,
                ModNetworking::handleTentacleStateSync);
        registrar.playToClient(RopeSnapshotPayload.TYPE, RopeSnapshotPayload.STREAM_CODEC,
                ModNetworking::handleRopeSnapshot);
        registrar.playToClient(TenderFleshEnclosurePayload.TYPE,
                TenderFleshEnclosurePayload.STREAM_CODEC,
                ModNetworking::handleTenderFleshEnclosure);
        registrar.playToClient(MudSplashPayload.TYPE, MudSplashPayload.STREAM_CODEC,
                ModNetworking::handleMudSplash);
        registrar.playToClient(MudClodScreenImpactPayload.TYPE,
                MudClodScreenImpactPayload.STREAM_CODEC,
                ModNetworking::handleMudClodScreenImpact);
        registrar.playToClient(DroppedItemMudStatePayload.TYPE,
                DroppedItemMudStatePayload.STREAM_CODEC,
                ModNetworking::handleDroppedItemMudState);
        registrar.playToClient(EntityMudCoveragePayload.TYPE,
                EntityMudCoveragePayload.STREAM_CODEC,
                ModNetworking::handleEntityMudCoverage);
        registrar.playToClient(MudSurfaceImpactPayload.TYPE, MudSurfaceImpactPayload.STREAM_CODEC,
                ModNetworking::handleMudSurfaceImpact);
        registrar.playToClient(MudEruptionVentPayload.TYPE, MudEruptionVentPayload.STREAM_CODEC,
                ModNetworking::handleMudEruptionVent);
        registrar.playToClient(MudProbeBubblePayload.TYPE, MudProbeBubblePayload.STREAM_CODEC,
                ModNetworking::handleMudProbeBubble);
        registrar.playToClient(MudTuningSelectionPayload.TYPE, MudTuningSelectionPayload.STREAM_CODEC,
                ModNetworking::handleMudTuningSelection);
        registrar.playToClient(MudTuningWandBeamPayload.TYPE,
                MudTuningWandBeamPayload.STREAM_CODEC,
                ModNetworking::handleMudTuningWandBeam);
        registrar.playToClient(MudTuningWandPulsePayload.TYPE,
                MudTuningWandPulsePayload.STREAM_CODEC,
                ModNetworking::handleMudTuningWandPulse);
        registrar.playToClient(MudTuningSessionPayload.TYPE, MudTuningSessionPayload.STREAM_CODEC,
                ModNetworking::handleMudTuningSession);
        registrar.playToClient(MudTuningGlobalSettingsPayload.TYPE,
                MudTuningGlobalSettingsPayload.STREAM_CODEC,
                ModNetworking::handleMudTuningGlobalSettings);
        registrar.playToClient(MudTuningConversionSafetyPayload.TYPE,
                MudTuningConversionSafetyPayload.STREAM_CODEC,
                ModNetworking::handleMudTuningConversionSafety);
        registrar.playToClient(MudLocalProfilesPayload.TYPE, MudLocalProfilesPayload.STREAM_CODEC,
                ModNetworking::handleMudLocalProfiles);
        registrar.playToClient(MudFlowVisualPayload.TYPE, MudFlowVisualPayload.STREAM_CODEC,
                ModNetworking::handleMudFlowVisual);
        registrar.playToClient(AdaptiveMudSourcesPayload.TYPE,
                AdaptiveMudSourcesPayload.STREAM_CODEC,
                ModNetworking::handleAdaptiveMudSources);
        registrar.playToClient(AdaptiveMudProfileSyncPayload.TYPE,
                AdaptiveMudProfileSyncPayload.STREAM_CODEC,
                ModNetworking::handleAdaptiveMudProfile);
        registrar.playToClient(WaterGunStreamPayload.TYPE, WaterGunStreamPayload.STREAM_CODEC,
                ModNetworking::handleWaterGunStream);
        registrar.playToClient(WaterGunProfileSyncPayload.TYPE, WaterGunProfileSyncPayload.STREAM_CODEC,
                ModNetworking::handleWaterGunProfile);
        registrar.playToClient(AssimilationStatePayload.TYPE, AssimilationStatePayload.STREAM_CODEC,
                ModNetworking::handleAssimilationState);
    }

    private static void handleDeveloperOptions(MudDeveloperOptionsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.hasPermissions(2)
                    && ServerInputBudget.allow(player, ServerInputBudget.Channel.DEVELOPER_OPTIONS)) {
                PhysicsTraceLog.setEnabled(player, payload.physicsLog());
            }
        });
    }

    private static void handleViewMode(MudViewModePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && ServerInputBudget.allow(player, ServerInputBudget.Channel.VIEW_MODE)) {
                // A client cannot prove that a third-party camera is active. Only accept
                // the clear operation here; spectator mode is already server authoritative.
                if (!payload.externalCamera()) {
                    MudPhysics.setClientPollutionSuppressed(player, false);
                }
            }
        });
    }

    private static void handleStruggle(MudStrugglePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && ServerInputBudget.allow(player, ServerInputBudget.Channel.STRUGGLE)) {
                MudPhysics.handleStruggle(player, payload.pressed(), payload.chargeTicks());
            }
        });
    }

    private static void handleSculkMireInput(SculkMireInputPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && ServerInputBudget.allow(player, ServerInputBudget.Channel.SCULK_MIRE_INPUT)) {
                MudPhysics.handleSculkMireInput(player, payload.movementStrength(),
                        payload.jumping(), payload.crouching());
            }
        });
    }

    private static void handleTenderFleshStrike(
            TenderFleshStrikePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && ServerInputBudget.allow(player, ServerInputBudget.Channel.TENDER_FLESH_STRIKE)) {
                MudPhysics.handleTenderFleshStrike(player, payload.pillarIndex());
            }
        });
    }

    private static void handleAssimilationQteInput(
            AssimilationQteInputPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && ServerInputBudget.allow(player, ServerInputBudget.Channel.ASSIMILATION_QTE)) {
                AssimilationSystem.handleSelfRescueQte(
                        player, payload.sequence(), payload.cell(), payload.button(), payload.phase());
            }
        });
    }

    private static void handleAssimilationQteTrace(
            AssimilationQteTracePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && ServerInputBudget.allow(player, ServerInputBudget.Channel.ASSIMILATION_TRACE)) {
                AssimilationSystem.handleSelfRescueTrace(player, payload.sequence(), payload.cell(),
                        payload.button(), payload.action(), payload.node());
            }
        });
    }

    private static void handleAssimilationSoulPosition(
            AssimilationSoulPositionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && ServerInputBudget.allow(
                            player, ServerInputBudget.Channel.ASSIMILATION_SOUL_POSITION)) {
                AssimilationSystem.updateSoulPosition(
                        player, payload.x(), payload.y(), payload.z());
            }
        });
    }

    private static void handleAssimilationPurgeInput(
            AssimilationPurgeInputPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && ServerInputBudget.allow(player, ServerInputBudget.Channel.ASSIMILATION_PURGE)) {
                if (payload.movementAttempt()) {
                    AssimilationSystem.cancelPartialPurgeForMovement(player);
                } else {
                    AssimilationSystem.togglePartialPurge(player);
                }
            }
        });
    }

    private static void handleMudTuningRequest(MudTuningRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && isHoldingTuningWand(player)
                    && ServerInputBudget.allow(player, ServerInputBudget.Channel.MUD_TUNING_REQUEST)) {
                MudTuningManager.handleRequest(player, payload);
            }
        });
    }

    private static void handleMudTuningGlobalRequest(
            MudTuningGlobalRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && isHoldingTuningWand(player)
                    && ServerInputBudget.allow(
                            player, ServerInputBudget.Channel.MUD_TUNING_REQUEST)) {
                MudTuningManager.handleGlobalSettings(player, payload);
            }
        });
    }

    private static void handleMudTerrainGeneration(
            MudTerrainGenerationPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && isHoldingTuningWand(player)
                    && ServerInputBudget.allow(
                            player, ServerInputBudget.Channel.MUD_TUNING_APPLY)) {
                MudTerrainGenerationManager.handle(player, payload);
            }
        });
    }

    private static void handleMudTuningSelectionNudge(
            MudTuningSelectionNudgePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && isHoldingTuningWand(player)
                    && ServerInputBudget.allow(
                            player, ServerInputBudget.Channel.MUD_TUNING_REQUEST)) {
                MudTuningManager.nudgeSelection(player, payload);
            }
        });
    }

    private static void handleMudTuningApply(MudTuningApplyPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && isHoldingTuningWand(player)
                    && ServerInputBudget.allow(player, ServerInputBudget.Channel.MUD_TUNING_APPLY)) {
                MudTuningManager.apply(player, payload);
            }
        });
    }

    private static void handleAdaptiveMudAction(
            AdaptiveMudActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && isHoldingTuningWand(player)
                    && ServerInputBudget.allow(player, ServerInputBudget.Channel.MUD_TUNING_APPLY)) {
                MudTuningManager.applyAdaptive(player, payload);
            }
        });
    }

    private static void handleMudTuningConversionUnlock(
            MudTuningConversionUnlockPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (payload.confirmed()
                    && context.player() instanceof ServerPlayer player
                    && isHoldingTuningWand(player)
                    && ServerInputBudget.allow(
                            player, ServerInputBudget.Channel.MUD_TUNING_APPLY)) {
                MudTuningConversionSafety.advance(player);
            }
        });
    }

    private static void handleTentacleWandAction(
            TentacleWandActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && isHoldingTuningWand(player)
                    && ServerInputBudget.allow(
                            player, ServerInputBudget.Channel.MUD_TUNING_REQUEST)) {
                MudTuningManager.handleTentacleWand(player, payload);
            }
        });
    }

    private static void handleArmorTextureContact(ArmorTextureContactPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && ServerInputBudget.allow(player, ServerInputBudget.Channel.ARMOR_TEXTURE_CONTACT)
                    && !MudPhysics.isPollutionSuppressed(player)) {
                ArmorTextureMudManager.handleSamples(player, payload);
            }
        });
    }

    private static void handlePlayerGeometry(PlayerGeometryPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && ServerInputBudget.allow(player, ServerInputBudget.Channel.PLAYER_GEOMETRY)) {
                AnimatedPlayerGeometryManager.handle(player, payload);
            }
        });
    }

    private static void handleWaterGunFire(WaterGunFirePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && ServerInputBudget.allow(player, ServerInputBudget.Channel.WATER_GUN_INPUT)) {
                WaterGunSystem.handleInput(player, payload.firing());
            }
        });
    }

    private static void handleWaterGunStream(WaterGunStreamPayload payload, IPayloadContext context) {
        enqueueClient(context, () -> ClientNetworkHandlers.handleWaterGunStream(payload));
    }

    private static void handleWaterGunProfile(WaterGunProfileSyncPayload payload, IPayloadContext context) {
        enqueueClient(context, () -> ClientNetworkHandlers.handleWaterGunProfile(payload));
    }

    private static void handleAssimilationState(AssimilationStatePayload payload, IPayloadContext context) {
        enqueueClient(context, () -> ClientNetworkHandlers.handleAssimilationState(payload));
    }

    private static void handleCoverageSync(MudCoverageSyncPayload payload, IPayloadContext context) {
        enqueueClient(context, () -> ClientNetworkHandlers.handleCoverageSync(payload));
    }

    private static void handleCoverageDelta(MudCoverageDeltaPayload payload, IPayloadContext context) {
        enqueueClient(context, () -> ClientNetworkHandlers.handleCoverageDelta(payload));
    }

    private static void handleDebugSync(MudDebugSyncPayload payload, IPayloadContext context) {
        enqueueClient(context, () -> ClientNetworkHandlers.handleDebugSync(payload));
    }

    private static void handlePhysicsProfileSync(MudPhysicsProfileSyncPayload payload, IPayloadContext context) {
        enqueueClient(context, () -> ClientNetworkHandlers.handlePhysicsProfileSync(payload));
    }

    private static void handleSwarmStateSync(SwarmStateSyncPayload payload, IPayloadContext context) {
        enqueueClient(context, () -> ClientNetworkHandlers.handleSwarmStateSync(payload));
    }

    private static void handleSculkClampState(SculkClampStatePayload payload, IPayloadContext context) {
        enqueueClient(context, () -> ClientNetworkHandlers.handleSculkClampState(payload));
    }

    private static void handleTentacleStateSync(TentacleStateSyncPayload payload, IPayloadContext context) {
        enqueueClient(context, () -> ClientNetworkHandlers.handleTentacleStateSync(payload));
    }

    private static void handleRopeSnapshot(
            RopeSnapshotPayload payload, IPayloadContext context) {
        enqueueClient(context, () -> ClientNetworkHandlers.handleRopeSnapshot(payload));
    }

    private static void handleRopeDrag(
            RopeDragPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && ServerInputBudget.allow(player, ServerInputBudget.Channel.ROPE_DRAG)) {
                com.fish.mirebound.rope.RopeRuntime.handleDrag(player, payload);
            }
        });
    }

    private static void handleRopeAnchor(
            RopeAnchorPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && ServerInputBudget.allow(player, ServerInputBudget.Channel.ROPE_ANCHOR)) {
                com.fish.mirebound.rope.RopeRuntime.handleAnchor(player, payload);
            }
        });
    }

    private static void handleRopeBreak(
            RopeBreakPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && ServerInputBudget.allow(player, ServerInputBudget.Channel.ROPE_BREAK)) {
                com.fish.mirebound.rope.RopeRuntime.handleBreak(player, payload);
            }
        });
    }

    private static void handleRopeExtend(
            RopeExtendPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && ServerInputBudget.allow(player, ServerInputBudget.Channel.ROPE_EXTEND)) {
                com.fish.mirebound.rope.RopeRuntime.handleExtend(player, payload);
            }
        });
    }

    private static void handleRopeConnect(
            RopeConnectPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && ServerInputBudget.allow(player, ServerInputBudget.Channel.ROPE_EXTEND)) {
                com.fish.mirebound.rope.RopeRuntime.handleConnect(player, payload);
            }
        });
    }

    private static void handleRopeRescueCast(
            RopeRescueCastPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                com.fish.mirebound.rope.RopeRuntime.setRescueCastArmed(
                        player, payload.armed());
            }
        });
    }

    private static void handleRopeRescueHaul(
            RopeRescueHaulPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                com.fish.mirebound.rope.RopeRuntime.handleRescueHaul(player, payload);
            }
        });
    }

    private static void handleRopeClimbInput(
            RopeClimbInputPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && ServerInputBudget.allow(player, ServerInputBudget.Channel.ROPE_CLIMB)) {
                com.fish.mirebound.rope.RopeRuntime.handleClimbInput(player, payload);
            }
        });
    }

    private static void handleRopeInteractionRelease(
            RopeInteractionReleasePayload payload, IPayloadContext context) {
        enqueueClient(context,
                () -> ClientNetworkHandlers.handleRopeInteractionRelease(payload));
    }

    private static void handleTenderFleshEnclosure(
            TenderFleshEnclosurePayload payload, IPayloadContext context) {
        enqueueClient(context, () -> ClientNetworkHandlers.handleTenderFleshEnclosure(payload));
    }

    private static void handleMudSplash(MudSplashPayload payload, IPayloadContext context) {
        enqueueClient(context, () -> ClientNetworkHandlers.handleMudSplash(payload));
    }

    private static void handleMudClodScreenImpact(
            MudClodScreenImpactPayload payload, IPayloadContext context) {
        enqueueClient(context, () -> ClientNetworkHandlers.handleMudClodScreenImpact(payload));
    }

    private static void handleDroppedItemMudState(
            DroppedItemMudStatePayload payload, IPayloadContext context) {
        enqueueClient(context, () -> ClientNetworkHandlers.handleDroppedItemMudState(payload));
    }

    private static void handleEntityMudCoverage(
            EntityMudCoveragePayload payload, IPayloadContext context) {
        enqueueClient(context, () -> ClientNetworkHandlers.handleEntityMudCoverage(payload));
    }

    private static void handleMudSurfaceImpact(
            MudSurfaceImpactPayload payload, IPayloadContext context) {
        enqueueClient(context, () -> ClientNetworkHandlers.handleMudSurfaceImpact(payload));
    }

    private static void handleMudEruptionVent(
            MudEruptionVentPayload payload, IPayloadContext context) {
        enqueueClient(context, () -> ClientNetworkHandlers.handleMudEruptionVent(payload));
    }

    private static void handleMudProbeBubble(MudProbeBubblePayload payload, IPayloadContext context) {
        enqueueClient(context, () -> ClientNetworkHandlers.handleMudProbeBubble(payload));
    }

    private static void handleMudTuningSelection(MudTuningSelectionPayload payload, IPayloadContext context) {
        enqueueClient(context, () -> ClientNetworkHandlers.handleMudTuningSelection(payload));
    }

    private static void handleMudTuningWandBeam(
            MudTuningWandBeamPayload payload, IPayloadContext context) {
        enqueueClient(context, () -> ClientNetworkHandlers.handleMudTuningWandBeam(payload));
    }

    private static void handleMudTuningWandPulse(
            MudTuningWandPulsePayload payload, IPayloadContext context) {
        enqueueClient(context, () -> ClientNetworkHandlers.handleMudTuningWandPulse(payload));
    }

    private static void handleMudTuningSession(MudTuningSessionPayload payload, IPayloadContext context) {
        enqueueClient(context, () -> ClientNetworkHandlers.handleMudTuningSession(payload));
    }

    private static void handleMudTuningGlobalSettings(
            MudTuningGlobalSettingsPayload payload, IPayloadContext context) {
        enqueueClient(context, () -> ClientNetworkHandlers.handleMudTuningGlobalSettings(payload));
    }

    private static void handleMudTuningConversionSafety(
            MudTuningConversionSafetyPayload payload, IPayloadContext context) {
        enqueueClient(context, () -> ClientNetworkHandlers.handleMudTuningConversionSafety(payload));
    }

    private static void handleMudLocalProfiles(MudLocalProfilesPayload payload, IPayloadContext context) {
        enqueueClient(context, () -> ClientNetworkHandlers.handleMudLocalProfiles(payload));
    }

    private static void handleMudFlowVisual(
            MudFlowVisualPayload payload, IPayloadContext context) {
        enqueueClient(context, () -> ClientNetworkHandlers.handleMudFlowVisual(payload));
    }

    private static void handleAdaptiveMudSources(
            AdaptiveMudSourcesPayload payload, IPayloadContext context) {
        enqueueClient(context, () -> ClientNetworkHandlers.handleAdaptiveMudSources(payload));
    }

    private static void handleAdaptiveMudProfile(
            AdaptiveMudProfileSyncPayload payload, IPayloadContext context) {
        enqueueClient(context, () -> ClientNetworkHandlers.handleAdaptiveMudProfile(payload));
    }

    private static void enqueueClient(IPayloadContext context, Runnable action) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            context.enqueueWork(action);
        }
    }

    private static boolean isHoldingTuningWand(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        return main.getItem() == ModBlocks.MUD_TUNING_WAND.get()
                || off.getItem() == ModBlocks.MUD_TUNING_WAND.get();
    }
}

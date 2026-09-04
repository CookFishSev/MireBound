package com.fish.mirebound.client;

import com.fish.mirebound.client.compat.ClientRenderCompat;
import com.fish.mirebound.client.compat.SableAdaptiveMudInvalidationQueue;
import com.fish.mirebound.client.tentacle.ClientTentacleManager;
import com.fish.mirebound.client.tentacle.ProceduralTentacleRenderer;
import com.fish.mirebound.client.generation.MudTerrainGenerationPreview;
import com.fish.mirebound.client.generation.MudTerrainGenerationController;
import com.fish.mirebound.client.generation.MudTerrainGenerationPreviewRenderer;
import com.fish.mirebound.client.config.MireboundClientSettings;
import com.fish.mirebound.client.config.MireboundClientSettings.ClientOption;
import com.fish.mirebound.client.tentacle.TentacleGrabCamera;
import com.fish.mirebound.client.tentacle.TentacleGrabPlayerRenderer;
import com.fish.mirebound.client.tentacle.TentacleCameraTraceLog;
import com.fish.mirebound.client.swarm.ClientSwarmState;
import com.fish.mirebound.client.swarm.SwarmScreenOverlay;
import com.fish.mirebound.client.eruption.ClientMudEruptionManager;
import com.fish.mirebound.client.itemphysics.DroppedItemPresentation;
import com.fish.mirebound.client.rope.RopeChargeOverlay;
import com.fish.mirebound.client.rope.ClientRopes;
import com.fish.mirebound.client.rope.RopeModelRenderer;
import com.fish.mirebound.client.rope.RopeSelectionRenderer;
import com.fish.mirebound.client.entitycoverage.ClientEntityMudCoverage;
import com.fish.mirebound.client.entitycoverage.EntityMudRenderLayer;
import com.fish.mirebound.client.entitycoverage.EntityMudTextureCache;
import com.fish.mirebound.client.tuning.MudTuningClientState;
import com.fish.mirebound.client.tuning.MudTuningInputController;
import com.fish.mirebound.client.tuning.MudTuningWandHud;
import com.fish.mirebound.compat.sable.SableCompat;
import com.fish.mirebound.network.payload.MudStrugglePayload;
import com.fish.mirebound.network.payload.SculkMireInputPayload;
import com.fish.mirebound.network.payload.MudDebugSyncPayload;
import com.fish.mirebound.network.payload.MudTuningRequestPayload;
import com.fish.mirebound.network.payload.WaterGunFirePayload;
import com.fish.mirebound.network.payload.TenderFleshStrikePayload;
import com.fish.mirebound.network.payload.AssimilationPurgeInputPayload;
import com.fish.mirebound.water.WaterGunItem;
import com.fish.mirebound.mud.MudPhysics;
import com.fish.mirebound.mud.MudPhysicsProfiles;
import com.fish.mirebound.mud.MudStruggleTiming;
import com.fish.mirebound.mud.MudLocalProfileCache;
import com.fish.mirebound.mud.MudTuningAnchor;
import com.fish.mirebound.physics.MudMovementControl;
import com.fish.mirebound.registry.ModBlocks;
import com.fish.mirebound.registry.ModParticles;
import com.fish.mirebound.registry.ModMudworkContent;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderArmEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public final class ClientEvents {
    private static final KeyMapping STRUGGLE_KEY = new KeyMapping(
            "key.mirebound.struggle",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_SPACE,
            "key.categories.mirebound");
    private static final KeyMapping ASSIMILATION_PURGE_KEY = new KeyMapping(
            "key.mirebound.assimilation_purge",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_ALT,
            "key.categories.mirebound");
    private static boolean wasStruggleDown;
    private static boolean wasLocalPlayerDead;
    private static int struggleCharge;
    private static int struggleCooldownTicks;
    private static int struggleCooldownTotalTicks;
    private static boolean purgeMovementCancelSent;
    private static int waterGunInputRefreshTicks;
    private static int sculkInputRefreshTicks;
    private static boolean waterGunFiring;
    private static int tuningRefreshCooldown;
    private static boolean playerCoverageRendering = true;

    private ClientEvents() {
    }

    public static void register(IEventBus modBus, ModContainer modContainer) {
        MireboundClientSettings.register(modContainer, modBus);
        modBus.addListener(ClientEvents::registerKeys);
        modBus.addListener(ClientEvents::registerParticleProviders);
        modBus.addListener(MudVariantModels::registerAdditional);
        modBus.addListener(MudVariantModels::modifyBakingResult);
        modBus.addListener(AdaptiveMudModels::modifyBakingResult);
        modBus.addListener(AdaptiveMudModels::registerBlockColors);
        modBus.addListener(MudBucketModels::registerAdditional);
        modBus.addListener(MudBucketModels::modifyBakingResult);
        modBus.addListener(MudProbeModels::registerAdditional);
        modBus.addListener(MudProbeModels::modifyBakingResult);
        modBus.addListener(MudTuningWandModels::registerAdditional);
        modBus.addListener(WaterGunModels::registerAdditional);
        modBus.addListener(ArmorMudItemMarker::register);
        modBus.addListener(ClientEvents::registerRenderers);
        modBus.addListener(ClientEvents::addPlayerLayers);
        modBus.addListener(ClientEvents::registerReloadListeners);
        modBus.addListener(WaterGunClientExtensions::register);
        modBus.addListener(MudProbeClientExtensions::register);
        modBus.addListener(MudTuningWandRenderer::register);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onClientTick);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onClientTickPre);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ClientEvents::onInteractionKey);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ClientEvents::onMouseButton);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ClientEvents::onMouseScroll);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,
                MudTuningInputController::handleKeyInput);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onClientLogin);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onClientLogout);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ClientEvents::onComputeFovModifier);
        NeoForge.EVENT_BUS.addListener(AssimilationSoulPresentation::onComputeFogColor);
        NeoForge.EVENT_BUS.addListener(AssimilationSoulPresentation::onRenderFog);
        NeoForge.EVENT_BUS.addListener(ClientEvents::renderWorldScreenEffects);
        NeoForge.EVENT_BUS.addListener(ClientEvents::reserveStruggleHudSpace);
        NeoForge.EVENT_BUS.addListener(ClientEvents::renderStruggleHud);
        NeoForge.EVENT_BUS.addListener(MudTuningWandHud::render);
        NeoForge.EVENT_BUS.addListener(RopeChargeOverlay::render);
        NeoForge.EVENT_BUS.addListener(RopeModelRenderer::render);
        NeoForge.EVENT_BUS.addListener(RopeSelectionRenderer::render);
        NeoForge.EVENT_BUS.addListener(ClientEvents::renderMudLayerAfterPlayer);
        NeoForge.EVENT_BUS.addListener(ArmorMudProxyRenderer::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(ProceduralTentacleRenderer::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(MudSurfaceEffectRenderer::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(InsectMoundSurfaceRenderer::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(ClientSculkClampRenderer::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(MudSplashRenderer::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(WaterGunStreamRenderer::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(MudContactGeometryRenderer::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(MudTuningSelectionRenderer::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(
                MudTerrainGenerationPreviewRenderer::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(MudTuningWandBeamRenderer::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(MudTuningTargetRenderer::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(MudTuningTargetRenderer::onRenderBlockHighlight);
        NeoForge.EVENT_BUS.addListener(AssimilationFrozenBodyProxy::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(AssimilationSoulPresentation::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,
                TentacleGrabCamera::onComputeCameraAngles);
        NeoForge.EVENT_BUS.addListener(TentacleGrabPlayerRenderer::onRenderPlayerPre);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,
                MudTuningWandClientEffects::onRenderPlayerPre);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,
                WaterGunNozzleFocus::onRenderPlayerPre);
        NeoForge.EVENT_BUS.addListener(AssimilationPlayerAnimation::onPre);
        NeoForge.EVENT_BUS.addListener(AssimilationPlayerAnimation::onPost);
        NeoForge.EVENT_BUS.addListener(TentacleGrabPlayerRenderer::onRenderPlayerPost);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,
                WaterGunNozzleFocus::onRenderPlayerPost);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,
                MudTuningWandClientEffects::onRenderPlayerPost);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ClientEvents::hideSoulHand);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ClientEvents::hideSoulArm);
        NeoForge.EVENT_BUS.addListener(FirstPersonMudArmRenderer::onRenderArm);
        NeoForge.EVENT_BUS.addListener(ClientMudCommands::register);
        NeoForge.EVENT_BUS.addListener(ArmorMudItemMarker::appendTooltip);
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(STRUGGLE_KEY);
        event.register(ASSIMILATION_PURGE_KEY);
        MudTuningInputController.registerKey(event);
    }

    private static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(
                ModParticles.TUNING_WAND_SELECTION.get(),
                MudTuningWandSelectionParticle.Provider::new);
    }

    private static void onComputeFovModifier(ComputeFovModifierEvent event) {
        double correction = MudMovementControl.movementFovCorrection(event.getPlayer());
        if (Math.abs(correction - 1.0D) < 1.0E-6D) {
            return;
        }
        float correctedRawModifier = (float) (event.getFovModifier() * correction);
        event.setNewFovModifier((float) Mth.lerp(
                Minecraft.getInstance().options.fovEffectScale().get(),
                1.0F,
                correctedRawModifier));
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlocks.MUD_FOOTPRINT_ENTITY.get(), MudFootprintBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(
                ModBlocks.ADAPTIVE_MUD_ENTITY.get(),
                AdaptiveMudBlockEntityRenderer::new);
        event.registerEntityRenderer(
                ModMudworkContent.MUD_BALL_PROJECTILE.get(),
                ThrownItemRenderer::new);
    }

    private static void addPlayerLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer != null) {
                renderer.addLayer(new MudSkinLayer(renderer));
                renderer.addLayer(new ArmorMudLayer(
                        renderer,
                        event.getEntityModels(),
                        skin == PlayerSkin.Model.SLIM));
            }
        }
        for (var entityType : event.getEntityTypes()) {
            var renderer = event.getRenderer(entityType);
            if (renderer instanceof LivingEntityRenderer<?, ?> livingRenderer
                    && !(renderer instanceof PlayerRenderer)) {
                addEntityMudLayer(livingRenderer);
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addEntityMudLayer(LivingEntityRenderer renderer) {
        renderer.addLayer(new EntityMudRenderLayer(renderer));
    }

    private static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resourceManager -> {
            MudSkinTextureCache.reset();
            MudSurfaceAppearance.reset();
            MudRenderedSurfaceGeometry.reset();
            MudTuningWandCoreTexture.reset();
            MudTuningWandClientEffects.reset();
            MudCapeTextureCache.reset();
            ArmorMudTextureCache.reset();
            ArmorMudRenderBridge.reset();
            ArmorAccessoryRenderContext.reset();
            ArmorVertexContactCapture.reset();
            AnimatedPlayerGeometryCapture.reset();
            ArmorMudProxyRenderer.reset();
            ArmorTextureFootprintCache.reset();
            SkinPixelCache.reset();
            MudFootprintTextureCache.reset();
            MudWallTextureCache.reset();
            EntityMudTextureCache.reset();
            ScreenMudOverlay.reset();
            AssimilationScreenOverlay.reset();
            AssimilationSoulPresentation.reset();
            MudSurfaceEffectManager.reset();
            InsectMoundSurfaceManager.reset();
        });
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        updatePlayerCoverageRendering();
        ClientMudState.tick();
        ClientEntityMudCoverage.tick(Minecraft.getInstance());
        EntityMudTextureCache.tick(Minecraft.getInstance());
        MudSkinTextureCache.tick(Minecraft.getInstance());
        ClientAssimilationState.tick();
        ClientMudDebugState.tick();
        ClientSwarmState.tick();
        ClientTentacleManager.tick();
        TentacleGrabCamera.tick();
        SwarmScreenOverlay.tick();
        MudSurfaceEffectManager.tick();
        ClientMudEruptionManager.tick();
        InsectMoundSurfaceManager.tick();
        ClientSculkClampManager.tick();
        MudSplashClientManager.tick();
        Minecraft minecraft = Minecraft.getInstance();
        AdaptiveMudClientCache.tick(minecraft.level);
        SableAdaptiveMudInvalidationQueue.tick(minecraft);
            MudTuningInputController.tick(minecraft);
            MudFlowClientManager.tick(minecraft);
            MudTuningSectionHighlightCache.tick(minecraft);
        MudTerrainGenerationController.tick(minecraft);
        DroppedItemPresentation.tick(minecraft);
        AssimilationSoulCamera.tick(minecraft);
        AssimilationQteClient.tick(minecraft);
        if (minecraft.player == null || minecraft.getConnection() == null) {
            ClientPollutionVisibility.reset();
            wasStruggleDown = false;
            wasLocalPlayerDead = false;
            struggleCharge = 0;
            struggleCooldownTicks = 0;
            struggleCooldownTotalTicks = 0;
            purgeMovementCancelSent = false;
            waterGunInputRefreshTicks = 0;
            sculkInputRefreshTicks = 0;
            waterGunFiring = false;
            WaterGunStreamClientManager.reset();
            ClientSculkClampManager.reset();
            AssimilationSoulCamera.reset();
            AssimilationQteClient.reset();
            tuningRefreshCooldown = 0;
            MudTuningSectionHighlightCache.reset();
            MudFlowClientManager.reset();
            ClientRopes.reset();
            MudSurfaceAppearance.reset();
            return;
        }
        MudTuningWandClientEffects.tick(minecraft);
        if (struggleCooldownTicks > 0) {
            struggleCooldownTicks--;
        }
        while (ASSIMILATION_PURGE_KEY.consumeClick()) {
            if (minecraft.screen == null && !minecraft.player.isSpectator()) {
                PacketDistributor.sendToServer(new AssimilationPurgeInputPayload(false));
            }
        }
        boolean partialPurgeActive = ClientAssimilationState.localPartialPurgeActive(minecraft);
        if (!partialPurgeActive) {
            purgeMovementCancelSent = false;
        } else if (!purgeMovementCancelSent && minecraft.screen == null
                && (minecraft.player.input.getMoveVector().lengthSquared() > 1.0E-6F
                        || minecraft.player.input.jumping)) {
            PacketDistributor.sendToServer(new AssimilationPurgeInputPayload(true));
            purgeMovementCancelSent = true;
        }
        if (MudTuningInputController.heldWandHand(minecraft.player) != null) {
            if (tuningRefreshCooldown-- <= 0) {
                PacketDistributor.sendToServer(new MudTuningRequestPayload(
                        MudTuningRequestPayload.Action.REFRESH, MudTuningAnchor.WORLD_ORIGIN));
                tuningRefreshCooldown = 20;
            }
        } else {
            tuningRefreshCooldown = 0;
        }
        boolean localPlayerDead = minecraft.player.isDeadOrDying();
        if (localPlayerDead && !wasLocalPlayerDead) {
            ClientMudState.clearEntity(minecraft.player.getId());
            ClientAssimilationState.clearEntity(minecraft.player.getId());
            ScreenMudOverlay.reset();
        }
        wasLocalPlayerDead = localPlayerDead;

        int waterGunWater = WaterGunItem.water(minecraft.player.getMainHandItem());
        boolean waterGunHasWater = WaterGunStreamClientManager.hasUsableWater(waterGunWater);
        boolean holdingWaterGun = minecraft.player.getMainHandItem().getItem() == ModBlocks.WATER_GUN.get()
                && !minecraft.player.isSpectator();
        boolean firingRequested = holdingWaterGun
                && minecraft.screen == null
                && waterGunHasWater
                && minecraft.options.keyAttack.isDown();
        boolean firingWaterGun = WaterGunStreamClientManager.setLocalState(
                holdingWaterGun, waterGunHasWater, firingRequested);
        if (firingWaterGun) {
            if (!waterGunFiring || waterGunInputRefreshTicks-- <= 0) {
                PacketDistributor.sendToServer(new WaterGunFirePayload(true));
                waterGunInputRefreshTicks = 8;
            }
        } else {
            if (waterGunFiring) {
                PacketDistributor.sendToServer(new WaterGunFirePayload(false));
            }
            waterGunInputRefreshTicks = 0;
        }
        waterGunFiring = firingWaterGun;
        WaterGunStreamClientManager.tick();

        boolean inMud = isLocalPlayerInSinking(minecraft.player);
        MudPhysics.updateClientInput(
                minecraft.player, minecraft.player.input.jumping);
        boolean localPollutionSuppressed =
                ClientPollutionVisibility.isLocalSuppressed(minecraft);
        boolean sculkMire = MudPhysics.isClientPlayerInSculkMire(minecraft.player);
        if (sculkMire && !localPollutionSuppressed) {
            if (sculkInputRefreshTicks-- <= 0) {
                float movement = Math.min(1.0F, minecraft.player.input.getMoveVector().length());
                PacketDistributor.sendToServer(new SculkMireInputPayload(
                        movement, minecraft.player.input.jumping,
                        minecraft.player.input.shiftKeyDown));
                sculkInputRefreshTicks = 2;
            }
        } else {
            sculkInputRefreshTicks = 0;
        }
        boolean struggleActive = inMud && !sculkMire && !localPollutionSuppressed
                && !ClientAssimilationState.localStasisActive(minecraft);
        boolean struggleDown = STRUGGLE_KEY.isDown() && struggleActive
                && struggleCooldownTicks <= 0;
        if (struggleDown) {
            struggleCharge = Math.min(
                    MudStruggleTiming.MAX_CHARGE_TICKS, struggleCharge + 1);
            if (minecraft.player.getDeltaMovement().y > 0.018D) {
                minecraft.player.setDeltaMovement(
                        minecraft.player.getDeltaMovement().x,
                        0.018D,
                        minecraft.player.getDeltaMovement().z);
            }
        }

        if (struggleDown && !wasStruggleDown) {
            PacketDistributor.sendToServer(new MudStrugglePayload(true, struggleCharge));
        } else if (!struggleDown && wasStruggleDown) {
            PacketDistributor.sendToServer(new MudStrugglePayload(false, struggleCharge));
            if (inMud && !sculkMire) {
                int cooldown = MudPhysics.queueClientStruggle(
                        minecraft.player, struggleCharge);
                struggleCooldownTicks = cooldown;
                struggleCooldownTotalTicks = cooldown;
            }
            struggleCharge = 0;
        } else if (!struggleActive) {
            struggleCharge = 0;
        }
        wasStruggleDown = struggleDown;
    }

    private static void onClientTickPre(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientRopes.tick();
        ClientPollutionVisibility.updateExternalCameraPhysics(minecraft);
        ClientPollutionVisibility.syncViewMode(minecraft);
    }

    private static void updatePlayerCoverageRendering() {
        boolean enabled = MireboundClientSettings.clientOptionEnabled(
                ClientOption.PLAYER_COVERAGE);
        if (!enabled && playerCoverageRendering) {
            MudSkinTextureCache.reset();
            MudCapeTextureCache.reset();
            ArmorMudTextureCache.reset();
            ArmorMudRenderBridge.reset();
            ArmorAccessoryRenderContext.reset();
        }
        playerCoverageRendering = enabled;
    }

    private static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.getConnection() == null) {
            return;
        }
        if (ClientAssimilationState.localStasisActive(minecraft)) {
            if (event.isAttack()) {
                AssimilationQteClient.handlePress(minecraft, 1);
            } else if (event.isUseItem()
                    && event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND) {
                AssimilationQteClient.handlePress(minecraft, 2);
            }
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }
        if (MudTuningInputController.handleInteraction(event)) {
            return;
        }
        if (event.isUseItem()
                && event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND
                && minecraft.player.getMainHandItem().getItem() == ModBlocks.WATER_GUN.get()
                && WaterGunItem.canFillFromView(
                        minecraft.level, minecraft.player, minecraft.player.getMainHandItem())) {
            event.setSwingHand(false);
            WaterGunStreamClientManager.beginRefillAnimation();
        }
        if (!event.isAttack()) {
            return;
        }
        if (minecraft.player.getMainHandItem().getItem() == ModBlocks.WATER_GUN.get()) {
            event.setCanceled(true);
            event.setSwingHand(false);
            int storedWater = WaterGunItem.water(minecraft.player.getMainHandItem());
            if (!waterGunFiring && WaterGunStreamClientManager.beginLocalFiring(true, storedWater)) {
                PacketDistributor.sendToServer(new WaterGunFirePayload(true));
                waterGunInputRefreshTicks = 8;
                waterGunFiring = true;
            }
            return;
        }
        int pillar = MudSurfaceEffectRenderer.raycastTenderFleshPillar(minecraft);
        if (pillar < 0) {
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(true);
        PacketDistributor.sendToServer(new TenderFleshStrikePayload(pillar));
    }

    private static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (MudTuningInputController.handleMouseScroll(event)) {
            return;
        }
        if (ClientAssimilationState.localStasisActive(Minecraft.getInstance())) {
            event.setCanceled(true);
        }
    }

    private static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (ClientRopes.onMouseButton(event.getButton(), event.getAction())) {
            event.setCanceled(true);
        }
    }

    private static void hideSoulHand(RenderHandEvent event) {
        if (ClientAssimilationState.localStasisActive(Minecraft.getInstance())) {
            event.setCanceled(true);
        }
    }

    private static void hideSoulArm(RenderArmEvent event) {
        if (ClientAssimilationState.localStasisActive(Minecraft.getInstance())) {
            event.setCanceled(true);
        }
    }

    private static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        AdaptiveMudClientCache.reset();
        SableAdaptiveMudInvalidationQueue.reset();
        resetClientSessionState();
    }

    private static void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        resetClientSessionState();
    }

    private static void resetClientSessionState() {
        wasStruggleDown = false;
        wasLocalPlayerDead = false;
        struggleCharge = 0;
        struggleCooldownTicks = 0;
        struggleCooldownTotalTicks = 0;
        purgeMovementCancelSent = false;
        waterGunInputRefreshTicks = 0;
        sculkInputRefreshTicks = 0;
        waterGunFiring = false;
        playerCoverageRendering = MireboundClientSettings.clientOptionEnabled(
                ClientOption.PLAYER_COVERAGE);
        tuningRefreshCooldown = 0;
        MudTuningInputController.resetSession();
        ClientMudState.reset();
        ClientAssimilationState.reset();
        AssimilationFrozenBodyProxy.reset();
        MudTuningClientState.resetSession();
        MudTuningSectionHighlightCache.reset();
        MudFlowClientManager.reset();
        ClientRopes.reset();
        MudTerrainGenerationPreview.reset();
        MudTerrainGenerationController.reset();
        MudTuningWandCoreTexture.reset();
        MudTuningWandClientEffects.reset();
        ClientMudDebugState.reset();
        ClientSwarmState.reset();
        ClientTentacleManager.reset();
        TentacleGrabCamera.reset();
        TentacleCameraTraceLog.reset();
        SwarmScreenOverlay.reset();
        MudFootprintTextureCache.reset();
        MudWallTextureCache.reset();
        MudSplashClientManager.reset();
        DroppedItemPresentation.reset();
        WaterGunStreamClientManager.reset();
        ArmorMudTextureCache.reset();
        ArmorMudRenderBridge.reset();
        ArmorAccessoryRenderContext.reset();
        ArmorVertexContactCapture.reset();
        ArmorMudProxyRenderer.reset();
        ArmorTextureFootprintCache.reset();
        ClientPollutionVisibility.reset();
        ScreenMudOverlay.reset();
        MudSurfaceEffectManager.reset();
        MudSurfaceAppearance.reset();
        ClientEntityMudCoverage.reset();
        EntityMudTextureCache.reset();
        ClientMudEruptionManager.reset();
        InsectMoundSurfaceManager.reset();
        ClientSculkClampManager.reset();
        MudPhysics.resetClientPhysicsState();
        MudPhysicsProfiles.resetClient();
        MudLocalProfileCache.reset();
        ClientMudDebugOptions.set("physics_log", false);
    }

    private static void renderWorldScreenEffects(RenderGuiEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        boolean firstPerson = minecraft.options.getCameraType().isFirstPerson();
        if (MireboundClientSettings.clientOptionEnabled(
                ClientOption.ASSIMILATION_SCREEN)
                && ScreenMaskCameraPolicy.showsBodyOwnedMasks(firstPerson)) {
            AssimilationScreenOverlay.render(event.getGuiGraphics(), minecraft);
        }
        boolean soulActive = ClientAssimilationState.localSoulActive(minecraft);
        if (!soulActive) {
            if (MireboundClientSettings.clientOptionEnabled(ClientOption.MUD_SCREEN)
                    && (ClientMudDebugOptions.screenOverlay()
                    || ClientMudDebugOptions.screenVisionDebug()
                    || ScreenMudOverlay.hasMudClodImpact())) {
                ScreenMudOverlay.render(event);
            }
            if (MireboundClientSettings.clientOptionEnabled(ClientOption.SWARM_SCREEN)
                    && ScreenMaskCameraPolicy.showsBodyOwnedMasks(firstPerson)) {
                SwarmScreenOverlay.render(
                        event.getGuiGraphics(),
                        minecraft,
                        event.getPartialTick().getGameTimeDeltaPartialTick(false));
            }
        } else if (MireboundClientSettings.clientOptionEnabled(
                ClientOption.ASSIMILATION_SCREEN)) {
            AssimilationRescuePulseOverlay.render(event.getGuiGraphics(), minecraft);
        }
    }

    private static void renderStruggleHud(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        if (!minecraft.isPaused() && ClientAssimilationState.localSoulActive(minecraft)) {
            AssimilationQteOverlay.render(event.getGuiGraphics(), minecraft,
                    event.getPartialTick().getGameTimeDeltaPartialTick(false));
            return;
        }
        if (ClientPollutionVisibility.isLocalSuppressed(minecraft)) {
            return;
        }
        if (minecraft.isPaused()) {
            return;
        }
        boolean inMud = isLocalPlayerInSinking(minecraft.player);
        boolean hasDebug = ClientMudDebugOptions.physicsHud() && ClientMudDebugState.currentFor(minecraft.player.getId()) != null;
        boolean partialPurgeVisible = AssimilationPurgeHudRenderer.visible(minecraft);
        if (!inMud && !hasDebug && !partialPurgeVisible) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        if (AssimilationPurgeHudRenderer.render(graphics, minecraft, partialTick)) {
            if (ClientMudDebugOptions.physicsHud()) {
                renderPhysicsDebugHud(graphics, minecraft);
            }
            return;
        }
        boolean renderedSculkHud = SculkMireHudRenderer.render(
                graphics, minecraft, partialTick);
        float charge = Math.min(1.0F,
                struggleCharge / (float) MudStruggleTiming.MAX_CHARGE_TICKS);
        boolean renderedFleshHud = !renderedSculkHud
                && TenderFleshHudRenderer.render(graphics, minecraft, charge);
        if (inMud && !renderedSculkHud && !renderedFleshHud
                && !MudPhysics.isClientPlayerInSculkMire(minecraft.player)) {
            int width = minecraft.getWindow().getGuiScaledWidth();
            int x = StruggleHudLayout.barX(width);
            int y = StruggleHudLayout.barY(minecraft);
            StruggleHudLayout.fillPixelRounded(graphics, x - 1, y - 1,
                    StruggleHudLayout.BAR_WIDTH + 2, StruggleHudLayout.BAR_HEIGHT + 2,
                    0xAA1B1712);
            StruggleHudLayout.fillPixelRounded(graphics, x, y,
                    StruggleHudLayout.BAR_WIDTH, StruggleHudLayout.BAR_HEIGHT, 0xBB3B3023);
            if (charge > 0.0F) {
                StruggleHudLayout.fillPixelRoundedProgress(
                        graphics, x + 2, y + 2, StruggleHudLayout.INNER_WIDTH,
                        StruggleHudLayout.BAR_HEIGHT - 4, charge, 0xFFE0B96C);
            }
        }
        if (inMud && !renderedSculkHud
                && !MudPhysics.isClientPlayerInSculkMire(minecraft.player)
                && struggleCooldownTicks > 0 && struggleCooldownTotalTicks > 0) {
            int x = StruggleHudLayout.barX(
                    minecraft.getWindow().getGuiScaledWidth()) + 2;
            int y = StruggleHudLayout.barY(minecraft) - 2;
            float remaining = Mth.clamp(
                    (struggleCooldownTicks - partialTick)
                            / (float) struggleCooldownTotalTicks,
                    0.0F, 1.0F);
            int width = Math.round(StruggleHudLayout.INNER_WIDTH * remaining);
            if (width > 0) {
                graphics.fill(x, y, x + width, y + 1, 0xFFE7473C);
            }
        }
        if (ClientMudDebugOptions.physicsHud()) {
            renderPhysicsDebugHud(graphics, minecraft);
        }
    }

    private static void reserveStruggleHudSpace(RenderGuiLayerEvent.Pre event) {
        if (!VanillaGuiLayers.PLAYER_HEALTH.equals(event.getName())) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        StruggleHudLayout.captureVanillaHeight(minecraft);
        int bars = 0;
        if (AssimilationPurgeHudRenderer.visible(minecraft)
                || shouldReserveStruggleHudSpace(minecraft)) {
            bars++;
        }
        if (RopeChargeOverlay.isCharging(minecraft)) {
            bars++;
        }
        if (bars == 0) {
            return;
        }
        int lift = StruggleHudLayout.customBarLift(bars);
        minecraft.gui.leftHeight += lift;
        minecraft.gui.rightHeight += lift;
    }

    private static boolean shouldReserveStruggleHudSpace(Minecraft minecraft) {
        if (AssimilationPurgeHudRenderer.visible(minecraft)) {
            return true;
        }
        if (minecraft.player == null
                || ClientPollutionVisibility.isLocalSuppressed(minecraft)
                || !isLocalPlayerInSinking(minecraft.player)) {
            return false;
        }
        if (SculkMireHudRenderer.isVisible(minecraft, 0.0F)
                || MudPhysics.isClientPlayerInTenderFlesh(minecraft.player)) {
            return true;
        }
        return !MudPhysics.isClientPlayerInSculkMire(minecraft.player);
    }

    private static void renderPhysicsDebugHud(GuiGraphics graphics, Minecraft minecraft) {
        MudDebugSyncPayload debug = ClientMudDebugState.currentFor(minecraft.player.getId());
        if (debug == null) {
            return;
        }

        int x = 8;
        int y = 8;
        int line = 10;
        graphics.fill(x - 4, y - 4, x + 318, y + line * 7 + 3, 0x88110F0C);
        drawDebugLine(graphics, minecraft, x, y, "Mirebound: Sinking Depths physics debug");
        double standingHeight = Math.max(0.1D, minecraft.player.getDimensions(Pose.STANDING).height());
        drawDebugLine(graphics, minecraft, x, y + line, String.format(Locale.ROOT,
                "medium=%s depth=%.3f body=%.3f limit=%.3f remain=%.3f",
                debug.medium().serializedName(), debug.depth(), debug.depth() / standingHeight,
                debug.sinkLimit(), debug.remainingDepth()));
        drawDebugLine(graphics, minecraft, x, y + line * 2, String.format(Locale.ROOT,
                "column=%.3f sink=%.4f walk=%.3f vert=%.3f",
                debug.columnDepth(), debug.sinkStep(), debug.walkScale(), debug.verticalScale()));
        drawDebugLine(graphics, minecraft, x, y + line * 3, String.format(Locale.ROOT,
                "vy %.4f -> %.4f hold=%d lift=%d",
                debug.yBefore(), debug.yAfter(), debug.holdTicks(), debug.liftTicks()));
        drawDebugLine(graphics, minecraft, x, y + line * 4, String.format(Locale.ROOT,
                "stuck=%d agitation=%.3f",
                debug.stuckTicks(), debug.agitation()));
        drawDebugLine(graphics, minecraft, x, y + line * 5, String.format(Locale.ROOT,
                "clientY=%.3f clientVy=%.4f",
                minecraft.player.getY(), minecraft.player.getDeltaMovement().y));
        drawDebugLine(graphics, minecraft, x, y + line * 6,
                "log: server prints one line per second while sinking");
    }

    private static void drawDebugLine(GuiGraphics graphics, Minecraft minecraft, int x, int y, String text) {
        graphics.drawString(minecraft.font, text, x, y, 0xFFEAD7A2, true);
    }

    private static void renderMudLayerAfterPlayer(RenderPlayerEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
                || !MireboundClientSettings.clientOptionEnabled(
                        ClientOption.PLAYER_COVERAGE)
                || !minecraft.options.getCameraType().isFirstPerson()) {
            return;
        }
        if (!ClientMudDebugOptions.skinLayer() || !(event.getEntity() instanceof AbstractClientPlayer player) || player != minecraft.player || !shouldRenderMudLayer(minecraft, player)) {
            return;
        }
        if (!ClientRenderCompat.isFirstPersonBodyRendererLikelyLoaded() || MudSkinLayer.wasRenderedThisTick(player)) {
            return;
        }

        boolean slimModel = player.getSkin().model() == PlayerSkin.Model.SLIM;
        ResourceLocation skinTexture = player.getSkin().texture();
        if (MudSkinTextureCache.isGeneratedSkin(skinTexture)) {
            return;
        }
        ResourceLocation mudTexture = MudSkinTextureCache.textureFor(player.getId(), skinTexture, slimModel);
        if (mudTexture == null) {
            return;
        }

        int overlay = LivingEntityRenderer.getOverlayCoords(player, 0.0F);
        MudSkinLayer.renderOverlay(
                event.getRenderer().getModel(),
                event.getPoseStack(),
                event.getMultiBufferSource(),
                event.getPackedLight(),
                overlay,
                mudTexture);
    }

    private static boolean shouldRenderMudLayer(Minecraft minecraft, AbstractClientPlayer player) {
        if (ClientPollutionVisibility.isSuppressed(player)) {
            return false;
        }
        if (!player.isInvisible()) {
            return true;
        }

        // Some first-person body renderers hide the local vanilla player before drawing it.
        return player == minecraft.player
                && minecraft.options.getCameraType().isFirstPerson()
                && ClientRenderCompat.isFirstPersonBodyRendererLikelyLoaded();
    }

    static boolean isLocalPlayerInSinking(Player player) {
        return player.level() != null && MudPhysics.isClientPlayerSinking(player);
    }

    public static boolean isStruggleHudVisible(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null || minecraft.isPaused()
                || ClientPollutionVisibility.isLocalSuppressed(minecraft)
                || ClientAssimilationState.localSoulActive(minecraft)) {
            return false;
        }
        if (AssimilationPurgeHudRenderer.visible(minecraft)) {
            return true;
        }
        if (!isLocalPlayerInSinking(minecraft.player)) {
            return false;
        }
        if (SculkMireHudRenderer.isVisible(minecraft, 0.0F)
                || MudPhysics.isClientPlayerInTenderFlesh(minecraft.player)) {
            return true;
        }
        return !MudPhysics.isClientPlayerInSculkMire(minecraft.player);
    }
}

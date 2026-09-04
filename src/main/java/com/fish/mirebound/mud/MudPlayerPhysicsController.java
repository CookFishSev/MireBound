package com.fish.mirebound.mud;

import com.fish.mirebound.assimilation.AssimilationSystem;
import static com.fish.mirebound.physics.MudMovementControl.clearMudMovement;
import static com.fish.mirebound.physics.MudMovementControl.restoreMudGravity;

import com.fish.mirebound.compat.sable.SableCompat;
import com.fish.mirebound.adaptive.MudVisualSource;
import com.fish.mirebound.compat.sable.SableCompat.SinkingSample;
import com.fish.mirebound.coverage.armor.ArmorTextureMudManager;
import com.fish.mirebound.coverage.MudCoverageService;
import com.fish.mirebound.registry.ModBlocks;
import com.fish.mirebound.registry.ModCriteria;
import com.fish.mirebound.rope.RopeRuntime;
import com.fish.mirebound.splash.MudSplashImpactDetector;
import com.fish.mirebound.stain.MudFootprintSystem;
import com.fish.mirebound.stain.MudWallStainSystem;
import com.fish.mirebound.swarm.SwarmSystem;
import com.fish.mirebound.water.MudWashingSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Authoritative player tick orchestration and player-state lifecycle. */
final class MudPlayerPhysicsController {
    private static final int ACTIVE_PROFILE_RESYNC_TICKS = 100;

    private MudPlayerPhysicsController() {
    }

    static void applyMudEffects(Level level, BlockPos pos, BlockState state, Entity entity, SinkingMedium medium) {
        if (entity instanceof Boat) {
            return;
        }
        if (!(entity instanceof Player player)) {
            if (entity instanceof LivingEntity living) {
                MudMobPhysics.applyMudEffects(living, level, pos, state, medium);
            }
            return;
        }

        if (level.isClientSide()) {
            if (isProjectedSableBlockCallback(player, pos)) {
                MudClientPhysics.wakeSable(player);
            }
            return;
        }

        // Server-side player physics is applied once from PlayerTick.Post. Running it
        // from entityInside() can happen mid-move and makes the sink curve feel constant.
    }

    private static boolean isProjectedSableBlockCallback(Player player, BlockPos pos) {
        if (!SableCompat.isLoaded()) {
            return false;
        }

        // Sable checks sub-level blocks in plot-local coordinates while leaving
        // the entity in world coordinates. Those callbacks must not feed the
        // ordinary-world client prediction path.
        AABB worldBounds = player.getBoundingBox().inflate(0.08D);
        return pos.getX() + 1.0D < worldBounds.minX || pos.getX() > worldBounds.maxX
                || pos.getY() + 1.0D < worldBounds.minY || pos.getY() > worldBounds.maxY
                || pos.getZ() + 1.0D < worldBounds.minZ || pos.getZ() > worldBounds.maxZ;
    }

    static boolean shouldBlockJump(LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            return false;
        }

        if (player.level() == null || player.isSpectator() || player.isPassenger()) {
            return false;
        }
        if (player.level().isClientSide() && MudClientPhysics.enclosureActive(player)) {
            return true;
        }
        if (player instanceof ServerPlayer serverPlayer
                && TenderFleshEnclosureSystem.isActive(serverPlayer)) {
            return true;
        }
        if (player.getAbilities().flying) {
            return false;
        }
        if (player.level().isClientSide() && player.isLocalPlayer() && MudClientPhysics.isSinking(player)) {
            return true;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            MudPlayerData data = MudStateStore.get(serverPlayer);
            if (data.inMud && ticksSince(serverPlayer, data.lastMudTick) <= 1L) {
                return true;
            }
        }
        return findPlayerContact(player) != null;
    }

    static void handleStruggle(ServerPlayer player, boolean pressed, int ignoredClientChargeTicks) {
        MudPlayerData data = MudStateStore.get(player);
        if (MudPollutionSuppression.isSuppressed(player)) {
            clearStruggleState(data);
            return;
        }
        if (pressed) {
            if (data.struggleCooldown > 0) {
                data.holdingStruggle = false;
                data.struggleHold = 0;
                return;
            }
            MudContact contact = findPlayerContact(player);
            if (contact != null) {
                data.inMud = true;
                data.medium = contact.medium();
                data.physicsMedium = contact.physicsMedium();
                data.physicsProfilePos = contact.physicsProfilePos();
                data.depth = contact.depth();
                data.holdingStruggle = true;
                data.struggleHold = Math.max(1, Math.min(data.struggleHold, 20));
            }
            return;
        }

        data.holdingStruggle = false;
        int chargeTicks = MudStruggleTiming.serverChargeTicks(data.struggleHold);
        data.struggleHold = 0;
        MudContact contact = findPlayerContact(player);
        if (contact == null || data.struggleCooldown > 0 || chargeTicks <= 0) {
            return;
        }

        data.inMud = true;
        data.medium = contact.medium();
        data.physicsMedium = contact.physicsMedium();
        data.physicsProfilePos = contact.physicsProfilePos();
        data.depth = contact.depth();
        SinkingMedium medium = contact.physicsMedium();
        if (MudBehaviorContext.sculk(
                player.level(), contact.physicsProfilePos(), medium)) {
            clearStruggleState(data);
            return;
        }
        boolean livingSlime = medium == SinkingMedium.LIVING_SLIME;
        data.struggleCooldown = MudStruggleTiming.cooldownTicks(
                chargeTicks, MudStruggleTiming.configuredMaximumCooldown(
                        player.level(), contact.physicsProfilePos(), medium));
        data.strugglePower = Math.min(5, data.strugglePower + 1);
        double charge = Mth.clamp(
                chargeTicks / (double) MudStruggleTiming.MAX_CHARGE_TICKS, 0.0D, 1.0D);
        SwarmSystem.onStruggle(player, (float) charge);
        data.struggleCharge = (float) charge;
        data.pendingStruggleCharge = (float) charge;
        data.liftTicks = 0;
        data.agitation = Mth.clamp(data.agitation + (livingSlime ? 0.12F : medium.struggleAgitation()) * (0.55F + (float) charge * 0.45F), 0.0F, 1.0F);
        MudSurfaceFeedback.spawn(player.serverLevel(), player.position(), medium, 10 + (int) (charge * 10.0D), 0.35D, 0.055D);
        if (!MudPollutionSuppression.isSuppressed(player)) {
            double depthFactor = Mth.clamp(data.depth / MudPlayerMovement.canonicalStandingHeight(player), 0.0D, 1.35D);
            if (depthFactor > 0.85D) {
                data.setCoverage(Math.max(data.coverage,
                        MudCoverageRules.contactTarget(
                                player.level(), medium, (float) (depthFactor * medium.coverageScale()))));
            } else {
                data.setCoverage(data.coverage - 0.035F);
            }
        }
        MudSurfaceFeedback.playStruggle(player, medium);
        MudCoverageService.sync(player, data, true);
    }

    static void handleSculkMireInput(ServerPlayer player, float movementStrength,
            boolean jumping, boolean crouching) {
        MudPlayerData data = MudStateStore.get(player);
        data.sculkMovementIntent = Mth.clamp(movementStrength, 0.0F, 1.0F);
        data.sculkJumpIntent = jumping;
        data.sculkCrouchIntent = crouching;
        data.sculkInputAge = 0;
        if (data.inMud && MudBehaviorContext.sculk(
                player.level(), data.physicsProfilePos, data.physicsMedium)) {
            player.setShiftKeyDown(crouching);
        }
    }

    static void handleTenderFleshStrike(ServerPlayer player, int pillarIndex) {
        TenderFleshEnclosureSystem.handleStrike(player, pillarIndex);
    }

    /** Prevents vanilla sneak edge protection from treating collision-empty mud as a cliff. */
    static boolean shouldBypassSneakEdgeBackoff(Player player) {
        if (player == null || player.isSpectator() || !player.isShiftKeyDown()) {
            return false;
        }
        if (player.level().isClientSide) {
            return MudClientPhysics.isSinking(player);
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        MudPlayerData data = MudStateStore.get(serverPlayer);
        return data.inMud && ticksSince(serverPlayer, data.lastMudTick) <= 1L;
    }

    /** Sculk clamps own all player translation until their restraint timer releases. */
    static boolean isSculkClampMovementLocked(Player player) {
        if (player == null || player.isSpectator()) {
            return false;
        }
        if (player.level().isClientSide) {
            return MudClientPhysics.sculkClampLocked(player);
        }
        return player instanceof ServerPlayer serverPlayer
                && MudStateStore.get(serverPlayer).sculkMireState.clampActive();
    }

    static void clearStruggleState(MudPlayerData data) {
        data.holdingStruggle = false;
        data.struggleHold = 0;
        data.struggleCharge = 0.0F;
        data.pendingStruggleCharge = -1.0F;
        data.liftTicks = 0;
    }

    static void onPlayerTick(PlayerTickEvent.Post event) {
        Player eventPlayer = event.getEntity();
        if (eventPlayer.level().isClientSide()) {
            if (eventPlayer.isLocalPlayer()) {
                if (eventPlayer.isSpectator()) {
                    MudClientPhysics.clear(eventPlayer);
                } else {
                    MudClientPhysics.tick(eventPlayer);
                }
            }
            return;
        }
        if (!(eventPlayer instanceof ServerPlayer player)) {
            return;
        }

        MudPlayerData data = MudStateStore.get(player);
        data.tickCooldowns();
        data.beginAssimilationContactFrame(player.tickCount);
        if (AssimilationSystem.isFrozen(player)) {
            data.resetMudImpactTracking();
            if (data.gravityOverrideActive) {
                restoreMudGravity(player, data);
            }
            clearMudMovement(player);
            data.inMud = false;
            data.resetPhysicsState();
            if (MudPollutionSuppression.isSuppressed(player, data)) {
                data.clearArmorContacts();
                data.eyeSubmerged = false;
                data.visionObstruction = 0.0F;
                data.clearVisionCoverage();
                MudCoverageService.sync(player, data, false);
                return;
            }

            MudVolumeSnapshot bodyVolumes =
                    MudVolumeContactResolver.nearbySnapshot(player, true);
            boolean bodyContact = MudCoverageSampler.updateFrozenBodyMudCoverage(
                    player, data, bodyVolumes);
            if (MudWashingSystem.washWaterTouchedCoverage(player, data)) {
                // Water cleans only the ordinary overlay; assimilation remains its base layer.
            } else if (data.hasPersistentCoverage()
                    && MudWashingSystem.isPlayerInRain(player)) {
                MudWashingSystem.washRainExposedCoverage(player, data);
            }
            if (!bodyContact) {
                data.fadeNaturalCoverage(player.level());
                ArmorMudManager.fadeNatural(player);
            }
            MudCoverageService.sync(player, data, false);
            return;
        }
        if (player.isSpectator()) {
            data.resetMudImpactTracking();
            if (data.sculkMireState.sunk() || data.sculkMireState.clampActive()) {
                leaveSculkMire(player, data);
            }
            MudPollutionSuppression.suspend(player, data);
            return;
        }
        TenderFleshRuntimeState enclosureState = data.tenderFleshState;
        if (enclosureState.enclosureActive
                && !enclosureState.enclosureRetreating) {
            TenderFleshProfile enclosureProfile = TenderFleshEnclosureSystem.resolveProfile(
                    player, enclosureState);
            if (TenderFleshEnclosureSystem.isOutsideSafety(
                    player, enclosureState, enclosureProfile)) {
                TenderFleshMechanics.beginForcedRetreat(enclosureState);
                TenderFleshEnclosureSystem.clearEffects(player);
                clearMudMovement(player);
                TenderFleshEnclosureSystem.sync(player, data, true);
            } else {
                TenderFleshEnclosureSystem.suppressFlight(player);
                TenderFleshEnclosureSystem.anchorPlayer(player, enclosureState);
            }
        }

        boolean pollutionSuppressed = MudPollutionSuppression.isSuppressed(
                player, data);
        MudContact contact = findPlayerContact(player);
        if (pollutionSuppressed) {
            data.resetMudImpactTracking();
            MudPollutionSuppression.suspend(player, data);
            if (contact != null) {
                player.setDeltaMovement(Vec3.ZERO);
            }
            return;
        }
        MudSplashImpactDetector.ContactFrame physicalImpactFrame =
                contact != null
                        ? new MudSplashImpactDetector.ContactFrame(
                                contact.medium(),
                                contact.surfacePoint(),
                                contact.surfaceNormal(),
                                contact.surfaceAxisX(),
                                contact.surfaceAxisZ(),
                                contact.depth(),
                                MudBlock.heightPixels(
                                        contact.state(), contact.medium()) / 16.0D,
                                contact.sableContext() == null)
                        : null;
        MudSplashImpactDetector.tryImpact(
                player, data, pollutionSuppressed, physicalImpactFrame);
        boolean contactTenderFlesh = contact != null && MudBehaviorContext.tenderFlesh(
                player.level(), contact.physicsProfilePos(), contact.physicsMedium());
        if (contact != null
                && !contactTenderFlesh
                && enclosureState.enclosureRetreating) {
            TenderFleshMechanics.tickRetreat(
                    TenderFleshEnclosureSystem.resolveProfile(player, enclosureState),
                    enclosureState);
            TenderFleshEnclosureSystem.sync(player, data, false);
        }
        if (contact != null
                && data.tenderFleshState.enclosureActive
                && !data.tenderFleshState.enclosureRetreating
                && !contactTenderFlesh
                && TenderFleshEnclosureSystem.maintainWithoutContact(player, data)) {
            return;
        }
        if (contact != null) {
            processPlayerPhysicsContact(player, data, contact);
            if (!contact.pollutionContact()) {
                MudPollutionSuppression.maintainSurfacePass(player, data);
                if (MudWashingSystem.washWaterTouchedCoverage(player, data)) {
                    // Direct water contact still cleans existing coverage while
                    // the player is only resting on the surface.
                } else {
                    MudWashingSystem.washRainExposedCoverage(player, data);
                }
                MudCoverageService.sync(player, data, false);
                return;
            }
            MudCoverageSampler.updateServerMudState(
                    player, player.level(), contact);
            MudWashingSystem.washWaterTouchedCoverage(player, data);
            MudWashingSystem.washRainExposedCoverage(player, data, contact.surfaceY());
            MudFootprintSystem.tick(player, data);
            MudWallStainSystem.tick(player, data);
            MudCoverageService.sync(player, data, false);
            return;
        }
        if (TenderFleshEnclosureSystem.maintainWithoutContact(player, data)) {
            return;
        }
        TenderFleshEnclosureSystem.maintainRetreatWithoutContact(player, data);
        MudVolumeSnapshot volumeSnapshot =
                MudVolumeContactResolver.nearbySnapshot(player, true);
        VolumePhysicsContact volumeContact =
                MudVolumeContactResolver.findPhysicsContact(player, volumeSnapshot);
        if (data.inMud || data.gravityOverrideActive) {
            if (data.sculkMireState.sunk() || data.sculkMireState.clampActive()) {
                leaveSculkMire(player, data);
            }
            boolean leavingLivingSlime = data.physicsMedium == SinkingMedium.LIVING_SLIME
                    && data.livingSlimeState.anchorActive;
            boolean leavingTenderFlesh = MudBehaviorContext.tenderFlesh(
                    player.level(), data.physicsProfilePos, data.physicsMedium);
            PhysicsTraceLog.traceExit(player, data.medium, data.physicsMedium, player.getDeltaMovement(),
                    data.gravityOverrideActive, data.pendingStruggleCharge, data.liftTicks, player.onGround());
            restoreMudGravity(player, data);
            if (player.level().noCollision(player, player.getBoundingBox().move(0.0D, -0.045D, 0.0D))) {
                player.setOnGround(false);
            }
            data.inMud = false;
            data.eyeSubmerged = false;
            data.depth = 0.0D;
            data.visionObstruction = 0.0F;
            data.clearVisionCoverage();
            data.stuckTicks = 0;
            data.liftTicks = 0;
            data.holdingStruggle = false;
            data.struggleHold = 0;
            data.struggleCharge = 0.0F;
            data.hasLookSample = false;
            data.resetPhysicsState();
            if (!leavingLivingSlime) {
                data.resetLivingSlimeState();
            }
            if (leavingTenderFlesh) {
                TenderFleshEnclosureSystem.sync(player, data, true);
            }
        }
        if (data.livingSlimeState.anchorActive) {
            data.livingSlimeState.detach();
        }
        if (volumeContact == null) {
            clearMudMovement(player);
        } else if (!RopeRuntime.isRopeMovementContact(player)) {
            MudVolumeContactResolver.applyResistance(player, volumeContact);
        } else {
            // A rope contact bypasses volume resistance entirely. Remove the
            // speed modifier left by the previous mud tick as well.
            clearMudMovement(player);
        }
        boolean contactOnlyCoverage =
                MudCoverageSampler.updateContactOnlyMudCoverage(
                        player, data, volumeSnapshot);
        if (!contactOnlyCoverage) {
            data.clearArmorContacts();
            data.eyeSubmerged = false;
            data.visionObstruction = 0.0F;
            data.clearVisionCoverage();
        }
        if (MudWashingSystem.washWaterTouchedCoverage(player, data)) {
            // Local water contact wins over rain so mixed water/rain scenes do not double-wash cells.
        } else if (data.hasPersistentCoverage() && MudWashingSystem.isPlayerInRain(player)) {
            MudWashingSystem.washRainExposedCoverage(player, data);
        }

        data.fadeNaturalCoverage(player.level());
        ArmorMudManager.fadeNatural(player);

        MudFootprintSystem.tick(player, data);
        MudWallStainSystem.tick(player, data);
        MudCoverageSampler.syncDebug(player, data, false);
        MudCoverageService.sync(player, data, false);
    }


    private static long ticksSince(ServerPlayer player, int lastTick) {
        return (long) player.tickCount - (long) lastTick;
    }

    static void onLivingBreathe(LivingBreatheEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof ServerPlayer player) {
            MudPlayerData data = MudStateStore.get(player);
            if (data.eyeSubmerged || MudCoverageSampler.isEntityEyeInSinking(player)) {
                event.setCanBreathe(false);
                event.setRefillAirAmount(0);
                event.setConsumeAirAmount(Math.max(event.getConsumeAirAmount(), 1));
            }
            return;
        }

        // LivingBreatheEvent is the authoritative per-tick air path for every
        // living entity. Using it instead of entityInside also catches mobs that
        // stop moving after falling into an empty-collision mud block.
        if (!entity.level().isClientSide()
                && MudCoverageSampler.isEntityEyeInSinking(entity)) {
            event.setCanBreathe(false);
            event.setRefillAirAmount(0);
            event.setConsumeAirAmount(Math.max(event.getConsumeAirAmount(), 1));
        }
    }

    static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            clearMudMovement(player);
            MudPlayerData data = MudStateStore.get(player);
            MudCoverageService.load(player, data);
            MudCoverageService.sync(player, data, true);
            MudPhysicsSettings.syncAll(player);
        }
    }

    static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            clearMudMovement(player);
            MudPlayerData data = MudStateStore.get(player);
            restoreMudGravity(player, data);
            MudCoverageService.save(player, data);
            PhysicsTraceLog.playerLoggedOut(player);
            AnimatedPlayerGeometry.clear(player);
            MudStateStore.remove(player);
        }
    }

    static void onServerStopping(ServerStoppingEvent event) {
        AnimatedPlayerGeometry.clearAll();
        MudStateStore.clear();
    }

    static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getOriginal() instanceof ServerPlayer original) {
            clearMudMovement(player);
            clearMudMovement(original);
            AnimatedPlayerGeometry.clear(player);
            AnimatedPlayerGeometry.clear(original);
            MudPlayerData data = MudStateStore.get(player);
            MudPlayerData originalData = MudStateStore.get(original);
            restoreMudGravity(original, originalData);
            data.gravityOverrideActive = false;
            if (event.isWasDeath()) {
                data.clearAfterDeath();
            } else {
                data.copyPersistentFrom(originalData);
            }
            MudStateStore.remove(original);
            MudCoverageService.save(player, data);
            MudCoverageService.sync(player, data, true);
        }
    }

    private static void processPlayerPhysicsContact(ServerPlayer player, MudPlayerData data, MudContact contact) {
        ModCriteria.enteredMud(player);
        boolean sculkEnabled = MudBehaviorContext.sculk(
                player.level(), contact.physicsProfilePos(), contact.physicsMedium());
        boolean fleshEnabled = MudBehaviorContext.tenderFlesh(
                player.level(), contact.physicsProfilePos(), contact.physicsMedium());
        data.markAssimilationContact(
                contact.medium(), contact.surfaceProfilePos(),
                MudVisualSource.capture(player.level(), contact.surfaceProfilePos()), 1.0F);
        if (contact.physicsMedium() != contact.medium()) {
            data.markAssimilationContact(
                    contact.physicsMedium(), contact.physicsProfilePos(),
                    MudVisualSource.capture(player.level(), contact.physicsProfilePos()), 1.0F);
        }
        if (!sculkEnabled
                && (data.sculkMireState.sunk() || data.sculkMireState.clampActive())) {
            leaveSculkMire(player, data);
        }
        if (sculkEnabled) {
            SculkMireMechanics.updateFrame(data.sculkMireState,
                    contact.surfacePoint(), contact.surfaceNormal(),
                    contact.surfaceAxisX(), contact.surfaceAxisZ());
        }
        if (!fleshEnabled
                && !data.tenderFleshState.enclosureActive
                && !data.tenderFleshState.enclosureRetreating
                && data.tenderFleshState.enclosureCooldownTicks <= 0) {
            data.resetTenderFleshState();
        } else if (fleshEnabled
                && contact.depth() > 0.04D && !data.tenderFleshHintShown) {
            data.tenderFleshHintShown = true;
            player.displayClientMessage(Component.translatable(
                    "message.mirebound.tender_flesh.hint"), true);
        }
        data.medium = contact.medium();
        data.depth = contact.depth();
        syncActivePhysicsProfile(player, data, contact);
        data.physicsProfilePos = contact.physicsProfilePos();
        data.debugPhysicalized = contact.sableContext() != null;
        if (data.lastPhysicsTick != player.tickCount) {
            data.lastPhysicsTick = player.tickCount;
            if (MudPlayerMovement.correctZeroDepthPenetration(player, contact)) {
                data.settlingVelocity = 0.0D;
            }
            if (contact.sableContext() == null) {
                MudPlayerMovement.applyPlayerMovement(
                        player, contact.state(), contact.depth(), contact.depthFactor(),
                        contact.horizontalCoverage(), contact.availableDepth(),
                        contact.layerTopDepth(), contact.layerDepth(), contact.hasDeeperLayer(), data,
                        contact.physicsMedium(), contact.physicsProfilePos(), "world");
            } else {
                MudPlayerMovement.applySablePlayerMovement(player, data, contact);
            }
        }
        MudCoverageSampler.syncDebug(player, data, true);
    }

    private static void syncActivePhysicsProfile(
            ServerPlayer player, MudPlayerData data, MudContact contact) {
        BlockPos profilePos = contact.physicsProfilePos();
        boolean changed = !profilePos.equals(data.lastClientPhysicsProfilePos);
        if (!changed && ticksSince(player, data.lastClientPhysicsProfileSyncTick)
                < ACTIVE_PROFILE_RESYNC_TICKS) {
            return;
        }
        MudLocalProfileSync.sendActiveProfile(
                player, player.serverLevel(), profilePos, contact.physicsMedium());
        data.lastClientPhysicsProfilePos = profilePos.immutable();
        data.lastClientPhysicsProfileSyncTick = player.tickCount;
    }

    static void leaveSculkMire(ServerPlayer player, MudPlayerData data) {
        if (data.sculkMireState.clampActive()) {
            SculkMireMechanics.syncClamp(player,
                    MudPlayerMovement.resolveSculkMireProfile(
                            player, data.physicsProfilePos, data.physicsMedium),
                    data.sculkMireState, false,
                    MudVisualSource.capture(player.level(), data.physicsProfilePos));
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.SCULK_CLICKING_STOP,
                    SoundSource.BLOCKS, 0.34F, 0.78F);
        }
        data.resetSculkMireState();
    }

    private static MudContact findPlayerContact(Player player) {
        return MudContactResolver.findPlayerContact(player);
    }

    private static MudContact findPlayerContact(Player player, boolean includeSable) {
        return MudContactResolver.findPlayerContact(player, includeSable);
    }

}

package com.fish.mirebound.mud;

import static com.fish.mirebound.physics.MudMovementControl.clearMudMovement;
import static com.fish.mirebound.physics.MudMovementControl.restoreMudGravity;
import static com.fish.mirebound.physics.MudMovementControl.updateMudMovementSpeed;

import com.fish.mirebound.coverage.MudCoverageService;
import com.fish.mirebound.network.payload.TenderFleshEnclosurePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Tender-flesh enclosure lifecycle, isolation, interaction, and synchronization. */
final class TenderFleshEnclosureSystem {
    private TenderFleshEnclosureSystem() {
    }

    static void handleStrike(ServerPlayer player, int pillarIndex) {
        if (player.isSpectator() || pillarIndex < 0 || pillarIndex >= 4) {
            return;
        }
        MudPlayerData data = MudStateStore.get(player);
        TenderFleshRuntimeState state = data.tenderFleshState;
        if (!state.enclosureActive || state.enclosureRetreating
                || !state.enclosureCenterSet) {
            return;
        }
        BlockPos profilePos = BlockPos.containing(
                state.enclosureCenterX,
                state.enclosureCenterY - MudContactRules.REQUIRED_PENETRATION,
                state.enclosureCenterZ);
        TenderFleshProfile profile = MudPlayerMovement.resolveTenderFleshProfile(
                player, profilePos);
        int pillarBit = 1 << pillarIndex;
        if (!TenderFleshMechanics.strikePillar(
                profile, state, pillarIndex)) {
            return;
        }
        boolean pillarBroken = (state.enclosureBrokenMask & pillarBit) != 0;
        double damageFraction = TenderFleshMechanics.pillarDamageFraction(
                state.enclosurePillarDamagePacked,
                state.enclosurePillarRequiredHitsPacked,
                pillarIndex);
        player.serverLevel().playSound(
                null,
                player.blockPosition(),
                pillarBroken ? SoundEvents.HONEY_BLOCK_BREAK : SoundEvents.HONEY_BLOCK_HIT,
                SoundSource.BLOCKS,
                (float) Math.max(0.20D, profile.soundVolume() * 1.15D),
                (float) (0.66D + pillarIndex * 0.025D + damageFraction * 0.16D));
        if (state.enclosureRetreating) {
            clearEffects(player);
        }
        sync(player, data, true);
    }

    static boolean maintainWithoutContact(
            ServerPlayer player, MudPlayerData data) {
        TenderFleshRuntimeState state = data.tenderFleshState;
        if (!state.enclosureActive || state.enclosureRetreating
                || !state.enclosurePlayerCenterSet) {
            return false;
        }
        suppressFlight(player);
        Vec3 motion = player.getDeltaMovement();
        anchorPlayer(player, state);
        player.setDeltaMovement(0.0D,
                clampVerticalMotion(player, state, motion.y), 0.0D);
        player.hasImpulse = true;
        updateMudMovementSpeed(player, 0.0D);
        data.lastProcessedTick = player.tickCount;
        data.lastMudTick = player.tickCount;
        data.inMud = true;
        data.physicsMedium = SinkingMedium.TENDER_FLESH;
        player.addEffect(new MobEffectInstance(
                MobEffects.BLINDNESS, 120, 0, false, false, false));
        player.addEffect(new MobEffectInstance(
                MobEffects.WEAKNESS, 120, 0, false, false, false));
        player.addEffect(new MobEffectInstance(
                MobEffects.DIG_SLOWDOWN, 120, 0, false, false, false));
        sync(player, data, false);
        MudCoverageService.sync(player, data, false);
        return true;
    }

    static void maintainRetreatWithoutContact(
            ServerPlayer player, MudPlayerData data) {
        TenderFleshRuntimeState state = data.tenderFleshState;
        if (!state.enclosureRetreating) {
            return;
        }
        TenderFleshMechanics.tickRetreat(
                resolveProfile(player, state), state);
        clearEffects(player);
        restoreMudGravity(player, data);
        clearMudMovement(player);
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
        data.pendingStruggleCharge = -1.0F;
        data.settlingVelocity = 0.0D;
        data.physicsMedium = SinkingMedium.MUD;
        data.physicsMediumFrom = SinkingMedium.MUD;
        data.physicsMediumBlend = 1.0F;
        sync(player, data, false);
        MudCoverageService.sync(player, data, false);
    }

    static void onLivingAttack(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.isSpectator()) {
            return;
        }
        MudPlayerData data = MudStateStore.get(player);
        if (data.tenderFleshState.enclosureActive
                && !data.tenderFleshState.enclosureRetreating
                && (event.getSource().getEntity() != null
                        || event.getSource().getDirectEntity() != null)) {
            event.setCanceled(true);
        }
    }

    static void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player.isSpectator()) {
            return;
        }
        if (player.level().isClientSide) {
            if (MudClientPhysics.enclosureActive(player)) {
                event.setCanceled(true);
            }
        } else if (player instanceof ServerPlayer serverPlayer
                && isActive(serverPlayer)) {
            event.setCanceled(true);
        }
    }

    /** Stop block breaking while the player is inside the closed membrane. */
    static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        if (player.isSpectator()) {
            return;
        }
        if (player.level().isClientSide) {
            if (MudClientPhysics.enclosureActive(player)) {
                event.setCanceled(true);
            }
        } else if (player instanceof ServerPlayer serverPlayer
                && isActive(serverPlayer)) {
            event.setCanceled(true);
        }
    }

    /** The membrane also isolates containers and other block interactions. */
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            if (MudClientPhysics.enclosureActive(player)) {
                event.setCanceled(true);
            }
        } else if (player instanceof ServerPlayer serverPlayer
                && isActive(serverPlayer)) {
            event.setCanceled(true);
        }
    }

    /** Do not allow interaction with entities through the closed membrane. */
    static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            if (MudClientPhysics.enclosureActive(player)) {
                event.setCanceled(true);
            }
        } else if (player instanceof ServerPlayer serverPlayer
                && isActive(serverPlayer)) {
            event.setCanceled(true);
        }
    }

    /** Server fallback for modded tools that bypass the normal left-click event. */
    static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer serverPlayer
                && !serverPlayer.isSpectator()
                && isActive(serverPlayer)) {
            event.setCanceled(true);
        }
    }

    static boolean isActive(ServerPlayer player) {
        MudPlayerData data = MudStateStore.get(player);
        return data.tenderFleshState.enclosureActive
                && !data.tenderFleshState.enclosureRetreating;
    }

    static TenderFleshProfile resolveProfile(
            Player player, TenderFleshRuntimeState state) {
        BlockPos profilePos = state.enclosureCenterSet
                ? BlockPos.containing(
                        state.enclosureCenterX,
                        state.enclosureCenterY - MudContactRules.REQUIRED_PENETRATION,
                        state.enclosureCenterZ)
                : player.blockPosition();
        return MudPlayerMovement.resolveTenderFleshProfile(player, profilePos);
    }

    static boolean isOutsideSafety(
            ServerPlayer player, TenderFleshRuntimeState state,
            TenderFleshProfile profile) {
        if (!state.enclosureCenterSet || !state.enclosurePlayerCenterSet) {
            return true;
        }
        if (state.enclosureDimension != null
                && !state.enclosureDimension.equals(player.level().dimension())) {
            return true;
        }
        double releaseDistance = profile.enclosureForcedReleaseDistance();
        double offsetX = player.getX() - state.enclosurePlayerX;
        double offsetZ = player.getZ() - state.enclosurePlayerZ;
        if (offsetX * offsetX + offsetZ * offsetZ
                > releaseDistance * releaseDistance) {
            return true;
        }
        double upperLimit = state.enclosureCenterY + Math.max(0.50D, releaseDistance * 0.50D);
        double lowerLimit = state.enclosureCenterY
                - Math.max(profile.enclosureMinLayers() + 1.0D, releaseDistance + 1.0D);
        return player.getY() > upperLimit
                || player.getBoundingBox().maxY < lowerLimit;
    }

    static void suppressFlight(Player player) {
        if (!player.getAbilities().flying) {
            return;
        }
        player.getAbilities().flying = false;
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.onUpdateAbilities();
        }
    }

    static void anchorPlayer(
            Player player, TenderFleshRuntimeState state) {
        if (!state.enclosureCenterSet || !state.enclosurePlayerCenterSet) {
            return;
        }
        double ceilingY = tenderFleshEnclosureCeiling(state);
        double anchoredY = Math.min(player.getY(), ceilingY);
        if (Math.abs(player.getX() - state.enclosurePlayerX) > 1.0E-5D
                || Math.abs(player.getY() - anchoredY) > 1.0E-5D
                || Math.abs(player.getZ() - state.enclosurePlayerZ) > 1.0E-5D) {
            player.setPos(state.enclosurePlayerX, anchoredY, state.enclosurePlayerZ);
        }
    }

    static double clampVerticalMotion(
            Player player, TenderFleshRuntimeState state, double motionY) {
        if (motionY <= 0.0D || !state.enclosureCenterSet) {
            return motionY;
        }
        double headroom = tenderFleshEnclosureCeiling(state) - player.getY();
        return Math.min(motionY, Math.max(0.0D, headroom));
    }

    private static double tenderFleshEnclosureCeiling(TenderFleshRuntimeState state) {
        return state.enclosureCenterY - MudContactRules.REQUIRED_PENETRATION;
    }

    static void clearEffects(ServerPlayer player) {
        player.removeEffect(MobEffects.BLINDNESS);
        player.removeEffect(MobEffects.WEAKNESS);
        player.removeEffect(MobEffects.DIG_SLOWDOWN);
    }

    static void sync(
            ServerPlayer player, MudPlayerData data, boolean force) {
        TenderFleshRuntimeState state = data.tenderFleshState;
        float progress = (float) Mth.clamp(state.enclosureProgress, 0.0D, 1.0D);
        boolean phaseChanged = state.enclosureActive != data.lastSyncedTenderFleshActive
                || state.enclosureRetreating != data.lastSyncedTenderFleshRetreating
                || state.enclosureBrokenMask != data.lastSyncedTenderFleshBrokenMask
                || state.enclosurePillarDamagePacked
                        != data.lastSyncedTenderFleshPillarDamagePacked
                || state.enclosurePillarRequiredHitsPacked
                        != data.lastSyncedTenderFleshPillarRequiredHitsPacked;
        boolean anchorChanged = Double.isNaN(data.lastSyncedTenderFleshAnchorX)
                || Math.abs(state.enclosureCenterX - data.lastSyncedTenderFleshAnchorX) > 0.002D
                || Math.abs(state.enclosureCenterY - data.lastSyncedTenderFleshAnchorY) > 0.002D
                || Math.abs(state.enclosureCenterZ - data.lastSyncedTenderFleshAnchorZ) > 0.002D
                || Double.isNaN(data.lastSyncedTenderFleshPlayerX)
                || Math.abs(state.enclosurePlayerX - data.lastSyncedTenderFleshPlayerX) > 0.002D
                || Math.abs(state.enclosurePlayerZ - data.lastSyncedTenderFleshPlayerZ) > 0.002D;
        boolean progressChanged = Math.abs(
                progress - data.lastSyncedTenderFleshProgress) >= 0.018F;
        long elapsed = ticksSince(player, data.lastTenderFleshEnclosureSyncTick);
        if (!force && !phaseChanged && !anchorChanged
                && !(progressChanged && elapsed >= 3L)
                && elapsed < 20L) {
            return;
        }
        data.lastTenderFleshEnclosureSyncTick = player.tickCount;
        data.lastSyncedTenderFleshActive = state.enclosureActive;
        data.lastSyncedTenderFleshRetreating = state.enclosureRetreating;
        data.lastSyncedTenderFleshBrokenMask = state.enclosureBrokenMask;
        data.lastSyncedTenderFleshPillarDamagePacked = state.enclosurePillarDamagePacked;
        data.lastSyncedTenderFleshPillarRequiredHitsPacked =
                state.enclosurePillarRequiredHitsPacked;
        data.lastSyncedTenderFleshCooldownTicks = state.enclosureCooldownTicks;
        data.lastSyncedTenderFleshProgress = progress;
        data.lastSyncedTenderFleshAnchorX = state.enclosureCenterX;
        data.lastSyncedTenderFleshAnchorY = state.enclosureCenterY;
        data.lastSyncedTenderFleshAnchorZ = state.enclosureCenterZ;
        data.lastSyncedTenderFleshPlayerX = state.enclosurePlayerX;
        data.lastSyncedTenderFleshPlayerZ = state.enclosurePlayerZ;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                player,
                new TenderFleshEnclosurePayload(
                        player.getId(),
                        state.enclosureActive,
                        state.enclosureRetreating,
                        state.enclosureBrokenMask,
                        state.enclosurePillarDamagePacked,
                        state.enclosurePillarRequiredHitsPacked,
                        state.enclosureCooldownTicks,
                        progress,
                        state.enclosureCenterX,
                        state.enclosureCenterY,
                        state.enclosureCenterZ,
                        state.enclosurePlayerX,
                        state.enclosurePlayerZ));
    }

    private static long ticksSince(ServerPlayer player, int lastTick) {
        return (long) player.tickCount - (long) lastTick;
    }
}

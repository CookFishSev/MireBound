package com.fish.mirebound.mud;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Stable event and compatibility facade for the split mud runtime. */
public final class MudPhysics {
    private MudPhysics() {
    }

    public static void applyMudEffects(
            Level level, BlockPos pos, BlockState state,
            Entity entity, SinkingMedium medium) {
        MudPlayerPhysicsController.applyMudEffects(
                level, pos, state, entity, medium);
    }

    public static boolean shouldBlockJump(LivingEntity entity) {
        return MudPlayerPhysicsController.shouldBlockJump(entity);
    }

    public static void handleStruggle(
            ServerPlayer player, boolean pressed, int clientChargeTicks) {
        MudPlayerPhysicsController.handleStruggle(
                player, pressed, clientChargeTicks);
    }

    public static void handleSculkMireInput(
            ServerPlayer player, float movementStrength,
            boolean jumping, boolean crouching) {
        MudPlayerPhysicsController.handleSculkMireInput(
                player, movementStrength, jumping, crouching);
    }

    public static void handleTenderFleshStrike(
            ServerPlayer player, int pillarIndex) {
        TenderFleshEnclosureSystem.handleStrike(player, pillarIndex);
    }

    public static boolean shouldBypassSneakEdgeBackoff(Player player) {
        return MudPlayerPhysicsController.shouldBypassSneakEdgeBackoff(player);
    }

    public static boolean isSculkClampMovementLocked(Player player) {
        return MudPlayerPhysicsController.isSculkClampMovementLocked(player);
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        MudPlayerPhysicsController.onPlayerTick(event);
    }

    public static void setClientPollutionSuppressed(
            ServerPlayer player, boolean suppressed) {
        MudPollutionSuppression.set(player, suppressed);
    }

    public static boolean isPollutionSuppressed(ServerPlayer player) {
        return MudPollutionSuppression.isSuppressed(player);
    }

    static boolean suppressionLeaseExpired(
            int currentTick, int deadlineTick) {
        return MudPollutionSuppression.leaseExpired(
                currentTick, deadlineTick);
    }

    public static int queueClientStruggle(Player player, int chargeTicks) {
        return MudClientPhysics.queueStruggle(player, chargeTicks);
    }

    public static void updateClientInput(Player player, boolean jumping) {
        MudClientPhysics.updateInput(player, jumping);
    }

    public static void resetClientPhysicsState() {
        MudClientPhysics.reset();
        MudCoverageSampler.resetThreadState();
    }

    public static void setClientPlayerPhysicsSuspended(Player player, boolean suspended) {
        MudClientPhysics.setSuspended(player, suspended);
    }

    public static void setClientExternalCameraPhysicsSuspended(
            Player player, boolean suspended) {
        MudClientPhysics.setExternalCameraSuspended(player, suspended);
    }

    public static boolean isClientPlayerSinking(Player player) {
        return MudClientPhysics.isSinking(player);
    }

    public static boolean isClientPlayerInSculkMire(Player player) {
        return MudClientPhysics.isInSculk(player);
    }

    public static boolean isClientPlayerInTenderFlesh(Player player) {
        return MudClientPhysics.isInTenderFlesh(player);
    }

    public static boolean isClientTenderFleshEnclosureActive(Player player) {
        return MudClientPhysics.enclosureActive(player);
    }

    public static float clientTenderFleshEscapeOpportunity(Player player) {
        return MudClientPhysics.tenderEscapeOpportunity(player);
    }

    public static float clientTenderFleshReleaseThreshold(Player player) {
        return MudClientPhysics.tenderReleaseThreshold(player);
    }

    public static float clientTenderFleshContraction(Player player) {
        return MudClientPhysics.tenderContraction(player);
    }

    public static float clientTenderFleshWrap(Player player) {
        return MudClientPhysics.tenderWrap(player);
    }

    public static float clientTenderFleshPressure(Player player) {
        return MudClientPhysics.tenderPressure(player);
    }

    public static float clientTenderFleshCalmness(Player player) {
        return MudClientPhysics.tenderCalmness(player);
    }

    public static float clientSculkEscapeProgress(Player player) {
        return MudClientPhysics.sculkEscapeProgress(player);
    }

    public static void updateClientSculkClampState(
            int entityId, boolean active, int remainingTicks) {
        MudClientPhysics.updateSculkClamp(
                entityId, active, remainingTicks);
    }

    public static void updateClientTenderFleshEnclosureState(
            int entityId, boolean active, boolean retreating,
            int brokenMask, int pillarDamagePacked,
            int pillarRequiredHitsPacked, int cooldownTicks,
            float progress, double anchorX, double anchorY,
            double anchorZ, double playerX, double playerZ) {
        MudClientPhysics.updateTenderEnclosure(
                entityId, active, retreating, brokenMask,
                pillarDamagePacked, pillarRequiredHitsPacked,
                cooldownTicks, progress, anchorX, anchorY, anchorZ,
                playerX, playerZ);
    }

    public static ClientSurfaceContact clientSurfaceContact(Player player) {
        return MudClientPhysics.surfaceContact(player);
    }

    public static boolean hasSinkingContact(Player player) {
        return player != null && MudContactResolver.findPlayerContact(player) != null;
    }

    public static void onLivingBreathe(LivingBreatheEvent event) {
        MudPlayerPhysicsController.onLivingBreathe(event);
    }

    public static void onLivingAttack(LivingIncomingDamageEvent event) {
        TenderFleshEnclosureSystem.onLivingAttack(event);
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        MudPlayerPhysicsController.onPlayerLoggedIn(event);
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        MudPlayerPhysicsController.onPlayerLoggedOut(event);
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        MudPlayerPhysicsController.onServerStopping(event);
    }

    public static void onPlayerClone(PlayerEvent.Clone event) {
        MudPlayerPhysicsController.onPlayerClone(event);
    }

    public static void onAttackEntity(AttackEntityEvent event) {
        TenderFleshEnclosureSystem.onAttackEntity(event);
    }

    public static void onLeftClickBlock(
            PlayerInteractEvent.LeftClickBlock event) {
        TenderFleshEnclosureSystem.onLeftClickBlock(event);
    }

    public static void onRightClickBlock(
            PlayerInteractEvent.RightClickBlock event) {
        TenderFleshEnclosureSystem.onRightClickBlock(event);
    }

    public static void onEntityInteract(
            PlayerInteractEvent.EntityInteract event) {
        TenderFleshEnclosureSystem.onEntityInteract(event);
    }

    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        TenderFleshEnclosureSystem.onBlockBreak(event);
    }

    public static boolean applyMudSplashToPlayer(
            ServerPlayer player, Vec3 impact, float radius,
            float strength, SinkingMedium medium) {
        return MudCoverageSampler.applyMudSplashToPlayer(
                player, impact, radius, strength, medium);
    }

    public static boolean applyMudSplashToPlayer(
            ServerPlayer player, Vec3 impact, float radius,
            float strength, SinkingMedium medium,
            boolean forceOrdinaryCoverage) {
        return applyMudSplashToPlayer(player, impact, radius, strength,
                medium, 0L, forceOrdinaryCoverage);
    }

    public static boolean applyMudSplashToPlayer(
            ServerPlayer player, Vec3 impact, float radius,
            float strength, SinkingMedium medium, long visualSource,
            boolean forceOrdinaryCoverage) {
        return MudCoverageSampler.applyMudSplashToPlayer(
                player, impact, radius, strength, medium, visualSource,
                forceOrdinaryCoverage);
    }

    public static boolean applyMudClodToPlayer(
            ServerPlayer player, Vec3 impact, float radius,
            float strength, SinkingMedium medium, long visualSource,
            boolean forceOrdinaryCoverage) {
        return MudCoverageSampler.applyMudClodToPlayer(
                player, impact, radius, strength, medium, visualSource,
                forceOrdinaryCoverage);
    }

    public record ClientSurfaceContact(
            SinkingMedium medium,
            Vec3 surfacePoint,
            Vec3 surfaceNormal,
            Vec3 surfaceAxisX,
            Vec3 surfaceAxisZ,
            BlockPos surfaceProfilePos,
            double depth,
            double availableDepth,
            float agitation,
            double horizontalSpeed,
            double walkScale,
            boolean physicalized,
            double clipNegativeX,
            double clipPositiveX,
            double clipNegativeZ,
            double clipPositiveZ) {
    }
}

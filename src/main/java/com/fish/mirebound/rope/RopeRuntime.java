package com.fish.mirebound.rope;

import com.fish.mirebound.network.payload.RopeSnapshotPayload;
import com.fish.mirebound.network.payload.RopeDragPayload;
import com.fish.mirebound.network.payload.RopeAnchorPayload;
import com.fish.mirebound.network.payload.RopeBreakPayload;
import com.fish.mirebound.network.payload.RopeExtendPayload;
import com.fish.mirebound.network.payload.RopeConnectPayload;
import com.fish.mirebound.network.payload.RopeRescueHaulPayload;
import com.fish.mirebound.network.payload.RopeRescueHaulStatePayload;
import com.fish.mirebound.network.payload.RopeClimbInputPayload;
import com.fish.mirebound.network.payload.RopeInteractionReleasePayload;
import com.fish.mirebound.mud.MudPhysicsSettings;
import com.fish.mirebound.registry.ModMudworkContent;
import com.fish.mirebound.registry.ModBlocks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server owner for non-entity rope chains. */
public final class RopeRuntime {
    private static final int MAX_ROPES_PER_LEVEL = 64;
    private static final int SNAPSHOT_INTERVAL = 1;
    private static final double TRACKING_DISTANCE = 96.0D;
    private static final int DRAG_INPUT_TIMEOUT_TICKS = 30;
    private static final int BREAK_INPUT_TIMEOUT_TICKS = 3;
    private static final int SURVIVAL_BREAK_TICKS = 15;
    private static final double MAX_VIEW_ORIGIN_ERROR = 1.25D;
    private static final int LASSO_SEGMENTS = 5;
    private static final int MINIMUM_RESCUE_SEGMENTS = LASSO_SEGMENTS + 1;
    private static final int MAXIMUM_LASSO_FLIGHT_TICKS = 60;
    private static final double LASSO_GRAVITY = 0.035D;
    private static final double LASSO_VELOCITY_DAMPING = 0.985D;
    private static final double RESCUE_TAUT_START = 0.92D;
    private static final int RESCUE_HAUL_INPUT_TIMEOUT_TICKS = 20;
    private static final double RESCUE_HAUL_BODY_HEIGHT = 0.62D;
    private static final double RESCUE_GRIP_ARRIVAL_RADIUS = 0.20D;
    private static final double RESCUE_GRIP_PROGRESS_DISTANCE = 0.035D;
    private static final double RESCUE_HAUL_MAX_SPEED = 0.075D;
    private static final int CLIMB_INPUT_TIMEOUT_TICKS = 5;
    private static final Map<ServerLevel, LevelRopes> LEVELS = new WeakHashMap<>();
    private static final Map<UUID, RescueCastIntent> RESCUE_CASTS = new HashMap<>();
    private static final Map<UUID, ClimbInput> CLIMB_INPUTS = new HashMap<>();
    private static final Map<UUID, ClimbContact> CLIMB_CONTACT_CACHE = new HashMap<>();

    private RopeRuntime() {
    }

    public static void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || LEVELS.containsKey(level)) {
            return;
        }
        RopeSavedData saved = RopeSavedData.get(level);
        if (saved.states().isEmpty()) {
            return;
        }
        LevelRopes ropes = new LevelRopes(saved.nextId());
        for (RopeSavedData.State state : saved.states()) {
            if (ropes.chains.size() >= MAX_ROPES_PER_LEVEL) {
                break;
            }
            ActiveRope restored = ActiveRope.restore(state, level);
            if (restored != null) {
                ropes.chains.add(restored);
            }
        }
        if (ropes.chains.isEmpty()) {
            saved.replace(ropes.nextId, List.of());
        } else {
            LEVELS.put(level, ropes);
        }
    }

    public static boolean throwRope(ServerPlayer owner, float charge, InteractionHand hand,
            int segmentCount) {
        return throwRope(owner, charge, hand, segmentCount, false);
    }

    public static boolean throwRope(ServerPlayer owner, float charge, InteractionHand hand,
            int segmentCount, boolean rescueCast) {
        if (segmentCount < 1 || segmentCount > RopeProperties.MAX_SEGMENTS) {
            return false;
        }
        if (rescueCast && segmentCount < MINIMUM_RESCUE_SEGMENTS) {
            owner.level().playSound(null, owner.blockPosition(),
                    SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS,
                    0.7F, 0.7F);
            return false;
        }
        ServerLevel level = owner.serverLevel();
        LevelRopes ropes = LEVELS.computeIfAbsent(level,
                ignored -> new LevelRopes(RopeSavedData.get(level).nextId()));
        if (ropes.chains.size() >= MAX_ROPES_PER_LEVEL) {
            return false;
        }
        RopeProperties properties = RopeProperties.DEFAULT.withSegmentCount(segmentCount);
        Vec3 look = owner.getLookAngle().normalize();
        Vec3 origin = handPosition(owner, hand, look);
        ActiveRope active;
        if (rescueCast) {
            int lassoFirstNode = segmentCount - LASSO_SEGMENTS;
            Vec3 right = horizontalPerpendicular(look);
            Vec3 up = right.cross(look).normalize();
            Vec3 center = origin.add(look.scale(0.9D));
            Vec3[] lasso = lassoPoints(center, right, up, 0.0D);
            RopeChain chain = RopeChain.rescueThrown(
                    properties, origin, look, charge, lassoFirstNode, lasso);
            double tipSpeed = Mth.lerp(Mth.clamp(charge, 0.0F, 1.0F),
                    0.35D, (float) properties.maximumThrowSpeed());
            active = ActiveRope.rescue(
                    ropes.nextId++, owner, hand, chain, lassoFirstNode,
                    center, look.scale(tipSpeed), right, up);
        } else {
            active = new ActiveRope(
                    ropes.nextId++, owner.getUUID(),
                    RopeChain.thrown(properties, origin, look, charge));
        }
        ropes.chains.add(active);
        active.send(level, false);
        level.playSound(null, origin.x, origin.y, origin.z,
                SoundEvents.LEASH_KNOT_PLACE, SoundSource.PLAYERS, 0.8F, 1.0F);
        ropes.persist(level);
        return true;
    }

    public static void setRescueCastArmed(ServerPlayer player, boolean armed) {
        if (player == null) {
            return;
        }
        if (!armed) {
            RESCUE_CASTS.remove(player.getUUID());
            return;
        }
        if (isHoldingTuningWand(player)) {
            return;
        }
        if (!player.isUsingItem() || !(player.getUseItem().getItem() instanceof RopeItem)) {
            return;
        }
        RESCUE_CASTS.put(player.getUUID(), new RescueCastIntent(
                player.getUsedItemHand(), player.serverLevel().getGameTime()));
    }

    public static boolean consumeRescueCast(ServerPlayer player, InteractionHand hand) {
        RescueCastIntent intent = RESCUE_CASTS.remove(player.getUUID());
        return intent != null && intent.hand() == hand
                && player.serverLevel().getGameTime() - intent.gameTime() <= 200L;
    }

    private static Vec3 handPosition(ServerPlayer owner, InteractionHand hand, Vec3 look) {
        Vec3 bodyForward = owner.calculateViewVector(0.0F, owner.getYRot()).normalize();
        Vec3 handDirection = owner.calculateViewVector(0.0F,
                owner.getYRot() + 90.0F).normalize();
        boolean rightHand = hand == InteractionHand.MAIN_HAND
                ? owner.getMainArm() == HumanoidArm.RIGHT
                : owner.getMainArm() == HumanoidArm.LEFT;
        double side = rightHand ? 1.0D : -1.0D;
        return owner.position().add(0.0D, 1.28D, 0.0D)
                .add(bodyForward.scale(1.275D))
                .add(handDirection.scale(0.325D * side))
                .add(look.subtract(bodyForward).scale(0.08D));
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            LevelRopes ropes = LEVELS.get(level);
            if (ropes == null) {
                continue;
            }
            ropes.tick(level);
            if (level.getGameTime() % 10L == 0L) {
                ropes.persist(level);
            }
            if (ropes.chains.isEmpty()) {
                ropes.persist(level);
                LEVELS.remove(level);
            }
        }
    }

    /** Applies rope-owned player motion after every mud movement branch has finished. */
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        applyRescueMotion(player);
        if (findRescueHaul(player) != null) {
            CLIMB_INPUTS.remove(player.getUUID());
            return;
        }
        boolean hasClimbContact = isClimbingContact(player);
        ClimbInput input = CLIMB_INPUTS.get(player.getUUID());
        if (input == null) {
            return;
        }
        long now = player.serverLevel().getGameTime();
        if (now - input.gameTime() > CLIMB_INPUT_TIMEOUT_TICKS
                || player.isSpectator() || player.isDeadOrDying()
                || player.getAbilities().flying || isHoldingTuningWand(player)) {
            CLIMB_INPUTS.remove(player.getUUID());
            return;
        }
        if (!hasClimbContact) {
            return;
        }
        player.setDeltaMovement(RopeClimbing.motion(
                player.getDeltaMovement(), input.jumping(), input.crouching()));
        player.resetFallDistance();
    }

    /** Applies the one server-authoritative rescue displacement for this tick. */
    private static void applyRescueMotion(ServerPlayer player) {
        LevelRopes ropes = LEVELS.get(player.serverLevel());
        if (ropes == null) {
            return;
        }
        for (ActiveRope rope : ropes.chains) {
            if (!rope.isRescueHauling(player)) {
                continue;
            }
            Vec3 motion = rope.updateRescueHaul(player);
            if (motion == null || motion.lengthSqr() <= 1.0E-10D) {
                return;
            }
            player.setDeltaMovement(rescueVelocity(
                    player.getDeltaMovement(), motion));
            player.resetFallDistance();
            player.hasImpulse = true;
            player.hurtMarked = true;
            return;
        }
    }

    /** Returns the cached geometric ladder contact for the current server tick. */
    public static boolean isClimbingContact(ServerPlayer player) {
        if (player == null || player.isSpectator() || player.isDeadOrDying()
                || isHoldingTuningWand(player)) {
            return false;
        }
        UUID playerId = player.getUUID();
        ServerLevel level = player.serverLevel();
        long gameTime = player.serverLevel().getGameTime();
        ClimbContact cached = CLIMB_CONTACT_CACHE.get(playerId);
        if (cached != null && cached.level() == level && cached.gameTime() == gameTime) {
            return cached.active();
        }
        boolean active = findClimbContact(player) != null;
        CLIMB_CONTACT_CACHE.put(playerId, new ClimbContact(level, gameTime, active));
        return active;
    }

    /** Mud uses the same current-tick rope contact gate for all movement paths. */
    public static boolean isRopeMovementContact(ServerPlayer player) {
        if (player == null || player.isSpectator() || player.isDeadOrDying()
                || isHoldingTuningWand(player)) {
            return false;
        }
        return isClimbingContact(player) || findRescueHaul(player) != null;
    }

    private static ActiveRope findRescueHaul(ServerPlayer player) {
        LevelRopes ropes = LEVELS.get(player.serverLevel());
        if (ropes == null) {
            return null;
        }
        for (ActiveRope rope : ropes.chains) {
            if (rope.isRescueHauling(player)) {
                return rope;
            }
        }
        return null;
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID playerId = event.getEntity().getUUID();
        CLIMB_INPUTS.remove(playerId);
        CLIMB_CONTACT_CACHE.remove(playerId);
        RESCUE_CASTS.remove(playerId);
        for (LevelRopes ropes : LEVELS.values()) {
            ropes.clearPlayerRescueState(playerId);
        }
    }

    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            LevelRopes ropes = LEVELS.remove(level);
            if (ropes != null) {
                ropes.persist(level);
            }
        }
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        for (Map.Entry<ServerLevel, LevelRopes> entry : LEVELS.entrySet()) {
            entry.getValue().persist(entry.getKey());
        }
        LEVELS.clear();
        RESCUE_CASTS.clear();
        CLIMB_INPUTS.clear();
        CLIMB_CONTACT_CACHE.clear();
    }

    public static void handleDrag(ServerPlayer player, RopeDragPayload payload) {
        if (player.isSpectator() || payload.inputSession() <= 0L
                || payload.inputSequence() <= 0L
                || payload.dragging() && findRescueHaul(player) != null
                || (isHoldingTuningWand(player) && payload.dragging())) {
            return;
        }
        LevelRopes ropes = LEVELS.get(player.serverLevel());
        if (ropes == null) {
            return;
        }
        ActiveRope rope = ropes.find(payload.ropeId());
        if (rope == null) {
            return;
        }
        if (rope.isRescueHauling()) {
            return;
        }
        if (!payload.dragging()) {
            if (!rope.acceptsRelease(player, payload)) {
                return;
            }
            rope.queueDrag(new PendingDrag(player.getUUID(), payload.segmentIndex(),
                    payload.frame(), payload.viewOrigin(), payload.viewDirection(),
                    payload.inputSession(), payload.inputSequence(), false));
            return;
        }
        if (!emptyHands(player) || !validSegment(rope.chain, payload.segmentIndex())
                || payload.frame() == null
                || !validVector(payload.viewOrigin())
                || !validDirection(payload.viewDirection())
                || !rope.acceptsActive(player, payload)) {
            return;
        }
        rope.queueDrag(new PendingDrag(player.getUUID(), payload.segmentIndex(),
                payload.frame(), payload.viewOrigin(), payload.viewDirection(),
                payload.inputSession(), payload.inputSequence(), true));
    }

    private static void applyDragIntent(ServerPlayer player, PendingDrag pending,
            ActiveRope rope) {
        if (rope.dragPlayerId != null
                && !rope.dragPlayerId.equals(player.getUUID())) {
            return;
        }
        Vec3 origin = pending.viewOrigin();
        Vec3 eye = player.getEyePosition();
        if (!validVector(origin)
                || origin.distanceTo(eye) > MAX_VIEW_ORIGIN_ERROR) {
            return;
        }
        Vec3 look = pending.viewDirection().normalize();
        double reach = Math.max(0.0D, player.blockInteractionRange());
        if (RopeProperties.GRAB_DISTANCE > reach || pending.frame() == null
                || pending.inputSession() <= 0L || pending.inputSequence() <= 0L) {
            return;
        }
        Vec3 current = rope.chain.segmentCenter(pending.segmentIndex());
        boolean firstInput = rope.dragPlayerId == null;
        if (current == null || (firstInput
                && (!hasLineOfSight(player, current)
                        || origin.distanceTo(current)
                                > reach + MAX_VIEW_ORIGIN_ERROR))) {
            return;
        }
        if (dragDistanceExceeded(eye, current,
                MudPhysicsSettings.ropeMaximumDragDistance())) {
            if (rope.dragging) {
                rope.stopDragAndNotify(player);
            } else {
                rope.notifyInteractionRelease(player, false, pending.segmentIndex());
            }
            return;
        }
        Vec3 target = origin.add(look.scale(RopeProperties.GRAB_DISTANCE));
        Vec3 constrainedTarget = rope.chain.clampDragTarget(
                pending.segmentIndex(), target, pending.frame());
        if (rope.chain.setDragTarget(
                pending.segmentIndex(), constrainedTarget, pending.frame())) {
            if (rope.rescueState == RescueStateMachine.State.ANCHORED
                    && rope.chain.rescueLassoFirstSegment() < 1) {
                rope.clearRescueAnchor(player.serverLevel());
            }
            rope.dragPlayerId = player.getUUID();
            rope.dragging = true;
            rope.lastDragInputTick = player.serverLevel().getGameTime();
        }
    }

    public static void handleAnchor(ServerPlayer player, RopeAnchorPayload payload) {
        if (!player.isCreative() || player.isSpectator()
                || isHoldingTuningWand(player) || findRescueHaul(player) != null) {
            return;
        }
        LevelRopes ropes = LEVELS.get(player.serverLevel());
        if (ropes == null) {
            return;
        }
        ActiveRope rope = ropes.find(payload.ropeId());
        if (rope != null) {
            rope.activatePendingDrag(player, payload.segmentIndex());
        }
        if (rope == null || !rope.dragging
                || !player.getUUID().equals(rope.dragPlayerId)
                || !emptyHands(player)
                || !validSegment(rope.chain, payload.segmentIndex())
                || rope.chain.segmentCenter(payload.segmentIndex()) == null
                || player.getEyePosition().distanceTo(
                        rope.chain.segmentCenter(payload.segmentIndex()))
                        > Math.max(0.0D, player.blockInteractionRange())
                || !hasLineOfSight(player,
                        rope.chain.segmentCenter(payload.segmentIndex()))) {
            return;
        }
        if (rope.chain.anchorSegment(payload.segmentIndex())) {
            rope.dragging = false;
            rope.dragPlayerId = null;
            rope.lastDragInputTick = Long.MIN_VALUE;
            rope.send(player.serverLevel(), false);
        }
    }

    public static void handleBreak(ServerPlayer player, RopeBreakPayload payload) {
        if (player.isSpectator()) {
            return;
        }
        if ((isHoldingTuningWand(player) || findRescueHaul(player) != null)
                && payload.breaking()) {
            return;
        }
        LevelRopes ropes = LEVELS.get(player.serverLevel());
        ActiveRope rope = ropes == null ? null : ropes.find(payload.ropeId());
        if (rope == null || !validSegment(rope.chain, payload.segmentIndex())
                || !withinReach(player, rope.chain.segmentCenter(payload.segmentIndex()))
                || !hasLineOfSight(player, rope.chain.segmentCenter(payload.segmentIndex()))) {
            return;
        }
        if (!payload.breaking()) {
            if (player.getUUID().equals(rope.breakPlayerId)) {
                rope.clearBreak();
            }
            return;
        }
        if (rope.breakPlayerId == null
                || !rope.breakPlayerId.equals(player.getUUID())
                || rope.breakSegment != payload.segmentIndex()
                || rope.breakAllConnected != payload.allConnected()) {
            rope.breakPlayerId = player.getUUID();
            rope.breakSegment = payload.segmentIndex();
            rope.breakAllConnected = payload.allConnected();
            rope.breakTicks = 0;
            rope.breakStartTick = player.serverLevel().getGameTime();
            player.swing(InteractionHand.MAIN_HAND, true);
        } else if (!player.swinging
                || player.swingTime >= player.getCurrentSwingDuration() / 2) {
            player.swing(InteractionHand.MAIN_HAND, true);
        }
        rope.lastBreakInputTick = player.serverLevel().getGameTime();
        rope.breakTicks++;
        int requiredBreakTicks = breakDurationTicks(payload.allConnected());
        if (player.isCreative()
                || player.serverLevel().getGameTime() - rope.breakStartTick
                        >= requiredBreakTicks - 1) {
            ropes.breakChain(player.serverLevel(), rope, rope.breakSegment,
                    rope.breakAllConnected);
        }
    }

    public static void handleExtend(ServerPlayer player, RopeExtendPayload payload) {
        if (player.isSpectator() || isHoldingTuningWand(player)
                || findRescueHaul(player) != null) {
            return;
        }
        LevelRopes ropes = LEVELS.get(player.serverLevel());
        ActiveRope rope = ropes == null ? null : ropes.find(payload.ropeId());
        ItemStack held = heldRope(player);
        if (rope == null || held == null || rope.dragging
                || !validSegment(rope.chain, payload.endpointSegment())
                || (payload.endpointSegment() != 0
                        && payload.endpointSegment() != rope.chain.segmentCount() - 1)
                || !rope.chain.canExtendAt(payload.endpointSegment())
                || !withinReach(player,
                        rope.chain.segmentCenter(payload.endpointSegment()))
                || !hasLineOfSight(player,
                        rope.chain.segmentCenter(payload.endpointSegment()))) {
            return;
        }
        boolean atStart = payload.endpointSegment() == 0;
        RopeChain extended = rope.chain.extended(atStart);
        if (extended == null) {
            return;
        }
        Vec3 extensionPoint = rope.chain.segmentCenter(payload.endpointSegment());
        rope.chain = extended;
        rope.collision = null;
        rope.nextCollisionCapture = 0;
        if (!player.isCreative()) {
            held.shrink(1);
        }
        player.swing(player.getMainHandItem().is(ModMudworkContent.ROPE.get())
                ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, true);
        rope.send(player.serverLevel(), false, 1);
        player.serverLevel().playSound(null, extensionPoint.x, extensionPoint.y,
                extensionPoint.z, SoundEvents.LEASH_KNOT_PLACE, SoundSource.PLAYERS,
                0.8F, 1.0F);
        ropes.persist(player.serverLevel());
    }

    public static void handleConnect(ServerPlayer player, RopeConnectPayload payload) {
        if (player.isSpectator() || isHoldingTuningWand(player) || !emptyHands(player)
                || findRescueHaul(player) != null) {
            return;
        }
        LevelRopes ropes = LEVELS.get(player.serverLevel());
        if (ropes == null || payload.sourceRopeId() == payload.targetRopeId()) {
            return;
        }
        ActiveRope source = ropes.find(payload.sourceRopeId());
        ActiveRope target = ropes.find(payload.targetRopeId());
        if (source != null) {
            // A connect packet can arrive in the same server task as the first
            // drag packet. Apply that pending intent before validating the
            // source state so a valid endpoint connection is not lost.
            source.activatePendingDrag(player, payload.sourceSegment());
        }
        if (source == null || target == null
                || !source.dragging
                || !player.getUUID().equals(source.dragPlayerId)
                || source.chain.draggedSegment() != payload.sourceSegment()
                || !source.chain.canConnectAt(payload.sourceSegment())
                || !target.chain.canConnectAt(payload.targetSegment())
                || source.chain.rescueLassoFirstSegment() >= 0
                || target.chain.rescueLassoFirstSegment() >= 0) {
            return;
        }
        if (!aimsAtSegment(player, target.chain, payload.targetSegment())) {
            return;
        }
        ropes.connect(player.serverLevel(), source, payload.sourceSegment(),
                target, payload.targetSegment());
    }

    public static void handleClimbInput(
            ServerPlayer player, RopeClimbInputPayload payload) {
        if (player == null || payload == null || player.isSpectator()
                || isHoldingTuningWand(player)
                || !payload.active() || findRescueHaul(player) != null) {
            if (player != null) {
                CLIMB_INPUTS.remove(player.getUUID());
            }
            return;
        }
        CLIMB_INPUTS.put(player.getUUID(), new ClimbInput(
                payload.jumping(), payload.crouching(),
                player.serverLevel().getGameTime()));
    }

    public static void handleRescueHaul(
            ServerPlayer player, RopeRescueHaulPayload payload) {
        if (player == null || payload == null) {
            return;
        }
        LevelRopes ropes = LEVELS.get(player.serverLevel());
        ActiveRope rope = ropes == null ? null : ropes.find(payload.ropeId());
        if (rope == null || player.isSpectator() || player.isDeadOrDying()
                || isHoldingTuningWand(player) || !emptyHands(player)) {
            if (rope != null && rope.isRescueHauling(player)) {
                rope.stopRescueHaul(player.serverLevel(), true);
            } else if (payload.operation() == RopeRescueHaulPayload.Operation.START) {
                sendRescueHaulState(player, payload.ropeId(), payload.segmentIndex(),
                        payload.sessionId(), false);
            }
            return;
        }
        if (payload.operation() == RopeRescueHaulPayload.Operation.START
                && ropes.hasNonRescueInteraction(player.getUUID())) {
            sendRescueHaulState(player, payload.ropeId(), payload.segmentIndex(),
                    payload.sessionId(), false);
            return;
        }
        rope.handleRescueHaul(player, payload);
    }

    private static void sendRescueHaulState(ServerPlayer player, int ropeId,
            int segment, long sessionId, boolean active) {
        if (player != null && sessionId > 0L) {
            PacketDistributor.sendToPlayer(player,
                    new RopeRescueHaulStatePayload(
                            ropeId, segment, sessionId, active));
        }
    }

    private static Vec3 horizontalPerpendicular(Vec3 forward) {
        Vec3 right = forward.cross(new Vec3(0.0D, 1.0D, 0.0D));
        return right.lengthSqr() <= 1.0E-10D
                ? new Vec3(1.0D, 0.0D, 0.0D) : right.normalize();
    }

    private static Vec3[] lassoPoints(
            Vec3 center, Vec3 axisX, Vec3 axisY, double phase) {
        Vec3 x = axisX.normalize();
        Vec3 y = axisY.subtract(x.scale(axisY.dot(x))).normalize();
        double radius = RopeProperties.DEFAULT.segmentLength()
                / (2.0D * Math.sin(Math.PI / LASSO_SEGMENTS));
        Vec3[] result = new Vec3[LASSO_SEGMENTS + 1];
        for (int index = 0; index < LASSO_SEGMENTS; index++) {
            double angle = phase + Math.PI * 2.0D * index / LASSO_SEGMENTS;
            result[index] = center.add(x.scale(Math.cos(angle) * radius))
                    .add(y.scale(Math.sin(angle) * radius));
        }
        result[LASSO_SEGMENTS] = result[0];
        return result;
    }

    private static Vec3[] anchoredLassoPoints(ServerLevel level, BlockHitResult hit,
            Vec3 throwOrigin) {
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (!isLassoTarget(level, pos, state)) {
            return null;
        }
        AABB bounds = state.getCollisionShape(level, pos).bounds().move(pos);
        double y = Mth.clamp(hit.getLocation().y,
                bounds.minY + 0.15D, bounds.maxY - 0.15D);
        Vec3 center = new Vec3(
                (bounds.minX + bounds.maxX) * 0.5D,
                y,
                (bounds.minZ + bounds.maxZ) * 0.5D);
        double phase = Math.atan2(
                throwOrigin.z - center.z, throwOrigin.x - center.x);
        return lassoPoints(center,
                new Vec3(1.0D, 0.0D, 0.0D),
                new Vec3(0.0D, 0.0D, 1.0D), phase);
    }

    static boolean isLassoTarget(ServerLevel level, BlockPos pos, BlockState state) {
        if (state == null || state.isAir() || state.canBeReplaced()
                || ModBlocks.mediumOf(state.getBlock()) != null) {
            return false;
        }
        var shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) {
            return false;
        }
        AABB bounds = shape.bounds();
        double width = bounds.getXsize();
        double height = bounds.getYsize();
        double depth = bounds.getZsize();
        if (width < 0.20D || depth < 0.20D || height < 0.45D
                || width > 1.05D || depth > 1.05D) {
            return false;
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = pos.relative(direction);
            if (!level.getBlockState(neighbor)
                    .getCollisionShape(level, neighbor).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static final class LevelRopes {
        private final List<ActiveRope> chains = new ArrayList<>();
        private int nextId;

        private LevelRopes() {
            this(1);
        }

        private LevelRopes(int nextId) {
            this.nextId = Math.max(1, nextId);
        }

        private void persist(ServerLevel level) {
            List<RopeSavedData.State> saved = new ArrayList<>(chains.size());
            for (ActiveRope rope : chains) {
                RopeSavedData.State state = rope.savedState();
                if (state != null) {
                    saved.add(state);
                }
            }
            RopeSavedData.get(level).replace(nextId, saved);
        }

        private void tick(ServerLevel level) {
            Iterator<ActiveRope> iterator = chains.iterator();
            while (iterator.hasNext()) {
                if (!iterator.next().tick(level)) {
                    iterator.remove();
                }
            }
        }

        private ActiveRope find(int id) {
            for (ActiveRope rope : chains) {
                if (rope.id == id) {
                    return rope;
                }
            }
            return null;
        }

        private boolean hasNonRescueInteraction(UUID playerId) {
            for (ActiveRope rope : chains) {
                if (playerId.equals(rope.dragPlayerId)
                        || playerId.equals(rope.breakPlayerId)) {
                    return true;
                }
            }
            return false;
        }

        private void clearPlayerRescueState(UUID playerId) {
            for (ActiveRope rope : chains) {
                rope.stopRescueHaul(playerId);
                if (playerId.equals(rope.ownerId)) {
                    rope.lastRescueSessionId = 0L;
                }
            }
        }

        private void breakChain(ServerLevel level, ActiveRope active,
                int segment, boolean allConnected) {
            Vec3 dropPosition = active.chain.segmentCenter(segment);
            if (dropPosition == null) {
                return;
            }
            int droppedCount = allConnected ? active.chain.segmentCount() : 1;
            active.stopRescueHaul(level, true);
            active.chain.clearDrag();
            if (allConnected) {
                level.playSound(null, dropPosition.x, dropPosition.y, dropPosition.z,
                        SoundEvents.LEASH_KNOT_BREAK, SoundSource.BLOCKS, 0.9F, 0.9F);
                active.send(level, true);
                chains.remove(active);
                dropRopes(level, dropPosition, droppedCount);
                persist(level);
                return;
            }
            RopeChain.Split split = active.chain.splitAt(segment);
            if (split == null) {
                return;
            }
            active.send(level, true);
            level.playSound(null, dropPosition.x, dropPosition.y, dropPosition.z,
                    SoundEvents.LEASH_KNOT_BREAK, SoundSource.BLOCKS, 0.9F, 0.9F);
            chains.remove(active);
            if (split.first() != null) {
                ActiveRope first = splitChild(active, split.first());
                chains.add(first);
                first.send(level, false);
            }
            if (split.second() != null) {
                ActiveRope second = splitChild(active, split.second());
                chains.add(second);
                second.send(level, false);
            }
            dropRopes(level, dropPosition, droppedCount);
            persist(level);
        }

        private void connect(ServerLevel level, ActiveRope source, int sourceSegment,
                ActiveRope target, int targetSegment) {
            RopeChain joined = source.chain.join(target.chain, sourceSegment, targetSegment);
            if (joined == null) {
                return;
            }
            Vec3 joinPoint = target.chain.segmentCenter(targetSegment);
            source.chain.clearDrag();
            source.send(level, true);
            target.send(level, true);
            chains.remove(source);
            chains.remove(target);
            ActiveRope merged = new ActiveRope(nextId++, source.ownerId, joined);
            chains.add(merged);
            merged.send(level, false);
            if (joinPoint != null) {
                level.playSound(null, joinPoint.x, joinPoint.y, joinPoint.z,
                        SoundEvents.LEASH_KNOT_PLACE, SoundSource.PLAYERS,
                        0.9F, 0.95F);
            }
            persist(level);
        }

        private ActiveRope splitChild(ActiveRope source, RopeChain childChain) {
            ActiveRope child = new ActiveRope(nextId++, source.ownerId, childChain);
            int lassoFirst = childChain.rescueLassoFirstSegment();
            if (lassoFirst >= 1 && source.rescueAnchorPos != null) {
                child.rescueState = source.rescueState == RescueStateMachine.State.HAULING
                        ? RescueStateMachine.State.ANCHORED : source.rescueState;
                child.rescueHand = source.rescueHand;
                child.lassoFirstNode = lassoFirst;
                child.rescueAnchorPos = source.rescueAnchorPos;
                child.rescueAnchorState = source.rescueAnchorState;
            } else if (!childChain.rescueAnchoredOrientations().isEmpty()) {
                // A broken loop is no longer a valid rescue binding.
                childChain.clearRescueAnchors();
            }
            return child;
        }
    }

    private static final class ActiveRope {
        private final int id;
        private final UUID ownerId;
        private RopeChain chain;
        private RopeCollisionWorld collision;
        private int age;
        private int snapshotSequence;
        private int nextCollisionCapture;
        private boolean dragging;
        private UUID dragPlayerId;
        private long dragInputSession;
        private long lastDragInputSequence;
        private long lastDragInputTick = Long.MIN_VALUE;
        private PendingDrag pendingDrag;
        private UUID breakPlayerId;
        private int breakSegment = -1;
        private boolean breakAllConnected;
        private int breakTicks;
        private long breakStartTick = Long.MIN_VALUE;
        private long lastBreakInputTick = Long.MIN_VALUE;
        private RescueStateMachine.State rescueState = RescueStateMachine.State.IDLE;
        private InteractionHand rescueHand = InteractionHand.MAIN_HAND;
        private int lassoFirstNode = -1;
        private int lassoFlightTicks;
        private Vec3 lassoCenter;
        private Vec3 lassoVelocity = Vec3.ZERO;
        private Vec3 lassoRight;
        private Vec3 lassoUp;
        private BlockPos rescueAnchorPos;
        private BlockState rescueAnchorState;
        private RescueSession rescueSession;
        private long lastRescueSessionId;

        private ActiveRope(int id, UUID ownerId, RopeChain chain) {
            this.id = id;
            this.ownerId = ownerId;
            this.chain = chain;
        }

        private static ActiveRope rescue(int id, ServerPlayer owner,
                InteractionHand hand, RopeChain chain, int lassoFirstNode,
                Vec3 center, Vec3 velocity, Vec3 right, Vec3 up) {
            ActiveRope rope = new ActiveRope(id, owner.getUUID(), chain);
            rope.rescueState = RescueStateMachine.transition(
                    rope.rescueState, RescueStateMachine.State.FLYING);
            rope.rescueHand = hand;
            rope.lassoFirstNode = lassoFirstNode;
            rope.lassoCenter = center;
            rope.lassoVelocity = velocity;
            rope.lassoRight = right;
            rope.lassoUp = up;
            return rope;
        }

        private static ActiveRope restore(RopeSavedData.State state, ServerLevel level) {
            if (state == null || state.properties() == null
                    || state.nodes().size() != state.properties().nodeCount()
                    || state.velocities().size() != state.nodes().size()
                    || state.nodes().stream().anyMatch(point -> !validVector(point))
                    || state.velocities().stream().anyMatch(point -> !validVector(point))) {
                return null;
            }
            try {
                Vec3[] nodes = state.nodes().toArray(Vec3[]::new);
                Vec3[] velocities = state.velocities().toArray(Vec3[]::new);
                RopeChain chain = new RopeChain(state.properties(), nodes, velocities);
                chain.restoreAnchors(state.anchors());
                ActiveRope restored = new ActiveRope(state.id(), state.ownerId(), chain);
                restored.age = state.age();
                restored.lassoFirstNode = chain.rescueLassoFirstSegment();
                BlockPos anchorPos = state.rescueAnchorPos();
                if (restored.lassoFirstNode >= 1 && anchorPos != null) {
                    restored.rescueState = RescueStateMachine.State.ANCHORED;
                    restored.rescueAnchorPos = anchorPos;
                    if (level.getChunkSource().hasChunk(
                            anchorPos.getX() >> 4, anchorPos.getZ() >> 4)) {
                        BlockState anchorState = level.getBlockState(anchorPos);
                        if (!validSavedAnchorState(anchorState)) {
                            chain.clearRescueAnchors();
                            restored.lassoFirstNode = -1;
                            restored.rescueState = RescueStateMachine.State.IDLE;
                            restored.rescueAnchorPos = null;
                        } else {
                            restored.rescueAnchorState = anchorState;
                        }
                    }
                } else if (restored.lassoFirstNode >= 1) {
                    chain.clearRescueAnchors();
                    restored.lassoFirstNode = -1;
                }
                return restored;
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }

        private boolean isRescueHauling() {
            return rescueState == RescueStateMachine.State.HAULING
                    && rescueSession != null;
        }

        private boolean isRescueHauling(ServerPlayer player) {
            return isRescueHauling() && rescueSession.playerId().equals(player.getUUID());
        }

        private void handleRescueHaul(
                ServerPlayer player, RopeRescueHaulPayload payload) {
            if (payload.operation() == null || payload.sessionId() <= 0L
                    || payload.sequence() <= 0L) {
                if (payload.operation() == RopeRescueHaulPayload.Operation.START) {
                    sendRescueHaulState(player, id, payload.segmentIndex(),
                            payload.sessionId(), false);
                }
                return;
            }
            long now = player.serverLevel().getGameTime();
            if (payload.operation() == RopeRescueHaulPayload.Operation.STOP) {
                if (rescueSession != null
                        && RescueInputGuard.acceptsFollowUp(
                                rescueSession.playerId(), rescueSession.sessionId(),
                                rescueSession.lastSequence(), player.getUUID(), payload,
                                RopeRescueHaulPayload.Operation.STOP)) {
                    stopRescueHaul(player.serverLevel(), true);
                }
                return;
            }
            int lassoFirst = chain.rescueLassoFirstSegment();
            if (RescueInputGuard.acceptsStart(
                    ownerId, player.getUUID(), rescueState, rescueSession != null,
                    lastRescueSessionId, lassoFirst, payload)
                    && !dragging && pendingDrag == null && breakPlayerId == null
                    && aimsAtSegment(player, chain, payload.segmentIndex())) {
                rescueSession = new RescueSession(
                        player.getUUID(), payload.sessionId(), payload.sequence(),
                        payload.segmentIndex(), now, null,
                        RescueHaulPhase.POSITIONING);
                lastRescueSessionId = payload.sessionId();
                rescueState = RescueStateMachine.transition(
                        rescueState, RescueStateMachine.State.HAULING);
                CLIMB_INPUTS.remove(player.getUUID());
                Vec3 grip = chain.point(payload.segmentIndex());
                if (grip != null) {
                    chain.setRescueTemporaryFixedPoint(payload.segmentIndex(), grip);
                }
                sendRescueHaulState(player, id, payload.segmentIndex(),
                        payload.sessionId(), true);
                return;
            }
            if (payload.operation() == RopeRescueHaulPayload.Operation.START) {
                sendRescueHaulState(player, id, payload.segmentIndex(),
                        payload.sessionId(), false);
                return;
            }
            if (rescueState != RescueStateMachine.State.HAULING
                    || rescueSession == null
                    || !RescueInputGuard.acceptsFollowUp(
                            rescueSession.playerId(), rescueSession.sessionId(),
                            rescueSession.lastSequence(), player.getUUID(), payload,
                            RopeRescueHaulPayload.Operation.KEEP_ALIVE)) {
                return;
            }
            rescueSession = rescueSession.withInput(now, payload.sequence());
        }

        private void stopRescueHaul() {
            chain.clearRescueTemporaryFixedPoint();
            rescueSession = null;
            if (rescueState == RescueStateMachine.State.HAULING) {
                rescueState = RescueStateMachine.transition(
                        rescueState, RescueStateMachine.State.ANCHORED);
            }
        }

        private void stopRescueHaul(UUID playerId) {
            if (rescueSession != null
                    && (playerId == null || rescueSession.playerId().equals(playerId))) {
                stopRescueHaul();
            }
        }

        private void stopRescueHaul(ServerLevel level, boolean notifyClient) {
            RescueSession stopped = rescueSession;
            stopRescueHaul();
            if (!notifyClient || stopped == null) {
                return;
            }
            ServerPlayer player = level.getServer().getPlayerList()
                    .getPlayer(stopped.playerId());
            sendRescueHaulState(player, id, stopped.gripNode(),
                    stopped.sessionId(), false);
        }

        private void notifyInteractionRelease(ServerPlayer player,
                boolean rescue, int segment) {
            if (player != null) {
                PacketDistributor.sendToPlayer(player,
                        new RopeInteractionReleasePayload(id, rescue, segment));
            }
        }

        private void stopDragAndNotify(ServerPlayer player) {
            if (!dragging) {
                return;
            }
            notifyInteractionRelease(player, false, chain.draggedSegment());
            chain.clearDrag();
            dragging = false;
            dragPlayerId = null;
            lastDragInputTick = Long.MIN_VALUE;
        }

        private boolean acceptsActive(ServerPlayer player, RopeDragPayload payload) {
            UUID playerId = player.getUUID();
            if (payload.inputSession() < dragInputSession) {
                return false;
            }
            if (dragPlayerId != null && !dragPlayerId.equals(playerId)) {
                return false;
            }
            if (pendingDrag != null) {
                if (!pendingDrag.playerId().equals(playerId)) {
                    return false;
                }
                if (!pendingDrag.dragging()) {
                    return false;
                }
                if (pendingDrag.dragging()
                        && pendingDrag.inputSession() == payload.inputSession()
                        && payload.inputSequence() <= pendingDrag.inputSequence()) {
                    return false;
                }
            }
            return payload.inputSession() > dragInputSession
                    || payload.inputSequence() > lastDragInputSequence;
        }

        private boolean acceptsRelease(ServerPlayer player, RopeDragPayload payload) {
            if (!player.getUUID().equals(dragPlayerId)
                    && !(pendingDrag != null
                            && pendingDrag.dragging()
                            && pendingDrag.playerId().equals(player.getUUID()))) {
                return false;
            }
            return payload.inputSession() == dragInputSession
                    && payload.inputSequence() > lastDragInputSequence
                    && (pendingDrag == null
                            || payload.inputSession() != pendingDrag.inputSession()
                            || payload.inputSequence() > pendingDrag.inputSequence());
        }

        private void queueDrag(PendingDrag pending) {
            if (pendingDrag != null && pending.inputSession() == pendingDrag.inputSession()
                    && pending.inputSequence() <= pendingDrag.inputSequence()) {
                return;
            }
            pendingDrag = pending;
            dragInputSession = pending.inputSession();
            lastDragInputSequence = pending.inputSequence();
        }

        private boolean tick(ServerLevel level) {
            RopeProperties properties = chain.properties();
            if (breakPlayerId != null && level.getGameTime() - lastBreakInputTick
                    > BREAK_INPUT_TIMEOUT_TICKS) {
                clearBreak();
            }
            age++;
            PendingDrag pending = pendingDrag;
            boolean captureDragTarget = pending != null && pending.dragging();
            if (collision == null || age >= nextCollisionCapture || captureDragTarget) {
                int refresh = Math.max(1, properties.collisionRefreshTicks());
                List<List<Vec3>> corridors = new ArrayList<>();
                corridors.add(chain.positions());
                corridors.add(chain.motionTargets(refresh));
                if (captureDragTarget) {
                    ServerPlayer player = level.getServer().getPlayerList()
                            .getPlayer(pending.playerId());
                    Vec3 current = chain.segmentCenter(pending.segmentIndex());
                    Vec3 target = player == null || current == null
                            ? null : previewDragTarget(player, pending);
                    if (target != null) {
                        corridors.add(List.of(current, target));
                    }
                }
                if (rescueState == RescueStateMachine.State.FLYING
                        && lassoCenter != null && validVector(lassoVelocity)) {
                    corridors.add(List.of(lassoCenter, lassoCenter.add(lassoVelocity)));
                } else if (isRescueHauling()) {
                    ServerPlayer player = level.getServer().getPlayerList()
                            .getPlayer(rescueSession.playerId());
                    Vec3 current = chain.point(rescueSession.gripNode());
                    if (player != null && current != null) {
                        corridors.add(List.of(current, rescueHaulTarget(player)));
                    }
                }
                collision = RopeCollisionWorld.captureCorridors(level,
                        corridors,
                        properties.collisionCapturePadding(),
                        properties.maximumCollisionBlockSamples());
                nextCollisionCapture = age + refresh;
            }
            tickRescue(level);
            processPendingDrag(level);
            tickRescueSession(level);
            if (dragging && level.getGameTime() - lastDragInputTick
                    > DRAG_INPUT_TIMEOUT_TICKS) {
                stopDragAndNotify(level.getServer().getPlayerList().getPlayer(dragPlayerId));
            } else if (dragging) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(dragPlayerId);
                Vec3 dragged = chain.segmentCenter(chain.draggedSegment());
                if (player == null || player.serverLevel() != level || dragged == null
                        || dragDistanceExceeded(player.getEyePosition(), dragged,
                                MudPhysicsSettings.ropeMaximumDragDistance())) {
                    stopDragAndNotify(player);
                }
            }
            chain.step(collision);
            if (dragging) {
                // A grabbed segment is rendered from the server pose. Send
                // every tick so the client never has to predict the chain.
                send(level, false, 1);
            } else if (age % SNAPSHOT_INTERVAL == 0) {
                send(level, false, SNAPSHOT_INTERVAL);
            }
            return true;
        }

        private void tickRescueSession(ServerLevel level) {
            if (!isRescueHauling()) {
                return;
            }
            ServerPlayer player = level.getServer().getPlayerList()
                    .getPlayer(rescueSession.playerId());
            if (!validRescuePlayer(player, level)
                    || level.getGameTime() - rescueSession.lastInputTick()
                            > RESCUE_HAUL_INPUT_TIMEOUT_TICKS) {
                stopRescueHaul(level, true);
                return;
            }
            setRescueTarget(player);
        }

        private static Vec3 rescueHaulTarget(ServerPlayer player) {
            double bodyHeight = Math.max(0.95D,
                    player.getBbHeight() * RESCUE_HAUL_BODY_HEIGHT);
            return player.position().add(0.0D, bodyHeight, 0.0D);
        }

        private void tickRescue(ServerLevel level) {
            if (rescueState == RescueStateMachine.State.IDLE) {
                return;
            }
            ServerPlayer player = ownerId == null ? null
                    : level.getServer().getPlayerList().getPlayer(ownerId);
            if (rescueState == RescueStateMachine.State.FLYING
                    && (player == null || player.serverLevel() != level
                    || player.isDeadOrDying() || player.isSpectator())) {
                cancelLassoFlight();
                return;
            }
            if (rescueState != RescueStateMachine.State.FLYING
                    && rescueAnchorPos != null && level.getChunkSource().hasChunk(
                            rescueAnchorPos.getX() >> 4, rescueAnchorPos.getZ() >> 4)) {
                BlockState currentAnchorState = level.getBlockState(rescueAnchorPos);
                if (!validSavedAnchorState(currentAnchorState)
                        || (rescueAnchorState != null
                                && currentAnchorState.getBlock()
                                        != rescueAnchorState.getBlock())) {
                    clearRescueAnchor(level);
                    return;
                }
                if (rescueAnchorState == null) {
                    rescueAnchorState = currentAnchorState;
                }
            }

            if (rescueState == RescueStateMachine.State.FLYING) {
                Vec3 look = player.getLookAngle().normalize();
                Vec3 hand = handPosition(player, rescueHand, look);
                advanceLasso(level, player, hand);
                return;
            }
        }

        private void advanceLasso(ServerLevel level, ServerPlayer player, Vec3 hand) {
            if (lassoCenter == null || lassoRight == null || lassoUp == null
                    || lassoFirstNode < 1
                    || ++lassoFlightTicks > MAXIMUM_LASSO_FLIGHT_TICKS) {
                cancelLassoFlight();
                stopRescueHaul();
                return;
            }
            Vec3 desired = lassoCenter.add(lassoVelocity);
            lassoVelocity = lassoVelocity.scale(LASSO_VELOCITY_DAMPING)
                    .add(0.0D, -LASSO_GRAVITY, 0.0D);
            BlockHitResult hit = level.clip(new ClipContext(
                    lassoCenter, desired,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    player));
            if (hit.getType() == HitResult.Type.BLOCK) {
                Vec3[] anchored = anchoredLassoPoints(level, hit, hand);
                if (anchored != null && chain.anchorLasso(lassoFirstNode, anchored)) {
                    rescueState = RescueStateMachine.transition(
                            rescueState, RescueStateMachine.State.ANCHORED);
                    rescueAnchorPos = hit.getBlockPos().immutable();
                    rescueAnchorState = level.getBlockState(rescueAnchorPos);
                    lassoCenter = null;
                    lassoVelocity = Vec3.ZERO;
                    level.playSound(null, hit.getBlockPos(),
                            SoundEvents.LEASH_KNOT_PLACE, SoundSource.PLAYERS,
                            1.0F, 0.86F);
                    return;
                }
                cancelLassoFlight();
                stopRescueHaul();
                return;
            }

            if (collision != null && !collision.isEmpty()) {
                Vec3 resolved = collision.sweep(
                        lassoCenter, desired, chain.properties().collisionRadius());
                if (resolved.distanceToSqr(desired) > 1.0E-5D) {
                    cancelLassoFlight();
                    stopRescueHaul();
                    return;
                }
            }
            lassoCenter = desired;
            chain.setMovingLasso(lassoFirstNode,
                    lassoPoints(lassoCenter, lassoRight, lassoUp, 0.0D));
        }

        private void cancelLassoFlight() {
            if (rescueState == RescueStateMachine.State.FLYING) {
                chain.clearMovingLasso();
            }
            rescueState = RescueStateMachine.transition(
                    rescueState, RescueStateMachine.State.IDLE);
            lassoCenter = null;
            lassoVelocity = Vec3.ZERO;
        }

        private void clearRescueAnchor(ServerLevel level) {
            stopRescueHaul(level, true);
            chain.clearRescueAnchors();
            rescueState = RescueStateMachine.transition(
                    rescueState, RescueStateMachine.State.IDLE);
            rescueAnchorPos = null;
            rescueAnchorState = null;
            lassoFirstNode = -1;
            send(level, false, 1);
        }

        private static boolean validRescuePlayer(ServerPlayer player, ServerLevel level) {
            return player != null && player.serverLevel() == level
                    && !player.isDeadOrDying() && !player.isSpectator()
                    && emptyHands(player);
        }

        private static boolean validSavedAnchorState(BlockState state) {
            return state != null && !state.isAir() && !state.canBeReplaced();
        }

        /** Updates the one temporary rope point and returns the bounded pull. */
        private Vec3 updateRescueHaul(ServerPlayer player) {
            if (!isRescueHauling(player)
                    || !validRescuePlayer(player, player.serverLevel())
                    || player.serverLevel().getGameTime() - rescueSession.lastInputTick()
                            > RESCUE_HAUL_INPUT_TIMEOUT_TICKS) {
                stopRescueHaul(player.serverLevel(), true);
                return Vec3.ZERO;
            }
            int grip = rescueSession.gripNode();
            int lassoFirst = chain.rescueLassoFirstSegment();
            if (lassoFirst < 1 || grip < 0 || grip >= lassoFirst) {
                stopRescueHaul(player.serverLevel(), true);
                return Vec3.ZERO;
            }
            Vec3 knot = chain.point(lassoFirst);
            if (rescueSession.phase() != RescueHaulPhase.READY
                    || rescueSession.targetCenter() == null) {
                return Vec3.ZERO;
            }
            Vec3 gripTarget = chain.segmentCenter(rescueSession.gripNode());
            if (knot == null || gripTarget == null) {
                return Vec3.ZERO;
            }
            Vec3 frontSegment = chain.segmentCenter(rescueSession.gripNode() + 1);
            Vec3 haulPoint = rescueHaulTarget(player);
            Vec3 ropeVector = frontSegment == null
                    ? knot.subtract(haulPoint) : frontSegment.subtract(haulPoint);
            return rescuePull(ropeVector, chain.properties().segmentLength());
        }

        private void setRescueTarget(ServerPlayer player) {
            int lassoFirst = chain.rescueLassoFirstSegment();
            if (lassoFirst < 1 || rescueSession == null
                    || rescueSession.gripNode() < 0
                    || rescueSession.gripNode() >= lassoFirst) {
                stopRescueHaul(player.serverLevel(), true);
                return;
            }
            Vec3 knot = chain.point(lassoFirst);
            if (knot == null) {
                stopRescueHaul(player.serverLevel(), true);
                return;
            }
            Vec3 targetCenter = rescueSession.targetCenter();
            if (targetCenter == null) {
                Vec3 desired = rescueHaulTarget(player);
                double available = (lassoFirst - rescueSession.gripNode())
                        * chain.properties().segmentLength();
                targetCenter = clampRescueHaulTarget(desired, knot, available);
                rescueSession = rescueSession.withTarget(targetCenter);
            }
            Vec3 current = chain.point(rescueSession.gripNode());
            Vec3 nextNode = chain.point(rescueSession.gripNode() + 1);
            if (current == null || nextNode == null) {
                stopRescueHaul(player.serverLevel(), true);
                return;
            }
            double segmentLength = chain.properties().segmentLength();
            boolean fixedNextNode = rescueSession.gripNode() + 1 == lassoFirst;
            Vec3 target = fixedNextNode
                    ? rescueAnchoredGripNodeTarget(
                            targetCenter, nextNode, current, segmentLength)
                    : rescueGripNodeTarget(targetCenter, nextNode);
            if (current != null && collision != null && !collision.isEmpty()) {
                target = collision.sweep(
                        current, target, chain.properties().collisionRadius());
            }
            if (fixedNextNode) {
                target = capRescueNodeDistance(
                        target, nextNode, segmentLength, current);
            }
            chain.setRescueTemporaryFixedPoint(rescueSession.gripNode(), target);

            if (rescueSession.phase() == RescueHaulPhase.POSITIONING) {
                if (rescueGripReachedTarget(
                        chain.segmentCenter(rescueSession.gripNode()), targetCenter)) {
                    rescueSession = rescueSession.withReady();
                }
                return;
            }
            if (rescueSession.gripNode() + 1 < lassoFirst) {
                Vec3 currentHaulTarget = rescueHaulTarget(player);
                Vec3 frontSegment = chain.segmentCenter(
                        rescueSession.gripNode() + 1);
                Vec3 pullDirection = frontSegment == null
                        ? nextNode.subtract(targetCenter)
                        : frontSegment.subtract(targetCenter);
                if (!rescueTargetMovedToward(
                        targetCenter, currentHaulTarget, pullDirection)) {
                    return;
                }
                int next = rescueSession.gripNode() + 1;
                Vec3 nextPoint = chain.point(next);
                if (nextPoint != null) {
                    rescueSession = rescueSession.withGrip(next);
                    chain.setRescueTemporaryFixedPoint(next, nextPoint);
                    sendRescueHaulState(player, id, next,
                            rescueSession.sessionId(), true);
                    return;
                }
            }
        }

        private void processPendingDrag(ServerLevel level) {
            PendingDrag pending = pendingDrag;
            pendingDrag = null;
            if (pending == null) {
                return;
            }
            if (!pending.dragging()) {
                if ((dragPlayerId != null && dragPlayerId.equals(pending.playerId()))
                        || (dragPlayerId == null && pending.inputSession() == dragInputSession)) {
                    chain.clearDrag();
                    dragging = false;
                    dragPlayerId = null;
                    lastDragInputTick = Long.MIN_VALUE;
                }
                return;
            }
            ServerPlayer player = level.getServer().getPlayerList()
                    .getPlayer(pending.playerId());
            if (player != null && player.serverLevel() == level
                    && emptyHands(player) && !player.isSpectator()) {
                applyDragIntent(player, pending, this);
            }
        }

        /** Applies an active drag that arrived immediately before an anchor packet. */
        private void activatePendingDrag(ServerPlayer player, int segment) {
            PendingDrag pending = pendingDrag;
            if (pending == null || !pending.dragging()
                    || pending.segmentIndex() != segment
                    || !pending.playerId().equals(player.getUUID())
                    || !emptyHands(player) || player.isSpectator()) {
                return;
            }
            pendingDrag = null;
            applyDragIntent(player, pending, this);
        }

        private Vec3 previewDragTarget(ServerPlayer player, PendingDrag pending) {
            if (!validVector(pending.viewOrigin())
                    || !validDirection(pending.viewDirection())
                    || pending.frame() == null
                    || pending.viewOrigin().distanceTo(player.getEyePosition())
                            > MAX_VIEW_ORIGIN_ERROR
                    || !validSegment(chain, pending.segmentIndex())) {
                return null;
            }
            Vec3 target = pending.viewOrigin().add(
                    pending.viewDirection().normalize().scale(RopeProperties.GRAB_DISTANCE));
            return chain.clampDragTarget(pending.segmentIndex(), target, pending.frame());
        }

        private void send(ServerLevel level, boolean removed) {
            send(level, removed, SNAPSHOT_INTERVAL);
        }

        private void send(ServerLevel level, boolean removed, int interval) {
            List<Vec3> nodes = chain.positions();
            Vec3 origin = average(nodes);
            int sequence = ++snapshotSequence;
            RopeSnapshotPayload payload = removed
                    ? RopeSnapshotPayload.removed(id, sequence)
            : new RopeSnapshotPayload(id, false, age, sequence, interval,
                            chain.anchoredOrientations(), chain.rescueAnchoredOrientations(),
                            chain.draggedOrientation(),
                            origin.x, origin.y, origin.z, nodes);
            PacketDistributor.sendToPlayersNear(
                    level, null, origin.x, origin.y, origin.z,
                    TRACKING_DISTANCE, payload);
        }

        private void clearBreak() {
            breakPlayerId = null;
            breakSegment = -1;
            breakAllConnected = false;
            breakTicks = 0;
            breakStartTick = Long.MIN_VALUE;
            lastBreakInputTick = Long.MIN_VALUE;
        }

        private RopeSavedData.State savedState() {
            if (chain == null) {
                return null;
            }
            List<Vec3> nodes = chain.positions();
            List<Vec3> velocities = chain.velocities();
            if (nodes.size() != chain.properties().nodeCount()
                    || velocities.size() != nodes.size()
                    || nodes.stream().anyMatch(point -> !validVector(point))
                    || velocities.stream().anyMatch(point -> !validVector(point))) {
                return null;
            }
            return new RopeSavedData.State(id, ownerId, age, chain.properties(),
                    nodes, velocities, chain.anchorStates(), rescueAnchorPos);
        }
    }

    private static boolean validSegment(RopeChain chain, int segment) {
        return chain != null && segment >= 0 && segment < chain.segmentCount();
    }

    private static Vec3 findClimbContact(ServerPlayer player) {
        LevelRopes ropes = LEVELS.get(player.serverLevel());
        if (ropes == null) {
            return null;
        }
        AABB body = player.getBoundingBox();
        for (ActiveRope rope : ropes.chains) {
            for (int segment = 0; segment < rope.chain.segmentCount(); segment++) {
                Vec3 contact = RopeClimbing.contactPoint(
                        body, rope.chain.point(segment), rope.chain.point(segment + 1),
                        rope.chain.properties().collisionRadius());
                if (contact != null && hasBodyLineOfSight(player, contact)) {
                    return contact;
                }
            }
        }
        return null;
    }

    private static boolean hasBodyLineOfSight(ServerPlayer player, Vec3 point) {
        Vec3 origin = player.getBoundingBox().getCenter();
        HitResult obstruction = player.serverLevel().clip(new ClipContext(
                origin, point, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        return obstruction.getType() == HitResult.Type.MISS
                || obstruction.getLocation().distanceToSqr(point) <= 0.01D;
    }

    private static boolean withinReach(ServerPlayer player, Vec3 point) {
        return point != null
                && player.getEyePosition().distanceTo(point)
                        <= Math.max(0.0D, player.blockInteractionRange());
    }

    private static boolean hasLineOfSight(ServerPlayer player, Vec3 point) {
        if (point == null || !validVector(point)) {
            return false;
        }
        Vec3 eye = player.getEyePosition();
        return player.serverLevel().clip(new ClipContext(
                eye, point, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS;
    }

    private static boolean aimsAtSegment(
            ServerPlayer player, RopeChain chain, int segment) {
        return aimsAtSegmentRange(player, chain, segment, segment + 1);
    }

    private static boolean aimsAtSegmentRange(ServerPlayer player, RopeChain chain,
            int firstSegment, int endSegmentExclusive) {
        if (chain == null || firstSegment < 0
                || endSegmentExclusive <= firstSegment
                || endSegmentExclusive > chain.segmentCount()) {
            return false;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 direction = player.getLookAngle();
        double range = Math.max(0.0D, player.blockInteractionRange());
        if (direction.lengthSqr() <= 1.0E-8D || range <= 0.0D) {
            return false;
        }
        double hitDistance = Double.POSITIVE_INFINITY;
        for (int segment = firstSegment; segment < endSegmentExclusive; segment++) {
            double candidate = RopeHitGeometry.rayCapsuleHitDistance(
                    eye, direction, chain.point(segment), chain.point(segment + 1),
                    RopeHitGeometry.SELECTION_RADIUS, range);
            if (candidate < hitDistance) {
                hitDistance = candidate;
            }
        }
        if (!Double.isFinite(hitDistance)) {
            return false;
        }
        Vec3 hit = eye.add(direction.normalize().scale(hitDistance));
        HitResult obstruction = player.serverLevel().clip(new ClipContext(
                eye, hit, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        return obstruction.getType() == HitResult.Type.MISS
                || obstruction.getLocation().distanceToSqr(hit) <= 0.01D;
    }

    private static boolean emptyHands(ServerPlayer player) {
        return player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty();
    }

    private static boolean isHoldingTuningWand(ServerPlayer player) {
        return player.getMainHandItem().getItem() == ModBlocks.MUD_TUNING_WAND.get()
                || player.getOffhandItem().getItem() == ModBlocks.MUD_TUNING_WAND.get();
    }

    private static boolean validDirection(Vec3 direction) {
        return direction != null && Double.isFinite(direction.x)
                && Double.isFinite(direction.y) && Double.isFinite(direction.z)
                && direction.lengthSqr() > 1.0E-8D;
    }

    private static boolean validVector(Vec3 vector) {
        return vector != null && Double.isFinite(vector.x)
                && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    static boolean dragDistanceExceeded(Vec3 playerPosition,
            Vec3 segmentPosition, double maximumDistance) {
        return !validVector(playerPosition) || !validVector(segmentPosition)
                || !Double.isFinite(maximumDistance) || maximumDistance <= 0.0D
                || playerPosition.distanceToSqr(segmentPosition)
                        > maximumDistance * maximumDistance;
    }

    private static ItemStack heldRope(ServerPlayer player) {
        if (player.getMainHandItem().is(ModMudworkContent.ROPE.get())) {
            return player.getMainHandItem();
        }
        if (player.getOffhandItem().is(ModMudworkContent.ROPE.get())) {
            return player.getOffhandItem();
        }
        return null;
    }

    private static void dropRopes(ServerLevel level, Vec3 position, int count) {
        if (count <= 0) {
            return;
        }
        ItemStack stack = new ItemStack(ModMudworkContent.ROPE.get(),
                Math.min(count, 64));
        level.addFreshEntity(new ItemEntity(level, position.x, position.y,
                position.z, stack));
    }

    private static Vec3 average(List<Vec3> nodes) {
        Vec3 sum = Vec3.ZERO;
        for (Vec3 node : nodes) {
            sum = sum.add(node);
        }
        return sum.scale(1.0D / nodes.size());
    }

    static Vec3 clampRescueHaulTarget(
            Vec3 target, Vec3 knot, double availableLength) {
        if (target == null || knot == null || !validVector(target)
                || !validVector(knot) || !Double.isFinite(availableLength)) {
            return target;
        }
        double maximum = Math.max(0.0D, availableLength - 1.0E-4D);
        Vec3 offset = target.subtract(knot);
        double distance = offset.length();
        return distance <= maximum || distance <= 1.0E-10D
                ? target : knot.add(offset.scale(maximum / distance));
    }

    /** Converts a segment-center target into the current segment's free-node target. */
    static Vec3 rescueGripNodeTarget(Vec3 targetCenter, Vec3 nextNode) {
        if (!validVector(targetCenter) || !validVector(nextNode)) {
            return targetCenter;
        }
        return targetCenter.scale(2.0D).subtract(nextNode);
    }

    /** Keeps the last movable node on the fixed-length sphere around the lasso. */
    static Vec3 rescueAnchoredGripNodeTarget(
            Vec3 targetCenter, Vec3 fixedNode, Vec3 currentNode, double segmentLength) {
        if (!validVector(targetCenter) || !validVector(fixedNode)
                || !Double.isFinite(segmentLength) || segmentLength <= 0.0D) {
            return currentNode;
        }
        Vec3 direction = targetCenter.subtract(fixedNode);
        if (direction.lengthSqr() <= 1.0E-10D
                && validVector(currentNode)) {
            direction = currentNode.subtract(fixedNode);
        }
        if (direction.lengthSqr() <= 1.0E-10D) {
            direction = new Vec3(0.0D, 0.0D, 1.0D);
        }
        return fixedNode.add(direction.normalize().scale(segmentLength));
    }

    static Vec3 capRescueNodeDistance(
            Vec3 target, Vec3 fixedNode, double maximumDistance, Vec3 fallback) {
        if (!validVector(target) || !validVector(fixedNode)
                || !Double.isFinite(maximumDistance) || maximumDistance <= 0.0D) {
            return target;
        }
        Vec3 offset = target.subtract(fixedNode);
        double distance = offset.length();
        if (distance <= maximumDistance || distance <= 1.0E-10D) {
            return target;
        }
        Vec3 direction = offset;
        if (!validVector(direction) && validVector(fallback)) {
            direction = fallback.subtract(fixedNode);
        }
        return fixedNode.add(direction.normalize().scale(maximumDistance));
    }

    static boolean rescueGripReachedTarget(Vec3 current, Vec3 target) {
        return validVector(current) && validVector(target)
                && current.distanceToSqr(target)
                        <= RESCUE_GRIP_ARRIVAL_RADIUS * RESCUE_GRIP_ARRIVAL_RADIUS;
    }

    static boolean rescueTargetMovedToward(
            Vec3 initialTarget, Vec3 currentTarget, Vec3 towardRope) {
        if (!validVector(initialTarget) || !validVector(currentTarget)
                || !validVector(towardRope) || towardRope.lengthSqr() <= 1.0E-10D) {
            return false;
        }
        return currentTarget.subtract(initialTarget).dot(towardRope.normalize())
                >= RESCUE_GRIP_PROGRESS_DISTANCE;
    }

    static Vec3 rescuePull(Vec3 ropeVector, double availableLength) {
        if (!validVector(ropeVector) || !Double.isFinite(availableLength)
                || availableLength <= 0.0D) {
            return Vec3.ZERO;
        }
        double distance = ropeVector.length();
        double slack = availableLength * RESCUE_TAUT_START;
        if (distance <= slack || distance <= 1.0E-8D) {
            return Vec3.ZERO;
        }
        double tautness = Mth.clamp((distance - slack)
                / Math.max(availableLength - slack, 0.01D), 0.0D, 1.0D);
        return ropeVector.scale(RESCUE_HAUL_MAX_SPEED * tautness / distance);
    }

    static Vec3 rescueVelocity(Vec3 currentVelocity, Vec3 pullVelocity) {
        if (!validVector(currentVelocity) || !validVector(pullVelocity)
                || pullVelocity.lengthSqr() <= 1.0E-10D) {
            return validVector(currentVelocity) ? currentVelocity : Vec3.ZERO;
        }
        Vec3 direction = pullVelocity.normalize();
        double currentAlongRope = currentVelocity.dot(direction);
        double requiredAlongRope = pullVelocity.length();
        return currentAlongRope >= requiredAlongRope
                ? currentVelocity
                : currentVelocity.add(
                        direction.scale(requiredAlongRope - currentAlongRope));
    }

    private record PendingDrag(UUID playerId, int segmentIndex, RopeFrame frame,
            Vec3 viewOrigin, Vec3 viewDirection, long inputSession,
            long inputSequence, boolean dragging) {
    }

    private record RescueCastIntent(InteractionHand hand, long gameTime) {
    }

    private record ClimbInput(boolean jumping, boolean crouching, long gameTime) {
    }

    private record ClimbContact(ServerLevel level, long gameTime, boolean active) {
    }

    private record RescueSession(
            UUID playerId, long sessionId, long lastSequence, int gripNode,
            long lastInputTick, Vec3 targetCenter, RescueHaulPhase phase) {
        private RescueSession withInput(long tick, long sequence) {
            return new RescueSession(
                    playerId, sessionId, sequence, gripNode, tick,
                    targetCenter, phase);
        }

        private RescueSession withGrip(int node) {
            return new RescueSession(
                    playerId, sessionId, lastSequence, node,
                    lastInputTick, null, RescueHaulPhase.POSITIONING);
        }

        private RescueSession withTarget(Vec3 newTarget) {
            return new RescueSession(
                    playerId, sessionId, lastSequence, gripNode,
                    lastInputTick, newTarget, RescueHaulPhase.POSITIONING);
        }

        private RescueSession withReady() {
            return new RescueSession(
                    playerId, sessionId, lastSequence, gripNode,
                    lastInputTick, targetCenter, RescueHaulPhase.READY);
        }
    }

    private enum RescueHaulPhase {
        POSITIONING,
        READY
    }

    static int breakDurationTicks(boolean allConnected) {
        return allConnected ? SURVIVAL_BREAK_TICKS * 2 : SURVIVAL_BREAK_TICKS;
    }
}

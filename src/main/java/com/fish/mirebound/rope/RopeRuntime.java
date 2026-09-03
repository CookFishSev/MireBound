package com.fish.mirebound.rope;

import com.fish.mirebound.network.payload.RopeSnapshotPayload;
import com.fish.mirebound.network.payload.RopeDragPayload;
import com.fish.mirebound.network.payload.RopeAnchorPayload;
import com.fish.mirebound.network.payload.RopeBreakPayload;
import com.fish.mirebound.network.payload.RopeExtendPayload;
import com.fish.mirebound.network.payload.RopeConnectPayload;
import com.fish.mirebound.network.payload.RopeRescueHaulPayload;
import com.fish.mirebound.network.payload.RopeClimbInputPayload;
import com.fish.mirebound.network.payload.RopeInteractionReleasePayload;
import com.fish.mirebound.mud.MudPhysics;
import com.fish.mirebound.mud.MudPhysicsSettings;
import com.fish.mirebound.mud.MudPlayerData;
import com.fish.mirebound.mud.MudStateStore;
import com.fish.mirebound.registry.ModMudworkContent;
import com.fish.mirebound.registry.ModBlocks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private static final int RESCUE_RELEASE_GRACE_TICKS = 40;
    private static final double LASSO_GRAVITY = 0.035D;
    private static final double LASSO_VELOCITY_DAMPING = 0.985D;
    private static final double TAUT_START = 0.82D;
    private static final int RESCUE_HAUL_INPUT_TIMEOUT_TICKS = 3;
    private static final int RESCUE_HAUL_CYCLE_TICKS = 6;
    private static final double RESCUE_HAUL_BEHIND_DISTANCE = 0.80D;
    private static final double RESCUE_HAUL_NODE_SPEED = 0.30D;
    private static final double RESCUE_HAUL_MUD_PULL_SPEED = 0.060D;
    private static final double RESCUE_HAUL_PLAYER_ACCELERATION = 0.012D;
    private static final double RESCUE_HAUL_MAX_PLAYER_SPEED = 0.14D;
    private static final double RESCUE_HAUL_CLIMB_SPEED = 0.018D;
    private static final int CLIMB_INPUT_TIMEOUT_TICKS = 5;
    private static final Map<ServerLevel, LevelRopes> LEVELS = new WeakHashMap<>();
    private static final Map<UUID, RescueCastIntent> RESCUE_CASTS = new HashMap<>();
    private static final Map<UUID, ClimbInput> CLIMB_INPUTS = new HashMap<>();
    private static final Set<UUID> CLIMB_CONTACTS = new HashSet<>();

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
            ActiveRope restored = ActiveRope.restore(state);
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

    /** Applies ladder-like rope motion after mud has finished its player tick. */
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ClimbInput input = CLIMB_INPUTS.get(player.getUUID());
        if (input == null) {
            CLIMB_CONTACTS.remove(player.getUUID());
            return;
        }
        long now = player.serverLevel().getGameTime();
        if (now - input.gameTime() > CLIMB_INPUT_TIMEOUT_TICKS
                || player.isSpectator() || player.isDeadOrDying()
                || player.getAbilities().flying || isHoldingTuningWand(player)) {
            CLIMB_INPUTS.remove(player.getUUID());
            CLIMB_CONTACTS.remove(player.getUUID());
            return;
        }
        if (findClimbContact(player) == null) {
            CLIMB_CONTACTS.remove(player.getUUID());
            return;
        }
        boolean continuingContact = !CLIMB_CONTACTS.add(player.getUUID());
        if (!continuingContact && !input.jumping() && !input.crouching()) {
            player.setOnGround(false);
            player.resetFallDistance();
            return;
        }
        player.setDeltaMovement(RopeClimbing.motion(
                player.getDeltaMovement(), input.jumping(), input.crouching()));
        player.setOnGround(false);
        player.resetFallDistance();
        player.hasImpulse = true;
        player.hurtMarked = true;
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        CLIMB_INPUTS.remove(event.getEntity().getUUID());
        CLIMB_CONTACTS.remove(event.getEntity().getUUID());
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
        CLIMB_CONTACTS.clear();
    }

    public static void handleDrag(ServerPlayer player, RopeDragPayload payload) {
        if (player.isSpectator() || payload.inputSession() <= 0L
                || payload.inputSequence() <= 0L
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
        if (rope.rescueHaulPlayerId != null) {
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
            if (rope.rescueAnchored && rope.chain.rescueLassoFirstSegment() < 1) {
                rope.clearRescueAnchor(player.serverLevel());
            }
            rope.dragPlayerId = player.getUUID();
            rope.dragging = true;
            rope.lastDragInputTick = player.serverLevel().getGameTime();
        }
    }

    public static void handleAnchor(ServerPlayer player, RopeAnchorPayload payload) {
        if (!player.isCreative() || player.isSpectator()
                || isHoldingTuningWand(player)) {
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
        if (isHoldingTuningWand(player) && payload.breaking()) {
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
        if (player.isSpectator() || isHoldingTuningWand(player)) {
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
        if (player.isSpectator() || isHoldingTuningWand(player) || !emptyHands(player)) {
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
                || !payload.active()) {
            if (player != null) {
                CLIMB_INPUTS.remove(player.getUUID());
                CLIMB_CONTACTS.remove(player.getUUID());
            }
            return;
        }
        CLIMB_INPUTS.put(player.getUUID(), new ClimbInput(
                payload.jumping(), payload.crouching(),
                player.serverLevel().getGameTime()));
    }

    public static void handleRescueHaul(
            ServerPlayer player, RopeRescueHaulPayload payload) {
        LevelRopes ropes = LEVELS.get(player.serverLevel());
        ActiveRope rope = ropes == null ? null : ropes.find(payload.ropeId());
        if (rope == null) {
            return;
        }
        if (!payload.active()) {
            rope.stopRescueHaul(player.getUUID());
            return;
        }
        int lassoFirst = rope.chain.rescueLassoFirstSegment();
        if (player.isSpectator() || isHoldingTuningWand(player)
                || !emptyHands(player)
                || !rope.ownerId.equals(player.getUUID())
                || rope.dragging || lassoFirst < 1
                || payload.segmentIndex() < 0
                || payload.segmentIndex() >= lassoFirst) {
            rope.stopRescueHaul(player.getUUID());
            return;
        }
        boolean established = rope.rescueHaulPlayerId != null
                && rope.rescueHaulPlayerId.equals(player.getUUID());
        boolean sameGrip = established
                && payload.segmentIndex() == rope.rescueHaulGripNode;
        if (!sameGrip && !aimsAtSegment(player, rope.chain, payload.segmentIndex())) {
            // The initial packet is still required to hit the rope. Once the
            // session is established, server and client may briefly disagree
            // about an interpolated segment while the rope is moving. Keep the
            // confirmed session alive instead of dropping it on one ray miss.
            if (!established) {
                rope.stopRescueHaul(player.getUUID());
                return;
            }
            rope.refreshRescueHaulInput(player);
        } else if (!sameGrip) {
            rope.acceptRescueHaul(player, payload.segmentIndex());
        } else {
            rope.refreshRescueHaulInput(player);
        }
        // Rescue hauling advances its internal grip along the rope. The grip
        // is intentionally allowed to move beyond ordinary interaction reach;
        // the session was already validated when it was first established.
    }

    public static RescuePull rescuePull(ServerPlayer player, boolean pulling) {
        if (player == null || player.isSpectator() || isHoldingTuningWand(player)) {
            return RescuePull.NONE;
        }
        LevelRopes ropes = LEVELS.get(player.serverLevel());
        if (ropes == null) {
            return RescuePull.NONE;
        }
        for (ActiveRope rope : ropes.chains) {
            RescuePull haulPull = rope.rescueHaulPull(player);
            if (haulPull.active()) {
                return haulPull;
            }
            if (!pulling) {
                continue;
            }
            RescuePull pull = rope.rescuePull(player);
            if (pull.active()) {
                return pull;
            }
        }
        return RescuePull.NONE;
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

        private void breakChain(ServerLevel level, ActiveRope active,
                int segment, boolean allConnected) {
            Vec3 dropPosition = active.chain.segmentCenter(segment);
            if (dropPosition == null) {
                return;
            }
            int droppedCount = allConnected ? active.chain.segmentCount() : 1;
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
                child.rescueAnchored = source.rescueAnchored;
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
        private boolean rescueHeld;
        private boolean rescueFlying;
        private boolean rescueAnchored;
        private InteractionHand rescueHand = InteractionHand.MAIN_HAND;
        private int lassoFirstNode = -1;
        private int lassoFlightTicks;
        private int outsideMudTicks;
        private Vec3 lassoCenter;
        private Vec3 lassoVelocity = Vec3.ZERO;
        private Vec3 lassoRight;
        private Vec3 lassoUp;
        private BlockPos rescueAnchorPos;
        private BlockState rescueAnchorState;
        private UUID rescueHaulPlayerId;
        private int rescueHaulGripNode = -1;
        private int rescueHaulCycleTicks;
        private boolean rescueHaulTaut;
        private Vec3 rescueHaulFixedTarget;
        private long lastRescueHaulInputTick = Long.MIN_VALUE;

        private ActiveRope(int id, UUID ownerId, RopeChain chain) {
            this.id = id;
            this.ownerId = ownerId;
            this.chain = chain;
        }

        private static ActiveRope rescue(int id, ServerPlayer owner,
                InteractionHand hand, RopeChain chain, int lassoFirstNode,
                Vec3 center, Vec3 velocity, Vec3 right, Vec3 up) {
            ActiveRope rope = new ActiveRope(id, owner.getUUID(), chain);
            rope.rescueFlying = true;
            rope.rescueHand = hand;
            rope.lassoFirstNode = lassoFirstNode;
            rope.lassoCenter = center;
            rope.lassoVelocity = velocity;
            rope.lassoRight = right;
            rope.lassoUp = up;
            return rope;
        }

        private static ActiveRope restore(RopeSavedData.State state) {
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
                restored.rescueAnchored = restored.lassoFirstNode >= 1;
                restored.rescueAnchorPos = state.rescueAnchorPos();
                return restored;
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }

        private void acceptRescueHaul(ServerPlayer player, int aimedSegment) {
            if (!player.getUUID().equals(rescueHaulPlayerId)) {
                releaseRescueGrip();
                rescueHaulPlayerId = player.getUUID();
                rescueHaulGripNode = aimedSegment;
                rescueHaulCycleTicks = 0;
                rescueHaulTaut = false;
                rescueHaulFixedTarget = null;
                Vec3 grip = chain.point(rescueHaulGripNode);
                if (grip != null) {
                    chain.setRescueGripTarget(rescueHaulGripNode, grip);
                }
            } else if (aimedSegment < rescueHaulGripNode) {
                // A ray can briefly hit the previous segment while the rope is
                // moving. Never move the authoritative grip backwards.
                lastRescueHaulInputTick = player.serverLevel().getGameTime();
                return;
            }
            lastRescueHaulInputTick = player.serverLevel().getGameTime();
        }

        private void refreshRescueHaulInput(ServerPlayer player) {
            if (rescueHaulPlayerId != null
                    && rescueHaulPlayerId.equals(player.getUUID())) {
                lastRescueHaulInputTick = player.serverLevel().getGameTime();
            }
        }

        private void stopRescueHaul(UUID playerId) {
            if (rescueHaulPlayerId == null
                    || (playerId != null && !playerId.equals(rescueHaulPlayerId))) {
                return;
            }
            chain.clearRescueGrip();
            rescueHaulPlayerId = null;
            rescueHaulGripNode = -1;
            rescueHaulCycleTicks = 0;
            rescueHaulTaut = false;
            rescueHaulFixedTarget = null;
            lastRescueHaulInputTick = Long.MIN_VALUE;
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
            tickRescue(level);
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
                collision = RopeCollisionWorld.captureCorridors(level,
                        corridors,
                        properties.collisionCapturePadding(),
                        properties.maximumCollisionBlockSamples());
                nextCollisionCapture = age + refresh;
            }
            processPendingDrag(level);
            tickRescueHaul(level);
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

        private void tickRescueHaul(ServerLevel level) {
            if (rescueHaulPlayerId == null) {
                return;
            }
            ServerPlayer player = level.getServer().getPlayerList()
                    .getPlayer(rescueHaulPlayerId);
            int lassoFirst = chain.rescueLassoFirstSegment();
            if (player == null || player.serverLevel() != level
                    || player.isDeadOrDying() || player.isSpectator()
                    || !emptyHands(player) || lassoFirst < 1
                    || rescueHaulGripNode < 0 || rescueHaulGripNode >= lassoFirst
                    || level.getGameTime() - lastRescueHaulInputTick
                            > RESCUE_HAUL_INPUT_TIMEOUT_TICKS) {
                stopRescueHaul(rescueHaulPlayerId);
                return;
            }

            Vec3 desired = rescueHaulTarget(player);
            Vec3 current = chain.point(rescueHaulGripNode);
            Vec3 knot = chain.point(lassoFirst);
            if (current == null || knot == null) {
                stopRescueHaul(rescueHaulPlayerId);
                return;
            }
            int nextGrip = rescueHaulGripNode + 1;
            double available = (lassoFirst - rescueHaulGripNode)
                    * chain.properties().segmentLength();
            Vec3 reachable = collision == null || collision.isEmpty() ? desired
                    : collision.sweep(
                    current, desired, chain.properties().collisionRadius());
            Vec3 goal = clampRescueHaulTarget(reachable, knot, available);
            Vec3 target = clampRescueHaulTarget(
                    moveTowards(current, goal, RESCUE_HAUL_NODE_SPEED),
                    knot, available);
            chain.setRescueGripTarget(rescueHaulGripNode, target);
            double tautness = rescueHaulTautness(target, knot, available);
            rescueHaulTaut = tautness > 0.0D;
            rescueHaulFixedTarget = rescueHaulTaut ? target : null;
            if (rescueHaulTaut) {
                applyRescueHaulPullIfNeeded(player,
                        rescueHaulDirection(player, target, knot), tautness);
            }
            if (target.distanceToSqr(goal) > 0.01D) {
                rescueHaulCycleTicks = 0;
                return;
            }
            if (++rescueHaulCycleTicks < RESCUE_HAUL_CYCLE_TICKS) {
                return;
            }
            rescueHaulCycleTicks = 0;
            if (nextGrip < lassoFirst) {
                advanceRescueHaulGrip(player, nextGrip);
            }
        }

        private static Vec3 rescueHaulTarget(ServerPlayer player) {
            Vec3 forward = player.calculateViewVector(0.0F, player.getYRot());
            forward = new Vec3(forward.x, 0.0D, forward.z);
            if (forward.lengthSqr() <= 1.0E-8D) {
                forward = new Vec3(0.0D, 0.0D, 1.0D);
            } else {
                forward = forward.normalize();
            }
            return player.position().add(0.0D, 0.95D, 0.0D)
                    .subtract(forward.scale(RESCUE_HAUL_BEHIND_DISTANCE));
        }

        private Vec3 rescueHaulDirection(
                ServerPlayer player, Vec3 gripTarget, Vec3 knot) {
            if (gripTarget != null && knot != null) {
                Vec3 ropeDirection = knot.subtract(gripTarget);
                if (ropeDirection.lengthSqr() > 1.0E-8D) {
                    return ropeDirection.normalize();
                }
            }
            Vec3 look = player.getLookAngle();
            return look.lengthSqr() <= 1.0E-8D ? Vec3.ZERO : look.normalize();
        }

        private void advanceRescueHaulGrip(ServerPlayer player, int nextGrip) {
            Vec3 horizontalLook = new Vec3(
                    player.getLookAngle().x, 0.0D, player.getLookAngle().z);
            Vec3 releasedVelocity = horizontalLook.lengthSqr() <= 1.0E-8D
                    ? new Vec3(0.0D, -0.02D, 0.0D)
                    : horizontalLook.normalize().scale(-0.16D)
                            .add(0.0D, -0.02D, 0.0D);
            Vec3 nextPosition = chain.point(nextGrip);
            rescueHaulGripNode = nextGrip;
            rescueHaulCycleTicks = 0;
            rescueHaulTaut = false;
            rescueHaulFixedTarget = null;
            chain.moveRescueGripTarget(
                    rescueHaulGripNode, nextPosition, releasedVelocity);
        }

        private static void applyRescueHaulPull(
                ServerPlayer player, Vec3 ropeDirection, double tautness) {
            player.setDeltaMovement(rescueHaulMotion(
                    player.getDeltaMovement(), ropeDirection, tautness,
                    true));
            player.hurtMarked = true;
        }

        private static void applyRescueHaulPullIfNeeded(
                ServerPlayer player, Vec3 ropeDirection, double tautness) {
            MudPlayerData data = MudStateStore.get(player);
            if (!data.inMud && !data.debugPhysicalized) {
                applyRescueHaulPull(player, ropeDirection, tautness);
            }
        }

        private void tickRescue(ServerLevel level) {
            if (!rescueHeld && !rescueFlying && !rescueAnchored) {
                return;
            }
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(ownerId);
            if (player == null || player.serverLevel() != level
                    || player.isDeadOrDying() || player.isSpectator()) {
                releaseRescueGrip();
                cancelLassoFlight();
                return;
            }
            if (rescueAnchored && rescueAnchorPos != null
                    && (rescueAnchorState == null
                            ? level.getBlockState(rescueAnchorPos).isAir()
                            : !level.getBlockState(rescueAnchorPos).equals(rescueAnchorState))) {
                clearRescueAnchor(level);
                return;
            }

            Vec3 look = player.getLookAngle().normalize();
            Vec3 hand = handPosition(player, rescueHand, look);
            if (rescueHeld) {
                chain.setRescueGripTarget(0, hand);
            }
            if (rescueFlying) {
                advanceLasso(level, player, hand);
                return;
            }
            if (!rescueAnchored) {
                releaseRescueGrip();
                return;
            }
            if (MudPhysics.hasSinkingContact(player)) {
                outsideMudTicks = 0;
            } else if (++outsideMudTicks > RESCUE_RELEASE_GRACE_TICKS) {
                releaseRescueGrip();
            }
        }

        private void advanceLasso(ServerLevel level, ServerPlayer player, Vec3 hand) {
            if (lassoCenter == null || lassoRight == null || lassoUp == null
                    || lassoFirstNode < 1
                    || ++lassoFlightTicks > MAXIMUM_LASSO_FLIGHT_TICKS) {
                cancelLassoFlight();
                releaseRescueGrip();
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
                    rescueFlying = false;
                    rescueAnchored = true;
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
                releaseRescueGrip();
                return;
            }

            if (collision != null && !collision.isEmpty()) {
                Vec3 resolved = collision.sweep(
                        lassoCenter, desired, chain.properties().collisionRadius());
                if (resolved.distanceToSqr(desired) > 1.0E-5D) {
                    cancelLassoFlight();
                    releaseRescueGrip();
                    return;
                }
            }
            lassoCenter = desired;
            chain.setMovingLasso(lassoFirstNode,
                    lassoPoints(lassoCenter, lassoRight, lassoUp, 0.0D));
        }

        private void cancelLassoFlight() {
            if (rescueFlying) {
                chain.clearMovingLasso();
            }
            rescueFlying = false;
            lassoCenter = null;
            lassoVelocity = Vec3.ZERO;
        }

        private void releaseRescueGrip() {
            if (rescueHeld) {
                chain.clearRescueGrip();
            }
            rescueHeld = false;
            outsideMudTicks = 0;
        }

        private void clearRescueAnchor(ServerLevel level) {
            chain.clearRescueAnchors();
            rescueAnchored = false;
            rescueAnchorPos = null;
            rescueAnchorState = null;
            lassoFirstNode = -1;
            stopRescueHaul(null);
            releaseRescueGrip();
            send(level, false, 1);
        }

        private RescuePull rescuePull(ServerPlayer player) {
            if (!rescueHeld || !rescueAnchored
                    || !ownerId.equals(player.getUUID()) || lassoFirstNode < 1) {
                return RescuePull.NONE;
            }
            Vec3 grip = chain.point(0);
            Vec3 knot = chain.point(lassoFirstNode);
            if (grip == null || knot == null) {
                return RescuePull.NONE;
            }
            double available = lassoFirstNode * chain.properties().segmentLength();
            double ratio = grip.distanceTo(knot) / Math.max(available, 0.01D);
            double tautness = Mth.clamp((ratio - TAUT_START) / (1.0D - TAUT_START),
                    0.0D, 1.0D);
            Vec3 direction = knot.subtract(grip);
            if (tautness <= 0.0D || direction.lengthSqr() <= 1.0E-10D) {
                return RescuePull.NONE;
            }
            direction = direction.normalize();
            double pullSpeed = 0.060D * tautness;
            double sinkRelief = 0.035D * tautness;
            Vec3 motion = direction.scale(pullSpeed);
            if (!player.horizontalCollision) {
                motion = new Vec3(motion.x, 0.0D, motion.z);
            }
            return new RescuePull(true, motion, sinkRelief, tautness);
        }

        private RescuePull rescueHaulPull(ServerPlayer player) {
            if (rescueHaulPlayerId == null
                    || !rescueHaulPlayerId.equals(player.getUUID())
                    || !rescueHaulTaut || rescueHaulFixedTarget == null
                    || lassoFirstNode < 1) {
                return RescuePull.NONE;
            }
            Vec3 knot = chain.point(lassoFirstNode);
            if (knot == null) {
                return RescuePull.NONE;
            }
            double available = (lassoFirstNode - rescueHaulGripNode)
                    * chain.properties().segmentLength();
            double tautness = rescueHaulTautness(
                    rescueHaulFixedTarget, knot, available);
            if (tautness <= 0.0D) {
                return RescuePull.NONE;
            }
            Vec3 direction = rescueHaulDirection(player,
                    rescueHaulFixedTarget, knot);
            Vec3 motion = rescueHaulMudPull(direction, tautness);
            return new RescuePull(true, motion, 0.035D * tautness, tautness);
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
        Vec3 start = chain.point(segment);
        Vec3 end = chain.point(segment + 1);
        if (start == null || end == null) {
            return false;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 direction = player.getLookAngle();
        double range = Math.max(0.0D, player.blockInteractionRange());
        if (direction.lengthSqr() <= 1.0E-8D || range <= 0.0D) {
            return false;
        }
        double hitDistance = RopeHitGeometry.rayCapsuleHitDistance(
                eye, direction, start, end,
                RopeHitGeometry.SELECTION_RADIUS, range);
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

    private static Vec3 moveTowards(Vec3 from, Vec3 to, double maximumDistance) {
        Vec3 offset = to.subtract(from);
        double distance = offset.length();
        return distance <= maximumDistance || distance <= 1.0E-10D
                ? to : from.add(offset.scale(maximumDistance / distance));
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

    static Vec3 rescueHaulMotion(
            Vec3 currentMotion, Vec3 ropeDirection, double tautness) {
        return rescueHaulMotion(currentMotion, ropeDirection, tautness, false);
    }

    static Vec3 rescueHaulMudPull(Vec3 ropeDirection, double tautness) {
        if (!validDirection(ropeDirection)) {
            return Vec3.ZERO;
        }
        return ropeDirection.normalize().scale(
                RESCUE_HAUL_MUD_PULL_SPEED
                        * Mth.clamp(tautness, 0.0D, 1.0D));
    }

    static double rescueHaulTautness(Vec3 target, Vec3 knot, double available) {
        if (target == null || knot == null || !validVector(target)
                || !validVector(knot)) {
            return 0.0D;
        }
        double ratio = target.distanceTo(knot) / Math.max(available, 0.01D);
        return Mth.clamp((ratio - TAUT_START) / (1.0D - TAUT_START), 0.0D, 1.0D);
    }

    static Vec3 rescueHaulMotion(
            Vec3 currentMotion, Vec3 ropeDirection, double tautness,
            boolean allowClimb) {
        if (currentMotion == null || ropeDirection == null) {
            return currentMotion;
        }
        Vec3 horizontal = new Vec3(ropeDirection.x, 0.0D, ropeDirection.z);
        double strength = Mth.clamp(tautness, 0.0D, 1.0D);
        if (strength <= 0.0D) {
            return currentMotion;
        }
        Vec3 result = currentMotion;
        if (horizontal.lengthSqr() > 1.0E-8D) {
            Vec3 axis = horizontal.normalize();
            double opposing = currentMotion.x * axis.x + currentMotion.z * axis.z;
            if (opposing < 0.0D) {
                result = result.subtract(axis.scale(opposing));
            }
            Vec3 impulse = horizontal.normalize().scale(
                    RESCUE_HAUL_PLAYER_ACCELERATION * strength);
            result = result.add(impulse.x, 0.0D, impulse.z);
            double horizontalSpeed = Math.sqrt(
                    result.x * result.x + result.z * result.z);
            if (horizontalSpeed > RESCUE_HAUL_MAX_PLAYER_SPEED) {
                double scale = RESCUE_HAUL_MAX_PLAYER_SPEED / horizontalSpeed;
                result = new Vec3(result.x * scale, result.y, result.z * scale);
            }
        }
        double upwardDirection = Mth.clamp(ropeDirection.y, 0.0D, 1.0D);
        if (allowClimb && upwardDirection > 0.0D) {
            double climbSpeed = RESCUE_HAUL_CLIMB_SPEED
                    * upwardDirection * strength;
            result = new Vec3(result.x, Math.max(result.y, climbSpeed), result.z);
        }
        return result;
    }

    private record PendingDrag(UUID playerId, int segmentIndex, RopeFrame frame,
            Vec3 viewOrigin, Vec3 viewDirection, long inputSession,
            long inputSequence, boolean dragging) {
    }

    private record RescueCastIntent(InteractionHand hand, long gameTime) {
    }

    private record ClimbInput(boolean jumping, boolean crouching, long gameTime) {
    }

    public record RescuePull(
            boolean active, Vec3 motion, double sinkRelief, double tautness) {
        public static final RescuePull NONE = new RescuePull(
                false, Vec3.ZERO, 0.0D, 0.0D);
    }

    static int breakDurationTicks(boolean allConnected) {
        return allConnected ? SURVIVAL_BREAK_TICKS * 2 : SURVIVAL_BREAK_TICKS;
    }
}

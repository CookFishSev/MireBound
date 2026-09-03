package com.fish.mirebound.client.rope;

import com.fish.mirebound.network.payload.RopeAnchorPayload;
import com.fish.mirebound.network.payload.RopeBreakPayload;
import com.fish.mirebound.network.payload.RopeConnectPayload;
import com.fish.mirebound.network.payload.RopeDragPayload;
import com.fish.mirebound.network.payload.RopeExtendPayload;
import com.fish.mirebound.network.payload.RopeRescueCastPayload;
import com.fish.mirebound.network.payload.RopeRescueHaulPayload;
import com.fish.mirebound.network.payload.RopeClimbInputPayload;
import com.fish.mirebound.network.payload.RopeSnapshotPayload;
import com.fish.mirebound.network.payload.RopeInteractionReleasePayload;
import com.fish.mirebound.client.compat.freecam.FreecamCompat;
import com.fish.mirebound.registry.ModBlocks;
import com.fish.mirebound.registry.ModMudworkContent;
import com.fish.mirebound.rope.RopeFrame;
import com.fish.mirebound.rope.RopeHitGeometry;
import com.fish.mirebound.rope.RopeClimbing;
import com.fish.mirebound.rope.RopeItem;
import com.fish.mirebound.rope.RopeSegmentOrientation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Client snapshot interpolation and direct manipulation for rope segments. */
public final class ClientRopes {
    private static final int STALE_TICKS = 60;
    private static final double ROTATE_SENSITIVITY = 0.35D;
    private static final int DRAG_HEARTBEAT_TICKS = 1;
    private static final int ANCHOR_RETRY_TICKS = 2;
    private static final int ANCHOR_CONFIRM_TIMEOUT_TICKS = 30;
    private static final int BREAK_DURATION_TICKS = 15;
    private static final int RESCUE_HAUL_HOLD_TICKS = 5;
    private static final Map<Integer, InterpolatedRope> ROPES = new HashMap<>();
    private static ClientLevel level;
    private static Selection selected;
    private static boolean dragging;
    private static boolean suppressRightRelease;
    private static long lastDragSendTick = Long.MIN_VALUE;
    private static long nextDragSession = 1L;
    private static long dragInputSession;
    private static long dragInputSequence;
    private static int dragRopeId = -1;
    private static int dragSegmentIndex = -1;
    private static RopeFrame dragFrame;
    private static int pendingAnchorRopeId = -1;
    private static int pendingAnchorSegment = -1;
    private static long pendingAnchorStartTick = Long.MIN_VALUE;
    private static long lastAnchorSendTick = Long.MIN_VALUE;
    private static boolean breaking;
    private static int breakRopeId = -1;
    private static int breakSegment = -1;
    private static boolean breakAllConnected;
    private static long breakStartTick;
    private static long lastBreakSendTick = Long.MIN_VALUE;
    private static long viewRevision;
    private static long cachedViewRevision = Long.MIN_VALUE;
    private static long cachedViewTick = Long.MIN_VALUE;
    private static int cachedViewPartialBits;
    private static List<View> cachedViews = List.of();
    private static boolean rescueCastArmed;
    private static Selection pendingRescueHaul;
    private static long pendingRescueHaulStartTick = Long.MIN_VALUE;
    private static boolean rescueHauling;
    private static int rescueHaulRopeId = -1;
    private static int rescueHaulSegment = -1;
    private static int rescueHaulInputSegment = -1;
    private static long lastRescueHaulSendTick = Long.MIN_VALUE;
    private static boolean climbInputActive;
    private static boolean climbInputJumping;
    private static boolean climbInputCrouching;
    private static int climbInputRefreshTicks;

    private ClientRopes() {
    }

    public static void accept(RopeSnapshotPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ensureLevel(minecraft)) {
            return;
        }
        if (payload.removed()) {
            ROPES.remove(payload.ropeId());
            if (payload.ropeId() == pendingAnchorRopeId
                    || dragging && payload.ropeId() == dragRopeId) {
                clearDragVisualState();
            }
            if (pendingRescueHaul != null
                    && pendingRescueHaul.ropeId() == payload.ropeId()) {
                clearPendingRescueHaul();
            }
            if (rescueHauling && rescueHaulRopeId == payload.ropeId()) {
                clearRescueHaulVisualState();
            }
            if (breaking && breakRopeId == payload.ropeId()) {
                clearBreaking();
            }
            viewRevision++;
            return;
        }
        long tick = minecraft.level.getGameTime();
        InterpolatedRope rope = ROPES.get(payload.ropeId());
        if (rope == null) {
            ROPES.put(payload.ropeId(), new InterpolatedRope(payload, tick));
        } else {
            rope.accept(payload, tick);
        }
        if (payload.ropeId() == pendingAnchorRopeId
                && payload.anchoredOrientations().stream()
                        .anyMatch(orientation -> orientation.segment() == pendingAnchorSegment)) {
            clearDragVisualState();
        }
        viewRevision++;
    }

    public static void releaseFromServer(RopeInteractionReleasePayload payload) {
        if (payload.rescue()) {
            if (pendingRescueHaul != null
                    && pendingRescueHaul.ropeId() == payload.ropeId()) {
                clearPendingRescueHaul();
            }
            if (rescueHauling && rescueHaulRopeId == payload.ropeId()) {
                clearRescueHaulVisualState();
            }
        } else if (dragging && dragRopeId == payload.ropeId()) {
            clearDragVisualState();
        }
        selected = null;
        viewRevision++;
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ensureLevel(minecraft)) {
            return;
        }
        long now = minecraft.level.getGameTime();
        if (ROPES.values().removeIf(rope -> now - rope.receivedTick > STALE_TICKS)) {
            viewRevision++;
        }
        viewRevision++;
        updateInteraction(minecraft);
        updateClimbInput(minecraft);
        if (rescueCastArmed && (!minecraft.player.isUsingItem()
                || !(minecraft.player.getUseItem().getItem() instanceof RopeItem))) {
            PacketDistributor.sendToServer(new RopeRescueCastPayload(false));
            rescueCastArmed = false;
        }
    }

    public static List<View> views(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ensureLevel(minecraft) || ROPES.isEmpty()) {
            return List.of();
        }
        int partialBits = Float.floatToIntBits(partialTick);
        if (cachedViewRevision == viewRevision
                && cachedViewTick == minecraft.level.getGameTime()
                && cachedViewPartialBits == partialBits) {
            return cachedViews;
        }
        List<View> result = new ArrayList<>(ROPES.size());
        for (InterpolatedRope rope : ROPES.values()) {
            List<Vec3> nodes = rope.pose(partialTick);
            if (nodes.size() < 2) {
                continue;
            }
            boolean localDrag = dragging && rope.id == dragRopeId
                    && dragFrame != null && dragSegmentIndex >= 0
                    && dragSegmentIndex < nodes.size() - 1;
            if (localDrag) {
                nodes = RopeSegmentPose.withRigidSegment(
                        nodes, dragSegmentIndex, dragFrame,
                        RopeSegmentSpec.INNER.halfLength() * 2.0D);
            }
            RopeSegmentPose.Frame[] frames = RopeSegmentPose.frames(nodes);
            ArrayList<Integer> anchors = new ArrayList<>(
                    anchoredSegments(rope.anchoredOrientations));
            ArrayList<Integer> rescueAnchors = new ArrayList<>(
                    anchoredSegments(rope.rescueAnchoredOrientations));
            for (RopeSegmentOrientation orientation : rope.anchoredOrientations) {
                applyOrientation(frames, orientation);
            }
            if (rope.draggedOrientation != null) {
                applyOrientation(frames, rope.draggedOrientation);
            }
            int draggedSegment = rope.draggedOrientation == null
                    ? -1 : rope.draggedOrientation.segment();
            if (localDrag) {
                // Use the local input frame for the held segment. The server remains
                // authoritative for position, while rotation must not wait for a snapshot.
                frames[dragSegmentIndex] = RopeSegmentPose.fromRopeFrame(dragFrame);
                draggedSegment = dragSegmentIndex;
            }
            result.add(new View(rope.id, nodes, bounds(nodes), frames,
                    List.copyOf(anchors), List.copyOf(rescueAnchors), draggedSegment));
        }
        cachedViews = List.copyOf(result);
        cachedViewRevision = viewRevision;
        cachedViewTick = minecraft.level.getGameTime();
        cachedViewPartialBits = partialBits;
        return cachedViews;
    }

    private static void applyOrientation(RopeSegmentPose.Frame[] frames,
            RopeSegmentOrientation orientation) {
        if (orientation.frame() != null
                && orientation.segment() >= 0 && orientation.segment() < frames.length) {
            frames[orientation.segment()] = RopeSegmentPose.fromRopeFrame(orientation.frame());
        }
    }

    private static List<Integer> anchoredSegments(
            List<RopeSegmentOrientation> orientations) {
        List<Integer> result = new ArrayList<>(orientations.size());
        for (RopeSegmentOrientation orientation : orientations) {
            result.add(orientation.segment());
        }
        return List.copyOf(result);
    }

    public static void reset() {
        ROPES.clear();
        level = null;
        selected = null;
        dragging = false;
        suppressRightRelease = false;
        lastDragSendTick = Long.MIN_VALUE;
        dragFrame = null;
        clearPendingAnchor();
        breaking = false;
        breakRopeId = -1;
        breakSegment = -1;
        breakAllConnected = false;
        breakStartTick = 0L;
        lastBreakSendTick = Long.MIN_VALUE;
        viewRevision++;
        cachedViewRevision = Long.MIN_VALUE;
        cachedViews = List.of();
        rescueCastArmed = false;
        clearPendingRescueHaul();
        clearRescueHaulVisualState();
        clearClimbInputState();
    }

    public static boolean onMouseButton(int button, int action) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) {
            return false;
        }
        if (!canInteract(minecraft)) {
            cancelInteraction(minecraft);
            return false;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && action == GLFW.GLFW_PRESS
                && minecraft.player.isUsingItem()
                && minecraft.player.getUseItem().getItem() instanceof RopeItem) {
            if (!rescueCastArmed) {
                rescueCastArmed = true;
                PacketDistributor.sendToServer(new RopeRescueCastPayload(true));
            }
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (action == GLFW.GLFW_PRESS) {
                if (dragging) {
                    if (minecraft.player.isCreative()
                            && dragRopeId >= 0 && dragSegmentIndex >= 0) {
                        if (pendingAnchorRopeId == dragRopeId
                                && pendingAnchorSegment == dragSegmentIndex) {
                            sendAnchor(minecraft, true);
                        } else {
                            requestAnchor(minecraft);
                        }
                        return true;
                    }
                    return false;
                }
                if (selected != null) {
                    beginBreaking(minecraft, selected);
                    minecraft.player.swing(InteractionHand.MAIN_HAND);
                    return true;
                }
            }
            if (action == GLFW.GLFW_RELEASE && breaking) {
                sendBreak(minecraft, false, true);
                clearBreaking();
                return true;
            }
            return false;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            return false;
        }
        if (action == GLFW.GLFW_RELEASE && rescueHauling) {
            stopRescueHaul(minecraft);
            suppressRightRelease = false;
            return true;
        }
        if (action == GLFW.GLFW_RELEASE && pendingRescueHaul != null) {
            Selection pending = pendingRescueHaul;
            clearPendingRescueHaul();
            suppressRightRelease = false;
            if (emptyHands(minecraft.player)) {
                beginDrag(minecraft, pending);
            }
            return true;
        }
        if (action == GLFW.GLFW_RELEASE && suppressRightRelease) {
            suppressRightRelease = false;
            return true;
        }
        if (isHoldingRope(minecraft.player)) {
            if (action == GLFW.GLFW_PRESS) {
                Selection hit = findHit(minecraft);
                selected = hit;
                if (isExtendableSelection(hit)) {
                    PacketDistributor.sendToServer(new RopeExtendPayload(
                            hit.ropeId(), hit.segmentIndex()));
                    return true;
                }
            }
            return selected != null
                    && (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_RELEASE);
        }
        if (!emptyHands(minecraft.player)) {
            return false;
        }
        if (action == GLFW.GLFW_PRESS) {
            suppressRightRelease = true;
            if (dragging) {
                Selection connect = findConnectHit(minecraft);
                if (connect != null) {
                    PacketDistributor.sendToServer(new RopeConnectPayload(
                            dragRopeId, dragSegmentIndex,
                            connect.ropeId(), connect.segmentIndex()));
                    selected = null;
                    return true;
                }
                releaseDrag(minecraft);
                return true;
            }
            if (selected == null) {
                suppressRightRelease = false;
                return false;
            }
            if (isRescueHaulSelection(selected)) {
                pendingRescueHaul = selected;
                pendingRescueHaulStartTick = minecraft.level.getGameTime();
                return true;
            }
            beginDrag(minecraft, selected);
            return true;
        }
        return false;
    }

    private static void beginBreaking(Minecraft minecraft, Selection selection) {
        breaking = true;
        breakRopeId = selection.ropeId();
        breakSegment = selection.segmentIndex();
        breakAllConnected = shiftDown(minecraft);
        breakStartTick = minecraft.level.getGameTime();
        lastBreakSendTick = Long.MIN_VALUE;
        sendBreak(minecraft, true, true);
    }

    private static void clearBreaking() {
        breaking = false;
        breakRopeId = -1;
        breakSegment = -1;
        breakAllConnected = false;
        breakStartTick = 0L;
        lastBreakSendTick = Long.MIN_VALUE;
    }

    public static boolean isBreaking() {
        return breaking;
    }

    private static boolean emptyHands(net.minecraft.client.player.LocalPlayer player) {
        return player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty();
    }

    public static boolean isHoldingRope(net.minecraft.client.player.LocalPlayer player) {
        return player != null
                && (player.getMainHandItem().getItem() == ModMudworkContent.ROPE.get()
                || player.getOffhandItem().getItem() == ModMudworkContent.ROPE.get());
    }

    public static boolean isHoldingTuningWand(
            net.minecraft.client.player.LocalPlayer player) {
        return player != null
                && (player.getMainHandItem().getItem() == ModBlocks.MUD_TUNING_WAND.get()
                || player.getOffhandItem().getItem() == ModBlocks.MUD_TUNING_WAND.get());
    }

    public static List<Selection> endpointSelections(float partialTick) {
        List<Selection> result = new ArrayList<>();
        for (View view : views(partialTick)) {
            int segmentCount = view.nodes().size() - 1;
            int rescueFirst = rescueLassoFirstSegment(view);
            int[] endpoints = {0, segmentCount - 1};
            for (int segment : endpoints) {
                if (segment < 0 || segment >= segmentCount
                        || (rescueFirst >= 0 && segment >= rescueFirst)) {
                    continue;
                }
                if (view.anchoredSegments().contains(segment)
                        || view.rescueAnchoredSegments().contains(segment)) {
                    continue;
                }
                Vec3 start = view.nodes().get(segment);
                Vec3 end = view.nodes().get(segment + 1);
                result.add(new Selection(view.id(), segment, start, end,
                        Double.POSITIVE_INFINITY, start.lerp(end, 0.5D),
                        view.frames()[segment], false,
                        view.anchoredSegments().contains(segment)
                                || view.rescueAnchoredSegments().contains(segment)));
            }
        }
        return List.copyOf(result);
    }

    /** Returns visible, unlocked endpoints of other ropes while an endpoint is dragged. */
    public static List<Selection> connectSelections(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!dragging || dragRopeId < 0 || dragSegmentIndex < 0
                || minecraft.player == null || minecraft.level == null) {
            return List.of();
        }
        View source = null;
        for (View view : views(partialTick)) {
            if (view.id() == dragRopeId) {
                source = view;
                break;
            }
        }
        if (source == null || dragSegmentIndex != 0
                && dragSegmentIndex != source.nodes().size() - 2
                || rescueLassoFirstSegment(source) >= 0) {
            return List.of();
        }
        Vec3 eye = minecraft.player.getEyePosition(partialTick);
        Vec3 direction = minecraft.player.getViewVector(partialTick);
        if (direction.lengthSqr() <= 1.0E-8D) {
            return List.of();
        }
        direction = direction.normalize();
        double range = connectionPickRange(minecraft, eye, direction);
        List<Selection> result = new ArrayList<>();
        for (View view : views(partialTick)) {
            if (view.id() == dragRopeId || rescueLassoFirstSegment(view) >= 0) {
                continue;
            }
            int count = view.nodes().size() - 1;
            int[] endpoints = {0, count - 1};
            for (int segment : endpoints) {
                if (segment < 0 || segment >= count
                        || view.anchoredSegments().contains(segment)
                        || view.rescueAnchoredSegments().contains(segment)) {
                    continue;
                }
                Vec3 start = view.nodes().get(segment);
                Vec3 end = view.nodes().get(segment + 1);
                double hitDistance = RopeHitGeometry.rayCapsuleHitDistance(
                        eye, direction, start, end,
                        RopeHitGeometry.SELECTION_RADIUS, range);
                if (!Double.isFinite(hitDistance)) {
                    continue;
                }
                result.add(new Selection(view.id(), segment, start, end,
                        hitDistance, eye.add(direction.scale(hitDistance)), view.frames()[segment],
                        false, false));
            }
        }
        return List.copyOf(result);
    }

    private static boolean shiftDown(Minecraft minecraft) {
        long window = minecraft.getWindow().getWindow();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    /** Applies Tab rotation in the camera's absolute coordinate system. */
    public static boolean onMouseTurn(double yaw, double pitch) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!dragging || dragFrame == null || minecraft.player == null
                || minecraft.screen != null || !tabDown(minecraft)) {
            return false;
        }
        Camera camera = minecraft.gameRenderer.getMainCamera();
        if (camera == null || !camera.isInitialized()) {
            return false;
        }
        ScreenPlaneAxes axes = screenPlaneAxes(
                new Vec3(camera.getLookVector()), new Vec3(camera.getUpVector()));
        if (axes == null) {
            return false;
        }
        dragFrame = dragFrame.applyScreenPlaneInput(
                yaw, pitch, axes.up(), axes.right(), ROTATE_SENSITIVITY);
        viewRevision++;
        return true;
    }

    static ScreenPlaneAxes screenPlaneAxes(Vec3 forward, Vec3 approximateUp) {
        if (forward == null || approximateUp == null
                || forward.lengthSqr() <= 1.0E-10D
                || approximateUp.lengthSqr() <= 1.0E-10D) {
            return null;
        }
        Vec3 normal = forward.normalize();
        Vec3 up = approximateUp.subtract(
                normal.scale(approximateUp.dot(normal)));
        if (up.lengthSqr() <= 1.0E-10D) {
            return null;
        }
        up = up.normalize();
        Vec3 right = up.cross(normal);
        if (right.lengthSqr() <= 1.0E-10D) {
            return null;
        }
        return new ScreenPlaneAxes(up, right.normalize());
    }

    public static boolean isDragging() {
        return dragging;
    }

    public static boolean isRescueCastArmed() {
        return rescueCastArmed;
    }

    private static boolean tabDown(Minecraft minecraft) {
        return GLFW.glfwGetKey(minecraft.getWindow().getWindow(), GLFW.GLFW_KEY_TAB)
                == GLFW.GLFW_PRESS;
    }

    public static List<Selection> anchoredSelections(float partialTick) {
        List<Selection> result = new ArrayList<>();
        for (View view : views(partialTick)) {
            int rescueFirst = rescueLassoFirstSegment(view);
            for (int segment : view.anchoredSegments()) {
                if (segment < 0 || segment >= view.nodes().size() - 1) {
                    continue;
                }
                if (dragging && view.id() == dragRopeId && segment == dragSegmentIndex) {
                    continue;
                }
                if (rescueFirst >= 0 && segment >= rescueFirst) {
                    continue;
                }
                Vec3 start = view.nodes().get(segment);
                Vec3 end = view.nodes().get(segment + 1);
                result.add(new Selection(view.id(), segment, start, end,
                        Double.POSITIVE_INFINITY, start.lerp(end, 0.5D),
                        view.frames()[segment], false, true));
            }
        }
        return List.copyOf(result);
    }

    public static List<Selection> rescueAnchoredSelections(float partialTick) {
        List<Selection> result = new ArrayList<>();
        for (View view : views(partialTick)) {
            for (int segment : view.rescueAnchoredSegments()) {
                if (segment < 0 || segment >= view.nodes().size() - 1) {
                    continue;
                }
                if (dragging && view.id() == dragRopeId && segment == dragSegmentIndex) {
                    continue;
                }
                Vec3 start = view.nodes().get(segment);
                Vec3 end = view.nodes().get(segment + 1);
                result.add(new Selection(view.id(), segment, start, end,
                        Double.POSITIVE_INFINITY, start.lerp(end, 0.5D),
                        view.frames()[segment], false, true));
            }
        }
        return List.copyOf(result);
    }

    public static Selection rescueHaulSelection(float partialTick) {
        if (!rescueHauling || rescueHaulRopeId < 0 || rescueHaulSegment < 0) {
            return null;
        }
        for (View view : views(partialTick)) {
            if (view.id() == rescueHaulRopeId) {
                int segment = Mth.clamp(rescueHaulSegment, 0,
                        view.nodes().size() - 2);
                Vec3 start = view.nodes().get(segment);
                Vec3 end = view.nodes().get(segment + 1);
                return new Selection(view.id(), segment, start, end,
                        0.0D, start.lerp(end, 0.5D), view.frames()[segment],
                        false, false);
            }
        }
        return null;
    }

    public static Selection selection(float partialTick) {
        if (selected == null) {
            return null;
        }
        for (View view : views(partialTick)) {
            int segment = selected.segmentIndex();
            if (view.id() == selected.ropeId() && segment >= 0
                    && segment + 1 < view.nodes().size()) {
                return new Selection(view.id(), segment, view.nodes().get(segment),
                        view.nodes().get(segment + 1), selected.distance(), selected.hitPoint(),
                        view.frames()[segment], dragging,
                        !dragging && (view.anchoredSegments().contains(segment)
                                || view.rescueAnchoredSegments().contains(segment)));
            }
        }
        return null;
    }

    public static Selection selection() {
        return selection(currentPartialTick(Minecraft.getInstance()));
    }

    private static void updateInteraction(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null
                || minecraft.screen != null || minecraft.options.hideGui) {
            if (rescueHauling) {
                stopRescueHaul(minecraft);
            }
            if (dragging) {
                releaseDrag(minecraft);
            }
            if (breaking) {
                sendBreak(minecraft, false, true);
            }
            selected = null;
            clearPendingRescueHaul();
            clearDragVisualState();
            clearBreaking();
            return;
        }
        if (!canInteract(minecraft)) {
            cancelInteraction(minecraft);
            return;
        }
        if (pendingAnchorRopeId >= 0) {
            if (!dragging || !minecraft.player.isCreative()
                    || !emptyHands(minecraft.player)) {
                releaseDrag(minecraft);
                return;
            }
            long age = minecraft.level.getGameTime() - pendingAnchorStartTick;
            if (age > ANCHOR_CONFIRM_TIMEOUT_TICKS) {
                releaseDrag(minecraft);
                return;
            }
            sendAnchor(minecraft, false);
            return;
        }
        if (pendingRescueHaul != null) {
            if (!emptyHands(minecraft.player)) {
                clearPendingRescueHaul();
                return;
            }
            if (!rightButtonDown(minecraft)) {
                return;
            }
            Selection hit = findHit(
                    minecraft, rescueHaulRopeId, rescueHaulSegment);
            if (hit == null || hit.ropeId() != pendingRescueHaul.ropeId()
                    || !isRescueHaulSelection(hit)) {
                clearPendingRescueHaul();
                return;
            }
            selected = hit;
            if (minecraft.level.getGameTime() - pendingRescueHaulStartTick
                    >= RESCUE_HAUL_HOLD_TICKS) {
                clearPendingRescueHaul();
                rescueHauling = true;
                rescueHaulRopeId = hit.ropeId();
                rescueHaulSegment = hit.segmentIndex();
                rescueHaulInputSegment = rescueHaulSegment;
                lastRescueHaulSendTick = Long.MIN_VALUE;
                sendRescueHaul(minecraft, true, true);
            }
            return;
        }
        if (breaking) {
            if (!minecraft.options.keyAttack.isDown() && !leftButtonDown(minecraft)) {
                sendBreak(minecraft, false, true);
                clearBreaking();
            } else {
                if (!minecraft.player.swinging
                        || minecraft.player.swingTime >= minecraft.player.getCurrentSwingDuration() / 2) {
                    minecraft.player.swing(InteractionHand.MAIN_HAND);
                }
                sendBreak(minecraft, true, false);
            }
            return;
        }
        if (rescueHauling) {
            if (!emptyHands(minecraft.player) || !rightButtonDown(minecraft)) {
                stopRescueHaul(minecraft);
                return;
            }
            Selection hit = findHit(minecraft);
            if (hit == null || hit.ropeId() != rescueHaulRopeId
                    || !isRescueHaulSelection(hit)) {
                // The server owns the active rescue session. A moving rope can
                // briefly miss the client ray between snapshots; keep sending
                // the last confirmed segment instead of treating that as a
                // right-button release.
                sendRescueHaul(minecraft, true, false);
                return;
            }
            selected = hit;
            rescueHaulInputSegment = hit.segmentIndex();
            rescueHaulSegment = Math.max(rescueHaulSegment, hit.segmentIndex());
            sendRescueHaul(minecraft, true, false);
            return;
        }
        if (dragging) {
            if (!emptyHands(minecraft.player)) {
                releaseDrag(minecraft);
                return;
            }
            sendDrag(minecraft, true, false);
            return;
        }
        selected = findHit(minecraft);
    }

    private static void updateClimbInput(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) {
            clearClimbInputState();
            return;
        }
        boolean wasActive = climbInputActive;
        boolean wantsJump = minecraft.player.input.jumping;
        boolean wantsCrouch = minecraft.player.isShiftKeyDown();
        boolean shouldProbe = wantsJump || wantsCrouch || climbInputActive
                || minecraft.player.getDeltaMovement().y < -0.01D;
        boolean active = shouldProbe && minecraft.screen == null
                && canInteract(minecraft) && findClimbContact(minecraft) != null;
        boolean jumping = active && wantsJump;
        boolean crouching = active && wantsCrouch;
        if (active && (wasActive || jumping || crouching)) {
            minecraft.player.setDeltaMovement(RopeClimbing.motion(
                    minecraft.player.getDeltaMovement(), jumping, crouching));
            minecraft.player.setOnGround(false);
            minecraft.player.resetFallDistance();
        } else if (active) {
            // Preserve the incoming fall speed on the first contact tick. The
            // ladder-like slide limit starts on the following tick.
            minecraft.player.setOnGround(false);
            minecraft.player.resetFallDistance();
        }
        boolean changed = active != climbInputActive
                || jumping != climbInputJumping
                || crouching != climbInputCrouching;
        if (changed || active && climbInputRefreshTicks-- <= 0) {
            PacketDistributor.sendToServer(new RopeClimbInputPayload(
                    active, jumping, crouching));
            climbInputRefreshTicks = 3;
        }
        climbInputActive = active;
        climbInputJumping = jumping;
        climbInputCrouching = crouching;
    }

    private static Vec3 findClimbContact(Minecraft minecraft) {
        AABB body = minecraft.player.getBoundingBox();
        Vec3 origin = body.getCenter();
        for (View view : views(currentPartialTick(minecraft))) {
            if (!view.bounds().intersects(body)) {
                continue;
            }
            for (int segment = 0; segment + 1 < view.nodes().size(); segment++) {
                Vec3 contact = RopeClimbing.contactPoint(
                        body, view.nodes().get(segment), view.nodes().get(segment + 1),
                        2.25D / 16.0D);
                if (contact == null) {
                    continue;
                }
                HitResult obstruction = minecraft.level.clip(new ClipContext(
                        origin, contact, ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, minecraft.player));
                if (obstruction.getType() == HitResult.Type.MISS
                        || obstruction.getLocation().distanceToSqr(contact) <= 0.01D) {
                    return contact;
                }
            }
        }
        return null;
    }

    private static void clearClimbInputState() {
        climbInputActive = false;
        climbInputJumping = false;
        climbInputCrouching = false;
        climbInputRefreshTicks = 0;
    }

    private static boolean leftButtonDown(Minecraft minecraft) {
        return GLFW.glfwGetMouseButton(
                minecraft.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT)
                == GLFW.GLFW_PRESS;
    }

    private static boolean rightButtonDown(Minecraft minecraft) {
        return GLFW.glfwGetMouseButton(
                minecraft.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_RIGHT)
                == GLFW.GLFW_PRESS;
    }

    private static boolean canInteract(Minecraft minecraft) {
        return minecraft.player != null
                && !minecraft.player.isSpectator()
                && !FreecamCompat.isExternalCameraActive(minecraft)
                && !isHoldingTuningWand(minecraft.player);
    }

    private static void clearDragVisualState() {
        dragging = false;
        dragFrame = null;
        lastDragSendTick = Long.MIN_VALUE;
        dragRopeId = -1;
        dragSegmentIndex = -1;
        dragInputSession = 0L;
        dragInputSequence = 0L;
        clearPendingAnchor();
    }

    private static void clearPendingAnchor() {
        pendingAnchorRopeId = -1;
        pendingAnchorSegment = -1;
        pendingAnchorStartTick = Long.MIN_VALUE;
        lastAnchorSendTick = Long.MIN_VALUE;
    }

    private static void cancelInteraction(Minecraft minecraft) {
        if (rescueCastArmed) {
            PacketDistributor.sendToServer(new RopeRescueCastPayload(false));
            rescueCastArmed = false;
        }
        clearPendingRescueHaul();
        if (rescueHauling) {
            stopRescueHaul(minecraft);
        }
        if (dragging) {
            releaseDrag(minecraft);
        }
        if (breaking) {
            sendBreak(minecraft, false, true);
        }
        selected = null;
        clearDragVisualState();
        clearBreaking();
    }

    private static void beginDrag(Minecraft minecraft, Selection selection) {
        clearPendingAnchor();
        dragging = true;
        dragRopeId = selection.ropeId();
        dragSegmentIndex = selection.segmentIndex();
        dragInputSession = nextDragSession++;
        if (nextDragSession <= 0L) {
            nextDragSession = 1L;
        }
        dragInputSequence = 0L;
        dragFrame = RopeSegmentPose.toRopeFrame(selection.frame());
        lastDragSendTick = Long.MIN_VALUE;
        sendDrag(minecraft, true, true);
    }

    private static void requestAnchor(Minecraft minecraft) {
        pendingAnchorRopeId = dragRopeId;
        pendingAnchorSegment = dragSegmentIndex;
        pendingAnchorStartTick = minecraft.level.getGameTime();
        lastAnchorSendTick = Long.MIN_VALUE;
        // Keep the latest drag intent ahead of the anchor request on the same connection.
        sendDrag(minecraft, true, true);
        sendAnchor(minecraft, true);
    }

    private static void sendAnchor(Minecraft minecraft, boolean force) {
        if (pendingAnchorRopeId < 0 || pendingAnchorSegment < 0
                || minecraft.level == null) {
            return;
        }
        long tick = minecraft.level.getGameTime();
        if (!force && tick - lastAnchorSendTick < ANCHOR_RETRY_TICKS) {
            return;
        }
        PacketDistributor.sendToServer(new RopeAnchorPayload(
                pendingAnchorRopeId, pendingAnchorSegment));
        lastAnchorSendTick = tick;
    }

    private static void clearPendingRescueHaul() {
        pendingRescueHaul = null;
        pendingRescueHaulStartTick = Long.MIN_VALUE;
    }

    private static void stopRescueHaul(Minecraft minecraft) {
        if (minecraft.level != null && rescueHaulRopeId >= 0
                && rescueHaulInputSegment >= 0) {
            PacketDistributor.sendToServer(new RopeRescueHaulPayload(
                    false, rescueHaulRopeId, rescueHaulInputSegment));
        }
        clearRescueHaulVisualState();
    }

    private static void clearRescueHaulVisualState() {
        rescueHauling = false;
        rescueHaulRopeId = -1;
        rescueHaulSegment = -1;
        rescueHaulInputSegment = -1;
        lastRescueHaulSendTick = Long.MIN_VALUE;
    }

    private static void sendRescueHaul(
            Minecraft minecraft, boolean active, boolean force) {
        if (!rescueHauling || rescueHaulRopeId < 0 || rescueHaulSegment < 0
                || minecraft.level == null) {
            return;
        }
        long tick = minecraft.level.getGameTime();
        if (!force && tick - lastRescueHaulSendTick < DRAG_HEARTBEAT_TICKS) {
            return;
        }
        PacketDistributor.sendToServer(new RopeRescueHaulPayload(
                active, rescueHaulRopeId, rescueHaulInputSegment));
        lastRescueHaulSendTick = tick;
    }

    private static float currentPartialTick(Minecraft minecraft) {
        return minecraft.getTimer().getGameTimeDeltaPartialTick(false);
    }

    private static void releaseDrag(Minecraft minecraft) {
        if (minecraft.level != null && dragRopeId >= 0 && dragSegmentIndex >= 0
                && dragInputSession > 0L) {
            PacketDistributor.sendToServer(RopeDragPayload.release(
                    dragRopeId, dragSegmentIndex, dragInputSession,
                    ++dragInputSequence));
        }
        clearDragVisualState();
    }

    private static void sendDrag(Minecraft minecraft, boolean active, boolean force) {
        if (!dragging || dragRopeId < 0 || dragSegmentIndex < 0
                || minecraft.player == null || dragFrame == null
                || dragInputSession <= 0L) {
            return;
        }
        long tick = minecraft.level.getGameTime();
        if (!force && tick - lastDragSendTick < DRAG_HEARTBEAT_TICKS) {
            return;
        }
        PacketDistributor.sendToServer(new RopeDragPayload(
                active, dragRopeId, dragSegmentIndex, dragFrame,
                minecraft.player.getEyePosition(1.0F),
                minecraft.player.getViewVector(1.0F).normalize(),
                dragInputSession, ++dragInputSequence));
        lastDragSendTick = tick;
    }

    private static void sendBreak(Minecraft minecraft, boolean active, boolean force) {
        if (breakRopeId < 0 || breakSegment < 0 || minecraft.level == null) {
            return;
        }
        long tick = minecraft.level.getGameTime();
        if (!force && tick - lastBreakSendTick < DRAG_HEARTBEAT_TICKS) {
            return;
        }
        PacketDistributor.sendToServer(new RopeBreakPayload(
                active, breakRopeId, breakSegment, breakAllConnected));
        lastBreakSendTick = tick;
    }

    public static BreakSelection breakSelection(float partialTick) {
        if (!breaking) {
            return null;
        }
        Selection selection = selection(partialTick);
        if (selection == null || selection.ropeId() != breakRopeId
                || selection.segmentIndex() != breakSegment) {
            for (View view : views(partialTick)) {
                if (view.id() == breakRopeId && breakSegment >= 0
                        && breakSegment + 1 < view.nodes().size()) {
                    selection = new Selection(breakRopeId, breakSegment,
                            view.nodes().get(breakSegment), view.nodes().get(breakSegment + 1),
                            0.0D, view.nodes().get(breakSegment).lerp(
                                    view.nodes().get(breakSegment + 1), 0.5D),
                            view.frames()[breakSegment], false,
                            view.anchoredSegments().contains(breakSegment));
                }
            }
        }
        if (selection == null) {
            return null;
        }
        long now = Minecraft.getInstance().level.getGameTime();
        int duration = breakAllConnected ? BREAK_DURATION_TICKS * 2 : BREAK_DURATION_TICKS;
        int stage = Mth.clamp((int) ((now + partialTick - breakStartTick)
                * 10L / duration), 0, 9);
        return new BreakSelection(selection, stage);
    }

    public record BreakSelection(Selection selection, int stage) {
    }

    record ScreenPlaneAxes(Vec3 up, Vec3 right) {
    }

    private static boolean isExtendableSelection(Selection selection) {
        if (selection == null) {
            return false;
        }
        for (Selection endpoint : endpointSelections(
                currentPartialTick(Minecraft.getInstance()))) {
            if (endpoint.ropeId() == selection.ropeId()
                    && endpoint.segmentIndex() == selection.segmentIndex()) {
                return true;
            }
        }
        return false;
    }

    private static Selection findConnectHit(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) {
            return null;
        }
        float partialTick = currentPartialTick(minecraft);
        Selection closest = null;
        for (Selection candidate : connectSelections(partialTick)) {
            if (closest == null || candidate.distance() < closest.distance()) {
                closest = candidate;
            }
        }
        return closest;
    }

    private static double connectionPickRange(
            Minecraft minecraft, Vec3 origin, Vec3 direction) {
        double range = Math.max(0.0D, minecraft.player.blockInteractionRange());
        HitResult blockHit = minecraft.level.clip(new ClipContext(
                origin, origin.add(direction.scale(range)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                minecraft.player));
        return blockHit.getType() == HitResult.Type.BLOCK
                ? Math.min(range, origin.distanceTo(blockHit.getLocation())) : range;
    }

    private static Selection findHit(Minecraft minecraft) {
        return findHit(minecraft, -1, Integer.MIN_VALUE);
    }

    private static Selection findHit(Minecraft minecraft, int minimumSegment) {
        return findHit(minecraft, -1, minimumSegment);
    }

    private static Selection findHit(
            Minecraft minecraft, int ropeId, int minimumSegment) {
        float partialTick = currentPartialTick(minecraft);
        Vec3 origin = minecraft.player.getEyePosition(partialTick);
        Vec3 direction = minecraft.player.getViewVector(partialTick).normalize();
        double pickRange = Math.max(0.0D, minecraft.player.blockInteractionRange());
        HitResult blockHit = minecraft.level.clip(new ClipContext(
                origin, origin.add(direction.scale(pickRange)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                minecraft.player));
        if (blockHit.getType() == HitResult.Type.BLOCK) {
            pickRange = Math.min(pickRange,
                    origin.distanceTo(blockHit.getLocation()));
        }
        Selection closest = null;
        for (View view : views(partialTick)) {
            if (ropeId >= 0 && view.id() != ropeId) {
                continue;
            }
            List<Vec3> nodes = view.nodes();
            RopeSegmentPose.Frame[] frames = view.frames();
            for (int segment = 0; segment < nodes.size() - 1; segment++) {
                if (segment < minimumSegment) {
                    continue;
                }
                double hitDistance = RopeHitGeometry.rayCapsuleHitDistance(
                        origin, direction, nodes.get(segment), nodes.get(segment + 1),
                        RopeHitGeometry.SELECTION_RADIUS, pickRange);
                if (!Double.isFinite(hitDistance)) {
                    continue;
                }
                if (closest == null || hitDistance < closest.distance()) {
                    Vec3 hitPoint = origin.add(direction.scale(hitDistance));
                    closest = new Selection(view.id(), segment,
                            nodes.get(segment), nodes.get(segment + 1), hitDistance,
                            hitPoint, frames[segment], false,
                            view.anchoredSegments().contains(segment)
                                    || view.rescueAnchoredSegments().contains(segment));
                }
            }
        }
        return closest;
    }

    private static boolean isRescueHaulSelection(Selection selection) {
        if (selection == null || selection.anchored()) {
            return false;
        }
        for (View view : views(currentPartialTick(Minecraft.getInstance()))) {
            int first = rescueLassoFirstSegment(view);
            if (view.id() == selection.ropeId() && first > 0
                    && selection.segmentIndex() < first) {
                return true;
            }
        }
        return false;
    }

    private static int rescueLassoFirstSegment(View view) {
        if (view.rescueAnchoredSegments().isEmpty()) {
            return -1;
        }
        return view.rescueAnchoredSegments().stream().min(Integer::compareTo).orElse(-1);
    }

    private static boolean ensureLevel(Minecraft minecraft) {
        if (minecraft.level != level) {
            ROPES.clear();
            level = minecraft.level;
            selected = null;
            dragging = false;
            suppressRightRelease = false;
            dragFrame = null;
            clearPendingAnchor();
            clearPendingRescueHaul();
            clearRescueHaulVisualState();
            clearClimbInputState();
            clearBreaking();
            viewRevision++;
            cachedViewRevision = Long.MIN_VALUE;
            cachedViews = List.of();
        }
        return minecraft.level != null;
    }

    private static AABB bounds(List<Vec3> nodes) {
        Vec3 first = nodes.getFirst();
        double minX = first.x;
        double minY = first.y;
        double minZ = first.z;
        double maxX = first.x;
        double maxY = first.y;
        double maxZ = first.z;
        for (int index = 1; index < nodes.size(); index++) {
            Vec3 node = nodes.get(index);
            minX = Math.min(minX, node.x);
            minY = Math.min(minY, node.y);
            minZ = Math.min(minZ, node.z);
            maxX = Math.max(maxX, node.x);
            maxY = Math.max(maxY, node.y);
            maxZ = Math.max(maxZ, node.z);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(0.25D);
    }

    public record View(int id, List<Vec3> nodes, AABB bounds,
            RopeSegmentPose.Frame[] frames, List<Integer> anchoredSegments,
            List<Integer> rescueAnchoredSegments,
            int draggedSegment) {
    }

    public record Selection(int ropeId, int segmentIndex, Vec3 start, Vec3 end,
            double distance, Vec3 hitPoint, RopeSegmentPose.Frame frame,
            boolean dragging, boolean anchored) {
    }

    private static final class InterpolatedRope {
        private final int id;
        private final ArrayList<Snapshot> snapshots = new ArrayList<>(8);
        private long receivedTick;
        private int lastSnapshotSequence;
        private List<RopeSegmentOrientation> anchoredOrientations;
        private List<RopeSegmentOrientation> rescueAnchoredOrientations;
        private RopeSegmentOrientation draggedOrientation;
        private long clientToServerAgeOffset;
        private final ArrayList<Vec3> poseBuffer = new ArrayList<>();

        private InterpolatedRope(RopeSnapshotPayload payload, long tick) {
            id = payload.ropeId();
            receivedTick = tick;
            lastSnapshotSequence = payload.snapshotSequence();
            clientToServerAgeOffset = tick - payload.age();
            snapshots.add(Snapshot.of(payload));
            anchoredOrientations = payload.anchoredOrientations();
            rescueAnchoredOrientations = payload.rescueAnchoredOrientations();
            draggedOrientation = payload.draggedOrientation();
        }

        private void accept(RopeSnapshotPayload payload, long tick) {
            if (payload.snapshotSequence() <= lastSnapshotSequence) {
                return;
            }
            Snapshot incoming = Snapshot.of(payload);
            boolean replaced = false;
            for (int index = 0; index < snapshots.size(); index++) {
                Snapshot existing = snapshots.get(index);
                if (existing.age() == incoming.age()) {
                    snapshots.set(index, incoming);
                    replaced = true;
                    break;
                }
                if (existing.age() > incoming.age()) {
                    snapshots.add(index, incoming);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                snapshots.add(incoming);
            }
            while (snapshots.size() > 8) {
                snapshots.removeFirst();
            }
            receivedTick = tick;
            lastSnapshotSequence = payload.snapshotSequence();
            anchoredOrientations = payload.anchoredOrientations();
            rescueAnchoredOrientations = payload.rescueAnchoredOrientations();
            draggedOrientation = payload.draggedOrientation();
        }

        private List<Vec3> pose(double partialTick) {
            long clientTick = Minecraft.getInstance().level.getGameTime();
            double serverAge = clientTick + partialTick
                    - clientToServerAgeOffset - 1.0D;
            Snapshot first = snapshots.getFirst();
            Snapshot last = snapshots.getLast();
            if (serverAge <= first.age()) {
                return updatePose(first.nodes(), first.nodes(), 0.0D);
            }
            for (int index = 1; index < snapshots.size(); index++) {
                Snapshot next = snapshots.get(index);
                if (serverAge <= next.age()) {
                    Snapshot previous = snapshots.get(index - 1);
                    double span = Math.max(1.0D, next.age() - previous.age());
                    double amount = Mth.clamp(
                            (serverAge - previous.age()) / span, 0.0D, 1.0D);
                    return updatePose(previous.nodes(), next.nodes(), amount);
                }
            }
            if (snapshots.size() < 2) {
                return updatePose(last.nodes(), last.nodes(), 0.0D);
            }
            Snapshot previous = snapshots.get(snapshots.size() - 2);
            double amount = Mth.clamp(
                    serverAge - last.age(), 0.0D, 1.0D);
            if (previous.nodes().size() != last.nodes().size()) {
                return updatePose(last.nodes(), last.nodes(), 0.0D);
            }
            ensurePoseBuffer(last.nodes().size());
            for (int point = 0; point < last.nodes().size(); point++) {
                Vec3 latest = last.nodes().get(point);
                Vec3 velocity = latest.subtract(previous.nodes().get(point));
                poseBuffer.set(point, latest.add(velocity.scale(amount)));
            }
            return poseBuffer;
        }

        private List<Vec3> updatePose(List<Vec3> from, List<Vec3> to,
                double amount) {
            if (from.size() != to.size()) {
                ensurePoseBuffer(to.size());
                for (int point = 0; point < to.size(); point++) {
                    poseBuffer.set(point, to.get(point));
                }
                return poseBuffer;
            }
            ensurePoseBuffer(from.size());
            for (int point = 0; point < from.size(); point++) {
                poseBuffer.set(point, amount <= 0.0D
                        ? from.get(point) : from.get(point).lerp(to.get(point), amount));
            }
            return poseBuffer;
        }

        private void ensurePoseBuffer(int size) {
            while (poseBuffer.size() < size) {
                poseBuffer.add(Vec3.ZERO);
            }
            while (poseBuffer.size() > size) {
                poseBuffer.removeLast();
            }
        }

        private record Snapshot(int age, List<Vec3> nodes) {
            private static Snapshot of(RopeSnapshotPayload payload) {
                return new Snapshot(payload.age(), payload.nodes());
            }
        }
    }
}

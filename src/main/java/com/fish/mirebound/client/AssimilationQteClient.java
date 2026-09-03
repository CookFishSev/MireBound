package com.fish.mirebound.client;

import com.fish.mirebound.assimilation.AssimilationQteAction;
import com.fish.mirebound.assimilation.AssimilationTracePattern;
import com.fish.mirebound.network.payload.AssimilationQteInputPayload;
import com.fish.mirebound.network.payload.AssimilationQteTracePayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Owner-side targeting and responsive hold feedback; timing stays server-authoritative. */
public final class AssimilationQteClient {
    private static int heldSequence = -1;
    private static int heldCell = -1;
    private static int heldButton;
    private static int localHoldTicks;
    private static boolean rapidPressed;
    private static int traceSequence = -1;
    private static int traceCell = -1;
    private static int traceButton;
    private static int traceProgress;
    private static int traceLastNode = -1;
    private static int traceNodeTick = Integer.MIN_VALUE;
    private static final boolean[] RAW_MOUSE_CAPTURED = new boolean[3];
    private static final boolean[] RAW_MOUSE_DOWN = new boolean[3];

    private AssimilationQteClient() {
    }

    /**
     * Handles attack/use before item-specific mouse mixins can consume the raw event.
     * This is active only while the local player is frozen for an assimilation QTE.
     */
    public static boolean handleRawMouseButton(Minecraft minecraft, int mouseButton, int action) {
        if (minecraft.screen != null || minecraft.getOverlay() != null
                || !ClientAssimilationState.localStasisActive(minecraft)) {
            return false;
        }
        InputConstants.Key mouseKey = InputConstants.Type.MOUSE.getOrCreate(mouseButton);
        int qteButton = minecraft.options.keyAttack.isActiveAndMatches(mouseKey) ? 1
                : minecraft.options.keyUse.isActiveAndMatches(mouseKey) ? 2 : 0;
        if (qteButton == 0) {
            return false;
        }
        RAW_MOUSE_CAPTURED[qteButton] = true;
        if (action == GLFW.GLFW_PRESS) {
            RAW_MOUSE_DOWN[qteButton] = true;
            handlePress(minecraft, qteButton);
        } else if (action == GLFW.GLFW_RELEASE) {
            RAW_MOUSE_DOWN[qteButton] = false;
            handleRelease(minecraft, qteButton);
        }
        return true;
    }

    static boolean handlePress(Minecraft minecraft, int button) {
        ClientAssimilationState.View view = validView(minecraft);
        if (view == null || button < 1 || button > 2) {
            return false;
        }
        if (!inRange(view)) {
            return false;
        }
        if (view.qteAction() == AssimilationQteAction.TRACE) {
            if (traceSequence == view.qteSequence() && traceCell == view.qteCell()) {
                return true;
            }
            int[] path = tracePath(view);
            float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
            int node = AssimilationTraceHudLayout.nodeAtCrosshair(minecraft, view);
            if (node < 0) {
                return true;
            }
            PacketDistributor.sendToServer(new AssimilationQteTracePayload(
                    view.qteSequence(), view.qteCell(), button,
                    AssimilationQteTracePayload.START, node));
            if (button == view.qteButton() && node == path[0]) {
                AssimilationTraceHudLayout.Layout lockedLayout = AssimilationTraceHudLayout.lock(
                        minecraft, view, partialTick);
                if (lockedLayout == null) {
                    return true;
                }
                traceSequence = view.qteSequence();
                traceCell = view.qteCell();
                traceButton = button;
                traceProgress = 1;
                traceLastNode = node;
                traceNodeTick = minecraft.player == null
                        ? Integer.MIN_VALUE : minecraft.player.tickCount;
            }
            return true;
        }
        if (!aimedAt(view)) {
            return false;
        }
        if (view.qteAction() == AssimilationQteAction.RAPID && rapidPressed) {
            return true;
        }
        if (view.qteAction() == AssimilationQteAction.HOLD
                && heldSequence == view.qteSequence()
                && heldCell == view.qteCell()) {
            return true;
        }
        PacketDistributor.sendToServer(new AssimilationQteInputPayload(
                view.qteSequence(), view.qteCell(), button,
                AssimilationQteInputPayload.PRESS));
        if (view.qteAction() == AssimilationQteAction.RAPID
                && button == view.qteButton()) {
            rapidPressed = true;
        }
        if (view.qteAction() == AssimilationQteAction.HOLD && button == view.qteButton()) {
            heldSequence = view.qteSequence();
            heldCell = view.qteCell();
            heldButton = button;
            localHoldTicks = 0;
        }
        return true;
    }

    static void tick(Minecraft minecraft) {
        ClientAssimilationState.View current = validView(minecraft);
        tickTrace(minecraft, current);
        if (rapidPressed) {
            if (current == null || current.qteAction() != AssimilationQteAction.RAPID
                    || !inRange(current)) {
                rapidPressed = false;
            } else {
                boolean down = buttonDown(minecraft, current.qteButton());
                if (!down) {
                    PacketDistributor.sendToServer(new AssimilationQteInputPayload(
                            current.qteSequence(), current.qteCell(), current.qteButton(),
                            AssimilationQteInputPayload.RELEASE));
                    rapidPressed = false;
                }
            }
        }
        if (heldSequence < 0) {
            return;
        }
        ClientAssimilationState.View view = validView(minecraft);
        if (view == null || view.qteSequence() != heldSequence || view.qteCell() != heldCell
                || view.qteAction() != AssimilationQteAction.HOLD || !inRange(view)) {
            reset();
            return;
        }
        boolean down = buttonDown(minecraft, heldButton);
        if (down && aimedAt(view)) {
            localHoldTicks++;
            return;
        }
        PacketDistributor.sendToServer(new AssimilationQteInputPayload(
                heldSequence, heldCell, heldButton, AssimilationQteInputPayload.RELEASE));
        reset();
    }

    static float holdProgress(ClientAssimilationState.View view) {
        if (!holding(view)) {
            return 0.0F;
        }
        return Mth.clamp(localHoldTicks
                / (float) Math.max(1, view.profile().selfRescueQteHoldTicks()), 0.0F, 1.0F);
    }

    static int traceDisplayProgress(ClientAssimilationState.View view) {
        if (view != null && traceSequence == view.qteSequence()
                && traceCell == view.qteCell()) {
            return Math.max(view.qteTraceProgress(), traceProgress);
        }
        return view == null ? 0 : view.qteTraceProgress();
    }

    static boolean tracing(ClientAssimilationState.View view) {
        return view != null && traceSequence == view.qteSequence()
                && traceCell == view.qteCell() && traceProgress > 0;
    }

    static boolean stabilizeCamera(ClientAssimilationState.View view) {
        return view != null && view.qteAction() == AssimilationQteAction.TRACE
                && view.qteCell() >= 0 && view.qteTicksRemaining() > 0 && inRange(view);
    }

    static int[] tracePath(ClientAssimilationState.View view) {
        return AssimilationTracePattern.build(view.patternSeed(), view.qteSequence(),
                view.profile().selfRescueQteTraceNodes());
    }

    static boolean holding(ClientAssimilationState.View view) {
        return view != null && heldSequence == view.qteSequence()
                && heldCell == view.qteCell() && heldButton == view.qteButton();
    }

    static boolean aimedAt(ClientAssimilationState.View view) {
        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = minecraft.gameRenderer.getMainCamera();
        if (camera == null || view == null || view.qteCell() < 0) {
            return false;
        }
        Vec3 toTarget = AssimilationBodyCellGeometry.worldPoint(view, view.qteCell())
                .subtract(camera.getPosition());
        if (toTarget.lengthSqr() < 1.0E-8D) {
            return true;
        }
        var lookVector = camera.getLookVector();
        Vec3 look = new Vec3(lookVector.x(), lookVector.y(), lookVector.z());
        double minimumDot = Math.cos(Math.toRadians(view.profile().selfRescueQteAimDegrees()));
        return look.dot(toTarget.normalize()) >= minimumDot;
    }

    static boolean inRange(ClientAssimilationState.View view) {
        Vec3 soul = AssimilationSoulCamera.position();
        if (view == null || soul == null) {
            return false;
        }
        double range = view.profile().selfRescueQteRange();
        Vec3 bodyCenter = bodyCenter(view);
        return soul.distanceToSqr(bodyCenter) <= range * range;
    }

    static double distanceToBody(ClientAssimilationState.View view) {
        Vec3 soul = AssimilationSoulCamera.position();
        if (view == null || soul == null) {
            return Double.POSITIVE_INFINITY;
        }
        return soul.distanceTo(bodyCenter(view));
    }

    static Vec3 bodyCenter(ClientAssimilationState.View view) {
        Minecraft minecraft = Minecraft.getInstance();
        double centerHeight = minecraft.player == null
                ? 0.9D : minecraft.player.getEyeHeight() * 0.5D;
        return view.anchor().add(0.0D, centerHeight, 0.0D);
    }

    static void reset() {
        heldSequence = -1;
        heldCell = -1;
        heldButton = 0;
        localHoldTicks = 0;
        rapidPressed = false;
        clearRawMouseState();
        resetTrace();
    }

    private static void tickTrace(Minecraft minecraft, ClientAssimilationState.View view) {
        if (traceSequence < 0) {
            return;
        }
        if (view == null || view.qteAction() != AssimilationQteAction.TRACE
                || view.qteSequence() != traceSequence || view.qteCell() != traceCell
                || !inRange(view)) {
            resetTrace();
            return;
        }
        boolean down = buttonDown(minecraft, traceButton);
        if (!down) {
            PacketDistributor.sendToServer(new AssimilationQteTracePayload(
                    traceSequence, traceCell, traceButton,
                    AssimilationQteTracePayload.RELEASE, AssimilationTracePattern.NODE_COUNT));
            resetTrace();
            return;
        }
        int[] path = tracePath(view);
        if (traceProgress >= path.length || minecraft.player == null
                || minecraft.player.tickCount <= traceNodeTick) {
            return;
        }
        int node = traceNode(minecraft, view);
        if (node < 0 || node == traceLastNode) {
            return;
        }
        PacketDistributor.sendToServer(new AssimilationQteTracePayload(
                traceSequence, traceCell, traceButton,
                AssimilationQteTracePayload.NODE, node));
        traceNodeTick = minecraft.player.tickCount;
        if (node != path[traceProgress]) {
            resetTrace();
            return;
        }
        traceLastNode = node;
        traceProgress++;
    }

    private static void resetTrace() {
        traceSequence = -1;
        traceCell = -1;
        traceButton = 0;
        traceProgress = 0;
        traceLastNode = -1;
        traceNodeTick = Integer.MIN_VALUE;
        AssimilationTraceHudLayout.reset();
    }

    private static int traceNode(Minecraft minecraft, ClientAssimilationState.View view) {
        return AssimilationTraceHudLayout.nodeAtCrosshair(minecraft, view);
    }

    private static boolean buttonDown(Minecraft minecraft, int button) {
        if (button >= 1 && button <= 2 && RAW_MOUSE_CAPTURED[button]) {
            return RAW_MOUSE_DOWN[button];
        }
        return button == 1 ? minecraft.options.keyAttack.isDown()
                : button == 2 && minecraft.options.keyUse.isDown();
    }

    private static void handleRelease(Minecraft minecraft, int button) {
        ClientAssimilationState.View current = validView(minecraft);
        if (rapidPressed && current != null && current.qteAction() == AssimilationQteAction.RAPID
                && current.qteButton() == button) {
            PacketDistributor.sendToServer(new AssimilationQteInputPayload(
                    current.qteSequence(), current.qteCell(), button,
                    AssimilationQteInputPayload.RELEASE));
            rapidPressed = false;
        }
        if (heldSequence >= 0 && heldButton == button) {
            PacketDistributor.sendToServer(new AssimilationQteInputPayload(
                    heldSequence, heldCell, heldButton, AssimilationQteInputPayload.RELEASE));
            heldSequence = -1;
            heldCell = -1;
            heldButton = 0;
            localHoldTicks = 0;
        }
        if (traceSequence >= 0 && traceButton == button) {
            PacketDistributor.sendToServer(new AssimilationQteTracePayload(
                    traceSequence, traceCell, traceButton,
                    AssimilationQteTracePayload.RELEASE, AssimilationTracePattern.NODE_COUNT));
            resetTrace();
        }
    }

    private static void clearRawMouseState() {
        for (int button = 1; button <= 2; button++) {
            RAW_MOUSE_CAPTURED[button] = false;
            RAW_MOUSE_DOWN[button] = false;
        }
    }

    private static ClientAssimilationState.View validView(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.getConnection() == null) {
            return null;
        }
        ClientAssimilationState.View view = ClientAssimilationState.view(
                minecraft.player.getId());
        return view == null || view.qteCell() < 0 || view.qteButton() == 0
                || view.qteTicksRemaining() <= 0 ? null : view;
    }
}

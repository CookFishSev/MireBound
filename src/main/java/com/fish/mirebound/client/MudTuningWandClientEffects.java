package com.fish.mirebound.client;

import com.fish.mirebound.compat.sable.SableCompat;
import com.fish.mirebound.client.tuning.MudTuningInputController;
import com.fish.mirebound.client.generation.MudTerrainGenerationController;
import com.fish.mirebound.client.tuning.MudTuningSpatialPlacement;
import com.fish.mirebound.client.tuning.MudTuningTentacleTargeting;
import com.fish.mirebound.mud.MudTuningAnchor;
import com.fish.mirebound.network.payload.MudTuningWandBeamPayload;
import com.fish.mirebound.network.payload.MudTuningWandPulsePayload;
import com.fish.mirebound.registry.ModBlocks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/** Owns bounded client state for synchronized wand cage and beam pulses. */
public final class MudTuningWandClientEffects {
    private static final Map<Integer, Activation> ACTIVATIONS = new HashMap<>();
    private static int renderedPlayerId = Integer.MIN_VALUE;
    private static final PreviewTransition MAIN_HAND_PREVIEW = new PreviewTransition();
    private static final PreviewTransition OFF_HAND_PREVIEW = new PreviewTransition();

    private MudTuningWandClientEffects() {
    }

    static void accept(MudTuningWandBeamPayload payload) {
        acceptActivation(payload.playerEntityId(), payload.target(),
                payload.mainHand(), payload.coreGameTime());
    }

    static void accept(MudTuningWandPulsePayload payload) {
        acceptActivation(payload.playerEntityId(), null,
                payload.mainHand(), payload.coreGameTime());
    }

    public static void triggerLocalPulse(int playerEntityId, boolean mainHand,
            long coreGameTime) {
        acceptActivation(playerEntityId, null, mainHand, coreGameTime);
    }

    private static void acceptActivation(int playerEntityId,
            MudTuningAnchor target, boolean mainHand, long coreGameTime) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Activation previous = ACTIVATIONS.get(playerEntityId);
        float startingOpening = previous == null
                ? 0.0F : previous.currentOpening();
        int color = activeColor(coreGameTime);
        ACTIVATIONS.put(playerEntityId, new Activation(
                playerEntityId, target, mainHand,
                minecraft.level.getGameTime(), startingOpening, color));
        if (target != null) {
            MudTuningWandSelectionParticles.spawn(minecraft, target, color);
        }
    }

    static void tick(Minecraft minecraft) {
        if (minecraft.level == null) {
            return;
        }
        tickInterruptedPreview(minecraft);
        if (ACTIVATIONS.isEmpty()) {
            return;
        }
        long gameTime = minecraft.level.getGameTime();
        ACTIVATIONS.values().removeIf(activation -> {
            activation.tick(gameTime);
            return activation.finished(gameTime);
        });
    }

    static float openingAmount(ItemStack stack, ItemDisplayContext context,
            float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || stack.getItem() != ModBlocks.MUD_TUNING_WAND.get()) {
            return 0.0F;
        }
        HeldContext held = heldContext(stack, context);
        if (held == null) {
            return 0.0F;
        }
        if (localPlayer(held, minecraft)) {
            PreviewTransition preview = preview(held.mainHand());
            if (livePreviewTarget(minecraft, held) != null || preview.visible()) {
                return preview.amount(partialTick);
            }
        }
        Activation activation = ACTIVATIONS.get(held.playerId());
        if (activation == null || activation.mainHand != held.mainHand()) {
            return 0.0F;
        }
        return activation.opening(partialTick);
    }

    static AimView activeAim(ItemStack stack, ItemDisplayContext context,
            float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }
        HeldContext held = heldContext(stack, context);
        if (held == null) {
            return null;
        }
        if (localPlayer(held, minecraft)) {
            PreviewTransition preview = preview(held.mainHand());
            Vec3 liveTarget = livePreviewTarget(minecraft, held);
            Vec3 target = liveTarget == null ? preview.target() : liveTarget;
            float amount = preview.amount(partialTick);
            if (target != null && amount > 0.0F) {
                return new AimView(target, amount);
            }
        }
        Activation activation = ACTIVATIONS.get(held.playerId());
        if (activation == null || activation.mainHand != held.mainHand()) {
            return null;
        }
        float amount = activation.opening(partialTick);
        if (activation.target == null) {
            return null;
        }
        Vec3 target = targetPosition(minecraft, activation.target);
        return target == null ? null : new AimView(target, amount);
    }

    static HeldContext heldContext(ItemStack stack, ItemDisplayContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || stack.getItem() != ModBlocks.MUD_TUNING_WAND.get()) {
            return null;
        }
        boolean rightSide;
        int playerId;
        switch (context) {
            case FIRST_PERSON_RIGHT_HAND -> {
                if (minecraft.player == null) {
                    return null;
                }
                playerId = minecraft.player.getId();
                rightSide = true;
            }
            case FIRST_PERSON_LEFT_HAND -> {
                if (minecraft.player == null) {
                    return null;
                }
                playerId = minecraft.player.getId();
                rightSide = false;
            }
            case THIRD_PERSON_RIGHT_HAND -> {
                playerId = renderedPlayerId;
                rightSide = true;
            }
            case THIRD_PERSON_LEFT_HAND -> {
                playerId = renderedPlayerId;
                rightSide = false;
            }
            default -> {
                return null;
            }
        }
        if (!(minecraft.level.getEntity(playerId) instanceof Player player)) {
            return null;
        }
        HumanoidArm renderedArm = rightSide ? HumanoidArm.RIGHT : HumanoidArm.LEFT;
        boolean mainHand = renderedArm == player.getMainArm();
        ItemStack heldStack = mainHand ? player.getMainHandItem() : player.getOffhandItem();
        return heldStack.getItem() == ModBlocks.MUD_TUNING_WAND.get()
                ? new HeldContext(playerId, mainHand) : null;
    }

    static List<BeamView> beams(double time) {
        List<BeamView> beams = new ArrayList<>(ACTIVATIONS.size());
        for (Activation activation : ACTIVATIONS.values()) {
            if (activation.target == null) {
                continue;
            }
            double age = time - activation.startTime;
            float alpha = MudTuningWandAnimation.beamAlpha(age);
            if (alpha > 0.001F) {
                beams.add(new BeamView(
                        activation.playerEntityId, activation.target,
                        activation.mainHand, activation.color,
                        MudTuningWandAnimation.beamExtension(age), alpha,
                        age, activation.startTime));
            }
        }
        return List.copyOf(beams);
    }

    static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        renderedPlayerId = event.getEntity().getId();
    }

    static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (renderedPlayerId == event.getEntity().getId()) {
            renderedPlayerId = Integer.MIN_VALUE;
        }
    }

    static void reset() {
        ACTIVATIONS.clear();
        renderedPlayerId = Integer.MIN_VALUE;
        MAIN_HAND_PREVIEW.reset();
        OFF_HAND_PREVIEW.reset();
        MudTuningWandCoreFocus.reset();
        MudTuningWandCoreMotion.reset();
    }

    static Vec3 targetPosition(Minecraft minecraft, MudTuningAnchor anchor) {
        Vec3 localCenter = Vec3.atCenterOf(anchor.pos());
        if (!anchor.isSable()) {
            return localCenter;
        }
        Object subLevel = SableCompat.subLevelById(minecraft.level, anchor.subLevelId());
        return subLevel == null ? null : SableCompat.toRenderWorld(subLevel, localCenter);
    }

    private static void tickInterruptedPreview(Minecraft minecraft) {
        InteractionHand hand = minecraft.player == null
                ? null : MudTuningInputController.heldWandHand(minecraft.player);
        Vec3 target = activeLocalPreviewTarget(minecraft);
        boolean mainHand = hand == InteractionHand.MAIN_HAND && target != null;
        boolean offHand = hand == InteractionHand.OFF_HAND && target != null;
        MAIN_HAND_PREVIEW.tick(target, mainHand);
        OFF_HAND_PREVIEW.tick(target, offHand);
    }

    private static Vec3 activeLocalPreviewTarget(Minecraft minecraft) {
        Vec3 generation = MudTerrainGenerationController.coreTarget(minecraft);
        if (generation != null) {
            return generation;
        }
        var tentacle = MudTuningTentacleTargeting.target(minecraft);
        return tentacle == null ? MudTuningSpatialPlacement.target(minecraft)
                : tentacle.position();
    }

    private static boolean localPlayer(HeldContext held, Minecraft minecraft) {
        return minecraft.player != null && held.playerId() == minecraft.player.getId();
    }

    private static Vec3 livePreviewTarget(Minecraft minecraft, HeldContext held) {
        InteractionHand hand = minecraft.player == null
                ? null : MudTuningInputController.heldWandHand(minecraft.player);
        boolean matches = hand == InteractionHand.MAIN_HAND
                ? held.mainHand() : hand == InteractionHand.OFF_HAND && !held.mainHand();
        return matches ? activeLocalPreviewTarget(minecraft) : null;
    }

    private static PreviewTransition preview(boolean mainHand) {
        return mainHand ? MAIN_HAND_PREVIEW : OFF_HAND_PREVIEW;
    }

    private static int brighten(int color) {
        int red = color >> 16 & 0xFF;
        int green = color >> 8 & 0xFF;
        int blue = color & 0xFF;
        int maximum = Math.max(red, Math.max(green, blue));
        if (maximum < 176) {
            float scale = 176.0F / Math.max(1, maximum);
            red = Mth.clamp(Math.round(red * scale), 0, 255);
            green = Mth.clamp(Math.round(green * scale), 0, 255);
            blue = Mth.clamp(Math.round(blue * scale), 0, 255);
        }
        return red << 16 | green << 8 | blue;
    }

    static int activeColor(double coreTime) {
        return brighten(MudTuningWandCoreTexture.beamColor(coreTime));
    }

    static int targetColor(int playerEntityId, double time) {
        Activation activation = ACTIVATIONS.get(playerEntityId);
        if (activation != null && activation.target != null
                && MudTuningWandAnimation.beamAlpha(
                        time - activation.startTime) > 0.001F) {
            return activation.color;
        }
        return activeColor(time);
    }

    private static final class Activation {
        private final int playerEntityId;
        private final MudTuningAnchor target;
        private final boolean mainHand;
        private final double startTime;
        private final int color;
        private float previousOpening;
        private float opening;
        private boolean openingPhase = true;

        private Activation(int playerEntityId, MudTuningAnchor target,
                boolean mainHand, double startTime, float startingOpening, int color) {
            this.playerEntityId = playerEntityId;
            this.target = target;
            this.mainHand = mainHand;
            this.startTime = startTime;
            this.color = color;
            this.previousOpening = startingOpening;
            this.opening = startingOpening;
        }

        private void tick(long gameTime) {
            previousOpening = opening;
            if (openingPhase) {
                opening = MudTuningWandAnimation.nextCageOpening(opening, true);
                if (MudTuningWandAnimation.cageReachedOpen(opening)) {
                    opening = 1.0F;
                    openingPhase = false;
                }
                return;
            }
            if (MudTuningWandAnimation.beamKeepsWandAimed(
                    target != null, gameTime - startTime)) {
                opening = 1.0F;
                return;
            }
            opening = MudTuningWandAnimation.nextCageOpening(opening, false);
        }

        private float opening(float partialTick) {
            return MudTuningWandAnimation.cageOpening(
                    previousOpening, opening, partialTick);
        }

        private float currentOpening() {
            return opening;
        }

        private boolean finished(long gameTime) {
            if (openingPhase || opening > 0.0F || previousOpening > 0.0F) {
                return false;
            }
            return target == null
                    || gameTime - startTime > MudTuningWandAnimation.BEAM_END_TICKS + 1.0D;
        }
    }

    record BeamView(int playerEntityId, MudTuningAnchor target, boolean mainHand,
            int color, float extension, float alpha, double age, double startTime) {
    }

    record AimView(Vec3 target, float amount) {
    }

    record HeldContext(int playerId, boolean mainHand) {
    }

    static final class PreviewTransition {
        private Vec3 target;
        private float previousAmount;
        private float amount;

        void tick(Vec3 nextTarget, boolean active) {
            previousAmount = amount;
            if (active && nextTarget != null) {
                target = nextTarget;
                amount = MudTuningWandAnimation.nextCageOpening(amount, true);
                return;
            }
            amount = MudTuningWandAnimation.nextCageOpening(amount, false);
            if (amount <= 0.0F) {
                target = null;
            }
        }

        Vec3 target() {
            return target;
        }

        float amount(float partialTick) {
            return MudTuningWandAnimation.cageOpening(
                    previousAmount, amount, partialTick);
        }

        boolean visible() {
            return target != null || amount > 0.0F || previousAmount > 0.0F;
        }

        void reset() {
            target = null;
            previousAmount = 0.0F;
            amount = 0.0F;
        }
    }
}

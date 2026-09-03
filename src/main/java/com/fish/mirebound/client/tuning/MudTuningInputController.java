package com.fish.mirebound.client.tuning;

import com.fish.mirebound.client.MudTuningSectionHighlightCache;
import com.fish.mirebound.client.MudTuningWandClientEffects;
import com.fish.mirebound.client.generation.MudTerrainGenerationController;
import com.fish.mirebound.compat.sable.SableCompat;
import com.fish.mirebound.mud.MudTuningAnchor;
import com.fish.mirebound.mud.tuning.MudTuningSelectionElement;
import com.fish.mirebound.network.payload.MudTuningRequestPayload;
import com.fish.mirebound.network.payload.MudTuningSelectionNudgePayload;
import com.fish.mirebound.network.payload.MudTuningGlobalRequestPayload;
import com.fish.mirebound.network.payload.MudTuningConversionSafetyPayload;
import com.fish.mirebound.network.payload.MudTuningConversionUnlockPayload;
import com.fish.mirebound.network.payload.TentacleWandActionPayload;
import com.fish.mirebound.client.tentacle.ClientTentacleManager;
import com.fish.mirebound.registry.ModBlocks;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Converts physical input into mode-specific, server-validated wand intents. */
public final class MudTuningInputController {
    static final int CONVERSION_UNLOCK_TICKS = 40;

    enum ConversionUnlockStage {
        NONE,
        STANDARD,
        UNRESTRICTED
    }

    private enum PendingConversionAction {
        NONE,
        CONVERT,
        RESTORE
    }

    enum ScrollIntent {
        NONE,
        SWITCH_MODE,
        NUDGE_RANGE,
        ADJUST_PLACEMENT
    }

    private static final IKeyConflictContext WAND_CONTEXT = new IKeyConflictContext() {
        @Override
        public boolean isActive() {
            Minecraft minecraft = Minecraft.getInstance();
            return minecraft.screen == null && minecraft.player != null
                    && heldWandHand(minecraft.player) != null;
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return other == this || other == KeyConflictContext.IN_GAME;
        }
    };
    private static final KeyMapping MODE_KEY = new KeyMapping(
            "key.mirebound.tuning_mode",
            WAND_CONTEXT,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_CONTROL,
            "key.categories.mirebound");
    private static final KeyMapping NUDGE_KEY = new KeyMapping(
            "key.mirebound.tuning_nudge",
            WAND_CONTEXT,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_ALT,
            "key.categories.mirebound");
    private static final KeyMapping OPEN_RANGE_KEY = new KeyMapping(
            "key.mirebound.open_tuning_range",
            WAND_CONTEXT,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_TAB,
            "key.categories.mirebound");
    private static final KeyMapping SELECT_ELEMENT_KEY = new KeyMapping(
            "key.mirebound.tuning_select_element",
            WAND_CONTEXT,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_ALT,
            "key.categories.mirebound");
    private static final KeyMapping QUICK_SUMMON_KEY = new KeyMapping(
            "key.mirebound.tuning_summon_tentacle",
            WAND_CONTEXT,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_ENTER,
            "key.categories.mirebound");
    private static final KeyMapping GENERATION_VOLUME_UP_KEY = new KeyMapping(
            "key.mirebound.tuning_generation_volume_up",
            WAND_CONTEXT,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UP,
            "key.categories.mirebound");
    private static final KeyMapping GENERATION_VOLUME_DOWN_KEY = new KeyMapping(
            "key.mirebound.tuning_generation_volume_down",
            WAND_CONTEXT,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_DOWN,
            "key.categories.mirebound");
    private static final KeyMapping GENERATION_REROLL_KEY = new KeyMapping(
            "key.mirebound.tuning_generation_reroll",
            WAND_CONTEXT,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_CONTROL,
            "key.categories.mirebound");
    private static final KeyMapping GENERATION_AXIS_KEY = new KeyMapping(
            "key.mirebound.tuning_generation_axis",
            WAND_CONTEXT,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT,
            "key.categories.mirebound");
    private static final KeyMapping GENERATION_ROTATE_KEY = new KeyMapping(
            "key.mirebound.tuning_generation_rotate",
            WAND_CONTEXT,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT,
            "key.categories.mirebound");
    private static final List<KeyMapping> WAND_MAPPINGS = List.of(
            MODE_KEY, NUDGE_KEY, OPEN_RANGE_KEY,
            SELECT_ELEMENT_KEY, QUICK_SUMMON_KEY,
            GENERATION_VOLUME_UP_KEY, GENERATION_VOLUME_DOWN_KEY,
            GENERATION_REROLL_KEY, GENERATION_AXIS_KEY,
            GENERATION_ROTATE_KEY);

    private static boolean attackHandled;
    private static boolean useHandled;
    private static InputConstants.Key cachedModeKey;
    private static InputConstants.Key cachedNudgeKey;
    private static InputConstants.Key cachedOpenRangeKey;
    private static InputConstants.Key cachedSelectElementKey;
    private static InputConstants.Key cachedQuickSummonKey;
    private static InputConstants.Key cachedGenerationVolumeUpKey;
    private static InputConstants.Key cachedGenerationVolumeDownKey;
    private static InputConstants.Key cachedGenerationRerollKey;
    private static InputConstants.Key cachedGenerationAxisKey;
    private static InputConstants.Key cachedGenerationRotateKey;
    private static List<KeyMapping> conflictingMappings = List.of();
    private static List<KeyMapping> conflictingNudgeMappings = List.of();
    private static List<KeyMapping> conflictingOpenRangeMappings = List.of();
    private static List<KeyMapping> conflictingSelectElementMappings = List.of();
    private static List<KeyMapping> conflictingQuickSummonMappings = List.of();
    private static List<KeyMapping> conflictingGenerationVolumeUpMappings = List.of();
    private static List<KeyMapping> conflictingGenerationVolumeDownMappings = List.of();
    private static List<KeyMapping> conflictingGenerationRerollMappings = List.of();
    private static List<KeyMapping> conflictingGenerationAxisMappings = List.of();
    private static List<KeyMapping> conflictingGenerationRotateMappings = List.of();
    private static int conversionUnlockTicks;
    private static boolean conversionUnlockRequested;
    private static boolean conversionChordLatched;
    private static PendingConversionAction pendingConversionAction =
            PendingConversionAction.NONE;
    private static int pendingConversionTicks;

    private MudTuningInputController() {
    }

    public static void registerKey(RegisterKeyMappingsEvent event) {
        event.register(MODE_KEY);
        event.register(NUDGE_KEY);
        event.register(OPEN_RANGE_KEY);
        event.register(SELECT_ELEMENT_KEY);
        event.register(QUICK_SUMMON_KEY);
        event.register(GENERATION_VOLUME_UP_KEY);
        event.register(GENERATION_VOLUME_DOWN_KEY);
        event.register(GENERATION_REROLL_KEY);
        event.register(GENERATION_AXIS_KEY);
        event.register(GENERATION_ROTATE_KEY);
    }

    public static KeyMapping modeKey() {
        return MODE_KEY;
    }

    public static KeyMapping nudgeKey() {
        return NUDGE_KEY;
    }

    public static KeyMapping openRangeKey() {
        return OPEN_RANGE_KEY;
    }

    public static KeyMapping selectElementKey() {
        return SELECT_ELEMENT_KEY;
    }

    public static KeyMapping quickSummonKey() {
        return QUICK_SUMMON_KEY;
    }

    public static KeyMapping generationVolumeUpKey() {
        return GENERATION_VOLUME_UP_KEY;
    }

    public static KeyMapping generationVolumeDownKey() {
        return GENERATION_VOLUME_DOWN_KEY;
    }

    public static KeyMapping generationRerollKey() {
        return GENERATION_REROLL_KEY;
    }

    public static KeyMapping generationAxisKey() {
        return GENERATION_AXIS_KEY;
    }

    public static KeyMapping generationRotateKey() {
        return GENERATION_ROTATE_KEY;
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.getConnection() == null) {
            resetTransient();
            return;
        }
        if (minecraft.screen != null || !minecraft.isWindowActive()
                || heldWandHand(minecraft.player) == null) {
            attackHandled = false;
            useHandled = false;
            conversionUnlockTicks = 0;
            conversionChordLatched = false;
            clearPendingConversion();
            releaseWandMappings();
            return;
        }
        if (!minecraft.options.keyAttack.isDown()) {
            attackHandled = false;
        }
        if (!minecraft.options.keyUse.isDown()) {
            useHandled = false;
        }
        updateConversionUnlock(minecraft);
        flushPendingConversion(minecraft);
        synchronizeModifierKeys(minecraft);
        suppressModifierConflicts(minecraft);
        consumeActionKeys(minecraft);
    }

    public static void handleKeyInput(InputEvent.Key event) {
        Minecraft minecraft = Minecraft.getInstance();
        InputConstants.Key pressed = InputConstants.getKey(event.getKey(), event.getScanCode());
        if (!ownsKey(pressed)) {
            return;
        }
        invalidateConflictCache(pressed);
        if (event.getAction() == GLFW.GLFW_RELEASE) {
            releaseOwnedKey(pressed);
            return;
        }
        if (minecraft.player == null || minecraft.screen != null
                || !minecraft.isWindowActive()
                || heldWandHand(minecraft.player) == null) {
            releaseOwnedKey(pressed);
            return;
        }
        synchronizeModifierKeys(minecraft);
        suppressModifierConflicts(minecraft);
        consumeActionKeys(minecraft);
    }

    public static boolean handleInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.getConnection() == null
                || (!event.isAttack() && !event.isUseItem() && !event.isPickBlock())) {
            return false;
        }
        InteractionHand hand = heldWandHand(minecraft.player);
        if (hand == null) {
            return false;
        }
        event.setCanceled(true);
        event.setSwingHand(false);
        if (event.isPickBlock()) {
            return true;
        }
        boolean fresh = event.isAttack() ? !attackHandled : !useHandled;
        if (event.isAttack()) {
            attackHandled = true;
        } else {
            useHandled = true;
        }
        if (!fresh || modifierDown(minecraft, MODE_KEY)) {
            return true;
        }
        if (MudTuningClientState.mode() == MudTuningWandMode.CONVERT
                && (conversionLocked() || conversionUnlockRequested
                        || conversionUnlockTicks > 0
                        || minecraft.options.keyAttack.isDown()
                                && minecraft.options.keyUse.isDown())) {
            if (minecraft.options.keyAttack.isDown()
                    && minecraft.options.keyUse.isDown()) {
                clearPendingConversion();
            }
            return true;
        }

        switch (MudTuningClientState.mode()) {
            case RANGE -> handleRange(minecraft, event.isAttack());
            case SINGLE -> handleSingle(minecraft);
            case CONVERT -> queueConversion(event.isAttack());
            case SUMMON -> handleSummon(minecraft, event.isAttack());
            case GENERATION -> handleGeneration(minecraft, event.isAttack());
            case SETTINGS -> requestGlobalScreen(
                    MudTuningClientState.GlobalScreen.SETTINGS);
        }
        return true;
    }

    public static boolean handleMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null
                || !minecraft.isWindowActive()
                || heldWandHand(minecraft.player) == null) {
            return false;
        }
        boolean switchMode = modifierDown(minecraft, MODE_KEY);
        boolean nudge = modifierDown(minecraft, NUDGE_KEY);
        synchronizeModifierKeys(minecraft);
        ScrollIntent intent = scrollIntent(
                MudTuningClientState.mode(), switchMode, nudge);
        if (intent == ScrollIntent.NONE) {
            return false;
        }
        double delta = event.getScrollDeltaY();
        if (Math.abs(delta) > 1.0E-6D) {
            switch (intent) {
                case SWITCH_MODE -> {
                    MudTuningClientState.cycleMode(delta > 0.0D ? -1 : 1);
                    MudTuningWandUiSounds.playModeSwitch(
                            minecraft, MudTuningClientState.mode());
                }
                case NUDGE_RANGE -> nudgeSelection(minecraft, delta);
                case ADJUST_PLACEMENT -> {
                    if (MudTuningClientState.mode() == MudTuningWandMode.GENERATION
                            && MudTerrainGenerationController.centerLocked()) {
                        if (MudTerrainGenerationController.moveLockedCenter(
                                minecraft, delta)) {
                            MudTuningWandUiSounds.playGenerationCenterMove(minecraft);
                        }
                    } else {
                        MudTuningSpatialPlacement.adjustDistance(delta);
                    }
                }
                case NONE -> {
                }
            }
        }
        event.setCanceled(true);
        return true;
    }

    public static InteractionHand heldWandHand(Player player) {
        if (player.getMainHandItem().getItem() == ModBlocks.MUD_TUNING_WAND.get()) {
            return InteractionHand.MAIN_HAND;
        }
        return player.getOffhandItem().getItem() == ModBlocks.MUD_TUNING_WAND.get()
                ? InteractionHand.OFF_HAND : null;
    }

    public static MudTuningAnchor pointedAnchor(Minecraft minecraft) {
        BlockHitResult hit = MudTuningWandTargeting.blockHit(minecraft);
        if (hit == null) {
            return null;
        }
        UUID subLevelId = SableCompat.subLevelIdAtStorage(
                minecraft.level, hit.getBlockPos());
        return subLevelId == null
                ? MudTuningAnchor.world(hit.getBlockPos())
                : MudTuningAnchor.sable(subLevelId, hit.getBlockPos());
    }

    public static void resetTransient() {
        attackHandled = false;
        useHandled = false;
        conversionUnlockTicks = 0;
        conversionChordLatched = false;
        clearPendingConversion();
        releaseWandMappings();
        cachedModeKey = null;
        cachedNudgeKey = null;
        cachedOpenRangeKey = null;
        cachedSelectElementKey = null;
        cachedQuickSummonKey = null;
        cachedGenerationVolumeUpKey = null;
        cachedGenerationVolumeDownKey = null;
        cachedGenerationRerollKey = null;
        cachedGenerationAxisKey = null;
        cachedGenerationRotateKey = null;
        conflictingMappings = List.of();
        conflictingNudgeMappings = List.of();
        conflictingOpenRangeMappings = List.of();
        conflictingSelectElementMappings = List.of();
        conflictingQuickSummonMappings = List.of();
        conflictingGenerationVolumeUpMappings = List.of();
        conflictingGenerationVolumeDownMappings = List.of();
        conflictingGenerationRerollMappings = List.of();
        conflictingGenerationAxisMappings = List.of();
        conflictingGenerationRotateMappings = List.of();
        MudTuningTentacleTargeting.invalidate();
        MudTuningWandTargeting.invalidate();
    }

    public static void resetSession() {
        resetTransient();
        conversionUnlockRequested = false;
        MudTuningClientSettings.resetServerState();
    }

    public static void acceptConversionSafety(
            MudTuningConversionSafetyPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean wasUnlocked = MudTuningClientSettings.conversionUnlocked();
        boolean wasUnrestrictedUnlocked =
                MudTuningClientSettings.unrestrictedConversionUnlocked();
        boolean wasUnrestrictedEnabled =
                MudTuningClientSettings.unrestrictedConversionEnabled();
        boolean standardUnlockedNow = payload.unlocked() && !wasUnlocked;
        boolean unrestrictedUnlockedNow = payload.unrestrictedUnlocked()
                && !wasUnrestrictedUnlocked;
        boolean unrestrictedToggled = wasUnrestrictedUnlocked
                && payload.unrestrictedUnlocked()
                && wasUnrestrictedEnabled != payload.unrestrictedEnabled();
        MudTuningClientSettings.acceptConversionSafety(
                payload.unlocked(), payload.unrestrictedUnlocked(),
                payload.unrestrictedEnabled());
        if (wasUnrestrictedEnabled != payload.unrestrictedEnabled()) {
            MudTuningSectionHighlightCache.reset();
        }
        conversionUnlockTicks = 0;
        boolean requested = conversionUnlockRequested;
        conversionUnlockRequested = false;
        if (!requested) {
            return;
        }
        if (unrestrictedUnlockedNow) {
            MudTuningWandUiSounds.playUnrestrictedConversionUnlock(minecraft);
        } else if (standardUnlockedNow) {
            MudTuningWandUiSounds.playConversionUnlock(minecraft);
        } else if (unrestrictedToggled) {
            MudTuningWandUiSounds.playUnrestrictedConversionToggle(
                    minecraft, payload.unrestrictedEnabled());
        }
    }

    static boolean conversionLocked() {
        return !MudTuningClientSettings.conversionUnlocked();
    }

    static float conversionUnlockProgress() {
        return conversionUnlockTicks / (float) CONVERSION_UNLOCK_TICKS;
    }

    static ConversionUnlockStage conversionUnlockStage() {
        if (!MudTuningClientSettings.conversionUnlocked()) {
            return ConversionUnlockStage.STANDARD;
        }
        if (!MudTuningClientSettings.unrestrictedConversionUnlocked()) {
            return ConversionUnlockStage.UNRESTRICTED;
        }
        return ConversionUnlockStage.NONE;
    }

    static boolean unrestrictedConversionEnabled() {
        return MudTuningClientSettings.unrestrictedConversionEnabled();
    }

    static int advanceConversionUnlock(int current, boolean bothDown, boolean active) {
        if (!active || !bothDown) {
            return 0;
        }
        return Math.min(CONVERSION_UNLOCK_TICKS, Math.max(0, current) + 1);
    }

    static boolean shouldPlayConversionUnlockStep(
            int previous, int current, boolean unrestricted) {
        int interval = unrestricted ? 2 : 4;
        return current > previous && current < CONVERSION_UNLOCK_TICKS
                && (current == 1 || current % interval == 0);
    }

    static int unrestrictedUnlockShake(int ticks, boolean vertical) {
        if (ticks <= 0) {
            return 0;
        }
        int amplitude = ticks >= CONVERSION_UNLOCK_TICKS * 3 / 4 ? 2 : 1;
        int span = amplitude * 2 + 1;
        int phase = ticks * (vertical ? 17 : 31) + (vertical ? 7 : 3);
        return Math.floorMod(phase, span) - amplitude;
    }

    private static void updateConversionUnlock(Minecraft minecraft) {
        boolean bothDown = minecraft.options.keyAttack.isDown()
                && minecraft.options.keyUse.isDown();
        if (!bothDown) {
            conversionChordLatched = false;
        }
        boolean active = !conversionUnlockRequested
                && !conversionChordLatched
                && MudTuningClientState.mode() == MudTuningWandMode.CONVERT;
        ConversionUnlockStage stage = conversionUnlockStage();
        if (stage == ConversionUnlockStage.NONE) {
            conversionUnlockTicks = 0;
            if (active && bothDown) {
                requestConversionSafetyChange();
            }
            return;
        }
        int previous = conversionUnlockTicks;
        conversionUnlockTicks = advanceConversionUnlock(
                conversionUnlockTicks, bothDown, active);
        if (shouldPlayConversionUnlockStep(previous, conversionUnlockTicks,
                stage == ConversionUnlockStage.UNRESTRICTED)) {
            MudTuningWandUiSounds.playConversionUnlockStep(
                    minecraft, stage == ConversionUnlockStage.UNRESTRICTED,
                    conversionUnlockProgress());
        }
        if (conversionUnlockTicks < CONVERSION_UNLOCK_TICKS) {
            return;
        }
        requestConversionSafetyChange();
    }

    private static void requestConversionSafetyChange() {
        clearPendingConversion();
        PacketDistributor.sendToServer(
                new MudTuningConversionUnlockPayload(true));
        conversionUnlockTicks = 0;
        conversionUnlockRequested = true;
        conversionChordLatched = true;
        attackHandled = true;
        useHandled = true;
    }

    static ScrollIntent scrollIntent(
            MudTuningWandMode mode, boolean modeDown, boolean nudgeDown) {
        if (modeDown) {
            return ScrollIntent.SWITCH_MODE;
        }
        if (!nudgeDown) {
            return ScrollIntent.NONE;
        }
        return switch (mode) {
            case RANGE -> ScrollIntent.NUDGE_RANGE;
            case SUMMON, GENERATION -> ScrollIntent.ADJUST_PLACEMENT;
            default -> ScrollIntent.NONE;
        };
    }

    private static void handleRange(Minecraft minecraft, boolean attack) {
        MudTuningAnchor target = pointedAnchor(minecraft);
        if (target != null) {
            MudTuningClientState.selectElement(attack
                    ? MudTuningSelectionElement.FIRST
                    : MudTuningSelectionElement.SECOND);
            send(attack ? MudTuningRequestPayload.Action.SELECT_FIRST
                    : MudTuningRequestPayload.Action.SELECT_SECOND, target);
        } else {
            pulseEmptyAction(minecraft);
        }
    }

    private static void handleSingle(Minecraft minecraft) {
        MudTuningAnchor target = pointedAnchor(minecraft);
        if (target != null) {
            send(MudTuningRequestPayload.Action.OPEN_SINGLE, target);
        } else {
            pulseEmptyAction(minecraft);
        }
    }

    private static void handleConversion(Minecraft minecraft, boolean attack) {
        MudTuningAnchor target = pointedAnchor(minecraft);
        if (target != null) {
            send(attack ? MudTuningRequestPayload.Action.CONVERT_SINGLE
                    : MudTuningRequestPayload.Action.RESTORE_SINGLE, target);
        } else {
            pulseEmptyAction(minecraft);
        }
    }

    private static void queueConversion(boolean convert) {
        pendingConversionAction = convert
                ? PendingConversionAction.CONVERT : PendingConversionAction.RESTORE;
        pendingConversionTicks = 1;
    }

    private static void flushPendingConversion(Minecraft minecraft) {
        if (pendingConversionAction == PendingConversionAction.NONE) {
            return;
        }
        if (MudTuningClientState.mode() != MudTuningWandMode.CONVERT
                || conversionLocked() || conversionUnlockRequested
                || conversionUnlockTicks > 0
                || minecraft.options.keyAttack.isDown()
                        && minecraft.options.keyUse.isDown()) {
            clearPendingConversion();
            return;
        }
        if (pendingConversionTicks-- > 0) {
            return;
        }
        PendingConversionAction action = pendingConversionAction;
        clearPendingConversion();
        handleConversion(minecraft, action == PendingConversionAction.CONVERT);
    }

    private static void clearPendingConversion() {
        pendingConversionAction = PendingConversionAction.NONE;
        pendingConversionTicks = 0;
    }

    private static void handleGeneration(Minecraft minecraft, boolean attack) {
        if (attack) {
            if (MudTerrainGenerationController.toggleCenterLock(minecraft)) {
                MudTuningWandUiSounds.playGenerationCenterLock(
                        minecraft, MudTerrainGenerationController.centerLocked());
            } else {
                pulseEmptyAction(minecraft);
            }
        } else {
            requestGlobalScreen(MudTuningClientState.GlobalScreen.GENERATION);
        }
    }

    private static void requestGlobalScreen(MudTuningClientState.GlobalScreen screen) {
        MudTuningClientState.expectGlobalScreen(screen);
        PacketDistributor.sendToServer(MudTuningGlobalRequestPayload.open());
    }

    private static void handleSummon(Minecraft minecraft, boolean attack) {
        switch (MudTuningClientState.summonType()) {
            case TENTACLE -> handleTentacle(minecraft, attack);
        }
    }

    private static void handleTentacle(Minecraft minecraft, boolean attack) {
        Camera camera = minecraft.gameRenderer.getMainCamera();
        if (!camera.isInitialized()) {
            return;
        }
        ClientTentacleManager.TentacleTarget target =
                MudTuningTentacleTargeting.target(minecraft);
        if (attack) {
            if (target != null) {
                Vec3 root = target.rootPosition();
                PacketDistributor.sendToServer(new TentacleWandActionPayload(
                        TentacleWandActionPayload.Action.REMOVE, target.instanceId(),
                        root.x, root.y, root.z, 0));
            } else {
                pulseEmptyAction(minecraft);
            }
            return;
        }
        if (target != null) {
            Vec3 point = target.position();
            PacketDistributor.sendToServer(new TentacleWandActionPayload(
                    TentacleWandActionPayload.Action.CONFIGURE, target.instanceId(),
                    point.x, point.y, point.z, 0));
            return;
        }
        Vec3 point = MudTuningSpatialPlacement.target(minecraft);
        if (point != null) {
            TentacleVolumeSelectionScreen.open(
                    minecraft, point, GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        } else {
            pulseEmptyAction(minecraft);
        }
    }

    private static void pulseEmptyAction(Minecraft minecraft) {
        InteractionHand hand = heldWandHand(minecraft.player);
        if (hand == null || minecraft.level == null) {
            return;
        }
        MudTuningWandClientEffects.triggerLocalPulse(
                minecraft.player.getId(), hand == InteractionHand.MAIN_HAND,
                minecraft.level.getGameTime());
        send(MudTuningRequestPayload.Action.ACTIVATE_WAND, MudTuningAnchor.WORLD_ORIGIN);
    }

    private static void consumeActionKeys(Minecraft minecraft) {
        while (SELECT_ELEMENT_KEY.consumeClick()) {
            if (minecraft.screen == null
                    && MudTuningClientState.mode() == MudTuningWandMode.RANGE) {
                MudTuningClientState.cycleSelectedElement();
            } else if (minecraft.screen == null
                    && MudTuningClientState.mode() == MudTuningWandMode.SUMMON
                    && MudTuningClientState.summonType()
                            == MudTuningSummonType.TENTACLE) {
                MudTuningTentacleTargeting.toggle(minecraft);
            } else if (minecraft.screen == null
                    && MudTuningClientState.mode() == MudTuningWandMode.GENERATION) {
                MudTerrainGenerationController.undo();
                MudTuningWandUiSounds.playGenerationUndo(minecraft);
            }
        }
        while (OPEN_RANGE_KEY.consumeClick()) {
            if (minecraft.screen != null) {
                continue;
            }
            if (MudTuningClientState.mode() == MudTuningWandMode.RANGE
                    && MudTuningClientState.hasFirst()
                    && MudTuningClientState.hasSecond()) {
                send(MudTuningRequestPayload.Action.OPEN_RANGE,
                        MudTuningAnchor.WORLD_ORIGIN);
            } else if (MudTuningClientState.mode() == MudTuningWandMode.SUMMON) {
                SummonSelectionScreen.open(minecraft);
            } else if (MudTuningClientState.mode() == MudTuningWandMode.GENERATION) {
                MudTerrainGenerationController.cycleType(1);
                MudTuningWandUiSounds.playGenerationType(minecraft,
                        MudTuningClientSettings.generationType().ordinal());
            }
        }
        while (QUICK_SUMMON_KEY.consumeClick()) {
            if (minecraft.screen != null) {
                continue;
            }
            if (MudTuningClientState.mode() == MudTuningWandMode.SUMMON) {
                quickSummon(minecraft);
            } else if (MudTuningClientState.mode()
                    == MudTuningWandMode.GENERATION) {
                MudTerrainGenerationController.generate(minecraft);
            }
        }
        while (GENERATION_VOLUME_UP_KEY.consumeClick()) {
            adjustGenerationVolume(minecraft, 1);
        }
        while (GENERATION_VOLUME_DOWN_KEY.consumeClick()) {
            adjustGenerationVolume(minecraft, -1);
        }
        while (GENERATION_REROLL_KEY.consumeClick()) {
            if (minecraft.screen == null
                    && MudTuningClientState.mode()
                            == MudTuningWandMode.GENERATION) {
                MudTerrainGenerationController.rerollSeed();
                MudTuningWandUiSounds.playGenerationSeed(minecraft);
            }
        }
        while (GENERATION_AXIS_KEY.consumeClick()) {
            if (minecraft.screen == null
                    && MudTuningClientState.mode()
                            == MudTuningWandMode.GENERATION) {
                MudTerrainGenerationController.cycleRotationAxis();
                MudTuningWandUiSounds.playGenerationAxis(
                        minecraft, MudTerrainGenerationController.rotationAxis());
            }
        }
        while (GENERATION_ROTATE_KEY.consumeClick()) {
            if (minecraft.screen == null
                    && MudTuningClientState.mode()
                            == MudTuningWandMode.GENERATION) {
                MudTerrainGenerationController.rotatePreview();
                MudTuningWandUiSounds.playGenerationRotation(minecraft);
            }
        }
    }

    private static void adjustGenerationVolume(
            Minecraft minecraft, int direction) {
        if (minecraft.screen != null
                || MudTuningClientState.mode() != MudTuningWandMode.GENERATION) {
            return;
        }
        MudTerrainGenerationController.adjustVolume(direction);
        MudTuningWandUiSounds.playVolumeStep(
                minecraft, MudTerrainGenerationController.volumeSoundLevel());
    }

    private static void quickSummon(Minecraft minecraft) {
        switch (MudTuningClientState.summonType()) {
            case TENTACLE -> TentacleVolumeSelectionScreen.summonDefault(
                    MudTuningSpatialPlacement.target(minecraft));
        }
    }

    private static boolean ownsKey(InputConstants.Key key) {
        return MODE_KEY.getKey().equals(key)
                || NUDGE_KEY.getKey().equals(key)
                || OPEN_RANGE_KEY.getKey().equals(key)
                || SELECT_ELEMENT_KEY.getKey().equals(key)
                || QUICK_SUMMON_KEY.getKey().equals(key)
                || GENERATION_VOLUME_UP_KEY.getKey().equals(key)
                || GENERATION_VOLUME_DOWN_KEY.getKey().equals(key)
                || GENERATION_REROLL_KEY.getKey().equals(key)
                || GENERATION_AXIS_KEY.getKey().equals(key)
                || GENERATION_ROTATE_KEY.getKey().equals(key);
    }

    private static void invalidateConflictCache(InputConstants.Key key) {
        if (MODE_KEY.getKey().equals(key)) {
            cachedModeKey = null;
        }
        if (NUDGE_KEY.getKey().equals(key)) {
            cachedNudgeKey = null;
        }
        if (OPEN_RANGE_KEY.getKey().equals(key)) {
            cachedOpenRangeKey = null;
        }
        if (SELECT_ELEMENT_KEY.getKey().equals(key)) {
            cachedSelectElementKey = null;
        }
        if (QUICK_SUMMON_KEY.getKey().equals(key)) {
            cachedQuickSummonKey = null;
        }
        if (GENERATION_VOLUME_UP_KEY.getKey().equals(key)) {
            cachedGenerationVolumeUpKey = null;
        }
        if (GENERATION_VOLUME_DOWN_KEY.getKey().equals(key)) {
            cachedGenerationVolumeDownKey = null;
        }
        if (GENERATION_REROLL_KEY.getKey().equals(key)) {
            cachedGenerationRerollKey = null;
        }
        if (GENERATION_AXIS_KEY.getKey().equals(key)) {
            cachedGenerationAxisKey = null;
        }
        if (GENERATION_ROTATE_KEY.getKey().equals(key)) {
            cachedGenerationRotateKey = null;
        }
    }

    private static void nudgeSelection(Minecraft minecraft, double scrollDelta) {
        MudTuningSelectionElement element = MudTuningClientState.selectedElement();
        if (element == MudTuningSelectionElement.NONE || !MudTuningClientState.hasFirst()) {
            return;
        }
        Camera camera = minecraft.gameRenderer.getMainCamera();
        if (!camera.isInitialized()) {
            return;
        }
        Vec3 look = new Vec3(camera.getLookVector());
        MudTuningAnchor first = MudTuningClientState.first();
        if (first.isSable()) {
            Object subLevel = SableCompat.subLevelById(minecraft.level, first.subLevelId());
            look = subLevel == null ? null : SableCompat.toLocalDirection(subLevel, look);
        }
        if (look == null || look.lengthSqr() < 1.0E-8D) {
            return;
        }
        Direction direction = Direction.getNearest(look.x, look.y, look.z);
        if (scrollDelta < 0.0D) {
            direction = direction.getOpposite();
        }
        PacketDistributor.sendToServer(
                new MudTuningSelectionNudgePayload(element, direction));
    }

    private static void suppressModifierConflicts(Minecraft minecraft) {
        if (heldWandHand(minecraft.player) == null) {
            return;
        }
        if (MODE_KEY.isDown()) {
            InputConstants.Key key = MODE_KEY.getKey();
            if (!key.equals(cachedModeKey)) {
                cachedModeKey = key;
                conflictingMappings = conflictingMappings(minecraft, MODE_KEY, key);
            }
            suppress(conflictingMappings);
        }
        if (NUDGE_KEY.isDown()) {
            InputConstants.Key key = NUDGE_KEY.getKey();
            if (!key.equals(cachedNudgeKey)) {
                cachedNudgeKey = key;
                conflictingNudgeMappings = conflictingMappings(minecraft, NUDGE_KEY, key);
            }
            suppress(conflictingNudgeMappings);
        }
        if (OPEN_RANGE_KEY.isDown()) {
            InputConstants.Key key = OPEN_RANGE_KEY.getKey();
            if (!key.equals(cachedOpenRangeKey)) {
                cachedOpenRangeKey = key;
                conflictingOpenRangeMappings = conflictingMappings(
                        minecraft, OPEN_RANGE_KEY, key);
            }
            suppress(conflictingOpenRangeMappings);
        }
        if (SELECT_ELEMENT_KEY.isDown()) {
            InputConstants.Key key = SELECT_ELEMENT_KEY.getKey();
            if (!key.equals(cachedSelectElementKey)) {
                cachedSelectElementKey = key;
                conflictingSelectElementMappings = conflictingMappings(
                        minecraft, SELECT_ELEMENT_KEY, key);
            }
            suppress(conflictingSelectElementMappings);
        }
        if (QUICK_SUMMON_KEY.isDown()) {
            InputConstants.Key key = QUICK_SUMMON_KEY.getKey();
            if (!key.equals(cachedQuickSummonKey)) {
                cachedQuickSummonKey = key;
                conflictingQuickSummonMappings = conflictingMappings(
                        minecraft, QUICK_SUMMON_KEY, key);
            }
            suppress(conflictingQuickSummonMappings);
        }
        if (GENERATION_VOLUME_UP_KEY.isDown()) {
            InputConstants.Key key = GENERATION_VOLUME_UP_KEY.getKey();
            if (!key.equals(cachedGenerationVolumeUpKey)) {
                cachedGenerationVolumeUpKey = key;
                conflictingGenerationVolumeUpMappings = conflictingMappings(
                        minecraft, GENERATION_VOLUME_UP_KEY, key);
            }
            suppress(conflictingGenerationVolumeUpMappings);
        }
        if (GENERATION_VOLUME_DOWN_KEY.isDown()) {
            InputConstants.Key key = GENERATION_VOLUME_DOWN_KEY.getKey();
            if (!key.equals(cachedGenerationVolumeDownKey)) {
                cachedGenerationVolumeDownKey = key;
                conflictingGenerationVolumeDownMappings = conflictingMappings(
                        minecraft, GENERATION_VOLUME_DOWN_KEY, key);
            }
            suppress(conflictingGenerationVolumeDownMappings);
        }
        if (GENERATION_REROLL_KEY.isDown()) {
            InputConstants.Key key = GENERATION_REROLL_KEY.getKey();
            if (!key.equals(cachedGenerationRerollKey)) {
                cachedGenerationRerollKey = key;
                conflictingGenerationRerollMappings = conflictingMappings(
                        minecraft, GENERATION_REROLL_KEY, key);
            }
            suppress(conflictingGenerationRerollMappings);
        }
        if (GENERATION_AXIS_KEY.isDown()) {
            InputConstants.Key key = GENERATION_AXIS_KEY.getKey();
            if (!key.equals(cachedGenerationAxisKey)) {
                cachedGenerationAxisKey = key;
                conflictingGenerationAxisMappings = conflictingMappings(
                        minecraft, GENERATION_AXIS_KEY, key);
            }
            suppress(conflictingGenerationAxisMappings);
        }
        if (GENERATION_ROTATE_KEY.isDown()) {
            InputConstants.Key key = GENERATION_ROTATE_KEY.getKey();
            if (!key.equals(cachedGenerationRotateKey)) {
                cachedGenerationRotateKey = key;
                conflictingGenerationRotateMappings = conflictingMappings(
                        minecraft, GENERATION_ROTATE_KEY, key);
            }
            suppress(conflictingGenerationRotateMappings);
        }
    }

    private static void synchronizeModifierKeys(Minecraft minecraft) {
        MODE_KEY.setDown(modifierDown(minecraft, MODE_KEY));
        NUDGE_KEY.setDown(modifierDown(minecraft, NUDGE_KEY));
        while (MODE_KEY.consumeClick()) {
        }
        while (NUDGE_KEY.consumeClick()) {
        }
    }

    private static boolean modifierDown(Minecraft minecraft, KeyMapping mapping) {
        if (minecraft.screen != null || !minecraft.isWindowActive()) {
            return false;
        }
        InputConstants.Key key = mapping.getKey();
        long window = minecraft.getWindow().getWindow();
        boolean physicalDown;
        if (key.getType() == InputConstants.Type.KEYSYM) {
            physicalDown = key.getValue() != InputConstants.UNKNOWN.getValue()
                    && InputConstants.isKeyDown(window, key.getValue());
        } else if (key.getType() == InputConstants.Type.MOUSE) {
            physicalDown = GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        } else {
            physicalDown = mapping.isDown();
        }
        return physicalDown
                && mapping.getKeyModifier().isActive(mapping.getKeyConflictContext());
    }

    private static void releaseOwnedKey(InputConstants.Key key) {
        for (KeyMapping mapping : WAND_MAPPINGS) {
            if (mapping.getKey().equals(key)) {
                mapping.setDown(false);
            }
        }
    }

    private static void releaseWandMappings() {
        for (KeyMapping mapping : WAND_MAPPINGS) {
            mapping.setDown(false);
            while (mapping.consumeClick()) {
            }
        }
    }

    private static List<KeyMapping> conflictingMappings(
            Minecraft minecraft, KeyMapping owner, InputConstants.Key key) {
        List<KeyMapping> matches = new ArrayList<>();
        for (KeyMapping mapping : minecraft.options.keyMappings) {
            if (mapping != owner && mapping.getKey().equals(key)) {
                matches.add(mapping);
            }
        }
        return List.copyOf(matches);
    }

    private static void suppress(List<KeyMapping> mappings) {
        for (KeyMapping mapping : mappings) {
            mapping.setDown(false);
            while (mapping.consumeClick()) {
                // Drain clicks so wand modifiers own their configured keys.
            }
        }
    }

    private static void send(
            MudTuningRequestPayload.Action action, MudTuningAnchor anchor) {
        PacketDistributor.sendToServer(new MudTuningRequestPayload(action, anchor));
    }
}

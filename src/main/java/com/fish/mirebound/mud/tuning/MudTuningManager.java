package com.fish.mirebound.mud.tuning;

import com.fish.mirebound.assimilation.AssimilationSystem;
import com.fish.mirebound.adaptive.AdaptiveMudEligibility;
import com.fish.mirebound.adaptive.AdaptiveMudBehaviorSettings;
import com.fish.mirebound.adaptive.AdaptiveMudConversionScheduler;
import com.fish.mirebound.adaptive.AdaptiveMudService;
import com.fish.mirebound.adaptive.AdaptiveMudBlock;
import com.fish.mirebound.adaptive.AdaptiveMudSourceStore;
import com.fish.mirebound.compat.sable.SableCompat;
import com.fish.mirebound.mud.MudBlock;
import com.fish.mirebound.mud.MudBlockProfileStore;
import com.fish.mirebound.mud.MudBlockVariant;
import com.fish.mirebound.mud.MudLocalProfileSync;
import com.fish.mirebound.mud.MudMediumRuntime;
import com.fish.mirebound.mud.MudPhysicsParameter;
import com.fish.mirebound.mud.MudPhysicsProfiles;
import com.fish.mirebound.mud.MudPhysicsSettings;
import com.fish.mirebound.mud.MudSinkingDepthControl;
import com.fish.mirebound.mud.MudTuningAnchor;
import com.fish.mirebound.mud.MudTuningScope;
import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.mud.flow.MudBlockMotionMode;
import com.fish.mirebound.mud.flow.MudFlowSystem;
import com.fish.mirebound.mud.flow.MudGravitySystem;
import com.fish.mirebound.network.payload.MudTuningApplyPayload;
import com.fish.mirebound.network.payload.MudTuningRequestPayload;
import com.fish.mirebound.network.payload.MudTuningSelectionPayload;
import com.fish.mirebound.network.payload.MudTuningSelectionNudgePayload;
import com.fish.mirebound.network.payload.MudTuningSessionPayload;
import com.fish.mirebound.network.payload.MudTuningGlobalRequestPayload;
import com.fish.mirebound.network.payload.MudTuningGlobalSettingsPayload;
import com.fish.mirebound.network.payload.MudTuningWandBeamPayload;
import com.fish.mirebound.network.payload.MudTuningWandPulsePayload;
import com.fish.mirebound.network.payload.AdaptiveMudActionPayload;
import com.fish.mirebound.registry.ModBlocks;
import com.fish.mirebound.tentacle.TentacleSystem;
import com.fish.mirebound.network.payload.TentacleWandActionPayload;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/** Server-authoritative wand selection and scoped tuning sessions. */
public final class MudTuningManager {
    private static final int MAX_CONFIGURATION_BLOCKS = 262_144;
    private static final int MAX_HIGHLIGHT_BLOCKS =
            MudTuningSelectionPayload.MAX_HIGHLIGHT_PRIMITIVES;
    private static final int MAX_HIGHLIGHT_PER_KIND = 1_024;
    private static final int MAX_HIGHLIGHT_SCAN_BLOCKS = 65_536;
    private static final int HIGHLIGHT_RADIUS = 48;
    private static final int HIGHLIGHT_CENTER_GRID = 8;
    private static final Map<UUID, Selection> SELECTIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, SessionWatch> SESSION_WATCHES = new ConcurrentHashMap<>();
    private static final Map<ServerLevel, Long> LEVEL_REVISIONS = new WeakHashMap<>();

    private MudTuningManager() {
    }

    private static void selectFirst(ServerPlayer player, MudTuningAnchor anchor,
            boolean mainHand) {
        Selection selection = selection(player);
        selection.dimension = player.level().dimension();
        selection.first = anchor;
        selection.second = null;
        selection.summary = null;
        displaySelectedPoint(player, "first", anchor);
        syncSelection(player);
        broadcastSelectionBeam(player, anchor, mainHand);
    }

    private static void selectSecond(ServerPlayer player, MudTuningAnchor anchor,
            boolean mainHand) {
        Selection selection = selection(player);
        if (selection.first == null || !player.level().dimension().equals(selection.dimension)) {
            selection.dimension = player.level().dimension();
            selection.first = anchor;
        } else if (!selection.first.sameDomain(anchor)) {
            player.displayClientMessage(Component.translatable(
                    "message.mirebound.tuning.different_domain"), true);
            syncSelection(player);
            return;
        }
        selection.second = anchor;
        selection.summary = null;
        displaySelectedPoint(player, "second", anchor);
        syncSelection(player);
        broadcastSelectionBeam(player, anchor, mainHand);
    }

    public static void handleRequest(ServerPlayer player, MudTuningRequestPayload payload) {
        if (MudTuningConversionSafety.requiresUnlock(payload.action())
                && !MudTuningConversionSafety.isUnlocked(player)) {
            return;
        }
        switch (payload.action()) {
            case SELECT_FIRST -> {
                MudTuningAnchor anchor = validateRequestedAnchor(player.serverLevel(), payload.anchor());
                if (anchor != null && validTarget(player, anchor)) {
                    selectFirst(player, anchor,
                            heldWandHand(player) == InteractionHand.MAIN_HAND);
                }
            }
            case SELECT_SECOND -> {
                MudTuningAnchor anchor = validateRequestedAnchor(player.serverLevel(), payload.anchor());
                if (anchor != null && validTarget(player, anchor)) {
                    selectSecond(player, anchor,
                            heldWandHand(player) == InteractionHand.MAIN_HAND);
                }
            }
            case OPEN_RANGE -> openRange(player);
            case OPEN_SINGLE -> openSingle(player, payload.anchor());
            case OPEN_WORLD -> openWorld(player);
            case REFRESH -> {
                invalidateStaleHighlights(player.serverLevel(), selection(player));
                syncSelection(player);
            }
            case REFRESH_SESSION -> refreshSession(player);
            case ACTIVATE_WAND -> broadcastWandPulse(
                    player, heldWandHand(player) == InteractionHand.MAIN_HAND);
            case CLEAR_SELECTION -> clearSelection(player);
            case LOCK_TARGET -> lockTarget(player, payload.anchor());
            case CONVERT_SINGLE -> mutateAdaptiveDirect(
                    player, payload.anchor(), false, true);
            case RESTORE_SINGLE -> mutateAdaptiveDirect(
                    player, payload.anchor(), false, false);
            case CONVERT_RANGE -> mutateAdaptiveDirect(
                    player, MudTuningAnchor.WORLD_ORIGIN, true, true);
            case RESTORE_RANGE -> mutateAdaptiveDirect(
                    player, MudTuningAnchor.WORLD_ORIGIN, true, false);
        }
    }

    public static void handleGlobalSettings(
            ServerPlayer player, MudTuningGlobalRequestPayload payload) {
        if (payload.apply() && player.hasPermissions(2)
                && payload.hasFiniteInteractionRange()) {
            MudPhysicsSettings.updateEruptionMaximumActivePerLevel(
                    payload.eruptionMaximumActivePerLevel());
            MudPhysicsSettings.updateEntityCoverageEnabled(
                    payload.entityCoverageEnabled());
            MudPhysicsSettings.updateEntityCoverageAutomaticFadeSeconds(
                    payload.entityCoverageAutomaticFadeSeconds());
            MudPhysicsSettings.updateMudTuningWandInteractionRange(
                    payload.interactionRange());
        }
        PacketDistributor.sendToPlayer(player, new MudTuningGlobalSettingsPayload(
                MudPhysicsSettings.eruptionMaximumActivePerLevel(),
                MudPhysicsSettings.entityCoverageEnabled(),
                MudPhysicsSettings.entityCoverageAutomaticFadeSeconds(),
                MudPhysicsSettings.mudTuningWandInteractionRange(),
                player.hasPermissions(2)));
    }

    public static void nudgeSelection(
            ServerPlayer player, MudTuningSelectionNudgePayload payload) {
        if (payload == null || payload.element() == MudTuningSelectionElement.NONE) {
            return;
        }
        Selection selection = selection(player);
        if (selection.dimension == null
                || !selection.dimension.equals(player.level().dimension())
                || selection.first == null
                || !validSelectionAnchor(player.serverLevel(), selection.first)) {
            syncSelection(player);
            return;
        }
        MudTuningSelectionMovement.Result moved = MudTuningSelectionMovement.move(
                selection.first.pos(),
                selection.second == null ? null : selection.second.pos(),
                payload.element(), payload.direction());
        if (moved == null) {
            syncSelection(player);
            return;
        }
        MudTuningAnchor first = selection.first.withPos(moved.first());
        MudTuningAnchor second = moved.second() == null
                ? null : selection.second.withPos(moved.second());
        if (!validSelectionAnchor(player.serverLevel(), first)
                || second != null
                        && (!first.sameDomain(second)
                                || !validSelectionAnchor(player.serverLevel(), second))) {
            syncSelection(player);
            return;
        }
        selection.first = first;
        selection.second = second;
        selection.summary = null;
        SESSION_WATCHES.remove(player.getUUID());
        syncSelection(player);
    }

    private static void clearSelection(ServerPlayer player) {
        Selection selection = selection(player);
        selection.dimension = player.level().dimension();
        selection.first = null;
        selection.second = null;
        selection.summary = null;
        SESSION_WATCHES.remove(player.getUUID());
        syncSelection(player);
    }

    private static void lockTarget(ServerPlayer player, MudTuningAnchor requested) {
        MudTuningAnchor anchor = validateRequestedAnchor(player.serverLevel(), requested);
        if (anchor != null && validTarget(player, anchor)) {
            broadcastSelectionBeam(player, anchor,
                    heldWandHand(player) == InteractionHand.MAIN_HAND);
        }
    }

    private static void mutateAdaptiveDirect(ServerPlayer player,
            MudTuningAnchor requested, boolean useSelection, boolean convert) {
        if (!player.hasPermissions(2)) {
            return;
        }
        MudTuningAnchor first;
        MudTuningAnchor second;
        if (useSelection) {
            Selection selection = selection(player);
            if (!selection.complete(player.serverLevel())) {
                player.displayClientMessage(Component.translatable(
                        "message.mirebound.tuning.incomplete"), true);
                return;
            }
            first = selection.first;
            second = selection.second;
        } else {
            first = validateRequestedAnchor(player.serverLevel(), requested);
            if (first == null || !validTarget(player, first)) {
                return;
            }
            second = first;
        }
        if (!first.sameDomain(second)) {
            return;
        }
        Bounds bounds = Bounds.of(first.pos(), second.pos());
        if (first.isSable()
                && SableCompat.subLevelById(player.serverLevel(), first.subLevelId()) == null) {
            return;
        }
        boolean accepted = AdaptiveMudConversionScheduler.submit(
                player, player.serverLevel(), first.isSable() ? first.subLevelId() : null,
                bounds.minimum, bounds.maximum,
                convert ? AdaptiveMudConversionScheduler.Operation.CONVERT
                        : AdaptiveMudConversionScheduler.Operation.RESTORE,
                null,
                convert && MudTuningConversionSafety.isUnrestrictedEnabled(player),
                (completedPlayer, result) -> finishAdaptiveDirect(
                        completedPlayer, convert, result));
        if (!accepted) {
            player.displayClientMessage(Component.translatable(
                    "message.mirebound.adaptive.task_active"), true);
            return;
        }
        broadcastSelectionBeam(player, first,
                heldWandHand(player) == InteractionHand.MAIN_HAND);
    }

    public static void markMudChanged(LevelAccessor level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        synchronized (LEVEL_REVISIONS) {
            LEVEL_REVISIONS.merge(serverLevel, 1L, Long::sum);
        }
    }

    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        markMudChanged(event.getLevel());
        invalidateSelectionAt(event.getLevel(), event.getPos());
    }

    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        markMudChanged(event.getLevel());
        invalidateSelectionAt(event.getLevel(), event.getPos());
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID playerId = event.getEntity().getUUID();
        SELECTIONS.remove(playerId);
        SESSION_WATCHES.remove(playerId);
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        SELECTIONS.clear();
        SESSION_WATCHES.clear();
        synchronized (LEVEL_REVISIONS) {
            LEVEL_REVISIONS.clear();
        }
    }

    public static void openRange(ServerPlayer player) {
        Selection selection = selection(player);
        if (!selection.complete(player.serverLevel())) {
            player.displayClientMessage(Component.translatable("message.mirebound.tuning.incomplete"), true);
            return;
        }
        Bounds bounds = Bounds.of(selection.first.pos(), selection.second.pos());
        if (!validConfigurationSelection(bounds)) {
            player.displayClientMessage(Component.translatable(
                    "message.mirebound.tuning.too_large", MAX_CONFIGURATION_BLOCKS), true);
            return;
        }
        sendSession(player, MudTuningScope.RANGE,
                selection.first.withPos(bounds.minimum), selection.second.withPos(bounds.maximum));
    }

    public static void openSingle(ServerPlayer player, MudTuningAnchor requested) {
        MudTuningAnchor anchor = validateRequestedAnchor(player.serverLevel(), requested);
        if (anchor == null || !validTarget(player, anchor)
                || player.level().getBlockState(anchor.pos()).isAir()) {
            return;
        }
        sendSession(player, MudTuningScope.SINGLE, anchor, anchor);
        broadcastSelectionBeam(player, anchor,
                heldWandHand(player) == InteractionHand.MAIN_HAND);
    }

    public static void openWorld(ServerPlayer player) {
        Selection selection = selection(player);
        MudTuningAnchor first = selection.first == null
                ? MudTuningAnchor.world(player.blockPosition()) : selection.first;
        MudTuningAnchor second = selection.second == null ? first : selection.second;
        sendSession(player, MudTuningScope.WORLD, first, second);
    }

    public static void handleTentacleWand(
            ServerPlayer player, TentacleWandActionPayload payload) {
        if (!player.hasPermissions(2) || payload == null
                || payload.action() == TentacleWandActionPayload.Action.INVALID
                || !Double.isFinite(payload.x()) || !Double.isFinite(payload.y())
                || !Double.isFinite(payload.z())) {
            return;
        }
        Vec3 requested = new Vec3(payload.x(), payload.y(), payload.z());
        double range = MudPhysicsSettings.mudTuningWandInteractionRange() + 1.0D;
        if (player.getEyePosition().distanceToSqr(requested) > range * range) {
            return;
        }
        if (payload.action() == TentacleWandActionPayload.Action.SUMMON) {
            if (payload.volume() < TentacleWandActionPayload.MINIMUM_SUMMON_VOLUME
                    || payload.volume() > TentacleWandActionPayload.MAXIMUM_SUMMON_VOLUME) {
                return;
            }
            BlockPos pos = BlockPos.containing(requested);
            if (!player.serverLevel().isInWorldBounds(pos)
                    || !player.serverLevel().getWorldBorder().isWithinBounds(pos)) {
                return;
            }
            int id = TentacleSystem.spawn(player.serverLevel(), requested, payload.volume());
            player.displayClientMessage(Component.translatable(id < 0
                    ? "message.mirebound.tuning.tentacle_limit"
                    : "message.mirebound.tuning.tentacle_summoned", id), true);
            if (id >= 0) {
                broadcastWandPulse(player,
                        heldWandHand(player) == InteractionHand.MAIN_HAND);
            }
            return;
        }
        Vec3 root = TentacleSystem.root(player.serverLevel(), payload.instanceId());
        if (root == null || !TentacleSystem.containsPoint(
                player.serverLevel(), payload.instanceId(), requested, 1.0D)
                || !hasLineOfSight(player, requested)) {
            return;
        }
        if (payload.action() == TentacleWandActionPayload.Action.REMOVE) {
            if (TentacleSystem.remove(player.serverLevel(), payload.instanceId())) {
                player.displayClientMessage(Component.translatable(
                        "message.mirebound.tuning.tentacle_removed",
                        payload.instanceId()), true);
                broadcastWandPulse(player,
                        heldWandHand(player) == InteractionHand.MAIN_HAND);
            }
            return;
        }
        MudTuningAnchor anchor = MudTuningAnchor.world(BlockPos.containing(root));
        sendTentacleSession(player, anchor);
    }

    private static boolean hasLineOfSight(ServerPlayer player, Vec3 target) {
        HitResult hit = player.serverLevel().clip(new ClipContext(
                player.getEyePosition(), target, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS
                || hit.getLocation().distanceToSqr(target) <= 0.04D;
    }

    private static void sendTentacleSession(ServerPlayer player, MudTuningAnchor anchor) {
        List<MudTuningSessionPayload.MediumProfile> profiles =
                List.of(MudTuningObjectScanner.worldTentacle().profile());
        SESSION_WATCHES.put(player.getUUID(), new SessionWatch(
                player.level().dimension(), MudTuningScope.WORLD, anchor, anchor,
                levelRevision(player.serverLevel()), true));
        PacketDistributor.sendToPlayer(player, new MudTuningSessionPayload(
                MudTuningScope.WORLD, true, anchor, anchor, profiles));
    }

    public static void apply(ServerPlayer player, MudTuningApplyPayload payload) {
        if (!player.hasPermissions(2) || payload.objectId() == null
                || payload.values().length != MudPhysicsParameter.COUNT
                || payload.changed().length != MudPhysicsParameter.COUNT) {
            return;
        }
        MudTuningObjectId objectId = payload.objectId();
        double[] requested = sanitizedValues(objectId, payload.values());
        if (payload.scope() == MudTuningScope.WORLD) {
            if (objectId.kind() == MudTuningObjectId.Kind.TENTACLE) {
                double[] values = mergeChanged(
                        MudPhysicsSettings.tentacleValues(), requested, payload.changed());
                MudPhysicsSettings.updateTentacle(values);
            } else if (objectId.kind() == MudTuningObjectId.Kind.NATIVE_MEDIUM) {
                SinkingMedium medium = objectId.nativeMedium();
                double[] values = mergeChanged(
                        MudPhysicsSettings.values(medium), requested, payload.changed());
                MudPhysicsSettings.update(medium, values);
                MudFlowSystem.invalidate(player.serverLevel(), medium);
                MudPhysicsSettings.broadcast(player.serverLevel(), medium);
            } else if (objectId.kind() == MudTuningObjectId.Kind.ADAPTIVE_DEFAULT) {
                AdaptiveMudBehaviorSettings settings =
                        AdaptiveMudBehaviorSettings.get(player.serverLevel());
                settings.update(mergeChanged(settings.values(), requested, payload.changed()));
                AdaptiveMudBehaviorSettings.broadcast(player.serverLevel());
                MudFlowSystem.invalidate(player.serverLevel(), SinkingMedium.MUD);
            } else {
                return;
            }
            AssimilationSystem.syncProfiles();
            markMudChanged(player.serverLevel());
            if (objectId.kind() == MudTuningObjectId.Kind.TENTACLE) {
                sendTentacleSession(player, payload.first());
            } else {
                openWorld(player);
            }
            return;
        }

        if (!payload.first().sameDomain(payload.second())
                || !validAnchorDomain(player.serverLevel(), payload.first())
                || !validAnchorDomain(player.serverLevel(), payload.second())) {
            return;
        }
        Bounds bounds = Bounds.of(payload.first().pos(), payload.second().pos());
        if (!validConfigurationSelection(bounds)) {
            return;
        }
        if (payload.scope() == MudTuningScope.SINGLE
                && (!bounds.minimum.equals(bounds.maximum) || !validTarget(player, payload.first()))) {
            return;
        }
        if (payload.scope() == MudTuningScope.RANGE) {
            Selection selection = selection(player);
            if (!selection.complete(player.serverLevel())) {
                return;
            }
            Bounds selected = Bounds.of(selection.first.pos(), selection.second.pos());
            if (!selection.first.sameDomain(payload.first()) || !selected.equals(bounds)) {
                return;
            }
        }

        SessionWatch watch = SESSION_WATCHES.get(player.getUUID());
        if (watch == null || !watch.matches(player.level().dimension(), payload.scope(),
                payload.first(), payload.second())) {
            return;
        }

        ServerLevel level = player.serverLevel();
        boolean sableScope = payload.first().isSable();
        Object subLevel = sableScope
                ? SableCompat.subLevelById(level, payload.first().subLevelId()) : null;
        if (sableScope && subLevel == null) {
            return;
        }
        MudTuningObjectScanner.ScanResult scan = MudTuningObjectScanner.scan(
                level, bounds.minimum, bounds.maximum, sableScope,
                MudTuningConversionSafety.isUnrestrictedEnabled(player));
        MudTuningObjectScanner.ObjectGroup group = scan.group(objectId);
        if (group == null || !new MudTuningCapabilities(
                group.profile().capabilities()).has(MudTuningCapabilities.EDIT_PARAMETERS)) {
            sendSession(player, payload.scope(), payload.first(), payload.second());
            return;
        }
        boolean[] effectiveChanged = Arrays.copyOf(payload.changed(), payload.changed().length);
        if (sableScope) {
            for (MudPhysicsParameter parameter : MudPhysicsParameter.values()) {
                if (isFlowParameter(parameter)) {
                    effectiveChanged[parameter.ordinal()] = false;
                }
            }
        }
        MudBlockProfileStore store = MudBlockProfileStore.get(level);
        List<BlockPos> targets = group.positions();
        Set<ChunkPos> changedChunks = new HashSet<>();
        SinkingMedium flowMedium = objectId.kind() == MudTuningObjectId.Kind.NATIVE_MEDIUM
                ? objectId.nativeMedium() : SinkingMedium.MUD;
        if (!sableScope) {
            MudFlowSystem.invalidate(level, flowMedium);
        }
        if (objectId.kind() == MudTuningObjectId.Kind.CONVERTED_BLOCK) {
            Map<BlockPos, double[]> replacements = new LinkedHashMap<>();
            if (!payload.followWorld() && hasChanged(effectiveChanged)) {
                for (BlockPos pos : targets) {
                    BlockState state = level.getBlockState(pos);
                    if (!(state.getBlock() instanceof AdaptiveMudBlock adaptive)) {
                        continue;
                    }
                    SinkingMedium currentMedium = adaptive.medium();
                    MudBlockProfileStore.Profile local = store.profile(level, pos, currentMedium);
                    double[] base = local != null
                            ? local.values()
                            : currentMedium == SinkingMedium.MUD
                                    ? AdaptiveMudBehaviorSettings.get(level).values()
                                    : MudPhysicsSettings.values(currentMedium);
                    replacements.put(pos, mergeChanged(base, requested, effectiveChanged));
                }
            }
            changedChunks.addAll(AdaptiveMudService.canonicalize(level, targets));
            for (BlockPos pos : targets) {
                if (payload.followWorld()) {
                    if (store.removeAll(pos)) {
                        changedChunks.add(new ChunkPos(pos));
                    }
                } else {
                    double[] values = replacements.get(pos);
                    if (values != null) {
                        store.putOrRemoveInherited(
                                level, pos, SinkingMedium.MUD, values);
                        changedChunks.add(new ChunkPos(pos));
                    }
                }
            }
        } else if (objectId.kind() == MudTuningObjectId.Kind.NATIVE_MEDIUM) {
            SinkingMedium medium = objectId.nativeMedium();
            for (BlockPos pos : targets) {
                if (payload.followWorld()) {
                    if (store.remove(pos)) {
                        changedChunks.add(new ChunkPos(pos));
                    }
                } else if (hasChanged(effectiveChanged)) {
                    MudBlockProfileStore.Profile local = store.profile(level, pos, medium);
                    double[] base = local == null
                            ? MudPhysicsSettings.values(medium)
                            : local.values();
                    store.putOrRemoveInherited(level, pos, medium,
                            mergeChanged(base, requested, effectiveChanged));
                    changedChunks.add(new ChunkPos(pos));
                }
            }
            if (payload.updateShape()) {
                MudBlockVariant variant = MudBlockVariant.byId(payload.blockVariant());
                if (variant == MudBlockVariant.SPECIAL) {
                    variant = MudBlockVariant.HEIGHT;
                }
                MudBlock.setInstanceShapes(level, targets, variant, payload.blockHeight());
            } else if (effectiveChanged[MudPhysicsParameter.AUTO_STACK_FILL.ordinal()]) {
                MudBlock.refreshStackFills(level, targets);
            }
        }
        for (ChunkPos chunk : changedChunks) {
            MudLocalProfileSync.broadcastChunk(level, chunk, subLevel);
        }
        if (!changedChunks.isEmpty()) {
            AssimilationSystem.syncProfiles();
        }
        if (!targets.isEmpty()) {
            if (!sableScope) {
                MudFlowSystem.wakeAll(level, targets);
                MudGravitySystem.wakeAll(level, targets);
            }
            markMudChanged(level);
        }
        sendSession(player, payload.scope(),
                payload.first().withPos(bounds.minimum), payload.second().withPos(bounds.maximum));
        syncSelection(player);
    }

    public static void applyAdaptive(
            ServerPlayer player, AdaptiveMudActionPayload payload) {
        if (!MudTuningConversionSafety.isUnlocked(player)
                || !player.hasPermissions(2) || payload.scope() == MudTuningScope.WORLD
                || payload.action() == AdaptiveMudActionPayload.Action.INVALID
                || payload.mediumId() < 0 || payload.mediumId() >= SinkingMedium.COUNT
                || !payload.first().sameDomain(payload.second())
                || !validAnchorDomain(player.serverLevel(), payload.first())
                || !validAnchorDomain(player.serverLevel(), payload.second())) {
            return;
        }
        ResourceLocation sourceFilter = payload.sourceBlockId();
        if (sourceFilter == null) {
            return;
        }
        if (AdaptiveMudActionPayload.ALL_SOURCE_BLOCKS.equals(sourceFilter)) {
            sourceFilter = null;
        } else if (!BuiltInRegistries.BLOCK.containsKey(sourceFilter)) {
            return;
        }
        Bounds bounds = Bounds.of(payload.first().pos(), payload.second().pos());
        if (!validConfigurationSelection(bounds)
                || payload.scope() == MudTuningScope.SINGLE
                        && (!bounds.minimum.equals(bounds.maximum)
                                || !validTarget(player, payload.first()))) {
            return;
        }
        if (payload.scope() == MudTuningScope.RANGE) {
            Selection selection = selection(player);
            if (!selection.complete(player.serverLevel())) {
                return;
            }
            Bounds selected = Bounds.of(selection.first.pos(), selection.second.pos());
            if (!selection.first.sameDomain(payload.first()) || !selected.equals(bounds)) {
                return;
            }
        }
        SessionWatch watch = SESSION_WATCHES.get(player.getUUID());
        if (watch == null || !watch.matches(player.level().dimension(), payload.scope(),
                payload.first(), payload.second())) {
            return;
        }
        if (payload.first().isSable()
                && SableCompat.subLevelById(
                        player.serverLevel(), payload.first().subLevelId()) == null) {
            return;
        }
        boolean convert = payload.action() == AdaptiveMudActionPayload.Action.CONVERT;
        boolean accepted = AdaptiveMudConversionScheduler.submit(
                player, player.serverLevel(), payload.first().isSable()
                        ? payload.first().subLevelId() : null,
                bounds.minimum, bounds.maximum,
                convert ? AdaptiveMudConversionScheduler.Operation.CONVERT
                        : AdaptiveMudConversionScheduler.Operation.RESTORE,
                sourceFilter,
                convert && MudTuningConversionSafety.isUnrestrictedEnabled(player),
                (completedPlayer, result) -> finishAdaptiveSession(
                        completedPlayer, payload, convert, result));
        if (!accepted) {
            player.displayClientMessage(Component.translatable(
                    "message.mirebound.adaptive.task_active"), true);
        }
    }

    private static void finishAdaptiveDirect(ServerPlayer player, boolean convert,
            AdaptiveMudConversionScheduler.Result result) {
        selection(player).summary = null;
        player.displayClientMessage(Component.translatable(
                convert
                        ? "message.mirebound.adaptive.converted"
                        : "message.mirebound.adaptive.restored",
                result.changed(), result.rejected()), true);
        syncSelection(player);
    }

    private static void finishAdaptiveSession(ServerPlayer player,
            AdaptiveMudActionPayload payload, boolean convert,
            AdaptiveMudConversionScheduler.Result result) {
        selection(player).summary = null;
        player.displayClientMessage(Component.translatable(
                convert ? "message.mirebound.adaptive.converted"
                        : "message.mirebound.adaptive.restored",
                result.changed(), result.rejected()), true);
        sendSession(player, payload.scope(), payload.first(), payload.second());
        syncSelection(player);
    }

    public static void syncSelection(ServerPlayer player) {
        Selection selection = selection(player);
        boolean sameDimension = selection.dimension != null
                && selection.dimension.equals(player.level().dimension());
        boolean hasFirst = sameDimension && selection.first != null
                && validSelectionAnchor(player.serverLevel(), selection.first);
        boolean hasSecond = hasFirst && selection.second != null
                && selection.first.sameDomain(selection.second)
                && validSelectionAnchor(player.serverLevel(), selection.second);
        MudTuningAnchor first = hasFirst ? selection.first : MudTuningAnchor.WORLD_ORIGIN;
        MudTuningAnchor second = hasSecond ? selection.second : MudTuningAnchor.WORLD_ORIGIN;
        MudTuningSelectionPayload.SelectionSummary summary = hasSecond
                ? selectionSummary(player, selection)
                : MudTuningSelectionPayload.SelectionSummary.EMPTY;
        BlockPos center = player.blockPosition();
        MudBlockProfileStore store = MudBlockProfileStore.get(player.serverLevel());
        List<MudTuningSelectionPayload.HighlightGroup> highlights = new ArrayList<>();
        int remaining = MAX_HIGHLIGHT_BLOCKS;
        if (hasSecond && first.isSable() && selection.incompatiblePositions.length > 0) {
            Set<Long> incompatible = new HashSet<>();
            for (long packed : selection.incompatiblePositions) {
                incompatible.add(packed);
            }
            remaining = appendHighlightGeometry(highlights,
                    MudTuningSelectionPayload.HighlightKind.INCOMPATIBLE,
                    first.subLevelId(), incompatible, true, remaining,
                    selection.highlightSample == null
                            ? first.pos() : selection.highlightSample.center());
        }
        AABB worldBounds = new AABB(
                center.getX() - HIGHLIGHT_RADIUS, center.getY() - HIGHLIGHT_RADIUS,
                center.getZ() - HIGHLIGHT_RADIUS,
                center.getX() + HIGHLIGHT_RADIUS + 1.0D,
                center.getY() + HIGHLIGHT_RADIUS + 1.0D,
                center.getZ() + HIGHLIGHT_RADIUS + 1.0D);
        for (Object subLevel : SableCompat.subLevelsIntersecting(player.serverLevel(), worldBounds)) {
            if (remaining <= 0
                    || highlights.size() >= MudTuningSelectionPayload.MAX_HIGHLIGHT_GROUPS) {
                break;
            }
            UUID subLevelId = SableCompat.subLevelId(subLevel);
            AABB localBounds = SableCompat.localBounds(subLevel, worldBounds);
            if (subLevelId == null || localBounds == null) {
                continue;
            }
            Bounds local = Bounds.of(
                    BlockPos.containing(localBounds.minX, localBounds.minY, localBounds.minZ),
                    BlockPos.containing(localBounds.maxX, localBounds.maxY, localBounds.maxZ));
            remaining = appendHighlightDomain(player.serverLevel(), store,
                    AdaptiveMudSourceStore.get(player.serverLevel()),
                    subLevelId, local, subLevel,
                    BlockPos.containing(localBounds.getCenter()), remaining, highlights);
        }
        PacketDistributor.sendToPlayer(player, new MudTuningSelectionPayload(
                hasFirst, first, hasSecond, second, summary, List.copyOf(highlights)));
    }

    static void conversionSafetyChanged(ServerPlayer player) {
        Selection selection = selection(player);
        selection.summary = null;
        selection.incompatiblePositions = new long[0];
        selection.highlightSample = null;
        syncSelection(player);
    }

    private static void sendSession(ServerPlayer player, MudTuningScope scope,
            MudTuningAnchor first, MudTuningAnchor second) {
        if (!first.sameDomain(second)) {
            return;
        }
        Bounds bounds = Bounds.of(first.pos(), second.pos());
        MudTuningAnchor minimum = first.withPos(bounds.minimum);
        MudTuningAnchor maximum = second.withPos(bounds.maximum);
        MudTuningObjectScanner.ScanResult scan = scope == MudTuningScope.WORLD
                ? null
                : MudTuningObjectScanner.scan(player.serverLevel(), bounds.minimum,
                        bounds.maximum, first.isSable(), MAX_HIGHLIGHT_PER_KIND,
                        MudTuningConversionSafety.isUnrestrictedEnabled(player));
        List<MudTuningSessionPayload.MediumProfile> profiles = scan == null
                ? worldProfiles(player.serverLevel())
                : scan.groups().stream()
                        .map(MudTuningObjectScanner.ObjectGroup::profile)
                        .toList();
        cacheSelectionScan(player, scope, minimum, maximum, scan);
        SESSION_WATCHES.put(player.getUUID(), new SessionWatch(
                player.level().dimension(), scope, minimum, maximum,
                levelRevision(player.serverLevel()), false));
        PacketDistributor.sendToPlayer(player, new MudTuningSessionPayload(
                scope, player.hasPermissions(2), minimum, maximum, profiles));
        if (profiles.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.mirebound.tuning.no_mud"), true);
        }
    }

    private static void refreshSession(ServerPlayer player) {
        SessionWatch watch = SESSION_WATCHES.get(player.getUUID());
        if (watch == null || !watch.dimension.equals(player.level().dimension())) {
            return;
        }
        long revision = levelRevision(player.serverLevel());
        if (!refreshNeeded(watch.revision, revision)) {
            return;
        }
        if (validAnchorDomain(player.serverLevel(), watch.first)
                && validAnchorDomain(player.serverLevel(), watch.second)) {
            if (watch.tentacle) {
                sendTentacleSession(player, watch.first);
            } else {
                sendSession(player, watch.scope, watch.first, watch.second);
            }
        }
    }

    static boolean refreshNeeded(long observedRevision, long currentRevision) {
        return observedRevision != currentRevision;
    }

    private static long levelRevision(ServerLevel level) {
        synchronized (LEVEL_REVISIONS) {
            return LEVEL_REVISIONS.getOrDefault(level, 0L);
        }
    }

    private static List<MudTuningSessionPayload.MediumProfile> worldProfiles(ServerLevel level) {
        List<MudTuningSessionPayload.MediumProfile> profiles = new ArrayList<>();
        for (SinkingMedium medium : SinkingMedium.values()) {
            if (ModBlocks.blockFor(medium) == null) {
                continue;
            }
            profiles.add(MudTuningObjectScanner.worldNative(medium).profile());
        }
        profiles.add(MudTuningObjectScanner.worldAdaptive(level).profile());
        return List.copyOf(profiles);
    }

    private static double[] sanitizedValues(MudTuningObjectId objectId, double[] requested) {
        if (objectId.kind() == MudTuningObjectId.Kind.TENTACLE) {
            return MudPhysicsProfiles.sanitizeTentacle(requested);
        }
        SinkingMedium medium = objectId.nativeMedium();
        double[] values = medium == null
                ? AdaptiveMudBehaviorSettings.defaults()
                : Arrays.copyOf(MudPhysicsProfiles.defaultValues(medium), MudPhysicsParameter.COUNT);
        for (MudPhysicsParameter parameter : MudPhysicsParameter.values()) {
            if (medium == null ? parameter.appliesToAdaptive() : parameter.appliesTo(medium)) {
                values[parameter.ordinal()] = parameter.sanitize(requested[parameter.ordinal()]);
            }
        }
        MudSinkingDepthControl.enforceSimpleBounds(values);
        return values;
    }

    private static boolean isFlowParameter(MudPhysicsParameter parameter) {
        return parameter.name().startsWith("FLOW_")
                || parameter == MudPhysicsParameter.GRAVITY_FALLING_ENABLED;
    }

    private static double[] mergeChanged(double[] base, double[] requested, boolean[] changed) {
        double[] merged = Arrays.copyOf(base, MudPhysicsParameter.COUNT);
        for (MudPhysicsParameter parameter : MudPhysicsParameter.values()) {
            int index = parameter.ordinal();
            if (index < changed.length && changed[index]) {
                merged[index] = requested[index];
            }
        }
        MudBlockMotionMode.enforceExclusive(merged, changed);
        return merged;
    }

    private static boolean hasChanged(boolean[] changed) {
        for (boolean value : changed) {
            if (value) {
                return true;
            }
        }
        return false;
    }

    private static Selection selection(ServerPlayer player) {
        return SELECTIONS.computeIfAbsent(player.getUUID(), ignored -> new Selection());
    }

    private static MudTuningSelectionPayload.SelectionSummary selectionSummary(
            ServerPlayer player, Selection selection) {
        ServerLevel level = player.serverLevel();
        Bounds bounds = Bounds.of(selection.first.pos(), selection.second.pos());
        BlockPos center = quantizedHighlightCenter(selectionHighlightCenter(player, selection));
        HighlightSample sample = new HighlightSample(selection.first, selection.second, center);
        long volume = selectionVolume(bounds);
        boolean forceAllBlocks = MudTuningConversionSafety.isUnrestrictedEnabled(player);
        boolean refreshHighlights = selection.summary == null
                || selection.forceAllBlocks != forceAllBlocks;
        if (refreshHighlights) {
            selection.forceAllBlocks = forceAllBlocks;
            if (volume <= MAX_CONFIGURATION_BLOCKS) {
                MudTuningObjectScanner.ScanResult scan = MudTuningObjectScanner.summarize(
                        level, bounds.minimum, bounds.maximum, selection.first.isSable(),
                        MAX_HIGHLIGHT_PER_KIND, center, forceAllBlocks);
                selection.incompatiblePositions = scan.incompatiblePositions();
                selection.summary = scan.summary();
                selection.highlightSample = sample;
                return selection.summary;
            }
            selection.summary = new MudTuningSelectionPayload.SelectionSummary(
                    volume, 0, 0, 0, 0, 0);
        }
        if (refreshHighlights || !sample.equals(selection.highlightSample)) {
            BlockPos[] scanBounds = boundedHighlightScanBounds(
                    bounds.minimum, bounds.maximum, center,
                    HIGHLIGHT_RADIUS, MAX_HIGHLIGHT_SCAN_BLOCKS);
            selection.incompatiblePositions = scanBounds.length == 0
                    ? new long[0]
                    : MudTuningObjectScanner.summarize(
                            level, scanBounds[0], scanBounds[1], selection.first.isSable(),
                            MAX_HIGHLIGHT_PER_KIND, center,
                            forceAllBlocks).incompatiblePositions();
            selection.highlightSample = sample;
        }
        return selection.summary;
    }

    private static BlockPos selectionHighlightCenter(ServerPlayer player, Selection selection) {
        if (!selection.first.isSable()) {
            return player.blockPosition();
        }
        Object subLevel = SableCompat.subLevelById(
                player.serverLevel(), selection.first.subLevelId());
        Vec3 local = subLevel == null ? null : SableCompat.toLocal(subLevel, player.position());
        return local == null ? selection.first.pos() : BlockPos.containing(local);
    }

    private static BlockPos quantizedHighlightCenter(BlockPos center) {
        return new BlockPos(
                Math.floorDiv(center.getX(), HIGHLIGHT_CENTER_GRID) * HIGHLIGHT_CENTER_GRID
                        + HIGHLIGHT_CENTER_GRID / 2,
                Math.floorDiv(center.getY(), HIGHLIGHT_CENTER_GRID) * HIGHLIGHT_CENTER_GRID
                        + HIGHLIGHT_CENTER_GRID / 2,
                Math.floorDiv(center.getZ(), HIGHLIGHT_CENTER_GRID) * HIGHLIGHT_CENTER_GRID
                        + HIGHLIGHT_CENTER_GRID / 2);
    }

    static BlockPos[] boundedHighlightScanBounds(BlockPos first, BlockPos second,
            BlockPos center, int radius, long maxVolume) {
        int[] minimum = {
                Math.max(Math.min(first.getX(), second.getX()), center.getX() - radius),
                Math.max(Math.min(first.getY(), second.getY()), center.getY() - radius),
                Math.max(Math.min(first.getZ(), second.getZ()), center.getZ() - radius)
        };
        int[] maximum = {
                Math.min(Math.max(first.getX(), second.getX()), center.getX() + radius),
                Math.min(Math.max(first.getY(), second.getY()), center.getY() + radius),
                Math.min(Math.max(first.getZ(), second.getZ()), center.getZ() + radius)
        };
        for (int axis = 0; axis < 3; axis++) {
            if (minimum[axis] > maximum[axis]) {
                return new BlockPos[0];
            }
        }
        while (boundedVolume(minimum, maximum) > maxVolume) {
            int axis = 0;
            for (int candidate = 1; candidate < 3; candidate++) {
                if (maximum[candidate] - minimum[candidate]
                        > maximum[axis] - minimum[axis]) {
                    axis = candidate;
                }
            }
            if (minimum[axis] == maximum[axis]) {
                return new BlockPos[0];
            }
            int centerCoordinate = axis == 0 ? center.getX()
                    : axis == 1 ? center.getY() : center.getZ();
            if ((long) maximum[axis] - centerCoordinate
                    >= (long) centerCoordinate - minimum[axis]) {
                maximum[axis]--;
            } else {
                minimum[axis]++;
            }
        }
        return new BlockPos[] {
                new BlockPos(minimum[0], minimum[1], minimum[2]),
                new BlockPos(maximum[0], maximum[1], maximum[2])
        };
    }

    private static long boundedVolume(int[] minimum, int[] maximum) {
        return (long) (maximum[0] - minimum[0] + 1)
                * (maximum[1] - minimum[1] + 1)
                * (maximum[2] - minimum[2] + 1);
    }

    private static void cacheSelectionScan(ServerPlayer player, MudTuningScope scope,
            MudTuningAnchor minimum, MudTuningAnchor maximum,
            MudTuningObjectScanner.ScanResult scan) {
        if (scope != MudTuningScope.RANGE || scan == null) {
            return;
        }
        Selection selection = selection(player);
        if (!selection.complete(player.serverLevel())
                || !selection.first.sameDomain(minimum)
                || !Bounds.of(selection.first.pos(), selection.second.pos())
                        .equals(Bounds.of(minimum.pos(), maximum.pos()))) {
            return;
        }
        selection.summary = scan.summary();
        selection.incompatiblePositions = scan.incompatiblePositions();
        selection.highlightSample = null;
        selection.forceAllBlocks = MudTuningConversionSafety.isUnrestrictedEnabled(player);
    }

    private static void invalidateStaleHighlights(ServerLevel level, Selection selection) {
        if (selection.summary == null || selection.incompatiblePositions.length == 0) {
            return;
        }
        for (long packed : selection.incompatiblePositions) {
            BlockPos pos = BlockPos.of(packed);
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof AdaptiveMudBlock
                    || state.getBlock() instanceof MudBlock
                    || AdaptiveMudEligibility.check(level, pos, state).supported()
                    || MudTuningObjectScanner.isIgnoredState(state)) {
                selection.summary = null;
                selection.incompatiblePositions = new long[0];
                return;
            }
        }
    }

    private static void invalidateSelectionAt(LevelAccessor levelAccess, BlockPos pos) {
        if (!(levelAccess instanceof ServerLevel level)) {
            return;
        }
        UUID subLevelId = SableCompat.subLevelIdAtStorage(level, pos);
        UUID domain = subLevelId == null
                ? MudTuningAnchor.WORLD_SUB_LEVEL_ID : subLevelId;
        for (Selection selection : SELECTIONS.values()) {
            if (selection.summary == null || selection.dimension == null
                    || !selection.dimension.equals(level.dimension())
                    || selection.first == null || selection.second == null
                    || !selection.first.subLevelId().equals(domain)) {
                continue;
            }
            Bounds bounds = Bounds.of(selection.first.pos(), selection.second.pos());
            if (inside(bounds, pos)) {
                selection.summary = null;
                selection.incompatiblePositions = new long[0];
            }
        }
    }

    private static MudTuningAnchor anchorAt(ServerLevel level, BlockPos pos) {
        UUID subLevelId = SableCompat.subLevelIdAtStorage(level, pos);
        return subLevelId == null
                ? MudTuningAnchor.world(pos)
                : MudTuningAnchor.sable(subLevelId, pos);
    }

    private static MudTuningAnchor validateRequestedAnchor(
            ServerLevel level, MudTuningAnchor requested) {
        if (!validAnchorPosition(level, requested)
                || !level.getChunkSource().hasChunk(
                        requested.pos().getX() >> 4, requested.pos().getZ() >> 4)) {
            return null;
        }
        MudTuningAnchor actual = anchorAt(level, requested.pos());
        return actual.equals(requested) ? actual : null;
    }

    private static boolean validAnchorDomain(ServerLevel level, MudTuningAnchor anchor) {
        return validAnchorPosition(level, anchor)
                && anchor.equals(anchorAt(level, anchor.pos()));
    }

    private static boolean validTarget(ServerPlayer player, MudTuningAnchor anchor) {
        BlockPos pos = anchor.pos();
        if (!validAnchorDomain(player.serverLevel(), anchor)
                || !player.serverLevel().getChunkSource().hasChunk(
                        pos.getX() >> 4, pos.getZ() >> 4)) {
            return false;
        }
        Vec3 target = Vec3.atCenterOf(pos);
        if (anchor.isSable()) {
            Object subLevel = SableCompat.subLevelById(player.serverLevel(), anchor.subLevelId());
            target = SableCompat.toWorld(subLevel, target);
            if (target == null) {
                return false;
            }
        }
        double range = MudPhysicsSettings.mudTuningWandInteractionRange() + 1.0D;
        return player.distanceToSqr(target) <= range * range;
    }

    private static boolean validSelectionAnchor(ServerLevel level, MudTuningAnchor anchor) {
        if (!validAnchorPosition(level, anchor)) {
            return false;
        }
        return !anchor.isSable()
                || SableCompat.subLevelById(level, anchor.subLevelId()) != null;
    }

    private static boolean validAnchorPosition(
            ServerLevel level, MudTuningAnchor anchor) {
        return anchor != null
                && level.isInWorldBounds(anchor.pos())
                && (anchor.isSable()
                        || level.getWorldBorder().isWithinBounds(anchor.pos()));
    }

    private static void displaySelectedPoint(
            ServerPlayer player, String point, MudTuningAnchor anchor) {
        if (anchor.isSable()) {
            player.displayClientMessage(Component.translatable(
                    "message.mirebound.tuning." + point + "_sable"), true);
            return;
        }
        BlockPos pos = anchor.pos();
        player.displayClientMessage(Component.translatable(
                "message.mirebound.tuning." + point,
                pos.getX(), pos.getY(), pos.getZ()), true);
    }

    private static void broadcastSelectionBeam(ServerPlayer player,
            MudTuningAnchor anchor, boolean mainHand) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                new MudTuningWandBeamPayload(
                        player.getId(), anchor, mainHand, player.level().getGameTime()));
        playWandActivationSound(player);
    }

    private static void broadcastWandPulse(ServerPlayer player, boolean mainHand) {
        PacketDistributor.sendToPlayersTrackingEntity(player,
                new MudTuningWandPulsePayload(
                        player.getId(), mainHand, player.level().getGameTime()));
        playWandActivationSound(player);
    }

    private static void playWandActivationSound(ServerPlayer player) {
        player.serverLevel().playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                SoundEvents.COPPER_BULB_TURN_ON, SoundSource.PLAYERS, 0.34F, 1.42F);
        player.serverLevel().playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.20F, 1.72F);
    }

    private static InteractionHand heldWandHand(ServerPlayer player) {
        return player.getMainHandItem().getItem() == ModBlocks.MUD_TUNING_WAND.get()
                ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
    }

    private static int appendHighlightDomain(ServerLevel level, MudBlockProfileStore store,
            AdaptiveMudSourceStore sources, UUID subLevelId, Bounds bounds,
            Object subLevel, BlockPos priorityCenter, int limit,
            List<MudTuningSelectionPayload.HighlightGroup> groups) {
        if (limit <= 0) {
            return 0;
        }
        Set<Long> nativeModified = new HashSet<>();
        Set<Long> nativeFlow = new HashSet<>();
        Set<Long> nativeFlowMixed = new HashSet<>();
        for (BlockPos pos : store.modifiedIn(
                bounds.minimum, bounds.maximum, Integer.MAX_VALUE)) {
            UUID owner = SableCompat.subLevelIdAtStorage(level, pos);
            BlockState state = level.getBlockState(pos);
            if ((subLevel == null ? owner == null : subLevelId.equals(owner))
                    && state.getBlock() instanceof MudBlock
                    && !(state.getBlock() instanceof AdaptiveMudBlock)
                    && store.isModified(level, pos)) {
                MudBlock mud = (MudBlock) state.getBlock();
                if (MudMediumRuntime.flowProfile(level, pos, mud.medium()).enabled()) {
                    (store.hasNonFlowChanges(level, pos, mud.medium())
                            ? nativeFlowMixed : nativeFlow).add(pos.asLong());
                } else {
                    nativeModified.add(pos.asLong());
                }
            }
        }
        limit = appendHighlightGeometry(groups,
                MudTuningSelectionPayload.HighlightKind.MODIFIED_NATIVE,
                subLevelId, nativeModified, false, limit, priorityCenter);
        limit = appendHighlightGeometry(groups,
                MudTuningSelectionPayload.HighlightKind.MODIFIED_NATIVE_FLOW,
                subLevelId, nativeFlow, false, limit, priorityCenter);
        limit = appendHighlightGeometry(groups,
                MudTuningSelectionPayload.HighlightKind.MODIFIED_NATIVE_FLOW_MIXED,
                subLevelId, nativeFlowMixed, false, limit, priorityCenter);
        if (limit <= 0) {
            return 0;
        }

        Set<Long> defaults = new HashSet<>();
        Set<Long> modified = new HashSet<>();
        int minimumChunkX = bounds.minimum.getX() >> 4;
        int maximumChunkX = bounds.maximum.getX() >> 4;
        int minimumChunkZ = bounds.minimum.getZ() >> 4;
        int maximumChunkZ = bounds.maximum.getZ() >> 4;
        for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
            for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
                for (AdaptiveMudSourceStore.Entry entry : sources.entriesInChunk(
                        level, new ChunkPos(chunkX, chunkZ))) {
                    BlockPos pos = entry.pos();
                    if (!inside(bounds, pos)) {
                        continue;
                    }
                    UUID owner = SableCompat.subLevelIdAtStorage(level, pos);
                    if (subLevel == null ? owner != null : !subLevelId.equals(owner)) {
                        continue;
                    }
                    Set<Long> target = store.isModified(level, pos) ? modified : defaults;
                    target.add(pos.asLong());
                }
            }
        }
        limit = appendHighlightGeometry(groups,
                MudTuningSelectionPayload.HighlightKind.CONVERTED_DEFAULT,
                subLevelId, defaults, false, limit, priorityCenter);
        return appendHighlightGeometry(groups,
                MudTuningSelectionPayload.HighlightKind.CONVERTED_MODIFIED,
                subLevelId, modified, false, limit, priorityCenter);
    }

    private static int appendHighlightGeometry(
            List<MudTuningSelectionPayload.HighlightGroup> groups,
            MudTuningSelectionPayload.HighlightKind kind, UUID subLevelId,
            Set<Long> positions, boolean includeFaces, int limit,
            BlockPos priorityCenter) {
        if (positions.isEmpty() || limit <= 0) {
            return limit;
        }
        int kindRemaining = MAX_HIGHLIGHT_PER_KIND;
        for (MudTuningSelectionPayload.HighlightGroup group : groups) {
            if (group.kind() == kind) {
                kindRemaining -= group.positions().length + group.edgeCorners().length;
            }
        }
        int available = Math.min(limit, Math.max(0, kindRemaining));
        MudTuningHighlightGeometry.BudgetedResult geometry =
                MudTuningHighlightGeometry.fitToBudget(
                        positions, priorityCenter, available, includeFaces);
        if (geometry.primitiveCount() == 0) {
            return limit;
        }
        long[] edgeCorners = new long[geometry.edges().size()];
        byte[] edgeAxes = new byte[geometry.edges().size()];
        for (int index = 0; index < geometry.edges().size(); index++) {
            MudTuningHighlightGeometry.Edge edge = geometry.edges().get(index);
            edgeCorners[index] = edge.corner();
            edgeAxes[index] = (byte) edge.axis();
        }
        groups.add(new MudTuningSelectionPayload.HighlightGroup(
                kind, subLevelId, geometry.positions(), edgeCorners, edgeAxes));
        return limit - geometry.primitiveCount();
    }

    private static boolean inside(Bounds bounds, BlockPos pos) {
        return pos.getX() >= bounds.minimum.getX() && pos.getX() <= bounds.maximum.getX()
                && pos.getY() >= bounds.minimum.getY() && pos.getY() <= bounds.maximum.getY()
                && pos.getZ() >= bounds.minimum.getZ() && pos.getZ() <= bounds.maximum.getZ();
    }

    private static boolean validConfigurationSelection(Bounds bounds) {
        long volume = selectionVolume(bounds);
        return volume > 0L && volume <= MAX_CONFIGURATION_BLOCKS;
    }

    private static long selectionVolume(Bounds bounds) {
        long x = (long) bounds.maximum.getX() - bounds.minimum.getX() + 1L;
        long y = (long) bounds.maximum.getY() - bounds.minimum.getY() + 1L;
        long z = (long) bounds.maximum.getZ() - bounds.minimum.getZ() + 1L;
        if (x <= 0L || y <= 0L || z <= 0L || x > Long.MAX_VALUE / y) {
            return Long.MAX_VALUE;
        }
        long xy = x * y;
        return xy > Long.MAX_VALUE / z ? Long.MAX_VALUE : xy * z;
    }

    private static final class Selection {
        private ResourceKey<Level> dimension;
        private MudTuningAnchor first;
        private MudTuningAnchor second;
        private MudTuningSelectionPayload.SelectionSummary summary;
        private long[] incompatiblePositions = new long[0];
        private HighlightSample highlightSample;
        private boolean forceAllBlocks;

        private boolean complete(ServerLevel level) {
            return dimension != null && dimension.equals(level.dimension())
                    && first != null && second != null && first.sameDomain(second)
                    && validSelectionAnchor(level, first)
                    && validSelectionAnchor(level, second);
        }
    }

    private record HighlightSample(MudTuningAnchor first, MudTuningAnchor second,
            BlockPos center) {
    }

    private record Bounds(BlockPos minimum, BlockPos maximum) {
        private static Bounds of(BlockPos first, BlockPos second) {
            return new Bounds(
                    new BlockPos(Math.min(first.getX(), second.getX()),
                            Math.min(first.getY(), second.getY()), Math.min(first.getZ(), second.getZ())),
                    new BlockPos(Math.max(first.getX(), second.getX()),
                            Math.max(first.getY(), second.getY()), Math.max(first.getZ(), second.getZ())));
        }
    }

    private record SessionWatch(ResourceKey<Level> dimension, MudTuningScope scope,
            MudTuningAnchor first, MudTuningAnchor second, long revision, boolean tentacle) {
        private boolean matches(ResourceKey<Level> currentDimension, MudTuningScope requestedScope,
                MudTuningAnchor requestedFirst, MudTuningAnchor requestedSecond) {
            return dimension.equals(currentDimension) && scope == requestedScope
                    && first.equals(requestedFirst) && second.equals(requestedSecond);
        }
    }

}

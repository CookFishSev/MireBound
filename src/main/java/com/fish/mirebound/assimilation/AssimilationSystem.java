package com.fish.mirebound.assimilation;

import com.fish.mirebound.adaptive.MudVisualPalette;
import com.fish.mirebound.adaptive.MudVisualSource;
import com.fish.mirebound.compat.sable.SableCompat;
import com.fish.mirebound.mud.MudBodyPart;
import com.fish.mirebound.mud.ArmorMudData;
import com.fish.mirebound.mud.ArmorMudManager;
import com.fish.mirebound.mud.MudPlayerData;
import com.fish.mirebound.mud.MudMediumRuntime;
import com.fish.mirebound.mud.MudStateStore;
import com.fish.mirebound.mud.MudSurface;
import com.fish.mirebound.mud.MudSurfaceLayout;
import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.network.payload.AssimilationQteTracePayload;
import com.fish.mirebound.coverage.MudCoverageService;
import com.fish.mirebound.physics.PlayerGravityControl;
import com.fish.mirebound.registry.ModMudworkContent;
import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Deque;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import static com.fish.mirebound.physics.MudMovementControl.clearAssimilationMovementSpeed;
import static com.fish.mirebound.physics.MudMovementControl.updateAssimilationMovementSpeed;

/** Server-authoritative reusable assimilation state machine. */
public final class AssimilationSystem {
    private static final int SOUL_POSITION_STALE_TICKS = 12;
    private static final int INVENTORY_CLOT_INTERVAL_TICKS = 20;
    private static final float INVENTORY_CLOT_GAIN_PER_SECOND = 0.0005F;
    private static final float INVENTORY_CLOT_PROGRESS_LIMIT = 0.99F;
    private static final SinkingMedium[] MEDIA = SinkingMedium.values();

    private AssimilationSystem() {
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        AssimilationState state = state(player);
        MudPlayerData mud = MudStateStore.get(player);
        refreshBehaviorContext(player, state, mud, player.tickCount);
        AssimilationProfile profile = profileFor(state);
        if (player.isSpectator()) {
            if (state.active()) {
                release(player, state, 0, false);
                sync(player, state, profile, true);
            }
            return;
        }
        addInventoryClotExposure(player, state);
        profile = profileFor(state);
        if (state.rescueGraceTicks > 0) {
            state.rescueGraceTicks--;
        }
        if (state.active() && !profile.enabled()) {
            release(player, state, 0);
            sync(player, state, profile, true);
            return;
        }

        if (state.frozen()) {
            if (!sameDimension(player, state)
                    || !updateFrozenBody(player, state, profile)) {
                release(player, state, profile.rescueGraceTicks(), false);
                sync(player, state, profile, true);
                return;
            }
            AssimilationRescueSystem.update(player, state, profile);
            if (state.stage == AssimilationStage.RESTORING && --state.restoringTicks <= 0) {
                release(player, state, profile.rescueGraceTicks());
                player.displayClientMessage(Component.translatable(
                        "message.mirebound.assimilation.released"), true);
            }
            sync(player, state, profile, false);
            saveIfNeeded(player, state);
            return;
        }

        boolean inAssimilation = mud.inMud
                && hasAssimilationContact(player, mud, player.tickCount)
                && !com.fish.mirebound.mud.MudPhysics.isPollutionSuppressed(player)
                && (long) player.tickCount - mud.lastMudTick <= 1L;
        AssimilationPurgeSystem.stopIfBlocked(player, state);
        if (inAssimilation && state.rescueGraceTicks <= 0
                && !state.partialPurgeActive) {
            state.beginAssimilating(patternSeedForSession(player, state));
            float bodyHeight = Math.max(0.25F, player.getBbHeight());
            float immersion = Mth.clamp((float) (mud.depth / bodyHeight), 0.0F, 1.0F);
            addContactContributions(player, state, mud, immersion, player.tickCount);
            profile = profileFor(state);
            if (state.progress >= 0.9999F && profile.finalStasisEnabled()) {
                state.seal(player.position(), dimensionId(player), player.getYRot(), player.getXRot(),
                        player.walkAnimation.position(1.0F), player.walkAnimation.speed(1.0F));
                state.qteCooldownTicks = profile.soulTransitionTicks()
                        + profile.selfRescueQteNextDelayTicks();
                initializeFrozenBody(player, state);
                state.soulPosition = player.getEyePosition();
                state.soulPositionTick = player.tickCount;
                playSealSound(player);
                sync(player, state, profile, true);
                player.displayClientMessage(Component.translatable(
                        "message.mirebound.assimilation.sealed"), true);
            }
        }

        if (state.progress > 0.0001F) {
            updateAssimilationMovementSpeed(
                    player, profile.movementScale(state.progress));
        } else {
            clearAssimilationMovementSpeed(player);
        }
        AssimilationPurgeSystem.update(player, state, profileFor(state));
        sync(player, state, profile, false);
        saveIfNeeded(player, state);
    }

    public static void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer rescuer)) {
            return;
        }
        if (isFrozen(rescuer)) {
            event.setCanceled(true);
            return;
        }
        if (!(event.getTarget() instanceof ServerPlayer target)) {
            return;
        }
        AssimilationState state = AssimilationStateStore.get(target);
        if (state == null || state.stage != AssimilationStage.SEALED) {
            return;
        }
        event.setCanceled(true);
        AssimilationRescueSystem.rescueHit(
                rescuer, target, state,
                AssimilationRescueSystem.hitPoint(rescuer, target)
                        .orElse(target.getBoundingBox().getCenter()));
    }

    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer rescuer)) {
            return;
        }
        if (isFrozen(rescuer)) {
            event.setCanceled(true);
            return;
        }
        if (!rescuer.getMainHandItem().is(ItemTags.PICKAXES)) {
            return;
        }
        AABB clickedArea = new AABB(event.getPos()).inflate(1.25D);
        ServerPlayer target = rescuer.serverLevel().getEntitiesOfClass(
                        ServerPlayer.class, clickedArea,
                        candidate -> candidate != rescuer && isFrozen(candidate))
                .stream()
                .min(java.util.Comparator.comparingDouble(rescuer::distanceToSqr))
                .orElse(null);
        if (target == null || rescuer.distanceToSqr(target) > 36.0D) {
            return;
        }
        AssimilationState state = AssimilationStateStore.get(target);
        if (state == null || state.stage != AssimilationStage.SEALED) {
            return;
        }
        event.setCanceled(true);
        AssimilationRescueSystem.rescueHit(
                rescuer, target, state,
                AssimilationRescueSystem.hitPoint(rescuer, target)
                        .orElse(target.getBoundingBox().getCenter()));
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        cancelFrozenInteraction(event.getEntity(), event::setCanceled);
    }

    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        cancelFrozenInteraction(event.getEntity(), event::setCanceled);
    }

    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        cancelFrozenInteraction(event.getEntity(), event::setCanceled);
    }

    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        cancelFrozenInteraction(event.getEntity(), event::setCanceled);
    }

    private static void cancelFrozenInteraction(Player player,
            java.util.function.Consumer<Boolean> cancel) {
        if (player instanceof ServerPlayer serverPlayer && isFrozen(serverPlayer)) {
            cancel.accept(true);
        }
    }

    public static void onLivingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        AssimilationState state = AssimilationStateStore.get(player);
        if (state != null && state.frozen()) {
            event.setCanceled(true);
        }
    }

    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        AssimilationState state = state(player);
        AssimilationStateStore.load(player, state);
        if (state.active()) {
            state.ensurePatternSeed(patternSeedForSession(player, state));
        }
        if (state.frozen() && !sameDimension(player, state)) {
            state.reset(profileFor(state).rescueGraceTicks());
        }
        sync(player, state, profileFor(state), true);
    }

    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AssimilationState state = AssimilationStateStore.remove(player);
            if (state != null) {
                save(player, state);
            }
            PlayerGravityControl.release(player, PlayerGravityControl.Owner.ASSIMILATION);
            clearAssimilationMovementSpeed(player);
        }
    }

    public static void onClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getOriginal() instanceof ServerPlayer original)) {
            return;
        }
        AssimilationState old = AssimilationStateStore.remove(original);
        clearAssimilationMovementSpeed(original);
        clearAssimilationMovementSpeed(player);
        AssimilationState next = state(player);
        if (!event.isWasDeath() && old != null) {
            next.load(old.save());
        } else {
            next.reset(0);
            AssimilationStateStore.clearPersistent(player);
        }
        sync(player, next, profileFor(next), true);
    }

    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer observer
                && event.getTarget() instanceof ServerPlayer target) {
            AssimilationState state = state(target);
            PacketDistributor.sendToPlayer(observer,
                    AssimilationSynchronizer.payload(
                            target, state, profileFor(state), true));
        }
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        for (var entry : AssimilationStateStore.entries()) {
            save(entry.getKey(), entry.getValue());
            PlayerGravityControl.release(entry.getKey(), PlayerGravityControl.Owner.ASSIMILATION);
            clearAssimilationMovementSpeed(entry.getKey());
        }
        AssimilationStateStore.clear();
    }

    public static void syncProfiles() {
        for (var entry : AssimilationStateStore.entries()) {
            entry.getValue().clearBehaviorContext();
            refreshRuntimeProfiles(entry.getKey(), entry.getValue());
            sync(entry.getKey(), entry.getValue(), profileFor(entry.getValue()), true);
        }
    }

    public static boolean isFrozen(Player player) {
        return player instanceof ServerPlayer serverPlayer
                && AssimilationStateStore.get(serverPlayer) != null
                && AssimilationStateStore.get(serverPlayer).frozen();
    }

    /**
     * Frozen players are transported in world space. Letting Sable inherit the
     * carrier's hidden plot space corrupts the player's chunk-tracking ticket
     * when Carry On dismounts them onto a physical structure.
     */
    public static boolean bypassSableVehicleTracking(Entity entity) {
        return entity instanceof Player player && entity.isPassenger() && isFrozen(player);
    }

    public static void clear(ServerPlayer player) {
        AssimilationState state = state(player);
        release(player, state, profileFor(state).rescueGraceTicks());
        save(player, state);
        sync(player, state, profileFor(state), true);
    }

    public static void setProgress(ServerPlayer player, float progress) {
        AssimilationState state = state(player);
        AssimilationProfile profile = profileFor(state);
        float bounded = Mth.clamp(progress, 0.0F, 1.0F);
        if (state.frozen()) {
            release(player, state, 0);
        }
        if (bounded <= 0.0001F) {
            release(player, state, profile.rescueGraceTicks());
        } else {
            state.beginAssimilating(patternSeedForSession(player, state));
            state.setProgress(state.medium, bounded);
            state.rescueGraceTicks = 0;
            state.dirty = true;
            if (bounded >= 0.9999F && profile.finalStasisEnabled()) {
                state.seal(player.position(), dimensionId(player),
                        player.getYRot(), player.getXRot(),
                        player.walkAnimation.position(1.0F), player.walkAnimation.speed(1.0F));
                initializeFrozenBody(player, state);
                playSealSound(player);
            } else {
                updateAssimilationMovementSpeed(player, profile.movementScale(bounded));
            }
        }
        save(player, state);
        sync(player, state, profile, true);
    }

    public static void togglePartialPurge(ServerPlayer player) {
        AssimilationPurgeSystem.toggle(player);
    }

    public static void cancelPartialPurgeForMovement(ServerPlayer player) {
        AssimilationState state = state(player);
        AssimilationProfile profile = profileFor(state);
        if (AssimilationPurgeSystem.cancelForMovement(state, profile)) {
            sync(player, state, profile, true);
        }
    }

    static void initializeFrozenBody(ServerPlayer player, AssimilationState state) {
        AssimilationFrozenBodySystem.initialize(player, state);
    }

    /**
     * Advances the sealed shell once. Standard riding is the compatibility boundary for
     * Carry On and similar transport mods: the carrier owns position while mounted, then
     * this solver resumes from the accepted dismount point.
     */
    private static boolean updateFrozenBody(ServerPlayer player, AssimilationState state,
            AssimilationProfile profile) {
        return AssimilationFrozenBodySystem.update(player, state, profile);
    }

    private static void release(ServerPlayer player, AssimilationState state, int graceTicks) {
        release(player, state, graceTicks, true);
    }

    private static void release(ServerPlayer player, AssimilationState state, int graceTicks,
            boolean returnToBody) {
        AssimilationFrozenBodySystem.release(
                player, state, graceTicks, returnToBody);
    }

    static void playSealSound(ServerPlayer player) {
        player.serverLevel().playSound(null, player.blockPosition(),
                SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.PLAYERS, 0.62F, 0.58F);
        player.serverLevel().playSound(null, player.blockPosition(),
                SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.78F, 0.66F);
    }

    public static void handleSelfRescueQte(ServerPlayer player, int sequence, int cell,
            int button, int phase) {
        AssimilationRescueSystem.handleQte(
                player, sequence, cell, button, phase);
    }

    public static void handleSelfRescueTrace(ServerPlayer player, int sequence, int cell,
            int button, int action, int node) {
        AssimilationRescueSystem.handleTrace(
                player, sequence, cell, button, action, node);
    }

    /** Prevents ordinary washable paint from immediately filling an active shell fracture. */
    public static boolean keepsCrackClear(Player player, int cell) {
        return AssimilationRescueSystem.keepsCrackClear(player, cell);
    }

    public static void updateSoulPosition(ServerPlayer player, float x, float y, float z) {
        AssimilationRescueSystem.updateSoulPosition(player, x, y, z);
    }

    private static int patternSeedForSession(ServerPlayer player, AssimilationState state) {
        return AssimilationRescueSystem.patternSeedForSession(player, state);
    }

    static int cellAtHit(ServerPlayer target, AssimilationState state, Vec3 hit) {
        return AssimilationCrackGeometry.cellAtHit(target, state, hit);
    }

    static MudSurface hitSurface(double partY, double normalizedX, double normalizedZ) {
        return AssimilationCrackGeometry.hitSurface(
                partY, normalizedX, normalizedZ);
    }

    static void sync(ServerPlayer player, AssimilationState state,
            AssimilationProfile profile, boolean immediate) {
        AssimilationSynchronizer.sync(player, state, profile, immediate);
    }

    static AssimilationProfile profileFor(AssimilationState state) {
        if (state != null && state.behaviorProfile != null) {
            return state.behaviorProfile;
        }
        if (state != null && state.medium != null) {
            AssimilationProfile stored = state.runtimeProfiles[state.medium.id()];
            if (stored != null) {
                return stored;
            }
        }
        return AssimilationConfig.profileFor(state == null ? null : state.medium);
    }

    static SinkingMedium chooseBehaviorMedium(
            SinkingMedium physicalMedium, float[] contactWeights,
            boolean[] enabled, SinkingMedium fallback) {
        if (physicalMedium != null
                && physicalMedium.id() < contactWeights.length
                && physicalMedium.id() < enabled.length
                && contactWeights[physicalMedium.id()] > 0.0F
                && enabled[physicalMedium.id()]) {
            return physicalMedium;
        }
        SinkingMedium selected = null;
        float strongest = 0.0F;
        for (SinkingMedium candidate : MEDIA) {
            int id = candidate.id();
            if (id >= contactWeights.length || id >= enabled.length
                    || !enabled[id] || contactWeights[id] <= 0.0F) {
                continue;
            }
            float weight = contactWeights[id];
            if (selected == null || weight > strongest
                    || weight == strongest && id < selected.id()) {
                selected = candidate;
                strongest = weight;
            }
        }
        return selected == null ? fallback : selected;
    }

    private static void refreshBehaviorContext(ServerPlayer player,
            AssimilationState state, MudPlayerData mud, int tick) {
        SinkingMedium physical = mud.physicsMedium;
        SinkingMedium selected = null;
        if (physical != null
                && mud.assimilationContactWeight(physical, tick) > 0.0F
                && contactProfile(player, mud, physical, tick).enabled()) {
            selected = physical;
        } else {
            float strongest = 0.0F;
            for (SinkingMedium candidate : MEDIA) {
                float weight = mud.assimilationContactWeight(candidate, tick);
                if (weight <= 0.0F
                        || !contactProfile(player, mud, candidate, tick).enabled()) {
                    continue;
                }
                if (selected == null || weight > strongest
                        || weight == strongest && candidate.id() < selected.id()) {
                    selected = candidate;
                    strongest = weight;
                }
            }
        }
        if (selected != null) {
            BlockPos position = mud.assimilationContactPosition(selected, tick);
            state.setBehaviorContext(selected, position,
                    contactProfile(player, mud, selected, tick));
            return;
        }
        SinkingMedium fallback = state.medium;
        AssimilationProfile fallbackProfile = fallback == null
                ? AssimilationProfile.DEFAULT
                : state.runtimeProfiles[fallback.id()];
        if (fallbackProfile == null) {
            fallbackProfile = AssimilationConfig.profileFor(fallback);
        }
        state.setBehaviorContext(fallback, state.runtimeProfilePositions[
                fallback == null ? SinkingMedium.ASSIMILATION_SLIME.id() : fallback.id()],
                fallbackProfile);
    }

    private static boolean hasAssimilationContact(
            ServerPlayer player, MudPlayerData mud, int tick) {
        if (mud.physicsMedium != null
                && mud.assimilationContactWeight(mud.physicsMedium, tick) > 0.0F
                && contactProfile(player, mud, mud.physicsMedium, tick).enabled()) {
            return true;
        }
        for (SinkingMedium medium : MEDIA) {
            if (contactProfile(player, mud, medium, tick).enabled()
                    && mud.assimilationContactWeight(medium, tick) > 0.0F) {
                return true;
            }
        }
        return false;
    }

    static boolean hasActiveAssimilationMudContact(ServerPlayer player) {
        MudPlayerData mud = MudStateStore.get(player);
        boolean enabledAtPhysicsContact = mud.physicsMedium != null
                && MudMediumRuntime.assimilationProfile(
                        player.level(), mud.physicsProfilePos, mud.physicsMedium).enabled();
        return blocksPartialPurge(
                mud.inMud,
                (long) player.tickCount - mud.lastMudTick,
                enabledAtPhysicsContact
                        || hasAssimilationContact(player, mud, player.tickCount));
    }

    static boolean blocksPartialPurge(
            boolean inMud, long ticksSinceMudContact, boolean assimilationEnabled) {
        return inMud && ticksSinceMudContact <= 1L && assimilationEnabled;
    }

    private static void addContactContributions(ServerPlayer player,
            AssimilationState state, MudPlayerData mud, float immersion, int tick) {
        float totalWeight = 0.0F;
        for (SinkingMedium medium : MEDIA) {
            if (contactProfile(player, mud, medium, tick).enabled()) {
                totalWeight += mud.assimilationContactWeight(medium, tick);
            }
        }
        if (totalWeight <= 0.0001F) {
            AssimilationProfile profile = contactProfile(
                    player, mud, mud.physicsMedium, tick);
            if (profile.enabled()) {
                state.templateId = AssimilationBehaviorTemplate.DEFAULT_ID;
                state.rememberRuntimeProfile(
                        mud.physicsMedium,
                        mud.assimilationContactPosition(mud.physicsMedium, tick),
                        profile);
                addVisualContribution(player, state, mud, mud.physicsMedium, tick,
                        profile.gainForImmersion(immersion));
            }
            return;
        }
        for (SinkingMedium medium : MEDIA) {
            float weight = mud.assimilationContactWeight(medium, tick);
            AssimilationProfile profile = contactProfile(player, mud, medium, tick);
            if (weight <= 0.0F || !profile.enabled()) {
                continue;
            }
            float share = weight / totalWeight;
            float gain = profile.gainForImmersion(immersion) * share;
            state.rememberRuntimeProfile(
                    medium, mud.assimilationContactPosition(medium, tick), profile);
            if (addVisualContribution(player, state, mud, medium, tick, gain)) {
                state.templateId = AssimilationBehaviorTemplate.DEFAULT_ID;
            }
        }
        if (state.runtimeProfile() == null) {
            BlockPos dominantPos = mud.assimilationContactPosition(state.medium, tick);
            state.rememberRuntimeProfile(
                    state.medium,
                    dominantPos,
                    MudMediumRuntime.assimilationProfile(
                            player.level(), dominantPos, state.medium));
        }
    }

    private static void addInventoryClotExposure(
            ServerPlayer player, AssimilationState state) {
        if (state.frozen()
                || player.tickCount % INVENTORY_CLOT_INTERVAL_TICKS != 0
                || state.progress >= INVENTORY_CLOT_PROGRESS_LIMIT) {
            return;
        }
        int count = 0;
        for (int slot = 0;
                slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(ModMudworkContent.BLOOD_CLOT_BALL.get())) {
                count += stack.getCount();
            }
        }
        ItemStack offhand = player.getOffhandItem();
        if (offhand.is(ModMudworkContent.BLOOD_CLOT_BALL.get())) {
            count += offhand.getCount();
        }
        if (count <= 0) {
            return;
        }

        float gain = INVENTORY_CLOT_GAIN_PER_SECOND
                * Mth.sqrt(Math.min(count, 64));
        gain = Math.min(gain,
                INVENTORY_CLOT_PROGRESS_LIMIT - state.progress);
        if (gain <= 0.0F) {
            return;
        }
        SinkingMedium medium = SinkingMedium.ASSIMILATION_SLIME;
        BlockPos pos = player.blockPosition();
        state.beginAssimilating(patternSeedForSession(player, state));
        state.templateId = AssimilationBehaviorTemplate.DEFAULT_ID;
        state.rememberRuntimeProfile(medium, pos,
                MudMediumRuntime.assimilationProfile(
                        player.level(), pos, medium));
        state.addContribution(medium, MudVisualSource.NONE, gain);
    }

    private static boolean addVisualContribution(ServerPlayer player,
            AssimilationState state, MudPlayerData mud, SinkingMedium medium,
            int tick, float gain) {
        if (medium == null || gain <= 0.0F) {
            return false;
        }
        MudVisualPalette contacts = mud.assimilationContactVisuals(tick);
        float matchingWeight = 0.0F;
        if (contacts != null) {
            for (int index = 0; index < contacts.size(); index++) {
                if (contacts.mediumAt(index) == medium) {
                    matchingWeight += contacts.weightAt(index);
                }
            }
        }
        boolean changed = false;
        if (matchingWeight > 0.0001F) {
            for (int index = 0; index < contacts.size(); index++) {
                if (contacts.mediumAt(index) != medium) {
                    continue;
                }
                changed |= state.addContribution(
                        medium, contacts.visualSourceAt(index),
                        gain * contacts.weightAt(index) / matchingWeight);
            }
            return changed;
        }
        BlockPos profilePos = mud.assimilationContactPosition(medium, tick);
        return state.addContribution(
                medium, MudVisualSource.capture(player.level(), profilePos), gain);
    }

    private static AssimilationProfile contactProfile(ServerPlayer player,
            MudPlayerData mud, SinkingMedium medium, int tick) {
        return medium == null
                ? AssimilationProfile.DEFAULT
                : MudMediumRuntime.assimilationProfile(
                        player.level(), mud.assimilationContactPosition(medium, tick), medium);
    }

    private static void refreshRuntimeProfiles(
            ServerPlayer player, AssimilationState state) {
        for (SinkingMedium medium : MEDIA) {
            if (state.runtimeProfiles[medium.id()] == null
                    && state.contributions[medium.id()] <= 0.0001F) {
                continue;
            }
            BlockPos pos = state.runtimeProfilePositions[medium.id()];
            if (pos != null && !player.serverLevel().getChunkSource()
                    .hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                continue;
            }
            state.rememberRuntimeProfile(
                    medium, pos,
                    MudMediumRuntime.assimilationProfile(player.level(), pos, medium));
        }
    }

    private static AssimilationState state(ServerPlayer player) {
        return AssimilationStateStore.state(player);
    }

    private static void saveIfNeeded(ServerPlayer player, AssimilationState state) {
        AssimilationStateStore.saveIfNeeded(player, state);
    }

    private static void save(ServerPlayer player, AssimilationState state) {
        AssimilationStateStore.save(player, state);
    }

    static boolean sameDimension(ServerPlayer player, AssimilationState state) {
        return dimensionId(player).equals(state.dimension);
    }

    static String dimensionId(ServerPlayer player) {
        return player.level().dimension().location().toString();
    }

}

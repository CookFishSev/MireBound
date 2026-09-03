package com.fish.mirebound.assimilation;

import com.fish.mirebound.mud.MudSurface;
import com.fish.mirebound.mud.MudSurfaceLayout;
import com.fish.mirebound.network.payload.AssimilationQteTracePayload;
import java.util.BitSet;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Owns external shell strikes, soul QTEs, and active fracture state. */
final class AssimilationRescueSystem {
    private static final int SOUL_POSITION_STALE_TICKS = 12;

    private AssimilationRescueSystem() {
    }

    static Optional<Vec3> hitPoint(ServerPlayer rescuer, Entity target) {
        Vec3 start = rescuer.getEyePosition();
        Vec3 end = start.add(rescuer.getViewVector(1.0F).scale(6.0D));
        return target.getBoundingBox().inflate(0.05D).clip(start, end);
    }

    static void rescueHit(ServerPlayer rescuer, ServerPlayer target,
            AssimilationState state, Vec3 hit) {
        if (!rescuer.getMainHandItem().is(ItemTags.PICKAXES)
                || rescuer.distanceToSqr(target) > 36.0D) {
            return;
        }
        AssimilationProfile profile = AssimilationSystem.profileFor(state);
        int cell = AssimilationCrackGeometry.cellAtHit(target, state, hit);
        BitSet opened = AssimilationCrackGeometry.openCrack(
                target, state, cell, profile.rescueRevealRadius());
        state.makeCrackExternal(opened);
        if (state.qteCell >= 0 && state.revealedCells.get(state.qteCell)) {
            clearActiveQte(state);
            state.qteCooldownTicks = profile.selfRescueQteNextDelayTicks();
        }
        state.shellIntegrity = Mth.clamp(
                state.shellIntegrity - profile.rescueDamagePerHit(), 0.0F, 1.0F);
        state.dirty = true;
        target.serverLevel().playSound(null, target.blockPosition(),
                SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS,
                0.65F, 0.62F + target.getRandom().nextFloat() * 0.16F);
        if (state.shellIntegrity <= 0.0001F) {
            state.beginRestoring(profile.restoreTicks());
        }
        AssimilationSystem.sync(target, state, profile, true);
    }

    static void handleQte(ServerPlayer player, int sequence, int cell,
            int button, int phase) {
        AssimilationState state = AssimilationStateStore.get(player);
        AssimilationProfile profile = AssimilationSystem.profileFor(state);
        if (state == null || state.stage != AssimilationStage.SEALED
                || !profile.selfRescueQteEnabled()
                || sequence != state.qteSequence || cell != state.qteCell
                || button < 1 || button > 2 || (phase != 1 && phase != 2)
                || !soulInRange(player, state, profile)) {
            return;
        }
        if (state.qteTicksRemaining <= 0 || button != state.qteButton) {
            fail(player, state, profile);
            AssimilationSystem.sync(player, state, profile, true);
            return;
        }
        if (state.qteAction == AssimilationQteAction.RAPID) {
            if (phase == 2) {
                state.qteRapidPressed = false;
                return;
            }
            if (state.qteRapidPressed) {
                return;
            }
            state.qteRapidPressed = true;
            state.qteRapidClicks++;
            state.dirty = true;
            player.serverLevel().playSound(null, player.blockPosition(),
                    SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS,
                    0.30F, 0.92F + state.qteRapidClicks * 0.08F);
            if (state.qteRapidClicks < profile.selfRescueQteRapidClicks()) {
                AssimilationSystem.sync(player, state, profile, true);
                return;
            }
        } else if (state.qteAction == AssimilationQteAction.HOLD) {
            if (phase == 1) {
                if (!state.qteHoldActive) {
                    state.qteHoldActive = true;
                    state.qteHoldTicks = 0;
                }
                return;
            }
            int minimumHold = Math.max(1, profile.selfRescueQteHoldTicks() - 1);
            if (!state.qteHoldActive || state.qteHoldTicks < minimumHold) {
                fail(player, state, profile);
                AssimilationSystem.sync(player, state, profile, true);
                return;
            }
        } else if (state.qteAction != AssimilationQteAction.CLICK || phase != 1) {
            return;
        }
        complete(player, state, profile);
    }

    static void handleTrace(ServerPlayer player, int sequence, int cell,
            int button, int action, int node) {
        AssimilationState state = AssimilationStateStore.get(player);
        AssimilationProfile profile = AssimilationSystem.profileFor(state);
        if (state == null || state.stage != AssimilationStage.SEALED
                || state.qteAction != AssimilationQteAction.TRACE
                || sequence != state.qteSequence || cell != state.qteCell
                || button < 1 || button > 2
                || action < AssimilationQteTracePayload.START
                || action > AssimilationQteTracePayload.RELEASE
                || !soulInRange(player, state, profile)) {
            return;
        }
        int[] path = AssimilationTracePattern.build(
                state.patternSeed, state.qteSequence,
                profile.selfRescueQteTraceNodes());
        if (action == AssimilationQteTracePayload.RELEASE) {
            if (state.qteTraceActive) {
                fail(player, state, profile);
                AssimilationSystem.sync(player, state, profile, true);
            }
            return;
        }
        if (button != state.qteButton || node < 0
                || node >= AssimilationTracePattern.NODE_COUNT) {
            fail(player, state, profile);
            AssimilationSystem.sync(player, state, profile, true);
            return;
        }
        if (action == AssimilationQteTracePayload.START) {
            if (state.qteTraceActive || state.qteTraceProgress != 0
                    || node != path[0]) {
                fail(player, state, profile);
                AssimilationSystem.sync(player, state, profile, true);
                return;
            }
            state.qteTraceActive = true;
            state.qteTraceProgress = 1;
        } else if (action == AssimilationQteTracePayload.NODE) {
            if (!state.qteTraceActive || state.qteTraceProgress <= 0
                    || state.qteTraceProgress >= path.length
                    || node != path[state.qteTraceProgress]) {
                fail(player, state, profile);
                AssimilationSystem.sync(player, state, profile, true);
                return;
            }
            state.qteTraceProgress++;
        } else {
            return;
        }
        state.dirty = true;
        player.serverLevel().playSound(null, player.blockPosition(),
                SoundEvents.AMETHYST_BLOCK_STEP, SoundSource.PLAYERS,
                0.24F, 0.86F + state.qteTraceProgress * 0.07F);
        if (state.qteTraceProgress >= path.length) {
            complete(player, state, profile);
        } else {
            AssimilationSystem.sync(player, state, profile, true);
        }
    }

    static boolean keepsCrackClear(Player player, int cell) {
        if (!(player instanceof ServerPlayer serverPlayer)
                || cell < 0 || cell >= MudSurfaceLayout.CELL_COUNT) {
            return false;
        }
        AssimilationState state = AssimilationStateStore.get(serverPlayer);
        return state != null && state.frozen()
                && (state.revealedCells.get(cell)
                || isActiveArea(state, cell, AssimilationSystem.profileFor(state)));
    }

    static void update(ServerPlayer player, AssimilationState state,
            AssimilationProfile profile) {
        if (state.stage != AssimilationStage.SEALED
                || !profile.selfRescueQteEnabled()) {
            if (state.qteCell >= 0 || state.qteStreak > 0
                    || state.qteCooldownTicks > 0) {
                state.clearQte();
                state.dirty = true;
            }
            return;
        }
        if (!soulInRange(player, state, profile)) {
            if (state.qteHoldActive || state.qteHoldTicks > 0
                    || state.qteRapidPressed || state.qteTraceActive) {
                state.qteHoldActive = false;
                state.qteHoldTicks = 0;
                state.qteRapidPressed = false;
                state.qteTraceActive = false;
                state.qteTraceProgress = 0;
                state.dirty = true;
            }
            return;
        }
        if (state.qteCell >= 0) {
            if (state.qteHoldActive) {
                state.qteHoldTicks++;
            }
            if (--state.qteTicksRemaining <= 0) {
                fail(player, state, profile);
            }
            return;
        }
        if (state.qteCooldownTicks > 0) {
            state.qteCooldownTicks--;
            return;
        }
        int cell = selectCell(state);
        if (cell < 0) {
            state.revealedCells.clear();
            cell = selectCell(state);
        }
        if (cell < 0) {
            return;
        }
        state.qteSequence++;
        state.qteCell = cell;
        state.qteButton = 1 + (mix(
                state.patternSeed ^ state.qteSequence * 0x632BE5AB) & 1);
        long actionRoll = Integer.toUnsignedLong(mix(
                state.patternSeed ^ state.qteSequence * 0x27D4EB2D));
        double actionSample = actionRoll / 4294967296.0D;
        double rapidChance = profile.selfRescueQteRapidChance();
        double traceLimit = Math.min(
                1.0D, rapidChance + profile.selfRescueQteTraceChance());
        double holdLimit = Math.min(
                1.0D, traceLimit + profile.selfRescueQteHoldChance());
        state.qteAction = actionSample < rapidChance
                ? AssimilationQteAction.RAPID
                : actionSample < traceLimit ? AssimilationQteAction.TRACE
                : actionSample < holdLimit ? AssimilationQteAction.HOLD
                : AssimilationQteAction.CLICK;
        state.qteHoldActive = false;
        state.qteHoldTicks = 0;
        state.qteRapidClicks = 0;
        state.qteRapidPressed = false;
        state.qteTraceProgress = 0;
        state.qteTraceActive = false;
        state.qteTicksRemaining = state.qteAction == AssimilationQteAction.TRACE
                ? profile.selfRescueQteTraceTimeoutTicks()
                : profile.selfRescueQteTimeoutTicks();
        state.dirty = true;
        AssimilationCrackGeometry.clearOrdinaryCoverage(
                player, AssimilationCrackGeometry.cellsAround(
                        cell, profile.selfRescueQteRevealRadius()));
        player.serverLevel().playSound(null, player.blockPosition(),
                SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS,
                0.45F, 0.72F + player.getRandom().nextFloat() * 0.12F);
    }

    static void updateSoulPosition(
            ServerPlayer player, float x, float y, float z) {
        AssimilationState state = AssimilationStateStore.get(player);
        if (state == null || !state.frozen()
                || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
            return;
        }
        Vec3 reported = new Vec3(x, y, z);
        double safetyRadius = AssimilationSystem.profileFor(state).soulRadius() + 1.0D;
        Vec3 offset = reported.subtract(state.anchor);
        if (Math.max(Math.max(Math.abs(offset.x), Math.abs(offset.y)),
                Math.abs(offset.z)) > safetyRadius) {
            return;
        }
        state.soulPosition = reported;
        state.soulPositionTick = player.tickCount;
    }

    static int patternSeedForSession(
            ServerPlayer player, AssimilationState state) {
        if (state.patternSeed != 0) {
            return state.patternSeed;
        }
        int seed = player.getRandom().nextInt();
        if (seed != 0) {
            return seed;
        }
        seed = mix(player.getUUID().hashCode() ^ player.tickCount ^ 0x4F1BBCDD);
        return seed == 0 ? 0x4F1BBCDD : seed;
    }

    private static void complete(ServerPlayer player, AssimilationState state,
            AssimilationProfile profile) {
        BitSet opened = AssimilationCrackGeometry.cellsAround(
                state.qteCell, profile.selfRescueQteRevealRadius());
        opened.andNot(state.revealedCells);
        state.applySelfRescueSuccess(opened, profile);
        AssimilationCrackGeometry.clearOrdinaryCoverage(player, opened);
        clearActiveQte(state);
        state.qteCooldownTicks = profile.selfRescueQteNextDelayTicks();
        state.dirty = true;
        player.serverLevel().playSound(null, player.blockPosition(),
                SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS,
                0.52F, 1.18F + player.getRandom().nextFloat() * 0.16F);
        if (state.qteStreak >= profile.selfRescueQteRequiredStreak()
                || state.shellIntegrity <= 0.0001F) {
            state.beginRestoring(profile.restoreTicks());
        }
        AssimilationSystem.sync(player, state, profile, true);
    }

    private static void fail(ServerPlayer player, AssimilationState state,
            AssimilationProfile profile) {
        state.rollbackSelfRescue(profile);
        clearActiveQte(state);
        state.qteStreak = 0;
        state.qteCooldownTicks = profile.selfRescueQteFailureDelayTicks();
        state.dirty = true;
        player.serverLevel().playSound(null, player.blockPosition(),
                SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS,
                0.18F, 0.55F);
    }

    private static void clearActiveQte(AssimilationState state) {
        state.qteCell = -1;
        state.qteButton = 0;
        state.qteAction = AssimilationQteAction.NONE;
        state.qteHoldActive = false;
        state.qteHoldTicks = 0;
        state.qteRapidClicks = 0;
        state.qteRapidPressed = false;
        state.qteTraceProgress = 0;
        state.qteTraceActive = false;
        state.qteTicksRemaining = 0;
    }

    private static int selectCell(AssimilationState state) {
        int start = Math.floorMod(mix(
                state.patternSeed + state.qteSequence * 0x9E3779B9),
                MudSurfaceLayout.CELL_COUNT);
        int stride = 811;
        for (int offset = 0; offset < MudSurfaceLayout.CELL_COUNT; offset++) {
            int cell = (start + offset * stride) % MudSurfaceLayout.CELL_COUNT;
            MudSurface surface = MudSurfaceLayout.surface(cell);
            if (surface != MudSurface.TOP && surface != MudSurface.BOTTOM
                    && !state.revealedCells.get(cell)) {
                return cell;
            }
        }
        return -1;
    }

    private static boolean soulInRange(ServerPlayer player, AssimilationState state,
            AssimilationProfile profile) {
        int age = player.tickCount - state.soulPositionTick;
        if (state.soulPositionTick == Integer.MIN_VALUE
                || age < 0 || age > SOUL_POSITION_STALE_TICKS) {
            return false;
        }
        Vec3 bodyCenter = state.anchor.add(
                0.0D, player.getEyeHeight() * 0.5D, 0.0D);
        double range = profile.selfRescueQteRange();
        return state.soulPosition.distanceToSqr(bodyCenter) <= range * range;
    }

    private static boolean isActiveArea(AssimilationState state, int cell,
            AssimilationProfile profile) {
        int target = state.qteCell;
        if (target < 0
                || MudSurfaceLayout.part(target) != MudSurfaceLayout.part(cell)
                || MudSurfaceLayout.surface(target)
                        != MudSurfaceLayout.surface(cell)) {
            return false;
        }
        int distance = Math.abs(
                MudSurfaceLayout.row(target) - MudSurfaceLayout.row(cell))
                + Math.abs(MudSurfaceLayout.column(target)
                        - MudSurfaceLayout.column(cell));
        return distance <= profile.selfRescueQteRevealRadius();
    }

    private static int mix(int value) {
        value ^= value >>> 16;
        value *= 0x7FEB352D;
        value ^= value >>> 15;
        value *= 0x846CA68B;
        return value ^ value >>> 16;
    }
}

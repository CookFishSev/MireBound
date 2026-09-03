package com.fish.mirebound.client;

import com.fish.mirebound.mud.MudMediumRuntime;
import com.fish.mirebound.mud.MudBehaviorContext;
import com.fish.mirebound.mud.MudPhysicsParameter;
import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.registry.ModBlocks;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Owns the fixed bubble pool, delayed probes, ambience, and bubble sounds. */
final class MudSurfaceBubbleSystem {
    private static final int MAX_PENDING_PROBES = 96;
    private static final List<PendingProbe> PENDING = new ArrayList<>();
    private static MudSurfaceEffectManager.Bubble[] bubbles = createPool(160);

    private MudSurfaceBubbleSystem() {
    }

    static MudSurfaceEffectManager.Bubble[] bubbles() {
        return bubbles;
    }

    static void reset() {
        PENDING.clear();
        for (MudSurfaceEffectManager.Bubble bubble : bubbles) {
            bubble.active = false;
        }
    }

    static void resizePool() {
        int requested = MudSurfaceClientSettings.maxBubbles();
        if (requested == bubbles.length) {
            return;
        }
        MudSurfaceEffectManager.Bubble[] replacement = createPool(requested);
        int target = 0;
        for (MudSurfaceEffectManager.Bubble bubble : bubbles) {
            if (bubble.active && target < replacement.length) {
                replacement[target++].copyFrom(bubble);
            }
        }
        bubbles = replacement;
    }

    static void tick(Minecraft minecraft, ClientLevel level) {
        tickBubbles(level);
        tickPending(level);
        tickAmbient(minecraft, level);
    }

    static void spawnProbe(ClientLevel level, Vec3 point, Vec3 normal,
            SinkingMedium medium) {
        Vec3 safeNormal = MudSurfaceEffectManager.safeNormal(normal);
        Vec3 tangent = Math.abs(safeNormal.y) < 0.85D
                ? new Vec3(0.0D, 1.0D, 0.0D)
                : new Vec3(1.0D, 0.0D, 0.0D);
        spawnProbe(level, point, safeNormal, tangent, medium,
                MudSurfaceEffectManager.supportPos(point, safeNormal));
    }

    static void spawnProbe(ClientLevel level, Vec3 point, Vec3 normal,
            Vec3 preferredTangent, SinkingMedium medium) {
        Vec3 safeNormal = MudSurfaceEffectManager.safeNormal(normal);
        spawnProbe(level, point, safeNormal, preferredTangent, medium,
                MudSurfaceEffectManager.supportPos(point, safeNormal));
    }

    static void spawnProbe(ClientLevel level, Vec3 point, Vec3 normal,
            Vec3 preferredTangent, SinkingMedium medium, BlockPos profilePos) {
        Vec3 safeNormal = MudSurfaceEffectManager.safeNormal(normal);
        spawn(level, point, safeNormal, preferredTangent, medium, profilePos, false);
    }

    static void schedule(ClientLevel level, Vec3 point, Vec3 normal,
            SinkingMedium medium, int delayTicks) {
        Vec3 safeNormal = MudSurfaceEffectManager.safeNormal(normal);
        Vec3 tangent = Math.abs(safeNormal.y) < 0.85D
                ? new Vec3(0.0D, 1.0D, 0.0D)
                : new Vec3(1.0D, 0.0D, 0.0D);
        schedule(level, point, safeNormal, tangent, medium,
                MudSurfaceEffectManager.supportPos(point, safeNormal), delayTicks);
    }

    static void schedule(ClientLevel level, Vec3 point, Vec3 normal,
            Vec3 preferredTangent, SinkingMedium medium, int delayTicks) {
        Vec3 safeNormal = MudSurfaceEffectManager.safeNormal(normal);
        schedule(level, point, safeNormal, preferredTangent, medium,
                MudSurfaceEffectManager.supportPos(point, safeNormal), delayTicks);
    }

    static void schedule(ClientLevel level, Vec3 point, Vec3 normal,
            Vec3 preferredTangent, SinkingMedium medium,
            BlockPos profilePos, int delayTicks) {
        if (delayTicks <= 0) {
            spawnProbe(level, point, normal, preferredTangent, medium, profilePos);
            return;
        }
        if (PENDING.size() >= MAX_PENDING_PROBES) {
            return;
        }
        Vec3 safeNormal = MudSurfaceEffectManager.safeNormal(normal);
        PENDING.add(new PendingProbe(
                point, safeNormal, preferredTangent, medium,
                profilePos == null
                        ? MudSurfaceEffectManager.supportPos(point, safeNormal) : profilePos,
                delayTicks));
    }

    static void spawnForHole(
            ClientLevel level, MudSurfaceEffectManager.Hole hole) {
        double angle = MudSurfaceEffectManager.nextUnit() * Math.PI * 2.0D;
        double distance = Math.sqrt(MudSurfaceEffectManager.nextUnit())
                * hole.radius * 1.20D;
        Vec3 center = hole.center
                .add(hole.axisX.scale(Math.cos(angle) * distance))
                .add(hole.axisZ.scale(Math.sin(angle) * distance));
        center = MudSurfaceEffectManager.alignedCenter(center);
        Vec3 normal = hole.normal;
        Vec3 tangent = hole.axisX;
        BlockPos supportPos = BlockPos.containing(
                center.x, hole.center.y - 0.025D, center.z);
        MudSurfaceEffectManager.VisualSurface visual =
                MudSurfaceEffectManager.visualSurfaceAt(
                        level, supportPos, hole.medium, center.x, center.z);
        if (visual == null && !supportPos.equals(hole.profilePos)) {
            visual = MudSurfaceEffectManager.visualSurfaceAt(
                    level, hole.profilePos, hole.medium, center.x, center.z);
        }
        if (visual != null) {
            center = visual.point();
            normal = visual.hit().normal();
            tangent = visual.hit().axisX();
        }
        center = center.add(normal.scale(0.004D));
        if (!MudSurfaceEffectManager.surfaceMatches(level, center, hole.medium)) {
            return;
        }
        spawn(level, center, normal, tangent,
                hole.medium, hole.profilePos, false);
    }

    private static void tickAmbient(Minecraft minecraft, ClientLevel level) {
        int probes = MudSurfaceClientSettings.ambientProbes();
        int interval = MudSurfaceClientSettings.ambientIntervalTicks();
        if (probes <= 0 || minecraft.player == null
                || Math.floorMod(level.getGameTime(), interval) != 0) {
            return;
        }
        BlockPos origin = minecraft.player.blockPosition();
        for (int probe = 0; probe < probes; probe++) {
            int x = origin.getX()
                    + (int) Math.floor(MudSurfaceEffectManager.nextUnit() * 25.0D) - 12;
            int z = origin.getZ()
                    + (int) Math.floor(MudSurfaceEffectManager.nextUnit() * 25.0D) - 12;
            int startY = origin.getY() + 4
                    - (int) Math.floor(MudSurfaceEffectManager.nextUnit() * 5.0D);
            for (int y = startY; y >= origin.getY() - 5; y--) {
                BlockPos pos = new BlockPos(x, y, z);
                BlockState state = level.getBlockState(pos);
                SinkingMedium medium = ModBlocks.mediumOf(state.getBlock());
                if (medium == null
                        || !MudSurfaceEffectManager.enabled(pos, medium)) {
                    continue;
                }
                if (MudBehaviorContext.tenderFlesh(level, pos, medium)) {
                    break;
                }
                if (ModBlocks.mediumOf(
                        level.getBlockState(pos.above()).getBlock()) != null) {
                    break;
                }
                double chance = MudSurfaceEffectManager.value(
                        pos, medium, MudPhysicsParameter.SURFACE_BUBBLE_RATE)
                        * interval * 0.18D;
                if (MudSurfaceEffectManager.nextUnit() < chance) {
                    double worldX = pos.getX() + 0.12D
                            + MudSurfaceEffectManager.nextUnit() * 0.76D;
                    double worldZ = pos.getZ() + 0.12D
                            + MudSurfaceEffectManager.nextUnit() * 0.76D;
                    MudSurfaceEffectManager.VisualSurface visual =
                            MudSurfaceEffectManager.visualSurfaceAt(
                                    level, pos, medium, worldX, worldZ);
                    double localSurfaceHeight = visual == null
                            ? MudSurfaceEffectManager.visualSurfaceHeightAt(
                                    level, pos, state, medium, worldX, worldZ)
                            : visual.point().y - pos.getY();
                    if (!Double.isFinite(localSurfaceHeight)) {
                        break;
                    }
                    Vec3 point = new Vec3(
                            worldX,
                            pos.getY() + localSurfaceHeight + 0.004D,
                            worldZ);
                    spawn(level, point,
                            visual == null
                                    ? new Vec3(0.0D, 1.0D, 0.0D)
                                    : visual.hit().normal(),
                            visual == null
                                    ? new Vec3(1.0D, 0.0D, 0.0D)
                                    : visual.hit().axisX(),
                            medium, pos, true);
                }
                break;
            }
        }
    }

    private static void spawn(ClientLevel level, Vec3 center, Vec3 normal,
            Vec3 preferredTangent, SinkingMedium medium,
            BlockPos profilePos, boolean ambient) {
        if (MudBehaviorContext.tenderFlesh(level, profilePos, medium)) {
            return;
        }
        MudSurfaceEffectManager.Bubble bubble = freeBubble();
        if (bubble == null) {
            return;
        }
        double minimum = MudSurfaceEffectManager.value(
                profilePos, medium,
                MudPhysicsParameter.SURFACE_BUBBLE_MIN_PIXELS) / 16.0D;
        double maximum = MudSurfaceEffectManager.value(
                profilePos, medium,
                MudPhysicsParameter.SURFACE_BUBBLE_MAX_PIXELS) / 16.0D;
        maximum = Math.max(maximum, minimum);
        bubble.active = true;
        bubble.normal = MudSurfaceEffectManager.safeNormal(normal);
        bubble.center = MudSurfaceEffectManager.alignedSurfaceCenter(
                center, bubble.normal);
        Vec3[] basis = MudSurfaceEffectManager.basis(
                bubble.normal, preferredTangent);
        bubble.tangent = basis[0];
        bubble.bitangent = basis[1];
        bubble.medium = medium;
        bubble.profilePos = profilePos;
        bubble.radius = Mth.lerp(
                MudSurfaceEffectManager.nextUnit(), minimum, maximum);
        bubble.lifeTicks = 34 + (int) Math.floor(
                MudSurfaceEffectManager.nextUnit() * (ambient ? 38.0D : 24.0D));
        bubble.ageTicks = 0;
        bubble.seed = MudSurfaceEffectManager.nextLong();
        bubble.ambient = ambient;
        bubble.soundPlayed = false;
    }

    private static void tickBubbles(ClientLevel level) {
        for (MudSurfaceEffectManager.Bubble bubble : bubbles) {
            if (!bubble.active) {
                continue;
            }
            bubble.ageTicks++;
            if (!bubble.soundPlayed
                    && bubble.ageTicks >= Math.ceil(bubble.lifeTicks * 0.88D)) {
                playSound(level, bubble);
                bubble.soundPlayed = true;
            }
            if (bubble.ageTicks >= bubble.lifeTicks) {
                bubble.active = false;
            }
        }
    }

    private static void tickPending(ClientLevel level) {
        Iterator<PendingProbe> iterator = PENDING.iterator();
        while (iterator.hasNext()) {
            PendingProbe pending = iterator.next();
            pending.delayTicks--;
            if (pending.delayTicks > 0) {
                continue;
            }
            spawn(level, pending.point, pending.normal, pending.tangent,
                    pending.medium, pending.profilePos, false);
            iterator.remove();
        }
    }

    private static void playSound(
            ClientLevel level, MudSurfaceEffectManager.Bubble bubble) {
        if (level == null) {
            return;
        }
        float volume = (float) MudSurfaceEffectManager.value(
                bubble.profilePos, bubble.medium,
                MudPhysicsParameter.SURFACE_BUBBLE_SOUND_VOLUME);
        if (volume <= 0.001F || bubble.ambient && (bubble.seed & 3L) != 0L) {
            return;
        }
        float pitch = (float) MudSurfaceEffectManager.value(
                bubble.profilePos, bubble.medium,
                MudPhysicsParameter.SURFACE_BUBBLE_SOUND_PITCH);
        float variation = 0.88F
                + (float) (((bubble.seed >>> 11) & 255L) / 255.0D) * 0.24F;
        float sizePitch = (float) Mth.clamp(
                0.11D / Math.max(0.035D, bubble.radius), 0.58D, 1.35D);
        float finalPitch = pitch * variation * sizePitch;
        level.playLocalSound(
                bubble.center.x, bubble.center.y, bubble.center.z,
                switch (bubble.medium) {
                    case LIVING_SLIME -> SoundEvents.SLIME_SQUISH_SMALL;
                    case TENDER_FLESH -> SoundEvents.HONEY_BLOCK_PLACE;
                    default -> SoundEvents.LAVA_POP;
                },
                SoundSource.BLOCKS,
                volume * (bubble.ambient ? 0.60F : 1.0F),
                finalPitch, false);
        if (bubble.medium != SinkingMedium.LIVING_SLIME) {
            level.playLocalSound(
                    bubble.center.x, bubble.center.y, bubble.center.z,
                    SoundEvents.SLIME_SQUISH_SMALL, SoundSource.BLOCKS,
                    volume * (bubble.ambient ? 0.10F : 0.18F),
                    finalPitch * 1.18F, false);
        }
    }

    private static MudSurfaceEffectManager.Bubble freeBubble() {
        int limit = Math.min(bubbles.length, MudSurfaceClientSettings.maxBubbles());
        for (int index = 0; index < limit; index++) {
            if (!bubbles[index].active) {
                return bubbles[index];
            }
        }
        return null;
    }

    private static MudSurfaceEffectManager.Bubble[] createPool(int size) {
        MudSurfaceEffectManager.Bubble[] result =
                new MudSurfaceEffectManager.Bubble[Math.max(0, size)];
        for (int index = 0; index < result.length; index++) {
            result[index] = new MudSurfaceEffectManager.Bubble();
        }
        return result;
    }

    private static final class PendingProbe {
        private final Vec3 point;
        private final Vec3 normal;
        private final Vec3 tangent;
        private final SinkingMedium medium;
        private final BlockPos profilePos;
        private int delayTicks;

        private PendingProbe(Vec3 point, Vec3 normal, Vec3 tangent,
                SinkingMedium medium, BlockPos profilePos, int delayTicks) {
            this.point = point;
            this.normal = normal;
            this.tangent = tangent;
            this.medium = medium;
            this.profilePos = profilePos;
            this.delayTicks = delayTicks;
        }
    }
}

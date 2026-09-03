package com.fish.mirebound.client;

import com.fish.mirebound.compat.sable.SableCompat;
import com.fish.mirebound.client.config.MireboundClientSettings;
import com.fish.mirebound.client.config.MireboundClientSettings.ClientOption;
import com.fish.mirebound.splash.MudSplashCollision;
import com.fish.mirebound.splash.MudFountainMotion;
import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.network.payload.MudSplashPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Fixed-array client simulation; droplets are render data, never Minecraft entities. */
final class MudSplashClientManager {
    private static final int HARD_CAPACITY = 1024;
    private static Droplet[] droplets = createPool(192);
    private static ClientLevel level;

    private MudSplashClientManager() {
    }

    static void accept(MudSplashPayload payload) {
        if (!MireboundClientSettings.clientOptionEnabled(
                ClientOption.SPLASH_EFFECTS)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        if (level != minecraft.level) {
            reset();
            level = minecraft.level;
        }
        ensureCapacity(activeCount() + payload.droplets().size());
        Vec3 origin = payload.origin();
        int index = 0;
        for (MudSplashPayload.Droplet source : payload.droplets()) {
            Droplet target = acquire();
            if (target == null) {
                break;
            }
            target.active = true;
            target.sourceEntityId = payload.sourceEntityId();
            target.position = origin;
            target.previousPosition = origin;
            target.velocity = source.velocity();
            target.fountain = source.fountain();
            target.breakupTriggerVelocityY = source.breakupTriggerVelocityY();
            target.breakupPending = target.fountain;
            target.breakupDurationTicks = source.breakupDurationTicks();
            target.breakupTicksRemaining = 0;
            target.columnTrailTicks = source.columnTrailTicks();
            target.medium = payload.medium();
            target.visualSource = payload.visualSource();
            target.gravity = payload.gravity();
            target.drag = payload.drag();
            target.lifeTicks = payload.lifetimeTicks();
            target.ageTicks = 0;
            target.size = source.size();
            target.seed = mix(payload.seed() + index++ * 0x9e3779b97f4a7c15L);
            target.trail.reset(origin);
        }
    }

    static void tick() {
        if (!MireboundClientSettings.clientOptionEnabled(
                ClientOption.SPLASH_EFFECTS)) {
            if (level != null) {
                reset();
            }
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            reset();
            return;
        }
        if (level != minecraft.level) {
            reset();
            level = minecraft.level;
        }
        AABB activeBounds = activeBounds();
        SableCompat.SurfaceProbe sableProbe = activeBounds != null && SableCompat.isLoaded()
                ? SableCompat.surfaceProbe(minecraft.level, activeBounds.inflate(0.28D))
                : null;
        for (Droplet droplet : droplets) {
            if (!droplet.active) {
                continue;
            }
            if (MudFountainMotion.shouldBreakUp(
                    droplet.breakupPending,
                    droplet.velocity.y,
                    droplet.breakupTriggerVelocityY)) {
                droplet.breakupPending = false;
                droplet.breakupTicksRemaining = droplet.breakupDurationTicks;
            }
            if (droplet.breakupTicksRemaining > 0) {
                droplet.breakupTicksRemaining--;
            }
            droplet.previousPosition = droplet.position;
            Vec3 next = droplet.position.add(droplet.velocity);
            if (hitsPlayer(minecraft.level, droplet, droplet.position, next)) {
                droplet.active = false;
                continue;
            }
            if (minecraft.player != null
                    && minecraft.level.clip(new ClipContext(
                            droplet.position, next,
                            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                            minecraft.player)).getType() == HitResult.Type.BLOCK
                    || hitsSable(sableProbe, droplet.position, next, droplet.velocity)) {
                droplet.active = false;
                continue;
            }
            droplet.position = next;
            if (droplet.fountain) {
                droplet.trail.record(next);
            }
            droplet.velocity = droplet.velocity
                    .add(0.0D, -droplet.gravity, 0.0D)
                    .scale(droplet.drag);
            droplet.ageTicks++;
            if (droplet.ageTicks >= droplet.lifeTicks
                    || droplet.position.distanceToSqr(droplet.previousPosition) > 9.0D) {
                droplet.active = false;
            }
        }
    }

    static Droplet[] droplets() {
        return droplets;
    }

    static void reset() {
        for (Droplet droplet : droplets) {
            droplet.active = false;
        }
        level = null;
    }

    private static Droplet acquire() {
        for (Droplet droplet : droplets) {
            if (!droplet.active) {
                return droplet;
            }
        }
        return null;
    }

    private static int activeCount() {
        int active = 0;
        for (Droplet droplet : droplets) {
            if (droplet.active) {
                active++;
            }
        }
        return active;
    }

    private static AABB activeBounds() {
        AABB bounds = null;
        for (Droplet droplet : droplets) {
            if (!droplet.active) {
                continue;
            }
            AABB segment = new AABB(
                    droplet.position,
                    droplet.position.add(droplet.velocity)).inflate(0.08D);
            bounds = bounds == null ? segment : bounds.minmax(segment);
        }
        return bounds;
    }

    private static boolean hitsSable(SableCompat.SurfaceProbe probe,
            Vec3 from, Vec3 to, Vec3 velocity) {
        if (probe == null || probe.isEmpty() || velocity.lengthSqr() < 1.0E-8D) {
            return false;
        }
        double distance = from.distanceTo(to);
        int steps = Mth.clamp((int) Math.ceil(distance / 0.18D), 1, 16);
        Vec3 preferredNormal = velocity.normalize().scale(-1.0D);
        for (int step = 1; step <= steps; step++) {
            Vec3 sample = from.lerp(to, step / (double) steps);
            if (SableCompat.findSurface(
                    probe, sample, preferredNormal, 0.126D, 0.02D) != null) {
                return true;
            }
        }
        return false;
    }

    private static boolean hitsPlayer(ClientLevel level, Droplet droplet, Vec3 from, Vec3 to) {
        MudSplashCollision.SweptHit closest = null;
        for (AbstractClientPlayer player : level.players()) {
            if (!player.isAlive() || player.isSpectator()
                    || player.getId() == droplet.sourceEntityId) {
                continue;
            }
            Vec3 motion = new Vec3(
                    player.getX() - player.xo,
                    player.getY() - player.yo,
                    player.getZ() - player.zo);
            MudSplashCollision.SweptHit hit = MudSplashCollision.sweepPlayer(
                    player.getBoundingBox(), motion, from, to);
            if (hit != null && (closest == null || hit.time() < closest.time())) {
                closest = hit;
            }
        }
        return closest != null;
    }

    private static void ensureCapacity(int requested) {
        if (requested <= droplets.length || droplets.length >= HARD_CAPACITY) {
            return;
        }
        int next = Math.min(HARD_CAPACITY, Math.max(requested, droplets.length * 2));
        Droplet[] resized = createPool(next);
        System.arraycopy(droplets, 0, resized, 0, droplets.length);
        droplets = resized;
    }

    private static Droplet[] createPool(int size) {
        Droplet[] result = new Droplet[size];
        for (int index = 0; index < size; index++) {
            result[index] = new Droplet();
        }
        return result;
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27;
        value *= 0x94d049bb133111ebL;
        return value ^ value >>> 31;
    }

    static final class Droplet {
        boolean active;
        int sourceEntityId;
        Vec3 position = Vec3.ZERO;
        Vec3 previousPosition = Vec3.ZERO;
        Vec3 velocity = Vec3.ZERO;
        boolean fountain;
        float breakupTriggerVelocityY;
        boolean breakupPending;
        int breakupDurationTicks = 1;
        int breakupTicksRemaining;
        int columnTrailTicks = 1;
        SinkingMedium medium = SinkingMedium.MUD;
        long visualSource;
        float gravity;
        float drag;
        float size;
        int ageTicks;
        int lifeTicks;
        long seed;
        final MudSplashTrail trail = new MudSplashTrail();

        boolean columnActive() {
            return fountain && (breakupPending || breakupTicksRemaining > 0);
        }

        double columnTrailFactor() {
            if (!fountain) {
                return 0.0D;
            }
            if (breakupPending) {
                return 1.0D;
            }
            return Math.max(0.0D, Math.min(1.0D,
                    breakupTicksRemaining / (double) Math.max(1, breakupDurationTicks)));
        }
    }
}

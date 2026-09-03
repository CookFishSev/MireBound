package com.fish.mirebound.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Emits bounded water particles directly along the shared ballistic path. */
final class WaterGunStreamRenderer {
    private static final int MAXIMUM_PATH_PARTICLES = 40;
    private static final int MAXIMUM_PARTICLES_PER_STREAM = 52;
    private static final int MAXIMUM_TOTAL_PARTICLES_PER_TICK = 192;
    private static final double PATH_PARTICLE_SPACING = 0.44D;
    private static final double NOZZLE_BLEND_DISTANCE = 1.25D;
    private static final double MAXIMUM_RENDER_DISTANCE_SQR = 128.0D * 128.0D;

    private WaterGunStreamRenderer() {
    }

    static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        long gameTick = minecraft.level.getGameTime();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        var prepared = new ArrayList<PreparedStream>();
        for (WaterGunStreamClientManager.Stream stream : WaterGunStreamClientManager.streams()) {
            if (!stream.beginParticleTick(gameTick)) {
                continue;
            }
            List<Vec3> points = stream.interpolated(partialTick);
            if (points.size() < 2
                    || points.getFirst().distanceToSqr(camera) > MAXIMUM_RENDER_DISTANCE_SQR) {
                continue;
            }
            if (minecraft.level.getEntity(stream.shooterId) instanceof Player player) {
                Vec3 nozzle = WaterGunNozzleFocus.resolve(
                        player, partialTick, event.getModelViewMatrix(), event.getProjectionMatrix());
                if (nozzle != null
                        && nozzle.distanceToSqr(player.getEyePosition(partialTick)) <= 9.0D) {
                    points = alignNearNozzle(points, nozzle);
                }
            }
            prepared.add(new PreparedStream(
                    stream, points, points.getFirst().distanceToSqr(camera)));
        }
        prepared.sort(java.util.Comparator.comparingDouble(PreparedStream::distanceSqr));
        int remainingBudget = MAXIMUM_TOTAL_PARTICLES_PER_TICK;
        for (PreparedStream candidate : prepared) {
            if (remainingBudget <= 0) {
                break;
            }
            remainingBudget -= emitPathParticles(
                    minecraft, candidate.stream(), candidate.points(), gameTick,
                    Math.min(MAXIMUM_PARTICLES_PER_STREAM, remainingBudget));
        }
    }

    static List<Vec3> alignNearNozzle(List<Vec3> points, Vec3 nozzle) {
        if (points.size() < 2 || nozzle.distanceToSqr(points.getFirst()) <= 1.0E-8D) {
            return points;
        }
        double pathLength = pathLength(points);
        if (pathLength <= 1.0E-6D) {
            return points;
        }
        double blendDistance = Math.min(NOZZLE_BLEND_DISTANCE, pathLength * 0.65D);
        Vec3 offset = nozzle.subtract(points.getFirst());
        var aligned = new ArrayList<Vec3>(points.size());
        double traveled = 0.0D;
        aligned.add(nozzle);
        for (int index = 1; index < points.size(); index++) {
            traveled += points.get(index - 1).distanceTo(points.get(index));
            double progress = Math.min(1.0D, traveled / blendDistance);
            double smooth = progress * progress * (3.0D - 2.0D * progress);
            aligned.add(points.get(index).add(offset.scale(1.0D - smooth)));
        }
        return List.copyOf(aligned);
    }

    private static int emitPathParticles(
            Minecraft minecraft, WaterGunStreamClientManager.Stream stream,
            List<Vec3> points, long gameTick, int particleBudget) {
        double length = pathLength(points);
        if (length <= 1.0E-6D || particleBudget <= 0) {
            return 0;
        }
        RandomSource random = minecraft.level.random;
        Vec3 muzzleDirection = points.get(1).subtract(points.getFirst()).normalize();
        int emitted = emitNozzleParticles(
                minecraft, points.getFirst(), muzzleDirection,
                stream.consumeBurst(), random, particleBudget);

        int pathBudget = Math.min(MAXIMUM_PATH_PARTICLES, particleBudget - emitted);
        if (pathBudget <= 0) {
            return emitted;
        }
        double spacing = Math.max(PATH_PARTICLE_SPACING,
                length / pathBudget);
        long phaseSeed = gameTick * 31L + stream.shooterId * 17L;
        double nextDistance = Math.floorMod(phaseSeed, 16L) / 16.0D * spacing;
        double segmentStart = 0.0D;
        int segmentIndex = 0;
        int pathParticles = 0;
        while (nextDistance <= length && pathParticles < pathBudget) {
            while (segmentIndex < points.size() - 1) {
                double segmentLength = points.get(segmentIndex)
                        .distanceTo(points.get(segmentIndex + 1));
                if (nextDistance <= segmentStart + segmentLength) {
                    if (segmentLength > 1.0E-8D) {
                        double local = (nextDistance - segmentStart) / segmentLength;
                        Vec3 from = points.get(segmentIndex);
                        Vec3 to = points.get(segmentIndex + 1);
                        Vec3 position = from.lerp(to, local);
                        emitPathParticle(minecraft, position, to.subtract(from).normalize(), random);
                        pathParticles++;
                    }
                    break;
                }
                segmentStart += segmentLength;
                segmentIndex++;
            }
            if (segmentIndex >= points.size() - 1) {
                break;
            }
            nextDistance += spacing;
        }
        emitted += pathParticles;

        if (stream.impact && emitted < particleBudget) {
            Vec3 incoming = points.getLast().subtract(points.get(points.size() - 2)).normalize();
            emitted += emitImpactParticles(
                    minecraft, points.getLast(), incoming, random,
                    Math.min(3, particleBudget - emitted));
        }
        return emitted;
    }

    private static int emitNozzleParticles(
            Minecraft minecraft, Vec3 nozzle, Vec3 direction,
            boolean burst, RandomSource random, int budget) {
        int count = Math.min(burst ? 9 : 3, budget);
        for (int index = 0; index < count; index++) {
            double spread = burst ? 0.055D : 0.028D;
            Vec3 position = nozzle.add(randomVector(random, spread));
            Vec3 velocity = direction.scale(burst ? 0.16D : 0.10D)
                    .add(randomVector(random, burst ? 0.075D : 0.035D));
            Particle particle = minecraft.particleEngine.createParticle(
                    ParticleTypes.SPLASH,
                    position.x, position.y, position.z,
                    velocity.x, velocity.y, velocity.z);
            configureParticle(particle, burst ? 7 + random.nextInt(4) : 4 + random.nextInt(3),
                    burst ? 0.85F : 0.62F);
        }
        return count;
    }

    private static void emitPathParticle(
            Minecraft minecraft, Vec3 position, Vec3 direction, RandomSource random) {
        Vec3 velocity = direction.scale(0.055D).add(randomVector(random, 0.012D));
        Particle particle = minecraft.particleEngine.createParticle(
                ParticleTypes.RAIN,
                position.x, position.y, position.z,
                velocity.x, velocity.y, velocity.z);
        configureParticle(particle, 4 + random.nextInt(3), 0.58F + random.nextFloat() * 0.18F);
    }

    private static int emitImpactParticles(
            Minecraft minecraft, Vec3 impact, Vec3 incoming, RandomSource random, int count) {
        for (int index = 0; index < count; index++) {
            Vec3 velocity = incoming.scale(-0.035D)
                    .add(randomVector(random, 0.09D))
                    .add(0.0D, 0.045D, 0.0D);
            Particle particle = minecraft.particleEngine.createParticle(
                    ParticleTypes.SPLASH,
                    impact.x, impact.y, impact.z,
                    velocity.x, velocity.y, velocity.z);
            configureParticle(particle, 6 + random.nextInt(4), 0.72F);
        }
        return count;
    }

    private static void configureParticle(Particle particle, int lifetime, float scale) {
        if (particle == null) {
            return;
        }
        particle.setLifetime(lifetime);
        particle.scale(scale);
        particle.setColor(0.36F, 0.70F, 1.0F);
    }

    private static Vec3 randomVector(RandomSource random, double scale) {
        return new Vec3(
                (random.nextDouble() * 2.0D - 1.0D) * scale,
                (random.nextDouble() * 2.0D - 1.0D) * scale,
                (random.nextDouble() * 2.0D - 1.0D) * scale);
    }

    private static double pathLength(List<Vec3> points) {
        double length = 0.0D;
        for (int index = 1; index < points.size(); index++) {
            length += points.get(index - 1).distanceTo(points.get(index));
        }
        return length;
    }

    private record PreparedStream(
            WaterGunStreamClientManager.Stream stream,
            List<Vec3> points,
            double distanceSqr) {
    }
}

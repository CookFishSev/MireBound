package com.fish.mirebound.client;

import com.fish.mirebound.client.config.MireboundClientSettings;
import com.fish.mirebound.client.config.MireboundClientSettings.ClientOption;
import com.fish.mirebound.mud.MudBlock;
import com.fish.mirebound.network.payload.MudFlowVisualPayload;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Bounded texture-matched particles bridge discrete finite-volume block updates. */
final class MudFlowClientManager {
    private static final int MAXIMUM_TRANSITIONS = 96;
    private static final int ALL_PARTICLE_BUDGET = 48;
    private static final int DECREASED_PARTICLE_BUDGET = 24;
    private static final int MINIMAL_PARTICLE_BUDGET = 8;
    private static final List<Transition> TRANSITIONS = new ArrayList<>();
    private static ClientLevel level;

    private MudFlowClientManager() {
    }

    static void accept(MudFlowVisualPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.level instanceof ClientLevel clientLevel)
                || !MireboundClientSettings.clientOptionEnabled(ClientOption.SURFACE_EFFECTS)) {
            return;
        }
        ensureLevel(clientLevel);
        if (TRANSITIONS.size() >= MAXIMUM_TRANSITIONS) {
            TRANSITIONS.remove(0);
        }
        TRANSITIONS.add(new Transition(payload));
    }

    static void tick(Minecraft minecraft) {
        if (!(minecraft.level instanceof ClientLevel clientLevel)
                || !MireboundClientSettings.clientOptionEnabled(ClientOption.SURFACE_EFFECTS)) {
            reset();
            return;
        }
        ensureLevel(clientLevel);
        int budget = switch (minecraft.options.particles().get()) {
            case ALL -> ALL_PARTICLE_BUDGET;
            case DECREASED -> DECREASED_PARTICLE_BUDGET;
            case MINIMAL -> MINIMAL_PARTICLE_BUDGET;
        };
        Iterator<Transition> iterator = TRANSITIONS.iterator();
        while (iterator.hasNext()) {
            Transition transition = iterator.next();
            budget -= transition.tick(clientLevel, Math.max(0, budget));
            if (transition.finished()) {
                iterator.remove();
            }
        }
    }

    static void reset() {
        TRANSITIONS.clear();
        level = null;
    }

    private static void ensureLevel(ClientLevel current) {
        if (level != current) {
            TRANSITIONS.clear();
            level = current;
        }
    }

    private static final class Transition {
        private final MudFlowVisualPayload payload;
        private int age;

        private Transition(MudFlowVisualPayload payload) {
            this.payload = payload;
        }

        private int tick(ClientLevel level, int budget) {
            int emitted = 0;
            BlockState visual = visualState(level, payload);
            if (visual != null && budget > 0) {
                int count = Math.min(budget, payload.transferredPixels() >= 3 ? 2 : 1);
                BlockParticleOption particle = new BlockParticleOption(
                        ParticleTypes.BLOCK, visual);
                Vec3 start = start(payload);
                Vec3 end = end(payload);
                Vec3 travel = end.subtract(start);
                Vec3 velocity = travel.lengthSqr() <= 1.0E-8D
                        ? new Vec3(0.0D, -0.018D, 0.0D)
                        : travel.normalize().scale(0.022D).add(0.0D, -0.012D, 0.0D);
                RandomSource random = level.random;
                for (int index = 0; index < count; index++) {
                    double progress = Math.min(1.0D,
                            (age + 0.30D + index * 0.34D) / payload.durationTicks());
                    Vec3 point = start.lerp(end, progress);
                    if (payload.source().getY() == payload.target().getY()) {
                        point = point.add(0.0D, Math.sin(Math.PI * progress) * 0.075D, 0.0D);
                    }
                    point = point.add(
                            centered(random, 0.035D),
                            centered(random, 0.018D),
                            centered(random, 0.035D));
                    level.addParticle(particle,
                            point.x, point.y, point.z,
                            velocity.x + centered(random, 0.010D),
                            velocity.y + centered(random, 0.008D),
                            velocity.z + centered(random, 0.010D));
                    emitted++;
                }
            }
            age++;
            return emitted;
        }

        private boolean finished() {
            return age >= payload.durationTicks();
        }
    }

    private static BlockState visualState(
            ClientLevel level, MudFlowVisualPayload payload) {
        BlockState target = level.getBlockState(payload.target());
        if (matches(target, payload)) {
            return target;
        }
        BlockState source = level.getBlockState(payload.source());
        return matches(source, payload) ? source : null;
    }

    private static boolean matches(BlockState state, MudFlowVisualPayload payload) {
        return state.getBlock() instanceof MudBlock mud
                && mud.medium() == payload.medium();
    }

    private static Vec3 start(MudFlowVisualPayload payload) {
        BlockPos source = payload.source();
        int sourceBefore = Math.min(16,
                payload.sourcePixelsAfter() + payload.transferredPixels());
        return new Vec3(
                source.getX() + 0.5D,
                source.getY() + Math.max(0.08D, sourceBefore / 16.0D),
                source.getZ() + 0.5D);
    }

    private static Vec3 end(MudFlowVisualPayload payload) {
        BlockPos target = payload.target();
        return new Vec3(
                target.getX() + 0.5D,
                target.getY() + payload.targetPixelsAfter() / 16.0D + 0.025D,
                target.getZ() + 0.5D);
    }

    private static double centered(RandomSource random, double radius) {
        return (random.nextDouble() * 2.0D - 1.0D) * radius;
    }
}

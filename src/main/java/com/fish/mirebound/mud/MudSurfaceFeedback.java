package com.fish.mirebound.mud;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** Small bounded sound and particle feedback emitted by mud interactions. */
final class MudSurfaceFeedback {
    private MudSurfaceFeedback() {
    }

    static void playStruggle(ServerPlayer player, SinkingMedium medium) {
        SoundEvent sound = switch (medium) {
            case LIVING_SLIME -> SoundEvents.SLIME_BLOCK_STEP;
            case RED_QUICKSAND, ASH_QUICKSAND, SOFT_QUICKSAND,
                    JUNGLE_QUICKSAND, SILT, SOUL_SILT -> SoundEvents.SILVERFISH_STEP;
            case TAR -> SoundEvents.HONEY_BLOCK_SLIDE;
            default -> SoundEvents.HONEY_BLOCK_PLACE;
        };
        player.level().playSound(
                null, player.blockPosition(), sound, SoundSource.BLOCKS,
                0.23F,
                0.45F + player.level().getRandom().nextFloat() * 0.16F);
    }

    static void spawn(
            ServerLevel level, Vec3 center, SinkingMedium medium,
            int count, double spread, double rise) {
        DustParticleOptions dust = new DustParticleOptions(
                medium.particleColor(), medium.particleScale());
        level.sendParticles(
                dust, center.x, center.y + 0.08D, center.z,
                count, spread, 0.035D, spread, rise);
    }
}

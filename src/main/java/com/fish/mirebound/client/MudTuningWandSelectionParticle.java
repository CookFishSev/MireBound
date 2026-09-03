package com.fish.mirebound.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.EndRodParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.util.FastColor;

/** End-rod-shaped selection spark tinted to the captured tuning-wand beam color. */
final class MudTuningWandSelectionParticle extends EndRodParticle {
    private MudTuningWandSelectionParticle(ColorParticleOption options,
            ClientLevel level, double x, double y, double z,
            double velocityX, double velocityY, double velocityZ,
            SpriteSet sprites) {
        super(level, x, y, z, velocityX, velocityY, velocityZ, sprites);
        float maximum = Math.max(options.getRed(),
                Math.max(options.getGreen(), options.getBlue()));
        float red = emissiveChannel(options.getRed(), maximum, 0.10F);
        float green = emissiveChannel(options.getGreen(), maximum, 0.10F);
        float blue = emissiveChannel(options.getBlue(), maximum, 0.10F);
        setColor(red, green, blue);
        setFadeColor(FastColor.ARGB32.colorFromFloat(
                1.0F,
                emissiveChannel(options.getRed(), maximum, 0.22F),
                emissiveChannel(options.getGreen(), maximum, 0.22F),
                emissiveChannel(options.getBlue(), maximum, 0.22F)));
        scale(1.18F);
    }

    private static float emissiveChannel(float channel, float maximum,
            float whiteMix) {
        if (maximum <= 1.0E-5F) {
            return 1.0F;
        }
        return whiteMix + channel / maximum * (1.0F - whiteMix);
    }

    static final class Provider implements ParticleProvider<ColorParticleOption> {
        private final SpriteSet sprites;

        Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(ColorParticleOption options,
                ClientLevel level, double x, double y, double z,
                double velocityX, double velocityY, double velocityZ) {
            return new MudTuningWandSelectionParticle(
                    options, level, x, y, z,
                    velocityX, velocityY, velocityZ, sprites);
        }
    }
}

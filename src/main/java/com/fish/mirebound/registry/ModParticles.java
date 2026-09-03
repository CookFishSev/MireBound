package com.fish.mirebound.registry;

import com.fish.mirebound.Mirebound;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Particle types shared by server options and client providers. */
public final class ModParticles {
    private static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(BuiltInRegistries.PARTICLE_TYPE, Mirebound.MOD_ID);

    public static final DeferredHolder<ParticleType<?>, ParticleType<ColorParticleOption>>
            TUNING_WAND_SELECTION = PARTICLE_TYPES.register(
                    "tuning_wand_selection", ModParticles::colorParticleType);

    private ModParticles() {
    }

    public static void register(IEventBus modBus) {
        PARTICLE_TYPES.register(modBus);
    }

    private static ParticleType<ColorParticleOption> colorParticleType() {
        return new ParticleType<>(false) {
            @Override
            public MapCodec<ColorParticleOption> codec() {
                return ColorParticleOption.codec(this);
            }

            @Override
            public StreamCodec<? super RegistryFriendlyByteBuf, ColorParticleOption>
                    streamCodec() {
                return ColorParticleOption.streamCodec(this);
            }
        };
    }
}

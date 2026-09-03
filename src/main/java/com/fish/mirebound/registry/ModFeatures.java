package com.fish.mirebound.registry;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.generation.natural.NaturalMudDepositFeature;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Feature types referenced by the mod's data-driven world generation. */
public final class ModFeatures {
    private static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(BuiltInRegistries.FEATURE, Mirebound.MOD_ID);

    public static final DeferredHolder<Feature<?>, NaturalMudDepositFeature>
            NATURAL_MUD_DEPOSIT = FEATURES.register(
                    "natural_mud_deposit",
                    () -> new NaturalMudDepositFeature(
                            NoneFeatureConfiguration.CODEC));

    private ModFeatures() {
    }

    public static void register(IEventBus modBus) {
        FEATURES.register(modBus);
    }
}

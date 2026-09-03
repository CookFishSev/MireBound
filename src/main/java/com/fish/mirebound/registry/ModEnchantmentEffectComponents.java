package com.fish.mirebound.registry;

import com.fish.mirebound.Mirebound;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Data-driven values consumed by the mod's enchantments. */
public final class ModEnchantmentEffectComponents {
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(
                    Registries.ENCHANTMENT_EFFECT_COMPONENT_TYPE,
                    Mirebound.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<LevelBasedValue>>
            MUD_WALKER_DEPTH_REDUCTION = COMPONENTS.registerComponentType(
                    "mud_walker_depth_reduction",
                    builder -> builder.persistent(LevelBasedValue.CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<LevelBasedValue>>
            MUD_WALKER_WALK_RESTORATION = COMPONENTS.registerComponentType(
                    "mud_walker_walk_restoration",
                    builder -> builder.persistent(LevelBasedValue.CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>>
            PREVENTS_STAINING = COMPONENTS.registerComponentType(
                    "prevents_staining",
                    builder -> builder.persistent(Codec.BOOL));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>>
            PROTECTS_INNER_SKIN = COMPONENTS.registerComponentType(
                    "protects_inner_skin",
                    builder -> builder.persistent(Codec.BOOL));

    private ModEnchantmentEffectComponents() {
    }

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
    }
}

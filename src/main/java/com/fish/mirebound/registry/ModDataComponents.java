package com.fish.mirebound.registry;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.mud.ArmorMudData;
import com.fish.mirebound.mud.ArmorTextureMudData;
import com.fish.mirebound.mud.container.MudVolumeData;
import com.mojang.serialization.Codec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Mirebound.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ArmorMudData>> ARMOR_MUD =
            COMPONENTS.registerComponentType("armor_mud", builder -> builder
                    .persistent(ArmorMudData.CODEC)
                    .networkSynchronized(ArmorMudData.STREAM_CODEC)
                    .cacheEncoding());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ArmorTextureMudData>> ARMOR_TEXTURE_MUD =
            COMPONENTS.registerComponentType("armor_texture_mud", builder -> builder
                    .persistent(ArmorTextureMudData.CODEC)
                    .networkSynchronized(ArmorTextureMudData.STREAM_CODEC)
                    .cacheEncoding());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> WATER_GUN_WATER =
            COMPONENTS.registerComponentType("water_gun_water", builder -> builder
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .cacheEncoding());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<MudVolumeData>> MUD_VOLUME =
            COMPONENTS.registerComponentType("mud_volume", builder -> builder
                    .persistent(MudVolumeData.CODEC)
                    .networkSynchronized(MudVolumeData.STREAM_CODEC)
                    .cacheEncoding());

    private ModDataComponents() {
    }

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
    }
}

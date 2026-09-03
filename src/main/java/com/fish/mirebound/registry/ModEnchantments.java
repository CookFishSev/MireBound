package com.fish.mirebound.registry;

import com.fish.mirebound.Mirebound;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;

/** Resource keys for the mod's data-driven enchantments. */
public final class ModEnchantments {
    public static final ResourceKey<Enchantment> MUD_WALKER = key("mud_walker");
    public static final ResourceKey<Enchantment> STAIN_PROTECTION = key("stain_protection");
    public static final ResourceKey<Enchantment> INNER_CLEANLINESS = key("inner_cleanliness");

    private ModEnchantments() {
    }

    private static ResourceKey<Enchantment> key(String name) {
        return ResourceKey.create(
                Registries.ENCHANTMENT,
                ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, name));
    }
}

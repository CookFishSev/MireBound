package com.fish.mirebound.compat.sophisticated;

import java.lang.reflect.Method;
import java.util.Optional;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/** Uses the mod's rendered-backpack selection, including its main-inventory setting. */
public final class SophisticatedBackpackCompat {
    private static Method provider, rendered, backpack;
    private static boolean initialized;
    private SophisticatedBackpackCompat() {}
    public static synchronized ItemStack renderedStack(Player player) {
        if (!ModList.get().isLoaded("sophisticatedbackpacks")) return ItemStack.EMPTY;
        try {
            if (!initialized) {
                initialized = true;
                Class<?> type = Class.forName("net.p3pp3rf1y.sophisticatedbackpacks.util.PlayerInventoryProvider");
                provider = type.getMethod("get");
                rendered = type.getMethod("getBackpackFromRendered", Player.class);
                backpack = Class.forName(type.getName() + "$RenderInfo").getMethod("getBackpack");
            }
            if (provider == null || rendered == null || backpack == null) return ItemStack.EMPTY;
            Object value = rendered.invoke(provider.invoke(null), player);
            if (value instanceof Optional<?> optional && optional.isPresent()
                    && backpack.invoke(optional.get()) instanceof ItemStack stack) return stack;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return ItemStack.EMPTY;
        }
        return ItemStack.EMPTY;
    }
}

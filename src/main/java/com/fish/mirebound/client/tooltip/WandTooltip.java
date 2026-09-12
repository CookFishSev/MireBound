package com.fish.mirebound.client.tooltip;

import com.fish.mirebound.tool.MudTuningWandItem;
import com.mojang.datafixers.util.Either;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Adds lore without replacing the tooltip, item title, or other mods' information. */
public final class WandTooltip {
    private static final WandLoreAnimation.Session SESSION = new WandLoreAnimation.Session();
    private static ItemStack lastStack = ItemStack.EMPTY;
    private static WandLoreTooltip lore;
    private static String lastText;
    private static int lastWidth;

    private WandTooltip() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(WandTooltip::registerFactory);
        NeoForge.EVENT_BUS.addListener(WandTooltip::gather);
        NeoForge.EVENT_BUS.addListener(WandTooltip::color);
    }

    private static void registerFactory(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(Lore.class, Lore::component);
    }

    private static void gather(RenderTooltipEvent.GatherComponents event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof MudTuningWandItem)) {
            SESSION.reset();
            return;
        }
        if (!ItemStack.isSameItemSameComponents(stack, lastStack)) {
            lastStack = stack.copy();
            SESSION.reset();
        }
        boolean expanded = Screen.hasShiftDown();
        double seconds = SESSION.observe(expanded, Util.getMillis());
        int insertion = Math.min(1, event.getTooltipElements().size());
        if (!expanded) {
            Component hint = Component.translatable("tooltip.mirebound.wand.expand",
                    Component.literal("Shift").withStyle(ChatFormatting.GRAY)).withStyle(ChatFormatting.DARK_GRAY);
            event.getTooltipElements().add(insertion, Either.left(hint));
            return;
        }
        String text = Component.translatable("tooltip.mirebound.wand.lore").getString();
        int width = Math.max(24, Math.min(270, event.getScreenWidth() - 28));
        if (event.getMaxWidth() > 0) width = Math.min(width, event.getMaxWidth());
        if (lore == null || width != lastWidth || !text.equals(lastText)) {
            lore = new WandLoreTooltip(text, width, Minecraft.getInstance().font);
            lastText = text;
            lastWidth = width;
        }
        lore.setTime(seconds);
        event.getTooltipElements().add(insertion, Either.right(new Lore(lore)));
    }

    private static void color(RenderTooltipEvent.Color event) {
        if (!(event.getItemStack().getItem() instanceof MudTuningWandItem)) return;
        event.setBackgroundStart(0xF0101114);
        event.setBackgroundEnd(0xF008090B);
        event.setBorderStart(0xB0797C82);
        event.setBorderEnd(0x7043464B);
    }

    public static void reset() {
        SESSION.reset();
        lastStack = ItemStack.EMPTY;
        lore = null;
        lastText = null;
    }

    private record Lore(WandLoreTooltip component) implements TooltipComponent {
    }
}

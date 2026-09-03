package com.fish.mirebound.client;

import com.fish.mirebound.mud.ArmorMudData;
import com.fish.mirebound.mud.ArmorMudManager;
import com.fish.mirebound.mud.ArmorTextureMudData;
import com.fish.mirebound.mud.MudEnchantmentEffects;
import com.fish.mirebound.mud.SinkingMedium;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.neoforge.client.IItemDecorator;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.joml.Vector3f;

final class ArmorMudItemMarker implements IItemDecorator {
    private static final ArmorMudItemMarker INSTANCE = new ArmorMudItemMarker();

    private ArmorMudItemMarker() {
    }

    static void register(RegisterItemDecorationsEvent event) {
        BuiltInRegistries.ITEM.forEach(item -> event.register(item, INSTANCE));
    }

    static void appendTooltip(ItemTooltipEvent event) {
        appendEnchantmentEffects(event);
        ArmorMudData data = ArmorMudManager.data(event.getItemStack());
        ArmorTextureMudData textureData = ArmorMudManager.textureData(event.getItemStack());
        if (data.isEmpty() && textureData.isEmpty()) {
            return;
        }
        float coverage = Math.max(ArmorMudManager.coverageFraction(event.getItemStack()),
                textureCoverageFraction(event.getItemStack(), textureData));
        int percent = Mth.clamp(Math.round(coverage * 100.0F), 1, 100);
        event.getToolTip().add(Component.translatable("tooltip.mirebound.armor_mud", percent));
    }

    private static void appendEnchantmentEffects(ItemTooltipEvent event) {
        HolderLookup.Provider registries = event.getContext().registries();
        if (registries == null) {
            return;
        }
        HolderLookup.RegistryLookup<Enchantment> enchantments =
                registries.lookupOrThrow(Registries.ENCHANTMENT);
        MudEnchantmentEffects.ItemDescription effects = MudEnchantmentEffects.describe(
                event.getItemStack(), enchantments);
        if (effects.mudWalkerLevel() > 0) {
            event.getToolTip().add(Component.translatable(
                    "tooltip.mirebound.mud_walker.depth",
                    Math.round(effects.depthReduction() * 100.0F))
                    .withStyle(ChatFormatting.DARK_GREEN));
            event.getToolTip().add(Component.translatable(
                    "tooltip.mirebound.mud_walker.walk",
                    Math.round(effects.walkRestoration() * 100.0F))
                    .withStyle(ChatFormatting.DARK_GREEN));
        }
        if (effects.preventsStaining()) {
            event.getToolTip().add(Component.translatable(
                    "tooltip.mirebound.stain_protection")
                    .withStyle(ChatFormatting.DARK_GREEN));
        }
        if (effects.protectsInnerSkin()) {
            event.getToolTip().add(Component.translatable(
                    "tooltip.mirebound.inner_cleanliness")
                    .withStyle(ChatFormatting.DARK_GREEN));
        }
    }

    private static float textureCoverageFraction(ItemStack stack, ArmorTextureMudData textureData) {
        double weightedCoverage = 0.0D;
        long paintableAlpha = 0L;
        boolean resolvedAllLayers = true;
        for (ArmorTextureMudData.Layer layer : textureData.layers()) {
            ArmorTextureFootprintCache.CoverageStats footprint =
                    ArmorTextureFootprintCache.coverage(stack, layer);
            if (footprint != null) {
                weightedCoverage += footprint.coveredAlpha();
                paintableAlpha += footprint.paintableAlpha();
                continue;
            }
            long layerAlpha = SkinPixelCache.alphaTotal(layer.texture(), layer.width(), layer.height());
            if (layerAlpha <= 0L) {
                resolvedAllLayers = false;
                continue;
            }
            paintableAlpha += layerAlpha;
            final double[] layerCoverage = {0.0D};
            layer.forEach((pixel, coverage, medium) -> layerCoverage[0] += coverage
                    * SkinPixelCache.alpha(layer.texture(), pixel % layer.width(), pixel / layer.width()));
            weightedCoverage += layerCoverage[0];
        }
        return resolvedAllLayers && paintableAlpha > 0L
                ? Mth.clamp((float) (weightedCoverage / paintableAlpha), 0.0F, 1.0F)
                : textureData.coverageFraction();
    }

    @Override
    public boolean render(GuiGraphics graphics, Font font, ItemStack stack, int x, int y) {
        ArmorMudData data = ArmorMudManager.data(stack);
        ArmorTextureMudData textureData = ArmorMudManager.textureData(stack);
        if (data.isEmpty() && textureData.isEmpty()) {
            return false;
        }
        int color = markerColor(data.isEmpty() ? textureData.dominantMedium() : data.dominantMedium());
        graphics.fill(x + 1, y + 1, x + 5, y + 4, color);
        graphics.fill(x + 2, y, x + 4, y + 5, color);
        graphics.fill(x, y + 2, x + 6, y + 3, color);
        return false;
    }

    private static int markerColor(SinkingMedium medium) {
        Vector3f color = medium.particleColor();
        int red = Mth.clamp(Math.round(color.x() * 255.0F), 0, 255);
        int green = Mth.clamp(Math.round(color.y() * 255.0F), 0, 255);
        int blue = Mth.clamp(Math.round(color.z() * 255.0F), 0, 255);
        return 0xE0000000 | red << 16 | green << 8 | blue;
    }
}

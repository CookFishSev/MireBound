package com.fish.mirebound.mud;

import com.fish.mirebound.registry.ModEnchantmentEffectComponents;
import com.fish.mirebound.registry.ModEnchantments;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.LevelBasedValue;

/** Resolves synchronized data-driven mud enchantment behavior. */
public final class MudEnchantmentEffects {
    private static final Modifiers NONE = new Modifiers(1.0D, 0.0D);

    private MudEnchantmentEffects() {
    }

    public static Modifiers mudWalker(Player player) {
        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
        Holder<Enchantment> enchantment = enchantment(player, ModEnchantments.MUD_WALKER);
        if (enchantment == null) {
            return NONE;
        }
        int level = boots.getEnchantmentLevel(enchantment);
        if (level <= 0) {
            return NONE;
        }
        float depthReduction = levelValue(
                enchantment,
                ModEnchantmentEffectComponents.MUD_WALKER_DEPTH_REDUCTION.get(),
                level);
        float walkRestoration = levelValue(
                enchantment,
                ModEnchantmentEffectComponents.MUD_WALKER_WALK_RESTORATION.get(),
                level);
        return new Modifiers(
                1.0D - Mth.clamp(depthReduction, 0.0F, 0.90F),
                Mth.clamp(walkRestoration, 0.0F, 0.95F));
    }

    public static ItemDescription describe(
            ItemStack stack,
            HolderLookup.RegistryLookup<Enchantment> enchantments) {
        ItemEnchantments applied = stack.getAllEnchantments(enchantments);
        Holder<Enchantment> mudWalker = enchantment(enchantments, ModEnchantments.MUD_WALKER);
        Holder<Enchantment> stainProtection = enchantment(
                enchantments, ModEnchantments.STAIN_PROTECTION);
        Holder<Enchantment> innerCleanliness = enchantment(
                enchantments, ModEnchantments.INNER_CLEANLINESS);
        int mudWalkerLevel = mudWalker == null ? 0 : applied.getLevel(mudWalker);
        return new ItemDescription(
                mudWalkerLevel,
                mudWalkerLevel <= 0 ? 0.0F : levelValue(
                        mudWalker,
                        ModEnchantmentEffectComponents.MUD_WALKER_DEPTH_REDUCTION.get(),
                        mudWalkerLevel),
                mudWalkerLevel <= 0 ? 0.0F : levelValue(
                        mudWalker,
                        ModEnchantmentEffectComponents.MUD_WALKER_WALK_RESTORATION.get(),
                        mudWalkerLevel),
                hasBooleanEffect(applied, stainProtection,
                        ModEnchantmentEffectComponents.PREVENTS_STAINING.get()),
                hasBooleanEffect(applied, innerCleanliness,
                        ModEnchantmentEffectComponents.PROTECTS_INNER_SKIN.get()));
    }

    public static boolean preventsArmorStaining(LivingEntity wearer, ItemStack armor) {
        Holder<Enchantment> enchantment = enchantment(wearer, ModEnchantments.STAIN_PROTECTION);
        return enchantment != null
                && armor.getEnchantmentLevel(enchantment) > 0
                && Boolean.TRUE.equals(enchantment.value().effects().get(
                        ModEnchantmentEffectComponents.PREVENTS_STAINING.get()));
    }

    public static boolean protectsInnerSkin(LivingEntity wearer, ItemStack armor) {
        Holder<Enchantment> enchantment = enchantment(wearer, ModEnchantments.INNER_CLEANLINESS);
        return enchantment != null
                && armor.getEnchantmentLevel(enchantment) > 0
                && Boolean.TRUE.equals(enchantment.value().effects().get(
                        ModEnchantmentEffectComponents.PROTECTS_INNER_SKIN.get()));
    }

    static double restoreWalkScale(double original, double restoration) {
        double clamped = Mth.clamp(restoration, 0.0D, 0.95D);
        return Mth.clamp(original + (1.0D - original) * clamped, 0.0D, 1.20D);
    }

    private static float levelValue(
            Holder<Enchantment> enchantment,
            DataComponentType<LevelBasedValue> component,
            int level) {
        LevelBasedValue value = enchantment.value().effects().get(component);
        return value == null ? 0.0F : value.calculate(level);
    }

    private static Holder<Enchantment> enchantment(
            LivingEntity entity,
            ResourceKey<Enchantment> key) {
        return enchantment(entity.registryAccess().lookupOrThrow(Registries.ENCHANTMENT), key);
    }

    private static Holder<Enchantment> enchantment(
            HolderLookup.RegistryLookup<Enchantment> enchantments,
            ResourceKey<Enchantment> key) {
        return enchantments
                .get(key)
                .map(reference -> (Holder<Enchantment>) reference)
                .orElse(null);
    }

    private static boolean hasBooleanEffect(
            ItemEnchantments applied,
            Holder<Enchantment> enchantment,
            DataComponentType<Boolean> component) {
        return enchantment != null
                && applied.getLevel(enchantment) > 0
                && Boolean.TRUE.equals(enchantment.value().effects().get(component));
    }

    public record Modifiers(double depthLimitScale, double walkRestoration) {
        public double applyWalkScale(double original) {
            return restoreWalkScale(original, walkRestoration);
        }
    }

    public record ItemDescription(
            int mudWalkerLevel,
            float depthReduction,
            float walkRestoration,
            boolean preventsStaining,
            boolean protectsInnerSkin) {
    }
}

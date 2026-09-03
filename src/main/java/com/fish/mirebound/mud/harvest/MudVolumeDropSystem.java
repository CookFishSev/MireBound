package com.fish.mirebound.mud.harvest;

import com.fish.mirebound.adaptive.AdaptiveMudBlock;
import com.fish.mirebound.mud.MudBlock;
import com.fish.mirebound.mud.MudVolumeState;
import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.mud.container.MudVolumeData;
import com.fish.mirebound.mud.harvest.MudDropRule.MudDropEntry;
import com.fish.mirebound.registry.ModDataComponents;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/**
 * Height-aware mud drops without one loot table for every 1/16 state.
 *
 * <p>Silk Touch preserves the block plus its stored volume. Otherwise the medium's entry in
 * {@link MudDropRules} decides the material outcome instead of special-casing plain mud.
 */
public final class MudVolumeDropSystem {
    private MudVolumeDropSystem() {
    }

    public static void onBlockDrops(BlockDropsEvent event) {
        BlockState state = event.getState();
        if (!(state.getBlock() instanceof MudBlock mudBlock)
                || state.getBlock() instanceof AdaptiveMudBlock) {
            return;
        }
        int pixels = MudVolumeState.pixels(state);
        if (hasSilkTouch(event.getLevel(), event.getTool())) {
            event.setDroppedExperience(0);
            event.getDrops().clear();
            ItemStack preserved = new ItemStack(state.getBlock().asItem());
            preserved.set(ModDataComponents.MUD_VOLUME.get(),
                    new MudVolumeData(mudBlock.medium(), pixels));
            event.getDrops().add(drop(event.getLevel(), event.getPos(), preserved));
            return;
        }
        event.getDrops().clear();
        RandomSource random = event.getLevel().random;
        event.setDroppedExperience(
                MudExperienceDrops.roll(mudBlock.medium(), pixels, random));
        for (ItemStack stack : rolledDrops(mudBlock.medium(), pixels, random)) {
            event.getDrops().add(drop(event.getLevel(), event.getPos(), stack));
        }
    }

    /**
     * Rolls one medium's drop rule into concrete stacks.
     *
     * <p>Separated from the event so the whole table is exercisable without a server, and so an
     * empty roll produces an empty list rather than an empty stack entity.
     */
    static List<ItemStack> rolledDrops(SinkingMedium medium, int pixels, RandomSource random) {
        MudDropRule rule = MudDropRules.ruleFor(medium);
        List<ItemStack> stacks = new ArrayList<>(1 + rule.bonus().size());
        addRolled(stacks, rule.primary(), pixels, random);
        for (MudDropEntry entry : rule.bonus()) {
            addRolled(stacks, entry, pixels, random);
        }
        return stacks;
    }

    private static void addRolled(List<ItemStack> stacks, MudDropEntry entry,
            int pixels, RandomSource random) {
        int count = entry.yield().count(pixels, random);
        if (count > 0) {
            stacks.add(new ItemStack(entry.item().get(), count));
        }
    }

    static int mudBallCount(int pixels, RandomSource random) {
        return MudDropYield.SCALED.count(pixels, random);
    }

    private static boolean hasSilkTouch(ServerLevel level, ItemStack tool) {
        var enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        return tool.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(
                enchantments.getOrThrow(Enchantments.SILK_TOUCH)) > 0;
    }

    private static ItemEntity drop(ServerLevel level, BlockPos pos, ItemStack stack) {
        return new ItemEntity(level,
                pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, stack);
    }
}

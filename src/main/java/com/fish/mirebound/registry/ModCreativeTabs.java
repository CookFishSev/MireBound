package com.fish.mirebound.registry;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.mud.container.MudBucketItem;
import com.fish.mirebound.mud.container.MudContainerRules;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** One creative tab with banner-separated sections for all public content. */
public final class ModCreativeTabs {
    public static final int ITEMS_PER_ROW = 9;

    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Mirebound.MOD_ID);

    private static final List<SinkingMedium> PUBLIC_MEDIA = List.of(
            SinkingMedium.MUD,
            SinkingMedium.THIN_MUD,
            SinkingMedium.SHALLOW_MUD,
            SinkingMedium.TIDAL_MUD,
            SinkingMedium.GEL_CLAY,
            SinkingMedium.LIME_MUD,
            SinkingMedium.STONE_CLAY,
            SinkingMedium.PALE_MIRE,
            SinkingMedium.PEAT_SILT,
            SinkingMedium.MIRE,
            SinkingMedium.PEAT_BOG,
            SinkingMedium.RED_QUICKSAND,
            SinkingMedium.ASH_QUICKSAND,
            SinkingMedium.SOUL_SILT,
            SinkingMedium.SOFT_QUICKSAND,
            SinkingMedium.SILT,
            SinkingMedium.JUNGLE_QUICKSAND,
            SinkingMedium.END_SILT,
            SinkingMedium.GRAVEL_SILT,
            SinkingMedium.TAR,
            SinkingMedium.LIVING_SLIME,
            SinkingMedium.ASSIMILATION_SLIME,
            SinkingMedium.INSECT_MOUND,
            SinkingMedium.SCULK_MIRE,
            SinkingMedium.FUNGAL_MIRE,
            SinkingMedium.TENDER_FLESH);

    private static final List<CreativeEntry> QUICKSAND_CONTENT = PUBLIC_MEDIA.stream()
            .map(ModCreativeTabs::mediumBlock)
            .toList();

    private static final List<CreativeEntry> BUCKET_CONTENT = PUBLIC_MEDIA.stream()
            .filter(MudContainerRules::isBucketable)
            .map(ModCreativeTabs::fullBucket)
            .toList();

    private static final List<Supplier<? extends ItemLike>> TOOL_CONTENT = List.of(
            ModBlocks.MUD_PROBE,
            ModBlocks.MUD_TUNING_WAND,
            ModBlocks.WATER_GUN,
            ModMudworkContent.ROPE);

    private static final List<Supplier<? extends ItemLike>> ITEM_CONTENT = List.of(
            ModBlocks.MUD_BALL,
            ModBlocks.THIN_MUD_BALL,
            ModBlocks.SHALLOW_MUD_BALL,
            ModBlocks.TIDAL_MUD_BALL,
            ModBlocks.LIME_MUD_BALL,
            ModBlocks.GRAVEL_SILT_MUD_BALL,
            ModBlocks.FUNGAL_MIRE_MUD_BALL,
            ModBlocks.PEAT_SILT_MUD_BALL,
            ModBlocks.MIRE_MUD_BALL,
            ModBlocks.PEAT_BOG_MUD_BALL,
            ModMudworkContent.MAGGOT,
            ModMudworkContent.COOKED_MAGGOT,
            ModMudworkContent.BLOOD_CLOT_BALL,
            ModMudworkContent.TAR_BLOB,
            ModMudworkContent.GEL_CLAY_BALL,
            ModMudworkContent.STONE_CLAY_BALL,
            ModMudworkContent.PALE_CLAY_BALL);

    private static final List<CreativeEntry> ENCHANTMENT_CONTENT = List.of(
            enchantment(ModEnchantments.MUD_WALKER, 1),
            enchantment(ModEnchantments.MUD_WALKER, 2),
            enchantment(ModEnchantments.MUD_WALKER, 3),
            enchantment(ModEnchantments.STAIN_PROTECTION, 1),
            enchantment(ModEnchantments.INNER_CLEANLINESS, 1));

    private static final List<Section> SECTIONS = List.of(
            section("quicksand", QUICKSAND_CONTENT, 0xAA39291F, 0xFFF4D38C),
            section("buckets", BUCKET_CONTENT, 0xAA354348, 0xFFE5F1F2),
            itemSection("tools", TOOL_CONTENT, 0xAA40372B, 0xFFFFE4B0),
            section("enchantments", ENCHANTMENT_CONTENT, 0xAA263347, 0xFFE4F2FF),
            itemSection("items", ITEM_CONTENT, 0xAA3C3430, 0xFFFFE5C4));

    private static SectionLayout cachedLayout;
    private static HolderLookup.Provider cachedHolders;

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN =
            TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.mirebound"))
                    .icon(() -> new ItemStack(ModBlocks.MUD_TUNING_WAND.get()))
                    .displayItems((parameters, output) -> sectionLayout(parameters.holders()).searchItems()
                            .forEach(output::accept))
                    .build());

    private ModCreativeTabs() {
    }

    public static void register(IEventBus modBus) {
        TABS.register(modBus);
    }

    public static List<Section> sections() {
        return SECTIONS;
    }

    /**
     * Builds category contents with one empty row reserved for each full-width
     * section banner. Empty rows are client-side layout markers only.
     */
    public static synchronized SectionLayout sectionLayout() {
        if (cachedLayout == null) {
            cachedLayout = buildSectionLayout(null);
        }
        return cachedLayout;
    }

    public static synchronized SectionLayout sectionLayout(HolderLookup.Provider holders) {
        if (cachedLayout == null || cachedHolders != holders) {
            cachedLayout = buildSectionLayout(holders);
            cachedHolders = holders;
        }
        return cachedLayout;
    }

    private static SectionLayout buildSectionLayout(HolderLookup.Provider holders) {
        List<ItemStack> displayItems = new ArrayList<>();
        Set<ItemStack> searchItems = new LinkedHashSet<>();
        List<Integer> bannerRows = new ArrayList<>(SECTIONS.size());
        int row = 0;

        for (Section section : SECTIONS) {
            bannerRows.add(row++);
            addEmptyRow(displayItems);

            int itemCount = 0;
            for (CreativeEntry entry : section.contents()) {
                ItemStack stack = entry.create(holders);
                if (stack.isEmpty()) {
                    continue;
                }
                displayItems.add(stack);
                searchItems.add(stack);
                itemCount++;
            }

            int remainder = itemCount % ITEMS_PER_ROW;
            if (remainder != 0) {
                int padding = ITEMS_PER_ROW - remainder;
                for (int index = 0; index < padding; index++) {
                    displayItems.add(ItemStack.EMPTY);
                }
            }
            row += Math.max(1, (itemCount + ITEMS_PER_ROW - 1) / ITEMS_PER_ROW);
        }

        return new SectionLayout(
                List.copyOf(displayItems),
                Collections.unmodifiableSet(new LinkedHashSet<>(searchItems)),
                List.copyOf(bannerRows));
    }

    private static void addEmptyRow(List<ItemStack> items) {
        for (int index = 0; index < ITEMS_PER_ROW; index++) {
            items.add(ItemStack.EMPTY);
        }
    }

    private static Section itemSection(
            String name,
            List<Supplier<? extends ItemLike>> contents,
            int titleBackground,
            int titleColor) {
        return section(
                name,
                contents.stream().map(ModCreativeTabs::item).toList(),
                titleBackground,
                titleColor);
    }

    private static Section section(
            String name,
            List<CreativeEntry> contents,
            int titleBackground,
            int titleColor) {
        return new Section(
                name,
                Component.translatable("itemGroup.mirebound." + name),
                id("creative/section_" + name),
                contents,
                titleBackground,
                titleColor);
    }

    private static CreativeEntry item(Supplier<? extends ItemLike> item) {
        return holders -> new ItemStack(item.get());
    }

    private static CreativeEntry mediumBlock(SinkingMedium medium) {
        return holders -> {
            var block = ModBlocks.blockFor(medium);
            return block == null ? ItemStack.EMPTY : new ItemStack(block);
        };
    }

    private static CreativeEntry fullBucket(SinkingMedium medium) {
        return holders -> MudBucketItem.create(medium, 16);
    }

    private static CreativeEntry enchantment(ResourceKey<Enchantment> key, int level) {
        return holders -> {
            if (holders == null) {
                return ItemStack.EMPTY;
            }
            return holders.lookup(Registries.ENCHANTMENT)
                    .flatMap(lookup -> lookup.get(key))
                    .map(holder -> EnchantedBookItem.createForEnchantment(
                            new EnchantmentInstance(
                                    holder,
                                    Math.max(1, Math.min(level, holder.value().getMaxLevel())))))
                    .orElse(ItemStack.EMPTY);
        };
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, path);
    }

    public record Section(
            String name,
            Component title,
            ResourceLocation bannerSprite,
            List<CreativeEntry> contents,
            int titleBackground,
            int titleColor) {
    }

    @FunctionalInterface
    public interface CreativeEntry {
        ItemStack create(HolderLookup.Provider holders);
    }

    public record SectionLayout(
            List<ItemStack> displayItems,
            Set<ItemStack> searchItems,
            List<Integer> bannerRows) {
    }
}

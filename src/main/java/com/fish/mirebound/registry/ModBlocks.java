package com.fish.mirebound.registry;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.adaptive.AdaptiveMudBlock;
import com.fish.mirebound.adaptive.AdaptiveMudBlockEntity;
import com.fish.mirebound.content.mudwork.MudBallItem;
import com.fish.mirebound.mud.MudBlock;
import com.fish.mirebound.mud.container.MudBlockItem;
import com.fish.mirebound.mud.container.MudBucketItem;
import com.fish.mirebound.stain.MudFootprintBlock;
import com.fish.mirebound.stain.MudFootprintBlockEntity;
import com.fish.mirebound.tool.MudProbeItem;
import com.fish.mirebound.tool.MudTuningWandItem;
import com.fish.mirebound.water.WaterGunItem;
import com.fish.mirebound.mud.SinkingMedium;
import java.util.EnumMap;
import java.util.Collection;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Mirebound.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Mirebound.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, Mirebound.MOD_ID);
    private static final Map<SinkingMedium, DeferredBlock<MudBlock>> SINKING_BLOCKS =
            new EnumMap<>(SinkingMedium.class);
    private static final Map<SinkingMedium, DeferredBlock<AdaptiveMudBlock>> ADAPTIVE_BLOCKS =
            new EnumMap<>(SinkingMedium.class);

    public static final DeferredBlock<MudBlock> MUD = registerSinkingBlock("mud", SinkingMedium.MUD, MapColor.DIRT, SoundType.MUD, 0.45F);
    public static final DeferredBlock<MudBlock> RED_QUICKSAND = registerSinkingBlock("red_quicksand", SinkingMedium.RED_QUICKSAND, MapColor.TERRACOTTA_ORANGE, SoundType.SAND, 0.56F);
    public static final DeferredBlock<MudBlock> ASH_QUICKSAND = registerSinkingBlock("ash_quicksand", SinkingMedium.ASH_QUICKSAND, MapColor.COLOR_GRAY, SoundType.SAND, 0.44F);
    public static final DeferredBlock<MudBlock> SOUL_SILT = registerSinkingBlock("soul_silt", SinkingMedium.SOUL_SILT, MapColor.TERRACOTTA_BROWN, SoundType.SOUL_SAND, 0.58F);
    public static final DeferredBlock<MudBlock> SOFT_QUICKSAND = registerSinkingBlock("soft_quicksand", SinkingMedium.SOFT_QUICKSAND, MapColor.SAND, SoundType.SAND, 0.42F);
    public static final DeferredBlock<MudBlock> SILT = registerSinkingBlock("silt", SinkingMedium.SILT, MapColor.COLOR_GRAY, SoundType.SAND, 0.48F);
    public static final DeferredBlock<MudBlock> THIN_MUD = registerSinkingBlock("thin_mud", SinkingMedium.THIN_MUD, MapColor.DIRT, SoundType.MUD, 0.36F);
    public static final DeferredBlock<MudBlock> SHALLOW_MUD = registerSinkingBlock("shallow_mud", SinkingMedium.SHALLOW_MUD, MapColor.DIRT, SoundType.MUD, 0.44F);
    public static final DeferredBlock<MudBlock> TIDAL_MUD = registerSinkingBlock("tidal_mud", SinkingMedium.TIDAL_MUD, MapColor.CLAY, SoundType.MUD, 0.42F);
    public static final DeferredBlock<MudBlock> GEL_CLAY = registerSinkingBlock("gel_clay", SinkingMedium.GEL_CLAY, MapColor.CLAY, SoundType.MUD, 0.62F);
    public static final DeferredBlock<MudBlock> LIME_MUD = registerSinkingBlock("lime_mud", SinkingMedium.LIME_MUD, MapColor.SAND, SoundType.MUD, 0.52F);
    public static final DeferredBlock<MudBlock> END_SILT = registerSinkingBlock("end_silt", SinkingMedium.END_SILT, MapColor.SAND, SoundType.SAND, 0.58F);
    public static final DeferredBlock<MudBlock> SCULK_MIRE = registerSinkingBlock("sculk_mire", SinkingMedium.SCULK_MIRE, MapColor.COLOR_BLACK, SoundType.MUD, 0.64F);
    public static final DeferredBlock<MudBlock> GRAVEL_SILT = registerSinkingBlock("gravel_silt", SinkingMedium.GRAVEL_SILT, MapColor.COLOR_GRAY, SoundType.GRAVEL, 0.56F);
    public static final DeferredBlock<MudBlock> FUNGAL_MIRE = registerSinkingBlock("fungal_mire", SinkingMedium.FUNGAL_MIRE, MapColor.COLOR_BROWN, SoundType.MUD, 0.62F);
    public static final DeferredBlock<MudBlock> STONE_CLAY = registerSinkingBlock("stone_clay", SinkingMedium.STONE_CLAY, MapColor.STONE, SoundType.MUD, 0.66F);
    public static final DeferredBlock<MudBlock> PALE_MIRE = registerSinkingBlock("pale_mire", SinkingMedium.PALE_MIRE, MapColor.CLAY, SoundType.MUD, 0.60F);
    public static final DeferredBlock<MudBlock> PEAT_SILT = registerSinkingBlock("peat_silt", SinkingMedium.PEAT_SILT, MapColor.PODZOL, SoundType.MUD, 0.56F);
    public static final DeferredBlock<MudBlock> TENDER_FLESH = registerSinkingBlock("tender_flesh", SinkingMedium.TENDER_FLESH, MapColor.COLOR_RED, SoundType.MUD, 0.58F);
    public static final DeferredBlock<MudBlock> MIRE = registerSinkingBlock("mire", SinkingMedium.MIRE, MapColor.COLOR_BLACK, SoundType.MUD, 0.56F);
    public static final DeferredBlock<MudBlock> PEAT_BOG = registerSinkingBlock("peat_bog", SinkingMedium.PEAT_BOG, MapColor.PODZOL, SoundType.MUD, 0.52F);
    public static final DeferredBlock<MudBlock> LIVING_SLIME = registerSinkingBlock("living_slime", SinkingMedium.LIVING_SLIME, MapColor.COLOR_LIGHT_GREEN, SoundType.SLIME_BLOCK, 0.60F);
    public static final DeferredBlock<MudBlock> ASSIMILATION_SLIME = registerSinkingBlock(
            "assimilation_slime", SinkingMedium.ASSIMILATION_SLIME,
            MapColor.COLOR_RED, SoundType.SLIME_BLOCK, 0.66F);
    public static final DeferredBlock<MudBlock> TAR = registerSinkingBlock("tar", SinkingMedium.TAR, MapColor.COLOR_BLACK, SoundType.HONEY_BLOCK, 0.80F);
    public static final DeferredBlock<MudBlock> JUNGLE_QUICKSAND = registerSinkingBlock("jungle_quicksand", SinkingMedium.JUNGLE_QUICKSAND, MapColor.PLANT, SoundType.SAND, 0.50F);
    public static final DeferredBlock<MudBlock> INSECT_MOUND = registerSinkingBlock(
            "insect_mound", SinkingMedium.INSECT_MOUND, MapColor.COLOR_LIGHT_GRAY, SoundType.WOOL, 0.34F);
    static {
        for (SinkingMedium medium : SinkingMedium.values()) {
            DeferredBlock<AdaptiveMudBlock> block = BLOCKS.registerBlock(
                    "adaptive_" + medium.serializedName(),
                    properties -> new AdaptiveMudBlock(properties, medium),
                    sinkingProperties(MapColor.NONE, SoundType.MUD, 1.0F)
                            .isSuffocating((state, level, pos) -> false)
                            .dynamicShape());
            ADAPTIVE_BLOCKS.put(medium, block);
        }
    }
    public static final DeferredBlock<MudFootprintBlock> MUD_FOOTPRINT = BLOCKS.registerBlock(
            "mud_footprint",
            MudFootprintBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.NONE)
                    .strength(0.0F)
                    .sound(SoundType.MUD)
                    .noCollission()
                    .noOcclusion()
                    .replaceable()
                    .pushReaction(PushReaction.DESTROY)
                    .isRedstoneConductor((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MudFootprintBlockEntity>> MUD_FOOTPRINT_ENTITY =
            BLOCK_ENTITIES.register(
                    "mud_footprint",
                    () -> BlockEntityType.Builder.of(MudFootprintBlockEntity::new, MUD_FOOTPRINT.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AdaptiveMudBlockEntity>> ADAPTIVE_MUD_ENTITY =
            BLOCK_ENTITIES.register(
                    "adaptive_mud",
                    () -> BlockEntityType.Builder.of(
                            AdaptiveMudBlockEntity::new,
                            adaptiveBlocks().toArray(Block[]::new)).build(null));

    public static final DeferredItem<MudBlockItem> MUD_ITEM = registerSinkingItem(MUD);
    public static final DeferredItem<MudBlockItem> RED_QUICKSAND_ITEM = registerSinkingItem(RED_QUICKSAND);
    public static final DeferredItem<MudBlockItem> ASH_QUICKSAND_ITEM = registerSinkingItem(ASH_QUICKSAND);
    public static final DeferredItem<MudBlockItem> SOUL_SILT_ITEM = registerSinkingItem(SOUL_SILT);
    public static final DeferredItem<MudBlockItem> SOFT_QUICKSAND_ITEM = registerSinkingItem(SOFT_QUICKSAND);
    public static final DeferredItem<MudBlockItem> SILT_ITEM = registerSinkingItem(SILT);
    public static final DeferredItem<MudBlockItem> THIN_MUD_ITEM = registerSinkingItem(THIN_MUD);
    public static final DeferredItem<MudBlockItem> SHALLOW_MUD_ITEM = registerSinkingItem(SHALLOW_MUD);
    public static final DeferredItem<MudBlockItem> TIDAL_MUD_ITEM = registerSinkingItem(TIDAL_MUD);
    public static final DeferredItem<MudBlockItem> GEL_CLAY_ITEM = registerSinkingItem(GEL_CLAY);
    public static final DeferredItem<MudBlockItem> LIME_MUD_ITEM = registerSinkingItem(LIME_MUD);
    public static final DeferredItem<MudBlockItem> END_SILT_ITEM = registerSinkingItem(END_SILT);
    public static final DeferredItem<MudBlockItem> SCULK_MIRE_ITEM = registerSinkingItem(SCULK_MIRE);
    public static final DeferredItem<MudBlockItem> GRAVEL_SILT_ITEM = registerSinkingItem(GRAVEL_SILT);
    public static final DeferredItem<MudBlockItem> FUNGAL_MIRE_ITEM = registerSinkingItem(FUNGAL_MIRE);
    public static final DeferredItem<MudBlockItem> STONE_CLAY_ITEM = registerSinkingItem(STONE_CLAY);
    public static final DeferredItem<MudBlockItem> PALE_MIRE_ITEM = registerSinkingItem(PALE_MIRE);
    public static final DeferredItem<MudBlockItem> PEAT_SILT_ITEM = registerSinkingItem(PEAT_SILT);
    public static final DeferredItem<MudBlockItem> TENDER_FLESH_ITEM = registerSinkingItem(TENDER_FLESH);
    public static final DeferredItem<MudBlockItem> MIRE_ITEM = registerSinkingItem(MIRE);
    public static final DeferredItem<MudBlockItem> PEAT_BOG_ITEM = registerSinkingItem(PEAT_BOG);
    public static final DeferredItem<MudBlockItem> LIVING_SLIME_ITEM = registerSinkingItem(LIVING_SLIME);
    public static final DeferredItem<MudBlockItem> ASSIMILATION_SLIME_ITEM = registerSinkingItem(ASSIMILATION_SLIME);
    public static final DeferredItem<MudBlockItem> TAR_ITEM = registerSinkingItem(TAR);
    public static final DeferredItem<MudBlockItem> JUNGLE_QUICKSAND_ITEM = registerSinkingItem(JUNGLE_QUICKSAND);
    public static final DeferredItem<MudBlockItem> INSECT_MOUND_ITEM = registerSinkingItem(INSECT_MOUND);
    public static final DeferredItem<MudBallItem> MUD_BALL = registerMudBall(
            "mud_ball", SinkingMedium.MUD);
    public static final DeferredItem<MudBallItem> THIN_MUD_BALL = registerMudBall(
            "thin_mud_ball", SinkingMedium.THIN_MUD);
    public static final DeferredItem<MudBallItem> SHALLOW_MUD_BALL = registerMudBall(
            "shallow_mud_ball", SinkingMedium.SHALLOW_MUD);
    public static final DeferredItem<MudBallItem> TIDAL_MUD_BALL = registerMudBall(
            "tidal_mud_ball", SinkingMedium.TIDAL_MUD);
    public static final DeferredItem<MudBallItem> LIME_MUD_BALL = registerMudBall(
            "lime_mud_ball", SinkingMedium.LIME_MUD);
    public static final DeferredItem<MudBallItem> GRAVEL_SILT_MUD_BALL = registerMudBall(
            "gravel_silt_mud_ball", SinkingMedium.GRAVEL_SILT);
    public static final DeferredItem<MudBallItem> FUNGAL_MIRE_MUD_BALL = registerMudBall(
            "fungal_mire_mud_ball", SinkingMedium.FUNGAL_MIRE);
    public static final DeferredItem<MudBallItem> PEAT_SILT_MUD_BALL = registerMudBall(
            "peat_silt_mud_ball", SinkingMedium.PEAT_SILT);
    public static final DeferredItem<MudBallItem> MIRE_MUD_BALL = registerMudBall(
            "mire_mud_ball", SinkingMedium.MIRE);
    public static final DeferredItem<MudBallItem> PEAT_BOG_MUD_BALL = registerMudBall(
            "peat_bog_mud_ball", SinkingMedium.PEAT_BOG);
    // Shared backing item for filled and partial buckets; intentionally hidden when empty.
    public static final DeferredItem<MudBucketItem> MUD_BUCKET = ITEMS.registerItem(
            "mud_bucket", MudBucketItem::new, new Item.Properties().stacksTo(1));
    public static final DeferredItem<MudTuningWandItem> MUD_TUNING_WAND = ITEMS.registerItem(
            "mud_tuning_wand",
            MudTuningWandItem::new,
            new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
    public static final DeferredItem<MudProbeItem> MUD_PROBE = ITEMS.registerItem(
            "mud_probe",
            MudProbeItem::new,
            new Item.Properties().stacksTo(1));
    public static final DeferredItem<WaterGunItem> WATER_GUN = ITEMS.registerItem(
            "water_gun",
            WaterGunItem::new,
            new Item.Properties().stacksTo(1));

    private ModBlocks() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
    }

    public static boolean isMud(Block block) {
        return block instanceof MudBlock mudBlock && mudBlock.medium() == SinkingMedium.MUD;
    }

    public static boolean isSinkingBlock(Block block) {
        return block instanceof MudBlock;
    }

    public static boolean isAdaptiveBlock(Block block) {
        return block instanceof AdaptiveMudBlock;
    }

    public static SinkingMedium mediumOf(Block block) {
        return block instanceof MudBlock mudBlock ? mudBlock.medium() : null;
    }

    public static MudBlock blockFor(SinkingMedium medium) {
        DeferredBlock<MudBlock> holder = SINKING_BLOCKS.get(medium);
        return holder == null ? null : holder.get();
    }

    public static AdaptiveMudBlock adaptiveBlockFor(SinkingMedium medium) {
        DeferredBlock<AdaptiveMudBlock> holder = ADAPTIVE_BLOCKS.get(medium);
        return holder == null ? null : holder.get();
    }

    public static Collection<AdaptiveMudBlock> adaptiveBlocks() {
        return ADAPTIVE_BLOCKS.values().stream().map(DeferredBlock::get).toList();
    }

    private static DeferredBlock<MudBlock> registerSinkingBlock(String name, SinkingMedium medium, MapColor mapColor, SoundType sound, float strength) {
        DeferredBlock<MudBlock> block = BLOCKS.registerBlock(
                name,
                properties -> new MudBlock(properties, medium, strength),
                sinkingProperties(mapColor, sound, strength));
        SINKING_BLOCKS.putIfAbsent(medium, block);
        return block;
    }

    private static BlockBehaviour.Properties sinkingProperties(MapColor mapColor, SoundType sound, float strength) {
        return BlockBehaviour.Properties.of()
                .mapColor(mapColor)
                .strength(strength)
                .randomTicks()
                .sound(sound)
                .isViewBlocking((state, level, pos) -> false);
    }

    private static <T extends MudBlock> DeferredItem<MudBlockItem> registerSinkingItem(DeferredBlock<T> block) {
        return ITEMS.registerItem(block.getId().getPath(),
                properties -> new MudBlockItem(block.get(), properties), new Item.Properties());
    }

    private static DeferredItem<MudBallItem> registerMudBall(
            String name, SinkingMedium medium) {
        return ITEMS.registerItem(name,
                properties -> new MudBallItem(medium, properties),
                new Item.Properties());
    }
}

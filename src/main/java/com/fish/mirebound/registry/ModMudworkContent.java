package com.fish.mirebound.registry;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.content.mudwork.MudBallProjectile;
import com.fish.mirebound.content.mudwork.TarFuelItem;
import com.fish.mirebound.rope.RopeItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.food.FoodProperties;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registry owner for survival mudwork materials, building blocks, and tools. */
public final class ModMudworkContent {
    private static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(Mirebound.MOD_ID);
    private static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(Mirebound.MOD_ID);
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, Mirebound.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<MudBallProjectile>>
            MUD_BALL_PROJECTILE = ENTITY_TYPES.register(
                    "mud_ball_projectile",
                    id -> EntityType.Builder.<MudBallProjectile>of(
                                    MudBallProjectile::new, MobCategory.MISC)
                            .sized(0.25F, 0.25F)
                            .clientTrackingRange(8)
                            .updateInterval(1)
                            .noSave()
                            .build(id.toString()));

    public static final DeferredItem<RopeItem> ROPE = ITEMS.registerItem(
            "rope", RopeItem::new,
            new Item.Properties().stacksTo(64));
    public static final DeferredItem<Item> MAGGOT = ITEMS.registerItem(
            "maggot", Item::new,
            new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(2)
                    .saturationModifier(0.1F)
                    .effect(new MobEffectInstance(
                            MobEffects.CONFUSION, 7 * 20), 1.0F)
                    .build()));
    public static final DeferredItem<Item> COOKED_MAGGOT = ITEMS.registerItem(
            "cooked_maggot", Item::new,
            new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(7)
                    .saturationModifier(0.6F)
                    .build()));
    public static final DeferredItem<Item> BLOOD_CLOT_BALL = ITEMS.registerItem(
            "blood_clot_ball", Item::new, new Item.Properties());
    public static final DeferredItem<TarFuelItem> TAR_BLOB = ITEMS.registerItem(
            "tar_blob", TarFuelItem::new, new Item.Properties());
    public static final DeferredItem<Item> GEL_CLAY_BALL = ITEMS.registerItem(
            "gel_clay_ball", Item::new, new Item.Properties());
    public static final DeferredItem<Item> STONE_CLAY_BALL = ITEMS.registerItem(
            "stone_clay_ball", Item::new, new Item.Properties());
    public static final DeferredItem<Item> PALE_CLAY_BALL = ITEMS.registerItem(
            "pale_clay_ball", Item::new, new Item.Properties());

    private ModMudworkContent() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ENTITY_TYPES.register(modBus);
    }

}

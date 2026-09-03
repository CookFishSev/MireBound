package com.fish.mirebound.client;

import com.fish.mirebound.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/** Registers the layered renderer; hand poses remain owned by the item model. */
final class WaterGunClientExtensions {
    private WaterGunClientExtensions() {
    }

    static void register(RegisterClientExtensionsEvent event) {
        WaterGunRenderer renderer = new WaterGunRenderer(Minecraft.getInstance());
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return renderer;
            }

            @Override
            public HumanoidModel.ArmPose getArmPose(
                    LivingEntity entity, InteractionHand hand, ItemStack stack) {
                return hand == InteractionHand.MAIN_HAND
                        && WaterGunStreamClientManager.isFiring(entity.getId())
                        ? HumanoidModel.ArmPose.SPYGLASS : null;
            }
        }, ModBlocks.WATER_GUN.get());
    }
}

package com.fish.mirebound.client;

import com.fish.mirebound.registry.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/** Gives the probe its raised, view-facing pose only while it is being used. */
final class MudProbeClientExtensions {
    private MudProbeClientExtensions() {
    }

    static void register(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public HumanoidModel.ArmPose getArmPose(
                    LivingEntity entity, InteractionHand hand, ItemStack stack) {
                return entity.isUsingItem()
                        && entity.getUsedItemHand() == hand
                        && entity.getUseItem().getItem() == stack.getItem()
                        ? HumanoidModel.ArmPose.SPYGLASS : null;
            }

            @Override
            public boolean applyForgeHandTransform(
                    PoseStack poseStack, LocalPlayer player, HumanoidArm arm,
                    ItemStack itemInHand, float partialTick,
                    float equipProcess, float swingProcess) {
                if (!player.isUsingItem()
                        || player.getUseItem().getItem() != itemInHand.getItem()) {
                    return false;
                }

                int side = arm == HumanoidArm.RIGHT ? 1 : -1;
                poseStack.translate(
                        side * 0.56F, -0.52F + equipProcess * -0.6F, -0.72F);
                if (arm == HumanoidArm.RIGHT) {
                    poseStack.translate(-0.25F, 0.22F, 0.35F);
                    poseStack.mulPose(Axis.XP.rotationDegrees(-80.0F));
                    poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
                } else {
                    poseStack.translate(0.1F, 0.83F, 0.35F);
                    poseStack.mulPose(Axis.XP.rotationDegrees(-80.0F));
                    poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
                    poseStack.translate(-0.3F, 0.22F, 0.35F);
                }
                return true;
            }
        }, ModBlocks.MUD_PROBE.get());
    }
}

package com.fish.mirebound.client;

import com.fish.mirebound.client.config.MireboundClientSettings;
import com.fish.mirebound.client.config.MireboundClientSettings.ClientOption;
import com.fish.mirebound.mud.MudBodyPart;
import com.fish.mirebound.client.coverage.EquipmentSurfaceRenderer;
import com.fish.mirebound.coverage.armor.EquipmentSurfaceTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class ArmorMudRenderBridge {

    private ArmorMudRenderBridge() {
    }

    public static void renderArmorLayer(PoseStack poseStack, MultiBufferSource buffers, int packedLight,
            Model model, int tint, ResourceLocation baseTexture, LivingEntity entity, EquipmentSlot slot) {
        if (!MireboundClientSettings.independentSurfaceCoverage()) {
            ClassicArmorMudRenderer.renderArmorLayer(
                    poseStack, buffers, packedLight, model, tint, baseTexture, entity, slot);
            return;
        }
        if (!MireboundClientSettings.clientOptionEnabled(
                ClientOption.PLAYER_COVERAGE)
                || ClientPollutionVisibility.isSuppressed(entity)) {
            renderWholeModel(poseStack, buffers, packedLight, model, tint, baseTexture);
            return;
        }
        ItemStack stack = entity.getItemBySlot(slot);
        if (!(model instanceof HumanoidModel<?> humanoid)) {
            renderWholeModel(poseStack, buffers, packedLight, model, tint, baseTexture);
            return;
        }

        var surfaceBuffers = new com.fish.mirebound.client.coverage.SurfaceDrawQueue(buffers);
        for (Part part : partsForSlot(humanoid, slot)) {
            if (!part.modelPart.visible) {
                continue;
            }
            renderPart(poseStack, surfaceBuffers.baseBuffers(), packedLight, part.modelPart, tint, baseTexture);
            EquipmentSurfaceRenderer.modelPart(entity, stack, EquipmentSurfaceTarget.armor(slot),
                    part.bodyPart.name(), part.modelPart, baseTexture, poseStack, surfaceBuffers,
                    packedLight, OverlayTexture.NO_OVERLAY, tint, true);
        }
        surfaceBuffers.flush();
    }

    static void reset() {
        ClassicArmorMudRenderer.reset();
    }


    private static void renderWholeModel(PoseStack poseStack, MultiBufferSource buffers, int packedLight,
            Model model, int tint, ResourceLocation texture) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.armorCutoutNoCull(texture));
        model.renderToBuffer(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY, tint);
    }

    private static void renderPart(PoseStack poseStack, MultiBufferSource buffers, int packedLight,
            ModelPart part, int tint, ResourceLocation texture) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.armorCutoutNoCull(texture));
        part.render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY, tint);
    }

    private static Part[] partsForSlot(HumanoidModel<?> model, EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> new Part[] {
                    new Part(model.head, MudBodyPart.HEAD),
                    new Part(model.hat, MudBodyPart.HEAD)
            };
            case CHEST -> new Part[] {
                    new Part(model.body, MudBodyPart.BODY),
                    new Part(model.leftArm, MudBodyPart.LEFT_ARM),
                    new Part(model.rightArm, MudBodyPart.RIGHT_ARM)
            };
            case LEGS -> new Part[] {
                    new Part(model.body, MudBodyPart.BODY),
                    new Part(model.leftLeg, MudBodyPart.LEFT_LEG),
                    new Part(model.rightLeg, MudBodyPart.RIGHT_LEG)
            };
            case FEET -> new Part[] {
                    new Part(model.leftLeg, MudBodyPart.LEFT_LEG),
                    new Part(model.rightLeg, MudBodyPart.RIGHT_LEG)
            };
            default -> new Part[0];
        };
    }


    private record Part(ModelPart modelPart, MudBodyPart bodyPart) {
    }
}

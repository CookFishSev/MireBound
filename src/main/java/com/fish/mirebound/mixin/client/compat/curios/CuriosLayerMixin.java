package com.fish.mirebound.mixin.client.compat.curios;

import com.fish.mirebound.client.ArmorAccessoryRenderContext;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

@Pseudo
@Mixin(targets = "top.theillusivec4.curios.client.render.CuriosLayer", remap = false)
public abstract class CuriosLayerMixin {
    @Redirect(
            method = "lambda$render$0",
            at = @At(
                    value = "INVOKE",
                    target = "Ltop/theillusivec4/curios/api/client/ICurioRenderer;render(Lnet/minecraft/world/item/ItemStack;Ltop/theillusivec4/curios/api/SlotContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/entity/RenderLayerParent;Lnet/minecraft/client/renderer/MultiBufferSource;IFFFFFF)V",
                    remap = false),
            remap = false,
            require = 0)
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void mirebound$captureCurioItem(ICurioRenderer renderer, ItemStack stack,
            SlotContext slotContext, PoseStack poseStack, RenderLayerParent parent,
            MultiBufferSource buffers, int packedLight, float limbSwing, float limbSwingAmount,
            float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        ArmorAccessoryRenderContext.beginCurio(slotContext.entity(), stack, slotContext.identifier(),
                slotContext.index(), slotContext.cosmetic());
        try {
            renderer.render(stack, slotContext, poseStack, parent, buffers, packedLight,
                    limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch);
        } finally {
            ArmorAccessoryRenderContext.endCurio();
        }
    }
}

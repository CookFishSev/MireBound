package com.fish.mirebound.mixin.client.itemphysics;

import com.fish.mirebound.client.itemphysics.DroppedItemPresentation;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMudMixin {
    private static final String RENDER_METHOD =
            "render(Lnet/minecraft/world/entity/item/ItemEntity;FF"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V";

    @ModifyExpressionValue(
            method = RENDER_METHOD,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;sin(F)F"))
    private float mirebound$removeMudBob(
            float original, ItemEntity item, float yaw, float partialTick,
            PoseStack poseStack, MultiBufferSource buffers, int light) {
        return DroppedItemPresentation.settleBobSample(item, partialTick, original);
    }

    @ModifyExpressionValue(
            method = RENDER_METHOD,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/item/ItemEntity;getSpin(F)F"))
    private float mirebound$stabilizeMudSpin(
            float original, ItemEntity item, float yaw, float partialTick,
            PoseStack poseStack, MultiBufferSource buffers, int light) {
        return DroppedItemPresentation.settleYaw(item, partialTick, original);
    }

    @ModifyArg(
            method = RENDER_METHOD,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/ItemEntityRenderer;"
                    + "renderMultipleFromCount(Lnet/minecraft/client/renderer/entity/ItemRenderer;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;I"
                    + "Lnet/minecraft/world/item/ItemStack;"
                    + "Lnet/minecraft/client/resources/model/BakedModel;Z"
                    + "Lnet/minecraft/util/RandomSource;)V"),
            index = 3)
    private int mirebound$useVisibleMudLight(
            int original, @Local(argsOnly = true) ItemEntity item) {
        return DroppedItemPresentation.light(item, original);
    }

    @Inject(
            method = RENDER_METHOD,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/ItemEntityRenderer;"
                            + "renderMultipleFromCount("
                            + "Lnet/minecraft/client/renderer/entity/ItemRenderer;"
                            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                            + "Lnet/minecraft/client/renderer/MultiBufferSource;I"
                            + "Lnet/minecraft/world/item/ItemStack;"
                            + "Lnet/minecraft/client/resources/model/BakedModel;Z"
                            + "Lnet/minecraft/util/RandomSource;)V",
                    shift = At.Shift.BEFORE))
    private void mirebound$tiltEmbeddedItem(
            ItemEntity item, float yaw, float partialTick, PoseStack poseStack,
            MultiBufferSource buffers, int light, CallbackInfo callback) {
        DroppedItemPresentation.applyTilt(item, partialTick, poseStack);
    }
}

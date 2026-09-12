package com.fish.mirebound.mixin.client.armor;

import com.fish.mirebound.client.compat.BackpackSurfaceContext;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemRenderer.class)
abstract class ItemModelEquipmentSurfaceMixin {
    @ModifyExpressionValue(method = "renderModelLists", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/resources/model/BakedModel;getQuads(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;Lnet/minecraft/util/RandomSource;)Ljava/util/List;"))
    private List<BakedQuad> mirebound$wornBackpack(List<BakedQuad> quads, BakedModel model,
            ItemStack stack, int light, int overlay, PoseStack pose, VertexConsumer consumer) {
        BackpackSurfaceContext.render(quads, stack, pose);
        return quads;
    }
}

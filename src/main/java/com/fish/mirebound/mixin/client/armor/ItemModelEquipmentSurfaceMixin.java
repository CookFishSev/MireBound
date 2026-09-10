package com.fish.mirebound.mixin.client.armor;

import com.fish.mirebound.client.compat.BackpackSurfaceContext;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
abstract class ItemModelEquipmentSurfaceMixin {
    @Inject(method = "renderQuadList", at = @At("RETURN"))
    private void mirebound$wornBackpack(PoseStack pose, VertexConsumer consumer, List<BakedQuad> quads,
            ItemStack stack, int light, int overlay, CallbackInfo callback) {
        BackpackSurfaceContext.render(quads, stack, pose, light, overlay);
    }
}

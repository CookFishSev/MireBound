package com.fish.mirebound.mixin.client.mud;

import com.fish.mirebound.client.MudVariantModels;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.FallingBlockRenderer;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FallingBlockRenderer.class)
public abstract class FallingBlockRendererMudModelMixin {
    private static final String RENDER_METHOD =
            "render(Lnet/minecraft/world/entity/item/FallingBlockEntity;FF"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V";

    @ModifyExpressionValue(
            method = RENDER_METHOD,
            at = @At(
                    value = "FIELD",
                    target = "Lnet/neoforged/neoforge/client/model/data/ModelData;"
                            + "EMPTY:Lnet/neoforged/neoforge/client/model/data/ModelData;"))
    private ModelData mirebound$keepMudTextureWhileFalling(
            ModelData original, FallingBlockEntity entity,
            float yaw, float partialTick, PoseStack poseStack,
            MultiBufferSource buffers, int packedLight) {
        return MudVariantModels.fallingModelData(entity);
    }
}

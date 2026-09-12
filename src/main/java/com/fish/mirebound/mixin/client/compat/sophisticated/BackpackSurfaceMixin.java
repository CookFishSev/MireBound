package com.fish.mirebound.mixin.client.compat.sophisticated;

import com.fish.mirebound.client.compat.BackpackSurfaceContext;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedbackpacks.client.render.BackpackLayerRenderer", remap = false)
abstract class BackpackSurfaceMixin {
    @Inject(method = "renderBackpack", at = @At("HEAD"), require = 0)
    private static void mirebound$begin(EntityModel<?> model, LivingEntity entity, PoseStack pose,
            MultiBufferSource buffers, int light, ItemStack stack, boolean offset, CallbackInfo callback) {
        BackpackSurfaceContext.begin(entity, stack, buffers, light);
    }
    @Inject(method = "renderBackpack", at = @At("RETURN"), require = 0)
    private static void mirebound$end(EntityModel<?> model, LivingEntity entity, PoseStack pose,
            MultiBufferSource buffers, int light, ItemStack stack, boolean offset, CallbackInfo callback) {
        BackpackSurfaceContext.end();
    }
}

package com.fish.mirebound.mixin.client.mud;

import com.fish.mirebound.client.AssimilationFrozenBodyProxy;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherAssimilationMixin {
    private static final String RENDER_METHOD =
            "render(Lnet/minecraft/world/entity/Entity;DDDFF"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V";

    @Inject(method = RENDER_METHOD, at = @At("HEAD"), cancellable = true)
    private void mirebound$replaceSealedPlayerWithProxy(Entity entity,
            double x, double y, double z, float yaw, float partialTick,
            PoseStack poseStack, MultiBufferSource buffers, int packedLight,
            CallbackInfo callback) {
        if (entity instanceof AbstractClientPlayer
                && AssimilationFrozenBodyProxy.suppressesOriginal(entity.getId())) {
            callback.cancel();
        }
    }

}

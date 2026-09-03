package com.fish.mirebound.mixin.client.compat.sable;

import com.fish.mirebound.client.MudVariantModels.SableDynamicUpQuad;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.block.model.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Sable uses an unshaded quad as its signal to replace the local normal with
 * the physical structure's dynamic up direction. Keep that convention local
 * to Sable so ordinary partial mud retains vanilla directional light and AO.
 */
@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.render.dynamic_shade.SubLevelVertexConsumer",
        remap = false)
public abstract class SablePartialMudLightingMixin {
    @WrapOperation(
            method = "putBulkData",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/block/model/BakedQuad;isShade()Z",
                    remap = false),
            remap = false,
            require = 0)
    private boolean mirebound$markPartialMudForDynamicUp(
            BakedQuad quad, Operation<Boolean> original) {
        return quad instanceof SableDynamicUpQuad ? false : original.call(quad);
    }
}

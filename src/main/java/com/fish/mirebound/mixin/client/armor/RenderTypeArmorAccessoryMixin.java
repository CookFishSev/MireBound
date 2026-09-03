package com.fish.mirebound.mixin.client.armor;

import com.fish.mirebound.client.ArmorAccessoryRenderContext;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(RenderType.class)
public abstract class RenderTypeArmorAccessoryMixin {
    @ModifyVariable(method = "armorCutoutNoCull", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static ResourceLocation mirebound$useCapturedArmorAccessoryTexture(ResourceLocation texture) {
        return ArmorAccessoryRenderContext.armorTexture(texture);
    }

    @ModifyVariable(
            method = "entitySolid(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static ResourceLocation mirebound$useCapturedSolidEquipmentTexture(ResourceLocation texture) {
        return ArmorAccessoryRenderContext.genericEquipmentTexture(texture);
    }

    @ModifyVariable(
            method = "entityCutout(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static ResourceLocation mirebound$useCapturedCutoutEquipmentTexture(ResourceLocation texture) {
        return ArmorAccessoryRenderContext.genericEquipmentTexture(texture);
    }

    @ModifyVariable(
            method = "entityCutoutNoCull(Lnet/minecraft/resources/ResourceLocation;Z)Lnet/minecraft/client/renderer/RenderType;",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static ResourceLocation mirebound$useCapturedNoCullEquipmentTexture(ResourceLocation texture) {
        return ArmorAccessoryRenderContext.genericEquipmentTexture(texture);
    }

    @ModifyVariable(
            method = "entityCutoutNoCullZOffset(Lnet/minecraft/resources/ResourceLocation;Z)Lnet/minecraft/client/renderer/RenderType;",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static ResourceLocation mirebound$useCapturedOffsetEquipmentTexture(ResourceLocation texture) {
        return ArmorAccessoryRenderContext.genericEquipmentTexture(texture);
    }

    @ModifyVariable(
            method = "itemEntityTranslucentCull(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static ResourceLocation mirebound$useCapturedItemTranslucentEquipmentTexture(ResourceLocation texture) {
        return ArmorAccessoryRenderContext.genericEquipmentTexture(texture);
    }

    @ModifyVariable(
            method = "entityTranslucentCull(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static ResourceLocation mirebound$useCapturedTranslucentCullEquipmentTexture(ResourceLocation texture) {
        return ArmorAccessoryRenderContext.genericEquipmentTexture(texture);
    }

    @ModifyVariable(
            method = "entityTranslucent(Lnet/minecraft/resources/ResourceLocation;Z)Lnet/minecraft/client/renderer/RenderType;",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static ResourceLocation mirebound$useCapturedTranslucentEquipmentTexture(ResourceLocation texture) {
        return ArmorAccessoryRenderContext.genericEquipmentTexture(texture);
    }
}

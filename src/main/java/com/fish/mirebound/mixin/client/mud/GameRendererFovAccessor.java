package com.fish.mirebound.mixin.client.mud;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes the exact FOV used by the world projection after zoom-mod mixins. */
@Mixin(GameRenderer.class)
public interface GameRendererFovAccessor {
    @Invoker("getFov")
    double mirebound$invokeGetFov(Camera camera, float partialTick, boolean useFovSetting);
}

package com.fish.mirebound.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/** Releases both the texture-manager registration and its GPU texture. */
final class DynamicTextureLifecycle {
    private DynamicTextureLifecycle() {
    }

    static void release(ResourceLocation location, DynamicTexture texture) {
        if (location != null) {
            Minecraft.getInstance().getTextureManager().release(location);
        } else if (texture != null) {
            texture.close();
        }
    }
}

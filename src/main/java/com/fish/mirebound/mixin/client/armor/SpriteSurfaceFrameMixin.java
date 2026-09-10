package com.fish.mirebound.mixin.client.armor;

import com.fish.mirebound.client.coverage.UploadedSpriteFrame;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpriteContents.class)
abstract class SpriteSurfaceFrameMixin implements UploadedSpriteFrame {
    @Shadow public abstract int width();
    @Shadow public abstract int height();
    @Unique private NativeImage mirebound$frame;
    @Unique private int mirebound$frameX;
    @Unique private int mirebound$frameY;

    @Inject(method = "upload(IIII[Lcom/mojang/blaze3d/platform/NativeImage;)V", at = @At("HEAD"))
    private void mirebound$recordFrame(int x, int y, int frameX, int frameY,
            NativeImage[] images, CallbackInfo callback) {
        mirebound$frame = images.length == 0 ? null : images[0];
        mirebound$frameX = frameX;
        mirebound$frameY = frameY;
    }

    @Override public int pixel(int x, int y) {
        NativeImage image = mirebound$frame;
        if (image == null || x < 0 || y < 0 || x >= width() || y >= height()) return 0;
        return image.getPixelRGBA(x + mirebound$frameX, y + mirebound$frameY);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void mirebound$forgetFrame(CallbackInfo callback) { mirebound$frame = null; }
}

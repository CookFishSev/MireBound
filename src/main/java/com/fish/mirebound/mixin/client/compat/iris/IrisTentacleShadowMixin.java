package com.fish.mirebound.mixin.client.compat.iris;

import com.fish.mirebound.client.tentacle.ProceduralTentacleRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.culling.Frustum;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.shadows.ShadowRenderer", remap = false)
public abstract class IrisTentacleShadowMixin {
    @Shadow(remap = false)
    public static Matrix4f MODELVIEW;

    @Shadow(remap = false)
    public static Frustum FRUSTUM;

    @Shadow(remap = false)
    @Final
    private RenderBuffers buffers;

    @Unique
    private boolean mirebound$shadowSubmitted;

    @Inject(method = "renderShadows", at = @At("HEAD"), remap = false)
    private void mirebound$beginShadowPass(CallbackInfo callback) {
        mirebound$shadowSubmitted = false;
    }

    @Inject(
            method = "renderShadows",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/irisshaders/batchedentityrendering/impl/FullyBufferedMultiBufferSource;readyUp()V",
                    shift = At.Shift.BEFORE),
            remap = false)
    private void mirebound$submitBeforeReadyUp(CallbackInfo callback) {
        mirebound$submitShadowGeometry();
    }

    @Inject(
            method = "renderShadows",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;endBatch()V",
                    shift = At.Shift.BEFORE),
            remap = false)
    private void mirebound$submitUnbatchedFallback(CallbackInfo callback) {
        mirebound$submitShadowGeometry();
    }

    @Unique
    private void mirebound$submitShadowGeometry() {
        if (mirebound$shadowSubmitted) {
            return;
        }
        mirebound$shadowSubmitted = true;
        if (MODELVIEW != null) {
            MultiBufferSource.BufferSource source = buffers.bufferSource();
            ProceduralTentacleRenderer.renderIrisShadowPass(source, new Matrix4f(MODELVIEW), FRUSTUM);
        }
    }
}

package com.fish.mirebound.client;

import com.fish.mirebound.mud.SinkingMedium;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Frame-local lookup for the RenderType buffers shared by mud surface cells. */
final class MudSurfaceRenderBatchCache {
    private static final Map<ResourceLocation, VertexConsumer> DECAL_CUTOUT_BUFFERS = new HashMap<>();
    private static final Map<ResourceLocation, VertexConsumer> PILE_CUTOUT_BUFFERS = new HashMap<>();
    private static final Map<ResourceLocation, VertexConsumer> DECAL_TRANSLUCENT_BUFFERS =
            new HashMap<>();
    private static final Map<ResourceLocation, VertexConsumer> PILE_TRANSLUCENT_BUFFERS =
            new HashMap<>();
    private static final Map<ResourceLocation, VertexConsumer> BUBBLE_TRANSLUCENT_BUFFERS =
            new HashMap<>();

    private MudSurfaceRenderBatchCache() {
    }

    static void beginFrame() {
        DECAL_CUTOUT_BUFFERS.clear();
        PILE_CUTOUT_BUFFERS.clear();
        DECAL_TRANSLUCENT_BUFFERS.clear();
        PILE_TRANSLUCENT_BUFFERS.clear();
        BUBBLE_TRANSLUCENT_BUFFERS.clear();
    }

    static VertexConsumer decal(MultiBufferSource.BufferSource buffers,
            MudSurfaceAppearance.Appearance appearance, SinkingMedium medium) {
        if (usesTranslucentSurface(appearance, medium)) {
            return DECAL_TRANSLUCENT_BUFFERS.computeIfAbsent(
                    appearance.texture(), texture -> buffers.getBuffer(
                            decalTranslucentRenderType(texture)));
        }
        return DECAL_CUTOUT_BUFFERS.computeIfAbsent(
                appearance.texture(), texture -> buffers.getBuffer(
                        decalCutoutRenderType(texture)));
    }

    static VertexConsumer pile(MultiBufferSource.BufferSource buffers,
            MudSurfaceAppearance.Appearance appearance, SinkingMedium medium) {
        if (usesTranslucentSurface(appearance, medium)) {
            return PILE_TRANSLUCENT_BUFFERS.computeIfAbsent(
                    appearance.texture(), texture -> buffers.getBuffer(
                            pileTranslucentRenderType(texture)));
        }
        return PILE_CUTOUT_BUFFERS.computeIfAbsent(
                appearance.texture(), texture -> buffers.getBuffer(
                        pileCutoutRenderType(texture)));
    }

    static VertexConsumer bubble(MultiBufferSource.BufferSource buffers,
            MudSurfaceAppearance.Appearance appearance) {
        return BUBBLE_TRANSLUCENT_BUFFERS.computeIfAbsent(appearance.texture(), texture ->
                buffers.getBuffer(RenderType.entityTranslucent(texture)));
    }

    static void endFrame(MultiBufferSource.BufferSource buffers) {
        for (ResourceLocation texture : DECAL_CUTOUT_BUFFERS.keySet()) {
            buffers.endBatch(decalCutoutRenderType(texture));
        }
        for (ResourceLocation texture : PILE_CUTOUT_BUFFERS.keySet()) {
            buffers.endBatch(pileCutoutRenderType(texture));
        }
        for (ResourceLocation texture : DECAL_TRANSLUCENT_BUFFERS.keySet()) {
            buffers.endBatch(decalTranslucentRenderType(texture));
        }
        for (ResourceLocation texture : PILE_TRANSLUCENT_BUFFERS.keySet()) {
            buffers.endBatch(pileTranslucentRenderType(texture));
        }
        for (ResourceLocation texture : BUBBLE_TRANSLUCENT_BUFFERS.keySet()) {
            buffers.endBatch(RenderType.entityTranslucent(texture));
        }
    }

    static RenderType decalCutoutRenderType(ResourceLocation texture) {
        // The geometric normal offset already prevents z-fighting. Reusing a
        // vanilla entity batch keeps block-atlas decals on the same renderer
        // path as the raised surface voxels under Sodium-compatible renderers.
        return RenderType.entityCutoutNoCull(texture);
    }

    static RenderType pileCutoutRenderType(ResourceLocation texture) {
        return RenderType.entityCutoutNoCull(texture);
    }

    static RenderType wallFlowRenderType(ResourceLocation texture) {
        return RenderType.entityTranslucent(texture);
    }

    static RenderType decalTranslucentRenderType(ResourceLocation texture) {
        return MudSurfaceDecalRenderTypes.translucent(texture);
    }

    static RenderType pileTranslucentRenderType(ResourceLocation texture) {
        return MudSurfaceDecalRenderTypes.surfaceTranslucent(texture);
    }

    static boolean usesTranslucentSurface(
            MudSurfaceAppearance.Appearance appearance, SinkingMedium medium) {
        return appearance.baseOpacity() < 0.995F
                || medium == SinkingMedium.LIVING_SLIME;
    }

}

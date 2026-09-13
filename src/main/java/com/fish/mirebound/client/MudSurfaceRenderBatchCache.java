package com.fish.mirebound.client;

import com.fish.mirebound.mud.SinkingMedium;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Records touched render types; buffer lifetimes remain owned by Minecraft. */
final class MudSurfaceRenderBatchCache {
    private static final MudSurfaceBatches<RenderType> BATCHES = new MudSurfaceBatches<>();

    private MudSurfaceRenderBatchCache() {}

    static void beginFrame() { BATCHES.begin(); }

    static VertexConsumer decal(MultiBufferSource.BufferSource buffers,
            MudSurfaceAppearance.Appearance appearance, SinkingMedium medium) {
        RenderType type = usesTranslucentSurface(appearance, medium)
                ? decalTranslucentRenderType(appearance.texture()) : decalCutoutRenderType(appearance.texture());
        return BATCHES.buffer(type, buffers::getBuffer);
    }

    static VertexConsumer pile(MultiBufferSource.BufferSource buffers,
            MudSurfaceAppearance.Appearance appearance, SinkingMedium medium) {
        RenderType type = usesTranslucentSurface(appearance, medium)
                ? pileTranslucentRenderType(appearance.texture()) : pileCutoutRenderType(appearance.texture());
        return BATCHES.buffer(type, buffers::getBuffer);
    }

    static VertexConsumer bubble(MultiBufferSource.BufferSource buffers,
            MudSurfaceAppearance.Appearance appearance) {
        return BATCHES.buffer(RenderType.entityTranslucent(appearance.texture()), buffers::getBuffer);
    }

    static void endFrame(MultiBufferSource.BufferSource buffers) { BATCHES.end(buffers::endBatch); }

    static RenderType decalCutoutRenderType(ResourceLocation texture) {
        // Keep atlas decals on the same renderer path as raised surface voxels.
        return RenderType.entityCutoutNoCull(texture);
    }

    static RenderType pileCutoutRenderType(ResourceLocation texture) {
        return RenderType.entityCutoutNoCull(texture);
    }

    static RenderType decalTranslucentRenderType(ResourceLocation texture) {
        return MudSurfaceDecalRenderTypes.translucent(texture);
    }

    static RenderType pileTranslucentRenderType(ResourceLocation texture) {
        return MudSurfaceDecalRenderTypes.surfaceTranslucent(texture);
    }

    static boolean usesTranslucentSurface(
            MudSurfaceAppearance.Appearance appearance, SinkingMedium medium) {
        return appearance.baseOpacity() < 0.995F || medium == SinkingMedium.LIVING_SLIME;
    }
}

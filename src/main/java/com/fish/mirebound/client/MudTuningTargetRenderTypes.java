package com.fish.mirebound.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Depth-aware vanilla render types for the tuning wand's crosshair target. */
final class MudTuningTargetRenderTypes {
    private static final ResourceLocation WHITE_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private static final RenderType FACE = create("face", true, false);
    private static final RenderType EDGE = create("edge", false, true);

    private MudTuningTargetRenderTypes() {
    }

    static RenderType face() {
        return FACE;
    }

    static RenderType edge() {
        return EDGE;
    }

    private static RenderType create(String layer, boolean sortOnUpload,
            boolean writeDepth) {
        return RenderType.create(
                "mirebound_tuning_target_" + layer,
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                256,
                false,
                sortOnUpload,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(
                                WHITE_TEXTURE, false, false))
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setLightmapState(RenderStateShard.LIGHTMAP)
                        .setOverlayState(RenderStateShard.OVERLAY)
                        .setWriteMaskState(writeDepth
                                ? RenderStateShard.COLOR_DEPTH_WRITE
                                : RenderStateShard.COLOR_WRITE)
                        .setOutputState(RenderStateShard.TRANSLUCENT_TARGET)
                        .createCompositeState(false));
    }
}

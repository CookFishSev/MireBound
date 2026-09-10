package com.fish.mirebound.client.coverage;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Decals inherit armor's view-space depth transform without moving the physical contact surface. */
final class SurfaceRenderTypes {
    private static final RenderStateShard.LayeringStateShard ARMOR_DECAL_LAYERING =
            new RenderStateShard.LayeringStateShard("mirebound_armor_surface_layering", () -> {
                RenderStateShard.VIEW_OFFSET_Z_LAYERING.setupRenderState();
                RenderStateShard.POLYGON_OFFSET_LAYERING.setupRenderState();
            }, () -> {
                RenderStateShard.POLYGON_OFFSET_LAYERING.clearRenderState();
                RenderStateShard.VIEW_OFFSET_Z_LAYERING.clearRenderState();
            });
    private static final RenderType SURFACE = create(false);
    private static final RenderType ARMOR_SURFACE = create(true);

    private SurfaceRenderTypes() {}

    static RenderType translucent(boolean armorViewOffset) {
        return armorViewOffset ? ARMOR_SURFACE : SURFACE;
    }

    private static RenderType create(boolean armorViewOffset) {
        return RenderType.create(armorViewOffset ? "mirebound_armor_surface" : "mirebound_equipment_surface",
                DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, false, true,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(
                                ResourceLocation.withDefaultNamespace("textures/misc/white.png"), false, false))
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setLightmapState(RenderStateShard.LIGHTMAP)
                        .setOverlayState(RenderStateShard.OVERLAY)
                        .setLayeringState(armorViewOffset ? ARMOR_DECAL_LAYERING : RenderStateShard.POLYGON_OFFSET_LAYERING)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .createCompositeState(false));
    }
}

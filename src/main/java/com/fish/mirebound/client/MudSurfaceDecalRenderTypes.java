package com.fish.mirebound.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Surface layers use a tiny geometric normal offset; precise wall layers also use depth offset. */
final class MudSurfaceDecalRenderTypes {
    private static final boolean SORT_SURFACE_TRANSLUCENT_QUADS = false;
    private static final Map<ResourceLocation, RenderType> CUTOUT = new HashMap<>();
    private static final Map<ResourceLocation, RenderType> TRANSLUCENT = new HashMap<>();
    private static final Map<ResourceLocation, RenderType> WALL_CUTOUT = new HashMap<>();
    private static final Map<ResourceLocation, RenderType> WALL_TRANSLUCENT = new HashMap<>();
    private static final Map<ResourceLocation, RenderType> SURFACE_TRANSLUCENT = new HashMap<>();
    private static final Map<ResourceLocation, RenderType> MEMBRANE = new HashMap<>();
    private static final Map<ResourceLocation, RenderType> OPAQUE_MEMBRANE = new HashMap<>();

    private MudSurfaceDecalRenderTypes() {
    }

    static RenderType cutout(ResourceLocation texture) {
        return CUTOUT.computeIfAbsent(texture, MudSurfaceDecalRenderTypes::createCutout);
    }

    static RenderType translucent(ResourceLocation texture) {
        return TRANSLUCENT.computeIfAbsent(texture, MudSurfaceDecalRenderTypes::createTranslucent);
    }

    static RenderType wallCutout(ResourceLocation texture) {
        return WALL_CUTOUT.computeIfAbsent(texture, MudSurfaceDecalRenderTypes::createWallCutout);
    }

    static RenderType wallTranslucent(ResourceLocation texture) {
        return WALL_TRANSLUCENT.computeIfAbsent(
                texture, MudSurfaceDecalRenderTypes::createWallTranslucent);
    }

    static RenderType surfaceTranslucent(ResourceLocation texture) {
        return SURFACE_TRANSLUCENT.computeIfAbsent(
                texture, MudSurfaceDecalRenderTypes::createSurfaceTranslucent);
    }

    static boolean sortsSurfaceTranslucentQuads() {
        return SORT_SURFACE_TRANSLUCENT_QUADS;
    }

    static RenderType membrane(ResourceLocation texture) {
        return MEMBRANE.computeIfAbsent(texture, MudSurfaceDecalRenderTypes::createMembrane);
    }

    static RenderType opaqueMembrane(ResourceLocation texture) {
        return OPAQUE_MEMBRANE.computeIfAbsent(texture,
                MudSurfaceDecalRenderTypes::createOpaqueMembrane);
    }

    static void release(ResourceLocation texture) {
        CUTOUT.remove(texture);
        TRANSLUCENT.remove(texture);
        WALL_CUTOUT.remove(texture);
        WALL_TRANSLUCENT.remove(texture);
        SURFACE_TRANSLUCENT.remove(texture);
        MEMBRANE.remove(texture);
        OPAQUE_MEMBRANE.remove(texture);
    }

    private static RenderType createCutout(ResourceLocation texture) {
        return RenderType.create(
                "mirebound_surface_decal_cutout",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                256,
                false,
                false,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                        .setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
                        .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setLightmapState(RenderStateShard.LIGHTMAP)
                        .setOverlayState(RenderStateShard.OVERLAY)
                        .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                        .createCompositeState(false));
    }

    private static RenderType createTranslucent(ResourceLocation texture) {
        return RenderType.create(
                "mirebound_surface_decal_translucent",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                256,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setLightmapState(RenderStateShard.LIGHTMAP)
                        .setOverlayState(RenderStateShard.OVERLAY)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .createCompositeState(false));
    }

    private static RenderType createWallCutout(ResourceLocation texture) {
        return RenderType.create(
                "mirebound_wall_decal_cutout",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                256,
                false,
                false,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                        .setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
                        .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setLightmapState(RenderStateShard.LIGHTMAP)
                        .setOverlayState(RenderStateShard.OVERLAY)
                        .setLayeringState(RenderStateShard.POLYGON_OFFSET_LAYERING)
                        .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                        .createCompositeState(false));
    }

    private static RenderType createWallTranslucent(ResourceLocation texture) {
        return RenderType.create(
                "mirebound_wall_decal_translucent",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                256,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setLightmapState(RenderStateShard.LIGHTMAP)
                        .setOverlayState(RenderStateShard.OVERLAY)
                        .setLayeringState(RenderStateShard.POLYGON_OFFSET_LAYERING)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .createCompositeState(false));
    }

    private static RenderType createSurfaceTranslucent(ResourceLocation texture) {
        return RenderType.create(
                "mirebound_surface_translucent",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                1536,
                true,
                SORT_SURFACE_TRANSLUCENT_QUADS,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_CULL_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setLightmapState(RenderStateShard.LIGHTMAP)
                        .setOverlayState(RenderStateShard.OVERLAY)
                        .createCompositeState(true));
    }

    private static RenderType createMembrane(ResourceLocation texture) {
        return RenderType.create(
                "mirebound_tender_flesh_membrane",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.TRIANGLES,
                256,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setLightmapState(RenderStateShard.LIGHTMAP)
                        .setOverlayState(RenderStateShard.OVERLAY)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .createCompositeState(false));
    }

    private static RenderType createOpaqueMembrane(ResourceLocation texture) {
        return RenderType.create(
                "mirebound_tender_flesh_membrane_opaque",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.TRIANGLES,
                256,
                false,
                false,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                        .setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
                        .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setLightmapState(RenderStateShard.LIGHTMAP)
                        .setOverlayState(RenderStateShard.OVERLAY)
                        .setLayeringState(RenderStateShard.POLYGON_OFFSET_LAYERING)
                        .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                        .createCompositeState(false));
    }

}

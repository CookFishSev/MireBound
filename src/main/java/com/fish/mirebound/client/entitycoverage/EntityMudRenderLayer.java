package com.fish.mirebound.client.entitycoverage;

import com.fish.mirebound.client.compat.ClientRenderCompat;
import com.fish.mirebound.client.config.MireboundClientSettings;
import com.fish.mirebound.client.config.MireboundClientSettings.ClientOption;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/** One shared-texture model pass for non-player living-entity mud coverage. */
public final class EntityMudRenderLayer<T extends LivingEntity, M extends EntityModel<T>>
        extends RenderLayer<T, M> {
    private final LivingEntityRenderer<T, M> renderer;

    public EntityMudRenderLayer(LivingEntityRenderer<T, M> renderer) {
        super(renderer);
        this.renderer = renderer;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource,
            int packedLight, T entity, float limbSwing, float limbSwingAmount,
            float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (entity.isInvisible()
                || !MireboundClientSettings.clientOptionEnabled(
                        ClientOption.ENTITY_COVERAGE)) {
            return;
        }
        ClientEntityMudCoverage.View view = ClientEntityMudCoverage.view(entity);
        if (view == null || view.totalCoverage() <= 0.001F
                || ClientRenderCompat.isRenderingShaderShadowPass()) {
            return;
        }
        ResourceLocation texture = EntityMudTextureCache.textureFor(
                entity, view, poseStack, getParentModel(),
                renderer.getTextureLocation(entity));
        if (texture == null) {
            return;
        }
        getParentModel().renderToBuffer(
                poseStack,
                bufferSource.getBuffer(RenderType.entityTranslucent(texture)),
                packedLight,
                LivingEntityRenderer.getOverlayCoords(entity, 0.0F),
                FastColor.ARGB32.color(
                        Mth.clamp(Math.round(
                                view.automaticFadeScale() * 255.0F), 0, 255),
                        255, 255, 255));
    }
}

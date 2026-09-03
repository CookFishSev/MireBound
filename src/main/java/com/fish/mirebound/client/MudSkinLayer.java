package com.fish.mirebound.client;

import com.fish.mirebound.client.config.MireboundClientSettings;
import com.fish.mirebound.client.config.MireboundClientSettings.ClientOption;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

public final class MudSkinLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static int lastRenderedEntityId = Integer.MIN_VALUE;
    private static int lastRenderedTick = Integer.MIN_VALUE;

    public MudSkinLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, AbstractClientPlayer player,
            float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!ClientMudDebugOptions.skinLayer()
                || !MireboundClientSettings.clientOptionEnabled(
                        ClientOption.PLAYER_COVERAGE)
                || player.isInvisible()
                || ClientPollutionVisibility.isSuppressed(player)) {
            return;
        }

        PlayerModel<AbstractClientPlayer> model = getParentModel();
        int overlay = LivingEntityRenderer.getOverlayCoords(player, 0.0F);
        ResourceLocation skinTexture = player.getSkin().texture();
        if (MudSkinTextureCache.isGeneratedSkin(skinTexture)) {
            markRendered(player);
            return;
        }
        boolean slimModel = player.getSkin().model() == PlayerSkin.Model.SLIM;
        ResourceLocation mudTexture = MudSkinTextureCache.textureFor(player.getId(), skinTexture, slimModel);
        if (mudTexture == null) {
            return;
        }

        renderOverlay(model, poseStack, bufferSource, packedLight, overlay, mudTexture);
        markRendered(player);
    }

    static boolean wasRenderedThisTick(AbstractClientPlayer player) {
        return player.getId() == lastRenderedEntityId && player.tickCount == lastRenderedTick;
    }

    private static void markRendered(AbstractClientPlayer player) {
        lastRenderedEntityId = player.getId();
        lastRenderedTick = player.tickCount;
    }

    static void renderOverlay(PlayerModel<AbstractClientPlayer> model, PoseStack poseStack, MultiBufferSource bufferSource,
            int packedLight, int overlay, ResourceLocation mudTexture) {
        MudRenderStyle.renderPart(model.rightLeg, poseStack, bufferSource, packedLight, overlay, mudTexture);
        MudRenderStyle.renderPart(model.leftLeg, poseStack, bufferSource, packedLight, overlay, mudTexture);
        MudRenderStyle.renderPart(model.rightPants, poseStack, bufferSource, packedLight, overlay, mudTexture);
        MudRenderStyle.renderPart(model.leftPants, poseStack, bufferSource, packedLight, overlay, mudTexture);
        MudRenderStyle.renderPart(model.body, poseStack, bufferSource, packedLight, overlay, mudTexture);
        MudRenderStyle.renderPart(model.jacket, poseStack, bufferSource, packedLight, overlay, mudTexture);
        MudRenderStyle.renderPart(model.rightArm, poseStack, bufferSource, packedLight, overlay, mudTexture);
        MudRenderStyle.renderPart(model.leftArm, poseStack, bufferSource, packedLight, overlay, mudTexture);
        MudRenderStyle.renderPart(model.rightSleeve, poseStack, bufferSource, packedLight, overlay, mudTexture);
        MudRenderStyle.renderPart(model.leftSleeve, poseStack, bufferSource, packedLight, overlay, mudTexture);
        MudRenderStyle.renderPart(model.head, poseStack, bufferSource, packedLight, overlay, mudTexture);
        MudRenderStyle.renderPart(model.hat, poseStack, bufferSource, packedLight, overlay, mudTexture);
    }
}

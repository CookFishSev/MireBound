package com.fish.mirebound.client;

import com.fish.mirebound.client.config.MireboundClientSettings;
import com.fish.mirebound.client.config.MireboundClientSettings.ClientOption;
import com.fish.mirebound.mud.MudBodyPart;
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
        renderOverlay(model, poseStack, bufferSource, packedLight, overlay,
                player.getId(), skinTexture, slimModel);
        markRendered(player);
    }

    static boolean wasRenderedThisTick(AbstractClientPlayer player) {
        return player.getId() == lastRenderedEntityId && player.tickCount == lastRenderedTick;
    }

    private static void markRendered(AbstractClientPlayer player) {
        lastRenderedEntityId = player.getId();
        lastRenderedTick = player.tickCount;
    }

    static void renderOverlay(PlayerModel<AbstractClientPlayer> model, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, int overlay, int entityId,
            ResourceLocation skinTexture, boolean slimModel) {
        renderPart(model.rightLeg, model.rightPants, entityId, skinTexture, slimModel,
                MudBodyPart.RIGHT_LEG, poseStack, bufferSource, packedLight, overlay);
        renderPart(model.leftLeg, model.leftPants, entityId, skinTexture, slimModel,
                MudBodyPart.LEFT_LEG, poseStack, bufferSource, packedLight, overlay);
        renderPart(model.body, model.jacket, entityId, skinTexture, slimModel,
                MudBodyPart.BODY, poseStack, bufferSource, packedLight, overlay);
        renderPart(model.rightArm, model.rightSleeve, entityId, skinTexture, slimModel,
                MudBodyPart.RIGHT_ARM, poseStack, bufferSource, packedLight, overlay);
        renderPart(model.leftArm, model.leftSleeve, entityId, skinTexture, slimModel,
                MudBodyPart.LEFT_ARM, poseStack, bufferSource, packedLight, overlay);
        renderPart(model.head, model.hat, entityId, skinTexture, slimModel,
                MudBodyPart.HEAD, poseStack, bufferSource, packedLight, overlay);
    }

    private static void renderPart(net.minecraft.client.model.geom.ModelPart base,
            net.minecraft.client.model.geom.ModelPart overlay, int entityId,
            ResourceLocation skinTexture, boolean slimModel, MudBodyPart part,
            PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
            int packedOverlay) {
        MudRenderStyle.renderCoveredSkinPart(base, poseStack, bufferSource,
                packedLight, packedOverlay, entityId, part, skinTexture, slimModel);
        MudRenderStyle.renderCoveredSkinPart(overlay, poseStack, bufferSource,
                packedLight, packedOverlay, entityId, part, skinTexture, slimModel);
    }
}

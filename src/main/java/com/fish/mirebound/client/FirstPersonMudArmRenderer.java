package com.fish.mirebound.client;

import com.fish.mirebound.client.config.MireboundClientSettings;
import com.fish.mirebound.client.config.MireboundClientSettings.ClientOption;
import com.fish.mirebound.mud.MudBodyPart;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.neoforge.client.event.RenderArmEvent;

final class FirstPersonMudArmRenderer {
    private static boolean renderingVanillaArm;

    private FirstPersonMudArmRenderer() {
    }

    static void onRenderArm(RenderArmEvent event) {
        if (renderingVanillaArm || !ClientMudDebugOptions.skinLayer()
                || !MireboundClientSettings.clientOptionEnabled(
                        ClientOption.PLAYER_COVERAGE)) {
            return;
        }

        AbstractClientPlayer player = event.getPlayer();
        if (ClientPollutionVisibility.isSuppressed(player)) {
            return;
        }
        MudBodyPart part = event.getArm() == HumanoidArm.RIGHT ? MudBodyPart.RIGHT_ARM : MudBodyPart.LEFT_ARM;
        float coverage = ClientMudState.displayPartCoverage(player.getId(), part);
        if (coverage <= 0.004F || player.isInvisible()) {
            return;
        }
        if (MudSkinTextureCache.isGeneratedSkin(player.getSkin().texture())) {
            return;
        }

        EntityRenderer<? super AbstractClientPlayer> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player);
        if (!(renderer instanceof PlayerRenderer playerRenderer)) {
            return;
        }

        boolean slimModel = player.getSkin().model() == PlayerSkin.Model.SLIM;
        renderingVanillaArm = true;
        try {
            if (event.getArm() == HumanoidArm.RIGHT) {
                playerRenderer.renderRightHand(event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(), player);
            } else {
                playerRenderer.renderLeftHand(event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(), player);
            }
        } finally {
            renderingVanillaArm = false;
        }

        renderMudArm(event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(),
                playerRenderer.getModel(), player, slimModel, event.getArm());
        event.setCanceled(true);
    }

    private static void renderMudArm(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
            PlayerModel<AbstractClientPlayer> model, AbstractClientPlayer player,
            boolean slimModel, HumanoidArm arm) {
        MudBodyPart part = arm == HumanoidArm.RIGHT
                ? MudBodyPart.RIGHT_ARM : MudBodyPart.LEFT_ARM;
        if (arm == HumanoidArm.RIGHT) {
            MudRenderStyle.renderCoveredSkinPart(model.rightArm, poseStack, bufferSource,
                    packedLight, OverlayTexture.NO_OVERLAY, player.getId(), part,
                    player.getSkin().texture(), slimModel);
            MudRenderStyle.renderCoveredSkinPart(model.rightSleeve, poseStack, bufferSource,
                    packedLight, OverlayTexture.NO_OVERLAY, player.getId(), part,
                    player.getSkin().texture(), slimModel);
        } else {
            MudRenderStyle.renderCoveredSkinPart(model.leftArm, poseStack, bufferSource,
                    packedLight, OverlayTexture.NO_OVERLAY, player.getId(), part,
                    player.getSkin().texture(), slimModel);
            MudRenderStyle.renderCoveredSkinPart(model.leftSleeve, poseStack, bufferSource,
                    packedLight, OverlayTexture.NO_OVERLAY, player.getId(), part,
                    player.getSkin().texture(), slimModel);
        }
    }
}

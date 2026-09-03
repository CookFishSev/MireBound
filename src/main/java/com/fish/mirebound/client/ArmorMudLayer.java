package com.fish.mirebound.client;

import com.fish.mirebound.client.config.MireboundClientSettings;
import com.fish.mirebound.client.config.MireboundClientSettings.ClientOption;
import com.fish.mirebound.mud.ArmorMudData;
import com.fish.mirebound.mud.ArmorMudManager;
import com.fish.mirebound.mud.MudBodyPart;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

public final class ArmorMudLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private final HumanoidArmorModel<AbstractClientPlayer> innerModel;
    private final HumanoidArmorModel<AbstractClientPlayer> outerModel;

    public ArmorMudLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
            EntityModelSet models, boolean slim) {
        super(parent);
        innerModel = new HumanoidArmorModel<>(models.bakeLayer(
                slim ? ModelLayers.PLAYER_SLIM_INNER_ARMOR : ModelLayers.PLAYER_INNER_ARMOR));
        outerModel = new HumanoidArmorModel<>(models.bakeLayer(
                slim ? ModelLayers.PLAYER_SLIM_OUTER_ARMOR : ModelLayers.PLAYER_OUTER_ARMOR));
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight, AbstractClientPlayer player,
            float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw,
            float headPitch) {
        if (player.isInvisible()
                || !MireboundClientSettings.clientOptionEnabled(
                        ClientOption.PLAYER_COVERAGE)
                || ClientPollutionVisibility.isSuppressed(player)) {
            return;
        }
        int overlay = LivingEntityRenderer.getOverlayCoords(player, 0.0F);
        for (EquipmentSlot slot : ArmorMudManager.armorSlots()) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!ArmorMudManager.validArmor(stack, slot)) {
                continue;
            }
            ArmorMudData data = ArmorMudManager.data(stack);
            if (data.isEmpty()) {
                continue;
            }
            if (ArmorMudRenderBridge.wasComposited(player.getId(), slot, player.level().getGameTime())) {
                continue;
            }

            HumanoidArmorModel<AbstractClientPlayer> original = slot == EquipmentSlot.LEGS ? innerModel : outerModel;
            getParentModel().copyPropertiesTo(original);
            original.setAllVisible(false);
            Model resolved = ClientHooks.getArmorModel(player, stack, slot, original);
            if (!(resolved instanceof HumanoidModel<?> armorModel)) {
                continue;
            }
            IClientItemExtensions.of(stack).setupModelAnimations(
                    player, stack, slot, resolved, limbSwing, limbSwingAmount,
                    partialTick, ageInTicks, netHeadYaw, headPitch);
            for (MudBodyPart part : partsForSlot(slot)) {
                var texture = ArmorMudTextureCache.textureFor(
                        player.getId(), slot, part, data, player.level().getGameTime());
                if (texture == null) {
                    continue;
                }
                armorModel.setAllVisible(false);
                ModelPart modelPart = modelPart(armorModel, part);
                modelPart.visible = true;
                MudRenderStyle.renderArmorPart(modelPart, poseStack, buffers, packedLight, overlay, texture);
            }
        }
    }

    private static List<MudBodyPart> partsForSlot(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> List.of(MudBodyPart.HEAD);
            case CHEST -> List.of(MudBodyPart.BODY, MudBodyPart.LEFT_ARM, MudBodyPart.RIGHT_ARM);
            case LEGS -> List.of(MudBodyPart.BODY, MudBodyPart.LEFT_LEG, MudBodyPart.RIGHT_LEG);
            case FEET -> List.of(MudBodyPart.LEFT_LEG, MudBodyPart.RIGHT_LEG);
            default -> List.of();
        };
    }

    private static ModelPart modelPart(HumanoidModel<?> model, MudBodyPart part) {
        return switch (part) {
            case HEAD -> model.head;
            case BODY -> model.body;
            case LEFT_ARM -> model.leftArm;
            case RIGHT_ARM -> model.rightArm;
            case LEFT_LEG -> model.leftLeg;
            case RIGHT_LEG -> model.rightLeg;
        };
    }
}

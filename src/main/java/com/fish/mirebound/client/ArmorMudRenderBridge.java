package com.fish.mirebound.client;

import com.fish.mirebound.client.config.MireboundClientSettings;
import com.fish.mirebound.client.config.MireboundClientSettings.ClientOption;
import com.fish.mirebound.mud.ArmorMudData;
import com.fish.mirebound.mud.ArmorMudManager;
import com.fish.mirebound.mud.ArmorTextureMudData;
import com.fish.mirebound.mud.MudBodyPart;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class ArmorMudRenderBridge {
    private static final Map<Long, Long> COMPOSITED_SLOTS = new HashMap<>();
    private static long compositedGameTime = Long.MIN_VALUE;

    private ArmorMudRenderBridge() {
    }

    public static void renderArmorLayer(PoseStack poseStack, MultiBufferSource buffers, int packedLight,
            Model model, int tint, ResourceLocation baseTexture, LivingEntity entity, EquipmentSlot slot) {
        if (!MireboundClientSettings.clientOptionEnabled(
                ClientOption.PLAYER_COVERAGE)
                || ClientPollutionVisibility.isSuppressed(entity)) {
            renderWholeModel(poseStack, buffers, packedLight, model, tint, baseTexture);
            return;
        }
        ItemStack stack = entity.getItemBySlot(slot);
        ArmorMudData data = ArmorMudManager.data(stack);
        if (data.isEmpty() || !(model instanceof HumanoidModel<?> humanoid)) {
            renderWholeModel(poseStack, buffers, packedLight, model, tint, baseTexture);
            return;
        }

        boolean composited = false;
        long gameTime = entity.level().getGameTime();
        for (Part part : partsForSlot(humanoid, slot)) {
            if (!part.modelPart.visible || part.modelPart.skipDraw) {
                continue;
            }
            ResourceLocation composite = ArmorMudCompositeTextureCache.textureFor(
                    entity.getId(), baseTexture, tint, slot, part.bodyPart, part.modelPart, data, gameTime);
            if (composite == null) {
                renderPart(poseStack, buffers, packedLight, part.modelPart, tint, baseTexture);
            } else {
                renderPart(poseStack, buffers, packedLight, part.modelPart, 0xFFFFFFFF, composite);
                composited = true;
            }
        }
        if (composited) {
            prepareCompositeTick(gameTime);
            COMPOSITED_SLOTS.put(key(entity.getId(), slot), gameTime);
        }
    }

    public static ResourceLocation accessoryTextureFor(LivingEntity entity, EquipmentSlot slot,
            MudBodyPart bodyPart, ModelPart projectionModel, ResourceLocation baseTexture) {
        ItemStack stack = entity.getItemBySlot(slot);
        return accessoryTextureFor(entity, stack, "armor:" + slot.getName(), bodyPart,
                projectionModel, baseTexture);
    }

    public static ResourceLocation accessoryTextureFor(LivingEntity entity, ItemStack stack,
            String targetKey, MudBodyPart bodyPart, ModelPart projectionModel,
            ResourceLocation baseTexture) {
        if (!MireboundClientSettings.clientOptionEnabled(
                ClientOption.PLAYER_COVERAGE)
                || ClientPollutionVisibility.isSuppressed(entity)) {
            return baseTexture;
        }
        ArmorMudData data = ArmorMudManager.data(stack);
        ArmorTextureMudData textureData = ArmorMudManager.textureData(stack);
        if (data.isEmpty() && textureData.isEmpty()) {
            return baseTexture;
        }
        ResourceLocation composite = ArmorMudCompositeTextureCache.accessoryTextureFor(
                entity.getId(), baseTexture, targetKey, bodyPart, projectionModel, data, textureData,
                entity.level().getGameTime());
        return composite == null ? baseTexture : composite;
    }

    static boolean wasComposited(int entityId, EquipmentSlot slot, long gameTime) {
        if (compositedGameTime != gameTime) {
            return false;
        }
        return COMPOSITED_SLOTS.getOrDefault(key(entityId, slot), Long.MIN_VALUE) == gameTime;
    }

    static void reset() {
        COMPOSITED_SLOTS.clear();
        compositedGameTime = Long.MIN_VALUE;
        ArmorMudCompositeTextureCache.reset();
    }

    private static void prepareCompositeTick(long gameTime) {
        if (compositedGameTime == gameTime) {
            return;
        }
        COMPOSITED_SLOTS.clear();
        compositedGameTime = gameTime;
    }

    private static void renderWholeModel(PoseStack poseStack, MultiBufferSource buffers, int packedLight,
            Model model, int tint, ResourceLocation texture) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.armorCutoutNoCull(texture));
        model.renderToBuffer(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY, tint);
    }

    private static void renderPart(PoseStack poseStack, MultiBufferSource buffers, int packedLight,
            ModelPart part, int tint, ResourceLocation texture) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.armorCutoutNoCull(texture));
        part.render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY, tint);
    }

    private static Part[] partsForSlot(HumanoidModel<?> model, EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> new Part[] {
                    new Part(model.head, MudBodyPart.HEAD),
                    new Part(model.hat, MudBodyPart.HEAD)
            };
            case CHEST -> new Part[] {
                    new Part(model.body, MudBodyPart.BODY),
                    new Part(model.leftArm, MudBodyPart.LEFT_ARM),
                    new Part(model.rightArm, MudBodyPart.RIGHT_ARM)
            };
            case LEGS -> new Part[] {
                    new Part(model.body, MudBodyPart.BODY),
                    new Part(model.leftLeg, MudBodyPart.LEFT_LEG),
                    new Part(model.rightLeg, MudBodyPart.RIGHT_LEG)
            };
            case FEET -> new Part[] {
                    new Part(model.leftLeg, MudBodyPart.LEFT_LEG),
                    new Part(model.rightLeg, MudBodyPart.RIGHT_LEG)
            };
            default -> new Part[0];
        };
    }

    private static long key(int entityId, EquipmentSlot slot) {
        return (long) entityId << 8 | slot.ordinal();
    }

    private record Part(ModelPart modelPart, MudBodyPart bodyPart) {
    }
}
